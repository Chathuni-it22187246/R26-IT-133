package com.greenhands.app.harvest.model

/**
 * Placeholder leaf-health outcome from a leaf scan.
 * Disease matching and confidence will be populated later.
 */
data class LeafHealthResult(
    val plantHealthStatus: String = "Unknown",
    val possibleDisease: String? = null,
    val diseaseConfidencePercent: Double? = null,
    val recommendedUiLabel: String? = null,
    val notes: String? = null
)
