package com.greenhands.app.harvest.model

import com.greenhands.app.harvest.domain.PlantHealthStatus
import com.greenhands.app.harvest.domain.TomatoLeafHealthCalibration

/**
 * Explainable tomato plant-health assessment.
 * Matching confidence is a project score, not a confirmed diagnosis.
 * Kept in memory for the current session; not written to CSV.
 */
data class PlantHealthAssessment(
    val status: PlantHealthStatus,
    val possibleDisease: String,
    val confidencePercent: Int?,
    val matchedSymptoms: List<String>,
    val reasons: List<String>,
    val recommendation: String,
    val sourceReference: String?,
    val diagnosisNote: String?,
    val scanRequired: Boolean,
    val leafMeasurement: LeafColorMeasurement?,
    val matchedDisease: DiseaseReference? = null,
    val visibleIssue: String = ""
) {
    val statusLabel: String
        get() = if (scanRequired) "SCAN REQUIRED" else status.name.replace('_', ' ')

    /** Same HEALTHY / UNHEALTHY / UNCERTAIN label used on the result screen and in AR. */
    val simpleHealthStatusLabel: String
        get() = when {
            scanRequired -> "SCAN REQUIRED"
            status == PlantHealthStatus.HEALTHY -> "HEALTHY"
            status == PlantHealthStatus.UNHEALTHY -> "UNHEALTHY"
            else -> "UNCERTAIN"
        }

    val confidenceLabel: String
        get() = confidencePercent?.let { "$it%" } ?: "—"

    val showsNamedDisease: Boolean
        get() = status == PlantHealthStatus.UNHEALTHY &&
            !scanRequired &&
            possibleDisease.isNotBlank() &&
            possibleDisease != PlantHealthReasons.POSSIBLE_NONE &&
            possibleDisease != PlantHealthReasons.POSSIBLE_UNABLE &&
            possibleDisease != PlantHealthReasons.NONE &&
            possibleDisease != PlantHealthReasons.UNCERTAIN_DISEASE

    /** Compact live-card visible-issue line. Same value as View Details. */
    val liveCardIssueLine: String?
        get() = when {
            scanRequired -> null
            status == PlantHealthStatus.HEALTHY ->
                visibleIssue.ifBlank { PlantHealthReasons.VISIBLE_NONE }
            status == PlantHealthStatus.UNHEALTHY ->
                visibleIssue.takeIf { it.isNotBlank() }
            else -> PlantHealthReasons.UNCERTAIN_SCAN_AGAIN
        }

    /** Compact live-card possible-disease line. Null when the card should omit it. */
    val liveCardDiseaseLine: String?
        get() = when {
            scanRequired -> null
            status == PlantHealthStatus.UNHEALTHY -> possibleDisease.takeIf { it.isNotBlank() }
            else -> null
        }

    fun toLeafHealthResult(): LeafHealthResult = LeafHealthResult(
        plantHealthStatus = statusLabel,
        possibleDisease = possibleDisease,
        diseaseConfidencePercent = confidencePercent?.toDouble(),
        recommendedUiLabel = possibleDisease,
        notes = reasons.joinToString(" ")
    )

    companion object {
        fun scanRequired(
            measurement: LeafColorMeasurement? = null
        ): PlantHealthAssessment = PlantHealthAssessment(
            status = PlantHealthStatus.UNCERTAIN,
            possibleDisease = "Uncertain",
            confidencePercent = null,
            matchedSymptoms = emptyList(),
            reasons = listOf(PlantHealthReasons.SCAN_REQUIRED),
            recommendation = PlantHealthReasons.RECOMMEND_SCAN,
            sourceReference = PlantHealthReasons.SOURCE_CSV,
            diagnosisNote = null,
            scanRequired = true,
            leafMeasurement = measurement,
            visibleIssue = ""
        )
    }
}

object PlantHealthReasons {
    const val SCAN_REQUIRED =
        "Scan required. No valid leaf measurement is available."
    const val INSUFFICIENT_FRAME =
        "The captured frame has too few usable pixels for a reliable leaf reading."
    const val MOSTLY_GREEN =
        "Leaf colour is mostly green with low discoloration."
    const val MODERATE_DISCOLORATION =
        "Moderate yellow, brown/dark, or white/pale discoloration was measured."
    const val STRONG_DISCOLORATION =
        "Strong discoloration or dark/spot evidence was measured."
    const val WEAK_DISEASE_MATCH =
        "Discoloration is present but no reliable multi-symptom disease match."
    const val SINGLE_COLOR_NOT_DIAGNOSIS =
        "A single colour cue is not enough for a confident disease match."
    const val NEEDS_INSPECTION = "Needs inspection."
    const val UNCERTAIN_DISEASE = "Uncertain"
    const val NONE_INDICATED = "None indicated"
    const val NONE = "None"
    const val VISIBLE_NONE = "No significant discoloration"
    const val VISIBLE_YELLOWING = "Yellowing"
    const val VISIBLE_DARK_BROWN = "Dark/Brown Spots"
    const val VISIBLE_PALE = "Pale/White Areas"
    const val VISIBLE_MIXED = "Mixed Discoloration"
    const val POSSIBLE_NONE = "None detected"
    const val POSSIBLE_UNABLE = "Unable to determine confidently"
    const val UNCERTAIN_SCAN_AGAIN =
        "Unable to determine plant health clearly. Please scan again."
    const val DISEASE_DISCLAIMER =
        "Possible disease prediction based on image analysis."
    const val SOURCE_CSV =
        "03_disease_reference.csv (Sri Lanka DOA/HORDI tomato crop reference). Matching confidence is a project score, not agronomic certainty."
    const val SOURCE_CLASSIFIER =
        "On-device tomato disease classifier. Possible disease only; not a confirmed diagnosis."
    const val CLASSIFIER_BELOW_THRESHOLD =
        "Classifier confidence is below the project threshold. This is not a confirmed diagnosis."
    const val CLASSIFIER_ROI_UNRELIABLE =
        "No reliable leaf-focused region for the classifier. Background-dominated input was not classified. This is not a confirmed diagnosis."
    const val HSV_SUPPLEMENTAL =
        "HSV colour percentages are supplemental measurements and did not decide Possible Disease."
    const val CLASSIFIER_HSV_DISAGREE =
        "Classifier and colour evidence do not agree"
    const val CLASSIFIER_MARGIN_TOO_SMALL =
        "Classifier top-1 and top-2 scores are too close for a reliable disease decision."
    const val CLASSIFIER_HSV_INSUFFICIENT =
        "Classifier suggested a disease, but colour evidence does not show enough abnormal discoloration."
    const val RECOMMEND_SCAN =
        "Capture a leaf scan first. Point the camera at the leaf and keep it steady."
    const val RECOMMEND_HEALTHY =
        "Continue routine observation. Low discoloration is not a guarantee the plant is disease-free."
    const val RECOMMEND_INSPECT =
        "Inspect the plant in the field and compare with documented symptoms. This is not a confirmed diagnosis."
    const val RECOMMEND_GENERIC =
        "Inspect the plant carefully and compare with documented symptoms. If symptoms persist or spread, consult an agricultural specialist."
    const val OOZE_NOT_FROM_IMAGE =
        "Viscous ooze cannot be inferred from an ordinary leaf camera image."
    const val IMAGE_UNSUPPORTED_FEATURES =
        "Curl, wilt, powdery texture, concentric rings, bronzing, mottling, shoestring shape, and ooze are not confirmed from HSV colour bins alone."

    fun possibleLabel(recommendedUiLabel: String?, diseaseName: String): String =
        recommendedUiLabel?.takeIf { it.isNotBlank() } ?: "Possible $diseaseName"

    fun likelyLabel(diseaseName: String): String = "Likely $diseaseName"

    fun confidenceCapNote(calibration: TomatoLeafHealthCalibration): String =
        "Project matching confidence is capped at ${calibration.maxConfidencePercent}% and is not diagnostic certainty."
}
