package com.greenhands.app.preview3d.ui

/**
 * Measured stacking for the Predicted Preview chrome. Values are logical pixels, not
 * hard-coded screen coordinates in the Compose UI.
 */
object PreviewOverlayLayout {
    const val WIDE_LANDSCAPE_DP = 700f
    const val PORTRAIT_TOOLBAR_DP = 136f
    const val PORTRAIT_PALETTE_DP = 100f
    const val PORTRAIT_MIN_VIEWPORT_DP = 360f
    const val PORTRAIT_STATUS_SHEET_MAX_DP = 280f
    const val SUGGESTION_RAIL_WIDTH_DP = 92f
    const val MODE_ROW_HEIGHT_DP = 40f
    const val ACTION_ROW_HEIGHT_DP = 40f
    const val EXACT_POSITION_MIN_WIDTH_DP = 112f
    const val DONE_MIN_WIDTH_DP = 64f
    const val RESET_VIEW_MIN_WIDTH_DP = 96f
    const val COORD_GUIDE_WIDTH_DP = 168f
    const val COORD_GUIDE_HEIGHT_DP = 88f
    const val COORD_GUIDE_COLLAPSED_DP = 40f
    const val SELECTED_BAR_HEIGHT_DP = 96f
    const val OVERLAY_INSET_DP = 8f

    data class Rect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        fun overlaps(other: Rect): Boolean =
            left < other.right && right > other.left && top < other.bottom && bottom > other.top

        fun area(): Float = (right - left).coerceAtLeast(0f) * (bottom - top).coerceAtLeast(0f)
    }

    fun isWideLandscape(widthDp: Float, landscape: Boolean): Boolean =
        landscape && widthDp >= WIDE_LANDSCAPE_DP

    fun compactPortrait(
        width: Float,
        height: Float,
        headerHeight: Float,
        cropRowHeight: Float,
        chromeHeight: Float
    ): OverlaySlots {
        val header = Rect(0f, 0f, width, headerHeight)
        val crop = Rect(0f, headerHeight, width, headerHeight + cropRowHeight)
        val chromeTop = (height - chromeHeight).coerceAtLeast(headerHeight + cropRowHeight)
        val chrome = Rect(0f, chromeTop, width, height)
        val viewport = Rect(0f, headerHeight + cropRowHeight, width, chromeTop)
        return OverlaySlots(header, crop, viewport, chrome, sidePanel = null)
    }

    /**
     * Pixel-class portrait Placement Mode: SceneView keeps the majority of the height.
     * Palette and toolbar are bounded strips. Equipment Status is an overlay sheet
     * and must not reduce [OverlaySlots.viewport].
     */
    fun compactPortraitPlacement(
        width: Float,
        height: Float,
        headerHeight: Float,
        cropRowHeight: Float
    ): OverlaySlots {
        val chromeHeight = PORTRAIT_TOOLBAR_DP + PORTRAIT_PALETTE_DP
        return compactPortrait(width, height, headerHeight, cropRowHeight, chromeHeight)
    }

    fun suggestionRail(viewport: Rect): Rect =
        Rect(
            (viewport.right - SUGGESTION_RAIL_WIDTH_DP).coerceAtLeast(viewport.left),
            viewport.top,
            viewport.right,
            viewport.bottom
        )

    fun paletteStrip(width: Float, height: Float): Rect =
        Rect(0f, height - PORTRAIT_PALETTE_DP, width, height)

    fun landscapeWide(
        width: Float,
        height: Float,
        headerHeight: Float,
        cropRowHeight: Float,
        sidePanelWidth: Float
    ): OverlaySlots {
        val header = Rect(0f, 0f, width, headerHeight)
        val crop = Rect(0f, headerHeight, width, headerHeight + cropRowHeight)
        val contentTop = headerHeight + cropRowHeight
        val side = Rect(width - sidePanelWidth, contentTop, width, height)
        val viewport = Rect(0f, contentTop, width - sidePanelWidth, height)
        return OverlaySlots(header, crop, viewport, chrome = side, sidePanel = side)
    }

    data class OverlaySlots(
        val header: Rect,
        val cropRow: Rect,
        val viewport: Rect,
        val chrome: Rect,
        val sidePanel: Rect?
    ) {
        fun overlappingPairs(): List<Pair<String, String>> {
            val named = listOf(
                "header" to header,
                "cropRow" to cropRow,
                "viewport" to viewport,
                "chrome" to chrome
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
    }

    fun placementModeRow1(width: Float, top: Float): Rect =
        Rect(0f, top, width, top + MODE_ROW_HEIGHT_DP)

    fun placementModeRow2(width: Float, top: Float): Rect =
        Rect(0f, top + MODE_ROW_HEIGHT_DP, width, top + MODE_ROW_HEIGHT_DP * 2f)

    fun doneButton(row2: Rect): Rect {
        val right = row2.right - OVERLAY_INSET_DP
        val left = (right - DONE_MIN_WIDTH_DP).coerceAtLeast(row2.left + OVERLAY_INSET_DP)
        return Rect(left, row2.top, right, row2.bottom)
    }

    fun resetViewButton(row2: Rect): Rect {
        val done = doneButton(row2)
        val right = done.left - OVERLAY_INSET_DP
        val left = (right - RESET_VIEW_MIN_WIDTH_DP).coerceAtLeast(row2.left + OVERLAY_INSET_DP)
        return Rect(left, row2.top, right, row2.bottom)
    }

    fun selectedActionRow1(bar: Rect): Rect =
        Rect(bar.left, bar.top, bar.right, bar.top + ACTION_ROW_HEIGHT_DP)

    fun selectedActionRow2(bar: Rect): Rect =
        Rect(bar.left, bar.top + ACTION_ROW_HEIGHT_DP, bar.right, bar.top + ACTION_ROW_HEIGHT_DP * 2f)

    fun exactPositionButton(row2: Rect): Rect {
        val right = row2.right - OVERLAY_INSET_DP
        val left = (right - EXACT_POSITION_MIN_WIDTH_DP).coerceAtLeast(row2.left)
        return Rect(left, row2.top, right, row2.bottom)
    }

    fun selectedActionBar(viewport: Rect): Rect {
        val rail = suggestionRail(viewport)
        val right = (rail.left - OVERLAY_INSET_DP).coerceAtLeast(viewport.left + OVERLAY_INSET_DP + EXACT_POSITION_MIN_WIDTH_DP)
        val bottom = viewport.bottom - OVERLAY_INSET_DP
        val top = (bottom - SELECTED_BAR_HEIGHT_DP).coerceAtLeast(viewport.top + OVERLAY_INSET_DP)
        return Rect(viewport.left + OVERLAY_INSET_DP, top, right, bottom)
    }

    fun coordinateGuide(viewport: Rect, collapsed: Boolean): Rect {
        val rail = suggestionRail(viewport)
        val width = if (collapsed) COORD_GUIDE_COLLAPSED_DP else COORD_GUIDE_WIDTH_DP
        val height = if (collapsed) COORD_GUIDE_COLLAPSED_DP else COORD_GUIDE_HEIGHT_DP
        val left = viewport.left + OVERLAY_INSET_DP
        val top = viewport.top + OVERLAY_INSET_DP
        val right = (left + width).coerceAtMost(rail.left - OVERLAY_INSET_DP)
        return Rect(left, top, right.coerceAtLeast(left + COORD_GUIDE_COLLAPSED_DP), top + height)
    }

    fun coordinateGuideCollapsed(viewport: Rect): Boolean {
        val expanded = coordinateGuide(viewport, collapsed = false)
        val rail = suggestionRail(viewport)
        val bar = selectedActionBar(viewport)
        return expanded.overlaps(rail) ||
            expanded.overlaps(bar) ||
            viewport.right - viewport.left < 360f ||
            viewport.bottom - viewport.top < 280f
    }
}
