package com.greenhands.app.harvest.model

/**
 * Row from 04_harvest_rules.csv.
 * [calibratedThresholdOrValue] keeps TO_BE_* / empty as null-or-marker text —
 * never parsed into a numeric threshold.
 */
data class HarvestRule(
    val ruleId: String,
    val ruleCategory: String?,
    val inputFeatures: String?,
    val sourceSupportedCondition: String?,
    val calibratedThresholdOrValue: String?,
    val decisionEffect: String?,
    val uiOutput: String?,
    val sourceUrl: String?,
    val status: String?
)
