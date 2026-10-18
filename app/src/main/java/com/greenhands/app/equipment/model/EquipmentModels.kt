package com.greenhands.app.equipment.model

import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/**
 * Greenhouse-domain coordinates (user-facing placement frame, schema v2):
 * X across width, Y along length, Z height above floor, origin at the front-left floor corner.
 * Values are metres. SceneView Y-up is never shown to the user.
 */
data class DomainPoint(
    val xM: Double,
    val yM: Double,
    val zM: Double
) {
    init {
        require(xM.isFinite() && yM.isFinite() && zM.isFinite()) {
            "Domain coordinates must be finite metres"
        }
    }
}

data class ScenePoint(
    val x: Double,
    val y: Double,
    val z: Double
)

data class ModelDimensions(
    val widthM: Double,
    val depthM: Double,
    val heightM: Double
) {
    init {
        require(widthM > 0.0 && widthM.isFinite()) { "widthM must be finite and > 0 m" }
        require(depthM > 0.0 && depthM.isFinite()) { "depthM must be finite and > 0 m" }
        require(heightM > 0.0 && heightM.isFinite()) { "heightM must be finite and > 0 m" }
    }
}

/** Model-space AABB in metres (glTF Y-up, +Z forward). */
data class LocalAabb(
    val minX: Double,
    val minY: Double,
    val minZ: Double,
    val maxX: Double,
    val maxY: Double,
    val maxZ: Double
) {
    fun corners(): List<Triple<Double, Double, Double>> = listOf(
        Triple(minX, minY, minZ),
        Triple(minX, minY, maxZ),
        Triple(minX, maxY, minZ),
        Triple(minX, maxY, maxZ),
        Triple(maxX, minY, minZ),
        Triple(maxX, minY, maxZ),
        Triple(maxX, maxY, minZ),
        Triple(maxX, maxY, maxZ)
    )
}

enum class EquipmentType(val assetFileName: String, val displayName: String) {
    CIRCULATION_FAN("circulation_fan.glb", "Circulation fan"),
    EXHAUST_FAN("exhaust_fan.glb", "Exhaust fan"),
    FOGGER("fogger.glb", "Fogger")
}

object EquipmentLabels {
    fun typeTitle(type: EquipmentType): String = when (type) {
        EquipmentType.CIRCULATION_FAN -> "Circulation Fan"
        EquipmentType.EXHAUST_FAN -> "Exhaust Fan"
        EquipmentType.FOGGER -> "Fogger"
    }

    fun label(type: EquipmentType, displayNumber: Int): String {
        val title = typeTitle(type)
        return if (displayNumber > 0) "$title $displayNumber" else title
    }

    fun deleteAction(type: EquipmentType, displayNumber: Int): String {
        val n = displayNumber.coerceAtLeast(1)
        return when (type) {
            EquipmentType.FOGGER -> "Delete Fogger $n"
            EquipmentType.CIRCULATION_FAN, EquipmentType.EXHAUST_FAN -> "Delete Fan $n"
        }
    }
}

enum class MountingType {
    SUSPENDED_OR_BAY,
    END_WALL,
    OVERHEAD
}

enum class EquipmentRuntimeState {
    NOT_RUNNING,
    ON,
    OFF
}

enum class PreviewInteractionMode {
    VIEW,
    PLACEMENT
}

data class PlacementValidity(
    val isValid: Boolean,
    val reasons: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
) {
    companion object {
        val VALID = PlacementValidity(true)
        fun invalid(vararg reasons: String) = PlacementValidity(false, reasons.toList())
        fun validWithWarnings(vararg warnings: String) =
            PlacementValidity(true, warnings = warnings.toList())
    }
}

data class EquipmentInstance(
    val id: String,
    val type: EquipmentType,
    val positionM: DomainPoint,
    val yawDeg: Double,
    val dimensions: ModelDimensions,
    val localAabb: LocalAabb,
    val assetPath: String,
    val assetVersion: String,
    val mounting: MountingType,
    val validity: PlacementValidity,
    val runtimeState: EquipmentRuntimeState = EquipmentRuntimeState.NOT_RUNNING,
    val isGhost: Boolean = false,
    val fromSuggestion: Boolean = false,
    val displayNumber: Int = 0
) {
    init {
        require(id.isNotBlank()) { "equipment instance id is required" }
        require(yawDeg.isFinite()) { "yawDeg must be finite" }
        require(displayNumber >= 0) { "displayNumber must be >= 0" }
    }

    fun displayLabel(): String = EquipmentLabels.label(type, displayNumber)
}

data class EquipmentLayout(
    val layoutId: String,
    val configurationId: String,
    val userId: String,
    val schemaVersion: Int = SCHEMA_VERSION,
    val lengthM: Double,
    val widthM: Double,
    val heightM: Double,
    val cropRowCount: Int,
    val instances: List<EquipmentInstance> = emptyList(),
    val countOverrideCirculation: Int? = null,
    val countOverrideExhaust: Int? = null,
    val countOverrideFogger: Int? = null,
    val recommendationAssumptions: String? = null,
    val origin: LayoutOrigin = LayoutOrigin.UNSPECIFIED,
    val explicitlySaved: Boolean = false,
    val coolingPadPresent: Boolean? = null,
    val coolingPadWidthM: Double? = null,
    val coolingPadHeightM: Double? = null,
    val coolingPadEfficiencyFraction: Double? = null,
    val coolingPadWetActive: Boolean? = null,
    val coolingPadWall: String? = null,
    val circulationRatedCfm: Double? = null,
    val circulationRatedM3s: Double? = null,
    val circulationThrowRadiusM: Double? = null,
    val exhaustRatedAirflowM3s: Double? = null,
    val exhaustRatedAirflowCfm: Double? = null,
    val foggerWaterFlowKgS: Double? = null,
    val foggerWaterFlowLph: Double? = null,
    val foggerSprayRadiusM: Double? = null,
    val foggerEvaporationEfficiency: Double? = null
) {
    fun resolvedOrigin(): LayoutOrigin {
        if (origin != LayoutOrigin.UNSPECIFIED) return origin
        if (instances.isEmpty()) return LayoutOrigin.MANUAL
        return if (instances.all { it.fromSuggestion }) {
            LayoutOrigin.LEGACY_AUTOMATIC
        } else {
            LayoutOrigin.MANUAL
        }
    }

    /**
     * Restore only user-saved manual or applied-suggestion layouts.
     * Automatically generated Phase 3B.2 records stay classified, not placed.
     */
    fun shouldRestoreAsPlaced(): Boolean {
        val resolved = resolvedOrigin()
        if (resolved == LayoutOrigin.LEGACY_AUTOMATIC) return false
        if (explicitlySaved) return resolved == LayoutOrigin.MANUAL || resolved == LayoutOrigin.SUGGESTION_APPLIED
        return origin == LayoutOrigin.UNSPECIFIED &&
            resolved == LayoutOrigin.MANUAL &&
            instances.any { !it.fromSuggestion }
    }

    companion object {
        const val SCHEMA_VERSION = 2
    }
}

data class DomainAabb(
    val minXM: Double,
    val maxXM: Double,
    val minYM: Double,
    val maxYM: Double,
    val minZM: Double,
    val maxZM: Double
) {
    fun intersects(other: DomainAabb, gapM: Double = 0.0): Boolean {
        return minXM < other.maxXM - gapM && maxXM > other.minXM + gapM &&
            minYM < other.maxYM - gapM && maxYM > other.minYM + gapM &&
            minZM < other.maxZM - gapM && maxZM > other.minZM + gapM
    }

    fun containsPoint(x: Double, y: Double, z: Double): Boolean =
        x in minXM..maxXM && y in minYM..maxYM && z in minZM..maxZM
}

/**
 * glTF local (X right, Y up, Z forward) → greenhouse domain at a pose.
 * Domain yaw 0° faces +X (across width).
 */
object EquipmentPose {
    fun localToDomain(
        position: DomainPoint,
        yawDeg: Double,
        localX: Double,
        localY: Double,
        localZ: Double
    ): DomainPoint {
        val yaw = Math.toRadians(yawDeg)
        val cos = cos(yaw)
        val sin = sin(yaw)
        val dx = localZ * cos - localX * sin
        val dy = localZ * sin + localX * cos
        val dz = localY
        return DomainPoint(position.xM + dx, position.yM + dy, position.zM + dz)
    }

    fun worldAabb(instance: EquipmentInstance): DomainAabb {
        val xs = ArrayList<Double>(8)
        val ys = ArrayList<Double>(8)
        val zs = ArrayList<Double>(8)
        instance.localAabb.corners().forEach { (lx, ly, lz) ->
            val p = localToDomain(instance.positionM, instance.yawDeg, lx, ly, lz)
            xs += p.xM
            ys += p.yM
            zs += p.zM
        }
        return DomainAabb(xs.min(), xs.max(), ys.min(), ys.max(), zs.min(), zs.max())
    }
}

fun formatMetres2(value: Double): String = String.format(Locale.US, "%.2f", value)

fun formatDegrees1(value: Double): String = String.format(Locale.US, "%.1f", value)
