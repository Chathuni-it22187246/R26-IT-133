package com.greenhands.app.ar.render

import com.google.android.filament.Engine
import com.greenhands.app.heat.visualization.FogMistAppearance
import io.github.sceneview.geometries.Geometry
import io.github.sceneview.geometries.UvCoordinate
import io.github.sceneview.math.Direction
import io.github.sceneview.math.Position
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Low-alpha horizontal humidity halo around the fogger plume. No GLB.
 */
object FogHumidityHaloMesh {
    fun create(engine: Engine): Geometry {
        val segments = FogMistAppearance.HALO_SEGMENT_COUNT.coerceAtLeast(12)
        val outer = FogMistAppearance.HALO_RADIUS_M.toFloat()
        val inner = (FogMistAppearance.HALO_RADIUS_M - FogMistAppearance.HALO_TUBE_M)
            .coerceAtLeast(FogMistAppearance.HALO_RADIUS_M * 0.55)
            .toFloat()
        val up = Direction(y = 1.0f)
        val down = Direction(y = -1.0f)
        val vertexStride = if (FogMistAppearance.DOUBLE_SIDED) 4 else 2
        val vertices = ArrayList<Geometry.Vertex>(segments * vertexStride)
        val indices = ArrayList<Int>(segments * if (FogMistAppearance.DOUBLE_SIDED) 12 else 6)
        var i = 0
        while (i < segments) {
            val angle = (2.0 * PI * i / segments).toFloat()
            val c = cos(angle)
            val s = sin(angle)
            val u = i.toFloat() / segments.toFloat()
            vertices += Geometry.Vertex(
                position = Position(inner * c, 0f, inner * s),
                normal = up,
                uvCoordinate = UvCoordinate(u, 0f)
            )
            vertices += Geometry.Vertex(
                position = Position(outer * c, 0f, outer * s),
                normal = up,
                uvCoordinate = UvCoordinate(u, 1f)
            )
            if (FogMistAppearance.DOUBLE_SIDED) {
                vertices += Geometry.Vertex(
                    position = Position(inner * c, 0f, inner * s),
                    normal = down,
                    uvCoordinate = UvCoordinate(u, 0f)
                )
                vertices += Geometry.Vertex(
                    position = Position(outer * c, 0f, outer * s),
                    normal = down,
                    uvCoordinate = UvCoordinate(u, 1f)
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
            if (FogMistAppearance.DOUBLE_SIDED) {
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
