package com.greenhands.app.ar.render

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import com.google.android.filament.Engine
import com.google.ar.core.Config
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.TrackingState
import com.greenhands.app.ar.ArCoreAvailabilityProbe
import com.greenhands.app.ar.domain.ArEquipmentGestures
import com.greenhands.app.ar.domain.ArGestureMode
import com.greenhands.app.ar.domain.ArPlacementAdapter
import com.greenhands.app.ar.domain.ArTabletopPhase
import com.greenhands.app.ar.domain.ArTabletopScale
import com.greenhands.app.ar.domain.ArTabletopUiState
import com.greenhands.app.ar.ui.ArTabletopViewModel
import com.greenhands.app.equipment.domain.EquipmentScreenAnchor
import com.greenhands.app.equipment.domain.MovementPlanes
import com.greenhands.app.equipment.domain.ViewportLabelLayout
import com.greenhands.app.equipment.domain.ViewportMath
import com.greenhands.app.equipment.model.DomainPoint
import com.greenhands.app.equipment.model.EquipmentType
import com.greenhands.app.equipment.model.SelectedEquipmentTool
import com.greenhands.app.equipment.render.EquipmentSceneMapper
import com.greenhands.app.preview3d.domain.SceneLayout
import com.greenhands.app.preview3d.render.EquipmentSceneBinder
import com.greenhands.app.preview3d.render.equipmentInstanceId
import com.greenhands.app.preview3d.render.windowToLocalOrNull
import com.greenhands.app.heat.visualization.CirculationEffectFrame
import com.greenhands.app.heat.visualization.ExhaustEffectFrame
import com.greenhands.app.heat.visualization.FoggerEffectFrame
import com.greenhands.app.heat.visualization.PadInletEffectFrame
import dev.romainguy.kotlin.math.Float2
import io.github.sceneview.ar.ARScene
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.ar.node.PoseNode
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.math.Size
import io.github.sceneview.node.CubeNode
import io.github.sceneview.node.Node
import io.github.sceneview.rememberOnGestureListener

/**
 * Live ARCore tabletop scene. Greenhouse mesh, crop rows and equipment share one
 * [AnchorNode] root. Geometry comes from [com.greenhands.app.preview3d.domain.GreenhouseMeshBuilder];
 * Filament meshes, materials and GLBs are created on this AR engine via [ArGreenhouseFactory]
 * and [EquipmentSceneBinder] — never reused from the non-AR Predicted Preview.
 */
@Composable
fun GreenhouseArScene(
    layout: SceneLayout,
    equipment: List<com.greenhands.app.equipment.model.EquipmentInstance>,
    selectedId: String?,
    selectedTool: SelectedEquipmentTool,
    labelsVisible: Boolean,
    ui: ArTabletopUiState,
    viewModel: ArTabletopViewModel,
    onSelectEquipment: (String?) -> Unit,
    onDragStart: (String) -> Unit,
    onDragHit: (String, DomainPoint) -> Unit,
    onDragEnd: () -> Unit,
    onAnchors: (List<EquipmentScreenAnchor>) -> Unit,
    onBridge: (ARSceneView?, Node?) -> Unit,
    onEffectRenderer: (ArCirculationRingRenderer?) -> Unit = {},
    onExhaustRenderer: (ArExhaustAirflowRenderer?) -> Unit = {},
    onPadRenderer: (ArFanPadAirflowRenderer?) -> Unit = {},
    onFogRenderer: (ArFoggerMistRenderer?) -> Unit = {},
    onFieldRenderer: (ArEnvironmentalFieldRenderer?) -> Unit = {},
    circulationFrame: CirculationEffectFrame = CirculationEffectFrame.EMPTY,
    exhaustFrame: ExhaustEffectFrame = ExhaustEffectFrame.EMPTY,
    padFrame: PadInletEffectFrame = PadInletEffectFrame.EMPTY,
    fogFrame: FoggerEffectFrame = FoggerEffectFrame.EMPTY,
    fieldFrame: com.greenhands.app.heat.visualization.EnvironmentalFieldFrame =
        com.greenhands.app.heat.visualization.EnvironmentalFieldFrame.EMPTY,
    probeMarkers: List<com.greenhands.app.heat.visualization.CirculationProbeMarker> = emptyList(),
    onProbeAnchors: (List<EquipmentScreenAnchor>) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (LocalInspectionMode.current) {
        Box(modifier.background(Color.DarkGray))
        return
    }

    val engine = rememberArLiveEngine()
    val host = rememberArLiveFilamentHost(engine)
    val materialLoader = host.materialLoader
    val modelLoader = host.modelLoader
    val environmentLoader = host.environmentLoader
    val environment = remember(environmentLoader) {
        ArSceneLighting.createFallbackEnvironment(environmentLoader)
    }
    val mainLight = host.mainLight
    val greenhouseRoot = remember(engine) {
        Node(engine).apply {
            name = "ar_greenhouse_root"
            isVisible = false
        }
    }
    val greenhouseNodes = remember(engine, materialLoader, layout) {
        ArGreenhouseFactory.create(engine, materialLoader, layout).onEach { node ->
            node.parent = greenhouseRoot
        }
    }
    DisposableEffect(greenhouseRoot) {
        onDispose { runCatching { greenhouseRoot.destroy() } }
    }
    DisposableEffect(greenhouseNodes) {
        onDispose { greenhouseNodes.forEach { node -> runCatching { node.destroy() } } }
    }
    val binder = remember(engine, modelLoader, materialLoader) {
        EquipmentSceneBinder(engine, modelLoader, materialLoader)
    }
    DisposableEffect(binder) {
        onDispose { binder.destroy() }
    }
    val placements = remember(equipment, layout) {
        EquipmentSceneMapper.placements(equipment, layout)
    }
    val latestCirculationFrame by rememberUpdatedState(circulationFrame)
    val latestExhaustFrame by rememberUpdatedState(exhaustFrame)
    val latestPadFrame by rememberUpdatedState(padFrame)
    val latestFogFrame by rememberUpdatedState(fogFrame)
    val latestFieldFrame by rememberUpdatedState(fieldFrame)
    val latestSelectedId by rememberUpdatedState(selectedId)
    val latestLayout by rememberUpdatedState(layout)
    val latestPhase by rememberUpdatedState(ui.phase)
    val latestOnEffectRenderer by rememberUpdatedState(onEffectRenderer)
    val latestOnExhaustRenderer by rememberUpdatedState(onExhaustRenderer)
    val latestOnPadRenderer by rememberUpdatedState(onPadRenderer)
    val latestOnFogRenderer by rememberUpdatedState(onFogRenderer)
    val latestOnFieldRenderer by rememberUpdatedState(onFieldRenderer)
    LaunchedEffect(binder) {
        runCatching { binder.preloadAssets() }
    }
    LaunchedEffect(placements, binder) {
        runCatching {
            binder.sync(placements, selectedId, highlightSelectedBounds = false)
            binder.nodes().forEach { node ->
                node.isTouchable = true
                node.parent = greenhouseRoot
            }
        }
    }
    LaunchedEffect(selectedId, binder) {
        runCatching {
            binder.sync(placements, selectedId, highlightSelectedBounds = false)
        }
    }
    val cursor = remember(engine, materialLoader) {
        PlacementCursorNode(engine, materialLoader)
    }
    DisposableEffect(cursor) {
        onDispose { runCatching { cursor.destroy() } }
    }
    val ringRenderer = remember(engine) {
        ArCirculationRingRenderer(engine, materialLoader)
    }
    val exhaustRenderer = remember(engine) {
        ArExhaustAirflowRenderer(engine, materialLoader)
    }
    val padRenderer = remember(engine) {
        ArFanPadAirflowRenderer(engine, materialLoader)
    }
    val fogRenderer = remember(engine) {
        ArFoggerMistRenderer(engine, materialLoader)
    }
    val fieldRenderer = remember(engine) {
        ArEnvironmentalFieldRenderer(engine, materialLoader)
    }
    DisposableEffect(engine) {
        latestOnEffectRenderer(ringRenderer)
        latestOnExhaustRenderer(exhaustRenderer)
        latestOnPadRenderer(padRenderer)
        latestOnFogRenderer(fogRenderer)
        latestOnFieldRenderer(fieldRenderer)
        onDispose {
            ringRenderer.releaseOnComposeDisposal()
            exhaustRenderer.releaseOnComposeDisposal()
            padRenderer.releaseOnComposeDisposal()
            fogRenderer.releaseOnComposeDisposal()
            fieldRenderer.releaseOnComposeDisposal()
            latestOnEffectRenderer(null)
            latestOnExhaustRenderer(null)
            latestOnPadRenderer(null)
            latestOnFogRenderer(null)
            latestOnFieldRenderer(null)
        }
    }

    val childNodes = remember { mutableStateListOf<Node>() }
    var sceneView by remember { mutableStateOf<ARSceneView?>(null) }
    val gestures = remember { ArGestureRelay() }
    gestures.viewModel = viewModel
    gestures.ui = ui
    gestures.sceneView = sceneView
    gestures.greenhouseRoot = greenhouseRoot
    gestures.cursor = cursor
    gestures.childNodes = childNodes
    gestures.layout = layout
    gestures.placements = placements
    gestures.selectedId = selectedId
    gestures.selectedTool = selectedTool
    gestures.labelsVisible = labelsVisible
    gestures.onSelectEquipment = onSelectEquipment
    gestures.onDragStart = onDragStart
    gestures.onDragHit = onDragHit
    gestures.onDragEnd = onDragEnd
    gestures.onAnchors = onAnchors
    gestures.probeMarkers = probeMarkers
    gestures.onProbeAnchors = onProbeAnchors
    gestures.padPanel = padFrame.panel

    DisposableEffect(Unit) {
        onDispose { onBridge(null, null) }
    }

    LaunchedEffect(ui.repositionEpoch) {
        if (ui.repositionEpoch > 0) {
            ringRenderer.stopVisualUpdates()
            ringRenderer.clearEffectsWhileSceneAlive()
            exhaustRenderer.stopVisualUpdates()
            exhaustRenderer.clearEffectsWhileSceneAlive()
            padRenderer.stopVisualUpdates()
            padRenderer.clearEffectsWhileSceneAlive()
            fogRenderer.stopVisualUpdates()
            fogRenderer.clearEffectsWhileSceneAlive()
            fieldRenderer.stopVisualUpdates()
            fieldRenderer.clearEffectsWhileSceneAlive()
            gestures.detachAnchor()
        }
    }
    LaunchedEffect(ui.phase) {
        if (ui.phase != ArTabletopPhase.PLACED) {
            ringRenderer.sync(
                CirculationEffectFrame.EMPTY,
                latestLayout,
                greenhouseRoot,
                ui.phase,
                null
            )
            exhaustRenderer.sync(
                ExhaustEffectFrame.EMPTY,
                latestLayout,
                greenhouseRoot,
                ui.phase,
                null
            )
            padRenderer.sync(
                PadInletEffectFrame.EMPTY,
                latestLayout,
                greenhouseRoot,
                ui.phase
            )
            fogRenderer.sync(
                FoggerEffectFrame.EMPTY,
                latestLayout,
                greenhouseRoot,
                ui.phase,
                null
            )
            fieldRenderer.sync(
                com.greenhands.app.heat.visualization.EnvironmentalFieldFrame.EMPTY,
                latestLayout,
                greenhouseRoot,
                ui.phase
            )
        }
    }
    LaunchedEffect(ui.phase, ui.visualScaleFactor, ui.yawDeg) {
        greenhouseRoot.scale = Scale(ArTabletopScale.worldScale(ui.visualScaleFactor).toFloat())
        greenhouseRoot.rotation = Rotation(y = ui.yawDeg.toFloat())
        greenhouseRoot.isVisible = ui.phase == ArTabletopPhase.PLACED
        cursor.isVisible = ui.showPlacementIndicator
        if (ui.phase == ArTabletopPhase.DETECTING && !childNodes.contains(cursor)) {
            childNodes.clear()
            childNodes.add(cursor)
        }
    }

    ARScene(
        modifier = modifier.fillMaxSize(),
        engine = engine,
        modelLoader = modelLoader,
        materialLoader = materialLoader,
        environmentLoader = environmentLoader,
        environment = environment,
        mainLightNode = mainLight,
        scene = host.scene,
        view = host.view,
        renderer = host.renderer,
        cameraNode = host.cameraNode,
        cameraStream = host.cameraStream,
        collisionSystem = host.collisionSystem,
        planeRenderer = ui.phase != ArTabletopPhase.PLACED,
        childNodes = childNodes,
        sessionConfiguration = { _, config ->
            config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
            config.depthMode = Config.DepthMode.DISABLED
            config.instantPlacementMode = Config.InstantPlacementMode.DISABLED
            config.lightEstimationMode = Config.LightEstimationMode.AMBIENT_INTENSITY
        },
        onSessionResumed = {
            sceneView?.cameraStream?.isDepthOcclusionEnabled = false
            viewModel.setDepthOcclusionEnabled(false)
        },
        onSessionFailed = { error ->
            viewModel.onSessionFailed(ArCoreAvailabilityProbe.sessionFailureOf(error))
        },
        onSessionUpdated = { _, _ ->
            val view = sceneView ?: return@ARScene
            ringRenderer.attachToLiveHost(view)
            exhaustRenderer.attachToLiveHost(view)
            padRenderer.attachToLiveHost(view)
            fogRenderer.attachToLiveHost(view)
            fieldRenderer.attachToLiveHost(view)
            ArSceneLighting.applyVisibleFallback(view)
            if (ui.phase == ArTabletopPhase.DETECTING) {
                val hit = horizontalPlaneHit(view, view.width / 2f, view.height / 2f)
                val valid = hit != null
                viewModel.setHasValidPlacementHit(valid)
                cursor.isVisible = valid
                if (hit != null) {
                    cursor.pose = hit.hitPose
                }
            } else {
                viewModel.setHasValidPlacementHit(false)
                cursor.isVisible = false
            }
            if (latestPhase == ArTabletopPhase.PLACED) {
                gestures.padPanel = latestPadFrame.panel
                ringRenderer.sync(
                    latestCirculationFrame,
                    latestLayout,
                    greenhouseRoot,
                    latestPhase,
                    latestSelectedId
                )
                ringRenderer.tickVisual()
                exhaustRenderer.sync(
                    latestExhaustFrame,
                    latestLayout,
                    greenhouseRoot,
                    latestPhase,
                    latestSelectedId
                )
                exhaustRenderer.tickVisual()
                padRenderer.sync(
                    latestPadFrame,
                    latestLayout,
                    greenhouseRoot,
                    latestPhase
                )
                padRenderer.tickVisual()
                fogRenderer.sync(
                    latestFogFrame,
                    latestLayout,
                    greenhouseRoot,
                    latestPhase,
                    latestSelectedId
                )
                fogRenderer.tickVisual()
                fieldRenderer.sync(
                    latestFieldFrame,
                    latestLayout,
                    greenhouseRoot,
                    latestPhase
                )
                gestures.publishAnchors(view)
            } else {
                ringRenderer.sync(
                    CirculationEffectFrame.EMPTY,
                    latestLayout,
                    greenhouseRoot,
                    latestPhase,
                    null
                )
                exhaustRenderer.sync(
                    ExhaustEffectFrame.EMPTY,
                    latestLayout,
                    greenhouseRoot,
                    latestPhase,
                    null
                )
                padRenderer.sync(
                    PadInletEffectFrame.EMPTY,
                    latestLayout,
                    greenhouseRoot,
                    latestPhase
                )
                fogRenderer.sync(
                    FoggerEffectFrame.EMPTY,
                    latestLayout,
                    greenhouseRoot,
                    latestPhase,
                    null
                )
                fieldRenderer.sync(
                    com.greenhands.app.heat.visualization.EnvironmentalFieldFrame.EMPTY,
                    latestLayout,
                    greenhouseRoot,
                    latestPhase
                )
            }
        },
        onGestureListener = rememberOnGestureListener(
            onSingleTapConfirmed = { event, node -> gestures.onTap(event.x, event.y, node) },
            onMoveBegin = { _, event, node -> gestures.onMoveBegin(event.x, event.y, node) },
            onMove = { _, event, _ -> gestures.onMove(event.x, event.y) },
            onMoveEnd = { _, _, _ -> gestures.onMoveEnd() },
            onScaleBegin = { _, _, _ -> gestures.endEquipmentDrag() },
            onScale = { detector, _, _ -> gestures.onPinch(detector.scaleFactor) },
            onRotateBegin = { _, _, _ -> gestures.endEquipmentDrag() },
            onRotate = { detector, _, _ -> gestures.onRotate(detector.rotation) }
        ),
        onViewCreated = {
            sceneView = this
            gestures.sceneView = this
            ringRenderer.attachToLiveHost(this)
            exhaustRenderer.attachToLiveHost(this)
            padRenderer.attachToLiveHost(this)
            fogRenderer.attachToLiveHost(this)
            fieldRenderer.attachToLiveHost(this)
            onBridge(this, greenhouseRoot)
            latestOnEffectRenderer(ringRenderer)
            latestOnExhaustRenderer(exhaustRenderer)
            latestOnPadRenderer(padRenderer)
            latestOnFogRenderer(fogRenderer)
            latestOnFieldRenderer(fieldRenderer)
            ArSceneLighting.configureEstimator(lightEstimator)
            cameraStream?.isDepthOcclusionEnabled = false
        }
    )
}

private class ArGestureRelay {
    var viewModel: ArTabletopViewModel? = null
    var ui: ArTabletopUiState = ArTabletopUiState()
    var sceneView: ARSceneView? = null
    var greenhouseRoot: Node? = null
    var cursor: PlacementCursorNode? = null
    var childNodes: MutableList<Node>? = null
    var anchorNode: AnchorNode? = null
    var layout: SceneLayout? = null
    var placements: List<com.greenhands.app.equipment.render.EquipmentScenePlacement> = emptyList()
    var selectedId: String? = null
    var selectedTool: SelectedEquipmentTool = SelectedEquipmentTool.MOVE
    var labelsVisible: Boolean = true
    var onSelectEquipment: (String?) -> Unit = {}
    var onDragStart: (String) -> Unit = {}
    var onDragHit: (String, DomainPoint) -> Unit = { _, _ -> }
    var onDragEnd: () -> Unit = {}
    var onAnchors: (List<EquipmentScreenAnchor>) -> Unit = {}
    var probeMarkers: List<com.greenhands.app.heat.visualization.CirculationProbeMarker> = emptyList()
    var onProbeAnchors: (List<EquipmentScreenAnchor>) -> Unit = {}
    var padPanel: com.greenhands.app.heat.visualization.PadPanelSpec? = null
    var dragId: String? = null
    var lockFar: Boolean = true
    var lastLabelSignature: String = ""
    var lastProbeSignature: String = ""

    fun onTap(xPx: Float, yPx: Float, node: Node?) {
        val vm = viewModel ?: return
        val view = sceneView ?: return
        val root = greenhouseRoot ?: return
        val nodes = childNodes ?: return
        if (ArEquipmentGestures.tapPlacesGreenhouse(ui.phase)) {
            val hit = horizontalPlaneHit(view, xPx, yPx) ?: return
            val anchor = runCatching { hit.createAnchor() }.getOrNull() ?: return
            runCatching { anchorNode?.destroy() }
            val next = AnchorNode(view.engine, anchor).apply {
                name = "ar_tabletop_anchor"
                isPositionEditable = false
            }
            root.parent = next
            root.isVisible = true
            root.scale = Scale(ArTabletopScale.worldScale(ui.visualScaleFactor).toFloat())
            root.rotation = Rotation(y = ui.yawDeg.toFloat())
            nodes.clear()
            nodes.add(next)
            cursor?.isVisible = false
            anchorNode = next
            vm.place()
            return
        }
        if (!ArEquipmentGestures.tapSelectsEquipment(ui.phase)) return
        val picked = pickEquipment(xPx, yPx, node)
        onSelectEquipment(picked)
        if (picked != null) {
            vm.setGestureMode(ArEquipmentGestures.afterSelect())
        } else if (ui.gestureMode == ArGestureMode.EQUIPMENT_MOVE) {
            vm.setGestureMode(ArEquipmentGestures.afterClearSelection())
        }
    }

    fun onMoveBegin(xPx: Float, yPx: Float, node: Node?) {
        if (!ArEquipmentGestures.oneFingerMovesEquipment(ui.phase, ui.gestureMode, selectedId)) return
        if (selectedTool != SelectedEquipmentTool.MOVE) return
        val id = pickEquipment(xPx, yPx, node) ?: selectedId ?: return
        val inst = placements.firstOrNull { it.id == id }?.instance ?: return
        val sceneLayout = layout ?: return
        dragId = id
        lockFar = MovementPlanes.farWallFromY(inst.positionM.yM, sceneLayout.dimensions.lengthM)
        onSelectEquipment(id)
        onDragStart(id)
    }

    fun onMove(xPx: Float, yPx: Float) {
        val id = dragId ?: return
        val inst = placements.firstOrNull { it.id == id }?.instance ?: return
        val domain = domainHit(xPx, yPx, inst.type, inst.positionM, lockFar) ?: return
        onDragHit(id, domain)
    }

    fun onMoveEnd() {
        endEquipmentDrag()
    }

    fun endEquipmentDrag() {
        if (dragId == null) return
        dragId = null
        onDragEnd()
    }

    fun onPinch(scaleFactor: Float) {
        val vm = viewModel ?: return
        if (!ArEquipmentGestures.pinchScalesGreenhouse(ui.phase, pointerCount = 2)) return
        vm.pinch(scaleFactor.toDouble())
        greenhouseRoot?.scale = Scale(ArTabletopScale.worldScale(vm.state.value.visualScaleFactor).toFloat())
    }

    fun onRotate(deltaDeg: Float) {
        val vm = viewModel ?: return
        if (!ArEquipmentGestures.twoFingerRotatesGreenhouse(ui.phase, pointerCount = 2)) return
        vm.rotateBy(deltaDeg.toDouble())
        greenhouseRoot?.rotation = Rotation(y = vm.state.value.yawDeg.toFloat())
    }

    fun detachAnchor() {
        val root = greenhouseRoot
        val nodes = childNodes ?: return
        runCatching { anchorNode?.destroy() }
        root?.parent = null
        root?.isVisible = false
        anchorNode = null
        nodes.clear()
        cursor?.let { nodes.add(it) }
        lastLabelSignature = ""
        lastProbeSignature = ""
        onAnchors(emptyList())
        onProbeAnchors(emptyList())
    }

    fun publishAnchors(view: ARSceneView) {
        if (!labelsVisible && selectedId == null && probeMarkers.isEmpty()) {
            if (lastLabelSignature.isNotEmpty() || lastProbeSignature.isNotEmpty()) {
                lastLabelSignature = ""
                lastProbeSignature = ""
                onAnchors(emptyList())
                onProbeAnchors(emptyList())
            }
            return
        }
        val root = greenhouseRoot ?: return
        val vpW = view.width
        val vpH = view.height
        if (vpW <= 0 || vpH <= 0) return
        val camera = view.cameraNode
        val anchors = ArrayList<EquipmentScreenAnchor>(placements.size)
        if (labelsVisible || selectedId != null) {
            placements.forEach { item ->
                val world = root.getWorldPosition(
                    Position(item.sceneX.toFloat(), item.sceneY.toFloat(), item.sceneZ.toFloat())
                )
                val viewPos = camera.worldToView(world)
                if (!viewPos.x.isFinite() || !viewPos.y.isFinite()) return@forEach
                anchors += EquipmentScreenAnchor(
                    id = item.id,
                    xPx = viewPos.x * vpW,
                    yPx = (1f - viewPos.y) * vpH,
                    viewportWidthPx = vpW,
                    viewportHeightPx = vpH
                )
            }
            val pad = padPanel
            val sceneLayoutForPad = layout
            if (labelsVisible && pad != null && sceneLayoutForPad != null) {
                val scene = CirculationRingSceneMath.scenePosition(pad.originM, sceneLayoutForPad)
                val world = root.getWorldPosition(
                    Position(scene.x.toFloat(), scene.y.toFloat(), scene.z.toFloat())
                )
                val viewPos = camera.worldToView(world)
                if (viewPos.x.isFinite() && viewPos.y.isFinite()) {
                    anchors += EquipmentScreenAnchor(
                        id = com.greenhands.app.heat.visualization.PadInletVisual.LABEL_ID,
                        xPx = viewPos.x * vpW,
                        yPx = (1f - viewPos.y) * vpH,
                        viewportWidthPx = vpW,
                        viewportHeightPx = vpH
                    )
                }
            }
        }
        val signature = ViewportLabelLayout.signature(anchors, selectedId)
        if (signature != lastLabelSignature) {
            lastLabelSignature = signature
            onAnchors(anchors)
        }
        val sceneLayout = layout
        val probeAnchors = ArrayList<EquipmentScreenAnchor>(probeMarkers.size)
        if (sceneLayout != null && probeMarkers.isNotEmpty()) {
            probeMarkers.forEach { marker ->
                val scene = CirculationRingSceneMath.scenePosition(marker.domain, sceneLayout)
                val world = root.getWorldPosition(
                    Position(scene.x.toFloat(), scene.y.toFloat(), scene.z.toFloat())
                )
                val viewPos = camera.worldToView(world)
                if (!viewPos.x.isFinite() || !viewPos.y.isFinite()) return@forEach
                probeAnchors += EquipmentScreenAnchor(
                    id = marker.id,
                    xPx = viewPos.x * vpW,
                    yPx = (1f - viewPos.y) * vpH,
                    viewportWidthPx = vpW,
                    viewportHeightPx = vpH
                )
            }
        }
        val probeSignature = ViewportLabelLayout.signature(probeAnchors, selectedId)
        if (probeSignature != lastProbeSignature) {
            lastProbeSignature = probeSignature
            onProbeAnchors(probeAnchors)
        }
    }

    private fun pickEquipment(xPx: Float, yPx: Float, node: Node?): String? {
        val hitId = node?.equipmentInstanceId()
        val ray = sceneRay(xPx, yPx) ?: return hitId
        return ArPlacementAdapter.pickEquipmentId(ray.first, ray.second, placements) ?: hitId
    }

    private fun domainHit(
        xPx: Float,
        yPx: Float,
        type: EquipmentType,
        current: DomainPoint,
        lockFarWall: Boolean
    ): DomainPoint? {
        val sceneLayout = layout ?: return null
        val ray = sceneRay(xPx, yPx) ?: return null
        return ArPlacementAdapter.hitDomain(ray.first, ray.second, type, current, sceneLayout, lockFarWall)
    }

    private fun sceneRay(xPx: Float, yPx: Float): Pair<ViewportMath.Vec3, ViewportMath.Vec3>? {
        val view = sceneView ?: return null
        val root = greenhouseRoot ?: return null
        return greenhouseSceneRay(view, root, xPx, yPx)
    }
}

internal fun greenhouseSceneRay(
    view: ARSceneView,
    root: Node,
    xPx: Float,
    yPx: Float
): Pair<ViewportMath.Vec3, ViewportMath.Vec3>? {
    val width = view.width
    val height = view.height
    if (width <= 0 || height <= 0) return null
    val nx = (xPx / width.toFloat()).coerceIn(0f, 1f)
    val ny = 1f - (yPx / height.toFloat()).coerceIn(0f, 1f)
    val ray = view.cameraNode.viewToRay(Float2(nx, ny))
    val originWorld = Position(ray.origin.x, ray.origin.y, ray.origin.z)
    val farWorld = Position(
        ray.origin.x + ray.direction.x,
        ray.origin.y + ray.direction.y,
        ray.origin.z + ray.direction.z
    )
    val originLocal = root.getLocalPosition(originWorld)
    val farLocal = root.getLocalPosition(farWorld)
    return ViewportMath.Vec3(originLocal.x.toDouble(), originLocal.y.toDouble(), originLocal.z.toDouble()) to
        ViewportMath.Vec3(
            (farLocal.x - originLocal.x).toDouble(),
            (farLocal.y - originLocal.y).toDouble(),
            (farLocal.z - originLocal.z).toDouble()
        )
}

fun ARSceneView.arDomainHit(
    root: Node,
    xPx: Float,
    yPx: Float,
    type: EquipmentType,
    current: DomainPoint,
    layout: SceneLayout,
    lockFarWall: Boolean
): DomainPoint? {
    val ray = greenhouseSceneRay(this, root, xPx, yPx) ?: return null
    return ArPlacementAdapter.hitDomain(ray.first, ray.second, type, current, layout, lockFarWall)
}

fun ARSceneView.arWindowToLocalOrNull(windowX: Float, windowY: Float): Pair<Float, Float>? =
    windowToLocalOrNull(windowX, windowY)

internal fun horizontalPlaneHit(view: ARSceneView, xPx: Float, yPx: Float): HitResult? {
    if (view.width <= 0 || view.height <= 0) return null
    return view.hitTestAR(
        xPx = xPx,
        yPx = yPx,
        planeTypes = setOf(Plane.Type.HORIZONTAL_UPWARD_FACING),
        point = false,
        depthPoint = false,
        instantPlacementPoint = false,
        trackingStates = setOf(TrackingState.TRACKING),
        planePoseInPolygon = true
    )
}

private class PlacementCursorNode(
    engine: Engine,
    materialLoader: MaterialLoader
) : PoseNode(engine) {
    init {
        name = "ar_placement_cursor"
        isPositionEditable = false
        isVisible = false
        CubeNode(
            engine = engine,
            size = Size(0.08f, 0.006f, 0.08f),
            center = Position(0f, 0.003f, 0f),
            materialInstance = materialLoader.createColorInstance(
                Color(0.20f, 0.82f, 0.42f, 0.85f),
                metallic = 0.05f,
                roughness = 0.45f
            ),
            builderApply = {
                culling(false)
                castShadows(false)
                receiveShadows(false)
            }
        ).apply {
            name = "ar_placement_cursor_mark"
            isTouchable = false
            parent = this@PlacementCursorNode
        }
    }
}
