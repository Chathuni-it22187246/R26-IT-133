package com.greenhands.app.heat.domain

import com.greenhands.app.heat.model.CirculationThresholds
import com.greenhands.app.heat.model.ClimateRange
import com.greenhands.app.heat.model.ClimateRecommendation
import com.greenhands.app.heat.model.ControlMode
import com.greenhands.app.heat.model.Crop
import com.greenhands.app.heat.model.ExhaustThresholds
import com.greenhands.app.heat.model.FoggerThresholds
import com.greenhands.app.heat.model.GrowthStage
import com.greenhands.app.heat.model.HeatConfiguration
import com.greenhands.app.heat.model.RH_HARD_MAX
import com.greenhands.app.heat.model.RH_HARD_MIN
import com.greenhands.app.heat.model.SchedulePeriod
import com.greenhands.app.heat.model.TEMP_HARD_MAX
import com.greenhands.app.heat.model.TEMP_HARD_MIN
import com.greenhands.app.heat.model.formatOneDecimal
import com.greenhands.app.heat.model.toClimateRange
import com.greenhands.app.heat.profile.CropProfileRegistry

object CropDefaults {
    fun recommendation(crop: Crop, stageId: String): ClimateRecommendation =
        CropProfileRegistry.climateFor(crop, stageId)

    fun rangeFor(crop: Crop, stageId: String): ClimateRange =
        recommendation(crop, stageId).toClimateRange()

    fun stage(crop: Crop, stageId: String): GrowthStage =
        CropProfileRegistry.stageProfile(crop, stageId).stage
}

object HeatFormulas {
    fun circulation(topt: Double): CirculationThresholds = CirculationThresholds(
        csp = topt,
        cdp = topt - 2.0,
        con = topt + 2.0
    )

    fun exhaust(csp: Double, con: Double): ExhaustThresholds {
        val esp = csp + 4.0
        return ExhaustThresholds(
            esp = esp,
            edp = con,
            eon = esp + 2.0
        )
    }

    fun exhaustFromCirculation(circulation: CirculationThresholds): ExhaustThresholds =
        exhaust(circulation.csp, circulation.con)

    fun fogger(rhopt: Double): FoggerThresholds = FoggerThresholds(
        fsp = rhopt,
        fon = rhopt - 2.0,
        fdp = rhopt + 2.0
    )

    fun configurationFor(crop: Crop, stageId: String): HeatEquipment {
        val rec = CropDefaults.recommendation(crop, stageId)
        val circ = circulation(rec.uiDayTemperatureC())
        val rh = rec.uiDayHumidityPercent()
        val fog = rh?.let { fogger(it) }
        return HeatEquipment(circ, exhaustFromCirculation(circ), fog)
    }

    fun configurationForPeriod(crop: Crop, stageId: String, period: SchedulePeriod): HeatEquipment {
        val rec = CropDefaults.recommendation(crop, stageId)
        val circ = circulation(rec.temperatureFor(period))
        val rh = rec.humidityFor(period)
        val fog = rh?.let { fogger(it) }
        return HeatEquipment(circ, exhaustFromCirculation(circ), fog)
    }

    fun configurationForTargets(temperatureC: Double, humidityPercent: Double?): HeatEquipment {
        val circ = circulation(temperatureC)
        val fog = humidityPercent?.let { fogger(it) }
        return HeatEquipment(circ, exhaustFromCirculation(circ), fog)
    }
}

data class HeatEquipment(
    val circulation: CirculationThresholds,
    val exhaust: ExhaustThresholds,
    val fogger: FoggerThresholds?
)

object TargetParser {
    fun parseDecimal(raw: String): Double? {
        val normalized = raw.trim().replace(',', '.')
        if (normalized.isEmpty() || normalized == "." || normalized == "-") return null
        return normalized.toDoubleOrNull()
    }
}

data class FieldValidation(
    val value: Double? = null,
    val error: String? = null,
    val warning: String? = null
) {
    val isValid: Boolean get() = value != null && error == null
}

object HeatValidation {
    fun temperature(raw: String, range: ClimateRange?): FieldValidation {
        if (raw.isBlank()) return FieldValidation(error = "Enter a temperature")
        val value = TargetParser.parseDecimal(raw)
            ?: return FieldValidation(error = "Enter a valid number")
        if (value < TEMP_HARD_MIN || value > TEMP_HARD_MAX) {
            return FieldValidation(
                value = value,
                error = "Temperature must be between ${formatOneDecimal(TEMP_HARD_MIN)}°C and ${formatOneDecimal(TEMP_HARD_MAX)}°C"
            )
        }
        val warning = if (range != null && (value < range.tempMinC || value > range.tempMaxC)) {
            "This value is outside the suggested range for this crop stage."
        } else {
            null
        }
        return FieldValidation(value = value, warning = warning)
    }

    fun humidity(raw: String, range: ClimateRange?): FieldValidation {
        if (raw.isBlank()) return FieldValidation(error = "Enter a humidity")
        val value = TargetParser.parseDecimal(raw)
            ?: return FieldValidation(error = "Enter a valid number")
        if (value < RH_HARD_MIN || value > RH_HARD_MAX) {
            return FieldValidation(
                value = value,
                error = "Humidity must be between ${formatOneDecimal(RH_HARD_MIN)}% and ${formatOneDecimal(RH_HARD_MAX)}%"
            )
        }
        val warning = if (
            range?.rhMinPercent != null &&
            range.rhMaxPercent != null &&
            (value < range.rhMinPercent || value > range.rhMaxPercent)
        ) {
            "This value is outside the suggested range for this crop stage."
        } else {
            null
        }
        return FieldValidation(value = value, warning = warning)
    }

    fun circulationOrder(cdp: Double, csp: Double, con: Double): String? {
        if (!(cdp < csp && csp < con)) {
            return "Thresholds must increase in order: stop, start, then continuous operation."
        }
        return null
    }

    fun exhaustOrder(edp: Double, esp: Double, eon: Double): String? {
        if (!(edp < esp && esp < eon)) {
            return "Thresholds must increase in order: stop exhausting, start exhausting, then continuous operation."
        }
        return null
    }

    fun foggerOrder(fon: Double, fsp: Double, fdp: Double): String? {
        if (!(fon < fsp && fsp < fdp)) {
            return "Thresholds must increase in order: fogging starts, fogging set point, then fogging stops."
        }
        return null
    }

    fun cspDiffersFromTarget(csp: Double, topt: Double): String? {
        if (csp != topt) {
            return "Starts circulating differs from the selected period target."
        }
        return null
    }

    fun fspDiffersFromTarget(fsp: Double, rhopt: Double): String? {
        if (fsp != rhopt) {
            return "Fogging set point differs from the selected period humidity target."
        }
        return null
    }
}

object HeatStageChange {
    fun shouldConfirm(current: HeatConfiguration, newStage: GrowthStage): Boolean {
        return current.stage != null && current.stage.id != newStage.id
    }

    fun apply(current: HeatConfiguration, crop: Crop, newStage: GrowthStage): HeatConfiguration {
        val rec = CropDefaults.recommendation(crop, newStage.id)
        val dayEq = HeatFormulas.configurationForPeriod(crop, newStage.id, SchedulePeriod.DAY)
        val nightEq = HeatFormulas.configurationForPeriod(crop, newStage.id, SchedulePeriod.NIGHT)
        val dayT = rec.uiDayTemperatureC()
        val nightT = rec.uiNightTemperatureC()
        val dayRh = rec.uiDayHumidityPercent()
        val nightRh = rec.uiNightHumidityPercent()
        return current.copy(
            crop = crop,
            stage = newStage,
            dayTemperatureC = dayT,
            nightTemperatureC = nightT,
            dailyMeanTemperatureC = rec.dailyMeanTemperatureC,
            targetTemperatureC = dayT,
            targetHumidityPercent = dayRh,
            dayHumidityPercent = dayRh,
            nightHumidityPercent = nightRh,
            controlMode = ControlMode.AUTOMATIC,
            circulation = dayEq.circulation,
            exhaust = dayEq.exhaust,
            fogger = dayEq.fogger,
            nightCirculation = nightEq.circulation,
            nightExhaust = nightEq.exhaust,
            nightFogger = nightEq.fogger,
            saved = true,
            valuesAreCustomised = false,
            profileVersion = rec.profileVersion,
            lastSuggestedProfileVersion = rec.profileVersion
        )
    }
}

object SuggestedComparison {
    fun isCustomised(config: HeatConfiguration, rec: ClimateRecommendation): Boolean {
        if (config.dayTemperatureC != rec.uiDayTemperatureC()) return true
        if (config.nightTemperatureC != rec.uiNightTemperatureC()) return true
        if ((config.dayHumidityPercent ?: config.targetHumidityPercent) != rec.uiDayHumidityPercent()) return true
        if (config.nightHumidityPercent != rec.uiNightHumidityPercent()) return true
        return false
    }

    fun newerSuggestionsAvailable(config: HeatConfiguration, rec: ClimateRecommendation): Boolean {
        if (!config.saved) return false
        val savedVersion = config.lastSuggestedProfileVersion ?: config.profileVersion ?: return false
        if (savedVersion == rec.profileVersion) return false
        return isCustomised(config, rec)
    }
}

/**
 * Profile 2.1.2 migration for the retired “selected climate target” field (`topt`).
 *
 * Rule: if a legacy custom selected target differs from the stored Day value, preserve
 * it as the custom Day target and retain the existing Night suggestion. Custom values
 * are never silently overwritten with research defaults.
 */
object ProfileMigration212 {
    fun migrate(config: HeatConfiguration): HeatConfiguration {
        val rec = config.crop?.let { crop ->
            config.stage?.id?.let { stageId ->
                runCatching { CropDefaults.recommendation(crop, stageId) }.getOrNull()
            }
        }
        val suggestedDay = rec?.uiDayTemperatureC()
        val suggestedNight = rec?.uiNightTemperatureC()
        val suggestedDayRh = rec?.uiDayHumidityPercent()
        val suggestedNightRh = rec?.uiNightHumidityPercent()
        val legacyTopt = config.targetTemperatureC
        val storedDay = config.dayTemperatureC
        val storedNight = config.nightTemperatureC

        val day: Double?
        val night: Double?
        var customised = config.valuesAreCustomised
        when {
            storedDay != null && legacyTopt != null && legacyTopt != storedDay -> {
                day = legacyTopt
                night = storedNight ?: suggestedNight
                customised = true
            }
            storedDay != null -> {
                day = storedDay
                night = storedNight ?: suggestedNight ?: storedDay
            }
            legacyTopt != null -> {
                day = legacyTopt
                night = storedNight ?: suggestedNight ?: legacyTopt
                if (suggestedDay != null && legacyTopt != suggestedDay) customised = true
            }
            else -> {
                day = suggestedDay
                night = suggestedNight
            }
        }
        val dayRh = config.dayHumidityPercent ?: config.targetHumidityPercent ?: suggestedDayRh
        val nightRh = config.nightHumidityPercent ?: suggestedNightRh ?: dayRh
        val dayEq = if (day != null) HeatFormulas.configurationForTargets(day, dayRh) else null
        val nightEq = if (night != null) HeatFormulas.configurationForTargets(night, nightRh) else null
        return config.copy(
            dayTemperatureC = day,
            nightTemperatureC = night,
            targetTemperatureC = day,
            targetHumidityPercent = dayRh,
            dayHumidityPercent = dayRh,
            nightHumidityPercent = nightRh,
            circulation = config.circulation ?: dayEq?.circulation,
            exhaust = config.exhaust ?: dayEq?.exhaust,
            fogger = config.fogger ?: dayEq?.fogger,
            nightCirculation = config.nightCirculation ?: nightEq?.circulation,
            nightExhaust = config.nightExhaust ?: nightEq?.exhaust,
            nightFogger = config.nightFogger ?: nightEq?.fogger,
            valuesAreCustomised = customised
        )
    }
}
