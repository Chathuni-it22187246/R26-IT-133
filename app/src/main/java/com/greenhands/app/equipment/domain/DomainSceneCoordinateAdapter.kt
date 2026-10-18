package com.greenhands.app.equipment.domain

import com.greenhands.app.equipment.model.DomainPoint
import com.greenhands.app.equipment.model.ScenePoint
import com.greenhands.app.preview3d.domain.SceneLayout

/**
 * Single conversion between the user-facing placement frame and SceneView Y-up scene units.
 *
 * User frame (schema v2), origin at the front-left floor corner, metres:
 * - X = width position (0 … greenhouse width)
 * - Y = length position (0 … greenhouse length)
 * - Z = height above the floor (0 … greenhouse height)
 *
 * Scene mapping when length is the long horizontal edge ([SceneLayout.lengthMappedToX]):
 * - User X (width) → Scene Z
 * - User Y (length) → Scene X
 * - User Z (height) → Scene Y
 *
 * When width is longer, Phase 3B.1 long-axis framing maps the long edge to Scene X:
 * - User X (width) → Scene X
 * - User Y (length) → Scene Z
 * - User Z (height) → Scene Y
 *
 * Phase 3A physics coordinates are not modified. Yaw 0° faces user +X (across width).
 */
object DomainSceneCoordinateAdapter {
    const val COORDINATE_SCHEMA_VERSION = 2

    fun toScene(domain: DomainPoint, layout: SceneLayout): ScenePoint {
        val scale = layout.uniformScale
        val alongLength = (domain.yM - layout.dimensions.lengthM * 0.5) * scale
        val acrossWidth = (domain.xM - layout.dimensions.widthM * 0.5) * scale
        val up = domain.zM * scale
        return if (layout.lengthMappedToX) {
            ScenePoint(x = alongLength, y = up, z = acrossWidth)
        } else {
            ScenePoint(x = acrossWidth, y = up, z = alongLength)
        }
    }

    fun toDomain(scene: ScenePoint, layout: SceneLayout): DomainPoint {
        val scale = layout.uniformScale
        require(scale > 0.0 && scale.isFinite()) { "uniformScale must be finite and > 0" }
        val alongLength: Double
        val acrossWidth: Double
        if (layout.lengthMappedToX) {
            alongLength = scene.x / scale + layout.dimensions.lengthM * 0.5
            acrossWidth = scene.z / scale + layout.dimensions.widthM * 0.5
        } else {
            acrossWidth = scene.x / scale + layout.dimensions.widthM * 0.5
            alongLength = scene.z / scale + layout.dimensions.lengthM * 0.5
        }
        return DomainPoint(xM = acrossWidth, yM = alongLength, zM = scene.y / scale)
    }

    /**
     * Model +Z is equipment forward. SceneView identity looks along +Z.
     * User yaw 0° faces +X (width). When length maps to Scene X, user +X is Scene +Z
     * so no extra offset is applied. When width is the long Scene X, yaw is offset
     * so user +X matches Scene +X.
     */
    fun toSceneYawDeg(domainYawDeg: Double, layout: SceneLayout): Double =
        if (layout.lengthMappedToX) domainYawDeg else domainYawDeg - 90.0

    fun toDomainYawDeg(sceneYawDeg: Double, layout: SceneLayout): Double =
        if (layout.lengthMappedToX) sceneYawDeg else sceneYawDeg + 90.0
}

/**
 * User-facing axis labels and bounds for the schema-v2 placement frame.
 */
object UserPlacementCoordinates {
    const val SCHEMA_VERSION = DomainSceneCoordinateAdapter.COORDINATE_SCHEMA_VERSION
    const val LABEL_X = "X — Width position"
    const val LABEL_Y = "Y — Length position"
    const val LABEL_Z = "Z — Height position"

    fun xRangeM(widthM: Double): ClosedFloatingPointRange<Double> = 0.0..widthM
    fun yRangeM(lengthM: Double): ClosedFloatingPointRange<Double> = 0.0..lengthM
    fun zRangeM(heightM: Double): ClosedFloatingPointRange<Double> = 0.0..heightM

    fun inBounds(point: DomainPoint, widthM: Double, lengthM: Double, heightM: Double): Boolean =
        point.xM in xRangeM(widthM) &&
            point.yM in yRangeM(lengthM) &&
            point.zM in zRangeM(heightM)

    /**
     * Schema v1 stored X=length and Y=width. Swap the horizontal axes and rotate yaw
     * so facing along the house length is preserved (old yaw 0° → new yaw 90°).
     */
    fun migrateFromLengthXWidthY(point: DomainPoint, yawDeg: Double): Pair<DomainPoint, Double> =
        DomainPoint(xM = point.yM, yM = point.xM, zM = point.zM) to (yawDeg + 90.0)
}
