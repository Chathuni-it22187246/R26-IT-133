package com.greenhands.app.heat.visualization

enum class TemperatureLegendBand {
    BELOW_CDP,
    BELOW_TARGET,
    TARGET,
    ABOVE_TARGET,
    CON_TO_EON,
    EON_OR_ABOVE,
    UNAVAILABLE
}

data class VisualizationRgb(
    val red: Float,
    val green: Float,
    val blue: Float,
    val alpha: Float = CirculationRingVisual.MATERIAL_ALPHA
) {
    val isUnavailable: Boolean get() = this === UNAVAILABLE

    fun withAlpha(alpha: Float): VisualizationRgb = copy(alpha = alpha.coerceIn(0f, 1f))

    companion object {
        val PURPLE_DARK_BLUE = VisualizationRgb(0.28f, 0.10f, 0.78f)
        val BLUE = VisualizationRgb(0.12f, 0.38f, 0.98f)
        val LIGHT_BLUE = VisualizationRgb(0.42f, 0.82f, 1.00f)
        val YELLOW = VisualizationRgb(1.00f, 0.88f, 0.12f)
        val ORANGE = VisualizationRgb(1.00f, 0.48f, 0.08f)
        val RED = VisualizationRgb(0.96f, 0.12f, 0.10f)
        /** Neutral qualitative airflow. Not a temperature band. */
        val NEUTRAL_AIRFLOW = VisualizationRgb(0.72f, 0.94f, 1.00f, 0.70f)
        val UNAVAILABLE = VisualizationRgb(0f, 0f, 0f, 0f)
        /** Field-layer optimal green. Circulation rings keep LIGHT_BLUE for TARGET. */
        val GREEN = VisualizationRgb(0.16f, 0.76f, 0.36f)
        val TEAL = VisualizationRgb(0.10f, 0.70f, 0.58f)
        val AMBER = VisualizationRgb(0.94f, 0.58f, 0.10f)
        val GOLD = VisualizationRgb(0.98f, 0.78f, 0.22f)
        val CYAN_FIELD = VisualizationRgb(0.18f, 0.78f, 0.88f)

        fun lerp(from: VisualizationRgb, to: VisualizationRgb, t: Float): VisualizationRgb {
            if (from.isUnavailable) return to
            if (to.isUnavailable) return from
            val u = t.coerceIn(0f, 1f)
            return VisualizationRgb(
                red = from.red + (to.red - from.red) * u,
                green = from.green + (to.green - from.green) * u,
                blue = from.blue + (to.blue - from.blue) * u,
                alpha = from.alpha + (to.alpha - from.alpha) * u
            )
        }
    }
}

/**
 * Approved Phase 3D.2B temperature colour bands. Invalid inputs return Unavailable
 * and never invent a colour.
 */
object TemperatureLegendClassifier {
    fun band(
        temperatureC: Double?,
        targetC: Double?,
        cdpC: Double?,
        conC: Double?,
        eonC: Double?,
        halfWidthC: Double = VisualizationBandSettings.TARGET_BAND_HALF_WIDTH_C
    ): TemperatureLegendBand {
        if (!finite(temperatureC) || !finite(targetC) || !finite(cdpC) || !finite(conC) || !finite(eonC)) {
            return TemperatureLegendBand.UNAVAILABLE
        }
        if (!finite(halfWidthC) || halfWidthC <= 0.0) return TemperatureLegendBand.UNAVAILABLE
        val t = temperatureC!!
        val target = targetC!!
        val cdp = cdpC!!
        val con = conC!!
        val eon = eonC!!
        if (!(cdp < con && con < eon)) return TemperatureLegendBand.UNAVAILABLE
        val low = target - halfWidthC
        val high = target + halfWidthC
        return when {
            t < cdp -> TemperatureLegendBand.BELOW_CDP
            t < low -> TemperatureLegendBand.BELOW_TARGET
            t <= high -> TemperatureLegendBand.TARGET
            t < con -> TemperatureLegendBand.ABOVE_TARGET
            t < eon -> TemperatureLegendBand.CON_TO_EON
            else -> TemperatureLegendBand.EON_OR_ABOVE
        }
    }

    fun color(
        temperatureC: Double?,
        targetC: Double?,
        cdpC: Double?,
        conC: Double?,
        eonC: Double?,
        halfWidthC: Double = VisualizationBandSettings.TARGET_BAND_HALF_WIDTH_C
    ): VisualizationRgb = colorOf(band(temperatureC, targetC, cdpC, conC, eonC, halfWidthC))

    /**
     * Smooth colour from the same approved temperature boundaries. Does not invent a
     * red→orange→yellow→light-blue sequence; a uniform field stays one colour.
     */
    fun blendedColor(
        temperatureC: Double?,
        targetC: Double?,
        cdpC: Double?,
        conC: Double?,
        eonC: Double?,
        halfWidthC: Double = VisualizationBandSettings.TARGET_BAND_HALF_WIDTH_C
    ): VisualizationRgb {
        val classified = band(temperatureC, targetC, cdpC, conC, eonC, halfWidthC)
        if (classified == TemperatureLegendBand.UNAVAILABLE) return VisualizationRgb.UNAVAILABLE
        val t = temperatureC!!
        val target = targetC!!
        val cdp = cdpC!!
        val con = conC!!
        val eon = eonC!!
        val low = target - halfWidthC
        val high = target + halfWidthC
        return when (classified) {
            TemperatureLegendBand.BELOW_CDP -> VisualizationRgb.PURPLE_DARK_BLUE
            TemperatureLegendBand.BELOW_TARGET ->
                lerpAcross(t, cdp, low, VisualizationRgb.BLUE, VisualizationRgb.LIGHT_BLUE)
            TemperatureLegendBand.TARGET -> VisualizationRgb.LIGHT_BLUE
            TemperatureLegendBand.ABOVE_TARGET ->
                lerpAcross(t, high, con, VisualizationRgb.YELLOW, VisualizationRgb.ORANGE)
            TemperatureLegendBand.CON_TO_EON ->
                lerpAcross(t, con, eon, VisualizationRgb.ORANGE, VisualizationRgb.RED)
            TemperatureLegendBand.EON_OR_ABOVE -> VisualizationRgb.RED
            TemperatureLegendBand.UNAVAILABLE -> VisualizationRgb.UNAVAILABLE
        }
    }

    fun colorOf(band: TemperatureLegendBand): VisualizationRgb = when (band) {
        TemperatureLegendBand.BELOW_CDP -> VisualizationRgb.PURPLE_DARK_BLUE
        TemperatureLegendBand.BELOW_TARGET -> VisualizationRgb.BLUE
        TemperatureLegendBand.TARGET -> VisualizationRgb.LIGHT_BLUE
        TemperatureLegendBand.ABOVE_TARGET -> VisualizationRgb.YELLOW
        TemperatureLegendBand.CON_TO_EON -> VisualizationRgb.ORANGE
        TemperatureLegendBand.EON_OR_ABOVE -> VisualizationRgb.RED
        TemperatureLegendBand.UNAVAILABLE -> VisualizationRgb.UNAVAILABLE
    }

    private fun finite(value: Double?): Boolean = value != null && value.isFinite()

    private fun lerpAcross(
        temperatureC: Double,
        startC: Double,
        endC: Double,
        from: VisualizationRgb,
        to: VisualizationRgb
    ): VisualizationRgb {
        if (!endC.isFinite() || !startC.isFinite() || endC <= startC) return from
        val u = ((temperatureC - startC) / (endC - startC)).toFloat().coerceIn(0f, 1f)
        return VisualizationRgb.lerp(from, to, u)
    }
}
