package com.greenhands.app.ar.render

import androidx.compose.ui.graphics.Color
import com.google.android.filament.Engine
import com.google.android.filament.MaterialInstance
import com.greenhands.app.ar.domain.ArTabletopPhase
import com.greenhands.app.heat.visualization.EnvironmentalFieldAppearance
import com.greenhands.app.heat.visualization.EnvironmentalFieldFrame
import com.greenhands.app.heat.visualization.EnvironmentalFieldVisual
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
 * Pooled whole-greenhouse environmental field. One combined [GeometryNode] per
 * classification band (maximum 6). Geometry updates only when an accepted
 * snapshot or view selection changes. Never advances the solver.
 *
 * Ownership: one renderer per live Engine identity. SceneView, Scene, Engine,
 * ModelLoader and MaterialLoader are never recreated here.
 */
class ArEnvironmentalFieldRenderer(
    private val engine: Engine,
    private val materialLoader: MaterialLoader
) {
    val engineIdentity: Int = System.identityHashCode(engine)
    val host = ArEnvironmentalFieldHost(engineIdentity)

    private val destroyedNodes = IdentityHashMap<Node, Boolean>()
    private val destroyedMaterials = IdentityHashMap<MaterialInstance, Boolean>()
    private var disposed = false
    private var hostAlive = false
    private var syncEnabled = false
    private var sceneView: ARSceneView? = null
    private var root: Node? = null
    private var bands: List<BoundBand> = emptyList()
    private var lastSignature: String? = null

    fun attachToLiveHost(liveView: ARSceneView): Boolean {
        if (disposed) return false
        if (System.identityHashCode(liveView.engine) != engineIdentity) return false
        sceneView = liveView
        hostAlive = true
        syncEnabled = true
        return host.lifecycle.attach(engineIdentity)
    }

    fun sync(
        frame: EnvironmentalFieldFrame,
        layout: SceneLayout,
        greenhouseRoot: Node,
        phase: ArTabletopPhase
    ) {
        if (disposed || !syncEnabled) return
        val shouldWrite = host.bind(ArEnvironmentalFieldHost.request(frame, phase, greenhouseRoot.name ?: ROOT_NAME))
        if (phase != ArTabletopPhase.PLACED || frame.isEmpty) {
            clearEffectsWhileSceneAlive()
            return
        }
        if (!canTouchScene()) return
        ensurePool(greenhouseRoot)
        val fieldRoot = root ?: return
        if (fieldRoot.parent !== greenhouseRoot) {
            fieldRoot.parent = greenhouseRoot
        }
        fieldRoot.position = Position(0f, 0f, 0f)
        fieldRoot.rotation = Rotation(y = 0f)
        fieldRoot.scale = Scale(1f)
        fieldRoot.isVisible = true
        fieldRoot.isTouchable = false
        if (!shouldWrite && lastSignature == frame.signature) {
            applyVisibility(frame)
            return
        }
        lastSignature = frame.signature
        val active = frame.bands.take(EnvironmentalFieldVisual.MAX_GEOMETRY_NODES)
        bands.forEachIndexed { index, bound ->
            val spec = active.getOrNull(index)
            if (spec == null) {
                bound.mesh.isVisible = false
                return@forEachIndexed
            }
            val patches = EnvironmentalFieldPatchMesh.patchesOf(spec, layout, EnvironmentalFieldVisual.MAX_CELLS)
            bound.mesh.updateGeometry(vertices = EnvironmentalFieldPatchMesh.verticesOf(patches))
            applyColor(bound.material, spec.color)
            bound.mesh.isVisible = spec.cellCount > 0
            bound.mesh.isTouchable = false
        }
        host.lifecycle.bindGroups(active.size)
    }

    /**
     * Field geometry is snapshot/view driven. Visual ticks never rewrite vertices
     * and never call the solver.
     */
    fun tickVisual() = Unit

    fun clearEffectsWhileSceneAlive() {
        if (disposed) return
        host.lifecycle.clearEffectsWhileSceneAlive()
        host.clear()
        lastSignature = null
        if (canTouchScene()) {
            hideBands()
        } else {
            abandonOwnedNodes()
        }
        if (!disposed && hostAlive && sceneViewStillAlive()) {
            syncEnabled = true
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
        if (!canTouchScene()) {
            abandonAfterHostUnavailable()
            return
        }
        host.clear()
        recycleAll()
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
        hostAlive = false
        host.lifecycle.markHostUnavailable()
        abandonOwnedNodes()
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
        syncEnabled = false
        host.lifecycle.stopVisualUpdates()
    }

    fun noteRecomposition() {
        host.noteRecomposition()
    }

    private fun ensurePool(greenhouseRoot: Node) {
        if (root != null && bands.size == EnvironmentalFieldVisual.MAX_GEOMETRY_NODES) {
            if (root?.parent !== greenhouseRoot && canTouchScene()) {
                root?.parent = greenhouseRoot
            }
            return
        }
        if (!canTouchScene()) return
        val fieldRoot = root ?: Node(engine).apply {
            name = "env-field-root"
            isTouchable = false
        }.also {
            root = it
            host.noteNodeCreated()
            host.lifecycle.registerOwnedResource("env-field-root", engineIdentity)
        }
        if (fieldRoot.parent !== greenhouseRoot) {
            fieldRoot.parent = greenhouseRoot
        }
        if (bands.size == EnvironmentalFieldVisual.MAX_GEOMETRY_NODES) return
        val created = ArrayList<BoundBand>(EnvironmentalFieldVisual.MAX_GEOMETRY_NODES)
        repeat(EnvironmentalFieldVisual.MAX_GEOMETRY_NODES) { index ->
            val material = obtainMaterial()
            host.noteMaterialCreated()
            val mesh = GeometryNode(
                engine = engine,
                geometry = EnvironmentalFieldPatchMesh.create(engine, EnvironmentalFieldVisual.MAX_CELLS),
                materialInstance = material,
                builderApply = {
                    culling(EnvironmentalFieldAppearance.CULLING)
                    castShadows(false)
                    receiveShadows(false)
                }
            ).apply {
                name = "env-field-band:$index"
                isTouchable = false
                isVisible = false
            }
            host.noteNodeCreated()
            host.lifecycle.registerOwnedResource("env-field-band:$index", engineIdentity)
            if (mesh.parent !== fieldRoot) {
                mesh.parent = fieldRoot
            }
            created += BoundBand(mesh, material)
        }
        bands = created
    }

    private fun applyVisibility(frame: EnvironmentalFieldFrame) {
        val active = frame.bands.take(EnvironmentalFieldVisual.MAX_GEOMETRY_NODES)
        bands.forEachIndexed { index, bound ->
            bound.mesh.isVisible = active.getOrNull(index)?.cellCount?.let { it > 0 } == true
        }
    }

    private fun applyColor(instance: MaterialInstance, color: VisualizationRgb) {
        val write = ArCirculationColorMaterial.frameColorWrite(color, fade = 1f)
        instance.setColor(Color(write.red, write.green, write.blue, write.alpha))
    }

    private fun obtainMaterial(): MaterialInstance {
        return materialLoader.createColorInstance(
            Color(
                VisualizationRgb.GREEN.red,
                VisualizationRgb.GREEN.green,
                VisualizationRgb.GREEN.blue,
                EnvironmentalFieldAppearance.MATERIAL_ALPHA
            ),
            metallic = EnvironmentalFieldAppearance.METALLIC,
            roughness = EnvironmentalFieldAppearance.ROUGHNESS
        )
    }

    private fun hideBands() {
        bands.forEach { it.mesh.isVisible = false }
        root?.isVisible = false
        lastSignature = null
    }

    private fun recycleAll() {
        hideBands()
        bands.forEach { bound ->
            destroyOwnedNode(bound.mesh)
            destroyMaterial(bound.material)
        }
        bands = emptyList()
        root?.let { destroyOwnedNode(it) }
        root = null
        lastSignature = null
    }

    private fun abandonOwnedNodes() {
        bands.forEach { bound ->
            markNodeAbandoned(bound.mesh)
            markMaterialAbandoned(bound.material)
        }
        bands = emptyList()
        root?.let { markNodeAbandoned(it) }
        root = null
        lastSignature = null
    }

    private fun destroyOwnedNode(node: Node) {
        if (destroyedNodes.put(node, true) != null) return
        if (!canTouchScene()) return
        host.lifecycle.destroyOwnedResource(node.name ?: System.identityHashCode(node).toString())
        node.destroy()
    }

    private fun destroyMaterial(instance: MaterialInstance) {
        if (destroyedMaterials.put(instance, true) != null) return
        if (!canTouchScene()) return
        materialLoader.destroyMaterialInstance(instance)
    }

    private fun markNodeAbandoned(node: Node) {
        destroyedNodes[node] = true
    }

    private fun markMaterialAbandoned(instance: MaterialInstance) {
        destroyedMaterials[instance] = true
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

    private data class BoundBand(
        val mesh: GeometryNode,
        val material: MaterialInstance
    )

    companion object {
        const val ROOT_NAME = CirculationRingSceneMath.GREENHOUSE_ROOT_NAME
        const val WRITES_EMISSIVE_FACTOR = false
    }
}
