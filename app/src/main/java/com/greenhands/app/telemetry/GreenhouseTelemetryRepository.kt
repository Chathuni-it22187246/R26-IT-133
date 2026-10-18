package com.greenhands.app.telemetry

import com.greenhands.app.ar.domain.ArLiveEnvironmentReading
import com.greenhands.app.ar.domain.LiveSensorUiState
import com.greenhands.app.equipment.future.EnvironmentDataSourceKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Testable My Greenhouse telemetry boundary. Demo Mode must not start this.
 */
interface GreenhouseTelemetryRepository {
    val states: Flow<GreenhouseTelemetryState>
    val weatherStates: Flow<GreenhouseWeatherState>
        get() = emptyFlow()
    val monitorsLiveGreenhouse: Boolean
    fun startMonitoring()
    fun stopMonitoring()
}

object NoOpGreenhouseTelemetryRepository : GreenhouseTelemetryRepository {
    override val states: Flow<GreenhouseTelemetryState> = emptyFlow()
    override val weatherStates: Flow<GreenhouseWeatherState> = emptyFlow()
    override val monitorsLiveGreenhouse: Boolean = false
    override fun startMonitoring() = Unit
    override fun stopMonitoring() = Unit
}

class FakeGreenhouseTelemetryRepository(
    initial: GreenhouseTelemetryState = GreenhouseTelemetryState.Connecting,
    initialWeather: GreenhouseWeatherState = GreenhouseWeatherState.Connecting
) : GreenhouseTelemetryRepository {
    private val _states = MutableStateFlow(initial)
    private val _weatherStates = MutableStateFlow(initialWeather)
    override val states: StateFlow<GreenhouseTelemetryState> = _states.asStateFlow()
    override val weatherStates: StateFlow<GreenhouseWeatherState> = _weatherStates.asStateFlow()
    override val monitorsLiveGreenhouse: Boolean = true
    var startCount: Int = 0
        private set
    var stopCount: Int = 0
        private set
    var monitoring: Boolean = false
        private set

    override fun startMonitoring() {
        startCount += 1
        monitoring = true
    }

    override fun stopMonitoring() {
        stopCount += 1
        monitoring = false
    }

    fun publish(state: GreenhouseTelemetryState) {
        _states.value = state
    }

    fun publishWeather(state: GreenhouseWeatherState) {
        _weatherStates.value = state
    }
}

fun GreenhouseTelemetryState.toLiveReading(): ArLiveEnvironmentReading = when (this) {
    GreenhouseTelemetryState.Connecting -> ArLiveEnvironmentReading(
        uiState = LiveSensorUiState.CONNECTING
    )
    is GreenhouseTelemetryState.Live -> ArLiveEnvironmentReading(
        temperatureC = snapshot.temperatureC,
        relativeHumidityPercent = snapshot.humidityPct,
        connected = true,
        timestampMillis = snapshot.timestampMillis,
        uiState = LiveSensorUiState.LIVE
    )
    is GreenhouseTelemetryState.Stale -> ArLiveEnvironmentReading(
        temperatureC = lastKnown?.temperatureC,
        relativeHumidityPercent = lastKnown?.humidityPct,
        connected = false,
        timestampMillis = lastKnown?.timestampMillis,
        uiState = LiveSensorUiState.STALE
    )
    is GreenhouseTelemetryState.Disconnected -> ArLiveEnvironmentReading(
        temperatureC = lastKnown?.temperatureC,
        relativeHumidityPercent = lastKnown?.humidityPct,
        connected = false,
        timestampMillis = lastKnown?.timestampMillis,
        uiState = LiveSensorUiState.DISCONNECTED
    )
}

fun GreenhouseTelemetryState.environmentKind(): EnvironmentDataSourceKind = when (this) {
    is GreenhouseTelemetryState.Live -> EnvironmentDataSourceKind.LIVE
    is GreenhouseTelemetryState.Stale -> EnvironmentDataSourceKind.OFFLINE_DELAYED
    else -> EnvironmentDataSourceKind.PREVIEW
}
