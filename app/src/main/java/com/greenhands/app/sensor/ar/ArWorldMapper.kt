package com.greenhands.app.sensor.ar

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure greenhouse-local → AR-world mapping (no Android / ARCore).
 *
 * Coordinate conventions:
 * - Greenhouse local: X = length, Y = height, Z = width
 * - AR world: Y-up, right-handed (ARCore / SceneView)
 * - [ArGreenhousePose.forwardX]/[ArGreenhousePose.forwardZ] = unit vector for local +X
 * - [ArGreenhousePose.yawRadians] = atan2(forwardZ, forwardX)
 *
 * Local +Z (width) is the floor-right vector:
 * `right = forward × up = (-forwardZ, 0, forwardX)`.
 *
 * World = origin + lx·forward + ly·up + lz·right
 */
object ArWorldMapper {

    /** Minimum horizontal distance (meters) between origin and direction tap. */
    const val MIN_DIRECTION_METERS = 0.3f

    data class WorldPoint(val x: Float, val y: Float, val z: Float)

    sealed class DirectionResult {
        data class Ok(
            val forwardX: Float,
            val forwardZ: Float,
            val yawRadians: Float,
            val horizontalLength: Float
        ) : DirectionResult()

        object TooClose : DirectionResult()
    }

    fun yawRadians(forwardX: Float, forwardZ: Float): Float = atan2(forwardZ, forwardX)

    fun directionFromPoints(
        originX: Float,
        @Suppress("UNUSED_PARAMETER") originY: Float,
        originZ: Float,
        pointX: Float,
        @Suppress("UNUSED_PARAMETER") pointY: Float,
        pointZ: Float
    ): DirectionResult {
        val dx = pointX - originX
        val dz = pointZ - originZ
        val len = sqrt(dx * dx + dz * dz)
        if (len < MIN_DIRECTION_METERS) return DirectionResult.TooClose
        val fx = dx / len
        val fz = dz / len
        return DirectionResult.Ok(
            forwardX = fx,
            forwardZ = fz,
            yawRadians = yawRadians(fx, fz),
            horizontalLength = len
        )
    }

    /**
     * Maps greenhouse-local meters to AR world meters using an [ALIGNED] pose.
     * Returns null if origin or yaw is missing.
     */
    fun localToWorld(
        pose: ArGreenhousePose,
        localX: Float,
        localY: Float,
        localZ: Float
    ): WorldPoint? {
        val ox = pose.worldTranslationX ?: return null
        val oy = pose.worldTranslationY ?: return null
        val oz = pose.worldTranslationZ ?: return null
        val fx = pose.forwardX ?: return null
        val fz = pose.forwardZ ?: return null
        // right = forward × up
        val rx = -fz
        val rz = fx
        return WorldPoint(
            x = ox + localX * fx + localZ * rx,
            y = oy + localY,
            z = oz + localX * fz + localZ * rz
        )
    }

    /**
     * Inverse of [localToWorld]: AR world meters → greenhouse-local meters.
     * Returns null if origin or yaw is missing.
     */
    fun worldToLocal(
        pose: ArGreenhousePose,
        worldX: Float,
        worldY: Float,
        worldZ: Float
    ): WorldPoint? {
        val ox = pose.worldTranslationX ?: return null
        val oy = pose.worldTranslationY ?: return null
        val oz = pose.worldTranslationZ ?: return null
        val fx = pose.forwardX ?: return null
        val fz = pose.forwardZ ?: return null
        val rx = -fz
        val rz = fx
        val dx = worldX - ox
        val dy = worldY - oy
        val dz = worldZ - oz
        // orthonormal: lx = delta · forward, lz = delta · right
        return WorldPoint(
            x = dx * fx + dz * fz,
            y = dy,
            z = dx * rx + dz * rz
        )
    }

    /** Convenience: build an aligned pose for unit tests (no AR session). */
    fun alignedPose(
        originX: Float,
        originY: Float,
        originZ: Float,
        forwardX: Float,
        forwardZ: Float
    ): ArGreenhousePose {
        val len = sqrt(forwardX * forwardX + forwardZ * forwardZ)
        require(len > 1e-5f) { "forward must be non-zero" }
        val fx = forwardX / len
        val fz = forwardZ / len
        return ArGreenhousePose(
            phase = ArOriginPlacementPhase.ALIGNED,
            worldTranslationX = originX,
            worldTranslationY = originY,
            worldTranslationZ = originZ,
            forwardX = fx,
            forwardZ = fz,
            yawRadians = yawRadians(fx, fz)
        )
    }
}
