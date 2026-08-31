package com.greenhands.app.harvest.ui

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.greenhands.app.R
import com.greenhands.app.harvest.data.HarvestMeasurementStore
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
    val harvestState by harvestViewModel.state.collectAsState()
    val saveStatus by harvestViewModel.fruitSaveStatus.collectAsState()
    val decision = remember(measured, harvestState.maturity, storedDecision) {
        storedDecision ?: engine.decideTomato(measured, harvestState.maturity)
    }
    LaunchedEffect(measured) {
        harvestViewModel.prepareFruitSave(measured)
    }
    val realDecision = !decision.scanRequired
    val decisionColor = harvestDecisionColor(decision.decision, decision.scanRequired)

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
                    decision.displayLabel,
                    style = MaterialTheme.typography.headlineSmall,
                    color = decisionColor,
                    modifier = Modifier.testTag("harvest_decision_value")
                )
                Spacer(Modifier.height(Spacing.md))
                ResultField(
                    stringResource(R.string.harvest_field_maturity_reason),
                    decision.maturityReasonLabel
                )
                Spacer(Modifier.height(Spacing.md))
                ResultField(
                    stringResource(R.string.harvest_field_ripeness_evidence),
                    decision.ripeness.label
                )
                Spacer(Modifier.height(Spacing.md))
                ResultField(
                    stringResource(R.string.harvest_field_quality_status),
                    decision.quality.label
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
                decision.reasons.forEachIndexed { index, reason ->
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
                onSave = { harvestViewModel.saveFruitScan(decision) }
            )
        }
    }
}
