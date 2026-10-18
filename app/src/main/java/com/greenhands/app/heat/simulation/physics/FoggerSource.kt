package com.greenhands.app.heat.simulation.physics

import com.greenhands.app.heat.simulation.model.FoggerProfile
import com.greenhands.app.heat.simulation.model.GridState
import kotlin.math.max
import kotlin.math.tan

data class FoggerApplyResult(
    val evaporatedKgS: Double,
    val nonEvaporatedKgS: Double,
    val limitedBySaturationKgS: Double,
    val latentHeatRemovalW: Double
)

/**
 * Fogger: only evaporated water removes latent heat and adds moisture (PHYSICS_MODEL.md §9.3).
 */
object FoggerSource {
    fun apply(
        grid: GridState,
        air: AirProperties,
        foggers: List<FoggerProfile>,
        active: Boolean,
        dtS: Double,
        pressurePa: Double
    ): FoggerApplyResult {
        if (!active || dtS <= 0.0 || foggers.isEmpty()) {
            return FoggerApplyResult(0.0, 0.0, 0.0, 0.0)
        }
        var evaporated = 0.0
        var nonEvaporated = 0.0
        var limited = 0.0
        var heatW = 0.0
        foggers.forEach { fogger ->
            val waterKgS = fogger.waterFlowKgS
            if (waterKgS <= 0.0) return@forEach
            val proposedEvapKgS = fogger.evaporationEfficiency * waterKgS
            nonEvaporated += waterKgS - proposedEvapKgS
            if (proposedEvapKgS <= 0.0) return@forEach
            val weights = plumeWeights(grid, fogger)
            var remainingProposed = proposedEvapKgS
            var actualEvap = 0.0
            weights.forEach { (idx, w) ->
                val proposedCell = proposedEvapKgS * w
                val allowed = maxEvaporationKgS(grid, air, idx, proposedCell, dtS, pressurePa)
                val used = minOf(proposedCell, allowed)
                remainingProposed -= proposedCell
                actualEvap += used
                limited += proposedCell - used
                if (used > 0.0) {
                    val tC = grid.temperatureC[idx]
                    val hfg = Psychrometrics.latentHeatVaporizationJkg(tC)
                    val qW = -used * hfg
                    grid.heatSourceWm3[idx] += qW / grid.cellVolumeM3
                    grid.moistureSourceKgM3s[idx] += used / grid.cellVolumeM3
                    heatW += qW
                }
            }
            evaporated += actualEvap
        }
        return FoggerApplyResult(evaporated, nonEvaporated, limited, -heatW)
    }

    private fun plumeWeights(grid: GridState, fogger: FoggerProfile): List<Pair<Int, Double>> {
        val dHat = fogger.directionHat()
        val halfRad = Math.toRadians(fogger.plumeAngleDeg * 0.5)
        val tanHalf = tan(halfRad)
        val raw = ArrayList<Pair<Int, Double>>()
        var sum = 0.0
        grid.forEachActive { idx, i, j, k ->
            val p = grid.cellCenterM(i, j, k)
            val offset = p.minus(fogger.positionM)
            val sM = offset.dot(dHat)
            if (sM <= 0.0 || sM > fogger.plumeLengthM) return@forEachActive
            val rM = offset.minus(dHat.times(sM)).lengthM()
            val rMax = sM * tanHalf
            if (rM > rMax) return@forEachActive
            val radial = 1.0 - rM / max(rMax, 1e-9)
            val axial = 1.0 - sM / fogger.plumeLengthM
            val w = max(0.0, radial * axial)
            if (w > 0.0) {
                raw.add(idx to w)
                sum += w
            }
        }
        if (raw.isEmpty() || sum <= 0.0) {
            val nearest = grid.nearestActiveIndex(fogger.positionM) ?: return emptyList()
            return listOf(nearest to 1.0)
        }
        return raw.map { it.first to it.second / sum }
    }

    private fun maxEvaporationKgS(
        grid: GridState,
        air: AirProperties,
        idx: Int,
        proposedKgS: Double,
        dtS: Double,
        pressurePa: Double
    ): Double {
        if (proposedKgS <= 0.0) return 0.0
        val vol = grid.cellVolumeM3
        val rhoCp = air.airDensityKgM3 * air.specificHeatJkgK
        val rhoDa = air.dryAirDensityKgM3
        var m = proposedKgS
        repeat(5) {
            val tC = grid.temperatureC[idx]
            val hfg = Psychrometrics.latentHeatVaporizationJkg(tC)
            val tNew = tC + dtS * (-m * hfg / vol) / rhoCp
            if (tNew < Psychrometrics.MIN_TEMPERATURE_C) {
                val minDt = Psychrometrics.MIN_TEMPERATURE_C - tC
                m = if (hfg > 0.0 && dtS > 0.0) {
                    max(0.0, -minDt * rhoCp * vol / (dtS * hfg))
                } else {
                    0.0
                }
                return@repeat
            }
            val omegaSat = try {
                Psychrometrics.saturationHumidityRatioKgKg(tNew, pressurePa)
            } catch (_: SimulationException) {
                return 0.0
            }
            val omegaNew = grid.humidityRatioKgKg[idx] + dtS * (m / vol) / rhoDa
            if (omegaNew <= omegaSat + 1e-12) return m
            val maxDOmega = max(0.0, omegaSat - grid.humidityRatioKgKg[idx])
            m = maxDOmega * rhoDa * vol / dtS
        }
        return max(0.0, m)
    }
}
