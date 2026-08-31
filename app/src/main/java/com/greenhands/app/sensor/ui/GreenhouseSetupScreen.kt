package com.greenhands.app.sensor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import com.greenhands.app.R
import com.greenhands.app.sensor.model.GreenhousePhysicalConfig
import com.greenhands.app.ui.components.DemoNotice
import com.greenhands.app.ui.components.InfoCard
import com.greenhands.app.ui.components.PrimaryActionButton
import com.greenhands.app.ui.components.StatusChip
import com.greenhands.app.ui.components.WarningPanel
import com.greenhands.app.ui.components.screenHorizontalPadding
import com.greenhands.app.ui.theme.Spacing
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GreenhouseSetupScreen(
    ui: SensorPlacementUiState,
    onCreateGreenhouse: (GreenhousePhysicalConfig) -> Boolean,
    onContinueToScan: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    var lengthInput by remember(ui.physicalConfig.lengthMeters) {
        mutableStateOf(formatInput(ui.physicalConfig.lengthMeters))
    }
    var widthInput by remember(ui.physicalConfig.widthMeters) {
        mutableStateOf(formatInput(ui.physicalConfig.widthMeters))
    }
    var heightInput by remember(ui.physicalConfig.heightMeters) {
        mutableStateOf(formatInput(ui.physicalConfig.heightMeters))
    }
    var cellInput by remember(ui.physicalConfig.cellSizeMeters) {
        mutableStateOf(formatInput(ui.physicalConfig.cellSizeMeters))
    }
    var localError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CenterAlignedTopAppBar(
                windowInsets = WindowInsets.statusBars,
                title = {
                    Text(
                        text = stringResource(R.string.sensor_setup_title),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics { heading() },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.size(Spacing.touch)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.cd_back)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = screenHorizontalPadding())
                .padding(
                    top = Spacing.afterAppBar,
                    bottom = Spacing.xxxl + Spacing.xxl + Spacing.navClearance
                )
                .testTag("greenhouse_setup"),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SensorWorkflowHeader(
                subtitle = stringResource(R.string.sensor_setup_subtitle),
                activeStepIndex = 0
            )
            Spacer(Modifier.height(Spacing.md))
            DemoNotice(
                text = stringResource(R.string.sensor_setup_notice),
                modifier = Modifier.testTag("setup_notice")
            )
            Spacer(Modifier.height(Spacing.section))
            InfoCard(modifier = Modifier.testTag("setup_form_card")) {
                Text(
                    text = stringResource(R.string.sensor_setup_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() }
                )
                Spacer(Modifier.height(Spacing.md))
                MeterField(
                    label = stringResource(R.string.sensor_setup_length),
                    value = lengthInput,
                    onValueChange = {
                        lengthInput = it
                        localError = null
                    },
                    testTag = "setup_length"
                )
                Spacer(Modifier.height(Spacing.md))
                MeterField(
                    label = stringResource(R.string.sensor_setup_width),
                    value = widthInput,
                    onValueChange = {
                        widthInput = it
                        localError = null
                    },
                    testTag = "setup_width"
                )
                Spacer(Modifier.height(Spacing.md))
                MeterField(
                    label = stringResource(R.string.sensor_setup_height),
                    value = heightInput,
                    onValueChange = {
                        heightInput = it
                        localError = null
                    },
                    testTag = "setup_height"
                )
                Spacer(Modifier.height(Spacing.md))
                MeterField(
                    label = stringResource(R.string.sensor_setup_cell_size),
                    value = cellInput,
                    onValueChange = {
                        cellInput = it
                        localError = null
                    },
                    testTag = "setup_cell_size"
                )
            }
            val errorText = localError ?: ui.configError
            if (errorText != null) {
                Spacer(Modifier.height(Spacing.md))
                WarningPanel(
                    title = stringResource(R.string.sensor_setup_error_title),
                    body = errorText,
                    error = true,
                    modifier = Modifier.testTag("setup_error")
                )
            }
            Spacer(Modifier.height(Spacing.section))
            PrimaryActionButton(
                text = stringResource(R.string.sensor_setup_create),
                onClick = {
                    val length = lengthInput.toDoubleOrNull()
                    val width = widthInput.toDoubleOrNull()
                    val height = heightInput.toDoubleOrNull()
                    val cell = cellInput.toDoubleOrNull()
                    if (length == null || width == null || height == null || cell == null) {
                        localError = "Enter valid numbers for length, width, height, and cell size."
                        return@PrimaryActionButton
                    }
                    localError = null
                    onCreateGreenhouse(
                        GreenhousePhysicalConfig(
                            lengthMeters = length,
                            widthMeters = width,
                            heightMeters = height,
                            cellSizeMeters = cell
                        )
                    )
                },
                modifier = Modifier.testTag("setup_create")
            )
            if (ui.greenhouseConfigured) {
                Spacer(Modifier.height(Spacing.section))
                StatusChip(
                    text = stringResource(R.string.sensor_setup_ready_chip),
                    modifier = Modifier.testTag("setup_ready_chip")
                )
                Spacer(Modifier.height(Spacing.md))
                InfoCard(modifier = Modifier.testTag("setup_preview_card")) {
                    Text(
                        text = stringResource(R.string.sensor_setup_preview_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() }
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        text = stringResource(R.string.sensor_setup_preview_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(Spacing.md))
                    VirtualGreenhouseSchematic(
                        greenhouse = ui.greenhouse,
                        config = ui.physicalConfig
                    )
                }
                Spacer(Modifier.height(Spacing.section))
                PrimaryActionButton(
                    text = stringResource(R.string.sensor_setup_continue),
                    onClick = onContinueToScan,
                    modifier = Modifier.testTag("setup_continue")
                )
            }
            Spacer(Modifier.height(Spacing.section))
        }
    }
}

@Composable
private fun MeterField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .testTag(testTag),
            label = { Text(label) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
        Text(
            text = stringResource(R.string.sensor_setup_unit_m),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatInput(value: Double): String {
    val asInt = value.toInt()
    return if (value == asInt.toDouble()) asInt.toString()
    else String.format(Locale.US, "%.1f", value)
}
