package com.greenhands.app.heat.simulation

import com.greenhands.app.heat.simulation.model.EquipmentDriveMode
import com.greenhands.app.heat.simulation.model.ForcedEquipmentState
import com.greenhands.app.heat.simulation.model.IntakeOpening
import com.greenhands.app.heat.simulation.model.Vec3
import com.greenhands.app.heat.simulation.physics.GreenhouseSimulationEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.exp

class ExhaustTest {

    @Test
    fun coolerOutsideAirCoolsTowardOutdoorConditionMonotonically() {
        val q = 0.24
        val geometry = TestEquipmentFixtures.boxGeometry(4.0, 3.0, 2.0, 1.0)
        val scenario = TestEquipmentFixtures.scenario(
            geometry = geometry,
            indoorTemperatureC = 30.0,
            indoorRhPercent = 50.0,
            outsideTemperatureC = 20.0,
            outsideRhPercent = 50.0,
            exhaustFans = listOf(TestEquipmentFixtures.testExhaustFan(ratedAirflowM3s = q)),
            driveMode = EquipmentDriveMode.FORCED,
            forced = ForcedEquipmentState(exhaustOn = true),
            alphaM2s = 0.0,
            dEffM2s = 0.0,
            dtS = 1.0
        )
        val engine = GreenhouseSimulationEngine.create(scenario)
        val volume = engine.gridState().totalActiveVolumeM3()
        var previous = engine.gridState().meanTemperatureC()
        var tSim = 0.0
        repeat(40) {
            val frame = engine.step(1.0)
            tSim += 1.0
            val mean = frame.diagnostics.wholeAirMeanTemperatureC
            assertTrue("mean must fall toward 20 °C: $previous → $mean", mean <= previous + 1e-12)
            assertTrue(mean >= 20.0 - 1e-9)
            previous = mean
        }
        val expected = 20.0 + 10.0 * exp(-q / volume * tSim)
        // Exact integrating-factor well-mixed update; 1e-9 °C is round-off.
        assertEquals(expected, previous, 1e-9)
        assertTrue(engine.step(1.0).diagnostics.wellMixedVentilation)
    }

    @Test
    fun hotterOutsideAirDoesNotFalselyCoolTheGreenhouse() {
        val scenario = TestEquipmentFixtures.scenario(
            geometry = TestEquipmentFixtures.boxGeometry(4.0, 3.0, 2.0, 1.0),
            indoorTemperatureC = 25.0,
            indoorRhPercent = 50.0,
            outsideTemperatureC = 35.0,
            outsideRhPercent = 50.0,
            exhaustFans = listOf(TestEquipmentFixtures.testExhaustFan(ratedAirflowM3s = 0.24)),
            driveMode = EquipmentDriveMode.FORCED,
            forced = ForcedEquipmentState(exhaustOn = true),
            alphaM2s = 0.0,
            dEffM2s = 0.0
        )
        val engine = GreenhouseSimulationEngine.create(scenario)
        val t0 = engine.gridState().meanTemperatureC()
        val frame = engine.step(2.0)
        assertTrue(
            "hotter outdoor air must not reduce mean T ($t0 → ${frame.diagnostics.wholeAirMeanTemperatureC})",
            frame.diagnostics.wholeAirMeanTemperatureC >= t0 - 1e-12
        )
    }

    @Test
    fun intakeAndOutletVolumeFlowsBalance() {
        val exhaust = TestEquipmentFixtures.testExhaustFan(Vec3(3.5, 1.5, 1.0), 0.30)
        val intake = IntakeOpening(
            id = "TestIntake",
            positionM = Vec3(0.4, 1.5, 1.0),
            inwardNormal = Vec3(1.0, 0.0, 0.0),
            areaM2 = 0.50
        )
        val scenario = TestEquipmentFixtures.scenario(
            geometry = TestEquipmentFixtures.boxGeometry(4.0, 3.0, 2.0, 0.50),
            exhaustFans = listOf(exhaust),
            intakes = listOf(intake),
            driveMode = EquipmentDriveMode.FORCED,
            forced = ForcedEquipmentState(exhaustOn = true)
        )
        val frame = GreenhouseSimulationEngine.create(scenario).step(0.25)
        assertEquals(frame.diagnostics.exhaustVolumeFlowM3s, frame.diagnostics.intakeVolumeFlowM3s, 1e-12)
        assertTrue(frame.diagnostics.volumeFlowBalanced)
        assertFalse(frame.diagnostics.wellMixedVentilation)
    }

    @Test
    fun wellMixedFallbackMatchesAnalyticalExponentialWithin1e9C() {
        val q = 0.20
        val scenario = TestEquipmentFixtures.scenario(
            geometry = TestEquipmentFixtures.boxGeometry(4.0, 2.0, 2.0, 1.0),
            indoorTemperatureC = 32.0,
            indoorRhPercent = 40.0,
            outsideTemperatureC = 22.0,
            outsideRhPercent = 40.0,
            exhaustFans = listOf(TestEquipmentFixtures.testExhaustFan(ratedAirflowM3s = q)),
            driveMode = EquipmentDriveMode.FORCED,
            forced = ForcedEquipmentState(exhaustOn = true),
            alphaM2s = 0.0,
            dEffM2s = 0.0,
            dtS = 2.0
        )
        val engine = GreenhouseSimulationEngine.create(scenario)
        val v = engine.gridState().totalActiveVolumeM3()
        var t = 0.0
        repeat(25) {
            engine.step(2.0)
            t += 2.0
        }
        val expected = 22.0 + 10.0 * exp(-q / v * t)
        assertEquals(expected, engine.gridState().meanTemperatureC(), 1e-9)
    }
}
