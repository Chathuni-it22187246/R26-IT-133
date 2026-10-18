package com.greenhands.app.harvest.model

/**
 * Final harvest readiness decision for a tomato fruit scan.
 * READY / NOT READY follows the planting-date maturity period.
 * Fruit HSV colour is supporting information.
 */
enum class HarvestDecision {
    READY_TO_HARVEST,
    NOT_READY,
    HOLD_INSPECT,
    UNCERTAIN
}

fun HarvestDecision.displayLabel(scanRequired: Boolean = false): String {
    if (scanRequired) return "SCAN REQUIRED"
    return when (this) {
        HarvestDecision.READY_TO_HARVEST -> "READY TO HARVEST"
        HarvestDecision.NOT_READY -> "NOT READY TO HARVEST"
        HarvestDecision.HOLD_INSPECT -> "HOLD / INSPECT"
        HarvestDecision.UNCERTAIN -> "UNCERTAIN"
    }
}
