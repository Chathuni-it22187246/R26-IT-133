package com.greenhands.app.heat.simulation.control

enum class CoolingSessionState {
    IDLE,
    COOLING,
    STABILIZING,
    COMPLETE,
    UNREACHABLE,
    CANCELLED
}

data class EquipmentCommand(
    val circulationOn: Boolean,
    val exhaustOn: Boolean,
    val foggerOn: Boolean,
    val circulationSpeedFraction: Double,
    val exhaustSpeedFraction: Double,
    val foggerSpeedFraction: Double,
    val source: EquipmentCommandSource
) {
    val anyOn: Boolean get() = circulationOn || exhaustOn || foggerOn
}

enum class EquipmentCommandSource {
    CONTROLLER,
    FORCED
}

data class EtaResult(
    val state: CoolingSessionState,
    val etaS: Double?,
    val label: String
)
