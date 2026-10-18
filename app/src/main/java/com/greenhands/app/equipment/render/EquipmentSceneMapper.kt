package com.greenhands.app.equipment.render

import com.greenhands.app.equipment.domain.DomainSceneCoordinateAdapter
import com.greenhands.app.equipment.model.EquipmentInstance
import com.greenhands.app.equipment.model.EquipmentPose
import com.greenhands.app.preview3d.domain.SceneLayout

data class EquipmentScenePlacement(
    val id: String,
    val instance: EquipmentInstance,
    val sceneX: Double,
    val sceneY: Double,
    val sceneZ: Double,
    val sceneYawDeg: Double,
    val scale: Double,
    val boxSizeX: Double,
    val boxSizeY: Double,
    val boxSizeZ: Double
)

object EquipmentSceneMapper {
    fun placements(instances: List<EquipmentInstance>, layout: SceneLayout): List<EquipmentScenePlacement> {
        val scale = layout.uniformScale
        return instances.map { instance ->
            val scene = DomainSceneCoordinateAdapter.toScene(instance.positionM, layout)
            val aabb = EquipmentPose.worldAabb(instance)
            EquipmentScenePlacement(
                id = instance.id,
                instance = instance,
                sceneX = scene.x,
                sceneY = scene.y,
                sceneZ = scene.z,
                sceneYawDeg = DomainSceneCoordinateAdapter.toSceneYawDeg(instance.yawDeg, layout),
                scale = scale,
                boxSizeX = (aabb.maxXM - aabb.minXM) * scale,
                boxSizeY = (aabb.maxZM - aabb.minZM) * scale,
                boxSizeZ = (aabb.maxYM - aabb.minYM) * scale
            )
        }
    }
}
