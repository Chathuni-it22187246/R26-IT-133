package com.greenhands.app

import com.greenhands.app.sensor.domain.CoverageCalculator
import com.greenhands.app.sensor.model.CellCoverageState
import com.greenhands.app.sensor.model.Greenhouse
import com.greenhands.app.sensor.model.Sensor
import com.greenhands.app.sensor.model.SensorType
import com.greenhands.app.sensor.ui.coverageCellFill
import com.greenhands.app.sensor.ui.displayedMapCoverage
import com.greenhands.app.sensor.ui.displayedMapSensors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverageMapVisualizationTest {

    @Test
    fun coverageCellFillMapsEachStateToADistinctColor() {
        val covered = coverageCellFill(CellCoverageState.COVERED)
        val overlap = coverageCellFill(CellCoverageState.OVERLAP)
        val blind = coverageCellFill(CellCoverageState.BLIND_SPOT)
        assertNotEquals(covered, overlap)
        assertNotEquals(overlap, blind)
        assertNotEquals(covered, blind)
        assertTrue(overlap.alpha > covered.alpha)
    }

    @Test
    fun typeFilterMapUsesSameTypeCoverageResultIncludingOverlapCells() {
        val greenhouse = Greenhouse()
        val sensors = listOf(
            Sensor(id = "S1", type = SensorType.TEMPERATURE, x = 0.0, y = 0.0, coverageRadius = 1.5),
            Sensor(id = "S2", type = SensorType.TEMPERATURE, x = 1.0, y = 0.0, coverageRadius = 1.5),
            Sensor(id = "S3", type = SensorType.HUMIDITY, x = 0.0, y = 0.0, coverageRadius = 1.5)
        )
        val byType = CoverageCalculator.calculateByType(greenhouse, sensors)
        val temperatureMap = displayedMapCoverage(byType, SensorType.TEMPERATURE)
        assertEquals(byType.forType(SensorType.TEMPERATURE), temperatureMap)
        assertTrue(temperatureMap.overlapCells > 0)
        assertTrue(temperatureMap.cells.any { it.state == CellCoverageState.OVERLAP })

        val allMap = displayedMapCoverage(byType, null)
        assertEquals(byType.monitoring, allMap)
        assertEquals(0, allMap.overlapCells)
        assertTrue(allMap.cells.none { it.state == CellCoverageState.OVERLAP })
    }

    @Test
    fun typeFilterShowsOnlyMatchingSensorMarkers() {
        val sensors = listOf(
            Sensor(id = "S1", type = SensorType.TEMPERATURE, x = 0.0, y = 0.0),
            Sensor(id = "S2", type = SensorType.HUMIDITY, x = 1.0, y = 0.0)
        )
        assertEquals(listOf("S1"), displayedMapSensors(sensors, SensorType.TEMPERATURE).map { it.id })
        assertEquals(listOf("S1", "S2"), displayedMapSensors(sensors, null).map { it.id })
    }
}
