package com.greenhands.app.ar.render

import com.google.android.filament.Engine
import com.greenhands.app.equipment.domain.DomainSceneCoordinateAdapter
import com.greenhands.app.equipment.model.DomainPoint
import com.greenhands.app.heat.visualization.EnvironmentalFieldAppearance
import com.greenhands.app.heat.visualization.EnvironmentalFieldBandMesh
import com.greenhands.app.heat.visualization.EnvironmentalFieldCellBox
import com.greenhands.app.heat.visualization.EnvironmentalFieldVisual
import com.greenhands.app.preview3d.domain.SceneLayout
import io.github.sceneview.geometries.Geometry
import io.github.sceneview.geometries.UvCoordinate
import io.github.sceneview.math.Direction
import io.github.sceneview.math.Position

data class EnvironmentalFieldScenePatch(
    val minX: Float,
    val maxX: Float,
    val minY: Float,
    val maxY: Float,
    val minZ: Float,
    val maxZ: Float,
    val visible: Boolean
) {
    companion object {
        val HIDDEN = EnvironmentalFieldScenePatch(0f, 0f, 0f, 0f, 0f, 0f, visible = false)
    }
}

/**
 * Combined thin-slab patches for one classification band. Unused slots are
 * degenerate so the node count stays fixed.
 */
object EnvironmentalFieldPatchMesh {
    const val VERTICES_PER_CELL = 24
    const val INDICES_PER_CELL = 36

    fun create(engine: Engine, slotCount: Int = EnvironmentalFieldVisual.MAX_CELLS): Geometry {
        val slots = slotCount.coerceAtLeast(1)
        val patches = List(slots) { EnvironmentalFieldScenePatch.HIDDEN }
        return Geometry.Builder()
            .vertices(verticesOf(patches))
            .indices(indicesOf(slots))
            .build(engine)
    }

    fun patchesOf(
        band: EnvironmentalFieldBandMesh,
        layout: SceneLayout,
        slotCount: Int = EnvironmentalFieldVisual.MAX_CELLS
    ): List<EnvironmentalFieldScenePatch> {
        val slots = slotCount.coerceAtLeast(1)
        return List(slots) { index ->
            val cell = band.cells.getOrNull(index) ?: return@List EnvironmentalFieldScenePatch.HIDDEN
            scenePatch(cell, layout)
        }
    }

    fun verticesOf(patches: List<EnvironmentalFieldScenePatch>): List<Geometry.Vertex> {
        val source = patches.ifEmpty { listOf(EnvironmentalFieldScenePatch.HIDDEN) }
        val vertices = ArrayList<Geometry.Vertex>(source.size * VERTICES_PER_CELL)
        source.forEach { addSlab(vertices, it) }
        return vertices
    }

    fun indicesOf(slotCount: Int): List<Int> {
        val n = slotCount.coerceAtLeast(1)
        val indices = ArrayList<Int>(n * INDICES_PER_CELL)
        var base = 0
        repeat(n) {
            fun quad(a: Int, b: Int, c: Int, d: Int) {
                indices += base + a
                indices += base + b
                indices += base + c
                indices += base + a
                indices += base + c
                indices += base + d
            }
            // 6 faces, 4 vertices each, matching addSlab order.
            quad(0, 1, 2, 3)
            quad(4, 5, 6, 7)
            quad(8, 9, 10, 11)
            quad(12, 13, 14, 15)
            quad(16, 17, 18, 19)
            quad(20, 21, 22, 23)
            base += VERTICES_PER_CELL
        }
        return indices
    }

    fun padded(
        patches: List<EnvironmentalFieldScenePatch>,
        slotCount: Int
    ): List<EnvironmentalFieldScenePatch> {
        val slots = slotCount.coerceAtLeast(1)
        if (patches.size == slots) return patches
        return List(slots) { index -> patches.getOrElse(index) { EnvironmentalFieldScenePatch.HIDDEN } }
    }

    fun scenePatch(cell: EnvironmentalFieldCellBox, layout: SceneLayout): EnvironmentalFieldScenePatch {
        val a = DomainSceneCoordinateAdapter.toScene(
            DomainPoint(cell.minXM, cell.minYM, cell.minZM),
            layout
        )
        val b = DomainSceneCoordinateAdapter.toScene(
            DomainPoint(cell.maxXM, cell.maxYM, cell.maxZM),
            layout
        )
        val visible = (cell.maxXM - cell.minXM) > 1e-4 &&
            (cell.maxYM - cell.minYM) > 1e-4 &&
            (cell.maxZM - cell.minZM) > 1e-4
        return EnvironmentalFieldScenePatch(
            minX = minOf(a.x, b.x).toFloat(),
            maxX = maxOf(a.x, b.x).toFloat(),
            minY = minOf(a.y, b.y).toFloat(),
            maxY = maxOf(a.y, b.y).toFloat(),
            minZ = minOf(a.z, b.z).toFloat(),
            maxZ = maxOf(a.z, b.z).toFloat(),
            visible = visible
        )
    }

    fun followsGreenhouseRoot(): Boolean = true

    fun appliesTabletopScaleToChildren(): Boolean =
        CirculationRingSceneMath.APPLIES_TABLETOP_SCALE_TO_CHILDREN

    private fun addSlab(vertices: MutableList<Geometry.Vertex>, patch: EnvironmentalFieldScenePatch) {
        if (!patch.visible) {
            repeat(VERTICES_PER_CELL) {
                vertices += Geometry.Vertex(
                    position = Position(0f, 0f, 0f),
                    normal = Direction(y = 1f),
                    uvCoordinate = UvCoordinate(0f, 0f)
                )
            }
            return
        }
        val x0 = patch.minX
        val x1 = patch.maxX
        val y0 = patch.minY
        val y1 = patch.maxY
        val z0 = patch.minZ
        val z1 = patch.maxZ
        fun addQuad(
            p0: Position,
            p1: Position,
            p2: Position,
            p3: Position,
            normal: Direction
        ) {
            listOf(p0, p1, p2, p3).forEachIndexed { i, p ->
                vertices += Geometry.Vertex(
                    position = p,
                    normal = normal,
                    uvCoordinate = UvCoordinate(if (i == 1 || i == 2) 1f else 0f, if (i >= 2) 1f else 0f)
                )
            }
        }
        addQuad(Position(x0, y1, z0), Position(x1, y1, z0), Position(x1, y1, z1), Position(x0, y1, z1), Direction(y = 1f))
        addQuad(Position(x0, y0, z1), Position(x1, y0, z1), Position(x1, y0, z0), Position(x0, y0, z0), Direction(y = -1f))
        addQuad(Position(x0, y0, z0), Position(x1, y0, z0), Position(x1, y1, z0), Position(x0, y1, z0), Direction(z = -1f))
        addQuad(Position(x0, y1, z1), Position(x1, y1, z1), Position(x1, y0, z1), Position(x0, y0, z1), Direction(z = 1f))
        addQuad(Position(x0, y0, z1), Position(x0, y0, z0), Position(x0, y1, z0), Position(x0, y1, z1), Direction(x = -1f))
        addQuad(Position(x1, y0, z0), Position(x1, y0, z1), Position(x1, y1, z1), Position(x1, y1, z0), Direction(x = 1f))
        if (!EnvironmentalFieldAppearance.DOUBLE_SIDED) return
    }
}
