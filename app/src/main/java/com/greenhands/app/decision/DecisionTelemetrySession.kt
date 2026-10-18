package com.greenhands.app.decision

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
 * Decision Making subscriber around the destination’s shared reference-counted
 * telemetry repository. Does not own Firebase Auth, RTDB, or a second listener.
 *
 * Acquire only while the Decision Making screen is visible. Recomposition must
 * not acquire twice. Leaving Decision Making cannot stop Heat or Harvesting if
 * either remains subscribed.
 */
class DecisionTelemetrySession(
    private val telemetry: GreenhouseTelemetryRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
) {
    private val lock = Any()
    private var collectJob: Job? = null

    @Volatile
    var acquired: Boolean = false
        private set

    private val _states = MutableStateFlow<GreenhouseTelemetryState>(
        GreenhouseTelemetryState.Disconnected()
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
