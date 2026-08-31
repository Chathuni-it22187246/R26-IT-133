package com.greenhands.app.sensor.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Grass
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.greenhands.app.R
import com.greenhands.app.sensor.domain.GridTapResult
import com.greenhands.app.sensor.domain.PlacementInteraction
import com.greenhands.app.sensor.model.DEFAULT_COVERAGE_RADIUS_CELLS
import com.greenhands.app.sensor.model.Sensor
import com.greenhands.app.sensor.model.SensorStatus
import com.greenhands.app.sensor.model.SensorType
import com.greenhands.app.ui.components.DemoNotice
import com.greenhands.app.ui.components.InfoCard
import com.greenhands.app.ui.components.PrimaryActionButton
import com.greenhands.app.ui.components.SecondaryActionButton
import com.greenhands.app.ui.components.StatusChip
import com.greenhands.app.ui.components.WarningPanel
import com.greenhands.app.ui.components.screenHorizontalPadding
import com.greenhands.app.ui.theme.ForestEmerald
import com.greenhands.app.ui.theme.NightElevated
import com.greenhands.app.ui.theme.Radii
import com.greenhands.app.ui.theme.Spacing
import com.greenhands.app.ui.theme.Stroke as GhStroke

/**
 * Place Sensors uses a local fillMaxSize Scaffold (same pattern as Scan Greenhouse) so the
 * scroll viewport is bounded. Continue is in the scroll content (not a sticky bottomBar) so
 * Placement Summary / actions are never clipped. [GreenhouseCoverageMap] keeps aspectRatio sizing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceSensorsScreen(
    ui: SensorPlacementUiState,
    onAddSensor: (x: Double, y: Double) -> Boolean,
    onSelectSensor: (String) -> Unit,
    onMoveSensor: (id: String, x: Double, y: Double) -> Boolean,
    onDeleteSensor: (String) -> Unit,
    onSetSensorActive: (id: String, active: Boolean) -> Unit,
    onDeselectSensor: () -> Unit,
    onResetSensors: () -> Unit,
    onOpenSensorTypePicker: () -> Unit,
    onSelectPendingSensorType: (SensorType) -> Unit,
    onConfirmPendingSensorType: () -> Unit,
    onCancelSensorTypePicker: () -> Unit,
    onContinueToCoverage: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    val selected = ui.selectedSensor
    val awaitingPlacement = ui.awaitingCellPlacement
    var mapTypeFilter by remember { mutableStateOf<SensorType?>(null) }
    val mapCoverage = displayedMapCoverage(ui.coverageByType, mapTypeFilter)
    val ringSensors = displayedRingSensors(ui.sensors, mapTypeFilter)
    val mapSensors = displayedMapSensors(ui.sensors, mapTypeFilter)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CenterAlignedTopAppBar(
                windowInsets = WindowInsets.statusBars,
                title = {
                    Text(
                        text = stringResource(R.string.sensor_place_title),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics { heading() },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.size(Spacing.touch)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.cd_back)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        // Continue lives in the scroll content so cards are never clipped behind a sticky CTA.
        // App bottom navigation still needs clearance via scroll bottom padding (see Column below).
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = screenHorizontalPadding())
                .padding(
                    top = Spacing.afterAppBar,
                    // Clear the authenticated bottom nav so Reset / Continue stay fully readable.
                    bottom = Spacing.xxxl + Spacing.xxl + Spacing.navClearance
                )
                .testTag("place_sensors"),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SensorWorkflowHeader(
                subtitle = stringResource(R.string.sensor_place_subtitle),
                activeStepIndex = 2
            )
            Spacer(Modifier.height(Spacing.md))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                StatusChip(
                    text = stringResource(R.string.sensor_place_count, ui.sensorCount),
                    modifier = Modifier.testTag("place_sensor_count")
                )
                StatusChip(
                    text = stringResource(
                        R.string.sensor_place_coverage,
                        String.format(java.util.Locale.US, "%.0f", ui.coverage.overallCoveragePercent)
                    ),
                    modifier = Modifier.testTag("place_coverage_percent")
                )
                StatusChip(
                    text = stringResource(R.string.sensor_place_blind_spots, ui.coverage.blindSpotCells),
                    warning = ui.coverage.blindSpotCells > 0
                )
            }
            Spacer(Modifier.height(Spacing.sm))
            // Stack these chips vertically. A horizontal Row squeezed the long overlap
            // hint against "Selected radius" (only present when a sensor is selected),
            // giving the radius chip ~0 width and wrapping it into a tall letter column.
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                StatusChip(
                    text = stringResource(R.string.sensor_place_overlap_hint),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("place_overlap_cells")
                )
                if (selected != null) {
                    StatusChip(
                        text = stringResource(
                            R.string.sensor_place_selected_radius_chip,
                            String.format(java.util.Locale.US, "%.1f", selected.coverageRadius)
                        ),
                        modifier = Modifier.testTag("place_selected_radius")
                    )
                }
            }
            Spacer(Modifier.height(Spacing.md))

            if (ui.showSensorTypePicker) {
                SensorTypePickerCard(
                    selectedType = ui.pendingSensorType ?: SensorType.TEMPERATURE,
                    onSelectType = onSelectPendingSensorType,
                    onConfirm = onConfirmPendingSensorType,
                    onCancel = onCancelSensorTypePicker
                )
                Spacer(Modifier.height(Spacing.section))
            }

            DemoNotice(
                text = when {
                    awaitingPlacement && ui.pendingSensorType != null ->
                        stringResource(
                            R.string.sensor_place_ready_type,
                            sensorTypeLabel(ui.pendingSensorType)
                        )
                    awaitingPlacement -> stringResource(R.string.sensor_place_awaiting_cell)
                    else -> stringResource(R.string.sensor_place_notice)
                },
                modifier = Modifier.testTag("place_notice")
            )
            if (ui.placementError != null) {
                Spacer(Modifier.height(Spacing.md))
                WarningPanel(
                    title = stringResource(R.string.sensor_place_error_title),
                    body = ui.placementError,
                    error = true,
                    modifier = Modifier.testTag("place_error")
                )
            }
            Spacer(Modifier.height(Spacing.section))
            InfoCard(modifier = Modifier.testTag("place_grid_card")) {
                Text(
                    text = stringResource(R.string.sensor_place_grid_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() }
                )
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    text = stringResource(
                        R.string.sensor_place_grid_size,
                        ui.greenhouse.widthCells,
                        ui.greenhouse.heightCells
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (awaitingPlacement && ui.pendingSensorType != null) {
                    Spacer(Modifier.height(Spacing.sm))
                    StatusChip(
                        text = stringResource(
                            R.string.sensor_place_ready_chip,
                            sensorTypeLabel(ui.pendingSensorType)
                        ),
                        modifier = Modifier.testTag("place_ready_type_chip")
                    )
                }
                Spacer(Modifier.height(Spacing.md))
                GreenhouseCoverageMap(
                    greenhouse = ui.greenhouse,
                    coverage = mapCoverage,
                    sensors = mapSensors,
                    ringSensors = ringSensors,
                    selectedSensorId = ui.selectedSensorId,
                    radiusEmphasis = 1f,
                    markerTestTagPrefix = "place_marker",
                    onTapCell = { column, row ->
                        val result = PlacementInteraction.onCellTapped(
                            column = column,
                            row = row,
                            greenhouse = ui.greenhouse,
                            sensors = ui.sensors,
                            selectedSensorId = ui.selectedSensorId,
                            forceAdd = awaitingPlacement
                        )
                        when (result) {
                            is GridTapResult.Add -> onAddSensor(result.x, result.y)
                            is GridTapResult.Select -> onSelectSensor(result.id)
                            is GridTapResult.Move -> onMoveSensor(result.id, result.x, result.y)
                            GridTapResult.Ignore -> Unit
                        }
                    }
                )
                Spacer(Modifier.height(Spacing.md))
                CoverageMapTypeFilter(
                    selectedType = mapTypeFilter,
                    onSelect = { mapTypeFilter = it },
                    testTagPrefix = "place_filter"
                )
                Spacer(Modifier.height(Spacing.md))
                CoverageStateLegend(testTagPrefix = "place_legend")
                Spacer(Modifier.height(Spacing.md))
                SensorTypeLegend(testTagPrefix = "place_type_legend")
            }
            Spacer(Modifier.height(Spacing.section))
            InfoCard(modifier = Modifier.testTag("place_coverage_by_type")) {
                CoverageByTypeSection(
                    coverageByType = ui.coverageByType,
                    testTagPrefix = "place_by_type"
                )
            }
            Spacer(Modifier.height(Spacing.section))
            InfoCard(modifier = Modifier.testTag("place_summary")) {
                Text(
                    text = stringResource(R.string.sensor_place_summary_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    text = stringResource(R.string.sensor_place_total_sensors, ui.sensorCount),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("place_summary_total")
                )
                Spacer(Modifier.height(Spacing.sm))
                SensorType.entries.forEach { type ->
                    val count = ui.sensors.count { it.type == type }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("place_summary_type_${type.name}"),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = sensorTypeShortName(type),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = count.toString(),
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    text = stringResource(R.string.sensor_place_default_type),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(
                        R.string.sensor_place_default_radius,
                        String.format(java.util.Locale.US, "%.1f", DEFAULT_COVERAGE_RADIUS_CELLS)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    text = stringResource(
                        R.string.sensor_place_live_coverage,
                        String.format(java.util.Locale.US, "%.1f", ui.coverage.overallCoveragePercent),
                        ui.coverage.blindSpotCells
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = stringResource(
                        R.string.sensor_place_overlap_summary
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(Spacing.related))
            if (selected != null) {
                SelectedSensorCard(
                    sensor = selected,
                    onDelete = { onDeleteSensor(selected.id) },
                    onToggleActive = { onSetSensorActive(selected.id, selected.status != SensorStatus.ACTIVE) },
                    onCancelMove = onDeselectSensor
                )
                Spacer(Modifier.height(Spacing.related))
            }
            if (!ui.showSensorTypePicker) {
                PrimaryActionButton(
                    text = stringResource(R.string.sensor_place_add),
                    onClick = {
                        onDeselectSensor()
                        onOpenSensorTypePicker()
                    },
                    modifier = Modifier.testTag("place_add")
                )
                Spacer(Modifier.height(Spacing.related))
            }
            SecondaryActionButton(
                text = stringResource(R.string.sensor_place_reset),
                onClick = onResetSensors,
                modifier = Modifier.testTag("place_reset")
            )
            Spacer(Modifier.height(Spacing.section))
            PrimaryActionButton(
                text = stringResource(R.string.sensor_place_continue),
                onClick = onContinueToCoverage,
                modifier = Modifier.testTag("place_continue")
            )
            Spacer(Modifier.height(Spacing.section))
        }
    }
}

@Composable
private fun SensorTypePickerCard(
    selectedType: SensorType,
    onSelectType: (SensorType) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    InfoCard(modifier = Modifier.testTag("sensor_type_picker")) {
        Text(
            text = stringResource(R.string.sensor_type_picker_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = stringResource(R.string.sensor_type_picker_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Spacing.md))
        SensorType.entries.forEach { type ->
            SensorTypeOptionRow(
                type = type,
                selected = type == selectedType,
                onClick = { onSelectType(type) }
            )
            Spacer(Modifier.height(Spacing.sm))
        }
        Spacer(Modifier.height(Spacing.sm))
        PrimaryActionButton(
            text = stringResource(R.string.sensor_type_place_action),
            onClick = onConfirm,
            modifier = Modifier.testTag("sensor_type_place")
        )
        Spacer(Modifier.height(Spacing.related))
        SecondaryActionButton(
            text = stringResource(R.string.sensor_type_cancel),
            onClick = onCancel,
            modifier = Modifier.testTag("sensor_type_cancel")
        )
    }
}

@Composable
private fun SensorTypeOptionRow(
    type: SensorType,
    selected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (selected) ForestEmerald else MaterialTheme.colorScheme.outline
    val container = if (selected) {
        ForestEmerald.copy(alpha = 0.16f)
    } else {
        NightElevated
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(container, Radii.md)
            .border(BorderStroke(GhStroke.hairline, borderColor), Radii.md)
            .clickable(onClick = onClick)
            .padding(Spacing.md)
            .testTag("sensor_type_option_${type.name.lowercase()}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Icon(
            imageVector = sensorTypeIcon(type),
            contentDescription = null,
            tint = if (selected) ForestEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(28.dp)
                .background(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                    shape = CircleShape
                )
                .padding(4.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = sensorTypeLabel(type),
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = sensorTypeDescription(type),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SelectedSensorCard(
    sensor: Sensor,
    onDelete: () -> Unit,
    onToggleActive: () -> Unit,
    onCancelMove: () -> Unit
) {
    InfoCard(modifier = Modifier.testTag("place_selected_card")) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.sensor_place_selected_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() }
            )
            StatusChip(
                text = if (sensor.status == SensorStatus.ACTIVE) {
                    stringResource(R.string.sensor_status_active)
                } else {
                    stringResource(R.string.sensor_status_inactive)
                },
                warning = sensor.status == SensorStatus.INACTIVE
            )
        }
        Spacer(Modifier.height(Spacing.sm))
        Text(stringResource(R.string.sensor_place_id, sensor.id), style = MaterialTheme.typography.bodyMedium)
        Text(
            text = stringResource(R.string.sensor_place_type, sensorTypeLabel(sensor.type)),
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = stringResource(
                R.string.sensor_place_position,
                formatCoord(sensor.x),
                formatCoord(sensor.y)
            ),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.testTag("place_selected_position")
        )
        Text(
            text = stringResource(
                R.string.sensor_place_radius,
                String.format(java.util.Locale.US, "%.1f", sensor.coverageRadius)
            ),
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(Spacing.sm))
        DemoNotice(stringResource(R.string.sensor_place_move_hint))
        Spacer(Modifier.height(Spacing.md))
        SecondaryActionButton(
            text = if (sensor.status == SensorStatus.ACTIVE) {
                stringResource(R.string.sensor_place_deactivate)
            } else {
                stringResource(R.string.sensor_place_activate)
            },
            onClick = onToggleActive,
            modifier = Modifier.testTag("place_toggle_active")
        )
        Spacer(Modifier.height(Spacing.related))
        SecondaryActionButton(
            text = stringResource(R.string.sensor_place_delete),
            onClick = onDelete,
            modifier = Modifier.testTag("place_delete")
        )
        Spacer(Modifier.height(Spacing.related))
        SecondaryActionButton(
            text = stringResource(R.string.sensor_place_deselect),
            onClick = onCancelMove,
            modifier = Modifier.testTag("place_deselect")
        )
    }
}

@Composable
fun sensorTypeLabel(type: SensorType): String = when (type) {
    SensorType.TEMPERATURE -> stringResource(R.string.sensor_type_temperature)
    SensorType.HUMIDITY -> stringResource(R.string.sensor_type_humidity)
    SensorType.SOIL_MOISTURE -> stringResource(R.string.sensor_type_soil_moisture)
    SensorType.LIGHT_INTENSITY -> stringResource(R.string.sensor_type_light_intensity)
}

@Composable
private fun sensorTypeDescription(type: SensorType): String = when (type) {
    SensorType.TEMPERATURE -> stringResource(R.string.sensor_type_temperature_body)
    SensorType.HUMIDITY -> stringResource(R.string.sensor_type_humidity_body)
    SensorType.SOIL_MOISTURE -> stringResource(R.string.sensor_type_soil_moisture_body)
    SensorType.LIGHT_INTENSITY -> stringResource(R.string.sensor_type_light_intensity_body)
}

private fun sensorTypeIcon(type: SensorType): ImageVector = when (type) {
    SensorType.TEMPERATURE -> Icons.Outlined.Thermostat
    SensorType.HUMIDITY -> Icons.Outlined.WaterDrop
    SensorType.SOIL_MOISTURE -> Icons.Outlined.Grass
    SensorType.LIGHT_INTENSITY -> Icons.Outlined.WbSunny
}

private fun formatCoord(value: Double): String {
    val asInt = value.toInt()
    return if (value == asInt.toDouble()) asInt.toString() else String.format(java.util.Locale.US, "%.1f", value)
}
