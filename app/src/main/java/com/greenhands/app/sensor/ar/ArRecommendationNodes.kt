package com.greenhands.app.sensor.ar

import androidx.compose.ui.graphics.Color
import com.google.android.filament.Engine
import com.google.android.filament.MaterialInstance
import com.greenhands.app.ui.theme.ClimateTeal
import io.github.sceneview.math.Position
import io.github.sceneview.math.Size
import io.github.sceneview.node.CubeNode
import io.github.sceneview.node.Node

/**
 * SceneView 2.2.1 P# recommendation markers as children of the greenhouse frame root.
 * Hollow teal diamond language — distinct from solid sensor glyphs.
 * No per-recommendation AR anchors.
 */
object ArRecommendationNodes {

    const val REC_ROOT_PREFIX = "ar_rec_"
    private const val STEM_THICK = 0.022f
    private const val EDGE = 0.028f

    fun clearRecommendations(frameRoot: Node?) {
        if (frameRoot == null) return
        frameRoot.childNodes.toList()
            .filter { it.name?.startsWith(REC_ROOT_PREFIX) == true }
            .forEach { child -> child.destroy() }
    }

    fun clearRecommendationsFromScene(nodes: MutableList<Node>) {
        nodes.forEach { node ->
            if (node.name == ArGreenhouseFrameNodes.FRAME_ROOT_NAME) {
                clearRecommendations(node)
            }
        }
        nodes.toList()
            .filter { it.name?.startsWith(REC_ROOT_PREFIX) == true }
            .forEach { node ->
                node.destroy()
                nodes -= node
            }
    }

    fun attachRecommendations(
        engine: Engine,
        frameRoot: Node,
        markers: List<ArRealRecommendationPlacement.RenderMarker>,
        materialFor: (Color) -> MaterialInstance
    ) {
        clearRecommendations(frameRoot)
        val solid = materialFor(ClimateTeal)
        val soft = materialFor(Color(0xFF7AD4CB))
        markers.forEach { marker ->
            frameRoot.addChildNode(buildRecommendationNode(engine, marker, solid, soft))
        }
    }

    private fun buildRecommendationNode(
        engine: Engine,
        marker: ArRealRecommendationPlacement.RenderMarker,
        solid: MaterialInstance,
        soft: MaterialInstance
    ): Node {
        val rec = marker.recommendation
        val lx = marker.local.x
        val ly = marker.local.y
        val lz = marker.local.z

        val group = Node(engine).apply {
            name = REC_ROOT_PREFIX + rec.label
            isEditable = false
            position = Position(lx, 0f, lz)
        }

        // Dashed stem (bead chain) — distinct from solid sensor poles.
        val segments = 5
        for (i in 0..segments) {
            if (i % 2 == 1) continue
            val t = i / segments.toFloat()
            val y = ly * t
            group.addChildNode(
                CubeNode(
                    engine = engine,
                    size = Size(STEM_THICK, STEM_THICK, STEM_THICK),
                    center = Position(0f, y, 0f),
                    materialInstance = soft
                ).apply {
                    name = REC_ROOT_PREFIX + rec.label + "_stem_$i"
                    isEditable = false
                }
            )
        }

        // Hollow diamond outline at mount height (4 edge beads).
        val r = 0.11f
        val diamond = listOf(
            Position(0f, ly + r, 0f),
            Position(r, ly, 0f),
            Position(0f, ly - r, 0f),
            Position(-r, ly, 0f)
        )
        diamond.forEachIndexed { index, pos ->
            group.addChildNode(
                CubeNode(
                    engine = engine,
                    size = Size(EDGE, EDGE, EDGE),
                    center = pos,
                    materialInstance = solid
                ).apply {
                    name = REC_ROOT_PREFIX + rec.label + "_diamond_$index"
                    isEditable = false
                }
            )
        }
        // Edge connectors (approximate hollow outline).
        val edges = listOf(0 to 1, 1 to 2, 2 to 3, 3 to 0)
        edges.forEachIndexed { ei, (a, b) ->
            val pa = diamond[a]
            val pb = diamond[b]
            val mx = (pa.x + pb.x) * 0.5f
            val my = (pa.y + pb.y) * 0.5f
            val mz = (pa.z + pb.z) * 0.5f
            group.addChildNode(
                CubeNode(
                    engine = engine,
                    size = Size(EDGE * 0.7f, EDGE * 0.7f, EDGE * 0.7f),
                    center = Position(mx, my, mz),
                    materialInstance = soft
                ).apply {
                    name = REC_ROOT_PREFIX + rec.label + "_edge_$ei"
                    isEditable = false
                }
            )
        }

        // Compact label plate named with P# for debug/tests.
        group.addChildNode(
            CubeNode(
                engine = engine,
                size = Size(0.09f, 0.02f, 0.09f),
                center = Position(0f, ly + r + 0.08f, 0f),
                materialInstance = solid
            ).apply {
                name = REC_ROOT_PREFIX + rec.label + "_tag"
                isEditable = false
            }
        )

        return group
    }
}
