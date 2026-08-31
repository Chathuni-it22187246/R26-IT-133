package com.greenhands.app.environment

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf

/**
 * Connection boundary for greenhouse sensor readings.
 *
 * Harvesting Environment UI observes [GreenhouseEnvironmentRepository.snapshots].
 * A future physical IoT adapter should implement this same repository and emit
 * [GreenhouseConnectionState.LIVE] values. Do not hardcode device IPs, MQTT
 * brokers, Firebase URLs, credentials, or API endpoints here.
 */
enum class GreenhouseConnectionState {
    PREVIEW,
    LIVE,
    OFFLINE_DELAYED,
    DISCONNECTED,
    CONNECTING
}

data class GreenhouseEnvironmentSnapshot(
    val connectionState: GreenhouseConnectionState,
    val temperatureC: Double? = null,
    val relativeHumidityPercent: Double? = null,
    val sensorOrGreenhouseId: String? = null,
    val serverTimestampMillis: Long? = null
) {
    val showsLiveTimestamp: Boolean
        get() = connectionState == GreenhouseConnectionState.LIVE && serverTimestampMillis != null

    val isSamplePreview: Boolean
        get() = connectionState == GreenhouseConnectionState.PREVIEW

    val lastUpdatedMillis: Long?
        get() = serverTimestampMillis
}

/**
 * Test / Compose-preview sample numbers only. Never wire this repository
 * into the production Harvesting or Dashboard path. UI maps PREVIEW the
 * same as DISCONNECTED: -- / No live sensor data.
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

/**
 * Shared sensor/environment source used by Harvesting (and later LIVE IoT).
 * This is the project's SensorDataRepository boundary: temperature, humidity,
 * last-updated, and connection state. Harvest must not duplicate this source.
 *
 * Switching DISCONNECTED → LIVE later is an implementation swap behind this
 * interface; harvest UI should keep reading [snapshots].
 */
interface GreenhouseEnvironmentRepository {
    val snapshot: GreenhouseEnvironmentSnapshot
    val snapshots: Flow<GreenhouseEnvironmentSnapshot>
}

typealias SensorDataRepository = GreenhouseEnvironmentRepository

class PreviewGreenhouseEnvironmentRepository : GreenhouseEnvironmentRepository {
    override val snapshot: GreenhouseEnvironmentSnapshot = PreviewEnvironment.snapshot
    override val snapshots: Flow<GreenhouseEnvironmentSnapshot> = flowOf(snapshot)
}

/**
 * Default Harvesting and Dashboard sensor source until a physical IoT adapter is wired.
 * Emits no temperature/humidity values so the UI cannot treat 25°C / 70% as live.
 */
class UnconnectedGreenhouseEnvironmentRepository : GreenhouseEnvironmentRepository {
    override val snapshot: GreenhouseEnvironmentSnapshot = UnconnectedEnvironment.snapshot
    override val snapshots: Flow<GreenhouseEnvironmentSnapshot> = flowOf(snapshot)
}

/** Mutable source for tests and a future LIVE adapter. Not a second harvest-local feed. */
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
