package com.greenhands.app.sensor.ar

import androidx.compose.ui.graphics.Color
import com.google.android.filament.MaterialInstance
import io.github.sceneview.loaders.MaterialLoader

/**
 * Real AR material factory for SceneView 2.2.1 procedural geometry.
 *
 * - [opaqueColor] — lit PBR for small sensor / P# markers only.
 * - [transparentOverlay] — translucent cell bodies and borders so the real floor stays visible.
 *
 * Never use opaque materials for full-footprint floor or greenhouse structure in Real AR.
 */
object ArRealMaterials {

    /** Dielectric — markers and translucent overlays. */
    const val METALLIC = 0f

    /** Fully diffuse so ambient/indirect light fills surfaces under real-world AR lighting. */
    const val ROUGHNESS = 1f

    /** Lower specular so colors read clearly without strong environment reflections. */
    const val REFLECTANCE = 0.35f

    /** Default alpha for translucent accents. */
    const val OVERLAY_ALPHA = 0.38f

    /** Translucent fill for each coverage / blind / overlap cell body. */
    const val CELL_BODY_ALPHA = 0.32f

    /** Slightly stronger alpha for cell edge bars. */
    const val CELL_EDGE_ALPHA = 0.55f

    /**
     * Opaque lit color material. Compose alpha is forced to 1.
     * Use only for compact markers — never for floor-sized geometry.
     */
    fun opaqueColor(
        materialLoader: MaterialLoader,
        color: Color
    ): MaterialInstance = materialLoader.createColorInstance(
        color = color.copy(alpha = 1f),
        metallic = METALLIC,
        roughness = ROUGHNESS,
        reflectance = REFLECTANCE
    )

    /**
     * Transparent lit overlay — SceneView selects `transparent_colored.filamat` when alpha &lt; 1.
     * Preferred for per-cell 3D coverage visualization in Real AR.
     */
    fun transparentOverlay(
        materialLoader: MaterialLoader,
        color: Color,
        alpha: Float = OVERLAY_ALPHA
    ): MaterialInstance = materialLoader.createColorInstance(
        color = color.copy(alpha = alpha.coerceIn(0.05f, 0.95f)),
        metallic = METALLIC,
        roughness = ROUGHNESS,
        reflectance = REFLECTANCE
    )
}
