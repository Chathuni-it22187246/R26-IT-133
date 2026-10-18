package com.greenhands.app.ar.domain

import com.greenhands.app.equipment.domain.DragPlaneSpec
import com.greenhands.app.equipment.domain.MovementPlanes
import com.greenhands.app.equipment.domain.PlacementContext
import com.greenhands.app.equipment.domain.PlacementPicking
import com.greenhands.app.equipment.domain.PlacementValidator
import com.greenhands.app.equipment.domain.ViewportMath
import com.greenhands.app.equipment.model.DomainPoint
import com.greenhands.app.equipment.model.EquipmentInstance
import com.greenhands.app.equipment.model.EquipmentType
import com.greenhands.app.equipment.model.ScenePoint
import com.greenhands.app.equipment.render.EquipmentScenePlacement
import com.greenhands.app.preview3d.domain.SceneLayout
import kotlin.math.cos
import kotlin.math.sin

/**
 * Converts AR world hits into greenhouse-local scene units and user-facing domain metres.
 *
 * Greenhouse root: parented to the AR plane anchor, uniform tabletop [rootScale], yaw about Y.
 * Scene units match the 3D preview [SceneLayout]; tabletop metres are scene × scale.
 *
 * Filament/SceneView Y rotation (column vector):
 * x' = x cos θ + z sin θ
 * z' = −x sin θ + z cos θ
 */
object ArPlacementAdapter {
    data class Vec3(val x: Double, val y: Double, val z: Double)

    fun sceneToWorld(
        scene: ScenePoint,
        origin: Vec3,
        yawDeg: Double,
        uniformScale: Double
    ): Vec3 {
        val scaled = Vec3(scene.x * uniformScale, scene.y * uniformScale, scene.z * uniformScale)
        val rotated = rotateY(Math.toRadians(yawDeg), scaled)
        return Vec3(origin.x + rotated.x, origin.y + rotated.y, origin.z + rotated.z)
    }

    fun worldToScene(
        world: Vec3,
        origin: Vec3,
        yawDeg: Double,
        uniformScale: Double
    ): ScenePoint {
        require(uniformScale.isFinite() && uniformScale > 0.0) { "uniformScale must be finite and > 0" }
        val translated = Vec3(world.x - origin.x, world.y - origin.y, world.z - origin.z)
        val local = rotateY(Math.toRadians(-yawDeg), translated)
        return ScenePoint(local.x / uniformScale, local.y / uniformScale, local.z / uniformScale)
    }

    fun worldToDomain(
        world: Vec3,
        origin: Vec3,
        yawDeg: Double,
        uniformScale: Double,
        layout: SceneLayout
    ): DomainPoint = ArCoordinateMapper.toDomain(
        worldToScene(world, origin, yawDeg, uniformScale),
        layout
    )

    fun domainToWorld(
        domain: DomainPoint,
        origin: Vec3,
        yawDeg: Double,
        uniformScale: Double,
        layout: SceneLayout
    ): Vec3 = sceneToWorld(ArCoordinateMapper.toArScene(domain, layout), origin, yawDeg, uniformScale)

    /**
     * Transforms an AR-world camera ray into greenhouse-local scene space.
     * Direction is inverse-rotated and inverse-scaled; it is not renormalized.
     */
    fun worldRayToScene(
        originWorld: Vec3,
        directionWorld: Vec3,
        rootOrigin: Vec3,
        yawDeg: Double,
        uniformScale: Double
    ): Pair<ViewportMath.Vec3, ViewportMath.Vec3> {
        val sceneOrigin = worldToScene(originWorld, rootOrigin, yawDeg, uniformScale)
        val sceneFar = worldToScene(
            Vec3(
                originWorld.x + directionWorld.x,
                originWorld.y + directionWorld.y,
                originWorld.z + directionWorld.z
            ),
            rootOrigin,
            yawDeg,
            uniformScale
        )
        return ViewportMath.Vec3(sceneOrigin.x, sceneOrigin.y, sceneOrigin.z) to
            ViewportMath.Vec3(
                sceneFar.x - sceneOrigin.x,
                sceneFar.y - sceneOrigin.y,
                sceneFar.z - sceneOrigin.z
            )
    }

    fun hitDomain(
        originScene: ViewportMath.Vec3,
        directionScene: ViewportMath.Vec3,
        type: EquipmentType,
        current: DomainPoint,
        layout: SceneLayout,
        lockFarWall: Boolean
    ): DomainPoint? {
        val plane: DragPlaneSpec = MovementPlanes.scenePlane(type, current, layout, lockFarWall)
        return PlacementPicking.hitDomain(originScene, directionScene, plane, layout)
    }

    fun pickEquipmentId(
        originScene: ViewportMath.Vec3,
        directionScene: ViewportMath.Vec3,
        placements: List<EquipmentScenePlacement>
    ): String? = PlacementPicking.pickEquipmentId(originScene, directionScene, placements)

    /**
     * Applies type-specific movement planes, exhaust wall lock, and mounting clamps.
     * Collision against peers is left to [PlacementValidator] via the placement ViewModel.
     */
    fun applyDragHit(
        instance: EquipmentInstance,
        rawHit: DomainPoint,
        context: PlacementContext,
        lockFarWall: Boolean
    ): DomainPoint {
        val planeHit = MovementPlanes.applyHit(
            instance.type,
            instance.positionM,
            rawHit,
            context,
            lockFarWall
        )
        var next = instance.copy(positionM = planeHit)
        next = when (next.type) {
            EquipmentType.EXHAUST_FAN -> PlacementValidator.snapExhaust(next, context)
            EquipmentType.FOGGER -> PlacementValidator.clampOverhead(next, context)
            EquipmentType.CIRCULATION_FAN -> PlacementValidator.clampElevation(next, context)
        }
        return next.positionM
    }

    fun exhaustYLocked(instance: EquipmentInstance, context: PlacementContext): Boolean {
        if (instance.type != EquipmentType.EXHAUST_FAN) return false
        val far = MovementPlanes.farWallFromY(instance.positionM.yM, context.lengthM)
        val expected = if (far) context.lengthM else 0.0
        return kotlin.math.abs(instance.positionM.yM - expected) <= PlacementValidator.EXHAUST_WALL_TOLERANCE_M
    }

    private fun rotateY(yawRad: Double, v: Vec3): Vec3 {
        val c = cos(yawRad)
        val s = sin(yawRad)
        return Vec3(
            x = v.x * c + v.z * s,
            y = v.y,
            z = -v.x * s + v.z * c
        )
    }
}
