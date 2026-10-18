package com.greenhands.app.ar.ui

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.greenhands.app.R
import com.greenhands.app.ar.ArCoreAvailabilityProbe
import com.greenhands.app.ar.domain.ArAvailabilityGate
import com.greenhands.app.ar.domain.ArCoreAvailabilityKind
import com.greenhands.app.ar.domain.ArEquipmentGestures
import com.greenhands.app.ar.domain.ArGateStatus
import com.greenhands.app.ar.domain.ArGestureMode
import com.greenhands.app.ar.domain.ArHeaderCopy
import com.greenhands.app.ar.domain.ArHudContent
import com.greenhands.app.ar.domain.ArInstallOutcome
import com.greenhands.app.ar.domain.ArOverlayLayout
import com.greenhands.app.ar.domain.ArTabletopScale
import com.greenhands.app.ar.domain.ArTabletopUiState
import com.greenhands.app.ar.domain.CameraPermissionState
import com.greenhands.app.ar.render.ArCirculationRingRenderer
import com.greenhands.app.ar.render.ArExhaustAirflowRenderer
import com.greenhands.app.ar.render.ArFanPadAirflowRenderer
import com.greenhands.app.ar.render.ArFoggerMistRenderer
import com.greenhands.app.ar.render.ArEnvironmentalFieldRenderer
import com.greenhands.app.ar.render.GreenhouseArScene
import com.greenhands.app.heat.visualization.CirculationEffectFrame
import com.greenhands.app.heat.visualization.ExhaustEffectFrame
import com.greenhands.app.heat.visualization.FoggerEffectFrame
import com.greenhands.app.heat.visualization.PadInletEffectFrame
import com.greenhands.app.heat.visualization.EnvironmentalFieldFrame
import com.greenhands.app.heat.visualization.EnvironmentalFieldLegendModel
import com.greenhands.app.heat.visualization.VisualizationCopy
import com.greenhands.app.ar.render.arDomainHit
import com.greenhands.app.ar.render.arWindowToLocalOrNull
import com.greenhands.app.equipment.domain.EquipmentScreenAnchor
import com.greenhands.app.equipment.domain.MovementPlanes
import com.greenhands.app.equipment.domain.PlacementSuggester
import com.greenhands.app.equipment.model.SelectedEquipmentTool
import com.greenhands.app.equipment.ui.ClearEquipmentDialog
import com.greenhands.app.equipment.ui.DeleteEquipmentDialog
import com.greenhands.app.equipment.ui.EquipmentPlacementViewModel
import com.greenhands.app.equipment.ui.OverlayClimate
import com.greenhands.app.equipment.ui.ThresholdHelpDialog
import com.greenhands.app.equipment.ui.PaletteSlot
import com.greenhands.app.equipment.ui.ReplaceLayoutDialog
import com.greenhands.app.equipment.ui.SuggestionGalleryPresentation
import com.greenhands.app.equipment.ui.ManufacturerSpecsSheet
import com.greenhands.app.equipment.ui.SuggestionGallerySheet
import com.greenhands.app.equipment.domain.SuggestionEvidence
import com.greenhands.app.equipment.model.EquipmentLabels
import com.greenhands.app.equipment.ui.placementPalettePointer
import com.greenhands.app.equipment.ui.reportWindowRect
import com.greenhands.app.preview3d.model.DemoGreenhouseProfile
import com.greenhands.app.preview3d.model.GreenhouseDimensions
import com.greenhands.app.preview3d.model.PreviewMode
import com.greenhands.app.ui.components.PrimaryActionButton
import com.greenhands.app.ui.components.SecondaryActionButton
import com.greenhands.app.ui.components.StatusChip
import com.greenhands.app.ui.theme.Radii
import com.greenhands.app.ui.theme.Spacing
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.node.Node
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

@Composable
fun GreenhouseArScreen(
    mode: PreviewMode,
    dimensions: GreenhouseDimensions,
    cropRowCount: Int,
    onBackToPreview: () -> Unit,
    placementViewModel: EquipmentPlacementViewModel? = null,
    overlayClimate: OverlayClimate = OverlayClimate(
        crop = null,
        stage = null,
        temperatureLabel = "—",
        humidityLabel = "—",
        vpdLabel = "Not published",
        circulation = null,
        exhaust = null,
        fogger = null
    ),
    userId: String = "",
    configurationId: String = "",
    viewModel: ArTabletopViewModel = viewModel(),
    onPersistMyGreenhouse: () -> Unit = {},
    onViewDecisionSupport: () -> Unit = {}
) {
    val ui by viewModel.state.collectAsState()
    val emptyPlacement = remember {
        kotlinx.coroutines.flow.MutableStateFlow(com.greenhands.app.equipment.ui.PlacementUiState())
    }
    val placementState by (placementViewModel?.state ?: emptyPlacement).collectAsState()
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val hudModel = remember(
        overlayClimate,
        mode,
        placementState.instances,
        placementState.runtimeById,
        placementState.displayedTemperatureC,
        placementState.displayedHumidityPercent,
        placementState.simulatedTemperatureC,
        placementState.simulatedHumidityPercent,
        placementState.liveEnvironment,
        placementState.behaviorRunning,
        placementState.actualRelayById,
        placementState.weatherUi
    ) {
        ArHudContent.from(
            overlayClimate,
            mode,
            placementState.liveEnvironment,
            placementState.instances,
            placementState.runtimeById,
            simulatedTemperatureC = placementState.displayedTemperatureC
                ?: placementState.simulatedTemperatureC,
            simulatedHumidityPercent = placementState.displayedHumidityPercent
                ?: placementState.simulatedHumidityPercent,
            visualizationRunning = placementState.behaviorRunning,
            actualRelayById = placementState.actualRelayById,
            weather = placementState.weatherUi
        )
    }
    val statusClimate = remember(overlayClimate, mode) {
        ArHudContent.resolveOverlay(overlayClimate, mode)
    }
    val rows = if (mode == PreviewMode.DEMO) DemoGreenhouseProfile.CROP_ROW_COUNT else cropRowCount.coerceIn(1, 20)
    val layout = remember(dimensions, rows) { ArTabletopScale.layout(dimensions, rows) }
    var installRequested by rememberSaveable { mutableStateOf(false) }
    val view = LocalView.current
    DisposableEffect(view) {
        val previous = view.keepScreenOn
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = previous }
    }
    DisposableEffect(placementViewModel) {
        onDispose { placementViewModel?.onLeaveArScreen() }
    }

    LaunchedEffect(placementState.behaviorRunning, mode, placementViewModel) {
        if (placementViewModel == null) return@LaunchedEffect
        if (!placementState.behaviorRunning) return@LaunchedEffect
        var lastNs = withFrameNanos { frameTimeNs -> frameTimeNs }
        while (isActive) {
            val now = withFrameNanos { frameTimeNs -> frameTimeNs }
            val dtS = (now - lastNs) / 1_000_000_000.0
            lastNs = now
            if (dtS > 0.0) {
                placementViewModel.advanceVisualization(dtS)
            }
        }
    }

    LaunchedEffect(userId, configurationId, dimensions, rows, overlayClimate, placementViewModel, mode) {
        placementViewModel?.attach(
            userId = userId,
            configurationId = configurationId,
            lengthM = dimensions.lengthM,
            widthM = dimensions.widthM,
            heightM = dimensions.heightM,
            cropRowCount = rows,
        overlay = overlayClimate,
            previewMode = mode
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onPermission(granted)
    }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.onPermission(true)
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && ui.permission == CameraPermissionState.GRANTED) {
                val kind = ArCoreAvailabilityProbe.kind(context)
                viewModel.onAvailability(kind)
                if (
                    (kind == ArCoreAvailabilityKind.SUPPORTED_INSTALLED ||
                        kind == ArCoreAvailabilityKind.SUPPORTED_NOT_INSTALLED ||
                        kind == ArCoreAvailabilityKind.SUPPORTED_APK_TOO_OLD) &&
                    installRequested &&
                    kind == ArCoreAvailabilityKind.SUPPORTED_INSTALLED
                ) {
                    viewModel.onInstallResult(ArInstallOutcome.INSTALLED)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(ui.permission) {
        if (ui.permission != CameraPermissionState.GRANTED) return@LaunchedEffect
        repeat(12) {
            val kind = ArCoreAvailabilityProbe.kind(context)
            viewModel.onAvailability(kind)
            if (!ArAvailabilityGate.isTransient(kind)) return@LaunchedEffect
            delay(200)
        }
        viewModel.onAvailability(ArCoreAvailabilityKind.UNKNOWN_TIMED_OUT)
    }

    LaunchedEffect(ui.gate, activity) {
        val host = activity ?: return@LaunchedEffect
        if (ui.gate == ArGateStatus.NEEDS_INSTALL || ui.gate == ArGateStatus.NEEDS_UPDATE) {
            val outcome = ArCoreAvailabilityProbe.requestInstall(host, installRequested)
            if (outcome == ArInstallOutcome.REQUESTED) {
                installRequested = true
            }
            viewModel.onInstallResult(outcome)
        }
    }

    val density = LocalDensity.current
    val touchSlop = LocalViewConfiguration.current.touchSlop
    var paletteSlots by remember { mutableStateOf(listOf<PaletteSlot>()) }
    var viewportWindow by remember { mutableStateOf<Rect?>(null) }
    var hostOrigin by remember { mutableStateOf(Offset.Zero) }
    var fingerWindow by remember { mutableStateOf<Offset?>(null) }
    var sceneView by remember { mutableStateOf<ARSceneView?>(null) }
    var greenhouseRoot by remember { mutableStateOf<Node?>(null) }
    var effectRenderer by remember { mutableStateOf<ArCirculationRingRenderer?>(null) }
    var exhaustRenderer by remember { mutableStateOf<ArExhaustAirflowRenderer?>(null) }
    var padRenderer by remember { mutableStateOf<ArFanPadAirflowRenderer?>(null) }
    var fogRenderer by remember { mutableStateOf<ArFoggerMistRenderer?>(null) }
    var fieldRenderer by remember { mutableStateOf<ArEnvironmentalFieldRenderer?>(null) }
    var anchors by remember { mutableStateOf<List<EquipmentScreenAnchor>>(emptyList()) }
    var probeAnchors by remember { mutableStateOf<List<EquipmentScreenAnchor>>(emptyList()) }
    var climateOpen by rememberSaveable { mutableStateOf(false) }
    var addOpen by rememberSaveable { mutableStateOf(false) }
    var moreOpen by rememberSaveable { mutableStateOf(false) }
    var selectedOpen by rememberSaveable { mutableStateOf(false) }
    var probesOpen by rememberSaveable { mutableStateOf(false) }
    val probeMarkers = placementViewModel?.visualizationProbeMarkers(placementState) ?: emptyList()
    val probesControlVisible = placementViewModel?.visualizationProbeControlAvailable(placementState)
        ?: false
    val probeCaption = placementViewModel?.visualizationProbeCaption(placementState)
        ?: VisualizationCopy.PREDICTED_UNCALIBRATED
    val disclosureOpen = climateOpen ||
        addOpen ||
        moreOpen ||
        selectedOpen ||
        probesOpen ||
        placementState.equipmentCardExpanded ||
        placementState.behaviorSheetOpen ||
        placementState.galleryType != null ||
        placementState.showExactEditor ||
        placementState.showDeleteConfirm ||
        placementState.showClearConfirm ||
        placementState.showReplaceLayoutConfirm ||
        placementState.showThresholdHelp
    BackHandler(enabled = disclosureOpen) {
        when {
            placementState.showThresholdHelp -> placementViewModel?.toggleThresholdHelp()
            placementState.showDeleteConfirm -> placementViewModel?.cancelDelete()
            placementState.showClearConfirm -> placementViewModel?.cancelClear()
            placementState.showReplaceLayoutConfirm -> placementViewModel?.cancelReplaceLayout()
            placementState.galleryType != null -> placementViewModel?.closeSuggestionGallery()
            placementState.showExactEditor -> placementViewModel?.setShowExactEditor(false)
            selectedOpen -> selectedOpen = false
            probesOpen -> probesOpen = false
            addOpen -> addOpen = false
            moreOpen -> moreOpen = false
            climateOpen -> climateOpen = false
            placementState.behaviorSheetOpen -> placementViewModel?.toggleBehaviorSheet()
            placementState.equipmentCardExpanded -> placementViewModel?.toggleEquipmentCard()
        }
    }
    fun leaveLiveAr() {
        effectRenderer?.disposeBeforeHostDestruction()
        exhaustRenderer?.disposeBeforeHostDestruction()
        padRenderer?.disposeBeforeHostDestruction()
        fogRenderer?.disposeBeforeHostDestruction()
        fieldRenderer?.disposeBeforeHostDestruction()
        placementViewModel?.onLeaveArScreen()
        onBackToPreview()
    }
    BackHandler(enabled = !disclosureOpen) {
        leaveLiveAr()
    }
    LaunchedEffect(placementState.selectedId) {
        if (placementState.selectedId == null) selectedOpen = false
    }
    LaunchedEffect(probesControlVisible) {
        if (!probesControlVisible) probesOpen = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .testTag("preview_ar")
            .reportWindowRect { rect -> hostOrigin = Offset(rect.left, rect.top) }
            .placementPalettePointer(
                enabled = ui.isPlaced,
                hostOrigin = { hostOrigin },
                slots = { paletteSlots },
                viewport = { viewportWindow },
                onTap = { type -> placementViewModel?.tapPalette(type) },
                onDragStart = { type ->
                    placementViewModel?.enterPlacementMode()
                    viewModel.setGestureMode(ArGestureMode.EQUIPMENT_MOVE)
                    placementViewModel?.beginPaletteDrag(type)
                },
                onDragWindow = { type, window ->
                    val sv = sceneView
                    val root = greenhouseRoot
                    val local = sv?.arWindowToLocalOrNull(window.x, window.y)
                    if (sv == null || root == null || local == null) return@placementPalettePointer
                    val vm = placementViewModel ?: return@placementPalettePointer
                    val ctx = vm.placementContext()
                    val current = placementState.ghost?.takeIf { it.type == type }?.positionM
                        ?: PlacementSuggester.defaultGhostPosition(type, ctx).first
                    val lockFar = MovementPlanes.farWallFromY(current.yM, ctx.lengthM)
                    val domain = sv.arDomainHit(root, local.first, local.second, type, current, layout, lockFar)
                        ?: return@placementPalettePointer
                    vm.paletteDragTo(type, domain)
                },
                onDragEnd = { over -> placementViewModel?.endPaletteDrag(overViewport = over) },
                onDragCancel = { placementViewModel?.cancelPaletteDrag() },
                onFingerWindow = { fingerWindow = it },
                touchSlopPx = touchSlop
            )
    ) {
        Column(Modifier.fillMaxSize()) {
            ArTopBar(
                mode = mode,
                dimensions = dimensions,
                cropRowCount = rows,
                landscape = landscape,
                onBack = { leaveLiveAr() }
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .reportWindowRect { viewportWindow = it }
            ) {
                if (ui.canOpenLiveSession) {
                    GreenhouseArScene(
                        layout = layout,
                        equipment = placementState.displayInstances,
                        selectedId = placementState.selectedId,
                        selectedTool = placementState.selectedTool,
                        labelsVisible = placementState.labelsVisible,
                        ui = ui,
                        viewModel = viewModel,
                        onSelectEquipment = { id ->
                            if (id != null) {
                                placementViewModel?.enterPlacementMode()
                                placementViewModel?.setSelectedTool(SelectedEquipmentTool.MOVE)
                                viewModel.setGestureMode(ArEquipmentGestures.afterSelect())
                            } else if (ui.gestureMode == ArGestureMode.EQUIPMENT_MOVE) {
                                viewModel.setGestureMode(ArEquipmentGestures.afterClearSelection())
                            }
                            placementViewModel?.select(id)
                        },
                        onDragStart = { id ->
                            placementViewModel?.enterPlacementMode()
                            placementViewModel?.setOrbitAllowed(false)
                            placementViewModel?.setSelectedTool(SelectedEquipmentTool.MOVE)
                            viewModel.setGestureMode(ArGestureMode.EQUIPMENT_MOVE)
                            placementViewModel?.startDrag(id)
                        },
                        onDragHit = { id, domain -> placementViewModel?.dragTo(id, domain) },
                        onDragEnd = { placementViewModel?.endDrag() },
                        onAnchors = { anchors = it },
                        onBridge = { view, root ->
                            sceneView = view
                            greenhouseRoot = root
                        },
                        onEffectRenderer = { effectRenderer = it },
                        onExhaustRenderer = { exhaustRenderer = it },
                        onPadRenderer = { padRenderer = it },
                        onFogRenderer = { fogRenderer = it },
                        onFieldRenderer = { fieldRenderer = it },
                        circulationFrame = placementViewModel?.circulationEffectFrame(placementState)
                            ?: CirculationEffectFrame.EMPTY,
                        exhaustFrame = placementViewModel?.exhaustEffectFrame(placementState)
                            ?: ExhaustEffectFrame.EMPTY,
                        padFrame = placementViewModel?.padInletEffectFrame(placementState)
                            ?: PadInletEffectFrame.EMPTY,
                        fogFrame = placementViewModel?.foggerEffectFrame(placementState)
                            ?: FoggerEffectFrame.EMPTY,
                        fieldFrame = placementViewModel?.environmentalFieldFrame(placementState)
                            ?: EnvironmentalFieldFrame.EMPTY,
                        probeMarkers = probeMarkers,
                        onProbeAnchors = { probeAnchors = it },
                        modifier = Modifier.fillMaxSize()
                    )
                    ArViewportLabels(
                        placementState = placementState,
                        anchors = anchors,
                        compactLabelW = with(density) { 152.dp.toPx() }.toDouble(),
                        compactLabelH = with(density) { 22.dp.toPx() }.toDouble(),
                        selectedLabelW = with(density) { 200.dp.toPx() }.toDouble(),
                        selectedLabelH = with(density) { 42.dp.toPx() }.toDouble(),
                        densityLiftPx = with(density) { 8.dp.toPx() },
                        densityPadPx = with(density) { 8.dp.toPx() },
                        bottomReservePx = with(density) {
                            if (placementState.selected != null) 48.dp.toPx() else 8.dp.toPx()
                        },
                        padPanel = placementViewModel?.padInletEffectFrame(placementState)?.panel
                    )
                    ArCirculationProbeLabels(
                        markers = probeMarkers,
                        anchors = probeAnchors,
                        labelW = with(density) { 36.dp.toPx() }.toDouble(),
                        labelH = with(density) { 14.dp.toPx() }.toDouble(),
                        densityLiftPx = with(density) { 6.dp.toPx() },
                        densityPadPx = with(density) { 8.dp.toPx() }
                    )
                    ArEnvironmentalFieldOverlay(
                        placed = ui.isPlaced,
                        model = placementViewModel?.environmentalFieldLegend(placementState)
                            ?: EnvironmentalFieldLegendModel.HIDDEN,
                        onToggle = { placementViewModel?.toggleFieldLegendExpanded() },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = Spacing.xs, top = 52.dp)
                    )
                    if (ui.isPlaced) {
                        ArClimateSummaryChip(
                            model = hudModel,
                            mode = mode,
                            onOpen = {
                                addOpen = false
                                moreOpen = false
                                climateOpen = true
                            },
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(start = Spacing.xs, top = Spacing.xs)
                                .widthIn(max = ArOverlayLayout.STATUS_SUMMARY_WIDTH_DP.dp)
                        )
                        ArSuggestionsControl(
                            onOpen = { type ->
                                placementViewModel?.enterPlacementMode()
                                placementViewModel?.openSuggestionGallery(type)
                            },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = Spacing.xs, end = Spacing.xs)
                        )
                        if (placementState.behaviorRunning) {
                            ArViewportStopChip(
                                onStop = { placementViewModel?.stopBehavior() },
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = Spacing.xs)
                            )
                        }
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.xs, vertical = Spacing.xxs),
                            verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
                        ) {
                            if (probesControlVisible) {
                                ArProbesControl(
                                    onOpen = {
                                        climateOpen = false
                                        moreOpen = false
                                        probesOpen = true
                                    },
                                    modifier = Modifier.align(Alignment.End)
                                )
                            }
                            if (placementState.selected != null) {
                                ArSelectedEquipmentChip(
                                    placementState = placementState,
                                    onOpen = {
                                        climateOpen = false
                                        moreOpen = false
                                        selectedOpen = true
                                    }
                                )
                            }
                            if (addOpen) {
                                ArAddEquipmentPanel(
                                    onTap = { type ->
                                        placementViewModel?.tapPalette(type)
                                        viewModel.setGestureMode(ArGestureMode.EQUIPMENT_MOVE)
                                    },
                                    onSlotPlaced = { type, rect ->
                                        paletteSlots = paletteSlots.filterNot { it.type == type } +
                                            PaletteSlot(type, rect)
                                    }
                                )
                            }
                        }
                    }
                }
                when {
                    ui.gate == ArGateStatus.CHECKING || ui.gate == ArGateStatus.INSTALL_REQUESTED -> {
                        ArBusyPanel(
                            message = if (ui.gate == ArGateStatus.INSTALL_REQUESTED) {
                                stringResource(R.string.ar_installing)
                            } else {
                                stringResource(R.string.ar_checking)
                            },
                            onContinue3d = onBackToPreview,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(Spacing.xl)
                        )
                    }
                    ui.showFallback -> {
                        ArFallbackPanel(
                            status = ui.gate,
                            onContinue3d = onBackToPreview,
                            onRetryInstall = if (
                                activity != null &&
                                (ui.gate == ArGateStatus.NEEDS_INSTALL || ui.gate == ArGateStatus.NEEDS_UPDATE)
                            ) {
                                {
                                    val outcome = ArCoreAvailabilityProbe.requestInstall(activity, installRequested)
                                    if (outcome == ArInstallOutcome.REQUESTED) installRequested = true
                                    viewModel.onInstallResult(outcome)
                                }
                            } else {
                                null
                            },
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(Spacing.lg)
                                .widthIn(max = 480.dp)
                        )
                    }
                    ui.isDetecting -> {
                        ArInstructionChip(
                            text = if (ui.showTapToPlaceInstruction) {
                                stringResource(R.string.ar_tap_to_place)
                            } else {
                                stringResource(R.string.ar_find_surface)
                            },
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = Spacing.sm)
                                .padding(horizontal = Spacing.sm)
                        )
                    }
                }
            }
            if (ui.canOpenLiveSession) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ar_lower_hud")
                ) {
                    placementState.tooColdAdvisory?.let { advisory ->
                        com.greenhands.app.heat.advisory.TooColdAdvisoryBanner(
                            advisory = advisory,
                            onViewSuggestions = {
                                placementViewModel?.onViewTooColdSuggestions(onViewDecisionSupport)
                                    ?: onViewDecisionSupport()
                            },
                            modifier = Modifier
                                .padding(horizontal = Spacing.xs)
                                .padding(bottom = Spacing.xxs)
                        )
                    }
                    ArPlacementChrome(
                        ui = ui,
                        placement = placementState,
                        onAdd = {
                            climateOpen = false
                            moreOpen = false
                            selectedOpen = false
                            addOpen = !addOpen
                        },
                        onGestureMode = { viewModel.setGestureMode(it) },
                        onLabels = { placementViewModel?.toggleLabelsVisible() },
                        onMore = {
                            addOpen = false
                            climateOpen = false
                            moreOpen = true
                        }
                    )
                }
            }
        }
        if (climateOpen) {
            ArClimateDetailsSheet(
                model = hudModel,
                onDismiss = { climateOpen = false },
                tooColdAdvisory = placementState.tooColdAdvisory,
                onViewDecisionSupport = {
                    placementViewModel?.onViewTooColdSuggestions(onViewDecisionSupport)
                        ?: onViewDecisionSupport()
                }
            )
        }
        if (moreOpen) {
            ArMoreSheet(
                ui = ui,
                placement = placementState,
                onEquipmentStatus = {
                    moreOpen = false
                    placementViewModel?.toggleEquipmentCard()
                },
                onBehavior = {
                    moreOpen = false
                    placementViewModel?.toggleBehaviorSheet()
                },
                onEnvironmentalLayer = { layer -> placementViewModel?.setEnvironmentalLayer(layer) },
                onFieldHeight = { height -> placementViewModel?.setFieldHeight(height) },
                onClear = {
                    moreOpen = false
                    placementViewModel?.requestClear()
                },
                onReset = {
                    moreOpen = false
                    viewModel.resetView()
                },
                onReposition = {
                    moreOpen = false
                    viewModel.reposition()
                    placementViewModel?.onArReposition()
                },
                onSave = {
                    moreOpen = false
                    placementViewModel?.saveLayout()
                    if (mode == PreviewMode.MY_GREENHOUSE) onPersistMyGreenhouse()
                },
                onDismiss = { moreOpen = false }
            )
        }
        if (selectedOpen && placementState.selected != null) {
            ArSelectedEquipmentSheet(
                placementState = placementState,
                placementViewModel = placementViewModel,
                onDismiss = { selectedOpen = false }
            )
        }
        if (probesOpen) {
            ArProbesSheet(
                markers = probeMarkers,
                caption = probeCaption,
                onDismiss = { probesOpen = false },
                showHumidity = placementState.selected?.type ==
                    com.greenhands.app.equipment.model.EquipmentType.EXHAUST_FAN,
                padSourceLabel = (placementState.displayedFieldSnapshot
                    ?: placementState.physicsSnapshot)?.fanPad?.sourceLabel,
                padOutletTemperatureC = (placementState.displayedFieldSnapshot
                    ?: placementState.physicsSnapshot)?.fanPad?.padOutletTemperatureC,
                padOutletRhPercent = (placementState.displayedFieldSnapshot
                    ?: placementState.physicsSnapshot)?.fanPad?.padOutletRelativeHumidityPercent
            )
        }
        if (placementState.equipmentCardExpanded) {
            ArEquipmentStatusSheet(
                placement = placementState,
                climate = statusClimate,
                onHelp = { placementViewModel?.toggleThresholdHelp() },
                onDismiss = { placementViewModel?.toggleEquipmentCard() }
            )
        }
        if (placementState.behaviorSheetOpen && placementViewModel != null) {
            ArBehaviorSheet(
                placement = placementState,
                viewModel = placementViewModel,
                onViewDecisionSupport = onViewDecisionSupport
            )
        }
        if (placementViewModel != null) {
            ManufacturerSpecsSheet(
                placement = placementState,
                viewModel = placementViewModel
            )
        }
        if (placementState.showThresholdHelp) {
            ThresholdHelpDialog(onDismiss = { placementViewModel?.toggleThresholdHelp() })
        }
        if (placementState.showClearConfirm) {
            ClearEquipmentDialog(
                itemCount = placementState.instances.size,
                onConfirm = { placementViewModel?.confirmClear() },
                onDismiss = { placementViewModel?.cancelClear() }
            )
        }
        if (placementState.showDeleteConfirm) {
            DeleteEquipmentDialog(
                name = placementState.pendingDeleteLabel,
                confirmLabel = placementState.pendingDeleteConfirm.ifBlank { stringResource(R.string.placement_delete) },
                onConfirm = { placementViewModel?.deleteSelected() },
                onDismiss = { placementViewModel?.cancelDelete() }
            )
        }
        val galleryType = placementState.galleryType
        if (galleryType != null && placementViewModel != null) {
            SuggestionGallerySheet(
                type = galleryType,
                context = placementViewModel.placementContext(),
                recommendation = placementState.recommendation,
                isDemo = placementState.isDemo,
                presentation = SuggestionGalleryPresentation.AR_MODAL,
                whyRationale = if (placementState.showWhyPosition) {
                    SuggestionEvidence.rationale(
                        galleryType,
                        placementState.recommendation,
                        placementViewModel.placementContext(),
                        illustrationOnly = !placementState.isDemo &&
                            !SuggestionEvidence.canApplyCalculatedLayout(
                                SuggestionEvidence.countFor(galleryType, placementState.recommendation)
                            )
                    )
                } else {
                    null
                },
                onWhy = { placementViewModel.openWhyPosition() },
                onApply = { placementViewModel.requestApplySuggestionLayout(galleryType) },
                onClose = { placementViewModel.closeSuggestionGallery() }
            )
        }
        if (placementState.showReplaceLayoutConfirm && placementViewModel != null) {
            val replaceType = placementState.pendingReplaceType
            if (replaceType != null) {
                ReplaceLayoutDialog(
                    typeTitle = EquipmentLabels.typeTitle(replaceType),
                    onConfirm = { placementViewModel.confirmReplaceLayout() },
                    onDismiss = { placementViewModel.cancelReplaceLayout() }
                )
            }
        }
        fingerWindow?.let { finger ->
            Surface(
                modifier = Modifier
                    .offset {
                        IntOffset((finger.x - hostOrigin.x).roundToInt() - 48, (finger.y - hostOrigin.y).roundToInt() - 48)
                    }
                    .testTag("palette_drag_preview"),
                shape = Radii.md,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f)
            ) {
                Text(
                    placementState.paletteDraggingType?.displayName ?: "",
                    modifier = Modifier.padding(Spacing.sm),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
private fun ArTopBar(
    mode: PreviewMode,
    dimensions: GreenhouseDimensions,
    cropRowCount: Int,
    landscape: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val badge = if (mode == PreviewMode.DEMO) {
        stringResource(R.string.preview3d_badge_demo)
    } else {
        stringResource(R.string.preview3d_badge_mine)
    }
    val subtitle = ArHeaderCopy.compactSubtitle(mode, dimensions, cropRowCount)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.sm, vertical = Spacing.xxs)
            .testTag("ar_top_bar"),
        shape = Radii.md,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
    ) {
        Row(
            modifier = Modifier.padding(end = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(Spacing.touch)
                    .testTag("ar_back")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back)
                )
            }
            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Text(
                        text = stringResource(R.string.ar_title),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.semantics { heading() },
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                    StatusChip(badge, modifier = Modifier.testTag("ar_mode_badge"))
                }
                Text(
                    text = subtitle,
                    style = if (landscape) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("ar_header_dimensions")
                )
            }
        }
    }
}

@Composable
private fun ArInstructionChip(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.testTag("ar_instruction"),
        shape = Radii.md,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            maxLines = 2
        )
    }
}

@Composable
private fun ArBusyPanel(
    message: String,
    onContinue3d: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.testTag("ar_busy"), shape = Radii.md, color = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.padding(Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator()
            Spacer(Modifier.height(Spacing.md))
            Text(message, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(Spacing.section))
            SecondaryActionButton(
                text = stringResource(R.string.ar_continue_3d),
                onClick = onContinue3d,
                modifier = Modifier.testTag("ar_continue_3d")
            )
        }
    }
}

@Composable
fun ArFallbackPanel(
    status: ArGateStatus,
    onContinue3d: () -> Unit,
    onRetryInstall: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val title = when (status) {
        ArGateStatus.PERMISSION_DENIED -> stringResource(R.string.ar_permission_denied_title)
        ArGateStatus.UNSUPPORTED -> stringResource(R.string.ar_unsupported_title)
        ArGateStatus.NEEDS_INSTALL, ArGateStatus.INSTALL_DECLINED ->
            stringResource(R.string.ar_install_title)
        ArGateStatus.NEEDS_UPDATE -> stringResource(R.string.ar_update_title)
        ArGateStatus.SESSION_FAILED, ArGateStatus.TRANSIENT_ERROR ->
            stringResource(R.string.ar_unavailable_title)
        else -> stringResource(R.string.ar_unavailable_title)
    }
    val body = when (status) {
        ArGateStatus.PERMISSION_DENIED -> stringResource(R.string.ar_permission_denied_body)
        ArGateStatus.UNSUPPORTED -> stringResource(R.string.ar_unsupported_body)
        ArGateStatus.NEEDS_INSTALL, ArGateStatus.INSTALL_DECLINED ->
            stringResource(R.string.ar_install_body)
        ArGateStatus.NEEDS_UPDATE -> stringResource(R.string.ar_update_body)
        else -> stringResource(R.string.ar_unavailable_body)
    }
    Surface(
        modifier = modifier.testTag("ar_fallback"),
        shape = Radii.md,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.padding(Spacing.xl)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(Spacing.sm))
            Text(body, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(Spacing.section))
            if (onRetryInstall != null) {
                PrimaryActionButton(
                    text = stringResource(R.string.ar_install_action),
                    onClick = onRetryInstall,
                    modifier = Modifier.testTag("ar_install_action")
                )
                Spacer(Modifier.height(Spacing.sm))
            }
            SecondaryActionButton(
                text = stringResource(R.string.ar_continue_3d),
                onClick = onContinue3d,
                modifier = Modifier.testTag("ar_continue_3d")
            )
        }
    }
}
