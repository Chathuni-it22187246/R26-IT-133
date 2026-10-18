package com.greenhands.app.telemetry

import com.greenhands.app.heat.runtime.contracts.FreshnessPolicy
import com.greenhands.app.heat.runtime.contracts.RuntimeClock
import com.greenhands.app.heat.runtime.contracts.SystemRuntimeClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DefaultGreenhouseTelemetryRepository(
    private val auth: AnonymousAuthClient,
    private val dataSource: CurrentTelemetryDataSource,
    private val weatherDataSource: CurrentWeatherDataSource = NoOpCurrentWeatherDataSource,
    private val clock: RuntimeClock = SystemRuntimeClock,
    private val scope: CoroutineScope,
    private val heartbeat: TelemetryHeartbeat = CoroutineTelemetryHeartbeat(scope),
    private val policy: TelemetryFreshnessPolicy = TelemetryFreshnessPolicy(),
    private val weatherFreshness: FreshnessPolicy = FreshnessPolicy()
) : GreenhouseTelemetryRepository {
    private val _states = MutableStateFlow<GreenhouseTelemetryState>(GreenhouseTelemetryState.Connecting)
    private val _weatherStates = MutableStateFlow<GreenhouseWeatherState>(GreenhouseWeatherState.Connecting)
    override val states: StateFlow<GreenhouseTelemetryState> = _states.asStateFlow()
    override val weatherStates: StateFlow<GreenhouseWeatherState> = _weatherStates.asStateFlow()
    override val monitorsLiveGreenhouse: Boolean = true

    private var startJob: Job? = null
    private var subscription: TelemetrySubscription? = null
    private var weatherSubscription: TelemetrySubscription? = null
    private var started: Boolean = false
    private var awaitingFirstEvent: Boolean = true
    private var awaitingFirstWeather: Boolean = true
    private var lastDto: CurrentTelemetryDto? = null
    private var lastAccepted: AcceptedLiveTelemetry? = null
    private var lastWeatherDto: CurrentWeatherDto? = null
    private var lastAcceptedWeather: AcceptedWeather? = null

    override fun startMonitoring() {
        if (started) return
        started = true
        awaitingFirstEvent = true
        awaitingFirstWeather = true
        lastDto = null
        lastAccepted = null
        lastWeatherDto = null
        lastAcceptedWeather = null
        _states.value = GreenhouseTelemetryState.Connecting
        _weatherStates.value = GreenhouseWeatherState.Connecting
        startJob = scope.launch {
            val signedIn = auth.ensureSignedIn()
            if (!started) return@launch
            if (signedIn.isFailure) {
                started = false
                awaitingFirstEvent = false
                awaitingFirstWeather = false
                _states.value = GreenhouseTelemetryState.Disconnected(
                    lastKnown = null,
                    reason = TelemetryCopy.CONNECTION_ERROR
                )
                _weatherStates.value = GreenhouseWeatherState.Unavailable()
                return@launch
            }
            subscription = dataSource.listen { result ->
                if (!started) return@listen
                result.fold(
                    onSuccess = { dto ->
                        awaitingFirstEvent = false
                        lastDto = dto
                        publish(connectionError = false)
                    },
                    onFailure = {
                        awaitingFirstEvent = false
                        publish(connectionError = true)
                    }
                )
            }
            weatherSubscription = weatherDataSource.listen { result ->
                if (!started) return@listen
                result.fold(
                    onSuccess = { dto ->
                        awaitingFirstWeather = false
                        lastWeatherDto = dto
                        publishWeather(connectionError = false)
                    },
                    onFailure = {
                        awaitingFirstWeather = false
                        publishWeather(connectionError = true)
                    }
                )
            }
            heartbeat.start {
                if (started) {
                    publish(connectionError = false)
                    publishWeather(connectionError = false)
                }
            }
        }
    }

    override fun stopMonitoring() {
        started = false
        startJob?.cancel()
        startJob = null
        heartbeat.stop()
        subscription?.cancel()
        subscription = null
        weatherSubscription?.cancel()
        weatherSubscription = null
        awaitingFirstEvent = true
        awaitingFirstWeather = true
        lastDto = null
        lastAccepted = null
        lastWeatherDto = null
        lastAcceptedWeather = null
        _states.value = GreenhouseTelemetryState.Disconnected(null, null)
        _weatherStates.value = GreenhouseWeatherState.Unavailable()
    }

    private fun publish(connectionError: Boolean) {
        val evaluation = GreenhouseTelemetryEvaluator.evaluate(
            dto = lastDto,
            nowMillis = clock.nowMillis(),
            lastAccepted = lastAccepted,
            awaitingFirstEvent = awaitingFirstEvent,
            connectionError = connectionError,
            policy = policy
        )
        lastAccepted = evaluation.lastAccepted
        _states.value = evaluation.state
    }

    private fun publishWeather(connectionError: Boolean) {
        val evaluation = GreenhouseWeatherEvaluator.evaluate(
            dto = lastWeatherDto,
            nowMillis = clock.nowMillis(),
            lastAccepted = lastAcceptedWeather,
            awaitingFirstEvent = awaitingFirstWeather,
            connectionError = connectionError,
            policy = weatherFreshness
        )
        lastAcceptedWeather = evaluation.lastAccepted
        _weatherStates.value = evaluation.state
    }
}
