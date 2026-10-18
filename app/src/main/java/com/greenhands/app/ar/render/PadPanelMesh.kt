package com.greenhands.app.ar.render

import com.google.android.filament.Engine
import com.greenhands.app.heat.visualization.PadInletAppearance
import com.greenhands.app.heat.visualization.PadPanelLayout
import io.github.sceneview.geometries.Geometry
import io.github.sceneview.geometries.UvCoordinate
import io.github.sceneview.math.Direction
import io.github.sceneview.math.Position

/**
 * Metre-accurate framed corrugated cooling-pad panel in XY (local +Z faces into
 * the greenhouse after yaw). Vertex positions are in domain metres so the renderer
 * must not non-uniformly scale this mesh by pad width/height.
 */
object PadPanelMesh {
    fun create(engine: Engine, widthM: Double, heightM: Double): Geometry {
        val vertices = ArrayList<Geometry.Vertex>(256)
        val indices = ArrayList<Int>(384)
        val quads = PadPanelLayout.quads(widthM, heightM)
        val thickness = PadPanelLayout.THICKNESS_M.toFloat()
        val corrugation = PadPanelLayout.CORRUGATION_M.toFloat()
        val frameZ = thickness
        val backZ = thickness * 0.22f
        val ribZEven = thickness * 0.72f
        val ribZOdd = (thickness * 0.72f) - corrugation
        val railZ = thickness * 0.55f
        val f = PadPanelLayout.frameM(widthM, heightM).toFloat()
        val hx = (widthM * 0.5).toFloat()
        val hy = (heightM * 0.5).toFloat()

        fun addQuad(a: Position, b: Position, c: Position, d: Position, normal: Direction) {
            val start = vertices.size
            listOf(a, b, c, d).forEachIndexed { i, p ->
                vertices += Geometry.Vertex(
                    position = p,
                    normal = normal,
                    uvCoordinate = UvCoordinate(if (i == 1 || i == 2) 1f else 0f, if (i >= 2) 1f else 0f)
                )
            }
            indices += start
            indices += start + 1
            indices += start + 2
            indices += start
            indices += start + 2
            indices += start + 3
            if (PadInletAppearance.DOUBLE_SIDED) {
                val back = Direction(x = -normal.x, y = -normal.y, z = -normal.z)
                val rev = vertices.size
                listOf(a, d, c, b).forEachIndexed { i, p ->
                    vertices += Geometry.Vertex(
                        position = p,
                        normal = back,
                        uvCoordinate = UvCoordinate(if (i == 1 || i == 2) 1f else 0f, if (i >= 2) 1f else 0f)
                    )
                }
                indices += rev
                indices += rev + 1
                indices += rev + 2
                indices += rev
                indices += rev + 2
                indices += rev + 3
            }
        }

        fun addRect(minX: Float, maxX: Float, minY: Float, maxY: Float, z: Float) {
            addQuad(
                Position(minX, minY, z),
                Position(maxX, minY, z),
                Position(maxX, maxY, z),
                Position(minX, maxY, z),
                Direction(z = 1f)
            )
        }

        addRect(-hx + f, hx - f, -hy + f, hy - f, backZ)

        var ribIndex = 0
        quads.forEach { quad ->
            val z = when {
                quad.isFrame -> frameZ
                quad.width >= quad.height * 0.85 -> railZ
                else -> {
                    val even = ribIndex % 2 == 0
                    ribIndex += 1
                    if (even) ribZEven else ribZOdd
                }
            }
            addRect(quad.minX.toFloat(), quad.maxX.toFloat(), quad.minY.toFloat(), quad.maxY.toFloat(), z)
        }

        return Geometry.Builder()
            .vertices(vertices)
            .indices(indices)
            .build(engine)
    }
}
