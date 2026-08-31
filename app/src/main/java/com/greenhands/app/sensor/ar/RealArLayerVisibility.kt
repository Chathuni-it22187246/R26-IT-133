package com.greenhands.app.sensor.ar

import com.greenhands.app.sensor.model.CellCoverageState
import com.greenhands.app.sensor.model.SensorType
import com.greenhands.app.ui.navigation.Routes

/**
 * Visualization-only layer flags for Real AR.
 * Toggles never mutate simulation, snapshot contents, or alignment pose.
 *
 * After alignment the Virtual-parity greenhouse structure is shown by default
 * ([guide] / [greenhouse]). Sensors, coverage cells, and P# remain independently toggleable.
 */
data class RealArLayerVisibility(
    /** Virtual-parity greenhouse structure (floor / walls / roof / frame / grid). Default ON. */
    val guide: Boolean = true,
    val sensors: Boolean = true,
    /** Green cells — same-type or monitoring COVERED state. */
    val covered: Boolean = true,
    /** Red cells — BLIND_SPOT state. */
    val blindSpots: Boolean = true,
    /** Amber cells — same-type OVERLAP state. */
    val overlap: Boolean = true,
    val recommendations: Boolean = true
) {
    /** Back-compat alias used by older call sites / tests. */
    val greenhouse: Boolean get() = guide

    fun toggleGuide(): RealArLayerVisibility = copy(guide = !guide)

    fun toggleGreenhouse(): RealArLayerVisibility = toggleGuide()

    fun toggleSensors(): RealArLayerVisibility = copy(sensors = !sensors)

    fun toggleCovered(): RealArLayerVisibility = copy(covered = !covered)

    fun toggleBlindSpots(): RealArLayerVisibility = copy(blindSpots = !blindSpots)

    fun toggleOverlap(): RealArLayerVisibility = copy(overlap = !overlap)

    fun toggleRecommendations(): RealArLayerVisibility = copy(recommendations = !recommendations)

    fun shouldAttachGuide(aligned: Boolean): Boolean = aligned && guide

    fun shouldAttachGreenhouseGeometry(aligned: Boolean): Boolean = shouldAttachGuide(aligned)

    fun shouldAttachSensors(aligned: Boolean): Boolean = aligned && sensors

    fun shouldRenderCell(state: CellCoverageState, aligned: Boolean): Boolean = when (state) {
        CellCoverageState.COVERED -> aligned && covered
        CellCoverageState.BLIND_SPOT -> aligned && blindSpots
        CellCoverageState.OVERLAP -> aligned && overlap
    }

    fun shouldAttachAnyCoverage(aligned: Boolean): Boolean =
        aligned && (covered || blindSpots || overlap)

    fun shouldAttachRecommendations(aligned: Boolean): Boolean =
        aligned && recommendations
}

/**
 * Pure UX helpers for Real AR summary / filter / navigation checks.
 * No domain coverage or optimization recalculation.
 */
object RealArUxHelpers {

    data class Summary(
        val lengthMeters: Double,
        val widthMeters: Double,
        val heightMeters: Double,
        val cellSizeMeters: Double,
        val sensorCount: Int,
        val recommendationCount: Int,
        val coveragePercent: Double,
        val blindSpotCells: Int,
        val overlapCells: Int,
        val monitoringFilter: Boolean,
        val typeFilter: SensorType?
    )

    fun defaultLayers(): RealArLayerVisibility = RealArLayerVisibility()

    fun summaryFromSnapshot(snapshot: ArVisualizationSnapshot): Summary {
        val metrics = ArRealCoveragePlacement.metricsFromCells(snapshot.coverageCells)
        val recCount = ArRealRecommendationPlacement.recommendationsForRender(
            snapshot,
            snapshot.selectedTypeFilter
        ).size
        return Summary(
            lengthMeters = snapshot.physical.lengthMeters,
            widthMeters = snapshot.physical.widthMeters,
            heightMeters = snapshot.physical.heightMeters,
            cellSizeMeters = snapshot.physical.cellSizeMeters,
            sensorCount = ArRealSensorPlacement.sensorsForRender(
                snapshot,
                snapshot.selectedTypeFilter
            ).size,
            recommendationCount = recCount,
            coveragePercent = metrics.coveragePercent,
            blindSpotCells = metrics.blindSpotCells,
            overlapCells = metrics.overlapCells,
            monitoringFilter = ArRealCoveragePlacement.isMonitoringFilter(snapshot.selectedTypeFilter),
            typeFilter = snapshot.selectedTypeFilter
        )
    }

    fun isAllFilter(type: SensorType?): Boolean = type == null

    fun virtualFallbackRoute(): String = Routes.SENSOR_VIRTUAL_PREVIEW

    fun realArRoute(): String = Routes.SENSOR_REAL_AR

    fun isSensorContentRoute(route: String): Boolean =
        Routes.isSensorContentRoute(route)
}
