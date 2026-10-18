package com.greenhands.app.telemetry

/**
 * Maps a Firebase current-telemetry DTO to an accepted snapshot.
 * Authoritative temperature is always [CurrentTelemetryDto.temperatureC].
 * [CurrentTelemetryRawDto.htu21dTemperatureC] is ignored as climate input.
 */
object GreenhouseTelemetryMapper {
    fun parseAuthoritative(dto: CurrentTelemetryDto): AcceptedLiveTelemetry? {
        if (!dto.status.equals(GreenhouseTelemetryPath.ONLINE, ignoreCase = false)) return null
        val temperature = dto.temperatureC ?: return null
        val humidity = dto.humidityPct ?: return null
        if (!temperature.isFinite() || temperature !in Ds18b20Acceptance.MIN_C..Ds18b20Acceptance.MAX_C) {
            return null
        }
        if (!humidity.isFinite() || humidity !in 0.0..100.0) {
            return null
        }
        val timestamp = TelemetryTimestamps.resolveMillis(dto.serverTimestampMs, dto.receivedAt)
            ?: return null
        return AcceptedLiveTelemetry(
            temperatureC = temperature,
            humidityPct = humidity,
            timestampMillis = timestamp,
            greenhouseId = dto.greenhouseId ?: GreenhouseTelemetryPath.GREENHOUSE_ID,
            deviceId = dto.deviceId
        )
    }

    fun authoritativeTemperature(dto: CurrentTelemetryDto): Double? {
        val temperature = dto.temperatureC ?: return null
        return temperature.takeIf { it.isFinite() }
    }

    fun authoritativeHumidity(dto: CurrentTelemetryDto): Double? {
        val humidity = dto.humidityPct ?: return null
        return humidity.takeIf { it.isFinite() }
    }

    fun averagedTemperature(dto: CurrentTelemetryDto): Double? {
        val authoritative = dto.temperatureC
        val htu21d = dto.raw?.htu21dTemperatureC
        if (authoritative == null || htu21d == null) return null
        if (!authoritative.isFinite() || !htu21d.isFinite()) return null
        return (authoritative + htu21d) / 2.0
    }
}
