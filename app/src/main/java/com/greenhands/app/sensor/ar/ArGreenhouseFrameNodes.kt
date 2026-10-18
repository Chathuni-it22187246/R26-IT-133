package com.greenhands.app.sensor.ar

import androidx.compose.ui.graphics.Color
import com.google.android.filament.Engine
import com.google.android.filament.MaterialInstance
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.greenhands.app.sensor.model.GreenhousePhysicalConfig
import com.greenhands.app.ui.theme.ForestEmerald
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.ar.node.PoseNode
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Size
import io.github.sceneview.node.CubeNode
import io.github.sceneview.node.Node
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Builds the Real AR greenhouse **root** (origin + yaw) and, after ALIGNED,
 * attaches one shared structure: floor grid/perimeter, posts, eave, pitched
 * roof frame, and optional transparent wall/roof panels.
 *
 * All structure, sensor, coverage and recommendation nodes are children of
 * [FRAME_ROOT_NAME]. One SceneView unit = one real-world metre. Heat tabletop
 * scaling is never applied.
 */
object ArGreenhouseFrameNodes {

    const val FRAME_ROOT_NAME = "greenhouse_frame_root"

    fun clearFrame(nodes: MutableList<Node>) {
        nodes.toList()
            .filter {
                it.name == FRAME_ROOT_NAME ||
                    it.name == ArGreenhouseStructurePlan.STRUCTURE_GROUP_NAME ||
                    it.name?.startsWith("greenhouse_frame") == true ||
                    it.name?.startsWith("greenhouse_floor") == true ||
                    it.name?.startsWith("greenhouse_panel") == true ||
                    it.name?.startsWith("greenhouse_structure") == true
            }
            .forEach { node ->
                clearStructure(node)
                node.destroy()
                nodes -= node
            }
    }

    fun clearStructure(root: Node?) {
        if (root == null) return
        root.childNodes.toList()
            .filter {
                it.name == ArGreenhouseStructurePlan.STRUCTURE_GROUP_NAME ||
                    it.name?.startsWith("greenhouse_floor") == true ||
                    it.name?.startsWith("greenhouse_frame") == true ||
                    it.name?.startsWith("greenhouse_panel") == true
            }
            .forEach { child -> child.destroy() }
    }

    fun findFrameRoot(nodes: List<Node>): Node? =
        nodes.firstOrNull { it.name == FRAME_ROOT_NAME }

    fun structureChildCount(root: Node?): Int {
        val group = root?.childNodes?.firstOrNull {
            it.name == ArGreenhouseStructurePlan.STRUCTURE_GROUP_NAME
        } ?: return 0
        return group.childNodes.size
    }

    /**
     * Creates the shared origin/yaw root. When [attachStructure] is true the
     * configured greenhouse wireframe (and safe transparent panels) are children
     * of that single root — never a second AR anchor.
     */
    fun buildFrame(
        engine: Engine,
        pose: ArGreenhousePose,
        physical: GreenhousePhysicalConfig,
        session: Session? = null,
        materialLoader: MaterialLoader? = null,
        attachStructure: Boolean = true
    ): Node? {
        if (!ArGreenhouseFrameGeometry.shouldShowFrame(pose.phase)) return null
        val ox = pose.worldTranslationX ?: return null
        val oy = pose.worldTranslationY ?: return null
        val oz = pose.worldTranslationZ ?: return null
        val yaw = pose.yawRadians ?: return null
        val length = physical.lengthMeters.toFloat()
        val width = physical.widthMeters.toFloat()
        val height = physical.heightMeters.toFloat()
        if (length <= 0f || width <= 0f || height <= 0f) return null

        val arPose = Pose(
            floatArrayOf(ox, oy, oz),
            ArGreenhouseFrameGeometry.yawQuaternionXyZw(yaw)
        )

        val root: Node = createRoot(engine, arPose, session) ?: return null
        root.name = FRAME_ROOT_NAME
        root.isEditable = false
        if (attachStructure && materialLoader != null) {
            attachStructure(engine, root, physical, materialLoader)
        }
        return root
    }

    fun attachStructure(
        engine: Engine,
        frameRoot: Node,
        physical: GreenhousePhysicalConfig,
        materialLoader: MaterialLoader
    ) {
        clearStructure(frameRoot)
        val plan = ArGreenhouseStructurePlan.plan(physical, includeTransparentPanels = true)
        if (plan.isEmpty()) return
        val group = Node(engine).apply {
            name = ArGreenhouseStructurePlan.STRUCTURE_GROUP_NAME
            isEditable = false
        }
        val frameMat = ArRealMaterials.opaqueColor(materialLoader, ForestEmerald)
        val gridMat = ArRealMaterials.opaqueColor(
            materialLoader,
            ForestEmerald.copy(alpha = 1f)
        )
        val wallMat = ArRealMaterials.transparentOverlay(
            materialLoader,
            ForestEmerald,
            ArGreenhouseFrameGeometry.AR_WALL_ALPHA
        )
        val roofMat = ArRealMaterials.transparentOverlay(
            materialLoader,
            ForestEmerald,
            ArGreenhouseFrameGeometry.AR_ROOF_ALPHA
        )
        plan.forEach { part ->
            when (part.part) {
                ArGreenhouseStructurePart.FLOOR_GRID -> {
                    val edge = part.edge ?: return@forEach
                    group.addChildNode(
                        beamNode(
                            engine = engine,
                            name = part.name,
                            a = edge.a,
                            b = edge.b,
                            thickness = ArGreenhouseStructurePlan.GRID_BEAM_THICK_M,
                            material = gridMat
                        )
                    )
                }
                ArGreenhouseStructurePart.FLOOR_PERIMETER,
                ArGreenhouseStructurePart.POST,
                ArGreenhouseStructurePart.EAVE,
                ArGreenhouseStructurePart.ROOF_FRAME -> {
                    val edge = part.edge ?: return@forEach
                    group.addChildNode(
                        beamNode(
                            engine = engine,
                            name = part.name,
                            a = edge.a,
                            b = edge.b,
                            thickness = ArGreenhouseStructurePlan.FRAME_BEAM_THICK_M,
                            material = frameMat
                        )
                    )
                }
                ArGreenhouseStructurePart.WALL_PANEL -> {
                    val panel = part.panel ?: return@forEach
                    group.addChildNode(panelNode(engine, part.name, panel, wallMat))
                }
                ArGreenhouseStructurePart.ROOF_PANEL -> {
                    val panel = part.panel ?: return@forEach
                    group.addChildNode(panelNode(engine, part.name, panel, roofMat))
                }
            }
        }
        frameRoot.addChildNode(group)
    }

    private fun createRoot(engine: Engine, arPose: Pose, session: Session?): Node? {
        if (session != null) {
            val anchor = try {
                session.createAnchor(arPose)
            } catch (_: Exception) {
                null
            }
            if (anchor != null) {
                return AnchorNode(engine = engine, anchor = anchor)
            }
        }
        return PoseNode(engine = engine, pose = arPose)
    }

    /**
     * Thin beam between two local-metre endpoints. Local Y of the cube is
     * aligned with the edge so pitched-roof spokes are not axis-aligned slabs.
     */
    internal fun beamNode(
        engine: Engine,
        name: String,
        a: ArLocalPoint,
        b: ArLocalPoint,
        thickness: Float,
        material: MaterialInstance
    ): Node {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val dz = b.z - a.z
        val len = sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(1e-4f)
        val holder = Node(engine).apply {
            this.name = name
            isEditable = false
            position = Position((a.x + b.x) * 0.5f, (a.y + b.y) * 0.5f, (a.z + b.z) * 0.5f)
            rotation = rotationAligningLocalY(dx / len, dy / len, dz / len)
        }
        holder.addChildNode(
            CubeNode(
                engine = engine,
                size = Size(thickness, len, thickness),
                center = Position(0f, 0f, 0f),
                materialInstance = material,
                builderApply = {
                    culling(false)
                    castShadows(false)
                    receiveShadows(false)
                }
            ).apply {
                this.name = name + "_beam"
                isEditable = false
                isTouchable = false
            }
        )
        return holder
    }

    internal fun panelNode(
        engine: Engine,
        name: String,
        panel: ArLocalPanel,
        material: MaterialInstance
    ): Node {
        val holder = Node(engine).apply {
            this.name = name
            isEditable = false
            position = Position(panel.center.x, panel.center.y, panel.center.z)
            rotation = rotationAligningLocalZ(panel.normalX, panel.normalY, panel.normalZ)
        }
        holder.addChildNode(
            CubeNode(
                engine = engine,
                size = Size(
                    panel.width.coerceAtLeast(0.05f),
                    panel.height.coerceAtLeast(0.05f),
                    ArGreenhouseStructurePlan.PANEL_THICK_M
                ),
                center = Position(0f, 0f, 0f),
                materialInstance = material,
                builderApply = {
                    culling(false)
                    castShadows(false)
                    receiveShadows(false)
                }
            ).apply {
                this.name = name + "_quad"
                isEditable = false
                isTouchable = false
            }
        )
        return holder
    }

    /** Rotate so local +Y points along [dir]. */
    internal fun rotationAligningLocalY(dirX: Float, dirY: Float, dirZ: Float): Rotation {
        val horizontal = sqrt(dirX * dirX + dirZ * dirZ)
        val pitchDeg = Math.toDegrees(atan2(-horizontal.toDouble(), dirY.toDouble())).toFloat()
        val yawDeg = Math.toDegrees(atan2(dirX.toDouble(), dirZ.toDouble())).toFloat()
        return Rotation(x = pitchDeg, y = yawDeg, z = 0f)
    }

    /** Rotate so local +Z points along [normal]. */
    internal fun rotationAligningLocalZ(nx: Float, ny: Float, nz: Float): Rotation {
        val yawDeg = Math.toDegrees(atan2(nx.toDouble(), nz.toDouble())).toFloat()
        val pitchDeg = Math.toDegrees(
            atan2(-ny.toDouble(), sqrt((nx * nx + nz * nz).toDouble()))
        ).toFloat()
        return Rotation(x = pitchDeg, y = yawDeg, z = 0f)
    }
}
