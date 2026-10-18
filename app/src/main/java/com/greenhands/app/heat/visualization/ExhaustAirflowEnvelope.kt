package com.greenhands.app.heat.visualization

import com.greenhands.app.equipment.model.DomainPoint
import com.greenhands.app.heat.runtime.adapter.UserPhysicsCoordinateAdapter
import com.greenhands.app.heat.runtime.contracts.EquipmentProbeAxis
import com.greenhands.app.heat.runtime.contracts.PhysicsFieldSnapshot
import com.greenhands.app.heat.runtime.contracts.ProbeAvailability
import com.greenhands.app.heat.runtime.contracts.WeatherBoundaryStatus
import com.greenhands.app.heat.simulation.probe.DistanceProbeSampler

/**
 * Indoor/outdoor envelope helpers for exhaust arrows. Outside points are omitted,
 * never clamped back onto a wall. Directions come from stored yaw, not the camera.
 */
object ExhaustAirflowEnvelope {
    val MAX_INWARD_PATH_M: Double = DistanceProbeSampler.DEFAULT_DISTANCES_M.last()

    fun contains(snapshot: PhysicsFieldSnapshot, domain: DomainPoint): Boolean =
        CirculationAirflowEnvelope.contains(snapshot, domain)

    fun outwardDomain(yawDeg: Double): Triple<Double, Double, Double> =
        UserPhysicsCoordinateAdapter.yawToDomainForward(yawDeg)

    fun inwardDomain(yawDeg: Double): Triple<Double, Double, Double> {
        val (dx, dy, dz) = outwardDomain(yawDeg)
        return Triple(-dx, -dy, -dz)
    }

    fun maxInsideDistanceM(
        snapshot: PhysicsFieldSnapshot,
        origin: DomainPoint,
        inwardDomainDx: Double,
        inwardDomainDy: Double,
        inwardDomainDz: Double
    ): Double? = CirculationAirflowEnvelope.maxInsideDistanceM(
        snapshot,
        origin,
        inwardDomainDx,
        inwardDomainDy,
        inwardDomainDz
    )?.coerceAtMost(MAX_INWARD_PATH_M)

    /**
     * Independent trilinear temperature at [domain]. Never returns the greenhouse mean
     * and never invents a cooling value.
     */
    fun predictedTemperatureC(snapshot: PhysicsFieldSnapshot, domain: DomainPoint): Double? =
        CirculationAirflowEnvelope.predictedTemperatureC(snapshot, domain)

    fun probeTemperatureC(
        snapshot: PhysicsFieldSnapshot,
        equipmentUuid: String,
        distanceM: Double
    ): Double? {
        val sample = snapshot.probes.firstOrNull { probe ->
            probe.equipmentUuid == equipmentUuid &&
                probe.axis == EquipmentProbeAxis.EXHAUST_INWARD &&
                probe.availability == ProbeAvailability.PREDICTED_UNCALIBRATED &&
                kotlin.math.abs(probe.distanceM - distanceM) < 1e-9
        } ?: return null
        val temperature = sample.temperatureC ?: return null
        return temperature.takeIf { it.isFinite() }
    }

    fun weatherPermitsQuantitative(snapshot: PhysicsFieldSnapshot): Boolean =
        snapshot.finiteValues &&
            snapshot.weatherStatus == WeatherBoundaryStatus.AVAILABLE

    fun cameraDoesNotAffectDomainDirection(): Boolean = true
}
