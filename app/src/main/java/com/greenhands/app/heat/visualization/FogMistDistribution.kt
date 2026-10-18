package com.greenhands.app.heat.visualization

import com.greenhands.app.equipment.model.DomainPoint
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Deterministic UUID-seeded mist-cloud parameters. The same fogger UUID always
 * produces the same cloud; different UUIDs differ. Not a random per-frame field.
 * Flakes fill the accepted cone disk across several depth layers, with inner
 * and outer radial samples in every quadrant.
 */
object FogMistDistribution {
    fun particles(
        equipmentUuid: String,
        count: Int,
        outletCount: Int
    ): List<FoggerMistParticle> {
        val n = count.coerceIn(0, FogMistVisual.MAX_PARTICLES_PER_FOGGER)
        val outlets = outletCount.coerceAtLeast(1)
        if (n <= 0) return emptyList()
        val layers = FogMistVisual.DEPTH_LAYERS.coerceAtLeast(2)
        val azimuths = FogMistVisual.AZIMUTHS_PER_LAYER.coerceAtLeast(4)
        val spin = unit01(equipmentUuid, 0, 0) * 2.0 * Math.PI
        return List(n) { index ->
            val layer = index / azimuths
            val slot = index % azimuths
            val dJitter = 0.08 * (unit01(equipmentUuid, index, 3) - 0.5)
            val distanceFraction = (
                (layer + 0.40) / layers.toDouble() + dJitter
                ).coerceIn(0.03, 0.97)
            val azimuth = slot * (2.0 * Math.PI / azimuths) +
                spin +
                layer * 0.13 +
                unit01(equipmentUuid, index, 4) * 0.12
            val inner = FogMistVisual.MIN_RADIAL_FRACTION +
                (FogMistVisual.INNER_RADIAL_MAX - FogMistVisual.MIN_RADIAL_FRACTION) *
                unit01(equipmentUuid, index, 1)
            val outer = FogMistVisual.OUTER_RADIAL_MIN +
                (1.0 - FogMistVisual.OUTER_RADIAL_MIN) * unit01(equipmentUuid, index, 1)
            val radialFraction = if (slot % 3 == 0) inner else outer
            FoggerMistParticle(
                nozzleIndex = index % outlets,
                azimuthRad = azimuth,
                radialFraction = radialFraction.coerceIn(FogMistVisual.MIN_RADIAL_FRACTION, 1.0),
                faceFraction = 0.08 + 0.55 * unit01(equipmentUuid, index, 2),
                phase = (
                    index.toDouble() / n.toDouble() +
                        unit01(equipmentUuid, index, 5) * 0.41 +
                        FoggerMistAnimator.phaseOffset(equipmentUuid) * 0.13
                    ).let { raw -> raw - kotlin.math.floor(raw) },
                distanceFraction = distanceFraction
            )
        }
    }

    fun unit01(uuid: String, index: Int, lane: Int): Double {
        var hash = 2166136261L
        uuid.forEach { ch ->
            hash = hash xor ch.code.toLong()
            hash *= 16777619L
        }
        hash = hash xor index.toLong()
        hash *= 16777619L
        hash = hash xor lane.toLong()
        hash *= 16777619L
        return ((hash and 0x7fffffffL).toDouble() / 0x7fffffffL).coerceIn(0.0, 1.0)
    }

    fun uniquePhases(particles: List<FoggerMistParticle>): Int =
        particles.map { kotlin.math.round(it.phase * 1000.0) / 1000.0 }.toSet().size

    fun uniqueDistanceLayers(particles: List<FoggerMistParticle>): Int {
        if (particles.isEmpty()) return 0
        return particles.map { (it.distanceFraction * FogMistVisual.DEPTH_LAYERS).toInt() }.toSet().size
    }

    fun uniqueAzimuthBuckets(particles: List<FoggerMistParticle>, buckets: Int = 8): Int {
        if (particles.isEmpty() || buckets <= 0) return 0
        return particles.map { particle ->
            val tau = 2.0 * Math.PI
            val wrapped = ((particle.azimuthRad % tau) + tau) % tau
            (wrapped / tau * buckets).toInt().coerceIn(0, buckets - 1)
        }.toSet().size
    }

    fun occupiesAllRadialQuadrants(points: List<DomainPoint>, origin: DomainPoint): Boolean {
        if (points.size < 8) return false
        val seen = BooleanArray(4)
        points.forEach { point ->
            val dx = point.xM - origin.xM
            val dy = point.yM - origin.yM
            if (kotlin.math.abs(dx) < 0.015 && kotlin.math.abs(dy) < 0.015) return@forEach
            val q = when {
                dx >= 0.0 && dy >= 0.0 -> 0
                dx < 0.0 && dy >= 0.0 -> 1
                dx < 0.0 && dy < 0.0 -> 2
                else -> 3
            }
            seen[q] = true
        }
        return seen.all { it }
    }

    fun hasCentreAndOuterRadialSamples(particles: List<FoggerMistParticle>): Boolean {
        if (particles.size < 8) return false
        val inner = particles.any { it.radialFraction <= FogMistVisual.INNER_RADIAL_MAX }
        val outer = particles.any { it.radialFraction >= FogMistVisual.OUTER_RADIAL_MIN }
        val notOnAxis = particles.none { it.radialFraction < FogMistVisual.MIN_RADIAL_FRACTION - 1e-9 }
        return inner && outer && notOnAxis
    }

    fun meanRadiusInAlongBand(
        points: List<DomainPoint>,
        alongs: List<Double>,
        origin: DomainPoint,
        loM: Double,
        hiM: Double
    ): Double {
        val radii = points.indices.mapNotNull { index ->
            val along = alongs.getOrNull(index) ?: return@mapNotNull null
            if (along < loM || along > hiM) return@mapNotNull null
            val point = points[index]
            val dx = point.xM - origin.xM
            val dy = point.yM - origin.yM
            hypot2(dx, dy)
        }
        if (radii.isEmpty()) return 0.0
        return radii.average()
    }

    fun coversMultipleRadialDirections(points: List<DomainPoint>, origin: DomainPoint): Boolean {
        if (points.size < 4) return false
        val dx = points.map { it.xM - origin.xM }
        val dy = points.map { it.yM - origin.yM }
        return dx.any { it > 0.02 } &&
            dx.any { it < -0.02 } &&
            dy.any { it > 0.02 } &&
            dy.any { it < -0.02 }
    }

    fun combinedMeshIncreasesVisibleSpecksWithoutNodeGrowth(): Boolean =
        FogMistVisual.MAX_PARTICLES_PER_FOGGER > FogMistVisual.MAX_SCENE_NODES_PER_FOGGER &&
            FogMistVisual.MAX_SCENE_NODES_PER_FOGGER in 1..3 &&
            FogMistVisual.MAX_TOTAL_SCENE_NODES <=
            FogMistVisual.MAX_FOGGER_GROUPS * FogMistVisual.MAX_SCENE_NODES_PER_FOGGER &&
            FogMistVisual.MAX_TOTAL_PARTICLES ==
            FogMistVisual.MAX_FOGGER_GROUPS * FogMistVisual.MAX_PARTICLES_PER_FOGGER

    fun tiltedAxis(
        equipmentUuid: String,
        index: Int,
        sprayX: Double,
        sprayY: Double,
        sprayZ: Double
    ): Triple<Double, Double, Double> {
        val mag = hypot3(sprayX, sprayY, sprayZ).coerceAtLeast(1e-9)
        val nx = sprayX / mag
        val ny = sprayY / mag
        val nz = sprayZ / mag
        val tilt = FogMistAppearance.MIN_TILT_RAD +
            (FogMistAppearance.MAX_TILT_RAD - FogMistAppearance.MIN_TILT_RAD) *
            unit01(equipmentUuid, index, 6)
        val roll = unit01(equipmentUuid, index, 7) * 2.0 * Math.PI
        val ax = if (kotlin.math.abs(nx) < 0.9) 1.0 else 0.0
        val ay = if (kotlin.math.abs(nx) < 0.9) 0.0 else 1.0
        val p = cross(nx, ny, nz, ax, ay, 0.0)
        val c = cross(nx, ny, nz, p.first, p.second, p.third)
        val cosR = cos(roll)
        val sinR = sin(roll)
        val rx = p.first * cosR + c.first * sinR
        val ry = p.second * cosR + c.second * sinR
        val rz = p.third * cosR + c.third * sinR
        val cosT = cos(tilt)
        val sinT = sin(tilt)
        return normalize(
            nx * cosT + rx * sinT,
            ny * cosT + ry * sinT,
            nz * cosT + rz * sinT
        )
    }

    fun areCollinear(
        points: List<DomainPoint>,
        epsilonM: Double = FogMistVisual.COLLINEAR_EPSILON_M
    ): Boolean {
        if (points.size < 3) return true
        val origin = points.first()
        var dirX = 0.0
        var dirY = 0.0
        var dirZ = 0.0
        var haveDir = false
        points.drop(1).forEach { point ->
            val dx = point.xM - origin.xM
            val dy = point.yM - origin.yM
            val dz = point.zM - origin.zM
            val length = sqrt(dx * dx + dy * dy + dz * dz)
            if (length <= epsilonM) return@forEach
            if (!haveDir) {
                dirX = dx
                dirY = dy
                dirZ = dz
                haveDir = true
                return@forEach
            }
            val cx = dy * dirZ - dz * dirY
            val cy = dz * dirX - dx * dirZ
            val cz = dx * dirY - dy * dirX
            val area = sqrt(cx * cx + cy * cy + cz * cz)
            if (area > epsilonM * length) return false
        }
        return true
    }

    fun horizontalSpanM(points: List<DomainPoint>): Pair<Double, Double> {
        if (points.isEmpty()) return 0.0 to 0.0
        val xs = points.map { it.xM }
        val ys = points.map { it.yM }
        return (xs.max() - xs.min()) to (ys.max() - ys.min())
    }

    fun downwardSpanM(points: List<DomainPoint>): Double {
        if (points.isEmpty()) return 0.0
        val zs = points.map { it.zM }
        return zs.max() - zs.min()
    }

    private fun hypot2(dx: Double, dy: Double): Double = sqrt(dx * dx + dy * dy)

    private fun hypot3(x: Double, y: Double, z: Double): Double = sqrt(x * x + y * y + z * z)

    private fun cross(
        ax: Double, ay: Double, az: Double,
        bx: Double, by: Double, bz: Double
    ): Triple<Double, Double, Double> {
        val cx = ay * bz - az * by
        val cy = az * bx - ax * bz
        val cz = ax * by - ay * bx
        return normalize(cx, cy, cz)
    }

    private fun normalize(x: Double, y: Double, z: Double): Triple<Double, Double, Double> {
        val mag = hypot3(x, y, z).coerceAtLeast(1e-9)
        return Triple(x / mag, y / mag, z / mag)
    }
}
