package com.greenhands.app.heat.simulation.control

import com.greenhands.app.heat.domain.HeatFormulas
import com.greenhands.app.heat.model.CirculationThresholds
import com.greenhands.app.heat.model.ExhaustThresholds
import com.greenhands.app.heat.model.FoggerThresholds
import com.greenhands.app.heat.simulation.model.ControlSource

/**
 * Pure Kotlin hysteresis state machine using Phase 2 CSP/CDP/CON, ESP/EDP/EON, FSP/FON/FDP
 * formulas unchanged (PHYSICS_MODEL.md §10). Classified PROJECT_CONTROL_RULE.
 */
class EquipmentController(
    val circulation: CirculationThresholds,
    val exhaust: ExhaustThresholds,
    val fogger: FoggerThresholds?,
    val controlSource: ControlSource,
    val circulationSpeedFraction: Double = 1.0,
    val exhaustSpeedFraction: Double = 1.0,
    circulationOn: Boolean = false,
    exhaustOn: Boolean = false,
    foggerOn: Boolean = false
) {
    var circulationOn: Boolean = circulationOn
        private set
    var exhaustOn: Boolean = exhaustOn
        private set
    var foggerOn: Boolean = foggerOn
        private set

    fun copy(): EquipmentController = EquipmentController(
        circulation = circulation,
        exhaust = exhaust,
        fogger = fogger,
        controlSource = controlSource,
        circulationSpeedFraction = circulationSpeedFraction,
        exhaustSpeedFraction = exhaustSpeedFraction,
        circulationOn = circulationOn,
        exhaustOn = exhaustOn,
        foggerOn = foggerOn
    )

    fun command(): EquipmentCommand = EquipmentCommand(
        circulationOn = circulationOn,
        exhaustOn = exhaustOn,
        foggerOn = foggerOn,
        circulationSpeedFraction = if (circulationOn) circulationSpeedFraction else 0.0,
        exhaustSpeedFraction = if (exhaustOn) exhaustSpeedFraction else 0.0,
        foggerSpeedFraction = if (foggerOn) 1.0 else 0.0,
        source = EquipmentCommandSource.CONTROLLER
    )

    fun update(controlTemperatureC: Double, controlRhPercent: Double?): EquipmentCommand {
        if (controlTemperatureC >= circulation.con) circulationOn = true
        if (controlTemperatureC <= circulation.cdp) circulationOn = false
        if (controlTemperatureC >= exhaust.eon) exhaustOn = true
        if (controlTemperatureC <= exhaust.edp) exhaustOn = false
        val thresholds = fogger
        if (thresholds == null || controlRhPercent == null) {
            foggerOn = false
        } else {
            if (controlRhPercent <= thresholds.fon) foggerOn = true
            if (controlRhPercent >= thresholds.fdp) foggerOn = false
        }
        return command()
    }

    companion object {
        fun fromTargets(
            toptC: Double,
            rhoptPercent: Double?,
            controlSource: ControlSource
        ): EquipmentController {
            val circ = HeatFormulas.circulation(toptC)
            val exhaust = HeatFormulas.exhaustFromCirculation(circ)
            val fog = rhoptPercent?.let { HeatFormulas.fogger(it) }
            return EquipmentController(circ, exhaust, fog, controlSource)
        }
    }
}
