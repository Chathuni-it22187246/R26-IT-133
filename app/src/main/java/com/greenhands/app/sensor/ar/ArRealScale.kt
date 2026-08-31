package com.greenhands.app.sensor.ar

import com.greenhands.app.sensor.model.GreenhousePhysicalConfig
import kotlin.math.max

/**
 * Real AR display scale for the greenhouse **root only**.
 *
 * Logical greenhouse dimensions stay in real meters (e.g. 12 × 8 × 4).
 * A uniform [rootScale] shrinks the SceneView root for practical tabletop /
 * near-field AR viewing so the camera is not trapped inside a 12 m mesh.
 *
 * Child nodes keep greenhouse-local meter coordinates; they inherit the root scale.
 * World ↔ local hit mapping must use the same scale (see [ArWorldMapper]).
 */
object ArRealScale {

    /** Longest displayed side target in AR world meters (tabletop-friendly). */
    const val TARGET_LONGEST_SIDE_METERS = 0.72f

    /**
     * Uniform root scale = targetLongest / max(length, width, height).
     * Example: 12 × 8 × 4 → 0.72 / 12 = 0.06 → displayed 0.72 × 0.48 × 0.24 m.
     */
    fun rootScale(physical: GreenhousePhysicalConfig): Float {
        val longest = max(
            physical.lengthMeters.toFloat(),
            max(physical.widthMeters.toFloat(), physical.heightMeters.toFloat())
        )
        if (longest <= 1e-6f) return 1f
        return TARGET_LONGEST_SIDE_METERS / longest
    }

    /** @deprecated Prefer [rootScale] with physical config. */
    fun rootScale(): Float = 1f

    fun localMetersToSceneUnits(meters: Float, physical: GreenhousePhysicalConfig): Float =
        meters * rootScale(physical)

    fun displayedSizeMeters(physical: GreenhousePhysicalConfig): Triple<Float, Float, Float> {
        val s = rootScale(physical)
        return Triple(
            physical.lengthMeters.toFloat() * s,
            physical.widthMeters.toFloat() * s,
            physical.heightMeters.toFloat() * s
        )
    }

    fun isTabletopDisplayScale(physical: GreenhousePhysicalConfig): Boolean =
        rootScale(physical) < 0.999f
}
