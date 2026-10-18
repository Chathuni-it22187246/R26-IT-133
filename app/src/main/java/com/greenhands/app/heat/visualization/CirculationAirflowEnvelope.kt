package com.greenhands.app.heat.visualization

import com.greenhands.app.equipment.model.DomainPoint
import com.greenhands.app.heat.runtime.adapter.UserPhysicsCoordinateAdapter
import com.greenhands.app.heat.runtime.contracts.EquipmentProbeAxis
import com.greenhands.app.heat.runtime.contracts.PhysicsFieldSnapshot
import com.greenhands.app.heat.runtime.contracts.ProbeAvailability
import com.greenhands.app.heat.simulation.probe.DistanceProbeSampler

/**
 * Greenhouse-envelope tests for circulation rings. Outside centres are omitted,
 * never clamped back onto a wall.
 */
object CirculationAirflowEnvelope {
    const val SCAN_STEP_M = 0.10
    val MAX_PATH_M: Double = DistanceProbeSampler.DEFAULT_DISTANCES_M.last()

    fun contains(snapshot: PhysicsFieldSnapshot, domain: DomainPoint): Boolean {
        val physics = UserPhysicsCoordinateAdapter.positionToPhysics(domain)
        val grid = SnapshotFieldSampler.gridOf(snapshot)
        if (grid != null) return grid.geometry.contains(physics)
        return UserPhysicsCoordinateAdapter.inPhysicsBounds(
            physics,
            snapshot.grid.lengthM,
            snapshot.grid.widthM,
            snapshot.grid.heightM
        )
    }

    /**
     * Last inside distance along [forward] in `[0, MAX_PATH_M]`.
     * Returns `0` when only the outlet is inside, or `null` when the outlet itself is outside.
     */
    fun maxInsideDistanceM(
        snapshot: PhysicsFieldSnapshot,
        origin: DomainPoint,
        forwardDomainDx: Double,
        forwardDomainDy: Double,
        forwardDomainDz: Double
    ): Double? {
        if (!contains(snapshot, origin)) return null
        var lastInside = 0.0
        var distance = SCAN_STEP_M
        while (distance <= MAX_PATH_M + 1e-12) {
            val point = DomainPoint(
                xM = origin.xM + forwardDomainDx * distance,
                yM = origin.yM + forwardDomainDy * distance,
                zM = origin.zM + forwardDomainDz * distance
            )
            if (!contains(snapshot, point)) break
            lastInside = distance
            distance += SCAN_STEP_M
        }
        return lastInside
    }

    /**
     * Independent trilinear temperature at [domain]. Never returns the greenhouse mean.
     */
    fun predictedTemperatureC(snapshot: PhysicsFieldSnapshot, domain: DomainPoint): Double? {
        if (!contains(snapshot, domain)) return null
        val physics = UserPhysicsCoordinateAdapter.positionToPhysics(domain)
        val sampled = SnapshotFieldSampler.temperatureC(snapshot, physics)
        return sampled?.takeIf { it.isFinite() }
    }

    fun probeTemperatureC(
        snapshot: PhysicsFieldSnapshot,
        equipmentUuid: String,
        distanceM: Double
    ): Double? {
        val sample = snapshot.probes.firstOrNull { probe ->
            probe.equipmentUuid == equipmentUuid &&
                probe.axis == EquipmentProbeAxis.CIRCULATION_FORWARD &&
                probe.availability == ProbeAvailability.PREDICTED_UNCALIBRATED &&
                kotlin.math.abs(probe.distanceM - distanceM) < 1e-9
        } ?: return null
        val temperature = sample.temperatureC ?: return null
        return temperature.takeIf { it.isFinite() }
    }
}
