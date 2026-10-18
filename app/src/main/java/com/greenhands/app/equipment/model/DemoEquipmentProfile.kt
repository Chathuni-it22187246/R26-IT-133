package com.greenhands.app.equipment.model

import com.greenhands.app.heat.model.Crop
import com.greenhands.app.heat.model.GrowthStage
import com.greenhands.app.heat.simulation.model.ProvenanceClass

/**
 * Documented Demo Mode equipment ratings for research-guided auto-placement.
 *
 * Provenance: [ProvenanceClass.PREVIEW_ASSUMPTION]. These are labelled sample
 * values for the Demo greenhouse envelope, not manufacturer datasheets and not
 * installation guidance.
 */
object DemoEquipmentProfile {
    const val NOTICE =
        "Demo equipment profile — research-guided example, not installation guidance."

    val PROVENANCE: ProvenanceClass = ProvenanceClass.PREVIEW_ASSUMPTION

    /** Sample HAF-class circulation fan, CFM. */
    const val CIRCULATION_RATED_CFM = 1000.0

    /** Sample exhaust rating at expected static pressure, m³/s. */
    const val EXHAUST_RATED_AIRFLOW_M3S = 2.5

    /** Labelled sample static pressure, Pa — not an AMCA test report. */
    const val EXHAUST_STATIC_PRESSURE_PA = 25.0

    /** Sample fogger nozzle water flow, kg/s. */
    const val FOGGER_WATER_FLOW_KG_S = 0.002

    /** Sample evaporation efficiency, 0–1. */
    const val FOGGER_EVAPORATION_EFFICIENCY = 0.50

    /** Synthetic Demo heat load, W. */
    const val NET_HEAT_LOAD_W = 5000.0

    /** Preview indoor temperature used when the crop overlay has none, °C. */
    const val INDOOR_TEMPERATURE_C = 25.0

    /** Preview indoor RH used when the crop overlay has none, % . */
    const val INDOOR_RH_PERCENT = 70.0

    fun inputs(
        lengthM: Double,
        widthM: Double,
        heightM: Double,
        cropRowCount: Int,
        canopyHeightM: Double,
        indoorTemperatureC: Double? = null,
        indoorRhPercent: Double? = null,
        crop: Crop? = null,
        stage: GrowthStage? = null
    ): RecommendationInputs {
        val indoorT = indoorTemperatureC ?: INDOOR_TEMPERATURE_C
        val indoorRh = indoorRhPercent ?: INDOOR_RH_PERCENT
        return RecommendationInputs(
            lengthM = lengthM,
            widthM = widthM,
            heightM = heightM,
            cropRowCount = cropRowCount,
            crop = crop,
            stage = stage,
            canopyHeightM = canopyHeightM,
            canopyHeightIsPreviewAssumption = true,
            optimalTemperatureC = indoorT,
            optimalRhPercent = indoorRh,
            indoorTemperatureC = indoorT,
            indoorRhPercent = indoorRh,
            circulationRatedCfm = CIRCULATION_RATED_CFM,
            exhaustRatedAirflowM3sAtStaticPressure = EXHAUST_RATED_AIRFLOW_M3S,
            exhaustStaticPressurePa = EXHAUST_STATIC_PRESSURE_PA,
            foggerWaterFlowKgS = FOGGER_WATER_FLOW_KG_S,
            foggerEvaporationEfficiency = FOGGER_EVAPORATION_EFFICIENCY,
            netSolarAndInternalHeatLoadW = NET_HEAT_LOAD_W
        )
    }
}
