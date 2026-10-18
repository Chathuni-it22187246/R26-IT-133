package com.greenhands.app.telemetry

import com.greenhands.app.heat.runtime.contracts.FreshnessPolicy
import com.greenhands.app.heat.runtime.contracts.WeatherBoundary
import com.greenhands.app.heat.runtime.contracts.WeatherBoundaryKind
import com.greenhands.app.heat.runtime.contracts.WeatherBoundaryProvenance
import com.greenhands.app.heat.runtime.contracts.WeatherBoundaryStatus

/**
 * Maps a Firebase weather/current DTO to an accepted modelled-weather snapshot.
 * Indoor `temperature_c` / `humidity_pct` are never read here.
 */
object GreenhouseWeatherMapper {
    fun parseAuthoritative(dto: CurrentWeatherDto): AcceptedWeather? {
        if (!dto.status.equals(GreenhouseWeatherPath.ONLINE, ignoreCase = false)) return null
        val temperature = dto.outsideTemperatureC ?: return null
        val humidity = dto.outsideHumidityPct ?: return null
        if (!temperature.isFinite() || temperature !in OpenMeteoAcceptance.MIN_C..OpenMeteoAcceptance.MAX_C) {
            return null
        }
        if (!humidity.isFinite() || humidity !in 0.0..100.0) {
            return null
        }
        val timestamp = resolveTimestampMillis(dto) ?: return null
        val fetched = resolveFetchedMillis(dto)
        return AcceptedWeather(
            outsideTemperatureC = temperature,
            outsideHumidityPct = humidity,
            timestampMillis = timestamp,
            fetchedAtMillis = fetched,
            locationName = dto.locationName,
            source = dto.source?.takeIf { it.isNotBlank() } ?: WeatherCopy.SOURCE_OPEN_METEO,
            greenhouseId = GreenhouseTelemetryPath.GREENHOUSE_ID
        )
    }

    fun toBoundary(
        snapshot: AcceptedWeather?,
        nowMillis: Long,
        policy: FreshnessPolicy = FreshnessPolicy()
    ): WeatherBoundary {
        if (snapshot == null) return WeatherBoundary.missing()
        return WeatherBoundary.estimated(
            outsideTemperatureC = snapshot.outsideTemperatureC,
            outsideHumidityPercent = snapshot.outsideHumidityPct,
            weatherDataTimeMillis = snapshot.timestampMillis,
            fetchedAtMillis = snapshot.fetchedAtMillis ?: snapshot.timestampMillis,
            greenhouseLatitude = null,
            greenhouseLongitude = null,
            nowMillis = nowMillis,
            policy = policy,
            provider = snapshot.source,
            locationName = snapshot.locationName
        ).copy(
            sourceLabel = WeatherCopy.MODELLED_OPEN_METEO,
            provenance = WeatherBoundaryProvenance.WEATHER_API_ESTIMATE,
            kind = WeatherBoundaryKind.ESTIMATED_OUTSIDE_WEATHER
        )
    }

    fun authoritativeOutsideTemperature(dto: CurrentWeatherDto): Double? {
        val temperature = dto.outsideTemperatureC ?: return null
        return temperature.takeIf { it.isFinite() }
    }

    fun authoritativeOutsideHumidity(dto: CurrentWeatherDto): Double? {
        val humidity = dto.outsideHumidityPct ?: return null
        return humidity.takeIf { it.isFinite() }
    }

    fun resolveTimestampMillis(dto: CurrentWeatherDto): Long? {
        val server = dto.serverTimestampMs
        if (server != null && server > 0L) return server
        return TelemetryTimestamps.parseIso8601Utc(dto.observedAt)
            ?: TelemetryTimestamps.parseIso8601Utc(dto.fetchedAt)
    }

    fun resolveFetchedMillis(dto: CurrentWeatherDto): Long? {
        return TelemetryTimestamps.parseIso8601Utc(dto.fetchedAt)
            ?: dto.serverTimestampMs?.takeIf { it > 0L }
    }
}
