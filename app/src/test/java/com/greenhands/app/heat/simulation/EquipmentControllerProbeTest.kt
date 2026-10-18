package com.greenhands.app.heat.simulation

import com.greenhands.app.heat.domain.HeatFormulas
import com.greenhands.app.heat.simulation.control.EquipmentCommandSource
import com.greenhands.app.heat.simulation.control.EquipmentController
import com.greenhands.app.heat.simulation.model.ControlSource
import com.greenhands.app.heat.simulation.model.EquipmentDriveMode
import com.greenhands.app.heat.simulation.model.ForcedEquipmentState
import com.greenhands.app.heat.simulation.model.Vec3
import com.greenhands.app.heat.simulation.physics.GreenhouseSimulationEngine
import com.greenhands.app.heat.simulation.probe.DistanceProbeSampler
import com.greenhands.app.heat.simulation.probe.ProbeStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EquipmentControllerProbeTest {

    @Test
    fun exactTopt25AndRhopt70ThresholdsArePreserved() {
        val circ = HeatFormulas.circulation(25.0)
        val exhaust = HeatFormulas.exhaustFromCirculation(circ)
        val fogger = HeatFormulas.fogger(70.0)
        assertEquals(25.0, circ.csp, 0.0)
        assertEquals(23.0, circ.cdp, 0.0)
        assertEquals(27.0, circ.con, 0.0)
        assertEquals(29.0, exhaust.esp, 0.0)
        assertEquals(27.0, exhaust.edp, 0.0)
        assertEquals(31.0, exhaust.eon, 0.0)
        assertEquals(68.0, fogger.fon, 0.0)
        assertEquals(70.0, fogger.fsp, 0.0)
        assertEquals(72.0, fogger.fdp, 0.0)
        val controller = EquipmentController.fromTargets(25.0, 70.0, ControlSource.CROP_ZONE_MEAN)
        assertEquals(circ, controller.circulation)
        assertEquals(exhaust, controller.exhaust)
        assertEquals(fogger, controller.fogger)
    }

    @Test
    fun hysteresisPreventsChatterInsideDeadbands() {
        val c = EquipmentController.fromTargets(25.0, 70.0, ControlSource.GREENHOUSE_MEAN)
        c.update(25.0, 70.0)
        assertFalse(c.circulationOn)
        assertFalse(c.exhaustOn)
        assertFalse(c.foggerOn)
        c.update(26.9, 70.0)
        assertFalse("must stay OFF below CON", c.circulationOn)
        c.update(27.0, 70.0)
        assertTrue(c.circulationOn)
        c.update(24.0, 70.0)
        assertTrue("must stay ON above CDP", c.circulationOn)
        c.update(23.0, 70.0)
        assertFalse(c.circulationOn)
        c.update(30.9, 70.0)
        assertFalse("exhaust stays OFF below EON", c.exhaustOn)
        c.update(31.0, 70.0)
        assertTrue(c.exhaustOn)
        c.update(27.1, 70.0)
        assertTrue(c.exhaustOn)
        c.update(27.0, 70.0)
        assertFalse(c.exhaustOn)
        c.update(25.0, 68.1)
        assertFalse(c.foggerOn)
        c.update(25.0, 68.0)
        assertTrue(c.foggerOn)
        c.update(25.0, 71.0)
        assertTrue(c.foggerOn)
        c.update(25.0, 72.0)
        assertFalse(c.foggerOn)
    }

    @Test
    fun controlSourceIsExplicitOnDiagnostics() {
        val scenario = TestEquipmentFixtures.scenario(
            controlSource = ControlSource.GREENHOUSE_MEAN,
            driveMode = EquipmentDriveMode.FORCED,
            forced = ForcedEquipmentState()
        )
        val frame = GreenhouseSimulationEngine.create(scenario).step(0.25)
        assertEquals(ControlSource.GREENHOUSE_MEAN, frame.diagnostics.controlSource)
        assertEquals(EquipmentCommandSource.FORCED, frame.command.source)
        val controller = EquipmentController.fromTargets(25.0, 70.0, ControlSource.CONTROL_SENSOR)
        assertEquals(ControlSource.CONTROL_SENSOR, controller.controlSource)
        assertEquals(EquipmentCommandSource.CONTROLLER, controller.command().source)
    }

    @Test
    fun fanAxisProbesAt1To5mSampleKnownLinearField() {
        val fan = TestEquipmentFixtures.testCirculationFan(positionM = Vec3(0.5, 1.5, 1.0))
        val scenario = TestEquipmentFixtures.scenario(
            geometry = TestEquipmentFixtures.boxGeometry(8.0, 3.0, 2.5, 0.50),
            circulationFans = listOf(fan),
            driveMode = EquipmentDriveMode.FORCED,
            forced = ForcedEquipmentState(circulationOn = true),
            alphaM2s = 0.0,
            dEffM2s = 0.0
        )
        val engine = GreenhouseSimulationEngine.create(scenario)
        val grid = engine.gridState()
        grid.forEachActive { idx, i, j, k ->
            val c = grid.cellCenterM(i, j, k)
            grid.temperatureC[idx] = c.xM
            grid.velocityXMps[idx] = 1.0
            grid.velocityYMps[idx] = 0.0
            grid.velocityZMps[idx] = 0.0
        }
        val samples = DistanceProbeSampler.sampleFanAxis(grid, fan, 101325.0)
        assertEquals(5, samples.size)
        samples.forEach { sample ->
            assertEquals(ProbeStatus.PREDICTED, sample.status)
            val expectedX = fan.positionM.xM + sample.distanceM
            assertEquals(expectedX, sample.temperatureC!!, 1e-9)
            assertTrue(sample.airSpeedMps!! > 0.0)
        }
        val frame = engine.step(0.25)
        assertEquals(5, frame.probes.size)
    }

    @Test
    fun outsideProbesReturnOutsideGreenhouse() {
        val fan = TestEquipmentFixtures.testCirculationFan(
            positionM = Vec3(2.6, 1.5, 1.0),
            direction = Vec3(1.0, 0.0, 0.0)
        )
        val scenario = TestEquipmentFixtures.scenario(
            geometry = TestEquipmentFixtures.boxGeometry(3.0, 3.0, 2.0, 0.50),
            circulationFans = listOf(fan)
        )
        val grid = GreenhouseSimulationEngine.create(scenario).gridState()
        val samples = DistanceProbeSampler.sampleFanAxis(grid, fan, 101325.0)
        assertTrue(samples.any { it.status == ProbeStatus.OUTSIDE_GREENHOUSE })
        samples.filter { it.status == ProbeStatus.OUTSIDE_GREENHOUSE }.forEach {
            assertEquals(null, it.temperatureC)
            assertEquals(null, it.relativeHumidityPercent)
            assertEquals(null, it.airSpeedMps)
        }
    }
}
