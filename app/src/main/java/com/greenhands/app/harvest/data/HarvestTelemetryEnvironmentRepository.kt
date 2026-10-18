package com.greenhands.app.harvest.data

import com.greenhands.app.environment.ConnectingEnvironment
import com.greenhands.app.environment.GreenhouseConnectionState
import com.greenhands.app.environment.GreenhouseEnvironmentRepository
import com.greenhands.app.environment.GreenhouseEnvironmentSnapshot
import com.greenhands.app.environment.UnconnectedEnvironment
import com.greenhands.app.telemetry.GreenhouseTelemetryPath
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
 * Harvesting adapter around the canonical My Greenhouse telemetry repository.
 * Does not own Firebase Auth, RTDB, or a second listener.
 *
 * Live temperature is always [GreenhouseTelemetryState.Live] `temperature_c`
 * (DS18B20). Humidity is `humidity_pct` (HTU21D). Raw HTU21D temperature is
 * never mapped. Stale last-known values are never shown or saved as Live.
 */
interface HarvestEnvironmentMonitor {
    fun acquire()
    fun release()
    val acquired: Boolean
}

class HarvestTelemetryEnvironmentRepository(
    private val telemetry: GreenhouseTelemetryRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
) : GreenhouseEnvironmentRepository, HarvestEnvironmentMonitor {

    private val lock = Any()
    private var collectJob: Job? = null
    @Volatile
    override var acquired: Boolean = false
        private set

    private val _snapshots = MutableStateFlow(UnconnectedEnvironment.snapshot)
    override val snapshot: GreenhouseEnvironmentSnapshot get() = _snapshots.value
    override val snapshots: StateFlow<GreenhouseEnvironmentSnapshot> = _snapshots.asStateFlow()

    override fun acquire() {
        synchronized(lock) {
            if (acquired) return
            acquired = true
            _snapshots.value = ConnectingEnvironment.snapshot
        }
        telemetry.startMonitoring()
        val job = scope.launch {
            telemetry.states.collect { state ->
                if (!acquired) return@collect
                _snapshots.value = map(state)
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

    override fun release() {
        val job: Job?
        synchronized(lock) {
            if (!acquired) return
            acquired = false
            job = collectJob
            collectJob = null
            _snapshots.value = UnconnectedEnvironment.snapshot
        }
        job?.cancel()
        telemetry.stopMonitoring()
    }

    companion object {
        fun map(state: GreenhouseTelemetryState): GreenhouseEnvironmentSnapshot = when (state) {
            GreenhouseTelemetryState.Connecting -> ConnectingEnvironment.snapshot
            is GreenhouseTelemetryState.Live -> GreenhouseEnvironmentSnapshot(
                connectionState = GreenhouseConnectionState.LIVE,
                temperatureC = state.snapshot.temperatureC,
                relativeHumidityPercent = state.snapshot.humidityPct,
                sensorOrGreenhouseId = state.snapshot.greenhouseId
                    ?: GreenhouseTelemetryPath.GREENHOUSE_ID,
                serverTimestampMillis = state.snapshot.timestampMillis
            )
            is GreenhouseTelemetryState.Stale -> GreenhouseEnvironmentSnapshot(
                connectionState = GreenhouseConnectionState.OFFLINE_DELAYED,
                temperatureC = null,
                relativeHumidityPercent = null,
                sensorOrGreenhouseId = state.lastKnown?.greenhouseId,
                serverTimestampMillis = null
            )
            is GreenhouseTelemetryState.Disconnected -> GreenhouseEnvironmentSnapshot(
                connectionState = GreenhouseConnectionState.DISCONNECTED,
                temperatureC = null,
                relativeHumidityPercent = null,
                sensorOrGreenhouseId = state.lastKnown?.greenhouseId,
                serverTimestampMillis = null
            )
        }
    }
}
