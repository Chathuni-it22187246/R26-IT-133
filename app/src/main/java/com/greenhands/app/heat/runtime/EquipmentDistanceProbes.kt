package com.greenhands.app.heat.runtime

import com.greenhands.app.equipment.model.EquipmentInstance
import com.greenhands.app.equipment.model.EquipmentType
import com.greenhands.app.heat.runtime.adapter.UserPhysicsCoordinateAdapter
import com.greenhands.app.heat.runtime.contracts.EquipmentProbeAxis
import com.greenhands.app.heat.runtime.contracts.EquipmentProbeSample
import com.greenhands.app.heat.runtime.contracts.FieldValidationStatus
import com.greenhands.app.heat.runtime.contracts.ProbeAvailability
import com.greenhands.app.heat.simulation.model.GridState
import com.greenhands.app.heat.simulation.probe.DistanceProbeSampler
import com.greenhands.app.heat.simulation.probe.ProbeStatus

/**
 * 1–5 m samples of the combined field along each equipment's documented axis.
 * Interpolation is [DistanceProbeSampler]; this layer only defines origin, direction and UUID.
 * Invalid 5 m points are not clamped into the greenhouse.
 */
object EquipmentDistanceProbes {
    val DISTANCES_M: DoubleArray = DistanceProbeSampler.DEFAULT_DISTANCES_M

    fun sampleAll(
        grid: GridState,
        instances: List<EquipmentInstance>,
        pressurePa: Double,
        exhaustQuantitativeAvailable: Boolean
    ): List<EquipmentProbeSample> {
        val out = ArrayList<EquipmentProbeSample>()
        instances.filter { !it.isGhost }.forEach { inst ->
            val origin = UserPhysicsCoordinateAdapter.positionToPhysics(inst.positionM)
            val (axis, direction) = when (inst.type) {
                EquipmentType.CIRCULATION_FAN ->
                    EquipmentProbeAxis.CIRCULATION_FORWARD to
                        UserPhysicsCoordinateAdapter.yawToPhysicsForward(inst.yawDeg)
                EquipmentType.EXHAUST_FAN ->
                    EquipmentProbeAxis.EXHAUST_INWARD to
                        UserPhysicsCoordinateAdapter.yawToPhysicsIndoorApproach(inst.yawDeg)
                EquipmentType.FOGGER ->
                    EquipmentProbeAxis.FOGGER_SPRAY to
                        UserPhysicsCoordinateAdapter.foggerSprayPhysics()
            }
            DISTANCES_M.forEach { distanceM ->
                val point = origin.plus(direction.times(distanceM))
                val sample = DistanceProbeSampler.samplePoint(grid, point, distanceM, pressurePa)
                val outside = sample.status == ProbeStatus.OUTSIDE_GREENHOUSE
                val exhaustBlocked = inst.type == EquipmentType.EXHAUST_FAN && !exhaustQuantitativeAvailable
                val availability = when {
                    outside -> ProbeAvailability.OUTSIDE_GREENHOUSE
                    exhaustBlocked -> ProbeAvailability.MODULE_UNAVAILABLE
                    else -> ProbeAvailability.PREDICTED_UNCALIBRATED
                }
                val hideValues = outside || exhaustBlocked
                out += EquipmentProbeSample(
                    equipmentUuid = inst.id,
                    equipmentType = inst.type,
                    axis = axis,
                    distanceM = distanceM,
                    temperatureC = if (hideValues) null else sample.temperatureC,
                    relativeHumidityPercent = if (hideValues) null else sample.relativeHumidityPercent,
                    airSpeedMps = if (hideValues) null else sample.airSpeedMps,
                    availability = availability,
                    validationStatus = if (hideValues) {
                        FieldValidationStatus.MODULE_UNAVAILABLE
                    } else {
                        FieldValidationStatus.UNCALIBRATED
                    },
                    reason = when (availability) {
                        ProbeAvailability.OUTSIDE_GREENHOUSE -> PhysicsRuntimeCopy.OUTSIDE_GREENHOUSE
                        ProbeAvailability.MODULE_UNAVAILABLE -> PhysicsRuntimeCopy.MODULE_UNAVAILABLE_WEATHER
                        ProbeAvailability.PREDICTED_UNCALIBRATED -> null
                    }
                )
            }
        }
        return out
    }
}
