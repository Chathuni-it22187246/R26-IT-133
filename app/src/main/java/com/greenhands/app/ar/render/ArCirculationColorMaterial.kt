package com.greenhands.app.ar.render

import com.greenhands.app.heat.visualization.CirculationRingAppearance
import com.greenhands.app.heat.visualization.VisualizationRgb

/**
 * SceneView 2.2.1 [io.github.sceneview.loaders.MaterialLoader.createColorInstance] uses
 * `opaque_colored` / `transparent_colored` filamat. Those instances expose `color`,
 * `metallic`, `roughness`, and `reflectance` only.
 *
 * [io.github.sceneview.material.setEmissiveFactor] writes the glTF ubershader uniform
 * `emissiveFactor`. Filament panics natively when that name is missing:
 * `getFieldInfo:170 reason: uniform named "emissiveFactor" not found` then SIGABRT.
 * That abort cannot be caught with try/catch or runCatching.
 */
object ArCirculationColorMaterial {
    const val HOST_MATERIAL = "MaterialLoader.createColorInstance"
    const val COLOR_UNIFORM = "color"
    const val EMISSIVE_FACTOR_UNIFORM = "emissiveFactor"
    const val NATIVE_FILAMENT_ABORT_IS_CATCHABLE = false

    val allowedUniforms: Set<String> = setOf("color", "metallic", "roughness", "reflectance")
    val forbiddenUniforms: Set<String> = setOf(
        "emissiveFactor",
        "emissiveStrength",
        "emissiveIndex",
        "emissiveMap"
    )

    data class ColorWrite(
        val uniformNames: Set<String>,
        val red: Float,
        val green: Float,
        val blue: Float,
        val alpha: Float
    ) {
        val requestsEmissiveFactor: Boolean get() = EMISSIVE_FACTOR_UNIFORM in uniformNames
    }

    fun writesEmissiveFactor(): Boolean = CirculationRingAppearance.WRITES_EMISSIVE_FACTOR

    fun frameColorWrite(color: VisualizationRgb, fade: Float): ColorWrite {
        if (color.isUnavailable) {
            return ColorWrite(setOf(COLOR_UNIFORM), 0f, 0f, 0f, 0f)
        }
        return ColorWrite(
            uniformNames = setOf(COLOR_UNIFORM),
            red = color.red,
            green = color.green,
            blue = color.blue,
            alpha = (color.alpha * fade).coerceIn(0f, 1f)
        )
    }

    fun maySetParameter(uniformName: String): Boolean =
        uniformName in allowedUniforms && uniformName !in forbiddenUniforms
}
