package com.greenhands.app.sensor.ar

import com.greenhands.app.sensor.model.GreenhousePhysicalConfig
import kotlin.math.cos
import kotlin.math.sin

/**
 * Pure greenhouse frame geometry for Real AR (Phase 10E-E).
 * Dimensions come from [GreenhousePhysicalConfig] / snapshot.physical — never hard-coded.
 *
 * Local corners use X=length, Y=height, Z=width (1 unit = 1 m).
 * World mapping uses [ArWorldMapper] so tests do not need SceneView/ARCore.
 */
data class ArLocalPoint(val x: Float, val y: Float, val z: Float)

data class ArLocalEdge(val a: ArLocalPoint, val b: ArLocalPoint)

/** Oriented rectangular panel in greenhouse-local meters (matches Virtual Greenhouse quads). */
data class ArLocalPanel(
    val center: ArLocalPoint,
    val width: Float,
    val height: Float,
    val normalX: Float,
    val normalY: Float,
    val normalZ: Float,
    val kind: StructurePanelKind
)

enum class StructurePanelKind {
    FLOOR,
    WALL,
    ROOF
}

object ArGreenhouseFrameGeometry {

    /** AR floor tint — much lower than Virtual preview so the real floor stays visible. */
    const val AR_FLOOR_ALPHA = 0.18f

    /** Matches [VirtualGreenhouseRenderer] wall fill alpha. */
    const val AR_WALL_ALPHA = 0.10f

    /** Matches [VirtualGreenhouseRenderer] roof fill alpha. */
    const val AR_ROOF_ALPHA = 0.14f

    private const val GRID_Y = 0.03f

    const val ROOF_PEAK_FACTOR = 1.22f

    /** Frame is only drawn once origin + yaw are established. */
    fun shouldShowFrame(phase: ArOriginPlacementPhase): Boolean =
        phase == ArOriginPlacementPhase.ALIGNED

    fun localCorners(
        lengthM: Float,
        widthM: Float,
        heightM: Float
    ): List<ArLocalPoint> {
        val l = lengthM
        val w = widthM
        val h = heightM
        val ridgeY = h * ROOF_PEAK_FACTOR
        return listOf(
            // Floor
            ArLocalPoint(0f, 0f, 0f),
            ArLocalPoint(l, 0f, 0f),
            ArLocalPoint(l, 0f, w),
            ArLocalPoint(0f, 0f, w),
            // Eave
            ArLocalPoint(0f, h, 0f),
            ArLocalPoint(l, h, 0f),
            ArLocalPoint(l, h, w),
            ArLocalPoint(0f, h, w),
            // Ridge
            ArLocalPoint(l * 0.5f, ridgeY, w * 0.5f)
        )
    }

    fun localEdges(
        lengthM: Float,
        widthM: Float,
        heightM: Float
    ): List<ArLocalEdge> = structureFrameEdges(lengthM, widthM, heightM)

    /**
     * Semi-transparent structure panels aligned with [VirtualGreenhouseRenderer] floor/walls/roof.
     * Uses true alpha (&lt; 1) so SceneView selects transparent_colored.filamat — never opaque
     * full-footprint cubes that block the camera feed.
     */
    fun structurePanels(
        lengthM: Float,
        widthM: Float,
        heightM: Float
    ): List<ArLocalPanel> {
        val l = lengthM
        val w = widthM
        val h = heightM
        val ridgeY = h * ROOF_PEAK_FACTOR
        val slopeAlongWidth = kotlin.math.sqrt((ridgeY - h) * (ridgeY - h) + (w * 0.5f) * (w * 0.5f))
        val slopeAlongLength = kotlin.math.sqrt((ridgeY - h) * (ridgeY - h) + (l * 0.5f) * (l * 0.5f))
        val panels = mutableListOf<ArLocalPanel>()
        panels += panel(
            center = ArLocalPoint(l * 0.5f, 0.002f, w * 0.5f),
            width = l,
            height = w,
            normalX = 0f,
            normalY = 1f,
            normalZ = 0f,
            kind = StructurePanelKind.FLOOR
        )
        panels += panel(
            center = ArLocalPoint(l * 0.5f, h * 0.5f, 0f),
            width = l,
            height = h,
            normalX = 0f,
            normalY = 0f,
            normalZ = 1f,
            kind = StructurePanelKind.WALL
        )
        panels += panel(
            center = ArLocalPoint(l * 0.5f, h * 0.5f, w),
            width = l,
            height = h,
            normalX = 0f,
            normalY = 0f,
            normalZ = -1f,
            kind = StructurePanelKind.WALL
        )
        panels += panel(
            center = ArLocalPoint(0f, h * 0.5f, w * 0.5f),
            width = w,
            height = h,
            normalX = 1f,
            normalY = 0f,
            normalZ = 0f,
            kind = StructurePanelKind.WALL
        )
        panels += panel(
            center = ArLocalPoint(l, h * 0.5f, w * 0.5f),
            width = w,
            height = h,
            normalX = -1f,
            normalY = 0f,
            normalZ = 0f,
            kind = StructurePanelKind.WALL
        )
        panels += roofSlopePanel(
            center = ArLocalPoint(l * 0.5f, h + (ridgeY - h) * 0.5f, w * 0.25f),
            width = l,
            slopeLength = slopeAlongWidth,
            normalX = 0f,
            normalY = -w * 0.5f,
            normalZ = ridgeY - h
        )
        panels += roofSlopePanel(
            center = ArLocalPoint(l * 0.5f, h + (ridgeY - h) * 0.5f, w * 0.75f),
            width = l,
            slopeLength = slopeAlongWidth,
            normalX = 0f,
            normalY = w * 0.5f,
            normalZ = ridgeY - h
        )
        panels += roofSlopePanel(
            center = ArLocalPoint(l * 0.25f, h + (ridgeY - h) * 0.5f, w * 0.5f),
            width = w,
            slopeLength = slopeAlongLength,
            normalX = w * (ridgeY - h),
            normalY = -l * w * 0.5f,
            normalZ = 0f
        )
        panels += roofSlopePanel(
            center = ArLocalPoint(l * 0.75f, h + (ridgeY - h) * 0.5f, w * 0.5f),
            width = w,
            slopeLength = slopeAlongLength,
            normalX = -w * (ridgeY - h),
            normalY = -l * w * 0.5f,
            normalZ = 0f
        )
        return panels
    }

    /** Floor grid lines at cell spacing — same layout as Virtual Greenhouse. */
    fun floorGridEdges(
        lengthM: Float,
        widthM: Float,
        cellSizeM: Float
    ): List<ArLocalEdge> {
        val cell = cellSizeM.coerceAtLeast(0.05f)
        val edges = mutableListOf<ArLocalEdge>()
        var x = 0f
        while (x <= lengthM + 0.01f) {
            val cx = x.coerceAtMost(lengthM)
            edges += ArLocalEdge(
                ArLocalPoint(cx, GRID_Y, 0f),
                ArLocalPoint(cx, GRID_Y, widthM)
            )
            x += cell
        }
        var z = 0f
        while (z <= widthM + 0.01f) {
            val cz = z.coerceAtMost(widthM)
            edges += ArLocalEdge(
                ArLocalPoint(0f, GRID_Y, cz),
                ArLocalPoint(lengthM, GRID_Y, cz)
            )
            z += cell
        }
        return edges
    }

    /** Full structural frame (posts, eave, mid studs, roof spokes) — matches Virtual preview. */
    fun structureFrameEdges(
        lengthM: Float,
        widthM: Float,
        heightM: Float
    ): List<ArLocalEdge> = fullDiagnosticEdges(lengthM, widthM, heightM)

    private fun panel(
        center: ArLocalPoint,
        width: Float,
        height: Float,
        normalX: Float,
        normalY: Float,
        normalZ: Float,
        kind: StructurePanelKind
    ): ArLocalPanel {
        val (nx, ny, nz) = normalizeNormal(normalX, normalY, normalZ)
        return ArLocalPanel(center, width, height, nx, ny, nz, kind)
    }

    private fun roofSlopePanel(
        center: ArLocalPoint,
        width: Float,
        slopeLength: Float,
        normalX: Float,
        normalY: Float,
        normalZ: Float
    ): ArLocalPanel {
        val (nx, ny, nz) = normalizeNormal(normalX, normalY, normalZ)
        return ArLocalPanel(center, width, slopeLength, nx, ny, nz, StructurePanelKind.ROOF)
    }

    private fun normalizeNormal(x: Float, y: Float, z: Float): Triple<Float, Float, Float> {
        val len = kotlin.math.sqrt(x * x + y * y + z * z).coerceAtLeast(1e-6f)
        return Triple(x / len, y / len, z / len)
    }

    /**
     * Lightweight optional greenhouse guide — no floor/roof panels, no mid studs.
     * Floor rectangle, corner posts, eave ring, optional roof spokes only.
     */
    fun guideEdges(
        lengthM: Float,
        widthM: Float,
        heightM: Float,
        includeRoofGuides: Boolean = true
    ): List<ArLocalEdge> {
        val c = localCorners(lengthM, widthM, heightM)
        val f0 = c[0]
        val f1 = c[1]
        val f2 = c[2]
        val f3 = c[3]
        val e0 = c[4]
        val e1 = c[5]
        val e2 = c[6]
        val e3 = c[7]
        val ridge = c[8]
        val edges = mutableListOf(
            // Floor perimeter
            ArLocalEdge(f0, f1),
            ArLocalEdge(f1, f2),
            ArLocalEdge(f2, f3),
            ArLocalEdge(f3, f0),
            // Corner posts
            ArLocalEdge(f0, e0),
            ArLocalEdge(f1, e1),
            ArLocalEdge(f2, e2),
            ArLocalEdge(f3, e3),
            // Eave ring
            ArLocalEdge(e0, e1),
            ArLocalEdge(e1, e2),
            ArLocalEdge(e2, e3),
            ArLocalEdge(e3, e0)
        )
        if (includeRoofGuides) {
            edges += listOf(
                ArLocalEdge(e0, ridge),
                ArLocalEdge(e1, ridge),
                ArLocalEdge(e2, ridge),
                ArLocalEdge(e3, ridge)
            )
        }
        return edges
    }

    private fun fullDiagnosticEdges(
        lengthM: Float,
        widthM: Float,
        heightM: Float
    ): List<ArLocalEdge> {
        val c = localCorners(lengthM, widthM, heightM)
        val f0 = c[0]
        val f1 = c[1]
        val f2 = c[2]
        val f3 = c[3]
        val e0 = c[4]
        val e1 = c[5]
        val e2 = c[6]
        val e3 = c[7]
        val ridge = c[8]
        return listOf(
            // Floor
            ArLocalEdge(f0, f1),
            ArLocalEdge(f1, f2),
            ArLocalEdge(f2, f3),
            ArLocalEdge(f3, f0),
            // Vertical posts
            ArLocalEdge(f0, e0),
            ArLocalEdge(f1, e1),
            ArLocalEdge(f2, e2),
            ArLocalEdge(f3, e3),
            // Eave ring
            ArLocalEdge(e0, e1),
            ArLocalEdge(e1, e2),
            ArLocalEdge(e2, e3),
            ArLocalEdge(e3, e0),
            // Mid studs (length + width) — matches VirtualGreenhouseRenderer
            ArLocalEdge(
                ArLocalPoint(lengthM * 0.5f, 0f, 0f),
                ArLocalPoint(lengthM * 0.5f, heightM, 0f)
            ),
            ArLocalEdge(
                ArLocalPoint(lengthM * 0.5f, 0f, widthM),
                ArLocalPoint(lengthM * 0.5f, heightM, widthM)
            ),
            ArLocalEdge(
                ArLocalPoint(0f, 0f, widthM * 0.5f),
                ArLocalPoint(0f, heightM, widthM * 0.5f)
            ),
            ArLocalEdge(
                ArLocalPoint(lengthM, 0f, widthM * 0.5f),
                ArLocalPoint(lengthM, heightM, widthM * 0.5f)
            ),
            // Roof spokes (corners + mid-eave)
            ArLocalEdge(e0, ridge),
            ArLocalEdge(e1, ridge),
            ArLocalEdge(e2, ridge),
            ArLocalEdge(e3, ridge),
            ArLocalEdge(
                ArLocalPoint(lengthM * 0.5f, heightM, 0f),
                ridge
            ),
            ArLocalEdge(
                ArLocalPoint(lengthM * 0.5f, heightM, widthM),
                ridge
            )
        )
    }

    /** @deprecated Internal — use [guideEdges] for Real AR rendering. */
    @Suppress("unused")
    fun legacyLocalEdgesWithMidStuds(
        lengthM: Float,
        widthM: Float,
        heightM: Float
    ): List<ArLocalEdge> = fullDiagnosticEdges(lengthM, widthM, heightM)

    fun worldCorners(
        pose: ArGreenhousePose,
        physical: GreenhousePhysicalConfig
    ): List<ArWorldMapper.WorldPoint>? {
        if (!shouldShowFrame(pose.phase)) return null
        val l = physical.lengthMeters.toFloat()
        val w = physical.widthMeters.toFloat()
        val h = physical.heightMeters.toFloat()
        return localCorners(l, w, h).map { p ->
            ArWorldMapper.localToWorld(pose, p.x, p.y, p.z) ?: return null
        }
    }

    fun worldEdges(
        pose: ArGreenhousePose,
        physical: GreenhousePhysicalConfig
    ): List<Pair<ArWorldMapper.WorldPoint, ArWorldMapper.WorldPoint>>? {
        if (!shouldShowFrame(pose.phase)) return null
        val l = physical.lengthMeters.toFloat()
        val w = physical.widthMeters.toFloat()
        val h = physical.heightMeters.toFloat()
        return localEdges(l, w, h).map { edge ->
            val a = ArWorldMapper.localToWorld(pose, edge.a.x, edge.a.y, edge.a.z) ?: return null
            val b = ArWorldMapper.localToWorld(pose, edge.b.x, edge.b.y, edge.b.z) ?: return null
            a to b
        }
    }

    /**
     * ARCore/SceneView Y-up quaternion for yaw around vertical (x,y,z,w).
     * Matches [ArWorldMapper] yawRadians = atan2(forwardZ, forwardX).
     */
    fun yawQuaternionXyZw(yawRadians: Float): FloatArray {
        val half = yawRadians * 0.5f
        return floatArrayOf(0f, sin(half), 0f, cos(half))
    }

    fun dimensionLine(physical: GreenhousePhysicalConfig): String =
        "${formatMeters(physical.lengthMeters)}m × " +
            "${formatMeters(physical.widthMeters)}m × " +
            "${formatMeters(physical.heightMeters)}m"

    fun cellSizeLine(physical: GreenhousePhysicalConfig): String =
        "${formatMeters(physical.cellSizeMeters)}m"

    private fun formatMeters(value: Double): String =
        String.format(java.util.Locale.US, "%.1f", value)
}
