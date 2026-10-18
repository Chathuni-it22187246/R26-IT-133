package com.greenhands.app.heat.runtime.adapter

import com.greenhands.app.equipment.model.DemoEquipmentProfile

/**
 * Explicit SI conversion at the Demo circulation-rating boundary.
 * 1 cubic foot per minute = 0.000471947443 m³/s.
 * 1000 CFM ≈ 0.4719 m³/s applies only to [DemoEquipmentProfile.CIRCULATION_RATED_CFM].
 * It is not the Demo exhaust rating [DemoEquipmentProfile.EXHAUST_RATED_AIRFLOW_M3S].
 */
object AirflowUnits {
    const val CUBIC_FEET_PER_MINUTE_TO_M3S = 0.00047194744320947

    fun cfmToCubicMetresPerSecond(cfm: Double): Double = cfm * CUBIC_FEET_PER_MINUTE_TO_M3S

    fun demoCirculationRatedM3s(): Double =
        cfmToCubicMetresPerSecond(DemoEquipmentProfile.CIRCULATION_RATED_CFM)
}
