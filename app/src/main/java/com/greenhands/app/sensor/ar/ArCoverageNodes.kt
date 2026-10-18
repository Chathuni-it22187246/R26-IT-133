package com.greenhands.app.sensor.ar



import androidx.compose.ui.graphics.Color

import com.google.android.filament.Engine

import com.google.android.filament.MaterialInstance

import io.github.sceneview.math.Position

import io.github.sceneview.math.Size

import io.github.sceneview.node.CubeNode

import io.github.sceneview.node.Node



/**

 * Real AR coverage visualization as thin floor **outlines** (not opaque slabs).

 * One shared greenhouse frame root — no per-cell anchors.

 */

object ArCoverageNodes {



    const val COVERAGE_ROOT_PREFIX = "ar_coverage_"

    private const val OUTLINE_THICK = 0.028f

    private const val OUTLINE_HEIGHT = 0.006f

    private const val CELL_INSET = 0.92f



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

     * Renders each cell as four thin edge bars on the floor so the real greenhouse

     * remains visible through the cell interior.

     */

    fun attachCoverage(

        engine: Engine,

        frameRoot: Node,

        cells: List<ArRealCoveragePlacement.RenderCell>,

        materialFor: (Color) -> MaterialInstance

    ) {

        clearCoverage(frameRoot)

        cells.forEach { render ->

            val half = render.cellSizeMeters * CELL_INSET * 0.5f

            val cx = render.local.x

            val cy = render.local.y + OUTLINE_HEIGHT * 0.5f

            val cz = render.local.z

            val material = materialFor(render.fill)

            val prefix = "${COVERAGE_ROOT_PREFIX}${render.cell.column}_${render.cell.row}_" +

                "${render.cell.state.name}"

            // Edges along local X (width direction in greenhouse Z)

            addOutlineBar(

                engine, frameRoot, material, prefix + "_zmin",

                cx, cy, cz - half, render.cellSizeMeters * CELL_INSET, OUTLINE_THICK, OUTLINE_HEIGHT

            )

            addOutlineBar(

                engine, frameRoot, material, prefix + "_zmax",

                cx, cy, cz + half, render.cellSizeMeters * CELL_INSET, OUTLINE_THICK, OUTLINE_HEIGHT

            )

            // Edges along local Z (length direction in greenhouse X)

            addOutlineBar(

                engine, frameRoot, material, prefix + "_xmin",

                cx - half, cy, cz, OUTLINE_THICK, OUTLINE_HEIGHT, render.cellSizeMeters * CELL_INSET

            )

            addOutlineBar(

                engine, frameRoot, material, prefix + "_xmax",

                cx + half, cy, cz, OUTLINE_THICK, OUTLINE_HEIGHT, render.cellSizeMeters * CELL_INSET

            )

        }

    }



    private fun addOutlineBar(

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

