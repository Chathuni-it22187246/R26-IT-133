package com.greenhands.app.sensor.ar

/**
 * Real AR uses greenhouse-local meters at 1:1 world scale.
 *
 * One SceneView/Filament unit = one physical meter. Virtual Greenhouse Preview may
 * use screen projection scale; Real AR must never apply tabletop shrink factors such as
 * `0.30f / max(length, width)` on the AR root or child nodes.
 */
object ArRealScale {

    const val METERS_PER_UNIT = 1f

    /** Root transform scale for the shared greenhouse AR anchor (identity). */
    fun rootScale(): Float = METERS_PER_UNIT

    fun localMetersToSceneUnits(meters: Float): Float = meters * METERS_PER_UNIT

    fun isRealWorldMeters(): Boolean = METERS_PER_UNIT == 1f
}
