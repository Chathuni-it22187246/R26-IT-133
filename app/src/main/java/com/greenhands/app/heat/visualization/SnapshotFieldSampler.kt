package com.greenhands.app.heat.visualization

import com.greenhands.app.heat.runtime.adapter.UserPhysicsCoordinateAdapter
import com.greenhands.app.heat.runtime.contracts.PhysicsFieldSnapshot
import com.greenhands.app.heat.simulation.model.GridState
import com.greenhands.app.heat.simulation.model.Vec3
import com.greenhands.app.heat.simulation.physics.Psychrometrics
import com.greenhands.app.heat.simulation.physics.SimulationException

/**
 * Samples the accepted snapshot arrays with the existing Phase 3A trilinear interpolator.
 * Does not introduce a second prediction equation.
 */
object SnapshotFieldSampler {
    @Volatile
    private var cachedSnapshot: PhysicsFieldSnapshot? = null
    @Volatile
    private var cachedGrid: GridState? = null

    fun temperatureC(snapshot: PhysicsFieldSnapshot, physicsPointM: Vec3): Double? {
        val grid = gridOf(snapshot) ?: return null
        return grid.interpolateScalar(grid.temperatureC, physicsPointM)
    }

    fun humidityRatioKgKg(snapshot: PhysicsFieldSnapshot, physicsPointM: Vec3): Double? {
        val grid = gridOf(snapshot) ?: return null
        return grid.interpolateScalar(grid.humidityRatioKgKg, physicsPointM)
    }

    /**
     * Derived RH from transported humidity ratio and local temperature.
     * Does not invent an independent humidity field.
     */
    fun relativeHumidityPercent(snapshot: PhysicsFieldSnapshot, physicsPointM: Vec3): Double? {
        val temperature = temperatureC(snapshot, physicsPointM) ?: return null
        val omega = humidityRatioKgKg(snapshot, physicsPointM) ?: return null
        if (!temperature.isFinite() || !omega.isFinite()) return null
        return try {
            Psychrometrics.displayedRelativeHumidityPercent(
                temperature,
                omega,
                snapshot.airPressurePa
            ).takeIf { it.isFinite() }
        } catch (_: SimulationException) {
            null
        }
    }

    fun gridOf(snapshot: PhysicsFieldSnapshot): GridState? {
        if (!snapshot.finiteValues) return null
        val hit = cachedGrid
        if (cachedSnapshot === snapshot && hit != null) return hit
        val built = build(snapshot) ?: return null
        cachedSnapshot = snapshot
        cachedGrid = built
        return built
    }

    fun clearCache() {
        cachedSnapshot = null
        cachedGrid = null
    }

    private fun build(snapshot: PhysicsFieldSnapshot): GridState? {
        val geometry = snapshot.grid
        if (geometry.cellCount <= 0 || snapshot.temperatureC.size != geometry.cellCount) return null
        if (!geometry.lengthM.isFinite() || !geometry.widthM.isFinite() ||
            !geometry.heightM.isFinite() || !geometry.cellEdgeM.isFinite()
        ) {
            return null
        }
        val built = GridState.build(
            UserPhysicsCoordinateAdapter.geometry(
                lengthM = geometry.lengthM,
                widthM = geometry.widthM,
                heightM = geometry.heightM,
                cellEdgeM = geometry.cellEdgeM
            ),
            initialTemperatureC = 0.0,
            initialHumidityRatioKgKg = 0.0
        )
        if (built.size != snapshot.temperatureC.size) return null
        snapshot.temperatureC.copyInto(built.temperatureC)
        if (snapshot.humidityRatioKgKg.size == built.size) {
            snapshot.humidityRatioKgKg.copyInto(built.humidityRatioKgKg)
        }
        return built
    }
}
