package com.greenhands.app.heat.visualization

import com.greenhands.app.equipment.model.DomainPoint
import com.greenhands.app.heat.runtime.adapter.UserPhysicsCoordinateAdapter
import com.greenhands.app.heat.runtime.contracts.PhysicsFieldSnapshot

/**
 * Greenhouse-envelope tests for fogger mist. Particles that would leave the
 * envelope are omitted or recycled; they are never invented outside the house.
 */
object FoggerMistEnvelope {
    const val SCAN_STEP_M = CirculationAirflowEnvelope.SCAN_STEP_M

    fun contains(snapshot: PhysicsFieldSnapshot, domain: DomainPoint): Boolean =
        CirculationAirflowEnvelope.contains(snapshot, domain)

    fun maxInsideDistanceM(
        snapshot: PhysicsFieldSnapshot,
        origin: DomainPoint,
        sprayDomainDx: Double,
        sprayDomainDy: Double,
        sprayDomainDz: Double
    ): Double? = CirculationAirflowEnvelope.maxInsideDistanceM(
        snapshot,
        origin,
        sprayDomainDx,
        sprayDomainDy,
        sprayDomainDz
    )

    fun predictedHumidityPercent(snapshot: PhysicsFieldSnapshot, domain: DomainPoint): Double? {
        if (!contains(snapshot, domain)) return null
        val physics = UserPhysicsCoordinateAdapter.positionToPhysics(domain)
        return SnapshotFieldSampler.relativeHumidityPercent(snapshot, physics)
    }

    fun predictedHumidityRatioKgKg(snapshot: PhysicsFieldSnapshot, domain: DomainPoint): Double? {
        if (!contains(snapshot, domain)) return null
        val physics = UserPhysicsCoordinateAdapter.positionToPhysics(domain)
        return SnapshotFieldSampler.humidityRatioKgKg(snapshot, physics)?.takeIf { it.isFinite() }
    }

    fun predictedTemperatureC(snapshot: PhysicsFieldSnapshot, domain: DomainPoint): Double? {
        if (!contains(snapshot, domain)) return null
        val physics = UserPhysicsCoordinateAdapter.positionToPhysics(domain)
        return SnapshotFieldSampler.temperatureC(snapshot, physics)?.takeIf { it.isFinite() }
    }
}
