 @file:OptIn(ExperimentalMaterial3Api::class)

package com.greenhands.app.heat.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.greenhands.app.R
import com.greenhands.app.heat.model.ControlMode
import com.greenhands.app.heat.model.Crop
import com.greenhands.app.heat.model.GrowthStage
import com.greenhands.app.heat.model.SchedulePeriod
import com.greenhands.app.ui.components.ScreenScaffold
import com.greenhands.app.ui.components.SegmentedTwoOption
import com.greenhands.app.ui.components.StickySaveBar
import com.greenhands.app.ui.components.TextAction
import com.greenhands.app.ui.theme.AmberWarning
import com.greenhands.app.ui.theme.ClimateTeal
import com.greenhands.app.ui.theme.ForestEmerald
import com.greenhands.app.ui.theme.Radii
import com.greenhands.app.ui.theme.Spacing

@Composable
fun HeatScaffold(
    title: String,
    onBack: (() -> Unit)?,
    stage: GrowthStage?,
    crop: Crop? = null,
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit
) {
    ScreenScaffold(
        title = title,
        onBack = onBack,
        actions = {
            if (stage != null) {
                StageBadge(stage = stage, crop = crop)
            }
        },
        bottomBar = bottomBar,
        content = content
    )
}

@Composable
fun RowScope.StageBadge(stage: GrowthStage, crop: Crop? = null) {
    Surface(
        modifier = Modifier
            .padding(end = Spacing.sm)
            .widthIn(max = 140.dp)
            .testTag("stage_badge"),
        shape = Radii.sm,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            CropGlyph(crop ?: Crop.TOMATO, size = 16.dp)
            Text(
                text = stage.shortLabel,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun CropGlyph(crop: Crop, modifier: Modifier = Modifier, size: Dp = 56.dp) {
    val accent = when (crop) {
        Crop.TOMATO -> ForestEmerald
        Crop.SALAD_CUCUMBER -> ClimateTeal
        Crop.BELL_PEPPER -> ForestEmerald
        Crop.CHILLI -> ClimateTeal
        Crop.LETTUCE -> ForestEmerald
    }
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        drawCircle(accent.copy(alpha = 0.14f), radius = w * 0.46f, center = Offset(w * 0.5f, h * 0.5f))
        when (crop) {
            Crop.TOMATO -> {
                drawCircle(accent, radius = w * 0.22f, center = Offset(w * 0.5f, h * 0.54f), style = Stroke(w * 0.07f))
                drawLine(accent, Offset(w * 0.5f, h * 0.22f), Offset(w * 0.5f, h * 0.36f), w * 0.06f, StrokeCap.Round)
            }
            Crop.SALAD_CUCUMBER -> {
                drawLine(accent, Offset(w * 0.28f, h * 0.62f), Offset(w * 0.72f, h * 0.38f), w * 0.08f, StrokeCap.Round)
                drawCircle(accent, radius = w * 0.05f, center = Offset(w * 0.32f, h * 0.58f))
            }
            Crop.BELL_PEPPER -> {
                val path = Path().apply {
                    moveTo(w * 0.5f, h * 0.28f)
                    cubicTo(w * 0.78f, h * 0.34f, w * 0.76f, h * 0.78f, w * 0.5f, h * 0.80f)
                    cubicTo(w * 0.24f, h * 0.78f, w * 0.22f, h * 0.34f, w * 0.5f, h * 0.28f)
                }
                drawPath(path, accent, style = Stroke(w * 0.07f))
            }
            Crop.CHILLI -> {
                val path = Path().apply {
                    moveTo(w * 0.42f, h * 0.24f)
                    quadraticBezierTo(w * 0.82f, h * 0.48f, w * 0.48f, h * 0.82f)
                }
                drawPath(path, accent, style = Stroke(width = w * 0.08f, cap = StrokeCap.Round))
            }
            Crop.LETTUCE -> {
                drawCircle(accent, radius = w * 0.18f, center = Offset(w * 0.5f, h * 0.52f), style = Stroke(w * 0.06f))
                drawCircle(accent, radius = w * 0.28f, center = Offset(w * 0.5f, h * 0.52f), style = Stroke(w * 0.045f))
            }
        }
    }
}

@Composable
fun FanGlyph(modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val teal = ClimateTeal
    Canvas(modifier.size(size)) {
        val c = Offset(this.size.width / 2f, this.size.height / 2f)
        val r = this.size.minDimension * 0.18f
        drawCircle(teal.copy(alpha = 0.2f), radius = this.size.minDimension * 0.46f, center = c)
        drawCircle(teal, radius = r * 0.45f, center = c)
        for (i in 0..3) {
            val ang = Math.toRadians((i * 90).toDouble())
            val x = c.x + (kotlin.math.cos(ang) * r * 1.6).toFloat()
            val y = c.y + (kotlin.math.sin(ang) * r * 1.6).toFloat()
            drawCircle(teal.copy(alpha = 0.85f), radius = r * 0.7f, center = Offset(x, y), style = Stroke(3f))
        }
    }
}

@Composable
fun FoggerGlyph(modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val teal = ClimateTeal
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        drawCircle(teal.copy(alpha = 0.2f), radius = w * 0.46f, center = Offset(w / 2f, h / 2f))
        drawLine(teal, Offset(w * 0.5f, h * 0.72f), Offset(w * 0.5f, h * 0.42f), 4f, StrokeCap.Round)
        drawCircle(teal.copy(alpha = 0.5f), radius = w * 0.08f, center = Offset(w * 0.38f, h * 0.32f))
        drawCircle(teal.copy(alpha = 0.7f), radius = w * 0.06f, center = Offset(w * 0.52f, h * 0.24f))
        drawCircle(teal.copy(alpha = 0.45f), radius = w * 0.07f, center = Offset(w * 0.64f, h * 0.34f))
    }
}

@Composable
fun DayNightSelector(
    period: SchedulePeriod,
    onSelect: (SchedulePeriod) -> Unit,
    modifier: Modifier = Modifier
) {
    SegmentedTwoOption(
        firstLabel = stringResource(R.string.climate_period_day),
        secondLabel = stringResource(R.string.climate_period_night),
        firstSelected = period == SchedulePeriod.DAY,
        onFirst = { onSelect(SchedulePeriod.DAY) },
        onSecond = { onSelect(SchedulePeriod.NIGHT) },
        modifier = modifier,
        firstTag = "climate_period_day",
        secondTag = "climate_period_night",
        firstDescription = stringResource(R.string.cd_day_period),
        secondDescription = stringResource(R.string.cd_night_period)
    )
}

@Composable
fun AutomaticAdvancedSelector(
    mode: ControlMode,
    onChange: (ControlMode) -> Unit
) {
    SegmentedTwoOption(
        firstLabel = stringResource(R.string.mode_automatic),
        secondLabel = stringResource(R.string.mode_advanced),
        firstSelected = mode == ControlMode.AUTOMATIC,
        onFirst = { onChange(ControlMode.AUTOMATIC) },
        onSecond = { onChange(ControlMode.ADVANCED) },
        firstTag = "mode_automatic",
        secondTag = "mode_advanced"
    )
}

@Composable
fun DecimalField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    testTag: String,
    suffix: String = ""
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag),
        label = { Text(label) },
        suffix = { if (suffix.isNotBlank()) Text(suffix) },
        enabled = enabled,
        isError = error != null,
        supportingText = { if (error != null) Text(error) },
        singleLine = true,
        shape = Radii.md,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    )
}

@Composable
fun ThresholdLine(code: String, meaning: String, value: String, testTag: String) {
    Column(Modifier.fillMaxWidth().testTag(testTag)) {
        Text(meaning, style = MaterialTheme.typography.titleMedium)
        Text(value, style = MaterialTheme.typography.headlineSmall, color = ClimateTeal)
        if (code.isNotBlank()) {
            Text(
                code,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun WarningText(text: String?) {
    if (text != null) {
        Text(
            text = text,
            color = AmberWarning,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun TechnicalDetails(content: @Composable () -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    TextButton(
        onClick = { open = !open },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("technical_details")
    ) {
        Text(stringResource(R.string.equipment_technical))
    }
    AnimatedVisibility(open) {
        Column(Modifier.fillMaxWidth().padding(bottom = Spacing.md), content = { content() })
    }
}

@Composable
fun EquipmentActions(
    onReset: () -> Unit,
    onSaveContinue: () -> Unit,
    saveTag: String
) {
    TextAction(
        text = stringResource(R.string.action_reset_automatic),
        onClick = onReset,
        modifier = Modifier.testTag("reset_formula")
    )
    Spacer(Modifier.height(Spacing.sm))
    StickySaveBar(
        label = stringResource(R.string.action_save_continue),
        onClick = onSaveContinue,
        enabled = true,
        testTag = saveTag
    )
}
