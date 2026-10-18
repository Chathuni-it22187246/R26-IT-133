package com.greenhands.app

import com.greenhands.app.harvest.detection.LetterboxTransform
import com.greenhands.app.harvest.detection.NormalizedRect
import com.greenhands.app.harvest.detection.ScanTargetType
import com.greenhands.app.harvest.detection.TargetDetection
import com.greenhands.app.harvest.detection.TargetValidator
import com.greenhands.app.harvest.detection.YoloLetterbox
import com.greenhands.app.harvest.detection.YoloV8OutputParser
import com.greenhands.app.harvest.domain.HarvestArgbFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HarvestYoloV8ParserTest {

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
    fun channelsFirstLayoutReadsFruitAndLeafScores() {
        val channels = emptyChannels(preds = 4)
        writeXywh(channels, 0, cx = 208f, cy = 208f, w = 120f, h = 120f)
        channels[4][0] = 0.91f
        channels[5][0] = 0.05f
        writeXywh(channels, 1, cx = 80f, cy = 80f, w = 80f, h = 80f)
        channels[4][1] = 0.10f
        channels[5][1] = 0.88f

        val detections = YoloV8OutputParser.parseChannelsFirst(
            channels = channels,
            inputWidth = 416,
            inputHeight = 416,
            transform = identity416,
            timestampMs = 1L
        )
        assertEquals(2, detections.size)
        val fruit = detections.first { it.targetType == ScanTargetType.TOMATO_FRUIT }
        val leaf = detections.first { it.targetType == ScanTargetType.TOMATO_LEAF }
        assertEquals(0.91f, fruit.confidence, 0.001f)
        assertEquals(0.88f, leaf.confidence, 0.001f)
        assertTrue(fruit.boundingBox.centerX in 0.45f..0.55f)
        assertTrue(leaf.boundingBox.centerX < 0.35f)
    }

    @Test
    fun classZeroIsTomatoFruitAndClassOneIsTomatoLeaf() {
        assertEquals(
            ScanTargetType.TOMATO_FRUIT,
            YoloV8OutputParser.classIndexToType(0, listOf("tomato_fruit", "tomato_leaf"))
        )
        assertEquals(
            ScanTargetType.TOMATO_LEAF,
            YoloV8OutputParser.classIndexToType(1, listOf("tomato_fruit", "tomato_leaf"))
        )
    }

    @Test
    fun lowScoresAreFilteredBeforeNms() {
        val channels = emptyChannels(preds = 2)
        writeXywh(channels, 0, cx = 208f, cy = 208f, w = 100f, h = 100f)
        channels[4][0] = 0.10f
        channels[5][0] = 0.08f
        val detections = YoloV8OutputParser.parseChannelsFirst(
            channels = channels,
            inputWidth = 416,
            inputHeight = 416,
            transform = identity416,
            timestampMs = 1L
        )
        assertTrue(detections.isEmpty())
    }

    @Test
    fun nmsKeepsHighestOverlappingBoxOfSameClass() {
        val a = detection(ScanTargetType.TOMATO_FRUIT, 0.92f, NormalizedRect(0.20f, 0.20f, 0.70f, 0.70f))
        val b = detection(ScanTargetType.TOMATO_FRUIT, 0.70f, NormalizedRect(0.22f, 0.22f, 0.72f, 0.72f))
        val kept = YoloV8OutputParser.nms(listOf(a, b), iouThreshold = 0.45f)
        assertEquals(1, kept.size)
        assertEquals(0.92f, kept[0].confidence, 0.001f)
    }

    @Test
    fun nmsDoesNotSuppressDifferentClasses() {
        val fruit = detection(ScanTargetType.TOMATO_FRUIT, 0.90f, NormalizedRect(0.20f, 0.20f, 0.70f, 0.70f))
        val leaf = detection(ScanTargetType.TOMATO_LEAF, 0.88f, NormalizedRect(0.22f, 0.22f, 0.72f, 0.72f))
        val kept = YoloV8OutputParser.nms(listOf(fruit, leaf), iouThreshold = 0.45f)
        assertEquals(2, kept.size)
    }

    @Test
    fun fruitScanStillAcceptsOnlyTomatoFruitFromParsedOutput() {
        val channels = emptyChannels(preds = 1)
        writeXywh(channels, 0, cx = 208f, cy = 208f, w = 140f, h = 140f)
        channels[4][0] = 0.12f
        channels[5][0] = 0.93f
        val detections = YoloV8OutputParser.parseChannelsFirst(
            channels = channels,
            inputWidth = 416,
            inputHeight = 416,
            transform = identity416,
            timestampMs = 1L
        )
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
        assertFalse(fruitScan.correctTarget)
        assertTrue(leafScan.correctTarget)
        assertEquals(ScanTargetType.TOMATO_LEAF, detections.single().targetType)
    }

    @Test
    fun letterbox640x480PadsTopAndBottomTo416() {
        val frame = HarvestArgbFrame(IntArray(640 * 480) { 0xFF112233.toInt() }, 640, 480)
        val boxed = YoloLetterbox.fit(frame, 416, 416)
        assertEquals(416, boxed.frame.width)
        assertEquals(416, boxed.frame.height)
        assertEquals(416f / 640f, boxed.transform.gain, 0.001f)
        assertEquals(0f, boxed.transform.padX, 0.6f)
        assertTrue(boxed.transform.padY > 40f)
        val pad = boxed.frame.argb[10]
        assertEquals(YoloLetterbox.PAD_RGB, (pad shr 16) and 0xFF)
        assertEquals(YoloLetterbox.PAD_RGB, (pad shr 8) and 0xFF)
    }

    @Test
    fun pixelBoxesMapBackThroughLetterboxToSource() {
        val transform = LetterboxTransform(
            sourceWidth = 640,
            sourceHeight = 480,
            inputWidth = 416,
            inputHeight = 416,
            gain = 416f / 640f,
            padX = 0f,
            padY = (416f - 480f * 416f / 640f) / 2f
        )
        val box = YoloV8OutputParser.xywhToSourceNormalized(
            cx = 208f,
            cy = 208f,
            w = 64f,
            h = 48f,
            inputWidth = 416,
            inputHeight = 416,
            transform = transform
        )
        assertTrue(box.centerX in 0.45f..0.55f)
        assertTrue(box.centerY in 0.45f..0.55f)
    }

    @Test
    fun detectionsFirstLayoutIsTransposedToTheSameParser() {
        val rows = Array(3) { FloatArray(6) }
        rows[0][0] = 208f
        rows[0][1] = 208f
        rows[0][2] = 100f
        rows[0][3] = 100f
        rows[0][4] = 0.87f
        rows[0][5] = 0.02f
        val detections = YoloV8OutputParser.parseDetectionsFirst(
            rows = rows,
            inputWidth = 416,
            inputHeight = 416,
            transform = identity416,
            timestampMs = 2L
        )
        assertEquals(1, detections.size)
        assertEquals(ScanTargetType.TOMATO_FRUIT, detections[0].targetType)
        assertEquals(0.87f, detections[0].confidence, 0.001f)
    }

    @Test
    fun channelMajorFlatBufferMatchesYoloV8_1_6_N() {
        val channels = 6
        val preds = 8
        val floats = FloatArray(channels * preds)
        floats[4 * preds + 3] = 0.77f
        floats[5 * preds + 3] = 0.11f
        val decoded = YoloV8OutputParser.channelsFromFlat(floats, channels, preds, channelMajor = true)
        assertEquals(0.77f, decoded[4][3], 0.0001f)
        assertEquals(0.11f, decoded[5][3], 0.0001f)
        assertEquals(0.77f, YoloV8OutputParser.maxClassScore(decoded, 0), 0.0001f)
        val asPredMajor = YoloV8OutputParser.channelsFromFlat(floats, channels, preds, channelMajor = false)
        assertTrue(YoloV8OutputParser.maxClassScore(asPredMajor, 0) < 0.77f)
    }

    @Test
    fun logitsAreActivatedToProbabilities() {
        val channels = emptyChannels(preds = 1)
        writeXywh(channels, 0, cx = 208f, cy = 208f, w = 120f, h = 120f)
        channels[4][0] = 3.0f
        channels[5][0] = -2.0f
        val detections = YoloV8OutputParser.parseChannelsFirst(
            channels = channels,
            inputWidth = 416,
            inputHeight = 416,
            transform = identity416,
            timestampMs = 3L
        )
        assertEquals(1, detections.size)
        assertEquals(ScanTargetType.TOMATO_FRUIT, detections[0].targetType)
        assertTrue(detections[0].confidence > 0.9f)
        assertTrue(detections[0].confidence <= 1f)
    }

    private fun emptyChannels(preds: Int): Array<FloatArray> =
        Array(6) { FloatArray(preds) }

    private fun writeXywh(
        channels: Array<FloatArray>,
        index: Int,
        cx: Float,
        cy: Float,
        w: Float,
        h: Float
    ) {
        channels[0][index] = cx
        channels[1][index] = cy
        channels[2][index] = w
        channels[3][index] = h
    }

    private fun detection(
        type: ScanTargetType,
        confidence: Float,
        box: NormalizedRect
    ) = TargetDetection(type, confidence, box, 1L)
}
