package com.greenhands.app.telemetry

import kotlinx.coroutines.flow.Flow

/**
 * Shared start/stop boundary so Heat and Harvesting can reuse one Firebase
 * current-telemetry listener. The first acquire starts the inner repository;
 * additional acquires reuse it; the last release stops it.
 *
 * Extra [stopMonitoring] calls when no subscriber is held are ignored so Demo
 * Mode cannot tear down another feature's subscription unless that feature
 * itself released — callers should still pair acquire/release.
 */
class ReferenceCountedGreenhouseTelemetryRepository(
    private val inner: GreenhouseTelemetryRepository
) : GreenhouseTelemetryRepository {
    private val lock = Any()
    private var subscribers: Int = 0

    override val states: Flow<GreenhouseTelemetryState> = inner.states
    override val weatherStates: Flow<GreenhouseWeatherState> = inner.weatherStates
    override val monitorsLiveGreenhouse: Boolean = inner.monitorsLiveGreenhouse

    val subscriberCount: Int
        get() = synchronized(lock) { subscribers }

    val innerRepository: GreenhouseTelemetryRepository
        get() = inner

    override fun startMonitoring() {
        val shouldStart = synchronized(lock) {
            subscribers += 1
            subscribers == 1
        }
        if (shouldStart) inner.startMonitoring()
    }

    override fun stopMonitoring() {
        val shouldStop = synchronized(lock) {
            if (subscribers <= 0) return
            subscribers -= 1
            subscribers == 0
        }
        if (shouldStop) inner.stopMonitoring()
    }
}
