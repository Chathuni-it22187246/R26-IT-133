package com.greenhands.app.sensor.ar

import com.greenhands.app.sensor.model.GreenhousePhysicalConfig
import com.greenhands.app.sensor.model.SensorType
import com.greenhands.app.ui.theme.ClimateTeal

/**
 * Pure Real-AR P# recommendation placement (Phase 10E-H).
 *
 * Visualizes [ArVisualizationSnapshot.recommendations] only — never invokes
 * the placement optimizer. Uses mapper-provided [ArRecommendationMarker.xMeters] /
 * [ArRecommendationMarker.zMeters] and [ArWorldMapper] for world poses.
 *
 * Mount height is rendering-only (same convention as sensors × 0.95).
 */
object ArRealRecommendationPlacement {

    enum class MarkerKind { SENSOR, RECOMMENDATION }

    data class LocalPosition(val x: Float, val y: Float, val z: Float)

    data class RenderMarker(
        val recommendation: ArRecommendationMarker,
        val local: LocalPosition,
        val label: String,
        val kind: MarkerKind = MarkerKind.RECOMMENDATION
    )

    fun shouldShowRecommendations(phase: ArOriginPlacementPhase): Boolean =
        phase == ArOriginPlacementPhase.ALIGNED

    /**
     * Recommendations to draw: **selected for Apply** only, optionally type-filtered.
     * Does not invent All/monitoring optimization — Phase 9 is always per-type;
     * All simply shows selected recommendations of whatever type is in the snapshot.
     */
    fun recommendationsForRender(
        snapshot: ArVisualizationSnapshot,
        typeFilter: SensorType? = null
    ): List<ArRecommendationMarker> {
        val selected = snapshot.recommendations.filter { it.selected }
        return if (typeFilter == null) {
            selected
        } else {
            selected.filter { it.type == typeFilter }
        }
    }

    fun mountHeightMeters(physical: GreenhousePhysicalConfig): Float =
        ArRealSensorPlacement.mountHeightMeters(physical) * 0.95f

    fun localPosition(
        rec: ArRecommendationMarker,
        physical: GreenhousePhysicalConfig
    ): LocalPosition = LocalPosition(
        x = rec.xMeters.toFloat(),
        y = mountHeightMeters(physical),
        z = rec.zMeters.toFloat()
    )

    fun worldPosition(
        pose: ArGreenhousePose,
        rec: ArRecommendationMarker,
        physical: GreenhousePhysicalConfig
    ): ArWorldMapper.WorldPoint? {
        if (!shouldShowRecommendations(pose.phase)) return null
        val local = localPosition(rec, physical)
        return ArWorldMapper.localToWorld(pose, local.x, local.y, local.z)
    }

    fun displayLabel(rec: ArRecommendationMarker): String =
        VirtualGreenhouseLabels.recommendationPrimary(rec)

    fun markerColor() = ClimateTeal

    /** Distinct from solid sensor glyphs — hollow teal diamond language. */
    fun isDistinctFromSensorStyle(kind: MarkerKind): Boolean =
        kind == MarkerKind.RECOMMENDATION

    fun buildRenderMarkers(
        pose: ArGreenhousePose,
        snapshot: ArVisualizationSnapshot,
        typeFilter: SensorType? = null
    ): List<RenderMarker> {
        if (!shouldShowRecommendations(pose.phase)) return emptyList()
        return recommendationsForRender(snapshot, typeFilter)
            .sortedBy { it.rank }
            .map { rec ->
                RenderMarker(
                    recommendation = rec,
                    local = localPosition(rec, snapshot.physical),
                    label = displayLabel(rec)
                )
            }
    }
}
