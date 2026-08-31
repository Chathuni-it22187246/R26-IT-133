package com.greenhands.app.harvest.data.csv

import com.greenhands.app.harvest.model.ObservationRecord

class ObservationCsvParser(
    private val loader: AssetCsvLoader = AssetCsvLoader()
) {
    fun parse(table: CsvTable): List<ObservationRecord> {
        loader.requireColumns(table, REQUIRED)
        return table.rows.map { row ->
            ObservationRecord(
                recordId = CsvValues.optionalString(row[COL_RECORD_ID]),
                plantId = CsvValues.optionalString(row[COL_PLANT_ID]),
                cropType = CsvValues.optionalString(row[COL_CROP_TYPE]),
                variety = CsvValues.optionalString(row[COL_VARIETY]),
                plantingOrTransplantDate = CsvValues.optionalString(row[COL_PLANTING_DATE]),
                scanDatetime = CsvValues.optionalString(row[COL_SCAN_DATETIME]),
                daysAfterPlantingOrTransplant = CsvValues.optionalInt(row[COL_DAYS_AFTER]),
                temperatureC = CsvValues.optionalDouble(row[COL_TEMP]),
                humidityPercent = CsvValues.optionalDouble(row[COL_HUMIDITY]),
                fruitImageFile = CsvValues.optionalString(row[COL_FRUIT_IMAGE]),
                fruitHueMean = CsvValues.optionalDouble(row[COL_FRUIT_HUE]),
                fruitSaturationMean = CsvValues.optionalDouble(row[COL_FRUIT_SAT]),
                fruitValueMean = CsvValues.optionalDouble(row[COL_FRUIT_VALUE]),
                fruitGreenPercent = CsvValues.optionalDouble(row[COL_FRUIT_GREEN]),
                fruitYellowPercent = CsvValues.optionalDouble(row[COL_FRUIT_YELLOW]),
                fruitRedPercent = CsvValues.optionalDouble(row[COL_FRUIT_RED]),
                fruitBrownDarkPercent = CsvValues.optionalDouble(row[COL_FRUIT_BROWN]),
                fruitSpotPercent = CsvValues.optionalDouble(row[COL_FRUIT_SPOT]),
                fruitDamageDetected = CsvValues.optionalBoolean(row[COL_FRUIT_DAMAGE]),
                ripenessStage = CsvValues.optionalString(row[COL_RIPENESS]),
                fruitQualityStatus = CsvValues.optionalString(row[COL_FRUIT_QUALITY]),
                leafImageFile = CsvValues.optionalString(row[COL_LEAF_IMAGE]),
                leafGreenPercent = CsvValues.optionalDouble(row[COL_LEAF_GREEN]),
                leafYellowPercent = CsvValues.optionalDouble(row[COL_LEAF_YELLOW]),
                leafBrownPercent = CsvValues.optionalDouble(row[COL_LEAF_BROWN]),
                leafWhitePercent = CsvValues.optionalDouble(row[COL_LEAF_WHITE]),
                leafSpotPercent = CsvValues.optionalDouble(row[COL_LEAF_SPOT]),
                leafSpotCount = CsvValues.optionalInt(row[COL_LEAF_SPOT_COUNT]),
                leafCurlDetected = CsvValues.optionalBoolean(row[COL_LEAF_CURL]),
                leafWiltingDetected = CsvValues.optionalBoolean(row[COL_LEAF_WILT]),
                lesionPattern = CsvValues.optionalString(row[COL_LESION]),
                possibleDisease = CsvValues.optionalString(row[COL_DISEASE]),
                diseaseConfidencePercent = CsvValues.optionalDouble(row[COL_DISEASE_CONF]),
                plantHealthStatus = CsvValues.optionalString(row[COL_PLANT_HEALTH]),
                sampleNumber = CsvValues.optionalInt(row[COL_SAMPLE_NUMBER]),
                ripeSampleCount = CsvValues.optionalInt(row[COL_RIPE_SAMPLE_COUNT]),
                maturityStatus = CsvValues.optionalString(row[COL_MATURITY_STATUS]),
                estimatedDaysRemaining = CsvValues.optionalInt(row[COL_DAYS_REMAINING]),
                harvestDecision = CsvValues.optionalString(row[COL_HARVEST_DECISION]),
                decisionReason = CsvValues.optionalString(row[COL_DECISION_REASON]),
                expertOrFarmerLabel = CsvValues.optionalString(row[COL_EXPERT_LABEL]),
                observerNotes = CsvValues.optionalString(row[COL_OBSERVER_NOTES]),
                dataOrigin = CsvValues.optionalString(row[COL_DATA_ORIGIN])
            )
        }
    }

    fun parse(csvText: String, sourceName: String = AssetCsvLoader.REAL_OBSERVATIONS): List<ObservationRecord> =
        parse(loader.parseTable(csvText, sourceName))

    companion object {
        private const val COL_RECORD_ID = "record_id"
        private const val COL_PLANT_ID = "plant_id"
        private const val COL_CROP_TYPE = "crop_type"
        private const val COL_VARIETY = "variety"
        private const val COL_PLANTING_DATE = "planting_or_transplant_date"
        private const val COL_SCAN_DATETIME = "scan_datetime"
        private const val COL_DAYS_AFTER = "days_after_planting_or_transplant"
        private const val COL_TEMP = "temperature_c"
        private const val COL_HUMIDITY = "humidity_percent"
        private const val COL_FRUIT_IMAGE = "fruit_image_file"
        private const val COL_FRUIT_HUE = "fruit_hue_mean"
        private const val COL_FRUIT_SAT = "fruit_saturation_mean"
        private const val COL_FRUIT_VALUE = "fruit_value_mean"
        private const val COL_FRUIT_GREEN = "fruit_green_percent"
        private const val COL_FRUIT_YELLOW = "fruit_yellow_percent"
        private const val COL_FRUIT_RED = "fruit_red_percent"
        private const val COL_FRUIT_BROWN = "fruit_brown_dark_percent"
        private const val COL_FRUIT_SPOT = "fruit_spot_percent"
        private const val COL_FRUIT_DAMAGE = "fruit_damage_detected"
        private const val COL_RIPENESS = "ripeness_stage"
        private const val COL_FRUIT_QUALITY = "fruit_quality_status"
        private const val COL_LEAF_IMAGE = "leaf_image_file"
        private const val COL_LEAF_GREEN = "leaf_green_percent"
        private const val COL_LEAF_YELLOW = "leaf_yellow_percent"
        private const val COL_LEAF_BROWN = "leaf_brown_percent"
        private const val COL_LEAF_WHITE = "leaf_white_percent"
        private const val COL_LEAF_SPOT = "leaf_spot_percent"
        private const val COL_LEAF_SPOT_COUNT = "leaf_spot_count"
        private const val COL_LEAF_CURL = "leaf_curl_detected"
        private const val COL_LEAF_WILT = "leaf_wilting_detected"
        private const val COL_LESION = "lesion_pattern"
        private const val COL_DISEASE = "possible_disease"
        private const val COL_DISEASE_CONF = "disease_confidence_percent"
        private const val COL_PLANT_HEALTH = "plant_health_status"
        private const val COL_SAMPLE_NUMBER = "sample_number"
        private const val COL_RIPE_SAMPLE_COUNT = "ripe_sample_count"
        private const val COL_MATURITY_STATUS = "maturity_status"
        private const val COL_DAYS_REMAINING = "estimated_days_remaining"
        private const val COL_HARVEST_DECISION = "harvest_decision"
        private const val COL_DECISION_REASON = "decision_reason"
        private const val COL_EXPERT_LABEL = "expert_or_farmer_label"
        private const val COL_OBSERVER_NOTES = "observer_notes"
        private const val COL_DATA_ORIGIN = "data_origin"

        val REQUIRED = listOf(
            COL_RECORD_ID,
            COL_PLANT_ID,
            COL_CROP_TYPE,
            COL_VARIETY,
            COL_PLANTING_DATE,
            COL_SCAN_DATETIME,
            COL_DAYS_AFTER,
            COL_TEMP,
            COL_HUMIDITY,
            COL_FRUIT_IMAGE,
            COL_FRUIT_HUE,
            COL_FRUIT_SAT,
            COL_FRUIT_VALUE,
            COL_FRUIT_GREEN,
            COL_FRUIT_YELLOW,
            COL_FRUIT_RED,
            COL_FRUIT_BROWN,
            COL_FRUIT_SPOT,
            COL_FRUIT_DAMAGE,
            COL_RIPENESS,
            COL_FRUIT_QUALITY,
            COL_LEAF_IMAGE,
            COL_LEAF_GREEN,
            COL_LEAF_YELLOW,
            COL_LEAF_BROWN,
            COL_LEAF_WHITE,
            COL_LEAF_SPOT,
            COL_LEAF_SPOT_COUNT,
            COL_LEAF_CURL,
            COL_LEAF_WILT,
            COL_LESION,
            COL_DISEASE,
            COL_DISEASE_CONF,
            COL_PLANT_HEALTH,
            COL_SAMPLE_NUMBER,
            COL_RIPE_SAMPLE_COUNT,
            COL_MATURITY_STATUS,
            COL_DAYS_REMAINING,
            COL_HARVEST_DECISION,
            COL_DECISION_REASON,
            COL_EXPERT_LABEL,
            COL_OBSERVER_NOTES,
            COL_DATA_ORIGIN
        )
    }
}
