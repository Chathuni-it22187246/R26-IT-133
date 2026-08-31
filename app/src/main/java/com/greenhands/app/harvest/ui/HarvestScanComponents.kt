package com.greenhands.app.harvest.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.greenhands.app.R
import com.greenhands.app.harvest.detection.HarvestScanPhase
import com.greenhands.app.harvest.detection.HybridScanConfig
import com.greenhands.app.harvest.detection.HybridTargetValidator
import com.greenhands.app.harvest.detection.ScanTargetType
import com.greenhands.app.harvest.detection.TargetAutoCaptureController
import com.greenhands.app.harvest.detection.TargetCaptureTick
import com.greenhands.app.harvest.detection.TargetDetectorFactory
import com.greenhands.app.harvest.detection.TargetRejectReason
import com.greenhands.app.harvest.detection.TargetRegionCropper
import com.greenhands.app.harvest.domain.HarvestArgbFrame
import com.greenhands.app.harvest.domain.HarvestEnvironmentContext
import com.greenhands.app.harvest.domain.HarvestFrameBuffer
import com.greenhands.app.harvest.domain.HarvestScanTimestamps
import com.greenhands.app.harvest.domain.MaturityAssessment
import com.greenhands.app.harvest.domain.MaturityCalculator
import com.greenhands.app.harvest.domain.MaturityReferenceKind
import com.greenhands.app.harvest.domain.MaturityTiming
import com.greenhands.app.harvest.domain.PlantingDates
import com.greenhands.app.harvest.model.HarvestDecision
import com.greenhands.app.harvest.model.HarvestSaveStatus
import com.greenhands.app.harvest.model.ScanRecord
import com.greenhands.app.harvest.model.ScanType
import com.greenhands.app.harvest.model.VarietyReference
import com.greenhands.app.harvest.domain.PlantHealthStatus
import com.greenhands.app.ui.components.PrimaryActionButton
import com.greenhands.app.ui.components.StatusChip
import com.greenhands.app.ui.theme.AmberWarning
import com.greenhands.app.ui.theme.ClimateTeal
import com.greenhands.app.ui.theme.ForestEmerald
import com.greenhands.app.ui.theme.GhType
import com.greenhands.app.ui.theme.SoftError
import com.greenhands.app.ui.theme.NightBg
import com.greenhands.app.ui.theme.NightElevated
import com.greenhands.app.ui.theme.NightText
import com.greenhands.app.ui.theme.Radii
import com.greenhands.app.ui.theme.Spacing
import com.greenhands.app.ui.theme.Stroke as GhStroke
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@Composable
fun MockCameraScanScaffold(
    title: String,
    instruction: String,
    statusText: String,
    onBack: () -> Unit,
    targetType: ScanTargetType,
    analyzing: Boolean,
    hasResult: Boolean,
    onValidatedCapture: (HarvestArgbFrame) -> Unit,
    overlay: (@Composable () -> Unit)?,
    testTag: String,
    showDemoChip: Boolean = false,
    captureEnabled: Boolean = true
) {
    val context = LocalContext.current
    val cameraPermission = rememberHarvestCameraPermissionState()
    val frameBuffer = remember { HarvestFrameBuffer() }
    val hybrid = remember { HybridTargetValidator() }
    val experimentalDetector = remember {
        if (HybridScanConfig.USE_EXPERIMENTAL_TFLITE_DETECTOR) {
            TargetDetectorFactory.createExperimental(context)
        } else {
            null
        }
    }
    DisposableEffect(experimentalDetector) {
        onDispose { experimentalDetector?.close() }
    }
    val controller = remember(targetType) {
        TargetAutoCaptureController(
            expected = targetType,
            modelReady = true
        )
    }
    val cropper = remember { TargetRegionCropper() }
    val analyzingFlag = remember { AtomicBoolean(false) }
    analyzingFlag.set(analyzing)
    val captureAllowed = remember { AtomicBoolean(true) }
    captureAllowed.set(captureEnabled)
    val captureSink = remember { AtomicReference<(HarvestArgbFrame) -> Unit>({}) }
    captureSink.set(onValidatedCapture)
    var latestTick by remember { mutableStateOf<TargetCaptureTick?>(null) }
    var latestFrame by remember { mutableStateOf<HarvestArgbFrame?>(null) }
    val session = remember(hybrid, experimentalDetector, controller, targetType) {
        HarvestTargetCameraSession(
            expected = targetType,
            hybrid = hybrid,
            experimentalDetector = experimentalDetector,
            controller = controller,
            analyzing = analyzingFlag,
            onTick = { tick, frame ->
                latestTick = tick
                latestFrame = frame
            },
            onAutoCapture = { crop ->
                if (captureAllowed.get()) captureSink.get().invoke(crop)
            }
        )
    }
    val tick = latestTick
    val box = tick?.takeIf { it.validation.detected }?.detection?.boundingBox
    val manualEnabled = captureEnabled &&
        cameraPermission.granted &&
        !analyzing &&
        tick?.validation?.readyForManualCapture == true &&
        tick.detection != null &&
        latestFrame != null
    val displayStatus = when {
        !cameraPermission.granted -> statusText
        analyzing || hasResult -> statusText
        else -> detectionStatusText(targetType, tick)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NightBg)
            .testTag(testTag)
    ) {
        if (cameraPermission.granted) {
            HarvestCameraPreview(
                frameBuffer = frameBuffer,
                targetSession = session,
                modifier = Modifier.fillMaxSize()
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            MockScanTopBar(title = title, onBack = onBack, showDemoChip = showDemoChip)
            Text(
                text = instruction,
                style = MaterialTheme.typography.bodyMedium,
                color = NightText.copy(alpha = 0.86f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.xl)
            )
            Spacer(Modifier.height(Spacing.md))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg)
            ) {
                if (cameraPermission.showDeniedMessage) {
                    CameraPermissionPanel(
                        onRetry = cameraPermission.requestOrRetry,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                if (!cameraPermission.showDeniedMessage && box == null && !hasResult) {
                    ScanFocusBrackets(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth()
                            .height(280.dp)
                            .padding(horizontal = Spacing.lg)
                    )
                }
                TargetBoundingBoxOverlay(
                    box = box,
                    modifier = Modifier.fillMaxSize()
                )
                if (!hasResult && cameraPermission.granted) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = Spacing.sm)
                    ) {
                        TargetDetectionBanner(targetType = targetType, tick = tick)
                    }
                }
                if (overlay != null && cameraPermission.granted && hasResult) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = Spacing.md)
                    ) {
                        overlay()
                    }
                }
            }
            Text(
                text = displayStatus,
                style = MaterialTheme.typography.labelLarge,
                color = NightText.copy(alpha = 0.8f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.xl)
                    .testTag("harvest_scan_status")
            )
            Spacer(Modifier.height(Spacing.lg))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.xl)
                    .padding(bottom = Spacing.xxl),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                CaptureButton(
                    enabled = manualEnabled,
                    onClick = {
                        val detection = tick?.detection ?: return@CaptureButton
                        val frame = latestFrame ?: return@CaptureButton
                        if (tick.validation.readyForManualCapture) {
                            onValidatedCapture(cropper.crop(frame, detection.boundingBox))
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun TargetBoundingBoxOverlay(
    box: com.greenhands.app.harvest.detection.NormalizedRect?,
    modifier: Modifier = Modifier
) {
    if (box == null) return
    Canvas(modifier.testTag("harvest_target_box")) {
        val stroke = 3.dp.toPx()
        drawRect(
            color = ForestEmerald,
            topLeft = Offset(box.left * size.width, box.top * size.height),
            size = Size(box.width * size.width, box.height * size.height),
            style = Stroke(width = stroke)
        )
    }
}

@Composable
private fun TargetDetectionBanner(targetType: ScanTargetType, tick: TargetCaptureTick?) {
    val phase = tick?.phase ?: HarvestScanPhase.SEARCHING
    val fruit = targetType == ScanTargetType.TOMATO_FRUIT
    val headline = when {
        phase == HarvestScanPhase.MODEL_UNAVAILABLE ||
            tick?.validation?.reason == TargetRejectReason.MODEL_UNAVAILABLE ->
            stringResource(R.string.harvest_detect_model_chip)
        phase == HarvestScanPhase.CAPTURING ->
            stringResource(R.string.harvest_detect_capturing)
        phase == HarvestScanPhase.HOLD_STEADY ->
            stringResource(R.string.harvest_detect_hold_steady)
        phase == HarvestScanPhase.TARGET_DETECTED && fruit ->
            stringResource(R.string.harvest_detect_tomato_detected)
        phase == HarvestScanPhase.TARGET_DETECTED ->
            stringResource(R.string.harvest_detect_leaf_detected)
        else -> hybridRejectHeadline(fruit, tick?.validation?.reason)
            ?: if (fruit) {
                stringResource(R.string.harvest_detect_searching_fruit)
            } else {
                stringResource(R.string.harvest_detect_searching_leaf)
            }
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md)
            .testTag("harvest_detect_banner"),
        shape = Radii.lg,
        color = NightElevated.copy(alpha = 0.92f),
        border = BorderStroke(GhStroke.hairline, ForestEmerald.copy(alpha = 0.4f))
    ) {
        Column(Modifier.padding(Spacing.md)) {
            Text(headline, style = MaterialTheme.typography.titleMedium, color = NightText)
            if (phase == HarvestScanPhase.MODEL_UNAVAILABLE) {
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    stringResource(R.string.harvest_detect_model_missing),
                    style = MaterialTheme.typography.bodySmall,
                    color = AmberWarning
                )
            }
        }
    }
}

@Composable
private fun hybridRejectHeadline(fruit: Boolean, reason: TargetRejectReason?): String? {
    return when (reason) {
        TargetRejectReason.TARGET_TOO_SMALL -> if (fruit) {
            stringResource(R.string.harvest_detect_too_small_fruit)
        } else {
            stringResource(R.string.harvest_detect_too_small_leaf)
        }
        TargetRejectReason.TARGET_NOT_CENTERED -> if (fruit) {
            stringResource(R.string.harvest_detect_not_centered_fruit)
        } else {
            stringResource(R.string.harvest_detect_not_centered_leaf)
        }
        TargetRejectReason.SHAPE_NOT_FRUIT_LIKE,
        TargetRejectReason.NO_FRUIT_LIKE_REGION,
        TargetRejectReason.INSUFFICIENT_COLOR_EVIDENCE,
        TargetRejectReason.WRONG_TARGET -> if (fruit) {
            stringResource(R.string.harvest_detect_not_fruit_like)
        } else {
            stringResource(R.string.harvest_detect_not_leaf_like)
        }
        TargetRejectReason.SHAPE_NOT_LEAF_LIKE,
        TargetRejectReason.NO_LEAF_LIKE_REGION,
        TargetRejectReason.INSUFFICIENT_VEGETATION_EVIDENCE ->
            stringResource(R.string.harvest_detect_not_leaf_like)
        else -> null
    }
}

@Composable
private fun detectionStatusText(targetType: ScanTargetType, tick: TargetCaptureTick?): String {
    val fruit = targetType == ScanTargetType.TOMATO_FRUIT
    return when (tick?.phase) {
        HarvestScanPhase.MODEL_UNAVAILABLE ->
            stringResource(R.string.harvest_detect_model_missing)
        HarvestScanPhase.CAPTURING -> stringResource(R.string.harvest_detect_capturing)
        HarvestScanPhase.HOLD_STEADY -> stringResource(R.string.harvest_detect_hold_steady)
        HarvestScanPhase.TARGET_DETECTED -> if (fruit) {
            stringResource(R.string.harvest_detect_tomato_detected)
        } else {
            stringResource(R.string.harvest_detect_leaf_detected)
        }
        HarvestScanPhase.ANALYZING -> stringResource(R.string.harvest_scan_status_analyzing)
        HarvestScanPhase.RESULT -> if (fruit) {
            stringResource(R.string.harvest_detect_tomato_detected)
        } else {
            stringResource(R.string.harvest_detect_leaf_detected)
        }
        else -> hybridRejectHeadline(fruit, tick?.validation?.reason)
            ?: if (fruit) {
                stringResource(R.string.harvest_detect_searching_fruit)
            } else {
                stringResource(R.string.harvest_detect_searching_leaf)
            }
    }
}

@Composable
private fun MockScanTopBar(title: String, onBack: () -> Unit, showDemoChip: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(Spacing.touch)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.cd_back),
                tint = NightText
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = NightText,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(Modifier.weight(1f))
        if (showDemoChip) {
            StatusChip(text = stringResource(R.string.harvest_demo_chip))
            Spacer(Modifier.width(Spacing.md))
        }
    }
}

@Composable
private fun CameraPermissionPanel(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md)
            .testTag("harvest_camera_permission"),
        shape = Radii.lg,
        color = NightElevated.copy(alpha = 0.94f),
        border = BorderStroke(GhStroke.hairline, ForestEmerald.copy(alpha = 0.35f))
    ) {
        Column(Modifier.padding(Spacing.lg)) {
            Text(
                text = stringResource(R.string.harvest_camera_permission_title),
                style = MaterialTheme.typography.titleMedium,
                color = NightText
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = stringResource(R.string.harvest_camera_permission_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(Spacing.lg))
            PrimaryActionButton(
                text = stringResource(R.string.harvest_camera_permission_retry),
                onClick = onRetry,
                modifier = Modifier.testTag("harvest_camera_permission_retry")
            )
        }
    }
}

@Composable
fun ScanFocusBrackets(modifier: Modifier = Modifier) {
    val accent = ForestEmerald
    Canvas(modifier.testTag("harvest_scan_frame")) {
        val len = 36.dp.toPx()
        val stroke = 3.dp.toPx()
        val inset = 8.dp.toPx()
        val left = inset
        val top = inset
        val right = size.width - inset
        val bottom = size.height - inset
        fun corner(x: Float, y: Float, dx: Float, dy: Float) {
            drawLine(accent, Offset(x, y), Offset(x + dx, y), stroke, StrokeCap.Round)
            drawLine(accent, Offset(x, y), Offset(x, y + dy), stroke, StrokeCap.Round)
        }
        corner(left, top, len, len)
        corner(right, top, -len, len)
        corner(left, bottom, len, -len)
        corner(right, bottom, -len, -len)
    }
}

@Composable
private fun CaptureButton(enabled: Boolean = true, onClick: () -> Unit) {
    val captureLabel = stringResource(R.string.harvest_cd_capture)
    val ring = if (enabled) ForestEmerald else NightText.copy(alpha = 0.35f)
    Box(
        modifier = Modifier
            .size(84.dp)
            .background(ring.copy(alpha = 0.18f), CircleShape)
            .semantics {
                role = Role.Button
                contentDescription = captureLabel
            }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .testTag("harvest_scan_capture"),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.size(72.dp)) {
                drawCircle(
                    color = ring,
                    radius = size.minDimension / 2f,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx())
                )
                drawCircle(
                    color = ring,
                    radius = size.minDimension * 0.32f
                )
            }
        }
    }
}

@Composable
fun MockArOverlayCard(
    heading: String,
    status: String,
    lineOne: String?,
    lineTwo: String?,
    statusColor: Color,
    onViewDetails: () -> Unit,
    modifier: Modifier = Modifier,
    showDemoChip: Boolean = true,
    onDetectedIssueClick: (() -> Unit)? = null
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md)
            .testTag("harvest_ar_overlay"),
        shape = Radii.lg,
        color = NightElevated.copy(alpha = 0.94f),
        border = BorderStroke(GhStroke.hairline, ForestEmerald.copy(alpha = 0.45f))
    ) {
        Column(Modifier.padding(Spacing.lg)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    heading,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (showDemoChip) {
                    StatusChip(text = stringResource(R.string.harvest_demo_chip))
                }
            }
            Spacer(Modifier.height(Spacing.xs))
            Text(
                status,
                style = MaterialTheme.typography.titleLarge,
                color = statusColor
            )
            if (!lineOne.isNullOrBlank()) {
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    lineOne,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = if (onDetectedIssueClick != null) {
                        Modifier
                            .clickable(onClick = onDetectedIssueClick)
                            .testTag("harvest_detected_issue")
                    } else {
                        Modifier
                    }
                )
            }
            if (!lineTwo.isNullOrBlank()) {
                Text(
                    lineTwo,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(Spacing.sm))
            Row(
                modifier = Modifier
                    .clickable(onClick = onViewDetails)
                    .testTag("harvest_view_details")
                    .padding(vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.harvest_view_details),
                    style = MaterialTheme.typography.titleSmall,
                    color = ForestEmerald
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = ForestEmerald
                )
            }
        }
    }
}

fun harvestDecisionColor(decision: HarvestDecision, scanRequired: Boolean = false): Color {
    if (scanRequired) return AmberWarning
    return when (decision) {
        HarvestDecision.READY_TO_HARVEST -> ForestEmerald
        HarvestDecision.NOT_READY -> AmberWarning
        HarvestDecision.HOLD_INSPECT -> SoftError
        HarvestDecision.UNCERTAIN -> AmberWarning
    }
}

fun plantHealthStatusColor(status: PlantHealthStatus, scanRequired: Boolean = false): Color {
    if (scanRequired) return AmberWarning
    return when (status) {
        PlantHealthStatus.HEALTHY -> ForestEmerald
        PlantHealthStatus.WARNING -> AmberWarning
        PlantHealthStatus.UNHEALTHY -> SoftError
        PlantHealthStatus.UNCERTAIN -> AmberWarning
    }
}

fun historyStatusColor(record: ScanRecord): Color {
    return when (record.scanType) {
        ScanType.FRUIT_SCAN -> harvestDecisionColor(
            decision = record.harvestDecision ?: HarvestDecision.UNCERTAIN,
            scanRequired = record.harvestDecision == null
        )
        ScanType.LEAF_SCAN -> {
            val label = record.plantHealthStatus
            val scanRequired = label.isNullOrBlank() || label == "SCAN REQUIRED"
            val status = when (label) {
                "HEALTHY" -> PlantHealthStatus.HEALTHY
                "WARNING" -> PlantHealthStatus.WARNING
                "UNHEALTHY" -> PlantHealthStatus.UNHEALTHY
                else -> PlantHealthStatus.UNCERTAIN
            }
            plantHealthStatusColor(status, scanRequired)
        }
    }
}

@Composable
fun ResultField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Column(
        modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Spacing.xxs))
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun ResultFieldsCard(content: @Composable ColumnScope.() -> Unit) {
    com.greenhands.app.ui.components.InfoCard(content = content)
}

@Composable
fun HarvestSaveSection(
    canSave: Boolean,
    saveStatus: HarvestSaveStatus,
    onSave: () -> Unit
) {
    val alreadySaved = saveStatus == HarvestSaveStatus.SAVED ||
        saveStatus == HarvestSaveStatus.ALREADY_SAVED
    PrimaryActionButton(
        text = stringResource(R.string.harvest_save_records),
        onClick = onSave,
        enabled = canSave && saveStatus != HarvestSaveStatus.SAVING && !alreadySaved,
        modifier = Modifier.testTag("harvest_save_records")
    )
    when {
        !canSave -> {
            Spacer(Modifier.height(Spacing.md))
            Text(
                stringResource(R.string.harvest_save_complete_scan),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("harvest_save_blocked")
            )
        }
        alreadySaved -> {
            Spacer(Modifier.height(Spacing.md))
            Text(
                stringResource(R.string.harvest_save_success),
                style = MaterialTheme.typography.bodyMedium,
                color = ForestEmerald,
                modifier = Modifier.testTag("harvest_save_success")
            )
        }
        saveStatus == HarvestSaveStatus.FAILED -> {
            Spacer(Modifier.height(Spacing.md))
            Text(
                stringResource(R.string.harvest_save_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = SoftError,
                modifier = Modifier.testTag("harvest_save_failed")
            )
        }
        saveStatus == HarvestSaveStatus.NO_VALID_SCAN -> {
            Spacer(Modifier.height(Spacing.md))
            Text(
                stringResource(R.string.harvest_save_complete_scan),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun HarvestEnvironmentCard(state: HarvestUiState, modifier: Modifier = Modifier) {
    val sensor = state.sensorUi
    com.greenhands.app.ui.components.InfoCard(
        modifier = modifier.testTag("harvest_live_environment")
    ) {
        if (sensor.isConnecting) {
            Text(
                sensor.statusText,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.testTag("harvest_env_connecting")
            )
            sensor.pendingDeviceNote?.let { note ->
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("harvest_env_disclaimer")
                )
            }
            return@InfoCard
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    stringResource(R.string.harvest_field_temperature),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    sensor.temperatureText,
                    style = GhType.metric,
                    color = ClimateTeal,
                    modifier = Modifier.testTag("harvest_sample_temp")
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    stringResource(R.string.harvest_field_humidity),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    sensor.humidityText,
                    style = GhType.metric,
                    color = ClimateTeal,
                    modifier = Modifier.testTag("harvest_sample_rh")
                )
            }
        }
        Spacer(Modifier.height(Spacing.md))
        Text(
            stringResource(R.string.harvest_env_status_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Spacing.xxs))
        Text(
            sensor.statusText,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.testTag("harvest_env_status")
        )
        Spacer(Modifier.height(Spacing.sm))
        StatusChip(
            text = sensor.statusText,
            modifier = Modifier.testTag("harvest_env_source")
        )
        sensor.pendingDeviceNote?.let { note ->
            Spacer(Modifier.height(Spacing.sm))
            Text(
                note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("harvest_env_disclaimer")
            )
        }
    }
}

@Composable
fun HarvestSessionSummaryCard(state: HarvestUiState, modifier: Modifier = Modifier) {
    com.greenhands.app.ui.components.InfoCard(
        modifier = modifier.testTag("harvest_session_summary")
    ) {
        ResultField(
            label = stringResource(R.string.harvest_field_crop),
            value = state.cropType
        )
        Spacer(Modifier.height(Spacing.md))
        ResultField(
            label = stringResource(R.string.harvest_variety_label),
            value = state.selectedVariety?.variety
                ?: stringResource(R.string.harvest_variety_not_selected)
        )
        val ripeColor = state.selectedVariety?.documentedRipeColor
        if (!ripeColor.isNullOrBlank()) {
            Spacer(Modifier.height(Spacing.md))
            ResultField(
                label = stringResource(R.string.harvest_variety_ripe_color_label),
                value = ripeColor
            )
        }
        Spacer(Modifier.height(Spacing.md))
        ResultField(
            label = stringResource(R.string.harvest_planting_date_label),
            value = state.plantingDateUtcMillis?.let { PlantingDates.formatDisplay(it) }
                ?: stringResource(R.string.harvest_planting_date_not_selected)
        )
        if (state.daysSincePlanting != null) {
            Spacer(Modifier.height(Spacing.md))
            ResultField(
                label = stringResource(R.string.harvest_days_since_planting_label),
                value = state.daysSincePlanting.toString()
            )
        } else {
            Spacer(Modifier.height(Spacing.md))
            ResultField(
                label = stringResource(R.string.harvest_days_since_planting_label),
                value = "—"
            )
        }
        Spacer(Modifier.height(Spacing.md))
        ResultField(
            label = stringResource(R.string.harvest_expected_maturity_range_label),
            value = expectedMaturityRangeLabel(state.maturity)
        )
        Spacer(Modifier.height(Spacing.md))
        ResultField(
            label = stringResource(R.string.harvest_estimated_days_remaining_label),
            value = estimatedDaysRemainingLabel(state.maturity)
        )
        Spacer(Modifier.height(Spacing.md))
        ResultField(
            label = stringResource(R.string.harvest_maturity_status_label),
            value = maturityStatusLabel(state.maturity.timing)
        )
        Spacer(Modifier.height(Spacing.md))
        ResultField(
            label = stringResource(R.string.harvest_maturity_reference_label),
            value = maturityReferenceLabel(state.maturity)
        )
    }
}

@Composable
fun PlantingDateSummaryCard(state: HarvestUiState, modifier: Modifier = Modifier) {
    HarvestSessionSummaryCard(state, modifier)
}

@Composable
fun maturityStatusLabel(timing: MaturityTiming): String = when (timing) {
    MaturityTiming.DATA_UNAVAILABLE -> stringResource(R.string.harvest_maturity_data_unavailable)
    MaturityTiming.NEEDS_TRANSPLANT_DATE -> stringResource(R.string.harvest_maturity_needs_planting_date)
    MaturityTiming.BEFORE_WINDOW -> stringResource(R.string.harvest_maturity_before_window)
    MaturityTiming.WITHIN_WINDOW -> stringResource(R.string.harvest_maturity_within_window)
    MaturityTiming.PAST_WINDOW -> stringResource(R.string.harvest_maturity_past_window)
}

@Composable
fun maturityReferenceLabel(assessment: MaturityAssessment): String {
    return when (assessment.referenceKind) {
        MaturityReferenceKind.VARIETY_SPECIFIC ->
            stringResource(R.string.harvest_maturity_reference_variety)
        MaturityReferenceKind.GENERAL_TOMATO ->
            stringResource(R.string.harvest_maturity_reference_general)
        MaturityReferenceKind.NONE ->
            stringResource(R.string.harvest_maturity_data_unavailable)
    }
}

@Composable
fun expectedMaturityRangeLabel(assessment: MaturityAssessment): String {
    val min = assessment.expectedMinDays
    val max = assessment.expectedMaxDays
    return if (min != null && max != null) {
        stringResource(R.string.harvest_maturity_range_days, min, max)
    } else {
        stringResource(R.string.harvest_maturity_data_unavailable)
    }
}

@Composable
fun estimatedDaysRemainingLabel(assessment: MaturityAssessment): String {
    val remaining = assessment.estimatedDaysRemaining
        ?: return stringResource(R.string.harvest_maturity_not_calculable)
    return stringResource(R.string.harvest_estimated_days_remaining_value, remaining)
}

fun varietyMaturityRangeText(variety: VarietyReference): String? {
    return if (MaturityCalculator.hasUsableMaturityWindow(variety)) {
        val min = variety.expectedMaturityMinDays ?: return null
        val max = variety.expectedMaturityMaxDays ?: return null
        "$min–$max days after transplant"
    } else {
        null
    }
}

@Composable
fun HarvestHistoryListItem(
    record: ScanRecord,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    com.greenhands.app.ui.components.InfoCard(
        modifier = modifier.testTag("harvest_history_item"),
        onClick = onClick
    ) {
        Text(
            record.listHeadline,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.testTag("harvest_history_item_headline")
        )
        Spacer(Modifier.height(Spacing.xxs))
        Text(
            record.listStatus,
            style = MaterialTheme.typography.titleMedium,
            color = historyStatusColor(record),
            modifier = Modifier.testTag("harvest_history_item_status")
        )
        record.listDetail?.let { detail ->
            Spacer(Modifier.height(Spacing.xxs))
            Text(
                detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(Spacing.xs))
        Text(
            HarvestScanTimestamps.formatList(record.scannedAtEpochMillis),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("harvest_history_item_time")
        )
        Spacer(Modifier.height(Spacing.xxs))
        Text(
            record.listMeta,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun SavedEnvironmentSnapshotCard(record: ScanRecord, modifier: Modifier = Modifier) {
    com.greenhands.app.ui.components.InfoCard(
        modifier = modifier.testTag("harvest_saved_environment")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.harvest_env_source_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            StatusChip(
                text = record.environmentSource,
                modifier = Modifier.testTag("harvest_saved_env_source")
            )
        }
        if (record.isPreviewEnvironment) {
            Spacer(Modifier.height(Spacing.sm))
            Text(
                HarvestEnvironmentContext.PREVIEW_DISCLAIMER,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(Spacing.lg))
        ResultField(
            label = stringResource(R.string.harvest_field_temperature),
            value = record.temperatureC?.let {
                String.format(java.util.Locale.US, "%.1f°C", it)
            } ?: "—"
        )
        Spacer(Modifier.height(Spacing.md))
        ResultField(
            label = stringResource(R.string.harvest_field_humidity),
            value = record.humidityPercent?.let {
                String.format(java.util.Locale.US, "%.0f%%", it)
            } ?: "—"
        )
    }
}

@Composable
fun storedMaturityStatusLabel(status: String?): String {
    if (status.isNullOrBlank()) return "—"
    val timing = runCatching { MaturityTiming.valueOf(status) }.getOrNull()
    return if (timing != null) maturityStatusLabel(timing) else status
}
