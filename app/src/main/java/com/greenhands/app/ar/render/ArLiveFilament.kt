package com.greenhands.app.ar.render

import android.opengl.EGLContext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.google.android.filament.Engine
import com.google.android.filament.Renderer
import com.google.android.filament.Scene
import com.google.android.filament.View
import io.github.sceneview.SceneView
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.camera.ARCameraStream
import io.github.sceneview.ar.node.ARCameraNode
import io.github.sceneview.ar.rememberARCameraNode
import io.github.sceneview.ar.rememberARCameraStream
import io.github.sceneview.collision.CollisionSystem
import io.github.sceneview.loaders.EnvironmentLoader
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.loaders.ModelLoader
import com.greenhands.app.ar.domain.ArTabletopLighting
import io.github.sceneview.node.LightNode
import io.github.sceneview.rememberCollisionSystem
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberRenderer
import io.github.sceneview.rememberScene
import io.github.sceneview.rememberView

/**
 * SceneView 2.2.1 [rememberEngine]/[rememberScene] key on a default `creator` lambda.
 * That lambda is a new instance every recomposition, so Engine/Scene/View are recreated
 * when Start Visualization, HUD, or selection updates the AR composable. Stable
 * remembered creators keep one live host for the AR session.
 */
data class ArLiveFilamentHost(
    val engine: Engine,
    val materialLoader: MaterialLoader,
    val modelLoader: ModelLoader,
    val environmentLoader: EnvironmentLoader,
    val scene: Scene,
    val view: View,
    val renderer: Renderer,
    val cameraNode: ARCameraNode,
    val cameraStream: ARCameraStream,
    val collisionSystem: CollisionSystem,
    val mainLight: LightNode
) {
    val engineIdentity: Int = System.identityHashCode(engine)
}

@Composable
fun rememberArLiveEngine(): Engine {
    val eglContextCreator = remember { { SceneView.createEglContext() } }
    val engineCreator = remember<(EGLContext) -> Engine> { { egl -> SceneView.createEngine(egl) } }
    return rememberEngine(eglContextCreator, engineCreator)
}

@Composable
fun rememberArLiveFilamentHost(engine: Engine): ArLiveFilamentHost {
    val context = LocalContext.current
    val materialCreator = remember(engine, context) {
        { SceneView.createMaterialLoader(engine, context) }
    }
    val materialLoader = rememberMaterialLoader(engine, context, materialCreator)
    val modelCreator = remember(engine, context) {
        { SceneView.createModelLoader(engine, context) }
    }
    val modelLoader = rememberModelLoader(engine, context, modelCreator)
    val environmentCreator = remember(engine, context) {
        { SceneView.createEnvironmentLoader(engine, context) }
    }
    val environmentLoader = rememberEnvironmentLoader(engine, context, environmentCreator)
    val sceneCreator = remember(engine) { { SceneView.createScene(engine) } }
    val scene = rememberScene(engine, sceneCreator)
    val viewCreator = remember(engine) { { SceneView.createView(engine) } }
    val view = rememberView(engine, viewCreator)
    val rendererCreator = remember(engine) { { SceneView.createRenderer(engine) } }
    val renderer = rememberRenderer(engine, rendererCreator)
    val cameraNodeCreator = remember(engine) { { ARSceneView.createARCameraNode(engine) } }
    val cameraNode = rememberARCameraNode(engine, cameraNodeCreator)
    val cameraStreamCreator = remember(materialLoader) {
        { ARSceneView.createARCameraStream(materialLoader) }
    }
    val cameraStream = rememberARCameraStream(materialLoader, cameraStreamCreator)
    val collisionCreator = remember(view) { { SceneView.createCollisionSystem(view) } }
    val collisionSystem = rememberCollisionSystem(view, collisionCreator)
    val mainLight = remember(engine) {
        SceneView.createMainLightNode(engine).apply {
            intensity = ArTabletopLighting.FALLBACK_INTENSITY_LUX
        }
    }
    return ArLiveFilamentHost(
        engine = engine,
        materialLoader = materialLoader,
        modelLoader = modelLoader,
        environmentLoader = environmentLoader,
        scene = scene,
        view = view,
        renderer = renderer,
        cameraNode = cameraNode,
        cameraStream = cameraStream,
        collisionSystem = collisionSystem,
        mainLight = mainLight
    )
}

object ArLiveRendererOwnership {
    val REMEMBER_KEYS: Set<String> = setOf("engine")
    val FORBIDDEN_REMEMBER_KEYS: Set<String> = setOf(
        "selectedEquipmentId",
        "equipmentEpoch",
        "labelsVisible",
        "gestureMode",
        "sheetState",
        "runtimeSnapshot",
        "probeSnapshot",
        "circulationFrame",
        "exhaustFrame",
        "padFrame",
        "fogFrame",
        "fieldFrame",
        "environmentalLayer",
        "fieldHeight",
        "visualizationFrame"
    )

    fun chromeDisposesRenderer(): Boolean = false
}
