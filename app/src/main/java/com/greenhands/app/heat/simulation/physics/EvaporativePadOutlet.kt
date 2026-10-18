package com.greenhands.app.heat.simulation.physics

/**
 * Evaporative cooling-pad outlet state. Does not change Phase 3A transport equations.
 *
 * Dry-bulb:
 * \(T_{pad}=T_{out}-\eta_{pad}(T_{out}-T_{wb,out})\), with \(T_{pad}\ge T_{wb,out}\).
 *
 * Humidity ratio from the approximately adiabatic moist-air energy balance
 * \(h(T_{out},\omega_{out})=h(T_{pad},\omega_{pad})\) using
 * [Psychrometrics.moistAirEnthalpyJPerKgDryAir], then RH from
 * [Psychrometrics.displayedRelativeHumidityPercent]. Supersaturation is prevented
 * by capping \(\omega\) at \(\omega_{sat}(T_{pad})\) (the same saturation limit the
 * solver later enforces in [ScalarTransportSolver.correctSupersaturation]).
 */
object EvaporativePadOutlet {
    data class Result(
        val temperatureC: Double,
        val humidityRatioKgKg: Double,
        val relativeHumidityPercent: Double,
        val wetBulbTemperatureC: Double,
        val outsideHumidityRatioKgKg: Double,
        val efficiencyFraction: Double,
        val wetPadActive: Boolean
    )

    fun evaluate(
        outsideTemperatureC: Double,
        outsideRhPercent: Double,
        pressurePa: Double,
        specificHeatJkgK: Double,
        efficiencyFraction: Double?,
        wetPadActive: Boolean
    ): Result {
        if (!outsideTemperatureC.isFinite() || !outsideRhPercent.isFinite()) {
            throw SimulationException("Outside T/RH for the pad outlet must be finite")
        }
        if (outsideRhPercent < 0.0) {
            throw SimulationException("Outside RH must be >= 0%")
        }
        Psychrometrics.validateTemperature(outsideTemperatureC)
        Psychrometrics.validatePressure(pressurePa)
        val omegaOut = Psychrometrics.humidityRatioKgKg(
            outsideTemperatureC,
            outsideRhPercent.coerceAtMost(100.0),
            pressurePa
        )
        val wetBulb = Psychrometrics.wetBulbTemperatureC(
            outsideTemperatureC,
            outsideRhPercent.coerceIn(0.0, 100.0),
            pressurePa,
            specificHeatJkgK
        )
        if (!wetPadActive) {
            return dryResult(
                outsideTemperatureC,
                omegaOut,
                pressurePa,
                wetBulb
            )
        }
        val eta = efficiencyFraction
        if (eta == null || !eta.isFinite() || eta <= 0.0 || eta > 1.0) {
            throw SimulationException(
                "Wet-pad cooling requires a finite pad efficiency in (0, 1], got $eta"
            )
        }
        val drop = eta * (outsideTemperatureC - wetBulb)
        val tPad = (outsideTemperatureC - drop).coerceAtLeast(wetBulb)
        if (!tPad.isFinite()) {
            throw SimulationException("Pad-outlet temperature is not finite")
        }
        if (tPad < wetBulb - 1e-9) {
            throw SimulationException("Pad cooling cannot go below the outside wet-bulb temperature")
        }
        val hIn = Psychrometrics.moistAirEnthalpyJPerKgDryAir(
            outsideTemperatureC,
            omegaOut,
            specificHeatJkgK
        )
        val hfgPad = Psychrometrics.latentHeatVaporizationJkg(tPad)
        var omegaPad = (hIn - specificHeatJkgK * tPad) / hfgPad
        if (!omegaPad.isFinite() || omegaPad < 0.0) {
            throw SimulationException("Pad-outlet humidity ratio is not finite and >= 0 kg/kg")
        }
        if (omegaPad < omegaOut) {
            omegaPad = omegaOut
        }
        val omegaSat = Psychrometrics.saturationHumidityRatioKgKg(tPad, pressurePa)
        if (omegaPad > omegaSat) {
            omegaPad = omegaSat
        }
        val rh = Psychrometrics.displayedRelativeHumidityPercent(tPad, omegaPad, pressurePa)
        return Result(
            temperatureC = tPad,
            humidityRatioKgKg = omegaPad,
            relativeHumidityPercent = rh.coerceIn(0.0, 100.0),
            wetBulbTemperatureC = wetBulb,
            outsideHumidityRatioKgKg = omegaOut,
            efficiencyFraction = eta,
            wetPadActive = true
        )
    }

    private fun dryResult(
        outsideTemperatureC: Double,
        omegaOut: Double,
        pressurePa: Double,
        wetBulb: Double
    ): Result {
        val rh = Psychrometrics.displayedRelativeHumidityPercent(
            outsideTemperatureC,
            omegaOut,
            pressurePa
        )
        return Result(
            temperatureC = outsideTemperatureC,
            humidityRatioKgKg = omegaOut,
            relativeHumidityPercent = rh.coerceIn(0.0, 100.0),
            wetBulbTemperatureC = wetBulb,
            outsideHumidityRatioKgKg = omegaOut,
            efficiencyFraction = 0.0,
            wetPadActive = false
        )
    }
}
