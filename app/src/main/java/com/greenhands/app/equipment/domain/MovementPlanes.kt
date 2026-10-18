package com.greenhands.app.equipment.domain

import com.greenhands.app.equipment.model.DomainPoint
import com.greenhands.app.equipment.model.EquipmentType
import com.greenhands.app.equipment.model.PreviewInteractionMode
import com.greenhands.app.equipment.model.ScenePoint
import com.greenhands.app.preview3d.domain.SceneLayout
import kotlin.math.abs

enum class MovementPlane {
    HORIZONTAL_XY,
    END_WALL_YZ
}

data class DragPlaneSpec(
    val plane: MovementPlane,
    val scenePointX: Double,
    val scenePointY: Double,
    val scenePointZ: Double,
    val normalX: Double,
    val normalY: Double,
    val normalZ: Double
)

/**
 * Type-specific 2D-touch movement. A screen drag cannot set X, Y and Z at once.
 */
object MovementPlanes {
    fun planeFor(type: EquipmentType): MovementPlane = when (type) {
        EquipmentType.CIRCULATION_FAN, EquipmentType.FOGGER -> MovementPlane.HORIZONTAL_XY
        EquipmentType.EXHAUST_FAN -> MovementPlane.END_WALL_YZ
    }

    fun scenePlane(
        type: EquipmentType,
        current: DomainPoint,
        layout: SceneLayout,
        lockFarWall: Boolean
    ): DragPlaneSpec {
        val scene = DomainSceneCoordinateAdapter.toScene(current, layout)
        return when (planeFor(type)) {
            MovementPlane.HORIZONTAL_XY -> DragPlaneSpec(
                plane = MovementPlane.HORIZONTAL_XY,
                scenePointX = scene.x,
                scenePointY = scene.y,
                scenePointZ = scene.z,
                normalX = 0.0,
                normalY = 1.0,
                normalZ = 0.0
            )
            MovementPlane.END_WALL_YZ -> {
                val wall = current.copy(yM = if (lockFarWall) layout.dimensions.lengthM else 0.0)
                val wallScene = DomainSceneCoordinateAdapter.toScene(wall, layout)
                if (layout.lengthMappedToX) {
                    DragPlaneSpec(
                        MovementPlane.END_WALL_YZ,
                        wallScene.x, wallScene.y, wallScene.z,
                        1.0, 0.0, 0.0
                    )
                } else {
                    DragPlaneSpec(
                        MovementPlane.END_WALL_YZ,
                        wallScene.x, wallScene.y, wallScene.z,
                        0.0, 0.0, 1.0
                    )
                }
            }
        }
    }

    fun applyHit(
        type: EquipmentType,
        current: DomainPoint,
        hit: DomainPoint,
        context: PlacementContext,
        lockFarWall: Boolean
    ): DomainPoint {
        return when (planeFor(type)) {
            MovementPlane.HORIZONTAL_XY -> DomainPoint(hit.xM, hit.yM, current.zM)
            MovementPlane.END_WALL_YZ -> DomainPoint(
                xM = hit.xM,
                yM = if (lockFarWall) context.lengthM else 0.0,
                zM = hit.zM
            )
        }
    }

    fun farWallFromY(yM: Double, lengthM: Double): Boolean = yM >= lengthM * 0.5
}

/**
 * Perspective ray/plane and screen projection helpers. Independent of SceneView so they can
 * be unit-tested. SceneView supplies the ray origin/direction and viewport size.
 */
object ViewportMath {
    data class Vec3(val x: Double, val y: Double, val z: Double)

    fun rayPlaneIntersection(
        origin: Vec3,
        direction: Vec3,
        planePoint: Vec3,
        planeNormal: Vec3
    ): Vec3? {
        val denom = dot(planeNormal, direction)
        if (!denom.isFinite() || abs(denom) < 1e-8) return null
        val t = dot(planeNormal, Vec3(planePoint.x - origin.x, planePoint.y - origin.y, planePoint.z - origin.z)) / denom
        if (!t.isFinite() || t < 0.0) return null
        return Vec3(origin.x + direction.x * t, origin.y + direction.y * t, origin.z + direction.z * t)
    }

    /**
     * Clip-space to screen pixels. [ndcX]/[ndcY] are after divide-by-w, in [-1, 1],
     * Y-up. Screen Y grows downward.
     */
    fun ndcToScreen(ndcX: Double, ndcY: Double, ndcW: Double, widthPx: Double, heightPx: Double): Pair<Double, Double>? {
        if (!ndcW.isFinite() || abs(ndcW) < 1e-8) return null
        val x = ndcX / ndcW
        val y = ndcY / ndcW
        if (x < -2.0 || x > 2.0 || y < -2.0 || y > 2.0) return null
        val sx = (x + 1.0) * 0.5 * widthPx
        val sy = (1.0 - (y + 1.0) * 0.5) * heightPx
        return sx to sy
    }

    fun worldToViewNormalized(world: ScenePoint, viewYUp: Boolean = true): Pair<Double, Double>? {
        // Placeholder for tests that supply already-normalized values through clampLabel.
        if (!world.x.isFinite() || !world.y.isFinite()) return null
        return world.x to if (viewYUp) world.y else 1.0 - world.y
    }

    fun clampLabel(
        xPx: Double,
        yPx: Double,
        viewportWidthPx: Double,
        viewportHeightPx: Double,
        padPx: Double
    ): Pair<Double, Double> {
        val maxX = (viewportWidthPx - padPx).coerceAtLeast(padPx)
        val maxY = (viewportHeightPx - padPx).coerceAtLeast(padPx)
        return xPx.coerceIn(padPx, maxX) to yPx.coerceIn(padPx, maxY)
    }

    fun viewPxToNormalized(xPx: Double, yPx: Double, widthPx: Double, heightPx: Double): Pair<Double, Double>? {
        if (widthPx <= 0.0 || heightPx <= 0.0) return null
        val nx = xPx / widthPx
        val ny = 1.0 - yPx / heightPx
        return nx to ny
    }

    /**
     * Ray/AABB intersection returning the nearest forward [t], or null.
     * Used for equipment picking when SceneView node hits miss GLB meshes.
     */
    fun rayAabbT(origin: Vec3, direction: Vec3, min: Vec3, max: Vec3): Double? {
        fun slab(originC: Double, dirC: Double, minC: Double, maxC: Double): Pair<Double, Double>? {
            if (abs(dirC) < 1e-12) {
                if (originC < minC || originC > maxC) return null
                return Double.NEGATIVE_INFINITY to Double.POSITIVE_INFINITY
            }
            val inv = 1.0 / dirC
            var t0 = (minC - originC) * inv
            var t1 = (maxC - originC) * inv
            if (t0 > t1) {
                val tmp = t0
                t0 = t1
                t1 = tmp
            }
            return t0 to t1
        }
        val x = slab(origin.x, direction.x, min.x, max.x) ?: return null
        val y = slab(origin.y, direction.y, min.y, max.y) ?: return null
        val z = slab(origin.z, direction.z, min.z, max.z) ?: return null
        val tEnter = maxOf(x.first, y.first, z.first)
        val tExit = minOf(x.second, y.second, z.second)
        if (tEnter > tExit) return null
        val t = if (tEnter >= 0.0) tEnter else tExit
        if (t < 0.0 || !t.isFinite()) return null
        return t
    }

    private fun dot(a: Vec3, b: Vec3): Double = a.x * b.x + a.y * b.y + a.z * b.z
}

object PlacementGesturePolicy {
    fun blocksCameraOrbit(
        mode: PreviewInteractionMode,
        orbitAllowed: Boolean,
        pointerCount: Int,
        dragging: Boolean
    ): Boolean {
        if (mode != PreviewInteractionMode.PLACEMENT) return false
        if (pointerCount > 1) return false
        if (orbitAllowed && !dragging) return false
        return true
    }
}
