package com.greenhands.app.sensor.ar

import com.greenhands.app.sensor.model.CellCoverageState
import com.greenhands.app.sensor.model.Greenhouse
import com.greenhands.app.sensor.model.GreenhousePhysicalConfig
import com.greenhands.app.sensor.model.SensorStatus
import com.greenhands.app.sensor.model.SensorType
import com.greenhands.app.sensor.ui.SensorWorkflowStep

/**
 * Immutable visualization snapshot derived from [com.greenhands.app.sensor.ui.SensorPlacementUiState].
 * Suitable for virtual 3D preview and later real AR — no coverage/optimization math.
 *
 * Coordinate convention for later renderers:
 * - X = greenhouse length (from Sensor.x / column)
 * - Y = greenhouse height (vertical; not stored per sensor here)
 * - Z = greenhouse width (from Sensor.y / row)
 */
data class ArVisualizationSnapshot(
    val physical: GreenhousePhysicalConfig,
    val grid: Greenhouse,
    val sensors: List<ArSensorMarker>,
    val coverageCells: List<ArCoverageCell>,
    val recommendations: List<ArRecommendationMarker>,
    /** Default coverage radius in meters (typical cell radius × cell size). */
    val coverageRadiusMeters: Double,
    val selectedTypeFilter: SensorType?,
    val generatedAtStep: SensorWorkflowStep
)

/**
 * Derived from [com.greenhands.app.sensor.model.Sensor]. Grid [gridX]/[gridY] remain authoritative.
 * [xMeters]/[zMeters] are cell-centre positions along length / width.
 */
data class ArSensorMarker(
    val id: String,
    val type: SensorType,
    val status: SensorStatus,
    val gridX: Double,
    val gridY: Double,
    val xMeters: Double,
    val zMeters: Double,
    val coverageRadiusMeters: Double
)

/**
 * Floor cell for coverage visualization. [state] reuses Phase 8 [CellCoverageState] semantics.
 */
data class ArCoverageCell(
    val column: Int,
    val row: Int,
    val state: CellCoverageState
)

/**
 * Derived from Phase 9 [com.greenhands.app.sensor.model.RecommendedPosition].
 * Empty when [com.greenhands.app.sensor.ui.SensorPlacementUiState.optimizationResult] is null.
 */
data class ArRecommendationMarker(
    val rank: Int,
    val label: String,
    val type: SensorType,
    val gridX: Double,
    val gridY: Double,
    val xMeters: Double,
    val zMeters: Double,
    val selected: Boolean
)
