package com.greenhands.app.harvest.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.greenhands.app.R
import com.greenhands.app.harvest.domain.HarvestScanTimestamps
import com.greenhands.app.harvest.domain.MaturityReferenceKind
import com.greenhands.app.harvest.domain.PlantingDates
import com.greenhands.app.harvest.model.ScanRecord
import com.greenhands.app.harvest.model.ScanType
import com.greenhands.app.harvest.model.hsvPercentLabel
import com.greenhands.app.ui.components.EmptyStateText
import com.greenhands.app.ui.components.ScreenScaffold
import com.greenhands.app.ui.components.ScrollScreen
import com.greenhands.app.ui.components.SecondaryActionButton
import com.greenhands.app.ui.components.SectionHeading
import com.greenhands.app.ui.components.StatusChip
import com.greenhands.app.ui.theme.Spacing

@Composable
fun HarvestHistoryDetailScreen(
    recordId: String,
    onBack: () -> Unit,
    harvestViewModel: HarvestViewModel
) {
    val state by harvestViewModel.state.collectAsState()
    val record = state.recentScans.firstOrNull { it.id == recordId }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    ScreenScaffold(
        title = stringResource(R.string.harvest_history_detail_title),
        onBack = onBack
    ) { padding ->
        ScrollScreen(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag("harvest_history_detail")
        ) {
            if (record == null) {
                EmptyStateText(stringResource(R.string.harvest_history_record_missing))
                return@ScrollScreen
            }
            SectionHeading(
                title = record.listHeadline,
                subtitle = HarvestScanTimestamps.formatList(record.scannedAtEpochMillis)
            )
            Spacer(Modifier.height(Spacing.sm))
            StatusChip(
                text = if (record.scanType == ScanType.FRUIT_SCAN) {
                    stringResource(R.string.harvest_history_type_fruit)
                } else {
                    stringResource(R.string.harvest_history_type_leaf)
                }
            )
            Spacer(Modifier.height(Spacing.md))
            SavedEnvironmentSnapshotCard(record)
            Spacer(Modifier.height(Spacing.section))
            when (record.scanType) {
                ScanType.FRUIT_SCAN -> FruitSnapshotCard(record)
                ScanType.LEAF_SCAN -> LeafSnapshotCard(record)
            }
            Spacer(Modifier.height(Spacing.section))
            SecondaryActionButton(
                text = stringResource(R.string.harvest_history_delete),
                onClick = { confirmDelete = true },
                modifier = Modifier.testTag("harvest_history_delete")
            )
        }
    }

    if (confirmDelete && record != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.harvest_history_delete_title)) },
            text = { Text(stringResource(R.string.harvest_history_delete_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        harvestViewModel.deleteScan(record.id)
                        confirmDelete = false
                        onBack()
                    },
                    modifier = Modifier.testTag("harvest_history_delete_confirm")
                ) {
                    Text(stringResource(R.string.harvest_history_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.harvest_history_delete_cancel))
                }
            }
        )
    }
}

@Composable
private fun FruitSnapshotCard(record: ScanRecord) {
    ResultFieldsCard {
        ResultField(stringResource(R.string.harvest_field_crop), record.cropType)
        Spacer(Modifier.height(Spacing.md))
        ResultField(
            stringResource(R.string.harvest_variety_label),
            record.variety ?: stringResource(R.string.harvest_variety_not_selected)
        )
        Spacer(Modifier.height(Spacing.md))
        ResultField(
            stringResource(R.string.harvest_planting_date_label),
            record.transplantDateUtcMillis?.let { PlantingDates.formatDisplay(it) }
                ?: stringResource(R.string.harvest_planting_date_not_selected)
        )
        Spacer(Modifier.height(Spacing.md))
        ResultField(
            stringResource(R.string.harvest_days_since_planting_label),
            record.daysSinceTransplant?.toString() ?: "—"
        )
        Spacer(Modifier.height(Spacing.md))
        ResultField(
            stringResource(R.string.harvest_maturity_reference_label),
            storedMaturityReferenceLabel(record.maturityReferenceKind)
        )
        Spacer(Modifier.height(Spacing.md))
        ResultField(
            stringResource(R.string.harvest_expected_maturity_range_label),
            storedMaturityRange(record.maturityMinDays, record.maturityMaxDays)
        )
        Spacer(Modifier.height(Spacing.md))
        ResultField(
            stringResource(R.string.harvest_maturity_status_label),
            storedMaturityStatusLabel(record.maturityStatus)
        )
        Spacer(Modifier.height(Spacing.md))
        ResultField(
            stringResource(R.string.harvest_estimated_days_remaining_label),
            record.estimatedDaysRemaining?.let {
                stringResource(R.string.harvest_estimated_days_remaining_value, it)
            } ?: "—"
        )
        Spacer(Modifier.height(Spacing.md))
        ResultField(
            stringResource(R.string.harvest_field_harvest_decision),
            record.listStatus
        )
        Spacer(Modifier.height(Spacing.md))
        ResultField(
            stringResource(R.string.harvest_field_ripeness_evidence),
            record.ripenessEvidence ?: "—"
        )
        Spacer(Modifier.height(Spacing.md))
        ResultField(
            stringResource(R.string.harvest_field_quality_status),
            record.qualityStatus ?: "—"
        )
        Spacer(Modifier.height(Spacing.md))
        ResultField(
            stringResource(R.string.harvest_field_hsv_mean),
            storedHsvMean(record)
        )
        Spacer(Modifier.height(Spacing.md))
        ResultField(
            stringResource(R.string.harvest_field_green_pct),
            record.greenPercent?.let { hsvPercentLabel(it) } ?: "—"
        )
        Spacer(Modifier.height(Spacing.md))
        ResultField(
            stringResource(R.string.harvest_field_yellow_pct),
            record.yellowPercent?.let { hsvPercentLabel(it) } ?: "—"
        )
        Spacer(Modifier.height(Spacing.md))
        ResultField(
            stringResource(R.string.harvest_field_red_pct),
            record.redPercent?.let { hsvPercentLabel(it) } ?: "—"
        )
        Spacer(Modifier.height(Spacing.md))
        ResultField(
            stringResource(R.string.harvest_field_brown_pct),
            record.brownDarkPercent?.let { hsvPercentLabel(it) } ?: "—"
        )
        if (record.decisionReasons.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.md))
            Text(
                stringResource(R.string.harvest_field_reasons),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(Spacing.xxs))
            record.decisionReasons.forEach { reason ->
                Text(reason, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(Spacing.sm))
            }
        }
    }
}

@Composable
private fun LeafSnapshotCard(record: ScanRecord) {
    ResultFieldsCard {
        ResultField(stringResource(R.string.harvest_field_crop), record.cropType)
        Spacer(Modifier.height(Spacing.md))
        ResultField(
            stringResource(R.string.harvest_variety_label),
            record.variety ?: stringResource(R.string.harvest_variety_not_selected)
        )
        Spacer(Modifier.height(Spacing.md))
        ResultField(
            stringResource(R.string.harvest_planting_date_label),
            record.transplantDateUtcMillis?.let { PlantingDates.formatDisplay(it) }
                ?: stringResource(R.string.harvest_planting_date_not_selected)
        )
        Spacer(Modifier.height(Spacing.md))
        ResultField(
            stringResource(R.string.harvest_field_health_status),
            record.plantHealthStatus ?: "—"
        )
        Spacer(Modifier.height(Spacing.md))
        ResultField(
            stringResource(R.string.harvest_field_disease),
            record.possibleDisease ?: "—"
        )
        Spacer(Modifier.height(Spacing.md))
        ResultField(
            stringResource(R.string.harvest_field_confidence),
            record.matchingConfidencePercent?.let { "$it%" } ?: "—"
        )
        Spacer(Modifier.height(Spacing.md))
        ResultField(
            stringResource(R.string.harvest_field_symptoms),
            if (record.matchedSymptoms.isEmpty()) {
                stringResource(R.string.harvest_symptoms_none)
            } else {
                record.matchedSymptoms.joinToString("; ")
            }
        )
        Spacer(Modifier.height(Spacing.md))
        ResultField(
            stringResource(R.string.harvest_field_recommendation),
            record.recommendation ?: "—"
        )
        Spacer(Modifier.height(Spacing.md))
        ResultField(
            stringResource(R.string.harvest_field_hsv_mean),
            storedHsvMean(record)
        )
        Spacer(Modifier.height(Spacing.md))
        ResultField(
            stringResource(R.string.harvest_field_green_pct),
            record.greenPercent?.let { hsvPercentLabel(it) } ?: "—"
        )
        Spacer(Modifier.height(Spacing.md))
        ResultField(
            stringResource(R.string.harvest_field_yellow_pct),
            record.yellowPercent?.let { hsvPercentLabel(it) } ?: "—"
        )
        Spacer(Modifier.height(Spacing.md))
        ResultField(
            stringResource(R.string.harvest_field_brown_pct),
            record.brownDarkPercent?.let { hsvPercentLabel(it) } ?: "—"
        )
        Spacer(Modifier.height(Spacing.md))
        ResultField(
            stringResource(R.string.harvest_field_white_pct),
            record.whitePalePercent?.let { hsvPercentLabel(it) } ?: "—"
        )
        Spacer(Modifier.height(Spacing.md))
        ResultField(
            stringResource(R.string.harvest_field_discolored_pct),
            record.discoloredPercent?.let { hsvPercentLabel(it) } ?: "—"
        )
    }
}

@Composable
private fun storedMaturityReferenceLabel(kind: String?): String {
    val parsed = kind?.let { runCatching { MaturityReferenceKind.valueOf(it) }.getOrNull() }
        ?: return "—"
    return when (parsed) {
        MaturityReferenceKind.VARIETY_SPECIFIC ->
            stringResource(R.string.harvest_maturity_reference_variety)
        MaturityReferenceKind.GENERAL_TOMATO ->
            stringResource(R.string.harvest_maturity_reference_general)
        MaturityReferenceKind.NONE ->
            stringResource(R.string.harvest_maturity_data_unavailable)
    }
}

private fun storedMaturityRange(minDays: Int?, maxDays: Int?): String {
    if (minDays == null || maxDays == null) return "—"
    return "$minDays–$maxDays days after transplant"
}

private fun storedHsvMean(record: ScanRecord): String {
    val h = record.hueMean
    val s = record.saturationMean
    val v = record.valueMean
    if (h == null || s == null || v == null) return "—"
    return String.format(java.util.Locale.US, "H %.0f°  S %.0f%%  V %.0f%%", h, s * 100f, v * 100f)
}
