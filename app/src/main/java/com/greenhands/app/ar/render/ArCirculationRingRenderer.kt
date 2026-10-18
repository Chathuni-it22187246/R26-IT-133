package com.greenhands.app.ar.render

import android.os.SystemClock
import androidx.compose.ui.graphics.Color
import com.google.android.filament.Engine
import com.google.android.filament.MaterialInstance
import com.greenhands.app.ar.domain.ArTabletopPhase
import com.greenhands.app.heat.visualization.CirculationAirflowEnvelope
import com.greenhands.app.heat.visualization.CirculationEffectFrame
import com.greenhands.app.heat.visualization.CirculationRingAppearance
import com.greenhands.app.heat.visualization.CirculationRingAnimator
import com.greenhands.app.heat.visualization.CirculationRingGroup
import com.greenhands.app.heat.visualization.CirculationRingVisual
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
 * Programmatic circulation rings on the live AR engine. One pooled low-poly annulus
 * [GeometryNode] per ring, parented to the anchored greenhouse content root.
 * Tabletop scale is inherited from that root only.
 *
 * Ownership: this renderer never destroys SceneView, Scene, Engine, ModelLoader, or
 * MaterialLoader. Nodes, geometries, and material instances are bound to [engine] and
 * are never reused with another Engine identity.
 */
class ArCirculationRingRenderer(
    private val engine: Engine,
    private val materialLoader: MaterialLoader
) {
    val engineIdentity: Int = System.identityHashCode(engine)
    val host = ArCirculationRingHost(engineIdentity)

    private val materialPool = ArrayDeque<MaterialInstance>()
    private val groups = linkedMapOf<String, BoundRingGroup>()
    private val destroyedNodes = IdentityHashMap<Node, Boolean>()
    private val destroyedMaterials = IdentityHashMap<MaterialInstance, Boolean>()
    private var visualTimeS = 0.0
    private var lastTickNs = 0L
    private var disposed = false
    private var hostAlive = false
    private var syncEnabled = false
    private var visualUpdatesEnabled = false
    private var sceneView: ARSceneView? = null

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
        frame: CirculationEffectFrame,
        layout: SceneLayout,
        greenhouseRoot: Node,
        phase: ArTabletopPhase,
        selectedEquipmentUuid: String? = null
    ) {
        if (disposed || !syncEnabled) return
        host.bind(ArCirculationRingHost.request(frame, phase, greenhouseRoot.name ?: ROOT_NAME))
        if (phase != ArTabletopPhase.PLACED || frame.isEmpty) {
            clearEffectsWhileSceneAlive()
            return
        }
        if (!canTouchScene()) return
        val uniqueGroups = frame.groups.distinctBy { it.equipmentUuid }
        val keep = uniqueGroups.map { it.equipmentUuid }.toSet()
        groups.keys.filter { it !in keep }.toList().forEach { recycle(it) }
        uniqueGroups.forEach { spec ->
            val existing = groups[spec.equipmentUuid]
            if (existing != null && existing.rings.size != spec.ringCount) {
                recycle(spec.equipmentUuid)
            }
            val bound = groups.getOrPut(spec.equipmentUuid) {
                createGroup(spec, layout, greenhouseRoot)
            }
            if (bound.colorSnapshot != null &&
                frame.colorSnapshot !== bound.colorSnapshot &&
                frame.colorSnapshot != null
            ) {
                bound.previousColorSnapshot = bound.colorSnapshot
                bound.colorBlend = 0f
            }
            bound.snapshot = frame.snapshot
            bound.colorSnapshot = frame.colorSnapshot ?: frame.snapshot
            bound.selected = spec.equipmentUuid == selectedEquipmentUuid
            applyPose(bound, spec, layout, greenhouseRoot)
        }
        host.lifecycle.bindGroups(groups.size)
    }

    fun tickVisual(nowNs: Long = SystemClock.elapsedRealtimeNanos()) {
        if (disposed || !visualUpdatesEnabled || groups.isEmpty()) return
        if (!canTouchScene()) return
        if (lastTickNs == 0L) {
            lastTickNs = nowNs
            return
        }
        val dtS = (nowNs - lastTickNs) / 1_000_000_000.0
        lastTickNs = nowNs
        if (!dtS.isFinite() || dtS <= 0.0 || dtS >= 0.25) return
        visualTimeS += dtS
        groups.values.forEach { animate(it, dtS) }
    }

    /**
     * Removes current UUID ring groups while the live Scene is valid. The renderer
     * stays reusable for a later Start Visualization on the same Engine.
     */
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
        if (!disposed && hostAlive && sceneViewStillAlive()) {
            syncEnabled = true
            visualUpdatesEnabled = true
        }
    }

    fun clear() = clearEffectsWhileSceneAlive()

    /**
     * Stop updates, detach renderer-owned nodes while the Scene is valid, destroy
     * only renderer-owned Filament resources once, then mark disposed.
     */
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

    /**
     * Late Compose disposal after SceneView/Engine teardown. Cancels callbacks only.
     * Never calls Scene.removeEntities, Node.parent, GeometryNode.destroy, or Engine APIs.
     */
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

    private fun createGroup(
        spec: CirculationRingGroup,
        layout: SceneLayout,
        greenhouseRoot: Node
    ): BoundRingGroup {
        val root = Node(engine).apply {
            name = "circ-ring:${spec.equipmentUuid}"
            isTouchable = false
        }
        if (canTouchScene() && root.parent !== greenhouseRoot) {
            root.parent = greenhouseRoot
        }
        val rings = List(spec.ringCount) { index ->
            createRing(spec.equipmentUuid, index).also { ring ->
                if (canTouchScene() && ring.holder.parent !== root) {
                    ring.holder.parent = root
                }
            }
        }
        host.lifecycle.registerOwnedResource("group:${spec.equipmentUuid}", engineIdentity)
        return BoundRingGroup(spec.equipmentUuid, root, rings, spec, layout, null)
    }

    private fun createRing(uuid: String, index: Int): BoundRing {
        val holder = Node(engine).apply {
            name = "circ-ring-item:$uuid:$index"
            isTouchable = false
        }
        val material = obtainMaterial()
        val mesh = GeometryNode(
            engine = engine,
            geometry = CirculationAnnulusMesh.create(engine),
            materialInstance = material,
            builderApply = {
                culling(CirculationRingAppearance.CULLING)
                castShadows(false)
                receiveShadows(false)
            }
        ).apply {
            name = "circ-ring-mesh:$uuid:$index"
            isTouchable = false
        }
        if (canTouchScene() && mesh.parent !== holder) {
            mesh.parent = holder
        }
        host.lifecycle.registerOwnedResource("mesh:$uuid:$index", engineIdentity)
        return BoundRing(holder, mesh, material)
    }

    private fun applyPose(
        bound: BoundRingGroup,
        spec: CirculationRingGroup,
        layout: SceneLayout,
        greenhouseRoot: Node
    ) {
        if (!canTouchScene()) return
        bound.spec = spec
        bound.layout = layout
        val scene = CirculationRingSceneMath.scenePosition(spec.originM, layout)
        if (bound.root.parent !== greenhouseRoot) {
            bound.root.parent = greenhouseRoot
        }
        bound.root.position = Position(scene.x.toFloat(), scene.y.toFloat(), scene.z.toFloat())
        bound.root.rotation = Rotation(y = CirculationRingSceneMath.sceneYawDeg(spec.yawDeg, layout).toFloat())
        bound.root.scale = Scale(layout.uniformScale.toFloat())
        bound.root.isVisible = true
        bound.root.isTouchable = false
        animate(bound, 0.0)
    }

    private fun animate(bound: BoundRingGroup, dtS: Double) {
        val spec = bound.spec
        val count = bound.rings.size
        if (dtS > 0.0 && dtS < 0.25 && CirculationRingVisual.DISPLAY_COLOR_BLEND_S > 0.0) {
            bound.colorBlend = (
                bound.colorBlend +
                    (dtS / CirculationRingVisual.DISPLAY_COLOR_BLEND_S).toFloat()
                ).coerceAtMost(1f)
        }
        bound.rings.forEachIndexed { index, ring ->
            val distanceM = CirculationRingAnimator.distanceM(
                spec.minDistanceM,
                spec.maxDistanceM,
                visualTimeS,
                index,
                count,
                CirculationRingVisual.CYCLE_S,
                spec.animationPhase
            )
            val progress = CirculationRingAnimator.progress(
                visualTimeS,
                index,
                count,
                CirculationRingVisual.CYCLE_S,
                spec.animationPhase
            )
            val fade = CirculationRingAnimator.fadeAtProgress(progress)
            val expand = CirculationRingAnimator.expandScale(progress)
            ring.holder.position = Position(0f, 0f, distanceM.toFloat())
            ring.holder.rotation = Rotation(x = 0f, y = 0f, z = 0f)
            ring.holder.scale = Scale(expand, expand, 1f)
            val domain = spec.domainPointAt(distanceM)
            val inside = bound.snapshot == null ||
                CirculationAirflowEnvelope.contains(bound.snapshot!!, domain)
            ring.holder.isVisible = inside && fade > 0.05f
            val from = CirculationRingAnimator.colorAtDistance(
                spec,
                distanceM,
                bound.previousColorSnapshot
            )
            val to = CirculationRingAnimator.colorAtDistance(
                spec,
                distanceM,
                bound.colorSnapshot ?: bound.snapshot
            )
            val color = CirculationRingAppearance.emphasize(
                VisualizationRgb.lerp(from, to, bound.colorBlend),
                bound.selected
            )
            applyColor(ring.material, color, fade)
        }
    }

    private fun applyColor(instance: MaterialInstance, color: VisualizationRgb, fade: Float) {
        val write = ArCirculationColorMaterial.frameColorWrite(color, fade)
        instance.setColor(Color(write.red, write.green, write.blue, write.alpha))
    }

    private fun obtainMaterial(): MaterialInstance {
        return materialPool.removeFirstOrNull() ?: materialLoader.createColorInstance(
            Color(
                VisualizationRgb.LIGHT_BLUE.red,
                VisualizationRgb.LIGHT_BLUE.green,
                VisualizationRgb.LIGHT_BLUE.blue,
                CirculationRingAppearance.MATERIAL_ALPHA
            ),
            metallic = CirculationRingAppearance.METALLIC,
            roughness = CirculationRingAppearance.ROUGHNESS
        )
    }

    private fun recycle(uuid: String) {
        groups.remove(uuid)?.let { bound ->
            bound.rings.forEach { ring ->
                destroyOwnedNode(ring.mesh)
                destroyOwnedNode(ring.holder)
                poolMaterial(ring.material)
            }
            destroyOwnedNode(bound.root)
        }
    }

    private fun recycleAll() {
        groups.keys.toList().forEach { recycle(it) }
        groups.clear()
    }

    private fun abandonOwnedNodes() {
        groups.values.forEach { bound ->
            bound.rings.forEach { ring ->
                markNodeAbandoned(ring.mesh)
                markNodeAbandoned(ring.holder)
                markMaterialAbandoned(ring.material)
            }
            markNodeAbandoned(bound.root)
        }
        groups.clear()
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

    private data class BoundRing(
        val holder: Node,
        val mesh: GeometryNode,
        val material: MaterialInstance
    )

    private data class BoundRingGroup(
        val uuid: String,
        val root: Node,
        val rings: List<BoundRing>,
        var spec: CirculationRingGroup,
        var layout: SceneLayout,
        var snapshot: com.greenhands.app.heat.runtime.contracts.PhysicsFieldSnapshot?,
        var colorSnapshot: com.greenhands.app.heat.runtime.contracts.PhysicsFieldSnapshot? = null,
        var previousColorSnapshot: com.greenhands.app.heat.runtime.contracts.PhysicsFieldSnapshot? = null,
        var colorBlend: Float = 1f,
        var selected: Boolean = false
    )

    companion object {
        const val ROOT_NAME = CirculationRingSceneMath.GREENHOUSE_ROOT_NAME
    }
}
