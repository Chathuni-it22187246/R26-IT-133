package com.greenhands.app.equipment.domain

import com.greenhands.app.equipment.model.DeviceCountResult
import com.greenhands.app.equipment.model.EquipmentRecommendation
import com.greenhands.app.equipment.model.EquipmentType
import com.greenhands.app.equipment.model.EvidenceClassification
import com.greenhands.app.equipment.model.RecommendationStatus
import com.greenhands.app.equipment.model.preferenceLabel

data class PositionRationale(
    val type: EquipmentType,
    val preference: String,
    val source: String,
    val supportedRule: String,
    val currentInputs: List<String>,
    val adaptations: List<String>,
    val missing: List<String>,
    val limitations: List<String>,
    val sriLankaStatus: String,
    val classification: EvidenceClassification,
    val illustrationOnly: Boolean
)

/**
 * Per-type "Why this position?" copy. Coordinates are research-adapted, not decorative.
 */
object SuggestionEvidence {
    const val SRI_LANKA_STATUS =
        "Not Sri Lanka field-validated. Local trials have not been recorded yet."

    const val KIM_FOG =
        "Kim et al., CFD modeling of a fog system for cooling greenhouses — https://doi.org/10.6090/jarq.41.283"
    const val ARBEL_FOG =
        "Arbel et al., greenhouse fogging with forced ventilation — https://doi.org/10.1016/S1537-5110(02)00216-7"

    const val UCONN_END_FT = "10–15 ft from an end wall and 30–50 ft fan spacing apply to sufficiently large houses; they are not copied into a 10 m greenhouse."
    const val KIM_NOT_UNIVERSAL =
        "Kim et al. 2.3 m nozzle height, 1.9 m side distance and 3.7 m spacing belong to that experimental greenhouse and are not applied universally."

    fun rationale(
        type: EquipmentType,
        recommendation: EquipmentRecommendation?,
        context: PlacementContext,
        illustrationOnly: Boolean
    ): PositionRationale {
        val count = recommendation?.let {
            when (type) {
                EquipmentType.CIRCULATION_FAN -> it.circulation
                EquipmentType.EXHAUST_FAN -> it.exhaust
                EquipmentType.FOGGER -> it.fogger
            }
        }
        return when (type) {
            EquipmentType.CIRCULATION_FAN -> circulation(count, context, illustrationOnly)
            EquipmentType.EXHAUST_FAN -> exhaust(count, context, illustrationOnly)
            EquipmentType.FOGGER -> fogger(count, context, illustrationOnly)
        }
    }

    fun illustrationCount(type: EquipmentType, result: DeviceCountResult?): Int {
        val calculated = result?.appliedCount
        if (canApplyCalculatedLayout(result) && calculated != null) {
            return calculated
        }
        return when (type) {
            EquipmentType.CIRCULATION_FAN -> 2
            EquipmentType.EXHAUST_FAN -> 1
            EquipmentType.FOGGER -> 3
        }
    }

    fun canApplyCalculatedLayout(result: DeviceCountResult?): Boolean {
        val calculated = result?.appliedCount
        return calculated != null && calculated > 0 && result.status != RecommendationStatus.CANNOT_CALCULATE
    }

    fun showsAutoPlaceSuggestedLayout(isDemo: Boolean): Boolean = isDemo

    fun showsApplyLayout(isDemo: Boolean, result: DeviceCountResult?): Boolean =
        !isDemo && canApplyCalculatedLayout(result)

    /**
     * AR Apply Layout: always available in Demo Mode (documented Demo equipment
     * profile). In My Greenhouse it is shown only when a defensible count exists.
     */
    fun showsArApplyLayout(isDemo: Boolean, result: DeviceCountResult?): Boolean =
        isDemo || canApplyCalculatedLayout(result)

    const val AR_DEMO_NOTICE =
        "Research-guided example — not installation guidance."

    const val MISSING_EQUIPMENT_INPUTS =
        "Cannot calculate — missing equipment inputs"

    const val EXAMPLE_PATTERN_ONLY =
        "Example placement pattern only."

    const val RESEARCH_PREFERRED_HEADLINE =
        "Research-preferred for current inputs"

    fun showsPreferenceHeadline(preference: String): Boolean =
        preference.isNotBlank() && preference != RESEARCH_PREFERRED_HEADLINE

    fun countFor(type: EquipmentType, recommendation: EquipmentRecommendation?): DeviceCountResult? =
        recommendation?.let {
            when (type) {
                EquipmentType.CIRCULATION_FAN -> it.circulation
                EquipmentType.EXHAUST_FAN -> it.exhaust
                EquipmentType.FOGGER -> it.fogger
            }
        }

    private fun circulation(
        result: DeviceCountResult?,
        context: PlacementContext,
        illustrationOnly: Boolean
    ): PositionRationale {
        val missing = result?.missingInputs.orEmpty()
        val cannot = result?.status == RecommendationStatus.CANNOT_CALCULATE || result == null
        return PositionRationale(
            type = EquipmentType.CIRCULATION_FAN,
            preference = result?.preferenceLabel()
                ?: "Cannot calculate — missing inputs",
            source = EquipmentRecommendationEngine.UCONN_HAF,
            supportedRule = "Create a coherent horizontal circulation loop. Fans along opposite long sides, opposing airflow, near the moving air mass, about one-quarter width from the side wall when the house is wide enough.",
            currentInputs = listOf(
                "Length ${context.lengthM} m",
                "Width ${context.widthM} m",
                "Height ${context.heightM} m",
                "Crop rows ${context.cropRowCount}",
                "Canopy height ${"%.2f".format(context.canopyHeightM)} m (preview assumption)"
            ),
            adaptations = listOf(
                UCONN_END_FT,
                "End inset uses about 12% of this greenhouse length instead of 10–15 ft.",
                "Along-length spacing is divided across the current length instead of 30–50 ft bays.",
                "Elevation stays above the canopy and below the roof."
            ),
            missing = if (cannot && missing.isEmpty()) {
                listOf("circulation rated airflow (CFM or m³/s)")
            } else {
                missing
            },
            limitations = listOf(
                "2 cfm/ft² is UConn starting guidance, not a design law.",
                "Tall crops may need more capacity; no invented multiplier is applied.",
                "Manufacturer throw distance is not supplied, so throw is not used to space fans."
            ) + (result?.warnings.orEmpty()),
            sriLankaStatus = SRI_LANKA_STATUS,
            classification = result?.classification ?: EvidenceClassification.RESEARCH_GUIDANCE,
            illustrationOnly = illustrationOnly || cannot
        )
    }

    private fun exhaust(
        result: DeviceCountResult?,
        context: PlacementContext,
        illustrationOnly: Boolean
    ): PositionRationale {
        val cannot = result?.status == RecommendationStatus.CANNOT_CALCULATE || result == null
        return PositionRationale(
            type = EquipmentType.EXHAUST_FAN,
            preference = result?.preferenceLabel()
                ?: "Cannot calculate — missing inputs",
            source = "${EquipmentRecommendationEngine.UMASS_VENT}\n${EquipmentRecommendationEngine.UF_IFAS_AE020}",
            supportedRule = "Mount the exhaust fan on an end wall, facing outward. Make-up air should enter from the opposite end so air passes through and over the crop canopy.",
            currentInputs = listOf(
                "Length ${context.lengthM} m",
                "Width ${context.widthM} m",
                "Height ${context.heightM} m"
            ),
            adaptations = listOf(
                "Y is locked to a valid end wall; drag moves X and Z along that wall.",
                "Count is not taken from diameter or free-air CFM."
            ),
            missing = result?.missingInputs.orEmpty().ifEmpty {
                if (cannot) listOf("exhaust rated airflow at expected static pressure (m³/s)") else emptyList()
            },
            limitations = listOf(
                "This suggestion assumes make-up air enters from the opposite end. Confirm the real inlet/louver design.",
                "Prevailing wind and intake area are not designed here.",
                "Free-air CFM is not sufficient for a count claim."
            ) + (result?.warnings.orEmpty()),
            sriLankaStatus = SRI_LANKA_STATUS,
            classification = result?.classification ?: EvidenceClassification.RESEARCH_GUIDANCE,
            illustrationOnly = illustrationOnly || cannot
        )
    }

    private fun fogger(
        result: DeviceCountResult?,
        context: PlacementContext,
        illustrationOnly: Boolean
    ): PositionRationale {
        val cannot = result?.status == RecommendationStatus.CANNOT_CALCULATE || result == null
        return PositionRationale(
            type = EquipmentType.FOGGER,
            preference = result?.preferenceLabel()
                ?: "Cannot calculate — missing inputs",
            source = "$KIM_FOG\n$ARBEL_FOG\n${EquipmentRecommendationEngine.PHASE3A_FOGGER}",
            supportedRule = "Place foggers above the canopy and below the roof, distributed over the treated area, and consider interaction with circulation and ventilation. Avoid excessive crop and wall wetting.",
            currentInputs = listOf(
                "Length ${context.lengthM} m",
                "Width ${context.widthM} m",
                "Height ${context.heightM} m",
                "Canopy height ${"%.2f".format(context.canopyHeightM)} m"
            ),
            adaptations = listOf(
                KIM_NOT_UNIVERSAL,
                "Height is clamped between canopy clearance and roof clearance for this house.",
                "Plan positions follow aisle centres of the current crop-row geometry."
            ),
            missing = result?.missingInputs.orEmpty(),
            limitations = listOf(
                "Count depends on cooling demand, nozzle flow, pressure, coverage and evaporation efficiency.",
                "Indoor/outdoor humidity and saturation limits are not fully specified unless the user enters them."
            ) + (result?.warnings.orEmpty()),
            sriLankaStatus = SRI_LANKA_STATUS,
            classification = result?.classification ?: EvidenceClassification.PROJECT_ASSUMPTION,
            illustrationOnly = illustrationOnly || cannot
        )
    }
}
