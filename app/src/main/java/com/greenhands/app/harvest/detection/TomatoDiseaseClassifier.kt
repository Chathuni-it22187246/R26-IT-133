package com.greenhands.app.harvest.detection

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.greenhands.app.harvest.data.HarvestMeasurementStore
import com.greenhands.app.harvest.domain.HarvestArgbFrame
import com.greenhands.app.harvest.domain.TomatoDiseaseClassificationCalibration
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.Tensor
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * YOLOv8-classification LiteRT model for tomato leaf disease classes.
 * Not a leaf detector and not a confirmed diagnosis.
 */
class TomatoDiseaseClassifier(
    private val appContext: Context,
    private val interpreter: Interpreter,
    private val labels: List<String>,
    private val calibration: TomatoDiseaseClassificationCalibration =
        TomatoDiseaseClassificationCalibration.PROJECT
) : AutoCloseable {

    private val inputTensor: Tensor = interpreter.getInputTensor(0)
    private val outputTensor: Tensor = interpreter.getOutputTensor(0)
    private val inputSpec: InputSpec = InputSpec.from(inputTensor.shape())
    private val inputIsQuantized: Boolean = inputTensor.dataType() == DataType.UINT8
    private val outputIsQuantized: Boolean = outputTensor.dataType() == DataType.UINT8
    private val inputBuffer: ByteBuffer
    private val outputBuffer: ByteBuffer

    init {
        val inBytes = if (inputIsQuantized) 1 else 4
        inputBuffer = ByteBuffer.allocateDirect(
            1 * inputSpec.channels * inputSpec.height * inputSpec.width * inBytes
        ).order(ByteOrder.nativeOrder())
        outputBuffer = ByteBuffer.allocateDirect(outputTensor.numBytes().coerceAtLeast(4))
            .order(ByteOrder.nativeOrder())
        logModelIdentity()
    }

    fun classify(frame: HarvestArgbFrame): TomatoDiseaseClassifyResult {
        return try {
            val roi = ClassifierLeafRoiFocuser.focus(frame)
            val focused = roi.frame
            val sized = focused?.let { ArgbFrameScaler.scale(it, inputSpec.width, inputSpec.height) }
            persistDebugFrames(frame, focused, sized)
            Log.i(DBG, "hybridCrop ${TomatoDiseaseDebug.roiStats(frame)}")
            Log.i(DBG, "focusedRoi ${focused?.let { TomatoDiseaseDebug.roiStats(it) } ?: "none"}")
            Log.i(DBG, "input224 ${sized?.let { TomatoDiseaseDebug.roiStats(it) } ?: "none"}")
            Log.i(
                DBG,
                "originalCrop=${roi.originalWidth}x${roi.originalHeight} " +
                    "focusedRoi=${roi.roiWidth}x${roi.roiHeight} " +
                    "bbox=${roi.bboxLabel} " +
                    "vegetation=${"%.1f".format(roi.vegetationPercent)}% " +
                    "retained=${"%.1f".format(roi.retainedPercent)}% " +
                    "leafFocused=${roi.usedFocusedRoi} source=${roi.source} reason=${roi.reason} " +
                    roi.roiDebugLine
            )
            Log.i(
                DBG,
                "cropPipeline=hybridCrop then classifierLeafFocus(${roi.source}) " +
                    "squareNoStretch=${focused != null && focused.width == focused.height} " +
                    "hsvStillUsesHybridCrop=true"
            )
            if (!roi.usedFocusedRoi || focused == null || sized == null) {
                Log.i(DBG, "classify skipped unreliable_roi reason=${roi.reason} top3=n/a")
                HarvestMeasurementStore.lastDiseaseDebugSummary =
                    "leafFocused=false classified=false reason=${roi.reason} " +
                        "${roi.roiDebugLine} " +
                        "vegetation=${"%.1f".format(roi.vegetationPercent)}% " +
                        "retained=${"%.1f".format(roi.retainedPercent)}% " +
                        "${roi.originalWidth}x${roi.originalHeight}->${roi.roiWidth}x${roi.roiHeight} " +
                        "top3=n/a"
                return TomatoDiseaseClassifyResult.UnreliableRoi(roi.reason)
            }
            Log.i(DBG, "channelOrder=RGB (R from ARGB bits 16-23, then G, then B). Not BGR.")
            Log.i(
                DBG,
                "normalize=${if (inputIsQuantized) "uint8 0..255" else "float32 value/255.0 expected 0..1"} " +
                    "layout=${if (inputSpec.nchw) "NCHW [1,3,H,W] planes R then G then B" else "NHWC [1,H,W,3] interleaved RGB"}"
            )
            fillInput(sized)
            logInputBufferRange(sized)
            outputBuffer.rewind()
            interpreter.run(inputBuffer, outputBuffer)
            val raw = readOutputScores()
            if (raw.isEmpty()) {
                return TomatoDiseaseClassifyResult.Failed("empty_output")
            }
            Log.i(DBG, "raw11 BEFORE prob conversion ${TomatoDiseaseDebug.formatScores(raw, labels)}")
            val (probs, appliedSoftmax) = TomatoDiseaseScores.asProbabilities(raw)
            Log.i(
                DBG,
                "appliedSoftmax=$appliedSoftmax looksLikeProbsAlready=" +
                    "${TomatoDiseaseScores.looksLikeProbabilities(raw)}"
            )
            Log.i(DBG, "prob11 AFTER conversion ${TomatoDiseaseDebug.formatScores(probs, labels)}")
            val ranked = TomatoDiseaseScores.topK(probs, TOP_K)
            val best = ranked.firstOrNull()
                ?: return TomatoDiseaseClassifyResult.Failed("no_top_class")
            val rawName = TomatoDiseaseLabels.labelAt(best.index, labels)
            val display = TomatoDiseaseLabels.displayName(rawName)
            val top = ranked.map { item ->
                val name = TomatoDiseaseLabels.labelAt(item.index, labels)
                TomatoDiseaseClassScore(
                    classIndex = item.index,
                    rawClassName = name,
                    displayName = TomatoDiseaseLabels.displayName(name),
                    confidence = item.value
                )
            }
            val top3 = top.joinToString(" | ") {
                "${it.classIndex}:${it.rawClassName}=${"%.4f".format(it.confidence)}"
            }
            Log.i(DBG, "top3=$top3")
            Log.i(
                DBG,
                "chosen index=${best.index} raw=$rawName display=$display " +
                    "p=${"%.4f".format(best.value)} index3=${labelOrMissing(3)} " +
                    "index9=${labelOrMissing(9)} healthyMapped=" +
                    "${TomatoDiseaseLabels.isHealthy(labelOrMissing(9))}"
            )
            HarvestMeasurementStore.lastDiseaseDebugSummary =
                "leafFocused=true classified=true ${roi.roiDebugLine} " +
                    "vegetation=${"%.1f".format(roi.vegetationPercent)}% " +
                    "retained=${"%.1f".format(roi.retainedPercent)}% " +
                    "${roi.originalWidth}x${roi.originalHeight}->${roi.roiWidth}x${roi.roiHeight} " +
                    "input=${inputTensor.shape().contentToString()} ${inputTensor.dataType()} " +
                    "${if (inputSpec.nchw) "NCHW" else "NHWC"} RGB /255 " +
                    "out=${outputTensor.shape().contentToString()} ${outputTensor.dataType()} " +
                    "softmax=$appliedSoftmax top=${best.index}:$rawName=" +
                    "${"%.3f".format(best.value)} top3=$top3"
            TomatoDiseaseClassifyResult.Success(
                TomatoDiseasePrediction(
                    classIndex = best.index,
                    rawClassName = rawName,
                    displayName = display,
                    confidence = best.value,
                    meetsThreshold = best.value >= calibration.confidenceThreshold,
                    isHealthyClass = TomatoDiseaseLabels.isHealthy(rawName),
                    appliedSoftmax = appliedSoftmax,
                    topPredictions = top
                )
            )
        } catch (t: Throwable) {
            Log.e(DBG, "classify failed", t)
            TomatoDiseaseClassifyResult.Failed(t.javaClass.simpleName)
        }
    }

    override fun close() {
        try {
            interpreter.close()
        } catch (_: Throwable) {
        }
    }

    private fun logModelIdentity() {
        Log.i(
            DBG,
            "model load inputShape=${inputTensor.shape().contentToString()} " +
                "inputType=${inputTensor.dataType()} " +
                "layout=${if (inputSpec.nchw) "NCHW" else "NHWC"} " +
                "hxw=${inputSpec.height}x${inputSpec.width} " +
                "outputShape=${outputTensor.shape().contentToString()} " +
                "outputType=${outputTensor.dataType()} " +
                "outputBytes=${outputTensor.numBytes()} " +
                "labels=${labels.size} ${labels.mapIndexed { i, n -> "$i=$n" }}"
        )
        Log.i(
            DBG,
            "classMapCheck index3=${labelOrMissing(3)} expected=Leaf_Mold " +
                "index9=${labelOrMissing(9)} expected=healthy"
        )
    }

    private fun labelOrMissing(index: Int): String =
        TomatoDiseaseLabels.labelAt(index, labels)

    private fun fillInput(frame: HarvestArgbFrame) {
        inputBuffer.rewind()
        val pixels = frame.argb
        val count = inputSpec.width * inputSpec.height
        if (inputSpec.nchw) {
            if (inputIsQuantized) {
                for (i in 0 until count) inputBuffer.put(((pixels[i] shr 16) and 0xFF).toByte())
                for (i in 0 until count) inputBuffer.put(((pixels[i] shr 8) and 0xFF).toByte())
                for (i in 0 until count) inputBuffer.put((pixels[i] and 0xFF).toByte())
            } else {
                for (i in 0 until count) {
                    inputBuffer.putFloat(((pixels[i] shr 16) and 0xFF) / 255f)
                }
                for (i in 0 until count) {
                    inputBuffer.putFloat(((pixels[i] shr 8) and 0xFF) / 255f)
                }
                for (i in 0 until count) {
                    inputBuffer.putFloat((pixels[i] and 0xFF) / 255f)
                }
            }
        } else {
            if (inputIsQuantized) {
                for (pixel in pixels) {
                    inputBuffer.put(((pixel shr 16) and 0xFF).toByte())
                    inputBuffer.put(((pixel shr 8) and 0xFF).toByte())
                    inputBuffer.put((pixel and 0xFF).toByte())
                }
            } else {
                for (pixel in pixels) {
                    inputBuffer.putFloat(((pixel shr 16) and 0xFF) / 255f)
                    inputBuffer.putFloat(((pixel shr 8) and 0xFF) / 255f)
                    inputBuffer.putFloat((pixel and 0xFF) / 255f)
                }
            }
        }
        inputBuffer.rewind()
    }

    private fun logInputBufferRange(frame: HarvestArgbFrame) {
        val dup = inputBuffer.duplicate().order(inputBuffer.order())
        dup.rewind()
        var min = Float.POSITIVE_INFINITY
        var max = Float.NEGATIVE_INFINITY
        var count = 0
        val plane = inputSpec.width * inputSpec.height
        var r0 = Float.NaN
        var g0 = Float.NaN
        var b0 = Float.NaN
        if (inputIsQuantized) {
            while (dup.hasRemaining()) {
                val v = (dup.get().toInt() and 0xFF).toFloat()
                if (v < min) min = v
                if (v > max) max = v
                count++
            }
        } else {
            while (dup.hasRemaining() && dup.remaining() >= 4) {
                val v = dup.float
                if (v < min) min = v
                if (v > max) max = v
                count++
            }
        }
        dup.rewind()
        if (!inputIsQuantized && dup.remaining() >= 12) {
            if (inputSpec.nchw && dup.remaining() >= 4 * (2 * plane + 1)) {
                r0 = dup.float
                dup.position(4 * plane)
                g0 = dup.float
                dup.position(4 * (2 * plane))
                b0 = dup.float
            } else if (!inputSpec.nchw) {
                r0 = dup.float
                g0 = dup.float
                b0 = dup.float
            }
        }
        val px = frame.argb.first()
        val expectR = ((px shr 16) and 0xFF) / 255f
        val expectG = ((px shr 8) and 0xFF) / 255f
        val expectB = (px and 0xFF) / 255f
        Log.i(
            DBG,
            "inputBuffer n=$count min=$min max=$max " +
                "expected=${if (inputIsQuantized) "0..255" else "0.0..1.0"} " +
                "writtenFirstRGB=$r0,$g0,$b0 expectFirstRGB=$expectR,$expectG,$expectB " +
                "rgbMatch=${r0 == expectR && g0 == expectG && b0 == expectB}"
        )
    }

    private fun persistDebugFrames(
        hybridCrop: HarvestArgbFrame,
        focused: HarvestArgbFrame?,
        sized: HarvestArgbFrame?
    ) {
        try {
            val cropFile = File(appContext.cacheDir, CROP_PNG)
            writePng(hybridCrop, cropFile)
            HarvestMeasurementStore.lastDiseaseDebugCropPath = cropFile.absolutePath
            Log.i(DBG, "debugHybridCropPng=${cropFile.absolutePath}")
            if (focused != null) {
                val focusedFile = File(appContext.cacheDir, FOCUSED_PNG)
                writePng(focused, focusedFile)
                HarvestMeasurementStore.lastDiseaseDebugFocusedPath = focusedFile.absolutePath
                Log.i(DBG, "debugFocusedRoiPng=${focusedFile.absolutePath}")
            }
            if (sized != null) {
                val inputFile = File(appContext.cacheDir, INPUT_PNG)
                writePng(sized, inputFile)
                HarvestMeasurementStore.lastDiseaseDebugInputPath = inputFile.absolutePath
                Log.i(DBG, "debugInput224Png=${inputFile.absolutePath}")
            }
        } catch (t: Throwable) {
            Log.w(DBG, "debug PNG save failed", t)
        }
    }

    private fun writePng(frame: HarvestArgbFrame, file: File) {
        val bitmap = Bitmap.createBitmap(frame.width, frame.height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(frame.argb, 0, frame.width, 0, 0, frame.width, frame.height)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bitmap.recycle()
    }

    private fun readOutputScores(): FloatArray {
        outputBuffer.rewind()
        val count = classCount(outputTensor.shape())
        val out = FloatArray(count)
        if (outputIsQuantized) {
            val q = outputTensor.quantizationParams()
            val scale = if (q.scale == 0f) 1f else q.scale
            val zero = q.zeroPoint
            for (i in 0 until count) {
                val v = outputBuffer.get().toInt() and 0xFF
                out[i] = (v - zero) * scale
            }
        } else {
            val dup = outputBuffer.order(ByteOrder.nativeOrder())
            for (i in 0 until count) {
                if (!dup.hasRemaining()) break
                out[i] = dup.float
            }
        }
        return out
    }

    private data class InputSpec(
        val nchw: Boolean,
        val height: Int,
        val width: Int,
        val channels: Int
    ) {
        companion object {
            fun from(shape: IntArray): InputSpec {
                require(shape.size == 4) { "Expected 4-D input, got ${shape.contentToString()}" }
                return when {
                    shape[3] == 3 -> InputSpec(
                        nchw = false,
                        height = shape[1],
                        width = shape[2],
                        channels = 3
                    )
                    shape[1] == 3 -> InputSpec(
                        nchw = true,
                        height = shape[2],
                        width = shape[3],
                        channels = 3
                    )
                    else -> InputSpec(
                        nchw = false,
                        height = shape[1],
                        width = shape[2],
                        channels = shape.getOrElse(3) { 3 }
                    )
                }
            }
        }
    }

    companion object {
        const val TAG = TomatoDiseaseDebug.TAG
        private const val DBG = TomatoDiseaseDebug.TAG
        const val MODEL_ASSET = "models/tomato_disease_classifier.tflite"
        const val LABELS_ASSET = "models/tomato_disease_labels.txt"
        const val CROP_PNG = "tomato_disease_debug_crop.png"
        const val FOCUSED_PNG = "tomato_disease_debug_focused.png"
        const val INPUT_PNG = "tomato_disease_debug_224.png"
        private const val TOP_K = 3

        fun tryOpen(
            context: Context,
            calibration: TomatoDiseaseClassificationCalibration =
                TomatoDiseaseClassificationCalibration.PROJECT
        ): TomatoDiseaseClassifier? {
            return try {
                val app = context.applicationContext
                val model = loadMappedAsset(app, MODEL_ASSET)
                val options = Interpreter.Options().setNumThreads(2)
                val interpreter = Interpreter(model, options)
                val labels = loadLabels(app)
                TomatoDiseaseClassifier(app, interpreter, labels, calibration)
            } catch (t: Throwable) {
                Log.e(DBG, "Disease classifier not available", t)
                null
            }
        }

        private fun loadLabels(context: Context): List<String> {
            return try {
                context.assets.open(LABELS_ASSET).bufferedReader().use { reader ->
                    reader.lineSequence()
                        .map { it.trim() }
                        .filter { it.isNotEmpty() && !it.startsWith("#") }
                        .toList()
                }.ifEmpty { TomatoDiseaseLabels.DEFAULT_ORDER }
            } catch (_: Throwable) {
                TomatoDiseaseLabels.DEFAULT_ORDER
            }
        }

        private fun loadMappedAsset(context: Context, path: String): MappedByteBuffer {
            val fd = context.assets.openFd(path)
            FileInputStream(fd.fileDescriptor).use { input ->
                return input.channel.map(
                    FileChannel.MapMode.READ_ONLY,
                    fd.startOffset,
                    fd.declaredLength
                )
            }
        }

        private fun classCount(shape: IntArray): Int {
            if (shape.isEmpty()) return TomatoDiseaseClassificationCalibration.EXPECTED_CLASS_COUNT
            return shape.last().coerceAtLeast(1)
        }
    }
}
