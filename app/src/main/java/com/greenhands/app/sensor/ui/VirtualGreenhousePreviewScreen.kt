package com.greenhands.app.sensor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.greenhands.app.R
import com.greenhands.app.sensor.ar.ArVisualizationMapper
import com.greenhands.app.sensor.ar.OrbitCameraState
import com.greenhands.app.sensor.ar.VirtualGreenhouseLabels
import com.greenhands.app.sensor.ar.VirtualGreenhouseRenderer
import com.greenhands.app.sensor.ar.defaultOrbitForSnapshot
import com.greenhands.app.sensor.model.SensorType
import com.greenhands.app.ui.components.DemoNotice
import com.greenhands.app.ui.components.InfoCard
import com.greenhands.app.ui.components.PrimaryActionButton
import com.greenhands.app.ui.components.ScreenScaffold
import com.greenhands.app.ui.components.ScrollScreen
import com.greenhands.app.ui.components.SecondaryActionButton
import com.greenhands.app.ui.theme.ClimateTeal
import com.greenhands.app.ui.theme.ForestEmerald
import com.greenhands.app.ui.theme.Spacing

@Composable
fun VirtualGreenhousePreviewScreen(
    ui: SensorPlacementUiState,
    onBack: (() -> Unit)? = null
) {
    var typeFilter by remember { mutableStateOf<SensorType?>(null) }
    var showCoverage by remember { mutableStateOf(true) }
    var showSensors by remember { mutableStateOf(true) }
    var showRecommendations by remember { mutableStateOf(true) }
    val snapshot = remember(ui, typeFilter) {
        ArVisualizationMapper.from(ui, selectedTypeFilter = typeFilter)
    }
    val physicalKey = remember(
        snapshot.physical.lengthMeters,
        snapshot.physical.widthMeters,
        snapshot.physical.heightMeters
    ) {
        Triple(
            snapshot.physical.lengthMeters,
            snapshot.physical.widthMeters,
            snapshot.physical.heightMeters
        )
    }
    var camera by remember(physicalKey) {
        mutableStateOf(defaultOrbitForSnapshot(snapshot))
    }
    val hasRecommendations = snapshot.recommendations.isNotEmpty()
    val blindCount = VirtualGreenhouseLabels.blindCellCount(snapshot)

    ScreenScaffold(
        title = stringResource(R.string.sensor_virtual_preview_title),
        onBack = onBack
    ) { padding ->
        ScrollScreen(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag("virtual_greenhouse_preview")
        ) {
            Text(
                text = stringResource(R.string.sensor_virtual_preview_heading),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier
                    .semantics { heading() }
                    .testTag("virtual_preview_heading")
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = stringResource(R.string.sensor_virtual_preview_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(Spacing.md))
            DemoNotice(
                text = stringResource(R.string.sensor_virtual_preview_notice),
                modifier = Modifier.testTag("virtual_preview_notice")
            )
            Spacer(Modifier.height(Spacing.md))

            InfoCard(modifier = Modifier.testTag("virtual_preview_metrics_card")) {
                PreviewMetricRow(
                    label = stringResource(R.string.sensor_virtual_preview_dim_greenhouse_label),
                    value = stringResource(
                        R.string.sensor_virtual_preview_dim_greenhouse_value,
                        VirtualGreenhouseLabels.formatMeters(snapshot.physical.lengthMeters),
                        VirtualGreenhouseLabels.formatMeters(snapshot.physical.widthMeters),
                        VirtualGreenhouseLabels.formatMeters(snapshot.physical.heightMeters)
                    ),
                    testTag = "virtual_preview_dimensions"
                )
                Spacer(Modifier.height(Spacing.xs))
                PreviewMetricRow(
                    label = stringResource(R.string.sensor_virtual_preview_dim_cell_label),
                    value = stringResource(
                        R.string.sensor_virtual_preview_dim_cell_value,
                        VirtualGreenhouseLabels.formatMeters(snapshot.physical.cellSizeMeters)
                    ),
                    testTag = "virtual_preview_cell_size"
                )
                Spacer(Modifier.height(Spacing.sm))
                PreviewMetricRow(
                    label = stringResource(R.string.sensor_virtual_preview_metric_sensors),
                    value = snapshot.sensors.size.toString(),
                    testTag = "virtual_preview_metric_sensors"
                )
                PreviewMetricRow(
                    label = stringResource(R.string.sensor_virtual_preview_metric_recommendations),
                    value = snapshot.recommendations.size.toString(),
                    testTag = "virtual_preview_metric_recommendations"
                )
                PreviewMetricRow(
                    label = stringResource(R.string.sensor_virtual_preview_metric_blind),
                    value = blindCount.toString(),
                    testTag = "virtual_preview_metric_blind"
                )
            }

            Spacer(Modifier.height(Spacing.section))

            InfoCard(modifier = Modifier.testTag("virtual_preview_filter_card")) {
                Text(
                    text = stringResource(R.string.sensor_virtual_preview_filter_title),
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(Modifier.height(Spacing.sm))
                PreviewTypeFilter(selected = typeFilter, onSelect = { typeFilter = it })
                Spacer(Modifier.height(Spacing.md))
                Text(
                    text = stringResource(R.string.sensor_virtual_preview_layers_title),
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(Modifier.height(Spacing.sm))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    LayerChip(
                        label = stringResource(R.string.sensor_virtual_preview_layer_coverage),
                        selected = showCoverage,
                        onClick = { showCoverage = !showCoverage },
                        testTag = "virtual_layer_coverage"
                    )
                    LayerChip(
                        label = stringResource(R.string.sensor_virtual_preview_layer_sensors),
                        selected = showSensors,
                        onClick = { showSensors = !showSensors },
                        testTag = "virtual_layer_sensors"
                    )
                    if (hasRecommendations) {
                        LayerChip(
                            label = stringResource(R.string.sensor_virtual_preview_layer_recommendations),
                            selected = showRecommendations,
                            onClick = { showRecommendations = !showRecommendations },
                            testTag = "virtual_layer_recommendations"
                        )
                    }
                }
            }

            Spacer(Modifier.height(Spacing.section))
            InfoCard(modifier = Modifier.testTag("virtual_preview_canvas_card")) {
                Text(
                    text = stringResource(R.string.sensor_virtual_preview_canvas_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() }
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = stringResource(R.string.sensor_virtual_preview_canvas_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(Spacing.md))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(480.dp)
                        .testTag("virtual_preview_canvas_host")
                ) {
                    VirtualGreenhouseRenderer(
                        snapshot = snapshot,
                        showCoverage = showCoverage,
                        showSensors = showSensors,
                        showRecommendations = showRecommendations,
                        camera = camera,
                        onCameraChange = { camera = it },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(Modifier.height(Spacing.md))
                SecondaryActionButton(
                    text = stringResource(R.string.sensor_virtual_preview_reset_view),
                    onClick = { camera = defaultOrbitForSnapshot(snapshot) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("virtual_preview_reset_view")
                )
            }

            Spacer(Modifier.height(Spacing.section))
            VirtualPreviewLegend()
            Spacer(Modifier.height(Spacing.section))
            if (onBack != null) {
                PrimaryActionButton(
                    text = stringResource(R.string.sensor_virtual_preview_back),
                    onClick = onBack,
                    modifier = Modifier.testTag("virtual_preview_back")
                )
            }
            Spacer(
                Modifier.height(Spacing.xxxl + Spacing.xxl + Spacing.navClearance)
            )
        }
    }
}

@Composable
private fun PreviewMetricRow(
    label: String,
    value: String,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun VirtualPreviewLegend() {
    InfoCard(modifier = Modifier.testTag("virtual_preview_legend_card")) {
        CoverageStateLegend(testTagPrefix = "virtual_preview_legend")
        Spacer(Modifier.height(Spacing.md))
        Text(
            text = stringResource(R.string.sensor_virtual_preview_legend_sensors_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Spacing.xs))
        LegendLine(
            stringResource(R.string.sensor_virtual_preview_legend_sensor_t),
            ForestEmerald,
            "virtual_preview_legend_sensor_t"
        )
        LegendLine(
            stringResource(R.string.sensor_virtual_preview_legend_sensor_h),
            ForestEmerald,
            "virtual_preview_legend_sensor_h"
        )
        LegendLine(
            stringResource(R.string.sensor_virtual_preview_legend_sensor_sm),
            ForestEmerald,
            "virtual_preview_legend_sensor_sm"
        )
        LegendLine(
            stringResource(R.string.sensor_virtual_preview_legend_sensor_l),
            ForestEmerald,
            "virtual_preview_legend_sensor_l"
        )
        Spacer(Modifier.height(Spacing.md))
        Text(
            text = stringResource(R.string.sensor_virtual_preview_legend_rec_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Spacing.xs))
        LegendLine(
            stringResource(R.string.sensor_virtual_preview_legend_rec),
            ClimateTeal,
            "virtual_preview_legend_rec"
        )
    }
}

@Composable
private fun LegendLine(text: String, color: androidx.compose.ui.graphics.Color, testTag: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .testTag(testTag)
    )
}

@Composable
private fun PreviewTypeFilter(
    selected: SensorType?,
    onSelect: (SensorType?) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            LayerChip(
                label = stringResource(R.string.sensor_coverage_filter_all),
                selected = selected == null,
                onClick = { onSelect(null) },
                testTag = "virtual_filter_all"
            )
            LayerChip(
                label = stringResource(R.string.sensor_type_name_temperature),
                selected = selected == SensorType.TEMPERATURE,
                onClick = { onSelect(SensorType.TEMPERATURE) },
                testTag = "virtual_filter_TEMPERATURE"
            )
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            LayerChip(
                label = stringResource(R.string.sensor_type_name_humidity),
                selected = selected == SensorType.HUMIDITY,
                onClick = { onSelect(SensorType.HUMIDITY) },
                testTag = "virtual_filter_HUMIDITY"
            )
            LayerChip(
                label = stringResource(R.string.sensor_type_name_soil_moisture),
                selected = selected == SensorType.SOIL_MOISTURE,
                onClick = { onSelect(SensorType.SOIL_MOISTURE) },
                testTag = "virtual_filter_SOIL_MOISTURE"
            )
        }
        LayerChip(
            label = stringResource(R.string.sensor_type_name_light_intensity),
            selected = selected == SensorType.LIGHT_INTENSITY,
            onClick = { onSelect(SensorType.LIGHT_INTENSITY) },
            testTag = "virtual_filter_LIGHT_INTENSITY"
        )
    }
}

@Composable
private fun LayerChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1) },
        shape = RoundedCornerShape(8.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
            selectedLabelColor = MaterialTheme.colorScheme.primary
        ),
        modifier = Modifier
            .widthIn(min = 64.dp)
            .testTag(testTag)
    )
}

/** Test helper: reset camera equals default orbit for snapshot dimensions. */
fun resetOrbitCamera(snapshot: com.greenhands.app.sensor.ar.ArVisualizationSnapshot): OrbitCameraState =
    defaultOrbitForSnapshot(snapshot)
