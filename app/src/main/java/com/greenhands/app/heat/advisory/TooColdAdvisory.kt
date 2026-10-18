package com.greenhands.app.heat.advisory

import com.greenhands.app.ar.domain.ArHudContent
import com.greenhands.app.ar.domain.ArLiveEnvironmentReading
import com.greenhands.app.ar.domain.LiveSensorUiState
import com.greenhands.app.equipment.ui.OverlayClimate
import com.greenhands.app.heat.domain.HeatFormulas
import com.greenhands.app.heat.visualization.TemperatureLegendBand
import com.greenhands.app.heat.visualization.TemperatureLegendClassifier
import com.greenhands.app.heat.visualization.VisualizationBandSettings
import com.greenhands.app.preview3d.model.PreviewMode

/**
 * Non-blocking “Too cold” advisory for the Heat Distribution live HUD.
 *
 * Thresholds come from the already-resolved crop/stage/period overlay:
 * [OverlayClimate.optimalTemperatureC] and the existing circulation/exhaust
 * set (or [HeatFormulas] derived from that optimal). Classification uses
 * [TemperatureLegendClassifier] so the lower TARGET edge
 * (`optimal − [VisualizationBandSettings.TARGET_BAND_HALF_WIDTH_C]`) is the
 * enter boundary. Visibility holds through that tolerance band until the
 * temperature is at or above the resolved optimal, matching the existing
 * hysteresis/tolerance used for colour bands.
 */
data class TooColdAdvisory(
    val currentTemperatureC: Double,
    val optimalTemperatureC: Double,
    val lowerBoundaryC: Double,
    val currentLabel: String,
    val optimalLabel: String
)

object TooColdAdvisoryCopy {
    const val TITLE = "Too cold"
    const val BODY =
        "The greenhouse temperature is below the optimal level for the selected crop stage."
    const val VIEW_SUGGESTIONS = "View suggestions"
    const val THRESHOLD_SOURCE =
        "Resolved OverlayClimate.optimalTemperatureC (selected crop/stage/period). " +
            "Lower enter boundary is TemperatureLegendClassifier TARGET low edge " +
            "(optimal − VisualizationBandSettings.TARGET_BAND_HALF_WIDTH_C) using existing CDP/CON/EON."
}

object TooColdAdvisoryEvaluator {
    fun trustedTemperatureC(
        mode: PreviewMode,
        simulatedTemperatureC: Double?,
        live: ArLiveEnvironmentReading
    ): Double? {
        return when (mode) {
            PreviewMode.DEMO -> finiteOrNull(simulatedTemperatureC)
            PreviewMode.MY_GREENHOUSE -> {
                if (live.uiState != LiveSensorUiState.LIVE || !live.connected) return null
                finiteOrNull(live.temperatureC)
            }
        }
    }

    fun lowerOptimalBoundaryC(optimalTemperatureC: Double): Double =
        optimalTemperatureC - VisualizationBandSettings.TARGET_BAND_HALF_WIDTH_C

    fun evaluate(
        mode: PreviewMode,
        climate: OverlayClimate,
        simulatedTemperatureC: Double?,
        live: ArLiveEnvironmentReading,
        previouslyVisible: Boolean,
        resolvedClimate: OverlayClimate = ArHudContent.resolveOverlay(climate, mode)
    ): TooColdAdvisory? {
        val temperatureC = trustedTemperatureC(mode, simulatedTemperatureC, live) ?: return null
        val optimalC = finiteOrNull(resolvedClimate.optimalTemperatureC) ?: return null
        val lowerBoundC = lowerOptimalBoundaryC(optimalC)
        if (!lowerBoundC.isFinite()) return null
        val visible = nextVisible(
            previouslyVisible = previouslyVisible,
            temperatureC = temperatureC,
            optimalC = optimalC,
            belowOptimal = classifiedBelowOptimal(temperatureC, optimalC, resolvedClimate)
        )
        if (!visible) return null
        return TooColdAdvisory(
            currentTemperatureC = temperatureC,
            optimalTemperatureC = optimalC,
            lowerBoundaryC = lowerBoundC,
            currentLabel = ArHudContent.temperatureReadingLabel(temperatureC),
            optimalLabel = ArHudContent.optimalTemperatureLabel(optimalC)
        )
    }

    fun nextVisible(
        previouslyVisible: Boolean,
        temperatureC: Double,
        optimalC: Double,
        belowOptimal: Boolean
    ): Boolean {
        if (!temperatureC.isFinite() || !optimalC.isFinite()) return false
        if (temperatureC >= optimalC) return false
        if (belowOptimal) return true
        return previouslyVisible
    }

    fun classifiedBelowOptimal(
        temperatureC: Double,
        optimalC: Double,
        climate: OverlayClimate
    ): Boolean {
        val circulation = climate.circulation ?: HeatFormulas.circulation(optimalC)
        val exhaust = climate.exhaust ?: HeatFormulas.exhaustFromCirculation(circulation)
        val band = TemperatureLegendClassifier.band(
            temperatureC = temperatureC,
            targetC = optimalC,
            cdpC = circulation.cdp,
            conC = circulation.con,
            eonC = exhaust.eon
        )
        return when (band) {
            TemperatureLegendBand.BELOW_CDP,
            TemperatureLegendBand.BELOW_TARGET -> true
            TemperatureLegendBand.UNAVAILABLE ->
                temperatureC < lowerOptimalBoundaryC(optimalC)
            TemperatureLegendBand.TARGET,
            TemperatureLegendBand.ABOVE_TARGET,
            TemperatureLegendBand.CON_TO_EON,
            TemperatureLegendBand.EON_OR_ABOVE -> false
        }
    }

    fun onViewSuggestions(navigateToDecisionSupport: () -> Unit) {
        navigateToDecisionSupport()
    }

    private fun finiteOrNull(value: Double?): Double? =
        value?.takeIf { it.isFinite() }
}
