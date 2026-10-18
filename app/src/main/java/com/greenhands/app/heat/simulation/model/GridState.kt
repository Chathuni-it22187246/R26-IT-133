package com.greenhands.app.heat.simulation.model

import com.greenhands.app.heat.simulation.physics.SimulationException
import kotlin.math.floor

/**
 * Coarse rectilinear greenhouse grid. Inactive cells lie outside the roof volume.
 */
class GridState(
    val geometry: GreenhouseGeometry,
    val active: BooleanArray,
    val temperatureC: DoubleArray,
    val humidityRatioKgKg: DoubleArray,
    val velocityXMps: DoubleArray,
    val velocityYMps: DoubleArray,
    val velocityZMps: DoubleArray,
    val heatSourceWm3: DoubleArray,
    val moistureSourceKgM3s: DoubleArray
) {
    val nx: Int = geometry.nx
    val ny: Int = geometry.ny
    val nz: Int = geometry.nz
    val size: Int = geometry.cellCount
    val dxM: Double = geometry.cellEdgeM
    val dyM: Double = geometry.cellEdgeM
    val dzM: Double = geometry.cellEdgeM
    val cellVolumeM3: Double = dxM * dyM * dzM
    val activeCellCount: Int = active.count { it }

    init {
        require(active.size == size)
        require(temperatureC.size == size)
        require(humidityRatioKgKg.size == size)
        require(velocityXMps.size == size)
        require(velocityYMps.size == size)
        require(velocityZMps.size == size)
        require(heatSourceWm3.size == size)
        require(moistureSourceKgM3s.size == size)
        if (activeCellCount > GreenhouseGeometry.MAX_ACTIVE_CELLS_PHASE_3A) {
            throw SimulationException(
                "Active cell count $activeCellCount exceeds Phase 3A cap " +
                    "${GreenhouseGeometry.MAX_ACTIVE_CELLS_PHASE_3A}"
            )
        }
        if (activeCellCount == 0) {
            throw SimulationException("Grid has no active air cells")
        }
    }

    fun index(i: Int, j: Int, k: Int): Int = geometry.index(i, j, k)
    fun inBounds(i: Int, j: Int, k: Int): Boolean =
        i in 0 until nx && j in 0 until ny && k in 0 until nz

    fun isActive(i: Int, j: Int, k: Int): Boolean = inBounds(i, j, k) && active[index(i, j, k)]

    fun cellCenterM(i: Int, j: Int, k: Int): Vec3 = geometry.cellCenterM(i, j, k)

    fun totalActiveVolumeM3(): Double = activeCellCount * cellVolumeM3

    inline fun forEachActive(block: (idx: Int, i: Int, j: Int, k: Int) -> Unit) {
        var idx = 0
        while (idx < size) {
            if (active[idx]) {
                block(idx, geometry.iOf(idx), geometry.jOf(idx), geometry.kOf(idx))
            }
            idx++
        }
    }

    fun volumeWeightedMean(values: DoubleArray, region: AxisAlignedRegion? = null): Double {
        var acc = 0.0
        var vol = 0.0
        forEachActive { idx, i, j, k ->
            if (region != null && !region.contains(cellCenterM(i, j, k))) return@forEachActive
            acc += values[idx] * cellVolumeM3
            vol += cellVolumeM3
        }
        if (vol <= 0.0) {
            throw SimulationException("No active cells in the requested averaging region")
        }
        return acc / vol
    }

    fun meanTemperatureC(region: AxisAlignedRegion? = null): Double =
        volumeWeightedMean(temperatureC, region)

    fun meanHumidityRatioKgKg(region: AxisAlignedRegion? = null): Double =
        volumeWeightedMean(humidityRatioKgKg, region)

    fun sensibleEnergyJ(airDensityKgM3: Double, specificHeatJkgK: Double): Double {
        var acc = 0.0
        forEachActive { idx, _, _, _ ->
            acc += airDensityKgM3 * specificHeatJkgK * (temperatureC[idx] + 273.15) * cellVolumeM3
        }
        return acc
    }

    fun vapourMassKg(dryAirDensityKgM3: Double): Double {
        var acc = 0.0
        forEachActive { idx, _, _, _ ->
            acc += dryAirDensityKgM3 * humidityRatioKgKg[idx] * cellVolumeM3
        }
        return acc
    }

    fun zeroSources() {
        java.util.Arrays.fill(heatSourceWm3, 0.0)
        java.util.Arrays.fill(moistureSourceKgM3s, 0.0)
    }

    fun zeroVelocity() {
        java.util.Arrays.fill(velocityXMps, 0.0)
        java.util.Arrays.fill(velocityYMps, 0.0)
        java.util.Arrays.fill(velocityZMps, 0.0)
    }

    fun fillUniform(temperatureCValue: Double, humidityRatioKgKgValue: Double) {
        forEachActive { idx, _, _, _ ->
            temperatureC[idx] = temperatureCValue
            humidityRatioKgKg[idx] = humidityRatioKgKgValue
        }
    }

    fun deepCopy(): GridState = GridState(
        geometry = geometry,
        active = active.copyOf(),
        temperatureC = temperatureC.copyOf(),
        humidityRatioKgKg = humidityRatioKgKg.copyOf(),
        velocityXMps = velocityXMps.copyOf(),
        velocityYMps = velocityYMps.copyOf(),
        velocityZMps = velocityZMps.copyOf(),
        heatSourceWm3 = heatSourceWm3.copyOf(),
        moistureSourceKgM3s = moistureSourceKgM3s.copyOf()
    )

    fun hasFiniteFields(): Boolean {
        var ok = true
        forEachActive { idx, _, _, _ ->
            if (!temperatureC[idx].isFinite() ||
                !humidityRatioKgKg[idx].isFinite() ||
                !velocityXMps[idx].isFinite() ||
                !velocityYMps[idx].isFinite() ||
                !velocityZMps[idx].isFinite()
            ) {
                ok = false
            }
        }
        return ok
    }

    /**
     * Trilinear interpolation of a cell-centered scalar. Returns null when the point
     * is outside the greenhouse roof volume or has no active support.
     */
    fun interpolateScalar(field: DoubleArray, pointM: Vec3): Double? {
        if (!geometry.contains(pointM)) return null
        val fx = pointM.xM / dxM - 0.5
        val fy = pointM.yM / dyM - 0.5
        val fz = pointM.zM / dzM - 0.5
        val i0 = floor(fx).toInt()
        val j0 = floor(fy).toInt()
        val k0 = floor(fz).toInt()
        val tx = fx - i0
        val ty = fy - j0
        val tz = fz - k0
        val c000 = sampleOrNull(field, i0, j0, k0) ?: return fallbackNearest(field, pointM)
        val c100 = sampleOrNull(field, i0 + 1, j0, k0) ?: c000
        val c010 = sampleOrNull(field, i0, j0 + 1, k0) ?: c000
        val c110 = sampleOrNull(field, i0 + 1, j0 + 1, k0) ?: c100
        val c001 = sampleOrNull(field, i0, j0, k0 + 1) ?: c000
        val c101 = sampleOrNull(field, i0 + 1, j0, k0 + 1) ?: c100
        val c011 = sampleOrNull(field, i0, j0 + 1, k0 + 1) ?: c010
        val c111 = sampleOrNull(field, i0 + 1, j0 + 1, k0 + 1) ?: c110
        val c00 = c000 * (1.0 - tx) + c100 * tx
        val c10 = c010 * (1.0 - tx) + c110 * tx
        val c01 = c001 * (1.0 - tx) + c101 * tx
        val c11 = c011 * (1.0 - tx) + c111 * tx
        val c0 = c00 * (1.0 - ty) + c10 * ty
        val c1 = c01 * (1.0 - ty) + c11 * ty
        return c0 * (1.0 - tz) + c1 * tz
    }

    fun interpolateVelocity(pointM: Vec3): Vec3? {
        val vx = interpolateScalar(velocityXMps, pointM) ?: return null
        val vy = interpolateScalar(velocityYMps, pointM) ?: return null
        val vz = interpolateScalar(velocityZMps, pointM) ?: return null
        return Vec3(vx, vy, vz)
    }

    fun nearestActiveIndex(pointM: Vec3): Int? {
        var best = -1
        var bestD = Double.POSITIVE_INFINITY
        forEachActive { idx, i, j, k ->
            val c = cellCenterM(i, j, k)
            val d = (c.xM - pointM.xM).let { dx ->
                val dy = c.yM - pointM.yM
                val dz = c.zM - pointM.zM
                dx * dx + dy * dy + dz * dz
            }
            if (d < bestD) {
                bestD = d
                best = idx
            }
        }
        return if (best >= 0) best else null
    }

    private fun sampleOrNull(field: DoubleArray, i: Int, j: Int, k: Int): Double? {
        if (!isActive(i, j, k)) return null
        return field[index(i, j, k)]
    }

    private fun fallbackNearest(field: DoubleArray, pointM: Vec3): Double? {
        val idx = nearestActiveIndex(pointM) ?: return null
        return field[idx]
    }

    companion object {
        fun build(
            geometry: GreenhouseGeometry,
            initialTemperatureC: Double,
            initialHumidityRatioKgKg: Double
        ): GridState {
            val size = geometry.cellCount
            val active = BooleanArray(size)
            var nActive = 0
            var idx = 0
            while (idx < size) {
                val i = geometry.iOf(idx)
                val j = geometry.jOf(idx)
                val k = geometry.kOf(idx)
                val center = geometry.cellCenterM(i, j, k)
                val isAir = geometry.contains(center)
                active[idx] = isAir
                if (isAir) nActive++
                idx++
            }
            if (nActive > GreenhouseGeometry.MAX_ACTIVE_CELLS_PHASE_3A) {
                throw SimulationException(
                    "Active cell count $nActive exceeds Phase 3A cap " +
                        "${GreenhouseGeometry.MAX_ACTIVE_CELLS_PHASE_3A}"
                )
            }
            val t = DoubleArray(size)
            val w = DoubleArray(size)
            idx = 0
            while (idx < size) {
                if (active[idx]) {
                    t[idx] = initialTemperatureC
                    w[idx] = initialHumidityRatioKgKg
                }
                idx++
            }
            return GridState(
                geometry = geometry,
                active = active,
                temperatureC = t,
                humidityRatioKgKg = w,
                velocityXMps = DoubleArray(size),
                velocityYMps = DoubleArray(size),
                velocityZMps = DoubleArray(size),
                heatSourceWm3 = DoubleArray(size),
                moistureSourceKgM3s = DoubleArray(size)
            )
        }
    }
}
