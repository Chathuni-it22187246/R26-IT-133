package com.greenhands.app.ar.render

import com.greenhands.app.ar.domain.ArTabletopLighting
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.light.LightEstimator
import io.github.sceneview.environment.Environment
import io.github.sceneview.loaders.EnvironmentLoader
import io.github.sceneview.math.Direction
import io.github.sceneview.node.LightNode

/**
 * AR lighting that keeps SceneView 2.2.1 PBR materials visible.
 *
 * Default [io.github.sceneview.ar.ARSceneView.createAREnvironment] has no IBL.
 * ENVIRONMENTAL_HDR can then replace lighting with a near-black cubemap and
 * compound the directional intensity every frame.
 */
object ArSceneLighting {
    fun createFallbackEnvironment(environmentLoader: EnvironmentLoader): Environment =
        environmentLoader.createKTX1Environment(
            iblAssetFile = ArTabletopLighting.NEUTRAL_IBL_ASSET,
            skyboxAssetFile = null
        )

    fun configureEstimator(estimator: LightEstimator?) {
        estimator ?: return
        estimator.environmentalHdrReflections = false
        estimator.environmentalHdrSphericalHarmonics = false
        estimator.environmentalHdrMainLightIntensity = true
        estimator.environmentalHdrMainLightDirection = true
    }

    fun applyVisibleFallback(view: ARSceneView) {
        val light = view.mainLightNode ?: return
        restoreDirectionalLight(light, view.lightEstimation)
    }

    fun restoreDirectionalLight(light: LightNode, estimation: LightEstimator.Estimation?) {
        light.intensity = ArTabletopLighting.visibleIntensityLux(estimation?.mainLightIntensity)
        val direction = estimation?.mainLightDirection
        light.lightDirection = if (direction != null && ArTabletopLighting.keepEstimatedDirection(direction.y)) {
            direction
        } else {
            Direction(0.0f, -1.0f, 0.0f)
        }
    }
}
