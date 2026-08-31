package com.greenhands.app.environment

/**
 * Connection boundary for future IoT → FastAPI → Firebase → GreenHands live data.
 * This phase populates PREVIEW only. Do not hardcode live timestamps.
 */
enum class GreenhouseConnectionState {
    PREVIEW,
    LIVE,
    OFFLINE_DELAYED
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
}

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

interface GreenhouseEnvironmentRepository {
    val snapshot: GreenhouseEnvironmentSnapshot
}

class PreviewGreenhouseEnvironmentRepository : GreenhouseEnvironmentRepository {
    override val snapshot: GreenhouseEnvironmentSnapshot = PreviewEnvironment.snapshot
}
