package com.greenhands.app.heat.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.greenhands.app.R
import com.greenhands.app.heat.data.HeatSessionRecord
import com.greenhands.app.heat.model.formatOneDecimal
import com.greenhands.app.preview3d.model.PreviewMode
import com.greenhands.app.ui.components.InfoCard
import com.greenhands.app.ui.components.PrimaryActionButton
import com.greenhands.app.ui.components.ScreenScaffold
import com.greenhands.app.ui.components.ScrollScreen
import com.greenhands.app.ui.theme.Spacing

@Composable
fun ActiveConfigurationScreen(
    record: HeatSessionRecord?,
    onBack: () -> Unit,
    onResume: () -> Unit
) {
    ScreenScaffold(title = stringResource(R.string.heat_active_config), onBack = onBack) { padding ->
        if (record == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .testTag("active_config_empty"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.heat_no_active_config),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.xl)
                )
            }
        } else {
            ScrollScreen(Modifier.fillMaxSize().padding(padding).testTag("active_config")) {
                SessionConfigurationCard(record = record, modifier = Modifier.testTag("active_config_card"))
                if (record.cropId != null) {
                    Spacer(Modifier.height(Spacing.section))
                    PrimaryActionButton(
                        text = stringResource(R.string.action_continue_config),
                        onClick = onResume,
                        modifier = Modifier.testTag("crops_resume")
                    )
                }
            }
        }
    }
}

@Composable
fun RecentConfigurationsScreen(
    records: List<HeatSessionRecord>,
    pendingDeleteId: String?,
    onBack: () -> Unit,
    onDelete: (String) -> Unit,
    onConfirmDelete: () -> Unit,
    onCancelDelete: () -> Unit
) {
    ScreenScaffold(title = stringResource(R.string.heat_recent_config), onBack = onBack) { padding ->
        if (records.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .testTag("recent_config_empty"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.heat_no_recent_config),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.xl)
                )
            }
        } else {
            ScrollScreen(Modifier.fillMaxSize().padding(padding).testTag("recent_config")) {
                records.forEach { record ->
                    SessionConfigurationCard(
                        record = record,
                        onDelete = { onDelete(record.id) },
                        modifier = Modifier.testTag("recent_config_${record.id}")
                    )
                    Spacer(Modifier.height(Spacing.related))
                }
            }
        }
    }
    if (pendingDeleteId != null) {
        AlertDialog(
            onDismissRequest = onCancelDelete,
            title = { Text(stringResource(R.string.heat_delete_recent_title)) },
            text = { Text(stringResource(R.string.heat_delete_recent_body)) },
            confirmButton = {
                TextButton(onClick = onConfirmDelete, modifier = Modifier.testTag("recent_delete_confirm")) {
                    Text(stringResource(R.string.heat_delete_recent_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = onCancelDelete, modifier = Modifier.testTag("recent_delete_cancel")) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun SessionConfigurationCard(
    record: HeatSessionRecord,
    modifier: Modifier = Modifier,
    onDelete: (() -> Unit)? = null
) {
    InfoCard(modifier = modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                record.cropDisplayName?.let {
                    Text(stringResource(R.string.dashboard_selected_crop), style = MaterialTheme.typography.labelLarge)
                    Text(it, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(Spacing.sm))
                }
                record.stageDisplayName?.let {
                    Text(stringResource(R.string.dashboard_selected_stage), style = MaterialTheme.typography.labelLarge)
                    Text(it, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(Spacing.sm))
                }
                if (record.dayTemperatureC != null || record.nightTemperatureC != null) {
                    Text(
                        stringResource(
                            R.string.dashboard_day_night,
                            record.dayTemperatureC?.let { formatOneDecimal(it) } ?: "—",
                            record.nightTemperatureC?.let { formatOneDecimal(it) } ?: "—"
                        )
                    )
                    Spacer(Modifier.height(Spacing.sm))
                }
                greenhouseSummary(record)?.let { summary ->
                    Text(stringResource(R.string.heat_session_greenhouse), style = MaterialTheme.typography.labelLarge)
                    Text(summary, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(Spacing.sm))
                }
                Text(
                    stringResource(R.string.preview3d_rows_summary, record.resolvedCropRowCount),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (onDelete != null) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("recent_delete_${record.id}")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.cd_delete)
                    )
                }
            }
        }
    }
}

private fun greenhouseSummary(record: HeatSessionRecord): String? {
    val mode = record.previewMode?.let { raw ->
        PreviewMode.fromRoute(raw)
    }
    val length = record.lengthM
    val width = record.widthM
    val height = record.heightM
    val dims = if (length != null && width != null && height != null) {
        "${formatOneDecimal(length)} m × ${formatOneDecimal(width)} m × ${formatOneDecimal(height)} m"
    } else {
        null
    }
    return when {
        mode == PreviewMode.DEMO && dims != null -> "Demo · $dims"
        mode == PreviewMode.MY_GREENHOUSE && dims != null -> dims
        dims != null -> dims
        mode == PreviewMode.DEMO -> "Demo"
        mode == PreviewMode.MY_GREENHOUSE -> null
        else -> null
    }
}
