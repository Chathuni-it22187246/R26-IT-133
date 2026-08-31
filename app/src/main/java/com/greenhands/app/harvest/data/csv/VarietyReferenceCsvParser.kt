package com.greenhands.app.harvest.data.csv

import com.greenhands.app.harvest.model.VarietyReference

class VarietyReferenceCsvParser(
    private val loader: AssetCsvLoader = AssetCsvLoader()
) {
    fun parse(table: CsvTable): List<VarietyReference> {
        loader.requireColumns(table, REQUIRED)
        return table.rows.mapIndexed { index, row ->
            val cropType = CsvValues.optionalString(row[COL_CROP_TYPE])
                ?: throw CsvLoadException(
                    "Malformed row ${index + 2}: missing crop_type (${table.sourceName})"
                )
            val variety = CsvValues.optionalString(row[COL_VARIETY])
                ?: throw CsvLoadException(
                    "Malformed row ${index + 2}: missing variety (${table.sourceName})"
                )
            VarietyReference(
                cropType = cropType,
                variety = variety,
                growthHabit = CsvValues.optionalString(row[COL_GROWTH_HABIT]),
                documentedRipeColor = CsvValues.optionalString(row[COL_RIPE_COLOR]),
                averageFruitWeightG = CsvValues.optionalDouble(row[COL_WEIGHT]),
                fruitShape = CsvValues.optionalString(row[COL_FRUIT_SHAPE]),
                yieldTHa = CsvValues.optionalString(row[COL_YIELD]),
                bacterialWiltResponse = CsvValues.optionalString(row[COL_BACTERIAL_WILT]),
                leafCurlResponse = CsvValues.optionalString(row[COL_LEAF_CURL]),
                otherNotes = CsvValues.optionalString(row[COL_OTHER_NOTES]),
                expectedMaturityMinDays = CsvValues.optionalInt(row[COL_MATURITY_MIN]),
                expectedMaturityMaxDays = CsvValues.optionalInt(row[COL_MATURITY_MAX]),
                maturityStatus = CsvValues.optionalString(row[COL_MATURITY_STATUS]),
                sourceUrl = CsvValues.optionalString(row[COL_SOURCE_URL])
            )
        }
    }

    fun parse(csvText: String, sourceName: String = AssetCsvLoader.VARIETY_REFERENCE): List<VarietyReference> =
        parse(loader.parseTable(csvText, sourceName))

    companion object {
        private const val COL_CROP_TYPE = "crop_type"
        private const val COL_VARIETY = "variety"
        private const val COL_GROWTH_HABIT = "growth_habit"
        private const val COL_RIPE_COLOR = "documented_ripe_color"
        private const val COL_WEIGHT = "average_fruit_weight_g"
        private const val COL_FRUIT_SHAPE = "fruit_shape"
        private const val COL_YIELD = "yield_t_ha"
        private const val COL_BACTERIAL_WILT = "bacterial_wilt_response"
        private const val COL_LEAF_CURL = "leaf_curl_response"
        private const val COL_OTHER_NOTES = "other_notes"
        private const val COL_MATURITY_MIN = "expected_maturity_min_days"
        private const val COL_MATURITY_MAX = "expected_maturity_max_days"
        private const val COL_MATURITY_STATUS = "maturity_status"
        private const val COL_SOURCE_URL = "source_url"

        val REQUIRED = listOf(
            COL_CROP_TYPE,
            COL_VARIETY,
            COL_GROWTH_HABIT,
            COL_RIPE_COLOR,
            COL_WEIGHT,
            COL_FRUIT_SHAPE,
            COL_YIELD,
            COL_BACTERIAL_WILT,
            COL_LEAF_CURL,
            COL_OTHER_NOTES,
            COL_MATURITY_MIN,
            COL_MATURITY_MAX,
            COL_MATURITY_STATUS,
            COL_SOURCE_URL
        )
    }
}
