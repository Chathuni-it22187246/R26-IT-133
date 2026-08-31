package com.greenhands.app.harvest.domain

import com.greenhands.app.environment.GreenhouseConnectionState
import com.greenhands.app.environment.GreenhouseEnvironmentSnapshot

/**
 * Broad temperature context for harvest UI only.
 * Does not feed [HarvestDecisionEngine] or [DiseaseMatcher].
 */
enum class HarvestTemperatureBand {
    UNKNOWN,
    SUITABLE,
    WARM,
    COOL
}

/**
 * Broad humidity context for harvest UI only.
 * Does not feed [HarvestDecisionEngine] or [DiseaseMatcher].
 */
enum class HarvestHumidityBand {
    UNKNOWN,
    SUITABLE,
    HIGH,
    LOW
}

/**
 * PROJECT CALIBRATION / DISPLAY THRESHOLDS for harvest environmental context.
 *
 * These bands are UI interpretation only. They are not harvest decision rules,
 * not disease-diagnosis rules, and not claimed agronomic setpoints.
 *
 * Crop CSV `01_crop_reference.csv` cites tomato optimum temperature as the
 * textual range 21-24 °C (DOA field guidance). That cited range is shown as
 * supporting context; it is not used as a READY / NOT READY cut-off.
 * No sourced numeric humidity range exists in harvest reference CSVs.
 */
object HarvestEnvironmentDisplayCalibration {
    const val TEMP_COOL_BELOW_C = 18.0
    const val TEMP_WARM_ABOVE_C = 28.0
    const val HUMIDITY_LOW_BELOW_PERCENT = 40.0
    const val HUMIDITY_HIGH_ABOVE_PERCENT = 80.0

    /** Source-supported cited range from 01_crop_reference.csv; display reminder only. */
    const val CITED_TOMATO_OPTIMUM_TEMPERATURE_C = "21-24"
}

/**
 * Harvest-domain view of a [GreenhouseEnvironmentSnapshot].
 * Supporting evidence only — never an independent READY / NOT READY or diagnosis.
 */
data class HarvestEnvironmentContext(
    val snapshot: GreenhouseEnvironmentSnapshot,
    val temperatureBand: HarvestTemperatureBand,
    val humidityBand: HarvestHumidityBand,
    val temperatureLabel: String,
    val humidityLabel: String,
    val summaryLabel: String,
    val sourceLabel: String,
    val supportingNote: String,
    val sourceDisclaimer: String?
) {
    val isPreview: Boolean
        get() = snapshot.connectionState == GreenhouseConnectionState.PREVIEW

    val isUnknown: Boolean
        get() = temperatureBand == HarvestTemperatureBand.UNKNOWN &&
            humidityBand == HarvestHumidityBand.UNKNOWN

    val sensorUi: HarvestSensorUiState
        get() = HarvestSensorUiState.from(snapshot)

    companion object {
        const val SOURCE_PREVIEW = "PREVIEW"
        const val SOURCE_LIVE = "LIVE"
        const val SOURCE_OFFLINE_DELAYED = "OFFLINE-DELAYED"
        const val SOURCE_DISCONNECTED = "DISCONNECTED"
        const val SOURCE_CONNECTING = "CONNECTING"

        const val LABEL_SUITABLE = "Normal / Suitable"
        const val LABEL_WARM = "Warm / High temperature"
        const val LABEL_COOL = "Cool / Low temperature"
        const val LABEL_HIGH_HUMIDITY = "High humidity"
        const val LABEL_LOW_HUMIDITY = "Low humidity"
        const val LABEL_UNKNOWN = "Unknown"

        const val SUPPORTING_INSPECTION_NOTE =
            "Environmental conditions should be considered during inspection. " +
                "Temperature and humidity do not independently determine harvest " +
                "readiness or disease diagnosis."

        const val PREVIEW_DISCLAIMER =
            "These are PREVIEW sample values, not live IoT sensor readings."

        const val OFFLINE_DISCLAIMER =
            "Last known values may be delayed. This is not a live IoT reading."

        fun from(snapshot: GreenhouseEnvironmentSnapshot): HarvestEnvironmentContext {
            val temperatureBand = classifyTemperature(snapshot.temperatureC)
            val humidityBand = classifyHumidity(snapshot.relativeHumidityPercent)
            return HarvestEnvironmentContext(
                snapshot = snapshot,
                temperatureBand = temperatureBand,
                humidityBand = humidityBand,
                temperatureLabel = temperatureLabel(temperatureBand),
                humidityLabel = humidityLabel(humidityBand),
                summaryLabel = summaryLabel(temperatureBand, humidityBand),
                sourceLabel = sourceLabel(snapshot.connectionState),
                supportingNote = SUPPORTING_INSPECTION_NOTE,
                sourceDisclaimer = when (snapshot.connectionState) {
                    GreenhouseConnectionState.PREVIEW,
                    GreenhouseConnectionState.DISCONNECTED -> HarvestSensorUiState.PENDING_DEVICE_NOTE
                    GreenhouseConnectionState.OFFLINE_DELAYED -> OFFLINE_DISCLAIMER
                    GreenhouseConnectionState.CONNECTING -> HarvestSensorUiState.PENDING_DEVICE_NOTE
                    GreenhouseConnectionState.LIVE -> null
                }
            )
        }

        fun sourceLabel(state: GreenhouseConnectionState): String = when (state) {
            GreenhouseConnectionState.PREVIEW -> SOURCE_PREVIEW
            GreenhouseConnectionState.LIVE -> SOURCE_LIVE
            GreenhouseConnectionState.OFFLINE_DELAYED -> SOURCE_OFFLINE_DELAYED
            GreenhouseConnectionState.DISCONNECTED -> SOURCE_DISCONNECTED
            GreenhouseConnectionState.CONNECTING -> SOURCE_CONNECTING
        }

        private fun classifyTemperature(celsius: Double?): HarvestTemperatureBand {
            if (celsius == null) return HarvestTemperatureBand.UNKNOWN
            return when {
                celsius < HarvestEnvironmentDisplayCalibration.TEMP_COOL_BELOW_C ->
                    HarvestTemperatureBand.COOL
                celsius > HarvestEnvironmentDisplayCalibration.TEMP_WARM_ABOVE_C ->
                    HarvestTemperatureBand.WARM
                else -> HarvestTemperatureBand.SUITABLE
            }
        }

        private fun classifyHumidity(percent: Double?): HarvestHumidityBand {
            if (percent == null) return HarvestHumidityBand.UNKNOWN
            return when {
                percent < HarvestEnvironmentDisplayCalibration.HUMIDITY_LOW_BELOW_PERCENT ->
                    HarvestHumidityBand.LOW
                percent > HarvestEnvironmentDisplayCalibration.HUMIDITY_HIGH_ABOVE_PERCENT ->
                    HarvestHumidityBand.HIGH
                else -> HarvestHumidityBand.SUITABLE
            }
        }

        private fun temperatureLabel(band: HarvestTemperatureBand): String = when (band) {
            HarvestTemperatureBand.UNKNOWN -> LABEL_UNKNOWN
            HarvestTemperatureBand.SUITABLE -> LABEL_SUITABLE
            HarvestTemperatureBand.WARM -> LABEL_WARM
            HarvestTemperatureBand.COOL -> LABEL_COOL
        }

        private fun humidityLabel(band: HarvestHumidityBand): String = when (band) {
            HarvestHumidityBand.UNKNOWN -> LABEL_UNKNOWN
            HarvestHumidityBand.SUITABLE -> LABEL_SUITABLE
            HarvestHumidityBand.HIGH -> LABEL_HIGH_HUMIDITY
            HarvestHumidityBand.LOW -> LABEL_LOW_HUMIDITY
        }

        private fun summaryLabel(
            temperatureBand: HarvestTemperatureBand,
            humidityBand: HarvestHumidityBand
        ): String {
            val notable = buildList {
                if (temperatureBand == HarvestTemperatureBand.WARM) add(LABEL_WARM)
                if (temperatureBand == HarvestTemperatureBand.COOL) add(LABEL_COOL)
                if (humidityBand == HarvestHumidityBand.HIGH) add(LABEL_HIGH_HUMIDITY)
                if (humidityBand == HarvestHumidityBand.LOW) add(LABEL_LOW_HUMIDITY)
            }
            if (notable.isNotEmpty()) return notable.joinToString(" · ")
            if (temperatureBand == HarvestTemperatureBand.UNKNOWN &&
                humidityBand == HarvestHumidityBand.UNKNOWN
            ) {
                return LABEL_UNKNOWN
            }
            return LABEL_SUITABLE
        }
    }
}
