package com.greenhands.app.telemetry

/**
 * Read-only Firebase current-telemetry payload.
 *
 * Authoritative app climate is [temperatureC] (`temperature_c`, DS18B20) and
 * [humidityPct] (`humidity_pct`, HTU21D). [raw] is diagnostic only and must
 * never replace, average, or modify those fields.
 */
data class CurrentTelemetryDto(
    val temperatureC: Double? = null,
    val humidityPct: Double? = null,
    val temperatureSource: String? = null,
    val humiditySource: String? = null,
    val receivedAt: String? = null,
    val serverTimestampMs: Long? = null,
    val deviceId: String? = null,
    val greenhouseId: String? = null,
    val status: String? = null,
    val raw: CurrentTelemetryRawDto? = null
)

data class CurrentTelemetryRawDto(
    val htu21dTemperatureC: Double? = null,
    val ds18b20TemperatureC: Double? = null,
    val htu21dHumidityPct: Double? = null
)

object GreenhouseTelemetryPath {
    const val GREENHOUSE_ID = "GH_001"
    const val DEVICE_ID = "esp32_greenhouse_01"
    const val ROOT = "greenhouses"
    const val TELEMETRY = "telemetry"
    const val CURRENT = "current"
    const val ONLINE = "online"

    val currentSegments: List<String> = listOf(ROOT, GREENHOUSE_ID, TELEMETRY, CURRENT)
}

object Ds18b20Acceptance {
    const val MIN_C = -55.0
    const val MAX_C = 125.0
}

data class TelemetryFreshnessPolicy(
    val expectedIntervalMs: Long = DEFAULT_EXPECTED_INTERVAL_MS,
    val staleAfterMs: Long = DEFAULT_STALE_AFTER_MS
) {
    init {
        require(expectedIntervalMs > 0L)
        require(staleAfterMs > 0L)
    }

    companion object {
        const val DEFAULT_EXPECTED_INTERVAL_MS = 5_000L
        const val DEFAULT_STALE_AFTER_MS = 20_000L
    }
}

data class AcceptedLiveTelemetry(
    val temperatureC: Double,
    val humidityPct: Double,
    val timestampMillis: Long,
    val greenhouseId: String,
    val deviceId: String?
)

sealed class GreenhouseTelemetryState {
    data object Connecting : GreenhouseTelemetryState()

    data class Live(val snapshot: AcceptedLiveTelemetry) : GreenhouseTelemetryState()

    data class Stale(val lastKnown: AcceptedLiveTelemetry?) : GreenhouseTelemetryState()

    data class Disconnected(
        val lastKnown: AcceptedLiveTelemetry? = null,
        val reason: String? = TelemetryCopy.CONNECTION_ERROR
    ) : GreenhouseTelemetryState()
}
