package com.greenhands.app.ar.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.greenhands.app.R
import com.greenhands.app.ar.domain.ArEquipmentInstanceStatus
import com.greenhands.app.ar.domain.ArGestureMode
import com.greenhands.app.ar.domain.ArHudContent
import com.greenhands.app.ar.domain.ArHudEquipmentLine
import com.greenhands.app.ar.domain.ArHudEquipmentSummary
import com.greenhands.app.ar.domain.ArHudModel
import com.greenhands.app.ar.domain.ArOverlayLayout
import com.greenhands.app.ar.domain.ArRuntimeTone
import com.greenhands.app.ar.domain.ArTabletopUiState
import com.greenhands.app.ar.domain.ArViewportSummary
import com.greenhands.app.ar.domain.EquipmentPresentation
import com.greenhands.app.ar.domain.EquipmentPresentationContext
import com.greenhands.app.ar.domain.EquipmentPresentationCopy
import com.greenhands.app.ui.theme.ForestEmerald
import com.greenhands.app.heat.ui.CropGlyph
import com.greenhands.app.equipment.domain.EquipmentLabelCatalog
import com.greenhands.app.equipment.domain.EquipmentLabelContent
import com.greenhands.app.equipment.domain.EquipmentScreenAnchor
import com.greenhands.app.equipment.domain.PlacementAxis
import com.greenhands.app.equipment.domain.PlacementAxisLimits
import com.greenhands.app.equipment.domain.PlacementValidator
import com.greenhands.app.equipment.domain.ViewportLabelLayout
import com.greenhands.app.equipment.model.EquipmentType
import com.greenhands.app.equipment.model.SelectedEquipmentTool
import com.greenhands.app.equipment.ui.CoordinateEditor
import com.greenhands.app.equipment.ui.EquipmentPlacementViewModel
import com.greenhands.app.equipment.ui.EquipmentPalette
import com.greenhands.app.equipment.ui.EquipmentStatusOverlay
import com.greenhands.app.equipment.ui.OverlayClimate
import com.greenhands.app.equipment.ui.PlacementUiState
import com.greenhands.app.equipment.ui.SelectedEquipmentBar
import com.greenhands.app.equipment.ui.ViewportCoordinateLabel
import com.greenhands.app.heat.visualization.CirculationProbeMarker
import com.greenhands.app.heat.visualization.FoggerDetailsModel
import com.greenhands.app.heat.visualization.PadInletVisual
import com.greenhands.app.heat.visualization.VisualizationCopy
import com.greenhands.app.heat.visualization.EnvironmentalFieldHeight
import com.greenhands.app.heat.visualization.EnvironmentalFieldLayer
import com.greenhands.app.preview3d.model.PreviewMode
import com.greenhands.app.ui.theme.Radii
import com.greenhands.app.ui.theme.Spacing
import kotlin.math.roundToInt

private val ArHudScrim = Color(0xFF0B0F0C).copy(alpha = 0.62f)
private val ArHudOnScrim = Color(0xFFF2F5F3)
private val ArHudMuted = Color(0xFFC5D0CA)

private fun hudRuntimeColor(tone: ArRuntimeTone): Color = when (tone) {
    ArRuntimeTone.ON -> ForestEmerald
    ArRuntimeTone.OFF, ArRuntimeTone.MIXED -> ArHudOnScrim
    ArRuntimeTone.NOT_RUNNING, ArRuntimeTone.EMPTY -> ArHudMuted
}

@Composable
fun ArEnvironmentStatusHud(
    model: ArHudModel,
    landscape: Boolean,
    crowded: Boolean,
    modifier: Modifier = Modifier,
    chip: Boolean = false,
    running: Boolean = false,
    onStopBehavior: (() -> Unit)? = null
) {
    val compact = chip || crowded || landscape
    Column(
        modifier = modifier
            .widthIn(
                max = if (compact) {
                    ArOverlayLayout.STATUS_HUD_COLLAPSED_WIDTH_DP.dp
                } else {
                    ArOverlayLayout.STATUS_HUD_WIDTH_DP.dp
                }
            )
            .testTag("ar_env_hud"),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
    ) {
        if (chip) {
            ArHudCard(tag = "ar_hud_chip_card") {
                Text(
                    text = "${model.cropName} · ${model.stageName} · ${model.optimalTemperatureLabel}",
                    color = ArHudOnScrim,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("ar_hud_chip_line")
                )
                Text(
                    text = "${model.liveTemperatureLabel} · ${model.liveStatusLabel}",
                    color = ArHudMuted,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (running && onStopBehavior != null) {
                ArHudStopChip(onStop = onStopBehavior)
            }
        } else if (compact) {
            ArHudCard(tag = "ar_hud_collapsed_card") {
                ArHudCropHeader(model = model, compact = true)
                Text(
                    text = "${model.optimalTemperatureLabel} · ${model.optimalHumidityLabel}",
                    color = ArHudOnScrim,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("ar_hud_optimal")
                )
                Text(
                    text = "${model.liveTemperatureLabel} · ${model.liveHumidityLabel} · ${model.liveStatusLabel}",
                    color = ArHudMuted,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("ar_hud_live_temp")
                )
                if (model.showsOutsideWeather) {
                    Text(
                        text = listOf(
                            model.outsideTemperatureLabel,
                            model.outsideHumidityLabel,
                            model.outsideStatusLabel
                        ).joinToString(" · "),
                        color = ArHudMuted,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("ar_hud_outside_compact")
                    )
                    Text(
                        text = listOfNotNull(
                            model.outsideLocationLabel.takeIf { it.isNotBlank() },
                            model.outsideAttributionLabel
                        ).joinToString(" · "),
                        color = ArHudMuted,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("ar_hud_outside_source")
                    )
                }
                ArHudEquipmentSummaries(
                    lines = model.equipment,
                    modifier = Modifier.testTag("ar_hud_equipment_summary")
                )
            }
            if (running && onStopBehavior != null) {
                ArHudStopChip(onStop = onStopBehavior)
            }
        } else {
            ArHudCropCard(model = model, compact = false)
            ArHudLiveCard(model = model)
            ArHudEquipmentCard(lines = model.equipment)
            if (running && onStopBehavior != null) {
                ArHudStopChip(onStop = onStopBehavior)
            }
        }
    }
}

@Composable
private fun ArHudCropHeader(model: ArHudModel, compact: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        model.crop?.let { crop ->
            CropGlyph(crop = crop, size = if (compact) 16.dp else 20.dp)
        }
        Column(Modifier.weight(1f, fill = false)) {
            Text(
                text = model.cropName,
                color = ArHudOnScrim,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("ar_hud_crop")
            )
            Text(
                text = model.stageName,
                color = ArHudMuted,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("ar_hud_stage")
            )
        }
    }
}

@Composable
private fun ArHudCropCard(model: ArHudModel, compact: Boolean) {
    ArHudCard(tag = "ar_hud_crop_card") {
        ArHudCropHeader(model = model, compact = compact)
        Text(
            text = stringResource(R.string.ar_hud_optimal),
            color = ArHudMuted,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1
        )
        Text(
            text = model.optimalTemperatureLabel,
            color = ArHudOnScrim,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("ar_hud_optimal_temp")
        )
        Text(
            text = model.optimalHumidityLabel,
            color = ArHudOnScrim,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("ar_hud_optimal_rh")
        )
    }
}

@Composable
private fun ArHudLiveCard(model: ArHudModel) {
    ArHudCard(tag = "ar_hud_live_card") {
        Text(
            text = stringResource(R.string.ar_hud_live_temp, model.liveTemperatureLabel),
            color = ArHudOnScrim,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("ar_hud_live_temp")
        )
        Text(
            text = stringResource(R.string.ar_hud_live_rh, model.liveHumidityLabel),
            color = ArHudOnScrim,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("ar_hud_live_rh")
        )
        Text(
            text = stringResource(R.string.ar_hud_live_status, model.liveStatusLabel),
            color = ArHudMuted,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("ar_hud_live_status")
        )
        if (model.showsOutsideWeather) {
            Text(
                text = model.outsideTemperatureLabel,
                color = ArHudOnScrim,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("ar_hud_outside_temp")
            )
            Text(
                text = model.outsideHumidityLabel,
                color = ArHudOnScrim,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("ar_hud_outside_rh")
            )
            if (model.outsideLocationLabel.isNotBlank()) {
                Text(
                    text = model.outsideLocationLabel,
                    color = ArHudMuted,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("ar_hud_outside_location")
                )
            }
            Text(
                text = model.outsideAttributionLabel,
                color = ArHudMuted,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("ar_hud_outside_source")
            )
            Text(
                text = model.outsideStatusLabel,
                color = ArHudMuted,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("ar_hud_outside_status")
            )
        }
    }
}

@Composable
private fun ArHudEquipmentCard(lines: List<ArHudEquipmentLine>) {
    ArHudCard(tag = "ar_hud_equipment_card") {
        ArHudEquipmentSummaries(lines = lines)
    }
}

@Composable
private fun ArHudEquipmentSummaries(
    lines: List<ArHudEquipmentLine>,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val stacked = ArHudEquipmentSummary.sectionStacked(
            lines.map { it.title to it.state },
            maxWidth.value
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            lines.forEach { line ->
                ArHudEquipmentSummaryRow(line = line, stacked = stacked)
            }
        }
    }
}

@Composable
private fun ArHudEquipmentSummaryRow(line: ArHudEquipmentLine, stacked: Boolean) {
    val titleColor = ArHudOnScrim
    val stateColor = hudRuntimeColor(line.tone)
    val rowModifier = Modifier
        .fillMaxWidth()
        .testTag("ar_hud_equip_${line.type.name.lowercase()}")
    if (stacked) {
        Column(rowModifier) {
            ArHudEquipmentText(
                text = line.title,
                color = titleColor,
                tag = "ar_hud_equip_title_${line.type.name.lowercase()}"
            )
            ArHudEquipmentText(
                text = line.state,
                color = stateColor,
                tag = "ar_hud_equip_state_${line.type.name.lowercase()}"
            )
        }
    } else {
        Row(
            modifier = rowModifier,
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ArHudEquipmentText(
                text = line.title,
                color = titleColor,
                tag = "ar_hud_equip_title_${line.type.name.lowercase()}",
                modifier = Modifier.weight(1f)
            )
            ArHudEquipmentText(
                text = line.state,
                color = stateColor,
                tag = "ar_hud_equip_state_${line.type.name.lowercase()}"
            )
        }
    }
}

@Composable
private fun ArHudEquipmentText(
    text: String,
    color: Color,
    tag: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        color = color,
        style = MaterialTheme.typography.labelSmall,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Visible,
        modifier = modifier.testTag(tag)
    )
}

@Composable
private fun ArHudCard(tag: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag),
        shape = Radii.md,
        color = ArHudScrim
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
            verticalArrangement = Arrangement.spacedBy(1.dp),
            content = content
        )
    }
}

@Composable
private fun ArHudStopChip(onStop: () -> Unit, modifier: Modifier = Modifier) {
    val stopDescription = stringResource(R.string.ar_stop_behavior)
    Surface(
        modifier = modifier
            .testTag("ar_hud_stop_behavior")
            .clickable(onClick = onStop)
            .semantics { contentDescription = stopDescription },
        shape = Radii.md,
        color = ArHudScrim
    ) {
        Text(
            text = stringResource(R.string.ar_stop_short),
            color = ArHudOnScrim,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xxs)
        )
    }
}

@Composable
fun ArClimateSummaryChip(
    model: ArHudModel,
    mode: PreviewMode,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val line = ArViewportSummary.climateLine(model, mode)
    val summaryDescription = stringResource(R.string.ar_climate_summary)
    Surface(
        modifier = modifier
            .testTag("ar_env_hud")
            .clickable(onClick = onOpen)
            .semantics { contentDescription = summaryDescription },
        shape = Radii.md,
        color = ArHudScrim
    ) {
        Text(
            text = "$line  ▼",
            color = ArHudOnScrim,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                .testTag("ar_hud_summary_line")
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArClimateDetailsSheet(
    model: ArHudModel,
    onDismiss: () -> Unit,
    tooColdAdvisory: com.greenhands.app.heat.advisory.TooColdAdvisory? = null,
    onViewDecisionSupport: () -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag("ar_climate_sheet")
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = Spacing.sm)
                .padding(bottom = Spacing.lg)
                .verticalScroll(rememberScrollState())
                .testTag("ar_env_hud_details"),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            tooColdAdvisory?.let { advisory ->
                com.greenhands.app.heat.advisory.TooColdAdvisoryBanner(
                    advisory = advisory,
                    onViewSuggestions = onViewDecisionSupport
                )
            }
            ArHudCropCard(model = model, compact = false)
            ArHudLiveCard(model = model)
            ArHudEquipmentCard(lines = model.equipment)
        }
    }
}

@Composable
fun ArViewportStopChip(
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    ArHudStopChip(onStop = onStop, modifier = modifier)
}

@Composable
fun ArPlacementChrome(
    ui: ArTabletopUiState,
    placement: PlacementUiState,
    onAdd: () -> Unit,
    onGestureMode: (ArGestureMode) -> Unit,
    onLabels: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    var modeMenu by remember { mutableStateOf(false) }
    val modeLabel = if (ui.gestureMode == ArGestureMode.TABLETOP) {
        stringResource(R.string.ar_gesture_table)
    } else {
        stringResource(R.string.placement_move)
    }
    val modeMenuDescription = stringResource(R.string.ar_mode_menu)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xxs, vertical = Spacing.xxs)
            .testTag("ar_bottom_bar"),
        shape = Radii.md,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.xxs, vertical = Spacing.xxs)
                .testTag("ar_chrome_row_1"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            CompactChromeAction(
                text = stringResource(R.string.ar_add),
                tag = "ar_add_equipment",
                enabled = ui.isPlaced,
                onClick = onAdd
            )
            Box(modifier = Modifier.weight(1f)) {
                FilledTonalButton(
                    onClick = { modeMenu = true },
                    enabled = ui.isPlaced,
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = Spacing.xxs),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 40.dp)
                        .defaultMinSize(minWidth = 48.dp, minHeight = 40.dp)
                        .testTag("ar_gesture_mode")
                        .semantics { contentDescription = modeMenuDescription }
                ) {
                    ChromeLabel("$modeLabel  ▼")
                }
                DropdownMenu(
                    expanded = modeMenu,
                    onDismissRequest = { modeMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.placement_move)) },
                        onClick = {
                            onGestureMode(ArGestureMode.EQUIPMENT_MOVE)
                            modeMenu = false
                        },
                        modifier = Modifier.testTag("ar_gesture_move")
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.ar_gesture_table)) },
                        onClick = {
                            onGestureMode(ArGestureMode.TABLETOP)
                            modeMenu = false
                        },
                        modifier = Modifier.testTag("ar_gesture_table")
                    )
                }
            }
            CompactChromeAction(
                text = stringResource(R.string.placement_labels),
                tag = "preview_labels_toggle",
                selected = placement.labelsVisible,
                onClick = onLabels
            )
            CompactChromeAction(
                text = stringResource(R.string.ar_more),
                tag = "ar_more",
                enabled = ui.isPlaced,
                onClick = onMore
            )
        }
    }
}

@Composable
private fun RowScope.CompactChromeAction(
    text: String,
    tag: String,
    onClick: () -> Unit,
    selected: Boolean = false,
    enabled: Boolean = true,
    contentDescription: String? = null
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = Spacing.xxs),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 40.dp)
            .defaultMinSize(minWidth = 48.dp, minHeight = 40.dp)
            .testTag(tag)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                }
            )
    ) {
        ChromeLabel(text)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArMoreSheet(
    ui: ArTabletopUiState,
    placement: PlacementUiState,
    onEquipmentStatus: () -> Unit,
    onBehavior: () -> Unit,
    onClear: () -> Unit,
    onReset: () -> Unit,
    onReposition: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    onEnvironmentalLayer: (EnvironmentalFieldLayer) -> Unit = {},
    onFieldHeight: (EnvironmentalFieldHeight) -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag("ar_more_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = Spacing.sm)
                .padding(bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            SheetAction(
                text = stringResource(R.string.placement_equipment_status),
                tag = "ar_equipment_status",
                enabled = ui.isPlaced,
                onClick = onEquipmentStatus
            )
            SheetAction(
                text = stringResource(R.string.ar_behavior),
                tag = "ar_behavior",
                selected = placement.behaviorSheetOpen || placement.behaviorRunning,
                enabled = ui.isPlaced,
                onClick = onBehavior
            )
            Text(
                text = stringResource(R.string.ar_env_layer_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier
                    .padding(top = Spacing.xs)
                    .testTag("ar_env_layer_title")
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                FieldChoiceButton(
                    text = stringResource(R.string.ar_env_layer_off),
                    tag = "ar_env_layer_off",
                    selected = placement.environmentalLayer == EnvironmentalFieldLayer.OFF,
                    onClick = { onEnvironmentalLayer(EnvironmentalFieldLayer.OFF) },
                    modifier = Modifier.weight(1f)
                )
                FieldChoiceButton(
                    text = stringResource(R.string.ar_env_layer_temperature),
                    tag = "ar_env_layer_temperature",
                    selected = placement.environmentalLayer == EnvironmentalFieldLayer.TEMPERATURE,
                    onClick = { onEnvironmentalLayer(EnvironmentalFieldLayer.TEMPERATURE) },
                    modifier = Modifier.weight(1f)
                )
                FieldChoiceButton(
                    text = stringResource(R.string.ar_env_layer_humidity),
                    tag = "ar_env_layer_humidity",
                    selected = placement.environmentalLayer == EnvironmentalFieldLayer.HUMIDITY,
                    onClick = { onEnvironmentalLayer(EnvironmentalFieldLayer.HUMIDITY) },
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                text = stringResource(R.string.ar_env_height_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.testTag("ar_env_height_title")
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                FieldChoiceButton(
                    text = stringResource(R.string.ar_env_height_whole),
                    tag = "ar_env_height_whole",
                    selected = placement.fieldHeight == EnvironmentalFieldHeight.WHOLE,
                    onClick = { onFieldHeight(EnvironmentalFieldHeight.WHOLE) },
                    modifier = Modifier.weight(1f)
                )
                FieldChoiceButton(
                    text = stringResource(R.string.ar_env_height_crop),
                    tag = "ar_env_height_crop",
                    selected = placement.fieldHeight == EnvironmentalFieldHeight.CROP,
                    onClick = { onFieldHeight(EnvironmentalFieldHeight.CROP) },
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                FieldChoiceButton(
                    text = stringResource(R.string.ar_env_height_middle),
                    tag = "ar_env_height_middle",
                    selected = placement.fieldHeight == EnvironmentalFieldHeight.MIDDLE,
                    onClick = { onFieldHeight(EnvironmentalFieldHeight.MIDDLE) },
                    modifier = Modifier.weight(1f)
                )
                FieldChoiceButton(
                    text = stringResource(R.string.ar_env_height_upper),
                    tag = "ar_env_height_upper",
                    selected = placement.fieldHeight == EnvironmentalFieldHeight.UPPER,
                    onClick = { onFieldHeight(EnvironmentalFieldHeight.UPPER) },
                    modifier = Modifier.weight(1f)
                )
            }
            SheetAction(
                text = stringResource(R.string.ar_clear_equipment),
                tag = "ar_clear_equipment",
                enabled = ui.isPlaced && placement.instances.isNotEmpty(),
                onClick = onClear
            )
            SheetAction(
                text = stringResource(R.string.preview3d_reset),
                tag = "ar_reset_view",
                enabled = ui.isPlaced,
                onClick = onReset
            )
            SheetAction(
                text = stringResource(R.string.ar_reposition),
                tag = "ar_reposition",
                enabled = ui.isPlaced,
                onClick = onReposition
            )
            if (placement.showsPersistentSave) {
                SheetAction(
                    text = stringResource(R.string.placement_save_short),
                    tag = "placement_save",
                    enabled = ui.isPlaced,
                    onClick = onSave,
                    contentDescription = stringResource(R.string.placement_save)
                )
            }
        }
    }
}

@Composable
private fun FieldChoiceButton(
    text: String,
    tag: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilledTonalButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = Spacing.xxs, vertical = Spacing.xxs),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        modifier = modifier
            .heightIn(min = 48.dp)
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .testTag(tag)
    ) {
        Text(
            text = text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun SheetAction(
    text: String,
    tag: String,
    onClick: () -> Unit,
    selected: Boolean = false,
    enabled: Boolean = true,
    contentDescription: String? = null
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag(tag)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                }
            )
    ) {
        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun ArSuggestionsControl(
    onOpen: (EquipmentType) -> Unit,
    modifier: Modifier = Modifier
) {
    var menu by remember { mutableStateOf(false) }
    val suggestionsDescription = stringResource(R.string.ar_suggestions)
    Box(modifier.testTag("ar_suggestion_rail")) {
        FilledTonalButton(
            onClick = { menu = true },
            contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = Spacing.xxs),
            modifier = Modifier
                .heightIn(min = 40.dp)
                .testTag("suggestion_rail")
                .semantics { contentDescription = suggestionsDescription }
        ) {
            Text(
                text = stringResource(R.string.ar_suggestions),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium
            )
        }
        DropdownMenu(
            expanded = menu,
            onDismissRequest = { menu = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.placement_suggest_circulation)) },
                onClick = {
                    onOpen(EquipmentType.CIRCULATION_FAN)
                    menu = false
                },
                modifier = Modifier.testTag("suggestion_circulation")
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.placement_suggest_exhaust)) },
                onClick = {
                    onOpen(EquipmentType.EXHAUST_FAN)
                    menu = false
                },
                modifier = Modifier.testTag("suggestion_exhaust")
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.placement_suggest_fogger)) },
                onClick = {
                    onOpen(EquipmentType.FOGGER)
                    menu = false
                },
                modifier = Modifier.testTag("suggestion_fogger")
            )
        }
    }
}

@Composable
fun ArAddEquipmentPanel(
    onTap: (EquipmentType) -> Unit,
    onSlotPlaced: (EquipmentType, androidx.compose.ui.geometry.Rect) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("ar_add_sheet"),
        shape = Radii.md,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
    ) {
        Column(Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.xxs)) {
            Text(
                text = stringResource(R.string.ar_add_title),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.xxs)
            )
            ArEquipmentPalette(onTap = onTap, onSlotPlaced = onSlotPlaced)
        }
    }
}

@Composable
private fun ChromeLabel(text: String) {
    Text(
        text = text,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Visible,
        style = MaterialTheme.typography.labelMedium
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArEquipmentStatusSheet(
    placement: PlacementUiState,
    climate: OverlayClimate,
    onHelp: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val presentation = EquipmentPresentationContext(
        mode = placement.previewMode,
        visualizationRunning = placement.behaviorRunning,
        actualRelayById = placement.actualRelayById
    )
    val snapshot = ArHudContent.workingCopyStatus(
        placement.instances,
        placement.runtimeById,
        presentation = presentation
    )
    val counts = EquipmentType.entries.associateWith { type ->
        snapshot.aggregates.first { it.type == type }.count
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag("ar_equipment_status_sheet")
    ) {
        EquipmentStatusOverlay(
            climate = climate,
            instanceNames = snapshot.instances.map { it.name },
            onHelp = onHelp,
            onDismiss = onDismiss,
            layoutSaved = placement.saved,
            typeCounts = counts,
            instanceStatuses = snapshot.instances,
            equipmentStateExplanation = EquipmentPresentationCopy.NOT_EVALUATED_EXPLANATION.takeIf {
                EquipmentPresentation.showsNotEvaluatedExplanation(presentation)
            },
            visualizationDetails = VisualizationCopy.PREDICTED_UNCALIBRATED.takeIf {
                placement.behaviorRunning
            },
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = Spacing.sm)
        )
    }
}

@Composable
fun ArEquipmentPalette(
    onTap: (com.greenhands.app.equipment.model.EquipmentType) -> Unit,
    onSlotPlaced: (com.greenhands.app.equipment.model.EquipmentType, androidx.compose.ui.geometry.Rect) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier.testTag("ar_equipment_palette")) {
        EquipmentPalette(
            onTap = onTap,
            onSlotPlaced = onSlotPlaced
        )
    }
}

@Composable
fun ArSelectedEquipmentChip(
    placementState: PlacementUiState,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selected = placementState.selected ?: return
    val line = ArViewportSummary.selectedLine(
        selected.displayLabel(),
        selected.positionM.xM,
        selected.positionM.yM,
        selected.positionM.zM
    )
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("ar_selected_chip")
            .clickable(onClick = onOpen),
        shape = Radii.md,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f)
    ) {
        Text(
            text = "$line  ▼",
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArSelectedEquipmentSheet(
    placementState: PlacementUiState,
    placementViewModel: EquipmentPlacementViewModel?,
    onDismiss: () -> Unit
) {
    val selected = placementState.selected ?: return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val ctx = placementViewModel?.placementContext()
    val others = placementState.instances
    val xLimit = if (ctx != null) PlacementAxisLimits.forAxis(selected, others, ctx, PlacementAxis.X) else null
    val yLimit = if (ctx != null) PlacementAxisLimits.forAxis(selected, others, ctx, PlacementAxis.Y) else null
    val zLimit = if (ctx != null) PlacementAxisLimits.forAxis(selected, others, ctx, PlacementAxis.Z) else null
    val elevation = if (ctx != null) {
        PlacementValidator.elevationRange(selected.type, ctx, selected.dimensions)
    } else {
        null
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag("ar_selected_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Spacing.sm)
                .padding(bottom = Spacing.sm)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            SelectedEquipmentBar(
                state = placementState,
                onMove = { placementViewModel?.setSelectedTool(SelectedEquipmentTool.MOVE) },
                onRotate90 = { placementViewModel?.rotateSelected(90.0) },
                onDuplicate = { placementViewModel?.duplicateSelected() },
                onDelete = { placementViewModel?.requestDelete() },
                onAxisX = { placementViewModel?.setSelectedTool(SelectedEquipmentTool.X) },
                onAxisY = { placementViewModel?.setSelectedTool(SelectedEquipmentTool.Y) },
                onAxisZ = { placementViewModel?.setSelectedTool(SelectedEquipmentTool.Z) },
                onExact = { placementViewModel?.setShowExactEditor(true) },
                onCoordinate = { axis, value ->
                    when (axis) {
                        PlacementAxis.X -> placementViewModel?.setAxisX(value)
                        PlacementAxis.Y -> placementViewModel?.setAxisY(value)
                        PlacementAxis.Z -> placementViewModel?.setAxisZ(value)
                    }
                },
                xLimit = xLimit,
                yLimit = yLimit,
                zLimit = zLimit
            )
            if (selected.type == EquipmentType.FOGGER) {
                ArFoggerDetailsSection(
                    details = placementViewModel?.foggerDetails(placementState)
                )
            }
            if (placementState.showExactEditor) {
                CoordinateEditor(
                    state = placementState,
                    onApply = { x, y, z, yaw -> placementViewModel?.applyNumericEdit(x, y, z, yaw) },
                    onRotate = { placementViewModel?.rotateSelected() },
                    onDuplicate = { placementViewModel?.duplicateSelected() },
                    onDelete = { placementViewModel?.requestDelete() },
                    onConfirmGhost = { placementViewModel?.confirmGhost() },
                    onCancelGhost = { placementViewModel?.cancelGhost() },
                    elevationMinM = elevation?.start,
                    elevationMaxM = elevation?.endInclusive,
                    onElevation = { placementViewModel?.setElevation(it) },
                    modifier = Modifier.heightIn(max = 220.dp)
                )
            }
        }
    }
}

@Composable
fun ArViewportLabels(
    placementState: PlacementUiState,
    anchors: List<EquipmentScreenAnchor>,
    compactLabelW: Double,
    compactLabelH: Double,
    selectedLabelW: Double,
    selectedLabelH: Double,
    densityLiftPx: Float,
    densityPadPx: Float,
    bottomReservePx: Float,
    padPanel: com.greenhands.app.heat.visualization.PadPanelSpec? = null
) {
    val equipment = EquipmentLabelCatalog.contents(
        instances = placementState.displayInstances,
        selectedId = placementState.selectedId,
        draggingId = placementState.draggingId,
        labelsVisible = placementState.labelsVisible
    )
    val padContent = if (placementState.labelsVisible && padPanel != null) {
        listOf(
            EquipmentLabelContent(
                id = PadInletVisual.LABEL_ID,
                name = PadInletVisual.LABEL,
                showCoordinates = false,
                xM = padPanel.originM.xM,
                yM = padPanel.originM.yM,
                zM = padPanel.originM.zM,
                selected = false
            )
        )
    } else {
        emptyList()
    }
    val contents = equipment + padContent
    val byId = anchors.associateBy { it.id }
    val layoutInputs = contents.mapNotNull { content ->
        val anchor = byId[content.id] ?: return@mapNotNull null
        ViewportLabelLayout.Input(
            id = content.id,
            anchorXPx = anchor.xPx.toDouble(),
            anchorYPx = anchor.yPx.toDouble(),
            widthPx = if (content.selected) selectedLabelW else compactLabelW,
            heightPx = if (content.selected) selectedLabelH else compactLabelH,
            selected = content.selected
        )
    }
    val viewportW = anchors.firstOrNull()?.viewportWidthPx?.toDouble() ?: 0.0
    val viewportH = (anchors.firstOrNull()?.viewportHeightPx?.toDouble() ?: 0.0)
        .minus(bottomReservePx.toDouble())
        .coerceAtLeast(0.0)
    val placed = ViewportLabelLayout.place(
        inputs = layoutInputs,
        viewportWidthPx = viewportW,
        viewportHeightPx = viewportH,
        padPx = densityPadPx.toDouble(),
        liftPx = densityLiftPx.toDouble()
    )
    val contentById = contents.associateBy { it.id }
    placed.sortedBy { it.selected }.forEach { item ->
        val content = contentById[item.id] ?: return@forEach
        ViewportCoordinateLabel(
            name = content.name,
            xM = content.xM,
            yM = content.yM,
            zM = content.zM,
            compact = !content.showCoordinates,
            selected = content.selected,
            modifier = Modifier
                .testTag("viewport_label_" + content.name.replace(' ', '_'))
                .offset {
                    IntOffset(item.xPx.roundToInt(), item.yPx.roundToInt())
                }
        )
    }
}

@Composable
fun ArProbesControl(
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .testTag("circ_probe_chip")
            .clickable(onClick = onOpen)
            .semantics {
                contentDescription = VisualizationCopy.CIRCULATION_PROBE_CHIP
            },
        shape = Radii.md,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f)
    ) {
        Text(
            text = "${stringResource(R.string.ar_probes)}  ▼",
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArProbesSheet(
    markers: List<CirculationProbeMarker>,
    onDismiss: () -> Unit,
    caption: String = VisualizationCopy.PREDICTED_UNCALIBRATED,
    showHumidity: Boolean = false,
    padSourceLabel: String? = null,
    padOutletTemperatureC: Double? = null,
    padOutletRhPercent: Double? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag("ar_probes_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Spacing.sm)
                .padding(bottom = Spacing.lg)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = stringResource(R.string.ar_probes_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = Spacing.xxs)
            )
            Text(
                text = caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Spacing.sm)
            )
            if (markers.isEmpty()) {
                Text(
                    text = stringResource(R.string.ar_probes_empty),
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                val showPadDistance = markers.any { it.distanceFromPadM != null }
                Row(Modifier.fillMaxWidth().padding(bottom = Spacing.xs)) {
                    Text(
                        stringResource(R.string.ar_probes_distance),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(0.8f)
                    )
                    if (showPadDistance) {
                        Text(
                            stringResource(R.string.ar_probes_distance_pad),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.weight(0.8f)
                        )
                    }
                    Text(
                        stringResource(R.string.ar_probes_temperature),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1.2f)
                    )
                    if (showHumidity) {
                        Text(
                            stringResource(R.string.ar_probes_humidity),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.weight(1.1f)
                        )
                    }
                    Text(
                        stringResource(R.string.ar_probes_classification),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1.0f)
                    )
                }
                markers.forEach { marker ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("circ_probe_row_${marker.distanceM.toInt()}")
                            .padding(vertical = Spacing.xxs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            marker.compactDistanceText,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(0.8f)
                        )
                        if (showPadDistance) {
                            Text(
                                marker.predictedPadDistanceText,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(0.8f)
                            )
                        }
                        Text(
                            marker.predictedTemperatureText,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1.2f)
                        )
                        if (showHumidity) {
                            Text(
                                marker.predictedHumidityText,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1.1f)
                            )
                        }
                        Text(
                            marker.classificationLabel,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1.0f)
                        )
                    }
                }
                if (padSourceLabel != null || padOutletTemperatureC != null) {
                    Text(
                        VisualizationCopy.FAN_PAD_PREDICTED_UNCALIBRATED,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.sm)
                    )
                    padSourceLabel?.let { label ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.testTag("ar_probes_pad_source")
                        )
                    }
                    if (padOutletTemperatureC != null) {
                        val rh = padOutletRhPercent
                        Text(
                            text = "Pad outlet predicted: ${
                                String.format(java.util.Locale.US, "%.1f°C", padOutletTemperatureC)
                            }" + if (rh != null) {
                                String.format(java.util.Locale.US, " / %.1f %% RH", rh)
                            } else {
                                ""
                            },
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.testTag("ar_probes_pad_outlet")
                        )
                    }
                    Text(
                        VisualizationCopy.FAN_PAD_COMBINED_FIELD_NOTE,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun ArCirculationProbeLabels(
    markers: List<CirculationProbeMarker>,
    anchors: List<EquipmentScreenAnchor>,
    labelW: Double,
    labelH: Double,
    densityLiftPx: Float,
    densityPadPx: Float
) {
    if (markers.isEmpty() || anchors.isEmpty()) return
    val byId = anchors.associateBy { it.id }
    val layoutInputs = markers.mapNotNull { marker ->
        val anchor = byId[marker.id] ?: return@mapNotNull null
        ViewportLabelLayout.Input(
            id = marker.id,
            anchorXPx = anchor.xPx.toDouble(),
            anchorYPx = anchor.yPx.toDouble(),
            widthPx = labelW,
            heightPx = labelH,
            selected = false
        )
    }
    val viewportW = anchors.firstOrNull()?.viewportWidthPx?.toDouble() ?: 0.0
    val viewportH = (anchors.firstOrNull()?.viewportHeightPx?.toDouble() ?: 0.0)
        .coerceAtLeast(0.0)
    val markerById = markers.associateBy { it.id }
    val placed = ViewportLabelLayout.place(
        inputs = layoutInputs,
        viewportWidthPx = viewportW,
        viewportHeightPx = viewportH,
        padPx = densityPadPx.toDouble(),
        liftPx = densityLiftPx.toDouble()
    )
    placed.forEach { item ->
        val marker = markerById[item.id] ?: return@forEach
        val rgb = marker.classificationColor
        val fill = if (rgb.isUnavailable) {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.70f)
        } else {
            Color(rgb.red, rgb.green, rgb.blue, 0.78f)
        }
        Surface(
            modifier = Modifier
                .testTag("circ_probe_label_${marker.distanceM.toInt()}")
                .offset { IntOffset(item.xPx.roundToInt(), item.yPx.roundToInt()) },
            shape = Radii.sm,
            color = fill,
            tonalElevation = 0.dp
        ) {
            Text(
                text = marker.compactDistanceText,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
            )
        }
    }
}

@Composable
private fun ArFoggerDetailsSection(
    details: FoggerDetailsModel?,
    modifier: Modifier = Modifier
) {
    if (details == null) return
    var expanded by remember { mutableStateOf(false) }
    val rhText = details.localRhPercent?.let { rh ->
        "${String.format(java.util.Locale.US, "%.1f", rh)} % RH · ${details.classificationLabel}"
    } ?: details.classificationLabel
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("ar_fog_details"),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
    ) {
        FilledTonalButton(
            onClick = { expanded = !expanded },
            contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = Spacing.xxs),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("ar_fog_details_action")
        ) {
            Text(
                text = stringResource(R.string.ar_fog_details),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = stringResource(R.string.ar_fog_state, details.runtimeLabel),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.testTag("ar_fog_state")
        )
        Text(
            text = stringResource(R.string.ar_fog_rh_class, rhText),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.testTag("ar_fog_rh_class")
        )
        Text(
            text = stringResource(R.string.ar_fog_ready, details.readinessLabel),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.testTag("ar_fog_ready")
        )
        details.limitation?.let { limitation ->
            Text(
                text = limitation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("ar_fog_limitation")
            )
        }
        if (expanded) {
            Text(
                text = details.qualitativeNote,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("ar_fog_qualitative_note")
            )
        }
    }
}
