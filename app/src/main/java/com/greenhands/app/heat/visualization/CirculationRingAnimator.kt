package com.greenhands.app.heat.visualization

import com.greenhands.app.heat.runtime.adapter.UserPhysicsCoordinateAdapter

/**
 * Qualitative ring motion along the valid probe path. Does not read or advance
 * [com.greenhands.app.heat.runtime.PhysicsFieldRuntime].
 */
object CirculationRingAnimator {
    fun phaseOffset(equipmentUuid: String): Double {
        if (equipmentUuid.isEmpty()) return 0.0
        var hash = 0
        equipmentUuid.forEach { ch -> hash = 31 * hash + ch.code }
        return ((hash.toLong() and 0x7fffffffL) % 997L).toDouble() / 997.0
    }

    fun progress(
        visualTimeS: Double,
        index: Int,
        count: Int,
        cycleS: Double = CirculationRingVisual.CYCLE_S,
        groupPhase: Double = 0.0
    ): Double {
        if (count <= 0 || !cycleS.isFinite() || cycleS <= 0.0) return 0.0
        val time = if (visualTimeS.isFinite() && visualTimeS >= 0.0) visualTimeS else 0.0
        val phase = index.toDouble() / count.toDouble() +
            if (groupPhase.isFinite()) groupPhase else 0.0
        val raw = time / cycleS + phase
        return raw - kotlin.math.floor(raw)
    }

    fun distanceM(
        minDistanceM: Double,
        maxDistanceM: Double,
        visualTimeS: Double,
        index: Int,
        count: Int,
        cycleS: Double = CirculationRingVisual.CYCLE_S,
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
        cycleS: Double = CirculationRingVisual.CYCLE_S,
        groupPhase: Double = 0.0
    ): Float {
        return fadeAtProgress(progress(visualTimeS, index, count, cycleS, groupPhase))
    }

    fun fadeAtProgress(progress: Double): Float {
        val t = progress.coerceIn(0.0, 1.0)
        val fadeIn = 0.06
        val intro = if (t < fadeIn && fadeIn > 0.0) t / fadeIn else 1.0
        val withDistance = 1.0 - t * CirculationRingAppearance.DISTANCE_FADE
        return (intro * withDistance).toFloat().coerceIn(0f, 1f)
    }

    fun expandScale(progress: Double): Float {
        val t = progress.coerceIn(0.0, 1.0)
        return (CirculationRingVisual.EXPAND_START +
            (CirculationRingVisual.EXPAND_END - CirculationRingVisual.EXPAND_START) * t).toFloat()
    }

    fun temperatureAtDistance(
        group: CirculationRingGroup,
        distanceM: Double,
        snapshot: com.greenhands.app.heat.runtime.contracts.PhysicsFieldSnapshot?
    ): Double? {
        if (snapshot != null && snapshot.finiteValues) {
            val domain = group.domainPointAt(distanceM)
            val physics = UserPhysicsCoordinateAdapter.positionToPhysics(domain)
            val sampled = SnapshotFieldSampler.temperatureC(snapshot, physics)
            if (sampled != null && sampled.isFinite()) return sampled
        }
        return interpolateProbeTemperature(group.samples, distanceM)
    }

    fun colorAtDistance(
        group: CirculationRingGroup,
        distanceM: Double,
        snapshot: com.greenhands.app.heat.runtime.contracts.PhysicsFieldSnapshot? = null
    ): VisualizationRgb {
        val temperature = temperatureAtDistance(group, distanceM, snapshot)
        val blended = TemperatureLegendClassifier.blendedColor(
            temperature,
            group.targetTemperatureC,
            group.cdpC,
            group.conC,
            group.eonC
        )
        if (!blended.isUnavailable) return blended
        return nearestSampleColor(group.samples, distanceM)
    }

    fun interpolateProbeTemperature(
        samples: List<CirculationProbeColorSample>,
        distanceM: Double
    ): Double? {
        if (samples.isEmpty() || !distanceM.isFinite()) return null
        val sorted = samples.sortedBy { it.distanceM }
        if (sorted.size == 1) return sorted.first().temperatureC
        if (distanceM <= sorted.first().distanceM) return sorted.first().temperatureC
        if (distanceM >= sorted.last().distanceM) return sorted.last().temperatureC
        for (i in 0 until sorted.lastIndex) {
            val a = sorted[i]
            val b = sorted[i + 1]
            if (distanceM >= a.distanceM && distanceM <= b.distanceM) {
                val span = b.distanceM - a.distanceM
                if (span <= 0.0) return a.temperatureC
                val u = (distanceM - a.distanceM) / span
                return a.temperatureC + (b.temperatureC - a.temperatureC) * u
            }
        }
        return sorted.last().temperatureC
    }

    private fun nearestSampleColor(
        samples: List<CirculationProbeColorSample>,
        distanceM: Double
    ): VisualizationRgb {
        if (samples.isEmpty()) return VisualizationRgb.UNAVAILABLE
        if (samples.size == 1 || !distanceM.isFinite()) return samples.first().color
        val sorted = samples.sortedBy { it.distanceM }
        if (distanceM <= sorted.first().distanceM) return sorted.first().color
        if (distanceM >= sorted.last().distanceM) return sorted.last().color
        for (i in 0 until sorted.lastIndex) {
            val a = sorted[i]
            val b = sorted[i + 1]
            if (distanceM >= a.distanceM && distanceM <= b.distanceM) {
                val span = b.distanceM - a.distanceM
                val u = if (span <= 0.0) 0f else ((distanceM - a.distanceM) / span).toFloat()
                return VisualizationRgb.lerp(a.color, b.color, u)
            }
        }
        return sorted.last().color
    }
}
