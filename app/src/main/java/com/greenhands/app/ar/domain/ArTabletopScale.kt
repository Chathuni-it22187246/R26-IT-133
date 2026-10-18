package com.greenhands.app.ar.domain

import com.greenhands.app.preview3d.domain.GreenhouseSurfaceLooks
import com.greenhands.app.preview3d.domain.MeshKind
import com.greenhands.app.preview3d.domain.SceneLayout
import com.greenhands.app.preview3d.domain.SceneScaleMapper
import com.greenhands.app.preview3d.model.GreenhouseDimensions
import kotlin.math.max

/**
 * Uniform tabletop visual scale. Domain dimensions stay in real metres.
 *
 * Mesh parts are built in the same scene units as the 3D preview
 * ([SceneScaleMapper.TARGET_LONG_SPAN]). [rootScale] is applied once on the AR
 * root so a Demo 10×5×3 m house occupies [TARGET_LONG_SPAN_M] on the table.
 */
object ArTabletopScale {
    /** Longest horizontal envelope edge on the table, metres. */
    const val TARGET_LONG_SPAN_M = 0.30
    const val PINCH_MIN = 0.4
    const val PINCH_MAX = 3.0
    const val DEFAULT_PINCH = 1.0

    fun visualMetresPerDomainMetre(dimensions: GreenhouseDimensions): Double {
        val longM = max(dimensions.lengthM, dimensions.widthM)
        require(longM.isFinite() && longM > 0.0) { "horizontal span must be finite and > 0" }
        return TARGET_LONG_SPAN_M / longM
    }

    fun tabletopLengthM(dimensions: GreenhouseDimensions): Double =
        dimensions.lengthM * visualMetresPerDomainMetre(dimensions)

    fun tabletopWidthM(dimensions: GreenhouseDimensions): Double =
        dimensions.widthM * visualMetresPerDomainMetre(dimensions)

    fun tabletopHeightM(dimensions: GreenhouseDimensions): Double =
        dimensions.heightM * visualMetresPerDomainMetre(dimensions)

    /** Same layout the non-AR Predicted Preview uses. */
    fun layout(dimensions: GreenhouseDimensions, cropRowCount: Int): SceneLayout =
        SceneScaleMapper.layout(dimensions, cropRowCount = cropRowCount)

    fun rootScale(): Double = TARGET_LONG_SPAN_M / SceneScaleMapper.TARGET_LONG_SPAN

    fun worldScale(pinchFactor: Double): Double = rootScale() * pinchFactor
}

data class ArSurfaceStyle(
    val red: Float,
    val green: Float,
    val blue: Float,
    val alpha: Float,
    val metallic: Float,
    val roughness: Float,
    val reflectance: Float,
    val doubleSided: Boolean
) {
    val isTransparent: Boolean get() = alpha < 0.999f
    val usesTransparentMaterial: Boolean get() = isTransparent
}

object ArGreenhouseVisuals {
    fun style(kind: MeshKind): ArSurfaceStyle {
        val look = GreenhouseSurfaceLooks.of(kind)
        return ArSurfaceStyle(
            red = look.red,
            green = look.green,
            blue = look.blue,
            alpha = look.alpha,
            metallic = look.metallic,
            roughness = look.roughness,
            reflectance = 0.5f,
            doubleSided = look.doubleSided
        )
    }
}

/**
 * Clamps ARCore light-estimation multipliers so PBR materials never go black.
 * SceneView 2.2.1 multiplies the same directional light each frame; callers must
 * re-apply [visibleIntensityLux] from this baseline rather than compounding.
 */
object ArTabletopLighting {
    const val FALLBACK_INTENSITY_LUX = 100_000f
    const val MIN_ESTIMATION_FACTOR = 0.35f
    const val MAX_ESTIMATION_FACTOR = 1.8f
    const val NEUTRAL_IBL_ASSET = "environments/neutral/neutral_ibl.ktx"

    fun visibleIntensityLux(estimatedFactor: Float?): Float {
        val factor = estimatedFactor
            ?.takeIf { it.isFinite() && it > 0f }
            ?.coerceIn(MIN_ESTIMATION_FACTOR, MAX_ESTIMATION_FACTOR)
            ?: 1f
        return FALLBACK_INTENSITY_LUX * factor
    }

    fun keepEstimatedDirection(directionY: Float): Boolean =
        directionY.isFinite() && directionY <= -0.15f
}
