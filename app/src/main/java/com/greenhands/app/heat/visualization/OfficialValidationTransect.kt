package com.greenhands.app.heat.visualization

import com.greenhands.app.equipment.model.EquipmentType
import com.greenhands.app.heat.simulation.probe.DistanceProbeSampler

/**
 * The only public 1–5 m validation transect is inward from a selected exhaust fan.
 * Circulation mixing and fogger moisture remain in the combined field; they do not
 * get their own user-facing temperature markers.
 */
object OfficialValidationTransect {
    val DISTANCES_M: DoubleArray = DistanceProbeSampler.DEFAULT_DISTANCES_M
    const val SHOWS_CIRCULATION_MARKERS = false
    const val SHOWS_FOGGER_MARKERS = false
    const val WRITES_DISTANCE_TEMPERATURE_DECAY = false

    fun isPublicEquipment(type: EquipmentType): Boolean = type == EquipmentType.EXHAUST_FAN
}
