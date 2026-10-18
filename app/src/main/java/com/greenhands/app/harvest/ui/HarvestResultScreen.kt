package com.greenhands.app.harvest.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.greenhands.app.R
import com.greenhands.app.harvest.data.HarvestMeasurementStore
import com.greenhands.app.harvest.detection.ClusterDisplayBox
import com.greenhands.app.harvest.detection.ClusterScanSummary
import com.greenhands.app.harvest.detection.MultiTomatoClusterResult
import com.greenhands.app.harvest.domain.HarvestDecisionEngine
import com.greenhands.app.harvest.model.HarvestDecisionResult
import com.greenhands.app.harvest.model.hsvPercentLabel
import com.greenhands.app.ui.components.DemoNotice
import com.greenhands.app.ui.components.ScreenScaffold
import com.greenhands.app.ui.components.ScrollScreen
import com.greenhands.app.ui.components.SectionHeading
import com.greenhands.app.ui.components.StatusChip
import com.greenhands.app.ui.theme.Spacing

@Suppress("UNUSED_PARAMETER")
@Composable
fun HarvestResultScreen(
    mockId: String,
    onBack: () -> Unit,
    onViewInAr: (HarvestDecisionResult) -> Unit,
    harvestViewModel: HarvestViewModel
) {
    val engine = remember { HarvestDecisionEngine() }
    val measured = HarvestMeasurementStore.lastFruit
    val storedDecision = HarvestMeasurementStore.lastFruitDecision
    val clusterResult = HarvestMeasurementStore.lastClusterResult
    val harvestState by harvestViewModel.state.collectAsState()
    val saveStatus by harvestViewModel.fruitSaveStatus.collectAsState()
    val showCluster = clusterResult?.enabled == true
    val decision = remember(measured, harvestState.maturity, storedDecision, showCluster) {
        if (showCluster) null
        else storedDecision ?: engine.decideTomato(measured, harvestState.maturity)
    }
    LaunchedEffect(measured, showCluster) {
        if (!showCluster) harvestViewModel.prepareFruitSave(measured)
    }
    if (showCluster && clusterResult != null) {
        ClusterHarvestResultScreen(
            result = clusterResult,
            onBack = onBack,
            harvestViewModel = harvestViewModel
        )
        return
    }
    val resolved = decision ?: return
    val realDecision = !resolved.scanRequired
    val decisionColor = harvestDecisionColor(resolved.decision, resolved.scanRequired)

    ScreenScaffold(
        title = stringResource(R.string.harvest_result_title),
        onBack = onBack
    ) { padding ->
        ScrollScreen(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag("harvest_result")
        ) {
            SectionHeading(
                title = stringResource(R.string.harvest_result_heading),
                subtitle = if (realDecision) {
                    stringResource(R.string.harvest_result_real_subtitle)
                } else {
                    stringResource(R.string.harvest_result_scan_required_subtitle)
                }
            )
            Spacer(Modifier.height(Spacing.sm))
            if (!realDecision) {
                StatusChip(text = stringResource(R.string.harvest_scan_required_chip), warning = true)
                Spacer(Modifier.height(Spacing.md))
                DemoNotice(stringResource(R.string.harvest_scan_required_notice))
            } else {
                StatusChip(text = stringResource(R.string.harvest_real_decision_chip))
                Spacer(Modifier.height(Spacing.md))
                DemoNotice(stringResource(R.string.harvest_decision_calibration_notice))
            }

            Spacer(Modifier.height(Spacing.section))
            Text(
                stringResource(R.string.harvest_overlay_harvest_status),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(Spacing.md))
            ResultFieldsCard {
                Text(
                    resolved.displayLabel,
                    style = MaterialTheme.typography.headlineSmall,
                    color = decisionColor,
                    modifier = Modifier.testTag("harvest_decision_value")
                )
                Spacer(Modifier.height(Spacing.md))
                ResultField(
                    stringResource(R.string.harvest_field_maturity_reason),
                    resolved.maturityReasonLabel
                )
                Spacer(Modifier.height(Spacing.md))
                ResultField(
                    stringResource(R.string.harvest_field_ripeness_evidence),
                    resolved.ripeness.label
                )
                Spacer(Modifier.height(Spacing.md))
                ResultField(
                    stringResource(R.string.harvest_field_quality_status),
                    resolved.quality.label
                )
            }

            Spacer(Modifier.height(Spacing.section))
            Text(
                stringResource(R.string.harvest_field_reasons),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(Spacing.md))
            ResultFieldsCard {
                resolved.reasons.forEachIndexed { index, reason ->
                    if (index > 0) Spacer(Modifier.height(Spacing.md))
                    Text(reason, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(Spacing.md))
                Text(
                    harvestState.environmentContext.supportingNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("harvest_env_decision_note")
                )
            }

            Spacer(Modifier.height(Spacing.section))
            HarvestSessionSummaryCard(harvestState)

            Spacer(Modifier.height(Spacing.section))
            Text(
                stringResource(R.string.harvest_measured_section),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(Spacing.sm))
            StatusChip(text = stringResource(R.string.harvest_measured_chip))
            Spacer(Modifier.height(Spacing.md))
            DemoNotice(stringResource(R.string.harvest_measured_notice))
            Spacer(Modifier.height(Spacing.md))
            ResultFieldsCard {
                if (measured == null || !measured.hasSamples) {
                    Text(
                        stringResource(R.string.harvest_measured_missing),
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    ResultField(
                        stringResource(R.string.harvest_field_sampled_pixels),
                        measured.sampledPixelCount.toString()
                    )
                    Spacer(Modifier.height(Spacing.md))
                    ResultField(
                        stringResource(R.string.harvest_field_hsv_mean),
                        String.format(
                            java.util.Locale.US,
                            "H %.0f°  S %.0f%%  V %.0f%%",
                            measured.hueMean,
                            measured.saturationMean * 100f,
                            measured.valueMean * 100f
                        )
                    )
                    Spacer(Modifier.height(Spacing.md))
                    ResultField(stringResource(R.string.harvest_field_green_pct), hsvPercentLabel(measured.greenPercent))
                    Spacer(Modifier.height(Spacing.md))
                    ResultField(stringResource(R.string.harvest_field_yellow_pct), hsvPercentLabel(measured.yellowPercent))
                    Spacer(Modifier.height(Spacing.md))
                    ResultField(stringResource(R.string.harvest_field_red_pct), hsvPercentLabel(measured.redPercent))
                    Spacer(Modifier.height(Spacing.md))
                    ResultField(stringResource(R.string.harvest_field_brown_pct), hsvPercentLabel(measured.brownDarkPercent))
                }
            }

            Spacer(Modifier.height(Spacing.section))
            HarvestEnvironmentCard(harvestState)

            Spacer(Modifier.height(Spacing.section))
            HarvestSaveSection(
                canSave = realDecision,
                saveStatus = saveStatus,
                onSave = { harvestViewModel.saveFruitScan(resolved) }
            )
        }
    }
}

@Composable
private fun ClusterHarvestResultScreen(
    result: MultiTomatoClusterResult,
    onBack: () -> Unit,
    harvestViewModel: HarvestViewModel
) {
    val harvestState by harvestViewModel.state.collectAsState()
    val fruits = result.fruits.sortedBy { fruit ->
        if (fruit.displayId > 0) fruit.displayId else Int.MAX_VALUE
    }
    var highlightedId by remember { mutableStateOf<Int?>(null) }
    ScreenScaffold(
        title = stringResource(R.string.harvest_result_title),
        onBack = onBack
    ) { padding ->
        ScrollScreen(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag("harvest_cluster_result")
        ) {
            SectionHeading(
                title = stringResource(R.string.harvest_cluster_result_heading),
                subtitle = stringResource(R.string.harvest_cluster_status_decided, fruits.size)
            )
            Spacer(Modifier.height(Spacing.sm))
            StatusChip(text = stringResource(R.string.harvest_real_decision_chip))
            Spacer(Modifier.height(Spacing.md))
            DemoNotice(stringResource(R.string.harvest_cluster_disclaimer))
            Spacer(Modifier.height(Spacing.md))
            result.snapshot?.let { snapshot ->
                Text(
                    stringResource(R.string.harvest_cluster_snapshot_heading),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    stringResource(R.string.harvest_cluster_tap_to_highlight),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(Spacing.md))
                ClusterSnapshotPanel(
                    frame = snapshot,
                    boxes = fruits.map { fruit ->
                        ClusterDisplayBox(
                            displayId = fruit.displayId,
                            box = fruit.detection.boundingBox,
                            highlighted = highlightedId != null && fruit.displayId == highlightedId
                        )
                    }
                )
                Spacer(Modifier.height(Spacing.md))
            }
            ResultFieldsCard {
                Text(
                    stringResource(
                        R.string.harvest_cluster_overlay_status,
                        fruits.size
                    ),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.testTag("harvest_cluster_count")
                )
                Spacer(Modifier.height(Spacing.md))
                Text(
                    stringResource(
                        R.string.harvest_cluster_counts,
                        ClusterScanSummary.readyCount(fruits),
                        ClusterScanSummary.notReadyCount(fruits),
                        ClusterScanSummary.uncertainCount(fruits)
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            fruits.forEach { fruit ->
                val id = if (fruit.displayId > 0) fruit.displayId else 0
                Spacer(Modifier.height(Spacing.section))
                Text(
                    stringResource(R.string.harvest_cluster_fruit_heading, id),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { highlightedId = id }
                        .testTag("harvest_cluster_fruit_$id")
                )
                Spacer(Modifier.height(Spacing.md))
                ResultFieldsCard {
                    Text(
                        fruit.harvest.displayLabel,
                        style = MaterialTheme.typography.headlineSmall,
                        color = harvestDecisionColor(
                            fruit.harvest.decision,
                            fruit.harvest.scanRequired
                        ),
                        modifier = Modifier.clickable { highlightedId = id }
                    )
                    Spacer(Modifier.height(Spacing.md))
                    ResultField(
                        stringResource(R.string.harvest_field_ripeness_evidence),
                        fruit.harvest.ripeness.label
                    )
                    Spacer(Modifier.height(Spacing.md))
                    ResultField(
                        stringResource(R.string.harvest_field_maturity_reason),
                        fruit.harvest.maturityReasonLabel
                    )
                    Spacer(Modifier.height(Spacing.md))
                    ResultField(
                        stringResource(R.string.harvest_field_quality_status),
                        fruit.harvest.quality.label
                    )
                }
            }
            Spacer(Modifier.height(Spacing.section))
            HarvestSessionSummaryCard(harvestState)
            Spacer(Modifier.height(Spacing.section))
            HarvestEnvironmentCard(harvestState)
        }
    }
}
