package com.greenhands.app.ar.domain

/**
 * Session-only pinch and yaw. Domain metres are never rewritten.
 */
object ArTabletopGestures {
    fun applyPinch(
        currentFactor: Double,
        scaleFactor: Double,
        min: Double = ArTabletopScale.PINCH_MIN,
        max: Double = ArTabletopScale.PINCH_MAX
    ): Double {
        if (!currentFactor.isFinite() || currentFactor <= 0.0) return ArTabletopScale.DEFAULT_PINCH
        if (!scaleFactor.isFinite() || scaleFactor <= 0.0) return currentFactor.coerceIn(min, max)
        return (currentFactor * scaleFactor).coerceIn(min, max)
    }

    fun applyHorizontalRotation(currentYawDeg: Double, deltaDeg: Double): Double {
        if (!currentYawDeg.isFinite()) return 0.0
        if (!deltaDeg.isFinite()) return currentYawDeg
        return normalizeYaw(currentYawDeg - deltaDeg)
    }

    fun normalizeYaw(yawDeg: Double): Double {
        if (!yawDeg.isFinite()) return 0.0
        var yaw = yawDeg % 360.0
        if (yaw > 180.0) yaw -= 360.0
        if (yaw <= -180.0) yaw += 360.0
        return yaw
    }
}
