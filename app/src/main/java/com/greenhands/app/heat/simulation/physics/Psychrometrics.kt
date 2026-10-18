package com.greenhands.app.heat.simulation.physics

import kotlin.math.exp

/**
 * SI psychrometric conversions (PHYSICS_MODEL.md §6).
 * Humidity ratio is the transported moisture variable; RH is derived.
 */
object Psychrometrics {
    /** NOAA Magnus coefficient, Pa. REFERENCE_PROPERTY. */
    const val MAGNUS_A_PA = 611.2

    /** NOAA Magnus coefficient (dimensionless). */
    const val MAGNUS_B = 17.67

    /** NOAA Magnus coefficient, °C. */
    const val MAGNUS_C_C = 243.5

    /** Molecular-weight ratio Mw/Mda. ASHRAE/psychrometric basis. */
    const val WATER_TO_DRY_AIR_MOLAR_RATIO = 0.62198

    /** Latent-heat intercept, J/kg. PHYSICS_MODEL.md §9.3. */
    const val LATENT_HEAT_INTERCEPT_J_KG = 2.501e6

    /** Latent-heat slope, J/(kg·K) with T in °C. */
    const val LATENT_HEAT_SLOPE_J_KG_K = 2369.0

    const val MIN_TEMPERATURE_C = -40.0
    const val MAX_TEMPERATURE_C = 80.0

    fun saturationVapourPressurePa(temperatureC: Double): Double {
        validateTemperature(temperatureC)
        val denom = temperatureC + MAGNUS_C_C
        if (denom <= 0.0 || !denom.isFinite()) {
            throw SimulationException("Magnus denominator is non-positive at T=$temperatureC °C")
        }
        val pws = MAGNUS_A_PA * exp(MAGNUS_B * temperatureC / denom)
        requireFinitePositive(pws, "saturation vapour pressure")
        return pws
    }

    fun vapourPressurePa(temperatureC: Double, relativeHumidityPercent: Double): Double {
        validateRelativeHumidityInput(relativeHumidityPercent)
        return (relativeHumidityPercent / 100.0) * saturationVapourPressurePa(temperatureC)
    }

    fun humidityRatioKgKg(
        temperatureC: Double,
        relativeHumidityPercent: Double,
        pressurePa: Double
    ): Double {
        validatePressure(pressurePa)
        val pv = vapourPressurePa(temperatureC, relativeHumidityPercent)
        if (pv >= pressurePa) {
            throw SimulationException(
                "Vapour pressure $pv Pa is not below total pressure $pressurePa Pa"
            )
        }
        return WATER_TO_DRY_AIR_MOLAR_RATIO * pv / (pressurePa - pv)
    }

    fun vapourPressureFromHumidityRatioPa(humidityRatioKgKg: Double, pressurePa: Double): Double {
        validatePressure(pressurePa)
        validateHumidityRatio(humidityRatioKgKg)
        val omega = humidityRatioKgKg
        return pressurePa * omega / (WATER_TO_DRY_AIR_MOLAR_RATIO + omega)
    }

    /**
     * Internal RH; may exceed 100% if the state is supersaturated.
     * Clamp only at display boundaries.
     */
    fun relativeHumidityPercent(
        temperatureC: Double,
        humidityRatioKgKg: Double,
        pressurePa: Double
    ): Double {
        val pv = vapourPressureFromHumidityRatioPa(humidityRatioKgKg, pressurePa)
        val pws = saturationVapourPressurePa(temperatureC)
        return 100.0 * pv / pws
    }

    fun displayedRelativeHumidityPercent(
        temperatureC: Double,
        humidityRatioKgKg: Double,
        pressurePa: Double
    ): Double = relativeHumidityPercent(temperatureC, humidityRatioKgKg, pressurePa).coerceIn(0.0, 100.0)

    fun saturationHumidityRatioKgKg(temperatureC: Double, pressurePa: Double): Double {
        validatePressure(pressurePa)
        val pws = saturationVapourPressurePa(temperatureC)
        if (pws >= pressurePa) {
            throw SimulationException(
                "Saturation vapour pressure $pws Pa is not below total pressure $pressurePa Pa"
            )
        }
        return WATER_TO_DRY_AIR_MOLAR_RATIO * pws / (pressurePa - pws)
    }

    /** VPD in kPa. PHYSICS_MODEL.md §6.3. */
    fun vapourPressureDeficitKpa(
        temperatureC: Double,
        relativeHumidityPercent: Double
    ): Double {
        val pws = saturationVapourPressurePa(temperatureC)
        val pv = vapourPressurePa(temperatureC, relativeHumidityPercent)
        return (pws - pv) / 1000.0
    }

    /**
     * h_fg(T) ≈ 2.501e6 − 2369 T  J/kg for 0–50 °C (PHYSICS_MODEL.md §9.3).
     * Tested against NIST/IAPWS saturation values; not a full IAPWS-95 evaluation.
     */
    fun latentHeatVaporizationJkg(temperatureC: Double): Double {
        validateTemperature(temperatureC)
        val hfg = LATENT_HEAT_INTERCEPT_J_KG - LATENT_HEAT_SLOPE_J_KG_K * temperatureC
        requireFinitePositive(hfg, "latent heat of vaporization")
        return hfg
    }

    /**
     * Approximate moist-air enthalpy per kilogram of dry air:
     * \(h \approx c_p T + \omega\, h_{fg}(T)\) using this object's \(h_{fg}\) and the
     * caller-supplied \(c_p\) (typically [AirProperties.specificHeatJkgK]).
     *
     * This is the project energy-balance form already used with fogger latent
     * cooling. It is not a full ASHRAE \(c_{p,da}T+\omega(h_{g0}+c_{p,v}T)\) expansion.
     */
    fun moistAirEnthalpyJPerKgDryAir(
        temperatureC: Double,
        humidityRatioKgKg: Double,
        specificHeatJkgK: Double
    ): Double {
        validateTemperature(temperatureC)
        validateHumidityRatio(humidityRatioKgKg)
        if (!specificHeatJkgK.isFinite() || specificHeatJkgK <= 0.0) {
            throw SimulationException("specificHeatJkgK must be finite and > 0 J/(kg·K)")
        }
        val h = specificHeatJkgK * temperatureC +
            humidityRatioKgKg * latentHeatVaporizationJkg(temperatureC)
        if (!h.isFinite()) {
            throw SimulationException("Moist-air enthalpy is not finite: $h J/kg")
        }
        return h
    }

    /**
     * Thermodynamic wet-bulb / adiabatic-saturation temperature: the \(T^*\) that
     * satisfies \(h(T_{db},\omega)=h(T^*,\omega_{sat}(T^*))\) with this object's
     * Magnus \(\omega_{sat}\) and enthalpy. Used as \(T_{wb,out}\) for evaporative pads.
     */
    fun wetBulbTemperatureC(
        dryBulbTemperatureC: Double,
        relativeHumidityPercent: Double,
        pressurePa: Double,
        specificHeatJkgK: Double
    ): Double {
        validateTemperature(dryBulbTemperatureC)
        validateRelativeHumidityInput(relativeHumidityPercent)
        validatePressure(pressurePa)
        if (relativeHumidityPercent >= 100.0) {
            return dryBulbTemperatureC
        }
        val omega = humidityRatioKgKg(dryBulbTemperatureC, relativeHumidityPercent, pressurePa)
        val hAir = moistAirEnthalpyJPerKgDryAir(dryBulbTemperatureC, omega, specificHeatJkgK)
        var lo = MIN_TEMPERATURE_C
        var hi = dryBulbTemperatureC
        repeat(64) {
            val mid = 0.5 * (lo + hi)
            val omegaSat = saturationHumidityRatioKgKg(mid, pressurePa)
            val hSat = moistAirEnthalpyJPerKgDryAir(mid, omegaSat, specificHeatJkgK)
            if (hSat > hAir) {
                hi = mid
            } else {
                lo = mid
            }
        }
        val wetBulb = 0.5 * (lo + hi)
        if (!wetBulb.isFinite()) {
            throw SimulationException("Wet-bulb temperature is not finite")
        }
        return wetBulb.coerceIn(MIN_TEMPERATURE_C, dryBulbTemperatureC)
    }

    fun validateTemperature(temperatureC: Double) {
        if (!temperatureC.isFinite()) {
            throw SimulationException("Temperature is not finite: $temperatureC")
        }
        if (temperatureC < MIN_TEMPERATURE_C || temperatureC > MAX_TEMPERATURE_C) {
            throw SimulationException(
                "Temperature $temperatureC °C is outside the calculation range " +
                    "[$MIN_TEMPERATURE_C, $MAX_TEMPERATURE_C] °C"
            )
        }
    }

    fun validatePressure(pressurePa: Double) {
        if (!pressurePa.isFinite() || pressurePa <= 0.0) {
            throw SimulationException("Pressure must be finite and > 0 Pa, got $pressurePa")
        }
    }

    fun validateRelativeHumidityInput(relativeHumidityPercent: Double) {
        if (!relativeHumidityPercent.isFinite() || relativeHumidityPercent < 0.0) {
            throw SimulationException(
                "Relative humidity must be finite and >= 0%, got $relativeHumidityPercent"
            )
        }
    }

    fun validateHumidityRatio(humidityRatioKgKg: Double) {
        if (!humidityRatioKgKg.isFinite() || humidityRatioKgKg < 0.0) {
            throw SimulationException(
                "Humidity ratio must be finite and >= 0 kg/kg, got $humidityRatioKgKg"
            )
        }
    }

    private fun requireFinitePositive(value: Double, name: String) {
        if (!value.isFinite() || value <= 0.0) {
            throw SimulationException("$name is not finite and positive: $value")
        }
    }
}

class SimulationException(message: String) : IllegalArgumentException(message)
