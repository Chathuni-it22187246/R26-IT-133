package com.greenhands.app.sensor.ar

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/** Simple right-handed 3D math for the camera-free virtual preview (1 unit = 1 meter). */
data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = Vec3(x * s, y * s, z * s)

    fun length(): Float = kotlin.math.sqrt(x * x + y * y + z * z)

    fun normalized(): Vec3 {
        val len = length().coerceAtLeast(1e-6f)
        return this * (1f / len)
    }

    fun cross(o: Vec3) = Vec3(
        y * o.z - z * o.y,
        z * o.x - x * o.z,
        x * o.y - y * o.x
    )

    fun dot(o: Vec3) = x * o.x + y * o.y + z * o.z
}

data class OrbitCameraState(
    val yawDeg: Float = 38f,
    val pitchDeg: Float = 32f,
    val distance: Float = 14f,
    val panX: Float = 0f,
    val panZ: Float = 0f
)

data class ProjectedPoint(
    val x: Float,
    val y: Float,
    val depth: Float
)

object VirtualGreenhouseMath {

    /** Look-at height bias so the greenhouse fills the frame without clipping the roof. */
    fun greenhouseCenter(lengthM: Float, widthM: Float, heightM: Float): Vec3 =
        Vec3(lengthM * 0.5f, heightM * 0.42f, widthM * 0.5f)

    /**
     * Default orbit distance so the full greenhouse is large in view for common sizes
     * (10×8×3, 12×8×4, 20×10×4) without hard-coding those dimensions.
     */
    fun defaultDistance(lengthM: Float, widthM: Float, heightM: Float): Float {
        val footprint = maxOf(lengthM, widthM)
        val roofPeak = heightM * 1.25f
        // Closer than Phase 10B so the model occupies more of the preview canvas.
        return maxOf(footprint * 1.15f, roofPeak * 1.55f).coerceIn(5.5f, 42f)
    }

    fun defaultOrbit(lengthM: Float, widthM: Float, heightM: Float): OrbitCameraState =
        OrbitCameraState(distance = defaultDistance(lengthM, widthM, heightM))

    fun cameraPosition(center: Vec3, state: OrbitCameraState): Vec3 {
        val yaw = Math.toRadians(state.yawDeg.toDouble())
        val pitch = Math.toRadians(state.pitchDeg.toDouble().coerceIn(5.0, 85.0))
        val dist = state.distance.coerceIn(3.5f, 80f)
        val look = center + Vec3(state.panX, 0f, state.panZ)
        return Vec3(
            x = look.x + (dist * cos(pitch) * sin(yaw)).toFloat(),
            y = look.y + (dist * sin(pitch)).toFloat(),
            z = look.z + (dist * cos(pitch) * cos(yaw)).toFloat()
        )
    }

    fun project(
        world: Vec3,
        camera: Vec3,
        target: Vec3,
        viewportW: Float,
        viewportH: Float,
        fovDeg: Float = 42f
    ): ProjectedPoint? {
        val forward = (target - camera).normalized()
        val worldUp = Vec3(0f, 1f, 0f)
        val right = forward.cross(worldUp).let {
            if (it.length() < 1e-4f) Vec3(1f, 0f, 0f) else it.normalized()
        }
        val up = right.cross(forward).normalized()
        val toPoint = world - camera
        val cx = toPoint.dot(right)
        val cy = toPoint.dot(up)
        val cz = toPoint.dot(forward)
        if (cz <= 0.05f) return null
        val fov = Math.toRadians(fovDeg.toDouble())
        // Use the smaller viewport axis so portrait phones still fill the canvas.
        val half = minOf(viewportW, viewportH) * 0.52f
        val scale = half / tan(fov * 0.5).toFloat()
        val sx = viewportW * 0.5f + cx * scale / cz
        val sy = viewportH * 0.5f - cy * scale / cz
        return ProjectedPoint(sx, sy, cz)
    }
}
