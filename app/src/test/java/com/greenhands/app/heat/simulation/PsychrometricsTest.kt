package com.greenhands.app.heat.simulation

import com.greenhands.app.heat.simulation.physics.Psychrometrics
import com.greenhands.app.heat.simulation.physics.SimulationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PsychrometricsTest {

    private val p = 101_325.0

    @Test
    fun rhToHumidityRatioToRhRoundTripOverWarmGreenhouseConditions() {
        // Algebraic inverse; tolerance is round-off, not a fitted model error.
        val temps = doubleArrayOf(15.0, 20.0, 25.0, 30.0, 35.0, 40.0)
        val rhs = doubleArrayOf(30.0, 50.0, 70.0, 90.0, 95.0)
        temps.forEach { tC ->
            rhs.forEach { rh ->
                val omega = Psychrometrics.humidityRatioKgKg(tC, rh, p)
                val rhBack = Psychrometrics.relativeHumidityPercent(tC, omega, p)
                assertEquals("round trip at ${tC}°C $rh% RH", rh, rhBack, 1e-9)
            }
        }
    }

    @Test
    fun saturationHumidityRatioYieldsApproximately100PercentRh() {
        val tC = 30.0
        val omegaSat = Psychrometrics.saturationHumidityRatioKgKg(tC, p)
        val rh = Psychrometrics.relativeHumidityPercent(tC, omegaSat, p)
        assertEquals(100.0, rh, 1e-9)
    }

    @Test
    fun vpdDecreasesAsRhIncreasesAtConstantTemperature() {
        val tC = 28.0
        val vpd30 = Psychrometrics.vapourPressureDeficitKpa(tC, 30.0)
        val vpd60 = Psychrometrics.vapourPressureDeficitKpa(tC, 60.0)
        val vpd90 = Psychrometrics.vapourPressureDeficitKpa(tC, 90.0)
        assertTrue(vpd30 > vpd60 && vpd60 > vpd90)
        assertTrue(vpd90 > 0.0)
    }

    @Test(expected = SimulationException::class)
    fun invalidNegativePressureIsRejected() {
        Psychrometrics.humidityRatioKgKg(25.0, 50.0, -1.0)
    }

    @Test(expected = SimulationException::class)
    fun invalidZeroPressureIsRejected() {
        Psychrometrics.saturationVapourPressurePa(25.0).let {
            Psychrometrics.humidityRatioKgKg(25.0, 50.0, 0.0)
        }
    }

    @Test(expected = SimulationException::class)
    fun invalidTemperatureBelowRangeIsRejected() {
        Psychrometrics.saturationVapourPressurePa(-50.0)
    }

    @Test(expected = SimulationException::class)
    fun invalidTemperatureNanIsRejected() {
        Psychrometrics.relativeHumidityPercent(Double.NaN, 0.01, p)
    }

    @Test(expected = SimulationException::class)
    fun invalidNegativeRhIsRejected() {
        Psychrometrics.humidityRatioKgKg(25.0, -1.0, p)
    }

    @Test
    fun latentHeatApproximationMatchesNistSaturationValuesWithin1KjPerKg() {
        // NIST/IAPWS saturation enthalpy of vaporization, kJ/kg (PHYSICS_MODEL.md §9.3, §21).
        // Tolerance 1 kJ/kg (~0.04%) is the documented engineering-approximation error,
        // not a loosened solver tolerance.
        val nistKjPerKg = mapOf(
            0.0 to 2500.9,
            20.0 to 2453.5,
            25.0 to 2441.7,
            40.0 to 2406.0,
            50.0 to 2381.9
        )
        nistKjPerKg.forEach { (tC, nist) ->
            val model = Psychrometrics.latentHeatVaporizationJkg(tC) / 1000.0
            assertEquals("h_fg at ${tC}°C vs NIST", nist, model, 1.0)
        }
    }

    @Test
    fun wetBulbMatchesAdiabaticSaturationEnthalpy() {
        val tDb = 35.0
        val rh = 40.0
        val cp = 1005.0
        val twb = Psychrometrics.wetBulbTemperatureC(tDb, rh, p, cp)
        assertTrue(twb <= tDb + 1e-9)
        val omega = Psychrometrics.humidityRatioKgKg(tDb, rh, p)
        val hAir = Psychrometrics.moistAirEnthalpyJPerKgDryAir(tDb, omega, cp)
        val omegaSat = Psychrometrics.saturationHumidityRatioKgKg(twb, p)
        val hSat = Psychrometrics.moistAirEnthalpyJPerKgDryAir(twb, omegaSat, cp)
        assertEquals(hAir, hSat, 50.0)
        assertEquals(tDb, Psychrometrics.wetBulbTemperatureC(tDb, 100.0, p, cp), 1e-9)
    }

    @Test
    fun coolingAtConstantHumidityRatioIncreasesRh() {
        val omega = Psychrometrics.humidityRatioKgKg(35.0, 40.0, p)
        val rhHot = Psychrometrics.displayedRelativeHumidityPercent(35.0, omega, p)
        val rhCool = Psychrometrics.displayedRelativeHumidityPercent(26.0, omega, p)
        assertTrue(rhCool > rhHot)
        assertTrue(rhCool <= 100.0)
    }
}
