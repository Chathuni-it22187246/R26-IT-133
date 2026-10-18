package com.greenhands.app

import com.greenhands.app.harvest.domain.HarvestArgbFrame
import com.greenhands.app.harvest.domain.HsvAnalyzer
import com.greenhands.app.harvest.domain.HsvColorBin
import com.greenhands.app.harvest.domain.HsvColorBins
import com.greenhands.app.harvest.domain.HsvConverter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HarvestHsvAnalyzerTest {

    @Test
    fun rgbRedConvertsNearZeroOrWrapHue() {
        val hsv = HsvConverter.fromRgb(220, 20, 20)
        assertTrue(hsv.hue < 15f || hsv.hue > 345f)
        assertEquals(HsvColorBin.RED, HsvColorBins.classify(hsv))
    }

    @Test
    fun rgbGreenAndYellowAndWhiteClassifyToExpectedBins() {
        assertEquals(HsvColorBin.GREEN, HsvColorBins.classify(HsvConverter.fromRgb(30, 180, 40)))
        assertEquals(HsvColorBin.YELLOW, HsvColorBins.classify(HsvConverter.fromRgb(230, 210, 20)))
        assertEquals(HsvColorBin.WHITE_PALE, HsvColorBins.classify(HsvConverter.fromRgb(240, 240, 235)))
        assertEquals(HsvColorBin.BROWN_DARK, HsvColorBins.classify(HsvConverter.fromRgb(40, 24, 16)))
    }

    @Test
    fun fruitAnalyzerReportsHighRedOnSolidRedFrame() {
        val frame = solidFrame(0xFFCC1A1A.toInt(), 40, 40)
        val result = HsvAnalyzer(centerCropFraction = 1f).analyzeFruit(frame)
        assertTrue(result.hasSamples)
        assertTrue(result.redPercent > 80f)
        assertTrue(result.greenPercent < 10f)
    }

    @Test
    fun leafAnalyzerCountsNonGreenAsDiscolored() {
        val frame = solidFrame(0xFFE8E8E0.toInt(), 32, 32)
        val result = HsvAnalyzer(centerCropFraction = 1f).analyzeLeaf(frame)
        assertTrue(result.whitePalePercent > 80f)
        assertTrue(result.discoloredPercent > 80f)
        assertTrue(result.greenPercent < 10f)
    }

    private fun solidFrame(argb: Int, width: Int, height: Int): HarvestArgbFrame {
        return HarvestArgbFrame(IntArray(width * height) { argb }, width, height)
    }
}
