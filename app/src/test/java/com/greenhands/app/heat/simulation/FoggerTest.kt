package com.greenhands.app.heat.simulation

import com.greenhands.app.heat.simulation.model.EquipmentDriveMode
import com.greenhands.app.heat.simulation.model.ForcedEquipmentState
import com.greenhands.app.heat.simulation.physics.GreenhouseSimulationEngine
import com.greenhands.app.heat.simulation.physics.Psychrometrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class FoggerTest {

    @Test
    fun evaporatedMassIncreasesHumidityRatio() {
        val engine = foggerEngine(waterFlowKgS = 0.002, efficiency = 0.50)
        val w0 = engine.gridState().meanHumidityRatioKgKg()
        val frame = engine.step(0.50)
        assertTrue(engine.gridState().meanHumidityRatioKgKg() > w0)
        assertTrue(frame.diagnostics.finiteValues)
    }

    @Test
    fun latentHeatRemovalEqualsEvaporatedMassTimesHfgWithin0p3Percent() {
        val t0 = 32.0
        val engine = foggerEngine(waterFlowKgS = 0.001, efficiency = 1.0, indoorC = t0, indoorRh = 40.0)
        val grid = engine.gridState()
        val properties = com.greenhands.app.heat.simulation.physics.AirProperties.chen2014Preview()
        val e0 = grid.sensibleEnergyJ(properties.airDensityKgM3, properties.specificHeatJkgK)
        val m0 = grid.vapourMassKg(properties.dryAirDensityKgM3)
        engine.step(0.25)
        val e1 = grid.sensibleEnergyJ(properties.airDensityKgM3, properties.specificHeatJkgK)
        val m1 = grid.vapourMassKg(properties.dryAirDensityKgM3)
        val dm = m1 - m0
        val dE = e1 - e0
        assertTrue("evaporated mass must increase", dm > 0.0)
        val hfg = Psychrometrics.latentHeatVaporizationJkg(t0)
        val expected = -dm * hfg
        val rel = abs(dE - expected) / abs(expected)
        // Explicit Euler + h_fg(T) evaluated per cell; 0.3% covers the T-dependence of h_fg
        // over a fraction-of-a-degree cooling step, not a weakened conservation statement.
        assertTrue("relative latent/sensible mismatch $rel", rel < 0.003)
        assertEquals(expected, dE, abs(expected) * 0.003)
    }

    @Test
    fun nonEvaporatedWaterCausesNoCooling() {
        val engine = foggerEngine(waterFlowKgS = 0.003, efficiency = 0.0, indoorC = 33.0, indoorRh = 40.0)
        val t0 = engine.gridState().meanTemperatureC()
        val w0 = engine.gridState().meanHumidityRatioKgKg()
        engine.step(1.0)
        assertEquals(t0, engine.gridState().meanTemperatureC(), 1e-12)
        assertEquals(w0, engine.gridState().meanHumidityRatioKgKg(), 1e-12)
    }

    @Test
    fun saturationCorrectionPreventsRhAbove100Percent() {
        val engine = foggerEngine(
            waterFlowKgS = 0.02,
            efficiency = 1.0,
            indoorC = 30.0,
            indoorRh = 99.0
        )
        var sawCorrection = false
        repeat(8) {
            val frame = engine.step(0.25)
            val rh = frame.diagnostics.wholeAirMeanRhPercent
            assertTrue("displayed RH must stay <= 100%, got $rh", rh <= 100.0000001)
            engine.gridState().forEachActive { idx, _, _, _ ->
                val t = engine.gridState().temperatureC[idx]
                val w = engine.gridState().humidityRatioKgKg[idx]
                val internalRh = Psychrometrics.relativeHumidityPercent(t, w, 101325.0)
                assertTrue(internalRh <= 100.0001)
            }
            if (frame.diagnostics.saturation.applied) sawCorrection = true
        }
        assertTrue("saturation correction must be reported", sawCorrection)
    }

    @Test
    fun zeroWaterFlowHasZeroEffect() {
        val engine = foggerEngine(waterFlowKgS = 0.0, efficiency = 1.0, indoorC = 31.0, indoorRh = 45.0)
        val t0 = engine.gridState().meanTemperatureC()
        val w0 = engine.gridState().meanHumidityRatioKgKg()
        engine.step(1.0)
        assertEquals(t0, engine.gridState().meanTemperatureC(), 1e-12)
        assertEquals(w0, engine.gridState().meanHumidityRatioKgKg(), 1e-12)
    }

    private fun foggerEngine(
        waterFlowKgS: Double,
        efficiency: Double,
        indoorC: Double = 32.0,
        indoorRh: Double = 45.0
    ): GreenhouseSimulationEngine {
        val scenario = TestEquipmentFixtures.scenario(
            geometry = TestEquipmentFixtures.boxGeometry(3.0, 3.0, 2.5, 0.50),
            indoorTemperatureC = indoorC,
            indoorRhPercent = indoorRh,
            foggers = listOf(
                TestEquipmentFixtures.testFogger(
                    waterFlowKgS = waterFlowKgS,
                    evaporationEfficiency = efficiency
                )
            ),
            driveMode = EquipmentDriveMode.FORCED,
            forced = ForcedEquipmentState(foggerOn = true),
            alphaM2s = 0.0,
            dEffM2s = 0.0
        )
        return GreenhouseSimulationEngine.create(scenario)
    }
}
