package com.greenhands.app.equipment.model

/**
 * Classification for Phase 3B.2 equipment-count and placement numbers.
 * Distinct from Phase 3A [com.greenhands.app.heat.simulation.model.ProvenanceClass].
 *
 * RESEARCH_VALIDATED_IN_SOURCE means the value was validated only under that source's
 * experimental conditions. SRI_LANKA_FIELD_VALIDATED must not be used until local
 * field validation exists.
 */
enum class EvidenceClassification {
    RESEARCH_VALIDATED_IN_SOURCE,
    RESEARCH_GUIDANCE,
    MANUFACTURER_RATED,
    PROJECT_ASSUMPTION,
    SRI_LANKA_FIELD_VALIDATED,
    @Deprecated("Use SRI_LANKA_FIELD_VALIDATED after local trials exist.")
    FIELD_CALIBRATED
}

fun EvidenceClassification.displayLabel(): String = when (this) {
    EvidenceClassification.RESEARCH_VALIDATED_IN_SOURCE -> "Research-validated in source"
    EvidenceClassification.RESEARCH_GUIDANCE -> "Research guidance"
    EvidenceClassification.MANUFACTURER_RATED -> "Manufacturer rated"
    EvidenceClassification.PROJECT_ASSUMPTION -> "Project assumption"
    EvidenceClassification.SRI_LANKA_FIELD_VALIDATED -> "Sri Lanka field-validated"
    EvidenceClassification.FIELD_CALIBRATED -> "Field calibrated"
}

enum class RecommendationStatus {
    OK,
    CANNOT_CALCULATE,
    OVERRIDE_APPLIED
}

enum class LayoutOrigin {
    UNSPECIFIED,
    MANUAL,
    SUGGESTION_APPLIED,
    LEGACY_AUTOMATIC
}

enum class SelectedEquipmentTool {
    MOVE,
    ROTATE,
    HEIGHT,
    X,
    Y,
    Z,
    EXACT
}

fun SelectedEquipmentTool.isZAxis(): Boolean =
    this == SelectedEquipmentTool.Z || this == SelectedEquipmentTool.HEIGHT

fun DeviceCountResult.preferenceLabel(): String = when {
    status == RecommendationStatus.CANNOT_CALCULATE -> "Cannot calculate — missing inputs"
    classification == EvidenceClassification.PROJECT_ASSUMPTION -> "Conditional — verification required"
    status == RecommendationStatus.OVERRIDE_APPLIED -> "Acceptable alternative"
    else -> "Research-preferred for current inputs"
}
