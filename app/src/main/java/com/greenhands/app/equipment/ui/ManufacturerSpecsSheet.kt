package com.greenhands.app.equipment.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.greenhands.app.equipment.model.EquipmentType
import com.greenhands.app.equipment.model.ManufacturerSpecCopy
import com.greenhands.app.ui.components.PrimaryActionButton
import com.greenhands.app.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManufacturerSpecsSheet(
    placement: PlacementUiState,
    viewModel: EquipmentPlacementViewModel,
    modifier: Modifier = Modifier
) {
    if (!placement.showManufacturerSpecsSheet) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val placed = placement.placedTypes()
    ModalBottomSheet(
        onDismissRequest = { viewModel.closeManufacturerSpecsSheet() },
        sheetState = sheetState,
        modifier = modifier.testTag("manufacturer_specs_sheet")
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
                text = ManufacturerSpecCopy.TITLE,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.testTag("manufacturer_specs_title")
            )
            Text(
                text = ManufacturerSpecCopy.UNIT_NOTE,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("manufacturer_specs_unit_note")
            )
            val missing = placement.missingManufacturerSpecs()
            if (missing.isNotEmpty()) {
                Text(
                    text = ManufacturerSpecCopy.INCOMPLETE,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("manufacturer_specs_incomplete")
                )
                missing.forEachIndexed { index, label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag("manufacturer_specs_missing_$index")
                    )
                }
            } else if (placed.isNotEmpty()) {
                Text(
                    text = ManufacturerSpecCopy.SAVED,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("manufacturer_specs_complete")
                )
            }
            if (EquipmentType.CIRCULATION_FAN in placed || placed.isEmpty()) {
                SpecField(
                    value = placement.circulationRatedCfmInput,
                    label = ManufacturerSpecCopy.CIRCULATION_CFM,
                    tag = "spec_circ_cfm"
                ) { viewModel.updateManufacturerSpecInput(circCfm = it) }
                SpecField(
                    value = placement.circulationRatedM3sInput,
                    label = ManufacturerSpecCopy.CIRCULATION_M3S,
                    tag = "spec_circ_m3s"
                ) { viewModel.updateManufacturerSpecInput(circM3s = it) }
                SpecField(
                    value = placement.circulationThrowRadiusMInput,
                    label = ManufacturerSpecCopy.CIRCULATION_THROW_FIELD,
                    tag = "spec_circ_throw"
                ) { viewModel.updateManufacturerSpecInput(circThrowM = it) }
            }
            if (EquipmentType.EXHAUST_FAN in placed || placed.isEmpty()) {
                SpecField(
                    value = placement.exhaustRatedM3sInput,
                    label = ManufacturerSpecCopy.EXHAUST_M3S,
                    tag = "spec_exh_m3s"
                ) { viewModel.updateManufacturerSpecInput(exhaustM3s = it) }
                SpecField(
                    value = placement.exhaustRatedCfmInput,
                    label = ManufacturerSpecCopy.EXHAUST_CFM,
                    tag = "spec_exh_cfm"
                ) { viewModel.updateManufacturerSpecInput(exhaustCfm = it) }
            }
            if (EquipmentType.FOGGER in placed || placed.isEmpty()) {
                SpecField(
                    value = placement.foggerFlowKgSInput,
                    label = ManufacturerSpecCopy.FOGGER_KG_S,
                    tag = "spec_fog_kgs"
                ) { viewModel.updateManufacturerSpecInput(fogFlowKgS = it) }
                SpecField(
                    value = placement.foggerFlowLphInput,
                    label = ManufacturerSpecCopy.FOGGER_LPH,
                    tag = "spec_fog_lph"
                ) { viewModel.updateManufacturerSpecInput(fogFlowLph = it) }
                SpecField(
                    value = placement.foggerSprayRadiusMInput,
                    label = ManufacturerSpecCopy.FOGGER_SPRAY_FIELD,
                    tag = "spec_fog_spray"
                ) { viewModel.updateManufacturerSpecInput(fogSprayM = it) }
                SpecField(
                    value = placement.foggerEtaInput,
                    label = ManufacturerSpecCopy.FOGGER_ETA_OPTIONAL,
                    tag = "spec_fog_eta"
                ) { viewModel.updateManufacturerSpecInput(fogEta = it) }
            }
            PrimaryActionButton(
                text = ManufacturerSpecCopy.SAVED,
                onClick = { viewModel.closeManufacturerSpecsSheet() },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("manufacturer_specs_done")
            )
        }
    }
}

@Composable
private fun SpecField(
    value: String,
    label: String,
    tag: String,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag),
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    )
}
