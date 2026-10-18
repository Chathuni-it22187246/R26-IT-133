package com.greenhands.app.equipment.domain

import com.greenhands.app.equipment.model.DomainPoint
import com.greenhands.app.equipment.model.EquipmentInstance
import com.greenhands.app.equipment.model.EquipmentType

enum class PlacementAxis { X, Y, Z }

data class AxisSliderLimit(
    val axis: PlacementAxis,
    val enabled: Boolean,
    val minM: Double,
    val maxM: Double,
    val lockReason: String? = null
) {
    val hasSpan: Boolean get() = enabled && maxM > minM + 1e-6
}

/**
 * Continuous legal travel along one user-facing axis from the current pose.
 * Uses [PlacementValidator] so equipment size, wall/floor/roof clearance, crop rows,
 * other devices and type-specific mounting all bound the slider.
 */
object PlacementAxisLimits {
    const val STEP_M = 0.05
    const val EXHAUST_Y_LOCK =
        "Y is locked to this end wall. Exhaust fans cannot move along the greenhouse length."

    fun forAxis(
        instance: EquipmentInstance,
        others: List<EquipmentInstance>,
        context: PlacementContext,
        axis: PlacementAxis
    ): AxisSliderLimit {
        if (instance.type == EquipmentType.EXHAUST_FAN && axis == PlacementAxis.Y) {
            val y = instance.positionM.yM
            return AxisSliderLimit(axis, enabled = false, minM = y, maxM = y, lockReason = EXHAUST_Y_LOCK)
        }
        val minBound = 0.0
        val maxBound = when (axis) {
            PlacementAxis.X -> context.widthM
            PlacementAxis.Y -> context.lengthM
            PlacementAxis.Z -> context.heightM
        }
        val start = read(instance.positionM, axis).coerceIn(minBound, maxBound)
        val peers = others.filter { it.id != instance.id && !it.isGhost }
        fun validAt(value: Double): Boolean {
            if (value < minBound - 1e-9 || value > maxBound + 1e-9) return false
            val trial = instance.copy(positionM = write(instance.positionM, axis, value))
            return PlacementValidator.validate(trial, peers, context).isValid
        }
        if (!validAt(start)) {
            return AxisSliderLimit(axis, enabled = true, minM = minBound, maxM = maxBound)
        }
        var lo = start
        var cursor = start - STEP_M
        while (cursor >= minBound && validAt(cursor)) {
            lo = cursor
            cursor -= STEP_M
        }
        var hi = start
        cursor = start + STEP_M
        while (cursor <= maxBound && validAt(cursor)) {
            hi = cursor
            cursor += STEP_M
        }
        return AxisSliderLimit(axis, enabled = hi > lo + 1e-9, minM = lo, maxM = hi)
    }

    private fun read(point: DomainPoint, axis: PlacementAxis): Double = when (axis) {
        PlacementAxis.X -> point.xM
        PlacementAxis.Y -> point.yM
        PlacementAxis.Z -> point.zM
    }

    private fun write(point: DomainPoint, axis: PlacementAxis, value: Double): DomainPoint = when (axis) {
        PlacementAxis.X -> point.copy(xM = value)
        PlacementAxis.Y -> point.copy(yM = value)
        PlacementAxis.Z -> point.copy(zM = value)
    }
}
