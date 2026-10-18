package com.greenhands.app.heat.simulation.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

enum class SimulationDataQuality {
    PREVIEW,
    LIVE,
    STALE,
    INVALID
}

data class SimulationEnvironmentSnapshot(
    val temperatureC: Double?,
    val relativeHumidityPercent: Double?,
    val serverTimestampMillis: Long?,
    val sensorId: String?,
    val greenhouseId: String?,
    val quality: SimulationDataQuality
)

/**
 * Firebase-independent environment stream (PHYSICS_MODEL.md §15).
 * Named [SimulationEnvironmentProvider] to avoid colliding with the existing
 * Phase 2 [com.greenhands.app.environment.GreenhouseEnvironmentRepository].
 */
interface SimulationEnvironmentProvider {
    val snapshots: Flow<SimulationEnvironmentSnapshot>
}

class PreviewSimulationEnvironmentProvider(
    snapshot: SimulationEnvironmentSnapshot = DEFAULT_PREVIEW
) : SimulationEnvironmentProvider {
    override val snapshots: Flow<SimulationEnvironmentSnapshot> = flowOf(snapshot)

    companion object {
        val DEFAULT_PREVIEW = SimulationEnvironmentSnapshot(
            temperatureC = 25.0,
            relativeHumidityPercent = 70.0,
            serverTimestampMillis = null,
            sensorId = null,
            greenhouseId = null,
            quality = SimulationDataQuality.PREVIEW
        )
    }
}
