package com.greenhands.app.sensor.domain

import com.greenhands.app.sensor.model.Greenhouse
import com.greenhands.app.sensor.model.GreenhousePhysicalConfig
import com.greenhands.app.sensor.model.MAX_GREENHOUSE_CELLS_PER_SIDE
import com.greenhands.app.sensor.model.MAX_GREENHOUSE_DIMENSION_METERS
import com.greenhands.app.sensor.model.MAX_GREENHOUSE_HEIGHT_METERS
import com.greenhands.app.sensor.model.MAX_GREENHOUSE_TOTAL_CELLS
import kotlin.math.floor

sealed class GreenhouseConfigResult {
    data class Success(
        val config: GreenhousePhysicalConfig,
        val greenhouse: Greenhouse
    ) : GreenhouseConfigResult()

    data class Invalid(val message: String) : GreenhouseConfigResult()
}

/**
 * Validates physical greenhouse dimensions and derives the logical [Greenhouse] grid.
 * Sensor placement/coverage continue to use cell coordinates from [Greenhouse].
 */
object GreenhouseConfigFactory {

    fun validate(config: GreenhousePhysicalConfig): GreenhouseConfigResult {
        val length = config.lengthMeters
        val width = config.widthMeters
        val height = config.heightMeters
        val cell = config.cellSizeMeters

        if (!length.isFinite() || !width.isFinite() || !height.isFinite() || !cell.isFinite()) {
            return GreenhouseConfigResult.Invalid(
                "Length, width, height, and cell size must be finite numbers."
            )
        }
        if (length <= 0.0 || width <= 0.0 || height <= 0.0 || cell <= 0.0) {
            return GreenhouseConfigResult.Invalid(
                "Length, width, height, and cell size must be greater than zero."
            )
        }
        if (length > MAX_GREENHOUSE_DIMENSION_METERS || width > MAX_GREENHOUSE_DIMENSION_METERS) {
            return GreenhouseConfigResult.Invalid(
                "Length and width must be at most ${MAX_GREENHOUSE_DIMENSION_METERS.toInt()} meters."
            )
        }
        if (height > MAX_GREENHOUSE_HEIGHT_METERS) {
            return GreenhouseConfigResult.Invalid(
                "Height must be at most ${MAX_GREENHOUSE_HEIGHT_METERS.toInt()} meters."
            )
        }
        if (cell > length || cell > width) {
            return GreenhouseConfigResult.Invalid(
                "Grid cell size cannot be larger than the greenhouse length or width."
            )
        }

        val widthCells = floor(length / cell).toInt()
        val heightCells = floor(width / cell).toInt()
        if (widthCells < 1 || heightCells < 1) {
            return GreenhouseConfigResult.Invalid(
                "Dimensions and cell size must produce at least one grid cell on each side."
            )
        }
        if (widthCells > MAX_GREENHOUSE_CELLS_PER_SIDE || heightCells > MAX_GREENHOUSE_CELLS_PER_SIDE) {
            return GreenhouseConfigResult.Invalid(
                "Grid is too fine: each side may have at most $MAX_GREENHOUSE_CELLS_PER_SIDE cells."
            )
        }
        val total = widthCells * heightCells
        if (total > MAX_GREENHOUSE_TOTAL_CELLS) {
            return GreenhouseConfigResult.Invalid(
                "Grid is too large: at most $MAX_GREENHOUSE_TOTAL_CELLS cells are allowed."
            )
        }

        return GreenhouseConfigResult.Success(
            config = config,
            greenhouse = Greenhouse(widthCells = widthCells, heightCells = heightCells)
        )
    }

    fun toGreenhouseOrNull(config: GreenhousePhysicalConfig): Greenhouse? =
        (validate(config) as? GreenhouseConfigResult.Success)?.greenhouse

    /** Physical X (meters along length) at the centre of a grid column. */
    fun physicalXMeters(column: Int, config: GreenhousePhysicalConfig): Double =
        (column + 0.5) * config.cellSizeMeters

    /** Physical Y (meters along width) at the centre of a grid row. */
    fun physicalYMeters(row: Int, config: GreenhousePhysicalConfig): Double =
        (row + 0.5) * config.cellSizeMeters

    fun physicalXMeters(gridX: Double, config: GreenhousePhysicalConfig): Double =
        (gridX + 0.5) * config.cellSizeMeters

    fun physicalYMeters(gridY: Double, config: GreenhousePhysicalConfig): Double =
        (gridY + 0.5) * config.cellSizeMeters
}
