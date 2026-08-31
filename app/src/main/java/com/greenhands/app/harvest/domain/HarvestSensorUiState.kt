package com.greenhands.app.harvest.domain

import com.greenhands.app.environment.GreenhouseConnectionState
import com.greenhands.app.environment.GreenhouseEnvironmentSnapshot

/**
 * Harvesting Environment display state derived from [GreenhouseEnvironmentSnapshot].
 * The UI must observe this mapping rather than rendering hardcoded 25°C / 70%.
 * Preview sample numbers are never shown as live sensor readings.
 */
data class HarvestSensorUiState(
    val temperatureText: String,
    val humidityText: String,
    val statusText: String,
    val isConnecting: Boolean,
    val showsLiveValues: Boolean,
    val pendingDeviceNote: String?
) {
    companion object {
        const val MISSING_VALUE = "--"
        const val STATUS_LIVE = "Live"
        const val STATUS_NO_DATA = "No live sensor data"
        const val STATUS_CONNECTING = "Connecting to sensor..."
        const val PENDING_DEVICE_NOTE =
            "IoT UI/state integration is prepared, but the physical device/backend connection is still pending."

        fun from(snapshot: GreenhouseEnvironmentSnapshot): HarvestSensorUiState {
            return when (snapshot.connectionState) {
                GreenhouseConnectionState.CONNECTING -> HarvestSensorUiState(
                    temperatureText = MISSING_VALUE,
                    humidityText = MISSING_VALUE,
                    statusText = STATUS_CONNECTING,
                    isConnecting = true,
                    showsLiveValues = false,
                    pendingDeviceNote = PENDING_DEVICE_NOTE
                )
                GreenhouseConnectionState.LIVE -> {
                    val temp = formatTemperature(snapshot.temperatureC)
                    val humidity = formatHumidity(snapshot.relativeHumidityPercent)
                    val live = snapshot.temperatureC != null || snapshot.relativeHumidityPercent != null
                    HarvestSensorUiState(
                        temperatureText = temp,
                        humidityText = humidity,
                        statusText = if (live) STATUS_LIVE else STATUS_NO_DATA,
                        isConnecting = false,
                        showsLiveValues = live,
                        pendingDeviceNote = if (live) null else PENDING_DEVICE_NOTE
                    )
                }
                GreenhouseConnectionState.PREVIEW,
                GreenhouseConnectionState.OFFLINE_DELAYED,
                GreenhouseConnectionState.DISCONNECTED -> HarvestSensorUiState(
                    temperatureText = MISSING_VALUE,
                    humidityText = MISSING_VALUE,
                    statusText = STATUS_NO_DATA,
                    isConnecting = false,
                    showsLiveValues = false,
                    pendingDeviceNote = PENDING_DEVICE_NOTE
                )
            }
        }

        fun formatTemperature(celsius: Double?): String {
            if (celsius == null) return MISSING_VALUE
            return if (celsius % 1.0 == 0.0) {
                String.format(java.util.Locale.US, "%.0f°C", celsius)
            } else {
                String.format(java.util.Locale.US, "%.1f°C", celsius)
            }
        }

        fun formatHumidity(percent: Double?): String {
            if (percent == null) return MISSING_VALUE
            return String.format(java.util.Locale.US, "%.0f%%", percent)
        }
    }
}
