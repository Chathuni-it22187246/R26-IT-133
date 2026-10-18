package com.greenhands.app.equipment.domain

import com.greenhands.app.equipment.model.DomainPoint
import com.greenhands.app.equipment.model.ScenePoint
import com.greenhands.app.equipment.render.EquipmentScenePlacement
import com.greenhands.app.preview3d.domain.SceneLayout

/**
 * Converts a world-space camera ray into a domain point on a type-specific movement plane
 * and picks equipment by ray versus scene AABB.
 *
 * Shared by [com.greenhands.app.equipment.domain.PreviewPlacementAdapter] (current SceneView
 * preview) and reserved for a future ARCore adapter. No ARCore types are referenced.
 */
object PlacementPicking {
    fun hitDomain(
        origin: ViewportMath.Vec3,
        direction: ViewportMath.Vec3,
        plane: DragPlaneSpec,
        layout: SceneLayout
    ): DomainPoint? {
        val hit = ViewportMath.rayPlaneIntersection(
            origin,
            direction,
            ViewportMath.Vec3(plane.scenePointX, plane.scenePointY, plane.scenePointZ),
            ViewportMath.Vec3(plane.normalX, plane.normalY, plane.normalZ)
        ) ?: return null
        return DomainSceneCoordinateAdapter.toDomain(ScenePoint(hit.x, hit.y, hit.z), layout)
    }

    fun pickEquipmentId(
        origin: ViewportMath.Vec3,
        direction: ViewportMath.Vec3,
        placements: List<EquipmentScenePlacement>,
        inflate: Double = 0.12
    ): String? {
        var bestId: String? = null
        var bestT = Double.POSITIVE_INFINITY
        placements.forEach { placement ->
            val hx = (placement.boxSizeX * 0.5) + inflate
            val hy = (placement.boxSizeY * 0.5) + inflate
            val hz = (placement.boxSizeZ * 0.5) + inflate
            val t = ViewportMath.rayAabbT(
                origin,
                direction,
                ViewportMath.Vec3(placement.sceneX - hx, placement.sceneY - hy, placement.sceneZ - hz),
                ViewportMath.Vec3(placement.sceneX + hx, placement.sceneY + hy, placement.sceneZ + hz)
            ) ?: return@forEach
            if (t < bestT) {
                bestT = t
                bestId = placement.id
            }
        }
        return bestId
    }
}

data class ViewportAnchor(
    val xPx: Float,
    val yPx: Float,
    val widthPx: Int,
    val heightPx: Int
)
