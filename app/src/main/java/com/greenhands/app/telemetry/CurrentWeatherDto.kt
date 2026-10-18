package com.greenhands.app.telemetry

import com.greenhands.app.heat.runtime.contracts.WeatherBoundary

/**
 * Read-only Firebase current-weather payload at `greenhouses/GH_001/weather/current`.
 *
 * Modelled Open-Meteo fields only. Never averaged with indoor `temperature_c` /
 * `humidity_pct`, and never treated as a greenhouse IoT measurement.
 */
data class CurrentWeatherDto(
    val outsideTemperatureC: Double? = null,
    val outsideHumidityPct: Double? = null,
    val source: String? = null,
    val locationName: String? = null,
    val observedAt: String? = null,
    val fetchedAt: String? = null,
    val serverTimestampMs: Long? = null,
    val status: String? = null
)

object GreenhouseWeatherPath {
    const val WEATHER = "weather"
    const val CURRENT = GreenhouseTelemetryPath.CURRENT
    const val ONLINE = GreenhouseTelemetryPath.ONLINE

    val currentSegments: List<String> = listOf(
        GreenhouseTelemetryPath.ROOT,
        GreenhouseTelemetryPath.GREENHOUSE_ID,
        WEATHER,
        CURRENT
    )
}

data class WeatherFreshnessPolicy(
    val staleAfterMs: Long = DEFAULT_STALE_AFTER_MS
) {
    init {
        require(staleAfterMs > 0L)
    }

    companion object {
        const val DEFAULT_STALE_AFTER_MS = 30L * 60L * 1_000L
    }
}

data class AcceptedWeather(
    val outsideTemperatureC: Double,
    val outsideHumidityPct: Double,
    val timestampMillis: Long,
    val fetchedAtMillis: Long?,
    val locationName: String?,
    val source: String,
    val greenhouseId: String
)

sealed class GreenhouseWeatherState {
    data object Connecting : GreenhouseWeatherState()

    data class Live(
        val snapshot: AcceptedWeather,
        val boundary: WeatherBoundary
    ) : GreenhouseWeatherState()

    data class Stale(
        val lastKnown: AcceptedWeather?,
        val boundary: WeatherBoundary
    ) : GreenhouseWeatherState()

    data class Unavailable(
        val lastKnown: AcceptedWeather? = null,
        val boundary: WeatherBoundary = WeatherBoundary.missing()
    ) : GreenhouseWeatherState()
}

fun GreenhouseWeatherState.boundary(): WeatherBoundary = when (this) {
    GreenhouseWeatherState.Connecting -> WeatherBoundary.missing()
    is GreenhouseWeatherState.Live -> boundary
    is GreenhouseWeatherState.Stale -> boundary
    is GreenhouseWeatherState.Unavailable -> boundary
}

fun GreenhouseWeatherState.isLive(): Boolean = this is GreenhouseWeatherState.Live

fun GreenhouseWeatherState.acceptedOrNull(): AcceptedWeather? = when (this) {
    GreenhouseWeatherState.Connecting -> null
    is GreenhouseWeatherState.Live -> snapshot
    is GreenhouseWeatherState.Stale -> lastKnown
    is GreenhouseWeatherState.Unavailable -> lastKnown
}
