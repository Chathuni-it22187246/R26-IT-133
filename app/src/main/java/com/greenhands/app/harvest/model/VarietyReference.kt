package com.greenhands.app.harvest.model

/**
 * Row from 02_variety_reference.csv.
 * Maturity day columns are null when blank or marked TO_BE_SOURCED_*.
 * Yield may be a range string (e.g. "55-60") and is never forced to a Double.
 */
data class VarietyReference(
    val cropType: String,
    val variety: String,
    val growthHabit: String?,
    val documentedRipeColor: String?,
    val averageFruitWeightG: Double?,
    val fruitShape: String?,
    val yieldTHa: String?,
    val bacterialWiltResponse: String?,
    val leafCurlResponse: String?,
    val otherNotes: String?,
    val expectedMaturityMinDays: Int?,
    val expectedMaturityMaxDays: Int?,
    val maturityStatus: String?,
    val sourceUrl: String?
)
