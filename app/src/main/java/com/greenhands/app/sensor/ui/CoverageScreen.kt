package com.greenhands.app.sensor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.greenhands.app.R
import com.greenhands.app.sensor.model.SensorStatus
import com.greenhands.app.sensor.model.SensorType
import com.greenhands.app.ui.components.DemoNotice
import com.greenhands.app.ui.components.InfoCard
import com.greenhands.app.ui.components.PrimaryActionButton
import com.greenhands.app.ui.components.ScreenScaffold
import com.greenhands.app.ui.components.ScrollScreen
import com.greenhands.app.ui.components.SecondaryActionButton
import com.greenhands.app.ui.components.StatusChip
import com.greenhands.app.ui.theme.Spacing
import java.util.Locale

@Composable
fun CoverageScreen(
    ui: SensorPlacementUiState,
    onContinueToOptimize: (() -> Unit)? = null,
    onOpenVirtualPreview: (() -> Unit)? = null,
    onOpenRealAr: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null
) {
    val monitoring = ui.coverage
    val activeCount = ui.sensors.count { it.status == SensorStatus.ACTIVE }
    val inactiveCount = ui.sensors.size - activeCount
    var mapTypeFilter by remember { mutableStateOf<SensorType?>(null) }
    val mapCoverage = displayedMapCoverage(ui.coverageByType, mapTypeFilter)
    val ringSensors = displayedRingSensors(ui.sensors, mapTypeFilter)
    val mapSensors = displayedMapSensors(ui.sensors, mapTypeFilter)

    ScreenScaffold(
        title = stringResource(R.string.sensor_coverage_title),
        onBack = onBack
    ) { padding ->
        ScrollScreen(Modifier.fillMaxSize().padding(padding).testTag("view_coverage")) {
            SensorWorkflowHeader(
                subtitle = stringResource(R.string.sensor_coverage_subtitle),
                activeStepIndex = 3
            )
            Spacer(Modifier.height(Spacing.md))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                StatusChip(
                    text = stringResource(
                        R.string.sensor_coverage_total_chip,
                        formatPercent(monitoring.overallCoveragePercent)
                    ),
                    modifier = Modifier.testTag("coverage_total_chip")
                )
                StatusChip(
                    text = stringResource(R.string.sensor_coverage_blind_chip, monitoring.blindSpotCells),
                    warning = monitoring.blindSpotCells > 0,
                    modifier = Modifier.testTag("coverage_blind_chip")
                )
            }
            Spacer(Modifier.height(Spacing.md))
            DemoNotice(
                text = stringResource(R.string.sensor_coverage_notice),
                modifier = Modifier.testTag("coverage_notice")
            )
            Spacer(Modifier.height(Spacing.section))
            InfoCard(modifier = Modifier.testTag("coverage_legend_card")) {
                CoverageStateLegend(testTagPrefix = "coverage_legend")
                Spacer(Modifier.height(Spacing.md))
                SensorTypeLegend(testTagPrefix = "coverage_type_legend")
            }
            Spacer(Modifier.height(Spacing.section))
            InfoCard(modifier = Modifier.testTag("coverage_grid_card")) {
                Text(
                    text = stringResource(R.string.sensor_coverage_grid_title),
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
                Spacer(Modifier.height(Spacing.md))
                CoverageMapTypeFilter(
                    selectedType = mapTypeFilter,
                    onSelect = { mapTypeFilter = it },
                    testTagPrefix = "coverage_filter"
                )
                Spacer(Modifier.height(Spacing.md))
                GreenhouseCoverageMap(
                    greenhouse = ui.greenhouse,
                    coverage = mapCoverage,
                    sensors = mapSensors,
                    ringSensors = ringSensors,
                    selectedSensorId = ui.selectedSensorId,
                    radiusEmphasis = 1.15f,
                    markerTestTagPrefix = "coverage_marker",
                    onTapCell = null
                )
            }
            Spacer(Modifier.height(Spacing.section))
            InfoCard(modifier = Modifier.testTag("coverage_by_type_card")) {
                CoverageByTypeSection(
                    coverageByType = ui.coverageByType,
                    testTagPrefix = "coverage_by_type"
                )
            }
            Spacer(Modifier.height(Spacing.section))
            InfoCard(modifier = Modifier.testTag("coverage_stats_card")) {
                Text(
                    text = stringResource(R.string.sensor_coverage_stats_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() }
                )
                Spacer(Modifier.height(Spacing.md))
                CoverageStatRow(
                    label = stringResource(R.string.sensor_coverage_stat_total),
                    value = stringResource(
                        R.string.sensor_coverage_percent_value,
                        formatPercent(monitoring.overallCoveragePercent)
                    ),
                    testTag = "coverage_stat_total"
                )
                CoverageStatRow(
                    label = stringResource(R.string.sensor_coverage_stat_good),
                    value = stringResource(
                        R.string.sensor_coverage_percent_value,
                        formatPercent(monitoring.goodCoveragePercent)
                    ),
                    testTag = "coverage_stat_good"
                )
                CoverageStatRow(
                    label = stringResource(R.string.sensor_coverage_stat_overlap),
                    value = stringResource(
                        R.string.sensor_coverage_percent_value,
                        formatPercent(0.0)
                    ),
                    testTag = "coverage_stat_overlap"
                )
                CoverageStatRow(
                    label = stringResource(R.string.sensor_coverage_stat_blind),
                    value = stringResource(
                        R.string.sensor_coverage_percent_value,
                        formatPercent(monitoring.blindSpotPercent)
                    ),
                    testTag = "coverage_stat_blind"
                )
            }
            Spacer(Modifier.height(Spacing.section))
            InfoCard(modifier = Modifier.testTag("coverage_sensors_card")) {
                Text(
                    text = stringResource(R.string.sensor_coverage_sensors_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() }
                )
                Spacer(Modifier.height(Spacing.md))
                CoverageStatRow(
                    label = stringResource(R.string.sensor_coverage_sensors_total),
                    value = ui.sensorCount.toString(),
                    testTag = "coverage_sensors_total"
                )
                CoverageStatRow(
                    label = stringResource(R.string.sensor_coverage_sensors_active),
                    value = activeCount.toString(),
                    testTag = "coverage_sensors_active"
                )
                CoverageStatRow(
                    label = stringResource(R.string.sensor_coverage_sensors_inactive),
                    value = inactiveCount.toString(),
                    testTag = "coverage_sensors_inactive"
                )
            }
            Spacer(Modifier.height(Spacing.section))
            InfoCard(modifier = Modifier.testTag("coverage_explain_card")) {
                Text(
                    text = stringResource(R.string.sensor_coverage_explain_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() }
                )
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    text = stringResource(R.string.sensor_coverage_explain_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (onContinueToOptimize != null) {
                Spacer(Modifier.height(Spacing.section))
                PrimaryActionButton(
                    text = stringResource(R.string.sensor_coverage_continue),
                    onClick = onContinueToOptimize,
                    modifier = Modifier.testTag("coverage_continue")
                )
            }
            if (onOpenVirtualPreview != null) {
                Spacer(Modifier.height(Spacing.sm))
                PrimaryActionButton(
                    text = stringResource(R.string.sensor_virtual_preview_open),
                    onClick = onOpenVirtualPreview,
                    modifier = Modifier.testTag("coverage_virtual_preview")
                )
            }
            if (onOpenRealAr != null) {
                Spacer(Modifier.height(Spacing.sm))
                SecondaryActionButton(
                    text = stringResource(R.string.sensor_real_ar_open),
                    onClick = onOpenRealAr,
                    modifier = Modifier.testTag("coverage_real_ar")
                )
            }
            Spacer(Modifier.height(Spacing.section))
        }
    }
}

@Composable
private fun CoverageStatRow(
    label: String,
    value: String,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xxs)
            .testTag(testTag),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(text = value, style = MaterialTheme.typography.titleSmall)
    }
}

private fun formatPercent(value: Double): String =
    String.format(Locale.US, "%.1f", value)
