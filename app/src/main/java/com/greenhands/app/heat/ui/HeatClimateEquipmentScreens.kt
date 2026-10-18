package com.greenhands.app.heat.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.greenhands.app.R
import com.greenhands.app.heat.model.ControlMode
import com.greenhands.app.heat.model.Crop
import com.greenhands.app.heat.model.HumiditySubPeriod
import com.greenhands.app.heat.model.SchedulePeriod
import com.greenhands.app.heat.model.TimedClimateBand
import com.greenhands.app.heat.model.formatOneDecimal
import com.greenhands.app.heat.profile.CropProfileRegistry
import com.greenhands.app.ui.components.ConfigurationProgress
import com.greenhands.app.ui.components.DemoNotice
import com.greenhands.app.ui.components.InfoCard
import com.greenhands.app.ui.components.SecondaryActionButton
import com.greenhands.app.ui.components.SectionHeading
import com.greenhands.app.ui.components.StatusChip
import com.greenhands.app.ui.components.StickySaveBar
import com.greenhands.app.ui.components.TextAction
import com.greenhands.app.ui.components.WarningPanel
import com.greenhands.app.ui.theme.ClimateTeal
import com.greenhands.app.ui.theme.GhType
import com.greenhands.app.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClimateTargetsScreen(
    ui: HeatUiState,
    onPeriod: (SchedulePeriod) -> Unit,
    onDayTempInput: (String) -> Unit,
    onNightTempInput: (String) -> Unit,
    onDayRhInput: (String) -> Unit,
    onNightRhInput: (String) -> Unit,
    onEdit: () -> Unit,
    onReset: () -> Unit,
    onSaveContinue: () -> Unit,
    onBack: () -> Unit,
    onConfirmDiscard: () -> Unit,
    onCancelDiscard: () -> Unit
) {
    val rec = ui.recommendation
    val period = ui.schedulePeriod
    val isDay = period == SchedulePeriod.DAY
    var showSource by rememberSaveable { mutableStateOf(false) }
    BackHandler { onBack() }
    HeatScaffold(
        title = stringResource(R.string.climate_title),
        onBack = onBack,
        stage = ui.config.stage,
        crop = ui.config.crop,
        bottomBar = {
            StickySaveBar(
                label = if (ui.returnToSummary) {
                    stringResource(R.string.action_save_return_summary)
                } else {
                    stringResource(R.string.action_save_continue)
                },
                onClick = onSaveContinue,
                enabled = !ui.saving,
                testTag = "climate_save_continue"
            )
        }
    ) { padding ->
        com.greenhands.app.ui.components.ScrollScreen(
            Modifier.fillMaxSize().padding(padding).testTag("climate_targets")
        ) {
            ConfigurationProgress(step = 3)
            Spacer(Modifier.height(Spacing.md))
            ui.config.crop?.let { crop ->
                Text(crop.displayName, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                Text(
                    crop.scientificName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            ui.config.stage?.let {
                Spacer(Modifier.height(Spacing.xxs))
                Text(it.displayName, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(Spacing.md))
            Text(
                stringResource(R.string.climate_suggested_heading),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("research_suggested_heading")
                    .semantics { heading() }
            )
            Spacer(Modifier.height(Spacing.titleDesc))
            Text(
                stringResource(R.string.climate_suggested_support),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(Spacing.md))
            StatusChip(
                if (ui.config.valuesAreCustomised) stringResource(R.string.status_customised)
                else stringResource(R.string.status_suggested)
            )
            TextAction(
                text = stringResource(R.string.action_view_source),
                onClick = { showSource = true },
                modifier = Modifier.testTag("climate_view_source")
            )
            Spacer(Modifier.height(Spacing.md))
            Text(
                stringResource(
                    R.string.climate_configuring,
                    if (isDay) stringResource(R.string.climate_period_day) else stringResource(R.string.climate_period_night)
                ),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth().testTag("climate_period_label")
            )
            Spacer(Modifier.height(Spacing.md))
            DayNightSelector(period, onPeriod)
            Spacer(Modifier.height(Spacing.md))
            val tempValue = if (isDay) ui.dayTempInput else ui.nightTempInput
            val rhValue = if (isDay) ui.dayRhInput else ui.nightRhInput
            val tempError = if (isDay) ui.dayTempError else ui.nightTempError
            val rhError = if (isDay) ui.dayRhError else ui.nightRhError
            val tempWarning = if (isDay) ui.dayTempWarning else ui.nightTempWarning
            val rhWarning = if (isDay) ui.dayRhWarning else ui.nightRhWarning
            val tempRange = rec?.temperatureRangeFor(period)
            val rhRange = rec?.humidityRangeFor(period)
            val tempTag = if (isDay) "climate_day_temp" else "climate_night_temp"
            InfoCard {
                Text(
                    stringResource(R.string.climate_temperature),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    if (tempValue.isBlank()) "—" else "${tempValue}°C",
                    style = GhType.metric,
                    color = ClimateTeal,
                    modifier = Modifier.testTag(if (ui.climateEditing) {
                        if (isDay) "climate_day_value" else "climate_night_value"
                    } else tempTag)
                )
                tempRange?.let {
                    Text(
                        stringResource(R.string.climate_suggested_range, it.label()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (ui.climateEditing) {
                    Spacer(Modifier.height(Spacing.field))
                    DecimalField(
                        value = tempValue,
                        onValueChange = if (isDay) onDayTempInput else onNightTempInput,
                        label = stringResource(R.string.climate_temperature),
                        error = tempError,
                        enabled = true,
                        testTag = tempTag,
                        suffix = "°C"
                    )
                }
            }
            tempWarning?.let {
                Spacer(Modifier.height(Spacing.md))
                WarningPanel(stringResource(R.string.climate_outside_title), it)
            }
            Spacer(Modifier.height(Spacing.related))
            InfoCard {
                Text(
                    stringResource(R.string.climate_humidity),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    if (rhValue.isBlank()) "—" else "$rhValue%",
                    style = GhType.metric,
                    color = ClimateTeal,
                    modifier = Modifier.testTag(if (ui.climateEditing) "climate_rh_value" else "climate_rh")
                )
                rhRange?.let {
                    Text(
                        stringResource(R.string.climate_suggested_range, it.label()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (ui.climateEditing) {
                    Spacer(Modifier.height(Spacing.field))
                    DecimalField(
                        value = rhValue,
                        onValueChange = if (isDay) onDayRhInput else onNightRhInput,
                        label = stringResource(R.string.climate_humidity),
                        error = rhError,
                        enabled = true,
                        testTag = "climate_rh",
                        suffix = "%"
                    )
                }
            }
            rhWarning?.let {
                Spacer(Modifier.height(Spacing.md))
                WarningPanel(stringResource(R.string.climate_outside_title), it)
            }
            rec?.let {
                val used = formatOneDecimal(ui.activeTemperature() ?: it.temperatureFor(period))
                Spacer(Modifier.height(Spacing.md))
                Text(
                    if (isDay) stringResource(R.string.climate_demo_calc_day, used)
                    else stringResource(R.string.climate_demo_calc_night, used),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("climate_demo_calc")
                )
            }
            rec?.operatorWarning?.let { warning ->
                Spacer(Modifier.height(Spacing.md))
                WarningPanel(stringResource(R.string.climate_heat_warning_title), warning)
            }
            ui.persistError?.let {
                Spacer(Modifier.height(Spacing.md))
                WarningPanel(stringResource(R.string.climate_notice_title), it)
            }
            Spacer(Modifier.height(Spacing.md))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TextAction(
                    text = stringResource(R.string.action_edit_values),
                    onClick = onEdit,
                    modifier = Modifier.testTag("climate_edit")
                )
                TextAction(
                    text = stringResource(R.string.action_reset_suggested),
                    onClick = onReset,
                    modifier = Modifier.testTag("climate_reset")
                )
            }
        }
    }
    if (showSource && rec != null) {
        SourceDetailsSheet(
            crop = ui.config.crop,
            rec = rec,
            onDismiss = { showSource = false }
        )
    }
    if (ui.showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = onCancelDiscard,
            title = { Text(stringResource(R.string.discard_title)) },
            text = { Text(stringResource(R.string.discard_body)) },
            confirmButton = {
                TextButton(onClick = onConfirmDiscard, modifier = Modifier.testTag("discard_confirm")) {
                    Text(stringResource(R.string.discard_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = onCancelDiscard, modifier = Modifier.testTag("discard_cancel")) {
                    Text(stringResource(R.string.discard_stay))
                }
            },
            modifier = Modifier.testTag("discard_changes_dialog")
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceDetailsSheet(
    crop: Crop?,
    rec: com.greenhands.app.heat.model.ClimateRecommendation,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val primary = rec.sourceIds.firstOrNull()?.let { CropProfileRegistry.citationOrNull(it) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag("source_detail_sheet")
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = Spacing.xl, vertical = Spacing.lg)
        ) {
            Text(stringResource(R.string.source_sheet_title), style = MaterialTheme.typography.headlineSmall)
            crop?.let {
                Spacer(Modifier.height(Spacing.sm))
                Text(it.displayName, style = MaterialTheme.typography.titleMedium)
                Text(
                    it.scientificName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(Spacing.md))
            primary?.let { source ->
                Text(source.title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(Spacing.sm))
                Text(stringResource(R.string.source_authors), style = MaterialTheme.typography.labelLarge)
                Text(source.authorsOrOrganisation, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(Spacing.sm))
                Text(stringResource(R.string.source_year), style = MaterialTheme.typography.labelLarge)
                Text(source.year, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(Spacing.sm))
                Text(stringResource(R.string.source_supported), style = MaterialTheme.typography.labelLarge)
                Text(
                    "${source.supportedCrops.joinToString { it.displayName }} · ${source.supportedParameters.joinToString()}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(Spacing.sm))
                Text(stringResource(R.string.source_geo), style = MaterialTheme.typography.labelLarge)
                Text(source.geographicApplicability, style = MaterialTheme.typography.bodyMedium)
            }
            if (rec.hasTimeOfDayTemperatureSchedule()) {
                Spacer(Modifier.height(Spacing.section))
                Text(stringResource(R.string.source_original), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(Spacing.sm))
                PublishedTemperatureSchedule(rec.temperatureSchedule)
            }
            if (rec.humiditySubPeriods.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.md))
                PublishedNurseryHumidity(rec.humiditySubPeriods)
            } else if (rec.humiditySchedule.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.md))
                PublishedHumiditySchedule(rec.humiditySchedule)
            }
            rec.presentationMapping?.let { mapping ->
                Spacer(Modifier.height(Spacing.md))
                Text(stringResource(R.string.source_app_presents), style = MaterialTheme.typography.titleMedium)
                Text(mapping, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(Spacing.md))
            Text(stringResource(R.string.source_notes), style = MaterialTheme.typography.titleMedium)
            rec.warningNotes.forEach { note ->
                Spacer(Modifier.height(Spacing.xs))
                Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(Spacing.md))
            DemoNotice(stringResource(R.string.source_local_note))
            primary?.doiOrUrl?.takeIf { it.startsWith("http") }?.let { url ->
                Spacer(Modifier.height(Spacing.md))
                SecondaryActionButton(
                    text = stringResource(R.string.source_open),
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        try {
                            context.startActivity(intent)
                        } catch (_: ActivityNotFoundException) {
                            Toast.makeText(context, context.getString(R.string.source_unavailable), Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.testTag("source_open_${primary.id}")
                )
            }
            Spacer(Modifier.height(Spacing.xxl))
        }
    }
}

@Composable
private fun PublishedTemperatureSchedule(bands: List<TimedClimateBand>) {
    InfoCard(modifier = Modifier.testTag("climate_temp_schedule")) {
        bands.forEach { band ->
            Text(
                "${band.label}: ${band.displayValue()}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("temp_period_${band.id}")
            )
        }
    }
}

@Composable
private fun PublishedHumiditySchedule(bands: List<TimedClimateBand>) {
    InfoCard(modifier = Modifier.testTag("climate_rh_schedule")) {
        bands.forEach { band ->
            Text(
                "${band.label}: ${band.displayValue()}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("rh_band_${band.id}")
            )
        }
    }
}

@Composable
private fun PublishedNurseryHumidity(periods: List<HumiditySubPeriod>) {
    InfoCard(modifier = Modifier.testTag("climate_rh_nursery_periods")) {
        periods.forEach { period ->
            Spacer(Modifier.height(Spacing.sm))
            Text(
                "${period.label} (${period.dayRangeNote})",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.testTag("rh_subperiod_${period.id}")
            )
            period.bands.forEach { band ->
                Text(
                    "${band.label}: ${band.displayValue()}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("rh_band_${band.id}")
                )
            }
        }
    }
}

@Composable
fun CirculationFanScreen(
    ui: HeatUiState,
    onPeriod: (SchedulePeriod) -> Unit,
    onMode: (ControlMode) -> Unit,
    onCsp: (String) -> Unit,
    onCdp: (String) -> Unit,
    onCon: (String) -> Unit,
    onReset: () -> Unit,
    onSaveContinue: () -> Unit,
    onBack: () -> Unit,
    onConfirmAdvanced: () -> Unit = {},
    onCancelAdvanced: () -> Unit = {}
) {
    EquipmentScreen(
        ui = ui,
        title = stringResource(R.string.equipment_circ_title),
        glyph = { FanGlyph() },
        onPeriod = onPeriod,
        onMode = onMode,
        onReset = onReset,
        onSaveContinue = onSaveContinue,
        onBack = onBack,
        onConfirmAdvanced = onConfirmAdvanced,
        onCancelAdvanced = onCancelAdvanced,
        saveTag = "circ_save",
        screenTag = "circulation_fan",
        content = { advanced ->
            if (advanced) {
                DecimalField(ui.conInput, onCon, stringResource(R.string.equipment_starts_circ), ui.circulationError, testTag = "circ_con", suffix = "°C")
                Spacer(Modifier.height(Spacing.field))
                DecimalField(ui.cdpInput, onCdp, stringResource(R.string.equipment_stops_circ), null, testTag = "circ_cdp", suffix = "°C")
                Spacer(Modifier.height(Spacing.field))
                DecimalField(ui.cspInput, onCsp, stringResource(R.string.equipment_cont_circ), null, testTag = "circ_csp", suffix = "°C")
            } else {
                ThresholdLine("", stringResource(R.string.equipment_starts_circ), "${ui.conInput}°C", "circ_con")
                Spacer(Modifier.height(Spacing.sm))
                ThresholdLine("", stringResource(R.string.equipment_stops_circ), "${ui.cdpInput}°C", "circ_cdp")
                Spacer(Modifier.height(Spacing.sm))
                ThresholdLine("", stringResource(R.string.equipment_cont_circ), "${ui.cspInput}°C", "circ_csp")
            }
            WarningText(ui.circulationError)
            WarningText(ui.circulationWarning)
            TechnicalDetails {
                Text(stringResource(R.string.equipment_codes_circ, ui.cspInput, ui.cdpInput, ui.conInput), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.equipment_formula_circ), style = MaterialTheme.typography.bodySmall)
            }
        }
    )
}

@Composable
fun ExhaustFanScreen(
    ui: HeatUiState,
    onPeriod: (SchedulePeriod) -> Unit,
    onMode: (ControlMode) -> Unit,
    onEsp: (String) -> Unit,
    onEon: (String) -> Unit,
    onReset: () -> Unit,
    onSaveContinue: () -> Unit,
    onBack: () -> Unit,
    onConfirmAdvanced: () -> Unit = {},
    onCancelAdvanced: () -> Unit = {}
) {
    val exhaust = ui.config.exhaust(ui.schedulePeriod)
    EquipmentScreen(
        ui = ui,
        title = stringResource(R.string.equipment_exh_title),
        glyph = { FanGlyph() },
        onPeriod = onPeriod,
        onMode = onMode,
        onReset = onReset,
        onSaveContinue = onSaveContinue,
        onBack = onBack,
        onConfirmAdvanced = onConfirmAdvanced,
        onCancelAdvanced = onCancelAdvanced,
        saveTag = "exh_save",
        screenTag = "exhaust_fan",
        content = { advanced ->
            if (advanced) {
                DecimalField(ui.eonInput, onEon, stringResource(R.string.equipment_starts_exh), ui.exhaustError, testTag = "exh_eon", suffix = "°C")
                Spacer(Modifier.height(Spacing.field))
                ThresholdLine("", stringResource(R.string.equipment_stops_exh), "${exhaust?.edp?.let { formatOneDecimal(it) } ?: "—"}°C", "exh_edp")
                Spacer(Modifier.height(Spacing.field))
                DecimalField(ui.espInput, onEsp, stringResource(R.string.equipment_cont_exh), null, testTag = "exh_esp", suffix = "°C")
            } else if (exhaust != null) {
                ThresholdLine("", stringResource(R.string.equipment_starts_exh), "${formatOneDecimal(exhaust.eon)}°C", "exh_eon")
                Spacer(Modifier.height(Spacing.sm))
                ThresholdLine("", stringResource(R.string.equipment_stops_exh), "${formatOneDecimal(exhaust.edp)}°C", "exh_edp")
                Spacer(Modifier.height(Spacing.sm))
                ThresholdLine("", stringResource(R.string.equipment_cont_exh), "${formatOneDecimal(exhaust.esp)}°C", "exh_esp")
            }
            WarningText(ui.exhaustError)
            TechnicalDetails {
                Text(
                    stringResource(
                        R.string.equipment_codes_exh,
                        ui.espInput,
                        exhaust?.edp?.let { formatOneDecimal(it) } ?: "—",
                        ui.eonInput
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(stringResource(R.string.equipment_formula_exh), style = MaterialTheme.typography.bodySmall)
            }
        }
    )
}

@Composable
fun FoggerSettingsScreen(
    ui: HeatUiState,
    onPeriod: (SchedulePeriod) -> Unit,
    onMode: (ControlMode) -> Unit,
    onFsp: (String) -> Unit,
    onFon: (String) -> Unit,
    onFdp: (String) -> Unit,
    onReset: () -> Unit,
    onSaveContinue: () -> Unit,
    onBack: () -> Unit,
    onConfirmAdvanced: () -> Unit = {},
    onCancelAdvanced: () -> Unit = {}
) {
    EquipmentScreen(
        ui = ui,
        title = stringResource(R.string.equipment_fog_title),
        glyph = { FoggerGlyph() },
        onPeriod = onPeriod,
        onMode = onMode,
        onReset = onReset,
        onSaveContinue = onSaveContinue,
        onBack = onBack,
        onConfirmAdvanced = onConfirmAdvanced,
        onCancelAdvanced = onCancelAdvanced,
        saveTag = "fog_save",
        screenTag = "fogger_settings",
        content = { advanced ->
            if (advanced) {
                DecimalField(ui.fonInput, onFon, stringResource(R.string.equipment_fog_starts), ui.foggerError, testTag = "fog_fon", suffix = "%")
                Spacer(Modifier.height(Spacing.field))
                DecimalField(ui.fspInput, onFsp, stringResource(R.string.equipment_fog_set), null, testTag = "fog_fsp", suffix = "%")
                Spacer(Modifier.height(Spacing.field))
                DecimalField(ui.fdpInput, onFdp, stringResource(R.string.equipment_fog_stops), null, testTag = "fog_fdp", suffix = "%")
            } else {
                ThresholdLine("", stringResource(R.string.equipment_fog_starts), "${ui.fonInput}%", "fog_fon")
                Spacer(Modifier.height(Spacing.sm))
                ThresholdLine("", stringResource(R.string.equipment_fog_set), "${ui.fspInput}%", "fog_fsp")
                Spacer(Modifier.height(Spacing.sm))
                ThresholdLine("", stringResource(R.string.equipment_fog_stops), "${ui.fdpInput}%", "fog_fdp")
            }
            WarningText(ui.foggerError)
            WarningText(ui.foggerWarning)
            TechnicalDetails {
                Text(stringResource(R.string.equipment_codes_fog, ui.fspInput, ui.fonInput, ui.fdpInput), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.equipment_formula_fog), style = MaterialTheme.typography.bodySmall)
            }
        }
    )
}

@Composable
private fun EquipmentScreen(
    ui: HeatUiState,
    title: String,
    glyph: @Composable () -> Unit,
    onPeriod: (SchedulePeriod) -> Unit,
    onMode: (ControlMode) -> Unit,
    onReset: () -> Unit,
    onSaveContinue: () -> Unit,
    onBack: () -> Unit,
    onConfirmAdvanced: () -> Unit,
    onCancelAdvanced: () -> Unit,
    saveTag: String,
    screenTag: String,
    content: @Composable (Boolean) -> Unit
) {
    val config = ui.config
    val advanced = config.controlMode == ControlMode.ADVANCED
    val temp = ui.activeTemperature()
    HeatScaffold(
        title = title,
        onBack = onBack,
        stage = config.stage,
        crop = config.crop,
        bottomBar = {
            StickySaveBar(
                label = if (ui.returnToSummary) {
                    stringResource(R.string.action_save_return_summary)
                } else {
                    stringResource(R.string.action_save_continue)
                },
                onClick = onSaveContinue,
                enabled = !ui.saving,
                testTag = saveTag
            )
        }
    ) { padding ->
        com.greenhands.app.ui.components.ScrollScreen(
            Modifier.fillMaxSize().padding(padding).testTag(screenTag)
        ) {
            glyph()
            Spacer(Modifier.height(Spacing.md))
            StatusChip(stringResource(R.string.status_disconnected))
            Spacer(Modifier.height(Spacing.sm))
            Text(
                "${config.crop?.displayName ?: "—"} · ${config.stage?.displayName ?: "—"}",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(Spacing.section))
            DayNightSelector(ui.schedulePeriod, onPeriod)
            Spacer(Modifier.height(Spacing.md))
            AutomaticAdvancedSelector(config.controlMode, onMode)
            Spacer(Modifier.height(Spacing.md))
            Text(
                if (config.controlMode == ControlMode.AUTOMATIC) {
                    stringResource(R.string.equipment_auto_title)
                } else {
                    stringResource(R.string.equipment_adv_title)
                },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth().testTag("equipment_mode_title")
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                if (config.controlMode == ControlMode.AUTOMATIC) {
                    stringResource(R.string.equipment_auto_body)
                } else {
                    stringResource(R.string.equipment_adv_body)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("equipment_mode_body")
            )
            Spacer(Modifier.height(Spacing.md))
            DemoNotice(stringResource(R.string.equipment_preview_notice))
            if (config.controlMode == ControlMode.ADVANCED) {
                Spacer(Modifier.height(Spacing.sm))
                StatusChip(stringResource(R.string.status_manual_override))
            }
            if (temp != null) {
                Spacer(Modifier.height(Spacing.md))
                Text(
                    "${stringResource(R.string.equipment_target_used)} ${formatOneDecimal(temp)}°C (${if (ui.schedulePeriod == SchedulePeriod.DAY) stringResource(R.string.climate_period_day) else stringResource(R.string.climate_period_night)})",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Spacer(Modifier.height(Spacing.section))
            content(advanced)
            Spacer(Modifier.height(Spacing.md))
            TextAction(
                text = stringResource(R.string.action_reset_automatic),
                onClick = onReset,
                modifier = Modifier.testTag("reset_formula")
            )
            Spacer(Modifier.height(Spacing.md))
        }
    }
    if (ui.showAdvancedConfirm) {
        AlertDialog(
            onDismissRequest = onCancelAdvanced,
            title = { Text(stringResource(R.string.equipment_adv_warn_title)) },
            text = { Text(stringResource(R.string.equipment_adv_warn_body)) },
            confirmButton = {
                TextButton(onClick = onConfirmAdvanced) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = onCancelAdvanced) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}
