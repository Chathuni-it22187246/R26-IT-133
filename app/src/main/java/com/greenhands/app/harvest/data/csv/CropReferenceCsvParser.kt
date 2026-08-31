package com.greenhands.app.harvest.data.csv

import com.greenhands.app.harvest.model.CropReference

class CropReferenceCsvParser(
    private val loader: AssetCsvLoader = AssetCsvLoader()
) {
    fun parse(table: CsvTable): List<CropReference> {
        loader.requireColumns(table, REQUIRED)
        return table.rows.mapIndexed { index, row ->
            val cropType = CsvValues.optionalString(row[COL_CROP_TYPE])
                ?: throw CsvLoadException(
                    "Malformed row ${index + 2}: missing crop_type (${table.sourceName})"
                )
            CropReference(
                cropType = cropType,
                scientificName = CsvValues.optionalString(row[COL_SCIENTIFIC_NAME]),
                optimumTemperatureC = CsvValues.optionalString(row[COL_OPTIMUM_TEMP]),
                soilPhRange = CsvValues.optionalString(row[COL_SOIL_PH]),
                transplantAfterSowingDays = CsvValues.optionalString(row[COL_TRANSPLANT]),
                harvestIndicator = CsvValues.optionalString(row[COL_HARVEST_INDICATOR]),
                postharvestNote = CsvValues.optionalString(row[COL_POSTHARVEST]),
                sourceUrl = CsvValues.optionalString(row[COL_SOURCE_URL]),
                sourceNote = CsvValues.optionalString(row[COL_SOURCE_NOTE])
            )
        }
    }

    fun parse(csvText: String, sourceName: String = AssetCsvLoader.CROP_REFERENCE): List<CropReference> =
        parse(loader.parseTable(csvText, sourceName))

    companion object {
        private const val COL_CROP_TYPE = "crop_type"
        private const val COL_SCIENTIFIC_NAME = "scientific_name"
        private const val COL_OPTIMUM_TEMP = "optimum_temperature_c"
        private const val COL_SOIL_PH = "soil_ph_range"
        private const val COL_TRANSPLANT = "transplant_after_sowing_days"
        private const val COL_HARVEST_INDICATOR = "harvest_indicator"
        private const val COL_POSTHARVEST = "postharvest_note"
        private const val COL_SOURCE_URL = "source_url"
        private const val COL_SOURCE_NOTE = "source_note"

        val REQUIRED = listOf(
            COL_CROP_TYPE,
            COL_SCIENTIFIC_NAME,
            COL_OPTIMUM_TEMP,
            COL_SOIL_PH,
            COL_TRANSPLANT,
            COL_HARVEST_INDICATOR,
            COL_POSTHARVEST,
            COL_SOURCE_URL,
            COL_SOURCE_NOTE
        )
    }
}
