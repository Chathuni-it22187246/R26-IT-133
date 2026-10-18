package com.greenhands.app.preview3d.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.Yard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.greenhands.app.R
import com.greenhands.app.heat.ui.DecimalField
import com.greenhands.app.heat.ui.IntegerField
import com.greenhands.app.preview3d.model.DemoGreenhouseProfile
import com.greenhands.app.preview3d.model.GreenhouseDimensions
import com.greenhands.app.preview3d.model.PreviewMode
import com.greenhands.app.ui.components.DemoNotice
import com.greenhands.app.ui.components.InfoCard
import com.greenhands.app.ui.components.PrimaryActionButton
import com.greenhands.app.ui.components.ScreenScaffold
import com.greenhands.app.ui.components.ScrollScreen
import com.greenhands.app.ui.components.SectionHeading
import com.greenhands.app.ui.components.StatusChip
import com.greenhands.app.ui.theme.ClimateTeal
import com.greenhands.app.ui.theme.ForestEmerald
import com.greenhands.app.ui.theme.IconSize
import com.greenhands.app.ui.theme.Spacing

@Composable
fun GreenhousePreviewSetupRoute(
    onBack: () -> Unit,
    onOpenPreview: (PreviewMode, GreenhouseDimensions, Int) -> Unit,
    viewModel: PreviewSetupViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    GreenhousePreviewSetupScreen(
        state = state,
        onBack = onBack,
        onSelectDemo = viewModel::selectDemo,
        onSelectMyGreenhouse = viewModel::selectMyGreenhouse,
        onLengthInput = viewModel::onLengthInput,
        onWidthInput = viewModel::onWidthInput,
        onHeightInput = viewModel::onHeightInput,
        onCropRowInput = viewModel::onCropRowInput,
        onOpenPreview = {
            val dimensions = state.resolvedDimensions() ?: return@GreenhousePreviewSetupScreen
            val mode = state.mode ?: return@GreenhousePreviewSetupScreen
            val rows = state.resolvedCropRowCount() ?: return@GreenhousePreviewSetupScreen
            onOpenPreview(mode, dimensions, rows)
        }
    )
}

@Composable
fun GreenhousePreviewSetupScreen(
    state: PreviewSetupState,
    onBack: () -> Unit,
    onSelectDemo: () -> Unit,
    onSelectMyGreenhouse: () -> Unit,
    onLengthInput: (String) -> Unit,
    onWidthInput: (String) -> Unit,
    onHeightInput: (String) -> Unit,
    onCropRowInput: (String) -> Unit,
    onOpenPreview: () -> Unit
) {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    ScreenScaffold(
        title = stringResource(R.string.preview3d_setup_title),
        onBack = onBack,
        bottomBar = {
            PrimaryActionButton(
                text = stringResource(R.string.preview3d_open),
                onClick = onOpenPreview,
                enabled = state.canOpenPreview,
                modifier = Modifier
                    .padding(horizontal = Spacing.xl, vertical = Spacing.md)
                    .testTag("preview_open")
            )
        }
    ) { padding ->
        ScrollScreen(Modifier.fillMaxSize().padding(padding).testTag("preview_setup")) {
            SectionHeading(
                title = stringResource(R.string.preview3d_setup_heading),
                subtitle = stringResource(R.string.preview3d_setup_body)
            )
            Spacer(Modifier.height(Spacing.md))
            DemoNotice(stringResource(R.string.preview3d_setup_notice))
            Spacer(Modifier.height(Spacing.section))
            if (landscape) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    ModeCard(
                        modifier = Modifier.weight(1f),
                        title = stringResource(R.string.preview3d_demo_title),
                        body = stringResource(R.string.preview3d_demo_body),
                        selected = state.mode == PreviewMode.DEMO,
                        testTag = "preview_demo",
                        accent = ClimateTeal,
                        onClick = onSelectDemo
                    ) {
                        Icon(Icons.Outlined.GridOn, contentDescription = null, modifier = Modifier.size(IconSize.lg))
                    }
                    ModeCard(
                        modifier = Modifier.weight(1f),
                        title = stringResource(R.string.preview3d_mine_title),
                        body = stringResource(R.string.preview3d_mine_body),
                        selected = state.mode == PreviewMode.MY_GREENHOUSE,
                        testTag = "preview_my_gh",
                        accent = ForestEmerald,
                        onClick = onSelectMyGreenhouse
                    ) {
                        Icon(Icons.Outlined.Yard, contentDescription = null, modifier = Modifier.size(IconSize.lg))
                    }
                }
            } else {
                ModeCard(
                    title = stringResource(R.string.preview3d_demo_title),
                    body = stringResource(R.string.preview3d_demo_body),
                    selected = state.mode == PreviewMode.DEMO,
                    testTag = "preview_demo",
                    accent = ClimateTeal,
                    onClick = onSelectDemo
                ) {
                    Icon(Icons.Outlined.GridOn, contentDescription = null, modifier = Modifier.size(IconSize.lg))
                }
                Spacer(Modifier.height(Spacing.related))
                ModeCard(
                    title = stringResource(R.string.preview3d_mine_title),
                    body = stringResource(R.string.preview3d_mine_body),
                    selected = state.mode == PreviewMode.MY_GREENHOUSE,
                    testTag = "preview_my_gh",
                    accent = ForestEmerald,
                    onClick = onSelectMyGreenhouse
                ) {
                    Icon(Icons.Outlined.Yard, contentDescription = null, modifier = Modifier.size(IconSize.lg))
                }
            }
            when (state.mode) {
                PreviewMode.DEMO -> {
                    Spacer(Modifier.height(Spacing.section))
                    StatusChip(stringResource(R.string.preview3d_badge_demo), modifier = Modifier.testTag("preview_mode_badge"))
                    Spacer(Modifier.height(Spacing.sm))
                    InfoCard(modifier = Modifier.testTag("preview_demo_dims")) {
                        Text(DemoGreenhouseProfile.displayLabel(), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(Spacing.xs))
                        Text(
                            stringResource(R.string.preview3d_predicted),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(Spacing.sm))
                        Text(
                            stringResource(R.string.preview3d_demo_provenance),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                PreviewMode.MY_GREENHOUSE -> {
                    Spacer(Modifier.height(Spacing.section))
                    StatusChip(stringResource(R.string.preview3d_badge_mine), modifier = Modifier.testTag("preview_mode_badge"))
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        stringResource(R.string.preview3d_form_heading),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    DecimalField(
                        value = state.lengthInput,
                        onValueChange = onLengthInput,
                        label = stringResource(R.string.preview3d_length),
                        error = state.lengthError,
                        testTag = "preview_length",
                        suffix = stringResource(R.string.preview3d_unit_m)
                    )
                    Spacer(Modifier.height(Spacing.related))
                    DecimalField(
                        value = state.widthInput,
                        onValueChange = onWidthInput,
                        label = stringResource(R.string.preview3d_width),
                        error = state.widthError,
                        testTag = "preview_width",
                        suffix = stringResource(R.string.preview3d_unit_m)
                    )
                    Spacer(Modifier.height(Spacing.related))
                    DecimalField(
                        value = state.heightInput,
                        onValueChange = onHeightInput,
                        label = stringResource(R.string.preview3d_height),
                        error = state.heightError,
                        testTag = "preview_height",
                        suffix = stringResource(R.string.preview3d_unit_m)
                    )
                    Spacer(Modifier.height(Spacing.related))
                    IntegerField(
                        value = state.cropRowInput,
                        onValueChange = onCropRowInput,
                        label = stringResource(R.string.preview3d_crop_rows),
                        error = state.cropRowError,
                        testTag = "preview_crop_rows"
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        stringResource(R.string.preview3d_form_guardrail),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                null -> {
                    Spacer(Modifier.height(Spacing.section))
                    Text(
                        stringResource(R.string.preview3d_choose_mode),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(Spacing.section))
        }
    }
}

@Composable
private fun ModeCard(
    title: String,
    body: String,
    selected: Boolean,
    testTag: String,
    accent: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit
) {
    InfoCard(
        modifier = modifier
            .testTag(testTag)
            .semantics { this.selected = selected },
        onClick = onClick
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            icon()
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = accent)
                    if (selected) {
                        StatusChip(stringResource(R.string.preview3d_selected))
                    }
                }
                Spacer(Modifier.height(Spacing.xs))
                Text(body, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
