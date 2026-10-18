package com.greenhands.app.heat.simulation.physics

import com.greenhands.app.heat.simulation.model.GridState
import com.greenhands.app.heat.simulation.model.Vec3
import kotlin.math.abs
import kotlin.math.sqrt

object VelocityField {
    fun applyUniformOverride(grid: GridState, velocityMps: Vec3) {
        grid.forEachActive { idx, _, _, _ ->
            grid.velocityXMps[idx] = velocityMps.xM
            grid.velocityYMps[idx] = velocityMps.yM
            grid.velocityZMps[idx] = velocityMps.zM
        }
    }

    fun maxAbsComponents(grid: GridState): Triple<Double, Double, Double> {
        var maxX = 0.0
        var maxY = 0.0
        var maxZ = 0.0
        grid.forEachActive { idx, _, _, _ ->
            maxX = maxOf(maxX, abs(grid.velocityXMps[idx]))
            maxY = maxOf(maxY, abs(grid.velocityYMps[idx]))
            maxZ = maxOf(maxZ, abs(grid.velocityZMps[idx]))
        }
        return Triple(maxX, maxY, maxZ)
    }

    fun maxSpeedMps(grid: GridState): Double {
        var max = 0.0
        grid.forEachActive { idx, _, _, _ ->
            val vx = grid.velocityXMps[idx]
            val vy = grid.velocityYMps[idx]
            val vz = grid.velocityZMps[idx]
            val s = sqrt(vx * vx + vy * vy + vz * vz)
            if (s > max) max = s
        }
        return max
    }
}
