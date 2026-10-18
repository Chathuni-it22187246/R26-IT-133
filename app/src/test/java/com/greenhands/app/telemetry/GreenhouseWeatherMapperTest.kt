package com.greenhands.app.telemetry

import com.greenhands.app.ar.domain.ArHudContent
import com.greenhands.app.heat.runtime.contracts.FreshnessPolicy
import com.greenhands.app.heat.runtime.contracts.WeatherBoundaryKind
import com.greenhands.app.heat.runtime.contracts.WeatherBoundaryStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GreenhouseWeatherMapperTest {

    @Test
    fun mapsValidOutsideFieldsWithoutTouchingIndoorTelemetry() {
        val dto = TelemetryFixtures.weather()
        val parsed = GreenhouseWeatherMapper.parseAuthoritative(dto)!!
        assertEquals(25.80, parsed.outsideTemperatureC, 0.0)
        assertEquals(89.00, parsed.outsideHumidityPct, 0.0)
        assertEquals("Colombo", parsed.locationName)
        assertEquals(WeatherCopy.SOURCE_OPEN_METEO, parsed.source)
        assertEquals(WeatherCopy.AUTHORITATIVE_TEMPERATURE, "outside_temperature_c")
        assertEquals(WeatherCopy.AUTHORITATIVE_HUMIDITY, "outside_humidity_pct")
        assertEquals(WeatherCopy.AUTHORITATIVE_INDOOR_TEMPERATURE, "temperature_c")
        assertEquals(WeatherCopy.AUTHORITATIVE_INDOOR_HUMIDITY, "humidity_pct")
        assertNotEquals(TelemetryFixtures.current().temperatureC, parsed.outsideTemperatureC)
        assertNotEquals(TelemetryFixtures.current().humidityPct, parsed.outsideHumidityPct)
    }

    @Test
    fun indoorAndOutsideFieldsAreNeverSwapped() {
        val indoor = TelemetryFixtures.current(temperatureC = 28.41, humidityPct = 61.25)
        val outdoor = TelemetryFixtures.weather(outsideTemperatureC = 25.80, outsideHumidityPct = 89.00)
        val indoorParsed = GreenhouseTelemetryMapper.parseAuthoritative(indoor)!!
        val outdoorParsed = GreenhouseWeatherMapper.parseAuthoritative(outdoor)!!
        assertEquals(28.41, indoorParsed.temperatureC, 0.0)
        assertEquals(61.25, indoorParsed.humidityPct, 0.0)
        assertEquals(25.80, outdoorParsed.outsideTemperatureC, 0.0)
        assertEquals(89.00, outdoorParsed.outsideHumidityPct, 0.0)
        assertNotEquals(indoorParsed.temperatureC, outdoorParsed.outsideTemperatureC)
        assertNotEquals(indoorParsed.humidityPct, outdoorParsed.outsideHumidityPct)
        assertNull(GreenhouseWeatherMapper.authoritativeOutsideTemperature(
            CurrentWeatherDto(outsideTemperatureC = indoor.temperatureC)
        )?.takeIf { it == indoor.humidityPct })
        assertEquals(25.80, GreenhouseWeatherMapper.authoritativeOutsideTemperature(outdoor))
        assertEquals(28.41, GreenhouseTelemetryMapper.authoritativeTemperature(indoor))
    }

    @Test
    fun invalidMissingNanAndOutOfRangeWeatherValuesAreRejected() {
        assertNull(GreenhouseWeatherMapper.parseAuthoritative(TelemetryFixtures.weather(outsideTemperatureC = null)))
        assertNull(GreenhouseWeatherMapper.parseAuthoritative(TelemetryFixtures.weather(outsideHumidityPct = null)))
        assertNull(GreenhouseWeatherMapper.parseAuthoritative(TelemetryFixtures.weather(timestampMs = null)))
        assertNull(GreenhouseWeatherMapper.parseAuthoritative(TelemetryFixtures.weather(status = "offline")))
        assertNull(GreenhouseWeatherMapper.parseAuthoritative(TelemetryFixtures.weather(outsideTemperatureC = Double.NaN)))
        assertNull(GreenhouseWeatherMapper.parseAuthoritative(TelemetryFixtures.weather(outsideHumidityPct = Double.NaN)))
        assertNull(GreenhouseWeatherMapper.parseAuthoritative(TelemetryFixtures.weather(outsideTemperatureC = Double.POSITIVE_INFINITY)))
        assertNull(GreenhouseWeatherMapper.parseAuthoritative(TelemetryFixtures.weather(outsideHumidityPct = -0.01)))
        assertNull(GreenhouseWeatherMapper.parseAuthoritative(TelemetryFixtures.weather(outsideHumidityPct = 100.01)))
        assertNull(GreenhouseWeatherMapper.parseAuthoritative(TelemetryFixtures.weather(outsideTemperatureC = -60.01)))
        assertNull(GreenhouseWeatherMapper.parseAuthoritative(TelemetryFixtures.weather(outsideTemperatureC = 60.01)))
        assertNotNull(GreenhouseWeatherMapper.parseAuthoritative(TelemetryFixtures.weather(outsideTemperatureC = -60.0)))
        assertNotNull(GreenhouseWeatherMapper.parseAuthoritative(TelemetryFixtures.weather(outsideTemperatureC = 60.0)))
        assertNotNull(GreenhouseWeatherMapper.parseAuthoritative(TelemetryFixtures.weather(outsideHumidityPct = 0.0)))
        assertNotNull(GreenhouseWeatherMapper.parseAuthoritative(TelemetryFixtures.weather(outsideHumidityPct = 100.0)))
    }

    @Test
    fun freshnessBoundaryIsThirtyMinutesAndStaleIsNeverLive() {
        val dto = TelemetryFixtures.weather(timestampMs = TelemetryFixtures.NOW_MS)
        val live = GreenhouseWeatherEvaluator.evaluate(
            dto = dto,
            nowMillis = TelemetryFixtures.NOW_MS + WeatherFreshnessPolicy.DEFAULT_STALE_AFTER_MS - 1L,
            lastAccepted = null,
            awaitingFirstEvent = false,
            connectionError = false
        )
        assertTrue(live.state is GreenhouseWeatherState.Live)
        assertEquals(WeatherCopy.LIVE, ArHudContent.outsideStatusLabel(live.state))
        assertEquals(WeatherBoundaryStatus.AVAILABLE, live.state.boundary().status)

        val atBoundary = GreenhouseWeatherEvaluator.evaluate(
            dto = dto,
            nowMillis = TelemetryFixtures.NOW_MS + WeatherFreshnessPolicy.DEFAULT_STALE_AFTER_MS,
            lastAccepted = live.lastAccepted,
            awaitingFirstEvent = false,
            connectionError = false
        )
        assertTrue(atBoundary.state is GreenhouseWeatherState.Stale)
        assertFalse(atBoundary.state is GreenhouseWeatherState.Live)
        assertEquals(WeatherCopy.STALE, ArHudContent.outsideStatusLabel(atBoundary.state))
        assertNotEquals(WeatherCopy.LIVE, ArHudContent.outsideStatusLabel(atBoundary.state))
        assertFalse(atBoundary.state.boundary().permitsQuantitativeExhaust())

        val cached = GreenhouseWeatherEvaluator.evaluate(
            dto = dto,
            nowMillis = TelemetryFixtures.NOW_MS + 60L * 60L * 1_000L,
            lastAccepted = null,
            awaitingFirstEvent = true,
            connectionError = false
        )
        assertTrue(cached.state is GreenhouseWeatherState.Stale)
        assertFalse(cached.state is GreenhouseWeatherState.Live)
        assertNotEquals(WeatherCopy.LIVE, ArHudContent.outsideStatusLabel(cached.state))
    }

    @Test
    fun liveWeatherHudKeepsIndoorReadingsSeparate() {
        val state = TelemetryFixtures.liveWeatherState()
        assertEquals("Outside 25.80 °C", ArHudContent.outsideTemperatureLabel(state))
        assertEquals("Outside 89.00 % RH", ArHudContent.outsideHumidityLabel(state))
        assertEquals("Colombo", ArHudContent.outsideLocationLabel(state))
        assertEquals(WeatherCopy.MODELLED_OPEN_METEO, WeatherCopy.MODELLED_OPEN_METEO)
        assertEquals(WeatherCopy.LIVE, ArHudContent.outsideStatusLabel(state))
        assertTrue(ArHudContent.weatherIsLive(state))
        val indoor = GreenhouseTelemetryState.Live(TelemetryFixtures.accepted()).toLiveReading()
        assertEquals("28.41 °C", ArHudContent.liveTemperatureLabel(indoor))
        assertEquals("61.25 % RH", ArHudContent.liveHumidityLabel(indoor))
        assertNotEquals(ArHudContent.liveTemperatureLabel(indoor), ArHudContent.outsideTemperatureLabel(state))
    }

    @Test
    fun estimatedBoundaryUsesModelledProvenanceAndThirtyMinutePolicy() {
        val accepted = TelemetryFixtures.acceptedWeather()
        val boundary = GreenhouseWeatherMapper.toBoundary(
            accepted,
            TelemetryFixtures.NOW_MS,
            FreshnessPolicy()
        )
        assertEquals(WeatherBoundaryStatus.AVAILABLE, boundary.status)
        assertEquals(WeatherBoundaryKind.ESTIMATED_OUTSIDE_WEATHER, boundary.kind)
        assertEquals(WeatherCopy.MODELLED_OPEN_METEO, boundary.sourceLabel)
        assertEquals("Colombo", boundary.locationName)
        assertEquals(25.80, boundary.outsideTemperatureC!!, 0.0)
        assertEquals(89.00, boundary.outsideHumidityPercent!!, 0.0)
        assertTrue(boundary.permitsQuantitativeExhaust())
    }
}
