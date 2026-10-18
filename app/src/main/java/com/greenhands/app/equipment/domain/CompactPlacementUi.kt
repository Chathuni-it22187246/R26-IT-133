package com.greenhands.app.equipment.domain

import com.greenhands.app.equipment.model.formatMetres2

/**
 * Compact live coordinates for the selected/moving equipment label.
 * Long axis descriptions belong only in the viewport guide and Exact editor.
 */
object CompactLiveCoordinates {
    fun xyz(xM: Double, yM: Double, zM: Double): String =
        "X ${formatMetres2(xM)} m · Y ${formatMetres2(yM)} m · Z ${formatMetres2(zM)} m"

    fun containsLongAxisDescriptions(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("width position") ||
            lower.contains("length position") ||
            lower.contains("height position")
    }
}

/**
 * Single greenhouse-fixed position guide. Not attached to individual equipment.
 */
object PlacementCoordinateGuide {
    const val TITLE = "Position guide"
    const val LINE_X = "X: Left → Right (Width)"
    const val LINE_Y = "Y: Front → Back (Length)"
    const val LINE_Z = "Z: Floor (0 m) → Roof (Height)"
    const val COLLAPSED_LABEL = "XYZ"
}
