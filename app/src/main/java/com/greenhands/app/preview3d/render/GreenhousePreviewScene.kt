package com.greenhands.app.preview3d.render

import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import com.greenhands.app.equipment.domain.DragPlaneSpec
import com.greenhands.app.equipment.domain.EquipmentScreenAnchor
import com.greenhands.app.equipment.domain.MovementPlanes
import com.greenhands.app.equipment.domain.PlacementGesturePolicy
import com.greenhands.app.equipment.domain.PlacementPicking
import com.greenhands.app.equipment.domain.PreviewPlacementAdapter
import com.greenhands.app.equipment.domain.ViewportLabelLayout
import com.greenhands.app.equipment.domain.ViewportMath
import com.greenhands.app.equipment.model.DomainPoint
import com.greenhands.app.equipment.model.PreviewInteractionMode
import com.greenhands.app.equipment.model.SelectedEquipmentTool
import com.greenhands.app.equipment.render.EquipmentScenePlacement
import com.greenhands.app.preview3d.domain.SceneLayout
import dev.romainguy.kotlin.math.Float2
import io.github.sceneview.Scene
import io.github.sceneview.SceneView
import io.github.sceneview.gesture.transform
import io.github.sceneview.math.Position
import io.github.sceneview.math.Size
import io.github.sceneview.node.CameraNode
import io.github.sceneview.node.CubeNode
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberMainLightNode
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.safeDestroy
import io.github.sceneview.utils.destroy
import java.util.concurrent.atomic.AtomicBoolean

private data class EngineHold(
    val engine: com.google.android.filament.Engine,
    val eglContext: android.opengl.EGLContext
)

private class PlacementTouchSession {
    var activeId: String? = null
    var lockFar: Boolean = true
    var lastLabelSignature: String = ""
}

/**
 * Non-AR Filament preview. Engine/loaders/nodes are remembered across recomposition
 * and released through SceneView + explicit engine teardown. Frame callbacks do not
 * write Compose state after the first successful frame, except equipment name
 * labels which are throttled to whole pixels.
 *
 * Camera framing is unchanged from Phase 3B.1 and does not include equipment bounds.
 */
@Composable
fun GreenhousePreviewScene(
    layout: SceneLayout,
    viewportAspect: Double,
    resetEpoch: Int,
    onReady: () -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier,
    interactionMode: PreviewInteractionMode = PreviewInteractionMode.VIEW,
    orbitAllowed: Boolean = false,
    equipment: List<EquipmentScenePlacement> = emptyList(),
    selectedEquipmentId: String? = null,
    onSelectEquipment: (String?) -> Unit = {},
    onDragStart: (String) -> Unit = {},
    onDragHit: (String, DomainPoint) -> Unit = { _, _ -> },
    onDragEnd: () -> Unit = {},
    onAnchors: (List<EquipmentScreenAnchor>) -> Unit = {},
    onSceneView: (SceneView?) -> Unit = {},
    selectedTool: SelectedEquipmentTool = SelectedEquipmentTool.MOVE
) {
    if (LocalInspectionMode.current) {
        Box(modifier.background(Color(0xFF1A2420)))
        return
    }

    val holdResult = remember {
        runCatching {
            val egl = SceneView.createEglContext()
            EngineHold(SceneView.createEngine(egl), egl)
        }
    }
    DisposableEffect(holdResult) {
        onDispose {
            onSceneView(null)
            holdResult.getOrNull()?.let { hold ->
                runCatching { hold.engine.safeDestroy() }
                runCatching { hold.eglContext.destroy() }
            }
        }
    }

    val hold = holdResult.getOrNull()
    if (hold == null) {
        LaunchedEffect(holdResult) {
            val message = holdResult.exceptionOrNull()?.message
                ?: "The 3D renderer could not start on this device."
            onError(message)
        }
        return
    }

    val engine = hold.engine
    val materialLoader = rememberMaterialLoader(engine)
    val modelLoader = rememberModelLoader(engine)
    val pose = remember(layout, viewportAspect) {
        GreenhouseSceneFactory.cameraPose(layout, viewportAspect)
    }
    val home = remember(pose) { GreenhouseSceneFactory.cameraHome(pose) }
    val target = remember(pose) { GreenhouseSceneFactory.cameraTarget(pose) }
    val cameraNode = rememberCameraNode(engine) {
        far = pose.farPlane.toFloat()
        position = home
        lookAt(target)
    }
    val mainLight = rememberMainLightNode(engine)
    val greenhouseNodes = remember(engine, materialLoader, layout) {
        GreenhouseSceneFactory.create(engine, materialLoader, layout).onEach { it.isTouchable = false }
    }
    DisposableEffect(greenhouseNodes) {
        onDispose { greenhouseNodes.forEach { node -> runCatching { node.destroy() } } }
    }
    val floor = remember(engine, materialLoader, layout) {
        CubeNode(
            engine = engine,
            size = Size(layout.extentX.toFloat(), 0.02f, layout.extentZ.toFloat()),
            center = Position(0f, 0.01f, 0f),
            materialInstance = materialLoader.createColorInstance(
                Color(0f, 0f, 0f, 0f),
                metallic = 0.0f,
                roughness = 1.0f
            )
        ).apply {
            name = "placement_floor"
            isTouchable = true
        }
    }
    DisposableEffect(floor) {
        onDispose { runCatching { floor.destroy() } }
    }
    val binder = remember(engine, modelLoader, materialLoader) {
        EquipmentSceneBinder(engine, modelLoader, materialLoader)
    }
    DisposableEffect(binder) {
        onDispose { binder.destroy() }
    }
    var equipmentEpoch by remember { mutableIntStateOf(0) }
    LaunchedEffect(equipment, selectedEquipmentId, binder) {
        runCatching {
            binder.sync(equipment, selectedEquipmentId)
            equipmentEpoch += 1
        }
    }
    val childNodes = remember(greenhouseNodes, floor, equipmentEpoch) {
        greenhouseNodes + floor + binder.nodes()
    }
    val firstFrame = remember { AtomicBoolean(false) }
    val session = remember { PlacementTouchSession() }
    val manipulator = remember(resetEpoch, home, target) {
        SceneView.createDefaultCameraManipulator(
            orbitHomePosition = home,
            targetPosition = target
        )
    }
    LaunchedEffect(resetEpoch, cameraNode, manipulator, pose, home, target) {
        firstFrame.set(false)
        cameraNode.far = pose.farPlane.toFloat()
        cameraNode.position = home
        cameraNode.lookAt(target)
        cameraNode.transform = manipulator.transform
    }

    key(resetEpoch) {
    Scene(
        modifier = modifier.fillMaxSize(),
        engine = engine,
        modelLoader = modelLoader,
        materialLoader = materialLoader,
        cameraNode = cameraNode,
        mainLightNode = mainLight,
        cameraManipulator = manipulator,
        childNodes = childNodes,
        isOpaque = true,
        onViewCreated = { onSceneView(this) },
        onTouchEvent = { event, hit ->
            if (interactionMode != PreviewInteractionMode.PLACEMENT) return@Scene false
            val dragging = session.activeId != null
            if (event.pointerCount > 1) return@Scene false
            val consumeCamera = PlacementGesturePolicy.blocksCameraOrbit(
                mode = interactionMode,
                orbitAllowed = orbitAllowed,
                pointerCount = event.pointerCount,
                dragging = dragging
            )
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val ray = cameraNode.rayFromViewPx(event.x, event.y)
                    val picked = ray?.let { PreviewPlacementAdapter.pickEquipment(it.first, it.second, equipment) }
                    val hitId = hit?.node?.equipmentInstanceId()
                    val id = picked ?: hitId
                    if (id != null) {
                        onSelectEquipment(id)
                        val canDrag = !orbitAllowed && selectedTool == SelectedEquipmentTool.MOVE
                        if (canDrag) {
                            val inst = equipment.firstOrNull { it.id == id }?.instance
                            session.activeId = id
                            session.lockFar = inst?.let {
                                MovementPlanes.farWallFromY(it.positionM.yM, layout.dimensions.lengthM)
                            } ?: true
                            onDragStart(id)
                        } else {
                            session.activeId = null
                        }
                        true
                    } else {
                        session.activeId = null
                        onSelectEquipment(null)
                        consumeCamera
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    val id = session.activeId
                    if (id != null) {
                        val inst = equipment.firstOrNull { it.id == id }?.instance
                        if (inst != null) {
                            val plane = MovementPlanes.scenePlane(inst.type, inst.positionM, layout, session.lockFar)
                            val domain = cameraNode.domainOnPlane(event.x, event.y, plane, layout)
                            if (domain != null) onDragHit(id, domain)
                        }
                        true
                    } else {
                        consumeCamera
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val wasDragging = session.activeId != null
                    if (wasDragging) onDragEnd()
                    session.activeId = null
                    if (wasDragging) true else consumeCamera
                }
                else -> consumeCamera
            }
        },
        onViewUpdated = {
            cameraManipulator?.setViewport(width, height)
            cameraNode.far = pose.farPlane.toFloat()
        },
        onFrame = {
            if (firstFrame.compareAndSet(false, true)) {
                cameraNode.far = pose.farPlane.toFloat()
                cameraNode.transform = manipulator.transform
                onReady()
            }
            val anchors = ArrayList<EquipmentScreenAnchor>(equipment.size)
            val vp = cameraNode.viewport
            if (vp != null && vp.width > 0 && vp.height > 0) {
                equipment.forEach { item ->
                    val screen = cameraNode.worldToScreenPx(item.sceneX, item.sceneY, item.sceneZ) ?: return@forEach
                    anchors += EquipmentScreenAnchor(
                        id = item.id,
                        xPx = screen.first,
                        yPx = screen.second,
                        viewportWidthPx = vp.width,
                        viewportHeightPx = vp.height
                    )
                }
            }
            val signature = ViewportLabelLayout.signature(anchors, selectedEquipmentId)
            if (signature != session.lastLabelSignature) {
                session.lastLabelSignature = signature
                onAnchors(anchors)
            }
        }
    )
    }
}

internal fun CameraNode.rayFromViewPx(xPx: Float, yPx: Float): Pair<ViewportMath.Vec3, ViewportMath.Vec3>? {
    val vp = viewport ?: return null
    if (vp.width <= 0 || vp.height <= 0) return null
    val nx = xPx / vp.width.toFloat()
    val ny = 1f - yPx / vp.height.toFloat()
    val ray = viewToRay(Float2(nx, ny))
    return ViewportMath.Vec3(ray.origin.x.toDouble(), ray.origin.y.toDouble(), ray.origin.z.toDouble()) to
        ViewportMath.Vec3(ray.direction.x.toDouble(), ray.direction.y.toDouble(), ray.direction.z.toDouble())
}

internal fun CameraNode.domainOnPlane(
    xPx: Float,
    yPx: Float,
    plane: DragPlaneSpec,
    layout: SceneLayout
): DomainPoint? {
    val vp = viewport ?: return null
    if (vp.width <= 0 || vp.height <= 0) return null
    val nx = xPx / vp.width.toFloat()
    val ny = 1f - yPx / vp.height.toFloat()
    val ray = viewToRay(Float2(nx, ny))
    return PlacementPicking.hitDomain(
        origin = ViewportMath.Vec3(ray.origin.x.toDouble(), ray.origin.y.toDouble(), ray.origin.z.toDouble()),
        direction = ViewportMath.Vec3(ray.direction.x.toDouble(), ray.direction.y.toDouble(), ray.direction.z.toDouble()),
        plane = plane,
        layout = layout
    )
}

internal fun CameraNode.worldToScreenPx(sceneX: Double, sceneY: Double, sceneZ: Double): Pair<Float, Float>? {
    val vp = viewport ?: return null
    if (vp.width <= 0 || vp.height <= 0) return null
    val view = worldToView(Position(sceneX.toFloat(), sceneY.toFloat(), sceneZ.toFloat()))
    if (!view.x.isFinite() || !view.y.isFinite()) return null
    val sx = view.x * vp.width
    val sy = (1f - view.y) * vp.height
    return sx to sy
}

fun SceneView.windowToLocalOrNull(windowX: Float, windowY: Float): Pair<Float, Float>? {
    val loc = IntArray(2)
    getLocationOnScreen(loc)
    val x = windowX - loc[0]
    val y = windowY - loc[1]
    if (x < 0f || y < 0f || x > width || y > height) return null
    return x to y
}

fun SceneView.pickDomain(
    localX: Float,
    localY: Float,
    plane: DragPlaneSpec,
    layout: SceneLayout
): DomainPoint? = cameraNode.domainOnPlane(localX, localY, plane, layout)
