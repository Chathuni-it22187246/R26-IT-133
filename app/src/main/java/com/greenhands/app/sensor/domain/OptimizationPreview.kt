package com.greenhands.app.sensor.domain

import com.greenhands.app.sensor.model.CoverageResult
import com.greenhands.app.sensor.model.DEFAULT_COVERAGE_RADIUS_CELLS
import com.greenhands.app.sensor.model.Greenhouse
import com.greenhands.app.sensor.model.RecommendedPosition
import com.greenhands.app.sensor.model.Sensor
import com.greenhands.app.sensor.model.SensorStatus
import com.greenhands.app.sensor.model.SensorType

/**
 * Visualization-only preview for selected recommendations.
 * Does not replace [SensorPlacementOptimizer]; uses [CoverageCalculator] only.
 */
object OptimizationPreview {

    private const val PREVIEW_ID_PREFIX = "__opt_preview_"

    fun predictForSelected(
        greenhouse: Greenhouse,
        sensors: List<Sensor>,
        sensorType: SensorType,
        selected: List<RecommendedPosition>
    ): CoverageResult {
        if (selected.isEmpty()) {
            return CoverageCalculator.calculateForType(greenhouse, sensors, sensorType)
        }
        val additions = selected.mapIndexed { index, pos ->
            Sensor(
                id = "$PREVIEW_ID_PREFIX$index",
                type = sensorType,
                x = pos.x,
                y = pos.y,
                coverageRadius = DEFAULT_COVERAGE_RADIUS_CELLS,
                status = SensorStatus.ACTIVE
            )
        }
        return CoverageCalculator.calculateForType(greenhouse, sensors + additions, sensorType)
    }
}
