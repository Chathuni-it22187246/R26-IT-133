package com.greenhands.app

import com.greenhands.app.harvest.detection.HarvestScanDiag
import com.greenhands.app.harvest.detection.HarvestScanLiveSample
import com.greenhands.app.harvest.detection.LetterboxTransform
import com.greenhands.app.harvest.detection.ScanTargetType
import com.greenhands.app.harvest.detection.TargetRejectReason
import com.greenhands.app.harvest.detection.TargetValidator
import com.greenhands.app.harvest.detection.YoloOutputLayoutSelector
import com.greenhands.app.harvest.detection.YoloTensorLayout
import com.greenhands.app.harvest.detection.YoloV8OutputParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HarvestYoloLayoutTest {

    private val identity416 = LetterboxTransform(
        sourceWidth = 416,
        sourceHeight = 416,
        inputWidth = 416,
        inputHeight = 416,
        gain = 1f,
        padX = 0f,
        padY = 0f
    )

    @Test
    fun shape1x6xNAlwaysChannelMajorEvenIfHeuristicPrefersPred() {
        val shape = intArrayOf(1, 6, 3549)
        assertEquals(YoloTensorLayout.CHANNEL_MAJOR, YoloOutputLayoutSelector.fromOutputShape(shape))
        assertEquals(YoloTensorLayout.CHANNEL_MAJOR, YoloOutputLayoutSelector.resolve(shape))
        assertTrue(YoloOutputLayoutSelector.isFromShape(shape))
        val heuristic = YoloOutputLayoutSelector()
        repeat(3) { heuristic.observeAndChoose(0.20f, 0.90f) }
        assertEquals(YoloTensorLayout.PRED_MAJOR, heuristic.cached)
        assertNotEquals(heuristic.cached, YoloOutputLayoutSelector.resolve(shape))
    }

    @Test
    fun shape1xNx6AlwaysPredMajor() {
        val shape = intArrayOf(1, 3549, 6)
        assertEquals(YoloTensorLayout.PRED_MAJOR, YoloOutputLayoutSelector.fromOutputShape(shape))
        assertEquals(YoloTensorLayout.PRED_MAJOR, YoloOutputLayoutSelector.resolve(shape))
        assertTrue(YoloOutputLayoutSelector.isFromShape(shape))
    }

    @Test
    fun channelMajorFruitIsNotRelabelledAsLeaf() {
        val channels = 6
        val preds = 4
        val floats = FloatArray(channels * preds)
        writeChannelMajor(floats, preds, prediction = 0, cx = 208f, cy = 208f, w = 120f, h = 120f, class0 = 0.91f, class1 = 0.05f)
        val decoded = YoloV8OutputParser.fillChannelsFromFlat(
            floats, channels, preds, channelMajor = true, dest = null
        )
        val detections = YoloV8OutputParser.parseChannelsFirst(
            channels = decoded,
            inputWidth = 416,
            inputHeight = 416,
            transform = identity416,
            timestampMs = 1L
        )
        val fruit = detections.single { it.targetType == ScanTargetType.TOMATO_FRUIT }
        assertEquals(ScanTargetType.TOMATO_FRUIT, fruit.targetType)
        assertEquals(0, fruit.classIndex)
        assertEquals(0.91f, fruit.confidence, 0.0001f)
        assertEquals(0.91f, fruit.class0Score!!, 0.0001f)
        assertEquals(0.05f, fruit.class1Score!!, 0.0001f)
        assertFalse(detections.any { it.targetType == ScanTargetType.TOMATO_LEAF })
    }

    @Test
    fun predMajorDecodeOfPredMajorFlatKeepsFruitClass() {
        val channels = 6
        val preds = 3
        val floats = FloatArray(channels * preds)
        writePredMajor(floats, channels, prediction = 1, cx = 208f, cy = 208f, w = 100f, h = 100f, class0 = 0.88f, class1 = 0.10f)
        val decoded = YoloV8OutputParser.fillChannelsFromFlat(
            floats, channels, preds, channelMajor = false, dest = null
        )
        val detections = YoloV8OutputParser.parseChannelsFirst(
            channels = decoded,
            inputWidth = 416,
            inputHeight = 416,
            transform = identity416,
            timestampMs = 2L
        )
        val fruit = detections.single()
        assertEquals(ScanTargetType.TOMATO_FRUIT, fruit.targetType)
        assertEquals(0.88f, fruit.confidence, 0.0001f)
    }

    @Test
    fun leafOnlyDetectionsAreWrongTargetOnFruitScan() {
        val channels = emptyChannels(1)
        channels[0][0] = 208f
        channels[1][0] = 208f
        channels[2][0] = 140f
        channels[3][0] = 140f
        channels[4][0] = 0.12f
        channels[5][0] = 0.93f
        val detections = YoloV8OutputParser.parseChannelsFirst(
            channels = channels,
            inputWidth = 416,
            inputHeight = 416,
            transform = identity416,
            timestampMs = 3L
        )
        assertEquals(ScanTargetType.TOMATO_LEAF, detections.single().targetType)
        val fruitScan = TargetValidator().validate(
            expected = ScanTargetType.TOMATO_FRUIT,
            detections = detections,
            modelReady = true,
            stable = true
        )
        val leafScan = TargetValidator().validate(
            expected = ScanTargetType.TOMATO_LEAF,
            detections = detections,
            modelReady = true,
            stable = true
        )
        assertEquals(TargetRejectReason.WRONG_TARGET, fruitScan.reason)
        assertFalse(fruitScan.correctTarget)
        assertTrue(leafScan.correctTarget)
        assertNull(leafScan.reason)
    }

    @Test
    fun scoreAboveOneIsNotClamped() {
        val channels = emptyChannels(1)
        channels[0][0] = 208f
        channels[1][0] = 208f
        channels[2][0] = 120f
        channels[3][0] = 120f
        channels[4][0] = 1.046f
        channels[5][0] = 0.20f
        val detections = YoloV8OutputParser.parseChannelsFirst(
            channels = channels,
            inputWidth = 416,
            inputHeight = 416,
            transform = identity416,
            timestampMs = 4L
        )
        val fruit = detections.single()
        assertEquals(ScanTargetType.TOMATO_FRUIT, fruit.targetType)
        assertEquals(1.046f, fruit.confidence, 0.0001f)
        assertEquals(1.046f, fruit.class0Score!!, 0.0001f)
        assertTrue(fruit.confidence > 1f)
        val line = HarvestScanDiag.fmtScore(fruit.confidence)
        assertEquals("1.046", line)
        assertFalse(line == "1.000")
        val live = HarvestScanDiag.liveLine(
            HarvestScanLiveSample(
                cluster = false,
                convertMs = 1L,
                preprocessMs = 1L,
                inferMs = 1L,
                parseMs = 1L,
                totalMs = 4L,
                fps = 1f,
                rawFruitCount = 1,
                acceptedFruitCount = 0,
                selectedConfidence = fruit.confidence,
                selectedArea = fruit.boundingBox.area,
                reason = TargetRejectReason.WRONG_TARGET,
                streak = 0,
                focusRequested = false,
                autoCapture = false,
                layoutCached = true,
                layout = YoloTensorLayout.CHANNEL_MAJOR,
                class0Score = fruit.class0Score,
                class1Score = fruit.class1Score
            )
        )
        assertTrue(live.contains("score=1.046"))
        assertTrue(live.contains("c0=1.046"))
        assertFalse(live.contains("score=1.000"))
    }

    @Test
    fun tensorAndScoreLinesDistinguishRawFromInterpreted() {
        val tensor = HarvestScanDiag.tensorLine(
            outputShape = intArrayOf(1, 6, 3549),
            classCount = 2,
            layout = YoloTensorLayout.CHANNEL_MAJOR,
            fromShape = true
        )
        assertTrue(tensor.contains("mode=YOLO_TENSOR"))
        assertTrue(tensor.contains("shape=[1, 6, 3549]"))
        assertTrue(tensor.contains("classes=2"))
        assertTrue(tensor.contains("layout=CH"))
        assertTrue(tensor.contains("fromShape=1"))
        val scores = HarvestScanDiag.scoreRangeLine(
            sigmoidApplied = false,
            rawMax0 = 1.046f,
            rawMax1 = 0.88f,
            interpMax0 = 1.046f,
            interpMax1 = 0.88f,
            boxMin = 0.02f,
            boxMax = 0.98f
        )
        assertTrue(scores.contains("mode=YOLO_SCORES"))
        assertTrue(scores.contains("sigmoid=0"))
        assertTrue(scores.contains("rawMax0=1.046"))
        assertTrue(scores.contains("interpMax0=1.046"))
    }

    @Test
    fun ambiguousSquareShapeUsesDocumentedChannelMajorFallback() {
        assertNull(YoloOutputLayoutSelector.fromOutputShape(intArrayOf(1, 20, 20)))
        assertEquals(
            YoloTensorLayout.CHANNEL_MAJOR,
            YoloOutputLayoutSelector.resolve(intArrayOf(1, 20, 20))
        )
        assertFalse(YoloOutputLayoutSelector.isFromShape(intArrayOf(1, 20, 20)))
    }

    private fun emptyChannels(preds: Int): Array<FloatArray> = Array(6) { FloatArray(preds) }

    private fun writeChannelMajor(
        floats: FloatArray,
        preds: Int,
        prediction: Int,
        cx: Float,
        cy: Float,
        w: Float,
        h: Float,
        class0: Float,
        class1: Float
    ) {
        floats[0 * preds + prediction] = cx
        floats[1 * preds + prediction] = cy
        floats[2 * preds + prediction] = w
        floats[3 * preds + prediction] = h
        floats[4 * preds + prediction] = class0
        floats[5 * preds + prediction] = class1
    }

    private fun writePredMajor(
        floats: FloatArray,
        channels: Int,
        prediction: Int,
        cx: Float,
        cy: Float,
        w: Float,
        h: Float,
        class0: Float,
        class1: Float
    ) {
        val base = prediction * channels
        floats[base + 0] = cx
        floats[base + 1] = cy
        floats[base + 2] = w
        floats[base + 3] = h
        floats[base + 4] = class0
        floats[base + 5] = class1
    }
}
