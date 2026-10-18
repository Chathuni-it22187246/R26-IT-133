package com.greenhands.app.telemetry

import com.greenhands.app.heat.runtime.contracts.SystemRuntimeClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Process-scoped My Greenhouse telemetry. Listeners start only when monitoring is requested.
 * Heat and Harvesting share one reference-counted owner so the last subscriber removes
 * the Firebase listener. Dashboard and login never start this.
 */
object GreenHandsTelemetry {
    @Volatile
    private var repository: GreenhouseTelemetryRepository? = null

    fun sharedRepository(): GreenhouseTelemetryRepository {
        val existing = repository
        if (existing != null) return existing
        synchronized(this) {
            val again = repository
            if (again != null) return again
            val inner = DefaultGreenhouseTelemetryRepository(
                auth = FirebaseAnonymousAuthClient(),
                dataSource = FirebaseCurrentTelemetryDataSource(),
                weatherDataSource = FirebaseCurrentWeatherDataSource(),
                clock = SystemRuntimeClock,
                scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            )
            val created = ReferenceCountedGreenhouseTelemetryRepository(inner)
            repository = created
            return created
        }
    }
}
