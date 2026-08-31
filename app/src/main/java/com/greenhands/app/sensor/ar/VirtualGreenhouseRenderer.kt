package com.greenhands.app.sensor.ar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import com.greenhands.app.sensor.model.CellCoverageState
import com.greenhands.app.sensor.model.SensorStatus
import com.greenhands.app.sensor.model.SensorType
import com.greenhands.app.ui.theme.AmberWarning
import com.greenhands.app.ui.theme.ClimateTeal
import com.greenhands.app.ui.theme.ForestEmerald
import com.greenhands.app.ui.theme.NightBg
import com.greenhands.app.ui.theme.NightBorder
import com.greenhands.app.ui.theme.NightElevated
import com.greenhands.app.ui.theme.NightText
import com.greenhands.app.ui.theme.SoftError
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Distinct marker colors by sensor type (coverage cells keep G/Y/R). */
private fun sensorTypeColor(type: SensorType): Color = when (type) {
    SensorType.TEMPERATURE -> Color(0xFFE07A3D)
    SensorType.HUMIDITY -> Color(0xFF4A9FE0)
    SensorType.SOIL_MOISTURE -> Color(0xFFA0784A)
    SensorType.LIGHT_INTENSITY -> Color(0xFFE0C04A)
}

/**
 * Camera-free 3D greenhouse renderer. Consumes [ArVisualizationSnapshot] only —
 * no ViewModel, CoverageCalculator, or Optimizer access.
 *
 * Camera state is hoisted so the screen can provide a Reset View control.
 */
@Composable
fun VirtualGreenhouseRenderer(
    snapshot: ArVisualizationSnapshot,
    showCoverage: Boolean,
    showSensors: Boolean,
    showRecommendations: Boolean,
    camera: OrbitCameraState,
    onCameraChange: (OrbitCameraState) -> Unit,
    modifier: Modifier = Modifier
) {
    val length = snapshot.physical.lengthMeters.toFloat()
    val width = snapshot.physical.widthMeters.toFloat()
    val height = snapshot.physical.heightMeters.toFloat()
    val cell = snapshot.physical.cellSizeMeters.toFloat().coerceAtLeast(0.05f)
    val center = remember(length, width, height) {
        VirtualGreenhouseMath.greenhouseCenter(length, width, height)
    }
    val drawRecs = VirtualGreenhouseLabels.shouldDrawRecommendations(
        showRecommendations,
        snapshot
    )
    val cameraLatest = rememberUpdatedState(camera)
    val onCameraLatest = rememberUpdatedState(onCameraChange)

    // Reused paints — avoid allocating new Paint() for every sensor each frame.
    val labelPaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
    }
    val subLabelPaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .testTag("virtual_greenhouse_renderer")
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val c = cameraLatest.value
                    onCameraLatest.value(
                        c.copy(
                            yawDeg = c.yawDeg - pan.x * 0.35f,
                            pitchDeg = (c.pitchDeg + pan.y * 0.25f).coerceIn(8f, 82f),
                            distance = (c.distance / zoom.coerceIn(0.7f, 1.4f))
                                .coerceIn(3.5f, 80f),
                            panX = c.panX - pan.x * 0.002f * c.distance,
                            panZ = c.panZ - pan.y * 0.002f * c.distance
                        )
                    )
                }
            }
    ) {
        val look = center + Vec3(camera.panX, 0f, camera.panZ)
        val camPos = VirtualGreenhouseMath.cameraPosition(center, camera)
        fun proj(v: Vec3): ProjectedPoint? =
            VirtualGreenhouseMath.project(v, camPos, look, size.width, size.height)

        drawRect(NightBg)

        data class Quad(
            val a: Vec3,
            val b: Vec3,
            val c: Vec3,
            val d: Vec3,
            val fill: Color,
            val stroke: Color? = null,
            val strokeWidth: Float = 1.5f,
            val depthBias: Float = 0f
        )

        val ridgeY = height * 1.22f
        val ridge = Vec3(length * 0.5f, ridgeY, width * 0.5f)
        val quads = ArrayList<Quad>(
            snapshot.coverageCells.size + snapshot.grid.widthCells + snapshot.grid.heightCells + 24
        )

        // Floor base
        quads += Quad(
            Vec3(0f, 0f, 0f),
            Vec3(length, 0f, 0f),
            Vec3(length, 0f, width),
            Vec3(0f, 0f, width),
            NightElevated.copy(alpha = 0.95f),
            NightBorder.copy(alpha = 0.85f),
            strokeWidth = 2.5f,
            depthBias = 0.5f
        )

        // Semi-transparent walls (glasshouse feel)
        val wallFill = ForestEmerald.copy(alpha = 0.10f)
        val wallStroke = ForestEmerald.copy(alpha = 0.55f)
        quads += Quad(
            Vec3(0f, 0f, 0f), Vec3(length, 0f, 0f),
            Vec3(length, height, 0f), Vec3(0f, height, 0f),
            wallFill, wallStroke, 1.8f, 0.05f
        )
        quads += Quad(
            Vec3(0f, 0f, width), Vec3(length, 0f, width),
            Vec3(length, height, width), Vec3(0f, height, width),
            wallFill, wallStroke, 1.8f, 0.05f
        )
        quads += Quad(
            Vec3(0f, 0f, 0f), Vec3(0f, 0f, width),
            Vec3(0f, height, width), Vec3(0f, height, 0f),
            wallFill, wallStroke, 1.8f, 0.05f
        )
        quads += Quad(
            Vec3(length, 0f, 0f), Vec3(length, 0f, width),
            Vec3(length, height, width), Vec3(length, height, 0f),
            wallFill, wallStroke, 1.8f, 0.05f
        )

        // Pitched roof panels (triangle-as-quad with repeated apex)
        val roofFill = ForestEmerald.copy(alpha = 0.14f)
        val roofStroke = ForestEmerald.copy(alpha = 0.7f)
        quads += Quad(
            Vec3(0f, height, 0f),
            Vec3(length, height, 0f),
            ridge,
            ridge,
            roofFill, roofStroke, 2f, -0.04f
        )
        quads += Quad(
            Vec3(0f, height, width),
            Vec3(length, height, width),
            ridge,
            ridge,
            roofFill, roofStroke, 2f, -0.04f
        )
        quads += Quad(
            Vec3(0f, height, 0f),
            Vec3(0f, height, width),
            ridge,
            ridge,
            roofFill.copy(alpha = 0.12f), roofStroke, 2f, -0.03f
        )
        quads += Quad(
            Vec3(length, height, 0f),
            Vec3(length, height, width),
            ridge,
            ridge,
            roofFill.copy(alpha = 0.12f), roofStroke, 2f, -0.03f
        )

        if (showCoverage) {
            snapshot.coverageCells.forEach { cellData ->
                val x0 = cellData.column * cell
                val z0 = cellData.row * cell
                val x1 = x0 + cell
                val z1 = z0 + cell
                val inset = cell * 0.04f
                val fill = when (cellData.state) {
                    CellCoverageState.COVERED -> ForestEmerald.copy(alpha = 0.55f)
                    CellCoverageState.OVERLAP -> AmberWarning.copy(alpha = 0.68f)
                    CellCoverageState.BLIND_SPOT -> SoftError.copy(alpha = 0.48f)
                }
                val stroke = when (cellData.state) {
                    CellCoverageState.COVERED -> ForestEmerald.copy(alpha = 0.9f)
                    CellCoverageState.OVERLAP -> AmberWarning.copy(alpha = 0.95f)
                    CellCoverageState.BLIND_SPOT -> SoftError.copy(alpha = 0.85f)
                }
                quads += Quad(
                    Vec3(x0 + inset, 0.02f, z0 + inset),
                    Vec3(x1 - inset, 0.02f, z0 + inset),
                    Vec3(x1 - inset, 0.02f, z1 - inset),
                    Vec3(x0 + inset, 0.02f, z1 - inset),
                    fill,
                    stroke,
                    strokeWidth = 1.2f,
                    depthBias = 0.25f
                )
            }
        }

        fun quadDepth(q: Quad): Float {
            val mid = Vec3(
                (q.a.x + q.c.x) * 0.5f,
                (q.a.y + q.c.y) * 0.5f,
                (q.a.z + q.c.z) * 0.5f
            )
            return (proj(mid)?.depth ?: Float.MAX_VALUE) + q.depthBias
        }
        quads.sortByDescending { quadDepth(it) }

        val path = Path()
        quads.forEach { q ->
            val pa = proj(q.a) ?: return@forEach
            val pb = proj(q.b) ?: return@forEach
            val pc = proj(q.c) ?: return@forEach
            val pd = proj(q.d) ?: return@forEach
            path.reset()
            path.moveTo(pa.x, pa.y)
            path.lineTo(pb.x, pb.y)
            path.lineTo(pc.x, pc.y)
            path.lineTo(pd.x, pd.y)
            path.close()
            drawPath(path, q.fill)
            q.stroke?.let { drawPath(path, it, style = Stroke(width = q.strokeWidth)) }
        }

        // Floor grid lines (cell layout)
        val gridColor = NightBorder.copy(alpha = 0.55f)
        val cols = snapshot.grid.widthCells
        val rows = snapshot.grid.heightCells
        for (i in 0..cols) {
            val x = i * cell
            if (x > length + 0.01f) break
            val a = proj(Vec3(x.coerceAtMost(length), 0.03f, 0f)) ?: continue
            val b = proj(Vec3(x.coerceAtMost(length), 0.03f, width)) ?: continue
            drawLine(gridColor, Offset(a.x, a.y), Offset(b.x, b.y), strokeWidth = 1f)
        }
        for (j in 0..rows) {
            val z = j * cell
            if (z > width + 0.01f) break
            val a = proj(Vec3(0f, 0.03f, z.coerceAtMost(width))) ?: continue
            val b = proj(Vec3(length, 0.03f, z.coerceAtMost(width))) ?: continue
            drawLine(gridColor, Offset(a.x, a.y), Offset(b.x, b.y), strokeWidth = 1f)
        }

        // Structural frame edges (walls + ridge)
        val frame = ForestEmerald.copy(alpha = 0.95f)
        val edges = listOf(
            Vec3(0f, 0f, 0f) to Vec3(length, 0f, 0f),
            Vec3(length, 0f, 0f) to Vec3(length, 0f, width),
            Vec3(length, 0f, width) to Vec3(0f, 0f, width),
            Vec3(0f, 0f, width) to Vec3(0f, 0f, 0f),
            Vec3(0f, 0f, 0f) to Vec3(0f, height, 0f),
            Vec3(length, 0f, 0f) to Vec3(length, height, 0f),
            Vec3(length, 0f, width) to Vec3(length, height, width),
            Vec3(0f, 0f, width) to Vec3(0f, height, width),
            Vec3(0f, height, 0f) to Vec3(length, height, 0f),
            Vec3(length, height, 0f) to Vec3(length, height, width),
            Vec3(length, height, width) to Vec3(0f, height, width),
            Vec3(0f, height, width) to Vec3(0f, height, 0f),
            // Mid-wall studs
            Vec3(length * 0.5f, 0f, 0f) to Vec3(length * 0.5f, height, 0f),
            Vec3(length * 0.5f, 0f, width) to Vec3(length * 0.5f, height, width),
            Vec3(0f, 0f, width * 0.5f) to Vec3(0f, height, width * 0.5f),
            Vec3(length, 0f, width * 0.5f) to Vec3(length, height, width * 0.5f),
            // Roof ridge spokes
            Vec3(0f, height, 0f) to ridge,
            Vec3(length, height, 0f) to ridge,
            Vec3(length, height, width) to ridge,
            Vec3(0f, height, width) to ridge,
            Vec3(length * 0.5f, height, 0f) to ridge,
            Vec3(length * 0.5f, height, width) to ridge
        )
        edges.forEach { (a, b) ->
            val pa = proj(a) ?: return@forEach
            val pb = proj(b) ?: return@forEach
            drawLine(
                color = frame,
                start = Offset(pa.x, pa.y),
                end = Offset(pb.x, pb.y),
                strokeWidth = 3.5f,
                cap = StrokeCap.Round
            )
        }

        val mountY = (height * 0.32f).coerceIn(0.5f, 2.4f)
        val markerR = min(size.minDimension * 0.028f, 16f).coerceAtLeast(11f)
        val textSize = min(size.minDimension * 0.032f, 30f)
        val subTextSize = textSize * 0.78f

        if (showSensors) {
            snapshot.sensors.forEach { sensor ->
                val base = Vec3(sensor.xMeters.toFloat(), 0.04f, sensor.zMeters.toFloat())
                val top = Vec3(sensor.xMeters.toFloat(), mountY, sensor.zMeters.toFloat())
                val pb = proj(base) ?: return@forEach
                val pt = proj(top) ?: return@forEach
                val active = sensor.status == SensorStatus.ACTIVE
                val color = if (active) {
                    sensorTypeColor(sensor.type)
                } else {
                    NightText.copy(alpha = 0.32f)
                }
                drawLine(
                    color = color,
                    start = Offset(pb.x, pb.y),
                    end = Offset(pt.x, pt.y),
                    strokeWidth = if (active) 5f else 2.5f,
                    cap = StrokeCap.Round
                )
                // Coverage radius disc (snapshot radius only)
                if (active && showCoverage && sensor.coverageRadiusMeters > 0) {
                    drawCoverageRing(
                        proj = ::proj,
                        cx = sensor.xMeters.toFloat(),
                        cz = sensor.zMeters.toFloat(),
                        radiusM = sensor.coverageRadiusMeters.toFloat(),
                        color = color.copy(alpha = 0.45f)
                    )
                }
                drawSensorGlyph(
                    type = sensor.type,
                    center = Offset(pt.x, pt.y),
                    radius = markerR,
                    fill = color,
                    muted = !active
                )
                val (abbr, id) = VirtualGreenhouseLabels.sensorMarkerLines(sensor)
                labelPaint.textSize = textSize
                labelPaint.color = if (active) {
                    android.graphics.Color.WHITE
                } else {
                    android.graphics.Color.argb(120, 200, 210, 200)
                }
                subLabelPaint.textSize = subTextSize
                subLabelPaint.color = labelPaint.color
                drawContext.canvas.nativeCanvas.drawText(abbr, pt.x, pt.y - markerR - 6f, labelPaint)
                drawContext.canvas.nativeCanvas.drawText(
                    id,
                    pt.x,
                    pt.y - markerR - 6f - textSize * 0.95f,
                    subLabelPaint
                )
            }
        }

        if (drawRecs) {
            snapshot.recommendations.forEach { rec ->
                val base = Vec3(rec.xMeters.toFloat(), 0.04f, rec.zMeters.toFloat())
                val top = Vec3(rec.xMeters.toFloat(), mountY * 0.95f, rec.zMeters.toFloat())
                val pb = proj(base) ?: return@forEach
                val pt = proj(top) ?: return@forEach
                val color = if (rec.selected) ClimateTeal else ClimateTeal.copy(alpha = 0.5f)
                // Dashed-style stem (distinct from solid sensor poles)
                val mid = Offset((pb.x + pt.x) * 0.5f, (pb.y + pt.y) * 0.5f)
                drawLine(color, Offset(pb.x, pb.y), mid, strokeWidth = 3f, cap = StrokeCap.Round)
                drawLine(
                    color.copy(alpha = 0.35f),
                    mid,
                    Offset(pt.x, pt.y),
                    strokeWidth = 3f,
                    cap = StrokeCap.Round
                )
                // Hollow diamond — clearly not a real sensor
                drawRecommendationDiamond(Offset(pt.x, pt.y), markerR * 1.15f, color)
                labelPaint.textSize = textSize
                labelPaint.color = android.graphics.Color.parseColor("#2EC4B6")
                subLabelPaint.textSize = subTextSize * 0.9f
                subLabelPaint.color = android.graphics.Color.parseColor("#2EC4B6")
                drawContext.canvas.nativeCanvas.drawText(
                    VirtualGreenhouseLabels.recommendationPrimary(rec),
                    pt.x,
                    pt.y - markerR - 8f,
                    labelPaint
                )
                drawContext.canvas.nativeCanvas.drawText(
                    VirtualGreenhouseLabels.recommendationSecondary(),
                    pt.x,
                    pt.y - markerR - 8f - textSize * 0.9f,
                    subLabelPaint
                )
            }
        }
    }
}

private fun DrawScope.drawCoverageRing(
    proj: (Vec3) -> ProjectedPoint?,
    cx: Float,
    cz: Float,
    radiusM: Float,
    color: Color
) {
    val segments = 28
    var prev: Offset? = null
    var first: Offset? = null
    for (i in 0..segments) {
        val ang = (Math.PI * 2.0 * i / segments)
        val wx = cx + radiusM * cos(ang).toFloat()
        val wz = cz + radiusM * sin(ang).toFloat()
        val p = proj(Vec3(wx, 0.05f, wz)) ?: continue
        val o = Offset(p.x, p.y)
        if (first == null) first = o
        prev?.let { drawLine(color, it, o, strokeWidth = 2.5f) }
        prev = o
    }
}

private fun DrawScope.drawSensorGlyph(
    type: SensorType,
    center: Offset,
    radius: Float,
    fill: Color,
    muted: Boolean
) {
    val stroke = if (muted) NightText.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.9f)
    when (type) {
        SensorType.TEMPERATURE -> {
            // Circle
            drawCircle(fill, radius = radius, center = center)
            drawCircle(stroke, radius = radius, center = center, style = Stroke(width = 2f))
        }
        SensorType.HUMIDITY -> {
            // Diamond
            val path = Path().apply {
                moveTo(center.x, center.y - radius)
                lineTo(center.x + radius, center.y)
                lineTo(center.x, center.y + radius)
                lineTo(center.x - radius, center.y)
                close()
            }
            drawPath(path, fill)
            drawPath(path, stroke, style = Stroke(width = 2f))
        }
        SensorType.SOIL_MOISTURE -> {
            // Triangle
            val path = Path().apply {
                moveTo(center.x, center.y - radius)
                lineTo(center.x + radius * 0.95f, center.y + radius * 0.75f)
                lineTo(center.x - radius * 0.95f, center.y + radius * 0.75f)
                close()
            }
            drawPath(path, fill)
            drawPath(path, stroke, style = Stroke(width = 2f))
        }
        SensorType.LIGHT_INTENSITY -> {
            // Square
            val s = radius * 1.5f
            drawRect(
                fill,
                topLeft = Offset(center.x - s * 0.5f, center.y - s * 0.5f),
                size = Size(s, s)
            )
            drawRect(
                stroke,
                topLeft = Offset(center.x - s * 0.5f, center.y - s * 0.5f),
                size = Size(s, s),
                style = Stroke(width = 2f)
            )
        }
    }
}

private fun DrawScope.drawRecommendationDiamond(center: Offset, radius: Float, color: Color) {
    val path = Path().apply {
        moveTo(center.x, center.y - radius)
        lineTo(center.x + radius, center.y)
        lineTo(center.x, center.y + radius)
        lineTo(center.x - radius, center.y)
        close()
    }
    drawPath(path, NightBg.copy(alpha = 0.85f))
    drawPath(path, color, style = Stroke(width = 3.5f))
    drawCircle(color, radius = radius * 0.28f, center = center)
}

/** Resets orbit camera for the given snapshot dimensions. */
fun defaultOrbitForSnapshot(snapshot: ArVisualizationSnapshot): OrbitCameraState {
    val l = snapshot.physical.lengthMeters.toFloat()
    val w = snapshot.physical.widthMeters.toFloat()
    val h = snapshot.physical.heightMeters.toFloat()
    return VirtualGreenhouseMath.defaultOrbit(l, w, h)
}
