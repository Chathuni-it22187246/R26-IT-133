package com.greenhands.app

import com.greenhands.app.harvest.detection.HarvestScanPhase
import com.greenhands.app.harvest.detection.NormalizedRect
import com.greenhands.app.harvest.detection.ScanTargetType
import com.greenhands.app.harvest.detection.StabilityTracker
import com.greenhands.app.harvest.detection.TargetAutoCaptureController
import com.greenhands.app.harvest.detection.TargetDetection
import com.greenhands.app.harvest.detection.TargetDetectionCalibration
import com.greenhands.app.harvest.detection.TargetDetector
import com.greenhands.app.harvest.detection.TargetRegionCropper
import com.greenhands.app.harvest.detection.TargetRejectReason
import com.greenhands.app.harvest.detection.TargetValidator
import com.greenhands.app.harvest.detection.UnavailableTargetDetector
import com.greenhands.app.harvest.domain.HarvestArgbFrame
import com.greenhands.app.harvest.domain.HarvestDecisionEngine
import com.greenhands.app.harvest.domain.HsvAnalyzer
import com.greenhands.app.harvest.domain.MaturityCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HarvestTargetDetectionTest {

    private val calibration = TargetDetectionCalibration.PROJECT
    private val validator = TargetValidator(calibration)
    private val engine = HarvestDecisionEngine()
    private val hsv = HsvAnalyzer(centerCropFraction = 1f)

    @Test
    fun correctFruitTargetIsAcceptedOnFruitScan() {
        val result = validator.validate(
            expected = ScanTargetType.TOMATO_FRUIT,
            detections = listOf(detection(ScanTargetType.TOMATO_FRUIT)),
            modelReady = true,
            stable = true
        )
        assertTrue(result.detected)
        assertTrue(result.correctTarget)
        assertTrue(result.readyForManualCapture)
        assertTrue(result.readyForAutoCapture)
        assertNull(result.reason)
    }

    @Test
    fun leafIsRejectedOnFruitScan() {
        val result = validator.validate(
            expected = ScanTargetType.TOMATO_FRUIT,
            detections = listOf(detection(ScanTargetType.TOMATO_LEAF)),
            modelReady = true
        )
        assertEquals(TargetRejectReason.WRONG_TARGET, result.reason)
        assertFalse(result.correctTarget)
        assertFalse(result.readyForManualCapture)
        assertFalse(result.readyForAutoCapture)
    }

    @Test
    fun correctLeafTargetIsAcceptedOnLeafScan() {
        val result = validator.validate(
            expected = ScanTargetType.TOMATO_LEAF,
            detections = listOf(detection(ScanTargetType.TOMATO_LEAF)),
            modelReady = true,
            stable = true
        )
        assertTrue(result.correctTarget)
        assertTrue(result.readyForAutoCapture)
        assertNull(result.reason)
    }

    @Test
    fun fruitIsRejectedOnLeafScan() {
        val result = validator.validate(
            expected = ScanTargetType.TOMATO_LEAF,
            detections = listOf(detection(ScanTargetType.TOMATO_FRUIT)),
            modelReady = true
        )
        assertEquals(TargetRejectReason.WRONG_TARGET, result.reason)
        assertFalse(result.readyForManualCapture)
    }

    @Test
    fun lowConfidenceIsRejected() {
        val result = validator.validate(
            expected = ScanTargetType.TOMATO_FRUIT,
            detections = listOf(detection(ScanTargetType.TOMATO_FRUIT, confidence = 0.40f)),
            modelReady = true,
            stable = true
        )
        assertEquals(TargetRejectReason.LOW_CONFIDENCE, result.reason)
        assertFalse(result.readyForManualCapture)
    }

    @Test
    fun targetTooSmallIsRejected() {
        val result = validator.validate(
            expected = ScanTargetType.TOMATO_FRUIT,
            detections = listOf(
                detection(
                    ScanTargetType.TOMATO_FRUIT,
                    box = NormalizedRect(0.46f, 0.46f, 0.54f, 0.54f)
                )
            ),
            modelReady = true,
            stable = true
        )
        assertEquals(TargetRejectReason.TARGET_TOO_SMALL, result.reason)
        assertFalse(result.sufficientlyLarge)
        assertFalse(result.readyForManualCapture)
    }

    @Test
    fun offCenterTargetIsRejected() {
        val result = validator.validate(
            expected = ScanTargetType.TOMATO_FRUIT,
            detections = listOf(
                detection(
                    ScanTargetType.TOMATO_FRUIT,
                    box = NormalizedRect(0.58f, 0.58f, 0.98f, 0.98f)
                )
            ),
            modelReady = true,
            stable = true
        )
        assertEquals(TargetRejectReason.TARGET_NOT_CENTERED, result.reason)
        assertFalse(result.centered)
        assertFalse(result.readyForManualCapture)
    }

    @Test
    fun unstableDetectionNeverAutoCaptures() {
        val controller = TargetAutoCaptureController(
            expected = ScanTargetType.TOMATO_FRUIT,
            modelReady = true,
            clock = { 1_000L }
        )
        val frame = solidFrame(0xFFCC3333.toInt())
        var captures = 0
        for (i in 0 until calibration.requiredStableFrames + 4) {
            val moving = if (i % 2 == 0) {
                NormalizedRect(0.25f, 0.25f, 0.75f, 0.75f)
            } else {
                NormalizedRect(0.05f, 0.05f, 0.55f, 0.55f)
            }
            val tick = controller.onFrame(
                frame,
                listOf(detection(ScanTargetType.TOMATO_FRUIT, box = moving)),
                analyzing = false
            )
            if (tick.captureFrame != null) captures++
        }
        assertEquals(0, captures)
    }

    @Test
    fun stableDetectionsTriggerExactlyOneAutoCapture() {
        val controller = TargetAutoCaptureController(
            expected = ScanTargetType.TOMATO_FRUIT,
            modelReady = true,
            clock = { 5_000L }
        )
        val frame = solidFrame(0xFFCC3333.toInt())
        val hits = List(calibration.requiredStableFrames + 6) {
            detection(ScanTargetType.TOMATO_FRUIT)
        }
        var captures = 0
        var capturingPhase = 0
        hits.forEach { det ->
            val tick = controller.onFrame(frame, listOf(det), analyzing = false)
            if (tick.captureFrame != null) {
                captures++
                assertEquals(HarvestScanPhase.CAPTURING, tick.phase)
                capturingPhase++
            }
        }
        assertEquals(1, captures)
        assertEquals(1, capturingPhase)
    }

    @Test
    fun cooldownPreventsRapidRepeatedAutoCaptures() {
        var now = 10_000L
        val controller = TargetAutoCaptureController(
            expected = ScanTargetType.TOMATO_FRUIT,
            modelReady = true,
            clock = { now }
        )
        val frame = solidFrame(0xFFCC3333.toInt())
        fun feedStable(count: Int = calibration.requiredStableFrames): Int {
            var captures = 0
            repeat(count) {
                val tick = controller.onFrame(
                    frame,
                    listOf(detection(ScanTargetType.TOMATO_FRUIT)),
                    analyzing = false
                )
                if (tick.captureFrame != null) captures++
            }
            return captures
        }
        assertEquals(1, feedStable())
        controller.onFrame(frame, emptyList(), analyzing = false)
        now += 400L
        assertEquals(0, feedStable())
        now += TargetDetectionCalibration.AUTO_CAPTURE_COOLDOWN_MS
        assertEquals(1, feedStable())
    }

    @Test
    fun invalidTargetNeverReachesHsvOrDecisionEngine() {
        val controller = TargetAutoCaptureController(
            expected = ScanTargetType.TOMATO_FRUIT,
            modelReady = true,
            clock = { 1_000L }
        )
        val frame = solidFrame(0xFFCC3333.toInt())
        var hsvCalls = 0
        var decisionCalls = 0
        val invalids = listOf(
            emptyList(),
            listOf(detection(ScanTargetType.TOMATO_LEAF)),
            listOf(detection(ScanTargetType.TOMATO_FRUIT, confidence = 0.2f)),
            listOf(
                detection(
                    ScanTargetType.TOMATO_FRUIT,
                    box = NormalizedRect(0.48f, 0.48f, 0.52f, 0.52f)
                )
            )
        )
        invalids.forEach { dets ->
            val tick = controller.onFrame(frame, dets, analyzing = false)
            tick.withCroppedFrame {
                hsvCalls++
                val measured = hsv.analyzeFruit(it)
                decisionCalls++
                engine.decideTomato(measured, MaturityCalculator.assessTomato(null, null))
            }
        }
        assertEquals(0, hsvCalls)
        assertEquals(0, decisionCalls)
    }

    @Test
    fun boundingBoxCropIsUsedForAnalysis() {
        val width = 20
        val height = 20
        val pixels = IntArray(width * height) { index ->
            val x = index % width
            if (x < width / 2) 0xFFCC1A1A.toInt() else 0xFF22BB33.toInt()
        }
        val frame = HarvestArgbFrame(pixels, width, height)
        val leftBox = NormalizedRect(0.05f, 0.20f, 0.45f, 0.80f)
        val crop = TargetRegionCropper(calibration).crop(frame, leftBox)
        val measured = hsv.analyzeFruit(crop)
        assertTrue(measured.redPercent > 70f)
        assertTrue(measured.greenPercent < 20f)
        assertTrue(crop.width < frame.width)
    }

    @Test
    fun missingModelNeverDetectsOrCaptures() {
        val frame = solidFrame(0xFF22BB33.toInt())
        assertFalse(UnavailableTargetDetector.isModelReady)
        assertTrue(UnavailableTargetDetector.detect(frame, 1L).isEmpty())
        val controller = TargetAutoCaptureController(
            expected = ScanTargetType.TOMATO_FRUIT,
            modelReady = false,
            clock = { 1_000L }
        )
        val tick = controller.onFrame(
            frame,
            listOf(detection(ScanTargetType.TOMATO_FRUIT)),
            analyzing = false
        )
        assertEquals(HarvestScanPhase.MODEL_UNAVAILABLE, tick.phase)
        assertEquals(TargetRejectReason.MODEL_UNAVAILABLE, tick.validation.reason)
        assertNull(tick.captureFrame)
    }

    @Test
    fun firstDetectedFrameIsNotStable() {
        val tracker = StabilityTracker(calibration)
        tracker.update(detection(ScanTargetType.TOMATO_FRUIT))
        assertEquals(1, tracker.consecutiveStableFrames)
        assertFalse(tracker.isStable())
    }

    @Test
    fun fakeDetectorDoesNotTreatColorAsRecognition() {
        val detector = ScriptedTargetDetector(emptyList())
        val redFrame = solidFrame(0xFFCC1A1A.toInt())
        assertTrue(detector.detect(redFrame, 1L).isEmpty())
    }

    private class ScriptedTargetDetector(
        var detections: List<TargetDetection>
    ) : TargetDetector {
        override val isModelReady: Boolean = true
        override fun detect(frame: HarvestArgbFrame, timestampMs: Long) = detections
    }

    private fun detection(
        type: ScanTargetType,
        confidence: Float = 0.86f,
        box: NormalizedRect = NormalizedRect(0.25f, 0.25f, 0.75f, 0.75f)
    ) = TargetDetection(
        targetType = type,
        confidence = confidence,
        boundingBox = box,
        timestampMs = 1L
    )

    private fun solidFrame(argb: Int, width: Int = 24, height: Int = 24) =
        HarvestArgbFrame(IntArray(width * height) { argb }, width, height)
}
