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
import com.greenhands.app.harvest.domain.PlantHealthStatus
import com.greenhands.app.harvest.domain.SimplePlantHealthDecider
import com.greenhands.app.harvest.integration.HarvestDecisionMakingBridge
import com.greenhands.app.harvest.model.PlantHealthAssessment
import com.greenhands.app.harvest.model.PlantHealthReasons
import com.greenhands.app.ui.components.DemoNotice
import com.greenhands.app.ui.components.ScreenScaffold
import com.greenhands.app.ui.components.ScrollScreen
import com.greenhands.app.ui.components.SectionHeading
import com.greenhands.app.ui.components.StatusChip
import com.greenhands.app.ui.theme.Spacing

@Suppress("UNUSED_PARAMETER")
@Composable
fun PlantHealthResultScreen(
    mockId: String,
    onBack: () -> Unit,
    onViewInAr: (PlantHealthAssessment) -> Unit,
    harvestViewModel: HarvestViewModel,
    onOpenDecisionMaking: () -> Unit = {}
) {
    val measured = HarvestMeasurementStore.lastLeaf
    val storedHealth = HarvestMeasurementStore.lastLeafHealth
    val harvestState by harvestViewModel.state.collectAsState()
    val saveStatus by harvestViewModel.leafSaveStatus.collectAsState()
    val assessment = remember(measured, storedHealth) {
        storedHealth ?: measured?.let { SimplePlantHealthDecider.decide(it) }
            ?: PlantHealthAssessment.scanRequired(measured)
    }
    LaunchedEffect(measured) {
        harvestViewModel.prepareLeafSave(measured)
    }
    val realDecision = !assessment.scanRequired
    val statusColor = plantHealthStatusColor(assessment.status, assessment.scanRequired)

    ScreenScaffold(
        title = stringResource(R.string.harvest_plant_health_title),
        onBack = onBack
    ) { padding ->
        ScrollScreen(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag("harvest_plant_health_result")
        ) {
            SectionHeading(
                title = stringResource(R.string.harvest_plant_health_heading),
                subtitle = if (realDecision) {
                    stringResource(R.string.harvest_plant_health_real_subtitle)
                } else {
                    stringResource(R.string.harvest_plant_health_scan_required_subtitle)
                }
            )
            Spacer(Modifier.height(Spacing.sm))
            if (!realDecision) {
                StatusChip(text = stringResource(R.string.harvest_scan_required_chip), warning = true)
                Spacer(Modifier.height(Spacing.md))
                DemoNotice(stringResource(R.string.harvest_scan_required_notice_leaf))
            } else {
                StatusChip(text = stringResource(R.string.harvest_real_health_chip))
                Spacer(Modifier.height(Spacing.md))
                DemoNotice(stringResource(R.string.harvest_plant_health_calibration_notice))
            }
            if (harvestState.referenceLoadFailed) {
                Spacer(Modifier.height(Spacing.md))
                DemoNotice(stringResource(R.string.harvest_reference_load_failed))
            }

            Spacer(Modifier.height(Spacing.section))
            Text(
                stringResource(R.string.harvest_plant_health_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(Spacing.md))
            ResultFieldsCard {
                Text(
                    assessment.simpleHealthStatusLabel,
                    style = MaterialTheme.typography.headlineSmall,
                    color = statusColor,
                    modifier = Modifier.testTag("harvest_health_status_value")
                )
                if (realDecision && assessment.status == PlantHealthStatus.UNCERTAIN) {
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        PlantHealthReasons.UNCERTAIN_SCAN_AGAIN,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("harvest_health_summary_value")
                    )
                }
                if (realDecision && assessment.status != PlantHealthStatus.UNCERTAIN) {
                    Spacer(Modifier.height(Spacing.md))
                    ResultField(
                        if (assessment.status == PlantHealthStatus.UNHEALTHY) {
                            stringResource(R.string.harvest_field_detected_visible_issue)
                        } else {
                            stringResource(R.string.harvest_field_detected_issue)
                        },
                        assessment.visibleIssue.ifBlank { PlantHealthReasons.VISIBLE_NONE },
                        modifier = Modifier.testTag("harvest_detected_issue"),
                        onClick = if (HarvestDecisionMakingBridge.canOpenFrom(assessment)) {
                            {
                                openDecisionMakingFromLeaf(
                                    assessment = assessment,
                                    cropType = harvestState.cropType,
                                    onOpenDecisionMaking = onOpenDecisionMaking
                                )
                            }
                        } else {
                            null
                        }
                    )
                    Spacer(Modifier.height(Spacing.md))
                    ResultField(
                        stringResource(R.string.harvest_field_possible_disease),
                        assessment.possibleDisease.ifBlank { PlantHealthReasons.POSSIBLE_NONE },
                    )
                    if (assessment.showsNamedDisease) {
                        Spacer(Modifier.height(Spacing.md))
                        ResultField(
                            stringResource(R.string.harvest_field_confidence),
                            assessment.confidenceLabel
                        )
                    }
                }
            }

            if (realDecision && assessment.status != PlantHealthStatus.UNCERTAIN) {
                Spacer(Modifier.height(Spacing.md))
                DemoNotice(
                    PlantHealthReasons.DISEASE_DISCLAIMER,
                    modifier = Modifier.testTag("harvest_disease_disclaimer")
                )
            }

            Spacer(Modifier.height(Spacing.section))
            HarvestSaveSection(
                canSave = realDecision,
                saveStatus = saveStatus,
                onSave = { harvestViewModel.saveLeafScan(assessment) }
            )
        }
    }
}
