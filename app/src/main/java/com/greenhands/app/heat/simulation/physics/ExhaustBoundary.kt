package com.greenhands.app.heat.simulation.physics

import com.greenhands.app.heat.simulation.model.ExhaustFanProfile
import com.greenhands.app.heat.simulation.model.GridState
import com.greenhands.app.heat.simulation.model.IntakeOpening
import kotlin.math.abs
import kotlin.math.exp

data class ExhaustFlowReport(
    val intakeVolumeFlowM3s: Double,
    val exhaustVolumeFlowM3s: Double,
    val wellMixed: Boolean
) {
    val balanced: Boolean
        get() = abs(intakeVolumeFlowM3s - exhaustVolumeFlowM3s) <=
            1e-12 * maxOf(1.0, maxOf(abs(intakeVolumeFlowM3s), abs(exhaustVolumeFlowM3s)))
}

/**
 * Exhaust removes indoor air; make-up air enters at outdoor T and ω (PHYSICS_MODEL.md §9.2).
 * No intake geometry → labelled well-mixed ventilation estimate.
 */
object ExhaustBoundary {
    fun totalExhaustVolumeFlowM3s(fans: List<ExhaustFanProfile>, active: Boolean): Double {
        if (!active) return 0.0
        var q = 0.0
        fans.forEach { fan -> q += fan.volumeFlowM3s(fan.speedFraction) }
        return q
    }

    fun applySpatialIntakeSources(
        grid: GridState,
        air: AirProperties,
        intakes: List<IntakeOpening>,
        exhaustVolumeFlowM3s: Double,
        outsideTemperatureC: Double,
        outsideHumidityRatioKgKg: Double
    ): ExhaustFlowReport {
        val qEx = exhaustVolumeFlowM3s
        if (qEx <= 0.0 || intakes.isEmpty()) {
            return ExhaustFlowReport(0.0, qEx, wellMixed = false)
        }
        val totalArea = intakes.sumOf { it.areaM2 }
        val qAt = DoubleArray(grid.size)
        intakes.forEach { intake ->
            val idx = grid.nearestActiveIndex(intake.positionM) ?: return@forEach
            val weight = if (totalArea > 0.0) intake.areaM2 / totalArea else 1.0 / intakes.size
            qAt[idx] += qEx * weight
        }
        val rhoCp = air.airDensityKgM3 * air.specificHeatJkgK
        val rhoDa = air.dryAirDensityKgM3
        grid.forEachActive { idx, _, _, _ ->
            val q = qAt[idx]
            if (q > 0.0) {
                val mixPerS = q / grid.cellVolumeM3
                grid.heatSourceWm3[idx] += rhoCp * mixPerS * (outsideTemperatureC - grid.temperatureC[idx])
                grid.moistureSourceKgM3s[idx] +=
                    rhoDa * mixPerS * (outsideHumidityRatioKgKg - grid.humidityRatioKgKg[idx])
            }
        }
        return ExhaustFlowReport(qEx, qEx, wellMixed = false)
    }

    fun applyWellMixedExact(
        grid: GridState,
        volumeFlowM3s: Double,
        outsideTemperatureC: Double,
        outsideHumidityRatioKgKg: Double,
        dtS: Double
    ): ExhaustFlowReport {
        val q = volumeFlowM3s
        if (q <= 0.0) return ExhaustFlowReport(0.0, 0.0, wellMixed = true)
        val volume = grid.totalActiveVolumeM3()
        val decay = exp(-q / volume * dtS)
        val tMean = grid.meanTemperatureC()
        val wMean = grid.meanHumidityRatioKgKg()
        val dT = outsideTemperatureC + (tMean - outsideTemperatureC) * decay - tMean
        val dW = outsideHumidityRatioKgKg + (wMean - outsideHumidityRatioKgKg) * decay - wMean
        grid.forEachActive { idx, _, _, _ ->
            grid.temperatureC[idx] += dT
            grid.humidityRatioKgKg[idx] += dW
        }
        return ExhaustFlowReport(q, q, wellMixed = true)
    }
}
