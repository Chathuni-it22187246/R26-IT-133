package com.greenhands.app.heat.visualization

import com.greenhands.app.ar.domain.EquipmentRuntimeThresholds
import com.greenhands.app.equipment.model.EquipmentInstance
import com.greenhands.app.equipment.model.EquipmentRuntimeState
import com.greenhands.app.equipment.model.EquipmentType
import com.greenhands.app.heat.runtime.PhysicsRuntimeCopy
import com.greenhands.app.heat.runtime.contracts.EquipmentProbeAxis
import com.greenhands.app.heat.runtime.contracts.PhysicsFieldSnapshot
import com.greenhands.app.heat.runtime.contracts.ProbeAvailability
import com.greenhands.app.heat.runtime.contracts.WeatherBoundaryStatus
import com.greenhands.app.heat.simulation.probe.DistanceProbeSampler

/**
 * Builds a UUID-keyed exhaust-arrow frame from an accepted [PhysicsFieldSnapshot].
 * The planner visualizes solver results; it does not add a distance-decay equation.
 */
object ExhaustEffectPlanner {
    fun plan(
        visualizationRunning: Boolean,
        instances: List<EquipmentInstance>,
        runtimeById: Map<String, EquipmentRuntimeState>,
        snapshot: PhysicsFieldSnapshot?,
        thresholds: EquipmentRuntimeThresholds,
        targetTemperatureC: Double?,
        colorSnapshot: PhysicsFieldSnapshot? = snapshot
    ): ExhaustEffectFrame {
        if (!visualizationRunning) return ExhaustEffectFrame.EMPTY
        if (snapshot == null || !snapshot.finiteValues) return ExhaustEffectFrame.EMPTY
        val exhaustFans = instances.filter { inst ->
            inst.type == EquipmentType.EXHAUST_FAN && !inst.isGhost
        }.sortedWith(compareBy({ it.displayNumber }, { it.id }))
        if (exhaustFans.isEmpty()) return ExhaustEffectFrame.EMPTY

        val candidates = ArrayList<ExhaustArrowGroup>()
        var qualitativeLimitation: String? = null
        exhaustFans.forEach { inst ->
            val state = runtimeById[inst.id]
            if (state != EquipmentRuntimeState.ON) return@forEach
            val group = groupFor(inst, snapshot, thresholds, targetTemperatureC)
            if (group != null) {
                candidates += group
                if (group.mode == ExhaustAirflowMode.QUALITATIVE && qualitativeLimitation == null) {
                    qualitativeLimitation = limitationFor(inst, snapshot)
                }
            }
        }
        val unique = candidates.distinctBy { it.equipmentUuid }
        val groups = applyRenderCaps(unique)
        val live = snapshot.takeIf { groups.isNotEmpty() }
        return ExhaustEffectFrame(
            groups = groups,
            limitation = qualitativeLimitation.takeIf { groups.any { it.mode == ExhaustAirflowMode.QUALITATIVE } },
            snapshot = live,
            colorSnapshot = (colorSnapshot ?: snapshot).takeIf { groups.isNotEmpty() }
        )
    }

    internal fun applyRenderCaps(groups: List<ExhaustArrowGroup>): List<ExhaustArrowGroup> {
        val limitedGroups = groups.take(ExhaustAirflowVisual.MAX_EXHAUST_GROUPS)
        var remaining = ExhaustAirflowVisual.MAX_TOTAL_ARROWS
        val out = ArrayList<ExhaustArrowGroup>(limitedGroups.size)
        limitedGroups.forEach { group ->
            if (remaining <= 0) return@forEach
            var indoor = minOf(group.indoorArrowCount, ExhaustAirflowVisual.MAX_INDOOR_ARROWS_PER_FAN)
            var outdoor = minOf(group.outdoorArrowCount, ExhaustAirflowVisual.MAX_OUTDOOR_ARROWS_PER_FAN)
            val total = indoor + outdoor
            if (total <= 0) return@forEach
            if (total > remaining) {
                val keepOutdoor = minOf(outdoor, remaining)
                val keepIndoor = minOf(indoor, remaining - keepOutdoor)
                indoor = keepIndoor
                outdoor = keepOutdoor
            }
            remaining -= indoor + outdoor
            if (indoor + outdoor <= 0) return@forEach
            out += if (indoor == group.indoorArrowCount && outdoor == group.outdoorArrowCount) {
                group
            } else {
                group.copy(indoorArrowCount = indoor, outdoorArrowCount = outdoor)
            }
        }
        return out
    }

    private fun groupFor(
        inst: EquipmentInstance,
        snapshot: PhysicsFieldSnapshot,
        thresholds: EquipmentRuntimeThresholds,
        targetTemperatureC: Double?
    ): ExhaustArrowGroup? {
        val (outDx, outDy, outDz) = ExhaustAirflowEnvelope.outwardDomain(inst.yawDeg)
        val (inDx, inDy, inDz) = ExhaustAirflowEnvelope.inwardDomain(inst.yawDeg)
        if (!ExhaustAirflowEnvelope.contains(snapshot, inst.positionM)) return null
        val maxInside = ExhaustAirflowEnvelope.maxInsideDistanceM(
            snapshot,
            inst.positionM,
            inDx,
            inDy,
            inDz
        ) ?: return null
        val indoorCount = if (maxInside >= ExhaustAirflowVisual.MIN_INDOOR_PATH_M) {
            ExhaustAirflowVisual.MAX_INDOOR_ARROWS_PER_FAN
        } else {
            0
        }
        val outdoorCount = ExhaustAirflowVisual.MAX_OUTDOOR_ARROWS_PER_FAN
        if (indoorCount + outdoorCount <= 0) return null
        val quantitative = quantitativeReady(inst, snapshot)
        val circulation = thresholds.circulation
        val exhaust = thresholds.exhaust
        val samples = if (quantitative) {
            samplesFor(inst, snapshot, targetTemperatureC, circulation?.cdp, circulation?.con, exhaust?.eon)
        } else {
            emptyList()
        }
        val distances = samples.map { it.distanceM }
        return ExhaustArrowGroup(
            equipmentUuid = inst.id,
            originM = inst.positionM,
            yawDeg = inst.yawDeg,
            inwardDomainDx = inDx,
            inwardDomainDy = inDy,
            inwardDomainDz = inDz,
            outwardDomainDx = outDx,
            outwardDomainDy = outDy,
            outwardDomainDz = outDz,
            validProbeDistancesM = distances,
            samples = samples,
            indoorArrowCount = indoorCount,
            outdoorArrowCount = outdoorCount,
            indoorMinDistanceM = ExhaustAirflowVisual.INDOOR_FAN_REACH_M,
            indoorMaxDistanceM = maxInside,
            outdoorMaxDistanceM = ExhaustAirflowVisual.OUTWARD_LENGTH_M,
            mode = if (quantitative) ExhaustAirflowMode.QUANTITATIVE else ExhaustAirflowMode.QUALITATIVE,
            targetTemperatureC = targetTemperatureC,
            cdpC = circulation?.cdp,
            conC = circulation?.con,
            eonC = exhaust?.eon,
            weatherSourceLabel = snapshot.weatherSourceLabel,
            animationPhase = ExhaustAirflowAnimator.phaseOffset(inst.id)
        )
    }

    private fun quantitativeReady(inst: EquipmentInstance, snapshot: PhysicsFieldSnapshot): Boolean {
        if (!ExhaustAirflowEnvelope.weatherPermitsQuantitative(snapshot)) return false
        val row = snapshot.equipment.firstOrNull { it.equipmentUuid == inst.id }
        if (row != null && !row.quantitativeReady) return false
        return true
    }

    private fun limitationFor(inst: EquipmentInstance, snapshot: PhysicsFieldSnapshot): String {
        val rowReason = snapshot.equipment.firstOrNull { it.equipmentUuid == inst.id }?.unavailableReason
        if (!rowReason.isNullOrBlank()) return rowReason
        val module = snapshot.unavailableModuleReasons[inst.id]
        if (!module.isNullOrBlank()) return module
        return when (snapshot.weatherStatus) {
            WeatherBoundaryStatus.AVAILABLE -> PhysicsRuntimeCopy.QUANTITATIVE_UNAVAILABLE_RATING
            else -> PhysicsRuntimeCopy.MODULE_UNAVAILABLE_WEATHER
        }
    }

    private fun samplesFor(
        inst: EquipmentInstance,
        snapshot: PhysicsFieldSnapshot,
        targetTemperatureC: Double?,
        cdpC: Double?,
        conC: Double?,
        eonC: Double?
    ): List<CirculationProbeColorSample> {
        val (inDx, inDy, inDz) = ExhaustAirflowEnvelope.inwardDomain(inst.yawDeg)
        return DistanceProbeSampler.DEFAULT_DISTANCES_M.asSequence()
            .mapNotNull { distance ->
                val domain = inst.positionM.copy(
                    xM = inst.positionM.xM + inDx * distance,
                    yM = inst.positionM.yM + inDy * distance,
                    zM = inst.positionM.zM + inDz * distance
                )
                if (!ExhaustAirflowEnvelope.contains(snapshot, domain)) return@mapNotNull null
                val temperature = ExhaustAirflowEnvelope.predictedTemperatureC(snapshot, domain)
                    ?: ExhaustAirflowEnvelope.probeTemperatureC(snapshot, inst.id, distance)
                    ?: publishedProbe(snapshot, inst.id, distance)
                    ?: return@mapNotNull null
                if (!temperature.isFinite()) return@mapNotNull null
                val band = TemperatureLegendClassifier.band(
                    temperature,
                    targetTemperatureC,
                    cdpC,
                    conC,
                    eonC
                )
                CirculationProbeColorSample(
                    distanceM = distance,
                    temperatureC = temperature,
                    color = TemperatureLegendClassifier.blendedColor(
                        temperature,
                        targetTemperatureC,
                        cdpC,
                        conC,
                        eonC
                    ),
                    band = band
                )
            }
            .sortedBy { it.distanceM }
            .toList()
    }

    private fun publishedProbe(
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
        return sample.temperatureC?.takeIf { it.isFinite() }
    }
}
