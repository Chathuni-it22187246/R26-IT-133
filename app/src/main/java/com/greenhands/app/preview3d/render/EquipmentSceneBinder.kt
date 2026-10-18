package com.greenhands.app.preview3d.render

import androidx.compose.ui.graphics.Color
import com.google.android.filament.Engine
import com.greenhands.app.equipment.model.EquipmentAssetCatalog
import com.greenhands.app.equipment.model.EquipmentType
import com.greenhands.app.equipment.render.EquipmentScenePlacement
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.math.Size
import io.github.sceneview.model.Model
import io.github.sceneview.model.ModelInstance
import io.github.sceneview.node.CubeNode
import io.github.sceneview.node.ModelNode
import io.github.sceneview.node.Node

/**
 * Loads each GLB once and creates independent ModelNode instances for placement.
 * Removed placements are pooled so the Filament instance root is not destroyed
 * while the source asset is still needed for further copies.
 */
class EquipmentSceneBinder(
    private val engine: Engine,
    private val modelLoader: ModelLoader,
    private val materialLoader: MaterialLoader
) {
    private val models = mutableMapOf<EquipmentType, Model>()
    private val usedPrimary = mutableSetOf<EquipmentType>()
    private val bounds = linkedMapOf<String, BoundEquipment>()
    private val pool = mutableMapOf<EquipmentType, MutableList<BoundEquipment>>()

    private val validMat = materialLoader.createColorInstance(
        Color(0.20f, 0.75f, 0.35f, 0.28f),
        metallic = 0.0f,
        roughness = 0.8f
    )
    private val invalidMat = materialLoader.createColorInstance(
        Color(0.85f, 0.18f, 0.16f, 0.32f),
        metallic = 0.0f,
        roughness = 0.8f
    )

    fun nodes(): List<Node> = bounds.values.map { it.root }

    /**
     * Loads the three equipment GLBs on this binder's engine/model loader.
     * Does not share Filament resources with any other SceneView.
     */
    fun preloadAssets() {
        EquipmentType.entries.forEach { type ->
            models.getOrPut(type) {
                modelLoader.createModel(EquipmentAssetCatalog.assetPath(type))
            }
        }
    }

    fun sync(
        placements: List<EquipmentScenePlacement>,
        selectedId: String?,
        highlightSelectedBounds: Boolean = true
    ) {
        val keep = placements.map { it.id }.toSet()
        bounds.keys.filter { it !in keep }.toList().forEach { id ->
            bounds.remove(id)?.let { recycle(it) }
        }
        placements.forEach { placement ->
            val bound = bounds.getOrPut(placement.id) { obtain(placement) }
            applyPose(bound, placement, selectedId, highlightSelectedBounds)
        }
    }

    fun destroy() {
        bounds.values.forEach { bound -> runCatching { bound.root.destroy() } }
        bounds.clear()
        pool.values.flatten().forEach { bound -> runCatching { bound.root.destroy() } }
        pool.clear()
    }

    private fun obtain(placement: EquipmentScenePlacement): BoundEquipment {
        val recycled = pool[placement.instance.type]?.removeFirstOrNull()
        if (recycled != null) {
            recycled.root.isVisible = true
            recycled.root.name = "eq:${placement.id}"
            recycled.model.name = "eq-model:${placement.id}"
            recycled.box.name = "eq-box:${placement.id}"
            return recycled
        }
        return create(placement)
    }

    private fun recycle(bound: BoundEquipment) {
        bound.root.parent = null
        bound.root.isVisible = false
        pool.getOrPut(bound.type) { mutableListOf() }.add(bound)
    }

    private fun create(placement: EquipmentScenePlacement): BoundEquipment {
        val type = placement.instance.type
        val spec = EquipmentAssetCatalog.spec(type)
        val aabb = spec.localAabb
        val root = Node(engine).apply {
            name = "eq:${placement.id}"
            isTouchable = true
        }
        val modelNode = ModelNode(
            modelInstance = obtainInstance(type),
            autoAnimate = false,
            scaleToUnits = null,
            centerOrigin = null
        ).apply {
            name = "eq-model:${placement.id}"
            parent = root
            isTouchable = true
        }
        val box = CubeNode(
            engine = engine,
            size = Size(
                (aabb.maxX - aabb.minX).toFloat(),
                (aabb.maxY - aabb.minY).toFloat(),
                (aabb.maxZ - aabb.minZ).toFloat()
            ),
            center = Position(
                ((aabb.minX + aabb.maxX) * 0.5).toFloat(),
                ((aabb.minY + aabb.maxY) * 0.5).toFloat(),
                ((aabb.minZ + aabb.maxZ) * 0.5).toFloat()
            ),
            materialInstance = validMat
        ).apply {
            name = "eq-box:${placement.id}"
            parent = root
            isTouchable = false
        }
        return BoundEquipment(type, root, modelNode, box)
    }

    private fun obtainInstance(type: EquipmentType): ModelInstance {
        val model = models.getOrPut(type) {
            modelLoader.createModel(EquipmentAssetCatalog.assetPath(type))
        }
        val instance = if (usedPrimary.add(type)) {
            model.instance
        } else {
            modelLoader.createInstance(model)
        }
        return instance ?: error("Could not create an independent instance of ${type.assetFileName}")
    }

    private fun applyPose(
        bound: BoundEquipment,
        placement: EquipmentScenePlacement,
        selectedId: String?,
        highlightSelectedBounds: Boolean
    ) {
        bound.root.position = Position(
            placement.sceneX.toFloat(),
            placement.sceneY.toFloat(),
            placement.sceneZ.toFloat()
        )
        bound.root.rotation = Rotation(y = placement.sceneYawDeg.toFloat())
        bound.root.scale = Scale(placement.scale.toFloat())
        bound.root.isVisible = true
        bound.box.materialInstance = when {
            !placement.instance.validity.isValid -> invalidMat
            else -> validMat
        }
        bound.box.isVisible = placement.instance.isGhost ||
            !placement.instance.validity.isValid ||
            (highlightSelectedBounds && placement.id == selectedId)
        bound.box.isTouchable = true
    }

    private data class BoundEquipment(
        val type: EquipmentType,
        val root: Node,
        val model: ModelNode,
        val box: CubeNode
    )
}

fun Node.equipmentInstanceId(): String? {
    var current: Node? = this
    while (current != null) {
        val name = current.name.orEmpty()
        if (name.startsWith("eq:")) return name.removePrefix("eq:")
        current = current.parent
    }
    return null
}
