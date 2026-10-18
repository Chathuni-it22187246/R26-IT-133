package com.greenhands.app.heat.simulation.probe

import com.greenhands.app.heat.simulation.model.CirculationFanProfile
import com.greenhands.app.heat.simulation.model.GridState
import com.greenhands.app.heat.simulation.model.Vec3
import com.greenhands.app.heat.simulation.physics.Psychrometrics
import kotlin.math.sqrt

enum class ProbeStatus {
    PREDICTED,
    OUTSIDE_GREENHOUSE,
    NOT_CALIBRATED
}

data class DistanceProbeSample(
    val distanceM: Double,
    val temperatureC: Double?,
    val relativeHumidityPercent: Double?,
    val airSpeedMps: Double?,
    val status: ProbeStatus
)

/**
 * 1–5 m samples along a selected fan axis from the 3D field (PHYSICS_MODEL.md §12).
 * Never uses a hard-coded temperature-versus-distance curve.
 */
object DistanceProbeSampler {
    val DEFAULT_DISTANCES_M: DoubleArray = doubleArrayOf(1.0, 2.0, 3.0, 4.0, 5.0)

    fun sampleFanAxis(
        grid: GridState,
        fan: CirculationFanProfile,
        pressurePa: Double,
        distancesM: DoubleArray = DEFAULT_DISTANCES_M
    ): List<DistanceProbeSample> {
        val dHat = fan.directionHat()
        return distancesM.map { distanceM ->
            val point = fan.positionM.plus(dHat.times(distanceM))
            samplePoint(grid, point, distanceM, pressurePa)
        }
    }

    fun samplePoint(
        grid: GridState,
        pointM: Vec3,
        distanceM: Double,
        pressurePa: Double
    ): DistanceProbeSample {
        if (!grid.geometry.contains(pointM)) {
            return DistanceProbeSample(
                distanceM = distanceM,
                temperatureC = null,
                relativeHumidityPercent = null,
                airSpeedMps = null,
                status = ProbeStatus.OUTSIDE_GREENHOUSE
            )
        }
        val tC = grid.interpolateScalar(grid.temperatureC, pointM)
        val omega = grid.interpolateScalar(grid.humidityRatioKgKg, pointM)
        val velocity = grid.interpolateVelocity(pointM)
        if (tC == null || omega == null || velocity == null) {
            return DistanceProbeSample(
                distanceM = distanceM,
                temperatureC = null,
                relativeHumidityPercent = null,
                airSpeedMps = null,
                status = ProbeStatus.OUTSIDE_GREENHOUSE
            )
        }
        val rh = Psychrometrics.displayedRelativeHumidityPercent(tC, omega, pressurePa)
        val speed = sqrt(velocity.xM * velocity.xM + velocity.yM * velocity.yM + velocity.zM * velocity.zM)
        return DistanceProbeSample(
            distanceM = distanceM,
            temperatureC = tC,
            relativeHumidityPercent = rh,
            airSpeedMps = speed,
            status = ProbeStatus.PREDICTED
        )
    }
}
