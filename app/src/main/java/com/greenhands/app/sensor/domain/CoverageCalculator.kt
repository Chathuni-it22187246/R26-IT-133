package com.greenhands.app.sensor.domain

import com.greenhands.app.sensor.model.CellCoverageState
import com.greenhands.app.sensor.model.CoverageByType
import com.greenhands.app.sensor.model.CoverageResult
import com.greenhands.app.sensor.model.Greenhouse
import com.greenhands.app.sensor.model.GridCell
import com.greenhands.app.sensor.model.Sensor
import com.greenhands.app.sensor.model.SensorStatus
import com.greenhands.app.sensor.model.SensorType
import kotlin.math.sqrt

object CoverageCalculator {

    fun isValidPosition(greenhouse: Greenhouse, x: Double, y: Double): Boolean =
        greenhouse.contains(x, y)

    fun distance(x1: Double, y1: Double, x2: Double, y2: Double): Double {
        val dx = x1 - x2
        val dy = y1 - y2
        return sqrt(dx * dx + dy * dy)
    }

    fun covers(sensor: Sensor, cellX: Int, cellY: Int): Boolean {
        if (sensor.status != SensorStatus.ACTIVE) return false
        return distance(sensor.x, sensor.y, cellX.toDouble(), cellY.toDouble()) <= sensor.coverageRadius
    }

    /**
     * Coverage for a single [SensorType].
     * OVERLAP only when two or more active in-bounds sensors of that type cover the cell.
     * Types with no sensors yield 0% coverage and all cells as blind spots.
     */
    fun calculateForType(
        greenhouse: Greenhouse,
        sensors: List<Sensor>,
        type: SensorType
    ): CoverageResult = calculateSameType(
        greenhouse = greenhouse,
        sensors = sensors.filter { it.type == type }
    )

    /**
     * Independent coverage result for every [SensorType], plus aggregate monitoring coverage.
     * Monitoring coverage is NOT same-type overlap: a cell is covered if any type reaches it.
     */
    fun calculateByType(greenhouse: Greenhouse, sensors: List<Sensor>): CoverageByType {
        val byType = SensorType.entries.associateWith { type ->
            calculateForType(greenhouse, sensors, type)
        }
        return CoverageByType(
            greenhouse = greenhouse,
            byType = byType,
            monitoring = calculateMonitoringCoverage(greenhouse, sensors)
        )
    }

    /**
     * Aggregate monitoring metric across all sensor types.
     * Cells are COVERED if at least one active in-bounds sensor of any type reaches them.
     * Never marks OVERLAP — same-type overlap lives only in [calculateForType] / [CoverageByType].
     */
    fun calculateMonitoringCoverage(
        greenhouse: Greenhouse,
        sensors: List<Sensor>
    ): CoverageResult {
        val contributing = contributingSensors(greenhouse, sensors)
        val cells = ArrayList<GridCell>(greenhouse.totalCells)
        var covered = 0
        var blind = 0
        for (y in greenhouse.rows) {
            for (x in greenhouse.columns) {
                val ids = contributing.mapNotNull { sensor ->
                    sensor.id.takeIf { covers(sensor, x, y) }
                }
                val state = if (ids.isEmpty()) {
                    blind++
                    CellCoverageState.BLIND_SPOT
                } else {
                    covered++
                    CellCoverageState.COVERED
                }
                cells += GridCell(x = x, y = y, state = state, coveringSensorIds = ids)
            }
        }
        val total = greenhouse.totalCells
        return CoverageResult(
            greenhouse = greenhouse,
            cells = cells,
            totalCells = total,
            coveredCells = covered,
            overlapCells = 0,
            blindSpotCells = blind,
            overallCoveragePercent = percent(covered, total),
            goodCoveragePercent = percent(covered, total),
            overlapPercent = 0.0,
            blindSpotPercent = percent(blind, total)
        )
    }

    /**
     * Backward-compatible entry point used by placement UI aggregate chips.
     * Returns [calculateMonitoringCoverage] — not same-type overlap statistics.
     */
    fun calculate(greenhouse: Greenhouse, sensors: List<Sensor>): CoverageResult =
        calculateMonitoringCoverage(greenhouse, sensors)

    private fun calculateSameType(
        greenhouse: Greenhouse,
        sensors: List<Sensor>
    ): CoverageResult {
        val contributing = contributingSensors(greenhouse, sensors)
        val cells = ArrayList<GridCell>(greenhouse.totalCells)
        var covered = 0
        var overlap = 0
        var blind = 0
        for (y in greenhouse.rows) {
            for (x in greenhouse.columns) {
                val ids = contributing.mapNotNull { sensor ->
                    sensor.id.takeIf { covers(sensor, x, y) }
                }
                val state = when (ids.size) {
                    0 -> {
                        blind++
                        CellCoverageState.BLIND_SPOT
                    }
                    1 -> {
                        covered++
                        CellCoverageState.COVERED
                    }
                    else -> {
                        overlap++
                        CellCoverageState.OVERLAP
                    }
                }
                cells += GridCell(x = x, y = y, state = state, coveringSensorIds = ids)
            }
        }
        val total = greenhouse.totalCells
        return CoverageResult(
            greenhouse = greenhouse,
            cells = cells,
            totalCells = total,
            coveredCells = covered,
            overlapCells = overlap,
            blindSpotCells = blind,
            overallCoveragePercent = percent(covered + overlap, total),
            goodCoveragePercent = percent(covered, total),
            overlapPercent = percent(overlap, total),
            blindSpotPercent = percent(blind, total)
        )
    }

    private fun contributingSensors(
        greenhouse: Greenhouse,
        sensors: List<Sensor>
    ): List<Sensor> = sensors.filter { sensor ->
        sensor.status == SensorStatus.ACTIVE && greenhouse.contains(sensor.x, sensor.y)
    }

    private fun percent(count: Int, total: Int): Double =
        if (total == 0) 0.0 else 100.0 * count / total
}
