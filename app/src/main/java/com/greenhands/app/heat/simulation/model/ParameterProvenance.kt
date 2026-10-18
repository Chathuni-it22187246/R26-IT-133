package com.greenhands.app.heat.simulation.model

/**
 * Truth class for every numerical simulation parameter (PHYSICS_MODEL.md §3).
 * Distinct from crop-evidence [com.greenhands.app.heat.model.EvidenceLevel].
 */
enum class ProvenanceClass {
    PHYSICAL_RELATION,
    REFERENCE_PROPERTY,
    MANUFACTURER_INPUT,
    MEASURED_INPUT,
    CALIBRATED_COEFFICIENT,
    PROJECT_CONTROL_RULE,
    PREVIEW_ASSUMPTION
}

data class ParameterRecord(
    val name: String,
    val valueText: String,
    val unit: String,
    val provenance: ProvenanceClass,
    val note: String
)

data class Provenanced<T>(
    val value: T,
    val provenance: ProvenanceClass,
    val note: String
)
