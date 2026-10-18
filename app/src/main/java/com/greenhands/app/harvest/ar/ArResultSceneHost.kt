package com.greenhands.app.harvest.ar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import com.google.android.filament.Texture
import com.google.ar.core.Config
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.TrackingState
import com.greenhands.app.R
import com.greenhands.app.ar.render.ArSceneLighting
import com.greenhands.app.ar.render.rememberArLiveEngine
import com.greenhands.app.ar.render.rememberArLiveFilamentHost
import io.github.sceneview.ar.ARScene
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.math.Position
import io.github.sceneview.math.Size
import io.github.sceneview.node.CubeNode
import io.github.sceneview.node.Node
import io.github.sceneview.rememberOnGestureListener
import io.github.sceneview.texture.ImageTexture

/**
 * SceneView 2.2.1 / ARCore host for tap-to-place Harvest result cards.
 * Reuses the destination Filament AR stack. Does not use Sceneform.
 */
class HarvestArPlacementController {
    @Volatile
    var destroyed: Boolean = false
        private set
    @Volatile
    var placed: Boolean = false
        private set

    fun markPlaced(value: Boolean) {
        if (destroyed) return
        placed = value
    }

    fun destroy() {
        destroyed = true
        placed = false
    }
}

@Composable
fun HarvestArResultScene(
    data: ArResultData,
    resetEpoch: Int,
    onPlacedChanged: (Boolean) -> Unit,
    onSessionFailed: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (LocalInspectionMode.current) {
        return
    }
    val context = LocalContext.current
    val engine = rememberArLiveEngine()
    val host = rememberArLiveFilamentHost(engine)
    val materialLoader = host.materialLoader
    val environmentLoader = host.environmentLoader
    val environment = remember(environmentLoader) {
        ArSceneLighting.createFallbackEnvironment(environmentLoader)
    }
    val childNodes = remember { mutableStateListOf<Node>() }
    var sceneView by remember { mutableStateOf<ARSceneView?>(null) }
    val controller = remember { HarvestArPlacementController() }
    val latestOnPlaced by rememberUpdatedState(onPlacedChanged)
    val latestOnFailed by rememberUpdatedState(onSessionFailed)
    var cardTexture by remember { mutableStateOf<Texture?>(null) }
    var placedAnchor by remember { mutableStateOf<AnchorNode?>(null) }

    DisposableEffect(engine, data) {
        val bitmap = runCatching { HarvestArCardBitmap.render(context, data) }.getOrNull()
        val texture = bitmap?.let { image ->
            runCatching {
                ImageTexture.Builder()
                    .bitmap(image)
                    .build(engine)
            }.getOrNull()
        }
        cardTexture = texture
        onDispose {
            texture?.let { runCatching { engine.destroyTexture(it) } }
            if (cardTexture === texture) cardTexture = null
        }
    }

    DisposableEffect(resetEpoch) {
        if (resetEpoch > 0) {
            runCatching { placedAnchor?.destroy() }
            placedAnchor = null
            childNodes.clear()
            controller.markPlaced(false)
            latestOnPlaced(false)
        }
        onDispose { }
    }

    DisposableEffect(Unit) {
        onDispose {
            controller.destroy()
            runCatching { placedAnchor?.destroy() }
            placedAnchor = null
            childNodes.clear()
            sceneView = null
        }
    }

    ARScene(
        modifier = modifier.fillMaxSize(),
        engine = engine,
        modelLoader = host.modelLoader,
        materialLoader = materialLoader,
        environmentLoader = environmentLoader,
        environment = environment,
        mainLightNode = host.mainLight,
        scene = host.scene,
        view = host.view,
        renderer = host.renderer,
        cameraNode = host.cameraNode,
        cameraStream = host.cameraStream,
        collisionSystem = host.collisionSystem,
        planeRenderer = !controller.placed,
        childNodes = childNodes,
        sessionConfiguration = { _, config ->
            config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
            config.depthMode = Config.DepthMode.DISABLED
            config.instantPlacementMode = Config.InstantPlacementMode.DISABLED
            config.lightEstimationMode = Config.LightEstimationMode.AMBIENT_INTENSITY
        },
        onSessionFailed = {
            latestOnFailed()
        },
        onSessionUpdated = { _, _ ->
            val view = sceneView ?: return@ARScene
            ArSceneLighting.applyVisibleFallback(view)
        },
        onGestureListener = rememberOnGestureListener(
            onSingleTapConfirmed = onTap@{ event, _ ->
                val view = sceneView ?: return@onTap
                if (controller.destroyed) return@onTap
                val hit = harvestPlaneHit(view, event.x, event.y) ?: return@onTap
                val texture = cardTexture ?: return@onTap
                val anchor = runCatching { hit.createAnchor() }.getOrNull() ?: return@onTap
                runCatching { placedAnchor?.destroy() }
                val material = materialLoader.createTextureInstance(
                    texture,
                    isOpaque = false,
                    metallic = 0.05f,
                    roughness = 0.45f
                )
                val next = AnchorNode(view.engine, anchor).apply {
                    name = "harvest_ar_result_anchor"
                    isPositionEditable = false
                }
                CubeNode(
                    engine = view.engine,
                    size = Size(0.22f, 0.14f, 0.008f),
                    center = Position(0f, 0.09f, 0f),
                    materialInstance = material,
                    builderApply = {
                        culling(false)
                        castShadows(false)
                        receiveShadows(true)
                    }
                ).apply {
                    name = "harvest_ar_result_card"
                    isTouchable = false
                    parent = next
                }
                childNodes.clear()
                childNodes.add(next)
                placedAnchor = next
                controller.markPlaced(true)
                latestOnPlaced(true)
            }
        ),
        onViewCreated = {
            sceneView = this
            cameraStream?.isDepthOcclusionEnabled = false
            ArSceneLighting.configureEstimator(lightEstimator)
        }
    )
}

internal fun harvestPlaneHit(view: ARSceneView, xPx: Float, yPx: Float): HitResult? {
    if (view.width <= 0 || view.height <= 0) return null
    return view.hitTestAR(
        xPx = xPx,
        yPx = yPx,
        planeTypes = setOf(
            Plane.Type.HORIZONTAL_UPWARD_FACING,
            Plane.Type.VERTICAL
        ),
        point = false,
        depthPoint = false,
        instantPlacementPoint = false,
        trackingStates = setOf(TrackingState.TRACKING),
        planePoseInPolygon = true
    )
}

object HarvestArCardBitmap {
    fun render(context: Context, data: ArResultData): Bitmap {
        val view = LayoutInflater.from(context).inflate(R.layout.ar_result_card, null)
        view.findViewById<TextView>(R.id.ar_card_title).text = data.title
        view.findViewById<TextView>(R.id.ar_card_status).text = data.status
        val detail = view.findViewById<TextView>(R.id.ar_card_detail)
        if (data.detail.isNullOrBlank()) {
            detail.visibility = View.GONE
        } else {
            detail.visibility = View.VISIBLE
            detail.text = data.detail
        }
        val confidence = view.findViewById<TextView>(R.id.ar_card_confidence)
        val percent = data.confidencePercent
        if (percent == null) {
            confidence.visibility = View.GONE
        } else {
            confidence.visibility = View.VISIBLE
            confidence.text = context.getString(R.string.harvest_ar_confidence, percent)
        }
        val widthSpec = View.MeasureSpec.makeMeasureSpec(512, View.MeasureSpec.AT_MOST)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(512, View.MeasureSpec.AT_MOST)
        view.measure(widthSpec, heightSpec)
        val width = view.measuredWidth.coerceAtLeast(1)
        val height = view.measuredHeight.coerceAtLeast(1)
        view.layout(0, 0, width, height)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        return bitmap
    }
}
