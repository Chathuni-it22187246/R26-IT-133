package com.greenhands.app.ar.domain

import com.greenhands.app.equipment.model.EquipmentType
import com.greenhands.app.preview3d.model.GreenhouseDimensions
import com.greenhands.app.preview3d.model.PreviewMode
import com.greenhands.app.preview3d.model.formatMetres

/**
 * Compact AR chrome slots in logical pixels. Camera viewport keeps the majority of height.
 */
object ArOverlayLayout {
    const val HEADER_PORTRAIT_DP = 56f
    const val HEADER_LANDSCAPE_DP = 48f
    const val BOTTOM_CHROME_DP = 48f
    const val PALETTE_DP = 0f
    const val SELECTED_NAME_DP = 20f
    const val ACTION_ROW_HEIGHT_DP = 40f
    const val SELECTED_BAR_DP = 40f
    const val SELECTED_SHEET_DP = 148f
    const val MIN_VIEWPORT_DP = 280f
    const val INSET_DP = 8f
    const val EXACT_POSITION_MIN_WIDTH_DP = 112f
    const val DELETE_MIN_WIDTH_DP = 64f
    const val CHROME_ROW_HEIGHT_DP = 40f
    const val SUGGESTION_PORTRAIT_WIDTH_DP = 108f
    const val SUGGESTION_PORTRAIT_HEIGHT_DP = 40f
    const val SUGGESTION_LANDSCAPE_WIDTH_DP = 108f
    const val SUGGESTION_LANDSCAPE_HEIGHT_DP = 40f
    const val STATUS_HUD_WIDTH_DP = 196f
    const val STATUS_HUD_HEIGHT_DP = 40f
    const val STATUS_SUMMARY_WIDTH_DP = 248f
    const val STATUS_SUMMARY_HEIGHT_DP = 40f
    const val STATUS_HUD_COLLAPSED_WIDTH_DP = 240f
    const val STATUS_HUD_COLLAPSED_HEIGHT_DP = 40f
    const val STATUS_HUD_CHIP_HEIGHT_DP = 40f
    const val STATUS_HUD_MIN_WIDTH_DP = 120f
    const val CROWDED_VIEWPORT_HEIGHT_DP = 160f

    data class Rect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        fun overlaps(other: Rect): Boolean =
            left < other.right && right > other.left && top < other.bottom && bottom > other.top

        fun area(): Float = (right - left).coerceAtLeast(0f) * (bottom - top).coerceAtLeast(0f)
        val height: Float get() = (bottom - top).coerceAtLeast(0f)
        val width: Float get() = (right - left).coerceAtLeast(0f)
    }

    data class OverlaySlots(
        val header: Rect,
        val viewport: Rect,
        val chrome: Rect,
        val palette: Rect,
        val suggestions: Rect = Rect(0f, 0f, 0f, 0f),
        val statusHud: Rect = Rect(0f, 0f, 0f, 0f)
    ) {
        fun overlappingPairs(): List<Pair<String, String>> {
            val named = listOf(
                "header" to header,
                "viewport" to viewport,
                "chrome" to chrome,
                "palette" to palette
            ).filter { it.second.area() > 0f }
            val overlaps = ArrayList<Pair<String, String>>()
            for (i in named.indices) {
                for (j in i + 1 until named.size) {
                    if (named[i].second.overlaps(named[j].second)) {
                        overlaps += named[i].first to named[j].first
                    }
                }
            }
            return overlaps
        }

        fun suggestionConflicts(): List<String> {
            if (suggestions.area() <= 0f) return emptyList()
            val conflicts = ArrayList<String>()
            if (header.area() > 0f && suggestions.overlaps(header)) conflicts += "header"
            if (chrome.area() > 0f && suggestions.overlaps(chrome)) conflicts += "chrome"
            if (palette.area() > 0f && suggestions.overlaps(palette)) conflicts += "palette"
            return conflicts
        }

        fun statusHudConflicts(): List<String> {
            if (statusHud.area() <= 0f) return emptyList()
            val conflicts = ArrayList<String>()
            if (header.area() > 0f && statusHud.overlaps(header)) conflicts += "header"
            if (chrome.area() > 0f && statusHud.overlaps(chrome)) conflicts += "chrome"
            if (palette.area() > 0f && statusHud.overlaps(palette)) conflicts += "palette"
            if (suggestions.area() > 0f && statusHud.overlaps(suggestions)) conflicts += "suggestions"
            return conflicts
        }
    }

    fun compactPortrait(
        width: Float,
        height: Float,
        placed: Boolean
    ): OverlaySlots = compact(width, height, placed, landscape = false)

    fun compactLandscape(
        width: Float,
        height: Float,
        placed: Boolean
    ): OverlaySlots = compact(width, height, placed, landscape = true)

    fun compact(
        width: Float,
        height: Float,
        placed: Boolean,
        landscape: Boolean
    ): OverlaySlots {
        val headerH = if (landscape) HEADER_LANDSCAPE_DP else HEADER_PORTRAIT_DP
        val paletteH = if (placed) PALETTE_DP else 0f
        val chromeH = if (placed) BOTTOM_CHROME_DP else 0f
        val header = Rect(0f, 0f, width, headerH)
        val chromeTop = (height - paletteH - chromeH).coerceAtLeast(headerH)
        val chrome = Rect(0f, chromeTop, width, chromeTop + chromeH)
        val palette = Rect(0f, chrome.bottom, width, height)
        val viewport = Rect(0f, header.bottom, width, chrome.top.coerceAtLeast(header.bottom))
        val suggestions = if (placed) suggestionRail(viewport, landscape) else Rect(0f, 0f, 0f, 0f)
        val statusHud = if (placed) statusHudRect(viewport, landscape, selectedVisible = false) else Rect(0f, 0f, 0f, 0f)
        return OverlaySlots(header, viewport, chrome, palette, suggestions, statusHud)
    }

    fun statusHudCrowded(viewport: Rect, landscape: Boolean): Boolean =
        landscape || viewport.height < CROWDED_VIEWPORT_HEIGHT_DP

    fun statusHudRect(
        viewport: Rect,
        landscape: Boolean,
        selectedVisible: Boolean = false
    ): Rect {
        val rail = suggestionRail(viewport, landscape)
        val barTop = if (selectedVisible) selectedBar(viewport, landscape).top else viewport.bottom
        val left = viewport.left + INSET_DP
        val top = viewport.top + INSET_DP
        val maxRight = (rail.left - INSET_DP).coerceAtLeast(left + STATUS_HUD_MIN_WIDTH_DP)
        val maxBottom = (barTop - INSET_DP).coerceAtLeast(top)
        val crowded = statusHudCrowded(viewport, landscape)
        val wantHeight = STATUS_SUMMARY_HEIGHT_DP
        val wantWidth = if (crowded) {
            STATUS_HUD_COLLAPSED_WIDTH_DP
        } else {
            STATUS_SUMMARY_WIDTH_DP
        }
        val width = wantWidth.coerceAtMost((maxRight - left).coerceAtLeast(0f))
        val height = wantHeight.coerceAtMost((maxBottom - top).coerceAtLeast(0f))
        return Rect(left, top, left + width, top + height)
    }

    fun suggestionRail(viewport: Rect, landscape: Boolean): Rect {
        return if (landscape) {
            val width = SUGGESTION_LANDSCAPE_WIDTH_DP.coerceAtMost(viewport.width - INSET_DP * 2f)
            val height = SUGGESTION_LANDSCAPE_HEIGHT_DP.coerceAtMost(viewport.height - INSET_DP)
            val right = viewport.right - INSET_DP
            val left = (right - width).coerceAtLeast(viewport.left + INSET_DP)
            val top = viewport.top + INSET_DP
            Rect(left, top, right, top + height)
        } else {
            val width = SUGGESTION_PORTRAIT_WIDTH_DP.coerceAtMost(viewport.width - INSET_DP * 2f)
            val maxBottom = (viewport.bottom - SELECTED_BAR_DP - INSET_DP)
                .coerceAtLeast(viewport.top + INSET_DP)
            val height = SUGGESTION_PORTRAIT_HEIGHT_DP
                .coerceAtMost((maxBottom - (viewport.top + INSET_DP)).coerceAtLeast(0f))
            val right = viewport.right - INSET_DP
            val left = (right - width).coerceAtLeast(viewport.left + INSET_DP)
            val top = viewport.top + INSET_DP
            Rect(left, top, right, top + height)
        }
    }

    fun selectedSheetBar(width: Float): Rect =
        Rect(0f, 0f, width, SELECTED_SHEET_DP)

    fun selectedBar(viewport: Rect, landscape: Boolean = false): Rect {
        val rail = suggestionRail(viewport, landscape)
        val bottom = viewport.bottom - INSET_DP
        return if (landscape) {
            val top = (rail.bottom + INSET_DP)
                .coerceAtMost(bottom)
                .let { railClear -> maxOf(railClear, bottom - SELECTED_BAR_DP) }
                .coerceAtLeast(viewport.top + INSET_DP)
            Rect(viewport.left + INSET_DP, top.coerceAtMost(bottom), viewport.right - INSET_DP, bottom)
        } else {
            val top = (bottom - SELECTED_BAR_DP).coerceAtLeast(viewport.top + INSET_DP)
            Rect(viewport.left + INSET_DP, top, viewport.right - INSET_DP, bottom)
        }
    }

    fun labelViewport(viewport: Rect, selectedVisible: Boolean, landscape: Boolean = false): Rect {
        if (!selectedVisible) return viewport
        val bar = selectedBar(viewport, landscape)
        return Rect(viewport.left, viewport.top, viewport.right, bar.top)
    }

    fun selectedNameRow(bar: Rect): Rect =
        Rect(bar.left, bar.top, bar.right, bar.top + SELECTED_NAME_DP)

    fun selectedActionRow1(bar: Rect): Rect {
        val top = bar.top + SELECTED_NAME_DP
        return Rect(bar.left, top, bar.right, top + ACTION_ROW_HEIGHT_DP)
    }

    fun selectedActionRow2(bar: Rect): Rect {
        val top = selectedActionRow1(bar).bottom
        return Rect(bar.left, top, bar.right, top + ACTION_ROW_HEIGHT_DP)
    }

    fun selectedActionRow3(bar: Rect): Rect {
        val top = selectedActionRow2(bar).bottom
        return Rect(bar.left, top, bar.right, top + ACTION_ROW_HEIGHT_DP)
    }

    fun deleteButton(row2: Rect): Rect {
        val mid = (row2.left + row2.right) * 0.5f
        return Rect(row2.left, row2.top, mid, row2.bottom)
    }

    fun exactPositionButton(row2: Rect): Rect {
        val mid = (row2.left + row2.right) * 0.5f
        return Rect(mid, row2.top, row2.right, row2.bottom)
    }

    fun chromeRow(chrome: Rect, index: Int): Rect {
        val top = chrome.top + INSET_DP + index * CHROME_ROW_HEIGHT_DP
        return Rect(chrome.left + INSET_DP, top, chrome.right - INSET_DP, top + CHROME_ROW_HEIGHT_DP)
    }

    fun equalButtons(row: Rect, count: Int): List<Rect> {
        if (count <= 0) return emptyList()
        val width = row.width / count
        return (0 until count).map { i ->
            Rect(row.left + i * width, row.top, row.left + (i + 1) * width, row.bottom)
        }
    }
}

object ArEquipmentStatusCopy {
    const val NOT_RUNNING = "Not running"
    const val SAVED_LAYOUT = "Saved layout"
    const val UNSAVED_WORKING_COPY = "Unsaved working copy"

    fun savedStateLabel(saved: Boolean): String = if (saved) SAVED_LAYOUT else UNSAVED_WORKING_COPY

    fun typeCountLine(type: EquipmentType, count: Int): String {
        val title = when (type) {
            EquipmentType.CIRCULATION_FAN -> "Circulation Fan"
            EquipmentType.EXHAUST_FAN -> "Exhaust Fan"
            EquipmentType.FOGGER -> "Fogger"
        }
        return "$title: $count"
    }
}

/**
 * Portrait AR header copy. Non-breaking spaces keep the unit "m" on the same line as its number.
 */
object ArHeaderCopy {
    const val NBSP = '\u00A0'

    fun dimensionsLine(dimensions: GreenhouseDimensions): String {
        val m = "${NBSP}m"
        return "${formatMetres(dimensions.lengthM)}$m × ${formatMetres(dimensions.widthM)}$m × ${formatMetres(dimensions.heightM)}$m"
    }

    fun compactSubtitle(
        mode: PreviewMode,
        dimensions: GreenhouseDimensions,
        cropRowCount: Int
    ): String {
        val dims = dimensionsLine(dimensions)
        val rows = "$cropRowCount crop rows"
        return if (mode == PreviewMode.DEMO) {
            "Demo · $dims · $rows"
        } else {
            "$dims · $rows"
        }
    }

    fun unitCanWrapAlone(text: String): Boolean {
        val trimmed = text.trimEnd()
        if (trimmed.endsWith("${NBSP}m")) return false
        val lastToken = trimmed.substringAfterLast(' ', missingDelimiterValue = trimmed)
        return lastToken == "m"
    }
}
