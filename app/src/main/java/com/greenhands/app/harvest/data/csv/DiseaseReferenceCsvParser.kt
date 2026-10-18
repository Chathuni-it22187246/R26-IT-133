package com.greenhands.app.harvest.data.csv

import com.greenhands.app.harvest.model.DiseaseReference

class DiseaseReferenceCsvParser(
    private val loader: AssetCsvLoader = AssetCsvLoader()
) {
    fun parse(table: CsvTable): List<DiseaseReference> {
        loader.requireColumns(table, REQUIRED)
        return table.rows.mapIndexed { index, row ->
            val cropType = CsvValues.optionalString(row[COL_CROP_TYPE])
                ?: throw CsvLoadException(
                    "Malformed row ${index + 2}: missing crop_type (${table.sourceName})"
                )
            val diseaseName = CsvValues.optionalString(row[COL_DISEASE_NAME])
                ?: throw CsvLoadException(
                    "Malformed row ${index + 2}: missing disease_name (${table.sourceName})"
                )
            DiseaseReference(
                cropType = cropType,
                diseaseName = diseaseName,
                diseaseGroup = CsvValues.optionalString(row[COL_GROUP]),
                causalOrganismOrVector = CsvValues.optionalString(row[COL_CAUSAL]),
                documentedSymptoms = CsvValues.optionalString(row[COL_SYMPTOMS]),
                managementSummary = CsvValues.optionalString(row[COL_MANAGEMENT]),
                visibleColorFeatures = CsvValues.optionalString(row[COL_COLOR]),
                visibleShapePatternFeatures = CsvValues.optionalString(row[COL_SHAPE]),
                recommendedUiLabel = CsvValues.optionalString(row[COL_UI_LABEL]),
                diagnosisNote = CsvValues.optionalString(row[COL_DIAGNOSIS]),
                sourceUrl = CsvValues.optionalString(row[COL_SOURCE_URL])
            )
        }
    }

    fun parse(csvText: String, sourceName: String = AssetCsvLoader.DISEASE_REFERENCE): List<DiseaseReference> =
        parse(loader.parseTable(csvText, sourceName))

    companion object {
        private const val COL_CROP_TYPE = "crop_type"
        private const val COL_DISEASE_NAME = "disease_name"
        private const val COL_GROUP = "disease_group"
        private const val COL_CAUSAL = "causal_organism_or_vector"
        private const val COL_SYMPTOMS = "documented_symptoms"
        private const val COL_MANAGEMENT = "management_summary"
        private const val COL_COLOR = "visible_color_features"
        private const val COL_SHAPE = "visible_shape_pattern_features"
        private const val COL_UI_LABEL = "recommended_ui_label"
        private const val COL_DIAGNOSIS = "diagnosis_note"
        private const val COL_SOURCE_URL = "source_url"

        val REQUIRED = listOf(
            COL_CROP_TYPE,
            COL_DISEASE_NAME,
            COL_GROUP,
            COL_CAUSAL,
            COL_SYMPTOMS,
            COL_MANAGEMENT,
            COL_COLOR,
            COL_SHAPE,
            COL_UI_LABEL,
            COL_DIAGNOSIS,
            COL_SOURCE_URL
        )
    }
}
