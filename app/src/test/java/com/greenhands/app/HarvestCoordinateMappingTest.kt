package com.greenhands.app

import com.greenhands.app.harvest.detection.LetterboxTransform
import com.greenhands.app.harvest.detection.NormalizedRect
import com.greenhands.app.harvest.detection.PreviewFillCenterMapper
import com.greenhands.app.harvest.detection.ScanTargetType
import com.greenhands.app.harvest.detection.TargetAutoCaptureController
import com.greenhands.app.harvest.detection.TargetDetection
import com.greenhands.app.harvest.detection.TargetDetectionCalibration
import com.greenhands.app.harvest.detection.TargetRegionCropper
import com.greenhands.app.harvest.detection.TargetRejectReason
import com.greenhands.app.harvest.detection.TargetValidator
import com.greenhands.app.harvest.detection.YoloLetterbox
import com.greenhands.app.harvest.detection.YoloV8OutputParser
import com.greenhands.app.harvest.domain.HarvestArgbFrame
import com.greenhands.app.harvest.domain.HarvestArgbRotate
import com.greenhands.app.harvest.domain.HarvestRgbaConverter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class HarvestCoordinateMappingTest {

    private val calibration = TargetDetectionCalibration.PROJECT
    private val validator = TargetValidator(calibration)

    @Test
    fun analysisCenterMapsToPreviewCenter() {
        val box = NormalizedRect(0.45f, 0.42f, 0.55f, 0.58f)
        val mapped = PreviewFillCenterMapper.mapToView(box, 480, 640, 720, 1600)
        assertEquals(0.5f, mapped.centerX, 0.03f)
        assertEquals(0.5f, mapped.centerY, 0.03f)
    }

    @Test
    fun analysisCornersStayOnMatchingEdgesWhenAspectMatches() {
        val mapped = PreviewFillCenterMapper.mapToView(
            box = NormalizedRect(0f, 0f, 1f, 1f),
            frameWidth = 480,
            frameHeight = 640,
            viewWidth = 480,
            viewHeight = 640
        )
        assertEquals(0f, mapped.left, 0.001f)
        assertEquals(0f, mapped.top, 0.001f)
        assertEquals(1f, mapped.right, 0.001f)
        assertEquals(1f, mapped.bottom, 0.001f)
    }

    @Test
    fun insetOverlayViewportDisplacesOffCenterBoxVersusFullPreview() {
        val box = NormalizedRect(0.05f, 0.60f, 0.40f, 0.98f)
        val onPreview = PreviewFillCenterMapper.mapToView(box, 480, 640, 720, 1600)
        val onInsetPane = PreviewFillCenterMapper.mapToView(box, 480, 640, 680, 900)
        assertTrue(abs(onPreview.centerX - onInsetPane.centerX) > 0.05f ||
            abs(onPreview.centerY - onInsetPane.centerY) > 0.02f)
    }

    @Test
    fun rotate90And270MatchTwoStepGeometry() {
        val src = intArrayOf(1, 2, 3, 4, 5, 6)
        val rot90 = HarvestArgbRotate.apply(src, 2, 3, 90)
        assertEquals(3, rot90.width)
        assertEquals(2, rot90.height)
        assertEquals(5, rot90.argb[0])
        assertEquals(2, rot90.argb[5])
        val rot270 = HarvestArgbRotate.apply(src, 2, 3, 270)
        assertEquals(3, rot270.width)
        assertEquals(2, rot270.height)
        assertEquals(2, rot270.argb[0])
        assertEquals(5, rot270.argb[5])
        val packed = packedUnique(2, 3)
        val decoded90 = HarvestRgbaConverter.decode(packed, 2, 3, 4, 8, maxSide = 3, rotationDegrees = 90)
        val step90 = twoStep(packed, 2, 3, 90)
        assertEquals(step90.width, decoded90.width)
        assertEquals(step90.height, decoded90.height)
        org.junit.Assert.assertArrayEquals(step90.argb, decoded90.argb)
        val decoded270 = HarvestRgbaConverter.decode(packed, 2, 3, 4, 8, maxSide = 3, rotationDegrees = 270)
        val step270 = twoStep(packed, 2, 3, 270)
        org.junit.Assert.assertArrayEquals(step270.argb, decoded270.argb)
    }

    @Test
    fun letterboxRoundTripKeepsPortraitAndLandscapeCenters() {
        assertLetterboxRoundTrip(640, 480)
        assertLetterboxRoundTrip(480, 640)
    }

    @Test
    fun a12OffCenterExampleFailsCenterGateBySpec() {
        val dx = -0.279f
        val dy = 0.280f
        val box = NormalizedRect(
            left = 0f,
            top = (0.5f + dy) * 2f - 1f,
            right = (0.5f + dx) * 2f,
            bottom = 1f
        )
        assertEquals(0.221f, box.centerX, 0.002f)
        assertEquals(0.780f, box.centerY, 0.002f)
        val result = validator.validate(
            expected = ScanTargetType.TOMATO_FRUIT,
            detections = listOf(detection(0.710f, box)),
            modelReady = true,
            stable = true
        )
        assertEquals(TargetRejectReason.TARGET_NOT_CENTERED, result.reason)
        assertFalse(result.centered)
        assertFalse(result.readyForManualCapture)
        assertFalse(result.readyForAutoCapture)
        assertTrue(abs(dx) > TargetDetectionCalibration.CENTER_TOLERANCE)
        assertTrue(abs(dy) > TargetDetectionCalibration.CENTER_TOLERANCE)
    }

    @Test
    fun clampedBoundaryBoxPullsCenterTowardTheEdge() {
        val box = NormalizedRect(-0.10f, 0.40f, 0.40f, 1.20f).clamp()
        assertEquals(0f, box.left, 0.0f)
        assertEquals(1f, box.bottom, 0.0f)
        assertTrue(box.centerX < 0.5f)
        assertTrue(box.centerY > 0.5f)
    }

    @Test
    fun hsvCropUsesAnalysisPixelsWithPaddingNotPreviewMapping() {
        val width = 100
        val height = 80
        val argb = IntArray(width * height) { i ->
            val x = i % width
            val y = i / width
            if (x in 10 until 40 && y in 16 until 64) 0xFFCC2020.toInt() else 0xFFFFFFFF.toInt()
        }
        val frame = HarvestArgbFrame(argb, width, height, rotationDegrees = 90)
        val box = NormalizedRect(0.10f, 0.20f, 0.40f, 0.80f)
        val crop = TargetRegionCropper().crop(frame, box)
        val padded = box.padded(TargetDetectionCalibration.CROP_PADDING_RATIO)
        assertEquals((padded.right * width).toInt() - (padded.left * width).toInt(), crop.width)
        assertEquals((padded.bottom * height).toInt() - (padded.top * height).toInt(), crop.height)
        assertEquals(90, crop.rotationDegrees)
        assertTrue(crop.argb.any { it == 0xFFCC2020.toInt() })
        assertTrue(crop.argb.any { it == 0xFFFFFFFF.toInt() })
        val previewMapped = PreviewFillCenterMapper.mapToView(box, width, height, 720, 1600)
        assertNotEquals(previewMapped.left, box.left, 0.001f)
        val wrongX0 = (previewMapped.left * width).toInt()
        val analysisX0 = (padded.left * width).toInt()
        assertTrue(abs(wrongX0 - analysisX0) > 1)
    }

    @Test
    fun confidenceDipResetsStreakBeforeEightEvenIfBoxIouIsHigh() {
        val controller = TargetAutoCaptureController(
            expected = ScanTargetType.TOMATO_FRUIT,
            modelReady = true,
            clock = { 8_000L }
        )
        val frame = HarvestArgbFrame(IntArray(40 * 40) { 0xFFCC3333.toInt() }, 40, 40)
        val box = NormalizedRect(0.28f, 0.28f, 0.72f, 0.72f)
        repeat(5) {
            val tick = controller.onFrame(frame, listOf(detection(0.80f, box)), false)
            assertTrue(tick.stabilityStreak in 1..5)
            assertNull(tick.captureFrame)
        }
        val dip = controller.onFrame(frame, listOf(detection(0.50f, box)), false)
        assertEquals(5, dip.stabilityStreak)
        assertEquals(TargetRejectReason.LOW_CONFIDENCE, dip.validation.reason)
        assertNull(dip.captureFrame)
        assertFalse(dip.validation.readyForAutoCapture)
        val resume = controller.onFrame(frame, listOf(detection(0.80f, box)), false)
        assertEquals(6, resume.stabilityStreak)
        assertFalse(resume.validation.readyForAutoCapture)
    }

    @Test
    fun lockReadyUnstableFruitAllowsManualNotAutoCapture() {
        val box = NormalizedRect(0.30f, 0.30f, 0.70f, 0.70f)
        val result = validator.validate(
            expected = ScanTargetType.TOMATO_FRUIT,
            detections = listOf(detection(0.71f, box)),
            modelReady = true,
            stable = false
        )
        assertEquals(TargetRejectReason.TARGET_NOT_STABLE, result.reason)
        assertTrue(result.readyForManualCapture)
        assertFalse(result.readyForAutoCapture)
    }

    private fun assertLetterboxRoundTrip(srcW: Int, srcH: Int) {
        val frame = HarvestArgbFrame(IntArray(srcW * srcH) { 0xFF112233.toInt() }, srcW, srcH)
        val boxed = YoloLetterbox.fit(frame, 416, 416)
        val t = boxed.transform
        val cx = 0.5f * srcW * t.gain + t.padX
        val cy = 0.5f * srcH * t.gain + t.padY
        val recovered = YoloV8OutputParser.xywhToSourceNormalized(
            cx = cx,
            cy = cy,
            w = 0.2f * srcW * t.gain,
            h = 0.2f * srcH * t.gain,
            inputWidth = 416,
            inputHeight = 416,
            transform = t
        )
        assertEquals(0.5f, recovered.centerX, 0.02f)
        assertEquals(0.5f, recovered.centerY, 0.02f)
        assertEquals(416, boxed.frame.width)
        assertEquals(416, boxed.frame.height)
        assertEquals(srcW, t.sourceWidth)
        assertEquals(srcH, t.sourceHeight)
    }

    private fun detection(confidence: Float, box: NormalizedRect): TargetDetection =
        TargetDetection(
            targetType = ScanTargetType.TOMATO_FRUIT,
            confidence = confidence,
            boundingBox = box,
            timestampMs = 1L
        )

    private fun packedUnique(width: Int, height: Int): ByteArray {
        val out = ByteArray(width * height * 4)
        var i = 0
        var v = 10
        repeat(width * height) {
            out[i++] = v.toByte()
            out[i++] = (v + 1).toByte()
            out[i++] = (v + 2).toByte()
            out[i++] = 0xFF.toByte()
            v += 3
        }
        return out
    }

    private fun twoStep(rgba: ByteArray, srcW: Int, srcH: Int, rotation: Int): HarvestArgbFrame {
        val dst = IntArray(srcW * srcH)
        var i = 0
        for (y in 0 until srcH) {
            for (x in 0 until srcW) {
                dst[i++] = HarvestRgbaConverter.readRgba8888(rgba, x, y, 4, srcW * 4)
            }
        }
        return HarvestArgbRotate.apply(dst, srcW, srcH, rotation)
    }
}
