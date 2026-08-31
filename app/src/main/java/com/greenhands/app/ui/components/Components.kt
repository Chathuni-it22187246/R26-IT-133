@file:OptIn(ExperimentalMaterial3Api::class)

package com.greenhands.app.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.greenhands.app.R
import com.greenhands.app.profile.initialsFor
import com.greenhands.app.ui.theme.AmberWarning
import com.greenhands.app.ui.theme.ButtonHeight
import com.greenhands.app.ui.theme.ClimateTeal
import com.greenhands.app.ui.theme.ContentMaxWidth
import com.greenhands.app.ui.theme.ForestEmerald
import com.greenhands.app.ui.theme.GhType
import com.greenhands.app.ui.theme.NightBg
import com.greenhands.app.ui.theme.Radii
import com.greenhands.app.ui.theme.SoftError
import com.greenhands.app.ui.theme.Spacing
import com.greenhands.app.ui.theme.Stroke as GhStroke

@Composable
fun screenHorizontalPadding(): Dp {
    val config = LocalConfiguration.current
    val width = config.screenWidthDp
    return when {
        config.orientation == Configuration.ORIENTATION_LANDSCAPE || width >= 600 -> Spacing.screenWide
        width < 360 -> Spacing.screenCompact
        else -> Spacing.screen
    }
}

@Composable
fun ScreenScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CenterAlignedTopAppBar(
                windowInsets = WindowInsets.statusBars,
                title = {
                    Text(
                        text = title,
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
                actions = actions,
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = bottomBar,
        content = content
    )
}

@Composable
fun ScrollScreen(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val horizontal = screenHorizontalPadding()
    Box(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = ContentMaxWidth.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = horizontal)
                .padding(top = Spacing.afterAppBar, bottom = Spacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content
        )
    }
}

@Composable
fun PrimaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = Radii.md,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = ButtonHeight.preferred),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
fun SecondaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = Radii.md,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = ButtonHeight.min),
        border = BorderStroke(GhStroke.hairline, MaterialTheme.colorScheme.outline)
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
fun DemoNotice(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = Radii.md,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(GhStroke.hairline, MaterialTheme.colorScheme.outline)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(Spacing.lg),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun StatusChip(text: String, warning: Boolean = false, modifier: Modifier = Modifier) {
    val container = if (warning) {
        AmberWarning.copy(alpha = 0.16f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val content = if (warning) AmberWarning else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        shape = Radii.sm,
        color = container,
        border = BorderStroke(GhStroke.hairline, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
        modifier = modifier
    ) {
        Text(
            text = text,
            color = content,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xxs)
        )
    }
}

@Composable
fun EvidenceBadge(text: String, modifier: Modifier = Modifier) {
    StatusChip(text = text, modifier = modifier)
}

@Composable
fun WarningPanel(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    error: Boolean = false
) {
    val accent = if (error) SoftError else AmberWarning
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = Radii.md,
        color = accent.copy(alpha = 0.12f),
        border = BorderStroke(GhStroke.hairline, accent.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(Spacing.lg)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = accent)
            Spacer(Modifier.height(Spacing.xs))
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun InfoCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    val border = BorderStroke(GhStroke.hairline, MaterialTheme.colorScheme.outline)
    val inner = @Composable {
        Column(Modifier.padding(Spacing.lg), content = content)
    }
    if (onClick != null) {
        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        Card(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = Radii.md,
            colors = colors,
            elevation = CardDefaults.cardElevation(defaultElevation = if (pressed) 0.dp else 2.dp),
            border = border,
            interactionSource = interaction,
            content = { inner() }
        )
    } else {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = Radii.md,
            colors = colors,
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            border = border,
            content = { inner() }
        )
    }
}

@Composable
fun ModuleCard(
    title: String,
    description: String,
    status: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = Radii.lg,
        interactionSource = interaction,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = if (pressed) 0.dp else 2.dp),
        border = BorderStroke(GhStroke.hairline, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(Radii.md)
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) { icon() }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(Spacing.sm))
                StatusChip(status)
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.cd_open),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SegmentedTwoOption(
    firstLabel: String,
    secondLabel: String,
    firstSelected: Boolean,
    onFirst: () -> Unit,
    onSecond: () -> Unit,
    modifier: Modifier = Modifier,
    firstTag: String = "segment_first",
    secondTag: String = "segment_second",
    firstDescription: String? = null,
    secondDescription: String? = null
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = Radii.md,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(GhStroke.hairline, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            Modifier
                .selectableGroup()
                .padding(4.dp)
        ) {
            SegmentOption(
                label = firstLabel,
                selected = firstSelected,
                onClick = onFirst,
                modifier = Modifier
                    .weight(1f)
                    .testTag(firstTag),
                optionDescription = firstDescription
            )
            SegmentOption(
                label = secondLabel,
                selected = !firstSelected,
                onClick = onSecond,
                modifier = Modifier
                    .weight(1f)
                    .testTag(secondTag),
                optionDescription = secondDescription
            )
        }
    }
}

@Composable
private fun SegmentOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    optionDescription: String?
) {
    val bg = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent
    val fg = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        modifier = modifier
            .heightIn(min = Spacing.touch)
            .semantics(mergeDescendants = true) {
                if (!optionDescription.isNullOrBlank()) {
                    this.contentDescription = optionDescription
                }
            }
            .selectable(selected = selected, onClick = onClick, role = Role.Tab),
        shape = Radii.sm,
        color = bg
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm)) {
            Text(
                text = label,
                color = fg,
                style = MaterialTheme.typography.titleSmall
            )
        }
    }
}

@Composable
fun ConfigurationProgress(step: Int, total: Int = 6, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        repeat(total) { index ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(Radii.pill)
                    .background(
                        if (index < step) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline
                    )
            )
        }
    }
}

@Composable
fun DemoPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    visible: Boolean,
    onToggleVisibility: () -> Unit,
    error: String?,
    modifier: Modifier = Modifier,
    testTag: String = "password"
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag),
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = { if (error != null) Text(error) },
        shape = Radii.md,
        visualTransformation = if (visible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        trailingIcon = {
            IconButton(onClick = onToggleVisibility, modifier = Modifier.size(Spacing.touch)) {
                Icon(
                    imageVector = if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (visible) {
                        stringResource(R.string.cd_hide_password)
                    } else {
                        stringResource(R.string.cd_show_password)
                    }
                )
            }
        }
    )
}

@Composable
fun InitialsAvatar(name: String, size: Dp = 48.dp, modifier: Modifier = Modifier) {
    val initials = initialsFor(name)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .semantics { contentDescription = name }
            .testTag("profile_avatar_initials"),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
fun ProfileAvatar(
    name: String,
    photoPath: String? = null,
    size: Dp = 48.dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    val bitmap = remember(photoPath) {
        photoPath?.takeIf { java.io.File(it).isFile }?.let { android.graphics.BitmapFactory.decodeFile(it) }
    }
    val description = contentDescription ?: stringResource(R.string.cd_profile)
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = description,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .testTag("profile_avatar_photo")
        )
    } else {
        InitialsAvatar(name = name, size = size, modifier = modifier)
    }
}

@Composable
fun GreenHandsLogo(size: Dp = 96.dp, modifier: Modifier = Modifier) {
    val primary = ForestEmerald
    val teal = ClimateTeal
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(primary.copy(alpha = 0.22f), Color.Transparent),
                center = Offset(w * 0.5f, h * 0.48f),
                radius = w * 0.55f
            )
        )
        val house = Path().apply {
            moveTo(w * 0.18f, h * 0.58f)
            lineTo(w * 0.50f, h * 0.22f)
            lineTo(w * 0.82f, h * 0.58f)
            lineTo(w * 0.82f, h * 0.82f)
            lineTo(w * 0.18f, h * 0.82f)
            close()
        }
        drawPath(house, color = primary.copy(alpha = 0.16f))
        drawPath(house, color = primary, style = Stroke(width = w * 0.045f, cap = StrokeCap.Round))
        drawLine(
            color = teal,
            start = Offset(w * 0.50f, h * 0.22f),
            end = Offset(w * 0.50f, h * 0.82f),
            strokeWidth = w * 0.028f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = teal.copy(alpha = 0.7f),
            start = Offset(w * 0.32f, h * 0.62f),
            end = Offset(w * 0.68f, h * 0.62f),
            strokeWidth = w * 0.018f,
            cap = StrokeCap.Round
        )
        drawCircle(primary, radius = w * 0.035f, center = Offset(w * 0.34f, h * 0.70f))
        drawCircle(teal, radius = w * 0.03f, center = Offset(w * 0.66f, h * 0.70f))
        val leaf = Path().apply {
            moveTo(w * 0.56f, h * 0.18f)
            cubicTo(w * 0.78f, h * 0.12f, w * 0.82f, h * 0.36f, w * 0.60f, h * 0.40f)
            cubicTo(w * 0.70f, h * 0.28f, w * 0.62f, h * 0.18f, w * 0.56f, h * 0.18f)
            close()
        }
        drawPath(leaf, color = primary)
    }
}

@Composable
fun GreenhouseHeaderVisual(modifier: Modifier = Modifier) {
    val primary = ForestEmerald
    val teal = ClimateTeal
    val surface = MaterialTheme.colorScheme.surfaceVariant
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(168.dp)
    ) {
        val w = size.width
        val h = size.height
        drawRoundRect(
            brush = Brush.verticalGradient(
                listOf(surface, NightBg.copy(alpha = 0.35f))
            ),
            cornerRadius = CornerRadius(28f),
            size = size
        )
        val roof = Path().apply {
            moveTo(w * 0.10f, h * 0.58f)
            lineTo(w * 0.50f, h * 0.16f)
            lineTo(w * 0.90f, h * 0.58f)
            close()
        }
        drawPath(roof, color = primary.copy(alpha = 0.18f))
        drawPath(roof, color = teal.copy(alpha = 0.85f), style = Stroke(width = 4f))
        drawRoundRect(
            color = teal.copy(alpha = 0.9f),
            topLeft = Offset(w * 0.16f, h * 0.56f),
            size = Size(w * 0.68f, h * 0.32f),
            cornerRadius = CornerRadius(10f),
            style = Stroke(width = 3.5f, cap = StrokeCap.Round)
        )
        for (i in 1..3) {
            val x = w * (0.16f + 0.17f * i)
            drawLine(
                color = teal.copy(alpha = 0.45f),
                start = Offset(x, h * 0.56f),
                end = Offset(x, h * 0.88f),
                strokeWidth = 2.5f
            )
        }
        drawCircle(primary, radius = 5f, center = Offset(w * 0.22f, h * 0.70f))
        drawCircle(teal, radius = 5f, center = Offset(w * 0.40f, h * 0.72f))
        drawCircle(primary, radius = 5f, center = Offset(w * 0.60f, h * 0.72f))
        drawCircle(teal, radius = 5f, center = Offset(w * 0.78f, h * 0.70f))
        drawLine(
            color = teal.copy(alpha = 0.45f),
            start = Offset(w * 0.22f, h * 0.70f),
            end = Offset(w * 0.40f, h * 0.72f),
            strokeWidth = 1.8f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = teal.copy(alpha = 0.45f),
            start = Offset(w * 0.40f, h * 0.72f),
            end = Offset(w * 0.60f, h * 0.72f),
            strokeWidth = 1.8f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = teal.copy(alpha = 0.45f),
            start = Offset(w * 0.60f, h * 0.72f),
            end = Offset(w * 0.78f, h * 0.70f),
            strokeWidth = 1.8f,
            cap = StrokeCap.Round
        )
        drawCircle(teal.copy(alpha = 0.35f), radius = 11f, center = Offset(w * 0.50f, h * 0.36f), style = Stroke(width = 1.6f))
        drawCircle(primary, radius = 3.5f, center = Offset(w * 0.50f, h * 0.36f))
        drawLine(
            color = teal.copy(alpha = 0.55f),
            start = Offset(w * 0.22f, h * 0.42f),
            end = Offset(w * 0.38f, h * 0.36f),
            strokeWidth = 2f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = teal.copy(alpha = 0.55f),
            start = Offset(w * 0.62f, h * 0.36f),
            end = Offset(w * 0.78f, h * 0.42f),
            strokeWidth = 2f,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun SectionHeading(title: String, subtitle: String? = null) {
    Column(
        Modifier
            .fillMaxWidth()
            .semantics { heading() },
        verticalArrangement = Arrangement.spacedBy(Spacing.titleDesc)
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun EmptyStateText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Start
    )
}

@Composable
fun TextAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = Spacing.touch)
    ) {
        Text(text)
    }
}

@Composable
fun StickySaveBar(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    testTag: String = "sticky_save"
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 2.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        PrimaryActionButton(
            text = label,
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .padding(horizontal = screenHorizontalPadding(), vertical = Spacing.sm)
                .testTag(testTag)
        )
    }
}

@Composable
fun CompactBrand(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        GreenHandsLogo(size = 40.dp)
        Column {
            Text("GreenHands", style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.brand_compact_subtitle),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
