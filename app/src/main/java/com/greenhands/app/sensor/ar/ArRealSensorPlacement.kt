package com.greenhands.app.sensor.ar

import com.greenhands.app.sensor.model.GreenhousePhysicalConfig
import com.greenhands.app.sensor.model.SensorStatus
import com.greenhands.app.sensor.model.SensorType

/**
 * Pure Real-AR sensor marker placement (Phase 10E-F).
 *
 * Mounting height is **rendering-only** — it does not affect coverage,
 * optimization, grid coordinates, or [ArVisualizationSnapshot] contents.
 * Matches Virtual Greenhouse Preview: `(height × 0.32).coerceIn(0.5, 2.4)` meters.
 *
 * Local position uses mapper-provided [ArSensorMarker.xMeters] / [ArSensorMarker.zMeters]
 * (do not recompute cell centres here). World transform uses [ArWorldMapper].
 */
object ArRealSensorPlacement {

    data class LocalPosition(val x: Float, val y: Float, val z: Float)

    data class RenderMarker(
        val sensor: ArSensorMarker,
        val local: LocalPosition,
        val abbrev: String,
        val label: String,
        val active: Boolean
    )

    /** Rendering-only global mount height (meters). Not stored on Sensor. */
    fun mountHeightMeters(physical: GreenhousePhysicalConfig): Float {
        val h = physical.heightMeters.toFloat()
        return (h * 0.32f).coerceIn(0.5f, 2.4f)
    }

    fun shouldShowMarkers(phase: ArOriginPlacementPhase): Boolean =
        phase == ArOriginPlacementPhase.ALIGNED

    /**
     * Sensors to draw. Optional [typeFilter] is applied only for rendering;
     * the snapshot list itself is never mutated.
     */
    fun sensorsForRender(
        snapshot: ArVisualizationSnapshot,
        typeFilter: SensorType? = null
    ): List<ArSensorMarker> {
        val all = snapshot.sensors
        return if (typeFilter == null) all else all.filter { it.type == typeFilter }
    }

    fun localPosition(
        sensor: ArSensorMarker,
        physical: GreenhousePhysicalConfig
    ): LocalPosition = LocalPosition(
        x = sensor.xMeters.toFloat(),
        y = mountHeightMeters(physical),
        z = sensor.zMeters.toFloat()
    )

    fun worldPosition(
        pose: ArGreenhousePose,
        sensor: ArSensorMarker,
        physical: GreenhousePhysicalConfig
    ): ArWorldMapper.WorldPoint? {
        if (!shouldShowMarkers(pose.phase)) return null
        val local = localPosition(sensor, physical)
        return ArWorldMapper.localToWorld(pose, local.x, local.y, local.z)
    }

    fun displayLabel(sensor: ArSensorMarker): String =
        "${VirtualGreenhouseLabels.sensorAbbrev(sensor)} · ${VirtualGreenhouseLabels.sensorId(sensor)}"

    fun isActive(sensor: ArSensorMarker): Boolean =
        sensor.status == SensorStatus.ACTIVE

    fun buildRenderMarkers(
        pose: ArGreenhousePose,
        snapshot: ArVisualizationSnapshot,
        typeFilter: SensorType? = null
    ): List<RenderMarker> {
        if (!shouldShowMarkers(pose.phase)) return emptyList()
        return sensorsForRender(snapshot, typeFilter).map { sensor ->
            RenderMarker(
                sensor = sensor,
                local = localPosition(sensor, snapshot.physical),
                abbrev = VirtualGreenhouseLabels.sensorAbbrev(sensor),
                label = displayLabel(sensor),
                active = isActive(sensor)
            )
        }
    }
}
