package com.greenhands.app

import com.greenhands.app.harvest.detection.ClassifierLeafRoiFocuser
import com.greenhands.app.harvest.domain.HarvestArgbFrame
import com.greenhands.app.harvest.domain.PlantHealthStatus
import com.greenhands.app.harvest.domain.SimplePlantHealthDecider
import com.greenhands.app.harvest.model.LeafColorMeasurement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HarvestClassifierLeafRoiTest {

    @Test
    fun smallLeafOnPaperIsTightSquare() {
        val crop = leafOnPaper(leafColor = GREEN)
        val roi = ClassifierLeafRoiFocuser.focus(crop)
        assertTrue(roi.usedFocusedRoi)
        assertEquals("focused", roi.source)
        val frame = roi.frame
        assertNotNull(frame)
        assertEquals(frame!!.width, frame.height)
        assertTrue(roi.roiWidth < crop.width)
        assertTrue(roi.vegetationPercent >= 30f)
        val leafFracRoi = frame.argb.count { it == GREEN }.toFloat() / frame.argb.size
        val leafFracOrig = crop.argb.count { it == GREEN }.toFloat() / crop.argb.size
        assertTrue(leafFracRoi > leafFracOrig * 2f)
    }

    @Test
    fun yellowBrownDiseasedLeafOnPaperIsStillFocused() {
        val crop = mixedLeafOnPaper()
        val roi = ClassifierLeafRoiFocuser.focus(crop)
        assertTrue(roi.usedFocusedRoi)
        val frame = roi.frame!!
        assertEquals(frame.width, frame.height)
        val isLeaf = { c: Int -> c == YELLOW || c == BROWN || c == GREEN }
        val leafFracRoi = frame.argb.count(isLeaf).toFloat() / frame.argb.size
        val leafFracOrig = crop.argb.count(isLeaf).toFloat() / crop.argb.size
        assertTrue(leafFracRoi > leafFracOrig * 2f)
    }

    @Test
    fun mostlyYellowDiseasedLeafIsKeptAsWholeLeaf() {
        val crop = mostlyYellowLeafOnPaper()
        val roi = ClassifierLeafRoiFocuser.focus(crop)
        assertTrue(roi.reason, roi.usedFocusedRoi)
        assertTrue(roi.vegetationPercent >= 38f)
        val frame = roi.frame!!
        assertEquals(frame.width, frame.height)
        val yellowOrig = crop.argb.count { it == YELLOW }
        val yellowRoi = frame.argb.count { it == YELLOW }
        assertTrue(yellowRoi >= (yellowOrig * 0.80f).toInt())
        assertTrue(roi.roiWidth * roi.roiHeight < crop.width * crop.height)
        assertTrue(roi.wholeLeafBboxLabel != "none")
        assertTrue(roi.gateBboxLabel.startsWith("["))
    }

    @Test
    fun yellowLeafDoesNotExpandThroughSkinFinger() {
        val crop = yellowLeafHeldBySkin()
        val roi = ClassifierLeafRoiFocuser.focus(crop)
        assertTrue(roi.reason, roi.usedFocusedRoi)
        val frame = roi.frame!!
        val skinOrig = crop.argb.count { it == SKIN }
        val skinRoi = frame.argb.count { it == SKIN }
        assertTrue(skinOrig > 0)
        assertTrue(skinRoi < (skinOrig * 0.35f).toInt())
        assertTrue(roi.bboxBottom < SIZE - 8)
        assertTrue(roi.skinRejectedPixels > 0)
    }

    @Test
    fun yellowBackgroundDoesNotSwallowSmallGreenLeaf() {
        val crop = leafOnPaper(leafColor = GREEN, bg = YELLOW)
        val roi = ClassifierLeafRoiFocuser.focus(crop)
        assertTrue(roi.usedFocusedRoi)
        val frame = roi.frame!!
        assertEquals(frame.width, frame.height)
        assertTrue(roi.roiWidth * roi.roiHeight < crop.width * crop.height * 0.45f)
        val greenFrac = frame.argb.count { it == GREEN }.toFloat() / frame.argb.size
        val yellowFrac = frame.argb.count { it == YELLOW }.toFloat() / frame.argb.size
        assertTrue(greenFrac > yellowFrac)
    }

    @Test
    fun paperOnlyIsUnreliableAndNotClassified() {
        val crop = HarvestArgbFrame(IntArray(SIZE * SIZE) { PAPER }, SIZE, SIZE)
        val roi = ClassifierLeafRoiFocuser.focus(crop)
        assertFalse(roi.usedFocusedRoi)
        assertEquals("unreliable", roi.source)
        assertEquals("no_connected_leaf_region", roi.reason)
    }

    @Test
    fun paddingStaysNearTwelvePercent() {
        assertEquals(0.12f, ClassifierLeafRoiFocuser.PAD_RATIO, 1.0e-6f)
        assertTrue(ClassifierLeafRoiFocuser.PAD_RATIO in 0.10f..0.15f)
    }

    @Test
    fun goodHsvStaysHealthyWhenClassifierRoiWouldFail() {
        val hsv = LeafColorMeasurement(
            sampledPixelCount = 800,
            hueMean = 95f,
            saturationMean = 0.42f,
            valueMean = 0.55f,
            greenPercent = 74f,
            yellowPercent = 10f,
            brownDarkPercent = 6f,
            whitePalePercent = 4f,
            discoloredPercent = 20f,
            otherPercent = 6f
        )
        val assessment = SimplePlantHealthDecider.decide(
            measurement = hsv,
            classifierAvailable = false
        )
        assertEquals(PlantHealthStatus.HEALTHY, assessment.status)
        assertFalse(assessment.scanRequired)
    }

    companion object {
        private const val SIZE = 120
        private const val GREEN = 0xFF28A032.toInt()
        private const val YELLOW = 0xFFDCB41E.toInt()
        private const val BROWN = 0xFF503216.toInt()
        private const val PAPER = 0xFFF4F1EA.toInt()
        private const val SKIN = 0xFFE8B090.toInt()

        private fun leafOnPaper(
            leafColor: Int,
            cx: Int = 38,
            cy: Int = 40,
            rx: Int = 18,
            ry: Int = 22,
            bg: Int = PAPER
        ): HarvestArgbFrame {
            val pixels = IntArray(SIZE * SIZE) { bg }
            fillEllipse(pixels, cx, cy, rx, ry, leafColor)
            return HarvestArgbFrame(pixels, SIZE, SIZE)
        }

        private fun mixedLeafOnPaper(): HarvestArgbFrame {
            val pixels = IntArray(SIZE * SIZE) { PAPER }
            fillEllipse(pixels, 40, 42, 20, 24, GREEN)
            fillEllipse(pixels, 36, 38, 8, 9, YELLOW)
            fillEllipse(pixels, 44, 48, 7, 8, BROWN)
            return HarvestArgbFrame(pixels, SIZE, SIZE)
        }

        private fun mostlyYellowLeafOnPaper(): HarvestArgbFrame {
            val pixels = IntArray(SIZE * SIZE) { PAPER }
            fillEllipse(pixels, 58, 56, 32, 36, YELLOW)
            fillEllipse(pixels, 50, 48, 6, 7, GREEN)
            fillEllipse(pixels, 62, 60, 10, 11, BROWN)
            return HarvestArgbFrame(pixels, SIZE, SIZE)
        }

        private fun yellowLeafHeldBySkin(): HarvestArgbFrame {
            val pixels = IntArray(SIZE * SIZE) { PAPER }
            fillEllipse(pixels, 60, 46, 30, 26, YELLOW)
            for (y in 72 until SIZE) {
                for (x in 42 until 80) {
                    pixels[y * SIZE + x] = SKIN
                }
            }
            return HarvestArgbFrame(pixels, SIZE, SIZE)
        }

        private fun fillEllipse(
            pixels: IntArray,
            cx: Int,
            cy: Int,
            rx: Int,
            ry: Int,
            color: Int
        ) {
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
        }
    }
}
