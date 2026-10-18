package com.greenhands.app.sensor.ar

import com.greenhands.app.sensor.model.GreenhousePhysicalConfig
import kotlin.math.max
import kotlin.math.min

/**
 * Pure Real AR scan / placement-area guidance (development & research testing).
 *
 * The physical environment is only an AR placement surface — the app does **not**
 * automatically recognize a real greenhouse. Configured dimensions from Setup /
 * [ArVisualizationSnapshot.physical] remain the source of truth.
 */
object RealArScanGuidance {

    /**
     * True when the configured footprint cannot fit on the detected plane extents
     * even with best-effort axis alignment (length along the longer plane side).
     * Does **not** resize the greenhouse — callers only show a warning.
     */
    fun configuredGreenhouseExceedsDetectedArea(
        physical: GreenhousePhysicalConfig,
        planeExtentXMeters: Float,
        planeExtentZMeters: Float
    ): Boolean {
        if (planeExtentXMeters <= 0f || planeExtentZMeters <= 0f) return false
        val length = physical.lengthMeters.toFloat()
        val width = physical.widthMeters.toFloat()
        val maxPlane = max(planeExtentXMeters, planeExtentZMeters)
        val minPlane = min(planeExtentXMeters, planeExtentZMeters)
        val fits =
            (length <= maxPlane && width <= minPlane) ||
                (width <= maxPlane && length <= minPlane)
        return !fits
    }

    fun instructionPhaseKey(phase: ArOriginPlacementPhase): String = when (phase) {
        ArOriginPlacementPhase.SCANNING -> "scan"
        ArOriginPlacementPhase.PLANE_FOUND -> "use_area"
        ArOriginPlacementPhase.ORIGIN_PLACED -> "origin_placed"
        ArOriginPlacementPhase.SETTING_DIRECTION -> "set_direction"
        ArOriginPlacementPhase.ALIGNED -> "aligned"
    }

    /** Research-safe copy keys — UI maps these to string resources. */
    fun noticeIsManualPlacement(): Boolean = true
}
