package com.greenhands.app.harvest.model

/**
 * Immutable saved snapshot of a completed fruit or leaf scan.
 * History UI must display these stored values and must not recompute
 * decisions from the current session date, environment, or calibration.
 */
data class ScanRecord(
    val id: String,
    val scanType: ScanType,
    val scannedAtEpochMillis: Long,
    val cropType: String = "Tomato",
    val variety: String? = null,
    val transplantDateUtcMillis: Long? = null,
    val daysSinceTransplant: Int? = null,
    val maturityReferenceKind: String? = null,
    val maturityMinDays: Int? = null,
    val maturityMaxDays: Int? = null,
    val maturityStatus: String? = null,
    val estimatedDaysRemaining: Int? = null,
    val hueMean: Float? = null,
    val saturationMean: Float? = null,
    val valueMean: Float? = null,
    val greenPercent: Float? = null,
    val yellowPercent: Float? = null,
    val redPercent: Float? = null,
    val brownDarkPercent: Float? = null,
    val whitePalePercent: Float? = null,
    val discoloredPercent: Float? = null,
    val ripenessEvidence: String? = null,
    val qualityStatus: String? = null,
    val harvestDecision: HarvestDecision? = null,
    val harvestDecisionLabel: String? = null,
    val decisionReasons: List<String> = emptyList(),
    val plantHealthStatus: String? = null,
    val possibleDisease: String? = null,
    val matchingConfidencePercent: Int? = null,
    val matchedSymptoms: List<String> = emptyList(),
    val recommendation: String? = null,
    val temperatureC: Double? = null,
    val humidityPercent: Double? = null,
    val environmentSource: String
) {
    val isPreviewEnvironment: Boolean
        get() = environmentSource == ENVIRONMENT_SOURCE_PREVIEW

    val listHeadline: String
        get() = when (scanType) {
            ScanType.FRUIT_SCAN -> "$cropType • ${variety ?: VARIETY_NOT_SELECTED}"
            ScanType.LEAF_SCAN -> "$cropType • Leaf Scan"
        }

    val listStatus: String
        get() = when (scanType) {
            ScanType.FRUIT_SCAN -> harvestDecisionLabel ?: harvestDecision?.displayLabel() ?: "—"
            ScanType.LEAF_SCAN -> plantHealthStatus ?: "—"
        }

    val listDetail: String?
        get() = when (scanType) {
            ScanType.FRUIT_SCAN -> null
            ScanType.LEAF_SCAN -> possibleDisease
        }

    val listMeta: String
        get() = "HSV measured • Environment: $environmentSource"

    companion object {
        const val ENVIRONMENT_SOURCE_PREVIEW = "PREVIEW"
        const val ENVIRONMENT_SOURCE_LIVE = "LIVE"
        const val ENVIRONMENT_SOURCE_OFFLINE_DELAYED = "OFFLINE-DELAYED"
        const val VARIETY_NOT_SELECTED = "Variety not selected"
    }
}

enum class ScanType {
    FRUIT_SCAN,
    LEAF_SCAN
}

enum class HarvestSaveStatus {
    IDLE,
    SAVING,
    SAVED,
    NO_VALID_SCAN,
    ALREADY_SAVED,
    FAILED
}
