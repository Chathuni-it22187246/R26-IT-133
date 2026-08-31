package com.greenhands.app.heat.model

const val PROFILE_VERSION = "2.1.2"

const val TEMP_HARD_MIN = 5.0
const val TEMP_HARD_MAX = 50.0
const val RH_HARD_MIN = 0.0
const val RH_HARD_MAX = 100.0

const val RESEARCH_SUGGESTED_HEADING = "Suggested climate profile"

const val RESEARCH_DISCLAIMER =
    "Suggested values can be adjusted to suit your greenhouse."

const val DEMO_CONTROL_LOGIC_NOTICE =
    "Thresholds are calculated from the selected crop profile and schedule period. No physical equipment is connected."

const val FOGGER_HUMID_CLIMATE_WARNING =
    "Fogging may provide limited evaporative cooling in warm, humid conditions. Demo fogger bands are project rules, not a cooling design."

enum class SchedulePeriod {
    DAY,
    NIGHT
}

fun formatOneDecimal(value: Double): String {
    val two = String.format(java.util.Locale.US, "%.2f", value)
    return if (two.endsWith("0")) two.dropLast(1) else two
}

enum class Crop(
    val id: String,
    val displayName: String,
    val scientificName: String,
    val greenhouseDescription: String,
    val available: Boolean = true
) {
    TOMATO(
        id = "tomato",
        displayName = "Tomato",
        scientificName = "Solanum lycopersicum",
        greenhouseDescription = "Indeterminate greenhouse tomato with research-supported day and night climate targets."
    ),
    SALAD_CUCUMBER(
        id = "salad_cucumber",
        displayName = "Salad Cucumber",
        scientificName = "Cucumis sativus",
        greenhouseDescription = "Protected-house salad cucumber with simplified day and night climate targets."
    ),
    BELL_PEPPER(
        id = "bell_pepper",
        displayName = "Bell Pepper/Capsicum",
        scientificName = "Capsicum annuum",
        greenhouseDescription = "Greenhouse sweet pepper with research-supported climate targets."
    ),
    CHILLI(
        id = "chilli",
        displayName = "Chilli",
        scientificName = "Capsicum annuum",
        greenhouseDescription = "Hot pepper with crop-level day and night climate targets."
    ),
    LETTUCE(
        id = "lettuce",
        displayName = "Greenhouse Lettuce",
        scientificName = "Lactuca sativa",
        greenhouseDescription = "Controlled-environment lettuce with production climate targets."
    );

    companion object {
        fun fromId(id: String?): Crop? = entries.find { it.id == id }
        fun selectable(): List<Crop> = entries.filter { it.available }
    }
}

data class GrowthStage(
    val id: String,
    val displayName: String,
    val shortLabel: String,
    val explanation: String
)

enum class ControlMode {
    AUTOMATIC,
    ADVANCED
}

enum class EvidenceLevel {
    DIRECT_SRI_LANKA_PROTECTED_CULTURE,
    SRI_LANKA_OFFICIAL_CROP_GUIDANCE,
    INTERNATIONAL_GREENHOUSE_GUIDANCE,
    CONTROLLED_ENVIRONMENT_RESEARCH,
    DERIVED_MIDPOINT,
    CROP_LEVEL_INHERITED,
    PROJECT_CONTROL_RULE,
    LOCAL_VALIDATION_REQUIRED
}

enum class DerivationMethod {
    DIRECT,
    DERIVED_MIDPOINT,
    CROP_LEVEL_INHERITED,
    PROJECT_CONTROL_RULE,
    NOT_PUBLISHED
}

data class RecommendedRange(
    val min: Double,
    val max: Double,
    val unit: String
) {
    fun label(): String = "${formatOneDecimal(min)}–${formatOneDecimal(max)}$unit"
}

data class TimedClimateBand(
    val id: String,
    val label: String,
    val applicability: String,
    val target: Double,
    val range: RecommendedRange?,
    val unit: String,
    val derivation: DerivationMethod,
    val evidence: EvidenceLevel
) {
    fun displayValue(): String {
        val rangeLabel = range?.takeIf { it.min != it.max }?.label()
        return if (rangeLabel != null) {
            "$rangeLabel (target ${formatOneDecimal(target)}$unit)"
        } else {
            "${formatOneDecimal(target)}$unit"
        }
    }
}

data class HumiditySubPeriod(
    val id: String,
    val label: String,
    val dayRangeNote: String,
    val bands: List<TimedClimateBand>
)

data class SourceCitation(
    val id: String,
    val title: String,
    val authorsOrOrganisation: String,
    val year: String,
    val supportedCrops: List<Crop>,
    val supportedParameters: List<String>,
    val evidenceLevel: EvidenceLevel,
    val geographicApplicability: String,
    val doiOrUrl: String,
    val locationInSource: String
)

data class ApplicabilityNote(
    val text: String
)

data class ClimateRecommendation(
    val dayTemperatureC: Double? = null,
    val dayTemperatureRange: RecommendedRange? = null,
    val nightTemperatureC: Double? = null,
    val nightTemperatureRange: RecommendedRange? = null,
    val dailyMeanTemperatureC: Double? = null,
    val generalTemperatureC: Double? = null,
    val generalTemperatureRange: RecommendedRange? = null,
    val selectedTargetTemperatureC: Double,
    val humidityPercent: Double? = null,
    val humidityRange: RecommendedRange? = null,
    val warningNotes: List<String> = emptyList(),
    val sourceIds: List<String>,
    val temperatureEvidence: EvidenceLevel,
    val humidityEvidence: EvidenceLevel? = null,
    val temperatureDerivation: DerivationMethod,
    val humidityDerivation: DerivationMethod? = null,
    val profileVersion: String = PROFILE_VERSION,
    val localValidationRequired: Boolean = true,
    val applicability: ApplicabilityNote,
    val humidityUnavailableNote: String? = null,
    val temperatureSchedule: List<TimedClimateBand> = emptyList(),
    val humiditySchedule: List<TimedClimateBand> = emptyList(),
    val humiditySubPeriods: List<HumiditySubPeriod> = emptyList(),
    val dayHumidityPercent: Double? = null,
    val nightHumidityPercent: Double? = null,
    val operatorWarning: String? = null,
    val presentationMapping: String? = null
) {
    fun temperatureWarningRange(): RecommendedRange {
        val mins = listOfNotNull(
            dayTemperatureRange?.min,
            nightTemperatureRange?.min,
            generalTemperatureRange?.min,
            dayTemperatureC,
            nightTemperatureC,
            generalTemperatureC,
            selectedTargetTemperatureC
        ) + temperatureSchedule.map { it.range?.min ?: it.target }
        val maxs = listOfNotNull(
            dayTemperatureRange?.max,
            nightTemperatureRange?.max,
            generalTemperatureRange?.max,
            dayTemperatureC,
            nightTemperatureC,
            generalTemperatureC,
            selectedTargetTemperatureC
        ) + temperatureSchedule.map { it.range?.max ?: it.target }
        return RecommendedRange(
            mins.minOrNull() ?: selectedTargetTemperatureC,
            maxs.maxOrNull() ?: selectedTargetTemperatureC,
            "°C"
        )
    }

    fun humidityWarningRange(): RecommendedRange? {
        val values = mutableListOf<Double>()
        humidityRange?.let {
            values += it.min
            values += it.max
        }
        humidityPercent?.let { values += it }
        humiditySchedule.forEach { band ->
            values += band.target
            band.range?.let {
                values += it.min
                values += it.max
            }
        }
        humiditySubPeriods.forEach { period ->
            period.bands.forEach { band ->
                values += band.target
                band.range?.let {
                    values += it.min
                    values += it.max
                }
            }
        }
        if (values.isEmpty()) return null
        return RecommendedRange(values.minOrNull()!!, values.maxOrNull()!!, "%")
    }

    fun hasDayNightSplit(): Boolean = dayTemperatureC != null && nightTemperatureC != null

    fun hasTimeOfDayTemperatureSchedule(): Boolean = temperatureSchedule.isNotEmpty()

    fun uiDayTemperatureC(): Double =
        dayTemperatureC ?: generalTemperatureC ?: selectedTargetTemperatureC

    fun uiNightTemperatureC(): Double =
        nightTemperatureC ?: generalTemperatureC ?: selectedTargetTemperatureC

    fun uiDayTemperatureRange(): RecommendedRange =
        dayTemperatureRange ?: generalTemperatureRange ?: temperatureWarningRange()

    fun uiNightTemperatureRange(): RecommendedRange =
        nightTemperatureRange ?: generalTemperatureRange ?: temperatureWarningRange()

    fun uiDayHumidityPercent(): Double? = dayHumidityPercent ?: humidityPercent

    fun uiNightHumidityPercent(): Double? {
        if (nightHumidityPercent != null) return nightHumidityPercent
        humiditySchedule.lastOrNull()?.target?.let { return it }
        humiditySubPeriods.firstOrNull()?.bands?.getOrNull(1)?.target?.let { return it }
        return humidityPercent
    }

    fun uiDayHumidityRange(): RecommendedRange? = humidityRange

    fun uiNightHumidityRange(): RecommendedRange? {
        humiditySchedule.lastOrNull()?.range?.let { return it }
        humiditySubPeriods.firstOrNull()?.bands?.getOrNull(1)?.range?.let { return it }
        return humidityRange
    }

    fun temperatureFor(period: SchedulePeriod): Double =
        if (period == SchedulePeriod.DAY) uiDayTemperatureC() else uiNightTemperatureC()

    fun humidityFor(period: SchedulePeriod): Double? =
        if (period == SchedulePeriod.DAY) uiDayHumidityPercent() else uiNightHumidityPercent()

    fun temperatureRangeFor(period: SchedulePeriod): RecommendedRange =
        if (period == SchedulePeriod.DAY) uiDayTemperatureRange() else uiNightTemperatureRange()

    fun humidityRangeFor(period: SchedulePeriod): RecommendedRange? =
        if (period == SchedulePeriod.DAY) uiDayHumidityRange() else uiNightHumidityRange()
}

data class GrowthStageProfile(
    val stage: GrowthStage,
    val climate: ClimateRecommendation
)

data class CropProfile(
    val crop: Crop,
    val evidenceBadge: EvidenceLevel,
    val stages: List<GrowthStageProfile>,
    val profileVersion: String = PROFILE_VERSION
) {
    fun stage(id: String): GrowthStageProfile? = stages.find { it.stage.id == id }
}

data class ClimateRange(
    val tempMinC: Double,
    val tempMaxC: Double,
    val defaultTempC: Double,
    val rhMinPercent: Double?,
    val rhMaxPercent: Double?,
    val defaultRhPercent: Double?
) {
    fun tempLabel(): String = "${formatOneDecimal(tempMinC)}–${formatOneDecimal(tempMaxC)}°C"
    fun rhLabel(): String = if (rhMinPercent != null && rhMaxPercent != null) {
        "${formatOneDecimal(rhMinPercent)}–${formatOneDecimal(rhMaxPercent)}%"
    } else {
        "No published RH setpoint"
    }
}

fun ClimateRecommendation.toClimateRange(): ClimateRange {
    val t = temperatureWarningRange()
    val rh = humidityWarningRange()
    return ClimateRange(
        tempMinC = t.min,
        tempMaxC = t.max,
        defaultTempC = selectedTargetTemperatureC,
        rhMinPercent = rh?.min ?: humidityRange?.min,
        rhMaxPercent = rh?.max ?: humidityRange?.max,
        defaultRhPercent = humidityPercent
    )
}

fun ClimateRecommendation.toClimateRangeFor(period: SchedulePeriod): ClimateRange {
    val t = temperatureRangeFor(period)
    val rh = humidityRangeFor(period)
    return ClimateRange(
        tempMinC = t.min,
        tempMaxC = t.max,
        defaultTempC = temperatureFor(period),
        rhMinPercent = rh?.min,
        rhMaxPercent = rh?.max,
        defaultRhPercent = humidityFor(period)
    )
}

data class CirculationThresholds(
    val csp: Double,
    val cdp: Double,
    val con: Double
)

data class ExhaustThresholds(
    val esp: Double,
    val edp: Double,
    val eon: Double
)

data class FoggerThresholds(
    val fsp: Double,
    val fon: Double,
    val fdp: Double
)

data class HeatConfiguration(
    val crop: Crop? = null,
    val stage: GrowthStage? = null,
    val dayTemperatureC: Double? = null,
    val nightTemperatureC: Double? = null,
    val dailyMeanTemperatureC: Double? = null,
    val targetTemperatureC: Double? = null,
    val targetHumidityPercent: Double? = null,
    val dayHumidityPercent: Double? = null,
    val nightHumidityPercent: Double? = null,
    val controlMode: ControlMode = ControlMode.AUTOMATIC,
    val circulation: CirculationThresholds? = null,
    val exhaust: ExhaustThresholds? = null,
    val fogger: FoggerThresholds? = null,
    val nightCirculation: CirculationThresholds? = null,
    val nightExhaust: ExhaustThresholds? = null,
    val nightFogger: FoggerThresholds? = null,
    val saved: Boolean = false,
    val valuesAreCustomised: Boolean = false,
    val profileVersion: String? = null,
    val lastSuggestedProfileVersion: String? = null
) {
    fun configKey(): String? {
        val cropId = crop?.id ?: return null
        val stageId = stage?.id ?: return null
        return "${cropId}_$stageId"
    }

    fun temperature(period: SchedulePeriod): Double? =
        if (period == SchedulePeriod.DAY) dayTemperatureC else nightTemperatureC

    fun humidity(period: SchedulePeriod): Double? =
        if (period == SchedulePeriod.DAY) {
            dayHumidityPercent ?: targetHumidityPercent
        } else {
            nightHumidityPercent
        }

    fun circulation(period: SchedulePeriod): CirculationThresholds? =
        if (period == SchedulePeriod.DAY) circulation else nightCirculation

    fun exhaust(period: SchedulePeriod): ExhaustThresholds? =
        if (period == SchedulePeriod.DAY) exhaust else nightExhaust

    fun fogger(period: SchedulePeriod): FoggerThresholds? =
        if (period == SchedulePeriod.DAY) fogger else nightFogger
}

fun EvidenceLevel.userFacingStatus(): String = when (this) {
    EvidenceLevel.PROJECT_CONTROL_RULE -> "Demo Mode"
    else -> "Research-supported"
}

fun EvidenceLevel.appLabel(): String = when (this) {
    EvidenceLevel.DIRECT_SRI_LANKA_PROTECTED_CULTURE -> "Sri Lankan protected-house evidence"
    EvidenceLevel.SRI_LANKA_OFFICIAL_CROP_GUIDANCE -> "Sri Lankan official crop guidance"
    EvidenceLevel.INTERNATIONAL_GREENHOUSE_GUIDANCE -> "International greenhouse guidance"
    EvidenceLevel.CONTROLLED_ENVIRONMENT_RESEARCH -> "Controlled-environment research"
    EvidenceLevel.DERIVED_MIDPOINT -> "Derived from cited range"
    EvidenceLevel.CROP_LEVEL_INHERITED -> "Crop-level inherited"
    EvidenceLevel.PROJECT_CONTROL_RULE -> "Demo control logic"
    EvidenceLevel.LOCAL_VALIDATION_REQUIRED -> "Local validation required"
}
