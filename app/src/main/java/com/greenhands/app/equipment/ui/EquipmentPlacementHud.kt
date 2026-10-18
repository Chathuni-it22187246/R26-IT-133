package com.greenhands.app.equipment.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.greenhands.app.R
import com.greenhands.app.ar.domain.ArEquipmentInstanceStatus
import com.greenhands.app.ar.domain.ArHudContent
import com.greenhands.app.ar.domain.ArRuntimeTone
import com.greenhands.app.ui.theme.ForestEmerald
import com.greenhands.app.equipment.domain.AxisSliderLimit
import com.greenhands.app.equipment.domain.PlacementAxis
import com.greenhands.app.equipment.future.EnvironmentDataSourceKind
import com.greenhands.app.equipment.model.EquipmentLabels
import com.greenhands.app.equipment.model.EquipmentRecommendation
import com.greenhands.app.equipment.model.EquipmentType
import com.greenhands.app.equipment.model.PreviewInteractionMode
import com.greenhands.app.equipment.model.SelectedEquipmentTool
import com.greenhands.app.equipment.model.formatMetres2
import com.greenhands.app.equipment.model.isZAxis
import com.greenhands.app.heat.ui.CropGlyph
import com.greenhands.app.heat.ui.FanGlyph
import com.greenhands.app.heat.ui.FoggerGlyph
import com.greenhands.app.ui.components.PrimaryActionButton
import com.greenhands.app.ui.components.SecondaryActionButton
import com.greenhands.app.ui.theme.Radii
import com.greenhands.app.ui.theme.Spacing
import com.greenhands.app.heat.model.formatOneDecimal

@Composable
fun CompactCropEnvironmentRow(
    climate: OverlayClimate,
    kind: EnvironmentDataSourceKind,
    modifier: Modifier = Modifier,
    liveStatusLabel: String? = null,
    liveReadingLabel: String? = null,
    outsideReadingLabel: String? = null,
    outsideStatusLabel: String? = null,
    outsideAttributionLabel: String? = null
) {
    val source = when (kind) {
        EnvironmentDataSourceKind.PREVIEW -> stringResource(R.string.placement_source_preview)
        EnvironmentDataSourceKind.LIVE -> stringResource(R.string.placement_source_live)
        EnvironmentDataSourceKind.OFFLINE_DELAYED -> stringResource(R.string.placement_source_offline)
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("placement_crop_card"),
        shape = Radii.md,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            CropGlyph(climate.crop ?: com.greenhands.app.heat.model.Crop.TOMATO, size = 24.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    climate.crop?.displayName ?: stringResource(R.string.placement_crop_unknown),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    climate.stage?.shortLabel ?: stringResource(R.string.placement_stage_unknown),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    climate.compactOptimalLabel(),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("placement_crop_optimal")
                )
                if (!liveStatusLabel.isNullOrBlank()) {
                    Text(
                        text = listOfNotNull(liveReadingLabel, liveStatusLabel)
                            .filter { it.isNotBlank() }
                            .joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("placement_live_sensor")
                    )
                }
                if (!outsideReadingLabel.isNullOrBlank() || !outsideStatusLabel.isNullOrBlank()) {
                    Text(
                        text = listOfNotNull(outsideReadingLabel, outsideStatusLabel)
                            .filter { it.isNotBlank() }
                            .joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("placement_outside_weather")
                    )
                    if (!outsideAttributionLabel.isNullOrBlank()) {
                        Text(
                            text = outsideAttributionLabel,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("placement_outside_source")
                        )
                    }
                }
            }
            Surface(
                shape = Radii.pill,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.testTag("placement_env_card")
            ) {
                Text(
                    source,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .padding(horizontal = Spacing.sm, vertical = Spacing.xxs)
                        .testTag("placement_env_source"),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun CropStatusCard(
    climate: OverlayClimate,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("placement_crop_card"),
        shape = Radii.md,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            CropGlyph(climate.crop ?: com.greenhands.app.heat.model.Crop.TOMATO, size = 24.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    climate.crop?.displayName ?: stringResource(R.string.placement_crop_unknown),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    climate.stage?.shortLabel ?: stringResource(R.string.placement_stage_unknown),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    climate.compactOptimalLabel(),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("placement_crop_optimal")
                )
            }
        }
    }
}

@Composable
fun EnvironmentStatusCard(
    kind: EnvironmentDataSourceKind,
    modifier: Modifier = Modifier
) {
    val label = when (kind) {
        EnvironmentDataSourceKind.PREVIEW -> stringResource(R.string.placement_source_preview)
        EnvironmentDataSourceKind.LIVE -> stringResource(R.string.placement_source_live)
        EnvironmentDataSourceKind.OFFLINE_DELAYED -> stringResource(R.string.placement_source_offline)
    }
    Surface(
        modifier = modifier.testTag("placement_env_card"),
        shape = Radii.pill,
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                .testTag("placement_env_source"),
            maxLines = 1
        )
    }
}

@Composable
@Suppress("UNUSED_PARAMETER")
fun EquipmentThresholdCard(
    climate: OverlayClimate,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    instanceNames: List<String> = emptyList(),
    onHelp: () -> Unit = {}
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("placement_equipment_card"),
        shape = Radii.md,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
    ) {
        Column(Modifier.padding(Spacing.sm)) {
            Text(
                stringResource(R.string.placement_equipment_status),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .clickable(onClick = onToggle)
                    .testTag("placement_equipment_toggle")
            )
            if (expanded) {
                Text(
                    stringResource(R.string.placement_not_running),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .semantics { contentDescription = "Equipment state: not running" }
                        .testTag("placement_runtime_state")
                )
                Text(
                    stringResource(R.string.placement_env_no_live),
                    style = MaterialTheme.typography.bodySmall
                )
                val c = climate.circulation
                val e = climate.exhaust
                val f = climate.fogger
                if (c != null) {
                    Text(
                        stringResource(R.string.equipment_controls_circulation),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.testTag("ar_controls_circulation")
                    )
                    Text("${stringResource(R.string.placement_csp_label)}: ${formatOneDecimal(c.csp)}°C", style = MaterialTheme.typography.bodySmall)
                    Text("${stringResource(R.string.placement_cdp_label)}: ${formatOneDecimal(c.cdp)}°C", style = MaterialTheme.typography.bodySmall)
                    Text("${stringResource(R.string.placement_con_label)}: ${formatOneDecimal(c.con)}°C", style = MaterialTheme.typography.bodySmall)
                }
                if (e != null) {
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        stringResource(R.string.equipment_controls_exhaust),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.testTag("ar_controls_exhaust")
                    )
                    Text("${stringResource(R.string.placement_esp_label)}: ${formatOneDecimal(e.esp)}°C", style = MaterialTheme.typography.bodySmall)
                    Text("${stringResource(R.string.placement_edp_label)}: ${formatOneDecimal(e.edp)}°C", style = MaterialTheme.typography.bodySmall)
                    Text("${stringResource(R.string.placement_eon_label)}: ${formatOneDecimal(e.eon)}°C", style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.placement_edp_equals_con), style = MaterialTheme.typography.labelSmall)
                }
                if (f != null) {
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        stringResource(R.string.equipment_controls_fogger),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.testTag("ar_controls_fogger")
                    )
                    Text("${stringResource(R.string.placement_fsp_label)}: ${formatOneDecimal(f.fsp)}%", style = MaterialTheme.typography.bodySmall)
                    Text("${stringResource(R.string.placement_fon_label)}: ${formatOneDecimal(f.fon)}%", style = MaterialTheme.typography.bodySmall)
                    Text("${stringResource(R.string.placement_fdp_label)}: ${formatOneDecimal(f.fdp)}%", style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = onHelp, modifier = Modifier.testTag("placement_threshold_help")) {
                    Text(stringResource(R.string.placement_threshold_help))
                }
            }
        }
    }
}

@Composable
fun EquipmentPalette(
    onTap: (EquipmentType) -> Unit,
    onDragStart: (EquipmentType) -> Unit = {},
    onDragEnd: (EquipmentType) -> Unit = {},
    modifier: Modifier = Modifier,
    onDragCancel: (EquipmentType) -> Unit = onDragEnd,
    onDragWindow: (EquipmentType, Offset) -> Unit = { _, _ -> },
    onSlotPlaced: (EquipmentType, androidx.compose.ui.geometry.Rect) -> Unit = { _, _ -> }
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp, max = 108.dp)
            .testTag("equipment_palette"),
        shape = Radii.md,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
    ) {
        Row(
            modifier = Modifier.padding(Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            PaletteItem(
                type = EquipmentType.CIRCULATION_FAN,
                tag = "palette_circulation",
                modifier = Modifier.weight(1f),
                onTap = onTap,
                onSlotPlaced = onSlotPlaced
            ) {
                FanGlyph(size = 28.dp)
            }
            PaletteItem(
                type = EquipmentType.EXHAUST_FAN,
                tag = "palette_exhaust",
                modifier = Modifier.weight(1f),
                onTap = onTap,
                onSlotPlaced = onSlotPlaced
            ) {
                FanGlyph(size = 28.dp)
            }
            PaletteItem(
                type = EquipmentType.FOGGER,
                tag = "palette_fogger",
                modifier = Modifier.weight(1f),
                onTap = onTap,
                onSlotPlaced = onSlotPlaced
            ) {
                FoggerGlyph(size = 28.dp)
            }
        }
    }
}

@Composable
private fun PaletteItem(
    type: EquipmentType,
    tag: String,
    modifier: Modifier,
    onTap: (EquipmentType) -> Unit,
    onSlotPlaced: (EquipmentType, androidx.compose.ui.geometry.Rect) -> Unit,
    icon: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .testTag(tag)
            .reportWindowRect { onSlotPlaced(type, it) }
            .clickable { onTap(type) }
            .padding(Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        icon()
        Text(
            EquipmentLabels.typeTitle(type),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun CoordinateEditor(
    state: PlacementUiState,
    onApply: (String, String, String, String) -> Unit,
    onRotate: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onConfirmGhost: () -> Unit,
    onCancelGhost: () -> Unit,
    modifier: Modifier = Modifier,
    elevationMinM: Double? = null,
    elevationMaxM: Double? = null,
    onElevation: (Double) -> Unit = {}
) {
    val selected = state.selected ?: return
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("coordinate_editor"),
        shape = Radii.md,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
    ) {
        Column(Modifier.padding(Spacing.sm)) {
            Text(
                "${selected.displayLabel()} · ${if (selected.validity.isValid) stringResource(R.string.placement_valid) else stringResource(R.string.placement_invalid)}",
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("coord_instance_name")
            )
            Text(
                stringResource(R.string.placement_position_heading),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.testTag("coord_position_section")
            )
            Text(
                "${stringResource(R.string.placement_axis_x)} ${formatMetres2(selected.positionM.xM)} m · ${stringResource(R.string.placement_axis_y)} ${formatMetres2(selected.positionM.yM)} m · ${stringResource(R.string.placement_axis_z)} ${formatMetres2(selected.positionM.zM)} m",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("coordinate_live")
            )
            Text(
                stringResource(R.string.placement_rotation_only, formatMetres2(selected.yawDeg)),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                stringResource(R.string.placement_size_heading),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.testTag("coord_size_section")
            )
            Text(
                stringResource(
                    R.string.placement_model_size,
                    formatMetres2(selected.dimensions.widthM),
                    formatMetres2(selected.dimensions.depthM),
                    formatMetres2(selected.dimensions.heightM)
                ),
                style = MaterialTheme.typography.bodySmall
            )
            if (!selected.validity.isValid) {
                Text(
                    selected.validity.reasons.joinToString(" "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("placement_invalid_warning")
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                CoordField(state.xInput, { onApply(it, state.yInput, state.zInput, state.yawInput) }, stringResource(R.string.placement_axis_x), "coord_x")
                CoordField(state.yInput, { onApply(state.xInput, it, state.zInput, state.yawInput) }, stringResource(R.string.placement_axis_y), "coord_y")
                CoordField(state.zInput, { onApply(state.xInput, state.yInput, it, state.yawInput) }, stringResource(R.string.placement_axis_z), "coord_z")
                CoordField(
                    state.yawInput,
                    { onApply(state.xInput, state.yInput, state.zInput, it) },
                    "Yaw",
                    "coord_yaw",
                    unit = "°"
                )
            }
            if (elevationMinM != null && elevationMaxM != null && elevationMaxM > elevationMinM) {
                Text(stringResource(R.string.placement_elevation), style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = selected.positionM.zM.toFloat().coerceIn(elevationMinM.toFloat(), elevationMaxM.toFloat()),
                    onValueChange = { onElevation(it.toDouble()) },
                    valueRange = elevationMinM.toFloat()..elevationMaxM.toFloat(),
                    modifier = Modifier.testTag("elevation_slider")
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                TextButton(onClick = onRotate, modifier = Modifier.testTag("placement_rotate")) {
                    Text(stringResource(R.string.placement_rotate))
                }
                TextButton(onClick = onDuplicate, modifier = Modifier.testTag("placement_duplicate")) {
                    Text(stringResource(R.string.placement_duplicate))
                }
                TextButton(onClick = onDelete, modifier = Modifier.testTag("placement_delete")) {
                    Text(stringResource(R.string.placement_delete))
                }
            }
            if (selected.isGhost) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    PrimaryActionButton(
                        text = stringResource(R.string.placement_place),
                        onClick = onConfirmGhost,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("placement_confirm_ghost")
                    )
                    SecondaryActionButton(
                        text = stringResource(R.string.placement_cancel_ghost),
                        onClick = onCancelGhost,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("placement_cancel_ghost")
                    )
                }
            }
            state.editError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun CoordField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    tag: String,
    unit: String = "m"
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier
            .width(88.dp)
            .testTag(tag),
        label = { Text("$label $unit") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    )
}

@Composable
fun PlacementActions(
    mode: PreviewInteractionMode,
    onEnterPlacement: () -> Unit,
    onEnterView: () -> Unit,
    onSuggest: () -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    showSave: Boolean = true
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        if (mode == PreviewInteractionMode.VIEW) {
            SecondaryActionButton(
                text = stringResource(R.string.placement_enter),
                onClick = onEnterPlacement,
                modifier = Modifier
                    .weight(1f)
                    .testTag("placement_enter")
            )
        } else {
            SecondaryActionButton(
                text = stringResource(R.string.placement_view_mode),
                onClick = onEnterView,
                modifier = Modifier.testTag("placement_view_mode")
            )
            if (showSave) {
                TextButton(onClick = onSave, modifier = Modifier.testTag("placement_save")) {
                    Text(stringResource(R.string.placement_save))
                }
            }
            TextButton(onClick = onClear, modifier = Modifier.testTag("placement_clear")) {
                Text(stringResource(R.string.placement_clear_equipment))
            }
        }
    }
}

@Composable
fun PlacementControlStrip(
    mode: PreviewInteractionMode,
    orbitAllowed: Boolean,
    labelsVisible: Boolean,
    moveSelected: Boolean,
    onEnterPlacement: () -> Unit,
    onExitPlacement: () -> Unit,
    onMove: () -> Unit,
    onOrbit: () -> Unit,
    onLabels: () -> Unit,
    onEquipmentStatus: () -> Unit,
    onReset: () -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit,
    statusMessage: String? = null,
    statusOpen: Boolean = false,
    showSave: Boolean = true,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 168.dp)
            .testTag("placement_toolbar"),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
    ) {
        Column(Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xxs)) {
            if (mode == PreviewInteractionMode.VIEW) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    FilledTonalButton(
                        onClick = onEnterPlacement,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("placement_enter")
                    ) {
                        Text(stringResource(R.string.placement_enter), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    FilterChip(
                        selected = labelsVisible,
                        onClick = onLabels,
                        label = { Text(stringResource(R.string.placement_labels), maxLines = 1) },
                        modifier = Modifier.testTag("preview_labels_toggle")
                    )
                    FilterChip(
                        selected = statusOpen,
                        onClick = onEquipmentStatus,
                        label = { Text(stringResource(R.string.placement_equipment_status), maxLines = 1) },
                        modifier = Modifier.testTag("placement_status_open")
                    )
                    FilledTonalButton(
                        onClick = onReset,
                        modifier = Modifier.testTag("preview_reset")
                    ) {
                        Text(
                            stringResource(R.string.preview3d_reset),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("placement_mode_row_1"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    FilterChip(
                        selected = moveSelected && !orbitAllowed,
                        onClick = onMove,
                        label = {
                            Text(
                                stringResource(R.string.placement_move),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Clip
                            )
                        },
                        modifier = Modifier.testTag("placement_move_mode")
                    )
                    FilterChip(
                        selected = orbitAllowed,
                        onClick = onOrbit,
                        label = {
                            Text(
                                stringResource(R.string.placement_orbit),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Clip
                            )
                        },
                        modifier = Modifier.testTag("preview_orbit_toggle")
                    )
                    FilterChip(
                        selected = labelsVisible,
                        onClick = onLabels,
                        label = {
                            Text(
                                stringResource(R.string.placement_labels),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Clip
                            )
                        },
                        modifier = Modifier.testTag("preview_labels_toggle")
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("placement_mode_row_2"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    FilledTonalButton(
                        onClick = onReset,
                        contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = Spacing.xxs),
                        modifier = Modifier
                            .widthIn(min = 96.dp)
                            .testTag("preview_reset")
                    ) {
                        Text(
                            stringResource(R.string.preview3d_reset),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip
                        )
                    }
                    FilledTonalButton(
                        onClick = onExitPlacement,
                        contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = Spacing.xxs),
                        modifier = Modifier
                            .widthIn(min = 64.dp)
                            .testTag("placement_view_mode")
                    ) {
                        Text(
                            stringResource(R.string.placement_done),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    FilterChip(
                        selected = statusOpen,
                        onClick = onEquipmentStatus,
                        label = {
                            Text(
                                stringResource(R.string.placement_equipment_status),
                                maxLines = 1,
                                softWrap = false
                            )
                        },
                        modifier = Modifier.testTag("placement_status_open")
                    )
                    if (showSave) {
                        TextButton(onClick = onSave, modifier = Modifier.testTag("placement_save")) {
                            Text(stringResource(R.string.placement_save), maxLines = 1, softWrap = false)
                        }
                    }
                    TextButton(onClick = onClear, modifier = Modifier.testTag("placement_clear")) {
                        Text(stringResource(R.string.placement_clear_equipment), maxLines = 1, softWrap = false)
                    }
                }
            }
            if (!statusMessage.isNullOrBlank()) {
                Text(
                    statusMessage,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("placement_status")
                )
            }
        }
    }
}

@Composable
fun EquipmentStatusOverlay(
    climate: OverlayClimate,
    instanceNames: List<String>,
    onHelp: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    layoutSaved: Boolean? = null,
    typeCounts: Map<EquipmentType, Int> = emptyMap(),
    instanceStatuses: List<ArEquipmentInstanceStatus> = emptyList(),
    equipmentStateExplanation: String? = null,
    visualizationDetails: String? = null
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 320.dp)
            .testTag("equipment_status_sheet"),
        shape = Radii.md,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(Spacing.sm)
        ) {
            if (typeCounts.isNotEmpty() || layoutSaved != null) {
                Text(
                    stringResource(R.string.placement_equipment_status),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.testTag("ar_status_title")
                )
                EquipmentType.entries.forEach { type ->
                    val count = typeCounts[type] ?: 0
                    Text(
                        "${EquipmentLabels.typeTitle(type)}: $count",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag("ar_status_count_${type.name.lowercase()}")
                    )
                }
                layoutSaved?.let { saved ->
                    Text(
                        if (saved) {
                            stringResource(R.string.ar_status_saved)
                        } else {
                            stringResource(R.string.ar_status_unsaved)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag("ar_status_saved")
                    )
                }
                Text(
                    climate.compactOptimalLabel(),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("ar_status_optimal")
                )
                Spacer(Modifier.height(Spacing.xs))
                ThresholdCodeGlossary()
                Spacer(Modifier.height(Spacing.xs))
            }
            EquipmentInstanceStatusList(
                statuses = instanceStatuses,
                fallbackNames = instanceNames
            )
            equipmentStateExplanation?.let { explanation ->
                Text(
                    explanation,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("ar_status_not_evaluated_reason")
                )
            }
            visualizationDetails?.let { details ->
                Text(
                    details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("ar_status_visualization_details")
                )
            }
            EquipmentThresholdCard(
                climate = climate,
                expanded = true,
                onToggle = onDismiss,
                instanceNames = emptyList(),
                onHelp = onHelp
            )
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("equipment_status_close")) {
                Text(stringResource(R.string.placement_close))
            }
        }
    }
}

@Composable
fun EquipmentInstanceStatusList(
    statuses: List<ArEquipmentInstanceStatus>,
    fallbackNames: List<String> = emptyList(),
    modifier: Modifier = Modifier
) {
    val rows = if (statuses.isNotEmpty()) {
        statuses
    } else {
        fallbackNames.map { name ->
            ArEquipmentInstanceStatus(
                instanceId = name,
                type = ArHudContent.typeFromDisplayName(name),
                name = name,
                displayNumber = 0,
                state = ArHudContent.NOT_RUNNING
            )
        }
    }
    Column(modifier.testTag("ar_status_instances")) {
        Text(
            stringResource(R.string.placement_equipment_instances),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.testTag("ar_status_instances_title")
        )
        if (rows.isEmpty()) {
            Text(
                stringResource(R.string.ar_equipment_empty),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("ar_status_instances_empty")
            )
        } else {
            rows.forEach { row ->
                Text(
                    row.displayLine(),
                    style = MaterialTheme.typography.bodySmall,
                    color = when (row.tone) {
                        ArRuntimeTone.ON -> ForestEmerald
                        ArRuntimeTone.OFF, ArRuntimeTone.MIXED -> MaterialTheme.colorScheme.onSurface
                        ArRuntimeTone.NOT_RUNNING, ArRuntimeTone.EMPTY -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag(
                        "ar_status_instance_${row.name.replace(' ', '_').lowercase()}"
                    )
                )
            }
        }
    }
}

@Composable
fun SuggestionPanel(
    state: PlacementUiState,
    onInput: (
        circ: String?,
        exh: String?,
        fog: String?,
        eta: String?,
        load: String?,
        tOut: String?,
        rhOut: String?,
        ovC: String?,
        ovE: String?,
        ovF: String?
    ) -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!state.showSuggestionPanel) return
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onApply, modifier = Modifier.testTag("placement_apply_suggestions")) {
                Text(stringResource(R.string.placement_apply_suggestions))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_back)) }
        },
        title = { Text(stringResource(R.string.placement_suggest_title)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(EquipmentRecommendation.DISCLAIMER, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(Spacing.sm))
                RecField(state.circulationRatedCfmInput, { onInput(it, null, null, null, null, null, null, null, null, null) }, stringResource(R.string.placement_circ_cfm), "rec_circ_cfm")
                RecField(state.exhaustRatedM3sInput, { onInput(null, it, null, null, null, null, null, null, null, null) }, stringResource(R.string.placement_exh_m3s), "rec_exh_m3s")
                RecField(state.foggerFlowKgSInput, { onInput(null, null, it, null, null, null, null, null, null, null) }, stringResource(R.string.placement_fog_flow), "rec_fog_flow")
                RecField(state.foggerEtaInput, { onInput(null, null, null, it, null, null, null, null, null, null) }, stringResource(R.string.placement_fog_eta), "rec_fog_eta")
                RecField(state.heatLoadWInput, { onInput(null, null, null, null, it, null, null, null, null, null) }, stringResource(R.string.placement_heat_load), "rec_heat_load")
                RecField(state.outdoorTempInput, { onInput(null, null, null, null, null, it, null, null, null, null) }, stringResource(R.string.placement_outdoor_t), "rec_outdoor_t")
                RecField(state.outdoorRhInput, { onInput(null, null, null, null, null, null, it, null, null, null) }, stringResource(R.string.placement_outdoor_rh), "rec_outdoor_rh")
                RecField(state.overrideCircInput, { onInput(null, null, null, null, null, null, null, it, null, null) }, stringResource(R.string.placement_ov_circ), "rec_ov_c")
                RecField(state.overrideExhInput, { onInput(null, null, null, null, null, null, null, null, it, null) }, stringResource(R.string.placement_ov_exh), "rec_ov_e")
                RecField(state.overrideFogInput, { onInput(null, null, null, null, null, null, null, null, null, it) }, stringResource(R.string.placement_ov_fog), "rec_ov_f")
                state.recommendation?.let { rec ->
                    Text(rec.circulation.equationNote, style = MaterialTheme.typography.bodySmall)
                    Text(rec.exhaust.equationNote, style = MaterialTheme.typography.bodySmall)
                    Text(rec.fogger.equationNote, style = MaterialTheme.typography.bodySmall)
                    rec.circulation.warnings.plus(rec.exhaust.warnings).plus(rec.fogger.warnings).forEach {
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    )
}

@Composable
private fun RecField(value: String, onChange: (String) -> Unit, label: String, tag: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag),
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    )
}

@Composable
fun ClearLayoutDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("placement_clear_confirm")) {
                Text(stringResource(R.string.placement_clear))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_back)) }
        },
        title = { Text(stringResource(R.string.placement_clear_title)) },
        text = { Text(stringResource(R.string.placement_clear_body)) }
    )
}

@Composable
fun ClearEquipmentDialog(
    itemCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("placement_clear_confirm")) {
                Text(stringResource(R.string.ar_clear_equipment))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("ar_clear_cancel")) {
                Text(stringResource(R.string.action_cancel))
            }
        },
        title = {
            Text(
                stringResource(R.string.ar_clear_all_title),
                modifier = Modifier.testTag("ar_clear_title")
            )
        },
        text = {
            Text(
                stringResource(R.string.ar_clear_all_body, itemCount),
                modifier = Modifier.testTag("ar_clear_body")
            )
        }
    )
}

@Composable
fun ReplaceLayoutDialog(
    typeTitle: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("placement_replace_confirm")) {
                Text(stringResource(R.string.placement_replace_layout_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("placement_replace_cancel")) {
                Text(stringResource(R.string.action_cancel))
            }
        },
        title = {
            Text(
                stringResource(R.string.placement_replace_layout_title, typeTitle),
                modifier = Modifier.testTag("placement_replace_title")
            )
        },
        text = {
            Text(
                stringResource(R.string.placement_replace_layout_body),
                modifier = Modifier.testTag("placement_replace_body")
            )
        }
    )
}

@Composable
fun DeleteEquipmentDialog(
    name: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("placement_delete_confirm")) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("placement_delete_cancel")) {
                Text(stringResource(R.string.action_cancel))
            }
        },
        title = {
            Text(
                stringResource(R.string.placement_delete_title_named, name),
                modifier = Modifier.testTag("placement_delete_title")
            )
        },
        text = {
            Text(
                stringResource(R.string.placement_delete_body_named, name),
                modifier = Modifier.testTag("placement_delete_body")
            )
        }
    )
}

@Composable
fun ViewportCoordinateLabel(
    name: String,
    xM: Double,
    yM: Double,
    zM: Double,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    selected: Boolean = !compact
) {
    val tag = if (selected) "viewport_coord_label" else "viewport_equip_label"
    Surface(
        modifier = modifier.testTag(tag),
        shape = Radii.md,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.94f)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
        },
        tonalElevation = if (selected) 3.dp else 0.dp
    ) {
        Column(Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs)) {
            Text(
                name,
                style = if (selected) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (selected && !compact) {
                Text(
                    stringResource(
                        R.string.placement_viewport_xyz,
                        formatMetres2(xM),
                        formatMetres2(yM),
                        formatMetres2(zM)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun SelectedEquipmentBar(
    state: PlacementUiState,
    onMove: () -> Unit,
    onRotate90: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onAxisX: () -> Unit,
    onAxisY: () -> Unit,
    onAxisZ: () -> Unit,
    onExact: () -> Unit,
    onCoordinate: (PlacementAxis, Double) -> Unit,
    xLimit: AxisSliderLimit?,
    yLimit: AxisSliderLimit?,
    zLimit: AxisSliderLimit?,
    modifier: Modifier = Modifier
) {
    val selected = state.selected ?: return
    if (selected.isGhost && state.paletteDraggingType != null) return
    if (state.draggingId != null) return
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 260.dp)
            .testTag("placement_action_bar"),
        shape = Radii.md,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
    ) {
        Column(Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xxs)) {
            Text(selected.displayLabel(), style = MaterialTheme.typography.labelLarge, maxLines = 1)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("placement_action_row_1"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)
            ) {
                CompactTextAction(
                    text = stringResource(R.string.placement_move),
                    tag = "placement_move",
                    selected = state.selectedTool == SelectedEquipmentTool.MOVE,
                    onClick = onMove
                )
                CompactTextAction(
                    text = stringResource(R.string.placement_rotate),
                    tag = "placement_rotate",
                    onClick = onRotate90
                )
                CompactTextAction(
                    text = stringResource(R.string.placement_duplicate),
                    tag = "placement_duplicate",
                    onClick = onDuplicate
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("placement_action_row_2"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)
            ) {
                CompactTextAction(
                    text = stringResource(R.string.placement_delete),
                    tag = "placement_delete",
                    onClick = onDelete
                )
                CompactTextAction(
                    text = stringResource(R.string.placement_exact),
                    tag = "placement_exact",
                    selected = state.selectedTool == SelectedEquipmentTool.EXACT || state.showExactEditor,
                    onClick = onExact
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("placement_action_row_3"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)
            ) {
                CompactTextAction(
                    text = stringResource(R.string.placement_axis_short_x),
                    tag = "placement_axis_x_tool",
                    selected = state.selectedTool == SelectedEquipmentTool.X,
                    enabled = xLimit?.enabled != false,
                    onClick = onAxisX
                )
                CompactTextAction(
                    text = stringResource(R.string.placement_axis_short_y),
                    tag = "placement_axis_y_tool",
                    selected = state.selectedTool == SelectedEquipmentTool.Y,
                    onClick = onAxisY
                )
                CompactTextAction(
                    text = stringResource(R.string.placement_axis_short_z),
                    tag = "placement_axis_z_tool",
                    selected = state.selectedTool.isZAxis(),
                    enabled = zLimit?.enabled != false,
                    onClick = onAxisZ
                )
            }
            val xSelected = state.selectedTool == SelectedEquipmentTool.X
            val ySelected = state.selectedTool == SelectedEquipmentTool.Y
            val zSelected = state.selectedTool.isZAxis()
            if (xSelected && xLimit != null) {
                AxisValueSlider(
                    axis = PlacementAxis.X,
                    valueM = selected.positionM.xM,
                    limit = xLimit,
                    valueTag = "placement_slider_x_value",
                    sliderTag = "placement_slider_x",
                    valueText = stringResource(R.string.placement_slider_x, formatMetres2(selected.positionM.xM)),
                    onChange = { onCoordinate(PlacementAxis.X, it) }
                )
            }
            if (ySelected && yLimit != null) {
                AxisValueSlider(
                    axis = PlacementAxis.Y,
                    valueM = selected.positionM.yM,
                    limit = yLimit,
                    valueTag = "placement_slider_y_value",
                    sliderTag = "placement_slider_y",
                    valueText = stringResource(R.string.placement_slider_y, formatMetres2(selected.positionM.yM)),
                    onChange = { onCoordinate(PlacementAxis.Y, it) }
                )
            }
            if (zSelected && zLimit != null) {
                AxisValueSlider(
                    axis = PlacementAxis.Z,
                    valueM = selected.positionM.zM,
                    limit = zLimit,
                    valueTag = "placement_slider_z_value",
                    sliderTag = "elevation_slider",
                    valueText = stringResource(R.string.placement_slider_z, formatMetres2(selected.positionM.zM)),
                    onChange = { onCoordinate(PlacementAxis.Z, it) }
                )
            }
        }
    }
}

@Composable
private fun RowScope.CompactTextAction(
    text: String,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = Spacing.xxs, vertical = Spacing.xxs),
        modifier = modifier
            .weight(1f)
            .heightIn(min = 40.dp)
            .defaultMinSize(minWidth = 48.dp, minHeight = 40.dp)
            .testTag(tag)
    ) {
        Text(
            text = text,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible,
            color = if (selected) MaterialTheme.colorScheme.primary else Color.Unspecified,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun AxisValueSlider(
    axis: PlacementAxis,
    valueM: Double,
    limit: AxisSliderLimit,
    valueTag: String,
    sliderTag: String,
    valueText: String,
    onChange: (Double) -> Unit
) {
    Column(Modifier.fillMaxWidth().testTag("placement_slider_" + axis.name.lowercase())) {
        Text(
            valueText,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.testTag(valueTag)
        )
        if (!limit.enabled) {
            Text(
                limit.lockReason ?: stringResource(R.string.placement_axis_lock_exhaust_y),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
                modifier = Modifier.testTag("placement_axis_lock_" + axis.name.lowercase())
            )
        } else if (limit.hasSpan) {
            Slider(
                value = valueM.toFloat().coerceIn(limit.minM.toFloat(), limit.maxM.toFloat()),
                onValueChange = { onChange(it.toDouble()) },
                valueRange = limit.minM.toFloat()..limit.maxM.toFloat(),
                modifier = Modifier.testTag(sliderTag)
            )
        }
    }
}

@Composable
fun PlacementCoordinateGuideCard(
    crowded: Boolean,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(!crowded) }
    val showFull = if (crowded) expanded else true
    Surface(
        modifier = modifier.testTag(if (showFull) "placement_coord_guide" else "placement_coord_guide_xyz"),
        shape = Radii.md,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
    ) {
        if (showFull) {
            Column(
                modifier = Modifier
                    .widthIn(max = 168.dp)
                    .clickable(enabled = crowded) { expanded = false }
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
            ) {
                Text(
                    stringResource(R.string.placement_coord_guide_title),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    stringResource(R.string.placement_coord_guide_x),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    stringResource(R.string.placement_coord_guide_y),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    stringResource(R.string.placement_coord_guide_z),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    softWrap = false
                )
            }
        } else {
            TextButton(
                onClick = { expanded = true },
                contentPadding = PaddingValues(horizontal = Spacing.xs, vertical = 0.dp),
                modifier = Modifier.testTag("placement_coord_guide_toggle")
            ) {
                Text(
                    stringResource(R.string.placement_coord_guide_collapsed),
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
fun ThresholdHelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.placement_close)) }
        },
        title = { Text(stringResource(R.string.placement_threshold_help)) },
        text = {
            Column {
                Text(stringResource(R.string.placement_threshold_help_body), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(Spacing.sm))
                ThresholdCodeGlossary()
            }
        },
        modifier = Modifier.testTag("placement_threshold_help_sheet")
    )
}

@Composable
fun ThresholdCodeGlossary(modifier: Modifier = Modifier) {
    val lines = listOf(
        stringResource(R.string.equipment_glossary_csp),
        stringResource(R.string.equipment_glossary_cdp),
        stringResource(R.string.equipment_glossary_con),
        stringResource(R.string.equipment_glossary_esp),
        stringResource(R.string.equipment_glossary_edp),
        stringResource(R.string.equipment_glossary_eon),
        stringResource(R.string.equipment_glossary_fsp),
        stringResource(R.string.equipment_glossary_fon),
        stringResource(R.string.equipment_glossary_fdp)
    )
    Column(modifier.testTag("ar_threshold_glossary")) {
        Text(
            stringResource(R.string.placement_threshold_glossary_title),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.testTag("ar_threshold_glossary_title")
        )
        lines.forEachIndexed { index, line ->
            Text(
                line,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("ar_glossary_line_$index")
            )
        }
    }
}
