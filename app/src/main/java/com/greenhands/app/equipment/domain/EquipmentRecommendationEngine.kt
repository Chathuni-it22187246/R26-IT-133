package com.greenhands.app.equipment.domain

import com.greenhands.app.equipment.model.DeviceCountResult
import com.greenhands.app.equipment.model.EquipmentRecommendation
import com.greenhands.app.equipment.model.EquipmentType
import com.greenhands.app.equipment.model.EvidenceClassification
import com.greenhands.app.equipment.model.RecommendationInputs
import com.greenhands.app.equipment.model.RecommendationStatus
import com.greenhands.app.equipment.domain.SuggestionEvidence
import com.greenhands.app.heat.simulation.physics.Psychrometrics
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.PI

/**
 * Equipment-count engine. Does not invent a size-only rule. Missing manufacturer
 * ratings or heat-load inputs return CANNOT_CALCULATE rather than a silent default.
 */
object EquipmentRecommendationEngine {
    /** UConn HAF: about 2 cfm per ft² of floor. RESEARCH_GUIDANCE, not a law. */
    const val HAF_CFM_PER_FT2 = 2.0

    const val UCONN_HAF =
        "UConn IPM, Horizontal Air Flow systems — https://ipm.cahnr.uconn.edu/horizontal-air-flow-systems/"
    const val UMASS_VENT =
        "UMass greenhouse ventilation — https://www.umass.edu/agriculture-food-environment/greenhouse-floriculture/fact-sheets/ventilation-for-greenhouses"
    const val UF_IFAS_AE020 =
        "UF/IFAS AE020 fan selection (AMCA-rated airflow at static pressure) — https://ask.ifas.ufl.edu/publication/AE020"
    const val PHASE3A_FOGGER =
        "GreenHands Phase 3A fogger energy/moisture model (PHYSICS_MODEL.md §9.3): evaporated water only."

    fun recommend(inputs: RecommendationInputs): EquipmentRecommendation {
        val circulation = circulationCount(inputs)
        val exhaust = exhaustCount(inputs)
        val fogger = foggerCount(inputs)
        val circN = inputs.overrideCirculationCount ?: circulation.appliedCount ?: 0
        val exhN = inputs.overrideExhaustCount ?: exhaust.appliedCount ?: 0
        val fogN = inputs.overrideFoggerCount ?: fogger.appliedCount ?: 0
        val context = inputs.context()
        val suggested = PlacementSuggester.suggest(context, circN, exhN, fogN)
        return EquipmentRecommendation(
            circulation = circulation.withOverride(inputs.overrideCirculationCount),
            exhaust = exhaust.withOverride(inputs.overrideExhaustCount),
            fogger = fogger.withOverride(inputs.overrideFoggerCount),
            suggestedInstances = suggested,
            inputsUsed = listedInputs(inputs)
        )
    }

    fun circulationCount(inputs: RecommendationInputs): DeviceCountResult {
        val missing = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val assumptions = mutableListOf<String>()
        val ratedM3s = ratedCirculationM3s(inputs)
        if (ratedM3s == null) {
            missing += "circulation rated airflow (CFM or m³/s)"
            return DeviceCountResult(
                type = EquipmentType.CIRCULATION_FAN,
                recommendedCount = null,
                appliedCount = inputs.overrideCirculationCount,
                status = RecommendationStatus.CANNOT_CALCULATE,
                classification = EvidenceClassification.RESEARCH_GUIDANCE,
                missingInputs = missing,
                assumptions = assumptions,
                warnings = warnings,
                sources = listOf(UCONN_HAF),
                equationNote = "Cannot calculate circulation fan count — rated airflow is required."
            )
        }
        val areaFt2 = AirflowUnits.squareMetresToSquareFeet(inputs.floorAreaM2())
        val requiredCfm = areaFt2 * HAF_CFM_PER_FT2 * inputs.circulationEngineeringFactor
        val ratedCfm = AirflowUnits.m3sToCubicFeetPerMinute(ratedM3s)
        val count = ceil(requiredCfm / ratedCfm).toInt().coerceAtLeast(1)
        assumptions += "Required circulation capacity = floor area × 2 cfm/ft² (UConn HAF starting guidance)."
        assumptions += "Suggested count = ceil(required capacity / rated airflow per fan)."
        if (inputs.circulationEngineeringFactor != 1.0) {
            assumptions += "User engineering factor ${inputs.circulationEngineeringFactor} applied to required capacity."
        }
        warnings += "Tall crops may require more airflow. No numeric tall-crop multiplier is invented; use the engineering factor if a designer specifies one."
        warnings += "Published HAF guidance sometimes runs circulation continuously. GreenHands Phase 3A still uses PROJECT_CONTROL_RULE hysteresis (CSP/CDP/CON)."
        return DeviceCountResult(
            type = EquipmentType.CIRCULATION_FAN,
            recommendedCount = count,
            appliedCount = inputs.overrideCirculationCount ?: count,
            status = RecommendationStatus.OK,
            classification = EvidenceClassification.RESEARCH_GUIDANCE,
            missingInputs = missing,
            assumptions = assumptions,
            warnings = warnings,
            sources = listOf(UCONN_HAF),
            equationNote = "requiredCfm = ${format(areaFt2)} ft² × 2 cfm/ft² × factor ${inputs.circulationEngineeringFactor} = ${format(requiredCfm)}; count = ceil(${format(requiredCfm)} / ${format(ratedCfm)}) = $count"
        )
    }

    fun exhaustCount(inputs: RecommendationInputs): DeviceCountResult {
        val missing = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val assumptions = mutableListOf<String>()
        val rated = inputs.exhaustRatedAirflowM3sAtStaticPressure
        if (rated == null || rated <= 0.0 || !rated.isFinite()) {
            missing += "exhaust rated airflow at expected static pressure (m³/s)"
            return DeviceCountResult(
                type = EquipmentType.EXHAUST_FAN,
                recommendedCount = null,
                appliedCount = inputs.overrideExhaustCount,
                status = RecommendationStatus.CANNOT_CALCULATE,
                classification = EvidenceClassification.RESEARCH_GUIDANCE,
                missingInputs = missing,
                assumptions = assumptions,
                warnings = warnings,
                sources = listOf(UMASS_VENT, UF_IFAS_AE020),
                equationNote = "Cannot calculate exhaust fan count — rated airflow at expected static pressure is required. Free-air CFM is not sufficient."
            )
        }
        if (inputs.exhaustStaticPressurePa == null) {
            warnings += "Static pressure value was not supplied. The airflow figure is still treated as the user-entered rating at expected static pressure, not free-air CFM."
        }
        val height = inputs.effectiveVentilationHeightM ?: inputs.heightM
        if (inputs.effectiveVentilationHeightM == null) {
            assumptions += "Effective ventilation height defaulted to greenhouse height ${format(inputs.heightM)} m. UMass discusses exchange to a defined height; this is not claimed as optimal for every Sri Lankan house."
            missing += "effective ventilation height (optional; greenhouse height used)"
        }
        val volume = inputs.lengthM * inputs.widthM * height
        val requiredM3s = volume / AirflowUnits.SECONDS_PER_MINUTE
        val count = ceil(requiredM3s / rated).toInt().coerceAtLeast(1)
        assumptions += "Preliminary summer guideline: about one air-volume exchange per minute to the effective height (UMass). RESEARCH_GUIDANCE, not an optimum."
        val outdoor = inputs.outdoorTemperatureC
        val target = inputs.optimalTemperatureC
        if (outdoor != null && target != null && outdoor >= target) {
            warnings += "Outside temperature (${format(outdoor)} °C) cannot provide cooling below the outdoor air. Exhaust ventilation cannot cool below the entering-air temperature without another cooling mechanism."
        }
        warnings += "This suggestion assumes make-up air enters from the opposite end. Confirm the real inlet/louver design."
        return DeviceCountResult(
            type = EquipmentType.EXHAUST_FAN,
            recommendedCount = count,
            appliedCount = inputs.overrideExhaustCount ?: count,
            status = RecommendationStatus.OK,
            classification = EvidenceClassification.RESEARCH_GUIDANCE,
            missingInputs = emptyList(),
            assumptions = assumptions,
            warnings = warnings,
            sources = listOf(UMASS_VENT, UF_IFAS_AE020),
            equationNote = "Q = V / 60 s = ${format(volume)} m³ / 60 = ${format(requiredM3s)} m³/s; count = ceil(${format(requiredM3s)} / ${format(rated)}) = $count"
        )
    }

    fun foggerCount(inputs: RecommendationInputs): DeviceCountResult {
        val missing = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val assumptions = mutableListOf<String>()
        val load = inputs.netSolarAndInternalHeatLoadW
        val flow = inputs.foggerWaterFlowKgS
        val eta = inputs.foggerEvaporationEfficiency
        val indoorT = inputs.indoorTemperatureC ?: inputs.optimalTemperatureC
        if (load == null || !load.isFinite() || load < 0.0) missing += "net solar/envelope heat load (W)"
        if (flow == null || !flow.isFinite() || flow <= 0.0) missing += "fogger nozzle water-flow rate (kg/s)"
        if (eta == null || !eta.isFinite() || eta !in 0.0..1.0) missing += "fogger evaporation efficiency (0–1)"
        if (indoorT == null || !indoorT.isFinite()) missing += "indoor or target temperature (°C) for h_fg"
        if (inputs.indoorRhPercent == null && inputs.optimalRhPercent == null) {
            warnings += "Indoor/optimal RH was not supplied, so saturation headroom was not quantified."
        }
        if (load == null || flow == null || eta == null || indoorT == null ||
            load < 0.0 || flow <= 0.0 || eta !in 0.0..1.0
        ) {
            return DeviceCountResult(
                type = EquipmentType.FOGGER,
                recommendedCount = null,
                appliedCount = inputs.overrideFoggerCount,
                status = RecommendationStatus.CANNOT_CALCULATE,
                classification = EvidenceClassification.PROJECT_ASSUMPTION,
                missingInputs = missing,
                assumptions = assumptions,
                warnings = warnings,
                sources = listOf(PHASE3A_FOGGER, SuggestionEvidence.KIM_FOG, SuggestionEvidence.ARBEL_FOG),
                equationNote = "Cannot calculate fogger count yet. Missing: ${missing.joinToString("; ")}."
            )
        }
        val tC = indoorT
        val hfg = Psychrometrics.latentHeatVaporizationJkg(tC)
        val demandKgS = load / hfg
        val evaporatedPerNozzle = eta * flow
        if (evaporatedPerNozzle <= 0.0) {
            return DeviceCountResult(
                type = EquipmentType.FOGGER,
                recommendedCount = null,
                appliedCount = inputs.overrideFoggerCount,
                status = RecommendationStatus.CANNOT_CALCULATE,
                classification = EvidenceClassification.MANUFACTURER_RATED,
                missingInputs = listOf("positive evaporated flow per nozzle"),
                assumptions = assumptions,
                warnings = listOf("Non-evaporated water does not produce cooling."),
                sources = listOf(PHASE3A_FOGGER, SuggestionEvidence.KIM_FOG, SuggestionEvidence.ARBEL_FOG),
                equationNote = "Effective evaporated flow per nozzle is zero."
            )
        }
        val flowCount = ceil(demandKgS / evaporatedPerNozzle).toInt().coerceAtLeast(1)
        var coverageCount = 0
        val coverageRadius = inputs.foggerCoverageRadiusM
        if (coverageRadius != null && coverageRadius > 0.0) {
            val area = PI * coverageRadius * coverageRadius
            val packing = 0.75
            assumptions += "Coverage packing factor 0.75 is a PROJECT_ASSUMPTION, not a copied nozzle grid from one experiment."
            coverageCount = ceil(inputs.floorAreaM2() / (area * packing)).toInt()
        } else {
            warnings += "Nozzle coverage radius was not supplied, so the coverage minimum was not applied."
        }
        val count = max(flowCount, coverageCount)
        assumptions += "evaporated water demand = required cooling power / h_fg(T) (Phase 3A)."
        assumptions += "flow-based nozzle count = ceil(demand / (η × nozzle water flow))."
        warnings += "Non-evaporated water does not produce cooling."
        val indoorRh = inputs.indoorRhPercent ?: inputs.optimalRhPercent
        if (indoorRh != null && indoorRh >= 90.0) {
            warnings += "Indoor RH is already high (${format(indoorRh)} %). Remaining evaporative capacity is limited; fogging may not deliver the requested sensible cooling."
        }
        val outdoorRh = inputs.outdoorRhPercent
        if (outdoorRh != null && outdoorRh >= 85.0) {
            warnings += "Outdoor humidity is high. Evaporative cooling is climate-limited in humid Sri Lankan conditions."
        }
        return DeviceCountResult(
            type = EquipmentType.FOGGER,
            recommendedCount = count,
            appliedCount = inputs.overrideFoggerCount ?: count,
            status = RecommendationStatus.OK,
            classification = EvidenceClassification.RESEARCH_GUIDANCE,
            missingInputs = missing,
            assumptions = assumptions,
            warnings = warnings,
            sources = listOf(PHASE3A_FOGGER, SuggestionEvidence.KIM_FOG, SuggestionEvidence.ARBEL_FOG),
            equationNote = "m_evap = ${format(load)} W / ${format(hfg)} J/kg = ${format(demandKgS)} kg/s; flowCount=$flowCount coverageCount=$coverageCount → $count"
        )
    }

    private fun ratedCirculationM3s(inputs: RecommendationInputs): Double? {
        inputs.circulationRatedM3s?.takeIf { it > 0.0 && it.isFinite() }?.let { return it }
        inputs.circulationRatedCfm?.takeIf { it > 0.0 && it.isFinite() }?.let {
            return AirflowUnits.cubicFeetPerMinuteToM3s(it)
        }
        return null
    }

    private fun DeviceCountResult.withOverride(override: Int?): DeviceCountResult {
        if (override == null) return this
        return copy(
            appliedCount = override,
            status = RecommendationStatus.OVERRIDE_APPLIED,
            assumptions = assumptions + "User overrode recommended count to $override."
        )
    }

    private fun listedInputs(inputs: RecommendationInputs): List<String> {
        val list = mutableListOf(
            "lengthM=${inputs.lengthM}",
            "widthM=${inputs.widthM}",
            "heightM=${inputs.heightM}",
            "cropRowCount=${inputs.cropRowCount}",
            "floorAreaM2=${inputs.floorAreaM2()}",
            "volumeM3=${inputs.volumeM3()}"
        )
        inputs.crop?.let { list += "crop=${it.id}" }
        inputs.stage?.let { list += "stage=${it.id}" }
        inputs.canopyHeightM?.let { list += "canopyHeightM=$it" }
        if (inputs.canopyHeightIsPreviewAssumption) list += "canopyHeight=PREVIEW_ASSUMPTION"
        return list
    }

    private fun format(value: Double): String = String.format(java.util.Locale.US, "%.4g", value)
}
