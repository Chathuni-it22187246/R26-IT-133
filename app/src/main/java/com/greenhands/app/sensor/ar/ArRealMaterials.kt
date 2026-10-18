package com.greenhands.app.sensor.ar



import androidx.compose.ui.graphics.Color

import com.google.android.filament.MaterialInstance

import io.github.sceneview.loaders.MaterialLoader



/**

 * Real AR material factory for SceneView 2.2.1 procedural geometry.

 *

 * - [opaqueColor] — lit PBR for sensor markers, wireframe guides, calibration beads.

 * - [transparentOverlay] — uses `transparent_colored.filamat` when alpha &lt; 1 (SceneView API).

 * - Coverage floor uses **outline edges only** ([ArCoverageNodes]) so the real floor stays visible.

 */

object ArRealMaterials {



    /** Dielectric — greenhouse frame, markers. */

    const val METALLIC = 0f



    /** Fully diffuse so ambient/indirect light fills surfaces under real-world AR lighting. */

    const val ROUGHNESS = 1f



    /** Lower specular so colors read clearly without strong environment reflections. */

    const val REFLECTANCE = 0.35f



    /** Default alpha for optional translucent accents (not used for full cell fills). */

    const val OVERLAY_ALPHA = 0.38f



    /**

     * Opaque lit color material. Compose alpha is forced to 1.

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

     * Do not use for full-footprint floor slabs; prefer outline coverage in Real AR.

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

