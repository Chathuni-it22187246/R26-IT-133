package com.greenhands.app.decision

import com.greenhands.app.environment.GreenhouseConnectionState
import com.greenhands.app.environment.GreenhouseEnvironmentSnapshot
import com.greenhands.app.telemetry.GreenhouseTelemetryState
import java.util.Locale
import kotlin.math.abs

/**
 * Canonical Firebase IoT readings for Decision Making temperature/humidity
 * fields. Never maps `raw.htu21d_temperature_c`, weather, or backend-simulated
 * `/api/v1/greenhouse/telemetry` climate numbers into those two fields.
 */
data class DecisionIotClimate(
    val temperatureC: Double?,
    val humidityPct: Double?,
    val isLive: Boolean,
    val connectionState: GreenhouseConnectionState,
    val timestampMillis: Long? = null
) {
    val displayTemperatureC: Double?
        get() = if (isLive) temperatureC else null

    val displayHumidityPct: Double?
        get() = if (isLive) humidityPct else null

    val uiConnectionState: GreenhouseConnectionState
        get() = when {
            isLive -> GreenhouseConnectionState.LIVE
            connectionState == GreenhouseConnectionState.OFFLINE_DELAYED ->
                GreenhouseConnectionState.OFFLINE_DELAYED
            connectionState == GreenhouseConnectionState.CONNECTING ->
                GreenhouseConnectionState.PREVIEW
            connectionState == GreenhouseConnectionState.DISCONNECTED ->
                GreenhouseConnectionState.DISCONNECTED
            else -> GreenhouseConnectionState.PREVIEW
        }

    companion object {
        val Unavailable = DecisionIotClimate(
            temperatureC = null,
            humidityPct = null,
            isLive = false,
            connectionState = GreenhouseConnectionState.DISCONNECTED
        )

        fun from(state: GreenhouseTelemetryState): DecisionIotClimate = when (state) {
            GreenhouseTelemetryState.Connecting -> DecisionIotClimate(
                temperatureC = null,
                humidityPct = null,
                isLive = false,
                connectionState = GreenhouseConnectionState.CONNECTING
            )
            is GreenhouseTelemetryState.Live -> DecisionIotClimate(
                temperatureC = state.snapshot.temperatureC,
                humidityPct = state.snapshot.humidityPct,
                isLive = true,
                connectionState = GreenhouseConnectionState.LIVE,
                timestampMillis = state.snapshot.timestampMillis
            )
            is GreenhouseTelemetryState.Stale -> DecisionIotClimate(
                temperatureC = null,
                humidityPct = null,
                isLive = false,
                connectionState = GreenhouseConnectionState.OFFLINE_DELAYED
            )
            is GreenhouseTelemetryState.Disconnected -> DecisionIotClimate(
                temperatureC = null,
                humidityPct = null,
                isLive = false,
                connectionState = GreenhouseConnectionState.DISCONNECTED
            )
        }
    }
}

fun formatDecisionTemperatureField(celsius: Double?): String =
    celsius?.let { String.format(Locale.US, "%.1f °C", it) } ?: "-- °C"

fun formatDecisionHumidityField(percent: Double?): String =
    percent?.let { String.format(Locale.US, "%.0f %%", it) } ?: "-- %"

/**
 * Overlay Firebase Live DS18B20 / HTU21D onto Dasuntha’s existing snapshot.
 * Backend telemetry may still supply infection/health/actuator fields.
 */
fun mergeDecisionEnvironment(
    telemetry: GreenhouseTelemetryResponse,
    iot: DecisionIotClimate
): GreenhouseEnvironmentSnapshot {
    return GreenhouseEnvironmentSnapshot(
        connectionState = iot.uiConnectionState,
        temperatureC = iot.displayTemperatureC,
        relativeHumidityPercent = iot.displayHumidityPct,
        sensorOrGreenhouseId = telemetry.sensorId.ifBlank { null },
        serverTimestampMillis = if (iot.isLive) iot.timestampMillis else null,
        lightLux = telemetry.lightLux,
        infectionCount = telemetry.infectionCount,
        health = telemetry.health,
        healthSummary = telemetry.healthSummary,
        healthColor = telemetry.healthColor,
        climateLevel = telemetry.climateLevel,
        infectionLevel = telemetry.infectionLevel
    )
}

fun overlayIotOnEnvironment(
    current: GreenhouseEnvironmentSnapshot,
    iot: DecisionIotClimate
): GreenhouseEnvironmentSnapshot {
    return current.copy(
        connectionState = iot.uiConnectionState,
        temperatureC = iot.displayTemperatureC,
        relativeHumidityPercent = iot.displayHumidityPct,
        serverTimestampMillis = if (iot.isLive) {
            iot.timestampMillis ?: current.serverTimestampMillis
        } else {
            null
        }
    )
}

fun heaterPredictRequestOrNull(
    telemetry: GreenhouseTelemetryResponse,
    iot: DecisionIotClimate
): HeaterPredictRequest? {
    val temp = iot.displayTemperatureC ?: return null
    val humidity = iot.displayHumidityPct ?: return null
    val target = telemetry.targetTemperature
    val tempOffTarget = abs(temp - target) >= 0.15
    val climateOff = !telemetry.climateOptimal
    if (!tempOffTarget && !climateOff) return null
    return HeaterPredictRequest(
        currentTemperature = temp,
        targetTemperature = target,
        humidity = humidity
    )
}
