package com.greenhands.app.sensor.ar

import com.greenhands.app.sensor.domain.CoverageCalculator
import com.greenhands.app.sensor.domain.GreenhouseConfigFactory
import com.greenhands.app.sensor.model.Greenhouse
import com.greenhands.app.sensor.model.GreenhousePhysicalConfig
import com.greenhands.app.sensor.model.SensorType
import kotlin.math.floor

/**
 * Pure world → greenhouse-local → grid-cell mapping for Real AR manual placement.
 * Does not call coverage algorithms beyond [CoverageCalculator.isValidPosition].
 */
object ArRealTapPlacement {

    data class LocalMeters(val x: Float, val y: Float, val z: Float)

    data class GridCell(
        val column: Double,
        val row: Double
    )

    sealed class PlacementResult {
        data class Ok(
            val gridX: Double,
            val gridY: Double,
            val localXMeters: Float,
            val localZMeters: Float,
            val type: SensorType
        ) : PlacementResult()

        object NotAligned : PlacementResult()
        object OutOfBounds : PlacementResult()
    }

    /**
     * Maps an AR floor hit to the nearest greenhouse grid cell centre used by [Sensor].
     * local X/Z meters → floor division by [GreenhousePhysicalConfig.cellSizeMeters].
     */
    fun worldHitToGrid(
        pose: ArGreenhousePose,
        worldX: Float,
        worldY: Float,
        worldZ: Float,
        physical: GreenhousePhysicalConfig,
        greenhouse: Greenhouse,
        type: SensorType
    ): PlacementResult {
        if (pose.phase != ArOriginPlacementPhase.ALIGNED) return PlacementResult.NotAligned
        val displayScale = ArRealScale.rootScale(physical)
        val local = ArWorldMapper.worldToLocal(pose, worldX, worldY, worldZ, displayScale)
            ?: return PlacementResult.NotAligned
        val cell = physical.cellSizeMeters.toFloat().coerceAtLeast(1e-4f)
        val col = floor(local.x / cell).toInt()
        val row = floor(local.z / cell).toInt()
        val gridX = col.toDouble()
        val gridY = row.toDouble()
        if (!CoverageCalculator.isValidPosition(greenhouse, gridX, gridY)) {
            return PlacementResult.OutOfBounds
        }
        return PlacementResult.Ok(
            gridX = gridX,
            gridY = gridY,
            localXMeters = GreenhouseConfigFactory.physicalXMeters(col, physical).toFloat(),
            localZMeters = GreenhouseConfigFactory.physicalYMeters(row, physical).toFloat(),
            type = type
        )
    }

    /** Nearest sensor id within [maxDistanceMeters] of a local floor point, or null. */
    fun nearestSensorId(
        localXMeters: Float,
        localZMeters: Float,
        sensors: List<ArSensorMarker>,
        maxDistanceMeters: Float = 0.45f
    ): String? {
        var bestId: String? = null
        var bestDist = maxDistanceMeters
        sensors.forEach { sensor ->
            val dx = sensor.xMeters.toFloat() - localXMeters
            val dz = sensor.zMeters.toFloat() - localZMeters
            val d = kotlin.math.sqrt(dx * dx + dz * dz)
            if (d <= bestDist) {
                bestDist = d
                bestId = sensor.id
            }
        }
        return bestId
    }
}
