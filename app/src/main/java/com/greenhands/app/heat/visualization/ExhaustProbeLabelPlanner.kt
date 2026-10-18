package com.greenhands.app.heat.visualization

import com.greenhands.app.equipment.model.DomainPoint
import com.greenhands.app.equipment.model.EquipmentInstance
import com.greenhands.app.equipment.model.EquipmentRuntimeState
import com.greenhands.app.equipment.model.EquipmentType
import com.greenhands.app.heat.runtime.adapter.UserPhysicsCoordinateAdapter
import com.greenhands.app.heat.runtime.contracts.PhysicsFieldSnapshot

/**
 * Compact 1–5 m exhaust markers along the accepted inward axis. Numeric values
 * are published only in quantitative mode.
 */
object ExhaustProbeLabelPlanner {
    fun plan(
        visualizationRunning: Boolean,
        labelsVisible: Boolean,
        selectedId: String?,
        instances: List<EquipmentInstance>,
        runtimeById: Map<String, EquipmentRuntimeState>,
        snapshot: PhysicsFieldSnapshot?,
        targetTemperatureC: Double? = null,
        cdpC: Double? = null,
        conC: Double? = null,
        eonC: Double? = null
    ): List<CirculationProbeMarker> {
        if (!visualizationRunning || !labelsVisible) return emptyList()
        val selected = selectedId ?: return emptyList()
        val inst = instances.firstOrNull {
            it.id == selected &&
                it.type == EquipmentType.EXHAUST_FAN &&
                !it.isGhost
        } ?: return emptyList()
        if (runtimeById[inst.id] != EquipmentRuntimeState.ON) return emptyList()
        if (snapshot == null || !snapshot.finiteValues) return emptyList()
        if (!ExhaustAirflowEnvelope.weatherPermitsQuantitative(snapshot)) return emptyList()
        val pad = snapshot.fanPad
        if (pad.present && pad.enabled && !pad.quantitativeAvailable) return emptyList()
        val row = snapshot.equipment.firstOrNull { it.equipmentUuid == inst.id }
        if (row != null && !row.quantitativeReady) return emptyList()
        val (inDx, inDy, inDz) = ExhaustAirflowEnvelope.inwardDomain(inst.yawDeg)
        return OfficialValidationTransect.DISTANCES_M.toList().mapNotNull { distance ->
            val domain = DomainPoint(
                xM = inst.positionM.xM + inDx * distance,
                yM = inst.positionM.yM + inDy * distance,
                zM = inst.positionM.zM + inDz * distance
            )
            if (!ExhaustAirflowEnvelope.contains(snapshot, domain)) return@mapNotNull null
            val temperature = ExhaustAirflowEnvelope.predictedTemperatureC(snapshot, domain)
                ?: ExhaustAirflowEnvelope.probeTemperatureC(snapshot, inst.id, distance)
                ?: return@mapNotNull null
            val humidityRatio = SnapshotFieldSampler.humidityRatioKgKg(
                snapshot,
                UserPhysicsCoordinateAdapter.positionToPhysics(domain)
            )
            val rh = if (humidityRatio != null && humidityRatio.isFinite()) {
                com.greenhands.app.heat.simulation.physics.Psychrometrics.displayedRelativeHumidityPercent(
                    temperature,
                    humidityRatio,
                    snapshot.airPressurePa
                )
            } else {
                snapshot.probes.firstOrNull { probe ->
                    probe.equipmentUuid == inst.id &&
                        kotlin.math.abs(probe.distanceM - distance) < 1e-9
                }?.relativeHumidityPercent
            }
            CirculationProbeMarker(
                id = markerId(inst.id, distance),
                equipmentUuid = inst.id,
                distanceM = distance,
                temperatureC = temperature,
                domain = domain,
                band = TemperatureLegendClassifier.band(
                    temperature,
                    targetTemperatureC,
                    cdpC,
                    conC,
                    eonC
                ),
                relativeHumidityPercent = rh,
                distanceFromPadM = distanceFromPadM(snapshot, distance)
            )
        }
    }

    fun markerId(equipmentUuid: String, distanceM: Double): String =
        "exh-probe:$equipmentUuid:${distanceM.toInt()}"

    fun distanceFromPadM(snapshot: PhysicsFieldSnapshot, distanceFromExhaustM: Double): Double? {
        val pad = snapshot.fanPad
        if (!pad.quantitativeAvailable || pad.wall == null) return null
        val span = pad.airflowSpanM ?: return null
        if (!span.isFinite() || span <= 0.0 || !distanceFromExhaustM.isFinite()) return null
        val fromPad = span - distanceFromExhaustM
        return fromPad.takeIf { it.isFinite() && it >= -1e-9 }
    }

    fun controlAvailable(
        visualizationRunning: Boolean,
        labelsVisible: Boolean,
        selectedId: String?,
        instances: List<EquipmentInstance>,
        runtimeById: Map<String, EquipmentRuntimeState>
    ): Boolean {
        if (!visualizationRunning || !labelsVisible) return false
        val selected = selectedId ?: return false
        val inst = instances.firstOrNull {
            it.id == selected &&
                it.type == EquipmentType.EXHAUST_FAN &&
                !it.isGhost
        } ?: return false
        return runtimeById[inst.id] == EquipmentRuntimeState.ON
    }

    fun cameraDoesNotAffectAxis(yawDeg: Double): Triple<Double, Double, Double> {
        val physics = UserPhysicsCoordinateAdapter.yawToPhysicsIndoorApproach(yawDeg)
        return UserPhysicsCoordinateAdapter.directionToDomain(physics)
    }
}
