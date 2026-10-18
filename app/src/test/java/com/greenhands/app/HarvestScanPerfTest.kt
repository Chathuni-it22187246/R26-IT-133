package com.greenhands.app

import com.greenhands.app.harvest.detection.HarvestScanDiag
import com.greenhands.app.harvest.detection.HarvestScanLiveSample
import com.greenhands.app.harvest.detection.HybridScanConfig
import com.greenhands.app.harvest.detection.NormalizedRect
import com.greenhands.app.harvest.detection.ScanTargetType
import com.greenhands.app.harvest.detection.TargetAutoCaptureController
import com.greenhands.app.harvest.detection.TargetDetection
import com.greenhands.app.harvest.detection.TargetDetectionCalibration
import com.greenhands.app.harvest.detection.TargetRejectReason
import com.greenhands.app.harvest.detection.TomatoYoloDiagnostics
import com.greenhands.app.harvest.detection.YoloLetterbox
import com.greenhands.app.harvest.detection.YoloOutputLayoutSelector
import com.greenhands.app.harvest.detection.YoloTensorLayout
import com.greenhands.app.harvest.detection.YoloV8OutputParser
import com.greenhands.app.harvest.domain.HarvestArgbFrame
import com.greenhands.app.harvest.domain.TomatoRipenessCalibration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class HarvestScanPerfTest {

    @Test
    fun layoutIsNotCachedOnNearZeroScores() {
        val selector = YoloOutputLayoutSelector()
        var last = selector.observeAndChoose(channelMajorMax = 0.01f, predMajorMax = 0.02f)
        repeat(9) {
            last = selector.observeAndChoose(channelMajorMax = 0.01f, predMajorMax = 0.02f)
        }
        assertFalse(selector.isCached)
        assertEquals(YoloTensorLayout.CHANNEL_MAJOR, last)
    }

    @Test
    fun channelMajorCachesAfterThreeValidatedFrames() {
        val selector = YoloOutputLayoutSelector()
        repeat(2) {
            selector.observeAndChoose(0.80f, 0.10f)
            assertFalse(selector.isCached)
        }
        selector.observeAndChoose(0.80f, 0.10f)
        assertTrue(selector.isCached)
        assertEquals(YoloTensorLayout.CHANNEL_MAJOR, selector.cached)
        assertEquals(
            YoloTensorLayout.CHANNEL_MAJOR,
            selector.observeAndChoose(0.10f, 0.99f)
        )
    }

    @Test
    fun predMajorCachesOnlyWhenClearlyStronger() {
        val selector = YoloOutputLayoutSelector()
        repeat(3) {
            selector.observeAndChoose(0.20f, 0.80f)
        }
        assertEquals(YoloTensorLayout.PRED_MAJOR, selector.cached)
    }

    @Test
    fun layoutFlipResetsConfirmationCount() {
        val selector = YoloOutputLayoutSelector()
        selector.observeAndChoose(0.80f, 0.10f)
        selector.observeAndChoose(0.80f, 0.10f)
        selector.observeAndChoose(0.10f, 0.90f)
        assertFalse(selector.isCached)
        selector.observeAndChoose(0.10f, 0.90f)
        assertFalse(selector.isCached)
        selector.observeAndChoose(0.10f, 0.90f)
        assertEquals(YoloTensorLayout.PRED_MAJOR, selector.cached)
    }

    @Test
    fun fillChannelsFromFlatReusesDestinationAndMatchesFreshDecode() {
        val channels = 6
        val preds = 8
        val floats = FloatArray(channels * preds) { index -> index * 0.01f }
        val fresh = YoloV8OutputParser.channelsFromFlat(floats, channels, preds, true)
        val dest = Array(channels) { FloatArray(preds) { -1f } }
        val reused = YoloV8OutputParser.fillChannelsFromFlat(floats, channels, preds, true, dest)
        assertSame(dest, reused)
        for (c in 0 until channels) {
            assertTrue(fresh[c].contentEquals(reused[c]))
        }
        val other = YoloV8OutputParser.fillChannelsFromFlat(
            floats, channels, preds, true, Array(2) { FloatArray(3) }
        )
        assertEquals(channels, other.size)
        assertEquals(preds, other[0].size)
        assertTrue(fresh[4].contentEquals(other[4]))
    }

    @Test
    fun letterboxReuseKeepsBilinearResultAndSameBuffer() {
        val frame = HarvestArgbFrame(IntArray(32 * 24) { idx -> 0xFF000000.toInt() + idx }, 32, 24)
        val first = YoloLetterbox.fit(frame, 16, 16)
        val reused = YoloLetterbox.fit(frame, 16, 16, reuseArgb = first.frame.argb)
        val fresh = YoloLetterbox.fit(frame, 16, 16)
        assertSame(first.frame.argb, reused.frame.argb)
        assertNotSame(fresh.frame.argb, reused.frame.argb)
        assertTrue(fresh.frame.argb.contentEquals(reused.frame.argb))
        assertEquals(fresh.transform.gain, reused.transform.gain, 0.0001f)
        assertEquals(fresh.transform.padY, reused.transform.padY, 0.0001f)
    }

    @Test
    fun liveDiagnosticsAreRateLimitedExceptCapture() {
        assertTrue(
            HarvestScanDiag.shouldEmitLive(
                frameIndex = 1, nowMs = 10L, lastLogAtMs = 0L,
                autoCapture = false, focusRequested = false, reasonChanged = false
            )
        )
        assertFalse(
            HarvestScanDiag.shouldEmitLive(
                frameIndex = 8, nowMs = 200L, lastLogAtMs = 100L,
                autoCapture = false, focusRequested = false, reasonChanged = false
            )
        )
        assertTrue(
            HarvestScanDiag.shouldEmitLive(
                frameIndex = 8, nowMs = 1200L, lastLogAtMs = 100L,
                autoCapture = false, focusRequested = false, reasonChanged = false
            )
        )
        assertTrue(
            HarvestScanDiag.shouldEmitLive(
                frameIndex = 8, nowMs = 150L, lastLogAtMs = 100L,
                autoCapture = true, focusRequested = false, reasonChanged = false
            )
        )
        assertFalse(
            HarvestScanDiag.shouldEmitLive(
                frameIndex = 8, nowMs = 200L, lastLogAtMs = 100L,
                autoCapture = false, focusRequested = false, reasonChanged = true
            )
        )
        assertTrue(
            HarvestScanDiag.shouldEmitLive(
                frameIndex = 8, nowMs = 550L, lastLogAtMs = 100L,
                autoCapture = false, focusRequested = false, reasonChanged = true
            )
        )
        assertTrue(
            HarvestScanDiag.shouldEmitLive(
                frameIndex = 8, nowMs = 400L, lastLogAtMs = 100L,
                autoCapture = false, focusRequested = true, reasonChanged = false
            )
        )
    }

    @Test
    fun liveLineIncludesTimingRejectReasonAndCaptureFlags() {
        val line = HarvestScanDiag.liveLine(
            HarvestScanLiveSample(
                cluster = false,
                convertMs = 12L,
                preprocessMs = 18L,
                inferMs = 340L,
                parseMs = 9L,
                totalMs = 380L,
                fps = 2.6f,
                rawFruitCount = 2,
                acceptedFruitCount = 1,
                selectedConfidence = 0.812f,
                selectedArea = 0.164f,
                reason = TargetRejectReason.TARGET_NOT_STABLE,
                streak = 3,
                focusRequested = true,
                autoCapture = false,
                layoutCached = true,
                layout = YoloTensorLayout.CHANNEL_MAJOR,
                layoutFromShape = true,
                rawLeafCount = 1,
                selectedType = ScanTargetType.TOMATO_FRUIT,
                selectedClassIndex = 0,
                class0Score = 0.812f,
                class1Score = 0.040f,
                sigmoidApplied = false,
                rawMax0 = 0.812f,
                rawMax1 = 0.040f
            )
        )
        assertTrue(line.contains("mode=SINGLE_LIVE"))
        assertTrue(line.contains("inferMs=340"))
        assertTrue(line.contains("preMs=18"))
        assertTrue(line.contains("fps=2.6"))
        assertTrue(line.contains("rawFruit=2"))
        assertTrue(line.contains("rawLeaf=1"))
        assertTrue(line.contains("acceptedFruit=1"))
        assertTrue(line.contains("layout=CH"))
        assertTrue(line.contains("fromShape=1"))
        assertTrue(line.contains("sel=FRUIT"))
        assertTrue(line.contains("class=0"))
        assertTrue(line.contains("c0=0.812"))
        assertTrue(line.contains("c1=0.040"))
        assertTrue(line.contains("sigmoid=0"))
        assertTrue(line.contains("reason=TARGET_NOT_STABLE"))
        assertTrue(line.contains("streak=3"))
        assertTrue(line.contains("focus=1"))
        assertTrue(line.contains("capture=0"))
        assertTrue(line.contains("cached=1"))
        assertFalse(line.lowercase().contains("utc"))
    }

    @Test
    fun detectionAndHarvestThresholdsAreUnchanged() {
        assertEquals(0.65f, TargetDetectionCalibration.MIN_DETECTION_CONFIDENCE, 0.0001f)
        assertEquals(0.10f, TargetDetectionCalibration.MIN_TARGET_AREA_RATIO, 0.0001f)
        assertEquals(0.22f, TargetDetectionCalibration.CENTER_TOLERANCE, 0.0001f)
        assertEquals(8, TargetDetectionCalibration.REQUIRED_STABLE_FRAMES)
        assertEquals(0.55f, TargetDetectionCalibration.STABILITY_IOU_MIN, 0.0001f)
        assertEquals(0.05f, TargetDetectionCalibration.STABILITY_CENTER_MAX_DELTA, 0.0001f)
        assertEquals(0.08f, TargetDetectionCalibration.STABILITY_AREA_MAX_DELTA, 0.0001f)
        assertEquals(2500L, TargetDetectionCalibration.AUTO_CAPTURE_COOLDOWN_MS)
        assertEquals(0.10f, TargetDetectionCalibration.CROP_PADDING_RATIO, 0.0001f)
        assertEquals(0.25f, TargetDetectionCalibration.DETECTOR_SCORE_FLOOR, 0.0001f)
        assertEquals(2, TargetDetectionCalibration.FOCUS_MIN_STREAK)
        assertEquals(0.45f, TargetDetectionCalibration.NMS_IOU_THRESHOLD, 0.0001f)
        assertEquals(640, TargetDetectionCalibration.ANALYSIS_MAX_SIDE)
        assertEquals(40f, TomatoRipenessCalibration.PROJECT.redDominantMinPercent, 0.0001f)
        assertEquals(0.44f, TomatoRipenessCalibration.PROJECT.darkGreenMaxValueMean, 0.0001f)
        assertTrue(HybridScanConfig.USE_EXPERIMENTAL_TFLITE_DETECTOR)
        assertFalse(HybridScanConfig.ENABLE_MULTI_TOMATO_CLUSTER_PROCESSING)
        assertFalse(TomatoYoloDiagnostics.ENABLED)
    }

    @Test
    fun singleTomatoStillNeedsEightStableFramesForOneCapture() {
        val controller = TargetAutoCaptureController(
            expected = ScanTargetType.TOMATO_FRUIT,
            modelReady = true,
            clock = { 8_000L }
        )
        val frame = HarvestArgbFrame(IntArray(20 * 20) { 0xFFCC3333.toInt() }, 20, 20)
        val hit = TargetDetection(
            targetType = ScanTargetType.TOMATO_FRUIT,
            confidence = 0.80f,
            boundingBox = NormalizedRect(0.25f, 0.25f, 0.75f, 0.75f),
            timestampMs = 1L
        )
        var captures = 0
        var lastStreak = 0
        repeat(TargetDetectionCalibration.REQUIRED_STABLE_FRAMES + 4) {
            val tick = controller.onFrame(frame, listOf(hit), analyzing = false)
            lastStreak = tick.stabilityStreak
            if (tick.captureFrame != null) captures++
        }
        assertEquals(1, captures)
        assertEquals(TargetDetectionCalibration.REQUIRED_STABLE_FRAMES + 4, lastStreak)
    }
}
