package com.greenhands.app.heat.runtime.adapter

import com.greenhands.app.heat.runtime.contracts.WeatherBoundary
import com.greenhands.app.preview3d.model.PreviewMode

/**
 * Demo Mode simulated outdoor T/RH for quantitative exhaust.
 *
 * Inspection: DemoEquipmentProfile documents indoor fallback T/RH and equipment
 * ratings only. DemoGreenhouseProfile documents envelope geometry only. Neither
 * publishes a scientifically labelled outside-weather series.
 *
 * Rule: when crop target/optimal T and RH are both finite, they are reused as the
 * Demo simulated outside boundary (DEMO_ONLY_ASSUMPTION). Unrelated hard-coded
 * weather numbers are not introduced. My Greenhouse never receives this object.
 */
object DemoOutsideBoundary {
    fun fromActiveCropTargets(
        previewMode: PreviewMode,
        optimalTemperatureC: Double?,
        optimalHumidityPercent: Double?
    ): WeatherBoundary {
        if (previewMode != PreviewMode.DEMO) return WeatherBoundary.missing()
        val temperature = optimalTemperatureC
        val humidity = optimalHumidityPercent
        if (temperature == null || humidity == null) return WeatherBoundary.missing()
        if (!temperature.isFinite() || !humidity.isFinite() || humidity < 0.0) {
            return WeatherBoundary.missing()
        }
        return WeatherBoundary.demoSimulated(temperature, humidity)
    }
}
