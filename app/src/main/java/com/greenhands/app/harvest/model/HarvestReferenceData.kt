package com.greenhands.app.harvest.model

/**
 * Reference bundle loaded from assets CSVs.
 * Observations may be empty until real field rows exist.
 */
data class HarvestReferenceData(
    val crops: List<CropReference>,
    val varieties: List<VarietyReference>,
    val diseases: List<DiseaseReference>,
    val harvestRules: List<HarvestRule>,
    val observations: List<ObservationRecord> = emptyList()
) {
    val isLoaded: Boolean
        get() = crops.isNotEmpty() && varieties.isNotEmpty() &&
            diseases.isNotEmpty() && harvestRules.isNotEmpty()
}
