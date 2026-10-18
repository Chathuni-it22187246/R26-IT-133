package com.greenhands.app.harvest.detection

import android.content.Context
import android.util.Log
import com.greenhands.app.harvest.domain.HarvestArgbFrame
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.Tensor
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * EXPERIMENTAL / optional TensorFlow Lite interpreter for the custom tomato
 * fruit/leaf detector. Not the primary scan path.
 *
 * Normal Fruit Scan / Leaf Scan use [HybridTargetValidator] and must work
 * without this model. Enable only via [HybridScanConfig.USE_EXPERIMENTAL_TFLITE_DETECTOR].
 *
 * This project's trained YOLOv8n export:
 * - input [1, 416, 416, 3] FLOAT32 RGB / 255 (NHWC) unless the tensor is NCHW
 * - output [1, 6, 3549] channels-first: cx, cy, w, h, tomato_fruit, tomato_leaf
 */
class TfliteTargetDetector(
    private val interpreter: Interpreter,
    private val labels: List<String>,
    private val calibration: TargetDetectionCalibration = TargetDetectionCalibration.PROJECT
) : TargetDetector {

    private val inputTensor: Tensor = interpreter.getInputTensor(0)
    private val outputTensor: Tensor = interpreter.getOutputTensor(0)
    private val inputSpec: InputSpec
    private val inputIsQuantized: Boolean = inputTensor.dataType() == DataType.UINT8
    private val inputBuffer: ByteBuffer
    private val outputBuffer: ByteBuffer
    private val outputLayout: OutputLayout
    private val outputChannels: Int
    private val outputPredictions: Int

    override val isModelReady: Boolean = true

    init {
        inputSpec = InputSpec.from(inputTensor.shape())
        val inputBytesPerChannel = if (inputIsQuantized) 1 else 4
        inputBuffer = ByteBuffer.allocateDirect(
            1 * inputSpec.channels * inputSpec.height * inputSpec.width * inputBytesPerChannel
        ).order(ByteOrder.nativeOrder())
        outputBuffer = ByteBuffer.allocateDirect(outputTensor.numBytes().coerceAtLeast(4))
            .order(ByteOrder.nativeOrder())
        outputLayout = OutputLayout.from(interpreter)
        val outShape = outputTensor.shape()
        if (outShape.size == 3) {
            val dim1 = outShape[1]
            val dim2 = outShape[2]
            if (dim1 <= 16 && dim2 > dim1) {
                outputChannels = dim1
                outputPredictions = dim2
            } else {
                outputChannels = dim2
                outputPredictions = dim1
            }
        } else {
            outputChannels = 6
            outputPredictions = 3549
        }
        Log.i(
            TomatoYoloDiagnostics.TAG,
            "model load inputShape=${inputTensor.shape().contentToString()} " +
                "inputType=${inputTensor.dataType()} " +
                "inputLayout=${if (inputSpec.nchw) "NCHW" else "NHWC"} " +
                "inputHxW=${inputSpec.height}x${inputSpec.width} " +
                "outputShape=${outputTensor.shape().contentToString()} " +
                "outputType=${outputTensor.dataType()} " +
                "outputBytes=${outputTensor.numBytes()} " +
                "parsedLayout=${outputLayout.description()} " +
                "quantIn=${inputTensor.quantizationParams().scale}/${inputTensor.quantizationParams().zeroPoint} " +
                "quantOut=${outputTensor.quantizationParams().scale}/${outputTensor.quantizationParams().zeroPoint} " +
                "labels=$labels"
        )
    }

    override fun detect(frame: HarvestArgbFrame, timestampMs: Long): List<TargetDetection> {
        return try {
            when (val layout = outputLayout) {
                is OutputLayout.DetectionApi -> detectDetectionApi(frame, timestampMs, layout)
                is OutputLayout.YoloV8ChannelsFirst,
                is OutputLayout.YoloDetectionsFirst -> detectYolo(frame, timestampMs)
            }
        } catch (t: Throwable) {
            Log.e(TomatoYoloDiagnostics.TAG, "TFLite detect failed", t)
            emptyList()
        }
    }

    override fun close() {
        try {
            interpreter.close()
        } catch (_: Throwable) {
        }
    }

    private fun detectYolo(frame: HarvestArgbFrame, timestampMs: Long): List<TargetDetection> {
        val letterboxed = YoloLetterbox.fit(frame, inputSpec.width, inputSpec.height)
        fillInput(letterboxed.frame)
        val logThis = TomatoYoloDiagnostics.shouldLog()
        if (logThis) {
            val (inMin, inMax) = inputBufferMinMax()
            Log.i(
                TomatoYoloDiagnostics.TAG,
                "preprocess letterbox src=${frame.width}x${frame.height} " +
                    "dst=${inputSpec.width}x${inputSpec.height} " +
                    "gain=${letterboxed.transform.gain} " +
                    "padX=${letterboxed.transform.padX} padY=${letterboxed.transform.padY} " +
                    "inputMin=$inMin inputMax=$inMax " +
                    "expectedRange=${if (inputIsQuantized) "uint8 0..255 RGB" else "float32 0..1 RGB"} " +
                    "channelOrder=RGB layout=${if (inputSpec.nchw) "NCHW" else "NHWC"}"
            )
        }
        outputBuffer.rewind()
        interpreter.run(inputBuffer, outputBuffer)
        val floats = readOutputFloats()
        val channelMajor = YoloV8OutputParser.channelsFromFlat(
            floats, outputChannels, outputPredictions, channelMajor = true
        )
        val predMajor = YoloV8OutputParser.channelsFromFlat(
            floats, outputChannels, outputPredictions, channelMajor = false
        )
        val ch0 = YoloV8OutputParser.maxClassScore(channelMajor, 0)
        val ch1 = YoloV8OutputParser.maxClassScore(channelMajor, 1)
        val pr0 = YoloV8OutputParser.maxClassScore(predMajor, 0)
        val pr1 = YoloV8OutputParser.maxClassScore(predMajor, 1)
        val usePredMajor = maxOf(pr0, pr1) > maxOf(ch0, ch1) * 1.5f && maxOf(pr0, pr1) > 0.05f
        val channels = if (usePredMajor) predMajor else channelMajor
        val layoutName = if (usePredMajor) "pred-major [1,N,6]" else "channel-major [1,6,N]"
        val scoreFloor = if (TomatoYoloDiagnostics.USE_DEBUG_PARSE_FLOOR) {
            TomatoYoloDiagnostics.DEBUG_PARSE_FLOOR
        } else {
            calibration.detectorScoreFloor
        }
        val beforeNms = countAboveFloor(channels, scoreFloor)
        val detections = YoloV8OutputParser.parseChannelsFirst(
            channels = channels,
            inputWidth = inputSpec.width,
            inputHeight = inputSpec.height,
            transform = letterboxed.transform,
            calibration = calibration,
            timestampMs = timestampMs,
            labels = labels,
            scoreFloor = scoreFloor
        )
        if (logThis) {
            logYoloDump(
                channels = channels,
                channelMajorMax = ch0 to ch1,
                predMajorMax = pr0 to pr1,
                layoutName = layoutName,
                transform = letterboxed.transform,
                beforeNms = beforeNms,
                afterNms = detections.size,
                detections = detections
            )
        }
        return detections
    }

    private fun detectDetectionApi(
        frame: HarvestArgbFrame,
        timestampMs: Long,
        layout: OutputLayout.DetectionApi
    ): List<TargetDetection> {
        val scaled = ArgbFrameScaler.scale(frame, inputSpec.width, inputSpec.height)
        fillInput(scaled)
        interpreter.runForMultipleInputsOutputs(arrayOf(inputBuffer), layout.outputMap())
        if (TomatoYoloDiagnostics.shouldLog()) {
            Log.w(
                TomatoYoloDiagnostics.TAG,
                "Using DetectionApi path, not YOLOv8 [1,6,N]. output=${outputTensor.shape().contentToString()}"
            )
        }
        return parseDetectionApi(layout, timestampMs)
    }

    private fun logYoloDump(
        channels: Array<FloatArray>,
        channelMajorMax: Pair<Float, Float>,
        predMajorMax: Pair<Float, Float>,
        layoutName: String,
        transform: LetterboxTransform,
        beforeNms: Int,
        afterNms: Int,
        detections: List<TargetDetection>
    ) {
        val min0 = YoloV8OutputParser.minClassScore(channels, 0)
        val min1 = YoloV8OutputParser.minClassScore(channels, 1)
        val max0 = YoloV8OutputParser.maxClassScore(channels, 0)
        val max1 = YoloV8OutputParser.maxClassScore(channels, 1)
        val (boxMin, boxMax) = YoloV8OutputParser.boxRange(channels)
        val logits = TomatoYoloDiagnostics.looksLikeLogits(minOf(min0, min1), maxOf(max0, max1))
        val pixels = TomatoYoloDiagnostics.looksLikePixelBoxes(boxMin, boxMax)
        Log.i(
            TomatoYoloDiagnostics.TAG,
            "raw scores layoutUsed=$layoutName " +
                "channelMajor max0=${channelMajorMax.first} max1=${channelMajorMax.second} " +
                "predMajor max0=${predMajorMax.first} max1=${predMajorMax.second} " +
                "chosen max0=$max0 max1=$max1 min0=$min0 min1=$min1 " +
                "sigmoid(max0)=${TomatoYoloDiagnostics.sigmoid(max0)} " +
                "sigmoid(max1)=${TomatoYoloDiagnostics.sigmoid(max1)} " +
                "classValues=${if (logits) "LOGITS" else "probably probabilities 0..1"} " +
                "boxMin=$boxMin boxMax=$boxMax " +
                "boxUnits=${if (pixels) "pixels" else "normalized-or-small"} " +
                "beforeNms=$beforeNms afterNms=$afterNms " +
                "debugFloor=${TomatoYoloDiagnostics.DEBUG_PARSE_FLOOR} " +
                "captureFloor=${calibration.minDetectionConfidence}"
        )
        YoloV8OutputParser.topPredictions(channels, 5).forEachIndexed { rank, pred ->
            val mapped = YoloV8OutputParser.xywhToSourceNormalized(
                cx = pred.cx,
                cy = pred.cy,
                w = pred.w,
                h = pred.h,
                inputWidth = inputSpec.width,
                inputHeight = inputSpec.height,
                transform = transform
            )
            Log.i(
                TomatoYoloDiagnostics.TAG,
                "top${rank + 1} i=${pred.index} raw xywh=${pred.cx},${pred.cy},${pred.w},${pred.h} " +
                    "class0=${pred.class0} class1=${pred.class1} best=${pred.bestClass}/${pred.bestScore} " +
                    "mappedLTRB=${mapped.left},${mapped.top},${mapped.right},${mapped.bottom}"
            )
        }
        detections.take(3).forEach {
            Log.i(
                TomatoYoloDiagnostics.TAG,
                "kept ${it.targetType} conf=${it.confidence} box=${it.boundingBox}"
            )
        }
    }

    private fun countAboveFloor(channels: Array<FloatArray>, floor: Float): Int {
        val activated = YoloV8OutputParser.activatedClassChannels(channels)
        var count = 0
        val preds = activated[0].size
        for (i in 0 until preds) {
            val s0 = activated.getOrNull(4)?.getOrNull(i) ?: 0f
            val s1 = activated.getOrNull(5)?.getOrNull(i) ?: 0f
            if (maxOf(s0, s1) >= floor) count++
        }
        return count
    }

    private fun readOutputFloats(): FloatArray {
        outputBuffer.rewind()
        return when (outputTensor.dataType()) {
            DataType.FLOAT32 -> {
                val fb = outputBuffer.order(ByteOrder.nativeOrder()).asFloatBuffer()
                val values = FloatArray(fb.remaining())
                fb.get(values)
                values
            }
            DataType.UINT8, DataType.INT8 -> {
                val scale = outputTensor.quantizationParams().scale
                val zero = outputTensor.quantizationParams().zeroPoint
                val bytes = ByteArray(outputBuffer.remaining())
                outputBuffer.get(bytes)
                FloatArray(bytes.size) { i ->
                    val q = if (outputTensor.dataType() == DataType.UINT8) {
                        bytes[i].toInt() and 0xFF
                    } else {
                        bytes[i].toInt()
                    }
                    (q - zero) * scale
                }
            }
            else -> {
                Log.w(
                    TomatoYoloDiagnostics.TAG,
                    "Unhandled output type ${outputTensor.dataType()}; treating as float32"
                )
                val fb = outputBuffer.order(ByteOrder.nativeOrder()).asFloatBuffer()
                val values = FloatArray(fb.remaining())
                fb.get(values)
                values
            }
        }
    }

    private fun fillInput(frame: HarvestArgbFrame) {
        inputBuffer.rewind()
        val pixels = frame.argb
        val width = inputSpec.width
        val height = inputSpec.height
        val count = width * height
        if (inputSpec.nchw) {
            if (inputIsQuantized) {
                for (i in 0 until count) {
                    inputBuffer.put(((pixels[i] shr 16) and 0xFF).toByte())
                }
                for (i in 0 until count) {
                    inputBuffer.put(((pixels[i] shr 8) and 0xFF).toByte())
                }
                for (i in 0 until count) {
                    inputBuffer.put((pixels[i] and 0xFF).toByte())
                }
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

    private fun inputBufferMinMax(): Pair<Float, Float> {
        val dup = inputBuffer.duplicate().order(inputBuffer.order())
        dup.rewind()
        var min = Float.POSITIVE_INFINITY
        var max = Float.NEGATIVE_INFINITY
        if (inputIsQuantized) {
            while (dup.hasRemaining()) {
                val v = (dup.get().toInt() and 0xFF).toFloat()
                if (v < min) min = v
                if (v > max) max = v
            }
        } else {
            val fb = dup.asFloatBuffer()
            while (fb.hasRemaining()) {
                val v = fb.get()
                if (v < min) min = v
                if (v > max) max = v
            }
        }
        return min to max
    }

    private fun parseDetectionApi(
        layout: OutputLayout.DetectionApi,
        timestampMs: Long
    ): List<TargetDetection> {
        val floor = calibration.detectorScoreFloor
        val out = ArrayList<TargetDetection>(8)
        val count = layout.detectedCount().coerceIn(0, layout.maxDetections)
        for (i in 0 until count) {
            val score = layout.scores[0][i]
            if (score < floor) continue
            val type = YoloV8OutputParser.classIndexToType(
                layout.classes[0][i].toInt(),
                labels
            ) ?: continue
            val loc = layout.locations[0][i]
            val box = NormalizedRect.fromMinMax(
                ymin = loc[0],
                xmin = loc[1],
                ymax = loc[2],
                xmax = loc[3]
            )
            if (box.area <= 0f) continue
            out.add(
                TargetDetection(
                    targetType = type,
                    confidence = score,
                    boundingBox = box,
                    timestampMs = timestampMs
                )
            )
        }
        return YoloV8OutputParser.nms(out, calibration.nmsIouThreshold)
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

    private sealed class OutputLayout {
        abstract fun description(): String

        class DetectionApi(
            val maxDetections: Int,
            val locations: Array<Array<FloatArray>>,
            val classes: Array<FloatArray>,
            val scores: Array<FloatArray>,
            val count: FloatArray?,
            private val tensorCount: Int
        ) : OutputLayout() {
            fun detectedCount(): Int = count?.getOrNull(0)?.toInt() ?: maxDetections

            fun outputMap(): Map<Int, Any> {
                val map = HashMap<Int, Any>(tensorCount)
                map[0] = locations
                map[1] = classes
                map[2] = scores
                if (tensorCount >= 4 && count != null) {
                    map[3] = count
                }
                return map
            }

            override fun description(): String = "DetectionApi(n=$maxDetections)"
        }

        class YoloV8ChannelsFirst(
            val channels: Int,
            val predictions: Int
        ) : OutputLayout() {
            override fun description(): String = "YOLOv8 [1,$channels,$predictions]"
        }

        class YoloDetectionsFirst(
            val predictions: Int,
            val attributes: Int
        ) : OutputLayout() {
            override fun description(): String = "YOLO [1,$predictions,$attributes]"
        }

        companion object {
            fun from(interpreter: Interpreter): OutputLayout {
                val outputs = interpreter.outputTensorCount
                if (outputs >= 3) {
                    val locShape = interpreter.getOutputTensor(0).shape()
                    val n = if (locShape.size >= 2) locShape[locShape.size - 2] else 25
                    return DetectionApi(
                        maxDetections = n,
                        locations = Array(1) { Array(n) { FloatArray(4) } },
                        classes = Array(1) { FloatArray(n) },
                        scores = Array(1) { FloatArray(n) },
                        count = if (outputs >= 4) FloatArray(1) else null,
                        tensorCount = outputs
                    )
                }
                val shape = interpreter.getOutputTensor(0).shape()
                require(shape.size == 3 && shape[0] == 1) {
                    "Unsupported YOLO output shape ${shape.contentToString()}"
                }
                val dim1 = shape[1]
                val dim2 = shape[2]
                return if (dim1 <= 16 && dim2 > dim1) {
                    YoloV8ChannelsFirst(channels = dim1, predictions = dim2)
                } else {
                    YoloDetectionsFirst(predictions = dim1, attributes = dim2)
                }
            }
        }
    }

    companion object {
        const val MODEL_ASSET = "models/tomato_target_detector.tflite"
        const val LABELS_ASSET = "models/tomato_target_labels.txt"

        fun tryOpen(
            context: Context,
            calibration: TargetDetectionCalibration = TargetDetectionCalibration.PROJECT
        ): TfliteTargetDetector? {
            return try {
                val model = loadMappedAsset(context, MODEL_ASSET)
                val options = Interpreter.Options().setNumThreads(2)
                val interpreter = Interpreter(model, options)
                val labels = loadLabels(context)
                TfliteTargetDetector(interpreter, labels, calibration)
            } catch (t: Throwable) {
                Log.e(TomatoYoloDiagnostics.TAG, "Tomato target model not available", t)
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
                }
            } catch (_: Throwable) {
                listOf(
                    TargetDetectionLabels.TOMATO_FRUIT,
                    TargetDetectionLabels.TOMATO_LEAF
                )
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
    }
}

object TargetDetectorFactory {
    /**
     * Optional experimental TFLite detector. The primary CameraX path does not
     * call this unless [HybridScanConfig.USE_EXPERIMENTAL_TFLITE_DETECTOR] is true.
     */
    fun createExperimental(context: Context): TargetDetector {
        return TfliteTargetDetector.tryOpen(context) ?: UnavailableTargetDetector
    }

    @Deprecated(
        "TFLite is optional. Use HybridTargetValidator for the primary scan path.",
        ReplaceWith("createExperimental(context)")
    )
    fun create(context: Context): TargetDetector = createExperimental(context)
}
