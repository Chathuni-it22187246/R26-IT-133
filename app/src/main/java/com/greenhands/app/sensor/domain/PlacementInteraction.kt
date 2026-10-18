package com.greenhands.app.sensor.domain

import com.greenhands.app.sensor.model.Greenhouse
import com.greenhands.app.sensor.model.Sensor
import kotlin.math.floor

sealed class GridTapResult {
    data class Add(val x: Double, val y: Double) : GridTapResult()
    data class Select(val id: String) : GridTapResult()
    data class Move(val id: String, val x: Double, val y: Double) : GridTapResult()
    data object Ignore : GridTapResult()
}

object PlacementInteraction {

    fun occupiesCell(sensor: Sensor, column: Int, row: Int): Boolean =
        floor(sensor.x).toInt() == column && floor(sensor.y).toInt() == row

    fun sensorInCell(sensors: List<Sensor>, column: Int, row: Int): Sensor? =
        sensors.firstOrNull { occupiesCell(it, column, row) }

    /**
     * Maps a tapped grid cell to a ViewModel operation.
     * Occupied cell → select. Empty cell with a selection → move. Empty cell otherwise → add.
     */
    fun onCellTapped(
        column: Int,
        row: Int,
        greenhouse: Greenhouse,
        sensors: List<Sensor>,
        selectedSensorId: String?,
        forceAdd: Boolean = false
    ): GridTapResult {
        val x = column.toDouble()
        val y = row.toDouble()
        if (!greenhouse.contains(x, y)) return GridTapResult.Ignore
        val occupant = sensorInCell(sensors, column, row)
        if (occupant != null) return GridTapResult.Select(occupant.id)
        val selected = selectedSensorId?.let { id -> sensors.find { it.id == id } }
        return if (selected != null && !forceAdd) {
            GridTapResult.Move(selected.id, x, y)
        } else {
            GridTapResult.Add(x, y)
        }
    }
}
