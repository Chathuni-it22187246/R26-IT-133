package com.greenhands.app.telemetry

import com.greenhands.app.heat.runtime.contracts.FakeRuntimeClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultGreenhouseTelemetryRepositoryTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Test
    fun anonymousAuthenticationReusesAnExistingUser() = runTest(dispatcher) {
        val auth = FakeAnonymousAuthClient(currentUserPresent = true)
        val source = FakeCurrentTelemetryDataSource()
        val repo = repository(auth, source)
        repo.startMonitoring()
        repo.startMonitoring()
        assertEquals(0, auth.signInCount)
        assertTrue(auth.currentUserPresent)
        assertEquals(1, source.listenCount)
        repo.stopMonitoring()
    }

    @Test
    fun anonymousAuthenticationOccursOnceWhenNeeded() = runTest(dispatcher) {
        val auth = FakeAnonymousAuthClient()
        val source = FakeCurrentTelemetryDataSource()
        val weather = FakeCurrentWeatherDataSource()
        val repo = repository(auth, source, weather)
        repo.startMonitoring()
        repo.startMonitoring()
        assertEquals(1, auth.signInCount)
        assertTrue(auth.currentUserPresent)
        assertEquals(1, source.listenCount)
        assertEquals(1, weather.listenCount)
        repo.stopMonitoring()
        assertEquals(1, source.cancelCount)
        assertEquals(1, weather.cancelCount)
        assertFalse(source.listenerActive)
        assertFalse(weather.listenerActive)
    }

    @Test
    fun authenticationFailureProducesErrorWithoutCrashing() = runTest(dispatcher) {
        val auth = FakeAnonymousAuthClient(fail = true)
        val source = FakeCurrentTelemetryDataSource()
        val repo = repository(auth, source)
        repo.startMonitoring()
        val state = repo.states.value as GreenhouseTelemetryState.Disconnected
        assertEquals(TelemetryCopy.CONNECTION_ERROR, state.reason)
        assertEquals(0, source.listenCount)
        repo.stopMonitoring()
    }

    @Test
    fun listenerIsInstalledOnceAndRemovedSafely() = runTest(dispatcher) {
        val source = FakeCurrentTelemetryDataSource()
        val weather = FakeCurrentWeatherDataSource()
        val heartbeat = ManualTelemetryHeartbeat()
        val clock = FakeRuntimeClock(TelemetryFixtures.NOW_MS)
        val repo = DefaultGreenhouseTelemetryRepository(
            auth = FakeAnonymousAuthClient(),
            dataSource = source,
            weatherDataSource = weather,
            clock = clock,
            scope = CoroutineScope(dispatcher),
            heartbeat = heartbeat
        )
        repo.startMonitoring()
        repo.startMonitoring()
        assertEquals(1, source.listenCount)
        assertEquals(1, weather.listenCount)
        source.emit(TelemetryFixtures.current())
        weather.emit(TelemetryFixtures.weather())
        assertTrue(repo.states.value is GreenhouseTelemetryState.Live)
        assertTrue(repo.weatherStates.value is GreenhouseWeatherState.Live)
        clock.millis = TelemetryFixtures.NOW_MS + TelemetryFreshnessPolicy.DEFAULT_STALE_AFTER_MS + 1L
        heartbeat.pulse()
        assertTrue(repo.states.value is GreenhouseTelemetryState.Stale)
        clock.millis = TelemetryFixtures.NOW_MS + WeatherFreshnessPolicy.DEFAULT_STALE_AFTER_MS + 1L
        heartbeat.pulse()
        assertTrue(repo.weatherStates.value is GreenhouseWeatherState.Stale)
        assertFalse(repo.weatherStates.value is GreenhouseWeatherState.Live)
        repo.stopMonitoring()
        assertEquals(1, source.cancelCount)
        assertEquals(1, weather.cancelCount)
        assertFalse(source.listenerActive)
        assertFalse(weather.listenerActive)
        source.emit(TelemetryFixtures.current(temperatureC = 99.0))
        weather.emit(TelemetryFixtures.weather(outsideTemperatureC = 11.0))
        assertTrue(repo.states.value is GreenhouseTelemetryState.Disconnected)
        assertTrue(repo.weatherStates.value is GreenhouseWeatherState.Unavailable)
    }

    private fun repository(
        auth: FakeAnonymousAuthClient,
        source: FakeCurrentTelemetryDataSource,
        weather: FakeCurrentWeatherDataSource = FakeCurrentWeatherDataSource()
    ): DefaultGreenhouseTelemetryRepository {
        return DefaultGreenhouseTelemetryRepository(
            auth = auth,
            dataSource = source,
            weatherDataSource = weather,
            clock = FakeRuntimeClock(TelemetryFixtures.NOW_MS),
            scope = CoroutineScope(dispatcher),
            heartbeat = ManualTelemetryHeartbeat()
        )
    }
}
