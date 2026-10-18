package com.greenhands.app.environment

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import java.util.Locale

/**
 * Connection boundary for greenhouse sensor readings.
 *
 * Dashboard Preview Mode continues to use [PreviewEnvironment]. Harvesting
 * observes [GreenhouseEnvironmentRepository.snapshots] through the telemetry
 * adapter and must not treat Preview sample numbers as Live.
 */
enum class GreenhouseConnectionState {
    PREVIEW,
    LIVE,
    OFFLINE_DELAYED,
    DISCONNECTED,
    CONNECTING
}

enum class GreenhouseHealthLevel(val label: String) {
    OPTIMAL("Optimal"),
    WARNING("Warning"),
    CRITICAL("Critical"),
    STANDBY("Standby")
}

fun parseGreenhouseHealthLevel(raw: String?): GreenhouseHealthLevel {
    return when (raw?.trim()?.lowercase()) {
        "optimal", "green" -> GreenhouseHealthLevel.OPTIMAL
        "warning", "yellow" -> GreenhouseHealthLevel.WARNING
        "critical", "red" -> GreenhouseHealthLevel.CRITICAL
        "standby" -> GreenhouseHealthLevel.STANDBY
        else -> GreenhouseHealthLevel.STANDBY
    }
}

fun worseHealth(a: GreenhouseHealthLevel, b: GreenhouseHealthLevel): GreenhouseHealthLevel {
    fun rank(level: GreenhouseHealthLevel): Int = when (level) {
        GreenhouseHealthLevel.STANDBY -> 0
        GreenhouseHealthLevel.OPTIMAL -> 1
        GreenhouseHealthLevel.WARNING -> 2
        GreenhouseHealthLevel.CRITICAL -> 3
    }
    return if (rank(a) >= rank(b)) a else b
}

fun combinedHealthFromReadings(
    temperatureC: Double?,
    humidityPercent: Double?,
    infectionCount: Int
): GreenhouseHealthLevel {
    if (temperatureC == null && humidityPercent == null && infectionCount <= 0) {
        return GreenhouseHealthLevel.STANDBY
    }
    val climate = when {
        temperatureC != null && (temperatureC < 20.0 || temperatureC > 32.0) ->
            GreenhouseHealthLevel.CRITICAL
        humidityPercent != null && (humidityPercent < 40.0 || humidityPercent > 90.0) ->
            GreenhouseHealthLevel.CRITICAL
        temperatureC != null && (temperatureC < 22.0 || temperatureC > 28.0) ->
            GreenhouseHealthLevel.WARNING
        humidityPercent != null && (humidityPercent < 55.0 || humidityPercent > 75.0) ->
            GreenhouseHealthLevel.WARNING
        else -> GreenhouseHealthLevel.OPTIMAL
    }
    val infections = when {
        infectionCount >= 2 -> GreenhouseHealthLevel.CRITICAL
        infectionCount == 1 -> GreenhouseHealthLevel.WARNING
        else -> GreenhouseHealthLevel.OPTIMAL
    }
    return worseHealth(climate, infections)
}

fun formatHealthSummary(
    level: GreenhouseHealthLevel,
    temperatureC: Double?,
    humidityPercent: Double?,
    infectionCount: Int
): String {
    if (level == GreenhouseHealthLevel.STANDBY) {
        return "Waiting for live greenhouse readings."
    }
    if (level == GreenhouseHealthLevel.OPTIMAL) {
        return "Climate on target and no unresolved infections."
    }
    val climateBit = when {
        temperatureC != null && (temperatureC < 22.0 || temperatureC > 28.0) ->
            String.format(Locale.US, "temperature %.1f°C off target", temperatureC)
        humidityPercent != null && (humidityPercent < 55.0 || humidityPercent > 75.0) ->
            "humidity ${humidityPercent.toInt()}% off target"
        else -> "climate on target"
    }
    val infectionBit = when {
        infectionCount <= 0 -> "no unresolved infections"
        infectionCount == 1 -> "1 unresolved infection"
        else -> "$infectionCount unresolved infections"
    }
    return "${level.label}: $climateBit · $infectionBit."
}

data class GreenhouseEnvironmentSnapshot(
    val connectionState: GreenhouseConnectionState,
    val temperatureC: Double? = null,
    val relativeHumidityPercent: Double? = null,
    val sensorOrGreenhouseId: String? = null,
    val serverTimestampMillis: Long? = null,
    val lightLux: Double? = null,
    val infectionCount: Int = 0,
    val health: String = "",
    val healthSummary: String = "",
    val healthColor: String = "",
    val climateLevel: String = "",
    val infectionLevel: String = ""
) {
    val showsLiveTimestamp: Boolean
        get() = connectionState == GreenhouseConnectionState.LIVE && serverTimestampMillis != null

    val isSamplePreview: Boolean
        get() = connectionState == GreenhouseConnectionState.PREVIEW

    val lastUpdatedMillis: Long?
        get() = serverTimestampMillis

    val healthLevel: GreenhouseHealthLevel
        get() {
            val fromBackend = parseGreenhouseHealthLevel(health)
            if (fromBackend != GreenhouseHealthLevel.STANDBY) return fromBackend
            return combinedHealthFromReadings(
                temperatureC,
                relativeHumidityPercent,
                infectionCount
            )
        }
}

/**
 * Test / Compose-preview sample numbers only. Never wire this repository
 * into production Harvesting. Dashboard Preview Mode still uses this snapshot.
 */
object PreviewEnvironment {
    const val SAMPLE_TEMPERATURE_C = 25.0
    const val SAMPLE_HUMIDITY_PERCENT = 70.0

    val snapshot = GreenhouseEnvironmentSnapshot(
        connectionState = GreenhouseConnectionState.PREVIEW,
        temperatureC = SAMPLE_TEMPERATURE_C,
        relativeHumidityPercent = SAMPLE_HUMIDITY_PERCENT,
        sensorOrGreenhouseId = null,
        serverTimestampMillis = null
    )
}

object UnconnectedEnvironment {
    val snapshot = GreenhouseEnvironmentSnapshot(
        connectionState = GreenhouseConnectionState.DISCONNECTED,
        temperatureC = null,
        relativeHumidityPercent = null,
        sensorOrGreenhouseId = null,
        serverTimestampMillis = null
    )
}

object ConnectingEnvironment {
    val snapshot = GreenhouseEnvironmentSnapshot(
        connectionState = GreenhouseConnectionState.CONNECTING,
        temperatureC = null,
        relativeHumidityPercent = null,
        sensorOrGreenhouseId = null,
        serverTimestampMillis = null
    )
}

interface GreenhouseEnvironmentRepository {
    val snapshot: GreenhouseEnvironmentSnapshot
    val snapshots: Flow<GreenhouseEnvironmentSnapshot>
        get() = flowOf(snapshot)
}

typealias SensorDataRepository = GreenhouseEnvironmentRepository

class PreviewGreenhouseEnvironmentRepository : GreenhouseEnvironmentRepository {
    override val snapshot: GreenhouseEnvironmentSnapshot = PreviewEnvironment.snapshot
    override val snapshots: Flow<GreenhouseEnvironmentSnapshot> = flowOf(snapshot)
}

class UnconnectedGreenhouseEnvironmentRepository : GreenhouseEnvironmentRepository {
    override val snapshot: GreenhouseEnvironmentSnapshot = UnconnectedEnvironment.snapshot
    override val snapshots: Flow<GreenhouseEnvironmentSnapshot> = flowOf(snapshot)
}

class InMemoryGreenhouseEnvironmentRepository(
    initial: GreenhouseEnvironmentSnapshot = UnconnectedEnvironment.snapshot
) : GreenhouseEnvironmentRepository {
    private val _snapshots = MutableStateFlow(initial)
    override val snapshot: GreenhouseEnvironmentSnapshot get() = _snapshots.value
    override val snapshots: Flow<GreenhouseEnvironmentSnapshot> = _snapshots.asStateFlow()

    fun emit(next: GreenhouseEnvironmentSnapshot) {
        _snapshots.value = next
    }
}
