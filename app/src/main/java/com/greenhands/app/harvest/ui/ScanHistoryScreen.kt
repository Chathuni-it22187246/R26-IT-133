package com.greenhands.app.harvest.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.greenhands.app.R
import com.greenhands.app.ui.components.EmptyStateText
import com.greenhands.app.ui.components.DemoNotice
import com.greenhands.app.ui.components.ScreenScaffold
import com.greenhands.app.ui.components.ScrollScreen
import com.greenhands.app.ui.components.SectionHeading
import com.greenhands.app.ui.theme.Spacing

@Composable
fun ScanHistoryScreen(
    onBack: () -> Unit,
    onOpenRecord: (String) -> Unit,
    viewModel: HarvestViewModel
) {
    val state by viewModel.state.collectAsState()

    ScreenScaffold(
        title = stringResource(R.string.harvest_history_title),
        onBack = onBack
    ) { padding ->
        ScrollScreen(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag("harvest_history")
        ) {
            SectionHeading(
                title = stringResource(R.string.harvest_history_heading),
                subtitle = stringResource(R.string.harvest_history_subtitle)
            )
            Spacer(Modifier.height(Spacing.md))
            if (state.historyLoadFailed) {
                DemoNotice(stringResource(R.string.harvest_history_load_failed))
                Spacer(Modifier.height(Spacing.md))
            }
            if (state.recentScans.isEmpty()) {
                EmptyStateText(stringResource(R.string.harvest_recent_scans_empty))
            } else {
                state.recentScans.forEachIndexed { index, record ->
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
