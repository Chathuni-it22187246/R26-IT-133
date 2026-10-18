package com.greenhands.app.equipment.domain

import com.greenhands.app.equipment.model.DomainAabb
import com.greenhands.app.equipment.model.EquipmentInstance
import com.greenhands.app.equipment.model.EquipmentPose
import com.greenhands.app.equipment.model.EquipmentType
import com.greenhands.app.equipment.model.ModelDimensions
import com.greenhands.app.equipment.model.PlacementValidity
import com.greenhands.app.preview3d.domain.CropRowGeometry
import com.greenhands.app.preview3d.domain.DomainCropRowBox
import kotlin.math.abs
import kotlin.math.min

data class PlacementContext(
    val lengthM: Double,
    val widthM: Double,
    val heightM: Double,
    val cropRowCount: Int,
    val canopyHeightM: Double
) {
    val cropBoxes: List<DomainCropRowBox> = CropRowGeometry.domainBoxes(
        lengthM, widthM, heightM, cropRowCount
    )

    val floorAreaM2: Double get() = lengthM * widthM
    val volumeM3: Double get() = lengthM * widthM * heightM
}

/**
 * Deterministic placement rules. Independent of Compose and SceneView.
 * Numeric clearances are PROJECT_ASSUMPTION unless noted.
 */
object PlacementValidator {
    const val WALL_INSET_M = 0.08
    const val EQUIPMENT_GAP_M = 0.05
    const val FLOOR_CLEARANCE_M = 0.25
    const val CANOPY_CLEARANCE_M = 0.20
    const val ROOF_CLEARANCE_M = 0.15
    const val EXHAUST_WALL_TOLERANCE_M = 0.04
    const val EXHAUST_OUTSIDE_ALLOWANCE_M = 0.06
    const val INWARD_YAW_DOT_LIMIT = -0.15

    fun validate(
        instance: EquipmentInstance,
        others: List<EquipmentInstance>,
        context: PlacementContext
    ): PlacementValidity {
        val reasons = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val box = EquipmentPose.worldAabb(instance)
        when (instance.type) {
            EquipmentType.CIRCULATION_FAN -> validateCirculation(box, context, reasons)
            EquipmentType.EXHAUST_FAN -> validateExhaust(instance, box, context, reasons, warnings)
            EquipmentType.FOGGER -> validateFogger(box, context, reasons)
        }
        if (overlapsCrops(box, context)) {
            reasons += "Intersects a crop row. Move the device into an aisle or above the canopy."
        }
        others.filter { it.id != instance.id && !it.isGhost }.forEach { other ->
            val otherBox = EquipmentPose.worldAabb(other)
            if (box.intersects(otherBox, EQUIPMENT_GAP_M)) {
                reasons += "Intersects another device (${other.displayLabel()})."
            }
        }
        return if (reasons.isEmpty()) {
            if (warnings.isEmpty()) PlacementValidity.VALID
            else PlacementValidity(true, warnings = warnings)
        } else {
            PlacementValidity(false, reasons, warnings)
        }
    }

    fun snapExhaust(instance: EquipmentInstance, context: PlacementContext): EquipmentInstance {
        if (instance.type != EquipmentType.EXHAUST_FAN) return instance
        val far = instance.positionM.yM >= context.lengthM * 0.5
        val y = if (far) context.lengthM else 0.0
        val yaw = if (far) 90.0 else 270.0
        val x = instance.positionM.xM.coerceIn(
            instance.dimensions.widthM * 0.5 + WALL_INSET_M,
            context.widthM - instance.dimensions.widthM * 0.5 - WALL_INSET_M
        )
        val z = instance.positionM.zM.coerceIn(
            instance.dimensions.heightM * 0.5 + FLOOR_CLEARANCE_M,
            context.heightM - instance.dimensions.heightM * 0.5 - ROOF_CLEARANCE_M
        )
        return instance.copy(positionM = instance.positionM.copy(xM = x, yM = y, zM = z), yawDeg = yaw)
    }

    fun clampOverhead(instance: EquipmentInstance, context: PlacementContext): EquipmentInstance {
        if (instance.type != EquipmentType.FOGGER) return instance
        val range = elevationRange(instance.type, context, instance.dimensions)
        val z = instance.positionM.zM.coerceIn(range.start, range.endInclusive)
        return instance.copy(positionM = instance.positionM.copy(zM = z))
    }

    fun clampElevation(instance: EquipmentInstance, context: PlacementContext): EquipmentInstance {
        val range = elevationRange(instance.type, context, instance.dimensions)
        val z = instance.positionM.zM.coerceIn(range.start, range.endInclusive)
        return instance.copy(positionM = instance.positionM.copy(zM = z))
    }

    fun overheadRange(context: PlacementContext): ClosedFloatingPointRange<Double> {
        val minZ = context.canopyHeightM + CANOPY_CLEARANCE_M
        val maxZ = context.heightM - ROOF_CLEARANCE_M
        return if (maxZ >= minZ) minZ..maxZ else minZ..minZ
    }

    fun elevationRange(
        type: EquipmentType,
        context: PlacementContext,
        dimensions: ModelDimensions
    ): ClosedFloatingPointRange<Double> {
        return when (type) {
            EquipmentType.FOGGER -> overheadRange(context)
            EquipmentType.CIRCULATION_FAN -> overheadRange(context)
            EquipmentType.EXHAUST_FAN -> {
                val minZ = dimensions.heightM * 0.5 + FLOOR_CLEARANCE_M
                val maxZ = context.heightM - dimensions.heightM * 0.5 - ROOF_CLEARANCE_M
                if (maxZ >= minZ) minZ..maxZ else minZ..minZ
            }
        }
    }

    fun overheadZ(context: PlacementContext): Double {
        val range = overheadRange(context)
        return if (range.endInclusive > range.start) {
            (range.start + range.endInclusive) * 0.5
        } else {
            min(range.start, context.heightM * 0.85)
        }
    }

    fun circulationZ(context: PlacementContext): Double {
        val minZ = context.canopyHeightM + CANOPY_CLEARANCE_M
        val maxZ = context.heightM - ROOF_CLEARANCE_M
        return if (maxZ > minZ) minZ + (maxZ - minZ) * 0.35 else minZ
    }

    private fun validateCirculation(box: DomainAabb, context: PlacementContext, reasons: MutableList<String>) {
        if (!insideEnvelope(box, context, outsideAllowanceM = 0.0)) {
            reasons += "Circulation fan must stay inside the greenhouse envelope."
        }
        if (box.minZM < FLOOR_CLEARANCE_M) {
            reasons += "Circulation fan is below the floor clearance of ${FLOOR_CLEARANCE_M} m."
        }
        if (box.minZM < context.canopyHeightM) {
            reasons += "Circulation fan intersects the crop canopy height."
        }
        if (box.maxZM > context.heightM - 1e-6) {
            reasons += "Circulation fan intersects the roof."
        }
    }

    private fun validateExhaust(
        instance: EquipmentInstance,
        box: DomainAabb,
        context: PlacementContext,
        reasons: MutableList<String>,
        warnings: MutableList<String>
    ) {
        val onNear = abs(instance.positionM.yM - 0.0) <= EXHAUST_WALL_TOLERANCE_M
        val onFar = abs(instance.positionM.yM - context.lengthM) <= EXHAUST_WALL_TOLERANCE_M
        if (!onNear && !onFar) {
            reasons += "Exhaust fans must mount on an end wall, not float in the greenhouse."
        }
        if (!insideEnvelope(box, context, outsideAllowanceM = EXHAUST_OUTSIDE_ALLOWANCE_M)) {
            reasons += "Exhaust fan frame is outside the end-wall mounting envelope."
        }
        val outward = if (onFar || instance.positionM.yM >= context.lengthM * 0.5) 1.0 else -1.0
        val forwardY = kotlin.math.sin(Math.toRadians(instance.yawDeg))
        if (forwardY * outward < INWARD_YAW_DOT_LIMIT) {
            warnings += "Exhaust orientation faces inward. Exhaust should discharge outdoors. " +
                "Prevailing wind and inlet/louver design also affect performance."
        }
        warnings += "Make-up air is assumed to enter from the opposite end wall. " +
            "Real inlet area and outdoor wind are not modelled in this placement phase."
    }

    private fun validateFogger(box: DomainAabb, context: PlacementContext, reasons: MutableList<String>) {
        if (!insideEnvelope(box, context, outsideAllowanceM = 0.0)) {
            reasons += "Fogger must stay inside the greenhouse envelope."
        }
        if (box.minZM < context.canopyHeightM + CANOPY_CLEARANCE_M * 0.5) {
            reasons += "Fogger must sit on the overhead plane above the crop canopy."
        }
        if (box.maxZM > context.heightM - 1e-6) {
            reasons += "Fogger intersects the roof."
        }
        if (box.minXM < WALL_INSET_M || box.maxXM > context.widthM - WALL_INSET_M ||
            box.minYM < WALL_INSET_M || box.maxYM > context.lengthM - WALL_INSET_M
        ) {
            reasons += "Fogger is too close to a wall and may increase wall wetting."
        }
    }

    private fun insideEnvelope(box: DomainAabb, context: PlacementContext, outsideAllowanceM: Double): Boolean {
        val a = outsideAllowanceM
        return box.minXM >= -a && box.maxXM <= context.widthM + a &&
            box.minYM >= -a && box.maxYM <= context.lengthM + a &&
            box.minZM >= -0.02 && box.maxZM <= context.heightM + a
    }

    private fun overlapsCrops(box: DomainAabb, context: PlacementContext): Boolean {
        return context.cropBoxes.any { crop ->
            val cropBox = DomainAabb(crop.minXM, crop.maxXM, crop.minYM, crop.maxYM, crop.minZM, crop.maxZM)
            box.intersects(cropBox, gapM = 0.0)
        }
    }
}
