package com.greenhands.app.ar.render

import com.google.android.filament.Engine
import com.greenhands.app.preview3d.domain.GreenhouseRenderParts
import com.greenhands.app.preview3d.domain.GreenhouseSurfaceLooks
import com.greenhands.app.preview3d.domain.SceneLayout
import com.greenhands.app.preview3d.render.GreenhouseMaterials
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.math.Position
import io.github.sceneview.math.Size
import io.github.sceneview.node.CubeNode
import io.github.sceneview.node.Node

/**
 * AR Filament nodes from the same [GreenhouseRenderParts] as the 3D preview.
 * Does not rescale individual boxes; tabletop fit is applied on the AR root.
 */
object ArGreenhouseFactory {
    fun create(
        engine: Engine,
        materialLoader: MaterialLoader,
        layout: SceneLayout
    ): List<Node> {
        val materials = GreenhouseMaterials.instancesByKind(materialLoader)
        return GreenhouseRenderParts.of(layout).map { box ->
            val look = GreenhouseSurfaceLooks.of(box.kind)
            CubeNode(
                engine = engine,
                size = Size(box.sizeX.toFloat(), box.sizeY.toFloat(), box.sizeZ.toFloat()),
                center = Position(
                    box.centerX.toFloat(),
                    box.centerY.toFloat(),
                    box.centerZ.toFloat()
                ),
                materialInstance = materials.getValue(box.kind),
                builderApply = {
                    culling(!look.doubleSided)
                    castShadows(false)
                    receiveShadows(false)
                }
            ).apply {
                name = "ar_${box.id}"
                isTouchable = false
            }
        }
    }
}
