package com.greenhands.app.sensor.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.greenhands.app.R
import com.greenhands.app.sensor.model.CellCoverageState
import com.greenhands.app.sensor.model.CoverageByType
import com.greenhands.app.sensor.model.CoverageResult
import com.greenhands.app.sensor.model.Greenhouse
import com.greenhands.app.sensor.model.GreenhousePhysicalConfig
import com.greenhands.app.sensor.model.Sensor
import com.greenhands.app.sensor.model.SensorStatus
import com.greenhands.app.sensor.model.SensorType
import com.greenhands.app.ui.components.ConfigurationProgress
import com.greenhands.app.ui.components.SectionHeading
import com.greenhands.app.ui.theme.AmberWarning
import com.greenhands.app.ui.theme.ClimateTeal
import com.greenhands.app.ui.theme.ClimateTealSoft
import com.greenhands.app.ui.theme.ForestEmerald
import com.greenhands.app.ui.theme.NightBg
import com.greenhands.app.ui.theme.NightBorder
import com.greenhands.app.ui.theme.NightElevated
import com.greenhands.app.ui.theme.NightMuted
import com.greenhands.app.ui.theme.NightText
import com.greenhands.app.ui.theme.SoftError
import com.greenhands.app.ui.theme.Spacing
import kotlin.math.floor
import kotlin.math.min

/** Map overlay for a recommended (not yet applied) sensor position. */
data class OptimizationMapMarker(
    val x: Double,
    val y: Double,
    val label: String,
    val selected: Boolean = true
)

/** Cell fill colors for CoverageResult states. Tuned so OVERLAP stays clearly yellow on phones. */
fun coverageCellFill(state: CellCoverageState): Color = when (state) {
    CellCoverageState.COVERED -> ForestEmerald.copy(alpha = 0.58f)
    CellCoverageState.OVERLAP -> AmberWarning.copy(alpha = 0.82f)
    CellCoverageState.BLIND_SPOT -> SoftError.copy(alpha = 0.55f)
}

@Composable
fun CoverageStateLegend(
    modifier: Modifier = Modifier,
    testTagPrefix: String = "coverage_legend"
) {
    Column(modifier.fillMaxWidth().testTag("${testTagPrefix}_card")) {
        Text(
            text = stringResource(R.string.sensor_coverage_legend_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Spacing.sm))
        // Stacked rows avoid narrow vertical wrapping of long labels on Medium Phone.
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            CoverageLegendSwatch(
                color = coverageCellFill(CellCoverageState.COVERED),
                label = stringResource(R.string.sensor_coverage_legend_covered),
                testTag = "${testTagPrefix}_covered"
            )
            CoverageLegendSwatch(
                color = coverageCellFill(CellCoverageState.OVERLAP),
                label = stringResource(R.string.sensor_coverage_legend_overlap),
                testTag = "${testTagPrefix}_overlap"
            )
            CoverageLegendSwatch(
                color = coverageCellFill(CellCoverageState.BLIND_SPOT),
                label = stringResource(R.string.sensor_coverage_legend_blind),
                testTag = "${testTagPrefix}_blind"
            )
        }
    }
}

@Composable
fun SensorTypeLegend(
    modifier: Modifier = Modifier,
    testTagPrefix: String = "sensor_type_legend"
) {
    Column(modifier.fillMaxWidth().testTag("${testTagPrefix}_card")) {
        Text(
            text = stringResource(R.string.sensor_type_legend_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Spacing.sm))
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            SensorType.entries.forEach { type ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("${testTagPrefix}_${type.name}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Text(
                        text = type.markerAbbreviation,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = ForestEmerald,
                        modifier = Modifier.widthIn(min = 28.dp)
                    )
                    Text(
                        text = sensorTypeShortName(type),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun sensorTypeShortName(type: SensorType): String = when (type) {
    SensorType.TEMPERATURE -> stringResource(R.string.sensor_type_name_temperature)
    SensorType.HUMIDITY -> stringResource(R.string.sensor_type_name_humidity)
    SensorType.SOIL_MOISTURE -> stringResource(R.string.sensor_type_name_soil_moisture)
    SensorType.LIGHT_INTENSITY -> stringResource(R.string.sensor_type_name_light_intensity)
}

@Composable
private fun CoverageLegendSwatch(
    color: Color,
    label: String,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .background(color, RoundedCornerShape(3.dp))
                .border(BorderStroke(1.dp, NightBorder.copy(alpha = 0.7f)), RoundedCornerShape(3.dp))
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            softWrap = true
        )
    }
}

/**
 * Schematic greenhouse map: frame + CoverageResult cell tints + translucent radius rings.
 * CoverageResult remains the source of truth; circles are visual aids only.
 *
 * Height is driven by [aspectRatio] from the greenhouse grid (not an unbounded
 * BoxWithConstraints inside verticalScroll), so the map cannot expand into a blank viewport gap.
 *
 * @param sensors markers shown on the map
 * @param ringSensors sensors whose coverage rings are drawn (defaults to [sensors])
 */
@Composable
fun GreenhouseCoverageMap(
    greenhouse: Greenhouse,
    coverage: CoverageResult,
    sensors: List<Sensor>,
    modifier: Modifier = Modifier,
    selectedSensorId: String? = null,
    radiusEmphasis: Float = 1f,
    markerTestTagPrefix: String = "sensor_marker",
    ringSensors: List<Sensor> = sensors,
    /** Optional recommendation overlays (Optimize stage). Not real sensors. */
    recommendationMarkers: List<OptimizationMapMarker> = emptyList(),
    onTapCell: ((column: Int, row: Int) -> Unit)? = null
) {
    val columns = greenhouse.widthCells
    val rows = greenhouse.heightCells
    val coveredColor = coverageCellFill(CellCoverageState.COVERED)
    val overlapColor = coverageCellFill(CellCoverageState.OVERLAP)
    val blindColor = coverageCellFill(CellCoverageState.BLIND_SPOT)
    val roofHeight = 28.dp

    Column(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .testTag("greenhouse_map")
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(roofHeight)
                .testTag("greenhouse_roof")
        ) {
            val stroke = NightBorder.copy(alpha = 0.95f)
            val accent = ForestEmerald.copy(alpha = 0.55f)
            val midX = size.width / 2f
            val path = Path().apply {
                moveTo(0f, size.height)
                lineTo(midX, 2f)
                lineTo(size.width, size.height)
                close()
            }
            drawPath(path, color = NightElevated.copy(alpha = 0.85f))
            drawPath(path, color = accent, style = Stroke(width = 2.5f))
            drawLine(stroke, Offset(0f, size.height), Offset(size.width, size.height), 2f)
            drawLine(accent, Offset(midX, 2f), Offset(midX, size.height), 1.5f)
            val ribStep = size.width / 6f
            for (i in 1 until 6) {
                val x = i * ribStep
                drawLine(
                    color = ClimateTeal.copy(alpha = 0.25f),
                    start = Offset(midX, 4f),
                    end = Offset(x, size.height),
                    strokeWidth = 1f
                )
            }
        }

        // Aspect-ratio grid: height = width * rows/columns (content-sized, scroll-safe).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(columns.toFloat() / rows.toFloat())
                .background(NightBg)
                .border(BorderStroke(2.dp, ForestEmerald.copy(alpha = 0.45f)))
                .then(
                    if (onTapCell != null) {
                        Modifier.pointerInput(columns, rows, sensors, selectedSensorId, coverage) {
                            detectTapGestures { offset ->
                                val cellW = size.width / columns
                                val cellH = size.height / rows
                                val column = (offset.x / cellW).toInt().coerceIn(0, columns - 1)
                                val row = (offset.y / cellH).toInt().coerceIn(0, rows - 1)
                                onTapCell(column, row)
                            }
                        }
                    } else {
                        Modifier
                    }
                )
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val cellW = size.width / columns
                val cellH = size.height / rows

                drawRect(NightElevated.copy(alpha = 0.55f))

                for (col in 0 until columns step 2) {
                    drawRect(
                        color = ForestEmerald.copy(alpha = 0.05f),
                        topLeft = Offset(col * cellW, 0f),
                        size = Size(cellW, size.height)
                    )
                }

                coverage.cells.forEach { gridCell ->
                    val fill = when (gridCell.state) {
                        CellCoverageState.COVERED -> coveredColor
                        CellCoverageState.OVERLAP -> overlapColor
                        CellCoverageState.BLIND_SPOT -> blindColor
                    }
                    drawRect(
                        color = fill,
                        topLeft = Offset(gridCell.x * cellW, gridCell.y * cellH),
                        size = Size(cellW, cellH)
                    )
                }

                val unit = min(cellW, cellH)
                ringSensors.forEach { sensor ->
                    if (sensor.status != SensorStatus.ACTIVE) return@forEach
                    val center = Offset(
                        ((sensor.x + 0.5) * cellW).toFloat(),
                        ((sensor.y + 0.5) * cellH).toFloat()
                    )
                    val radiusPx = (sensor.coverageRadius * unit).toFloat()
                    val selected = sensor.id == selectedSensorId
                    val strokeAlpha = (0.28f * radiusEmphasis).coerceIn(0.16f, 0.40f)
                    val ringColor = if (selected) {
                        AmberWarning.copy(alpha = strokeAlpha + 0.10f)
                    } else {
                        NightText.copy(alpha = strokeAlpha)
                    }
                    drawCircle(
                        color = ringColor,
                        radius = radiusPx,
                        center = center,
                        style = Stroke(width = if (selected) 2f else 1.5f)
                    )
                }

                for (x in 0..columns) {
                    drawLine(
                        NightBorder.copy(alpha = 0.85f),
                        Offset(x * cellW, 0f),
                        Offset(x * cellW, size.height),
                        strokeWidth = 1f
                    )
                }
                for (y in 0..rows) {
                    drawLine(
                        NightBorder.copy(alpha = 0.85f),
                        Offset(0f, y * cellH),
                        Offset(size.width, y * cellH),
                        strokeWidth = 1f
                    )
                }

                drawLine(
                    ForestEmerald.copy(alpha = 0.35f),
                    Offset(0f, 0f),
                    Offset(0f, size.height),
                    strokeWidth = 3f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    ForestEmerald.copy(alpha = 0.35f),
                    Offset(size.width, 0f),
                    Offset(size.width, size.height),
                    strokeWidth = 3f,
                    cap = StrokeCap.Round
                )
            }

            // Marker positions need measured cell size; parent Box is already height-bounded.
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val cellWidth = maxWidth / columns
                val cellHeight = maxHeight / rows
                sensors.forEach { sensor ->
                    val selected = sensor.id == selectedSensorId
                    Box(
                        modifier = Modifier
                            .offset(
                                x = cellWidth * sensor.cellColumn(),
                                y = cellHeight * sensor.cellRow()
                            )
                            .size(width = cellWidth, height = cellHeight),
                        contentAlignment = Alignment.Center
                    ) {
                        SensorTypeMarker(
                            sensor = sensor,
                            selected = selected,
                            testTag = "${markerTestTagPrefix}_${sensor.id}"
                        )
                    }
                }
                recommendationMarkers.forEach { marker ->
                    Box(
                        modifier = Modifier
                            .offset(
                                x = cellWidth * floor(marker.x).toInt(),
                                y = cellHeight * floor(marker.y).toInt()
                            )
                            .size(width = cellWidth, height = cellHeight),
                        contentAlignment = Alignment.Center
                    ) {
                        RecommendationMarker(
                            label = marker.label,
                            selected = marker.selected,
                            testTag = "opt_marker_${marker.label}"
                        )
                    }
                }
            }
        }
    }
}

private fun Sensor.cellColumn(): Int = floor(x).toInt()

private fun Sensor.cellRow(): Int = floor(y).toInt()

/**
 * Neutral schematic greenhouse for Setup: roof, walls, crop rows, and the configured grid.
 * Height follows the logical cell aspect ratio — not a fixed 12×8 visual.
 */
@Composable
fun VirtualGreenhouseSchematic(
    greenhouse: Greenhouse,
    config: GreenhousePhysicalConfig,
    modifier: Modifier = Modifier
) {
    val columns = greenhouse.widthCells
    val rows = greenhouse.heightCells
    val roofHeight = 28.dp
    Column(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .testTag("virtual_greenhouse_schematic")
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(roofHeight)
                .testTag("virtual_greenhouse_roof")
        ) {
            val accent = ForestEmerald.copy(alpha = 0.55f)
            val midX = size.width / 2f
            val path = Path().apply {
                moveTo(0f, size.height)
                lineTo(midX, 2f)
                lineTo(size.width, size.height)
                close()
            }
            drawPath(path, color = NightElevated.copy(alpha = 0.85f))
            drawPath(path, color = accent, style = Stroke(width = 2.5f))
            drawLine(accent, Offset(midX, 2f), Offset(midX, size.height), 1.5f)
            val ribStep = size.width / 6f
            for (i in 1 until 6) {
                drawLine(
                    color = ClimateTeal.copy(alpha = 0.25f),
                    start = Offset(midX, 4f),
                    end = Offset(i * ribStep, size.height),
                    strokeWidth = 1f
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(columns.toFloat() / rows.toFloat())
                .background(NightBg)
                .border(BorderStroke(2.dp, ForestEmerald.copy(alpha = 0.45f)))
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val cellW = size.width / columns
                val cellH = size.height / rows
                drawRect(NightElevated.copy(alpha = 0.7f))
                for (col in 0 until columns step 2) {
                    drawRect(
                        color = ForestEmerald.copy(alpha = 0.10f),
                        topLeft = Offset(col * cellW, 0f),
                        size = Size(cellW, size.height)
                    )
                }
                for (x in 0..columns) {
                    drawLine(
                        NightBorder.copy(alpha = 0.75f),
                        Offset(x * cellW, 0f),
                        Offset(x * cellW, size.height),
                        strokeWidth = 1f
                    )
                }
                for (y in 0..rows) {
                    drawLine(
                        NightBorder.copy(alpha = 0.75f),
                        Offset(0f, y * cellH),
                        Offset(size.width, y * cellH),
                        strokeWidth = 1f
                    )
                }
                drawLine(
                    ForestEmerald.copy(alpha = 0.4f),
                    Offset(0f, 0f),
                    Offset(0f, size.height),
                    strokeWidth = 3f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    ForestEmerald.copy(alpha = 0.4f),
                    Offset(size.width, 0f),
                    Offset(size.width, size.height),
                    strokeWidth = 3f,
                    cap = StrokeCap.Round
                )
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = stringResource(
                R.string.sensor_setup_dims_summary,
                formatMeters(config.lengthMeters),
                formatMeters(config.widthMeters),
                formatMeters(config.heightMeters),
                formatMeters(config.cellSizeMeters)
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("virtual_greenhouse_dims")
        )
        Text(
            text = stringResource(
                R.string.sensor_setup_grid_summary,
                greenhouse.widthCells,
                greenhouse.heightCells,
                greenhouse.totalCells
            ),
            style = MaterialTheme.typography.labelMedium,
            color = ForestEmerald,
            modifier = Modifier.testTag("virtual_greenhouse_grid_summary")
        )
    }
}

private fun formatMeters(value: Double): String {
    val asInt = value.toInt()
    return if (value == asInt.toDouble()) asInt.toString()
    else String.format(java.util.Locale.US, "%.1f", value)
}

@Composable
private fun SensorTypeMarker(
    sensor: Sensor,
    selected: Boolean,
    testTag: String
) {
    val shape = RoundedCornerShape(6.dp)
    val borderColor = when {
        selected -> AmberWarning
        sensor.status == SensorStatus.INACTIVE -> NightMuted
        else -> ForestEmerald
    }
    Column(
        modifier = Modifier
            .widthIn(min = 22.dp, max = 34.dp)
            .background(NightElevated.copy(alpha = 0.96f), shape)
            .border(
                BorderStroke(if (selected) 2.dp else 1.25.dp, borderColor),
                shape
            )
            .padding(horizontal = 3.dp, vertical = 2.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = sensor.type.markerAbbreviation,
            color = if (sensor.status == SensorStatus.INACTIVE) NightMuted else ForestEmerald,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
                lineHeight = 10.sp
            ),
            textAlign = TextAlign.Center,
            maxLines = 1
        )
        Text(
            text = sensor.id,
            color = NightText.copy(alpha = if (sensor.status == SensorStatus.INACTIVE) 0.65f else 1f),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 8.sp,
                lineHeight = 9.sp
            ),
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
private fun RecommendationMarker(
    label: String,
    selected: Boolean,
    testTag: String
) {
    val shape = RoundedCornerShape(6.dp)
    val border = if (selected) ClimateTeal else ClimateTeal.copy(alpha = 0.45f)
    Box(
        modifier = Modifier
            .widthIn(min = 22.dp, max = 34.dp)
            .background(
                if (selected) ClimateTeal.copy(alpha = 0.92f) else NightElevated.copy(alpha = 0.9f),
                shape
            )
            .border(BorderStroke(if (selected) 2.dp else 1.25.dp, border), shape)
            .padding(horizontal = 3.dp, vertical = 4.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) NightBg else ClimateTealSoft,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                lineHeight = 11.sp
            ),
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
fun SensorWorkflowHeader(
    subtitle: String,
    activeStepIndex: Int,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.sensor_component_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth().testTag("sensor_component_label")
        )
        Spacer(Modifier.height(Spacing.xs))
        SectionHeading(
            title = stringResource(R.string.sensor_component_heading),
            subtitle = subtitle
        )
        Spacer(Modifier.height(Spacing.md))
        ConfigurationProgress(
            step = activeStepIndex + 1,
            total = 5,
            modifier = Modifier.testTag("sensor_progress_steps")
        )
        Spacer(Modifier.height(Spacing.sm))
        SensorStepLabels(activeIndex = activeStepIndex)
    }
}

@Composable
fun SensorStepLabels(
    activeIndex: Int,
    modifier: Modifier = Modifier
) {
    val labels = listOf(
        stringResource(R.string.sensor_step_setup),
        stringResource(R.string.sensor_step_scan),
        stringResource(R.string.sensor_step_place),
        stringResource(R.string.sensor_step_coverage),
        stringResource(R.string.sensor_step_optimize)
    )
    Column(modifier.fillMaxWidth().testTag("sensor_step_labels")) {
        labels.forEachIndexed { index, label ->
            val active = index == activeIndex
            Text(
                text = "${index + 1}  $label",
                style = if (active) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodySmall,
                color = if (active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.padding(vertical = Spacing.xxs)
            )
        }
    }
}

/**
 * null = All (monitoring map: covered vs blind, no cross-type overlap coloring).
 * Non-null = same-type coverage map for that [SensorType].
 */
@Composable
fun CoverageMapTypeFilter(
    selectedType: SensorType?,
    onSelect: (SensorType?) -> Unit,
    modifier: Modifier = Modifier,
    testTagPrefix: String = "coverage_filter"
) {
    Column(modifier.fillMaxWidth().testTag("${testTagPrefix}_row")) {
        Text(
            text = stringResource(R.string.sensor_coverage_filter_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Spacing.sm))
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                CoverageFilterChip(
                    label = stringResource(R.string.sensor_coverage_filter_all),
                    selected = selectedType == null,
                    onClick = { onSelect(null) },
                    testTag = "${testTagPrefix}_all"
                )
                CoverageFilterChip(
                    label = stringResource(R.string.sensor_type_name_temperature),
                    selected = selectedType == SensorType.TEMPERATURE,
                    onClick = { onSelect(SensorType.TEMPERATURE) },
                    testTag = "${testTagPrefix}_TEMPERATURE"
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                CoverageFilterChip(
                    label = stringResource(R.string.sensor_type_name_humidity),
                    selected = selectedType == SensorType.HUMIDITY,
                    onClick = { onSelect(SensorType.HUMIDITY) },
                    testTag = "${testTagPrefix}_HUMIDITY"
                )
                CoverageFilterChip(
                    label = stringResource(R.string.sensor_type_name_soil_moisture),
                    selected = selectedType == SensorType.SOIL_MOISTURE,
                    onClick = { onSelect(SensorType.SOIL_MOISTURE) },
                    testTag = "${testTagPrefix}_SOIL_MOISTURE"
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                CoverageFilterChip(
                    label = stringResource(R.string.sensor_type_name_light_intensity),
                    selected = selectedType == SensorType.LIGHT_INTENSITY,
                    onClick = { onSelect(SensorType.LIGHT_INTENSITY) },
                    testTag = "${testTagPrefix}_LIGHT_INTENSITY"
                )
            }
        }
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = if (selectedType == null) {
                stringResource(R.string.sensor_coverage_filter_all_hint)
            } else {
                stringResource(R.string.sensor_coverage_filter_type_hint)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CoverageFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val shape = RoundedCornerShape(8.dp)
    val border = if (selected) ForestEmerald else NightBorder
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) ForestEmerald else NightText,
        modifier = Modifier
            .background(
                color = if (selected) ForestEmerald.copy(alpha = 0.16f) else NightElevated,
                shape = shape
            )
            .border(BorderStroke(1.dp, border), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
            .testTag(testTag)
    )
}

@Composable
fun CoverageByTypeSection(
    coverageByType: CoverageByType,
    modifier: Modifier = Modifier,
    testTagPrefix: String = "coverage_by_type"
) {
    Column(modifier.fillMaxWidth().testTag("${testTagPrefix}_card")) {
        Text(
            text = stringResource(R.string.sensor_coverage_by_type_title),
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = stringResource(R.string.sensor_coverage_by_type_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Spacing.md))
        SensorType.entries.forEach { type ->
            val result = coverageByType.forType(type)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.xs)
                    .testTag("${testTagPrefix}_${type.name}")
            ) {
                Text(
                    text = sensorTypeShortName(type),
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(Modifier.height(Spacing.xxs))
                Text(
                    text = stringResource(
                        R.string.sensor_coverage_by_type_coverage,
                        String.format(java.util.Locale.US, "%.0f", result.overallCoveragePercent)
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = stringResource(
                        R.string.sensor_coverage_by_type_overlap,
                        result.overlapCells
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(
                        R.string.sensor_coverage_by_type_blind,
                        result.blindSpotCells
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (result.overallCoveragePercent == 0.0 && result.blindSpotCells == result.totalCells) {
                    Text(
                        text = stringResource(R.string.sensor_coverage_by_type_none),
                        style = MaterialTheme.typography.labelSmall,
                        color = NightMuted
                    )
                }
            }
        }
    }
}

fun displayedMapCoverage(
    coverageByType: CoverageByType,
    filterType: SensorType?
): CoverageResult =
    if (filterType == null) coverageByType.monitoring else coverageByType.forType(filterType)

fun displayedRingSensors(
    sensors: List<Sensor>,
    filterType: SensorType?
): List<Sensor> =
    if (filterType == null) sensors else sensors.filter { it.type == filterType }

/** Markers for the active map filter; All keeps every sensor visible. */
fun displayedMapSensors(
    sensors: List<Sensor>,
    filterType: SensorType?
): List<Sensor> = displayedRingSensors(sensors, filterType)
