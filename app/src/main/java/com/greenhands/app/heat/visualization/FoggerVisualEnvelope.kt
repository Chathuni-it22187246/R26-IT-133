package com.greenhands.app.heat.visualization

import com.greenhands.app.heat.runtime.adapter.UncalibratedPhase3AProfileSettings
import com.greenhands.app.heat.runtime.contracts.PhysicsFieldSnapshot
import com.greenhands.app.preview3d.domain.CropRowGeometry
import kotlin.math.min

/**
 * Visual mist envelope from the accepted FoggerSource / FoggerProfile plume fields.
 * Crop canopy is porous attenuation, not a hard clip. Does not change the solver.
 */
object FoggerVisualEnvelope {
    const val HARD_CLIPS_CANOPY = false
    const val FLOOR_IS_HARD_BOUNDARY = true
    const val WALLS_ARE_HARD_BOUNDARY = true
    /** Slope of the absolute height curve. Not multiplied across frames. */
    const val CANOPY_ATTENUATION = 0.50
    const val MIN_CANOPY_ALPHA = 0.42f
    const val CANOPY_IS_ABSOLUTE_MULTIPLIER = true
    const val CANOPY_IS_CUMULATIVE = false

    fun acceptedPlumeLengthM(): Double = UncalibratedPhase3AProfileSettings.FOGGER_PLUME_LENGTH_M

    fun acceptedPlumeAngleDeg(): Double = UncalibratedPhase3AProfileSettings.FOGGER_PLUME_ANGLE_DEG

    fun acceptedHalfAngleDeg(): Double = acceptedPlumeAngleDeg() * 0.5

    fun canopyHeightM(snapshot: PhysicsFieldSnapshot): Double =
        snapshot.grid.heightM * CropRowGeometry.HEIGHT_FILL

    fun usesArbitraryVisualFallback(halfAngleDeg: Double, lengthM: Double): Boolean {
        val arbitraryAngle = kotlin.math.abs(halfAngleDeg - FogMistVisual.UNUSED_PLUME_HALF_ANGLE_DEG) < 1e-9
        val arbitraryLength = kotlin.math.abs(lengthM - FogMistVisual.UNUSED_PLUME_LENGTH_M) < 1e-9
        return arbitraryAngle || arbitraryLength
    }

    fun clippedPathM(
        acceptedLengthM: Double,
        envelopePathM: Double,
        nozzleZM: Double
    ): Double {
        val floorRoom = nozzleZM.coerceAtLeast(0.0)
        return min(acceptedLengthM, min(envelopePathM, floorRoom)).coerceAtLeast(0.0)
    }

    fun canopyAttenuation(zM: Double, canopyHeightM: Double?): Float {
        if (canopyHeightM == null || !canopyHeightM.isFinite() || canopyHeightM <= 0.0) return 1f
        if (!zM.isFinite() || zM >= canopyHeightM) return 1f
        val depth = ((canopyHeightM - zM) / canopyHeightM).coerceIn(0.0, 1.0)
        val absolute = 1.0 - CANOPY_ATTENUATION * depth
        return absolute.toFloat().coerceIn(MIN_CANOPY_ALPHA, 1f)
    }

    fun plumeRadiusM(distanceM: Double, halfAngleDeg: Double): Double {
        if (!distanceM.isFinite() || distanceM <= 0.0) return 0.0
        return distanceM * kotlin.math.tan(Math.toRadians(halfAngleDeg))
    }

    fun insideHardBoundaries(
        point: com.greenhands.app.equipment.model.DomainPoint,
        lengthM: Double,
        widthM: Double,
        heightM: Double
    ): Boolean {
        if (!point.xM.isFinite() || !point.yM.isFinite() || !point.zM.isFinite()) return false
        return point.xM + 1e-9 >= 0.0 &&
            point.xM <= widthM + 1e-9 &&
            point.yM + 1e-9 >= 0.0 &&
            point.yM <= lengthM + 1e-9 &&
            point.zM + 1e-9 >= 0.0 &&
            point.zM <= heightM + 1e-9
    }
}
