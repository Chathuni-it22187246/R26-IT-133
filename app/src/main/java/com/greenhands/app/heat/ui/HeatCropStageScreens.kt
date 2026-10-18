package com.greenhands.app.heat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.greenhands.app.R
import com.greenhands.app.heat.model.Crop
import com.greenhands.app.heat.model.GrowthStage
import com.greenhands.app.heat.model.formatOneDecimal
import com.greenhands.app.heat.profile.CropProfileRegistry
import com.greenhands.app.ui.components.ConfigurationProgress
import com.greenhands.app.ui.components.DemoNotice
import com.greenhands.app.ui.components.InfoCard
import com.greenhands.app.ui.components.ScreenScaffold
import com.greenhands.app.ui.components.ScrollScreen
import com.greenhands.app.ui.components.SectionHeading
import com.greenhands.app.ui.components.StatusChip
import com.greenhands.app.ui.components.StickySaveBar
import com.greenhands.app.ui.theme.Spacing

@Composable
fun SelectCropScreen(
    onSelectCrop: (Crop) -> Unit,
    onBack: (() -> Unit)? = null,
    onOpenActive: () -> Unit = {},
    onOpenRecent: () -> Unit = {}
) {
    ScreenScaffold(title = stringResource(R.string.crops_title), onBack = onBack) { padding ->
        ScrollScreen(Modifier.fillMaxSize().padding(padding).testTag("select_crop")) {
            SectionHeading(
                title = stringResource(R.string.heat_intro_heading),
                subtitle = stringResource(R.string.heat_intro_body)
            )
            Spacer(Modifier.height(Spacing.md))
            DemoNotice(stringResource(R.string.heat_intro_notice))
            Spacer(Modifier.height(Spacing.section))
            CompactConfigNavButton(
                label = stringResource(R.string.heat_active_config),
                testTag = "crops_active",
                onClick = onOpenActive
            )
            Spacer(Modifier.height(Spacing.related))
            CompactConfigNavButton(
                label = stringResource(R.string.heat_recent_config),
                testTag = "crops_recent",
                onClick = onOpenRecent
            )
            Spacer(Modifier.height(Spacing.section))
            SectionHeading(
                title = stringResource(R.string.crops_heading),
                subtitle = stringResource(R.string.crops_subtitle)
            )
            Spacer(Modifier.height(Spacing.section))
            Crop.selectable().forEach { crop ->
                val profile = CropProfileRegistry.profile(crop)
                InfoCard(
                    onClick = { onSelectCrop(crop) },
                    modifier = Modifier.testTag("crop_${crop.id}")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                    ) {
                        CropGlyph(crop, size = Spacing.xxxl)
                        Column(Modifier.weight(1f)) {
                            Text(crop.displayName, style = MaterialTheme.typography.titleLarge)
                            Text(
                                crop.scientificName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag("crop_scientific_${crop.id}")
                            )
                            Spacer(Modifier.height(Spacing.sm))
                            StatusChip(stringResource(R.string.status_suggested_profile))
                            Spacer(Modifier.height(Spacing.sm))
                            Text(
                                crop.greenhouseDescription,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(Spacing.xxs))
                            Text(
                                stringResource(R.string.crops_profile_count, profile.stages.size),
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.testTag("crop_profiles_${crop.id}")
                            )
                        }
                    }
                }
                Spacer(Modifier.height(Spacing.related))
            }
        }
    }
}

@Composable
private fun CompactConfigNavButton(
    label: String,
    testTag: String,
    onClick: () -> Unit
) {
    InfoCard(onClick = onClick, modifier = Modifier.testTag(testTag)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.cd_open),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SelectStageScreen(
    crop: Crop?,
    currentStage: GrowthStage?,
    pendingStage: GrowthStage?,
    onSelect: (GrowthStage) -> Unit,
    onConfirmChange: () -> Unit,
    onCancelChange: () -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit = {}
) {
    val selected = crop ?: Crop.TOMATO
    val profile = CropProfileRegistry.profile(selected)
    ScreenScaffold(
        title = stringResource(R.string.stage_title),
        onBack = onBack,
        bottomBar = {
            StickySaveBar(
                label = stringResource(R.string.stage_continue),
                onClick = onContinue,
                enabled = currentStage != null,
                testTag = "stage_continue"
            )
        }
    ) { padding ->
        ScrollScreen(Modifier.fillMaxSize().padding(padding).testTag("select_stage")) {
            ConfigurationProgress(step = 2)
            Spacer(Modifier.height(Spacing.lg))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                CropGlyph(selected, size = 48.dp)
                Column(modifier = Modifier.testTag("stage_crop_header")) {
                    Text(
                        selected.displayName,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier
                            .testTag("stage_heading")
                            .semantics { heading() }
                    )
                    Text(
                        selected.scientificName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(Spacing.md))
            StatusChip(stringResource(R.string.status_suggested_profile))
            Spacer(Modifier.height(Spacing.section))
            profile.stages.forEachIndexed { index, item ->
                val stage = item.stage
                val rec = item.climate
                InfoCard(
                    onClick = { onSelect(stage) },
                    modifier = Modifier.testTag("stage_${stage.id}")
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${index + 1}",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = Spacing.md)
                        )
                        Column(Modifier.weight(1f)) {
                            Text(stage.displayName, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(Spacing.sm))
                            Text(
                                stringResource(
                                    R.string.stage_day_night,
                                    com.greenhands.app.heat.model.formatOneDecimal(rec.uiDayTemperatureC()),
                                    com.greenhands.app.heat.model.formatOneDecimal(rec.uiNightTemperatureC())
                                ),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (currentStage?.id == stage.id) {
                                Spacer(Modifier.height(Spacing.sm))
                                StatusChip(stringResource(R.string.stage_selected))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(Spacing.related))
            }
        }
    }
    if (pendingStage != null) {
        AlertDialog(
            onDismissRequest = onCancelChange,
            title = { Text(stringResource(R.string.stage_change_title)) },
            text = { Text(stringResource(R.string.stage_change_body, pendingStage.displayName)) },
            confirmButton = {
                TextButton(onClick = onConfirmChange, modifier = Modifier.testTag("stage_change_confirm")) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = onCancelChange, modifier = Modifier.testTag("stage_change_cancel")) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}
