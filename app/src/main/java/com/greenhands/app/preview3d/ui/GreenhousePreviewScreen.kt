package com.greenhands.app.preview3d.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.greenhands.app.R
import com.greenhands.app.equipment.domain.EquipmentLabelCatalog
import com.greenhands.app.equipment.domain.EquipmentScreenAnchor
import com.greenhands.app.equipment.domain.MovementPlanes
import com.greenhands.app.equipment.domain.PlacementAxis
import com.greenhands.app.equipment.domain.PlacementAxisLimits
import com.greenhands.app.equipment.domain.PlacementSuggester
import com.greenhands.app.equipment.domain.PlacementValidator
import com.greenhands.app.equipment.domain.ViewportLabelLayout
import com.greenhands.app.equipment.model.PreviewInteractionMode
import com.greenhands.app.equipment.model.EquipmentLabels
import com.greenhands.app.equipment.model.SelectedEquipmentTool
import com.greenhands.app.equipment.render.EquipmentSceneMapper
import com.greenhands.app.equipment.ui.ClearLayoutDialog
import com.greenhands.app.equipment.ui.CompactCropEnvironmentRow
import com.greenhands.app.equipment.ui.CoordinateEditor
import com.greenhands.app.equipment.ui.DeleteEquipmentDialog
import com.greenhands.app.equipment.ui.EquipmentPalette
import com.greenhands.app.equipment.ui.EquipmentPlacementViewModel
import com.greenhands.app.equipment.ui.EquipmentStatusOverlay
import com.greenhands.app.equipment.ui.OverlayClimate
import com.greenhands.app.ar.domain.ArEquipmentInstanceStatus
import com.greenhands.app.ar.domain.ArHudContent
import com.greenhands.app.ar.domain.EquipmentStateSource
import com.greenhands.app.equipment.ui.PaletteSlot
import com.greenhands.app.equipment.ui.PlacementControlStrip
import com.greenhands.app.equipment.ui.PlacementCoordinateGuideCard
import com.greenhands.app.equipment.ui.ReplaceLayoutDialog
import com.greenhands.app.equipment.ui.SelectedEquipmentBar
import com.greenhands.app.equipment.ui.ManufacturerSpecsSheet
import com.greenhands.app.equipment.ui.SuggestionGallerySheet
import com.greenhands.app.equipment.model.ManufacturerSpecCopy
import com.greenhands.app.equipment.ui.SuggestionRail
import com.greenhands.app.equipment.ui.ThresholdHelpDialog
import com.greenhands.app.equipment.ui.ViewportCoordinateLabel
import com.greenhands.app.equipment.ui.WhyPositionSheet
import com.greenhands.app.equipment.ui.placementPalettePointer
import com.greenhands.app.equipment.ui.reportWindowRect
import com.greenhands.app.equipment.domain.SuggestionEvidence
import com.greenhands.app.preview3d.domain.PreviewCameraFramer
import com.greenhands.app.preview3d.domain.SceneScaleMapper
import com.greenhands.app.preview3d.model.DemoGreenhouseProfile
import com.greenhands.app.preview3d.model.GreenhouseDimensions
import com.greenhands.app.preview3d.model.PreviewMode
import com.greenhands.app.preview3d.render.GreenhousePreviewScene
import com.greenhands.app.preview3d.render.pickDomain
import com.greenhands.app.preview3d.render.windowToLocalOrNull
import com.greenhands.app.ui.components.PrimaryActionButton
import com.greenhands.app.ui.components.SecondaryActionButton
import com.greenhands.app.ui.components.StatusChip
import com.greenhands.app.ui.theme.Radii
import com.greenhands.app.ui.theme.Spacing
import io.github.sceneview.SceneView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.roundToInt

private enum class PreviewRenderState {
    LOADING,
    READY,
    ERROR
}

@Composable
fun GreenhousePreviewScreen(
    mode: PreviewMode,
    dimensions: GreenhouseDimensions,
    cropRowCount: Int,
    onBack: () -> Unit,
    placementViewModel: EquipmentPlacementViewModel? = null,
    overlayClimate: OverlayClimate = OverlayClimate(
        crop = null,
        stage = null,
        temperatureLabel = "—",
        humidityLabel = "—",
        vpdLabel = "VPD target not published",
        circulation = null,
        exhaust = null,
        fogger = null
    ),
    userId: String = "",
    configurationId: String = "",
    onViewInAr: () -> Unit = {},
    onPersistMyGreenhouse: () -> Unit = {},
    onViewDecisionSupport: () -> Unit = {}
) {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val rows = if (mode == PreviewMode.DEMO) {
        DemoGreenhouseProfile.CROP_ROW_COUNT
    } else {
        cropRowCount.coerceIn(1, 20)
    }
    val layout = SceneScaleMapper.layout(dimensions, cropRowCount = rows)
    var resetEpoch by rememberSaveable { mutableIntStateOf(0) }
    var retryEpoch by rememberSaveable { mutableIntStateOf(0) }
    var renderState by remember { mutableStateOf(PreviewRenderState.LOADING) }
    var errorMessage by remember { mutableStateOf("") }
    var sceneView by remember { mutableStateOf<SceneView?>(null) }
    var anchors by remember { mutableStateOf<List<EquipmentScreenAnchor>>(emptyList()) }
    val placementFlow = remember(placementViewModel) {
        placementViewModel?.state ?: MutableStateFlow(com.greenhands.app.equipment.ui.PlacementUiState())
    }
    val placementState by placementFlow.collectAsState()
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
    val equipmentPlacements = EquipmentSceneMapper.placements(placementState.displayInstances, layout)
    val density = LocalDensity.current
    val touchSlop = LocalViewConfiguration.current.touchSlop
    var paletteSlots by remember { mutableStateOf(listOf<PaletteSlot>()) }
    var viewportWindow by remember { mutableStateOf<Rect?>(null) }
    var hostOrigin by remember { mutableStateOf(Offset.Zero) }
    var fingerWindow by remember { mutableStateOf<Offset?>(null) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .testTag("preview_3d")
            .reportWindowRect { rect -> hostOrigin = Offset(rect.left, rect.top) }
            .placementPalettePointer(
                enabled = placementState.mode == PreviewInteractionMode.PLACEMENT,
                hostOrigin = { hostOrigin },
                slots = { paletteSlots },
                viewport = { viewportWindow },
                onTap = { type -> placementViewModel?.tapPalette(type) },
                onDragStart = { type -> placementViewModel?.beginPaletteDrag(type) },
                onDragWindow = { type, window ->
                    val sv = sceneView
                    val local = sv?.windowToLocalOrNull(window.x, window.y)
                    if (sv == null || local == null) return@placementPalettePointer
                    val vm = placementViewModel ?: return@placementPalettePointer
                    val ctx = vm.placementContext()
                    val current = placementState.ghost?.takeIf { it.type == type }?.positionM
                        ?: PlacementSuggester.defaultGhostPosition(type, ctx).first
                    val lockFar = MovementPlanes.farWallFromY(current.yM, ctx.lengthM)
                    val plane = MovementPlanes.scenePlane(type, current, layout, lockFar)
                    val domain = sv.pickDomain(local.first, local.second, plane, layout) ?: return@placementPalettePointer
                    vm.paletteDragTo(type, domain)
                },
                onDragEnd = { over -> placementViewModel?.endPaletteDrag(overViewport = over) },
                onDragCancel = { placementViewModel?.cancelPaletteDrag() },
                onFingerWindow = { fingerWindow = it },
                touchSlopPx = touchSlop
            )
    ) {
        val wide = PreviewOverlayLayout.isWideLandscape(maxWidth.value, landscape)
        val compact = maxWidth < 600.dp || landscape
        val inPlacement = placementState.mode == PreviewInteractionMode.PLACEMENT
        val controlStrip: @Composable () -> Unit = {
            Column {
            PlacementControlStrip(
                mode = placementState.mode,
                orbitAllowed = placementState.orbitAllowed,
                labelsVisible = placementState.labelsVisible,
                moveSelected = placementState.selectedTool == SelectedEquipmentTool.MOVE,
                onEnterPlacement = { placementViewModel?.enterPlacementMode() },
                onExitPlacement = { placementViewModel?.enterViewMode() },
                onMove = {
                    placementViewModel?.setOrbitAllowed(false)
                    placementViewModel?.setSelectedTool(SelectedEquipmentTool.MOVE)
                },
                onOrbit = { placementViewModel?.setOrbitAllowed(!placementState.orbitAllowed) },
                onLabels = { placementViewModel?.toggleLabelsVisible() },
                onEquipmentStatus = { placementViewModel?.toggleEquipmentCard() },
                onReset = {
                    resetEpoch += 1
                    placementViewModel?.resetView()
                },
                onSave = {
                    placementViewModel?.saveLayout()
                    if (mode == PreviewMode.MY_GREENHOUSE) onPersistMyGreenhouse()
                },
                onClear = { placementViewModel?.requestClear() },
                statusMessage = placementState.statusMessage,
                statusOpen = placementState.equipmentCardExpanded,
                showSave = placementState.showsPersistentSave
            )
            if (mode == PreviewMode.MY_GREENHOUSE && placementViewModel != null) {
                TextButton(
                    onClick = { placementViewModel.openManufacturerSpecsSheet() },
                    modifier = Modifier.testTag("preview_enter_specs")
                ) {
                    Text(ManufacturerSpecCopy.ENTER)
                }
            }
            }
        }
        val palette: @Composable () -> Unit = {
            EquipmentPalette(
                onTap = { placementViewModel?.tapPalette(it) },
                onSlotPlaced = { type, rect ->
                    paletteSlots = paletteSlots.filterNot { it.type == type } + PaletteSlot(type, rect)
                }
            )
        }
        val viewportHost: @Composable (Modifier) -> Unit = { hostModifier ->
            PreviewViewportHost(
                modifier = hostModifier,
                layout = layout,
                resetEpoch = resetEpoch + placementState.cameraResetEpoch,
                retryEpoch = retryEpoch,
                renderState = renderState,
                errorMessage = errorMessage,
                placementState = placementState,
                equipmentPlacements = equipmentPlacements,
                anchors = anchors,
                densityLiftPx = with(density) { 8.dp.toPx() },
                densityPadPx = with(density) { 8.dp.toPx() },
                compactLabelW = with(density) { 152.dp.toPx() }.toDouble(),
                compactLabelH = with(density) { 22.dp.toPx() }.toDouble(),
                selectedLabelW = with(density) { 200.dp.toPx() }.toDouble(),
                selectedLabelH = with(density) { 42.dp.toPx() }.toDouble(),
                onReady = { renderState = PreviewRenderState.READY },
                onError = { message ->
                    errorMessage = message
                    renderState = PreviewRenderState.ERROR
                },
                onRetry = {
                    errorMessage = ""
                    renderState = PreviewRenderState.LOADING
                    retryEpoch += 1
                    resetEpoch += 1
                },
                onBack = onBack,
                onAnchors = { anchors = it },
                onSceneView = { sceneView = it },
                placementViewModel = placementViewModel,
                onViewportRect = { viewportWindow = it }
            )
        }

        val equipmentStatus = ArHudContent.workingCopyStatus(
            placementState.instances,
            placementState.runtimeById,
            EquipmentStateSource.forMode(mode)
        )
        val liveStatusLabel = if (mode == PreviewMode.MY_GREENHOUSE) {
            ArHudContent.liveStatusLabel(placementState.liveEnvironment)
        } else {
            null
        }
        val liveReadingLabel = if (mode == PreviewMode.MY_GREENHOUSE) {
            "${ArHudContent.liveTemperatureLabel(placementState.liveEnvironment)} · ${ArHudContent.liveHumidityLabel(placementState.liveEnvironment)}"
        } else {
            null
        }
        val outsideReadingLabel = if (mode == PreviewMode.MY_GREENHOUSE) {
            "${ArHudContent.outsideTemperatureLabel(placementState.weatherUi)} · ${ArHudContent.outsideHumidityLabel(placementState.weatherUi)}"
        } else {
            null
        }
        val outsideStatusLabel = if (mode == PreviewMode.MY_GREENHOUSE) {
            ArHudContent.outsideStatusLabel(placementState.weatherUi)
        } else {
            null
        }
        val outsideAttributionLabel = if (mode == PreviewMode.MY_GREENHOUSE) {
            listOfNotNull(
                ArHudContent.outsideLocationLabel(placementState.weatherUi).takeIf { it.isNotBlank() },
                com.greenhands.app.telemetry.WeatherCopy.MODELLED_OPEN_METEO
            ).joinToString(" · ")
        } else {
            null
        }
        if (wide) {
            Column(Modifier.fillMaxSize()) {
                PreviewTopStack(
                    mode = mode,
                    dimensions = dimensions,
                    cropRowCount = rows,
                    landscape = landscape,
                    compact = compact,
                    overlayClimate = overlayClimate,
                    environmentKind = placementState.environmentKind,
                    liveStatusLabel = liveStatusLabel,
                    liveReadingLabel = liveReadingLabel,
                    outsideReadingLabel = outsideReadingLabel,
                    outsideStatusLabel = outsideStatusLabel,
                    outsideAttributionLabel = outsideAttributionLabel,
                    onBack = onBack,
                    onViewInAr = onViewInAr,
                    tooColdAdvisory = placementState.tooColdAdvisory,
                    onViewDecisionSupport = {
                        placementViewModel?.onViewTooColdSuggestions(onViewDecisionSupport)
                            ?: onViewDecisionSupport()
                    }
                )
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    PlacementScenePane(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        showStatusSheet = placementState.equipmentCardExpanded,
                        onDismissStatus = { placementViewModel?.toggleEquipmentCard() },
                        overlayClimate = overlayClimate,
                        instanceNames = equipmentStatus.instances.map { it.name },
                        instanceStatuses = equipmentStatus.instances,
                        onHelp = { placementViewModel?.toggleThresholdHelp() },
                        suggestionRail = {
                            if (inPlacement) {
                                SuggestionRail(
                                    onOpen = { placementViewModel?.openSuggestionGallery(it) },
                                    modifier = Modifier.align(Alignment.CenterEnd)
                                )
                            }
                        },
                        selectedOverlay = {
                            PlacementSelectedOverlay(
                                placementState = placementState,
                                placementViewModel = placementViewModel
                            )
                        },
                        viewport = { viewportHost(Modifier.fillMaxSize()) }
                    )
                    Column(
                        modifier = Modifier
                            .width(280.dp)
                            .fillMaxHeight()
                            .padding(Spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        controlStrip()
                        if (inPlacement) palette()
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                PreviewTopStack(
                    mode = mode,
                    dimensions = dimensions,
                    cropRowCount = rows,
                    landscape = landscape,
                    compact = compact,
                    overlayClimate = overlayClimate,
                    environmentKind = placementState.environmentKind,
                    liveStatusLabel = liveStatusLabel,
                    liveReadingLabel = liveReadingLabel,
                    outsideReadingLabel = outsideReadingLabel,
                    outsideStatusLabel = outsideStatusLabel,
                    outsideAttributionLabel = outsideAttributionLabel,
                    onBack = onBack,
                    onViewInAr = onViewInAr,
                    tooColdAdvisory = placementState.tooColdAdvisory,
                    onViewDecisionSupport = {
                        placementViewModel?.onViewTooColdSuggestions(onViewDecisionSupport)
                            ?: onViewDecisionSupport()
                    }
                )
                PlacementPortraitWorkspace(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    inPlacement = inPlacement,
                    controlStrip = controlStrip,
                    palette = palette,
                    suggestionRail = {
                        if (inPlacement) {
                            SuggestionRail(
                                onOpen = { placementViewModel?.openSuggestionGallery(it) },
                                modifier = Modifier.align(Alignment.CenterEnd)
                            )
                        }
                    },
                    selectedOverlay = {
                        PlacementSelectedOverlay(
                            placementState = placementState,
                            placementViewModel = placementViewModel
                        )
                    },
                    statusSheet = if (placementState.equipmentCardExpanded) {
                        {
                            EquipmentStatusOverlay(
                                climate = overlayClimate,
                                instanceNames = equipmentStatus.instances.map { it.name },
                                onHelp = { placementViewModel?.toggleThresholdHelp() },
                                onDismiss = { placementViewModel?.toggleEquipmentCard() },
                                instanceStatuses = equipmentStatus.instances
                            )
                        }
                    } else {
                        null
                    },
                    onDismissStatus = { placementViewModel?.toggleEquipmentCard() },
                    viewport = { viewportHost(Modifier.fillMaxSize()) }
                )
            }
        }

        if (placementState.showClearConfirm) {
            ClearLayoutDialog(
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
        val galleryType = placementState.galleryType
        if (galleryType != null && placementViewModel != null) {
            SuggestionGallerySheet(
                type = galleryType,
                context = placementViewModel.placementContext(),
                recommendation = placementState.recommendation,
                isDemo = placementState.isDemo,
                onWhy = { placementViewModel.openWhyPosition() },
                onApply = {
                    placementViewModel.requestApplySuggestionLayout(galleryType)
                },
                onClose = { placementViewModel.closeSuggestionGallery() }
            )
        }
        if (placementState.showWhyPosition && galleryType != null && placementViewModel != null) {
            WhyPositionSheet(
                rationale = SuggestionEvidence.rationale(
                    galleryType,
                    placementState.recommendation,
                    placementViewModel.placementContext(),
                    illustrationOnly = !placementState.isDemo &&
                        !SuggestionEvidence.canApplyCalculatedLayout(
                            SuggestionEvidence.countFor(galleryType, placementState.recommendation)
                        )
                ),
                onClose = { placementViewModel.closeWhyPosition() }
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
    }
}

@Composable
fun PlacementPortraitWorkspace(
    inPlacement: Boolean,
    controlStrip: @Composable () -> Unit,
    palette: @Composable () -> Unit,
    suggestionRail: @Composable BoxScope.() -> Unit,
    selectedOverlay: @Composable BoxScope.() -> Unit,
    viewport: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    statusSheet: (@Composable () -> Unit)? = null,
    onDismissStatus: () -> Unit = {}
) {
    Column(modifier.fillMaxSize()) {
        PlacementScenePane(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .heightIn(min = 280.dp),
            showStatusSheet = statusSheet != null,
            onDismissStatus = onDismissStatus,
            overlayClimate = null,
            instanceNames = emptyList(),
            instanceStatuses = emptyList(),
            onHelp = {},
            suggestionRail = suggestionRail,
            selectedOverlay = selectedOverlay,
            viewport = viewport,
            statusSheet = statusSheet
        )
        controlStrip()
        if (inPlacement) palette()
    }
}

@Composable
private fun PlacementScenePane(
    modifier: Modifier,
    showStatusSheet: Boolean,
    onDismissStatus: () -> Unit,
    overlayClimate: OverlayClimate?,
    instanceNames: List<String>,
    onHelp: () -> Unit,
    suggestionRail: @Composable BoxScope.() -> Unit,
    selectedOverlay: @Composable BoxScope.() -> Unit,
    viewport: @Composable () -> Unit,
    statusSheet: (@Composable () -> Unit)? = null,
    instanceStatuses: List<ArEquipmentInstanceStatus> = emptyList()
) {
    BoxWithConstraints(modifier) {
        viewport()
        val crowded = maxWidth < 360.dp || maxHeight < 300.dp
        PlacementCoordinateGuideCard(
            crowded = crowded,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = Spacing.sm, top = Spacing.sm, end = PreviewOverlayLayout.SUGGESTION_RAIL_WIDTH_DP.dp)
        )
        suggestionRail()
        selectedOverlay()
        val sheet = statusSheet ?: if (showStatusSheet && overlayClimate != null) {
            {
                EquipmentStatusOverlay(
                    climate = overlayClimate,
                    instanceNames = instanceNames,
                    onHelp = onHelp,
                    onDismiss = onDismissStatus,
                    instanceStatuses = instanceStatuses
                )
            }
        } else {
            null
        }
        if (sheet != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.28f))
                    .clickable(onClick = onDismissStatus)
                    .testTag("equipment_status_scrim")
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(Spacing.sm)
            ) {
                sheet()
            }
        }
    }
}

@Composable
private fun BoxScope.PlacementSelectedOverlay(
    placementState: com.greenhands.app.equipment.ui.PlacementUiState,
    placementViewModel: EquipmentPlacementViewModel?
) {
    val selected = placementState.selected
    if (placementState.mode != PreviewInteractionMode.PLACEMENT) return
    if (selected == null || placementState.paletteDraggingType != null) return
    if (placementState.draggingId != null) return
    val ctx = placementViewModel?.placementContext()
    val others = placementState.instances
    val xLimit = if (ctx != null) {
        PlacementAxisLimits.forAxis(selected, others, ctx, PlacementAxis.X)
    } else null
    val yLimit = if (ctx != null) {
        PlacementAxisLimits.forAxis(selected, others, ctx, PlacementAxis.Y)
    } else null
    val zLimit = if (ctx != null) {
        PlacementAxisLimits.forAxis(selected, others, ctx, PlacementAxis.Z)
    } else null
    val elevation = if (ctx != null) {
        PlacementValidator.elevationRange(selected.type, ctx, selected.dimensions)
    } else null
    Column(
        modifier = Modifier
            .align(Alignment.BottomStart)
            .fillMaxWidth()
            .heightIn(max = 240.dp)
            .padding(
                start = Spacing.sm,
                end = PreviewOverlayLayout.SUGGESTION_RAIL_WIDTH_DP.dp,
                bottom = Spacing.sm
            ),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        SelectedEquipmentBar(
            state = placementState,
            onMove = { placementViewModel?.setSelectedTool(SelectedEquipmentTool.MOVE) },
            onRotate90 = { placementViewModel?.rotateSelected(90.0) },
            onDuplicate = { placementViewModel?.duplicateSelected() },
            onDelete = { placementViewModel?.requestDelete() },
            onAxisX = { placementViewModel?.setSelectedTool(SelectedEquipmentTool.X) },
            onAxisY = { placementViewModel?.setSelectedTool(SelectedEquipmentTool.Y) },
            onAxisZ = { placementViewModel?.setSelectedTool(SelectedEquipmentTool.Z) },
            onExact = { placementViewModel?.setShowExactEditor(true) },
            onCoordinate = { axis, value ->
                when (axis) {
                    PlacementAxis.X -> placementViewModel?.setAxisX(value)
                    PlacementAxis.Y -> placementViewModel?.setAxisY(value)
                    PlacementAxis.Z -> placementViewModel?.setAxisZ(value)
                }
            },
            xLimit = xLimit,
            yLimit = yLimit,
            zLimit = zLimit
        )
        if (placementState.showExactEditor) {
            CoordinateEditor(
                state = placementState,
                onApply = { x, y, z, yaw -> placementViewModel?.applyNumericEdit(x, y, z, yaw) },
                onRotate = { placementViewModel?.rotateSelected() },
                onDuplicate = { placementViewModel?.duplicateSelected() },
                onDelete = { placementViewModel?.requestDelete() },
                onConfirmGhost = { placementViewModel?.confirmGhost() },
                onCancelGhost = { placementViewModel?.cancelGhost() },
                elevationMinM = elevation?.start,
                elevationMaxM = elevation?.endInclusive,
                onElevation = { placementViewModel?.setElevation(it) },
                modifier = Modifier.heightIn(max = 180.dp)
            )
        }
    }
}

@Composable
private fun PreviewTopStack(
    mode: PreviewMode,
    dimensions: GreenhouseDimensions,
    cropRowCount: Int,
    landscape: Boolean,
    compact: Boolean,
    overlayClimate: OverlayClimate,
    environmentKind: com.greenhands.app.equipment.future.EnvironmentDataSourceKind,
    liveStatusLabel: String? = null,
    liveReadingLabel: String? = null,
    outsideReadingLabel: String? = null,
    outsideStatusLabel: String? = null,
    outsideAttributionLabel: String? = null,
    onBack: () -> Unit,
    onViewInAr: () -> Unit,
    tooColdAdvisory: com.greenhands.app.heat.advisory.TooColdAdvisory? = null,
    onViewDecisionSupport: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("preview_top_stack")
    ) {
        PreviewOverlayBar(
            mode = mode,
            dimensions = dimensions,
            cropRowCount = cropRowCount,
            landscape = landscape,
            compact = compact,
            onBack = onBack,
            onViewInAr = onViewInAr,
            modifier = Modifier.fillMaxWidth()
        )
        CompactCropEnvironmentRow(
            climate = overlayClimate,
            kind = environmentKind,
            liveStatusLabel = liveStatusLabel,
            liveReadingLabel = liveReadingLabel,
            outsideReadingLabel = outsideReadingLabel,
            outsideStatusLabel = outsideStatusLabel,
            outsideAttributionLabel = outsideAttributionLabel,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.sm, vertical = Spacing.xxs)
        )
        tooColdAdvisory?.let { advisory ->
            com.greenhands.app.heat.advisory.TooColdAdvisoryBanner(
                advisory = advisory,
                onViewSuggestions = onViewDecisionSupport,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xxs)
            )
        }
    }
}

@Composable
private fun PreviewViewportHost(
    modifier: Modifier,
    layout: com.greenhands.app.preview3d.domain.SceneLayout,
    resetEpoch: Int,
    retryEpoch: Int,
    renderState: PreviewRenderState,
    errorMessage: String,
    placementState: com.greenhands.app.equipment.ui.PlacementUiState,
    equipmentPlacements: List<com.greenhands.app.equipment.render.EquipmentScenePlacement>,
    anchors: List<EquipmentScreenAnchor>,
    densityLiftPx: Float,
    densityPadPx: Float,
    compactLabelW: Double,
    compactLabelH: Double,
    selectedLabelW: Double,
    selectedLabelH: Double,
    onReady: () -> Unit,
    onError: (String) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onAnchors: (List<EquipmentScreenAnchor>) -> Unit,
    onSceneView: (SceneView?) -> Unit,
    placementViewModel: EquipmentPlacementViewModel?,
    onViewportRect: (Rect) -> Unit
) {
    Box(modifier.reportWindowRect(onViewportRect)) {
        PreviewViewport(
            modifier = Modifier.fillMaxSize(),
            layout = layout,
            resetEpoch = resetEpoch,
            retryEpoch = retryEpoch,
            renderState = renderState,
            errorMessage = errorMessage,
            placementState = placementState,
            equipmentPlacements = equipmentPlacements,
            anchors = anchors,
            densityLiftPx = densityLiftPx,
            densityPadPx = densityPadPx,
            compactLabelW = compactLabelW,
            compactLabelH = compactLabelH,
            selectedLabelW = selectedLabelW,
            selectedLabelH = selectedLabelH,
            onReady = onReady,
            onError = onError,
            onRetry = onRetry,
            onBack = onBack,
            onAnchors = onAnchors,
            onSceneView = onSceneView,
            placementViewModel = placementViewModel
        )
    }
}

@Composable
private fun PreviewViewport(
    modifier: Modifier,
    layout: com.greenhands.app.preview3d.domain.SceneLayout,
    resetEpoch: Int,
    retryEpoch: Int,
    renderState: PreviewRenderState,
    errorMessage: String,
    placementState: com.greenhands.app.equipment.ui.PlacementUiState,
    equipmentPlacements: List<com.greenhands.app.equipment.render.EquipmentScenePlacement>,
    anchors: List<EquipmentScreenAnchor>,
    densityLiftPx: Float,
    densityPadPx: Float,
    compactLabelW: Double,
    compactLabelH: Double,
    selectedLabelW: Double,
    selectedLabelH: Double,
    onReady: () -> Unit,
    onError: (String) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onAnchors: (List<EquipmentScreenAnchor>) -> Unit,
    onSceneView: (SceneView?) -> Unit,
    placementViewModel: EquipmentPlacementViewModel?
) {
    BoxWithConstraints(modifier.testTag("preview_viewport")) {
        val viewportAspect = if (maxHeight.value <= 0f) {
            PreviewCameraFramer.PORTRAIT_ASPECT
        } else {
            (maxWidth.value / maxHeight.value).toDouble()
                .coerceIn(PreviewCameraFramer.MIN_ASPECT, PreviewCameraFramer.MAX_ASPECT)
        }
        if (renderState != PreviewRenderState.ERROR) {
            key(retryEpoch) {
                GreenhousePreviewScene(
                    layout = layout,
                    viewportAspect = viewportAspect,
                    resetEpoch = resetEpoch,
                    onReady = onReady,
                    onError = onError,
                    modifier = Modifier.fillMaxSize(),
                    interactionMode = placementState.mode,
                    orbitAllowed = placementState.orbitAllowed,
                    equipment = equipmentPlacements,
                    selectedEquipmentId = placementState.selectedId,
                    onSelectEquipment = { id -> placementViewModel?.select(id) },
                    onDragStart = { id -> placementViewModel?.startDrag(id) },
                    onDragHit = { id, domain -> placementViewModel?.dragTo(id, domain) },
                    onDragEnd = { placementViewModel?.endDrag() },
                    onAnchors = onAnchors,
                    onSceneView = onSceneView,
                    selectedTool = placementState.selectedTool
                )
            }
        }
        val contents = EquipmentLabelCatalog.contents(
            instances = placementState.displayInstances,
            selectedId = placementState.selectedId,
            draggingId = placementState.draggingId,
            labelsVisible = placementState.labelsVisible
        )
        val byId = anchors.associateBy { it.id }
        val layoutInputs = contents.mapNotNull { content ->
            val anchor = byId[content.id] ?: return@mapNotNull null
            ViewportLabelLayout.Input(
                id = content.id,
                anchorXPx = anchor.xPx.toDouble(),
                anchorYPx = anchor.yPx.toDouble(),
                widthPx = if (content.selected) selectedLabelW else compactLabelW,
                heightPx = if (content.selected) selectedLabelH else compactLabelH,
                selected = content.selected
            )
        }
        val viewportW = anchors.firstOrNull()?.viewportWidthPx?.toDouble() ?: 0.0
        val viewportH = anchors.firstOrNull()?.viewportHeightPx?.toDouble() ?: 0.0
        val placed = ViewportLabelLayout.place(
            inputs = layoutInputs,
            viewportWidthPx = viewportW,
            viewportHeightPx = viewportH,
            padPx = densityPadPx.toDouble(),
            liftPx = densityLiftPx.toDouble()
        )
        if (renderState == PreviewRenderState.READY) {
            val contentById = contents.associateBy { it.id }
            placed.sortedBy { it.selected }.forEach { item ->
                val content = contentById[item.id] ?: return@forEach
                ViewportCoordinateLabel(
                    name = content.name,
                    xM = content.xM,
                    yM = content.yM,
                    zM = content.zM,
                    compact = !content.showCoordinates,
                    selected = content.selected,
                    modifier = Modifier
                        .testTag("viewport_label_" + content.name.replace(' ', '_'))
                        .offset {
                            IntOffset(item.xPx.roundToInt(), item.yPx.roundToInt())
                        }
                )
            }
        }
        if (renderState == PreviewRenderState.LOADING) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("preview_loading"),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(Spacing.md))
                    Text(
                        stringResource(R.string.preview3d_loading),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
        if (renderState == PreviewRenderState.ERROR) {
            PreviewErrorPanel(
                message = errorMessage.ifBlank { stringResource(R.string.preview3d_error_body) },
                onRetry = onRetry,
                onBack = onBack,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(Spacing.xl)
                    .widthIn(max = 480.dp)
            )
        }
    }
}

@Composable
private fun PreviewOverlayBar(
    mode: PreviewMode,
    dimensions: GreenhouseDimensions,
    cropRowCount: Int,
    landscape: Boolean,
    compact: Boolean,
    onBack: () -> Unit,
    onViewInAr: () -> Unit,
    modifier: Modifier = Modifier
) {
    val badge = if (mode == PreviewMode.DEMO) {
        stringResource(R.string.preview3d_badge_demo)
    } else {
        stringResource(R.string.preview3d_badge_mine)
    }
    val summary = if (mode == PreviewMode.DEMO) {
        DemoGreenhouseProfile.displayLabel()
    } else {
        dimensions.displaySummary()
    }
    val rowSummary = stringResource(R.string.preview3d_rows_summary, cropRowCount)
    Surface(
        modifier = modifier.padding(Spacing.sm),
        shape = Radii.md,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
    ) {
        Row(
            modifier = Modifier.padding(end = Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(Spacing.touch)
                    .testTag("preview_back")
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
                        text = stringResource(R.string.preview3d_predicted),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.semantics { heading() }
                    )
                    StatusChip(badge, modifier = Modifier.testTag("preview_3d_badge"))
                }
                Text(
                    text = summary,
                    style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                    maxLines = if (landscape) 1 else 2
                )
                Text(
                    text = rowSummary,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.testTag("preview_crop_row_count")
                )
            }
            TextButton(
                onClick = onViewInAr,
                modifier = Modifier
                    .testTag("preview_view_in_ar")
                    .heightIn(min = Spacing.touch)
            ) {
                Text(
                    text = stringResource(R.string.ar_view_in_ar),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun PreviewErrorPanel(
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.testTag("preview_error"),
        shape = Radii.md,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.padding(Spacing.xl)) {
            Text(
                stringResource(R.string.preview3d_error_title),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(message, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(Spacing.section))
            PrimaryActionButton(
                text = stringResource(R.string.preview3d_retry),
                onClick = onRetry,
                modifier = Modifier.testTag("preview_retry")
            )
            Spacer(Modifier.height(Spacing.sm))
            SecondaryActionButton(
                text = stringResource(R.string.action_back),
                onClick = onBack,
                modifier = Modifier.testTag("preview_error_back")
            )
        }
    }
}
