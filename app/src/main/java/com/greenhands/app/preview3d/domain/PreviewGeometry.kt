package com.greenhands.app.preview3d.domain

import com.greenhands.app.heat.domain.FieldValidation
import com.greenhands.app.heat.domain.TargetParser
import com.greenhands.app.preview3d.model.GreenhouseDimensions
import kotlin.math.max
import kotlin.math.min

/**
 * Software guardrails for the Phase 3B.1 preview form. Not universal greenhouse limits
 * and not Phase 3A geometry constraints.
 */
object DimensionValidation {
    const val LENGTH_MIN_M = 2.0
    const val LENGTH_MAX_M = 100.0
    const val WIDTH_MIN_M = 2.0
    const val WIDTH_MAX_M = 50.0
    const val HEIGHT_MIN_M = 2.0
    const val HEIGHT_MAX_M = 10.0

    fun parseFinite(raw: String): Double? {
        val value = TargetParser.parseDecimal(raw) ?: return null
        return if (value.isFinite()) value else null
    }

    fun length(raw: String): FieldValidation = validate(raw, "Length", LENGTH_MIN_M, LENGTH_MAX_M)

    fun width(raw: String): FieldValidation = validate(raw, "Width", WIDTH_MIN_M, WIDTH_MAX_M)

    fun height(raw: String): FieldValidation = validate(raw, "Height", HEIGHT_MIN_M, HEIGHT_MAX_M)

    fun allValid(lengthM: Double, widthM: Double, heightM: Double): Boolean =
        inRange(lengthM, LENGTH_MIN_M, LENGTH_MAX_M) &&
            inRange(widthM, WIDTH_MIN_M, WIDTH_MAX_M) &&
            inRange(heightM, HEIGHT_MIN_M, HEIGHT_MAX_M)

    private fun inRange(value: Double, min: Double, max: Double): Boolean =
        value.isFinite() && value > 0.0 && value >= min && value <= max

    private fun validate(raw: String, name: String, minM: Double, maxM: Double): FieldValidation {
        if (raw.isBlank()) return FieldValidation(error = "Enter a ${name.lowercase()}")
        val parsed = TargetParser.parseDecimal(raw)
            ?: return FieldValidation(error = "Enter a valid number")
        if (!parsed.isFinite()) {
            return FieldValidation(error = "Enter a finite number")
        }
        if (parsed <= 0.0) {
            return FieldValidation(
                value = parsed,
                error = "$name must be greater than 0 m"
            )
        }
        if (parsed < minM || parsed > maxM) {
            return FieldValidation(
                value = parsed,
                error = "$name must be between ${trimNumber(minM)} and ${trimNumber(maxM)} m"
            )
        }
        return FieldValidation(value = parsed)
    }

    private fun trimNumber(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
}

/**
 * Uniform scene scaling: every metre maps to the same scene-unit length.
 * The longest horizontal envelope edge is mapped to scene +X.
 */
data class SceneLayout(
    val dimensions: GreenhouseDimensions,
    val uniformScale: Double,
    val extentX: Double,
    val extentY: Double,
    val extentZ: Double,
    val lengthMappedToX: Boolean,
    val targetLongSpan: Double,
    val cropRowCount: Int = 3
) {
    val extentForLength: Double get() = if (lengthMappedToX) extentX else extentZ
    val extentForWidth: Double get() = if (lengthMappedToX) extentZ else extentX

    fun scaleAlongLength(): Double = extentForLength / dimensions.lengthM
    fun scaleAlongWidth(): Double = extentForWidth / dimensions.widthM
    fun scaleAlongHeight(): Double = extentY / dimensions.heightM
}

object SceneScaleMapper {
    const val TARGET_LONG_SPAN = 10.0

    fun layout(
        dimensions: GreenhouseDimensions,
        targetLongSpan: Double = TARGET_LONG_SPAN,
        cropRowCount: Int = 3
    ): SceneLayout {
        require(dimensions.lengthM.isFinite() && dimensions.lengthM > 0.0) { "lengthM must be finite and > 0" }
        require(dimensions.widthM.isFinite() && dimensions.widthM > 0.0) { "widthM must be finite and > 0" }
        require(dimensions.heightM.isFinite() && dimensions.heightM > 0.0) { "heightM must be finite and > 0" }
        require(targetLongSpan.isFinite() && targetLongSpan > 0.0) { "targetLongSpan must be finite and > 0" }

        val longHorizontalM = max(dimensions.lengthM, dimensions.widthM)
        val uniformScale = targetLongSpan / longHorizontalM
        val lengthMappedToX = dimensions.lengthM >= dimensions.widthM
        val extentX = longHorizontalM * uniformScale
        val extentZ = min(dimensions.lengthM, dimensions.widthM) * uniformScale
        val extentY = dimensions.heightM * uniformScale
        return SceneLayout(
            dimensions = dimensions,
            uniformScale = uniformScale,
            extentX = extentX,
            extentY = extentY,
            extentZ = extentZ,
            lengthMappedToX = lengthMappedToX,
            targetLongSpan = targetLongSpan,
            cropRowCount = cropRowCount
        )
    }
}

enum class MeshKind {
    GROUND,
    FLOOR,
    FRAME,
    PANEL,
    CROP
}

data class MeshBox(
    val id: String,
    val kind: MeshKind,
    val centerX: Double,
    val centerY: Double,
    val centerZ: Double,
    val sizeX: Double,
    val sizeY: Double,
    val sizeZ: Double
) {
    val minX: Double get() = centerX - sizeX * 0.5
    val maxX: Double get() = centerX + sizeX * 0.5
    val minY: Double get() = centerY - sizeY * 0.5
    val maxY: Double get() = centerY + sizeY * 0.5
    val minZ: Double get() = centerZ - sizeZ * 0.5
    val maxZ: Double get() = centerZ + sizeZ * 0.5
}

/**
 * Lightweight procedural greenhouse: floor, frame, translucent panels, crop-row placeholders.
 * Decorative ridge sits at envelope height; walls are slightly lower so the pitched outline
 * does not enlarge the displayed L×W×H envelope.
 */
object GreenhouseMeshBuilder {
    /** Modest apron around the floor so the ground does not dominate framing. */
    const val GROUND_SPAN_FACTOR_X = 1.12
    const val GROUND_SPAN_FACTOR_Z = 1.16

    fun build(layout: SceneLayout): List<MeshBox> {
        val length = layout.extentX
        val width = layout.extentZ
        val height = layout.extentY
        val halfL = length * 0.5
        val halfW = width * 0.5
        val beam = (minOf(length, width, height) * 0.028).coerceIn(0.045, 0.12)
        val panel = (beam * 0.42).coerceAtLeast(0.02)
        val wallH = height * 0.82
        val ridgeH = height
        val boxes = mutableListOf<MeshBox>()

        boxes += MeshBox(
            id = "ground",
            kind = MeshKind.GROUND,
            centerX = 0.0,
            centerY = -0.03,
            centerZ = 0.0,
            sizeX = length * GROUND_SPAN_FACTOR_X,
            sizeY = 0.04,
            sizeZ = width * GROUND_SPAN_FACTOR_Z
        )
        boxes += MeshBox(
            id = "floor",
            kind = MeshKind.FLOOR,
            centerX = 0.0,
            centerY = -0.01,
            centerZ = 0.0,
            sizeX = length,
            sizeY = 0.04,
            sizeZ = width
        )

        val posts = listOf(
            Triple("post_sw", -halfL, -halfW),
            Triple("post_se", halfL, -halfW),
            Triple("post_nw", -halfL, halfW),
            Triple("post_ne", halfL, halfW)
        )
        posts.forEach { (id, x, z) ->
            boxes += MeshBox(
                id = id,
                kind = MeshKind.FRAME,
                centerX = x,
                centerY = wallH * 0.5,
                centerZ = z,
                sizeX = beam,
                sizeY = wallH,
                sizeZ = beam
            )
        }

        fun rail(id: String, cx: Double, cy: Double, cz: Double, sx: Double, sz: Double) {
            boxes += MeshBox(
                id = id,
                kind = MeshKind.FRAME,
                centerX = cx,
                centerY = cy,
                centerZ = cz,
                sizeX = sx,
                sizeY = beam,
                sizeZ = sz
            )
        }

        rail("base_s", 0.0, beam * 0.5, -halfW, length, beam)
        rail("base_n", 0.0, beam * 0.5, halfW, length, beam)
        rail("base_w", -halfL, beam * 0.5, 0.0, beam, width)
        rail("base_e", halfL, beam * 0.5, 0.0, beam, width)
        rail("eave_s", 0.0, wallH, -halfW, length, beam)
        rail("eave_n", 0.0, wallH, halfW, length, beam)
        rail("eave_w", -halfL, wallH, 0.0, beam, width)
        rail("eave_e", halfL, wallH, 0.0, beam, width)
        rail("ridge", 0.0, ridgeH - beam * 0.5, 0.0, length, beam)

        boxes += MeshBox(
            id = "gable_w",
            kind = MeshKind.FRAME,
            centerX = -halfL,
            centerY = (wallH + ridgeH) * 0.5,
            centerZ = 0.0,
            sizeX = beam,
            sizeY = (ridgeH - wallH).coerceAtLeast(0.01),
            sizeZ = beam
        )
        boxes += MeshBox(
            id = "gable_e",
            kind = MeshKind.FRAME,
            centerX = halfL,
            centerY = (wallH + ridgeH) * 0.5,
            centerZ = 0.0,
            sizeX = beam,
            sizeY = (ridgeH - wallH).coerceAtLeast(0.01),
            sizeZ = beam
        )

        fun panelBox(id: String, cx: Double, cy: Double, cz: Double, sx: Double, sy: Double, sz: Double) {
            boxes += MeshBox(
                id = id,
                kind = MeshKind.PANEL,
                centerX = cx,
                centerY = cy,
                centerZ = cz,
                sizeX = sx,
                sizeY = sy,
                sizeZ = sz
            )
        }

        val wallPanelH = (wallH - beam * 2.0).coerceAtLeast(height * 0.4)
        val wallPanelY = beam + wallPanelH * 0.5
        panelBox("glass_s", 0.0, wallPanelY, -halfW, length - beam * 2.0, wallPanelH, panel)
        panelBox("glass_n", 0.0, wallPanelY, halfW, length - beam * 2.0, wallPanelH, panel)
        panelBox("glass_w", -halfL, wallPanelY, 0.0, panel, wallPanelH, width - beam * 2.0)
        panelBox("glass_e", halfL, wallPanelY, 0.0, panel, wallPanelH, width - beam * 2.0)
        panelBox(
            "roof_s",
            0.0,
            (wallH + ridgeH) * 0.5,
            -halfW * 0.5,
            length - beam,
            panel,
            halfW
        )
        panelBox(
            "roof_n",
            0.0,
            (wallH + ridgeH) * 0.5,
            halfW * 0.5,
            length - beam,
            panel,
            halfW
        )

        // PREVIEW_ASSUMPTION: crop rows are visual placeholders, not agronomic or Phase 3A physics values.
        boxes += CropRowGeometry.boxes(layout)
        return boxes
    }
}
