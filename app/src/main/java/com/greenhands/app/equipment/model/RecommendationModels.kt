package com.greenhands.app.equipment.model

import com.greenhands.app.equipment.domain.PlacementContext
import com.greenhands.app.heat.model.Crop
import com.greenhands.app.heat.model.GrowthStage

data class RecommendationInputs(
    val lengthM: Double,
    val widthM: Double,
    val heightM: Double,
    val cropRowCount: Int,
    val crop: Crop? = null,
    val stage: GrowthStage? = null,
    val canopyHeightM: Double? = null,
    val canopyHeightIsPreviewAssumption: Boolean = false,
    val optimalTemperatureMinC: Double? = null,
    val optimalTemperatureMaxC: Double? = null,
    val optimalTemperatureC: Double? = null,
    val optimalRhMinPercent: Double? = null,
    val optimalRhMaxPercent: Double? = null,
    val optimalRhPercent: Double? = null,
    val optimalVpdKpa: Double? = null,
    val outdoorTemperatureC: Double? = null,
    val outdoorRhPercent: Double? = null,
    val indoorTemperatureC: Double? = null,
    val indoorRhPercent: Double? = null,
    val circulationRatedCfm: Double? = null,
    val circulationRatedM3s: Double? = null,
    val circulationEngineeringFactor: Double = 1.0,
    val exhaustRatedAirflowM3sAtStaticPressure: Double? = null,
    val exhaustStaticPressurePa: Double? = null,
    val effectiveVentilationHeightM: Double? = null,
    val foggerWaterFlowKgS: Double? = null,
    val foggerEvaporationEfficiency: Double? = null,
    val foggerOperatingPressurePa: Double? = null,
    val foggerCoverageRadiusM: Double? = null,
    val netSolarAndInternalHeatLoadW: Double? = null,
    val envelopeUaWPerK: Double? = null,
    val intakeOpeningCount: Int? = null,
    val intakeNote: String? = null,
    val overrideCirculationCount: Int? = null,
    val overrideExhaustCount: Int? = null,
    val overrideFoggerCount: Int? = null
) {
    fun context(): PlacementContext {
        val canopy = canopyHeightM ?: (heightM * 0.26)
        return PlacementContext(lengthM, widthM, heightM, cropRowCount, canopy)
    }

    fun floorAreaM2(): Double = lengthM * widthM

    fun volumeM3(): Double = lengthM * widthM * heightM
}

data class DeviceCountResult(
    val type: EquipmentType,
    val recommendedCount: Int?,
    val appliedCount: Int?,
    val status: RecommendationStatus,
    val classification: EvidenceClassification,
    val missingInputs: List<String>,
    val assumptions: List<String>,
    val warnings: List<String>,
    val sources: List<String>,
    val equationNote: String
)

data class EquipmentRecommendation(
    val circulation: DeviceCountResult,
    val exhaust: DeviceCountResult,
    val fogger: DeviceCountResult,
    val suggestedInstances: List<EquipmentInstance>,
    val disclaimer: String = DISCLAIMER,
    val inputsUsed: List<String>
) {
    companion object {
        const val DISCLAIMER =
            "Research-guided starting layout — verify with the equipment manufacturer and a greenhouse engineer."
    }
}
