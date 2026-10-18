package com.greenhands.app.heat.visualization

import com.greenhands.app.heat.domain.HeatFormulas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TemperatureLegendClassifierTest {

    private val circ = HeatFormulas.circulation(25.0)
    private val exhaust = HeatFormulas.exhaustFromCirculation(circ)
    private val target = 25.0
    private val half = VisualizationBandSettings.TARGET_BAND_HALF_WIDTH_C

    @Test
    fun namedHalfWidthIsProductSetting() {
        assertEquals(0.5, VisualizationBandSettings.TARGET_BAND_HALF_WIDTH_C, 0.0)
        assertEquals(0.5, half, 0.0)
    }

    @Test
    fun everyApprovedTemperatureBoundary() {
        assertEquals(TemperatureLegendBand.BELOW_CDP, classify(22.999))
        assertEquals(VisualizationRgb.PURPLE_DARK_BLUE, color(22.999))

        assertEquals(TemperatureLegendBand.BELOW_TARGET, classify(circ.cdp))
        assertEquals(TemperatureLegendBand.BELOW_TARGET, classify(24.499))
        assertEquals(VisualizationRgb.BLUE, color(circ.cdp))

        assertEquals(TemperatureLegendBand.TARGET, classify(target - half))
        assertEquals(TemperatureLegendBand.TARGET, classify(target))
        assertEquals(TemperatureLegendBand.TARGET, classify(target + half))
        assertEquals(VisualizationRgb.LIGHT_BLUE, color(target))

        assertEquals(TemperatureLegendBand.ABOVE_TARGET, classify(25.5001))
        assertEquals(TemperatureLegendBand.ABOVE_TARGET, classify(26.999))
        assertEquals(VisualizationRgb.YELLOW, color(26.0))

        assertEquals(TemperatureLegendBand.CON_TO_EON, classify(circ.con))
        assertEquals(TemperatureLegendBand.CON_TO_EON, classify(30.999))
        assertEquals(VisualizationRgb.ORANGE, color(circ.con))

        assertEquals(TemperatureLegendBand.EON_OR_ABOVE, classify(exhaust.eon))
        assertEquals(TemperatureLegendBand.EON_OR_ABOVE, classify(40.0))
        assertEquals(VisualizationRgb.RED, color(exhaust.eon))
    }

    @Test
    fun invalidThresholdsOrSamplesReturnUnavailable() {
        assertEquals(TemperatureLegendBand.UNAVAILABLE, classify(Double.NaN))
        assertEquals(TemperatureLegendBand.UNAVAILABLE, classify(Double.POSITIVE_INFINITY))
        assertEquals(
            TemperatureLegendBand.UNAVAILABLE,
            TemperatureLegendClassifier.band(25.0, null, circ.cdp, circ.con, exhaust.eon)
        )
        assertEquals(
            TemperatureLegendBand.UNAVAILABLE,
            TemperatureLegendClassifier.band(25.0, target, Double.NaN, circ.con, exhaust.eon)
        )
        assertEquals(
            TemperatureLegendBand.UNAVAILABLE,
            TemperatureLegendClassifier.band(25.0, target, circ.cdp, circ.cdp, exhaust.eon)
        )
        assertEquals(
            TemperatureLegendBand.UNAVAILABLE,
            TemperatureLegendClassifier.band(25.0, target, circ.cdp, circ.con, exhaust.eon, halfWidthC = 0.0)
        )
        assertEquals(VisualizationRgb.UNAVAILABLE, color(Double.NaN))
        assertNotEquals(VisualizationRgb.RED, VisualizationRgb.UNAVAILABLE)
    }

    private fun classify(temperatureC: Double): TemperatureLegendBand =
        TemperatureLegendClassifier.band(temperatureC, target, circ.cdp, circ.con, exhaust.eon)

    private fun color(temperatureC: Double): VisualizationRgb =
        TemperatureLegendClassifier.color(temperatureC, target, circ.cdp, circ.con, exhaust.eon)
}
