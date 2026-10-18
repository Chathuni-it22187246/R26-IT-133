package com.greenhands.app.heat.simulation

import com.greenhands.app.heat.simulation.data.PreviewSimulationEnvironmentProvider
import com.greenhands.app.heat.simulation.data.SimulationDataQuality
import com.greenhands.app.heat.simulation.model.ProvenanceClass
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SimulationEnvironmentProviderTest {

    @Test
    fun previewSnapshotsAreDeterministicWithoutServerTimestamp() = runTest {
        val provider = PreviewSimulationEnvironmentProvider()
        val first = provider.snapshots.first()
        val second = provider.snapshots.first()
        assertEquals(first, second)
        assertEquals(SimulationDataQuality.PREVIEW, first.quality)
        assertEquals(25.0, first.temperatureC!!, 0.0)
        assertEquals(70.0, first.relativeHumidityPercent!!, 0.0)
        assertNull(first.serverTimestampMillis)
        assertNull(first.sensorId)
        assertNull(first.greenhouseId)
    }

    @Test
    fun scenarioRecordsControlRulesAndCalibratedCoefficients() {
        val scenario = TestEquipmentFixtures.scenario(toptC = 25.0, rhoptPercent = 70.0)
        val classes = scenario.parameterRecords.map { it.provenance }.toSet()
        assertTrue(classes.contains(ProvenanceClass.PROJECT_CONTROL_RULE))
        assertTrue(classes.contains(ProvenanceClass.CALIBRATED_COEFFICIENT))
        val control = scenario.parameterRecords.filter { it.provenance == ProvenanceClass.PROJECT_CONTROL_RULE }
        assertTrue(control.any { it.name.contains("CSP") })
        assertTrue(control.any { it.name.contains("FSP") })
    }
}
