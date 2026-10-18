package com.greenhands.app.heat.ui

import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.greenhands.app.R
import com.greenhands.app.heat.model.ControlMode
import com.greenhands.app.heat.model.GrowthStage
import com.greenhands.app.heat.model.formatOneDecimal
import com.greenhands.app.ui.components.DemoNotice
import com.greenhands.app.ui.components.EmptyStateText
import com.greenhands.app.ui.components.InfoCard
import com.greenhands.app.ui.components.PrimaryActionButton
import com.greenhands.app.ui.components.ScrollScreen
import com.greenhands.app.ui.components.SecondaryActionButton
import com.greenhands.app.ui.components.SectionHeading
import com.greenhands.app.ui.components.StatusChip
import com.greenhands.app.ui.components.StickySaveBar
import com.greenhands.app.ui.theme.Spacing

@Suppress("UNUSED_PARAMETER")
@Composable
fun ConfigurationSummaryScreen(
    ui: HeatUiState,
    onEditClimate: () -> Unit,
    onEditCirculation: () -> Unit,
    onEditExhaust: () -> Unit,
    onEditFogger: () -> Unit,
    onResetEntire: () -> Unit,
    onContinueSimulation: () -> Unit,
    onBack: () -> Unit
) {
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    val config = ui.config
    HeatScaffold(
        title = stringResource(R.string.summary_title),
        onBack = onBack,
        stage = config.stage,
        crop = config.crop,
        bottomBar = {
            StickySaveBar(
                label = stringResource(R.string.action_continue_simulation),
                onClick = onContinueSimulation,
                enabled = !ui.saving,
                testTag = "summary_continue"
            )
        }
    ) { padding ->
        ScrollScreen(Modifier.fillMaxSize().padding(padding).testTag("heat_summary")) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusChip(if (config.saved) stringResource(R.string.status_saved) else stringResource(R.string.status_customised))
                Spacer(Modifier.padding(Spacing.sm))
                StatusChip(
                    if (config.valuesAreCustomised) stringResource(R.string.status_customised)
                    else stringResource(R.string.status_suggested)
                )
            }
            Spacer(Modifier.height(Spacing.section))
            SummarySection(
                title = stringResource(R.string.summary_crop_stage),
                onEdit = onEditClimate,
                tag = "summary_edit_climate"
            ) {
                Text("${config.crop?.displayName ?: "—"}", style = MaterialTheme.typography.titleMedium)
                Text(config.crop?.scientificName ?: "—", style = MaterialTheme.typography.bodySmall)
                Text(config.stage?.displayName ?: "—", style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(Spacing.related))
            SummarySection(title = stringResource(R.string.summary_day_climate), onEdit = onEditClimate, tag = "summary_edit_day") {
                Text("${config.dayTemperatureC?.let { formatOneDecimal(it) } ?: "—"}°C", style = MaterialTheme.typography.headlineSmall)
                Text("${config.dayHumidityPercent ?: config.targetHumidityPercent ?: "—"}%", style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(Spacing.related))
            SummarySection(title = stringResource(R.string.summary_night_climate), onEdit = onEditClimate, tag = "summary_edit_night") {
                Text("${config.nightTemperatureC?.let { formatOneDecimal(it) } ?: "—"}°C", style = MaterialTheme.typography.headlineSmall)
                Text("${config.nightHumidityPercent ?: "—"}%", style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(Spacing.related))
            SummarySection(title = stringResource(R.string.summary_humidity), onEdit = onEditClimate, tag = "summary_edit_humidity") {
                Text("Day ${config.dayHumidityPercent ?: config.targetHumidityPercent ?: "—"}% · Night ${config.nightHumidityPercent ?: "—"}%")
            }
            Spacer(Modifier.height(Spacing.related))
            SummarySection(title = stringResource(R.string.summary_mode), onEdit = onEditCirculation, tag = "summary_edit_mode") {
                Text(
                    if (config.controlMode == ControlMode.AUTOMATIC) stringResource(R.string.equipment_auto_title)
                    else stringResource(R.string.equipment_adv_title),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(Modifier.height(Spacing.related))
            SummarySection(title = stringResource(R.string.summary_day_eq), onEdit = onEditCirculation, tag = "summary_edit_circulation") {
                val c = config.circulation
                val e = config.exhaust
                val f = config.fogger
                Text(stringResource(R.string.summary_circ_line, c?.csp?.let { formatOneDecimal(it) } ?: "—"))
                Text(stringResource(R.string.summary_exh_line, e?.esp?.let { formatOneDecimal(it) } ?: "—"))
                Text(stringResource(R.string.summary_fog_line, f?.fsp?.let { formatOneDecimal(it) } ?: "—"))
            }
            Spacer(Modifier.height(Spacing.related))
            SummarySection(title = stringResource(R.string.summary_night_eq), onEdit = onEditExhaust, tag = "summary_edit_exhaust") {
                val c = config.nightCirculation
                val e = config.nightExhaust
                val f = config.nightFogger
                Text(stringResource(R.string.summary_circ_line, c?.csp?.let { formatOneDecimal(it) } ?: "—"))
                Text(stringResource(R.string.summary_exh_line, e?.esp?.let { formatOneDecimal(it) } ?: "—"))
                Text(stringResource(R.string.summary_fog_line, f?.fsp?.let { formatOneDecimal(it) } ?: "—"))
            }
            Spacer(Modifier.height(Spacing.section))
            SecondaryActionButton(
                text = stringResource(R.string.summary_reset),
                onClick = { confirmReset = true },
                modifier = Modifier.testTag("summary_reset")
            )
            Spacer(Modifier.height(Spacing.section))
        }
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.summary_reset_title)) },
            text = { Text(stringResource(R.string.summary_reset_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onResetEntire()
                        confirmReset = false
                    },
                    modifier = Modifier.testTag("summary_reset_confirm")
                ) { Text(stringResource(R.string.action_reset)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}

@Composable
private fun SummarySection(
    title: String,
    onEdit: () -> Unit,
    tag: String,
    content: @Composable () -> Unit
) {
    InfoCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onEdit, modifier = Modifier.testTag(tag)) {
                Text(stringResource(R.string.summary_edit))
            }
        }
        content()
    }
}

@Composable
fun DemoSimulationNextScreen(cropName: String?, stage: GrowthStage?, onBack: (() -> Unit)?) {
    HeatScaffold(title = stringResource(R.string.sim_title), onBack = onBack, stage = stage) { padding ->
        ScrollScreen(Modifier.fillMaxSize().padding(padding).testTag("demo_sim_next")) {
            SectionHeading(
                title = stringResource(R.string.sim_heading),
                subtitle = null
            )
            Spacer(Modifier.height(Spacing.md))
            EmptyStateText(stringResource(R.string.sim_body))
            Spacer(Modifier.height(Spacing.md))
            DemoNotice(stringResource(R.string.sim_notice))
            if (cropName != null) {
                Spacer(Modifier.height(Spacing.sm))
                Text(stringResource(R.string.sim_last_crop, cropName), style = MaterialTheme.typography.bodyMedium)
            }
            if (onBack != null) {
                Spacer(Modifier.height(Spacing.section))
                PrimaryActionButton(stringResource(R.string.action_back), onBack, Modifier.testTag("demo_sim_back"))
            }
        }
    }
}
