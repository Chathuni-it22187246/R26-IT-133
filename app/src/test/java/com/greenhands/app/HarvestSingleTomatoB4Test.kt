package com.greenhands.app

import com.greenhands.app.R
import com.greenhands.app.harvest.detection.MultiTomatoFruitSelector
import com.greenhands.app.harvest.detection.NormalizedRect
import com.greenhands.app.harvest.detection.PreviewFillCenterMapper
import com.greenhands.app.harvest.detection.ScanTargetType
import com.greenhands.app.harvest.detection.SingleTomatoFruitSelector
import com.greenhands.app.harvest.detection.SingleTomatoGuidance
import com.greenhands.app.harvest.detection.TargetAutoCaptureController
import com.greenhands.app.harvest.detection.TargetDetection
import com.greenhands.app.harvest.detection.TargetDetectionCalibration
import com.greenhands.app.harvest.detection.TargetRejectReason
import com.greenhands.app.harvest.detection.TargetValidator
import com.greenhands.app.harvest.detection.YoloOutputLayoutSelector
import com.greenhands.app.harvest.detection.YoloTensorLayout
import com.greenhands.app.harvest.detection.YoloV8OutputParser
import com.greenhands.app.harvest.domain.HarvestArgbFrame
import com.greenhands.app.harvest.domain.HarvestDecisionEngine
import com.greenhands.app.harvest.domain.HsvAnalyzer
import com.greenhands.app.harvest.domain.HsvColorBin
import com.greenhands.app.harvest.domain.HsvColorBins
import com.greenhands.app.harvest.domain.HsvConverter
import com.greenhands.app.harvest.domain.TomatoRipenessCalibration
import com.greenhands.app.harvest.model.HarvestDecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HarvestSingleTomatoB4Test {

    private val validator = TargetValidator()
    private val calibration = TargetDetectionCalibration.PROJECT

    @Test
    fun prefersLockReadyCenteredFruitOverHigherConfidenceClampedBox() {
        val clamped = fruit(0.93f, NormalizedRect(0.00f, 0.00f, 1.00f, 1.00f))
        val valid = fruit(0.71f, NormalizedRect(0.30f, 0.30f, 0.70f, 0.70f))
        val selected = SingleTomatoFruitSelector.select(listOf(clamped, valid))
        assertEquals(0.71f, selected!!.confidence, 0.0f)
        assertEquals(valid.boundingBox, selected.boundingBox)
        val result = validator.validate(
            expected = ScanTargetType.TOMATO_FRUIT,
            detections = listOf(clamped, valid),
            modelReady = true,
            stable = true
        )
        assertTrue(result.readyForManualCapture)
        assertTrue(result.readyForAutoCapture)
        assertEquals(valid.boundingBox, result.detection!!.boundingBox)
        assertEquals(0.71f, result.confidence, 0.0f)
        assertNull(result.reason)
    }

    @Test
    fun severelyClampedFruitIsRejectedEvenWhenHighestConfidence() {
        val clamped = fruit(0.88f, NormalizedRect(0.00f, 0.20f, 1.00f, 0.80f))
        assertTrue(SingleTomatoFruitSelector.isSeverelyClamped(clamped.boundingBox))
        val result = validator.validate(
            expected = ScanTargetType.TOMATO_FRUIT,
            detections = listOf(clamped),
            modelReady = true,
            stable = true
        )
        assertEquals(TargetRejectReason.TARGET_NOT_DETECTED, result.reason)
        assertFalse(result.readyForManualCapture)
        assertFalse(result.readyForAutoCapture)
        assertNull(SingleTomatoFruitSelector.overlayBox(result.detection))
        assertNull(SingleTomatoFruitSelector.select(listOf(clamped)))
    }

    @Test
    fun fruitTouchingOneEdgeOrACornerIsNotRejectedAsClamped() {
        val leftEdge = NormalizedRect(0.00f, 0.28f, 0.58f, 0.72f)
        val corner = NormalizedRect(0.00f, 0.60f, 0.40f, 1.00f)
        assertFalse(SingleTomatoFruitSelector.isSeverelyClamped(leftEdge))
        assertFalse(SingleTomatoFruitSelector.isSeverelyClamped(corner))
        assertTrue(SingleTomatoFruitSelector.isPlausibleFruitBox(leftEdge))
        assertTrue(SingleTomatoFruitSelector.isPlausibleFruitBox(corner))
        val edgeFruit = fruit(0.80f, leftEdge)
        val result = validator.validate(
            expected = ScanTargetType.TOMATO_FRUIT,
            detections = listOf(edgeFruit),
            modelReady = true,
            stable = true
        )
        assertTrue(result.detected)
        assertTrue(result.correctTarget)
        assertEquals(edgeFruit.boundingBox, result.detection!!.boundingBox)
        assertNotNull(SingleTomatoFruitSelector.overlayBox(result.detection))
    }

    @Test
    fun leafOnlyDoesNotProduceSingleTomatoFruitOverlay() {
        val leaf = TargetDetection(
            targetType = ScanTargetType.TOMATO_LEAF,
            confidence = 0.90f,
            boundingBox = NormalizedRect(0.20f, 0.20f, 0.80f, 0.80f),
            timestampMs = 1L
        )
        val result = validator.validate(
            expected = ScanTargetType.TOMATO_FRUIT,
            detections = listOf(leaf),
            modelReady = true,
            stable = true
        )
        assertEquals(TargetRejectReason.WRONG_TARGET, result.reason)
        assertEquals(ScanTargetType.TOMATO_LEAF, result.detection!!.targetType)
        assertNull(SingleTomatoFruitSelector.overlayBox(result.detection))
        assertFalse(result.readyForManualCapture)
        assertEquals(
            R.string.harvest_detect_not_fruit_like,
            SingleTomatoGuidance.stringRes(result.reason)
        )
    }

    @Test
    fun lowConfidenceFruitShowsGuidanceAndCannotCapture() {
        val low = fruit(0.40f, NormalizedRect(0.30f, 0.30f, 0.70f, 0.70f))
        val result = validator.validate(
            expected = ScanTargetType.TOMATO_FRUIT,
            detections = listOf(low),
            modelReady = true,
            stable = true
        )
        assertEquals(TargetRejectReason.LOW_CONFIDENCE, result.reason)
        assertTrue(result.detected)
        assertTrue(result.correctTarget)
        assertFalse(result.readyForManualCapture)
        assertFalse(result.readyForAutoCapture)
        assertNotNull(SingleTomatoFruitSelector.overlayBox(result.detection))
        assertEquals(
            R.string.harvest_detect_low_confidence_fruit,
            SingleTomatoGuidance.stringRes(result.reason)
        )
        val controller = TargetAutoCaptureController(
            expected = ScanTargetType.TOMATO_FRUIT,
            modelReady = true,
            clock = { 8_000L }
        )
        val frame = solidFrame()
        repeat(calibration.requiredStableFrames + 2) {
            val tick = controller.onFrame(frame, listOf(low), analyzing = false)
            assertNull(tick.captureFrame)
            assertEquals(0, tick.stabilityStreak)
        }
    }

    @Test
    fun validCenteredFruitStillReachesManualAndAutoCapture() {
        val hit = fruit(0.80f, NormalizedRect(0.28f, 0.28f, 0.72f, 0.72f))
        val unlocked = validator.validate(
            expected = ScanTargetType.TOMATO_FRUIT,
            detections = listOf(hit),
            modelReady = true,
            stable = false
        )
        assertTrue(unlocked.readyForManualCapture)
        assertFalse(unlocked.readyForAutoCapture)
        assertEquals(TargetRejectReason.TARGET_NOT_STABLE, unlocked.reason)
        val controller = TargetAutoCaptureController(
            expected = ScanTargetType.TOMATO_FRUIT,
            modelReady = true,
            clock = { 8_000L }
        )
        val frame = solidFrame()
        var captures = 0
        var lastStreak = 0
        repeat(calibration.requiredStableFrames + 3) {
            val tick = controller.onFrame(frame, listOf(hit), analyzing = false)
            lastStreak = tick.stabilityStreak
            if (tick.captureFrame != null) captures++
        }
        assertEquals(calibration.requiredStableFrames + 3, lastStreak)
        assertEquals(1, captures)
    }

    @Test
    fun lowConfidenceIsNotPromotedToLockReadyWhenItIsTheOnlyPlausibleFruit() {
        val clamped = fruit(0.91f, NormalizedRect(0.00f, 0.00f, 1.00f, 1.00f))
        val low = fruit(0.48f, NormalizedRect(0.32f, 0.32f, 0.68f, 0.68f))
        val result = validator.validate(
            expected = ScanTargetType.TOMATO_FRUIT,
            detections = listOf(clamped, low),
            modelReady = true,
            stable = true
        )
        assertEquals(0.48f, result.confidence, 0.0f)
        assertEquals(TargetRejectReason.LOW_CONFIDENCE, result.reason)
        assertFalse(result.readyForManualCapture)
        assertFalse(result.readyForAutoCapture)
    }

    @Test
    fun singleTomatoGuidanceMapsRejectReasonsWithoutChangingStabilityCopy() {
        assertEquals(
            R.string.harvest_detect_low_confidence_fruit,
            SingleTomatoGuidance.stringRes(TargetRejectReason.LOW_CONFIDENCE)
        )
        assertEquals(
            R.string.harvest_detect_no_fruit,
            SingleTomatoGuidance.stringRes(TargetRejectReason.TARGET_NOT_DETECTED)
        )
        assertEquals(
            R.string.harvest_detect_too_small_fruit,
            SingleTomatoGuidance.stringRes(TargetRejectReason.TARGET_TOO_SMALL)
        )
        assertEquals(
            R.string.harvest_detect_not_centered_fruit,
            SingleTomatoGuidance.stringRes(TargetRejectReason.TARGET_NOT_CENTERED)
        )
        assertEquals(
            R.string.harvest_detect_not_fruit_like,
            SingleTomatoGuidance.stringRes(TargetRejectReason.WRONG_TARGET)
        )
        assertNull(SingleTomatoGuidance.stringRes(TargetRejectReason.TARGET_NOT_STABLE))
        assertNull(SingleTomatoGuidance.stringRes(TargetRejectReason.MODEL_UNAVAILABLE))
    }

    @Test
    fun mappingCoversLandscapeAndPortraitAnalysisOnPortraitPreviews() {
        val center = NormalizedRect(0.45f, 0.42f, 0.55f, 0.58f)
        val corner = NormalizedRect(0.02f, 0.02f, 0.18f, 0.16f)
        listOf(
            Triple(640, 480, 720 to 1600),
            Triple(640, 480, 1080 to 2400),
            Triple(640, 480, 720 to 1280),
            Triple(480, 640, 720 to 1600),
            Triple(480, 640, 1080 to 2400),
            Triple(480, 640, 720 to 1280)
        ).forEach { (fw, fh, view) ->
            val mappedCenter = PreviewFillCenterMapper.mapToView(center, fw, fh, view.first, view.second)
            assertEquals("centerX $fw x $fh -> ${view.first}x${view.second}", 0.5f, mappedCenter.centerX, 0.08f)
            assertEquals("centerY $fw x $fh -> ${view.first}x${view.second}", 0.5f, mappedCenter.centerY, 0.08f)
            val mappedCorner = PreviewFillCenterMapper.mapToView(corner, fw, fh, view.first, view.second)
            assertTrue(mappedCorner.left <= mappedCorner.right)
            assertTrue(mappedCorner.top <= mappedCorner.bottom)
        }
    }

    @Test
    fun rotate90And270KeepAnalysisDimensionsFromRuntimeBuffers() {
        val landscape = HarvestArgbFrame(IntArray(640 * 480) { 1 }, 640, 480, rotationDegrees = 90)
        assertEquals(640, landscape.width)
        assertEquals(480, landscape.height)
        assertEquals(90, landscape.rotationDegrees)
        val portrait = HarvestArgbFrame(IntArray(480 * 640) { 1 }, 480, 640, rotationDegrees = 270)
        assertEquals(480, portrait.width)
        assertEquals(640, portrait.height)
        assertEquals(270, portrait.rotationDegrees)
    }

    @Test
    fun lockThresholdsParserFloorAndLayoutStayUnchanged() {
        assertEquals(0.65f, TargetDetectionCalibration.MIN_DETECTION_CONFIDENCE, 0.0f)
        assertEquals(0.25f, TargetDetectionCalibration.DETECTOR_SCORE_FLOOR, 0.0f)
        assertEquals(0.10f, TargetDetectionCalibration.MIN_TARGET_AREA_RATIO, 0.0f)
        assertEquals(0.22f, TargetDetectionCalibration.CENTER_TOLERANCE, 0.0f)
        assertEquals(8, TargetDetectionCalibration.REQUIRED_STABLE_FRAMES)
        assertEquals(2500L, TargetDetectionCalibration.AUTO_CAPTURE_COOLDOWN_MS)
        assertEquals(0.45f, TargetDetectionCalibration.NMS_IOU_THRESHOLD, 0.0f)
        assertEquals(YoloV8OutputParser.CLASS_TOMATO_FRUIT, 0)
        assertEquals(YoloV8OutputParser.CLASS_TOMATO_LEAF, 1)
        assertEquals(
            YoloTensorLayout.CHANNEL_MAJOR,
            YoloOutputLayoutSelector.fromOutputShape(intArrayOf(1, 6, 3549))
        )
        assertEquals(YoloTensorLayout.CHANNEL_MAJOR, YoloOutputLayoutSelector.AMBIGUOUS_FALLBACK)
    }

    @Test
    fun leafScanStillAcceptsCenteredLeafIncludingFullFrameLeaf() {
        val leaf = TargetDetection(
            targetType = ScanTargetType.TOMATO_LEAF,
            confidence = 0.86f,
            boundingBox = NormalizedRect(0.00f, 0.00f, 1.00f, 1.00f),
            timestampMs = 1L
        )
        val result = validator.validate(
            expected = ScanTargetType.TOMATO_LEAF,
            detections = listOf(leaf),
            modelReady = true,
            stable = true
        )
        assertTrue(result.readyForAutoCapture)
        assertEquals(leaf.boundingBox, result.detection!!.boundingBox)
        assertNull(SingleTomatoFruitSelector.overlayBox(leaf))
    }

    @Test
    fun clusterSelectorStillKeepsAFullFrameFruitThatSingleTomatoRejects() {
        val full = fruit(0.90f, NormalizedRect(0.00f, 0.00f, 1.00f, 1.00f))
        val cluster = MultiTomatoFruitSelector.select(listOf(full))
        assertEquals(1, cluster.fruits.size)
        assertNull(SingleTomatoFruitSelector.select(listOf(full)))
    }

    @Test
    fun hsvBinsAndEarlyHarvestRuleStayUnchanged() {
        assertEquals(HsvColorBin.YELLOW, HsvColorBins.classify(HsvConverter.fromRgb(230, 140, 20)))
        assertEquals(HsvColorBin.RED, HsvColorBins.classify(HsvConverter.fromRgb(220, 20, 20)))
        assertEquals(40f, TomatoRipenessCalibration.PROJECT.redDominantMinPercent, 0.0f)
        val frame = HarvestArgbFrame(IntArray(16 * 16) { 0xFFCC1A1A.toInt() }, 16, 16)
        val hsv = HsvAnalyzer(centerCropFraction = 1f).analyzeFruit(frame)
        assertTrue(hsv.redPercent > 80f)
        val beforeWindow = com.greenhands.app.harvest.domain.MaturityCalculator.assess(40, 65, 90)
        val engine = HarvestDecisionEngine()
        val decision = engine.decideTomato(hsv, beforeWindow)
        assertEquals(HarvestDecision.READY_TO_HARVEST, decision.decision)
    }

    @Test
    fun parserScoreFloorStillDropsFruitBelowTwentyFive() {
        val channels = Array(6) { FloatArray(1) }
        channels[0][0] = 208f
        channels[1][0] = 208f
        channels[2][0] = 120f
        channels[3][0] = 120f
        channels[4][0] = 0.20f
        channels[5][0] = 0.05f
        val detections = YoloV8OutputParser.parseChannelsFirst(
            channels = channels,
            inputWidth = 416,
            inputHeight = 416,
            transform = com.greenhands.app.harvest.detection.LetterboxTransform(
                sourceWidth = 416,
                sourceHeight = 416,
                inputWidth = 416,
                inputHeight = 416,
                gain = 1f,
                padX = 0f,
                padY = 0f
            ),
            timestampMs = 1L
        )
        assertTrue(detections.isEmpty())
    }

    private fun fruit(confidence: Float, box: NormalizedRect) = TargetDetection(
        targetType = ScanTargetType.TOMATO_FRUIT,
        confidence = confidence,
        boundingBox = box,
        timestampMs = 1L
    )

    private fun solidFrame() = HarvestArgbFrame(
        IntArray(24 * 24) { 0xFFCC3333.toInt() },
        24,
        24
    )
}
