package com.greenhands.app.decision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.abs
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
    val coverage: Float
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
 * 1) Verify the frame contains a plant leaf or fruit (ignore walls, hands, tools, sky).
 * 2) Only then classify the single highest-priority infection spot.
 */
class InfectionDetector(context: Context) : AutoCloseable {
    private val labels: List<String> = context.assets.open(LABELS_ASSET)
        .bufferedReader()
        .readLines()
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    private val interpreter: Interpreter? = try {
        Interpreter(loadModel(context, MODEL_ASSET), Interpreter.Options().apply {
            setNumThreads(2)
        })
    } catch (_: Exception) {
        null
    }

    fun analyze(frame: Bitmap): FrameAnalysis {
        val working = scaleForAnalysis(frame)
        val target = verifyPlantTarget(working)
        if (!target.targetFound) {
            return FrameAnalysis(target = target, infections = emptyList())
        }
        val vegetationMask = buildVegetationMask(working)
        val tfliteLabel = classifyFrame(working)
        val candidates = findLesionBlobs(working, vegetationMask).mapNotNull { blob ->
            if (!InfectionPriority.isLocalizedSpotBox(blob.box)) return@mapNotNull null
            val label = tfliteLabel?.takeIf { it.score >= 0.35f }?.label ?: blob.label
            val score = max(blob.score, tfliteLabel?.score ?: 0f)
            DetectedInfection(
                label = label,
                score = score.coerceIn(0.35f, 0.99f),
                box = blob.box
            )
        }
        val isolated = listOfNotNull(InfectionPriority.pickHighestPriority(candidates))
        return FrameAnalysis(target = target, infections = isolated)
    }

    private fun verifyPlantTarget(bitmap: Bitmap): PlantTargetVerification {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val leafMask = BooleanArray(pixels.size)
        val fruitMask = BooleanArray(pixels.size)
        val hsv = FloatArray(3)
        var leafCount = 0
        var fruitCount = 0
        var centerLeaf = 0
        var centerFruit = 0
        val x0 = (width * 0.18f).toInt()
        val x1 = (width * 0.82f).toInt()
        val y0 = (height * 0.18f).toInt()
        val y1 = (height * 0.82f).toInt()
        val centerArea = max(1, (x1 - x0) * (y1 - y0))

        for (i in pixels.indices) {
            val pixel = pixels[i]
            Color.colorToHSV(pixel, hsv)
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)
            val leaf = isVegetationPixel(hsv, r, g, b)
            val fruit = isFruitPixel(hsv, r, g, b)
            leafMask[i] = leaf
            fruitMask[i] = fruit && !leaf
            val x = i % width
            val y = i / width
            val inCenter = x in x0 until x1 && y in y0 until y1
            if (leaf) {
                leafCount++
                if (inCenter) centerLeaf++
            }
            if (fruitMask[i]) {
                fruitCount++
                if (inCenter) centerFruit++
            }
        }

        val leafCoverage = leafCount.toFloat() / pixels.size
        val fruitCoverage = fruitCount.toFloat() / pixels.size
        val leafBlob = largestVegetationBlob(leafMask, width, height).toFloat() / pixels.size
        val fruitBlob = largestVegetationBlob(fruitMask, width, height).toFloat() / pixels.size
        val leafCenter = centerLeaf.toFloat() / centerArea
        val fruitCenter = centerFruit.toFloat() / centerArea

        val leafOk = leafCoverage >= MIN_LEAF_COVERAGE &&
            leafBlob >= MIN_LEAF_BLOB &&
            leafCenter >= MIN_CENTER_COVERAGE
        val fruitOk = fruitCoverage >= MIN_FRUIT_COVERAGE &&
            fruitBlob >= MIN_FRUIT_BLOB &&
            fruitCenter >= MIN_FRUIT_CENTER

        val leafConfidence = (leafCoverage * 0.45f + leafBlob * 0.35f + leafCenter * 0.20f).coerceIn(0f, 0.99f)
        val fruitConfidence = (fruitCoverage * 0.40f + fruitBlob * 0.40f + fruitCenter * 0.20f).coerceIn(0f, 0.99f)

        return when {
            fruitOk && (!leafOk || fruitConfidence >= leafConfidence) -> PlantTargetVerification(
                kind = PlantTargetKind.Fruit,
                confidence = max(fruitConfidence, 0.55f),
                coverage = fruitCoverage
            )
            leafOk -> PlantTargetVerification(
                kind = PlantTargetKind.Leaf,
                confidence = max(leafConfidence, 0.55f),
                coverage = leafCoverage
            )
            else -> PlantTargetVerification(
                kind = PlantTargetKind.None,
                confidence = max(leafConfidence, fruitConfidence),
                coverage = max(leafCoverage, fruitCoverage)
            )
        }
    }

    private fun largestVegetationBlob(mask: BooleanArray, width: Int, height: Int): Int {
        val visited = BooleanArray(mask.size)
        var largest = 0
        for (start in mask.indices) {
            if (!mask[start] || visited[start]) continue
            var count = 0
            val stack = ArrayDeque<Int>()
            stack.add(start)
            visited[start] = true
            while (stack.isNotEmpty()) {
                val idx = stack.removeLast()
                count++
                val neighbors = intArrayOf(idx - 1, idx + 1, idx - width, idx + width)
                for (n in neighbors) {
                    if (n < 0 || n >= mask.size || visited[n] || !mask[n]) continue
                    visited[n] = true
                    stack.add(n)
                }
            }
            if (count > largest) largest = count
        }
        return largest
    }

    private fun classifyFrame(bitmap: Bitmap): DetectedInfection? {
        val tflite = interpreter ?: return null
        val input = bitmapToBuffer(bitmap)
        val output = Array(1) { FloatArray(labels.size.coerceAtLeast(1)) }
        return try {
            tflite.run(input, output)
            val scores = output[0]
            val best = scores.indices.maxByOrNull { scores[it] } ?: return null
            DetectedInfection(
                label = labels.getOrElse(best) { "Leaf Spot" },
                score = scores[best],
                box = RectF()
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun buildVegetationMask(bitmap: Bitmap): BooleanArray {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val mask = BooleanArray(pixels.size)
        val hsv = FloatArray(3)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            Color.colorToHSV(pixel, hsv)
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)
            mask[i] = isVegetationPixel(hsv, r, g, b) || isFruitPixel(hsv, r, g, b)
        }
        return mask
    }

    private fun touchesVegetation(index: Int, vegetationMask: BooleanArray, width: Int, height: Int): Boolean {
        if (vegetationMask[index]) return true
        val x = index % width
        val y = index / width
        for (dy in -1..1) {
            for (dx in -1..1) {
                if (dx == 0 && dy == 0) continue
                val nx = x + dx
                val ny = y + dy
                if (nx !in 0 until width || ny !in 0 until height) continue
                val n = ny * width + nx
                if (vegetationMask[n]) return true
            }
        }
        return false
    }

    private fun findLesionBlobs(bitmap: Bitmap, vegetationMask: BooleanArray): List<DetectedInfection> {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val labelsGrid = IntArray(pixels.size)
        val hsv = FloatArray(3)
        for (i in pixels.indices) {
            Color.colorToHSV(pixels[i], hsv)
            labelsGrid[i] = lesionClass(hsv, Color.red(pixels[i]), Color.green(pixels[i]), Color.blue(pixels[i]))
        }

        val visited = BooleanArray(pixels.size)
        val results = mutableListOf<DetectedInfection>()
        val frameArea = width * height
        val minArea = (frameArea * 0.0012f).toInt().coerceAtLeast(12)
        val maxArea = (frameArea * 0.055f).toInt()

        for (start in pixels.indices) {
            if (visited[start] || labelsGrid[start] == HEALTHY) continue
            if (!touchesVegetation(start, vegetationMask, width, height)) continue
            val kind = labelsGrid[start]
            var count = 0
            var minX = width
            var minY = height
            var maxX = 0
            var maxY = 0
            val stack = ArrayDeque<Int>()
            stack.add(start)
            visited[start] = true
            while (stack.isNotEmpty()) {
                val idx = stack.removeLast()
                val x = idx % width
                val y = idx / width
                count++
                minX = min(minX, x)
                minY = min(minY, y)
                maxX = max(maxX, x)
                maxY = max(maxY, y)
                val neighbors = intArrayOf(idx - 1, idx + 1, idx - width, idx + width)
                for (n in neighbors) {
                    if (n < 0 || n >= pixels.size || visited[n]) continue
                    if (labelsGrid[n] != kind) continue
                    visited[n] = true
                    stack.add(n)
                }
            }
            if (count < minArea || count > maxArea) continue
            val padX = (maxX - minX) * 0.06f
            val padY = (maxY - minY) * 0.06f
            results += DetectedInfection(
                label = classToInfection(kind),
                score = (0.52f + (count.toFloat() / frameArea).coerceAtMost(0.28f)),
                box = RectF(
                    ((minX - padX) / width).coerceIn(0f, 1f),
                    ((minY - padY) / height).coerceIn(0f, 1f),
                    ((maxX + padX) / width).coerceIn(0f, 1f),
                    ((maxY + padY) / height).coerceIn(0f, 1f)
                )
            )
        }
        return results.sortedByDescending { it.box.width() * it.box.height() }
    }

    private fun lesionClass(hsv: FloatArray, r: Int, g: Int, b: Int): Int {
        val hue = hsv[0]
        val sat = hsv[1]
        val value = hsv[2]
        val maxC = max(r, max(g, b))
        val minC = min(r, min(g, b))
        if (g > r + 18 && g > b + 12 && sat < 0.72f && value > 0.18f && value < 0.92f) {
            return HEALTHY
        }
        if (value > 0.78f && sat < 0.22f && maxC > 170) return POWDERY
        if (sat < 0.18f && value in 0.35f..0.75f && abs(r - g) < 18) return GRAY_MOLD
        if (hue in 40f..70f && sat > 0.35f && value > 0.35f) return YELLOW
        if (hue in 18f..45f && sat > 0.35f && value in 0.25f..0.85f) return BROWN
        if (value < 0.28f && maxC < 80) return BLACK
        if (hue in 70f..95f && sat > 0.25f && minC < 90) return BACTERIAL
        return HEALTHY
    }

    private fun classToInfection(kind: Int): String = when (kind) {
        POWDERY -> "Powdery Mildew"
        YELLOW -> "Mosaic Virus"
        BROWN -> "Early Blight"
        BLACK -> "Late Blight"
        GRAY_MOLD -> "Gray Mold"
        BACTERIAL -> "Bacterial Leaf Spot"
        else -> "Leaf Spot"
    }

    private fun scaleForAnalysis(src: Bitmap): Bitmap {
        val targetW = 224
        val targetH = (src.height.toFloat() / src.width * targetW).toInt().coerceAtLeast(160)
        return Bitmap.createScaledBitmap(src, targetW, targetH, true)
    }

    private fun bitmapToBuffer(src: Bitmap): ByteBuffer {
        val scaled = Bitmap.createScaledBitmap(src, INPUT_SIZE, INPUT_SIZE, true)
        val buffer = ByteBuffer.allocateDirect(1 * INPUT_SIZE * INPUT_SIZE * 3 * 4)
        buffer.order(ByteOrder.nativeOrder())
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        scaled.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        for (pixel in pixels) {
            buffer.putFloat(Color.red(pixel) / 255f)
            buffer.putFloat(Color.green(pixel) / 255f)
            buffer.putFloat(Color.blue(pixel) / 255f)
        }
        buffer.rewind()
        return buffer
    }

    override fun close() {
        interpreter?.close()
    }

    companion object {
        private const val MODEL_ASSET = "ml/infection_detector.tflite"
        private const val LABELS_ASSET = "ml/infection_labels.txt"
        private const val INPUT_SIZE = 96
        private const val HEALTHY = 0
        private const val POWDERY = 1
        private const val YELLOW = 2
        private const val BROWN = 3
        private const val BLACK = 4
        private const val GRAY_MOLD = 5
        private const val BACTERIAL = 6
        private const val MIN_LEAF_COVERAGE = 0.10f
        private const val MIN_LEAF_BLOB = 0.05f
        private const val MIN_CENTER_COVERAGE = 0.08f
        private const val MIN_FRUIT_COVERAGE = 0.035f
        private const val MIN_FRUIT_BLOB = 0.022f
        private const val MIN_FRUIT_CENTER = 0.025f

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

        private fun loadModel(context: Context, assetPath: String): MappedByteBuffer {
            val fd = context.assets.openFd(assetPath)
            FileInputStream(fd.fileDescriptor).use { input ->
                return input.channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
            }
        }
    }
}
