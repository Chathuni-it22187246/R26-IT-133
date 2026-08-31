package com.greenhands.app.sensor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.greenhands.app.R
import com.greenhands.app.sensor.domain.OptimizationPreview
import com.greenhands.app.sensor.model.OptimizationApplySummary
import com.greenhands.app.sensor.model.OptimizationCountCandidate
import com.greenhands.app.sensor.model.OptimizationEvaluation
import com.greenhands.app.sensor.model.RecommendedPosition
import com.greenhands.app.sensor.model.SensorType
import com.greenhands.app.ui.components.DemoNotice
import com.greenhands.app.ui.components.InfoCard
import com.greenhands.app.ui.components.PrimaryActionButton
import com.greenhands.app.ui.components.ScreenScaffold
import com.greenhands.app.ui.components.ScrollScreen
import com.greenhands.app.ui.components.SecondaryActionButton
import com.greenhands.app.ui.components.StatusChip
import com.greenhands.app.ui.theme.ClimateTeal
import com.greenhands.app.ui.theme.Spacing
import java.util.Locale

@Composable
fun OptimizePlacementScreen(
    ui: SensorPlacementUiState,
    onSelectType: (SensorType) -> Unit,
    onAnalyze: () -> Unit,
    onSelectAlternative: (Int) -> Unit,
    onTogglePosition: (Double, Double) -> Unit,
    onApply: () -> Unit,
    onKeepCurrent: () -> Unit,
    onDismissApplySummary: () -> Unit,
    onOpenVirtualPreview: (() -> Unit)? = null,
    onOpenRealAr: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null
) {
    val typeCoverage = ui.coverageByType.forType(ui.optimizationSensorType)
    val evaluation = ui.optimizationEvaluation
    val result = ui.optimizationResult
    val applySummary = ui.lastOptimizationApply
    val selectedAlternative = ui.selectedOptimizationAlternative
    val selectedRecommendations = result?.recommendedPositions.orEmpty().filter { pos ->
        optimizationPositionKey(pos.x, pos.y) in ui.selectedOptimizationPositions
    }
    val predictedForSelected = result?.let {
        OptimizationPreview.predictForSelected(
            greenhouse = ui.greenhouse,
            sensors = ui.sensors,
            sensorType = ui.optimizationSensorType,
            selected = selectedRecommendations
        )
    }
    val mapSensors = ui.sensors.filter { it.type == ui.optimizationSensorType }
    val recommendationMarkers = result?.recommendedPositions.orEmpty().map { pos ->
        OptimizationMapMarker(
            x = pos.x,
            y = pos.y,
            label = "P${pos.rank}",
            selected = optimizationPositionKey(pos.x, pos.y) in ui.selectedOptimizationPositions
        )
    }

    ScreenScaffold(
        title = stringResource(R.string.sensor_optimize_title),
        onBack = onBack
    ) { padding ->
        ScrollScreen(Modifier.fillMaxSize().padding(padding).testTag("optimize_placement")) {
            SensorWorkflowHeader(
                subtitle = stringResource(R.string.sensor_optimize_subtitle),
                activeStepIndex = 4
            )
            Spacer(Modifier.height(Spacing.md))
            DemoNotice(
                text = stringResource(R.string.sensor_optimize_notice),
                modifier = Modifier.testTag("optimize_notice")
            )
            Spacer(Modifier.height(Spacing.section))

            InfoCard(modifier = Modifier.testTag("optimize_current_config_card")) {
                Text(
                    text = stringResource(R.string.sensor_optimize_current_config_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() }
                )
                Spacer(Modifier.height(Spacing.md))
                Text(
                    text = stringResource(R.string.sensor_optimize_current_sensors, ui.sensorCount),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("optimize_current_sensors")
                )
                Text(
                    text = stringResource(
                        R.string.sensor_optimize_current_coverage,
                        optimizeTypeDisplayName(ui.optimizationSensorType),
                        formatOptimizePercent(typeCoverage.overallCoveragePercent)
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("optimize_current_coverage")
                )
                Text(
                    text = stringResource(
                        R.string.sensor_optimize_current_blind_percent,
                        formatOptimizePercent(typeCoverage.blindSpotPercent)
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("optimize_current_blind_percent")
                )
                Text(
                    text = stringResource(
                        R.string.sensor_optimize_current_overlap,
                        typeCoverage.overlapCells
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("optimize_current_overlap")
                )
            }

            Spacer(Modifier.height(Spacing.section))

            InfoCard(modifier = Modifier.testTag("optimize_controls_card")) {
                Text(
                    text = stringResource(R.string.sensor_optimize_section_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() }
                )
                Spacer(Modifier.height(Spacing.md))
                Text(
                    text = stringResource(R.string.sensor_optimize_type_label),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(Spacing.sm))
                OptimizeTypeSelector(
                    selected = ui.optimizationSensorType,
                    onSelect = onSelectType
                )
                Spacer(Modifier.height(Spacing.md))
                Text(
                    text = stringResource(R.string.sensor_optimize_auto_count_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("optimize_auto_count_hint")
                )
                Spacer(Modifier.height(Spacing.md))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    StatusChip(
                        text = stringResource(
                            R.string.sensor_optimize_current_coverage_chip,
                            formatOptimizePercent(typeCoverage.overallCoveragePercent)
                        ),
                        modifier = Modifier.testTag("optimize_current_coverage_chip")
                    )
                    StatusChip(
                        text = stringResource(
                            R.string.sensor_optimize_current_blind_chip,
                            typeCoverage.blindSpotCells
                        ),
                        warning = typeCoverage.blindSpotCells > 0,
                        modifier = Modifier.testTag("optimize_current_blind_chip")
                    )
                }
                Spacer(Modifier.height(Spacing.md))
                PrimaryActionButton(
                    text = if (ui.isOptimizing) {
                        stringResource(R.string.sensor_optimize_analyzing)
                    } else {
                        stringResource(R.string.sensor_optimize_analyze_optimal)
                    },
                    onClick = onAnalyze,
                    enabled = !ui.isOptimizing,
                    modifier = Modifier.testTag("optimize_analyze")
                )
            }

            Spacer(Modifier.height(Spacing.section))
            InfoCard(modifier = Modifier.testTag("optimize_map_card")) {
                Text(
                    text = stringResource(R.string.sensor_optimize_map_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() }
                )
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    text = stringResource(R.string.sensor_optimize_map_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(Spacing.md))
                CoverageStateLegend(testTagPrefix = "optimize_legend")
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = stringResource(R.string.sensor_optimize_legend_recommended),
                    style = MaterialTheme.typography.bodySmall,
                    color = ClimateTeal
                )
                Spacer(Modifier.height(Spacing.md))
                GreenhouseCoverageMap(
                    greenhouse = ui.greenhouse,
                    coverage = typeCoverage,
                    sensors = mapSensors,
                    ringSensors = mapSensors.filter {
                        it.status == com.greenhands.app.sensor.model.SensorStatus.ACTIVE
                    },
                    selectedSensorId = ui.selectedSensorId,
                    radiusEmphasis = 1.1f,
                    markerTestTagPrefix = "optimize_sensor",
                    recommendationMarkers = recommendationMarkers,
                    onTapCell = null
                )
            }

            if (evaluation != null && result == null) {
                Spacer(Modifier.height(Spacing.section))
                InfoCard(modifier = Modifier.testTag("optimize_no_improvement_card")) {
                    Text(
                        text = stringResource(R.string.sensor_optimize_no_improvement),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (evaluation != null && evaluation.candidates.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.section))
                OptimizationEvaluationCard(
                    evaluation = evaluation,
                    selectedAlternative = selectedAlternative,
                    onSelectAlternative = onSelectAlternative
                )
            }

            if (result != null && predictedForSelected != null) {
                Spacer(Modifier.height(Spacing.section))
                InfoCard(modifier = Modifier.testTag("optimize_summary_card")) {
                    Text(
                        text = stringResource(R.string.sensor_optimize_minimum_config_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() }
                    )
                    if (selectedAlternative != null &&
                        selectedAlternative != evaluation?.recommendedAdditionalCount
                    ) {
                        Spacer(Modifier.height(Spacing.xs))
                        Text(
                            text = stringResource(R.string.sensor_optimize_alternative_selected),
                            style = MaterialTheme.typography.labelMedium,
                            color = ClimateTeal,
                            modifier = Modifier.testTag("optimize_alternative_selected")
                        )
                    }
                    Spacer(Modifier.height(Spacing.md))
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text(
                            text = stringResource(
                                R.string.sensor_optimize_recommended_additional,
                                selectedRecommendations.size
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.testTag("optimize_recommended_additional")
                        )
                        Text(
                            text = stringResource(
                                R.string.sensor_optimize_recommended_after_sensors,
                                ui.sensorCount + selectedRecommendations.size
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.testTag("optimize_recommended_after_sensors")
                        )
                        Text(
                            text = stringResource(
                                R.string.sensor_optimize_recommended_coverage,
                                formatOptimizePercent(predictedForSelected.overallCoveragePercent)
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.testTag("optimize_recommended_coverage")
                        )
                        Text(
                            text = stringResource(
                                R.string.sensor_optimize_recommended_blind,
                                formatOptimizePercent(predictedForSelected.blindSpotPercent)
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.testTag("optimize_recommended_blind")
                        )
                        Text(
                            text = stringResource(
                                R.string.sensor_optimize_recommended_improvement,
                                formatOptimizePercent(
                                    predictedForSelected.overallCoveragePercent - result.beforeCoverage
                                )
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = ClimateTeal,
                            modifier = Modifier.testTag("optimize_recommended_improvement")
                        )
                    }
                    Spacer(Modifier.height(Spacing.sm))
                    OptimizeMetricRow(
                        label = stringResource(R.string.sensor_optimize_metric_overlap),
                        value = stringResource(
                            R.string.sensor_optimize_metric_overlap_value,
                            result.beforeOverlap,
                            predictedForSelected.overlapCells
                        ),
                        testTag = "optimize_metric_overlap"
                    )
                }

                Spacer(Modifier.height(Spacing.section))
                InfoCard(modifier = Modifier.testTag("optimize_recommendations_card")) {
                    Text(
                        text = stringResource(R.string.sensor_optimize_recommendations_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() }
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    if (result.recommendedPositions.isEmpty()) {
                        Text(
                            text = stringResource(R.string.sensor_optimize_recommendations_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        result.recommendedPositions.forEach { pos ->
                            RecommendationRow(
                                position = pos,
                                selected = optimizationPositionKey(pos.x, pos.y) in
                                    ui.selectedOptimizationPositions,
                                onToggle = { onTogglePosition(pos.x, pos.y) }
                            )
                            Spacer(Modifier.height(Spacing.sm))
                        }
                    }
                }

                Spacer(Modifier.height(Spacing.section))
                PrimaryActionButton(
                    text = stringResource(R.string.sensor_optimize_apply_configuration),
                    onClick = onApply,
                    enabled = ui.selectedOptimizationPositions.isNotEmpty() &&
                        result.recommendedPositions.isNotEmpty(),
                    modifier = Modifier.testTag("optimize_apply")
                )
                Spacer(Modifier.height(Spacing.sm))
                SecondaryActionButton(
                    text = stringResource(R.string.sensor_optimize_keep_current),
                    onClick = onKeepCurrent,
                    modifier = Modifier.testTag("optimize_keep_current")
                )
            }

            if (applySummary != null) {
                Spacer(Modifier.height(Spacing.section))
                OptimizationApplySummaryCard(
                    summary = applySummary,
                    onDismiss = onDismissApplySummary
                )
            }
            if (onOpenVirtualPreview != null) {
                Spacer(Modifier.height(Spacing.section))
                PrimaryActionButton(
                    text = stringResource(R.string.sensor_virtual_preview_open),
                    onClick = onOpenVirtualPreview,
                    modifier = Modifier.testTag("optimize_virtual_preview")
                )
            }
            if (onOpenRealAr != null) {
                Spacer(Modifier.height(Spacing.sm))
                SecondaryActionButton(
                    text = stringResource(R.string.sensor_real_ar_open),
                    onClick = onOpenRealAr,
                    modifier = Modifier.testTag("optimize_real_ar")
                )
            }
            Spacer(Modifier.height(Spacing.section))
        }
    }
}

@Composable
private fun OptimizationEvaluationCard(
    evaluation: OptimizationEvaluation,
    selectedAlternative: Int?,
    onSelectAlternative: (Int) -> Unit
) {
    InfoCard(modifier = Modifier.testTag("optimize_evaluation_card")) {
        Text(
            text = stringResource(R.string.sensor_optimize_comparison_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = selectionReasonText(evaluation),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("optimize_selection_reason")
        )
        Spacer(Modifier.height(Spacing.md))
        evaluation.candidates.forEach { candidate ->
            OptimizationAlternativeRow(
                candidate = candidate,
                selected = candidate.additionalCount == selectedAlternative,
                onSelect = { onSelectAlternative(candidate.additionalCount) }
            )
            Spacer(Modifier.height(Spacing.xs))
        }
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = stringResource(R.string.sensor_optimize_deployment_cost_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("optimize_deployment_hint")
        )
    }
}

@Composable
private fun selectionReasonText(evaluation: OptimizationEvaluation): String = when (
    evaluation.selectionReason
) {
    com.greenhands.app.sensor.model.OptimizationSelectionReason.MINIMUM_COUNT_AT_BEST_COVERAGE ->
        stringResource(R.string.sensor_optimize_selection_minimum_count)
    com.greenhands.app.sensor.model.OptimizationSelectionReason.NO_IMPROVEMENT ->
        stringResource(R.string.sensor_optimize_no_improvement)
    com.greenhands.app.sensor.model.OptimizationSelectionReason.USER_ALTERNATIVE ->
        stringResource(R.string.sensor_optimize_selection_user_alternative)
}

@Composable
private fun OptimizationAlternativeRow(
    candidate: OptimizationCountCandidate,
    selected: Boolean,
    onSelect: () -> Unit
) {
    val result = candidate.result
    val rowLabel = stringResource(
        R.string.sensor_optimize_comparison_row,
        candidate.additionalCount,
        candidate.finalSensorCount,
        formatOptimizePercent(result.predictedCoverage),
        formatOptimizePercent(candidate.predictedBlindSpotPercent),
        result.predictedOverlap,
        formatOptimizePercent(candidate.totalCoverageGain),
        formatOptimizePercent(candidate.marginalCoverageGain)
    )
    val prefix = if (candidate.isRecommended) {
        stringResource(R.string.sensor_optimize_recommended_badge) + " "
    } else {
        ""
    }
    OptimizeChip(
        label = prefix + rowLabel,
        selected = selected,
        onClick = onSelect,
        testTag = "optimize_alt_${candidate.additionalCount}"
    )
}

@Composable
private fun OptimizeTypeSelector(
    selected: SensorType,
    onSelect: (SensorType) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            OptimizeChip(
                label = stringResource(R.string.sensor_type_name_temperature),
                selected = selected == SensorType.TEMPERATURE,
                onClick = { onSelect(SensorType.TEMPERATURE) },
                testTag = "optimize_type_TEMPERATURE"
            )
            OptimizeChip(
                label = stringResource(R.string.sensor_type_name_humidity),
                selected = selected == SensorType.HUMIDITY,
                onClick = { onSelect(SensorType.HUMIDITY) },
                testTag = "optimize_type_HUMIDITY"
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            OptimizeChip(
                label = stringResource(R.string.sensor_type_name_soil_moisture),
                selected = selected == SensorType.SOIL_MOISTURE,
                onClick = { onSelect(SensorType.SOIL_MOISTURE) },
                testTag = "optimize_type_SOIL_MOISTURE"
            )
            OptimizeChip(
                label = stringResource(R.string.sensor_type_name_light_intensity),
                selected = selected == SensorType.LIGHT_INTENSITY,
                onClick = { onSelect(SensorType.LIGHT_INTENSITY) },
                testTag = "optimize_type_LIGHT_INTENSITY"
            )
        }
    }
}

@Composable
private fun OptimizeChip(
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
        modifier = Modifier.testTag(testTag)
    )
}

@Composable
private fun RecommendationRow(
    position: RecommendedPosition,
    selected: Boolean,
    onToggle: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("optimize_rec_${position.rank}")
    ) {
        OptimizeChip(
            label = stringResource(
                R.string.sensor_optimize_rec_toggle,
                position.rank,
                if (selected) stringResource(R.string.sensor_optimize_rec_selected)
                else stringResource(R.string.sensor_optimize_rec_deselected)
            ),
            selected = selected,
            onClick = onToggle,
            testTag = "optimize_rec_toggle_${position.rank}"
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = stringResource(
                R.string.sensor_optimize_rec_position,
                position.rank,
                position.x.toInt(),
                position.y.toInt()
            ),
            style = MaterialTheme.typography.titleSmall
        )
        Text(
            text = stringResource(
                R.string.sensor_optimize_rec_gain,
                formatOptimizePercent(position.coverageImprovement),
                position.blindSpotReduction
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun OptimizeMetricRow(
    label: String,
    value: String,
    testTag: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xxs)
            .testTag(testTag)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(text = value, style = MaterialTheme.typography.titleSmall)
    }
}

private fun formatOptimizePercent(value: Double): String =
    String.format(Locale.US, "%.1f", value)

@Composable
private fun optimizeTypeDisplayName(type: SensorType): String = when (type) {
    SensorType.TEMPERATURE -> stringResource(R.string.sensor_type_name_temperature)
    SensorType.HUMIDITY -> stringResource(R.string.sensor_type_name_humidity)
    SensorType.SOIL_MOISTURE -> stringResource(R.string.sensor_type_name_soil_moisture)
    SensorType.LIGHT_INTENSITY -> stringResource(R.string.sensor_type_name_light_intensity)
}

@Composable
private fun OptimizationApplySummaryCard(
    summary: OptimizationApplySummary,
    onDismiss: () -> Unit
) {
    InfoCard(modifier = Modifier.testTag("optimize_apply_summary_card")) {
        Text(
            text = stringResource(R.string.sensor_optimize_apply_result_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(Modifier.height(Spacing.md))
        Text(
            text = stringResource(R.string.sensor_optimize_apply_before_title),
            style = MaterialTheme.typography.titleSmall
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = stringResource(R.string.sensor_optimize_apply_sensors, summary.beforeSensorCount),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = stringResource(
                R.string.sensor_optimize_apply_coverage,
                formatOptimizePercent(summary.beforeCoveragePercent)
            ),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = stringResource(
                R.string.sensor_optimize_apply_blind,
                formatOptimizePercent(summary.beforeBlindSpotPercent)
            ),
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(Spacing.md))
        Text(
            text = stringResource(R.string.sensor_optimize_apply_after_title),
            style = MaterialTheme.typography.titleSmall
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = stringResource(R.string.sensor_optimize_apply_sensors, summary.afterSensorCount),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = stringResource(
                R.string.sensor_optimize_apply_coverage,
                formatOptimizePercent(summary.afterCoveragePercent)
            ),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = stringResource(
                R.string.sensor_optimize_apply_blind,
                formatOptimizePercent(summary.afterBlindSpotPercent)
            ),
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(Spacing.md))
        Text(
            text = stringResource(R.string.sensor_optimize_apply_improvement_title),
            style = MaterialTheme.typography.titleSmall
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = stringResource(
                R.string.sensor_optimize_apply_coverage_gain,
                formatOptimizePercent(summary.coverageImprovement)
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = ClimateTeal
        )
        Text(
            text = stringResource(
                R.string.sensor_optimize_apply_additional_sensors,
                summary.appliedRecommendationCount
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = ClimateTeal
        )
        Spacer(Modifier.height(Spacing.md))
        SecondaryActionButton(
            text = stringResource(R.string.sensor_optimize_apply_dismiss),
            onClick = onDismiss,
            modifier = Modifier.testTag("optimize_apply_dismiss")
        )
    }
}
