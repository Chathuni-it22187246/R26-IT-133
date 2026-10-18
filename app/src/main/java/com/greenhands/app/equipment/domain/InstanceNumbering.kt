package com.greenhands.app.equipment.domain

import com.greenhands.app.equipment.model.EquipmentInstance
import com.greenhands.app.equipment.model.EquipmentType

/**
 * Stable per-type display numbers. UUID remains the identity; numbers are user-facing only.
 * Deleting number 1 does not rename number 2. New items take the lowest unused integer.
 */
object InstanceNumbering {
    fun nextNumber(type: EquipmentType, existing: List<EquipmentInstance>): Int {
        val used = existing.filter { it.type == type && it.displayNumber > 0 }
            .map { it.displayNumber }
            .toSet()
        var n = 1
        while (n in used) n++
        return n
    }

    /**
     * Assigns missing/zero numbers in list order, per type. Existing numbers are kept.
     * Used when decoding records saved before displayNumber existed.
     */
    fun assignMissing(instances: List<EquipmentInstance>): List<EquipmentInstance> {
        val result = instances.toMutableList()
        EquipmentType.entries.forEach { type ->
            val used = result.filter { it.type == type && it.displayNumber > 0 }
                .map { it.displayNumber }
                .toMutableSet()
            var next = 1
            for (i in result.indices) {
                val inst = result[i]
                if (inst.type != type || inst.displayNumber > 0) continue
                while (next in used) next++
                result[i] = inst.copy(displayNumber = next)
                used += next
                next++
            }
        }
        return result
    }
}
