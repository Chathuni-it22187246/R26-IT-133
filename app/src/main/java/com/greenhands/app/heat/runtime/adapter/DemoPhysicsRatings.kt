package com.greenhands.app.heat.runtime.adapter

import com.greenhands.app.equipment.model.DemoEquipmentProfile
import com.greenhands.app.heat.simulation.model.ProvenanceClass

/**
 * Demo Mode labelled physics ratings. My Greenhouse must never read these values.
 *
 * Airflow, fogger flow and evaporation efficiency come from [DemoEquipmentProfile].
 * Outlet diameter is a labelled Demo-only placeholder required to form A0 = πD²/4;
 * it is not an AMCA diameter, not a GLB bounding-box size, and not a Phase 3A test fixture.
 */
object DemoPhysicsRatings {
    val PROVENANCE: ProvenanceClass = DemoEquipmentProfile.PROVENANCE

    /** Unverified Demo-only placeholder outlet diameter, m. */
    const val CIRCULATION_OUTLET_DIAMETER_M = 0.41

    fun circulationRatedCfm(): Double = DemoEquipmentProfile.CIRCULATION_RATED_CFM

    fun circulationRatedM3s(): Double = AirflowUnits.demoCirculationRatedM3s()

    fun exhaustRatedAirflowM3s(): Double = DemoEquipmentProfile.EXHAUST_RATED_AIRFLOW_M3S

    fun exhaustStaticPressurePa(): Double = DemoEquipmentProfile.EXHAUST_STATIC_PRESSURE_PA

    fun foggerWaterFlowKgS(): Double = DemoEquipmentProfile.FOGGER_WATER_FLOW_KG_S

    fun foggerEvaporationEfficiency(): Double = DemoEquipmentProfile.FOGGER_EVAPORATION_EFFICIENCY
}
