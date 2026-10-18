package com.greenhands.app.ar.render

import com.google.android.filament.Engine
import com.greenhands.app.heat.visualization.PadInletAppearance
import io.github.sceneview.geometries.Geometry
import io.github.sceneview.math.Direction
import io.github.sceneview.math.Position

/**
 * Small inlet-airflow arrowhead pointing local +Z. Separate from pad corrugation.
 */
object PadInletArrowMesh {
    fun create(engine: Engine): Geometry {
        val length = PadInletAppearance.ARROW_LENGTH_M.toFloat()
        val halfW = (PadInletAppearance.ARROW_WIDTH_M * 0.5).toFloat()
        val halfH = (PadInletAppearance.ARROW_HEIGHT_M * 0.5).toFloat()
        val tip = Position(0f, 0f, length)
        val base = arrayOf(
            Position(-halfW, -halfH, 0f),
            Position(halfW, -halfH, 0f),
            Position(halfW, halfH, 0f),
            Position(-halfW, halfH, 0f)
        )
        val vertices = ArrayList<Geometry.Vertex>(24)
        val indices = ArrayList<Int>(36)

        fun addTriangle(a: Position, b: Position, c: Position, normal: Direction) {
            val start = vertices.size
            vertices += Geometry.Vertex(
                position = a,
                normal = normal,
                uvCoordinate = io.github.sceneview.geometries.UvCoordinate(0f, 0f)
            )
            vertices += Geometry.Vertex(
                position = b,
                normal = normal,
                uvCoordinate = io.github.sceneview.geometries.UvCoordinate(1f, 0f)
            )
            vertices += Geometry.Vertex(
                position = c,
                normal = normal,
                uvCoordinate = io.github.sceneview.geometries.UvCoordinate(0.5f, 1f)
            )
            indices += start
            indices += start + 1
            indices += start + 2
            if (PadInletAppearance.DOUBLE_SIDED) {
                val back = Direction(x = -normal.x, y = -normal.y, z = -normal.z)
                val rev = vertices.size
                vertices += Geometry.Vertex(
                    position = a,
                    normal = back,
                    uvCoordinate = io.github.sceneview.geometries.UvCoordinate(0f, 0f)
                )
                vertices += Geometry.Vertex(
                    position = c,
                    normal = back,
                    uvCoordinate = io.github.sceneview.geometries.UvCoordinate(0.5f, 1f)
                )
                vertices += Geometry.Vertex(
                    position = b,
                    normal = back,
                    uvCoordinate = io.github.sceneview.geometries.UvCoordinate(1f, 0f)
                )
                indices += rev
                indices += rev + 1
                indices += rev + 2
            }
        }

        addTriangle(tip, base[0], base[1], Direction(x = 0f, y = -1f, z = 0.35f))
        addTriangle(tip, base[1], base[2], Direction(x = 1f, y = 0f, z = 0.35f))
        addTriangle(tip, base[2], base[3], Direction(x = 0f, y = 1f, z = 0.35f))
        addTriangle(tip, base[3], base[0], Direction(x = -1f, y = 0f, z = 0.35f))
        addTriangle(base[0], base[3], base[2], Direction(z = -1f))
        addTriangle(base[0], base[2], base[1], Direction(z = -1f))

        return Geometry.Builder()
            .vertices(vertices)
            .indices(indices)
            .build(engine)
    }
}
