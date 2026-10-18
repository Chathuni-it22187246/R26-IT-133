package com.greenhands.app.sensor.ar

import com.greenhands.app.sensor.model.GreenhousePhysicalConfig
import kotlin.math.abs

/**
 * Pure Real-AR greenhouse structure plan. Geometry is attached only after
 * [ArOriginPlacementPhase.ALIGNED] and only as children of one shared root.
 *
 * Floor uses grid/perimeter lines — never an opaque full-footprint slab.
 * Wall/roof panels are optional transparent quads; the wireframe is required.
 */
enum class ArGreenhouseStructurePart {
    FLOOR_PERIMETER,
    FLOOR_GRID,
    POST,
    EAVE,
    ROOF_FRAME,
    WALL_PANEL,
    ROOF_PANEL
}

data class ArGreenhousePlannedPart(
    val name: String,
    val part: ArGreenhouseStructurePart,
    val edge: ArLocalEdge? = null,
    val panel: ArLocalPanel? = null
)

object ArGreenhouseStructurePlan {

    const val STRUCTURE_GROUP_NAME = "greenhouse_structure"
    const val FLOOR_PERIMETER_PREFIX = "greenhouse_floor_perimeter"
    const val FLOOR_GRID_PREFIX = "greenhouse_floor_grid"
    const val POST_PREFIX = "greenhouse_frame_post"
    const val EAVE_PREFIX = "greenhouse_frame_eave"
    const val ROOF_PREFIX = "greenhouse_frame_roof"
    const val WALL_PANEL_PREFIX = "greenhouse_panel_wall"
    const val ROOF_PANEL_PREFIX = "greenhouse_panel_roof"

    const val FRAME_BEAM_THICK_M = 0.045f
    const val GRID_BEAM_THICK_M = 0.018f
    const val PANEL_THICK_M = 0.012f

    fun shouldAttach(
        phase: ArOriginPlacementPhase,
        greenhouseVisible: Boolean
    ): Boolean = ArGreenhouseFrameGeometry.shouldShowFrame(phase) && greenhouseVisible

    fun plan(
        physical: GreenhousePhysicalConfig,
        includeTransparentPanels: Boolean = true
    ): List<ArGreenhousePlannedPart> {
        val length = physical.lengthMeters.toFloat()
        val width = physical.widthMeters.toFloat()
        val height = physical.heightMeters.toFloat()
        val cell = physical.cellSizeMeters.toFloat()
        if (length <= 0f || width <= 0f || height <= 0f) return emptyList()

        val parts = mutableListOf<ArGreenhousePlannedPart>()
        var floorI = 0
        var postI = 0
        var eaveI = 0
        var roofI = 0
        ArGreenhouseFrameGeometry.structureFrameEdges(length, width, height).forEach { edge ->
            val part = classifyFrameEdge(edge, height)
            val prefix = when (part) {
                ArGreenhouseStructurePart.FLOOR_PERIMETER -> FLOOR_PERIMETER_PREFIX + "_${floorI++}"
                ArGreenhouseStructurePart.POST -> POST_PREFIX + "_${postI++}"
                ArGreenhouseStructurePart.EAVE -> EAVE_PREFIX + "_${eaveI++}"
                else -> ROOF_PREFIX + "_${roofI++}"
            }
            parts += ArGreenhousePlannedPart(name = prefix, part = part, edge = edge)
        }
        ArGreenhouseFrameGeometry.floorGridEdges(length, width, cell).forEachIndexed { index, edge ->
            parts += ArGreenhousePlannedPart(
                name = "${FLOOR_GRID_PREFIX}_$index",
                part = ArGreenhouseStructurePart.FLOOR_GRID,
                edge = edge
            )
        }
        if (includeTransparentPanels) {
            var wallI = 0
            var roofPanelI = 0
            ArGreenhouseFrameGeometry.structurePanels(length, width, height).forEach { panel ->
                when (panel.kind) {
                    StructurePanelKind.FLOOR -> Unit
                    StructurePanelKind.WALL -> parts += ArGreenhousePlannedPart(
                        name = "${WALL_PANEL_PREFIX}_${wallI++}",
                        part = ArGreenhouseStructurePart.WALL_PANEL,
                        panel = panel
                    )
                    StructurePanelKind.ROOF -> parts += ArGreenhousePlannedPart(
                        name = "${ROOF_PANEL_PREFIX}_${roofPanelI++}",
                        part = ArGreenhouseStructurePart.ROOF_PANEL,
                        panel = panel
                    )
                }
            }
        }
        return parts
    }

    fun hasPitchedRoof(parts: List<ArGreenhousePlannedPart>): Boolean =
        parts.any { it.part == ArGreenhouseStructurePart.ROOF_FRAME }

    fun hasFloorGrid(parts: List<ArGreenhousePlannedPart>): Boolean =
        parts.any { it.part == ArGreenhouseStructurePart.FLOOR_GRID }

    fun hasPosts(parts: List<ArGreenhousePlannedPart>): Boolean =
        parts.any { it.part == ArGreenhouseStructurePart.POST }

    fun hasOpaqueFloorSlab(parts: List<ArGreenhousePlannedPart>): Boolean =
        parts.any { it.part == ArGreenhouseStructurePart.WALL_PANEL && false } ||
            parts.any { it.panel?.kind == StructurePanelKind.FLOOR }

    fun localExtents(parts: List<ArGreenhousePlannedPart>): Triple<Float, Float, Float>? {
        val points = parts.flatMap { part ->
            val edge = part.edge
            val panel = part.panel
            buildList {
                if (edge != null) {
                    add(edge.a)
                    add(edge.b)
                }
                if (panel != null) add(panel.center)
            }
        }
        if (points.isEmpty()) return null
        return Triple(
            points.maxOf { it.x },
            points.maxOf { it.y },
            points.maxOf { it.z }
        )
    }

    private fun classifyFrameEdge(edge: ArLocalEdge, heightM: Float): ArGreenhouseStructurePart {
        val dx = abs(edge.b.x - edge.a.x)
        val dy = abs(edge.b.y - edge.a.y)
        val dz = abs(edge.b.z - edge.a.z)
        val vertical = dx < 0.02f && dz < 0.02f && dy > 0.02f
        if (vertical) return ArGreenhouseStructurePart.POST
        val nearFloor = edge.a.y < 0.08f && edge.b.y < 0.08f
        if (nearFloor) return ArGreenhouseStructurePart.FLOOR_PERIMETER
        val nearEave = abs(edge.a.y - heightM) < 0.08f && abs(edge.b.y - heightM) < 0.08f
        if (nearEave) return ArGreenhouseStructurePart.EAVE
        return ArGreenhouseStructurePart.ROOF_FRAME
    }
}
