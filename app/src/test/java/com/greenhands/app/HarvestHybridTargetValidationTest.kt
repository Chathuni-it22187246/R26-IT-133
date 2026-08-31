package com.greenhands.app

import com.greenhands.app.harvest.detection.HarvestScanPhase
import com.greenhands.app.harvest.detection.HybridScanConfig
import com.greenhands.app.harvest.detection.HybridTargetValidator
import com.greenhands.app.harvest.detection.HybridValidationStatus
import com.greenhands.app.harvest.detection.ScanTargetType
import com.greenhands.app.harvest.detection.TargetAutoCaptureController
import com.greenhands.app.harvest.detection.TargetDetectionCalibration
import com.greenhands.app.harvest.domain.HarvestArgbFrame
import com.greenhands.app.harvest.domain.HsvAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HarvestHybridTargetValidationTest {

    private val validator = HybridTargetValidator()
    private val hsv = HsvAnalyzer(centerCropFraction = 1f)
    private val captureCalibration = TargetDetectionCalibration.PROJECT

    @Test
    fun experimentalTfliteFlagIsOffForNormalScans() {
        assertFalse(HybridScanConfig.USE_EXPERIMENTAL_TFLITE_DETECTOR)
    }

    @Test
    fun greenRoundFruitIsAccepted() {
        val result = fruit(circle(GREEN))
        assertEquals(describe(result.features), HybridValidationStatus.VALID_FRUIT_TARGET, result.status)
    }

    @Test
    fun yellowOrangeRoundFruitIsAccepted() {
        assertEquals(
            HybridValidationStatus.VALID_FRUIT_TARGET,
            fruit(circle(YELLOW)).status
        )
        assertEquals(
            HybridValidationStatus.VALID_FRUIT_TARGET,
            fruit(circle(ORANGE)).status
        )
    }

    @Test
    fun redRoundFruitIsAccepted() {
        assertEquals(HybridValidationStatus.VALID_FRUIT_TARGET, fruit(circle(RED)).status)
    }

    @Test
    fun greenRectangularBackgroundIsRejectedAsFruit() {
        val result = fruit(solid(GREEN))
        assertNotEquals(HybridValidationStatus.VALID_FRUIT_TARGET, result.status)
        assertTrue(
            result.status == HybridValidationStatus.SHAPE_NOT_FRUIT_LIKE ||
                result.status == HybridValidationStatus.NO_FRUIT_LIKE_REGION
        )
    }

    @Test
    fun redFlatBackgroundIsRejectedAsFruit() {
        val result = fruit(solid(RED))
        assertNotEquals(HybridValidationStatus.VALID_FRUIT_TARGET, result.status)
        assertTrue(
            result.status == HybridValidationStatus.SHAPE_NOT_FRUIT_LIKE ||
                result.status == HybridValidationStatus.NO_FRUIT_LIKE_REGION
        )
    }

    @Test
    fun fruitTargetTooSmallIsRejected() {
        val result = fruit(circle(RED, cx = 40, cy = 40, radius = 7))
        assertEquals(HybridValidationStatus.TARGET_TOO_SMALL, result.status)
    }

    @Test
    fun fruitTargetOffCenterIsRejected() {
        val result = fruit(circle(RED, cx = 62, cy = 62, radius = 16))
        assertEquals(HybridValidationStatus.TARGET_NOT_CENTERED, result.status)
    }

    @Test
    fun unstableFruitNeverAutoCaptures() {
        var now = 4_000L
        val controller = TargetAutoCaptureController(
            expected = ScanTargetType.TOMATO_FRUIT,
            clock = { now }
        )
        val left = circle(RED, cx = 32, cy = 40, radius = 16)
        val right = circle(RED, cx = 48, cy = 40, radius = 16)
        var captures = 0
        repeat(captureCalibration.requiredStableFrames + 6) { i ->
            val frame = if (i % 2 == 0) left else right
            val tick = controller.onHybridFrame(
                frame,
                validator.validate(frame, ScanTargetType.TOMATO_FRUIT, now),
                analyzing = false
            )
            if (tick.captureFrame != null) captures++
        }
        assertEquals(0, captures)
    }

    @Test
    fun stableFruitTriggersExactlyOneAutoCapture() {
        assertEquals(1, captureCount(circle(RED), ScanTargetType.TOMATO_FRUIT))
    }

    @Test
    fun greenElongatedLeafIsAccepted() {
        val result = leaf(ellipse(GREEN, rx = 28, ry = 11))
        assertEquals(describe(result.features), HybridValidationStatus.VALID_LEAF_TARGET, result.status)
    }

    @Test
    fun broadFrontOnLeafIsAccepted() {
        val result = leaf(diamond(GREEN, rx = 22, ry = 21))
        assertEquals(describe(result.features), HybridValidationStatus.VALID_LEAF_TARGET, result.status)
    }

    @Test
    fun lessElongatedLeafIsAccepted() {
        val result = leaf(ellipse(GREEN, rx = 22, ry = 18))
        assertEquals(describe(result.features), HybridValidationStatus.VALID_LEAF_TARGET, result.status)
    }

    @Test
    fun yellowBrownUnhealthyLeafIsAccepted() {
        val result = leaf(ellipse(BROWN, rx = 27, ry = 11))
        assertEquals(describe(result.features), HybridValidationStatus.VALID_LEAF_TARGET, result.status)
    }

    @Test
    fun yellowingLeafIsAccepted() {
        val result = leaf(ellipse(YELLOW, rx = 27, ry = 11))
        assertEquals(describe(result.features), HybridValidationStatus.VALID_LEAF_TARGET, result.status)
    }

    @Test
    fun leafOnPalePaperIsAccepted() {
        val result = leaf(ellipse(GREEN, rx = 28, ry = 11, bg = PAPER))
        assertEquals(describe(result.features), HybridValidationStatus.VALID_LEAF_TARGET, result.status)
    }

    @Test
    fun palePaperOnlyIsRejectedAsLeaf() {
        val result = leaf(solid(PAPER))
        assertNotEquals(HybridValidationStatus.VALID_LEAF_TARGET, result.status)
    }

    @Test
    fun blankGrayWallIsRejectedAsLeaf() {
        val result = leaf(solid(GRAY))
        assertNotEquals(HybridValidationStatus.VALID_LEAF_TARGET, result.status)
    }

    @Test
    fun brownTableFillIsRejectedAsLeaf() {
        val result = leaf(solid(BROWN))
        assertNotEquals(HybridValidationStatus.VALID_LEAF_TARGET, result.status)
        assertEquals(HybridValidationStatus.SHAPE_NOT_LEAF_LIKE, result.status)
    }

    @Test
    fun tinyGreenPatchIsRejectedAsLeaf() {
        val result = leaf(circle(GREEN, radius = 6))
        assertNotEquals(HybridValidationStatus.VALID_LEAF_TARGET, result.status)
        assertTrue(
            result.status == HybridValidationStatus.TARGET_TOO_SMALL ||
                result.status == HybridValidationStatus.NO_LEAF_LIKE_REGION ||
                result.status == HybridValidationStatus.INSUFFICIENT_VEGETATION_EVIDENCE
        )
    }

    @Test
    fun leafTargetOffCenterIsRejected() {
        val result = leaf(ellipse(GREEN, cx = 62, cy = 62, rx = 18, ry = 12))
        assertEquals(HybridValidationStatus.TARGET_NOT_CENTERED, result.status)
    }

    @Test
    fun greenCircularObjectIsRejectedAsLeafWhenFruitLikeIndicatorsAgree() {
        val result = leaf(circle(GREEN))
        assertEquals(describe(result.features), HybridValidationStatus.SHAPE_NOT_LEAF_LIKE, result.status)
    }

    @Test
    fun largeGreenRectangularBackgroundIsRejectedAsLeaf() {
        val result = leaf(solid(GREEN))
        assertNotEquals(HybridValidationStatus.VALID_LEAF_TARGET, result.status)
        assertEquals(HybridValidationStatus.SHAPE_NOT_LEAF_LIKE, result.status)
    }

    @Test
    fun unstableLeafNeverAutoCaptures() {
        var now = 4_000L
        val controller = TargetAutoCaptureController(
            expected = ScanTargetType.TOMATO_LEAF,
            clock = { now }
        )
        val left = ellipse(GREEN, cx = 32, cy = 40, rx = 26, ry = 11)
        val right = ellipse(GREEN, cx = 48, cy = 40, rx = 26, ry = 11)
        var captures = 0
        repeat(captureCalibration.requiredStableFrames + 6) { i ->
            val frame = if (i % 2 == 0) left else right
            val tick = controller.onHybridFrame(
                frame,
                validator.validate(frame, ScanTargetType.TOMATO_LEAF, now),
                analyzing = false
            )
            if (tick.captureFrame != null) captures++
        }
        assertEquals(0, captures)
    }

    @Test
    fun stableLeafTriggersExactlyOneAutoCapture() {
        assertEquals(1, captureCount(ellipse(GREEN, rx = 28, ry = 11), ScanTargetType.TOMATO_LEAF))
    }

    @Test
    fun invalidTargetNeverReachesHsvAnalyzer() {
        val controller = TargetAutoCaptureController(expected = ScanTargetType.TOMATO_FRUIT)
        val frame = solid(GRAY)
        var hsvCalls = 0
        repeat(12) {
            val tick = controller.onHybridFrame(
                frame,
                validator.validate(frame, ScanTargetType.TOMATO_FRUIT, 1L),
                analyzing = false
            )
            tick.withCroppedFrame {
                hsvCalls++
                hsv.analyzeFruit(it)
            }
        }
        assertEquals(0, hsvCalls)
    }

    @Test
    fun validCroppedTargetReachesHsvAnalyzerInsteadOfFullFrame() {
        val frame = circle(RED)
        val controller = TargetAutoCaptureController(
            expected = ScanTargetType.TOMATO_FRUIT,
            clock = { 8_000L }
        )
        var analyzed: HarvestArgbFrame? = null
        repeat(captureCalibration.requiredStableFrames + 2) {
            val tick = controller.onHybridFrame(
                frame,
                validator.validate(frame, ScanTargetType.TOMATO_FRUIT, 8_000L),
                analyzing = false
            )
            tick.withCroppedFrame { crop ->
                analyzed = crop
                hsv.analyzeFruit(crop)
            }
        }
        assertNotNull(analyzed)
        val crop = analyzed!!
        assertTrue(crop.width < frame.width || crop.height < frame.height)
        assertTrue(crop.argb.size < frame.argb.size)
        val measured = hsv.analyzeFruit(crop)
        assertTrue(measured.redPercent > 40f)
    }

    @Test
    fun fruitAndLeafModesRemainSeparate() {
        val fruitCircle = circle(GREEN)
        val leafBlob = ellipse(GREEN, rx = 28, ry = 11)
        assertEquals(
            HybridValidationStatus.VALID_FRUIT_TARGET,
            fruit(fruitCircle).status
        )
        assertEquals(
            HybridValidationStatus.SHAPE_NOT_LEAF_LIKE,
            leaf(fruitCircle).status
        )
        assertEquals(
            HybridValidationStatus.VALID_LEAF_TARGET,
            leaf(leafBlob).status
        )
        assertEquals(
            HybridValidationStatus.SHAPE_NOT_FRUIT_LIKE,
            fruit(leafBlob).status
        )
    }

    @Test
    fun cooldownPreventsDuplicateHybridCaptureUntilTargetReturns() {
        var now = 20_000L
        val controller = TargetAutoCaptureController(
            expected = ScanTargetType.TOMATO_FRUIT,
            clock = { now }
        )
        val target = circle(RED)
        val empty = solid(GRAY)

        fun feed(frame: HarvestArgbFrame, n: Int = captureCalibration.requiredStableFrames): Int {
            var captures = 0
            repeat(n) {
                val tick = controller.onHybridFrame(
                    frame,
                    validator.validate(frame, ScanTargetType.TOMATO_FRUIT, now),
                    analyzing = false
                )
                if (tick.captureFrame != null) captures++
            }
            return captures
        }

        assertEquals(1, feed(target))
        assertEquals(HarvestScanPhase.SEARCHING, controller.onHybridFrame(
            empty,
            validator.validate(empty, ScanTargetType.TOMATO_FRUIT, now),
            analyzing = false
        ).phase)
        now += 400L
        assertEquals(0, feed(target))
        now += TargetDetectionCalibration.AUTO_CAPTURE_COOLDOWN_MS
        assertEquals(1, feed(target))
    }

    private fun captureCount(frame: HarvestArgbFrame, type: ScanTargetType): Int {
        val controller = TargetAutoCaptureController(expected = type, clock = { 9_000L })
        var captures = 0
        repeat(captureCalibration.requiredStableFrames + 4) {
            val tick = controller.onHybridFrame(
                frame,
                validator.validate(frame, type, 9_000L),
                analyzing = false
            )
            if (tick.captureFrame != null) captures++
        }
        return captures
    }

    private fun fruit(frame: HarvestArgbFrame) =
        validator.validate(frame, ScanTargetType.TOMATO_FRUIT, 1L)

    private fun leaf(frame: HarvestArgbFrame) =
        validator.validate(frame, ScanTargetType.TOMATO_LEAF, 1L)

    private fun describe(features: com.greenhands.app.harvest.detection.HybridTargetFeatures): String =
        "area=${"%.3f".format(features.areaRatio)} extent=${"%.3f".format(features.extent)} " +
            "aspect=${"%.3f".format(features.aspectElongation)} circ=${"%.3f".format(features.circularity)} " +
            "color=${"%.3f".format(features.tomatoColorPixelRatio)} veg=${"%.3f".format(features.vegetationPixelRatio)} " +
            "cx=${"%.3f".format(features.centerX)} cy=${"%.3f".format(features.centerY)} " +
            "dom=${features.dominantColorFamily}"

    companion object {
        private const val SIZE = 80
        private const val GREEN = 0xFF28A032.toInt()
        private const val YELLOW = 0xFFDCB41E.toInt()
        private const val ORANGE = 0xFFE67A1E.toInt()
        private const val RED = 0xFFC41E26.toInt()
        private const val BROWN = 0xFF503216.toInt()
        private const val GRAY = 0xFF6E6E6E.toInt()
        private const val PAPER = 0xFFF4F1EA.toInt()

        private fun solid(color: Int, width: Int = SIZE, height: Int = SIZE) =
            HarvestArgbFrame(IntArray(width * height) { color }, width, height)

        private fun circle(
            color: Int,
            cx: Int = SIZE / 2,
            cy: Int = SIZE / 2,
            radius: Int = 18,
            bg: Int = GRAY
        ): HarvestArgbFrame {
            val pixels = IntArray(SIZE * SIZE) { bg }
            val r2 = radius * radius
            for (y in 0 until SIZE) {
                for (x in 0 until SIZE) {
                    val dx = x - cx
                    val dy = y - cy
                    if (dx * dx + dy * dy <= r2) pixels[y * SIZE + x] = color
                }
            }
            return HarvestArgbFrame(pixels, SIZE, SIZE)
        }

        private fun ellipse(
            color: Int,
            cx: Int = SIZE / 2,
            cy: Int = SIZE / 2,
            rx: Int,
            ry: Int,
            bg: Int = GRAY
        ): HarvestArgbFrame {
            val pixels = IntArray(SIZE * SIZE) { bg }
            val rx2 = (rx * rx).coerceAtLeast(1).toFloat()
            val ry2 = (ry * ry).coerceAtLeast(1).toFloat()
            for (y in 0 until SIZE) {
                for (x in 0 until SIZE) {
                    val nx = (x - cx).toFloat()
                    val ny = (y - cy).toFloat()
                    if ((nx * nx) / rx2 + (ny * ny) / ry2 <= 1f) {
                        pixels[y * SIZE + x] = color
                    }
                }
            }
            return HarvestArgbFrame(pixels, SIZE, SIZE)
        }

        private fun diamond(
            color: Int,
            cx: Int = SIZE / 2,
            cy: Int = SIZE / 2,
            rx: Int,
            ry: Int,
            bg: Int = GRAY
        ): HarvestArgbFrame {
            val pixels = IntArray(SIZE * SIZE) { bg }
            val rxF = rx.coerceAtLeast(1).toFloat()
            val ryF = ry.coerceAtLeast(1).toFloat()
            for (y in 0 until SIZE) {
                for (x in 0 until SIZE) {
                    val nx = kotlin.math.abs(x - cx) / rxF
                    val ny = kotlin.math.abs(y - cy) / ryF
                    if (nx + ny <= 1f) pixels[y * SIZE + x] = color
                }
            }
            return HarvestArgbFrame(pixels, SIZE, SIZE)
        }
    }
}
