package com.greenhands.app.decision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.locks.ReentrantLock
import kotlin.math.max
import kotlin.math.min

data class DetectedInfection(
    val label: String,
    val score: Float,
    val box: RectF
)

enum class PlantTargetKind {
    None,
    Leaf,
    Fruit
}

data class PlantTargetVerification(
    val kind: PlantTargetKind,
    val confidence: Float,
    val coverage: Float,
    val box: RectF = RectF()
) {
    val targetFound: Boolean
        get() = kind != PlantTargetKind.None
}

data class FrameAnalysis(
    val target: PlantTargetVerification,
    val infections: List<DetectedInfection>
)

/**
 * Two-stage on-device pipeline:
 * 1) A MobileNet embedding plus a learned head decides Leaf, Fruit, or Other.
 * 2) Only a confirmed leaf or fruit is passed to the infection head.
 *
 * Color masks only propose a crop and a size check. They never name a disease.
 * Low-confidence and unstable frames produce no infection.
 */
class InfectionDetector(context: Context) : AutoCloseable {
    private val thresholds = DetectionThresholds.load(context)
    private val infectionLabels = readLabels(context, INFECTION_LABELS_ASSET)
    private val targetLabels = readLabels(context, TARGET_LABELS_ASSET)
    private val featureInterpreter = openInterpreter(context, FEATURE_ASSET)
    private val targetInterpreter = openInterpreter(context, TARGET_ASSET)
    private val infectionInterpreter = openInterpreter(context, INFECTION_ASSET)
    private val featureSize = featureInterpreter?.getInputTensor(0)?.shape()?.getOrNull(1) ?: thresholds.featureSize
    private val featureOutputCount = featureInterpreter?.let { outputElementCount(it) } ?: 0
    private val spatialMap = featureOutputCount == SPATIAL_COUNT
    private val embeddingSize = if (featureOutputCount == SPATIAL_COUNT || featureOutputCount == EMBEDDING) {
        EMBEDDING
    } else {
        0
    }
    private val camWeights = CamWeights.load(context, CAM_ASSET)
    private val targetCamWeights = CamWeights.load(context, TARGET_CAM_ASSET)
    private val stabilizer = PredictionStabilizer()
    private val grayScratch = FloatArray(QUALITY_SIZE * QUALITY_SIZE)
    private val networkPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private var analysisPixels: IntArray? = null
    private var networkBitmap: Bitmap? = null
    private var networkCanvas: Canvas? = null
    private var networkPixels: IntArray? = null
    private var inputBuffer: ByteBuffer? = null
    private var embeddingBuffer: ByteBuffer? = null
    private var embeddingValues: FloatArray? = null
    private var spatialValues: FloatArray? = null
    private var headInput: ByteBuffer? = null
    private var targetScoreBuffer: Array<FloatArray>? = null
    private var infectionScoreBuffer: Array<FloatArray>? = null
    private var heldTarget: PlantTargetVerification? = null
    private var heldBox: RectF = RectF()
    private var heldLesion: RectF = RectF()
    private var uncertainTargetFrames = 0
    private val inferenceLock = ReentrantLock()
    @Volatile private var closed = false

    /** Clears a locked or in-progress scan so the next frame starts clean. */
    fun reset() {
        inferenceLock.lock()
        try {
            if (!closed) clearHold()
        } finally {
            inferenceLock.unlock()
        }
    }

    fun analyze(frame: Bitmap): FrameAnalysis {
        if (closed || frame.isRecycled || frame.width < 8 || frame.height < 8) return emptyAnalysis()
        inferenceLock.lock()
        try {
            if (closed) return emptyAnalysis()
            if (featureInterpreter == null || targetInterpreter == null || infectionInterpreter == null) {
                clearHold()
                return emptyAnalysis()
            }
            if (embeddingSize <= 0 || inputElementCount(targetInterpreter) != embeddingSize) {
                clearHold()
                return emptyAnalysis()
            }
            val inputShape = featureInterpreter.getInputTensor(0).shape()
            val inputBytes = featureInterpreter.getInputTensor(0).numBytes()
            if (inputShape.size != 4 || inputShape[1] != featureSize || inputShape[2] != featureSize || inputShape[3] != 3) {
                return emptyAnalysis()
            }
            if (inputBuffer != null && inputBuffer!!.capacity() != inputBytes && inputBytes > 0) {
                inputBuffer = null
            }
            val working = scaleForAnalysis(frame)
            return try {
                analyzeWorking(frame, working)
            } catch (error: IllegalArgumentException) {
                clearHold()
                emptyAnalysis()
            } catch (error: IllegalStateException) {
                clearHold()
                emptyAnalysis()
            } finally {
                if (working !== frame && !working.isRecycled) working.recycle()
            }
        } finally {
            inferenceLock.unlock()
        }
    }

    private fun analyzeWorking(source: Bitmap, working: Bitmap): FrameAnalysis {
        val width = working.width
        val height = working.height
        if (width < 8 || height < 8) return holdOrReject()
        val pixels = ensureAnalysisPixels(width, height)
        working.getPixels(pixels, 0, width, 0, 0, width, height)
        downsampleGray(pixels, width, height, 0, 0, width, height, grayScratch)
        val brightness = grayScratch.average().toFloat()
        val sharpness = laplacianVariance(grayScratch)
        if (brightness !in thresholds.minBrightness..thresholds.maxBrightness ||
            sharpness < thresholds.minSharpnessTarget
        ) {
            return holdOrReject()
        }

        val cropBox = SUBJECT_CROP
        val cropLeft = (cropBox.left * width).toInt().coerceIn(0, width - 1)
        val cropTop = (cropBox.top * height).toInt().coerceIn(0, height - 1)
        val cropRight = (cropBox.right * width).toInt().coerceIn(cropLeft + 1, width)
        val cropBottom = (cropBox.bottom * height).toInt().coerceIn(cropTop + 1, height)
        downsampleGray(
            pixels,
            width,
            height,
            cropLeft,
            cropTop,
            cropRight - cropLeft,
            cropBottom - cropTop,
            grayScratch
        )
        val cropTexture = textureFill(grayScratch, thresholds.textureGradient)
        if (cropTexture < thresholds.minTextureFill) return holdOrReject()

        val embedding = embedRegion(source, cropBox) ?: return holdOrReject()
        val targetScores = runHead(
            targetInterpreter!!,
            embedding,
            targetScoreBuffer ?: Array(1) { FloatArray(targetLabels.size) }.also { targetScoreBuffer = it }
        ) ?: return holdOrReject()
        val targetPrediction = InfectionConfidenceGate.accept(
            scores = targetScores,
            labels = targetLabels,
            minTop1 = thresholds.targetMinConfidence,
            minMargin = thresholds.targetMinMargin
        )
        val kind = kindOf(targetPrediction?.label)
        if (kind == PlantTargetKind.None && targetPrediction != null) {
            clearHold()
            return emptyAnalysis(max(targetScores.maxOrNull() ?: 0f, 0f))
        }
        if (kind == PlantTargetKind.None) {
            return holdOrReject()
        }

        val objectBox = targetObjectBox(cropBox, kind)
        val verified = PlantTargetVerification(
            kind = kind,
            confidence = targetPrediction?.score ?: 0f,
            coverage = objectBox.width() * objectBox.height(),
            box = RectF(objectBox)
        )
        heldTarget = verified
        heldBox = RectF(objectBox)
        uncertainTargetFrames = 0

        val scores = runHead(
            infectionInterpreter!!,
            embedding,
            infectionScoreBuffer ?: Array(1) { FloatArray(infectionLabels.size) }.also { infectionScoreBuffer = it }
        )
        val gated = scores?.let {
            InfectionConfidenceGate.accept(
                scores = it,
                labels = infectionLabels,
                minTop1 = thresholds.infectionMinConfidence,
                minMargin = thresholds.infectionMinMargin
            )
        }
        val infection = when {
            gated == null -> stabilizer.observe(kind.name, null)
            gated.label.equals(UNKNOWN_LABEL, ignoreCase = true) -> {
                stabilizer.invalidate()
                null
            }
            else -> stabilizer.observe(kind.name, gated)
        }
        val lesion = if (infection == null) RectF() else lesionBox(cropBox, infection.label)
        heldLesion = RectF(lesion)
        val detections = if (infection == null) {
            emptyList()
        } else {
            listOf(
                DetectedInfection(
                    label = infection.label,
                    score = infection.score,
                    box = RectF(lesion)
                )
            )
        }
        return FrameAnalysis(target = verified, infections = detections)
    }

    private fun holdOrReject(): FrameAnalysis {
        val held = heldTarget
        if (held != null) {
            uncertainTargetFrames++
            if (uncertainTargetFrames >= 2) {
                clearHold()
            } else {
                stabilizer.observe(held.kind.name, null)
            }
        }
        return emptyAnalysis()
    }

    private fun clearHold() {
        heldTarget = null
        heldBox = RectF()
        heldLesion = RectF()
        uncertainTargetFrames = 0
        stabilizer.reset()
    }


    private fun embedRegion(source: Bitmap, box: RectF): FloatArray? {
        val interpreter = featureInterpreter ?: return null
        if (embeddingSize != EMBEDDING || featureOutputCount <= 0) return null
        val input = fillInput(source, box)
        val output = embeddingBuffer ?: ByteBuffer.allocateDirect(featureOutputCount * 4)
            .order(ByteOrder.nativeOrder())
            .also { embeddingBuffer = it }
        output.clear()
        if (input.capacity() != interpreter.getInputTensor(0).numBytes()) return null
        if (output.capacity() != interpreter.getOutputTensor(0).numBytes()) return null
        interpreter.run(input, output)
        output.rewind()
        val embedding = embeddingValues ?: FloatArray(EMBEDDING).also { embeddingValues = it }
        if (spatialMap) {
            val spatial = spatialValues ?: FloatArray(SPATIAL_COUNT).also { spatialValues = it }
            output.asFloatBuffer().get(spatial)
            poolSpatial(spatial, embedding)
        } else {
            spatialValues = null
            output.asFloatBuffer().get(embedding)
        }
        return embedding
    }

    /**
     * Where the target head attends inside the subject crop. Color is not used.
     * An empty rect means the model confirmed the class but did not localize an object.
     */
    private fun targetObjectBox(crop: RectF, kind: PlantTargetKind): RectF {
        val weights = targetCamWeights ?: return RectF()
        val spatial = spatialValues ?: return RectF()
        val embedding = embeddingValues ?: return RectF()
        if (!spatialMap || spatial.size != SPATIAL_COUNT) return RectF()
        val label = kind.name
        val classIndex = targetLabels.indexOfFirst { it.equals(label, ignoreCase = true) }
        if (classIndex < 0 || classIndex >= weights.classes) return RectF()
        val activation = weights.activation(spatial, embedding, classIndex)
        val norm = targetNormRect(activation, SPATIAL_GRID) ?: return RectF()
        val width = crop.width()
        val height = crop.height()
        val box = RectF(
            crop.left + norm.left * width,
            crop.top + norm.top * height,
            crop.left + norm.right * width,
            crop.top + norm.bottom * height
        )
        if (box.width() >= 0.96f && box.height() >= 0.96f) return RectF()
        return box
    }

    /**
     * Class activation on the 7x7 map, using the infection head's gradient for the
     * predicted class. Color is not used. A map that lights the whole crop is not a lesion.
     */
    private fun lesionBox(crop: RectF, label: String): RectF {
        if (label.equals("Healthy", ignoreCase = true) || label.equals(UNKNOWN_LABEL, ignoreCase = true)) {
            return RectF()
        }
        val weights = camWeights ?: return RectF()
        val spatial = spatialValues ?: return RectF()
        val embedding = embeddingValues ?: return RectF()
        if (!spatialMap || spatial.size != SPATIAL_COUNT) return RectF()
        val classIndex = infectionLabels.indexOfFirst { it.equals(label, ignoreCase = true) }
        if (classIndex < 0 || classIndex >= weights.classes) return RectF()
        val activation = weights.activation(spatial, embedding, classIndex)
        val norm = lesionNormRect(activation, SPATIAL_GRID) ?: return RectF()
        val width = crop.width()
        val height = crop.height()
        return RectF(
            crop.left + norm.left * width,
            crop.top + norm.top * height,
            crop.left + norm.right * width,
            crop.top + norm.bottom * height
        )
    }

    private fun poolSpatial(spatial: FloatArray, embedding: FloatArray) {
        val cells = SPATIAL_GRID * SPATIAL_GRID
        for (channel in 0 until EMBEDDING) {
            var sum = 0f
            var offset = channel
            var cell = 0
            while (cell < cells) {
                sum += spatial[offset]
                offset += EMBEDDING
                cell++
            }
            embedding[channel] = sum / cells
        }
    }

    private fun runHead(
        interpreter: Interpreter,
        embedding: FloatArray,
        scores: Array<FloatArray>
    ): FloatArray? {
        val labelCount = scores.firstOrNull()?.size ?: return null
        if (labelCount <= 0 || outputElementCount(interpreter) != labelCount) return null
        val input = headInput ?: ByteBuffer.allocateDirect(embedding.size * 4)
            .order(ByteOrder.nativeOrder())
            .also { headInput = it }
        if (input.capacity() < embedding.size * 4) return null
        if (interpreter.getInputTensor(0).numBytes() != embedding.size * 4) return null
        if (interpreter.getOutputTensor(0).numBytes() != labelCount * 4) return null
        input.clear()
        for (value in embedding) input.putFloat(value)
        input.rewind()
        interpreter.run(input, scores)
        return scores[0]
    }

    private fun fillInput(source: Bitmap, box: RectF): ByteBuffer {
        val size = featureSize
        val bitmap = networkBitmap ?: Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also {
            networkBitmap = it
            networkCanvas = Canvas(it)
        }
        val canvas = networkCanvas ?: Canvas(bitmap).also { networkCanvas = it }
        val left = (box.left * source.width).toInt().coerceIn(0, (source.width - 1).coerceAtLeast(0))
        val top = (box.top * source.height).toInt().coerceIn(0, (source.height - 1).coerceAtLeast(0))
        val right = (box.right * source.width).toInt().coerceIn(left + 1, source.width)
        val bottom = (box.bottom * source.height).toInt().coerceIn(top + 1, source.height)
        canvas.drawBitmap(
            source,
            Rect(left, top, right, bottom),
            Rect(0, 0, size, size),
            networkPaint
        )
        val pixels = networkPixels ?: IntArray(size * size).also { networkPixels = it }
        bitmap.getPixels(pixels, 0, size, 0, 0, size, size)
        val buffer = inputBuffer ?: ByteBuffer.allocateDirect(size * size * 12)
            .order(ByteOrder.nativeOrder())
            .also { inputBuffer = it }
        buffer.clear()
        val divisor = thresholds.inputDivisor
        for (pixel in pixels) {
            buffer.putFloat((pixel shr 16 and 0xFF) / divisor - 1f)
            buffer.putFloat((pixel shr 8 and 0xFF) / divisor - 1f)
            buffer.putFloat((pixel and 0xFF) / divisor - 1f)
        }
        buffer.rewind()
        return buffer
    }

    private fun ensureAnalysisPixels(width: Int, height: Int): IntArray {
        val needed = width * height
        val current = analysisPixels
        if (current != null && current.size == needed) return current
        return IntArray(needed).also { analysisPixels = it }
    }

    private fun kindOf(label: String?): PlantTargetKind = when (label?.lowercase()) {
        "leaf" -> PlantTargetKind.Leaf
        "fruit" -> PlantTargetKind.Fruit
        else -> PlantTargetKind.None
    }

    private fun scaleForAnalysis(src: Bitmap): Bitmap {
        if (src.width <= ANALYSIS_WIDTH) return src
        val targetHeight = (src.height.toFloat() / src.width * ANALYSIS_WIDTH).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, ANALYSIS_WIDTH, targetHeight, true)
    }

    override fun close() {
        inferenceLock.lock()
        try {
            if (closed) return
            closed = true
            featureInterpreter?.close()
            targetInterpreter?.close()
            infectionInterpreter?.close()
            networkBitmap?.recycle()
            networkBitmap = null
            networkCanvas = null
        } finally {
            inferenceLock.unlock()
        }
    }

    private data class DetectionThresholds(
        val targetMinConfidence: Float,
        val targetMinMargin: Float,
        val infectionMinConfidence: Float,
        val infectionMinMargin: Float,
        val minLeafCoverage: Float,
        val minFruitCoverage: Float,
        val minFrameFill: Float,
        val minTextureFill: Float,
        val textureGradient: Float,
        val minBrightness: Float,
        val maxBrightness: Float,
        val minSharpnessTarget: Float,
        val minSharpnessInfection: Float,
        val centerMargin: Float,
        val inputDivisor: Float,
        val featureSize: Int
    ) {
        companion object {
            fun load(context: Context): DetectionThresholds {
                val values = mutableMapOf<String, Float>()
                try {
                    context.assets.open(THRESHOLD_ASSET).bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            val trimmed = line.trim()
                            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEach
                            val parts = trimmed.split("=", limit = 2)
                            if (parts.size == 2) {
                                parts[1].trim().toFloatOrNull()?.let { values[parts[0].trim()] = it }
                            }
                        }
                    }
                } catch (_: Exception) {
                    // Conservative defaults reject uncertain frames instead of guessing.
                }
                fun value(key: String, fallback: Float) = values[key] ?: fallback
                return DetectionThresholds(
                    targetMinConfidence = value("target_min_confidence", 0.75f),
                    targetMinMargin = value("target_min_margin", 0.20f),
                    infectionMinConfidence = value("infection_min_confidence", 0.70f),
                    infectionMinMargin = value("infection_min_margin", 0.18f),
                    minLeafCoverage = value("min_leaf_coverage", 0.18f),
                    minFruitCoverage = value("min_fruit_coverage", 0.12f),
                    minFrameFill = value("min_frame_fill", 0.28f),
                    minTextureFill = value("min_texture_fill", 0.12f),
                    textureGradient = value("texture_gradient", 18f),
                    minBrightness = value("min_brightness", 28f),
                    maxBrightness = value("max_brightness", 242f),
                    minSharpnessTarget = value("min_sharpness_target", 12f),
                    minSharpnessInfection = value("min_sharpness_infection", 40f),
                    centerMargin = value("center_margin", 0.18f),
                    inputDivisor = value("input_divisor", 127.5f),
                    featureSize = value("feature_size", 224f).toInt()
                )
            }
        }
    }

    private class CamWeights(
        val classes: Int,
        private val hidden: Int,
        private val features: Int,
        private val w1: FloatArray,
        private val b1: FloatArray,
        private val w2: FloatArray
    ) {
        fun activation(spatial: FloatArray, embedding: FloatArray, classIndex: Int): FloatArray {
            val hiddenValues = FloatArray(hidden)
            for (unit in 0 until hidden) {
                var sum = b1[unit]
                val row = unit * features
                for (feature in 0 until features) {
                    sum += w1[row + feature] * embedding[feature]
                }
                hiddenValues[unit] = if (sum > 0f) sum else 0f
            }
            val gradient = FloatArray(features)
            val classRow = classIndex * hidden
            for (unit in 0 until hidden) {
                if (hiddenValues[unit] <= 0f) continue
                val influence = w2[classRow + unit]
                val row = unit * features
                for (feature in 0 until features) {
                    gradient[feature] += influence * w1[row + feature]
                }
            }
            val cells = spatial.size / features
            val map = FloatArray(cells)
            for (cell in 0 until cells) {
                var sum = 0f
                val base = cell * features
                for (feature in 0 until features) {
                    sum += gradient[feature] * spatial[base + feature]
                }
                map[cell] = if (sum > 0f) sum else 0f
            }
            return map
        }

        companion object {
            fun load(context: Context, asset: String): CamWeights? {
                return try {
                    context.assets.open(asset).use { input ->
                        val buffer = ByteBuffer.wrap(input.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
                        val magic = ByteArray(4)
                        buffer.get(magic)
                        if (!magic.contentEquals(byteArrayOf('C'.code.toByte(), 'A'.code.toByte(), 'M'.code.toByte(), '1'.code.toByte()))) {
                            return null
                        }
                        val hidden = buffer.int
                        val features = buffer.int
                        val classes = buffer.int
                        if (hidden <= 0 || features != EMBEDDING || classes <= 0) return null
                        fun floats(count: Int) = FloatArray(count) { buffer.float }
                        val w1 = floats(hidden * features)
                        val b1 = floats(hidden)
                        val w2 = floats(classes * hidden)
                        floats(classes)
                        CamWeights(classes, hidden, features, w1, b1, w2)
                    }
                } catch (_: Exception) {
                    null
                }
            }
        }
    }

    companion object {
        private const val FEATURE_ASSET = "ml/plant_features.tflite"
        private const val TARGET_ASSET = "ml/plant_target.tflite"
        private const val INFECTION_ASSET = "ml/infection_detector.tflite"
        private const val CAM_ASSET = "ml/infection_cam_weights.bin"
        private const val TARGET_CAM_ASSET = "ml/target_cam_weights.bin"
        private const val EMBEDDING = 1280
        private const val SPATIAL_GRID = 7
        private const val SPATIAL_COUNT = SPATIAL_GRID * SPATIAL_GRID * EMBEDDING
        private const val INFECTION_LABELS_ASSET = "ml/infection_labels.txt"
        private const val TARGET_LABELS_ASSET = "ml/plant_target_labels.txt"
        private const val THRESHOLD_ASSET = "ml/detection_thresholds.txt"
        private const val UNKNOWN_LABEL = "Unknown"
        private const val QUALITY_SIZE = 64
        private const val ANALYSIS_WIDTH = 160
        private val SUBJECT_CROP = RectF(0.16f, 0.14f, 0.84f, 0.86f)

        internal data class NormRect(
            val left: Float,
            val top: Float,
            val right: Float,
            val bottom: Float
        )

        /**
         * Bounding box of the strong class-activation cells, in crop coordinates.
         * Returns null when nothing stands out or the whole map is active.
         */
        internal fun lesionNormRect(activation: FloatArray, grid: Int): NormRect? {
            if (grid < 2 || activation.size != grid * grid) return null
            var peak = 0f
            for (value in activation) {
                if (value > peak) peak = value
            }
            if (peak <= 1e-4f) return null
            val cutoff = peak * 0.55f
            var minX = grid
            var minY = grid
            var maxX = -1
            var maxY = -1
            var count = 0
            for (y in 0 until grid) {
                val row = y * grid
                for (x in 0 until grid) {
                    if (activation[row + x] < cutoff) continue
                    count++
                    if (x < minX) minX = x
                    if (y < minY) minY = y
                    if (x > maxX) maxX = x
                    if (y > maxY) maxY = y
                }
            }
            if (count == 0 || maxX < minX || maxY < minY) return null
            val spanX = maxX - minX + 1
            val spanY = maxY - minY + 1
            if (spanX >= grid && spanY >= grid) return null
            if (count.toFloat() / activation.size > 0.85f) return null
            return NormRect(
                left = minX.toFloat() / grid,
                top = minY.toFloat() / grid,
                right = (maxX + 1).toFloat() / grid,
                bottom = (maxY + 1).toFloat() / grid
            )
        }

        /** Object extent from the target-class activation. A blank map draws nothing. */
        internal fun targetNormRect(activation: FloatArray, grid: Int): NormRect? {
            if (grid < 2 || activation.size != grid * grid) return null
            var peak = 0f
            for (value in activation) {
                if (value > peak) peak = value
            }
            if (peak <= 1e-4f) return null
            val cutoff = peak * 0.40f
            var minX = grid
            var minY = grid
            var maxX = -1
            var maxY = -1
            for (y in 0 until grid) {
                val row = y * grid
                for (x in 0 until grid) {
                    if (activation[row + x] < cutoff) continue
                    if (x < minX) minX = x
                    if (y < minY) minY = y
                    if (x > maxX) maxX = x
                    if (y > maxY) maxY = y
                }
            }
            if (maxX < minX || maxY < minY) return null
            val spanX = maxX - minX + 1
            val spanY = maxY - minY + 1
            if (spanX >= grid && spanY >= grid) return null
            return NormRect(
                left = minX.toFloat() / grid,
                top = minY.toFloat() / grid,
                right = (maxX + 1).toFloat() / grid,
                bottom = (maxY + 1).toFloat() / grid
            )
        }

        fun isVegetationPixel(hsv: FloatArray, r: Int, g: Int, b: Int): Boolean {
            val hue = hsv[0]
            val sat = hsv[1]
            val value = hsv[2]
            if (value < 0.10f || value > 0.97f) return false
            val greenDominant = g >= r - 10 && g > b
            val leafGreen = hue in 55f..165f && sat >= 0.16f && greenDominant
            val chlorotic = hue in 35f..75f && sat >= 0.28f && g >= b && value in 0.22f..0.92f
            val brownLeaf = hue in 18f..45f && sat >= 0.22f && value in 0.16f..0.78f && g > b
            val notSkin = !(hue in 8f..28f && sat > 0.28f && r > g + 20 && r > b)
            val notSky = hue !in 185f..250f
            return (leafGreen || chlorotic || brownLeaf) && notSkin && notSky
        }

        fun isFruitPixel(hsv: FloatArray, r: Int, g: Int, b: Int): Boolean {
            val hue = hsv[0]
            val sat = hsv[1]
            val value = hsv[2]
            if (value < 0.18f || value > 0.96f || sat < 0.38f) return false
            val skinLike = hue in 10f..26f && sat in 0.22f..0.48f && r - g < 45 && g > b
            if (skinLike) return false
            val vividRed = (hue <= 12f || hue >= 348f) && sat >= 0.42f && r > g + 25 && r > b + 30
            val orange = hue in 12f..34f && sat >= 0.48f && r > b + 20 && r >= g
            val yellowFruit = hue in 38f..58f && sat >= 0.50f && r > 90 && g > 80 && b < g - 15
            return vividRed || orange || yellowFruit
        }

        private fun readLabels(context: Context, asset: String): List<String> = try {
            context.assets.open(asset).bufferedReader().readLines().map { it.trim() }.filter { it.isNotEmpty() }
        } catch (_: Exception) {
            emptyList()
        }

        private fun openInterpreter(context: Context, asset: String): Interpreter? = try {
            Interpreter(
                loadModel(context, asset),
                Interpreter.Options().apply {
                    setNumThreads(2)
                    setUseXNNPACK(false)
                }
            )
        } catch (_: Exception) {
            null
        }

        private fun loadModel(context: Context, assetPath: String): MappedByteBuffer {
            val descriptor = context.assets.openFd(assetPath)
            FileInputStream(descriptor.fileDescriptor).use { input ->
                return input.channel.map(
                    FileChannel.MapMode.READ_ONLY,
                    descriptor.startOffset,
                    descriptor.declaredLength
                )
            }
        }

        private fun outputElementCount(interpreter: Interpreter): Int =
            interpreter.getOutputTensor(0).shape().fold(1) { product, dim -> product * dim }

        private fun inputElementCount(interpreter: Interpreter): Int =
            interpreter.getInputTensor(0).shape().fold(1) { product, dim -> product * dim }

        /**
         * Box-average grayscale. The training script uses the same 64x64 reduction
         * when it chooses brightness, blur, and texture thresholds.
         */
        private fun downsampleGray(
            pixels: IntArray,
            stride: Int,
            imageHeight: Int,
            originX: Int,
            originY: Int,
            regionWidth: Int,
            regionHeight: Int,
            out: FloatArray
        ) {
            val size = QUALITY_SIZE
            val maxX = (originX + regionWidth).coerceAtMost(stride)
            val maxY = (originY + regionHeight).coerceAtMost(imageHeight)
            val width = (maxX - originX).coerceAtLeast(1)
            val height = (maxY - originY).coerceAtLeast(1)
            for (y in 0 until size) {
                val y0 = originY + y * height / size
                val y1 = max(y0 + 1, originY + (y + 1) * height / size).coerceAtMost(maxY)
                for (x in 0 until size) {
                    val x0 = originX + x * width / size
                    val x1 = max(x0 + 1, originX + (x + 1) * width / size).coerceAtMost(maxX)
                    var sum = 0f
                    var count = 0
                    for (yy in y0 until y1) {
                        var offset = yy * stride + x0
                        for (xx in x0 until x1) {
                            val pixel = pixels[offset]
                            val red = pixel shr 16 and 0xFF
                            val green = pixel shr 8 and 0xFF
                            val blue = pixel and 0xFF
                            sum += (red + green + blue) / 3f
                            count++
                            offset++
                        }
                    }
                    out[y * size + x] = if (count == 0) 0f else sum / count
                }
            }
        }

        private fun laplacianVariance(gray: FloatArray): Float {
            val size = QUALITY_SIZE
            var count = 0
            var mean = 0f
            var second = 0f
            for (y in 1 until size - 1) {
                for (x in 1 until size - 1) {
                    val center = gray[y * size + x]
                    val lap = gray[(y - 1) * size + x] + gray[(y + 1) * size + x] +
                        gray[y * size + (x - 1)] + gray[y * size + (x + 1)] - 4f * center
                    count++
                    val delta = lap - mean
                    mean += delta / count
                    second += delta * (lap - mean)
                }
            }
            return if (count <= 1) 0f else second / count
        }

        private fun textureFill(gray: FloatArray, gradient: Float): Float {
            val size = QUALITY_SIZE
            var strong = 0
            var count = 0
            for (y in 1 until size - 1) {
                for (x in 1 until size - 1) {
                    val dx = kotlin.math.abs(gray[y * size + (x + 1)] - gray[y * size + (x - 1)])
                    val dy = kotlin.math.abs(gray[(y + 1) * size + x] - gray[(y - 1) * size + x])
                    if (dx + dy >= gradient) strong++
                    count++
                }
            }
            return if (count == 0) 0f else strong.toFloat() / count
        }

        private fun emptyAnalysis(confidence: Float = 0f) = FrameAnalysis(
            target = PlantTargetVerification(PlantTargetKind.None, confidence, 0f),
            infections = emptyList()
        )
    }
}
