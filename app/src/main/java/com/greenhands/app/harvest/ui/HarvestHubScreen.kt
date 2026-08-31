@file:OptIn(ExperimentalMaterial3Api::class)

package com.greenhands.app.harvest.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.greenhands.app.R
import com.greenhands.app.harvest.domain.PlantingDates
import com.greenhands.app.harvest.model.VarietyReference
import com.greenhands.app.ui.components.EmptyStateText
import com.greenhands.app.ui.components.InfoCard
import com.greenhands.app.ui.components.PrimaryActionButton
import com.greenhands.app.ui.components.ScreenScaffold
import com.greenhands.app.ui.components.ScrollScreen
import com.greenhands.app.ui.components.SecondaryActionButton
import com.greenhands.app.ui.components.SectionHeading
import com.greenhands.app.ui.components.DemoNotice
import com.greenhands.app.ui.components.TextAction
import com.greenhands.app.ui.theme.Spacing
import java.util.Calendar

@Composable
fun HarvestHubScreen(
    onScanFruit: () -> Unit,
    onScanLeaf: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenRecord: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: HarvestViewModel
) {
    val state by viewModel.state.collectAsState()
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showVarietyPicker by rememberSaveable { mutableStateOf(false) }
    var showNeedPlantingDate by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshDaysSincePlanting()
    }

    ScreenScaffold(
        title = stringResource(R.string.harvest_hub_title),
        onBack = onBack
    ) { padding ->
        ScrollScreen(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag("harvest_hub")
        ) {
            SectionHeading(
                title = stringResource(R.string.harvest_hub_heading),
                subtitle = stringResource(R.string.harvest_hub_subtitle)
            )
            if (state.referenceLoadFailed) {
                Spacer(Modifier.height(Spacing.md))
                DemoNotice(stringResource(R.string.harvest_reference_load_failed))
            }
            Spacer(Modifier.height(Spacing.section))

            Text(
                stringResource(R.string.harvest_crop_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(Spacing.md))
            InfoCard(modifier = Modifier.testTag("harvest_crop")) {
                Text(
                    stringResource(R.string.harvest_field_crop),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    state.cropType,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.testTag("harvest_crop_value")
                )
            }

            Spacer(Modifier.height(Spacing.section))
            Text(
                stringResource(R.string.harvest_variety_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(Spacing.md))
            TomatoVarietyCard(
                state = state,
                onOpenPicker = {
                    if (state.tomatoVarieties.isNotEmpty()) {
                        showVarietyPicker = true
                    }
                },
                onClear = { viewModel.clearVariety() }
            )

            Spacer(Modifier.height(Spacing.section))
            Text(
                stringResource(R.string.harvest_planting_date_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(Spacing.md))
            InfoCard(
                modifier = Modifier.testTag("harvest_planting_date")
            ) {
                Text(
                    stringResource(R.string.harvest_planting_date_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(Spacing.xs))
                val selected = state.plantingDateUtcMillis
                if (selected == null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDatePicker = true }
                    ) {
                        Text(
                            stringResource(R.string.harvest_planting_date_not_selected),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.testTag("harvest_planting_date_value")
                        )
                        Spacer(Modifier.height(Spacing.sm))
                        Text(
                            stringResource(R.string.harvest_planting_date_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { showDatePicker = true }
                        ) {
                            Text(
                                PlantingDates.formatDisplay(selected),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.testTag("harvest_planting_date_value")
                            )
                            Spacer(Modifier.height(Spacing.sm))
                            Text(
                                stringResource(
                                    R.string.harvest_days_since_planting,
                                    state.daysSincePlanting ?: 0
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.testTag("harvest_days_since_planting")
                            )
                        }
                        TextAction(
                            text = stringResource(R.string.harvest_planting_date_clear),
                            onClick = { viewModel.clearPlantingDate() },
                            modifier = Modifier.testTag("harvest_planting_date_clear")
                        )
                    }
                }
            }

            Spacer(Modifier.height(Spacing.section))
            Text(
                stringResource(R.string.harvest_maturity_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(Spacing.md))
            InfoCard(modifier = Modifier.testTag("harvest_maturity")) {
                ResultField(
                    label = stringResource(R.string.harvest_expected_maturity_range_label),
                    value = expectedMaturityRangeLabel(state.maturity)
                )
                Spacer(Modifier.height(Spacing.md))
                ResultField(
                    label = stringResource(R.string.harvest_estimated_days_remaining_label),
                    value = estimatedDaysRemainingLabel(state.maturity)
                )
                Spacer(Modifier.height(Spacing.md))
                ResultField(
                    label = stringResource(R.string.harvest_maturity_status_label),
                    value = maturityStatusLabel(state.maturity.timing)
                )
                Spacer(Modifier.height(Spacing.md))
                ResultField(
                    label = stringResource(R.string.harvest_maturity_reference_label),
                    value = maturityReferenceLabel(state.maturity)
                )
                Spacer(Modifier.height(Spacing.md))
                Text(
                    stringResource(R.string.harvest_maturity_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(Spacing.section))

            if (!state.canScanCrop) {
                DemoNotice(
                    stringResource(R.string.harvest_crop_scan_requires_date),
                    modifier = Modifier.testTag("harvest_need_planting_date")
                )
                Spacer(Modifier.height(Spacing.md))
            }
            PrimaryActionButton(
                text = stringResource(R.string.harvest_scan_fruit),
                onClick = {
                    if (state.canScanCrop) {
                        onScanFruit()
                    } else {
                        showNeedPlantingDate = true
                    }
                },
                modifier = Modifier.testTag("harvest_scan_fruit")
            )
            Spacer(Modifier.height(Spacing.related))
            SecondaryActionButton(
                text = stringResource(R.string.harvest_scan_leaf),
                onClick = onScanLeaf,
                modifier = Modifier.testTag("harvest_scan_leaf")
            )

            Spacer(Modifier.height(Spacing.section))
            Text(
                stringResource(R.string.harvest_live_env_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(Spacing.md))
            HarvestEnvironmentCard(state)

            Spacer(Modifier.height(Spacing.section))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.harvest_recent_scans_title),
                    style = MaterialTheme.typography.titleLarge
                )
                TextAction(
                    text = stringResource(R.string.harvest_view_history),
                    onClick = onOpenHistory,
                    modifier = Modifier.testTag("harvest_open_history")
                )
            }
            Spacer(Modifier.height(Spacing.md))
            if (state.recentScans.isEmpty()) {
                InfoCard(modifier = Modifier.testTag("harvest_recent_scans")) {
                    EmptyStateText(stringResource(R.string.harvest_recent_scans_empty))
                }
            } else {
                Column(modifier = Modifier.testTag("harvest_recent_scans")) {
                    state.recentScans.take(3).forEachIndexed { index, record ->
                        if (index > 0) Spacer(Modifier.height(Spacing.md))
                        HarvestHistoryListItem(
                            record = record,
                            onClick = { onOpenRecord(record.id) }
                        )
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        PlantingDatePickerDialog(
            currentUtcMillis = state.plantingDateUtcMillis,
            onConfirm = { millis ->
                viewModel.setPlantingDateUtcMillis(millis)
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false }
        )
    }
    if (showVarietyPicker) {
        TomatoVarietyPickerDialog(
            varieties = state.tomatoVarieties,
            selectedName = state.selectedVariety?.variety,
            onSelect = { variety ->
                viewModel.selectVariety(variety.variety)
                showVarietyPicker = false
            },
            onDismiss = { showVarietyPicker = false }
        )
    }
    if (showNeedPlantingDate) {
        AlertDialog(
            onDismissRequest = { showNeedPlantingDate = false },
            text = {
                Text(
                    stringResource(R.string.harvest_crop_scan_requires_date),
                    modifier = Modifier.testTag("harvest_need_planting_date_dialog")
                )
            },
            confirmButton = {
                TextButton(onClick = { showNeedPlantingDate = false }) {
                    Text(stringResource(R.string.harvest_planting_date_confirm))
                }
            }
        )
    }
}

@Composable
private fun PlantingDatePickerDialog(
    currentUtcMillis: Long?,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val currentYear = Calendar.getInstance().get(Calendar.YEAR)
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = currentUtcMillis,
        yearRange = IntRange(currentYear - 40, currentYear),
        selectableDates = PastOrTodaySelectableDates
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val selected = pickerState.selectedDateMillis ?: return@TextButton
                    if (PlantingDates.isNotAfterToday(selected)) {
                        onConfirm(selected)
                    }
                },
                modifier = Modifier.testTag("harvest_planting_date_confirm")
            ) {
                Text(stringResource(R.string.harvest_planting_date_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.harvest_planting_date_cancel))
            }
        }
    ) {
        DatePicker(state = pickerState, modifier = Modifier.testTag("harvest_planting_date_picker"))
    }
}

private object PastOrTodaySelectableDates : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean =
        PlantingDates.isNotAfterToday(utcTimeMillis)

    override fun isSelectableYear(year: Int): Boolean =
        year <= PlantingDates.localYear()
}

@Composable
private fun TomatoVarietyCard(
    state: HarvestUiState,
    onOpenPicker: () -> Unit,
    onClear: () -> Unit
) {
    val selected = state.selectedVariety
    InfoCard(modifier = Modifier.testTag("harvest_variety")) {
        Text(
            stringResource(R.string.harvest_variety_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Spacing.xs))
        if (state.tomatoVarieties.isEmpty()) {
            Text(
                stringResource(R.string.harvest_variety_list_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("harvest_variety_value")
            )
            return@InfoCard
        }
        if (selected == null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenPicker)
            ) {
                Text(
                    stringResource(R.string.harvest_variety_not_selected),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.testTag("harvest_variety_value")
                )
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    stringResource(R.string.harvest_variety_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onOpenPicker)
                ) {
                    Text(
                        selected.variety,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.testTag("harvest_variety_value")
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        selected.documentedRipeColor?.let { color ->
                            stringResource(R.string.harvest_variety_ripe_color_label) + ": " + color
                        } ?: stringResource(
                            R.string.harvest_variety_ripe_color_label
                        ) + ": " + stringResource(R.string.harvest_variety_ripe_color_unknown),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(Spacing.xxs))
                    Text(
                        varietyMaturityRangeText(selected)
                            ?: stringResource(R.string.harvest_variety_uses_general_maturity),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("harvest_variety_maturity_hint")
                    )
                }
                TextAction(
                    text = stringResource(R.string.harvest_variety_clear),
                    onClick = onClear,
                    modifier = Modifier.testTag("harvest_variety_clear")
                )
            }
        }
    }
}

@Composable
private fun TomatoVarietyPickerDialog(
    varieties: List<VarietyReference>,
    selectedName: String?,
    onSelect: (VarietyReference) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.harvest_variety_picker_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
                    .testTag("harvest_variety_picker")
            ) {
                varieties.forEachIndexed { index, variety ->
                    if (index > 0) Spacer(Modifier.height(Spacing.md))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(variety) }
                            .testTag("harvest_variety_option_$index")
                    ) {
                        Text(
                            variety.variety,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (variety.variety == selectedName) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                        val ripe = variety.documentedRipeColor
                        if (!ripe.isNullOrBlank()) {
                            Text(
                                stringResource(R.string.harvest_variety_ripe_color_label) + ": " + ripe,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            varietyMaturityRangeText(variety)
                                ?: stringResource(R.string.harvest_variety_uses_general_maturity),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("harvest_variety_picker_close")
            ) {
                Text(stringResource(R.string.harvest_variety_picker_close))
            }
        }
    )
}
