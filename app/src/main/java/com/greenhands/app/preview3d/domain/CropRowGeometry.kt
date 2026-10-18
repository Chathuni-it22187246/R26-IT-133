package com.greenhands.app.preview3d.domain

/**
 * Visual crop-row placeholders for the 3D preview.
 *
 * PREVIEW_ASSUMPTION: crop-row count and placement are visual placeholders for this
 * preview only. They are not a validated agronomic layout and are not a Phase 3A
 * physics or simulation-grid value.
 */
object CropRowGeometry {
    const val SIDE_MARGIN_FRACTION = 0.06
    const val SLOT_FILL = 0.72
    const val LENGTH_FILL = 0.72
    const val HEIGHT_FILL = 0.26

    /**
     * Crop-row AABBs in the user-facing placement frame (X width, Y length, Z up).
     * Same placeholder fractions as the visual mesh. PREVIEW_ASSUMPTION.
     */
    fun domainBoxes(
        lengthM: Double,
        widthM: Double,
        heightM: Double,
        rowCount: Int
    ): List<DomainCropRowBox> {
        val rows = rowCount.coerceIn(1, 20)
        val margin = widthM * SIDE_MARGIN_FRACTION
        val available = (widthM - 2.0 * margin).coerceAtLeast(0.0)
        val slot = if (rows <= 0) 0.0 else available / rows
        val rowWidth = slot * SLOT_FILL
        val rowLength = lengthM * LENGTH_FILL
        val rowHeight = heightM * HEIGHT_FILL
        val lengthStart = (lengthM - rowLength) * 0.5
        val widthStart = margin
        return (0 until rows).map { index ->
            val x0 = widthStart + slot * index + (slot - rowWidth) * 0.5
            DomainCropRowBox(
                id = "crop_$index",
                minXM = x0,
                maxXM = x0 + rowWidth,
                minYM = lengthStart,
                maxYM = lengthStart + rowLength,
                minZM = 0.0,
                maxZM = rowHeight
            )
        }
    }

    fun boxes(layout: SceneLayout): List<MeshBox> {
        val rowCount = layout.cropRowCount.coerceIn(1, 20)
        val length = layout.extentForLength
        val width = layout.extentForWidth
        val height = layout.extentY
        val margin = width * SIDE_MARGIN_FRACTION
        val available = (width - 2.0 * margin).coerceAtLeast(0.0)
        val slot = if (rowCount <= 0) 0.0 else available / rowCount
        val rowWidth = slot * SLOT_FILL
        val rowLength = length * LENGTH_FILL
        val rowHeight = height * HEIGHT_FILL
        val widthStart = -width * 0.5 + margin
        return (0 until rowCount).map { index ->
            val across = widthStart + slot * (index + 0.5)
            val along = 0.0
            val centerX: Double
            val centerZ: Double
            val sizeX: Double
            val sizeZ: Double
            if (layout.lengthMappedToX) {
                centerX = along
                centerZ = across
                sizeX = rowLength
                sizeZ = rowWidth
            } else {
                centerX = across
                centerZ = along
                sizeX = rowWidth
                sizeZ = rowLength
            }
            MeshBox(
                id = "crop_$index",
                kind = MeshKind.CROP,
                centerX = centerX,
                centerY = rowHeight * 0.5,
                centerZ = centerZ,
                sizeX = sizeX,
                sizeY = rowHeight,
                sizeZ = sizeZ
            )
        }
    }
}

data class DomainCropRowBox(
    val id: String,
    val minXM: Double,
    val maxXM: Double,
    val minYM: Double,
    val maxYM: Double,
    val minZM: Double,
    val maxZM: Double
)
