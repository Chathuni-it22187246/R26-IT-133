package com.greenhands.app.ar.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.greenhands.app.heat.visualization.EnvironmentalFieldAvailability
import com.greenhands.app.heat.visualization.EnvironmentalFieldLayer
import com.greenhands.app.heat.visualization.EnvironmentalFieldLegendModel
import com.greenhands.app.heat.visualization.EnvironmentalFieldOverlayHost
import com.greenhands.app.heat.visualization.VisualizationCopy
import com.greenhands.app.heat.visualization.VisualizationRgb
import com.greenhands.app.ui.theme.Radii
import com.greenhands.app.ui.theme.Spacing

private val ArHudScrim = Color(0xFF0B0F0C).copy(alpha = 0.62f)
private val ArHudOnScrim = Color(0xFFF2F5F3)
private val ArHudMuted = Color(0xFFC5D0CA)

/**
 * Always-present field overlay slot. [placed] and layer only change the model;
 * this composable is not created or destroyed on anchor, Start, or layer switches.
 */
@Composable
fun ArEnvironmentalFieldOverlay(
    placed: Boolean,
    model: EnvironmentalFieldLegendModel,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    ArEnvironmentalFieldLegend(
        model = EnvironmentalFieldOverlayHost.legendModel(placed, model),
        onToggle = onToggle,
        modifier = modifier.testTag("ar_env_field_overlay")
    )
}

@Composable
fun ArEnvironmentalFieldLegend(
    model: EnvironmentalFieldLegendModel,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hidden = model.layer == EnvironmentalFieldLayer.OFF
    Surface(
        modifier = modifier
            .then(if (hidden) Modifier.size(0.dp) else Modifier.widthIn(min = 168.dp, max = 220.dp))
            .testTag("ar_env_legend")
            .semantics { contentDescription = model.title },
        shape = Radii.md,
        color = if (hidden) Color.Transparent else ArHudScrim
    ) {
                if (hidden) {
            Box(Modifier.size(0.dp).testTag("ar_env_legend_off"))
        } else {
            Column(
                modifier = Modifier
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                    .testTag("ar_env_legend_visible"),
                verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
            ) {
                ArEnvironmentalFieldLegendHeader(model = model, onToggle = onToggle)
                if (model.expanded) {
                    ArEnvironmentalFieldLegendDetails(model = model)
                } else if (model.message != null) {
                    Text(
                        text = model.message,
                        color = ArHudMuted,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("ar_env_start_hint")
                    )
                }
            }
        }
    }
}

@Composable
private fun ArEnvironmentalFieldLegendHeader(
    model: EnvironmentalFieldLegendModel,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .clickable(onClick = onToggle)
            .testTag("ar_env_legend_toggle"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = model.title,
                color = ArHudOnScrim,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .testTag("ar_env_legend_title")
                    .semantics { heading() }
            )
            Text(
                text = model.predictionLabel,
                color = ArHudMuted,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("ar_env_legend_disclaimer")
            )
        }
        Text(
            text = if (model.expanded) "▲" else "▼",
            color = ArHudOnScrim,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun ArEnvironmentalFieldLegendDetails(
    model: EnvironmentalFieldLegendModel
) {
    Text(
        text = model.cropStageText,
        color = ArHudOnScrim,
        style = MaterialTheme.typography.labelSmall,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.testTag("ar_env_crop_stage")
    )
    Text(
        text = model.unit,
        color = ArHudMuted,
        style = MaterialTheme.typography.labelSmall,
        maxLines = 1,
        modifier = Modifier.testTag("ar_env_legend_unit")
    )
    if (model.availability == EnvironmentalFieldAvailability.AVAILABLE) {
        Text(
            text = "Min ${model.minText()}",
            color = ArHudOnScrim,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("ar_env_min")
        )
        Text(
            text = "Avg ${model.averageText()}",
            color = ArHudOnScrim,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("ar_env_avg")
        )
        Text(
            text = "Max ${model.maxText()}",
            color = ArHudOnScrim,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("ar_env_max")
        )
    }
    if (model.message != null) {
        Text(
            text = model.message,
            color = ArHudMuted,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("ar_env_start_hint")
        )
    }
    Column(
        modifier = Modifier
            .heightIn(max = 160.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
    ) {
        model.bands.forEach { band ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.size(12.dp)) {
                    Surface(
                        modifier = Modifier.size(12.dp),
                        shape = Radii.sm,
                        color = band.color.toComposeColor()
                    ) {}
                }
                Spacer(Modifier.width(Spacing.xs))
                Text(
                    text = "${band.name} ${band.rangeText}",
                    color = ArHudOnScrim,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
    Text(
        text = VisualizationCopy.PREDICTED_UNCALIBRATED,
        color = ArHudMuted,
        style = MaterialTheme.typography.labelSmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

private fun VisualizationRgb.toComposeColor(): Color =
    Color(red, green, blue, alpha.coerceIn(0.35f, 1f))
