package com.greenhands.app.sensor.telemetry

import com.greenhands.app.telemetry.GreenhouseTelemetryRepository
import com.greenhands.app.telemetry.GreenhouseTelemetryState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Sensor Real AR subscriber around the shared reference-counted telemetry
 * repository. Does not own Firebase Auth, RTDB, or a second listener.
 *
 * Acquire only while `sensor_real_ar` is active. Recomposition must not
 * acquire twice. Leaving Sensor AR cannot stop Heat or Harvesting if either
 * remains subscribed.
 */
class SensorArTelemetrySession(
    private val telemetry: GreenhouseTelemetryRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
) {
    private val lock = Any()
    private var collectJob: Job? = null

    @Volatile
    var acquired: Boolean = false
        private set

    private val _states = MutableStateFlow<GreenhouseTelemetryState>(
        GreenhouseTelemetryState.Connecting
    )
    val states: StateFlow<GreenhouseTelemetryState> = _states.asStateFlow()

    fun acquire() {
        synchronized(lock) {
            if (acquired) return
            acquired = true
            _states.value = GreenhouseTelemetryState.Connecting
        }
        telemetry.startMonitoring()
        val job = scope.launch {
            telemetry.states.collect { state ->
                if (!acquired) return@collect
                _states.value = state
            }
        }
        synchronized(lock) {
            if (!acquired) {
                job.cancel()
            } else {
                collectJob = job
            }
        }
    }

    fun release() {
        val job: Job?
        synchronized(lock) {
            if (!acquired) return
            acquired = false
            job = collectJob
            collectJob = null
            _states.value = GreenhouseTelemetryState.Disconnected()
        }
        job?.cancel()
        telemetry.stopMonitoring()
    }
}
