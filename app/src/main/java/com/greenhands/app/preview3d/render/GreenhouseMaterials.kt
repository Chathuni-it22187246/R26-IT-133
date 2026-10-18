package com.greenhands.app.preview3d.render

import androidx.compose.ui.graphics.Color
import com.google.android.filament.MaterialInstance
import com.greenhands.app.preview3d.domain.GreenhouseSurfaceLooks
import com.greenhands.app.preview3d.domain.MeshKind
import io.github.sceneview.loaders.MaterialLoader

/**
 * SceneView 2.2.1 colour instances. Alpha &lt; 1 selects the transparent filamat.
 */
object GreenhouseMaterials {
    fun instance(materialLoader: MaterialLoader, kind: MeshKind): MaterialInstance {
        val look = GreenhouseSurfaceLooks.of(kind)
        return materialLoader.createColorInstance(
            color = Color(look.red, look.green, look.blue, look.alpha),
            metallic = look.metallic,
            roughness = look.roughness
        )
    }

    fun instancesByKind(materialLoader: MaterialLoader): Map<MeshKind, MaterialInstance> =
        MeshKind.values().associateWith { instance(materialLoader, it) }
}
