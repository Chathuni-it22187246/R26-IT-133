package com.greenhands.app.heat.visualization

import com.greenhands.app.equipment.model.DomainPoint
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Qualitative mist motion in a downward cone from documented nozzle outlets.
 * Does not read or advance [com.greenhands.app.heat.runtime.PhysicsFieldRuntime].
 * Animation speed, cone angle and speck density are not measured nozzle flow.
 */
object FoggerMistAnimator {
    fun phaseOffset(equipmentUuid: String): Double {
        if (equipmentUuid.isEmpty()) return 0.0
        var hash = 0
        equipmentUuid.forEach { ch -> hash = 31 * hash + ch.code }
        return ((hash.toLong() and 0x7fffffffL) % 997L).toDouble() / 997.0
    }

    fun fractionalPhase(
        visualTimeS: Double,
        particlePhase: Double,
        cycleS: Double = FogMistVisual.CYCLE_S
    ): Double = progress(visualTimeS, 0, 1, cycleS, particlePhase)

    fun progress(
        visualTimeS: Double,
        index: Int,
        count: Int,
        cycleS: Double = FogMistVisual.CYCLE_S,
        groupPhase: Double = 0.0
    ): Double {
        if (count <= 0 || !cycleS.isFinite() || cycleS <= 0.0) return 0.0
        val time = if (visualTimeS.isFinite() && visualTimeS >= 0.0) visualTimeS else 0.0
        val turns = time / cycleS
        val timeFrac = turns - kotlin.math.floor(turns)
        val phase = index.toDouble() / count.toDouble() +
            if (groupPhase.isFinite()) groupPhase else 0.0
        val raw = timeFrac + phase
        return raw - kotlin.math.floor(raw)
    }

    fun distanceM(
        minDistanceM: Double,
        maxDistanceM: Double,
        visualTimeS: Double,
        index: Int,
        count: Int,
        cycleS: Double = FogMistVisual.CYCLE_S,
        groupPhase: Double = 0.0
    ): Double {
        if (!minDistanceM.isFinite() || !maxDistanceM.isFinite()) return minDistanceM
        val t = progress(visualTimeS, index, count, cycleS, groupPhase)
        return minDistanceM + (maxDistanceM - minDistanceM) * t
    }

    fun fade(
        visualTimeS: Double,
        index: Int,
        count: Int,
        cycleS: Double = FogMistVisual.CYCLE_S,
        groupPhase: Double = 0.0
    ): Float = fadeAtProgress(progress(visualTimeS, index, count, cycleS, groupPhase))

    /**
     * Fade in near the nozzle and fade out before the envelope boundary so
     * particles recycle instead of leaving the greenhouse.
     */
    fun fadeAtProgress(progress: Double): Float {
        val t = progress.coerceIn(0.0, 1.0)
        val fadeIn = 0.10
        val fadeOut = 0.84
        val intro = if (t < fadeIn && fadeIn > 0.0) t / fadeIn else 1.0
        val outro = if (t <= fadeOut) {
            1.0
        } else {
            val span = 1.0 - fadeOut
            if (span <= 0.0) 0.0 else (1.0 - t) / span
        }
        return (intro * outro).toFloat().coerceIn(0f, 1f)
    }

    fun recyclesAtEnvelopeBoundary(progress: Double): Boolean = progress >= 0.84

    fun recycleIsBoundedAndContinuous(progressNearWrap: Double = 0.995): Boolean {
        val start = fadeAtProgress(0.0)
        val end = fadeAtProgress(progressNearWrap.coerceIn(0.0, 1.0))
        val mid = fadeAtProgress(0.40)
        return start <= 0.05f && end < 0.20f && mid > end && mid > start
    }

    fun particle(
        group: FoggerMistGroup,
        index: Int
    ): FoggerMistParticle = group.particles.getOrElse(index) {
        FoggerMistParticle(
            nozzleIndex = 0,
            azimuthRad = 0.0,
            radialFraction = 0.5,
            faceFraction = 0.5,
            phase = group.animationPhase,
            distanceFraction = 0.5
        )
    }

    fun particleProgress(
        group: FoggerMistGroup,
        index: Int,
        visualTimeS: Double,
        cycleS: Double = FogMistVisual.CYCLE_S
    ): Double = fractionalPhase(visualTimeS, particle(group, index).phase, cycleS)

    fun visualDistanceM(
        group: FoggerMistGroup,
        index: Int,
        visualTimeS: Double,
        cycleS: Double = FogMistVisual.CYCLE_S
    ): Double {
        val t = particleProgress(group, index, visualTimeS, cycleS)
        val span = (group.maxDistanceM - group.minDistanceM).coerceAtLeast(0.0)
        return group.minDistanceM + span * t
    }

    fun alongSprayM(group: FoggerMistGroup, point: DomainPoint, nozzleIndex: Int): Double {
        val nozzle = group.nozzleOrigin(nozzleIndex)
        return (point.xM - nozzle.xM) * group.sprayDomainDx +
            (point.yM - nozzle.yM) * group.sprayDomainDy +
            (point.zM - nozzle.zM) * group.sprayDomainDz
    }

    fun radialFromAxisM(group: FoggerMistGroup, point: DomainPoint, nozzleIndex: Int): Double {
        val nozzle = group.nozzleOrigin(nozzleIndex)
        val along = alongSprayM(group, point, nozzleIndex)
        val ax = nozzle.xM + group.sprayDomainDx * along
        val ay = nozzle.yM + group.sprayDomainDy * along
        val az = nozzle.zM + group.sprayDomainDz * along
        val dx = point.xM - ax
        val dy = point.yM - ay
        val dz = point.zM - az
        return kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
    }

    fun insideAcceptedCone(
        group: FoggerMistGroup,
        point: DomainPoint,
        nozzleIndex: Int,
        slackM: Double = FogMistVisual.CONE_SLACK_M
    ): Boolean {
        val along = alongSprayM(group, point, nozzleIndex)
        if (along < -1e-4) return false
        val limit = FoggerVisualEnvelope.plumeRadiusM(along.coerceAtLeast(0.0), group.plumeHalfAngleDeg) +
            FogMistVisual.NOZZLE_FACE_RADIUS_M +
            slackM
        return radialFromAxisM(group, point, nozzleIndex) <= limit + 1e-9
    }

    fun particleDomainPoint(
        group: FoggerMistGroup,
        index: Int,
        visualTimeS: Double,
        cycleS: Double = FogMistVisual.CYCLE_S
    ): DomainPoint {
        val speck = particle(group, index)
        val nozzle = group.nozzleOrigin(speck.nozzleIndex)
        val t = particleProgress(group, index, visualTimeS, cycleS)
        val distance = visualDistanceM(group, index, visualTimeS, cycleS)
        val halfRad = Math.toRadians(group.plumeHalfAngleDeg)
        val tanHalf = tan(halfRad)
        val face = FogMistVisual.NOZZLE_FACE_RADIUS_M * speck.faceFraction
        val driftAz = FogMistVisual.DRIFT_AZIMUTH_RAD *
            sin(2.0 * Math.PI * (t + speck.phase * 2.0))
        val driftRad = 1.0 + FogMistVisual.DRIFT_RADIAL *
            cos(2.0 * Math.PI * (t * 1.7 + speck.phase))
        val azimuth = speck.azimuthRad + driftAz
        val radialFraction = (speck.radialFraction * driftRad).coerceIn(
            FogMistVisual.MIN_RADIAL_FRACTION,
            1.0
        )
        val (latA, latB) = FoggerNozzleLayout.laterals(group.yawDeg)
        fun atRadial(radial: Double): DomainPoint {
            val radius = distance * tanHalf * radial.coerceIn(0.0, 1.0)
            val c = cos(azimuth)
            val s = sin(azimuth)
            val lx = c * latA.first + s * latB.first
            val ly = c * latA.second + s * latB.second
            val lz = c * latA.third + s * latB.third
            return DomainPoint(
                xM = nozzle.xM + group.sprayDomainDx * distance + (face + radius) * lx,
                yM = nozzle.yM + group.sprayDomainDy * distance + (face + radius) * ly,
                zM = nozzle.zM + group.sprayDomainDz * distance + (face + radius) * lz
            )
        }
        val raw = atRadial(radialFraction)
        if (FoggerVisualEnvelope.insideHardBoundaries(
                raw,
                group.houseLengthM,
                group.houseWidthM,
                group.houseHeightM
            )
        ) {
            return raw
        }
        var lo = 0.0
        var hi = radialFraction
        var best = atRadial(0.0)
        repeat(10) {
            val mid = 0.5 * (lo + hi)
            val candidate = atRadial(mid)
            if (FoggerVisualEnvelope.insideHardBoundaries(
                    candidate,
                    group.houseLengthM,
                    group.houseWidthM,
                    group.houseHeightM
                )
            ) {
                lo = mid
                best = candidate
            } else {
                hi = mid
            }
        }
        if (!FoggerVisualEnvelope.insideHardBoundaries(
                best,
                group.houseLengthM,
                group.houseWidthM,
                group.houseHeightM
            )
        ) {
            return DomainPoint(
                xM = best.xM.coerceIn(0.0, group.houseWidthM),
                yM = best.yM.coerceIn(0.0, group.houseLengthM),
                zM = best.zM.coerceIn(0.0, group.houseHeightM)
            )
        }
        return best
    }

    fun particlePositions(
        group: FoggerMistGroup,
        visualTimeS: Double,
        cycleS: Double = FogMistVisual.CYCLE_S
    ): List<DomainPoint> = List(group.particleCount) { index ->
        particleDomainPoint(group, index, visualTimeS, cycleS)
    }

    fun visualPresence(
        group: FoggerMistGroup,
        index: Int,
        visualTimeS: Double,
        cycleS: Double = FogMistVisual.CYCLE_S
    ): Float {
        val point = particleDomainPoint(group, index, visualTimeS, cycleS)
        if (!FoggerVisualEnvelope.insideHardBoundaries(
                point,
                group.houseLengthM,
                group.houseWidthM,
                group.houseHeightM
            )
        ) {
            return 0f
        }
        val travel = fadeAtProgress(particleProgress(group, index, visualTimeS, cycleS))
        val canopy = FoggerVisualEnvelope.canopyAttenuation(point.zM, group.canopyHeightM)
        return travel * canopy
    }

    fun visibleParticleCount(
        group: FoggerMistGroup,
        visualTimeS: Double,
        minPresence: Float = 0.12f,
        cycleS: Double = FogMistVisual.CYCLE_S
    ): Int = group.particles.indices.count { index ->
        visualPresence(group, index, visualTimeS, cycleS) >= minPresence
    }

    fun haloDistanceM(group: FoggerMistGroup, index: Int): Double? {
        val sample = group.humiditySamples.getOrNull(index) ?: return null
        return sample.distanceM
    }

    fun haloColor(group: FoggerMistGroup, index: Int): VisualizationRgb {
        val sample = group.humiditySamples.getOrNull(index) ?: return VisualizationRgb.UNAVAILABLE
        return sample.color
    }

    fun humidityAtDistance(
        group: FoggerMistGroup,
        distanceM: Double,
        snapshot: com.greenhands.app.heat.runtime.contracts.PhysicsFieldSnapshot?
    ): Double? {
        if (snapshot != null && snapshot.finiteValues) {
            val domain = group.domainPointAt(distanceM)
            val sampled = FoggerMistEnvelope.predictedHumidityPercent(snapshot, domain)
            if (sampled != null && sampled.isFinite()) return sampled
        }
        return interpolateHumidity(group.humiditySamples, distanceM)
    }

    fun interpolateHumidity(
        samples: List<FoggerHumiditySample>,
        distanceM: Double
    ): Double? {
        if (samples.isEmpty() || !distanceM.isFinite()) return null
        val sorted = samples.sortedBy { it.distanceM }
        if (sorted.size == 1) return sorted.first().relativeHumidityPercent
        if (distanceM <= sorted.first().distanceM) return sorted.first().relativeHumidityPercent
        if (distanceM >= sorted.last().distanceM) return sorted.last().relativeHumidityPercent
        for (i in 0 until sorted.lastIndex) {
            val a = sorted[i]
            val b = sorted[i + 1]
            if (distanceM >= a.distanceM && distanceM <= b.distanceM) {
                val span = b.distanceM - a.distanceM
                if (span <= 0.0) return a.relativeHumidityPercent
                val u = (distanceM - a.distanceM) / span
                return a.relativeHumidityPercent + (b.relativeHumidityPercent - a.relativeHumidityPercent) * u
            }
        }
        return sorted.last().relativeHumidityPercent
    }
}
