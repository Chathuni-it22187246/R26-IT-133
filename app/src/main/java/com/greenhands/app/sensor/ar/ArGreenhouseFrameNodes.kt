package com.greenhands.app.sensor.ar

import com.google.android.filament.Engine
import com.google.android.filament.MaterialInstance
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.greenhands.app.sensor.model.GreenhousePhysicalConfig
import com.greenhands.app.ui.theme.ForestEmerald
import com.greenhands.app.ui.theme.NightBorder
import dev.romainguy.kotlin.math.Quaternion
import dev.romainguy.kotlin.math.cross
import dev.romainguy.kotlin.math.dot
import dev.romainguy.kotlin.math.length
import dev.romainguy.kotlin.math.normalize
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.ar.node.PoseNode
import io.github.sceneview.math.Direction
import io.github.sceneview.math.Position
import io.github.sceneview.math.Size
import io.github.sceneview.node.CubeNode
import io.github.sceneview.node.Node
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Builds the Real AR greenhouse root anchored to the confirmed AR surface.
 *
 * Hierarchy:
 * ```
 * Anchor / Pose root  (uniform ArRealScale)
 *   ├── structure panels (floor / walls / roof) — translucent
 *   ├── structural frame edges — thin opaque beams
 *   ├── floor grid — thin lines
 *   ├── sensors (attached by ArSensorMarkerNodes)
 *   ├── coverage cells (attached by ArCoverageNodes)
 *   └── P# markers (attached by ArRecommendationNodes)
 * ```
 *
 * Geometry vertices come from [ArGreenhouseFrameGeometry] — the same logical
 * dimensions / ridge / frame layout as [VirtualGreenhouseRenderer].
 *
 * NEVER attaches a single L×W×H opaque cuboid.
 */
object ArGreenhouseFrameNodes {

    const val FRAME_ROOT_NAME = "greenhouse_frame_root"
    const val STRUCTURE_PREFIX = "greenhouse_structure_"
    const val FRAME_EDGE_PREFIX = "greenhouse_frame_edge_"
    const val GRID_EDGE_PREFIX = "greenhouse_grid_edge_"

    /** Thin panel thickness in logical meters (inherits root scale). */
    private const val PANEL_THICK = 0.02f

    /** Structural beam thickness in logical meters. */
    private const val BEAM_THICK = 0.04f

    /** Floor grid line thickness. */
    private const val GRID_THICK = 0.012f

    fun clearFrame(nodes: MutableList<Node>) {
        nodes.toList()
            .filter { it.name == FRAME_ROOT_NAME || it.name?.startsWith("greenhouse_frame") == true }
            .forEach { node ->
                node.destroy()
                nodes -= node
            }
    }

    /**
     * @param includeStructure when true, attaches translucent panels + frame + grid
     *   matching the Virtual Greenhouse Preview layout.
     */
    fun buildFrame(
        engine: Engine,
        pose: ArGreenhousePose,
        physical: GreenhousePhysicalConfig,
        session: Session? = null,
        includeStructure: Boolean = true,
        floorMaterial: MaterialInstance? = null,
        wallMaterial: MaterialInstance? = null,
        roofMaterial: MaterialInstance? = null,
        frameMaterial: MaterialInstance? = null,
        gridMaterial: MaterialInstance? = null
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
        // Uniform tabletop display scale — logical children stay in meters.
        root.setScale(ArRealScale.rootScale(physical))

        if (includeStructure) {
            val floorMat = floorMaterial
            val wallMat = wallMaterial
            val roofMat = roofMaterial
            val frameMat = frameMaterial
            val gridMat = gridMaterial
            if (floorMat != null && wallMat != null && roofMat != null) {
                attachStructurePanels(root, engine, length, width, height, floorMat, wallMat, roofMat)
            }
            if (frameMat != null) {
                ArGreenhouseFrameGeometry.structureFrameEdges(length, width, height)
                    .forEachIndexed { index, edge ->
                        addLocalEdge(root, engine, edge, frameMat, FRAME_EDGE_PREFIX, index, BEAM_THICK)
                    }
            }
            if (gridMat != null) {
                ArGreenhouseFrameGeometry.floorGridEdges(
                    length,
                    width,
                    physical.cellSizeMeters.toFloat()
                ).forEachIndexed { index, edge ->
                    addLocalEdge(root, engine, edge, gridMat, GRID_EDGE_PREFIX, index, GRID_THICK)
                }
            }
        }
        return root
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

    private fun attachStructurePanels(
        root: Node,
        engine: Engine,
        length: Float,
        width: Float,
        height: Float,
        floorMaterial: MaterialInstance,
        wallMaterial: MaterialInstance,
        roofMaterial: MaterialInstance
    ) {
        ArGreenhouseFrameGeometry.structurePanels(length, width, height).forEachIndexed { index, panel ->
            val material = when (panel.kind) {
                StructurePanelKind.FLOOR -> floorMaterial
                StructurePanelKind.WALL -> wallMaterial
                StructurePanelKind.ROOF -> roofMaterial
            }
            // Thin box: panel.width × PANEL_THICK × panel.height, local +Y = panel normal.
            val node = CubeNode(
                engine = engine,
                size = Size(panel.width, PANEL_THICK, panel.height),
                center = Position(0f, 0f, 0f),
                materialInstance = material
            ).apply {
                name = STRUCTURE_PREFIX + panel.kind.name.lowercase() + "_$index"
                isEditable = false
                position = Position(panel.center.x, panel.center.y, panel.center.z)
                quaternion = quaternionAlignYTo(
                    Direction(panel.normalX, panel.normalY, panel.normalZ)
                )
            }
            // Guard: reject accidental full-volume cuboid (L×H×W).
            val sx = panel.width
            val sy = PANEL_THICK
            val sz = panel.height
            check(
                !ArCoverageNodes.isFullGreenhouseCuboid(sx, sy, sz, length, width, height)
            ) { "Refusing full greenhouse cuboid panel" }
            root.addChildNode(node)
        }
    }

    private fun addLocalEdge(
        root: Node,
        engine: Engine,
        edge: ArLocalEdge,
        material: MaterialInstance,
        prefix: String,
        index: Int,
        thick: Float
    ) {
        val dx = edge.b.x - edge.a.x
        val dy = edge.b.y - edge.a.y
        val dz = edge.b.z - edge.a.z
        val len = sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(0.01f)
        val dominantAxes =
            listOf(abs(dx) > 0.02f, abs(dy) > 0.02f, abs(dz) > 0.02f).count { it }

        if (dominantAxes <= 1) {
            val mx = (edge.a.x + edge.b.x) * 0.5f
            val my = (edge.a.y + edge.b.y) * 0.5f
            val mz = (edge.a.z + edge.b.z) * 0.5f
            val (sx, sy, sz) = when {
                abs(dx) >= abs(dy) && abs(dx) >= abs(dz) -> Triple(len, thick, thick)
                abs(dy) >= abs(dx) && abs(dy) >= abs(dz) -> Triple(thick, len, thick)
                else -> Triple(thick, thick, len)
            }
            root.addChildNode(
                CubeNode(
                    engine = engine,
                    size = Size(sx, sy, sz),
                    center = Position(mx, my, mz),
                    materialInstance = material
                ).apply {
                    name = prefix + index
                    isEditable = false
                }
            )
        } else {
            // Diagonal (roof spokes): bead chain along the edge.
            val segments = 8
            for (i in 0..segments) {
                val t = i / segments.toFloat()
                root.addChildNode(
                    CubeNode(
                        engine = engine,
                        size = Size(thick, thick, thick),
                        center = Position(
                            edge.a.x + dx * t,
                            edge.a.y + dy * t,
                            edge.a.z + dz * t
                        ),
                        materialInstance = material
                    ).apply {
                        name = "${prefix}${index}_$i"
                        isEditable = false
                    }
                )
            }
        }
    }

    /** Rotate local +Y onto [targetNormal]. */
    internal fun quaternionAlignYTo(targetNormal: Direction): Quaternion {
        val from = Direction(0f, 1f, 0f)
        val to = normalize(targetNormal)
        val d = dot(from, to).coerceIn(-1f, 1f)
        if (d > 0.9999f) return Quaternion()
        if (d < -0.9999f) {
            val axis = normalize(
                cross(Direction(1f, 0f, 0f), from).let { a ->
                    if (length(a) < 1e-4f) cross(Direction(0f, 0f, 1f), from) else a
                }
            )
            return Quaternion(axis.x, axis.y, axis.z, 0f)
        }
        val c = cross(from, to)
        val s = sqrt((1f + d) * 2f)
        return Quaternion(c.x / s, c.y / s, c.z / s, s * 0.5f)
    }

    /** Theme colors used when callers build materials for structure. */
    object StructureColors {
        val floor = NightBorder.copy(alpha = 1f)
        val wall = ForestEmerald
        val roof = ForestEmerald
        val frame = ForestEmerald
        val grid = NightBorder
    }
}
