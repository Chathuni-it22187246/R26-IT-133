package com.greenhands.app.ar.render

import com.google.android.filament.Engine
import com.greenhands.app.heat.visualization.FogMistAppearance
import io.github.sceneview.geometries.Geometry
import io.github.sceneview.geometries.UvCoordinate
import io.github.sceneview.math.Direction
import io.github.sceneview.math.Position
import kotlin.math.sqrt

data class FogCloudFlake(
    val x: Float,
    val y: Float,
    val z: Float,
    val size: Float,
    val streak: Float,
    val fade: Float,
    val dx: Float,
    val dy: Float,
    val dz: Float
) {
    companion object {
        val HIDDEN = FogCloudFlake(
            x = 0f, y = 0f, z = 0f,
            size = 0.0004f, streak = 0.0004f, fade = 0f,
            dx = 0f, dy = -1f, dz = 0f
        )
    }
}

/**
 * Combined mist mesh of short crossed micro-flakes. Two perpendicular diamonds
 * stay visible from orthogonal views; a single thin plane would vanish edge-on.
 * Density is qualitative and is not a water-flow rate. Vertex buffers are
 * updated in place; no extra SceneView node is created per flake.
 */
object FogDropletMesh {
    /** Two double-sided diamonds: 2 planes × 4 triangles × 3 vertices. */
    const val VERTICES_PER_FLAKE = 24
    const val CROSSED_PLANES = FogMistAppearance.CROSSED_PLANES

    fun create(engine: Engine): Geometry {
        val s = FogMistAppearance.DROPLET_SIZE_M.toFloat()
        val streak = FogMistAppearance.STREAK_LENGTH_M.toFloat()
        return createCloud(
            engine,
            listOf(
                FogCloudFlake(0f, 0f, 0f, s, streak, 1f, 0.25f, -0.90f, 0.35f)
            )
        )
    }

    fun createCloud(engine: Engine, flakes: List<FogCloudFlake>): Geometry {
        val source = flakes.ifEmpty { listOf(FogCloudFlake.HIDDEN) }
        return Geometry.Builder()
            .vertices(verticesOf(source))
            .indices(indicesOf(source.size))
            .build(engine)
    }

    fun verticesOf(flakes: List<FogCloudFlake>): List<Geometry.Vertex> {
        val source = flakes.ifEmpty { listOf(FogCloudFlake.HIDDEN) }
        val vertices = ArrayList<Geometry.Vertex>(source.size * VERTICES_PER_FLAKE)
        source.forEach { addCrossedFlake(vertices, it) }
        return vertices
    }

    fun indicesOf(flakeCount: Int): List<Int> {
        val n = flakeCount.coerceAtLeast(1) * VERTICES_PER_FLAKE
        return List(n) { it }
    }

    fun padded(flakes: List<FogCloudFlake>, slotCount: Int): List<FogCloudFlake> {
        val slots = slotCount.coerceAtLeast(1)
        if (flakes.size == slots) return flakes
        return List(slots) { index -> flakes.getOrElse(index) { FogCloudFlake.HIDDEN } }
    }

    fun clampedAspect(size: Float, streak: Float): Float {
        val width = size.coerceAtLeast(1e-6f)
        val length = streak.coerceAtLeast(0f).coerceAtMost(
            (width * FogMistAppearance.MAX_STREAK_TO_WIDTH).toFloat()
        )
        return length / width
    }

    fun visibleFromOrthogonalViews(
        vertices: List<Geometry.Vertex>,
        minSpan: Float = 0.008f
    ): Boolean {
        if (vertices.size < 3) return false
        val xs = vertices.map { it.position.x }
        val ys = vertices.map { it.position.y }
        val zs = vertices.map { it.position.z }
        val dx = xs.max() - xs.min()
        val dy = ys.max() - ys.min()
        val dz = zs.max() - zs.min()
        return dx > minSpan && dy > minSpan && dz > minSpan &&
            dx * dy > minSpan * minSpan &&
            dy * dz > minSpan * minSpan &&
            dz * dx > minSpan * minSpan
    }

    private fun addCrossedFlake(vertices: MutableList<Geometry.Vertex>, flake: FogCloudFlake) {
        val fade = flake.fade.coerceIn(0f, 1f)
        val visible = fade > 0.04f
        val w = if (visible) {
            (flake.size * (0.55f + 0.45f * fade)).coerceIn(0.010f, 0.055f)
        } else {
            0.0004f
        }
        val requested = if (visible) {
            (flake.streak * (0.60f + 0.40f * fade)).coerceIn(0.012f, 0.10f)
        } else {
            0.0004f
        }
        val len = requested.coerceAtMost(w * FogMistAppearance.MAX_STREAK_TO_WIDTH.toFloat())
        var dx = flake.dx
        var dy = flake.dy
        var dz = flake.dz
        val mag = sqrt(dx * dx + dy * dy + dz * dz)
        if (mag < 1e-6f) {
            dx = 0.25f
            dy = -0.90f
            dz = 0.35f
        } else {
            dx /= mag
            dy /= mag
            dz /= mag
        }
        val p1 = perpendicular(dx, dy, dz)
        val p2 = cross(dx, dy, dz, p1.first, p1.second, p1.third)
        addDiamond(vertices, flake.x, flake.y, flake.z, dx, dy, dz, p1, len, w)
        addDiamond(vertices, flake.x, flake.y, flake.z, dx, dy, dz, p2, len, w)
    }

    private fun addDiamond(
        vertices: MutableList<Geometry.Vertex>,
        ox: Float,
        oy: Float,
        oz: Float,
        dx: Float,
        dy: Float,
        dz: Float,
        lateral: Triple<Float, Float, Float>,
        len: Float,
        w: Float
    ) {
        val hx = dx * len * 0.5f
        val hy = dy * len * 0.5f
        val hz = dz * len * 0.5f
        val wx = lateral.first * w
        val wy = lateral.second * w
        val wz = lateral.third * w
        val a = Position(ox + hx, oy + hy, oz + hz)
        val b = Position(ox - hx, oy - hy, oz - hz)
        val c = Position(ox + wx, oy + wy, oz + wz)
        val d = Position(ox - wx, oy - wy, oz - wz)
        addTriangle(vertices, a, c, b)
        addTriangle(vertices, a, b, d)
        if (FogMistAppearance.DOUBLE_SIDED) {
            addTriangle(vertices, a, b, c)
            addTriangle(vertices, a, d, b)
        }
    }

    private fun perpendicular(dx: Float, dy: Float, dz: Float): Triple<Float, Float, Float> {
        val ax = if (kotlin.math.abs(dx) < 0.9f) 1f else 0f
        val ay = if (kotlin.math.abs(dx) < 0.9f) 0f else 1f
        return cross(dx, dy, dz, ax, ay, 0f)
    }

    private fun cross(
        ax: Float, ay: Float, az: Float,
        bx: Float, by: Float, bz: Float
    ): Triple<Float, Float, Float> {
        val cx = ay * bz - az * by
        val cy = az * bx - ax * bz
        val cz = ax * by - ay * bx
        val mag = sqrt(cx * cx + cy * cy + cz * cz).coerceAtLeast(1e-6f)
        return Triple(cx / mag, cy / mag, cz / mag)
    }

    private fun addTriangle(
        vertices: MutableList<Geometry.Vertex>,
        p0: Position,
        p1: Position,
        p2: Position
    ) {
        val nx = (p1.y - p0.y) * (p2.z - p0.z) - (p1.z - p0.z) * (p2.y - p0.y)
        val ny = (p1.z - p0.z) * (p2.x - p0.x) - (p1.x - p0.x) * (p2.z - p0.z)
        val nz = (p1.x - p0.x) * (p2.y - p0.y) - (p1.y - p0.y) * (p2.x - p0.x)
        val normal = Direction(x = nx, y = ny, z = nz)
        vertices += Geometry.Vertex(position = p0, normal = normal, uvCoordinate = UvCoordinate(0f, 0f))
        vertices += Geometry.Vertex(position = p1, normal = normal, uvCoordinate = UvCoordinate(1f, 0f))
        vertices += Geometry.Vertex(position = p2, normal = normal, uvCoordinate = UvCoordinate(0.5f, 1f))
    }
}
