package com.greenhands.app.telemetry

internal object TelemetryFixtures {
    const val NOW_MS = 1_700_000_000_000L

    fun current(
        temperatureC: Double? = 28.41,
        humidityPct: Double? = 61.25,
        timestampMs: Long? = NOW_MS,
        receivedAt: String? = null,
        status: String? = GreenhouseTelemetryPath.ONLINE,
        htu21dTemperatureC: Double? = 40.00,
        ds18b20TemperatureC: Double? = 28.41,
        htu21dHumidityPct: Double? = 61.25,
        greenhouseId: String? = GreenhouseTelemetryPath.GREENHOUSE_ID,
        deviceId: String? = GreenhouseTelemetryPath.DEVICE_ID
    ): CurrentTelemetryDto = CurrentTelemetryDto(
        temperatureC = temperatureC,
        humidityPct = humidityPct,
        temperatureSource = "DS18B20",
        humiditySource = "HTU21D",
        receivedAt = receivedAt,
        serverTimestampMs = timestampMs,
        deviceId = deviceId,
        greenhouseId = greenhouseId,
        status = status,
        raw = CurrentTelemetryRawDto(
            htu21dTemperatureC = htu21dTemperatureC,
            ds18b20TemperatureC = ds18b20TemperatureC,
            htu21dHumidityPct = htu21dHumidityPct
        )
    )

    fun accepted(
        temperatureC: Double = 28.41,
        humidityPct: Double = 61.25,
        timestampMs: Long = NOW_MS
    ): AcceptedLiveTelemetry = AcceptedLiveTelemetry(
        temperatureC = temperatureC,
        humidityPct = humidityPct,
        timestampMillis = timestampMs,
        greenhouseId = GreenhouseTelemetryPath.GREENHOUSE_ID,
        deviceId = GreenhouseTelemetryPath.DEVICE_ID
    )

    fun weather(
        outsideTemperatureC: Double? = 25.80,
        outsideHumidityPct: Double? = 89.00,
        timestampMs: Long? = NOW_MS,
        observedAt: String? = null,
        fetchedAt: String? = null,
        status: String? = GreenhouseWeatherPath.ONLINE,
        source: String? = WeatherCopy.SOURCE_OPEN_METEO,
        locationName: String? = "Colombo"
    ): CurrentWeatherDto = CurrentWeatherDto(
        outsideTemperatureC = outsideTemperatureC,
        outsideHumidityPct = outsideHumidityPct,
        source = source,
        locationName = locationName,
        observedAt = observedAt,
        fetchedAt = fetchedAt,
        serverTimestampMs = timestampMs,
        status = status
    )

    fun acceptedWeather(
        outsideTemperatureC: Double = 25.80,
        outsideHumidityPct: Double = 89.00,
        timestampMs: Long = NOW_MS,
        locationName: String? = "Colombo"
    ): AcceptedWeather = AcceptedWeather(
        outsideTemperatureC = outsideTemperatureC,
        outsideHumidityPct = outsideHumidityPct,
        timestampMillis = timestampMs,
        fetchedAtMillis = timestampMs,
        locationName = locationName,
        source = WeatherCopy.SOURCE_OPEN_METEO,
        greenhouseId = GreenhouseTelemetryPath.GREENHOUSE_ID
    )

    fun liveWeatherState(
        outsideTemperatureC: Double = 25.80,
        outsideHumidityPct: Double = 89.00,
        timestampMs: Long = NOW_MS,
        locationName: String? = "Colombo",
        nowMillis: Long = NOW_MS
    ): GreenhouseWeatherState {
        val accepted = acceptedWeather(
            outsideTemperatureC,
            outsideHumidityPct,
            timestampMs,
            locationName
        )
        return GreenhouseWeatherEvaluator.classify(accepted, nowMillis).state
    }
}
