package com.greenhands.app.sensor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.greenhands.app.R
import com.greenhands.app.sensor.model.Greenhouse
import com.greenhands.app.sensor.model.ScanPhase
import com.greenhands.app.ui.components.DemoNotice
import com.greenhands.app.ui.components.InfoCard
import com.greenhands.app.ui.components.PrimaryActionButton
import com.greenhands.app.ui.components.SecondaryActionButton
import com.greenhands.app.ui.components.StatusChip
import com.greenhands.app.ui.components.screenHorizontalPadding
import com.greenhands.app.ui.theme.ForestEmerald
import com.greenhands.app.ui.theme.NightBg
import com.greenhands.app.ui.theme.NightBorder
import com.greenhands.app.ui.theme.NightElevated
import com.greenhands.app.ui.theme.Spacing

/**
 * Scan Greenhouse uses its own fillMaxSize Scaffold (not [com.greenhands.app.ui.components.ScreenScaffold])
 * so the scroll viewport is bounded to the NavHost area. ScreenScaffold's Scaffold has no fillMaxSize,
 * so it grew with content, left verticalScroll with zero scroll range, and clipped below the phone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanGreenhouseScreen(
    ui: SensorPlacementUiState,
    onStartScan: () -> Unit,
    onResetScan: () -> Unit,
    onContinueToPlacement: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    val scan = ui.scan
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CenterAlignedTopAppBar(
                windowInsets = WindowInsets.statusBars,
                title = {
                    Text(
                        text = stringResource(R.string.sensor_scan_title),
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
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = screenHorizontalPadding())
                .padding(
                    top = Spacing.afterAppBar,
                    // Clear the bottom of the scroll content so Start Scan sits above the app bottom bar.
                    bottom = Spacing.xxxl + Spacing.xxl + Spacing.navClearance
                )
                .testTag("scan_greenhouse"),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SensorWorkflowHeader(
                subtitle = stringResource(R.string.sensor_scan_subtitle),
                activeStepIndex = 1
            )
            Spacer(Modifier.height(Spacing.md))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                StatusChip(
                    text = stringResource(R.string.sensor_scan_simulated_chip),
                    modifier = Modifier.testTag("scan_mode_chip")
                )
                StatusChip(
                    text = stringResource(R.string.sensor_scan_emulator_chip),
                    warning = true
                )
            }
            Spacer(Modifier.height(Spacing.md))
            DemoNotice(
                text = stringResource(R.string.sensor_scan_notice),
                modifier = Modifier.testTag("scan_notice")
            )
            Spacer(Modifier.height(Spacing.section))
            InfoCard(modifier = Modifier.testTag("scan_visualization_card")) {
                Text(
                    text = stringResource(R.string.sensor_scan_viz_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() }
                )
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    text = scanStatusText(scan.phase),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("scan_status")
                )
                Spacer(Modifier.height(Spacing.md))
                SimulatedGreenhouseGrid(
                    greenhouse = ui.greenhouse,
                    scanning = scan.isScanning,
                    detected = scan.phase == ScanPhase.DETECTED,
                    modifier = Modifier.testTag("scan_grid")
                )
                if (scan.isScanning || scan.phase == ScanPhase.DETECTED) {
                    Spacer(Modifier.height(Spacing.md))
                    LinearProgressIndicator(
                        progress = { scan.progressPercent / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("scan_progress_bar"),
                        color = ForestEmerald
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        text = stringResource(R.string.sensor_scan_progress_value, scan.progressPercent),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.testTag("scan_progress_value")
                    )
                }
            }
            Spacer(Modifier.height(Spacing.section))
            InfoCard {
                Text(
                    text = stringResource(R.string.sensor_scan_tips_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    text = stringResource(R.string.sensor_scan_tips_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(Spacing.section))
            if (scan.phase == ScanPhase.IDLE) {
                PrimaryActionButton(
                    text = stringResource(R.string.sensor_scan_start),
                    onClick = onStartScan,
                    modifier = Modifier.testTag("scan_start")
                )
            } else if (scan.isScanning) {
                PrimaryActionButton(
                    text = stringResource(R.string.sensor_scan_scanning),
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.testTag("scan_start")
                )
            } else {
                PrimaryActionButton(
                    text = stringResource(R.string.sensor_scan_continue),
                    onClick = onContinueToPlacement,
                    enabled = scan.canContinueToPlacement,
                    modifier = Modifier.testTag("scan_continue")
                )
                Spacer(Modifier.height(Spacing.related))
                SecondaryActionButton(
                    text = stringResource(R.string.sensor_scan_reset),
                    onClick = onResetScan,
                    modifier = Modifier.testTag("scan_reset")
                )
            }
        }
    }
}

@Composable
private fun scanStatusText(phase: ScanPhase): String = when (phase) {
    ScanPhase.IDLE -> stringResource(R.string.sensor_scan_status_idle)
    ScanPhase.SCANNING -> stringResource(R.string.sensor_scan_status_scanning)
    ScanPhase.DETECTED -> stringResource(R.string.sensor_scan_status_detected)
}

@Composable
private fun SimulatedGreenhouseGrid(
    greenhouse: Greenhouse,
    scanning: Boolean,
    detected: Boolean,
    modifier: Modifier = Modifier
) {
    val columns = greenhouse.widthCells
    val rows = greenhouse.heightCells
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(columns.toFloat() / rows.toFloat())
            .background(NightBg),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val cellWidth = size.width / columns
            val cellHeight = size.height / rows
            val fill = when {
                detected -> ForestEmerald.copy(alpha = 0.18f)
                scanning -> NightElevated.copy(alpha = 0.9f)
                else -> NightElevated
            }
            drawRect(color = fill)
            for (x in 0..columns) {
                val px = x * cellWidth
                drawLine(NightBorder, Offset(px, 0f), Offset(px, size.height), strokeWidth = 1f)
            }
            for (y in 0..rows) {
                val py = y * cellHeight
                drawLine(NightBorder, Offset(0f, py), Offset(size.width, py), strokeWidth = 1f)
            }
            if (detected) {
                drawRect(
                    color = ForestEmerald.copy(alpha = 0.7f),
                    topLeft = Offset(2f, 2f),
                    size = Size(size.width - 4f, size.height - 4f),
                    style = Stroke(width = 3f)
                )
            }
        }
    }
}
