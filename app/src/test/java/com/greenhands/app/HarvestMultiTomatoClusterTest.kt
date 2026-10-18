package com.greenhands.app

import com.greenhands.app.harvest.detection.HybridScanConfig
import com.greenhands.app.harvest.detection.MultiTomatoClusterProcessor
import com.greenhands.app.harvest.detection.MultiTomatoFruitSelector
import com.greenhands.app.harvest.detection.MultiTomatoFruitTracker
import com.greenhands.app.harvest.detection.NormalizedRect
import com.greenhands.app.harvest.detection.ScanTargetType
import com.greenhands.app.harvest.detection.TargetAutoCaptureController
import com.greenhands.app.harvest.detection.TargetDetection
import com.greenhands.app.harvest.detection.TargetDetectionCalibration
import com.greenhands.app.harvest.detection.TargetValidator
import com.greenhands.app.harvest.domain.HarvestArgbFrame
import com.greenhands.app.harvest.domain.HarvestDecisionEngine
import com.greenhands.app.harvest.domain.MaturityCalculator
import com.greenhands.app.harvest.domain.TomatoHarvestReasons
import com.greenhands.app.harvest.domain.TomatoRipenessState
import com.greenhands.app.harvest.model.FruitColorMeasurement
import com.greenhands.app.harvest.model.HarvestDecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HarvestMultiTomatoClusterTest {

    private val selector = MultiTomatoFruitSelector
    private val processor = MultiTomatoClusterProcessor()
    private val validator = TargetValidator()
    private val engine = HarvestDecisionEngine()

    @Test
    fun clusterProcessingIsDisabledByDefault() {
        assertFalse(HybridScanConfig.ENABLE_MULTI_TOMATO_CLUSTER_PROCESSING)
        val result = processor.analyze(
            frame = twoFruitFrame(),
            detections = listOf(leftFruit(), rightFruit()),
            maturity = withinWindow()
        )
        assertFalse(result.enabled)
        assertTrue(result.fruits.isEmpty())
        assertEquals(0, result.fruitCount)
    }

    @Test
    fun experimentalYoloDetectorFlagIsUnchanged() {
        assertTrue(HybridScanConfig.USE_EXPERIMENTAL_TFLITE_DETECTOR)
    }

    @Test
    fun oneTomatoDetectionIsKept() {
        val selected = selector.select(listOf(leftFruit()))
        assertEquals(1, selected.fruits.size)
        assertEquals(0, selected.suppressedDuplicateCount)
        assertEquals(0, selected.rejectedCount)
        assertEquals(leftFruit().boundingBox, selected.fruits.single().boundingBox)
    }

    @Test
    fun multipleSeparateTomatoDetectionsAreKept() {
        val selected = selector.select(listOf(leftFruit(), rightFruit()))
        assertEquals(2, selected.fruits.size)
        assertEquals(0, selected.suppressedDuplicateCount)
        assertEquals(0, selected.rejectedCount)
    }

    @Test
    fun overlappingBoxesForTheSameTomatoKeepTheStrongerOne() {
        val stronger = fruit(box = NormalizedRect(0.20f, 0.20f, 0.70f, 0.70f), confidence = 0.93f)
        val weaker = fruit(box = NormalizedRect(0.22f, 0.22f, 0.72f, 0.72f), confidence = 0.80f)
        val selected = selector.select(listOf(weaker, stronger))
        assertEquals(1, selected.fruits.size)
        assertEquals(1, selected.suppressedDuplicateCount)
        assertEquals(stronger.boundingBox, selected.fruits.single().boundingBox)
    }

    @Test
    fun neighbouringTomatoesWithPartialOverlapStaySeparate() {
        val left = fruit(box = NormalizedRect(0.05f, 0.20f, 0.45f, 0.80f), confidence = 0.88f)
        val right = fruit(box = NormalizedRect(0.40f, 0.20f, 0.80f, 0.80f), confidence = 0.86f)
        assertTrue(left.boundingBox.iou(right.boundingBox) < TargetDetectionCalibration.NMS_IOU_THRESHOLD)
        val selected = selector.select(listOf(left, right))
        assertEquals(2, selected.fruits.size)
        assertEquals(0, selected.suppressedDuplicateCount)
    }

    @Test
    fun emptyDetectionsYieldNoFruits() {
        val selected = selector.select(emptyList())
        assertTrue(selected.fruits.isEmpty())
        assertEquals(0, selected.rejectedCount)
        val analyzed = processor.analyze(
            frame = twoFruitFrame(),
            detections = emptyList(),
            maturity = withinWindow(),
            enabled = true
        )
        assertTrue(analyzed.enabled)
        assertTrue(analyzed.fruits.isEmpty())
    }

    @Test
    fun lowConfidenceDetectionsAreRejected() {
        val selected = selector.select(
            listOf(fruit(box = leftBox(), confidence = 0.40f))
        )
        assertTrue(selected.fruits.isEmpty())
        assertEquals(1, selected.rejectedCount)
    }

    @Test
    fun invalidBoundingBoxesAreRejected() {
        val inverted = fruit(box = NormalizedRect(0.80f, 0.20f, 0.20f, 0.80f))
        val tiny = fruit(box = NormalizedRect(0.48f, 0.48f, 0.50f, 0.50f))
        val outside = fruit(box = NormalizedRect(1.2f, 1.2f, 1.5f, 1.5f))
        val nanBox = fruit(box = NormalizedRect(Float.NaN, 0.2f, 0.6f, 0.8f))
        val selected = selector.select(listOf(inverted, tiny, outside, nanBox))
        assertTrue(selected.fruits.isEmpty())
        assertEquals(4, selected.rejectedCount)
    }

    @Test
    fun leavesAreNotCountedAsTomatoes() {
        val leaf = TargetDetection(
            targetType = ScanTargetType.TOMATO_LEAF,
            confidence = 0.92f,
            boundingBox = leftBox(),
            timestampMs = 1L
        )
        val selected = selector.select(listOf(leaf, leftFruit()))
        assertEquals(1, selected.fruits.size)
        assertEquals(ScanTargetType.TOMATO_FRUIT, selected.fruits.single().targetType)
        assertEquals(1, selected.rejectedCount)
    }

    @Test
    fun oneValidBoxDoesNotInventExtraTomatoes() {
        val selected = selector.select(listOf(leftFruit(), fruit(box = leftBox(), confidence = 0.30f)))
        assertEquals(1, selected.fruits.size)
        assertEquals(1, selected.rejectedCount)
        assertEquals(0, selected.suppressedDuplicateCount)
    }

    @Test
    fun mixedGreenAndRedTomatoesKeepSeparateHarvestDecisions() {
        val result = processor.analyze(
            frame = twoFruitFrame(),
            detections = listOf(leftFruit(), rightFruit()),
            maturity = withinWindow(),
            enabled = true
        )
        assertEquals(2, result.fruitCount)
        val left = result.fruits.first { it.detection.boundingBox == leftBox() }
        val right = result.fruits.first { it.detection.boundingBox == rightBox() }
        assertEquals(1, left.displayId)
        assertEquals(2, right.displayId)
        assertTrue(result.snapshot != null)
        assertEquals(TomatoRipenessState.PREDOMINANTLY_RED_RIPE, left.harvest.ripeness.state)
        assertEquals(HarvestDecision.READY_TO_HARVEST, left.harvest.decision)
        assertEquals(TomatoRipenessState.DARK_GREEN, right.harvest.ripeness.state)
        assertEquals(HarvestDecision.NOT_READY, right.harvest.decision)
        assertNotEquals(left.harvest.decision, right.harvest.decision)
    }

    @Test
    fun insufficientColourOnADetectedBoxIsUncertain() {
        val gray = HarvestArgbFrame(IntArray(80 * 80) { 0xFF6E6E6E.toInt() }, 80, 80)
        val result = processor.analyze(
            frame = gray,
            detections = listOf(centeredFruit()),
            maturity = withinWindow(),
            enabled = true
        )
        assertEquals(1, result.fruitCount)
        assertEquals(HarvestDecision.UNCERTAIN, result.fruits.single().harvest.decision)
        assertEquals(TomatoRipenessState.INSUFFICIENT, result.fruits.single().harvest.ripeness.state)
    }

    @Test
    fun trackerReusesTheSameIdAcrossFrames() {
        val tracker = MultiTomatoFruitTracker()
        val first = tracker.assign(listOf(leftFruit(), rightFruit()))
        val second = tracker.assign(
            listOf(
                fruit(box = leftBox(), confidence = 0.84f, timestampMs = 40L),
                fruit(box = rightBox(), confidence = 0.81f, timestampMs = 40L)
            )
        )
        assertEquals(first[0].trackId, second[0].trackId)
        assertEquals(first[1].trackId, second[1].trackId)
        assertEquals(2, second.map { it.trackId }.toSet().size)
    }

    @Test
    fun singleTomatoScanStillLocksOneCandidate() {
        val offCenter = fruit(
            box = NormalizedRect(0.58f, 0.58f, 0.98f, 0.98f),
            confidence = 0.95f
        )
        val centered = centeredFruit()
        val validation = validator.validate(
            expected = ScanTargetType.TOMATO_FRUIT,
            detections = listOf(offCenter, centered),
            modelReady = true,
            stable = true
        )
        assertTrue(validation.readyForAutoCapture)
        assertEquals(centered.boundingBox, validation.detection?.boundingBox)
        val controller = TargetAutoCaptureController(
            expected = ScanTargetType.TOMATO_FRUIT,
            modelReady = true,
            clock = { 8_000L }
        )
        val frame = twoFruitFrame()
        var captures = 0
        var lastCrop: HarvestArgbFrame? = null
        repeat(TargetDetectionCalibration.REQUIRED_STABLE_FRAMES + 2) {
            val tick = controller.onFrame(frame, listOf(offCenter, centered), analyzing = false)
            if (tick.captureFrame != null) {
                captures++
                lastCrop = tick.captureFrame
            }
        }
        assertEquals(1, captures)
        assertTrue(lastCrop != null)
        assertTrue(lastCrop!!.argb.size < frame.argb.size)
    }

    @Test
    fun phase1MixedColourAfterDateStaysUncertain() {
        val mixed = engine.decideTomato(
            fruitMeasurement(green = 33f, yellow = 10f, red = 33f, brown = 5f, valueMean = 0.55f),
            withinWindow()
        )
        assertEquals(HarvestDecision.UNCERTAIN, mixed.decision)
        assertEquals(TomatoRipenessState.MIXED_UNCERTAIN, mixed.ripeness.state)
        assertEquals(TomatoHarvestReasons.DATE_REACHED_MIXED_COLOUR, mixed.harvestReasonLabel)
    }

    @Test
    fun phase1DarkGreenAfterDateStaysNotReady() {
        val result = engine.decideTomato(
            fruitMeasurement(
                green = 78f,
                yellow = 8f,
                red = 6f,
                brown = 5f,
                valueMean = 0.34f,
                greenValueMean = 0.33f
            ),
            withinWindow()
        )
        assertEquals(HarvestDecision.NOT_READY, result.decision)
        assertEquals(TomatoRipenessState.DARK_GREEN, result.ripeness.state)
    }

    @Test
    fun phase1ClearlyRedBeforeDateStaysReady() {
        val result = engine.decideTomato(
            fruitMeasurement(green = 10f, yellow = 18f, red = 62f, brown = 5f, valueMean = 0.52f),
            MaturityCalculator.assess(40, 65, 90)
        )
        assertEquals(HarvestDecision.READY_TO_HARVEST, result.decision)
        assertEquals(TomatoRipenessState.PREDOMINANTLY_RED_RIPE, result.ripeness.state)
        assertEquals(TomatoHarvestReasons.EARLY_VISUALLY_RIPE, result.harvestReasonLabel)
    }

    private fun leftFruit() = fruit(box = leftBox(), confidence = 0.88f)

    private fun rightFruit() = fruit(box = rightBox(), confidence = 0.86f)

    private fun centeredFruit() = fruit(
        box = NormalizedRect(0.25f, 0.25f, 0.75f, 0.75f),
        confidence = 0.80f
    )

    private fun fruit(
        box: NormalizedRect,
        confidence: Float = 0.86f,
        timestampMs: Long = 1L
    ) = TargetDetection(
        targetType = ScanTargetType.TOMATO_FRUIT,
        confidence = confidence,
        boundingBox = box,
        timestampMs = timestampMs
    )

    private fun leftBox() = NormalizedRect(0.08f, 0.15f, 0.40f, 0.85f)

    private fun rightBox() = NormalizedRect(0.60f, 0.15f, 0.92f, 0.85f)

    private fun twoFruitFrame(): HarvestArgbFrame {
        val width = 200
        val height = 100
        val pixels = IntArray(width * height) { GRAY }
        paintCircle(pixels, width, height, cx = 50, cy = 50, radius = 28, color = RED)
        paintCircle(pixels, width, height, cx = 150, cy = 50, radius = 28, color = DARK_GREEN)
        return HarvestArgbFrame(pixels, width, height)
    }

    private fun paintCircle(
        pixels: IntArray,
        width: Int,
        height: Int,
        cx: Int,
        cy: Int,
        radius: Int,
        color: Int
    ) {
        val r2 = radius * radius
        for (y in 0 until height) {
            for (x in 0 until width) {
                val dx = x - cx
                val dy = y - cy
                if (dx * dx + dy * dy <= r2) {
                    pixels[y * width + x] = color
                }
            }
        }
    }

    private fun fruitMeasurement(
        green: Float,
        yellow: Float,
        red: Float,
        brown: Float,
        samples: Int = 1000,
        valueMean: Float = 0.52f,
        greenValueMean: Float? = null
    ) = FruitColorMeasurement(
        sampledPixelCount = samples,
        hueMean = 80f,
        saturationMean = 0.55f,
        valueMean = valueMean,
        greenPercent = green,
        yellowPercent = yellow,
        redPercent = red,
        brownDarkPercent = brown,
        otherPercent = (100f - green - yellow - red - brown).coerceAtLeast(0f),
        greenSampledCount = (green * samples / 100f).toInt(),
        greenValueMean = greenValueMean ?: valueMean,
        greenSaturationMean = 0.55f
    )

    private fun withinWindow() = MaturityCalculator.assess(75, 65, 90)

    companion object {
        private const val GRAY = 0xFF6E6E6E.toInt()
        private const val RED = 0xFFCC1A1A.toInt()
        private const val DARK_GREEN = 0xFF1E641E.toInt()
    }
}
