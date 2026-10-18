package com.greenhands.app.sensor.ar

import com.greenhands.app.sensor.domain.GreenhouseConfigFactory
import com.greenhands.app.sensor.model.DEFAULT_COVERAGE_RADIUS_CELLS
import com.greenhands.app.sensor.model.GreenhousePhysicalConfig
import com.greenhands.app.sensor.model.Sensor
import com.greenhands.app.sensor.model.SensorType
import com.greenhands.app.sensor.ui.SensorPlacementUiState
import com.greenhands.app.sensor.ui.optimizationPositionKey

/**
 * Pure mapper: simulation UI state → immutable AR/virtual visualization snapshot.
 * Does not call CoverageCalculator or SensorPlacementOptimizer.
 */
object ArVisualizationMapper {

    /**
     * @param selectedTypeFilter when null, coverage cells come from monitoring [SensorPlacementUiState.coverage]
     *   (no same-type OVERLAP). When non-null, cells come from [SensorPlacementUiState.coverageByType] for that type.
     */
    fun from(
        ui: SensorPlacementUiState,
        selectedTypeFilter: SensorType? = null
    ): ArVisualizationSnapshot {
        val physical = ui.physicalConfig
        val coverageResult = if (selectedTypeFilter == null) {
            ui.coverage
        } else {
            ui.coverageByType.forType(selectedTypeFilter)
        }

        return ArVisualizationSnapshot(
            physical = physical,
            grid = ui.greenhouse,
            sensors = ui.sensors.map { toSensorMarker(it, physical) },
            coverageCells = coverageResult.cells.map { cell ->
                ArCoverageCell(
                    column = cell.x,
                    row = cell.y,
                    state = cell.state
                )
            },
            recommendations = mapRecommendations(ui),
            coverageRadiusMeters = DEFAULT_COVERAGE_RADIUS_CELLS * physical.cellSizeMeters,
            selectedTypeFilter = selectedTypeFilter,
            generatedAtStep = ui.step
        )
    }

    fun toSensorMarker(sensor: Sensor, physical: GreenhousePhysicalConfig): ArSensorMarker =
        ArSensorMarker(
            id = sensor.id,
            type = sensor.type,
            status = sensor.status,
            gridX = sensor.x,
            gridY = sensor.y,
            xMeters = GreenhouseConfigFactory.physicalXMeters(sensor.x, physical),
            zMeters = GreenhouseConfigFactory.physicalYMeters(sensor.y, physical),
            coverageRadiusMeters = sensor.coverageRadius * physical.cellSizeMeters
        )

    private fun mapRecommendations(ui: SensorPlacementUiState): List<ArRecommendationMarker> {
        val result = ui.optimizationResult ?: return emptyList()
        val physical = ui.physicalConfig
        return result.recommendedPositions.map { pos ->
            val key = optimizationPositionKey(pos.x, pos.y)
            ArRecommendationMarker(
                rank = pos.rank,
                label = "P${pos.rank}",
                type = result.sensorType,
                gridX = pos.x,
                gridY = pos.y,
                xMeters = GreenhouseConfigFactory.physicalXMeters(pos.x, physical),
                zMeters = GreenhouseConfigFactory.physicalYMeters(pos.y, physical),
                selected = key in ui.selectedOptimizationPositions
            )
        }
    }
}
