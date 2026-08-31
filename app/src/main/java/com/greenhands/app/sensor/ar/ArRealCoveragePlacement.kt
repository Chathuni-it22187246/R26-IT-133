package com.greenhands.app.sensor.ar

import androidx.compose.ui.graphics.Color
import com.greenhands.app.sensor.domain.GreenhouseConfigFactory
import com.greenhands.app.sensor.model.CellCoverageState
import com.greenhands.app.sensor.model.GreenhousePhysicalConfig
import com.greenhands.app.sensor.model.SensorType

/**
 * Pure Real-AR coverage floor placement (Phase 10E-G).
 *
 * Visualizes [ArVisualizationSnapshot.coverageCells] only — never invokes
 * the domain coverage calculator. Cell centres use [GreenhouseConfigFactory] physical helpers.
 *
 * [COVERAGE_FLOOR_Y_METERS] is rendering-only (avoids z-fighting); not simulation.
 */
object ArRealCoveragePlacement {

    /** Slightly above the real floor so translucent cell bodies clear the plane. */
    const val COVERAGE_FLOOR_Y_METERS = 0.04f

    data class LocalPosition(val x: Float, val y: Float, val z: Float)

    data class RenderCell(
        val cell: ArCoverageCell,
        val local: LocalPosition,
        val cellSizeMeters: Float,
        val fill: Color
    )

    /** Aggregates already-mapped [ArCoverageCell] states — no domain recalculation. */
    data class DisplayMetrics(
        val coveredCells: Int,
        val overlapCells: Int,
        val blindSpotCells: Int,
        val totalCells: Int,
        /** (covered + overlap) / total × 100 — same meaning as CoverageResult.overallCoveragePercent. */
        val coveragePercent: Double
    )

    fun shouldShowCoverage(phase: ArOriginPlacementPhase): Boolean =
        phase == ArOriginPlacementPhase.ALIGNED

    /** null filter = monitoring (“All”); non-null = that type’s mapped cells in the snapshot. */
    fun isMonitoringFilter(selectedTypeFilter: SensorType?): Boolean =
        selectedTypeFilter == null

    fun fillColor(state: CellCoverageState): Color = when (state) {
        CellCoverageState.COVERED -> Color(0xFF2E9E5A)
        CellCoverageState.OVERLAP -> Color(0xFFD4A017)
        CellCoverageState.BLIND_SPOT -> Color(0xFFD64545)
    }

    /**
     * Cell centre in greenhouse-local meters via existing config factory
     * (column/row + 0.5) × cellSize — not reimplemented here.
     */
    fun localCenter(
        cell: ArCoverageCell,
        physical: GreenhousePhysicalConfig
    ): LocalPosition = LocalPosition(
        x = GreenhouseConfigFactory.physicalXMeters(cell.column, physical).toFloat(),
        y = COVERAGE_FLOOR_Y_METERS,
        z = GreenhouseConfigFactory.physicalYMeters(cell.row, physical).toFloat()
    )

    fun worldPosition(
        pose: ArGreenhousePose,
        cell: ArCoverageCell,
        physical: GreenhousePhysicalConfig
    ): ArWorldMapper.WorldPoint? {
        if (!shouldShowCoverage(pose.phase)) return null
        val local = localCenter(cell, physical)
        return ArWorldMapper.localToWorld(pose, local.x, local.y, local.z)
    }

    fun metricsFromCells(cells: List<ArCoverageCell>): DisplayMetrics {
        var covered = 0
        var overlap = 0
        var blind = 0
        cells.forEach { cell ->
            when (cell.state) {
                CellCoverageState.COVERED -> covered++
                CellCoverageState.OVERLAP -> overlap++
                CellCoverageState.BLIND_SPOT -> blind++
            }
        }
        val total = cells.size
        val monitored = covered + overlap
        val percent = if (total <= 0) 0.0 else (monitored * 100.0) / total.toDouble()
        return DisplayMetrics(
            coveredCells = covered,
            overlapCells = overlap,
            blindSpotCells = blind,
            totalCells = total,
            coveragePercent = percent
        )
    }

    fun buildRenderCells(
        pose: ArGreenhousePose,
        snapshot: ArVisualizationSnapshot,
        layers: RealArLayerVisibility = RealArLayerVisibility()
    ): List<RenderCell> {
        if (!shouldShowCoverage(pose.phase)) return emptyList()
        val cellSize = snapshot.physical.cellSizeMeters.toFloat()
        if (cellSize <= 0f) return emptyList()
        return snapshot.coverageCells
            .filter { layers.shouldRenderCell(it.state, pose.phase == ArOriginPlacementPhase.ALIGNED) }
            .map { cell ->
            RenderCell(
                cell = cell,
                local = localCenter(cell, snapshot.physical),
                cellSizeMeters = cellSize,
                fill = fillColor(cell.state)
            )
        }
    }
}
