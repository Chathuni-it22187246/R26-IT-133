package com.greenhands.app.heat.visualization

import com.greenhands.app.ar.domain.EquipmentRuntimeThresholds
import com.greenhands.app.equipment.model.EquipmentInstance
import com.greenhands.app.equipment.model.EquipmentRuntimeState
import com.greenhands.app.equipment.model.EquipmentType
import com.greenhands.app.heat.runtime.adapter.UserPhysicsCoordinateAdapter
import com.greenhands.app.heat.runtime.contracts.EquipmentProbeAxis
import com.greenhands.app.heat.runtime.contracts.PhysicsFieldSnapshot
import com.greenhands.app.heat.runtime.contracts.ProbeAvailability
import com.greenhands.app.heat.simulation.probe.DistanceProbeSampler

/**
 * Builds a UUID-keyed circulation-ring frame from an accepted [PhysicsFieldSnapshot].
 * Rings are never invented from a GLB being present.
 */
object CirculationEffectPlanner {
    fun plan(
        visualizationRunning: Boolean,
        instances: List<EquipmentInstance>,
        runtimeById: Map<String, EquipmentRuntimeState>,
        snapshot: PhysicsFieldSnapshot?,
        thresholds: EquipmentRuntimeThresholds,
        targetTemperatureC: Double?,
        colorSnapshot: PhysicsFieldSnapshot? = snapshot
    ): CirculationEffectFrame {
        if (!visualizationRunning) return CirculationEffectFrame.EMPTY
        val circulationFans = instances.filter { inst ->
            inst.type == EquipmentType.CIRCULATION_FAN && !inst.isGhost
        }.sortedWith(compareBy({ it.displayNumber }, { it.id }))
        if (circulationFans.isEmpty()) return CirculationEffectFrame.EMPTY

        val candidates = ArrayList<CirculationRingGroup>()
        var onWithoutRings = false
        circulationFans.forEach { inst ->
            val state = runtimeById[inst.id]
            if (state != EquipmentRuntimeState.ON) return@forEach
            val group = groupFor(inst, snapshot, thresholds, targetTemperatureC)
            if (group == null) {
                onWithoutRings = true
            } else {
                candidates += group
            }
        }
        val unique = candidates.distinctBy { it.equipmentUuid }
        val groups = applyRenderCaps(unique)
        val limitation = VisualizationCopy.CIRCULATION_AIRFLOW_UNAVAILABLE.takeIf { onWithoutRings }
        val live = snapshot.takeIf { groups.isNotEmpty() }
        return CirculationEffectFrame(
            groups = groups,
            limitation = limitation,
            snapshot = live,
            colorSnapshot = (colorSnapshot ?: snapshot).takeIf { groups.isNotEmpty() }
        )
    }

    internal fun applyRenderCaps(groups: List<CirculationRingGroup>): List<CirculationRingGroup> {
        val limitedGroups = groups.take(CirculationRingVisual.MAX_RING_GROUPS)
        var remaining = CirculationRingVisual.MAX_TOTAL_RINGS
        val out = ArrayList<CirculationRingGroup>(limitedGroups.size)
        limitedGroups.forEach { group ->
            if (remaining <= 0) return@forEach
            val count = minOf(group.ringCount, remaining, CirculationRingVisual.MAX_RINGS_PER_FAN)
            if (count <= 0) return@forEach
            remaining -= count
            out += if (count == group.ringCount) group else group.copy(ringCount = count)
        }
        return out
    }

    private fun groupFor(
        inst: EquipmentInstance,
        snapshot: PhysicsFieldSnapshot?,
        thresholds: EquipmentRuntimeThresholds,
        targetTemperatureC: Double?
    ): CirculationRingGroup? {
        if (snapshot == null || !snapshot.finiteValues) return null
        val row = snapshot.equipment.firstOrNull { it.equipmentUuid == inst.id }
        if (row != null && !row.quantitativeReady) return null
        val circulation = thresholds.circulation ?: return null
        val exhaust = thresholds.exhaust ?: return null
        val (dx, dy, dz) = UserPhysicsCoordinateAdapter.yawToDomainForward(inst.yawDeg)
        val maxInside = CirculationAirflowEnvelope.maxInsideDistanceM(
            snapshot,
            inst.positionM,
            dx,
            dy,
            dz
        ) ?: return null
        val throwM = row?.specification?.effectRadiusM?.takeIf { it.isFinite() && it > 0.0 }
        val maxDistance = if (throwM != null) minOf(maxInside, throwM) else maxInside
        val samples = snapshot.probes.asSequence()
            .filter { sample ->
                sample.equipmentUuid == inst.id &&
                    sample.equipmentType == EquipmentType.CIRCULATION_FAN &&
                    sample.axis == EquipmentProbeAxis.CIRCULATION_FORWARD &&
                    sample.availability == ProbeAvailability.PREDICTED_UNCALIBRATED &&
                    sample.distanceM.isFinite() &&
                    DistanceProbeSampler.DEFAULT_DISTANCES_M.any { distance ->
                        kotlin.math.abs(distance - sample.distanceM) < 1e-9
                    } &&
                    sample.temperatureC != null &&
                    sample.temperatureC.isFinite()
            }
            .mapNotNull { sample ->
                val temperature = sample.temperatureC ?: return@mapNotNull null
                val band = TemperatureLegendClassifier.band(
                    temperature,
                    targetTemperatureC,
                    circulation.cdp,
                    circulation.con,
                    exhaust.eon
                )
                if (band == TemperatureLegendBand.UNAVAILABLE) {
                    null
                } else {
                    CirculationProbeColorSample(
                        distanceM = sample.distanceM,
                        temperatureC = temperature,
                        color = TemperatureLegendClassifier.blendedColor(
                            temperature,
                            targetTemperatureC,
                            circulation.cdp,
                            circulation.con,
                            exhaust.eon
                        ),
                        band = band
                    )
                }
            }
            .sortedBy { it.distanceM }
            .toList()
        val distances = samples.map { it.distanceM }
        return CirculationRingGroup(
            equipmentUuid = inst.id,
            originM = inst.positionM,
            yawDeg = inst.yawDeg,
            forwardDomainDx = dx,
            forwardDomainDy = dy,
            forwardDomainDz = dz,
            validProbeDistancesM = distances,
            samples = samples,
            ringCount = CirculationRingVisual.MAX_RINGS_PER_FAN,
            minDistanceM = 0.0,
            maxDistanceM = maxDistance,
            targetTemperatureC = targetTemperatureC,
            cdpC = circulation.cdp,
            conC = circulation.con,
            eonC = exhaust.eon,
            animationPhase = CirculationRingAnimator.phaseOffset(inst.id)
        )
    }
}
