package com.greenhands.app.sensor.ar

import com.greenhands.app.sensor.model.CellCoverageState
import com.greenhands.app.sensor.model.GreenhousePhysicalConfig
import java.util.Locale

/**
 * Pure rendering-label helpers for Phase 10C polish.
 * No coverage/optimization math — display strings only from snapshot data.
 */
object VirtualGreenhouseLabels {

    fun formatMeters(value: Double): String =
        String.format(Locale.US, "%.1f", value)

    fun greenhouseSizeLine(physical: GreenhousePhysicalConfig): String =
        "${formatMeters(physical.lengthMeters)}m × " +
            "${formatMeters(physical.widthMeters)}m × " +
            "${formatMeters(physical.heightMeters)}m"

    fun cellSizeLine(physical: GreenhousePhysicalConfig): String =
        "${formatMeters(physical.cellSizeMeters)}m"

    /** Compact marker text: "T" / "H" / "SM" / "L". */
    fun sensorAbbrev(sensor: ArSensorMarker): String = sensor.type.markerAbbreviation

    fun sensorId(sensor: ArSensorMarker): String = sensor.id

    /** Two-line style primary+secondary for canvas labels. */
    fun sensorMarkerLines(sensor: ArSensorMarker): Pair<String, String> =
        sensorAbbrev(sensor) to sensorId(sensor)

    fun recommendationPrimary(rec: ArRecommendationMarker): String = rec.label

    fun recommendationSecondary(): String = "Recommended"

    fun blindCellCount(snapshot: ArVisualizationSnapshot): Int =
        snapshot.coverageCells.count { it.state == CellCoverageState.BLIND_SPOT }

    fun shouldDrawRecommendations(
        layerEnabled: Boolean,
        snapshot: ArVisualizationSnapshot
    ): Boolean = layerEnabled && snapshot.recommendations.isNotEmpty()
}
