package com.greenhands.app.harvest.model

/** Row from 03_disease_reference.csv. */
data class DiseaseReference(
    val cropType: String,
    val diseaseName: String,
    val diseaseGroup: String?,
    val causalOrganismOrVector: String?,
    val documentedSymptoms: String?,
    val managementSummary: String?,
    val visibleColorFeatures: String?,
    val visibleShapePatternFeatures: String?,
    val recommendedUiLabel: String?,
    val diagnosisNote: String?,
    val sourceUrl: String?
)
