package com.greenhands.app.ar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.greenhands.app.R
import com.greenhands.app.ar.domain.ArHudContent
import com.greenhands.app.ar.domain.ArRuntimeCopy
import com.greenhands.app.ar.domain.EquipmentPresentation
import com.greenhands.app.ar.domain.EquipmentPresentationCopy
import com.greenhands.app.ar.domain.EquipmentRuntimeInputSource
import com.greenhands.app.equipment.model.ManufacturerSpecCopy
import com.greenhands.app.equipment.ui.EquipmentPlacementViewModel
import com.greenhands.app.equipment.ui.PlacementUiState
import com.greenhands.app.heat.model.RH_HARD_MAX
import com.greenhands.app.heat.model.RH_HARD_MIN
import com.greenhands.app.heat.model.TEMP_HARD_MAX
import com.greenhands.app.heat.model.TEMP_HARD_MIN
import com.greenhands.app.heat.runtime.FanPadCopy
import com.greenhands.app.heat.runtime.contracts.FanPadWallSelection
import com.greenhands.app.heat.visualization.CirculationEffectPlanner
import com.greenhands.app.heat.visualization.ExhaustEffectPlanner
import com.greenhands.app.heat.visualization.FoggerEffectPlanner
import com.greenhands.app.heat.visualization.VisualizationCopy
import com.greenhands.app.preview3d.model.PreviewMode
import com.greenhands.app.ui.components.PrimaryActionButton
import com.greenhands.app.ui.components.SecondaryActionButton
import com.greenhands.app.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArBehaviorSheet(
    placement: PlacementUiState,
    viewModel: EquipmentPlacementViewModel,
    modifier: Modifier = Modifier,
    onViewDecisionSupport: () -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val input = viewModel.runtimeInput(placement)
    val startBlocked = viewModel.startBlockReason()
    val snapshot = ArHudContent.workingCopyStatus(
        placement.instances,
        placement.runtimeById,
        presentation = viewModel.equipmentPresentation()
    )
    ModalBottomSheet(
        onDismissRequest = { viewModel.closeBehaviorSheet() },
        sheetState = sheetState,
        modifier = modifier.testTag("ar_behavior_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            Text(
                text = stringResource(R.string.ar_behavior_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.testTag("ar_behavior_title")
            )
            placement.tooColdAdvisory?.let { advisory ->
                com.greenhands.app.heat.advisory.TooColdAdvisoryBanner(
                    advisory = advisory,
                    onViewSuggestions = { viewModel.onViewTooColdSuggestions(onViewDecisionSupport) }
                )
            }
            Text(
                text = input.sourceLabel(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("ar_behavior_input_source")
            )
            if (placement.previewMode == PreviewMode.MY_GREENHOUSE) {
                Text(
                    text = ArHudContent.liveStatusLabel(placement.liveEnvironment),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("ar_behavior_live_status")
                )
                if (placement.liveEnvironment.uiState == com.greenhands.app.ar.domain.LiveSensorUiState.DISCONNECTED) {
                    Text(
                        text = com.greenhands.app.telemetry.TelemetryCopy.RETRY_OR_DEMO,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("ar_behavior_sensor_retry")
                    )
                }
                Text(
                    text = ArHudContent.outsideTemperatureLabel(placement.weatherUi),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("ar_behavior_outside_temperature")
                )
                Text(
                    text = ArHudContent.outsideHumidityLabel(placement.weatherUi),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("ar_behavior_outside_humidity")
                )
                if (ArHudContent.outsideLocationLabel(placement.weatherUi).isNotBlank()) {
                    Text(
                        text = ArHudContent.outsideLocationLabel(placement.weatherUi),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag("ar_behavior_outside_location")
                    )
                }
                Text(
                    text = com.greenhands.app.telemetry.WeatherCopy.MODELLED_OPEN_METEO,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("ar_behavior_outside_source")
                )
                Text(
                    text = ArHudContent.outsideStatusLabel(placement.weatherUi),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("ar_behavior_outside_status")
                )
            }
            Text(
                text = stringResource(
                    R.string.ar_behavior_temperature,
                    if (placement.previewMode == PreviewMode.MY_GREENHOUSE) {
                        ArHudContent.liveTemperatureLabel(placement.liveEnvironment)
                    } else {
                        ArHudContent.temperatureReadingLabel(
                            if (placement.behaviorRunning) {
                                placement.displayedTemperatureC ?: input.temperatureC
                            } else {
                                input.temperatureC
                            }
                        )
                    }
                ),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("ar_behavior_temperature")
            )
            Text(
                text = stringResource(
                    R.string.ar_behavior_humidity,
                    if (placement.previewMode == PreviewMode.MY_GREENHOUSE) {
                        ArHudContent.liveHumidityLabel(placement.liveEnvironment)
                    } else {
                        ArHudContent.humidityReadingLabel(
                            if (placement.behaviorRunning) {
                                placement.displayedHumidityPercent ?: input.humidityPercent
                            } else {
                                input.humidityPercent
                            }
                        )
                    }
                ),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("ar_behavior_humidity")
            )
            if (input.source == EquipmentRuntimeInputSource.DEMO_SIMULATED) {
                Text(
                    text = ArRuntimeCopy.SIMULATED_DISCLAIMER,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("ar_behavior_simulated_disclaimer")
                )
                if (placement.behaviorRunning) {
                    Text(
                        text = VisualizationCopy.PREDICTED_UNCALIBRATED,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("ar_behavior_predicted_uncalibrated")
                    )
                }
                SimulatedClimateControls(
                    temperatureInput = placement.simulatedTemperatureInput,
                    humidityInput = placement.simulatedHumidityInput,
                    temperatureC = placement.simulatedTemperatureC,
                    humidityPercent = placement.simulatedHumidityPercent,
                    onTemperatureSlider = { viewModel.setSimulatedTemperature(it) },
                    onHumiditySlider = { viewModel.setSimulatedHumidity(it) },
                    onTemperatureInput = { viewModel.setSimulatedTemperatureInput(it) },
                    onHumidityInput = { viewModel.setSimulatedHumidityInput(it) }
                )
                FanPadDemoControls(
                    placement = placement,
                    viewModel = viewModel
                )
            }
            if (placement.previewMode == PreviewMode.MY_GREENHOUSE) {
                FanPadMyGreenhouseControls(
                    placement = placement,
                    viewModel = viewModel
                )
                ManufacturerSpecStatus(
                    placement = placement,
                    onEnter = { viewModel.openManufacturerSpecsSheet(reopenBehavior = true) }
                )
            }
            Text(
                text = stringResource(
                    R.string.ar_behavior_status,
                    if (placement.behaviorRunning) ArRuntimeCopy.RUNNING else ArRuntimeCopy.NOT_RUNNING
                ),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("ar_behavior_runtime_status")
            )
            snapshot.aggregates.filter { it.count > 0 }.forEach { line ->
                Text(
                    text = line.displayLine(),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("ar_behavior_summary_${line.type.name.lowercase()}")
                )
            }
            if (EquipmentPresentation.showsNotEvaluatedExplanation(viewModel.equipmentPresentation())) {
                Text(
                    text = EquipmentPresentationCopy.NOT_EVALUATED_EXPLANATION,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("ar_behavior_not_evaluated_reason")
                )
            }
            placement.behaviorError?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("ar_behavior_error")
                )
            }
            CirculationEffectPlanner.plan(
                visualizationRunning = placement.behaviorRunning,
                instances = placement.instances,
                runtimeById = placement.runtimeById,
                snapshot = placement.physicsSnapshot,
                thresholds = viewModel.resolvedClimate().runtimeThresholds(),
                targetTemperatureC = viewModel.resolvedClimate().optimalTemperatureC
            ).limitation?.let { limitation ->
                Text(
                    text = limitation,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("ar_behavior_circulation_limitation")
                )
            }
            ExhaustEffectPlanner.plan(
                visualizationRunning = placement.behaviorRunning,
                instances = placement.instances,
                runtimeById = placement.runtimeById,
                snapshot = placement.physicsSnapshot,
                thresholds = viewModel.resolvedClimate().runtimeThresholds(),
                targetTemperatureC = viewModel.resolvedClimate().optimalTemperatureC
            ).limitation?.let { limitation ->
                Text(
                    text = limitation,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("ar_behavior_exhaust_limitation")
                )
            }
            FoggerEffectPlanner.plan(
                visualizationRunning = placement.behaviorRunning,
                instances = placement.instances,
                runtimeById = placement.runtimeById,
                snapshot = placement.physicsSnapshot,
                thresholds = viewModel.resolvedClimate().runtimeThresholds()
            ).limitation?.let { limitation ->
                Text(
                    text = limitation,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("ar_behavior_fog_limitation")
                )
            }
            if (placement.behaviorRunning &&
                placement.instances.any { it.type == com.greenhands.app.equipment.model.EquipmentType.FOGGER && !it.isGhost }
            ) {
                Text(
                    text = VisualizationCopy.FOG_MIST_QUALITATIVE,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("ar_behavior_fog_qualitative")
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ar_airflow_fog_row")
            ) {
                Text(
                    text = stringResource(R.string.ar_airflow_fog_visible),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall
                )
                Switch(
                    checked = placement.airflowFogVisible,
                    onCheckedChange = { viewModel.setAirflowFogVisible(it) },
                    modifier = Modifier.testTag("ar_airflow_fog_toggle")
                )
            }
            val padResolved = viewModel.resolvedFanPadBoundary(placement)
            (padResolved.missingInputs + listOfNotNull(padResolved.layoutExplanation))
                .distinct()
                .forEach { missing ->
                    Text(
                        text = missing,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("ar_behavior_fan_pad_missing")
                    )
                }
            if (placement.behaviorRunning) {
                Text(
                    text = viewModel.visualizationProbeCaption(placement),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("ar_behavior_circulation_probe_details")
                )
            }
            if (placement.previewMode == PreviewMode.MY_GREENHOUSE &&
                !placement.quantitativeVisualizationComplete() &&
                placement.behaviorRunning
            ) {
                Text(
                    text = ManufacturerSpecCopy.INCOMPLETE,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("ar_behavior_specs_incomplete_running")
                )
            }
            if (startBlocked != null && !placement.behaviorRunning) {
                Text(
                    text = startBlocked,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("ar_behavior_start_blocked")
                )
            }
            Spacer(Modifier.height(Spacing.xs))
            PrimaryActionButton(
                text = stringResource(R.string.ar_start_behavior),
                onClick = { viewModel.startBehavior() },
                enabled = startBlocked == null && !placement.behaviorRunning,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ar_start_behavior")
            )
            SecondaryActionButton(
                text = stringResource(R.string.ar_stop_behavior),
                onClick = { viewModel.stopBehavior() },
                enabled = placement.behaviorRunning,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ar_stop_behavior")
            )
        }
    }
}

@Composable
private fun SimulatedClimateControls(
    temperatureInput: String,
    humidityInput: String,
    temperatureC: Double?,
    humidityPercent: Double?,
    onTemperatureSlider: (Double) -> Unit,
    onHumiditySlider: (Double) -> Unit,
    onTemperatureInput: (String) -> Unit,
    onHumidityInput: (String) -> Unit
) {
    val temp = (temperatureC ?: TEMP_HARD_MIN).toFloat()
    val rh = (humidityPercent ?: RH_HARD_MIN).toFloat()
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            text = stringResource(R.string.ar_behavior_sim_temp),
            style = MaterialTheme.typography.labelMedium
        )
        Slider(
            value = temp.coerceIn(TEMP_HARD_MIN.toFloat(), TEMP_HARD_MAX.toFloat()),
            onValueChange = { onTemperatureSlider(it.toDouble()) },
            valueRange = TEMP_HARD_MIN.toFloat()..TEMP_HARD_MAX.toFloat(),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("ar_behavior_temp_slider")
        )
        OutlinedTextField(
            value = temperatureInput,
            onValueChange = onTemperatureInput,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("ar_behavior_temp_input"),
            label = { Text(stringResource(R.string.ar_behavior_sim_temp)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
        Text(
            text = stringResource(R.string.ar_behavior_sim_rh),
            style = MaterialTheme.typography.labelMedium
        )
        Slider(
            value = rh.coerceIn(RH_HARD_MIN.toFloat(), RH_HARD_MAX.toFloat()),
            onValueChange = { onHumiditySlider(it.toDouble()) },
            valueRange = RH_HARD_MIN.toFloat()..RH_HARD_MAX.toFloat(),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("ar_behavior_rh_slider")
        )
        OutlinedTextField(
            value = humidityInput,
            onValueChange = onHumidityInput,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("ar_behavior_rh_input"),
            label = { Text(stringResource(R.string.ar_behavior_sim_rh)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
    }
}

@Composable
private fun FanPadDemoControls(
    placement: PlacementUiState,
    viewModel: EquipmentPlacementViewModel
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        modifier = Modifier.testTag("ar_behavior_fan_pad_demo")
    ) {
        Text(
            text = stringResource(R.string.ar_fan_pad_title),
            style = MaterialTheme.typography.titleSmall
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.ar_fan_pad_enabled), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            Switch(
                checked = placement.demoFanPadEnabled,
                onCheckedChange = { viewModel.setDemoFanPadEnabled(it) },
                modifier = Modifier.testTag("ar_behavior_fan_pad_enabled")
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.ar_fan_pad_wet), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            Switch(
                checked = placement.demoFanPadWetActive,
                onCheckedChange = { viewModel.setDemoFanPadWetActive(it) },
                modifier = Modifier.testTag("ar_behavior_fan_pad_wet")
            )
        }
        Text(FanPadCopy.DEMO_OUTSIDE_WEATHER, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.ar_fan_pad_outside_t), style = MaterialTheme.typography.labelMedium)
        Slider(
            value = placement.demoOutsideTemperatureC.toFloat().coerceIn(TEMP_HARD_MIN.toFloat(), TEMP_HARD_MAX.toFloat()),
            onValueChange = { viewModel.setDemoOutsideTemperature(it.toDouble()) },
            valueRange = TEMP_HARD_MIN.toFloat()..TEMP_HARD_MAX.toFloat(),
            modifier = Modifier.fillMaxWidth().testTag("ar_behavior_fan_pad_outside_t_slider")
        )
        OutlinedTextField(
            value = placement.demoOutsideTemperatureInput,
            onValueChange = { viewModel.setDemoOutsideTemperatureInput(it) },
            modifier = Modifier.fillMaxWidth().testTag("ar_behavior_fan_pad_outside_t"),
            label = { Text(stringResource(R.string.ar_fan_pad_outside_t)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
        Text(stringResource(R.string.ar_fan_pad_outside_rh), style = MaterialTheme.typography.labelMedium)
        Slider(
            value = placement.demoOutsideHumidityPercent.toFloat().coerceIn(RH_HARD_MIN.toFloat(), RH_HARD_MAX.toFloat()),
            onValueChange = { viewModel.setDemoOutsideHumidity(it.toDouble()) },
            valueRange = RH_HARD_MIN.toFloat()..RH_HARD_MAX.toFloat(),
            modifier = Modifier.fillMaxWidth().testTag("ar_behavior_fan_pad_outside_rh_slider")
        )
        OutlinedTextField(
            value = placement.demoOutsideHumidityInput,
            onValueChange = { viewModel.setDemoOutsideHumidityInput(it) },
            modifier = Modifier.fillMaxWidth().testTag("ar_behavior_fan_pad_outside_rh"),
            label = { Text(stringResource(R.string.ar_fan_pad_outside_rh)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
        Text(FanPadCopy.DEMO_PAD_EFFICIENCY, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(FanPadCopy.DEMO_EFFICIENCY_ASSUMPTION, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(
            value = placement.demoFanPadEfficiency.toFloat().coerceIn(0.01f, 1f),
            onValueChange = { viewModel.setDemoFanPadEfficiency(it.toDouble()) },
            valueRange = 0.01f..1f,
            modifier = Modifier.fillMaxWidth().testTag("ar_behavior_fan_pad_efficiency_slider")
        )
        OutlinedTextField(
            value = placement.demoFanPadEfficiencyInput,
            onValueChange = { viewModel.setDemoFanPadEfficiencyInput(it) },
            modifier = Modifier.fillMaxWidth().testTag("ar_behavior_fan_pad_efficiency"),
            label = { Text(stringResource(R.string.ar_fan_pad_efficiency)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
        Text(VisualizationCopy.PREDICTED_UNCALIBRATED, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FanPadMyGreenhouseControls(
    placement: PlacementUiState,
    viewModel: EquipmentPlacementViewModel
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        modifier = Modifier.testTag("ar_behavior_fan_pad_mygh")
    ) {
        Text(text = stringResource(R.string.ar_fan_pad_title), style = MaterialTheme.typography.titleSmall)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.ar_fan_pad_present), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            Switch(
                checked = placement.myPadPresent == true,
                onCheckedChange = { viewModel.setMyPadPresent(it) },
                modifier = Modifier.testTag("ar_behavior_my_pad_present")
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.ar_fan_pad_wet), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            Switch(
                checked = placement.myPadWetActive,
                onCheckedChange = { viewModel.setMyPadWetActive(it) },
                modifier = Modifier.testTag("ar_behavior_my_pad_wet")
            )
        }
        OutlinedTextField(
            value = placement.myPadWidthInput,
            onValueChange = { viewModel.setMyPadWidthInput(it) },
            modifier = Modifier.fillMaxWidth().testTag("ar_behavior_my_pad_width"),
            label = { Text(stringResource(R.string.ar_fan_pad_width)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
        OutlinedTextField(
            value = placement.myPadHeightInput,
            onValueChange = { viewModel.setMyPadHeightInput(it) },
            modifier = Modifier.fillMaxWidth().testTag("ar_behavior_my_pad_height"),
            label = { Text(stringResource(R.string.ar_fan_pad_height)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
        OutlinedTextField(
            value = placement.myPadEfficiencyInput,
            onValueChange = { viewModel.setMyPadEfficiencyInput(it) },
            modifier = Modifier.fillMaxWidth().testTag("ar_behavior_my_pad_efficiency"),
            label = { Text(stringResource(R.string.ar_fan_pad_efficiency)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
        Text(stringResource(R.string.ar_fan_pad_wall), style = MaterialTheme.typography.labelMedium)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            TextButton(
                onClick = { viewModel.setMyPadWall(FanPadWallSelection.AUTO) },
                modifier = Modifier.testTag("ar_behavior_my_pad_wall_auto")
            ) {
                Text(
                    stringResource(R.string.ar_fan_pad_wall_auto),
                    style = if (placement.myPadWall == FanPadWallSelection.AUTO) {
                        MaterialTheme.typography.labelLarge
                    } else {
                        MaterialTheme.typography.labelSmall
                    }
                )
            }
            TextButton(
                onClick = { viewModel.setMyPadWall(FanPadWallSelection.NEAR_Y0) },
                modifier = Modifier.testTag("ar_behavior_my_pad_wall_near")
            ) {
                Text(
                    stringResource(R.string.ar_fan_pad_wall_near),
                    style = if (placement.myPadWall == FanPadWallSelection.NEAR_Y0) {
                        MaterialTheme.typography.labelLarge
                    } else {
                        MaterialTheme.typography.labelSmall
                    }
                )
            }
            TextButton(
                onClick = { viewModel.setMyPadWall(FanPadWallSelection.FAR_Y_LENGTH) },
                modifier = Modifier.testTag("ar_behavior_my_pad_wall_far")
            ) {
                Text(
                    stringResource(R.string.ar_fan_pad_wall_far),
                    style = if (placement.myPadWall == FanPadWallSelection.FAR_Y_LENGTH) {
                        MaterialTheme.typography.labelLarge
                    } else {
                        MaterialTheme.typography.labelSmall
                    }
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            TextButton(
                onClick = { viewModel.setMyPadWall(FanPadWallSelection.NEAR_X0) },
                modifier = Modifier.testTag("ar_behavior_my_pad_wall_near_x")
            ) {
                Text(
                    stringResource(R.string.ar_fan_pad_wall_near_x),
                    style = if (placement.myPadWall == FanPadWallSelection.NEAR_X0) {
                        MaterialTheme.typography.labelLarge
                    } else {
                        MaterialTheme.typography.labelSmall
                    }
                )
            }
            TextButton(
                onClick = { viewModel.setMyPadWall(FanPadWallSelection.FAR_X_WIDTH) },
                modifier = Modifier.testTag("ar_behavior_my_pad_wall_far_x")
            ) {
                Text(
                    stringResource(R.string.ar_fan_pad_wall_far_x),
                    style = if (placement.myPadWall == FanPadWallSelection.FAR_X_WIDTH) {
                        MaterialTheme.typography.labelLarge
                    } else {
                        MaterialTheme.typography.labelSmall
                    }
                )
            }
        }
    }
}

@Composable
private fun ManufacturerSpecStatus(
    placement: PlacementUiState,
    onEnter: () -> Unit
) {
    val missing = placement.missingManufacturerSpecs()
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        if (missing.isNotEmpty()) {
            Text(
                text = ManufacturerSpecCopy.INCOMPLETE,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("ar_behavior_specs_incomplete")
            )
            missing.forEachIndexed { index, label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("ar_behavior_specs_missing_$index")
                )
            }
        } else if (placement.placedTypes().isNotEmpty()) {
            Text(
                text = ManufacturerSpecCopy.SAVED,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("ar_behavior_specs_complete")
            )
        }
        SecondaryActionButton(
            text = ManufacturerSpecCopy.ENTER,
            onClick = onEnter,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("ar_behavior_enter_specs")
        )
    }
}
