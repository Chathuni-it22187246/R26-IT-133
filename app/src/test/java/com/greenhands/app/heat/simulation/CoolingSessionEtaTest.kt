package com.greenhands.app.heat.simulation

import com.greenhands.app.heat.simulation.control.CoolingSessionState
import com.greenhands.app.heat.simulation.control.CoolingSessionTracker
import com.greenhands.app.heat.simulation.control.EtaForecaster
import com.greenhands.app.heat.simulation.model.EquipmentDriveMode
import com.greenhands.app.heat.simulation.model.ForcedEquipmentState
import com.greenhands.app.heat.simulation.physics.GreenhouseSimulationEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoolingSessionEtaTest {

    @Test
    fun timerStartsOnFirstEquipmentActivation() {
        val scenario = TestEquipmentFixtures.scenario(
            indoorTemperatureC = 20.0,
            indoorRhPercent = 70.0,
            toptC = 25.0,
            rhoptPercent = 70.0,
            driveMode = EquipmentDriveMode.CONTROLLER
        )
        val engine = GreenhouseSimulationEngine.create(scenario)
        val idle = engine.step(1.0)
        assertEquals(CoolingSessionState.IDLE, idle.sessionState)
        assertEquals(0.0, idle.elapsedCoolingS, 0.0)
        engine.gridState().fillUniform(28.0, engine.gridState().meanHumidityRatioKgKg())
        val started = engine.step(1.0)
        assertTrue(started.command.circulationOn)
        assertEquals(CoolingSessionState.COOLING, started.sessionState)
        assertEquals(1.0, started.elapsedCoolingS, 1e-12)
    }

    @Test
    fun stabilizationRequires30ContinuousSeconds() {
        val tracker = CoolingSessionTracker(30.0)
        assertFalse(tracker.update(10.0, anyEquipmentOn = true, inConfiguredBand = true))
        assertEquals(CoolingSessionState.STABILIZING, tracker.state)
        assertEquals(10.0, tracker.stabilityElapsedS, 0.0)
        assertFalse(tracker.update(10.0, true, true))
        assertFalse(tracker.update(9.9, true, true))
        assertEquals(CoolingSessionState.STABILIZING, tracker.state)
        val completed = tracker.update(0.2, true, true)
        assertTrue(completed)
        assertEquals(CoolingSessionState.COMPLETE, tracker.state)
    }

    @Test
    fun leavingTheBandResetsStabilization() {
        val tracker = CoolingSessionTracker(30.0)
        tracker.update(20.0, true, true)
        assertEquals(20.0, tracker.stabilityElapsedS, 0.0)
        tracker.update(1.0, true, false)
        assertEquals(CoolingSessionState.COOLING, tracker.state)
        assertEquals(0.0, tracker.stabilityElapsedS, 0.0)
        tracker.update(20.0, true, true)
        assertEquals(CoolingSessionState.STABILIZING, tracker.state)
        assertEquals(20.0, tracker.stabilityElapsedS, 0.0)
        assertFalse(tracker.state == CoolingSessionState.COMPLETE)
    }

    @Test
    fun completionEventFiresOnceOnly() {
        val scenario = inBandForcedScenario()
        val engine = GreenhouseSimulationEngine.create(scenario)
        var completions = 0
        var lastEvent = false
        repeat(40) {
            val frame = engine.step(1.0)
            if (frame.completionEvent) completions++
            lastEvent = frame.completionEvent
        }
        assertEquals(1, completions)
        assertEquals(CoolingSessionState.COMPLETE, engine.sessionState())
        val after = engine.step(1.0)
        assertFalse(after.completionEvent)
        assertFalse(lastEvent && after.completionEvent)
    }

    @Test
    fun etaCompletesOnceWhenBandIsReachable() {
        val scenario = TestEquipmentFixtures.scenario(
            geometry = TestEquipmentFixtures.boxGeometry(4.0, 2.0, 2.0, 1.0),
            indoorTemperatureC = 32.0,
            indoorRhPercent = 70.0,
            outsideTemperatureC = 25.0,
            outsideRhPercent = 70.0,
            toptC = 25.0,
            rhoptPercent = 70.0,
            exhaustFans = listOf(TestEquipmentFixtures.testExhaustFan(ratedAirflowM3s = 0.40)),
            driveMode = EquipmentDriveMode.FORCED,
            forced = ForcedEquipmentState(exhaustOn = true),
            alphaM2s = 0.0,
            dEffM2s = 0.0,
            dtS = 2.0,
            horizonS = 400.0
        )
        val engine = GreenhouseSimulationEngine.create(scenario)
        val eta = EtaForecaster.forecast(engine, horizonS = 400.0, stepS = 2.0)
        assertEquals(CoolingSessionState.COMPLETE, eta.state)
        assertNotNull(eta.etaS)
        assertTrue(eta.etaS!! >= 30.0)
        assertEquals(CoolingSessionState.IDLE, engine.sessionState())
        val etaAgain = EtaForecaster.forecast(engine, horizonS = 400.0, stepS = 2.0)
        assertEquals(eta.etaS!!, etaAgain.etaS!!, 1e-9)
    }

    @Test
    fun etaReportsUnreachableWhenHorizonIsExhausted() {
        val scenario = TestEquipmentFixtures.scenario(
            geometry = TestEquipmentFixtures.boxGeometry(4.0, 2.0, 2.0, 1.0),
            indoorTemperatureC = 35.0,
            indoorRhPercent = 70.0,
            outsideTemperatureC = 40.0,
            outsideRhPercent = 70.0,
            toptC = 25.0,
            rhoptPercent = 70.0,
            exhaustFans = listOf(TestEquipmentFixtures.testExhaustFan(ratedAirflowM3s = 0.40)),
            driveMode = EquipmentDriveMode.FORCED,
            forced = ForcedEquipmentState(exhaustOn = true),
            alphaM2s = 0.0,
            dEffM2s = 0.0,
            dtS = 2.0,
            horizonS = 40.0
        )
        val engine = GreenhouseSimulationEngine.create(scenario)
        val eta = EtaForecaster.forecast(engine, horizonS = 40.0, stepS = 2.0)
        assertEquals(CoolingSessionState.UNREACHABLE, eta.state)
        assertNull(eta.etaS)
        assertEquals(EtaForecaster.UNREACHABLE_MESSAGE, eta.label)
        assertEquals(CoolingSessionState.IDLE, engine.sessionState())
    }

    @Test
    fun cancelDoesNotEmitASecondCompletion() {
        val tracker = CoolingSessionTracker(30.0)
        assertTrue(tracker.update(30.0, true, true))
        assertFalse(tracker.update(1.0, true, true))
        tracker.cancel()
        assertEquals(CoolingSessionState.CANCELLED, tracker.state)
        assertFalse(tracker.update(1.0, true, true))
    }

    private fun inBandForcedScenario() = TestEquipmentFixtures.scenario(
        geometry = TestEquipmentFixtures.boxGeometry(3.0, 2.0, 2.0, 1.0),
        indoorTemperatureC = 25.0,
        indoorRhPercent = 70.0,
        toptC = 25.0,
        rhoptPercent = 70.0,
        circulationFans = listOf(TestEquipmentFixtures.testCirculationFan()),
        driveMode = EquipmentDriveMode.FORCED,
        forced = ForcedEquipmentState(circulationOn = true),
        alphaM2s = 0.0,
        dEffM2s = 0.0,
        dtS = 1.0
    )
}
