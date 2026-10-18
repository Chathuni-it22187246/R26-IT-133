package com.greenhands.app.heat.simulation.control

import com.greenhands.app.heat.simulation.physics.GreenhouseSimulationEngine
import kotlin.math.min

/**
 * Forecasts time to COMPLETE by cloning the live state and running the same solver
 * (PHYSICS_MODEL.md §14.2). Does not mutate the caller's engine.
 */
object EtaForecaster {
    const val PREVIEW_LABEL = "Estimated time to configured band"
    const val LIVE_SENSOR_LABEL = "Estimated time for control sensor"
    const val VALIDATED_LABEL = "Estimated crop-zone time"
    const val UNREACHABLE_MESSAGE = "Target not reached within forecast window"

    fun forecast(
        engine: GreenhouseSimulationEngine,
        horizonS: Double = engine.scenario.numerical.forecastHorizonS,
        stepS: Double = engine.scenario.numerical.requestedTimeStepS,
        label: String = PREVIEW_LABEL
    ): EtaResult {
        if (engine.sessionState() == CoolingSessionState.COMPLETE) {
            return EtaResult(CoolingSessionState.COMPLETE, 0.0, label)
        }
        if (engine.sessionState() == CoolingSessionState.CANCELLED) {
            return EtaResult(CoolingSessionState.CANCELLED, null, label)
        }
        val clone = engine.copyForForecast()
        var elapsed = 0.0
        val eps = 1e-12
        while (elapsed + eps < horizonS) {
            val dt = min(stepS, horizonS - elapsed)
            val frame = clone.step(dt)
            elapsed += dt
            if (frame.sessionState == CoolingSessionState.COMPLETE) {
                return EtaResult(CoolingSessionState.COMPLETE, elapsed, label)
            }
        }
        return EtaResult(CoolingSessionState.UNREACHABLE, null, UNREACHABLE_MESSAGE)
    }
}
