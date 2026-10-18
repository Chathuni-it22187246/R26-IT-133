package com.greenhands.app.ar.render

import com.greenhands.app.ar.domain.ArTabletopScale
import com.greenhands.app.equipment.domain.DomainSceneCoordinateAdapter
import com.greenhands.app.equipment.model.DomainPoint
import com.greenhands.app.equipment.model.ScenePoint
import com.greenhands.app.preview3d.domain.SceneLayout

/**
 * Circulation-ring coordinates in greenhouse-local scene units.
 * Tabletop scale stays on the AR greenhouse root and is never applied again here.
 */
object CirculationRingSceneMath {
    const val GREENHOUSE_ROOT_NAME = "ar_greenhouse_root"
    const val APPLIES_TABLETOP_SCALE_TO_CHILDREN = false

    fun scenePosition(domain: DomainPoint, layout: SceneLayout): ScenePoint =
        DomainSceneCoordinateAdapter.toScene(domain, layout)

    fun sceneDistance(domainM: Double, layout: SceneLayout): Double =
        domainM * layout.uniformScale

    fun sceneYawDeg(domainYawDeg: Double, layout: SceneLayout): Double =
        DomainSceneCoordinateAdapter.toSceneYawDeg(domainYawDeg, layout)

    fun domainForwardToScene(
        domainDx: Double,
        domainDy: Double,
        domainDz: Double,
        layout: SceneLayout
    ): Triple<Double, Double, Double> {
        val local = domainOffsetToRootLocal(domainDx, domainDy, domainDz, layout)
        return Triple(
            local.first * layout.uniformScale,
            local.second * layout.uniformScale,
            local.third * layout.uniformScale
        )
    }

    fun domainOffsetToRootLocal(
        domainDx: Double,
        domainDy: Double,
        domainDz: Double,
        layout: SceneLayout
    ): Triple<Double, Double, Double> {
        return if (layout.lengthMappedToX) {
            Triple(domainDy, domainDz, domainDx)
        } else {
            Triple(domainDx, domainDz, domainDy)
        }
    }

    fun childWouldDoubleScaleIfMultipliedByRootScale(
        sceneDistance: Double,
        pinchFactor: Double = 1.0
    ): Double = sceneDistance * ArTabletopScale.worldScale(pinchFactor)
}
