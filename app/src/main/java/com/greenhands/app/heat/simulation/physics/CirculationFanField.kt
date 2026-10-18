package com.greenhands.app.heat.simulation.physics

import com.greenhands.app.heat.simulation.model.CirculationFanProfile
import com.greenhands.app.heat.simulation.model.GridState
import com.greenhands.app.heat.simulation.model.Vec3
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Parameterized turbulent jet (PHYSICS_MODEL.md §9.1). Circulation adds momentum/mixing only.
 * `jetDecayCoefficient` (k_u), `jetSpreadRate` (k_b) and `jetCoreLengthM` (x_0) are calibrated.
 */
object CirculationFanField {
    private const val VIRTUAL_ORIGIN_EPSILON_M = 1e-6

    fun outletVelocityMps(fan: CirculationFanProfile, activeSpeedFraction: Double): Double {
        val q = fan.volumeFlowM3s(activeSpeedFraction)
        val a0 = fan.outletAreaM2()
        return if (a0 <= 0.0) 0.0 else q / a0
    }

    fun velocityContributionMps(
        fan: CirculationFanProfile,
        pointM: Vec3,
        activeSpeedFraction: Double
    ): Vec3 {
        if (activeSpeedFraction <= 0.0) return Vec3(0.0, 0.0, 0.0)
        val dHat = fan.directionHat()
        val offset = pointM.minus(fan.positionM)
        val sM = offset.dot(dHat)
        if (sM <= 0.0) return Vec3(0.0, 0.0, 0.0)
        val radial = offset.minus(dHat.times(sM))
        val rM = radial.lengthM()
        val diameterM = fan.outletDiameterM
        val bM = diameterM * 0.5 + fan.jetSpreadRate * sM
        if (bM <= 0.0) return Vec3(0.0, 0.0, 0.0)
        val u0 = outletVelocityMps(fan, activeSpeedFraction)
        val decayArg = maxOf(sM - fan.jetCoreLengthM, VIRTUAL_ORIGIN_EPSILON_M)
        val uc = u0 * min(1.0, fan.jetDecayCoefficient * diameterM / decayArg)
        val u = uc * exp(-((rM / bM) * (rM / bM)))
        if (!u.isFinite() || u == 0.0) return Vec3(0.0, 0.0, 0.0)
        return dHat.times(u)
    }

    fun applyToGrid(
        grid: GridState,
        fans: List<Pair<CirculationFanProfile, Double>>
    ): Int {
        var capCount = 0
        grid.forEachActive { idx, i, j, k ->
            val p = grid.cellCenterM(i, j, k)
            var vx = 0.0
            var vy = 0.0
            var vz = 0.0
            var capMps = 0.0
            fans.forEach { (fan, speed) ->
                val v = velocityContributionMps(fan, p, speed)
                vx += v.xM
                vy += v.yM
                vz += v.zM
                capMps = maxOf(capMps, fan.maximumModelVelocityMps)
            }
            if (fans.isEmpty()) {
                grid.velocityXMps[idx] = 0.0
                grid.velocityYMps[idx] = 0.0
                grid.velocityZMps[idx] = 0.0
                return@forEachActive
            }
            val speed = sqrt(vx * vx + vy * vy + vz * vz)
            if (capMps > 0.0 && speed > capMps) {
                val s = capMps / speed
                vx *= s
                vy *= s
                vz *= s
                capCount++
            }
            grid.velocityXMps[idx] = vx
            grid.velocityYMps[idx] = vy
            grid.velocityZMps[idx] = vz
        }
        return capCount
    }
}
