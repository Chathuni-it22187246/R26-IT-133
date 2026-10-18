package com.greenhands.app.preview3d.domain

/**
 * Engine-independent greenhouse surface recipes shared by the 3D preview and AR.
 * Colours match the accepted Phase 3B.1/3B.2 preview. [PANEL] alpha is below 1 so
 * SceneView 2.2.1 selects `transparent_colored.filamat` rather than an opaque tint.
 */
data class GreenhouseSurfaceLook(
    val red: Float,
    val green: Float,
    val blue: Float,
    val alpha: Float,
    val metallic: Float,
    val roughness: Float,
    val doubleSided: Boolean = false
) {
    val usesTransparentMaterial: Boolean get() = alpha < 1f - 1e-5f
}

object GreenhouseSurfaceLooks {
    val ground = GreenhouseSurfaceLook(0.18f, 0.24f, 0.18f, 1f, 0.0f, 0.95f)
    val floor = GreenhouseSurfaceLook(0.46f, 0.38f, 0.28f, 1f, 0.0f, 0.90f)
    val frame = GreenhouseSurfaceLook(0.62f, 0.64f, 0.66f, 1f, 0.35f, 0.40f)
    val panel = GreenhouseSurfaceLook(
        red = 0.55f, green = 0.78f, blue = 0.86f, alpha = 0.28f,
        metallic = 0.0f, roughness = 0.20f, doubleSided = true
    )
    val crop = GreenhouseSurfaceLook(0.22f, 0.55f, 0.30f, 1f, 0.0f, 0.85f)

    fun of(kind: MeshKind): GreenhouseSurfaceLook = when (kind) {
        MeshKind.GROUND -> ground
        MeshKind.FLOOR -> floor
        MeshKind.FRAME -> frame
        MeshKind.PANEL -> panel
        MeshKind.CROP -> crop
    }
}

/**
 * Canonical render parts: the same [GreenhouseMeshBuilder] boxes the 3D preview uses.
 */
object GreenhouseRenderParts {
    fun of(layout: SceneLayout): List<MeshBox> = GreenhouseMeshBuilder.build(layout)
}
