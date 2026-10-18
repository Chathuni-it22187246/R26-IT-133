package com.greenhands.app.equipment.ui

import com.greenhands.app.heat.domain.HeatFormulas
import com.greenhands.app.heat.model.CirculationThresholds
import com.greenhands.app.heat.model.Crop
import com.greenhands.app.heat.model.ExhaustThresholds
import com.greenhands.app.heat.model.FoggerThresholds
import com.greenhands.app.heat.model.GrowthStage
import com.greenhands.app.heat.model.HeatConfiguration
import com.greenhands.app.heat.model.SchedulePeriod
import com.greenhands.app.heat.model.formatOneDecimal
import com.greenhands.app.heat.simulation.physics.Psychrometrics

/**
 * Maps the active Heat configuration's current Day/Night set into overlay climate.
 * Does not choose a period from the device clock.
 */
object OverlayClimateMapper {
    fun fromActiveConfiguration(
        config: HeatConfiguration,
        period: SchedulePeriod,
        temperatureC: Double?,
        humidityPercent: Double?
    ): OverlayClimate {
        val derived = temperatureC?.let { HeatFormulas.configurationForTargets(it, humidityPercent) }
        return from(
            crop = config.crop,
            stage = config.stage,
            temperatureC = temperatureC,
            humidityPercent = humidityPercent,
            circulation = config.circulation(period) ?: derived?.circulation,
            exhaust = config.exhaust(period) ?: derived?.exhaust,
            fogger = config.fogger(period) ?: derived?.fogger
        )
    }

    fun from(
        crop: Crop?,
        stage: GrowthStage?,
        temperatureC: Double?,
        humidityPercent: Double?,
        circulation: CirculationThresholds?,
        exhaust: ExhaustThresholds?,
        fogger: FoggerThresholds?
    ): OverlayClimate {
        val tempLabel = if (temperatureC != null) {
            "Optimal temperature: ${formatOneDecimal(temperatureC)}°C"
        } else {
            "Not available"
        }
        val humidityLabel = if (humidityPercent != null) {
            "Optimal humidity: ${formatOneDecimal(humidityPercent)} % RH"
        } else {
            "Not available"
        }
        val vpdLabel = if (temperatureC != null && humidityPercent != null) {
            val vpd = runCatching {
                Psychrometrics.vapourPressureDeficitKpa(temperatureC, humidityPercent)
            }.getOrNull()
            if (vpd != null) {
                "Derived ${formatOneDecimal(vpd)} kPa from T/RH — not a published VPD target"
            } else {
                "VPD target not published"
            }
        } else {
            "VPD target not published"
        }
        return OverlayClimate(
            crop = crop,
            stage = stage,
            temperatureLabel = tempLabel,
            humidityLabel = humidityLabel,
            vpdLabel = vpdLabel,
            circulation = circulation,
            exhaust = exhaust,
            fogger = fogger,
            optimalTemperatureC = temperatureC,
            optimalHumidityPercent = humidityPercent
        )
    }
}
