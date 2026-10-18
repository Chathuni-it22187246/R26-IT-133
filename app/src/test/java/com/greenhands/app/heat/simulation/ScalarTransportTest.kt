package com.greenhands.app.heat.simulation

import com.greenhands.app.heat.simulation.model.EquipmentDriveMode
import com.greenhands.app.heat.simulation.model.ForcedEquipmentState
import com.greenhands.app.heat.simulation.model.Vec3
import com.greenhands.app.heat.simulation.physics.GreenhouseSimulationEngine
import com.greenhands.app.heat.simulation.physics.SimulationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ScalarTransportTest {

    @Test
    fun uniformClosedFieldRemainsUnchangedWithoutSources() {
        val scenario = TestEquipmentFixtures.scenario(
            geometry = TestEquipmentFixtures.boxGeometry(3.0, 2.0, 2.0, 0.50),
            indoorTemperatureC = 28.0,
            indoorRhPercent = 55.0,
            alphaM2s = 0.02,
            dEffM2s = 0.02
        )
        val engine = GreenhouseSimulationEngine.create(scenario)
        val t0 = engine.gridState().meanTemperatureC()
        val w0 = engine.gridState().meanHumidityRatioKgKg()
        repeat(20) { engine.step(0.25) }
        assertEquals(t0, engine.gridState().meanTemperatureC(), 1e-12)
        assertEquals(w0, engine.gridState().meanHumidityRatioKgKg(), 1e-12)
    }

    @Test
    fun circulationOnlyConservesVolumeWeightedMeanSensibleEnergyWithin1e8C() {
        // Conservative face-flux upwind + no-flux walls: mean T should be unchanged
        // to round-off. Tolerance 1e-8 °C is ~1e-10 relative for a 30 °C field.
        val scenario = TestEquipmentFixtures.scenario(
            geometry = TestEquipmentFixtures.boxGeometry(4.0, 3.0, 2.5, 0.50),
            indoorTemperatureC = 30.0,
            circulationFans = listOf(TestEquipmentFixtures.testCirculationFan()),
            driveMode = EquipmentDriveMode.FORCED,
            forced = ForcedEquipmentState(circulationOn = true),
            alphaM2s = 0.03,
            dEffM2s = 0.03
        )
        val engine = GreenhouseSimulationEngine.create(scenario)
        val grid = engine.gridState()
        val hot = grid.nearestActiveIndex(Vec3(1.5, 1.5, 1.0))!!
        grid.temperatureC[hot] = 40.0
        val mean0 = grid.meanTemperatureC()
        val air = scenario.airProperties
        val e0 = grid.sensibleEnergyJ(air.airDensityKgM3, air.specificHeatJkgK)
        repeat(40) { engine.step(0.20) }
        val mean1 = grid.meanTemperatureC()
        val e1 = grid.sensibleEnergyJ(air.airDensityKgM3, air.specificHeatJkgK)
        assertEquals("mean T conserved with circulation only", mean0, mean1, 1e-8)
        val relEnergy = abs(e1 - e0) / abs(e0)
        assertTrue("relative sensible-energy drift $relEnergy", relEnergy < 1e-10)
        assertTrue("hot cell should mix downward", grid.temperatureC[hot] < 40.0)
    }

    @Test
    fun diffusionSmoothsHotCellWithoutUnexplainedEnergyGainOrLoss() {
        val scenario = TestEquipmentFixtures.scenario(
            geometry = TestEquipmentFixtures.boxGeometry(3.0, 3.0, 2.0, 0.50),
            indoorTemperatureC = 25.0,
            alphaM2s = 0.05,
            dEffM2s = 0.0
        )
        val engine = GreenhouseSimulationEngine.create(scenario)
        val grid = engine.gridState()
        val idx = grid.index(2, 2, 2)
        grid.temperatureC[idx] = 35.0
        val mean0 = grid.meanTemperatureC()
        val neighbor = grid.index(3, 2, 2)
        val neighbor0 = grid.temperatureC[neighbor]
        repeat(15) { engine.step(0.20) }
        assertEquals("diffusion conserves mean T to 1e-10 °C", mean0, grid.meanTemperatureC(), 1e-10)
        assertTrue(grid.temperatureC[idx] < 35.0)
        assertTrue(grid.temperatureC[neighbor] > neighbor0)
    }

    @Test
    fun advectionMovesScalarInConfiguredPlusXDirection() {
        val scenario = TestEquipmentFixtures.scenario(
            geometry = TestEquipmentFixtures.boxGeometry(6.0, 2.0, 2.0, 0.50),
            indoorTemperatureC = 20.0,
            alphaM2s = 0.0,
            dEffM2s = 0.0,
            velocityOverride = Vec3(0.50, 0.0, 0.0),
            dtS = 0.20
        )
        val engine = GreenhouseSimulationEngine.create(scenario)
        val grid = engine.gridState()
        val blob = grid.index(2, 2, 2)
        grid.temperatureC[blob] = 30.0
        val com0 = temperatureAnomalyCenterXM(grid, 20.0)
        repeat(10) { engine.step(0.20) }
        val com1 = temperatureAnomalyCenterXM(grid, 20.0)
        // 2 s at 0.50 m/s → 1.0 m; first-order upwind is diffusive, so require
        // a clear +x shift of at least 0.3 m rather than an exact 1 m.
        assertTrue("anomaly COM moved +x: $com0 → $com1", com1 > com0 + 0.30)
    }

    @Test
    fun repeatedRunsAreDeterministic() {
        val scenario = TestEquipmentFixtures.scenario(
            geometry = TestEquipmentFixtures.boxGeometry(3.0, 2.0, 2.0, 0.50),
            circulationFans = listOf(TestEquipmentFixtures.testCirculationFan(Vec3(0.5, 1.0, 1.0))),
            driveMode = EquipmentDriveMode.FORCED,
            forced = ForcedEquipmentState(circulationOn = true)
        )
        val a = GreenhouseSimulationEngine.create(scenario)
        val b = GreenhouseSimulationEngine.create(scenario)
        a.gridState().temperatureC[a.gridState().index(2, 1, 1)] = 33.0
        b.gridState().temperatureC[b.gridState().index(2, 1, 1)] = 33.0
        repeat(12) {
            a.step(0.25)
            b.step(0.25)
        }
        val ga = a.gridState()
        val gb = b.gridState()
        ga.forEachActive { idx, _, _, _ ->
            assertEquals(ga.temperatureC[idx], gb.temperatureC[idx], 0.0)
            assertEquals(ga.humidityRatioKgKg[idx], gb.humidityRatioKgKg[idx], 0.0)
            assertEquals(ga.velocityXMps[idx], gb.velocityXMps[idx], 0.0)
        }
    }

    @Test
    fun largeRequestedStepIsHandledBySubsteps() {
        val scenario = TestEquipmentFixtures.scenario(
            geometry = TestEquipmentFixtures.boxGeometry(3.0, 2.0, 2.0, 0.50),
            velocityOverride = Vec3(2.0, 0.0, 0.0),
            alphaM2s = 0.02,
            dtS = 0.25
        )
        val engine = GreenhouseSimulationEngine.create(scenario)
        val frame = engine.step(1.0)
        assertTrue(frame.diagnostics.substepCount > 1)
        assertTrue(frame.diagnostics.finiteValues)
    }

    @Test(expected = SimulationException::class)
    fun unsafeRequestedTimeStepIsRejected() {
        val scenario = TestEquipmentFixtures.scenario(
            geometry = TestEquipmentFixtures.boxGeometry(3.0, 2.0, 2.0, 0.50),
            velocityOverride = Vec3(4.0, 0.0, 0.0),
            alphaM2s = 0.05,
            dtS = 0.25
        )
        val engine = GreenhouseSimulationEngine.create(scenario)
        engine.step(1.0e9)
    }

    @Test(expected = SimulationException::class)
    fun nonPositiveRequestedTimeStepIsRejected() {
        val scenario = TestEquipmentFixtures.scenario(
            geometry = TestEquipmentFixtures.boxGeometry(3.0, 2.0, 2.0, 0.50)
        )
        GreenhouseSimulationEngine.create(scenario).step(0.0)
    }

    private fun temperatureAnomalyCenterXM(
        grid: com.greenhands.app.heat.simulation.model.GridState,
        backgroundC: Double
    ): Double {
        var acc = 0.0
        var mass = 0.0
        grid.forEachActive { idx, i, j, k ->
            val d = grid.temperatureC[idx] - backgroundC
            if (d > 0.0) {
                acc += d * grid.cellCenterM(i, j, k).xM
                mass += d
            }
        }
        return acc / mass
    }
}
