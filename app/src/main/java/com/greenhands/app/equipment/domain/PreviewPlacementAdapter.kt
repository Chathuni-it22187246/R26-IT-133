package com.greenhands.app.equipment.domain

import com.greenhands.app.equipment.model.DomainPoint
import com.greenhands.app.equipment.render.EquipmentScenePlacement
import com.greenhands.app.preview3d.domain.SceneLayout

/**
 * Non-AR SceneView implementation of shared placement hit-testing.
 * Camera rays come from SceneView 2.2.1; domain mapping stays in this module.
 */
object PreviewPlacementAdapter {
    fun domainHit(
        origin: ViewportMath.Vec3,
        direction: ViewportMath.Vec3,
        plane: DragPlaneSpec,
        layout: SceneLayout
    ): DomainPoint? = PlacementPicking.hitDomain(origin, direction, plane, layout)

    fun pickEquipment(
        origin: ViewportMath.Vec3,
        direction: ViewportMath.Vec3,
        placements: List<EquipmentScenePlacement>
    ): String? = PlacementPicking.pickEquipmentId(origin, direction, placements)
}

/**
 * Phase 3C.2B adds research-guided suggestion galleries and Apply Layout on the
 * live AR tabletop. Manual placement from Phase 3C.2A is unchanged.
 */
object ArPlacementAdapterContract {
    const val STATUS = "Phase 3C.2B research-guided suggestions and Apply Layout"
    const val HIT_SOURCE =
        "Camera rays transformed through the greenhouse root (anchor, yaw, tabletop scale) into shared scene/domain metres"
    const val SHARED_LOGIC =
        "Equipment models, IDs, names, domain coordinates, placement planes, validation, labels, persistence and GLB assets stay shared. Filament resources are created on the AR engine only."
    const val REQUIRES_LATER = "AR animation and heat visualisation remain deferred."
}
