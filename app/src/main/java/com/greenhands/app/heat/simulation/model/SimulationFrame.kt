package com.greenhands.app.heat.simulation.model

import com.greenhands.app.heat.simulation.control.CoolingSessionState
import com.greenhands.app.heat.simulation.control.EquipmentCommand
import com.greenhands.app.heat.simulation.control.EtaResult
import com.greenhands.app.heat.simulation.probe.DistanceProbeSample

data class SimulationFrame(
    val simulatedTimeS: Double,
    val command: EquipmentCommand,
    val sessionState: CoolingSessionState,
    val elapsedCoolingS: Double,
    val stabilityElapsedS: Double,
    val completionEvent: Boolean,
    val probes: List<DistanceProbeSample>,
    val eta: EtaResult?,
    val diagnostics: SimulationDiagnostics
)
