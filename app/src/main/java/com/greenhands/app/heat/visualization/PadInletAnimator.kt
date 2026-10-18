package com.greenhands.app.heat.visualization

import com.greenhands.app.heat.runtime.adapter.UserPhysicsCoordinateAdapter
import com.greenhands.app.heat.runtime.contracts.PhysicsFieldSnapshot

/**
 * Qualitative inlet-arrow motion from the pad toward the exhaust.
 * Speed is not measured air velocity; density is not airflow rate.
 */
object PadInletAnimator {
    fun distanceM(
        pathM: Double,
        visualTimeS: Double,
        index: Int,
        count: Int,
        cycleS: Double = PadInletVisual.CYCLE_S
    ): Double {
        val t = ExhaustAirflowAnimator.progress(visualTimeS, index, count, cycleS, 0.12)
        return pathM * t
    }

    fun colorAt(
        spec: PadInletStreamSpec,
        distanceM: Double,
        snapshot: PhysicsFieldSnapshot?,
        wetPadActive: Boolean,
        padOutletTemperatureC: Double?
    ): VisualizationRgb {
        val domain = spec.pointAt(distanceM)
        val sampled = snapshot?.let { snap ->
            if (!snap.finiteValues) null
            else SnapshotFieldSampler.temperatureC(
                snap,
                UserPhysicsCoordinateAdapter.positionToPhysics(domain)
            )
        }
        val temperature = when {
            sampled != null && sampled.isFinite() -> sampled
            spec.claimsEvaporativeCooling &&
                wetPadActive &&
                padOutletTemperatureC != null &&
                padOutletTemperatureC.isFinite() -> padOutletTemperatureC
            else -> null
        }
        val blended = TemperatureLegendClassifier.blendedColor(
            temperature,
            spec.targetTemperatureC,
            spec.cdpC,
            spec.conC,
            spec.eonC
        )
        if (!blended.isUnavailable) return blended.withAlpha(PadInletAppearance.MATERIAL_ALPHA)
        return VisualizationRgb.NEUTRAL_AIRFLOW.withAlpha(PadInletAppearance.MATERIAL_ALPHA)
    }
}
