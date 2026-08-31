package com.greenhands.app.sensor.ar

import androidx.compose.ui.graphics.Color
import com.google.android.filament.Engine
import com.google.android.filament.MaterialInstance
import io.github.sceneview.math.Position
import io.github.sceneview.math.Size
import io.github.sceneview.node.CubeNode
import io.github.sceneview.node.Node

/**
 * Real AR coverage visualization as **independent translucent 3D grid cells**.
 *
 * Each greenhouse floor cell becomes its own thin CubeNode just above the real floor —
 * never a single full-footprint slab, PlaneNode, or greenhouse-sized cuboid.
 * Cell interiors use translucent materials so the physical greenhouse remains visible.
 */
object ArCoverageNodes {

    const val COVERAGE_ROOT_PREFIX = "ar_coverage_"

    /** Vertical thickness of each cell body (meters). Lightweight, not a wall. */
    const val CELL_BODY_HEIGHT = 0.08f

    /** Inset fraction so neighboring cells show a small gap. */
    const val CELL_INSET = 0.88f

    /** Edge bar thickness (meters) — slightly stronger visual border. */
    const val EDGE_THICK = 0.024f

    /** Edge bar height — a bit taller than the body for readability. */
    const val EDGE_HEIGHT = 0.10f

    data class CellGeometry(
        val bodySizeX: Float,
        val bodySizeY: Float,
        val bodySizeZ: Float,
        val centerY: Float
    )

    /**
     * Pure geometry helper for one floor cell. Body footprint is always smaller than
     * [cellSizeMeters] (via [CELL_INSET]) and height is [CELL_BODY_HEIGHT] — never
     * greenhouse length × width × height.
     */
    fun cellGeometry(
        cellSizeMeters: Float,
        floorY: Float = ArRealCoveragePlacement.COVERAGE_FLOOR_Y_METERS
    ): CellGeometry {
        val inset = cellSizeMeters.coerceAtLeast(0.05f) * CELL_INSET
        return CellGeometry(
            bodySizeX = inset,
            bodySizeY = CELL_BODY_HEIGHT,
            bodySizeZ = inset,
            centerY = floorY + CELL_BODY_HEIGHT * 0.5f
        )
    }

    /** True if dimensions approximate a full greenhouse cuboid (must never be used for cells). */
    fun isFullGreenhouseCuboid(
        sizeX: Float,
        sizeY: Float,
        sizeZ: Float,
        lengthMeters: Float,
        widthMeters: Float,
        heightMeters: Float
    ): Boolean =
        sizeX >= lengthMeters * 0.9f &&
            sizeZ >= widthMeters * 0.9f &&
            sizeY >= heightMeters * 0.4f

    fun clearCoverage(frameRoot: Node?) {
        if (frameRoot == null) return
        frameRoot.childNodes.toList()
            .filter { it.name?.startsWith(COVERAGE_ROOT_PREFIX) == true }
            .forEach { child -> child.destroy() }
    }

    fun clearCoverageFromScene(nodes: MutableList<Node>) {
        nodes.forEach { node ->
            if (node.name == ArGreenhouseFrameNodes.FRAME_ROOT_NAME) {
                clearCoverage(node)
            }
        }
        nodes.toList()
            .filter { it.name?.startsWith(COVERAGE_ROOT_PREFIX) == true }
            .forEach { node ->
                node.destroy()
                nodes -= node
            }
    }

    /**
     * Renders each [ArRealCoveragePlacement.RenderCell] as:
     * 1. a translucent thin box (cell body)
     * 2. four slightly stronger edge bars (cell border)
     *
     * One shared greenhouse frame root — no per-cell AR anchors.
     */
    fun attachCoverage(
        engine: Engine,
        frameRoot: Node,
        cells: List<ArRealCoveragePlacement.RenderCell>,
        bodyMaterialFor: (Color) -> MaterialInstance,
        edgeMaterialFor: (Color) -> MaterialInstance = bodyMaterialFor
    ) {
        clearCoverage(frameRoot)
        cells.forEach { render ->
            val geom = cellGeometry(render.cellSizeMeters, render.local.y)
            val cx = render.local.x
            val cz = render.local.z
            val prefix = "${COVERAGE_ROOT_PREFIX}${render.cell.column}_${render.cell.row}_" +
                "${render.cell.state.name}"
            val bodyMaterial = bodyMaterialFor(render.fill)
            val edgeMaterial = edgeMaterialFor(render.fill)

            frameRoot.addChildNode(
                CubeNode(
                    engine = engine,
                    size = Size(geom.bodySizeX, geom.bodySizeY, geom.bodySizeZ),
                    center = Position(cx, geom.centerY, cz),
                    materialInstance = bodyMaterial
                ).apply {
                    name = prefix + "_body"
                    isEditable = false
                }
            )

            val half = geom.bodySizeX * 0.5f
            val halfZ = geom.bodySizeZ * 0.5f
            val edgeY = render.local.y + EDGE_HEIGHT * 0.5f

            addEdgeBar(
                engine, frameRoot, edgeMaterial, prefix + "_zmin",
                cx, edgeY, cz - halfZ, geom.bodySizeX, EDGE_HEIGHT, EDGE_THICK
            )
            addEdgeBar(
                engine, frameRoot, edgeMaterial, prefix + "_zmax",
                cx, edgeY, cz + halfZ, geom.bodySizeX, EDGE_HEIGHT, EDGE_THICK
            )
            addEdgeBar(
                engine, frameRoot, edgeMaterial, prefix + "_xmin",
                cx - half, edgeY, cz, EDGE_THICK, EDGE_HEIGHT, geom.bodySizeZ
            )
            addEdgeBar(
                engine, frameRoot, edgeMaterial, prefix + "_xmax",
                cx + half, edgeY, cz, EDGE_THICK, EDGE_HEIGHT, geom.bodySizeZ
            )
        }
    }

    private fun addEdgeBar(
        engine: Engine,
        frameRoot: Node,
        material: MaterialInstance,
        name: String,
        cx: Float,
        cy: Float,
        cz: Float,
        sizeX: Float,
        sizeY: Float,
        sizeZ: Float
    ) {
        frameRoot.addChildNode(
            CubeNode(
                engine = engine,
                size = Size(sizeX, sizeY, sizeZ),
                center = Position(cx, cy, cz),
                materialInstance = material
            ).apply {
                this.name = name
                isEditable = false
            }
        )
    }
}
