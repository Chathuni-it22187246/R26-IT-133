package com.greenhands.app.sensor.ar

import com.greenhands.app.sensor.telemetry.SensorArIotLabel
import io.github.sceneview.math.Position
import io.github.sceneview.node.CameraNode

data class SensorArProjectedLabel(
    val sensorId: String,
    val titleLine: String,
    val detailLine: String,
    val screenX: Float,
    val screenY: Float,
    val typeAbbrev: String
)

/**
 * Projects greenhouse-world sensor positions to screen pixels for Compose
 * labels. Firebase callbacks never mutate SceneView from here — callers pass
 * already-mapped [SensorArIotLabel] world coordinates.
 */
object SensorArWorldProjector {

    data class ViewPoint(val x: Float, val y: Float, val z: Float)

    fun project(
        view: ViewPoint,
        viewportWidthPx: Float,
        viewportHeightPx: Float
    ): Pair<Float, Float>? {
        if (viewportWidthPx <= 0f || viewportHeightPx <= 0f) return null
        if (!view.x.isFinite() || !view.y.isFinite() || !view.z.isFinite()) return null
        if (isBehindCamera(view)) return null
        if (view.x !in 0f..1f || view.y !in 0f..1f) return null
        val sx = view.x * viewportWidthPx
        val sy = (1f - view.y) * viewportHeightPx
        if (sx !in 0f..viewportWidthPx || sy !in 0f..viewportHeightPx) return null
        return sx to sy
    }

    fun isBehindCamera(view: ViewPoint): Boolean =
        view.z < 0f || view.z > 1f

    fun signature(labels: List<SensorArProjectedLabel>): String =
        labels.joinToString(separator = "|") { label ->
            "${label.sensorId}:${label.screenX.toInt()},${label.screenY.toInt()}:${label.detailLine}"
        }

    fun projectLabels(
        labels: List<SensorArIotLabel>,
        viewOf: (worldX: Float, worldY: Float, worldZ: Float) -> ViewPoint?,
        viewportWidthPx: Float,
        viewportHeightPx: Float
    ): List<SensorArProjectedLabel> {
        if (labels.isEmpty()) return emptyList()
        return labels.mapNotNull { label ->
            val view = viewOf(label.worldX, label.worldY, label.worldZ) ?: return@mapNotNull null
            val screen = project(view, viewportWidthPx, viewportHeightPx) ?: return@mapNotNull null
            SensorArProjectedLabel(
                sensorId = label.sensorId,
                titleLine = label.titleLine,
                detailLine = label.detailLine,
                screenX = screen.first,
                screenY = screen.second,
                typeAbbrev = label.abbrev
            )
        }
    }

    fun viewPointFromCamera(
        camera: CameraNode,
        worldX: Float,
        worldY: Float,
        worldZ: Float
    ): ViewPoint? {
        val view = camera.worldToView(Position(worldX, worldY, worldZ))
        if (!view.x.isFinite() || !view.y.isFinite()) return null
        return ViewPoint(view.x, view.y, 0.5f)
    }
}
