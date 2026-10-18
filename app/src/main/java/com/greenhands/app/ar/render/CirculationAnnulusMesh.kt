package com.greenhands.app.ar.render

import com.google.android.filament.Engine
import com.greenhands.app.heat.visualization.CirculationRingAppearance
import com.greenhands.app.heat.visualization.CirculationRingVisual
import io.github.sceneview.geometries.Geometry
import io.github.sceneview.math.Direction
import io.github.sceneview.math.Position
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * One reusable low-poly annulus for a circulation ring. Built with SceneView 2.2.1
 * [Geometry] so it stays on the accepted Filament path without a GLB.
 */
object CirculationAnnulusMesh {
    fun create(engine: Engine): Geometry {
        val segments = CirculationRingVisual.SEGMENT_COUNT.coerceAtLeast(12)
        val outer = CirculationRingAppearance.RADIUS_M.toFloat()
        val inner = (CirculationRingAppearance.RADIUS_M - CirculationRingAppearance.TUBE_M)
            .coerceAtLeast(CirculationRingAppearance.RADIUS_M * 0.55)
            .toFloat()
        val front = Direction(z = 1.0f)
        val back = Direction(z = -1.0f)
        val vertexStride = if (CirculationRingAppearance.DOUBLE_SIDED) 4 else 2
        val vertices = ArrayList<Geometry.Vertex>(segments * vertexStride)
        val indices = ArrayList<Int>(segments * if (CirculationRingAppearance.DOUBLE_SIDED) 12 else 6)
        var i = 0
        while (i < segments) {
            val angle = (2.0 * PI * i / segments).toFloat()
            val c = cos(angle)
            val s = sin(angle)
            val u = i.toFloat() / segments.toFloat()
            vertices += Geometry.Vertex(
                position = Position(inner * c, inner * s, 0f),
                normal = front,
                uvCoordinate = io.github.sceneview.geometries.UvCoordinate(u, 0f)
            )
            vertices += Geometry.Vertex(
                position = Position(outer * c, outer * s, 0f),
                normal = front,
                uvCoordinate = io.github.sceneview.geometries.UvCoordinate(u, 1f)
            )
            if (CirculationRingAppearance.DOUBLE_SIDED) {
                vertices += Geometry.Vertex(
                    position = Position(inner * c, inner * s, 0f),
                    normal = back,
                    uvCoordinate = io.github.sceneview.geometries.UvCoordinate(u, 0f)
                )
                vertices += Geometry.Vertex(
                    position = Position(outer * c, outer * s, 0f),
                    normal = back,
                    uvCoordinate = io.github.sceneview.geometries.UvCoordinate(u, 1f)
                )
            }
            val i0 = i * vertexStride
            val i1 = i0 + 1
            val j = ((i + 1) % segments) * vertexStride
            val j1 = j + 1
            indices += i0
            indices += i1
            indices += j1
            indices += i0
            indices += j1
            indices += j
            if (CirculationRingAppearance.DOUBLE_SIDED) {
                val b0 = i0 + 2
                val b1 = i0 + 3
                val bj = j + 2
                val bj1 = j + 3
                indices += b0
                indices += bj1
                indices += b1
                indices += b0
                indices += bj
                indices += bj1
            }
            i++
        }
        return Geometry.Builder()
            .vertices(vertices)
            .indices(indices)
            .build(engine)
    }
}
