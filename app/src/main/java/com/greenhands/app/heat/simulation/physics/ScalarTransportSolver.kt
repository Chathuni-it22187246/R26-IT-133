package com.greenhands.app.heat.simulation.physics

import com.greenhands.app.heat.simulation.model.GridState
import kotlin.math.min

/**
 * Explicit update of temperature and humidity ratio (PHYSICS_MODEL.md §7, §11).
 *
 * Advection is first-order upwind of the advective operator `v·∇φ` with
 * zero-gradient ghosts at roof/wall cells. That matches the incompressible
 * reduced-order equations and does not pile scalar at solid walls.
 *
 * Diffusion is central-difference / conservative face flux with zero-flux walls.
 *
 * Because the parameterized jet is not discretely divergence-free, the discrete
 * advective operator can change the volume mean. After advection a uniform
 * mean-restoring correction is applied so circulation cannot act as a net heat
 * or moisture source; the correction is reported, not hidden.
 */
class ScalarTransportSolver(
    private val grid: GridState,
    private val air: AirProperties,
    private val thermalDiffusivityM2s: Double,
    private val moistureDiffusivityM2s: Double
) {
    private val dTemperatureC = DoubleArray(grid.size)
    private val dHumidityRatioKgKg = DoubleArray(grid.size)

    var lastAdvectionMeanCorrectionK: Double = 0.0
        private set
    var lastAdvectionMeanCorrectionKgKg: Double = 0.0
        private set

    fun stableTimeStepS(cflSafetyFactor: Double, diffusionSafetyFactor: Double): Double {
        val (maxVx, maxVy, maxVz) = VelocityField.maxAbsComponents(grid)
        val dx = grid.dxM
        val dy = grid.dyM
        val dz = grid.dzM
        var dtAdv = Double.POSITIVE_INFINITY
        if (maxVx > 0.0) dtAdv = min(dtAdv, cflSafetyFactor * dx / maxVx)
        if (maxVy > 0.0) dtAdv = min(dtAdv, cflSafetyFactor * dy / maxVy)
        if (maxVz > 0.0) dtAdv = min(dtAdv, cflSafetyFactor * dz / maxVz)
        val laplacianMetric = 1.0 / (dx * dx) + 1.0 / (dy * dy) + 1.0 / (dz * dz)
        var dtDiff = Double.POSITIVE_INFINITY
        if (thermalDiffusivityM2s > 0.0) {
            dtDiff = min(dtDiff, diffusionSafetyFactor / (2.0 * thermalDiffusivityM2s * laplacianMetric))
        }
        if (moistureDiffusivityM2s > 0.0) {
            dtDiff = min(dtDiff, diffusionSafetyFactor / (2.0 * moistureDiffusivityM2s * laplacianMetric))
        }
        val dt = min(dtAdv, dtDiff)
        return if (dt.isFinite() && dt > 0.0) dt else Double.POSITIVE_INFINITY
    }

    fun substepCount(requestedDtS: Double, stableDtS: Double, maxSubsteps: Int): Int {
        if (!requestedDtS.isFinite() || requestedDtS <= 0.0) {
            throw SimulationException("Requested time step must be finite and > 0 s")
        }
        if (!stableDtS.isFinite() || stableDtS <= 0.0) {
            return 1
        }
        val n = kotlin.math.ceil(requestedDtS / stableDtS).toInt().coerceAtLeast(1)
        if (n > maxSubsteps) {
            throw SimulationException(
                "Requested dt=$requestedDtS s needs $n substeps (stable dt=$stableDtS s); " +
                    "exceeds cap $maxSubsteps. Reduce velocity, increase cell size, or reduce dt."
            )
        }
        return n
    }

    fun step(dtS: Double) {
        java.util.Arrays.fill(dTemperatureC, 0.0)
        java.util.Arrays.fill(dHumidityRatioKgKg, 0.0)
        accumulateAdvection(dtS)
        restoreAdvectiveMean()
        accumulateDiffusion(dtS)
        accumulateSources(dtS)
        applyIncrements()
    }

    fun correctSupersaturation(pressurePa: Double): SaturationCorrectionResult {
        var condensedKg = 0.0
        var cells = 0
        grid.forEachActive { idx, _, _, _ ->
            val tC = grid.temperatureC[idx]
            val omega = grid.humidityRatioKgKg[idx]
            val omegaSat = Psychrometrics.saturationHumidityRatioKgKg(tC, pressurePa)
            if (omega > omegaSat) {
                val excess = omega - omegaSat
                grid.humidityRatioKgKg[idx] = omegaSat
                val hfg = Psychrometrics.latentHeatVaporizationJkg(tC)
                val dTemp = (excess * air.dryAirDensityKgM3 * hfg) /
                    (air.airDensityKgM3 * air.specificHeatJkgK)
                grid.temperatureC[idx] = tC + dTemp
                condensedKg += excess * air.dryAirDensityKgM3 * grid.cellVolumeM3
                cells++
            }
        }
        return SaturationCorrectionResult(condensedKg, cells)
    }

    private fun accumulateAdvection(dtS: Double) {
        val dx = grid.dxM
        val dy = grid.dyM
        val dz = grid.dzM
        grid.forEachActive { idx, i, j, k ->
            val tC = grid.temperatureC[idx]
            val w = grid.humidityRatioKgKg[idx]
            val vx = grid.velocityXMps[idx]
            val vy = grid.velocityYMps[idx]
            val vz = grid.velocityZMps[idx]
            val tXm = scalarAt(grid.temperatureC, i - 1, j, k, tC)
            val tXp = scalarAt(grid.temperatureC, i + 1, j, k, tC)
            val tYm = scalarAt(grid.temperatureC, i, j - 1, k, tC)
            val tYp = scalarAt(grid.temperatureC, i, j + 1, k, tC)
            val tZm = scalarAt(grid.temperatureC, i, j, k - 1, tC)
            val tZp = scalarAt(grid.temperatureC, i, j, k + 1, tC)
            val wXm = scalarAt(grid.humidityRatioKgKg, i - 1, j, k, w)
            val wXp = scalarAt(grid.humidityRatioKgKg, i + 1, j, k, w)
            val wYm = scalarAt(grid.humidityRatioKgKg, i, j - 1, k, w)
            val wYp = scalarAt(grid.humidityRatioKgKg, i, j + 1, k, w)
            val wZm = scalarAt(grid.humidityRatioKgKg, i, j, k - 1, w)
            val wZp = scalarAt(grid.humidityRatioKgKg, i, j, k + 1, w)
            val dTdx = if (vx >= 0.0) (tC - tXm) / dx else (tXp - tC) / dx
            val dTdy = if (vy >= 0.0) (tC - tYm) / dy else (tYp - tC) / dy
            val dTdz = if (vz >= 0.0) (tC - tZm) / dz else (tZp - tC) / dz
            val dWdx = if (vx >= 0.0) (w - wXm) / dx else (wXp - w) / dx
            val dWdy = if (vy >= 0.0) (w - wYm) / dy else (wYp - w) / dy
            val dWdz = if (vz >= 0.0) (w - wZm) / dz else (wZp - w) / dz
            dTemperatureC[idx] -= dtS * (vx * dTdx + vy * dTdy + vz * dTdz)
            dHumidityRatioKgKg[idx] -= dtS * (vx * dWdx + vy * dWdy + vz * dWdz)
        }
    }

    private fun restoreAdvectiveMean() {
        var accT = 0.0
        var accW = 0.0
        var vol = 0.0
        val vCell = grid.cellVolumeM3
        grid.forEachActive { idx, _, _, _ ->
            accT += dTemperatureC[idx] * vCell
            accW += dHumidityRatioKgKg[idx] * vCell
            vol += vCell
        }
        val meanT = accT / vol
        val meanW = accW / vol
        lastAdvectionMeanCorrectionK = -meanT
        lastAdvectionMeanCorrectionKgKg = -meanW
        if (meanT == 0.0 && meanW == 0.0) return
        grid.forEachActive { idx, _, _, _ ->
            dTemperatureC[idx] -= meanT
            dHumidityRatioKgKg[idx] -= meanW
        }
    }

    private fun accumulateDiffusion(dtS: Double) {
        accumulateDiffusiveAxis(dtS, 1, 0, 0, grid.dxM)
        accumulateDiffusiveAxis(dtS, 0, 1, 0, grid.dyM)
        accumulateDiffusiveAxis(dtS, 0, 0, 1, grid.dzM)
    }

    private fun accumulateDiffusiveAxis(
        dtS: Double,
        di: Int,
        dj: Int,
        dk: Int,
        spacingM: Double
    ) {
        val iMax = grid.nx - if (di != 0) 1 else 0
        val jMax = grid.ny - if (dj != 0) 1 else 0
        val kMax = grid.nz - if (dk != 0) 1 else 0
        var i = 0
        while (i < iMax) {
            var j = 0
            while (j < jMax) {
                var k = 0
                while (k < kMax) {
                    val idxL = grid.index(i, j, k)
                    val idxR = grid.index(i + di, j + dj, k + dk)
                    if (grid.active[idxL] && grid.active[idxR]) {
                        val dT = grid.temperatureC[idxR] - grid.temperatureC[idxL]
                        val dW = grid.humidityRatioKgKg[idxR] - grid.humidityRatioKgKg[idxL]
                        val tDiff = dtS * thermalDiffusivityM2s * dT / (spacingM * spacingM)
                        val wDiff = dtS * moistureDiffusivityM2s * dW / (spacingM * spacingM)
                        dTemperatureC[idxL] += tDiff
                        dTemperatureC[idxR] -= tDiff
                        dHumidityRatioKgKg[idxL] += wDiff
                        dHumidityRatioKgKg[idxR] -= wDiff
                    }
                    k++
                }
                j++
            }
            i++
        }
    }

    private fun accumulateSources(dtS: Double) {
        val rhoCp = air.airDensityKgM3 * air.specificHeatJkgK
        val rhoDa = air.dryAirDensityKgM3
        grid.forEachActive { idx, _, _, _ ->
            dTemperatureC[idx] += dtS * grid.heatSourceWm3[idx] / rhoCp
            dHumidityRatioKgKg[idx] += dtS * grid.moistureSourceKgM3s[idx] / rhoDa
        }
    }

    private fun applyIncrements() {
        grid.forEachActive { idx, _, _, _ ->
            grid.temperatureC[idx] += dTemperatureC[idx]
            grid.humidityRatioKgKg[idx] += dHumidityRatioKgKg[idx]
        }
    }

    private fun scalarAt(field: DoubleArray, i: Int, j: Int, k: Int, fallback: Double): Double {
        return if (grid.isActive(i, j, k)) field[grid.index(i, j, k)] else fallback
    }
}

data class SaturationCorrectionResult(
    val condensedVapourKg: Double,
    val affectedCellCount: Int
)
