package com.greenhands.app.equipment.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.Window
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.greenhands.app.R
import com.greenhands.app.equipment.domain.PlacementContext
import com.greenhands.app.equipment.domain.PlacementSuggester
import com.greenhands.app.equipment.domain.PositionRationale
import com.greenhands.app.equipment.domain.SuggestionEvidence
import com.greenhands.app.equipment.model.EquipmentInstance
import com.greenhands.app.equipment.model.EquipmentRecommendation
import com.greenhands.app.equipment.model.EquipmentType
import com.greenhands.app.equipment.model.formatMetres2
import com.greenhands.app.ui.components.PrimaryActionButton
import com.greenhands.app.ui.components.SecondaryActionButton
import com.greenhands.app.ui.theme.Radii
import com.greenhands.app.ui.theme.Spacing
import kotlin.math.cos
import kotlin.math.sin

enum class SuggestionGalleryPresentation {
    PREVIEW_OVERLAY,
    AR_MODAL
}

@Composable
fun SuggestionRail(
    onOpen: (EquipmentType) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    landscape: Boolean = false
) {
    Surface(
        modifier = modifier
            .testTag("suggestion_rail")
            .padding(if (compact) Spacing.xxs else Spacing.xs),
        shape = Radii.md,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
    ) {
        val buttons: @Composable () -> Unit = {
            RailButton(
                type = EquipmentType.CIRCULATION_FAN,
                tag = "suggestion_circulation",
                label = stringResource(R.string.placement_suggest_circulation),
                shortLabel = stringResource(R.string.placement_suggest_circulation_short),
                compact = compact,
                landscape = landscape,
                onOpen = onOpen
            ) { Icon(Icons.Outlined.Air, contentDescription = null, modifier = if (compact) Modifier.size(18.dp) else Modifier) }
            RailButton(
                type = EquipmentType.EXHAUST_FAN,
                tag = "suggestion_exhaust",
                label = stringResource(R.string.placement_suggest_exhaust),
                shortLabel = stringResource(R.string.placement_suggest_exhaust_short),
                compact = compact,
                landscape = landscape,
                onOpen = onOpen
            ) { Icon(Icons.Outlined.Window, contentDescription = null, modifier = if (compact) Modifier.size(18.dp) else Modifier) }
            RailButton(
                type = EquipmentType.FOGGER,
                tag = "suggestion_fogger",
                label = stringResource(R.string.placement_suggest_fogger),
                shortLabel = stringResource(R.string.placement_suggest_fogger_short),
                compact = compact,
                landscape = landscape,
                onOpen = onOpen
            ) { Icon(Icons.Outlined.WaterDrop, contentDescription = null, modifier = if (compact) Modifier.size(18.dp) else Modifier) }
        }
        if (compact && landscape) {
            Row(
                modifier = Modifier.padding(Spacing.xxs),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)
            ) { buttons() }
        } else {
            Column(
                modifier = Modifier.padding(Spacing.xxs),
                verticalArrangement = Arrangement.spacedBy(if (compact) Spacing.xxs else Spacing.xs)
            ) { buttons() }
        }
    }
}

@Composable
private fun RailButton(
    type: EquipmentType,
    tag: String,
    label: String,
    shortLabel: String = label,
    compact: Boolean = false,
    landscape: Boolean = false,
    onOpen: (EquipmentType) -> Unit,
    icon: @Composable () -> Unit
) {
    val width = when {
        compact && landscape -> 84.dp
        compact -> 88.dp
        else -> 96.dp
    }
    val minHeight = when {
        compact && landscape -> 40.dp
        compact -> 56.dp
        else -> 72.dp
    }
    FilledTonalButton(
        onClick = { onOpen(type) },
        modifier = Modifier
            .width(width)
            .heightIn(min = minHeight)
            .testTag(tag)
            .semantics { contentDescription = label },
        contentPadding = PaddingValues(if (compact) 2.dp else Spacing.xxs)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            icon()
            Text(
                if (compact) shortLabel else label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = if (compact && landscape) 1 else 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuggestionGallerySheet(
    type: EquipmentType,
    context: PlacementContext,
    recommendation: EquipmentRecommendation?,
    isDemo: Boolean,
    onWhy: () -> Unit,
    onApply: () -> Unit,
    onClose: () -> Unit,
    presentation: SuggestionGalleryPresentation = SuggestionGalleryPresentation.PREVIEW_OVERLAY,
    whyRationale: PositionRationale? = null
) {
    val count = SuggestionEvidence.countFor(type, recommendation)
    val illustration = !isDemo &&
        (count == null || !SuggestionEvidence.canApplyCalculatedLayout(count))
    val ar = presentation == SuggestionGalleryPresentation.AR_MODAL
    val showApply = if (ar) {
        SuggestionEvidence.showsArApplyLayout(isDemo, count)
    } else {
        SuggestionEvidence.showsApplyLayout(isDemo, count)
    }
    val showAutoPlace = !ar && SuggestionEvidence.showsAutoPlaceSuggestedLayout(isDemo)
    val n = SuggestionEvidence.illustrationCount(type, count)
    val preview = when (type) {
        EquipmentType.CIRCULATION_FAN -> PlacementSuggester.suggest(context, n, 0, 0)
        EquipmentType.EXHAUST_FAN -> PlacementSuggester.suggest(context, 0, n, 0)
        EquipmentType.FOGGER -> PlacementSuggester.suggest(context, 0, 0, n)
    }
    val rationale = SuggestionEvidence.rationale(type, recommendation, context, illustration)
    val title = when (type) {
        EquipmentType.CIRCULATION_FAN -> stringResource(R.string.placement_suggest_circulation)
        EquipmentType.EXHAUST_FAN -> stringResource(R.string.placement_suggest_exhaust)
        EquipmentType.FOGGER -> stringResource(R.string.placement_suggest_fogger)
    }
    val preference = if (ar && illustration) {
        stringResource(R.string.placement_cannot_calculate_missing_equipment)
    } else {
        rationale.preference
    }
    val body: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .padding(Spacing.md)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.testTag("suggestion_gallery_title")
            )
            if (SuggestionEvidence.showsPreferenceHeadline(preference)) {
                Text(
                    preference,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.testTag("suggestion_preference")
                )
            }
            Spacer(Modifier.height(Spacing.sm))
            GreenhouseGuidanceVisual(
                context = context,
                instances = preview,
                type = type,
                illustration = illustration
            )
            if (isDemo) {
                Text(
                    if (ar) {
                        stringResource(R.string.placement_demo_research_example)
                    } else {
                        stringResource(R.string.placement_demo_equipment_profile_notice)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("suggestion_demo_profile_notice")
                )
            }
            if (illustration) {
                Text(
                    if (ar) stringResource(R.string.placement_example_pattern_only_period)
                    else stringResource(R.string.placement_example_pattern_only),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("suggestion_illustration_caption")
                )
            }
            Spacer(Modifier.height(Spacing.sm))
            preview.forEach { inst ->
                Text(
                    "${inst.displayLabel()}  X ${formatMetres2(inst.positionM.xM)} m · Y ${formatMetres2(inst.positionM.yM)} m · Z ${formatMetres2(inst.positionM.zM)} m",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("suggestion_coord_${inst.displayNumber}")
                )
            }
            Spacer(Modifier.height(Spacing.md))
            SecondaryActionButton(
                text = stringResource(R.string.placement_why_position),
                onClick = onWhy,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("suggestion_why")
            )
            if (whyRationale != null) {
                Spacer(Modifier.height(Spacing.sm))
                WhyPositionContent(whyRationale)
            }
            if (showAutoPlace) {
                Spacer(Modifier.height(Spacing.xs))
                PrimaryActionButton(
                    text = stringResource(R.string.placement_auto_place_suggested_layout),
                    onClick = onApply,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("suggestion_auto_place")
                )
            }
            if (showApply) {
                Spacer(Modifier.height(Spacing.xs))
                PrimaryActionButton(
                    text = stringResource(R.string.placement_apply_layout),
                    onClick = onApply,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("suggestion_apply_layout")
                )
            }
            TextButton(onClick = onClose, modifier = Modifier.testTag("suggestion_close")) {
                Text(stringResource(R.string.placement_close))
            }
        }
    }
    if (ar) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = onClose,
            sheetState = sheetState,
            modifier = Modifier.testTag("suggestion_gallery")
        ) {
            body()
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(onClick = onClose)
                .testTag("suggestion_gallery")
        ) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.88f)
                    .clickable(enabled = false, onClick = {}),
                shape = Radii.md,
                color = MaterialTheme.colorScheme.surface
            ) {
                body()
            }
        }
    }
}

@Composable
fun WhyPositionSheet(
    rationale: PositionRationale,
    onClose: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onClose)
            .testTag("suggestion_why_sheet")
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.8f),
            shape = Radii.md,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .padding(Spacing.md)
                    .verticalScroll(rememberScrollState())
            ) {
                WhyPositionContent(rationale)
                TextButton(onClick = onClose) { Text(stringResource(R.string.placement_close)) }
            }
        }
    }
}

@Composable
fun WhyPositionContent(rationale: PositionRationale) {
    Text(stringResource(R.string.placement_why_position), style = MaterialTheme.typography.titleMedium)
    if (SuggestionEvidence.showsPreferenceHeadline(rationale.preference)) {
        Text(rationale.preference, style = MaterialTheme.typography.labelLarge)
    }
    Spacer(Modifier.height(Spacing.sm))
    Text(stringResource(R.string.placement_source_heading), style = MaterialTheme.typography.labelMedium)
    Text(rationale.source, style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(Spacing.sm))
    Text(stringResource(R.string.placement_rule_heading), style = MaterialTheme.typography.labelMedium)
    Text(rationale.supportedRule, style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(Spacing.sm))
    Text(stringResource(R.string.placement_inputs_heading), style = MaterialTheme.typography.labelMedium)
    rationale.currentInputs.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
    Spacer(Modifier.height(Spacing.sm))
    Text(stringResource(R.string.placement_adapt_heading), style = MaterialTheme.typography.labelMedium)
    rationale.adaptations.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
    if (rationale.missing.isNotEmpty()) {
        Spacer(Modifier.height(Spacing.sm))
        Text(stringResource(R.string.placement_missing_heading), style = MaterialTheme.typography.labelMedium)
        rationale.missing.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
    Spacer(Modifier.height(Spacing.sm))
    Text(stringResource(R.string.placement_limits_heading), style = MaterialTheme.typography.labelMedium)
    rationale.limitations.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
    Spacer(Modifier.height(Spacing.sm))
    Text(rationale.sriLankaStatus, style = MaterialTheme.typography.bodySmall)
}

@Composable
fun GreenhouseGuidanceVisual(
    context: PlacementContext,
    instances: List<EquipmentInstance>,
    type: EquipmentType,
    illustration: Boolean,
    modifier: Modifier = Modifier
) {
    val outline = MaterialTheme.colorScheme.outline
    val crop = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
    val mark = MaterialTheme.colorScheme.tertiary
    Column(modifier.testTag("suggestion_visual")) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(168.dp)
        ) {
            val pad = 18.dp.toPx()
            val innerW = size.width - pad * 2
            val innerH = size.height - pad * 2
            drawRect(outline, Offset(pad, pad), Size(innerW, innerH), style = Stroke(2.dp.toPx()))
            val length = context.lengthM.coerceAtLeast(0.1)
            val width = context.widthM.coerceAtLeast(0.1)
            fun mapAlong(yLength: Double) = pad + (yLength / length).toFloat() * innerW
            fun mapAcross(xWidth: Double) = pad + innerH - (xWidth / width).toFloat() * innerH
            context.cropBoxes.forEach { box ->
                val left = mapAlong(box.minYM)
                val right = mapAlong(box.maxYM)
                val top = mapAcross(box.maxXM)
                val bottom = mapAcross(box.minXM)
                drawRect(crop, Offset(left, top), Size((right - left).coerceAtLeast(2f), (bottom - top).coerceAtLeast(2f)))
            }
            instances.forEach { inst ->
                val cx = mapAlong(inst.positionM.yM)
                val cy = mapAcross(inst.positionM.xM)
                drawCircle(mark, 7.dp.toPx(), Offset(cx, cy))
                val rad = Math.toRadians(inst.yawDeg)
                val dx = (sin(rad) * 22.dp.toPx()).toFloat()
                val dy = (-cos(rad) * 22.dp.toPx()).toFloat()
                drawLine(mark, Offset(cx, cy), Offset(cx + dx, cy + dy), strokeWidth = 3.dp.toPx())
                val arrow = Path().apply {
                    moveTo(cx + dx, cy + dy)
                    lineTo(cx + dx - 6f, cy + dy - 4f)
                    lineTo(cx + dx - 6f, cy + dy + 4f)
                    close()
                }
                rotate(inst.yawDeg.toFloat(), Offset(cx + dx, cy + dy)) {
                    drawPath(arrow, mark)
                }
            }
            if (type == EquipmentType.CIRCULATION_FAN && instances.size >= 2) {
                drawArc(
                    color = mark.copy(alpha = 0.45f),
                    startAngle = 200f,
                    sweepAngle = 140f,
                    useCenter = false,
                    topLeft = Offset(pad + innerW * 0.15f, pad + innerH * 0.2f),
                    size = Size(innerW * 0.7f, innerH * 0.6f),
                    style = Stroke(2.dp.toPx())
                )
            }
        }
        Text(
            "${formatMetres2(context.lengthM)} m × ${formatMetres2(context.widthM)} m × ${formatMetres2(context.heightM)} m",
            style = MaterialTheme.typography.labelSmall
        )
        if (illustration) {
            Text(
                stringResource(R.string.placement_example_pattern_only),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
