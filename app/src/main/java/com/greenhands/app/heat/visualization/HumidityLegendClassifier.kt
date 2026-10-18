package com.greenhands.app.heat.visualization

import com.greenhands.app.heat.model.RH_HARD_MAX
import com.greenhands.app.heat.model.RH_HARD_MIN

enum class HumidityLegendBand {
    FOGGER_DEMAND,
    APPROACHING_TARGET,
    TARGET_CONTROL,
    ABOVE_CONTROL,
    UNAVAILABLE
}

/**
 * Fogger humidity colour bands from the active FON / FSP / FDP thresholds.
 * Never uses [TemperatureLegendClassifier]. Invalid inputs return Unavailable
 * and never invent a colour.
 *
 * Inclusive/exclusive boundaries:
 * RH ≤ FON → Fogger demand (red)
 * FON < RH < FSP → Approaching target (yellow)
 * FSP ≤ RH ≤ FDP → Target/control band (light blue)
 * RH > FDP → Above control band (purple)
 */
object HumidityLegendClassifier {
    val DEMAND = VisualizationRgb.RED
    val APPROACHING = VisualizationRgb.YELLOW
    val TARGET = VisualizationRgb.LIGHT_BLUE
    val ABOVE = VisualizationRgb(0.62f, 0.22f, 0.86f)

    fun band(
        relativeHumidityPercent: Double?,
        fonPercent: Double?,
        fspPercent: Double?,
        fdpPercent: Double?
    ): HumidityLegendBand {
        if (!finite(relativeHumidityPercent) || !finite(fonPercent) ||
            !finite(fspPercent) || !finite(fdpPercent)
        ) {
            return HumidityLegendBand.UNAVAILABLE
        }
        val rh = relativeHumidityPercent!!
        val fon = fonPercent!!
        val fsp = fspPercent!!
        val fdp = fdpPercent!!
        if (!(fon < fsp && fsp < fdp)) return HumidityLegendBand.UNAVAILABLE
        if (rh < RH_HARD_MIN || rh > RH_HARD_MAX) return HumidityLegendBand.UNAVAILABLE
        return when {
            rh <= fon -> HumidityLegendBand.FOGGER_DEMAND
            rh < fsp -> HumidityLegendBand.APPROACHING_TARGET
            rh <= fdp -> HumidityLegendBand.TARGET_CONTROL
            else -> HumidityLegendBand.ABOVE_CONTROL
        }
    }

    fun color(
        relativeHumidityPercent: Double?,
        fonPercent: Double?,
        fspPercent: Double?,
        fdpPercent: Double?
    ): VisualizationRgb = colorOf(band(relativeHumidityPercent, fonPercent, fspPercent, fdpPercent))

    fun colorOf(band: HumidityLegendBand): VisualizationRgb = when (band) {
        HumidityLegendBand.FOGGER_DEMAND -> DEMAND
        HumidityLegendBand.APPROACHING_TARGET -> APPROACHING
        HumidityLegendBand.TARGET_CONTROL -> TARGET
        HumidityLegendBand.ABOVE_CONTROL -> ABOVE
        HumidityLegendBand.UNAVAILABLE -> VisualizationRgb.UNAVAILABLE
    }

    private fun finite(value: Double?): Boolean = value != null && value.isFinite()
}
