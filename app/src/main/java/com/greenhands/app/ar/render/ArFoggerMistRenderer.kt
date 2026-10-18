package com.greenhands.app.ar.render

import android.os.SystemClock
import androidx.compose.ui.graphics.Color
import com.google.android.filament.Engine
import com.google.android.filament.MaterialInstance
import com.greenhands.app.ar.domain.ArTabletopPhase
import com.greenhands.app.heat.visualization.FogMistAppearance
import com.greenhands.app.heat.visualization.FogMistDistribution
import com.greenhands.app.heat.visualization.FogMistVisual
import com.greenhands.app.heat.visualization.FoggerEffectFrame
import com.greenhands.app.heat.visualization.FoggerMistAnimator
import com.greenhands.app.heat.visualization.FoggerMistGroup
import com.greenhands.app.heat.visualization.FoggerVisualEnvelope
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
 * Programmatic fogger mist on the live AR engine. Three pooled combined-mesh
 * GeometryNodes per fogger. Vertex buffers are updated with SceneView 2.2.1
 * [GeometryNode.updateGeometry]; nodes are not created or destroyed per frame.
 * Tabletop scale is inherited from the greenhouse root only.
 *
 * Ownership: this renderer never destroys SceneView, Scene, Engine, ModelLoader,
 * or MaterialLoader. Nodes, geometries, and material instances are bound to
 * [engine] and are never reused with another Engine identity.
 */
class ArFoggerMistRenderer(
    private val engine: Engine,
    private val materialLoader: MaterialLoader
) {
    val engineIdentity: Int = System.identityHashCode(engine)
    val host = ArFoggerMistHost(engineIdentity)

    private val materialPool = ArrayDeque<MaterialInstance>()
    private val groups = linkedMapOf<String, BoundFogGroup>()
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
        frame: FoggerEffectFrame,
        layout: SceneLayout,
        greenhouseRoot: Node,
        phase: ArTabletopPhase,
        selectedEquipmentUuid: String? = null
    ) {
        if (disposed || !syncEnabled) return
        host.bind(ArFoggerMistHost.request(frame, phase, greenhouseRoot.name ?: ROOT_NAME))
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
            val key = meshKey(spec, layout)
            if (existing != null && existing.meshKey != key) {
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
        if (!dtS.isFinite() || dtS <= 0.0 || dtS >= FogMistVisual.MAX_FRAME_GAP_S) {
            lastTickNs = nowNs
            visualTimeS = 0.0
            return
        }
        val minDt = 1.0 / FogMistVisual.MAX_VISUAL_HZ
        if (dtS + 1e-12 < minDt) return
        lastTickNs = nowNs
        visualTimeS += dtS
        groups.values.forEach { animate(it, dtS) }
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

    private fun createGroup(
        spec: FoggerMistGroup,
        layout: SceneLayout,
        greenhouseRoot: Node
    ): BoundFogGroup {
        val root = Node(engine).apply {
            name = "fog-mist:${spec.equipmentUuid}"
            isTouchable = false
        }
        if (canTouchScene() && root.parent !== greenhouseRoot) {
            root.parent = greenhouseRoot
        }
        val shells = List(FogMistVisual.MAX_SCENE_NODES_PER_FOGGER) { shell ->
            createShell(spec, layout, shell).also { mesh ->
                if (canTouchScene() && mesh.holder.parent !== root) {
                    mesh.holder.parent = root
                }
            }
        }
        host.lifecycle.registerOwnedResource("fog-group:${spec.equipmentUuid}", engineIdentity)
        return BoundFogGroup(spec.equipmentUuid, root, shells, spec, layout, null, meshKey(spec, layout))
    }

    private fun createShell(
        spec: FoggerMistGroup,
        layout: SceneLayout,
        shell: Int
    ): BoundMesh {
        val holder = Node(engine).apply {
            name = "fog-cloud:${spec.equipmentUuid}:$shell"
            isTouchable = false
        }
        val material = obtainMaterial(FogMistAppearance.MIST)
        val slots = shellSlotCount(spec, shell)
        val flakes = FogDropletMesh.padded(cloudFlakes(spec, layout, shell, visualTimeS), slots)
        val mesh = GeometryNode(
            engine = engine,
            geometry = FogDropletMesh.createCloud(engine, flakes),
            materialInstance = material,
            builderApply = {
                culling(FogMistAppearance.CULLING)
                castShadows(false)
                receiveShadows(false)
            }
        ).apply {
            name = "fog-cloud-mesh:${spec.equipmentUuid}:$shell"
            isTouchable = false
        }
        if (canTouchScene() && mesh.parent !== holder) {
            mesh.parent = holder
        }
        host.lifecycle.registerOwnedResource("fog-cloud:${spec.equipmentUuid}:$shell", engineIdentity)
        return BoundMesh(holder, mesh, material, slots)
    }

    private fun shellSlotCount(spec: FoggerMistGroup, shell: Int): Int {
        val n = spec.particles.count { FogMistVisual.shellIndex(it.distanceFraction) == shell }
        return n.coerceAtLeast(1)
    }

    private fun cloudFlakes(
        spec: FoggerMistGroup,
        layout: SceneLayout,
        shell: Int,
        timeS: Double
    ): List<FogCloudFlake> {
        val spray = CirculationRingSceneMath.domainOffsetToRootLocal(
            spec.sprayDomainDx,
            spec.sprayDomainDy,
            spec.sprayDomainDz,
            layout
        )
        val out = ArrayList<FogCloudFlake>(spec.particleCount / FogMistVisual.MAX_SCENE_NODES_PER_FOGGER + 1)
        spec.particles.forEachIndexed { index, speck ->
            if (FogMistVisual.shellIndex(speck.distanceFraction) != shell) return@forEachIndexed
            val domain = FoggerMistAnimator.particleDomainPoint(spec, index, timeS)
            val inside = FoggerVisualEnvelope.insideHardBoundaries(
                domain,
                spec.houseLengthM,
                spec.houseWidthM,
                spec.houseHeightM
            )
            val progress = FoggerMistAnimator.particleProgress(spec, index, timeS)
            val travelFade = FoggerMistAnimator.fadeAtProgress(progress)
            val canopy = FoggerVisualEnvelope.canopyAttenuation(domain.zM, spec.canopyHeightM)
            val fade = if (inside) travelFade * canopy else 0f
            val local = CirculationRingSceneMath.domainOffsetToRootLocal(
                domain.xM - spec.originM.xM,
                domain.yM - spec.originM.yM,
                domain.zM - spec.originM.zM,
                layout
            )
            val size = FogMistAppearance.DROPLET_SIZE_M +
                (FogMistAppearance.FAR_DROPLET_SIZE_M - FogMistAppearance.DROPLET_SIZE_M) * progress
            val streak = FogMistAppearance.STREAK_LENGTH_M +
                (FogMistAppearance.FAR_STREAK_LENGTH_M - FogMistAppearance.STREAK_LENGTH_M) * progress
            val axis = FogMistDistribution.tiltedAxis(
                spec.equipmentUuid,
                index,
                spray.first,
                spray.second,
                spray.third
            )
            out += FogCloudFlake(
                x = local.first.toFloat(),
                y = local.second.toFloat(),
                z = local.third.toFloat(),
                size = size.toFloat(),
                streak = streak.toFloat(),
                fade = fade,
                dx = axis.first.toFloat(),
                dy = axis.second.toFloat(),
                dz = axis.third.toFloat()
            )
        }
        return out
    }

    private fun meshKey(spec: FoggerMistGroup, layout: SceneLayout): String =
        "${spec.equipmentUuid}:${spec.particleCount}:${(spec.maxDistanceM * 20.0).toInt()}:${layout.lengthMappedToX}"

    private fun applyPose(
        bound: BoundFogGroup,
        spec: FoggerMistGroup,
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
        bound.root.rotation = Rotation(y = 0f)
        bound.root.scale = Scale(layout.uniformScale.toFloat())
        bound.root.isVisible = true
        bound.root.isTouchable = false
        bound.particles.forEach { shell ->
            shell.holder.position = Position(0f, 0f, 0f)
            shell.holder.rotation = Rotation()
            shell.holder.scale = Scale(1f)
        }
        animate(bound, 0.0)
    }

    private fun animate(bound: BoundFogGroup, dtS: Double) {
        val spec = bound.spec
        if (dtS > 0.0 && dtS < FogMistVisual.MAX_FRAME_GAP_S && FogMistVisual.DISPLAY_COLOR_BLEND_S > 0.0) {
            bound.colorBlend = (
                bound.colorBlend +
                    (dtS / FogMistVisual.DISPLAY_COLOR_BLEND_S).toFloat()
                ).coerceAtMost(1f)
        }
        val color = FogMistAppearance.emphasizeMist(bound.selected)
        bound.particles.forEachIndexed { shell, mesh ->
            if (!canTouchScene()) return
            val flakes = FogDropletMesh.padded(
                cloudFlakes(spec, bound.layout, shell, visualTimeS),
                mesh.slotCount
            )
            mesh.mesh.updateGeometry(vertices = FogDropletMesh.verticesOf(flakes))
            val tint = color.withAlpha(FogMistVisual.shellAlpha(shell))
            mesh.holder.isVisible = true
            applyColor(mesh.material, tint, 1f)
        }
    }

    private fun applyColor(instance: MaterialInstance, color: VisualizationRgb, fade: Float) {
        val write = ArCirculationColorMaterial.frameColorWrite(color, fade)
        instance.setColor(Color(write.red, write.green, write.blue, write.alpha))
    }

    private fun obtainMaterial(color: VisualizationRgb): MaterialInstance {
        return materialPool.removeFirstOrNull() ?: materialLoader.createColorInstance(
            Color(color.red, color.green, color.blue, color.alpha),
            metallic = FogMistAppearance.METALLIC,
            roughness = FogMistAppearance.ROUGHNESS
        )
    }

    private fun recycle(uuid: String) {
        groups.remove(uuid)?.let { bound ->
            bound.particles.forEach { mesh ->
                destroyOwnedNode(mesh.mesh)
                destroyOwnedNode(mesh.holder)
                poolMaterial(mesh.material)
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
            bound.particles.forEach { mesh ->
                markNodeAbandoned(mesh.mesh)
                markNodeAbandoned(mesh.holder)
                markMaterialAbandoned(mesh.material)
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

    private data class BoundMesh(
        val holder: Node,
        val mesh: GeometryNode,
        val material: MaterialInstance,
        val slotCount: Int
    )

    private data class BoundFogGroup(
        val uuid: String,
        val root: Node,
        val particles: List<BoundMesh>,
        var spec: FoggerMistGroup,
        var layout: SceneLayout,
        var snapshot: com.greenhands.app.heat.runtime.contracts.PhysicsFieldSnapshot?,
        val meshKey: String,
        var colorSnapshot: com.greenhands.app.heat.runtime.contracts.PhysicsFieldSnapshot? = null,
        var previousColorSnapshot: com.greenhands.app.heat.runtime.contracts.PhysicsFieldSnapshot? = null,
        var colorBlend: Float = 1f,
        var selected: Boolean = false
    )

    companion object {
        const val ROOT_NAME = CirculationRingSceneMath.GREENHOUSE_ROOT_NAME
    }
}
