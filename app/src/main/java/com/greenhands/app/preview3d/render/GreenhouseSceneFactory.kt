package com.greenhands.app.preview3d.render

import com.google.android.filament.Engine
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.math.Position
import io.github.sceneview.math.Size
import io.github.sceneview.node.CubeNode
import io.github.sceneview.node.Node
import com.greenhands.app.preview3d.domain.GreenhouseRenderParts
import com.greenhands.app.preview3d.domain.PreviewCameraFramer
import com.greenhands.app.preview3d.domain.PreviewCameraPose
import com.greenhands.app.preview3d.domain.SceneLayout

/**
 * Builds a small set of CubeNode instances from [GreenhouseRenderParts].
 * Geometry is created once per layout; callers must destroy the nodes.
 */
object GreenhouseSceneFactory {
    fun create(engine: Engine, materialLoader: MaterialLoader, layout: SceneLayout): List<Node> {
        val materials = GreenhouseMaterials.instancesByKind(materialLoader)
        return GreenhouseRenderParts.of(layout).map { box ->
            CubeNode(
                engine = engine,
                size = Size(box.sizeX.toFloat(), box.sizeY.toFloat(), box.sizeZ.toFloat()),
                center = Position(box.centerX.toFloat(), box.centerY.toFloat(), box.centerZ.toFloat()),
                materialInstance = materials.getValue(box.kind)
            )
        }
    }

    fun cameraPose(layout: SceneLayout, viewportAspect: Double): PreviewCameraPose =
        PreviewCameraFramer.frame(layout, viewportAspect)

    fun cameraHome(pose: PreviewCameraPose): Position =
        Position(pose.eyeX.toFloat(), pose.eyeY.toFloat(), pose.eyeZ.toFloat())

    fun cameraTarget(pose: PreviewCameraPose): Position =
        Position(pose.targetX.toFloat(), pose.targetY.toFloat(), pose.targetZ.toFloat())
}
