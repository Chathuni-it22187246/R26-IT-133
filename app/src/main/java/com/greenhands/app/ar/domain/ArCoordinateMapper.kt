package com.greenhands.app.ar.domain

import com.greenhands.app.equipment.domain.DomainSceneCoordinateAdapter
import com.greenhands.app.equipment.model.DomainPoint
import com.greenhands.app.equipment.model.ScenePoint
import com.greenhands.app.preview3d.domain.SceneLayout

/**
 * Domain (user X=width, Y=length, Z=height, metres) to AR scene units.
 * Reuses [DomainSceneCoordinateAdapter] and the same [SceneLayout] as the 3D preview.
 * Tabletop metres are scene units times [com.greenhands.app.ar.domain.ArTabletopScale.rootScale].
 */
object ArCoordinateMapper {
    const val USER_X = "X — Width position"
    const val USER_Y = "Y — Length position"
    const val USER_Z = "Z — Height position"

    fun toArScene(domain: DomainPoint, layout: SceneLayout): ScenePoint =
        DomainSceneCoordinateAdapter.toScene(domain, layout)

    fun toDomain(scene: ScenePoint, layout: SceneLayout): DomainPoint =
        DomainSceneCoordinateAdapter.toDomain(scene, layout)

    fun toArYawDeg(domainYawDeg: Double, layout: SceneLayout): Double =
        DomainSceneCoordinateAdapter.toSceneYawDeg(domainYawDeg, layout)

    fun toDomainYawDeg(sceneYawDeg: Double, layout: SceneLayout): Double =
        DomainSceneCoordinateAdapter.toDomainYawDeg(sceneYawDeg, layout)
}
