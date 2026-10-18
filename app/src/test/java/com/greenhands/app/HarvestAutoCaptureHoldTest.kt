package com.greenhands.app

import com.greenhands.app.harvest.detection.HarvestScanPhase
import com.greenhands.app.harvest.detection.NormalizedRect
import com.greenhands.app.harvest.detection.ScanTargetType
import com.greenhands.app.harvest.detection.StabilityTracker
import com.greenhands.app.harvest.detection.TargetAutoCaptureController
import com.greenhands.app.harvest.detection.TargetDetection
import com.greenhands.app.harvest.detection.TargetDetectionCalibration
import com.greenhands.app.harvest.detection.TargetRejectReason
import com.greenhands.app.harvest.detection.TargetValidator
import com.greenhands.app.harvest.domain.HarvestArgbFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HarvestAutoCaptureHoldTest {

    private val calibration = TargetDetectionCalibration.PROJECT
    private val box = NormalizedRect(0.28f, 0.28f, 0.72f, 0.72f)

    @Test
    fun eightValidLockReadyFramesStillTriggerOneAutoCapture() {
        val controller = fruitController()
        val frame = solidFrame()
        var captures = 0
        var lastStreak = 0
        repeat(calibration.requiredStableFrames) {
            val tick = controller.onFrame(frame, listOf(lockReady()), analyzing = false)
            lastStreak = tick.stabilityStreak
            if (tick.captureFrame != null) captures++
        }
        assertEquals(calibration.requiredStableFrames, lastStreak)
        assertEquals(1, captures)
    }

    @Test
    fun oneTemporaryMissPausesStreakWithoutIncreasingIt() {
        val controller = fruitController()
        val frame = solidFrame()
        repeat(5) {
            controller.onFrame(frame, listOf(lockReady()), analyzing = false)
        }
        val miss = controller.onFrame(frame, listOf(lowConf()), analyzing = false)
        assertEquals(5, miss.stabilityStreak)
        assertNull(miss.captureFrame)
        assertEquals(TargetRejectReason.LOW_CONFIDENCE, miss.validation.reason)
        val resume = controller.onFrame(frame, listOf(lockReady()), analyzing = false)
        assertEquals(6, resume.stabilityStreak)
        assertNull(resume.captureFrame)
        assertFalse(resume.validation.readyForAutoCapture)
    }

    @Test
    fun twoConsecutiveMissesResetStreak() {
        val controller = fruitController()
        val frame = solidFrame()
        repeat(5) {
            controller.onFrame(frame, listOf(lockReady()), analyzing = false)
        }
        val pause = controller.onFrame(frame, emptyList(), analyzing = false)
        assertEquals(5, pause.stabilityStreak)
        assertNull(pause.captureFrame)
        val reset = controller.onFrame(frame, listOf(lowConf()), analyzing = false)
        assertEquals(0, reset.stabilityStreak)
        assertNull(reset.captureFrame)
        val resume = controller.onFrame(frame, listOf(lockReady()), analyzing = false)
        assertEquals(1, resume.stabilityStreak)
        assertFalse(resume.validation.readyForAutoCapture)
    }

    @Test
    fun failedCurrentFrameNeverTriggersCaptureEvenAfterSevenValid() {
        val controller = fruitController()
        val frame = solidFrame()
        repeat(7) {
            val tick = controller.onFrame(frame, listOf(lockReady()), analyzing = false)
            assertNull(tick.captureFrame)
        }
        val miss = controller.onFrame(frame, listOf(leafHit()), analyzing = false)
        assertEquals(7, miss.stabilityStreak)
        assertNull(miss.captureFrame)
        assertFalse(miss.validation.readyForManualCapture)
        assertFalse(miss.validation.readyForAutoCapture)
    }

    @Test
    fun leafAndRejectedFruitNeverTriggerFruitAutoCapture() {
        val controller = fruitController()
        val frame = solidFrame()
        repeat(calibration.requiredStableFrames + 2) {
            val leafOnly = controller.onFrame(frame, listOf(leafHit()), analyzing = false)
            assertNull(leafOnly.captureFrame)
            val clamped = controller.onFrame(
                frame,
                listOf(fruit(0.90f, NormalizedRect(0f, 0f, 1f, 1f))),
                analyzing = false
            )
            assertNull(clamped.captureFrame)
            val tooSmall = controller.onFrame(
                frame,
                listOf(fruit(0.80f, NormalizedRect(0.46f, 0.46f, 0.54f, 0.54f))),
                analyzing = false
            )
            assertNull(tooSmall.captureFrame)
        }
    }

    @Test
    fun oneMissThenSevenMoreValidCanReachCaptureOnCurrentLockReadyFrame() {
        val controller = fruitController()
        val frame = solidFrame()
        repeat(4) {
            assertNull(controller.onFrame(frame, listOf(lockReady()), analyzing = false).captureFrame)
        }
        val paused = controller.onFrame(frame, listOf(lowConf()), analyzing = false)
        assertEquals(4, paused.stabilityStreak)
        assertNull(paused.captureFrame)
        var captures = 0
        var last: com.greenhands.app.harvest.detection.TargetCaptureTick? = null
        repeat(4) {
            last = controller.onFrame(frame, listOf(lockReady()), analyzing = false)
            if (last?.captureFrame != null) captures++
        }
        assertEquals(8, last!!.stabilityStreak)
        assertEquals(1, captures)
        assertEquals(HarvestScanPhase.CAPTURING, last!!.phase)
        assertTrue(last!!.validation.readyForManualCapture)
        assertEquals(box, last!!.detection!!.boundingBox)
    }

    @Test
    fun manualCaptureReadinessIsUnchangedByTheHold() {
        val result = TargetValidator().validate(
            expected = ScanTargetType.TOMATO_FRUIT,
            detections = listOf(lockReady()),
            modelReady = true,
            stable = false
        )
        assertTrue(result.readyForManualCapture)
        assertFalse(result.readyForAutoCapture)
        assertEquals(TargetRejectReason.TARGET_NOT_STABLE, result.reason)
        val controller = fruitController()
        val tick = controller.onFrame(solidFrame(), listOf(lockReady()), analyzing = false)
        assertTrue(tick.validation.readyForManualCapture)
        assertNull(tick.captureFrame)
    }

    @Test
    fun cooldownStillBlocksDuplicateCapture() {
        var now = 12_000L
        val controller = fruitController { now }
        val frame = solidFrame()
        var captures = 0
        repeat(calibration.requiredStableFrames) {
            if (controller.onFrame(frame, listOf(lockReady()), false).captureFrame != null) captures++
        }
        assertEquals(1, captures)
        repeat(3) {
            assertNull(controller.onFrame(frame, listOf(lockReady()), false).captureFrame)
        }
        controller.onFrame(frame, emptyList(), false)
        controller.onFrame(frame, emptyList(), false)
        now += 400L
        repeat(calibration.requiredStableFrames) {
            assertNull(controller.onFrame(frame, listOf(lockReady()), false).captureFrame)
        }
        now += TargetDetectionCalibration.AUTO_CAPTURE_COOLDOWN_MS
        var second = 0
        repeat(calibration.requiredStableFrames) {
            if (controller.onFrame(frame, listOf(lockReady()), false).captureFrame != null) second++
        }
        assertEquals(1, second)
    }

    @Test
    fun leafScanStillResetsStreakOnASingleMiss() {
        val controller = TargetAutoCaptureController(
            expected = ScanTargetType.TOMATO_LEAF,
            modelReady = true,
            clock = { 8_000L }
        )
        val frame = solidFrame()
        val leaf = leafHit()
        repeat(5) {
            controller.onFrame(frame, listOf(leaf), analyzing = false)
        }
        val miss = controller.onFrame(frame, emptyList(), analyzing = false)
        assertEquals(0, miss.stabilityStreak)
        val resume = controller.onFrame(frame, listOf(leaf), analyzing = false)
        assertEquals(1, resume.stabilityStreak)
        assertNull(resume.captureFrame)
    }

    @Test
    fun trackerWithoutPauseStillResetsImmediately() {
        val tracker = StabilityTracker(calibration, pauseOnSingleMiss = false)
        val hit = lockReady()
        repeat(4) { tracker.update(hit) }
        assertEquals(4, tracker.consecutiveStableFrames)
        assertEquals(0, tracker.update(null))
        assertFalse(tracker.paused)
        assertEquals(1, tracker.update(hit))
    }

    @Test
    fun thresholdsRemainUnchanged() {
        assertEquals(0.65f, TargetDetectionCalibration.MIN_DETECTION_CONFIDENCE, 0.0f)
        assertEquals(0.25f, TargetDetectionCalibration.DETECTOR_SCORE_FLOOR, 0.0f)
        assertEquals(0.10f, TargetDetectionCalibration.MIN_TARGET_AREA_RATIO, 0.0f)
        assertEquals(0.22f, TargetDetectionCalibration.CENTER_TOLERANCE, 0.0f)
        assertEquals(8, TargetDetectionCalibration.REQUIRED_STABLE_FRAMES)
        assertEquals(2500L, TargetDetectionCalibration.AUTO_CAPTURE_COOLDOWN_MS)
        assertEquals(0.45f, TargetDetectionCalibration.NMS_IOU_THRESHOLD, 0.0f)
        assertNotNull(box)
    }

    private fun fruitController(
        clock: () -> Long = { 8_000L }
    ) = TargetAutoCaptureController(
        expected = ScanTargetType.TOMATO_FRUIT,
        modelReady = true,
        clock = clock
    )

    private fun lockReady() = fruit(0.80f, box)

    private fun lowConf() = fruit(0.50f, box)

    private fun fruit(confidence: Float, box: NormalizedRect) = TargetDetection(
        targetType = ScanTargetType.TOMATO_FRUIT,
        confidence = confidence,
        boundingBox = box,
        timestampMs = 1L
    )

    private fun leafHit() = TargetDetection(
        targetType = ScanTargetType.TOMATO_LEAF,
        confidence = 0.88f,
        boundingBox = box,
        timestampMs = 1L
    )

    private fun solidFrame() = HarvestArgbFrame(
        IntArray(24 * 24) { 0xFFCC3333.toInt() },
        24,
        24
    )
}
