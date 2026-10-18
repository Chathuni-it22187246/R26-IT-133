package com.greenhands.app.heat.visualization

import com.greenhands.app.heat.runtime.adapter.UserPhysicsCoordinateAdapter

/**
 * Qualitative exhaust-arrow motion along the indoor approach and short outdoor
 * discharge. Does not read or advance [com.greenhands.app.heat.runtime.PhysicsFieldRuntime].
 */
object ExhaustAirflowAnimator {
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
        cycleS: Double,
        groupPhase: Double = 0.0
    ): Double {
        if (count <= 0 || !cycleS.isFinite() || cycleS <= 0.0) return 0.0
        val time = if (visualTimeS.isFinite() && visualTimeS >= 0.0) visualTimeS else 0.0
        val phase = index.toDouble() / count.toDouble() +
            if (groupPhase.isFinite()) groupPhase else 0.0
        val raw = time / cycleS + phase
        return raw - kotlin.math.floor(raw)
    }

    /**
     * Indoor distance from the fan along the inward axis. Progress 0 is farthest
     * inside; progress 1 reaches the fan.
     */
    fun indoorDistanceM(
        minDistanceM: Double,
        maxDistanceM: Double,
        visualTimeS: Double,
        index: Int,
        count: Int,
        cycleS: Double = ExhaustAirflowVisual.CYCLE_S,
        groupPhase: Double = 0.0
    ): Double {
        if (!minDistanceM.isFinite() || !maxDistanceM.isFinite()) return minDistanceM
        val t = progress(visualTimeS, index, count, cycleS, groupPhase)
        return maxDistanceM + (minDistanceM - maxDistanceM) * t
    }

    fun outdoorDistanceM(
        maxDistanceM: Double,
        visualTimeS: Double,
        index: Int,
        count: Int,
        cycleS: Double = ExhaustAirflowVisual.OUTDOOR_CYCLE_S,
        groupPhase: Double = 0.0
    ): Double {
        if (!maxDistanceM.isFinite() || maxDistanceM < 0.0) return 0.0
        val t = progress(visualTimeS, index, count, cycleS, groupPhase)
        return maxDistanceM * t
    }

    fun fadeAtProgress(progress: Double, outdoor: Boolean = false): Float {
        val t = progress.coerceIn(0.0, 1.0)
        val fadeIn = 0.08
        val intro = if (t < fadeIn && fadeIn > 0.0) t / fadeIn else 1.0
        val fadeOutStart = 0.82
        val outro = if (t > fadeOutStart) {
            ((1.0 - t) / (1.0 - fadeOutStart)).coerceIn(0.0, 1.0)
        } else {
            1.0
        }
        val distance = 1.0 - t * ExhaustAirflowAppearance.DISTANCE_FADE
        val outdoorScale = if (outdoor) ExhaustAirflowAppearance.OUTDOOR_ALPHA_SCALE.toDouble() else 1.0
        return (intro * outro * distance * outdoorScale).toFloat().coerceIn(0f, 1f)
    }

    fun temperatureAtIndoorDistance(
        group: ExhaustArrowGroup,
        distanceM: Double,
        snapshot: com.greenhands.app.heat.runtime.contracts.PhysicsFieldSnapshot?
    ): Double? {
        if (group.mode != ExhaustAirflowMode.QUANTITATIVE) return null
        if (snapshot != null && snapshot.finiteValues) {
            val domain = group.indoorPointAt(distanceM)
            val physics = UserPhysicsCoordinateAdapter.positionToPhysics(domain)
            val sampled = SnapshotFieldSampler.temperatureC(snapshot, physics)
            if (sampled != null && sampled.isFinite()) return sampled
        }
        return interpolateProbeTemperature(group.samples, distanceM)
    }

    fun colorAtIndoorDistance(
        group: ExhaustArrowGroup,
        distanceM: Double,
        snapshot: com.greenhands.app.heat.runtime.contracts.PhysicsFieldSnapshot? = null
    ): VisualizationRgb {
        if (group.mode != ExhaustAirflowMode.QUANTITATIVE) {
            return VisualizationRgb.NEUTRAL_AIRFLOW.withAlpha(ExhaustAirflowAppearance.QUALITATIVE_ALPHA)
        }
        val temperature = temperatureAtIndoorDistance(group, distanceM, snapshot)
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

    fun outdoorColor(): VisualizationRgb =
        VisualizationRgb.NEUTRAL_AIRFLOW.withAlpha(
            ExhaustAirflowAppearance.QUALITATIVE_ALPHA * ExhaustAirflowAppearance.OUTDOOR_ALPHA_SCALE
        )

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
