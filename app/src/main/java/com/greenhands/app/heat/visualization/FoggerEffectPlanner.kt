package com.greenhands.app.heat.visualization

import com.greenhands.app.ar.domain.EquipmentPresentation
import com.greenhands.app.ar.domain.EquipmentPresentationContext
import com.greenhands.app.ar.domain.EquipmentRuntimeThresholds
import com.greenhands.app.equipment.model.DomainPoint
import com.greenhands.app.equipment.model.EquipmentInstance
import com.greenhands.app.equipment.model.EquipmentRuntimeState
import com.greenhands.app.equipment.model.EquipmentType
import com.greenhands.app.heat.runtime.PhysicsRuntimeCopy
import com.greenhands.app.heat.runtime.adapter.UserPhysicsCoordinateAdapter
import com.greenhands.app.heat.runtime.contracts.PhysicsFieldSnapshot

/**
 * Builds a UUID-keyed fogger mist frame from an accepted [PhysicsFieldSnapshot].
 * Mist is never invented from a GLB being present. Render caps do not change the solver.
 */
object FoggerEffectPlanner {
    fun plan(
        visualizationRunning: Boolean,
        instances: List<EquipmentInstance>,
        runtimeById: Map<String, EquipmentRuntimeState>,
        snapshot: PhysicsFieldSnapshot?,
        thresholds: EquipmentRuntimeThresholds,
        colorSnapshot: PhysicsFieldSnapshot? = snapshot
    ): FoggerEffectFrame {
        if (!visualizationRunning) return FoggerEffectFrame.EMPTY
        if (snapshot == null || !snapshot.finiteValues) return FoggerEffectFrame.EMPTY
        val foggers = instances.filter { inst ->
            inst.type == EquipmentType.FOGGER && !inst.isGhost
        }.sortedWith(compareBy({ it.displayNumber }, { it.id }))
        if (foggers.isEmpty()) return FoggerEffectFrame.EMPTY

        val candidates = ArrayList<FoggerMistGroup>()
        var onWithoutMist = false
        foggers.forEach { inst ->
            val state = runtimeById[inst.id]
            if (state != EquipmentRuntimeState.ON) return@forEach
            val group = groupFor(inst, snapshot, thresholds)
            if (group == null) {
                onWithoutMist = true
            } else {
                candidates += group
            }
        }
        val unique = candidates.distinctBy { it.equipmentUuid }
        val groups = applyRenderCaps(unique)
        val limitation = VisualizationCopy.FOGGER_VISUALIZATION_UNAVAILABLE.takeIf { onWithoutMist }
        val live = snapshot.takeIf { groups.isNotEmpty() }
        return FoggerEffectFrame(
            groups = groups,
            limitation = limitation,
            snapshot = live,
            colorSnapshot = (colorSnapshot ?: snapshot).takeIf { groups.isNotEmpty() }
        )
    }

    internal fun applyRenderCaps(groups: List<FoggerMistGroup>): List<FoggerMistGroup> {
        val limitedGroups = groups.take(FogMistVisual.MAX_FOGGER_GROUPS)
        var remainingParticles = FogMistVisual.MAX_TOTAL_PARTICLES
        val out = ArrayList<FoggerMistGroup>(limitedGroups.size)
        limitedGroups.forEach { group ->
            if (remainingParticles <= 0) return@forEach
            val particles = minOf(
                group.particleCount,
                remainingParticles,
                FogMistVisual.MAX_PARTICLES_PER_FOGGER
            )
            if (particles <= 0) return@forEach
            remainingParticles -= particles
            val specks = group.particles.take(particles)
            out += if (particles == group.particleCount) {
                group.copy(haloCount = 0)
            } else {
                group.copy(
                    particleCount = particles,
                    haloCount = 0,
                    particles = specks
                )
            }
        }
        return out
    }

    fun details(
        visualizationRunning: Boolean,
        selected: EquipmentInstance?,
        runtimeById: Map<String, EquipmentRuntimeState>,
        snapshot: PhysicsFieldSnapshot?,
        thresholds: EquipmentRuntimeThresholds,
        presentation: EquipmentPresentationContext
    ): FoggerDetailsModel? {
        val inst = selected ?: return null
        if (inst.type != EquipmentType.FOGGER || inst.isGhost) return null
        val state = runtimeById[inst.id] ?: EquipmentRuntimeState.NOT_RUNNING
        val row = snapshot?.equipment?.firstOrNull { it.equipmentUuid == inst.id }
        val ready = row?.quantitativeReady == true
        val localRh = if (visualizationRunning && snapshot != null && snapshot.finiteValues) {
            FoggerMistEnvelope.predictedHumidityPercent(snapshot, inst.positionM)
        } else {
            null
        }
        val fog = thresholds.fogger
        val band = HumidityLegendClassifier.band(localRh, fog?.fon, fog?.fsp, fog?.fdp)
        val limitation = when {
            !visualizationRunning -> null
            snapshot == null || !snapshot.finiteValues -> VisualizationCopy.FOGGER_VISUALIZATION_UNAVAILABLE
            !ready -> row?.unavailableReason ?: PhysicsRuntimeCopy.QUANTITATIVE_UNAVAILABLE_RATING
            else -> null
        }
        return FoggerDetailsModel(
            equipmentUuid = inst.id,
            displayLabel = inst.displayLabel(),
            runtimeState = state,
            runtimeLabel = EquipmentPresentation.label(inst.id, state, presentation),
            quantitativeReady = ready,
            readinessLabel = if (ready) {
                VisualizationCopy.QUANTITATIVE_READY
            } else {
                VisualizationCopy.QUANTITATIVE_UNAVAILABLE
            },
            localRhPercent = localRh,
            humidityBand = band,
            classificationLabel = VisualizationCopy.compactHumidityLabel(band),
            limitation = limitation
        )
    }

    private fun groupFor(
        inst: EquipmentInstance,
        snapshot: PhysicsFieldSnapshot,
        thresholds: EquipmentRuntimeThresholds
    ): FoggerMistGroup? {
        val row = snapshot.equipment.firstOrNull { it.equipmentUuid == inst.id } ?: return null
        if (!row.quantitativeReady) return null
        val (dx, dy, dz) = UserPhysicsCoordinateAdapter.foggerSprayDomain()
        val nozzles = FoggerNozzleLayout.domainOutlets(inst.positionM, inst.yawDeg)
        val nozzle = nozzles.firstOrNull() ?: return null
        if (!FoggerMistEnvelope.contains(snapshot, nozzle)) return null
        val maxInside = FoggerMistEnvelope.maxInsideDistanceM(snapshot, nozzle, dx, dy, dz)
            ?: return null
        val acceptedLength = row.specification.effectRadiusM?.takeIf { it.isFinite() && it > 0.0 }
            ?: FoggerVisualEnvelope.acceptedPlumeLengthM()
        val halfAngle = FoggerVisualEnvelope.acceptedHalfAngleDeg()
        val path = FoggerVisualEnvelope.clippedPathM(
            acceptedLength,
            maxInside,
            nozzle.zM
        )
        if (path < FogMistVisual.MIN_DISTANCE_M) return null
        val fog = thresholds.fogger
        val samples = humiditySamples(nozzle, snapshot, path, dx, dy, dz, fog?.fon, fog?.fsp, fog?.fdp)
        val particleCount = FogMistVisual.MAX_PARTICLES_PER_FOGGER
        val canopy = FoggerVisualEnvelope.canopyHeightM(snapshot)
        return FoggerMistGroup(
            equipmentUuid = inst.id,
            originM = inst.positionM,
            yawDeg = inst.yawDeg,
            sprayDomainDx = dx,
            sprayDomainDy = dy,
            sprayDomainDz = dz,
            particleCount = particleCount,
            haloCount = 0,
            minDistanceM = FogMistVisual.MIN_DISTANCE_M,
            maxDistanceM = path,
            humiditySamples = samples,
            fonPercent = fog?.fon,
            fspPercent = fog?.fsp,
            fdpPercent = fog?.fdp,
            quantitativeReady = true,
            animationPhase = FoggerMistAnimator.phaseOffset(inst.id),
            nozzleOrigins = nozzles,
            particles = FogMistDistribution.particles(inst.id, particleCount, nozzles.size),
            plumeHalfAngleDeg = halfAngle,
            acceptedPlumeLengthM = acceptedLength,
            houseLengthM = snapshot.grid.lengthM,
            houseWidthM = snapshot.grid.widthM,
            houseHeightM = snapshot.grid.heightM,
            canopyHeightM = canopy
        )
    }

    private fun humiditySamples(
        nozzle: DomainPoint,
        snapshot: PhysicsFieldSnapshot,
        pathM: Double,
        dx: Double,
        dy: Double,
        dz: Double,
        fon: Double?,
        fsp: Double?,
        fdp: Double?
    ): List<FoggerHumiditySample> {
        val fractions = if (pathM >= FogMistVisual.MIN_PLUME_PATH_M) {
            listOf(0.25, 0.55, 0.80)
        } else {
            listOf(0.0)
        }
        val out = ArrayList<FoggerHumiditySample>(3)
        fractions.forEach { fraction ->
            if (out.size >= 3) return@forEach
            val distance = pathM * fraction
            val domain = DomainPoint(
                xM = nozzle.xM + dx * distance,
                yM = nozzle.yM + dy * distance,
                zM = nozzle.zM + dz * distance
            )
            if (!FoggerMistEnvelope.contains(snapshot, domain)) return@forEach
            val rh = FoggerMistEnvelope.predictedHumidityPercent(snapshot, domain) ?: return@forEach
            val band = HumidityLegendClassifier.band(rh, fon, fsp, fdp)
            if (band == HumidityLegendBand.UNAVAILABLE) return@forEach
            out += FoggerHumiditySample(
                distanceM = distance,
                relativeHumidityPercent = rh,
                humidityRatioKgKg = FoggerMistEnvelope.predictedHumidityRatioKgKg(snapshot, domain),
                temperatureC = FoggerMistEnvelope.predictedTemperatureC(snapshot, domain),
                color = HumidityLegendClassifier.colorOf(band),
                band = band
            )
        }
        return out
    }
}
