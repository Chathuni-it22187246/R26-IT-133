package com.greenhands.app.harvest.data.csv

import com.greenhands.app.harvest.model.HarvestRule

class HarvestRulesCsvParser(
    private val loader: AssetCsvLoader = AssetCsvLoader()
) {
    fun parse(table: CsvTable): List<HarvestRule> {
        loader.requireColumns(table, REQUIRED)
        return table.rows.mapIndexed { index, row ->
            val ruleId = CsvValues.optionalString(row[COL_RULE_ID])
                ?: throw CsvLoadException(
                    "Malformed row ${index + 2}: missing rule_id (${table.sourceName})"
                )
            HarvestRule(
                ruleId = ruleId,
                ruleCategory = CsvValues.optionalString(row[COL_CATEGORY]),
                inputFeatures = CsvValues.optionalString(row[COL_INPUT]),
                sourceSupportedCondition = CsvValues.optionalString(row[COL_CONDITION]),
                calibratedThresholdOrValue = calibratedOrUnknown(row[COL_THRESHOLD]),
                decisionEffect = CsvValues.optionalString(row[COL_EFFECT]),
                uiOutput = CsvValues.optionalString(row[COL_UI]),
                sourceUrl = CsvValues.optionalString(row[COL_SOURCE_URL]),
                status = CsvValues.optionalString(row[COL_STATUS])
            )
        }
    }

    fun parse(csvText: String, sourceName: String = AssetCsvLoader.HARVEST_RULES): List<HarvestRule> =
        parse(loader.parseTable(csvText, sourceName))

    /**
     * Blank and TO_BE_* calibration placeholders stay null (unknown), never numeric.
     */
    private fun calibratedOrUnknown(raw: String?): String? {
        val value = CsvValues.optionalString(raw) ?: return null
        return if (CsvValues.isUnknownOrPending(value)) null else value
    }

    companion object {
        private const val COL_RULE_ID = "rule_id"
        private const val COL_CATEGORY = "rule_category"
        private const val COL_INPUT = "input_features"
        private const val COL_CONDITION = "source_supported_condition"
        private const val COL_THRESHOLD = "calibrated_threshold_or_value"
        private const val COL_EFFECT = "decision_effect"
        private const val COL_UI = "ui_output"
        private const val COL_SOURCE_URL = "source_url"
        private const val COL_STATUS = "status"

        val REQUIRED = listOf(
            COL_RULE_ID,
            COL_CATEGORY,
            COL_INPUT,
            COL_CONDITION,
            COL_THRESHOLD,
            COL_EFFECT,
            COL_UI,
            COL_SOURCE_URL,
            COL_STATUS
        )
    }
}
