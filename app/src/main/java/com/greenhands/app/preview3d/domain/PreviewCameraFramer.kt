package com.greenhands.app.preview3d.domain

import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Axis-aligned visual bounds of the greenhouse mesh, including a modest ground apron.
 */
data class VisualBounds(
    val minX: Double,
    val maxX: Double,
    val minY: Double,
    val maxY: Double,
    val minZ: Double,
    val maxZ: Double
) {
    val centerX: Double get() = (minX + maxX) * 0.5
    val centerY: Double get() = (minY + maxY) * 0.5
    val centerZ: Double get() = (minZ + maxZ) * 0.5
    val sizeX: Double get() = maxX - minX
    val sizeY: Double get() = maxY - minY
    val sizeZ: Double get() = maxZ - minZ
    val radius: Double get() = 0.5 * sqrt(sizeX * sizeX + sizeY * sizeY + sizeZ * sizeZ)

    fun corners(): List<Triple<Double, Double, Double>> = listOf(
        Triple(minX, minY, minZ),
        Triple(minX, minY, maxZ),
        Triple(minX, maxY, minZ),
        Triple(minX, maxY, maxZ),
        Triple(maxX, minY, minZ),
        Triple(maxX, minY, maxZ),
        Triple(maxX, maxY, minZ),
        Triple(maxX, maxY, maxZ)
    )
}

/**
 * Reusable home camera and orbit target derived from visual bounds and viewport aspect.
 * Values are scene units, not metres.
 */
data class PreviewCameraPose(
    val eyeX: Double,
    val eyeY: Double,
    val eyeZ: Double,
    val targetX: Double,
    val targetY: Double,
    val targetZ: Double,
    val distance: Double,
    val farPlane: Double,
    val viewportAspect: Double
)

/**
 * Portrait-aware framing for SceneView 2.2.1.
 *
 * SceneView's [io.github.sceneview.node.CameraNode] uses a 28 mm lens projection.
 * Filament 1.52 models a 36×24 mm frame, so vertical FOV is
 * `2 * atan(12 / 28)` ≈ 46.4°. The manipulator's `orbitHomePosition` is an absolute
 * eye position; `targetPosition` is the orbit pivot. Default far plane is 30 and
 * must be raised when the fitted distance exceeds that.
 */
object PreviewCameraFramer {
    /** SceneView 2.2.1 CameraNode default focal length, millimetres. */
    const val FOCAL_LENGTH_MM = 28.0

    /** Filament 1.52 35 mm frame height, millimetres. */
    const val SENSOR_HEIGHT_MM = 24.0

    /** Conservative phone portrait width/height (9:19.5). */
    const val PORTRAIT_ASPECT = 9.0 / 19.5

    /** Orbit azimuth from +Z toward +X, degrees. */
    const val AZIMUTH_DEG = 40.0

    /** Orbit elevation above the horizontal, degrees. */
    const val ELEVATION_DEG = 26.0

    /** Keep projected content inside this horizontal NDC half-span. */
    const val NDC_X_LIMIT = 0.90

    /**
     * Keep projected content inside this vertical NDC half-span so the greenhouse
     * sits in the unobstructed band between the top overlay and Reset View bar.
     */
    const val NDC_Y_LIMIT = 0.58

    const val MIN_ASPECT = 0.35
    const val MAX_ASPECT = 2.4

    val verticalFovDegrees: Double =
        Math.toDegrees(2.0 * atan((SENSOR_HEIGHT_MM * 0.5) / FOCAL_LENGTH_MM))

    fun visualBounds(layout: SceneLayout): VisualBounds {
        val mesh = GreenhouseMeshBuilder.build(layout)
        return VisualBounds(
            minX = mesh.minOf { it.minX },
            maxX = mesh.maxOf { it.maxX },
            minY = mesh.minOf { it.minY },
            maxY = mesh.maxOf { it.maxY },
            minZ = mesh.minOf { it.minZ },
            maxZ = mesh.maxOf { it.maxZ }
        )
    }

    fun greenhouseEnvelopeBounds(layout: SceneLayout): VisualBounds = VisualBounds(
        minX = -layout.extentX * 0.5,
        maxX = layout.extentX * 0.5,
        minY = 0.0,
        maxY = layout.extentY,
        minZ = -layout.extentZ * 0.5,
        maxZ = layout.extentZ * 0.5
    )

    fun frame(
        layout: SceneLayout,
        viewportAspect: Double = PORTRAIT_ASPECT
    ): PreviewCameraPose {
        val aspect = viewportAspect.coerceIn(MIN_ASPECT, MAX_ASPECT)
        val bounds = visualBounds(layout)
        return frameBounds(bounds, aspect)
    }

    fun frameBounds(
        bounds: VisualBounds,
        viewportAspect: Double = PORTRAIT_ASPECT
    ): PreviewCameraPose {
        require(bounds.sizeX > 0.0 && bounds.sizeY > 0.0 && bounds.sizeZ > 0.0) {
            "visual bounds must have positive size"
        }
        val aspect = viewportAspect.coerceIn(MIN_ASPECT, MAX_ASPECT)
        val targetX = bounds.centerX
        val targetY = bounds.centerY
        val targetZ = bounds.centerZ
        val dir = viewDirection(AZIMUTH_DEG, ELEVATION_DEG)
        val distance = requiredDistance(
            corners = bounds.corners(),
            targetX = targetX,
            targetY = targetY,
            targetZ = targetZ,
            dirToEye = dir,
            aspect = aspect
        )
        val eyeX = targetX + dir.first * distance
        val eyeY = targetY + dir.second * distance
        val eyeZ = targetZ + dir.third * distance
        val farPlane = max(80.0, distance + bounds.radius * 4.0)
        return PreviewCameraPose(
            eyeX = eyeX,
            eyeY = eyeY,
            eyeZ = eyeZ,
            targetX = targetX,
            targetY = targetY,
            targetZ = targetZ,
            distance = distance,
            farPlane = farPlane,
            viewportAspect = aspect
        )
    }

    fun viewDirection(azimuthDeg: Double, elevationDeg: Double): Triple<Double, Double, Double> {
        val azimuth = Math.toRadians(azimuthDeg)
        val elevation = Math.toRadians(elevationDeg)
        val cosEl = cos(elevation)
        return Triple(
            cosEl * sin(azimuth),
            sin(elevation),
            cosEl * cos(azimuth)
        )
    }

    fun projectNdc(
        x: Double,
        y: Double,
        z: Double,
        pose: PreviewCameraPose
    ): Pair<Double, Double> {
        val dir = normalize(
            pose.eyeX - pose.targetX,
            pose.eyeY - pose.targetY,
            pose.eyeZ - pose.targetZ
        )
        val basis = cameraBasis(dir)
        val offsetX = x - pose.targetX
        val offsetY = y - pose.targetY
        val offsetZ = z - pose.targetZ
        val camX = offsetX * basis.rightX + offsetY * basis.rightY + offsetZ * basis.rightZ
        val camY = offsetX * basis.upX + offsetY * basis.upY + offsetZ * basis.upZ
        val depth = pose.distance +
            offsetX * basis.forwardX + offsetY * basis.forwardY + offsetZ * basis.forwardZ
        val tanV = tan(Math.toRadians(verticalFovDegrees) * 0.5)
        val tanH = pose.viewportAspect * tanV
        return Pair(camX / (depth * tanH), camY / (depth * tanV))
    }

    fun fitsInPortraitFrame(
        x: Double,
        y: Double,
        z: Double,
        pose: PreviewCameraPose,
        ndcXLimit: Double = NDC_X_LIMIT,
        ndcYLimit: Double = NDC_Y_LIMIT
    ): Boolean {
        val ndc = projectNdc(x, y, z, pose)
        return ndc.first.isFinite() &&
            ndc.second.isFinite() &&
            kotlin.math.abs(ndc.first) <= ndcXLimit + 1e-9 &&
            kotlin.math.abs(ndc.second) <= ndcYLimit + 1e-9
    }

    private fun requiredDistance(
        corners: List<Triple<Double, Double, Double>>,
        targetX: Double,
        targetY: Double,
        targetZ: Double,
        dirToEye: Triple<Double, Double, Double>,
        aspect: Double
    ): Double {
        val tanV = tan(Math.toRadians(verticalFovDegrees) * 0.5)
        val tanH = aspect * tanV
        val basis = cameraBasis(dirToEye)
        var distance = 0.0
        corners.forEach { (x, y, z) ->
            val ox = x - targetX
            val oy = y - targetY
            val oz = z - targetZ
            val camX = ox * basis.rightX + oy * basis.rightY + oz * basis.rightZ
            val camY = ox * basis.upX + oy * basis.upY + oz * basis.upZ
            val alongForward = ox * basis.forwardX + oy * basis.forwardY + oz * basis.forwardZ
            val needX = kotlin.math.abs(camX) / (NDC_X_LIMIT * tanH) - alongForward
            val needY = kotlin.math.abs(camY) / (NDC_Y_LIMIT * tanV) - alongForward
            val inFront = 0.35 - alongForward
            distance = max(distance, max(needX, max(needY, inFront)))
        }
        return distance
    }

    private data class CameraBasis(
        val forwardX: Double,
        val forwardY: Double,
        val forwardZ: Double,
        val rightX: Double,
        val rightY: Double,
        val rightZ: Double,
        val upX: Double,
        val upY: Double,
        val upZ: Double
    )

    private fun cameraBasis(dirToEye: Triple<Double, Double, Double>): CameraBasis {
        val forwardX = -dirToEye.first
        val forwardY = -dirToEye.second
        val forwardZ = -dirToEye.third
        // right = normalize(forward × worldUp), worldUp = (0, 1, 0)
        val right = normalize(-forwardZ, 0.0, forwardX)
        val upX = right.second * forwardZ - right.third * forwardY
        val upY = right.third * forwardX - right.first * forwardZ
        val upZ = right.first * forwardY - right.second * forwardX
        val up = normalize(upX, upY, upZ)
        return CameraBasis(
            forwardX = forwardX,
            forwardY = forwardY,
            forwardZ = forwardZ,
            rightX = right.first,
            rightY = right.second,
            rightZ = right.third,
            upX = up.first,
            upY = up.second,
            upZ = up.third
        )
    }

    private fun normalize(x: Double, y: Double, z: Double): Triple<Double, Double, Double> {
        val length = hypot(hypot(x, y), z)
        require(length > 1e-12) { "cannot normalize a near-zero vector" }
        return Triple(x / length, y / length, z / length)
    }
}
