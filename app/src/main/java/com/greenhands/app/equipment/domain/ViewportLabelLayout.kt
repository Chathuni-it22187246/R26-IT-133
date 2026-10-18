package com.greenhands.app.equipment.domain

import com.greenhands.app.equipment.model.EquipmentInstance

data class EquipmentLabelContent(
    val id: String,
    val name: String,
    val showCoordinates: Boolean,
    val xM: Double,
    val yM: Double,
    val zM: Double,
    val selected: Boolean
)

data class EquipmentScreenAnchor(
    val id: String,
    val xPx: Float,
    val yPx: Float,
    val viewportWidthPx: Int,
    val viewportHeightPx: Int
)

data class PlacedViewportLabel(
    val id: String,
    val xPx: Double,
    val yPx: Double,
    val widthPx: Double,
    val heightPx: Double,
    val selected: Boolean
)

/**
 * Builds per-instance viewport label content. Non-selected items are name-only;
 * the selected or moving instance also carries live X/Y/Z.
 */
object EquipmentLabelCatalog {
    fun contents(
        instances: List<EquipmentInstance>,
        selectedId: String?,
        draggingId: String? = null,
        labelsVisible: Boolean = true
    ): List<EquipmentLabelContent> {
        val highlightId = draggingId ?: selectedId
        return instances.mapNotNull { inst ->
            val selected = inst.id == highlightId
            if (!labelsVisible && !selected) return@mapNotNull null
            EquipmentLabelContent(
                id = inst.id,
                name = inst.displayLabel(),
                showCoordinates = selected,
                xM = inst.positionM.xM,
                yM = inst.positionM.yM,
                zM = inst.positionM.zM,
                selected = selected
            )
        }
    }
}

/**
 * Places name labels above projected model points, clamps them to the viewport,
 * and nudges them apart when they would overlap.
 */
object ViewportLabelLayout {
    data class Input(
        val id: String,
        val anchorXPx: Double,
        val anchorYPx: Double,
        val widthPx: Double,
        val heightPx: Double,
        val selected: Boolean
    )

    fun place(
        inputs: List<Input>,
        viewportWidthPx: Double,
        viewportHeightPx: Double,
        padPx: Double,
        liftPx: Double,
        gapPx: Double = 4.0
    ): List<PlacedViewportLabel> {
        if (viewportWidthPx <= 0.0 || viewportHeightPx <= 0.0) return emptyList()
        val ordered = inputs.sortedWith(compareByDescending<Input> { it.selected }.thenBy { it.anchorYPx }.thenBy { it.anchorXPx })
        val placed = ArrayList<PlacedViewportLabel>(ordered.size)
        ordered.forEach { item ->
            var x = item.anchorXPx - item.widthPx * 0.5
            var y = item.anchorYPx - item.heightPx - liftPx
            val clamped = clampBox(x, y, item.widthPx, item.heightPx, viewportWidthPx, viewportHeightPx, padPx)
            x = clamped.first
            y = clamped.second
            var attempt = 0
            while (attempt < 10 && placed.any { overlaps(x, y, item.widthPx, item.heightPx, it, gapPx) }) {
                val step = item.heightPx + gapPx
                val candidateYs = listOf(y + step, y - step, y)
                val candidateXs = listOf(x, x + item.widthPx * 0.35, x - item.widthPx * 0.35)
                var found = false
                for (ny in candidateYs) {
                    for (nx in candidateXs) {
                        val next = clampBox(nx, ny, item.widthPx, item.heightPx, viewportWidthPx, viewportHeightPx, padPx)
                        if (!placed.any { overlaps(next.first, next.second, item.widthPx, item.heightPx, it, gapPx) }) {
                            x = next.first
                            y = next.second
                            found = true
                            break
                        }
                    }
                    if (found) break
                }
                if (!found) {
                    y += step
                    val next = clampBox(x, y, item.widthPx, item.heightPx, viewportWidthPx, viewportHeightPx, padPx)
                    x = next.first
                    y = next.second
                }
                attempt++
            }
            placed += PlacedViewportLabel(item.id, x, y, item.widthPx, item.heightPx, item.selected)
        }
        return placed
    }

    fun signature(anchors: List<EquipmentScreenAnchor>, selectedId: String?): String =
        buildString {
            append(selectedId ?: "-")
            append('|')
            anchors.forEach { a ->
                append(a.id)
                append(':')
                append(a.xPx.toInt())
                append(':')
                append(a.yPx.toInt())
                append(';')
            }
        }

    private fun clampBox(
        x: Double,
        y: Double,
        w: Double,
        h: Double,
        viewportW: Double,
        viewportH: Double,
        pad: Double
    ): Pair<Double, Double> {
        val maxX = (viewportW - w - pad).coerceAtLeast(pad)
        val maxY = (viewportH - h - pad).coerceAtLeast(pad)
        return x.coerceIn(pad, maxX) to y.coerceIn(pad, maxY)
    }

    private fun overlaps(
        x: Double,
        y: Double,
        w: Double,
        h: Double,
        other: PlacedViewportLabel,
        gap: Double
    ): Boolean {
        return x < other.xPx + other.widthPx + gap &&
            x + w + gap > other.xPx &&
            y < other.yPx + other.heightPx + gap &&
            y + h + gap > other.yPx
    }
}
