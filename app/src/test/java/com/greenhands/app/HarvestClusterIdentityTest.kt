package com.greenhands.app

import com.greenhands.app.harvest.detection.ClusterFruitIdentity
import com.greenhands.app.harvest.detection.ClusterScanSummary
import com.greenhands.app.harvest.detection.HarvestScanDiag
import com.greenhands.app.harvest.detection.MultiTomatoClusterProcessor
import com.greenhands.app.harvest.detection.NormalizedRect
import com.greenhands.app.harvest.detection.ScanTargetType
import com.greenhands.app.harvest.detection.TargetAutoCaptureController
import com.greenhands.app.harvest.detection.TargetDetection
import com.greenhands.app.harvest.detection.TargetDetectionCalibration
import com.greenhands.app.harvest.domain.HarvestArgbFrame
import com.greenhands.app.harvest.domain.HarvestDecisionEngine
import com.greenhands.app.harvest.domain.MaturityCalculator
import com.greenhands.app.harvest.model.FruitColorMeasurement
import com.greenhands.app.harvest.model.HarvestDecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HarvestClusterIdentityTest {

    private val processor = MultiTomatoClusterProcessor()

    @Test
    fun displayNumbersAreLeftToRightEvenIfRightBoxHasHigherConfidence() {
        val left = fruit(box = NormalizedRect(0.08f, 0.20f, 0.36f, 0.80f), confidence = 0.70f)
        val right = fruit(box = NormalizedRect(0.60f, 0.20f, 0.88f, 0.80f), confidence = 0.96f)
        val numbered = ClusterFruitIdentity.displayBoxes(listOf(right, left))
        assertEquals(2, numbered.size)
        assertEquals(1, numbered[0].displayId)
        assertEquals(left.boundingBox, numbered[0].box)
        assertEquals(2, numbered[1].displayId)
        assertEquals(right.boundingBox, numbered[1].box)
    }

    @Test
    fun equalXUsesTopToBottom() {
        val upper = fruit(box = NormalizedRect(0.30f, 0.05f, 0.50f, 0.35f), confidence = 0.80f)
        val lower = fruit(box = NormalizedRect(0.30f, 0.55f, 0.50f, 0.85f), confidence = 0.80f)
        val numbered = ClusterFruitIdentity.displayBoxes(listOf(lower, upper))
        assertEquals(upper.boundingBox, numbered[0].box)
        assertEquals(1, numbered[0].displayId)
        assertEquals(lower.boundingBox, numbered[1].box)
        assertEquals(2, numbered[1].displayId)
    }

    @Test
    fun freezeKeepsOneDisplayIdPerDetectionAndDoesNotInventFruits() {
        val result = processor.analyze(
            frame = twoFruitFrame(),
            detections = listOf(
                fruit(box = leftBox(), confidence = 0.91f),
                fruit(box = rightBox(), confidence = 0.72f)
            ),
            maturity = withinWindow(),
            enabled = true
        )
        assertEquals(2, result.fruitCount)
        assertEquals(setOf(1, 2), result.fruits.map { it.displayId }.toSet())
        val byId = result.fruits.associateBy { it.displayId }
        assertEquals(leftBox(), byId.getValue(1).detection.boundingBox)
        assertEquals(rightBox(), byId.getValue(2).detection.boundingBox)
        val lines = ClusterScanSummary.overlayLines(result.fruits)
        assertTrue(lines[0].startsWith("Fruit 1:"))
        assertTrue(lines[1].startsWith("Fruit 2:"))
    }

    @Test
    fun emptyDetectionsStayEmpty() {
        assertTrue(ClusterFruitIdentity.displayBoxes(emptyList()).isEmpty())
        val result = processor.analyze(
            frame = twoFruitFrame(),
            detections = emptyList(),
            maturity = withinWindow(),
            enabled = true
        )
        assertTrue(result.fruits.isEmpty())
        assertTrue(ClusterScanSummary.overlayLines(result.fruits).isEmpty())
    }

    @Test
    fun snapshotBoxMapsToSameNormalizedCoordinatesAsAnalysisFrame() {
        val result = processor.analyze(
            frame = twoFruitFrame(),
            detections = listOf(fruit(box = leftBox(), confidence = 0.88f)),
            maturity = withinWindow(),
            enabled = true
        )
        val fruit = result.fruits.single()
        val snapshot = result.snapshot!!
        assertEquals(fruit.detection.boundingBox, fruit.detection.boundingBox)
        val leftPx = (fruit.detection.boundingBox.left * snapshot.width).toInt()
        val topPx = (fruit.detection.boundingBox.top * snapshot.height).toInt()
        assertEquals((leftBox().left * 200).toInt(), leftPx)
        assertEquals((leftBox().top * 100).toInt(), topPx)
        assertEquals(200, snapshot.width)
        assertEquals(100, snapshot.height)
    }

    @Test
    fun diagnosticLineRecordsDecisionReasonWithoutPlantingCalendarDate() {
        val engine = HarvestDecisionEngine()
        val decision = engine.decideTomato(redMeasurement(), withinWindow())
        val line = HarvestScanDiag.singleFruitLine(
            rotationDegrees = 90,
            frameWidth = 480,
            frameHeight = 640,
            detection = fruit(box = leftBox(), confidence = 0.84f),
            measurement = redMeasurement(),
            result = decision,
            daysSincePlanting = 75
        )
        assertTrue(line.contains("mode=SINGLE_FRUIT"))
        assertTrue(line.contains("rot=90"))
        assertTrue(line.contains("frame=480x640"))
        assertTrue(line.contains("daysSince=75"))
        assertTrue(line.contains("decision=${HarvestDecision.READY_TO_HARVEST}"))
        assertTrue(line.contains("reason="))
        assertFalse(line.contains("millis"))
        assertFalse(line.lowercase().contains("utc"))
    }

    @Test
    fun clusterDiagnosticLineIncludesFruitIdsAndBoxes() {
        val result = processor.analyze(
            frame = twoFruitFrame(),
            detections = listOf(
                fruit(box = leftBox(), confidence = 0.88f),
                fruit(box = rightBox(), confidence = 0.86f)
            ),
            maturity = withinWindow(),
            enabled = true
        )
        val line = HarvestScanDiag.clusterLine(
            rotationDegrees = 90,
            frameWidth = 200,
            frameHeight = 100,
            result = result,
            daysSincePlanting = 75
        )
        assertTrue(line.contains("mode=CLUSTER"))
        assertTrue(line.contains("id=1"))
        assertTrue(line.contains("id=2"))
        assertTrue(line.contains("ltrb="))
        assertTrue(line.contains("count=2"))
    }

    @Test
    fun singleTomatoAutoCaptureStillOneCrop() {
        val controller = TargetAutoCaptureController(
            expected = ScanTargetType.TOMATO_FRUIT,
            modelReady = true,
            clock = { 8_000L }
        )
        val frame = twoFruitFrame()
        val centered = fruit(
            box = NormalizedRect(0.25f, 0.25f, 0.75f, 0.75f),
            confidence = 0.80f
        )
        var captures = 0
        repeat(TargetDetectionCalibration.REQUIRED_STABLE_FRAMES + 2) {
            val tick = controller.onFrame(frame, listOf(centered), analyzing = false)
            if (tick.captureFrame != null) captures++
        }
        assertEquals(1, captures)
    }

    private fun fruit(box: NormalizedRect, confidence: Float) = TargetDetection(
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
        return HarvestArgbFrame(pixels, width, height, rotationDegrees = 90)
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

    private fun redMeasurement() = FruitColorMeasurement(
        sampledPixelCount = 1000,
        hueMean = 8f,
        saturationMean = 0.55f,
        valueMean = 0.52f,
        greenPercent = 10f,
        yellowPercent = 18f,
        redPercent = 62f,
        brownDarkPercent = 5f,
        otherPercent = 5f,
        greenSampledCount = 100,
        greenValueMean = 0.52f,
        greenSaturationMean = 0.55f
    )

    private fun withinWindow() = MaturityCalculator.assess(75, 65, 90)
}
