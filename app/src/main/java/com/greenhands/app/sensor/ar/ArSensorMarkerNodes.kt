package com.greenhands.app.sensor.ar

import androidx.compose.ui.graphics.Color
import com.google.android.filament.Engine
import com.google.android.filament.MaterialInstance
import com.greenhands.app.sensor.model.SensorType
import io.github.sceneview.math.Position
import io.github.sceneview.math.Size
import io.github.sceneview.node.CubeNode
import io.github.sceneview.node.Node

/**
 * SceneView 2.2.1 sensor markers as **children of the greenhouse frame root**
 * (one shared AR anchor/pose — no per-sensor anchors).
 *
 * Local child positions are greenhouse meters (X length, Y height, Z width),
 * matching [ArWorldMapper.localToWorld] for the same ALIGNED pose.
 *
 * Labels ("T · S1") are shown in the Compose overlay — ViewNode text requires
 * ViewAttachmentManager lifecycle that is fragile on SceneView 2.2.1; type is
 * distinguished in-scene by glyph shape + color (virtual-preview language).
 */
object ArSensorMarkerNodes {

    const val SENSOR_ROOT_PREFIX = "ar_sensor_"
    private const val STEM_THICK = 0.028f

    fun clearSensors(frameRoot: Node?) {
        if (frameRoot == null) return
        frameRoot.childNodes.toList()
            .filter { it.name?.startsWith(SENSOR_ROOT_PREFIX) == true }
            .forEach { child -> child.destroy() }
    }

    fun clearSensorsFromScene(nodes: MutableList<Node>) {
        nodes.forEach { node ->
            if (node.name == ArGreenhouseFrameNodes.FRAME_ROOT_NAME) {
                clearSensors(node)
            }
        }
        nodes.toList()
            .filter { it.name?.startsWith(SENSOR_ROOT_PREFIX) == true }
            .forEach { node ->
                node.destroy()
                nodes -= node
            }
    }

    fun findFrameRoot(nodes: List<Node>): Node? =
        nodes.firstOrNull { it.name == ArGreenhouseFrameNodes.FRAME_ROOT_NAME }

    fun attachSensors(
        engine: Engine,
        frameRoot: Node,
        markers: List<ArRealSensorPlacement.RenderMarker>,
        materialFor: (Color) -> MaterialInstance
    ) {
        clearSensors(frameRoot)
        markers.forEach { marker ->
            frameRoot.addChildNode(buildSensorNode(engine, marker, materialFor))
        }
    }

    fun typeColor(type: SensorType): Color = when (type) {
        SensorType.TEMPERATURE -> Color(0xFFE07A3D)
        SensorType.HUMIDITY -> Color(0xFF4A9FE0)
        SensorType.SOIL_MOISTURE -> Color(0xFFA0784A)
        SensorType.LIGHT_INTENSITY -> Color(0xFFE0C04A)
    }

    private fun buildSensorNode(
        engine: Engine,
        marker: ArRealSensorPlacement.RenderMarker,
        materialFor: (Color) -> MaterialInstance
    ): Node {
        val sensor = marker.sensor
        val lx = marker.local.x
        val ly = marker.local.y
        val lz = marker.local.z
        val active = marker.active
        val baseColor = if (active) typeColor(sensor.type) else Color(0xFF9AA3A0)
        val material = materialFor(baseColor)

        val group = Node(engine).apply {
            name = SENSOR_ROOT_PREFIX + sensor.id
            isEditable = false
            position = Position(lx, 0f, lz)
        }

        val stemH = ly.coerceAtLeast(0.05f)
        group.addChildNode(
            CubeNode(
                engine = engine,
                size = Size(STEM_THICK, stemH, STEM_THICK),
                center = Position(0f, stemH * 0.5f, 0f),
                materialInstance = material
            ).apply {
                name = SENSOR_ROOT_PREFIX + sensor.id + "_stem"
                isEditable = false
            }
        )

        val (glyphSize, glyphCenterY) = glyphSizeFor(sensor.type, active)
        group.addChildNode(
            CubeNode(
                engine = engine,
                size = glyphSize,
                center = Position(0f, ly + glyphCenterY, 0f),
                materialInstance = material
            ).apply {
                name = SENSOR_ROOT_PREFIX + sensor.id + "_glyph"
                isEditable = false
            }
        )

        // Compact type plate above glyph — carries abbreviation via node name for tests/debug;
        // visible as a small accent cube (shape already encodes type).
        val plate = when (sensor.type) {
            SensorType.TEMPERATURE -> Size(0.08f, 0.02f, 0.08f)
            SensorType.HUMIDITY -> Size(0.06f, 0.02f, 0.06f)
            SensorType.SOIL_MOISTURE -> Size(0.10f, 0.02f, 0.05f)
            SensorType.LIGHT_INTENSITY -> Size(0.10f, 0.015f, 0.10f)
        }
        group.addChildNode(
            CubeNode(
                engine = engine,
                size = plate,
                center = Position(0f, ly + glyphCenterY + 0.12f, 0f),
                materialInstance = materialFor(
                    if (active) Color(0xFFE8F0EC) else Color(0xFF7A8480)
                )
            ).apply {
                name = SENSOR_ROOT_PREFIX + sensor.id + "_tag_" + marker.abbrev
                isEditable = false
            }
        )

        return group
    }

    private fun glyphSizeFor(type: SensorType, active: Boolean): Pair<Size, Float> {
        val s = if (active) 1f else 0.85f
        return when (type) {
            SensorType.TEMPERATURE -> Size(0.14f * s, 0.14f * s, 0.14f * s) to (0.08f * s)
            SensorType.HUMIDITY -> Size(0.10f * s, 0.18f * s, 0.10f * s) to (0.10f * s)
            SensorType.SOIL_MOISTURE -> Size(0.16f * s, 0.11f * s, 0.09f * s) to (0.07f * s)
            SensorType.LIGHT_INTENSITY -> Size(0.16f * s, 0.05f * s, 0.16f * s) to (0.04f * s)
        }
    }
}
