package com.greenhands.app

import com.greenhands.app.harvest.detection.HarvestFruitStabilityDiag
import com.greenhands.app.harvest.detection.HarvestScanDiag
import com.greenhands.app.harvest.detection.HarvestScanLiveSample
import com.greenhands.app.harvest.detection.LetterboxTransform
import com.greenhands.app.harvest.detection.NormalizedRect
import com.greenhands.app.harvest.detection.PreviewFillCenterMapper
import com.greenhands.app.harvest.detection.ScanTargetType
import com.greenhands.app.harvest.detection.SingleTomatoCandidateDiag
import com.greenhands.app.harvest.detection.SingleTomatoFruitSelector
import com.greenhands.app.harvest.detection.TargetDetection
import com.greenhands.app.harvest.detection.TargetDetectionCalibration
import com.greenhands.app.harvest.detection.TargetRejectReason
import com.greenhands.app.harvest.detection.TargetValidator
import com.greenhands.app.harvest.detection.YoloTensorLayout
import com.greenhands.app.harvest.detection.YoloV8OutputParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HarvestSingleTomatoB5Test {

    private val validator = TargetValidator()
    private val identity416 = LetterboxTransform(
        sourceWidth = 416,
        sourceHeight = 416,
        inputWidth = 416,
        inputHeight = 416,
        gain = 1f,
        padX = 0f,
        padY = 0f
    )

    @Before
    fun resetDiag() {
        HarvestFruitStabilityDiag.reset()
    }

    @Test
    fun localizedCenteredFruitIsSelectedAndDrawn() {
        val fruit = fruit(0.78f, NormalizedRect(0.32f, 0.30f, 0.68f, 0.70f))
        val result = validator.validate(
            expected = ScanTargetType.TOMATO_FRUIT,
            detections = listOf(fruit),
            modelReady = true,
            stable = true
        )
        assertTrue(result.readyForAutoCapture)
        assertEquals(fruit.boundingBox, result.detection!!.boundingBox)
        assertEquals(fruit.boundingBox, SingleTomatoFruitSelector.overlayBox(result.detection))
        val cand = SingleTomatoCandidateDiag.observe(listOf(fruit), result.detection)
        assertEquals(SingleTomatoCandidateDiag.WHY_LOCK_READY, cand.selWhy)
        assertEquals(SingleTomatoCandidateDiag.OVERLAY_SEL, cand.overlayState)
        assertEquals("OK", cand.topFruits.single().tag)
    }

    @Test
    fun oversizedButNotOppositeEdgeClampedFruitIsStillSelected() {
        val oversized = fruit(0.86f, NormalizedRect(0.05f, 0.08f, 0.94f, 0.93f))
        assertFalse(SingleTomatoFruitSelector.isSeverelyClamped(oversized.boundingBox))
        assertTrue(oversized.boundingBox.area > 0.70f)
        val result = validator.validate(
            expected = ScanTargetType.TOMATO_FRUIT,
            detections = listOf(oversized),
            modelReady = true,
            stable = true
        )
        assertTrue(result.readyForManualCapture)
        assertEquals(oversized.boundingBox, result.detection!!.boundingBox)
        assertEquals(oversized.boundingBox, SingleTomatoFruitSelector.overlayBox(result.detection))
        assertEquals("OK", SingleTomatoCandidateDiag.classify(oversized))
    }

    @Test
    fun lockReadyTightFruitBeatsHigherConfidenceClampedFalsePositive() {
        val clamped = fruit(0.94f, NormalizedRect(0f, 0f, 1f, 1f))
        val tight = fruit(0.70f, NormalizedRect(0.34f, 0.32f, 0.66f, 0.68f))
        val selected = SingleTomatoFruitSelector.select(listOf(clamped, tight))
        assertEquals(tight.boundingBox, selected!!.boundingBox)
        assertEquals(0.70f, selected.confidence, 0.0f)
    }

    @Test
    fun higherConfidenceOversizedNonClampedFruitBeatsTighterLowerConfidenceFruit() {
        val oversized = fruit(0.90f, NormalizedRect(0.06f, 0.08f, 0.93f, 0.92f))
        val tight = fruit(0.72f, NormalizedRect(0.36f, 0.34f, 0.64f, 0.66f))
        val selected = SingleTomatoFruitSelector.select(listOf(oversized, tight))
        assertEquals(oversized.boundingBox, selected!!.boundingBox)
        assertEquals(0.90f, selected.confidence, 0.0f)
    }

    @Test
    fun nmsDropsOverlappingLowerConfidenceTighterFruit() {
        val large = fruit(0.91f, NormalizedRect(0.20f, 0.20f, 0.80f, 0.80f))
        val tight = fruit(0.70f, NormalizedRect(0.25f, 0.25f, 0.75f, 0.75f))
        assertTrue(large.boundingBox.iou(tight.boundingBox) > TargetDetectionCalibration.NMS_IOU_THRESHOLD)
        val kept = YoloV8OutputParser.nms(listOf(large, tight), TargetDetectionCalibration.NMS_IOU_THRESHOLD)
        assertEquals(1, kept.size)
        assertEquals(large.boundingBox, kept.single().boundingBox)
        assertEquals(large.boundingBox, SingleTomatoFruitSelector.select(kept)!!.boundingBox)
    }

    @Test
    fun nmsDoesNotDropOverlappingLeafVersusFruit() {
        val fruitBox = fruit(0.70f, NormalizedRect(0.28f, 0.28f, 0.72f, 0.72f))
        val leaf = leaf(0.88f, NormalizedRect(0.26f, 0.26f, 0.74f, 0.74f))
        val kept = YoloV8OutputParser.nms(listOf(fruitBox, leaf), TargetDetectionCalibration.NMS_IOU_THRESHOLD)
        assertEquals(2, kept.size)
        val result = validator.validate(
            expected = ScanTargetType.TOMATO_FRUIT,
            detections = kept,
            modelReady = true,
            stable = true
        )
        assertEquals(ScanTargetType.TOMATO_FRUIT, result.detection!!.targetType)
        assertEquals(fruitBox.boundingBox, SingleTomatoFruitSelector.overlayBox(result.detection))
    }

    @Test
    fun leafOnlyYieldsWrongTargetAndNoFruitOverlay() {
        val leaf = leaf(0.84f, NormalizedRect(0.20f, 0.20f, 0.80f, 0.80f))
        val result = validator.validate(
            expected = ScanTargetType.TOMATO_FRUIT,
            detections = listOf(leaf),
            modelReady = true
        )
        assertEquals(TargetRejectReason.WRONG_TARGET, result.reason)
        assertNull(SingleTomatoFruitSelector.overlayBox(result.detection))
        val cand = SingleTomatoCandidateDiag.observe(listOf(leaf), result.detection)
        assertEquals(0.84f, cand.leafMaxConf!!, 0.0f)
        assertNull(cand.fruitMaxConf)
        assertEquals(SingleTomatoCandidateDiag.WHY_NONE, cand.selWhy)
        assertEquals(SingleTomatoCandidateDiag.OVERLAY_NONE, cand.overlayState)
        val snap = HarvestFruitStabilityDiag.observe(listOf(leaf), result.detection, 0)
        assertEquals(HarvestFruitStabilityDiag.RESET_WRONG_TARGET, snap.resetReason)
    }

    @Test
    fun clampedFruitPlusLeafReportsClampedResetAndWrongTargetMessagePath() {
        val clamped = fruit(0.88f, NormalizedRect(0f, 0.10f, 1f, 0.90f))
        val leaf = leaf(0.60f, NormalizedRect(0.10f, 0.10f, 0.40f, 0.40f))
        val result = validator.validate(
            expected = ScanTargetType.TOMATO_FRUIT,
            detections = listOf(clamped, leaf),
            modelReady = true
        )
        assertEquals(TargetRejectReason.WRONG_TARGET, result.reason)
        assertEquals(ScanTargetType.TOMATO_LEAF, result.detection!!.targetType)
        assertNull(SingleTomatoFruitSelector.overlayBox(result.detection))
        val cand = SingleTomatoCandidateDiag.observe(listOf(clamped, leaf), result.detection)
        assertEquals("CLAMP", cand.topFruits.first().tag)
        assertEquals(SingleTomatoCandidateDiag.WHY_NONE, cand.selWhy)
        val snap = HarvestFruitStabilityDiag.observe(listOf(clamped, leaf), result.detection, 0)
        assertEquals(HarvestFruitStabilityDiag.RESET_CLAMPED, snap.resetReason)
        assertEquals(0.88f, snap.fruitMaxConf!!, 0.0f)
        assertNull(snap.fruitSelConf)
    }

    @Test
    fun parserArgmaxChoosesLeafWhenClass1ExceedsClass0() {
        val channels = Array(6) { FloatArray(1) }
        channels[0][0] = 208f
        channels[1][0] = 208f
        channels[2][0] = 140f
        channels[3][0] = 140f
        channels[4][0] = 0.40f
        channels[5][0] = 0.42f
        val parsed = YoloV8OutputParser.parseChannelsFirstResult(
            channels = channels,
            inputWidth = 416,
            inputHeight = 416,
            transform = identity416,
            timestampMs = 1L
        )
        assertEquals(1, parsed.detections.size)
        assertEquals(ScanTargetType.TOMATO_LEAF, parsed.detections.single().targetType)
        assertEquals(0, parsed.preNmsFruit)
        assertEquals(1, parsed.preNmsLeaf)
        assertEquals(0, parsed.nmsDropped)
    }

    @Test
    fun parserReportsNmsDroppedWhenOverlappingFruitPredictionsExist() {
        val channels = Array(6) { FloatArray(2) }
        writeFruit(channels, 0, cx = 208f, cy = 208f, w = 250f, h = 250f, score = 0.90f)
        writeFruit(channels, 1, cx = 208f, cy = 208f, w = 200f, h = 200f, score = 0.70f)
        val parsed = YoloV8OutputParser.parseChannelsFirstResult(
            channels = channels,
            inputWidth = 416,
            inputHeight = 416,
            transform = identity416,
            timestampMs = 1L
        )
        assertEquals(2, parsed.preNmsFruit)
        assertEquals(1, parsed.detections.size)
        assertEquals(1, parsed.nmsDropped)
        assertEquals(0.90f, parsed.detections.single().confidence, 0.001f)
    }

    @Test
    fun cornerAndSingleEdgeFruitStayPlausible() {
        val corner = fruit(0.80f, NormalizedRect(0.00f, 0.62f, 0.38f, 1.00f))
        val edge = fruit(0.80f, NormalizedRect(0.00f, 0.30f, 0.48f, 0.70f))
        assertTrue(SingleTomatoFruitSelector.isPlausibleFruitBox(corner.boundingBox))
        assertTrue(SingleTomatoFruitSelector.isPlausibleFruitBox(edge.boundingBox))
        assertEquals("OFF", SingleTomatoCandidateDiag.classify(corner))
        val edgeResult = validator.validate(
            expected = ScanTargetType.TOMATO_FRUIT,
            detections = listOf(edge),
            modelReady = true,
            stable = true
        )
        assertTrue(edgeResult.detected)
        assertTrue(edgeResult.correctTarget)
        assertNotNull(SingleTomatoFruitSelector.overlayBox(edgeResult.detection))
    }

    @Test
    fun letterboxThenFillCenterKeepsCenteredBoxNearPreviewCenter() {
        val landscape = LetterboxTransform(
            sourceWidth = 640,
            sourceHeight = 480,
            inputWidth = 416,
            inputHeight = 416,
            gain = 416f / 640f,
            padX = 0f,
            padY = (416f - 480f * 416f / 640f) / 2f
        )
        val portrait = LetterboxTransform(
            sourceWidth = 480,
            sourceHeight = 640,
            inputWidth = 416,
            inputHeight = 416,
            gain = 416f / 640f,
            padX = (416f - 480f * 416f / 640f) / 2f,
            padY = 0f
        )
        listOf(landscape to (640 to 480), portrait to (480 to 640)).forEach { (transform, src) ->
            val box = YoloV8OutputParser.xywhToSourceNormalized(
                cx = 208f,
                cy = 208f,
                w = 80f,
                h = 80f,
                inputWidth = 416,
                inputHeight = 416,
                transform = transform
            )
            assertEquals(0.5f, box.centerX, 0.03f)
            assertEquals(0.5f, box.centerY, 0.03f)
            val mapped = PreviewFillCenterMapper.mapToView(
                box = box,
                frameWidth = src.first,
                frameHeight = src.second,
                viewWidth = 720,
                viewHeight = 1600
            )
            assertEquals(0.5f, mapped.centerX, 0.08f)
            assertEquals(0.5f, mapped.centerY, 0.08f)
        }
    }

    @Test
    fun liveLineIncludesCandidateQualityWithoutChangingThresholds() {
        val box = NormalizedRect(0.30f, 0.30f, 0.70f, 0.70f)
        val line = HarvestScanDiag.liveLine(
            HarvestScanLiveSample(
                cluster = false,
                convertMs = 8L,
                preprocessMs = 15L,
                inferMs = 420L,
                parseMs = 4L,
                totalMs = 660L,
                fps = 1.5f,
                rawFruitCount = 2,
                acceptedFruitCount = 0,
                selectedConfidence = 0.48f,
                selectedArea = box.area,
                reason = TargetRejectReason.LOW_CONFIDENCE,
                streak = 0,
                focusRequested = false,
                autoCapture = false,
                layoutCached = true,
                layout = YoloTensorLayout.CHANNEL_MAJOR,
                fruitSelConf = 0.48f,
                fruitMaxConf = 0.48f,
                fruitBox = box,
                fruitArea = box.area,
                fruitDx = 0f,
                fruitDy = 0f,
                leafMaxConf = 0.41f,
                nmsDropped = 1,
                preNmsFruit = 3,
                preNmsLeaf = 1,
                selWhy = SingleTomatoCandidateDiag.WHY_PLAUSIBLE,
                overlayState = SingleTomatoCandidateDiag.OVERLAY_SEL,
                topFruits = "0.48/0.160/+0.00/+0.00/LOW"
            )
        )
        assertTrue(line.contains("leafMax=0.410"))
        assertTrue(line.contains("nmsDrop=1"))
        assertTrue(line.contains("preNmsF=3"))
        assertTrue(line.contains("preNmsL=1"))
        assertTrue(line.contains("selWhy=PLAUSIBLE"))
        assertTrue(line.contains("overlay=SEL"))
        assertTrue(line.contains("top=0.48/0.160/+0.00/+0.00/LOW"))
        assertEquals(0.65f, TargetDetectionCalibration.MIN_DETECTION_CONFIDENCE, 0.0f)
        assertEquals(0.25f, TargetDetectionCalibration.DETECTOR_SCORE_FLOOR, 0.0f)
        assertEquals(8, TargetDetectionCalibration.REQUIRED_STABLE_FRAMES)
        assertEquals(0.10f, TargetDetectionCalibration.MIN_TARGET_AREA_RATIO, 0.0f)
        assertEquals(0.22f, TargetDetectionCalibration.CENTER_TOLERANCE, 0.0f)
        assertEquals(0.45f, TargetDetectionCalibration.NMS_IOU_THRESHOLD, 0.0f)
    }

    @Test
    fun clusterLiveStillOmitsSingleTomatoCandidateFields() {
        val line = HarvestScanDiag.liveLine(
            HarvestScanLiveSample(
                cluster = true,
                convertMs = 8L,
                preprocessMs = 15L,
                inferMs = 420L,
                parseMs = 4L,
                totalMs = 660L,
                fps = 1.5f,
                rawFruitCount = 2,
                acceptedFruitCount = 2,
                selectedConfidence = 0.80f,
                selectedArea = 0.12f,
                reason = null,
                streak = 8,
                focusRequested = false,
                autoCapture = false,
                layoutCached = true,
                leafMaxConf = 0.20f,
                nmsDropped = 2,
                selWhy = SingleTomatoCandidateDiag.WHY_LOCK_READY,
                overlayState = SingleTomatoCandidateDiag.OVERLAY_SEL,
                topFruits = "should-not-print"
            )
        )
        assertTrue(line.contains("mode=CLUSTER_LIVE"))
        assertFalse(line.contains("leafMax="))
        assertFalse(line.contains("nmsDrop="))
        assertFalse(line.contains("selWhy="))
        assertFalse(line.contains("top="))
        assertNotEquals("should-not-print", line)
    }

    private fun fruit(confidence: Float, box: NormalizedRect) = TargetDetection(
        targetType = ScanTargetType.TOMATO_FRUIT,
        confidence = confidence,
        boundingBox = box,
        timestampMs = 1L
    )

    private fun leaf(confidence: Float, box: NormalizedRect) = TargetDetection(
        targetType = ScanTargetType.TOMATO_LEAF,
        confidence = confidence,
        boundingBox = box,
        timestampMs = 1L
    )

    private fun writeFruit(
        channels: Array<FloatArray>,
        index: Int,
        cx: Float,
        cy: Float,
        w: Float,
        h: Float,
        score: Float
    ) {
        channels[0][index] = cx
        channels[1][index] = cy
        channels[2][index] = w
        channels[3][index] = h
        channels[4][index] = score
        channels[5][index] = 0.05f
    }
}
