package com.greenhands.app

import com.greenhands.app.harvest.data.HarvestMeasurementStore
import com.greenhands.app.harvest.detection.ClusterScanCaptureController
import com.greenhands.app.harvest.detection.ClusterScanSummary
import com.greenhands.app.harvest.detection.CropScanMode
import com.greenhands.app.harvest.detection.HybridScanConfig
import com.greenhands.app.harvest.detection.MultiTomatoClusterProcessor
import com.greenhands.app.harvest.detection.NormalizedRect
import com.greenhands.app.harvest.detection.ScanTargetType
import com.greenhands.app.harvest.detection.TargetAutoCaptureController
import com.greenhands.app.harvest.detection.TargetDetection
import com.greenhands.app.harvest.detection.TargetDetectionCalibration
import com.greenhands.app.harvest.detection.TargetValidator
import com.greenhands.app.harvest.domain.HarvestArgbFrame
import com.greenhands.app.harvest.domain.HarvestDecisionEngine
import com.greenhands.app.harvest.domain.MaturityCalculator
import com.greenhands.app.harvest.domain.TomatoRipenessState
import com.greenhands.app.harvest.model.HarvestDecision
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HarvestClusterScanModeTest {

    private val processor = MultiTomatoClusterProcessor()
    private val engine = HarvestDecisionEngine()
    private val validator = TargetValidator()

    @Before
    fun clearStore() {
        HarvestMeasurementStore.clearSession()
    }

    @After
    fun resetStore() {
        HarvestMeasurementStore.clearSession()
    }

    @Test
    fun singleTomatoIsTheDefaultCropScanMode() {
        assertEquals(CropScanMode.SINGLE, CropScanMode.entries.first())
        assertFalse(HybridScanConfig.ENABLE_MULTI_TOMATO_CLUSTER_PROCESSING)
        assertTrue(HybridScanConfig.USE_EXPERIMENTAL_TFLITE_DETECTOR)
    }

    @Test
    fun clusterControllerDoesNotCaptureEmptyDetections() {
        val controller = ClusterScanCaptureController(clock = { 8_000L })
        repeat(TargetDetectionCalibration.REQUIRED_STABLE_FRAMES + 2) {
            val tick = controller.onFrame(emptyList(), analyzing = false)
            assertFalse(tick.capture)
            assertTrue(tick.fruits.isEmpty())
        }
    }

    @Test
    fun clusterControllerCapturesOnceAfterStableFruits() {
        var now = 8_000L
        val controller = ClusterScanCaptureController(clock = { now })
        val fruits = listOf(leftFruit(), rightFruit())
        var captures = 0
        repeat(TargetDetectionCalibration.REQUIRED_STABLE_FRAMES + 3) {
            val tick = controller.onFrame(fruits, analyzing = false)
            if (tick.capture) captures++
        }
        assertEquals(1, captures)
        now += 400L
        assertFalse(controller.onFrame(fruits, analyzing = false).capture)
    }

    @Test
    fun clusterControllerDoesNotCaptureWhileAnalyzing() {
        val controller = ClusterScanCaptureController(clock = { 8_000L })
        val fruits = listOf(centeredFruit())
        repeat(TargetDetectionCalibration.REQUIRED_STABLE_FRAMES + 2) {
            assertFalse(controller.onFrame(fruits, analyzing = true).capture)
        }
    }

    @Test
    fun clusterProcessorDefaultPathStaysDisabled() {
        val result = processor.analyze(
            frame = twoFruitFrame(),
            detections = listOf(leftFruit(), rightFruit()),
            maturity = withinWindow()
        )
        assertFalse(result.enabled)
        assertTrue(result.fruits.isEmpty())
    }

    @Test
    fun enabledClusterProcessorKeepsPerFruitDecisions() {
        val result = processor.analyze(
            frame = twoFruitFrame(),
            detections = listOf(leftFruit(), rightFruit()),
            maturity = withinWindow(),
            enabled = true
        )
        assertTrue(result.enabled)
        assertEquals(2, result.fruitCount)
        val left = result.fruits.first { it.detection.boundingBox == leftBox() }
        val right = result.fruits.first { it.detection.boundingBox == rightBox() }
        assertEquals(HarvestDecision.READY_TO_HARVEST, left.harvest.decision)
        assertEquals(TomatoRipenessState.PREDOMINANTLY_RED_RIPE, left.harvest.ripeness.state)
        assertEquals(HarvestDecision.NOT_READY, right.harvest.decision)
        assertEquals(TomatoRipenessState.DARK_GREEN, right.harvest.ripeness.state)
        assertEquals(1, ClusterScanSummary.readyCount(result.fruits))
        assertEquals(1, ClusterScanSummary.notReadyCount(result.fruits))
        assertEquals(
            "Fruit 1: READY TO HARVEST · Red",
            ClusterScanSummary.fruitLine(1, left.harvest.displayLabel, left.harvest.ripeness.label)
        )
    }

    @Test
    fun enabledClusterProcessorEmptyDetectionsStayEmpty() {
        val result = processor.analyze(
            frame = twoFruitFrame(),
            detections = emptyList(),
            maturity = withinWindow(),
            enabled = true
        )
        assertTrue(result.enabled)
        assertEquals(0, result.fruitCount)
        assertEquals(0, ClusterScanSummary.readyCount(result.fruits))
        assertEquals(0, ClusterScanSummary.uncertainCount(result.fruits))
    }

    @Test
    fun switchingToSingleScanClearsClusterStore() {
        val cluster = processor.analyze(
            frame = twoFruitFrame(),
            detections = listOf(leftFruit(), rightFruit()),
            maturity = withinWindow(),
            enabled = true
        )
        HarvestMeasurementStore.lastClusterResult = cluster
        HarvestMeasurementStore.lastFruit = null
        HarvestMeasurementStore.lastFruitDecision = null
        assertEquals(2, HarvestMeasurementStore.lastClusterResult?.fruitCount)

        val singleMeasurement = cluster.fruits.first().measurement
        val singleDecision = engine.decideTomato(singleMeasurement, withinWindow())
        HarvestMeasurementStore.lastFruit = singleMeasurement
        HarvestMeasurementStore.lastFruitDecision = singleDecision
        HarvestMeasurementStore.lastClusterResult = null
        assertNull(HarvestMeasurementStore.lastClusterResult)
        assertEquals(HarvestDecision.READY_TO_HARVEST, HarvestMeasurementStore.lastFruitDecision?.decision)
    }

    @Test
    fun singleTomatoAutoCaptureStillTakesOneCrop() {
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
        repeat(TargetDetectionCalibration.REQUIRED_STABLE_FRAMES + 2) {
            val tick = controller.onFrame(frame, listOf(offCenter, centered), analyzing = false)
            if (tick.captureFrame != null) captures++
        }
        assertEquals(1, captures)
    }

    @Test
    fun clearSessionRemovesClusterResult() {
        HarvestMeasurementStore.lastClusterResult = processor.analyze(
            frame = twoFruitFrame(),
            detections = listOf(leftFruit()),
            maturity = withinWindow(),
            enabled = true
        )
        HarvestMeasurementStore.clearSession()
        assertNull(HarvestMeasurementStore.lastClusterResult)
        assertNull(HarvestMeasurementStore.lastFruit)
        assertNull(HarvestMeasurementStore.lastFruitDecision)
    }

    private fun leftFruit() = fruit(box = leftBox(), confidence = 0.88f)

    private fun rightFruit() = fruit(box = rightBox(), confidence = 0.86f)

    private fun centeredFruit() = fruit(
        box = NormalizedRect(0.25f, 0.25f, 0.75f, 0.75f),
        confidence = 0.80f
    )

    private fun fruit(
        box: NormalizedRect,
        confidence: Float = 0.86f
    ) = TargetDetection(
        targetType = ScanTargetType.TOMATO_FRUIT,
        confidence = confidence,
        boundingBox = box,
        timestampMs = 1L
    )

    private fun leftBox() = NormalizedRect(0.08f, 0.15f, 0.40f, 0.85f)

    private fun rightBox() = NormalizedRect(0.60f, 0.15f, 0.92f, 0.85f)

    private fun twoFruitFrame(): HarvestArgbFrame {
        val width = 200
        val height = 100
        val pixels = IntArray(width * height) { 0xFF6E6E6E.toInt() }
        paintCircle(pixels, width, height, 50, 50, 28, 0xFFCC1A1A.toInt())
        paintCircle(pixels, width, height, 150, 50, 28, 0xFF1E641E.toInt())
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
                if (dx * dx + dy * dy <= r2) pixels[y * width + x] = color
            }
        }
    }

    private fun withinWindow() = MaturityCalculator.assess(75, 65, 90)
}
