package com.greenhands.app.telemetry

import com.greenhands.app.ar.domain.ArHudContent
import com.greenhands.app.ar.domain.LiveSensorUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GreenhouseTelemetryMapperTest {

    @Test
    fun mapsTemperatureCFromDs18b20AuthoritativeField() {
        val dto = TelemetryFixtures.current(temperatureC = 28.41, ds18b20TemperatureC = 28.41)
        val parsed = GreenhouseTelemetryMapper.parseAuthoritative(dto)!!
        assertEquals(28.41, parsed.temperatureC, 0.0)
        assertEquals(28.41, GreenhouseTelemetryMapper.authoritativeTemperature(dto)!!, 0.0)
        assertEquals("DS18B20", dto.temperatureSource)
        assertEquals(TelemetryCopy.AUTHORITATIVE_TEMPERATURE, "temperature_c")
    }

    @Test
    fun mapsHumidityFromHumidityPct() {
        val dto = TelemetryFixtures.current(humidityPct = 61.25)
        val parsed = GreenhouseTelemetryMapper.parseAuthoritative(dto)!!
        assertEquals(61.25, parsed.humidityPct, 0.0)
        assertEquals(61.25, GreenhouseTelemetryMapper.authoritativeHumidity(dto)!!, 0.0)
        assertEquals("HTU21D", dto.humiditySource)
        assertEquals(TelemetryCopy.AUTHORITATIVE_HUMIDITY, "humidity_pct")
    }

    @Test
    fun rawHtu21dTemperatureIsIgnoredAsAuthoritativeTemperature() {
        val dto = TelemetryFixtures.current(temperatureC = 28.41, htu21dTemperatureC = 40.00)
        val parsed = GreenhouseTelemetryMapper.parseAuthoritative(dto)!!
        assertEquals(28.41, parsed.temperatureC, 0.0)
        assertNotEquals(40.00, parsed.temperatureC, 0.0)
        assertEquals(40.00, dto.raw!!.htu21dTemperatureC!!, 0.0)
        assertEquals(TelemetryCopy.RAW_HTU21D_TEMPERATURE, "htu21d_temperature_c")
    }

    @Test
    fun noAveragingOccursBetweenTemperatureSensors() {
        val dto = TelemetryFixtures.current(temperatureC = 28.41, htu21dTemperatureC = 40.00)
        val parsed = GreenhouseTelemetryMapper.parseAuthoritative(dto)!!
        val averaged = GreenhouseTelemetryMapper.averagedTemperature(dto)!!
        assertEquals(34.205, averaged, 0.0001)
        assertEquals(28.41, parsed.temperatureC, 0.0)
        assertNotEquals(averaged, parsed.temperatureC, 0.0)
        assertNotEquals((28.41 + 40.00) / 2.0, parsed.temperatureC, 0.0)
    }

    @Test
    fun validFreshSnapshotBecomesLive() {
        val dto = TelemetryFixtures.current(timestampMs = TelemetryFixtures.NOW_MS)
        val evaluation = GreenhouseTelemetryEvaluator.evaluate(
            dto = dto,
            nowMillis = TelemetryFixtures.NOW_MS + 5_000L,
            lastAccepted = null,
            awaitingFirstEvent = false,
            connectionError = false
        )
        val live = evaluation.state as GreenhouseTelemetryState.Live
        assertEquals(28.41, live.snapshot.temperatureC, 0.0)
        assertEquals(61.25, live.snapshot.humidityPct, 0.0)
        assertEquals(LiveSensorUiState.LIVE, evaluation.state.toLiveReading().uiState)
        assertEquals(TelemetryCopy.LIVE, ArHudContent.liveStatusLabel(evaluation.state.toLiveReading()))
        assertEquals("28.41 °C", ArHudContent.liveTemperatureLabel(evaluation.state.toLiveReading()))
        assertEquals("61.25 % RH", ArHudContent.liveHumidityLabel(evaluation.state.toLiveReading()))
    }

    @Test
    fun snapshotOlderThan20SecondsBecomesStale() {
        val dto = TelemetryFixtures.current(timestampMs = TelemetryFixtures.NOW_MS)
        val live = GreenhouseTelemetryEvaluator.evaluate(
            dto = dto,
            nowMillis = TelemetryFixtures.NOW_MS + TelemetryFreshnessPolicy.DEFAULT_STALE_AFTER_MS,
            lastAccepted = null,
            awaitingFirstEvent = false,
            connectionError = false
        )
        assertTrue(live.state is GreenhouseTelemetryState.Live)
        val stale = GreenhouseTelemetryEvaluator.evaluate(
            dto = dto,
            nowMillis = TelemetryFixtures.NOW_MS + TelemetryFreshnessPolicy.DEFAULT_STALE_AFTER_MS + 1L,
            lastAccepted = live.lastAccepted,
            awaitingFirstEvent = false,
            connectionError = false
        )
        val state = stale.state as GreenhouseTelemetryState.Stale
        assertEquals(28.41, state.lastKnown!!.temperatureC, 0.0)
        assertEquals(TelemetryCopy.STALE, ArHudContent.liveStatusLabel(stale.state.toLiveReading()))
        assertNotEquals(TelemetryCopy.LIVE, ArHudContent.liveStatusLabel(stale.state.toLiveReading()))
        assertEquals("28.41 °C", ArHudContent.liveTemperatureLabel(stale.state.toLiveReading()))
    }

    @Test
    fun cachedOldFirebasePayloadIsNeverLive() {
        val dto = TelemetryFixtures.current(timestampMs = TelemetryFixtures.NOW_MS)
        val evaluation = GreenhouseTelemetryEvaluator.evaluate(
            dto = dto,
            nowMillis = TelemetryFixtures.NOW_MS + 60_000L,
            lastAccepted = null,
            awaitingFirstEvent = true,
            connectionError = false
        )
        assertTrue(evaluation.state is GreenhouseTelemetryState.Stale)
        assertFalse(evaluation.state is GreenhouseTelemetryState.Live)
    }

    @Test
    fun invalidMissingNanAndOutOfRangeValuesAreRejected() {
        assertNull(GreenhouseTelemetryMapper.parseAuthoritative(TelemetryFixtures.current(temperatureC = null)))
        assertNull(GreenhouseTelemetryMapper.parseAuthoritative(TelemetryFixtures.current(humidityPct = null)))
        assertNull(GreenhouseTelemetryMapper.parseAuthoritative(TelemetryFixtures.current(timestampMs = null)))
        assertNull(GreenhouseTelemetryMapper.parseAuthoritative(TelemetryFixtures.current(status = "offline")))
        assertNull(GreenhouseTelemetryMapper.parseAuthoritative(TelemetryFixtures.current(temperatureC = Double.NaN)))
        assertNull(GreenhouseTelemetryMapper.parseAuthoritative(TelemetryFixtures.current(humidityPct = Double.NaN)))
        assertNull(GreenhouseTelemetryMapper.parseAuthoritative(TelemetryFixtures.current(temperatureC = Double.POSITIVE_INFINITY)))
        assertNull(GreenhouseTelemetryMapper.parseAuthoritative(TelemetryFixtures.current(humidityPct = -1.0)))
        assertNull(GreenhouseTelemetryMapper.parseAuthoritative(TelemetryFixtures.current(humidityPct = 100.1)))
        assertNull(GreenhouseTelemetryMapper.parseAuthoritative(TelemetryFixtures.current(temperatureC = -55.01)))
        assertNull(GreenhouseTelemetryMapper.parseAuthoritative(TelemetryFixtures.current(temperatureC = 125.01)))
        val rejected = GreenhouseTelemetryEvaluator.evaluate(
            dto = TelemetryFixtures.current(temperatureC = Double.NaN),
            nowMillis = TelemetryFixtures.NOW_MS,
            lastAccepted = null,
            awaitingFirstEvent = false,
            connectionError = false
        )
        assertTrue(rejected.state is GreenhouseTelemetryState.Disconnected)
        assertNotNull(GreenhouseTelemetryMapper.parseAuthoritative(TelemetryFixtures.current(temperatureC = -55.0)))
        assertNotNull(GreenhouseTelemetryMapper.parseAuthoritative(TelemetryFixtures.current(temperatureC = 125.0)))
        assertNotNull(GreenhouseTelemetryMapper.parseAuthoritative(TelemetryFixtures.current(humidityPct = 0.0)))
        assertNotNull(GreenhouseTelemetryMapper.parseAuthoritative(TelemetryFixtures.current(humidityPct = 100.0)))
    }

    @Test
    fun connectingAndDisconnectedLabelsMatchRequiredCopy() {
        assertEquals(
            TelemetryCopy.CONNECTING,
            ArHudContent.liveStatusLabel(GreenhouseTelemetryState.Connecting.toLiveReading())
        )
        assertEquals(
            TelemetryCopy.DISCONNECTED,
            ArHudContent.liveStatusLabel(
                GreenhouseTelemetryState.Disconnected().toLiveReading()
            )
        )
        assertEquals("Sensor connecting…", TelemetryCopy.CONNECTING)
        assertEquals("Sensor data stale", TelemetryCopy.STALE)
        assertEquals("Sensor disconnected", TelemetryCopy.DISCONNECTED)
        assertEquals("Live", TelemetryCopy.LIVE)
    }
}
