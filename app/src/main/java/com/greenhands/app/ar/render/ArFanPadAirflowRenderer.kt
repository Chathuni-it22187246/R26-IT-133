package com.greenhands.app.ar.render

import android.os.SystemClock
import androidx.compose.ui.graphics.Color
import com.google.android.filament.Engine
import com.google.android.filament.MaterialInstance
import com.greenhands.app.ar.domain.ArTabletopPhase
import com.greenhands.app.heat.visualization.ExhaustAirflowAnimator
import com.greenhands.app.heat.visualization.PadInletAnimator
import com.greenhands.app.heat.visualization.PadInletAppearance
import com.greenhands.app.heat.visualization.PadInletEffectFrame
import com.greenhands.app.heat.visualization.PadInletVisual
import com.greenhands.app.heat.visualization.VisualizationRgb
import com.greenhands.app.preview3d.domain.SceneLayout
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.material.setColor
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.node.GeometryNode
import io.github.sceneview.node.Node
import java.util.IdentityHashMap

/**
 * Programmatic pad panel and inlet streamlines on the live AR engine.
 * Ownership matches circulation rings and exhaust arrows: one renderer per Engine.
 */
class ArFanPadAirflowRenderer(
    private val engine: Engine,
    private val materialLoader: MaterialLoader
) {
    val engineIdentity: Int = System.identityHashCode(engine)
    val host = ArFanPadAirflowHost(engineIdentity)

    private val materialPool = ArrayDeque<MaterialInstance>()
    private val destroyedNodes = IdentityHashMap<Node, Boolean>()
    private val destroyedMaterials = IdentityHashMap<MaterialInstance, Boolean>()
    private var visualTimeS = 0.0
    private var lastTickNs = 0L
    private var disposed = false
    private var hostAlive = false
    private var syncEnabled = false
    private var visualUpdatesEnabled = false
    private var sceneView: ARSceneView? = null
    private var root: Node? = null
    private var panel: BoundMesh? = null
    private var panelWidthM = 0.0
    private var panelHeightM = 0.0
    private var arrows: List<BoundMesh> = emptyList()
    private var frame: PadInletEffectFrame = PadInletEffectFrame.EMPTY
    private var layout: SceneLayout? = null
    private var colorBlend = 1f
    private var previousColorSnapshot = frame.colorSnapshot

    fun attachToLiveHost(liveView: ARSceneView): Boolean {
        if (disposed) return false
        if (System.identityHashCode(liveView.engine) != engineIdentity) return false
        sceneView = liveView
        hostAlive = true
        syncEnabled = true
        visualUpdatesEnabled = true
        return host.lifecycle.attach(engineIdentity)
    }

    fun sync(
        next: PadInletEffectFrame,
        sceneLayout: SceneLayout,
        greenhouseRoot: Node,
        phase: ArTabletopPhase
    ) {
        if (disposed || !syncEnabled) return
        host.bind(ArFanPadAirflowHost.request(next, phase, greenhouseRoot.name ?: ROOT_NAME))
        if (phase != ArTabletopPhase.PLACED || next.isEmpty) {
            clearEffectsWhileSceneAlive()
            return
        }
        if (!canTouchScene()) return
        layout = sceneLayout
        if (next.colorSnapshot !== frame.colorSnapshot && next.colorSnapshot != null) {
            previousColorSnapshot = frame.colorSnapshot
            colorBlend = 0f
        }
        frame = next
        ensureNodes(next, greenhouseRoot)
        applyPose(next, sceneLayout, greenhouseRoot)
        host.lifecycle.bindGroups(if (next.isEmpty) 0 else 1)
    }

    fun tickVisual(nowNs: Long = SystemClock.elapsedRealtimeNanos()) {
        if (disposed || !visualUpdatesEnabled || frame.isEmpty) return
        if (!canTouchScene()) return
        if (lastTickNs == 0L) {
            lastTickNs = nowNs
            return
        }
        val dtS = (nowNs - lastTickNs) / 1_000_000_000.0
        if (!dtS.isFinite() || dtS <= 0.0 || dtS >= 0.25) {
            lastTickNs = nowNs
            return
        }
        val minDt = 1.0 / PadInletVisual.MAX_VISUAL_HZ
        if (dtS + 1e-12 < minDt) return
        lastTickNs = nowNs
        visualTimeS += dtS
        animate(dtS)
    }

    fun clearEffectsWhileSceneAlive() {
        if (disposed) return
        host.lifecycle.clearEffectsWhileSceneAlive()
        host.clear()
        if (canTouchScene()) {
            recycleAll()
        } else {
            abandonOwnedNodes()
        }
        visualTimeS = 0.0
        lastTickNs = 0L
        frame = PadInletEffectFrame.EMPTY
        if (!disposed && hostAlive && sceneViewStillAlive()) {
            syncEnabled = true
            visualUpdatesEnabled = true
        }
    }

    fun clear() = clearEffectsWhileSceneAlive()

    fun disposeBeforeHostDestruction() {
        if (disposed) {
            host.lifecycle.disposeBeforeHostDestruction()
            return
        }
        stopVisualUpdates()
        syncEnabled = false
        visualUpdatesEnabled = false
        if (!canTouchScene()) {
            abandonAfterHostUnavailable()
            return
        }
        host.clear()
        recycleAll()
        destroyPooledMaterials()
        host.lifecycle.disposeBeforeHostDestruction()
        sceneView = null
        hostAlive = false
        disposed = true
        host.markDisposed()
    }

    fun abandonAfterHostUnavailable() {
        if (disposed) {
            host.lifecycle.abandonAfterHostUnavailable()
            return
        }
        stopVisualUpdates()
        syncEnabled = false
        visualUpdatesEnabled = false
        hostAlive = false
        host.lifecycle.markHostUnavailable()
        abandonOwnedNodes()
        materialPool.clear()
        host.lifecycle.abandonAfterHostUnavailable()
        host.clear()
        sceneView = null
        disposed = true
        host.markDisposed()
    }

    fun releaseOnComposeDisposal() {
        if (canTouchScene()) {
            disposeBeforeHostDestruction()
        } else {
            abandonAfterHostUnavailable()
        }
    }

    fun destroy() = releaseOnComposeDisposal()

    fun stopVisualUpdates() {
        visualUpdatesEnabled = false
        lastTickNs = 0L
        host.lifecycle.stopVisualUpdates()
    }

    fun noteRecomposition() {
        host.noteRecomposition()
    }

    private fun ensureNodes(next: PadInletEffectFrame, greenhouseRoot: Node) {
        val wantedArrows = next.stream?.arrowCount ?: 0
        if (root == null) {
            val created = Node(engine).apply {
                name = "pad-inlet-root"
                isTouchable = false
            }
            if (canTouchScene() && created.parent !== greenhouseRoot) {
                created.parent = greenhouseRoot
            }
            root = created
            host.lifecycle.registerOwnedResource("pad-root", engineIdentity)
        }
        val panelSpec = next.panel
        val sizeChanged = panelSpec != null &&
            panel != null &&
            (kotlin.math.abs(panelWidthM - panelSpec.widthM) > 1e-6 ||
                kotlin.math.abs(panelHeightM - panelSpec.heightM) > 1e-6)
        if (panelSpec == null && panel != null) {
            destroyMesh(panel!!)
            panel = null
            panelWidthM = 0.0
            panelHeightM = 0.0
        } else if (panelSpec != null && (panel == null || sizeChanged)) {
            panel?.let { destroyMesh(it) }
            panel = createPanel("pad-panel", panelSpec.widthM, panelSpec.heightM)
            panelWidthM = panelSpec.widthM
            panelHeightM = panelSpec.heightM
            if (canTouchScene() && panel!!.holder.parent !== root) {
                panel!!.holder.parent = root
            }
        }
        if (arrows.size != wantedArrows) {
            arrows.forEach { destroyMesh(it) }
            arrows = List(wantedArrows) { index ->
                createArrow("pad-inlet-arrow:$index").also { arrow ->
                    if (canTouchScene() && arrow.holder.parent !== root) {
                        arrow.holder.parent = root
                    }
                }
            }
        }
    }

    private fun createPanel(name: String, widthM: Double, heightM: Double): BoundMesh {
        val holder = Node(engine).apply {
            this.name = name
            isTouchable = false
        }
        val material = obtainMaterial()
        val mesh = GeometryNode(
            engine = engine,
            geometry = PadPanelMesh.create(engine, widthM, heightM),
            materialInstance = material,
            builderApply = {
                culling(PadInletAppearance.CULLING)
                castShadows(false)
                receiveShadows(false)
            }
        ).apply {
            this.name = "$name-mesh"
            isTouchable = false
        }
        if (canTouchScene() && mesh.parent !== holder) {
            mesh.parent = holder
        }
        host.lifecycle.registerOwnedResource(name, engineIdentity)
        return BoundMesh(holder, mesh, material)
    }

    private fun createArrow(name: String): BoundMesh {
        val holder = Node(engine).apply {
            this.name = name
            isTouchable = false
        }
        val material = obtainMaterial()
        val mesh = GeometryNode(
            engine = engine,
            geometry = PadInletArrowMesh.create(engine),
            materialInstance = material,
            builderApply = {
                culling(PadInletAppearance.CULLING)
                castShadows(false)
                receiveShadows(false)
            }
        ).apply {
            this.name = "$name-mesh"
            isTouchable = false
        }
        if (canTouchScene() && mesh.parent !== holder) {
            mesh.parent = holder
        }
        host.lifecycle.registerOwnedResource(name, engineIdentity)
        return BoundMesh(holder, mesh, material)
    }

    private fun applyPose(
        next: PadInletEffectFrame,
        sceneLayout: SceneLayout,
        greenhouseRoot: Node
    ) {
        if (!canTouchScene()) return
        val origin = next.panel?.originM ?: next.stream?.originM ?: return
        val yaw = next.panel?.yawDeg ?: next.stream?.yawDeg ?: 0.0
        val scene = CirculationRingSceneMath.scenePosition(origin, sceneLayout)
        val liveRoot = root ?: return
        if (liveRoot.parent !== greenhouseRoot) {
            liveRoot.parent = greenhouseRoot
        }
        liveRoot.position = Position(scene.x.toFloat(), scene.y.toFloat(), scene.z.toFloat())
        liveRoot.rotation = Rotation(y = CirculationRingSceneMath.sceneYawDeg(yaw, sceneLayout).toFloat())
        liveRoot.scale = Scale(sceneLayout.uniformScale.toFloat())
        liveRoot.isVisible = true
        liveRoot.isTouchable = false
        val panelSpec = next.panel
        val boundPanel = panel
        if (panelSpec != null && boundPanel != null) {
            boundPanel.holder.position = Position(0f, 0f, 0f)
            boundPanel.holder.scale = Scale(1f)
            boundPanel.holder.isVisible = true
            val color = if (panelSpec.wetPadActive) {
                PadInletAppearance.WET_PANEL
            } else {
                PadInletAppearance.DRY_PANEL
            }
            applyColor(boundPanel.material, color, 1f)
        } else if (boundPanel != null) {
            boundPanel.holder.isVisible = false
        }
        if (next.stream == null) {
            arrows.forEach { it.holder.isVisible = false }
        }
        animate(0.0)
    }

    private fun animate(dtS: Double) {
        if (dtS > 0.0 && dtS < 0.25 && PadInletVisual.DISPLAY_COLOR_BLEND_S > 0.0) {
            colorBlend = (colorBlend + (dtS / PadInletVisual.DISPLAY_COLOR_BLEND_S).toFloat())
                .coerceAtMost(1f)
        }
        val spec = frame.stream ?: return
        arrows.forEachIndexed { index, arrow ->
            val distanceM = PadInletAnimator.distanceM(
                spec.pathM,
                visualTimeS,
                index,
                arrows.size
            )
            val progress = ExhaustAirflowAnimator.progress(
                visualTimeS,
                index,
                arrows.size,
                PadInletVisual.CYCLE_S,
                0.12
            )
            val fade = ExhaustAirflowAnimator.fadeAtProgress(progress, outdoor = false)
            arrow.holder.position = Position(0f, 0f, distanceM.toFloat())
            arrow.holder.rotation = Rotation()
            arrow.holder.scale = Scale(1f)
            arrow.holder.isVisible = fade > 0.05f
            val color = if (spec.wetPadActive && spec.claimsEvaporativeCooling) {
                PadInletAppearance.WET_INLET_ARROW
            } else {
                val from = PadInletAnimator.colorAt(
                    spec,
                    distanceM,
                    previousColorSnapshot,
                    spec.wetPadActive,
                    frame.fanPad.padOutletTemperatureC
                )
                val to = PadInletAnimator.colorAt(
                    spec,
                    distanceM,
                    frame.colorSnapshot ?: frame.snapshot,
                    spec.wetPadActive,
                    frame.fanPad.padOutletTemperatureC
                )
                VisualizationRgb.lerp(from, to, colorBlend)
            }
            applyColor(arrow.material, color, fade)
        }
    }

    private fun applyColor(instance: MaterialInstance, color: VisualizationRgb, fade: Float) {
        val write = ArCirculationColorMaterial.frameColorWrite(color, fade)
        instance.setColor(Color(write.red, write.green, write.blue, write.alpha))
    }

    private fun obtainMaterial(): MaterialInstance {
        return materialPool.removeFirstOrNull() ?: materialLoader.createColorInstance(
            Color(
                VisualizationRgb.NEUTRAL_AIRFLOW.red,
                VisualizationRgb.NEUTRAL_AIRFLOW.green,
                VisualizationRgb.NEUTRAL_AIRFLOW.blue,
                PadInletAppearance.MATERIAL_ALPHA
            ),
            metallic = PadInletAppearance.METALLIC,
            roughness = PadInletAppearance.ROUGHNESS
        )
    }

    private fun recycleAll() {
        arrows.forEach { destroyMesh(it) }
        arrows = emptyList()
        panel?.let { destroyMesh(it) }
        panel = null
        panelWidthM = 0.0
        panelHeightM = 0.0
        root?.let { destroyOwnedNode(it) }
        root = null
    }

    private fun destroyMesh(bound: BoundMesh) {
        destroyOwnedNode(bound.mesh)
        destroyOwnedNode(bound.holder)
        poolMaterial(bound.material)
    }

    private fun abandonOwnedNodes() {
        arrows.forEach { bound ->
            markNodeAbandoned(bound.mesh)
            markNodeAbandoned(bound.holder)
            markMaterialAbandoned(bound.material)
        }
        arrows = emptyList()
        panel?.let { bound ->
            markNodeAbandoned(bound.mesh)
            markNodeAbandoned(bound.holder)
            markMaterialAbandoned(bound.material)
        }
        panel = null
        panelWidthM = 0.0
        panelHeightM = 0.0
        root?.let { markNodeAbandoned(it) }
        root = null
        materialPool.forEach { markMaterialAbandoned(it) }
        materialPool.clear()
    }

    private fun destroyOwnedNode(node: Node) {
        if (destroyedNodes.put(node, true) != null) return
        if (!canTouchScene()) return
        host.lifecycle.destroyOwnedResource(node.name ?: System.identityHashCode(node).toString())
        node.destroy()
    }

    private fun markNodeAbandoned(node: Node) {
        destroyedNodes[node] = true
    }

    private fun poolMaterial(instance: MaterialInstance) {
        if (destroyedMaterials.containsKey(instance)) return
        materialPool.addLast(instance)
    }

    private fun markMaterialAbandoned(instance: MaterialInstance) {
        destroyedMaterials[instance] = true
    }

    private fun destroyPooledMaterials() {
        while (materialPool.isNotEmpty()) {
            val instance = materialPool.removeFirst()
            if (destroyedMaterials.put(instance, true) != null) continue
            if (!canTouchScene()) continue
            materialLoader.destroyMaterialInstance(instance)
        }
    }

    private fun canTouchScene(): Boolean {
        if (disposed || !hostAlive) return false
        if (!sceneViewStillAlive()) {
            hostAlive = false
            return false
        }
        return true
    }

    private fun sceneViewStillAlive(): Boolean {
        val view = sceneView ?: return false
        return view.lifecycle != null
    }

    private data class BoundMesh(
        val holder: Node,
        val mesh: GeometryNode,
        val material: MaterialInstance
    )

    companion object {
        const val ROOT_NAME = CirculationRingSceneMath.GREENHOUSE_ROOT_NAME
    }
}
