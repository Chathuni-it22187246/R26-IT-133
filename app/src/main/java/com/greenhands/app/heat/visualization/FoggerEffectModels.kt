package com.greenhands.app.heat.visualization

import com.greenhands.app.equipment.model.DomainPoint
import com.greenhands.app.equipment.model.EquipmentRuntimeState
import com.greenhands.app.heat.runtime.contracts.PhysicsFieldSnapshot

data class FoggerHumiditySample(
    val distanceM: Double,
    val relativeHumidityPercent: Double,
    val humidityRatioKgKg: Double?,
    val temperatureC: Double?,
    val color: VisualizationRgb,
    val band: HumidityLegendBand
)

data class FoggerMistParticle(
    val nozzleIndex: Int,
    val azimuthRad: Double,
    val radialFraction: Double,
    val faceFraction: Double,
    val phase: Double,
    val distanceFraction: Double
) {
    init {
        require(nozzleIndex >= 0)
        require(azimuthRad.isFinite())
        require(radialFraction.isFinite() && radialFraction in 0.0..1.0)
        require(faceFraction.isFinite() && faceFraction in 0.0..1.0)
        require(phase.isFinite())
        require(distanceFraction.isFinite() && distanceFraction in 0.0..1.0)
    }
}

data class FoggerMistGroup(
    val equipmentUuid: String,
    val originM: DomainPoint,
    val yawDeg: Double,
    val sprayDomainDx: Double,
    val sprayDomainDy: Double,
    val sprayDomainDz: Double,
    val particleCount: Int,
    val haloCount: Int,
    val minDistanceM: Double,
    val maxDistanceM: Double,
    val humiditySamples: List<FoggerHumiditySample>,
    val fonPercent: Double?,
    val fspPercent: Double?,
    val fdpPercent: Double?,
    val quantitativeReady: Boolean,
    val animationPhase: Double = 0.0,
    val nozzleOrigins: List<DomainPoint> = emptyList(),
    val particles: List<FoggerMistParticle> = emptyList(),
    val plumeHalfAngleDeg: Double = FoggerVisualEnvelope.acceptedHalfAngleDeg(),
    val acceptedPlumeLengthM: Double = FoggerVisualEnvelope.acceptedPlumeLengthM(),
    val houseLengthM: Double = 10.0,
    val houseWidthM: Double = 5.0,
    val houseHeightM: Double = 3.0,
    val canopyHeightM: Double? = null
) {
    init {
        require(equipmentUuid.isNotBlank())
        require(particleCount in 1..FogMistVisual.MAX_PARTICLES_PER_FOGGER)
        require(haloCount in 0..FogMistVisual.MAX_HALOS_PER_FOGGER)
        require(minDistanceM.isFinite() && maxDistanceM.isFinite())
        require(maxDistanceM + 1e-9 >= minDistanceM)
        require(animationPhase.isFinite())
        require(sprayDomainDx.isFinite() && sprayDomainDy.isFinite() && sprayDomainDz.isFinite())
        require(nozzleOrigins.isNotEmpty())
        require(particles.size == particleCount)
        require(particles.all { it.nozzleIndex in nozzleOrigins.indices })
        require(plumeHalfAngleDeg.isFinite() && plumeHalfAngleDeg > 0.0 && plumeHalfAngleDeg < 90.0)
        require(acceptedPlumeLengthM.isFinite() && acceptedPlumeLengthM > 0.0)
        require(houseLengthM.isFinite() && houseLengthM > 0.0)
        require(houseWidthM.isFinite() && houseWidthM > 0.0)
        require(houseHeightM.isFinite() && houseHeightM > 0.0)
    }

    fun nozzleOrigin(index: Int = 0): DomainPoint =
        nozzleOrigins.getOrElse(index.coerceAtLeast(0)) { nozzleOrigins.first() }

    fun domainPointAt(distanceM: Double): DomainPoint {
        val nozzle = nozzleOrigin(0)
        return DomainPoint(
            xM = nozzle.xM + sprayDomainDx * distanceM,
            yM = nozzle.yM + sprayDomainDy * distanceM,
            zM = nozzle.zM + sprayDomainDz * distanceM
        )
    }
}

data class FoggerEffectFrame(
    val groups: List<FoggerMistGroup> = emptyList(),
    val limitation: String? = null,
    val snapshot: PhysicsFieldSnapshot? = null,
    val colorSnapshot: PhysicsFieldSnapshot? = null
) {
    val isEmpty: Boolean get() = groups.isEmpty()
    val particleObjectCount: Int get() = groups.sumOf { it.particleCount }
    val sceneNodeObjectCount: Int get() = groups.size * FogMistVisual.MAX_SCENE_NODES_PER_FOGGER
    val haloObjectCount: Int get() = groups.sumOf { it.haloCount }

    fun group(uuid: String): FoggerMistGroup? = groups.firstOrNull { it.equipmentUuid == uuid }

    companion object {
        val EMPTY = FoggerEffectFrame()
    }
}

data class FoggerDetailsModel(
    val equipmentUuid: String,
    val displayLabel: String,
    val runtimeState: EquipmentRuntimeState,
    val runtimeLabel: String,
    val quantitativeReady: Boolean,
    val readinessLabel: String,
    val localRhPercent: Double?,
    val humidityBand: HumidityLegendBand,
    val classificationLabel: String,
    val qualitativeNote: String = VisualizationCopy.FOG_MIST_QUALITATIVE,
    val limitation: String? = null
)
