package com.greenhands.app

import com.greenhands.app.sensor.domain.CoverageCalculator
import com.greenhands.app.sensor.model.CellCoverageState
import com.greenhands.app.sensor.model.DEFAULT_COVERAGE_RADIUS_CELLS
import com.greenhands.app.sensor.model.DEFAULT_GREENHOUSE_HEIGHT_CELLS
import com.greenhands.app.sensor.model.DEFAULT_GREENHOUSE_WIDTH_CELLS
import com.greenhands.app.sensor.model.Greenhouse
import com.greenhands.app.sensor.model.Sensor
import com.greenhands.app.sensor.model.SensorStatus
import com.greenhands.app.sensor.model.SensorType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class CoverageCalculatorTest {

    private val greenhouse = Greenhouse()

    @Test
    fun noSensorsYieldZeroCoverageAndAllBlindSpots() {
        val byType = CoverageCalculator.calculateByType(greenhouse, emptyList())
        val total = DEFAULT_GREENHOUSE_WIDTH_CELLS * DEFAULT_GREENHOUSE_HEIGHT_CELLS
        SensorType.entries.forEach { type ->
            val result = byType.forType(type)
            assertEquals(total, result.totalCells)
            assertEquals(0, result.coveredCells)
            assertEquals(0, result.overlapCells)
            assertEquals(total, result.blindSpotCells)
            assertEquals(0.0, result.overallCoveragePercent, 0.0)
            assertTrue(result.cells.all { it.state == CellCoverageState.BLIND_SPOT })
        }
        assertEquals(0.0, byType.monitoring.overallCoveragePercent, 0.0)
        assertEquals(total, byType.monitoring.blindSpotCells)
    }

    @Test
    fun oneTemperatureSensorProducesCorrectTemperatureCoverage() {
        val sensor = Sensor(
            id = "S1",
            type = SensorType.TEMPERATURE,
            x = 0.0,
            y = 0.0,
            coverageRadius = DEFAULT_COVERAGE_RADIUS_CELLS
        )
        val temp = CoverageCalculator.calculateForType(greenhouse, listOf(sensor), SensorType.TEMPERATURE)
        val expectedCovered = countCells { x, y ->
            hypot(x - 0.0, y - 0.0) <= DEFAULT_COVERAGE_RADIUS_CELLS
        }
        assertTrue(expectedCovered in 1 until greenhouse.totalCells)
        assertEquals(expectedCovered, temp.coveredCells)
        assertEquals(0, temp.overlapCells)
        assertEquals(greenhouse.totalCells - expectedCovered, temp.blindSpotCells)
        assertEquals(CellCoverageState.COVERED, temp.cell(0, 0)?.state)
        assertEquals(listOf("S1"), temp.cell(0, 0)?.coveringSensorIds)
        assertEquals(CellCoverageState.BLIND_SPOT, temp.cell(11, 7)?.state)

        val humidity = CoverageCalculator.calculateForType(greenhouse, listOf(sensor), SensorType.HUMIDITY)
        assertEquals(0.0, humidity.overallCoveragePercent, 0.0)
        assertEquals(greenhouse.totalCells, humidity.blindSpotCells)
    }

    @Test
    fun twoTemperatureSensorsWithOverlapMarkSharedCellsAsOverlap() {
        val first = Sensor(id = "S1", type = SensorType.TEMPERATURE, x = 0.0, y = 0.0, coverageRadius = 1.5)
        val second = Sensor(id = "S2", type = SensorType.TEMPERATURE, x = 1.0, y = 0.0, coverageRadius = 1.5)
        val result = CoverageCalculator.calculateForType(greenhouse, listOf(first, second), SensorType.TEMPERATURE)
        val expected = countByCoverage(listOf(first, second))
        assertTrue(expected.overlap > 0)
        assertEquals(expected.overlap, result.overlapCells)
        assertEquals(expected.covered, result.coveredCells)
        assertEquals(CellCoverageState.OVERLAP, result.cell(0, 0)?.state)
        assertEquals(CellCoverageState.OVERLAP, result.cell(1, 0)?.state)
        val originIds = result.cell(0, 0)?.coveringSensorIds.orEmpty()
        assertTrue(originIds.containsAll(listOf("S1", "S2")))
        assertEquals(2, originIds.size)
    }

    @Test
    fun temperatureAndHumidityCoveringSameCellDoNotCreateOverlap() {
        val temperature = Sensor(
            id = "S1",
            type = SensorType.TEMPERATURE,
            x = 0.0,
            y = 0.0,
            coverageRadius = DEFAULT_COVERAGE_RADIUS_CELLS
        )
        val humidity = Sensor(
            id = "S2",
            type = SensorType.HUMIDITY,
            x = 0.0,
            y = 0.0,
            coverageRadius = DEFAULT_COVERAGE_RADIUS_CELLS
        )
        val byType = CoverageCalculator.calculateByType(greenhouse, listOf(temperature, humidity))
        assertEquals(0, byType.forType(SensorType.TEMPERATURE).overlapCells)
        assertEquals(0, byType.forType(SensorType.HUMIDITY).overlapCells)
        assertEquals(CellCoverageState.COVERED, byType.forType(SensorType.TEMPERATURE).cell(0, 0)?.state)
        assertEquals(CellCoverageState.COVERED, byType.forType(SensorType.HUMIDITY).cell(0, 0)?.state)
        assertEquals(listOf("S1"), byType.forType(SensorType.TEMPERATURE).cell(0, 0)?.coveringSensorIds)
        assertEquals(listOf("S2"), byType.forType(SensorType.HUMIDITY).cell(0, 0)?.coveringSensorIds)
        assertEquals(0, byType.monitoring.overlapCells)
        assertEquals(CellCoverageState.COVERED, byType.monitoring.cell(0, 0)?.state)
        val monitoringIds = byType.monitoring.cell(0, 0)?.coveringSensorIds.orEmpty()
        assertTrue(monitoringIds.containsAll(listOf("S1", "S2")))
    }

    @Test
    fun twoHumiditySensorsWithOverlapMarkHumidityOverlapOnly() {
        val first = Sensor(id = "S1", type = SensorType.HUMIDITY, x = 0.0, y = 0.0, coverageRadius = 1.5)
        val second = Sensor(id = "S2", type = SensorType.HUMIDITY, x = 1.0, y = 0.0, coverageRadius = 1.5)
        val byType = CoverageCalculator.calculateByType(greenhouse, listOf(first, second))
        assertTrue(byType.forType(SensorType.HUMIDITY).overlapCells > 0)
        assertEquals(CellCoverageState.OVERLAP, byType.forType(SensorType.HUMIDITY).cell(0, 0)?.state)
        assertEquals(0.0, byType.forType(SensorType.TEMPERATURE).overallCoveragePercent, 0.0)
        assertEquals(greenhouse.totalCells, byType.forType(SensorType.TEMPERATURE).blindSpotCells)
        assertEquals(0, byType.monitoring.overlapCells)
    }

    @Test
    fun soilMoistureCoverageIsIndependent() {
        val soil = Sensor(id = "S1", type = SensorType.SOIL_MOISTURE, x = 5.0, y = 4.0)
        val byType = CoverageCalculator.calculateByType(greenhouse, listOf(soil))
        assertTrue(byType.forType(SensorType.SOIL_MOISTURE).overallCoveragePercent > 0.0)
        assertEquals(0, byType.forType(SensorType.SOIL_MOISTURE).overlapCells)
        assertEquals(0.0, byType.forType(SensorType.TEMPERATURE).overallCoveragePercent, 0.0)
        assertEquals(0.0, byType.forType(SensorType.HUMIDITY).overallCoveragePercent, 0.0)
        assertEquals(0.0, byType.forType(SensorType.LIGHT_INTENSITY).overallCoveragePercent, 0.0)
    }

    @Test
    fun lightIntensityCoverageIsIndependent() {
        val light = Sensor(id = "S1", type = SensorType.LIGHT_INTENSITY, x = 2.0, y = 2.0)
        val byType = CoverageCalculator.calculateByType(greenhouse, listOf(light))
        assertTrue(byType.forType(SensorType.LIGHT_INTENSITY).overallCoveragePercent > 0.0)
        assertEquals(0.0, byType.forType(SensorType.TEMPERATURE).overallCoveragePercent, 0.0)
        assertEquals(0.0, byType.forType(SensorType.HUMIDITY).overallCoveragePercent, 0.0)
        assertEquals(0.0, byType.forType(SensorType.SOIL_MOISTURE).overallCoveragePercent, 0.0)
    }

    @Test
    fun inactiveSensorsDoNotContribute() {
        val active = Sensor(id = "S1", type = SensorType.TEMPERATURE, x = 0.0, y = 0.0, coverageRadius = 1.0)
        val inactive = Sensor(
            id = "S2",
            type = SensorType.TEMPERATURE,
            x = 1.0,
            y = 0.0,
            coverageRadius = 1.5,
            status = SensorStatus.INACTIVE
        )
        val withInactive = CoverageCalculator.calculateForType(
            greenhouse,
            listOf(active, inactive),
            SensorType.TEMPERATURE
        )
        val activeOnly = CoverageCalculator.calculateForType(
            greenhouse,
            listOf(active),
            SensorType.TEMPERATURE
        )
        assertEquals(activeOnly, withInactive)
        assertEquals(0, withInactive.overlapCells)
    }

    @Test
    fun outOfBoundSensorsAreIgnored() {
        assertFalse(CoverageCalculator.isValidPosition(greenhouse, -1.0, 0.0))
        assertFalse(CoverageCalculator.isValidPosition(greenhouse, 12.0, 0.0))
        assertFalse(CoverageCalculator.isValidPosition(greenhouse, 0.0, 8.0))
        assertTrue(CoverageCalculator.isValidPosition(greenhouse, 0.0, 0.0))

        val outside = Sensor(id = "S1", type = SensorType.TEMPERATURE, x = 12.0, y = 0.0, coverageRadius = 50.0)
        val inside = Sensor(id = "S2", type = SensorType.TEMPERATURE, x = 0.0, y = 0.0, coverageRadius = 1.0)
        val ignored = CoverageCalculator.calculateForType(greenhouse, listOf(outside), SensorType.TEMPERATURE)
        assertEquals(0.0, ignored.overallCoveragePercent, 0.0)
        assertEquals(greenhouse.totalCells, ignored.blindSpotCells)

        val mixed = CoverageCalculator.calculateForType(greenhouse, listOf(outside, inside), SensorType.TEMPERATURE)
        val expected = CoverageCalculator.calculateForType(greenhouse, listOf(inside), SensorType.TEMPERATURE)
        assertEquals(expected, mixed)
    }

    @Test
    fun sensorTypeWithNoSensorsHasZeroCoverageAndAllBlindSpots() {
        val humidity = Sensor(id = "S1", type = SensorType.HUMIDITY, x = 3.0, y = 3.0)
        val byType = CoverageCalculator.calculateByType(greenhouse, listOf(humidity))
        val emptyTypes = listOf(
            SensorType.TEMPERATURE,
            SensorType.SOIL_MOISTURE,
            SensorType.LIGHT_INTENSITY
        )
        emptyTypes.forEach { type ->
            val result = byType.forType(type)
            assertEquals(0.0, result.overallCoveragePercent, 0.0)
            assertEquals(0, result.overlapCells)
            assertEquals(96, result.blindSpotCells)
            assertTrue(result.cells.all { it.state == CellCoverageState.BLIND_SPOT })
        }
    }

    @Test
    fun oneSensorCanCoverTheEntireGreenhouseForItsType() {
        val sensor = Sensor(
            id = "S1",
            type = SensorType.TEMPERATURE,
            x = 5.5,
            y = 3.5,
            coverageRadius = 20.0
        )
        val result = CoverageCalculator.calculateForType(greenhouse, listOf(sensor), SensorType.TEMPERATURE)
        assertEquals(greenhouse.totalCells, result.coveredCells)
        assertEquals(0, result.overlapCells)
        assertEquals(0, result.blindSpotCells)
        assertEquals(100.0, result.overallCoveragePercent, 0.0)
    }

    @Test
    fun multipleSameTypeSensorsCoverSeparateRegionsWithoutOverlap() {
        val left = Sensor(id = "S1", type = SensorType.TEMPERATURE, x = 0.0, y = 0.0, coverageRadius = 1.0)
        val right = Sensor(id = "S2", type = SensorType.TEMPERATURE, x = 11.0, y = 7.0, coverageRadius = 1.0)
        val result = CoverageCalculator.calculateForType(greenhouse, listOf(left, right), SensorType.TEMPERATURE)
        val expected = countByCoverage(listOf(left, right))
        assertEquals(expected.covered, result.coveredCells)
        assertEquals(0, result.overlapCells)
        assertEquals(expected.blind, result.blindSpotCells)
    }

    @Test
    fun coveragePercentagesMatchCellCountsForType() {
        val sensors = listOf(
            Sensor(id = "S1", type = SensorType.TEMPERATURE, x = 2.0, y = 2.0),
            Sensor(id = "S2", type = SensorType.TEMPERATURE, x = 3.0, y = 2.0)
        )
        val result = CoverageCalculator.calculateForType(greenhouse, sensors, SensorType.TEMPERATURE)
        val total = result.totalCells.toDouble()
        assertEquals(100.0 * result.coveredCells / total, result.goodCoveragePercent, 1e-9)
        assertEquals(100.0 * result.overlapCells / total, result.overlapPercent, 1e-9)
        assertEquals(100.0 * result.blindSpotCells / total, result.blindSpotPercent, 1e-9)
        assertEquals(
            100.0 * (result.coveredCells + result.overlapCells) / total,
            result.overallCoveragePercent,
            1e-9
        )
    }

    @Test
    fun monitoringCoverageNeverMarksOverlap() {
        val first = Sensor(id = "S1", type = SensorType.TEMPERATURE, x = 0.0, y = 0.0, coverageRadius = 1.5)
        val second = Sensor(id = "S2", type = SensorType.TEMPERATURE, x = 1.0, y = 0.0, coverageRadius = 1.5)
        val monitoring = CoverageCalculator.calculateMonitoringCoverage(greenhouse, listOf(first, second))
        assertEquals(0, monitoring.overlapCells)
        assertEquals(0.0, monitoring.overlapPercent, 0.0)
        assertTrue(monitoring.cells.none { it.state == CellCoverageState.OVERLAP })
        assertEquals(
            CoverageCalculator.calculate(greenhouse, listOf(first, second)),
            monitoring
        )
    }

    @Test
    fun coverageUsesConfiguredGreenhouseDimensionsNotFixedTwelveByEight() {
        val greenhouse = Greenhouse(widthCells = 10, heightCells = 6)
        val sensor = Sensor(id = "S1", type = SensorType.TEMPERATURE, x = 0.0, y = 0.0)
        val result = CoverageCalculator.calculateForType(greenhouse, listOf(sensor), SensorType.TEMPERATURE)
        assertEquals(60, result.totalCells)
        assertEquals(60, result.coveredCells + result.overlapCells + result.blindSpotCells)
        assertTrue(result.blindSpotCells < 60)
        assertEquals(CellCoverageState.BLIND_SPOT, result.cell(9, 5)?.state)
    }

    @Test
    fun fortyByTwentyGreenhouseCoverageUsesAllEightHundredCells() {
        val greenhouse = Greenhouse(widthCells = 40, heightCells = 20)
        val empty = CoverageCalculator.calculateByType(greenhouse, emptyList())
        assertEquals(800, empty.monitoring.totalCells)
        assertEquals(800, empty.monitoring.blindSpotCells)
        assertEquals(0.0, empty.monitoring.overallCoveragePercent, 0.0)
    }

    private fun hypot(dx: Double, dy: Double): Double = sqrt(dx * dx + dy * dy)

    private fun countCells(predicate: (Int, Int) -> Boolean): Int {
        var count = 0
        for (y in greenhouse.rows) {
            for (x in greenhouse.columns) {
                if (predicate(x, y)) count++
            }
        }
        return count
    }

    private data class CoverageCounts(val covered: Int, val overlap: Int, val blind: Int)

    private fun countByCoverage(sensors: List<Sensor>): CoverageCounts {
        var covered = 0
        var overlap = 0
        var blind = 0
        for (y in greenhouse.rows) {
            for (x in greenhouse.columns) {
                val hits = sensors.count { sensor ->
                    sensor.status == SensorStatus.ACTIVE &&
                        greenhouse.contains(sensor.x, sensor.y) &&
                        hypot(sensor.x - x, sensor.y - y) <= sensor.coverageRadius
                }
                when (hits) {
                    0 -> blind++
                    1 -> covered++
                    else -> overlap++
                }
            }
        }
        return CoverageCounts(covered, overlap, blind)
    }
}
