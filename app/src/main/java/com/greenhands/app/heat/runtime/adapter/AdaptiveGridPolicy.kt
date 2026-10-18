package com.greenhands.app.heat.runtime.adapter

import com.greenhands.app.heat.runtime.PhysicsRuntimeCopy
import com.greenhands.app.heat.simulation.model.GreenhouseGeometry
import com.greenhands.app.heat.simulation.physics.SimulationException
import kotlin.math.ceil
import kotlin.math.min

/**
 * Adaptive coarse-grid spacing within the frozen Phase 3A cell-edge range.
 * Does not alter solver mathematics. Resolution is not frozen for a specific Oppo device.
 */
object AdaptiveGridPolicy {
    data class Config(
        val preferredCellEdgeM: Double = GreenhouseGeometry.DEFAULT_CELL_EDGE_M,
        val minCellEdgeM: Double = GreenhouseGeometry.MIN_CELL_EDGE_M,
        val maxCellEdgeM: Double = GreenhouseGeometry.MAX_CELL_EDGE_M,
        val maximumCellCount: Int = GreenhouseGeometry.MAX_ACTIVE_CELLS_PHASE_3A
    ) {
        init {
            require(preferredCellEdgeM.isFinite() && preferredCellEdgeM > 0.0)
            require(minCellEdgeM.isFinite() && minCellEdgeM > 0.0)
            require(maxCellEdgeM.isFinite() && maxCellEdgeM >= minCellEdgeM)
            require(maximumCellCount >= 1)
        }
    }

    data class Selection(
        val cellEdgeM: Double,
        val nx: Int,
        val ny: Int,
        val nz: Int,
        val cellCount: Int,
        val withinCellLimit: Boolean,
        val requiresOppoBenchmarking: Boolean = true,
        val note: String = PhysicsRuntimeCopy.REQUIRES_OPPO_BENCHMARK
    )

    fun select(
        lengthM: Double,
        widthM: Double,
        heightM: Double,
        config: Config = Config()
    ): Selection {
        if (!lengthM.isFinite() || lengthM <= 0.0 ||
            !widthM.isFinite() || widthM <= 0.0 ||
            !heightM.isFinite() || heightM <= 0.0
        ) {
            throw SimulationException(
                "Adaptive-grid dimensions must be finite and > 0 m (L=$lengthM W=$widthM H=$heightM)"
            )
        }
        val preferred = config.preferredCellEdgeM.coerceIn(config.minCellEdgeM, config.maxCellEdgeM)
        var edge = preferred
        var counts = countsFor(lengthM, widthM, heightM, edge)
        if (counts.cellCount <= config.maximumCellCount) {
            return selection(edge, counts, within = true)
        }
        val coarsest = config.maxCellEdgeM
        val coarse = countsFor(lengthM, widthM, heightM, coarsest)
        if (coarse.cellCount > config.maximumCellCount) {
            return selection(coarsest, coarse, within = false)
        }
        var lo = preferred
        var hi = coarsest
        repeat(40) {
            val mid = 0.5 * (lo + hi)
            val midCounts = countsFor(lengthM, widthM, heightM, mid)
            if (midCounts.cellCount <= config.maximumCellCount) {
                hi = mid
                edge = mid
                counts = midCounts
            } else {
                lo = mid
            }
        }
        edge = min(config.maxCellEdgeM, hi)
        counts = countsFor(lengthM, widthM, heightM, edge)
        val within = counts.cellCount <= config.maximumCellCount
        return selection(edge, counts, within)
    }

    private data class Counts(val nx: Int, val ny: Int, val nz: Int) {
        val cellCount: Int get() = nx * ny * nz
    }

    private fun countsFor(
        lengthM: Double,
        widthM: Double,
        heightM: Double,
        edge: Double
    ): Counts {
        val nx = ceil(lengthM / edge).toInt().coerceAtLeast(1)
        val ny = ceil(widthM / edge).toInt().coerceAtLeast(1)
        val nz = ceil(heightM / edge).toInt().coerceAtLeast(1)
        return Counts(nx, ny, nz)
    }

    private fun selection(
        edge: Double,
        counts: Counts,
        within: Boolean
    ): Selection {
        return Selection(
            cellEdgeM = edge,
            nx = counts.nx,
            ny = counts.ny,
            nz = counts.nz,
            cellCount = counts.cellCount,
            withinCellLimit = within
        )
    }
}
