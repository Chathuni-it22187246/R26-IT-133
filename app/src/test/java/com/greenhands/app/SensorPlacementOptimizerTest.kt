package com.greenhands.app

import com.greenhands.app.sensor.domain.CoverageCalculator
import com.greenhands.app.sensor.domain.SensorPlacementOptimizer
import com.greenhands.app.sensor.model.Greenhouse
import com.greenhands.app.sensor.model.Sensor
import com.greenhands.app.sensor.model.SensorStatus
import com.greenhands.app.sensor.model.SensorType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensorPlacementOptimizerTest {

    @Test
    fun noSensorsRecommendsValidPositions() {
        val greenhouse = Greenhouse(12, 8)
        val result = SensorPlacementOptimizer.optimize(
            greenhouse = greenhouse,
            sensors = emptyList(),
            sensorType = SensorType.TEMPERATURE,
            recommendationCount = 2
        )
        assertEquals(2, result.recommendedPositions.size)
        result.recommendedPositions.forEach { pos ->
            assertTrue(greenhouse.contains(pos.x, pos.y))
        }
        assertTrue(result.predictedCoverage > result.beforeCoverage)
        assertTrue(result.blindSpotReduction > 0)
        assertEquals(0.0, result.beforeCoverage, 0.0)
    }

    @Test
    fun oneTemperatureSensorRecommendationsImproveTemperatureCoverage() {
        val greenhouse = Greenhouse()
        val sensors = listOf(
            Sensor("S1", SensorType.TEMPERATURE, 1.0, 1.0)
        )
        val before = CoverageCalculator.calculateForType(
            greenhouse, sensors, SensorType.TEMPERATURE
        )
        val result = SensorPlacementOptimizer.optimize(
            greenhouse, sensors, SensorType.TEMPERATURE, 1
        )
        assertEquals(1, result.recommendedPositions.size)
        assertEquals(before.overallCoveragePercent, result.beforeCoverage, 0.0)
        assertTrue(result.predictedCoverage >= result.beforeCoverage)
        assertTrue(result.coverageImprovement >= 0.0)
        val simulated = sensors + Sensor(
            "T",
            SensorType.TEMPERATURE,
            result.recommendedPositions[0].x,
            result.recommendedPositions[0].y
        )
        val after = CoverageCalculator.calculateForType(
            greenhouse, simulated, SensorType.TEMPERATURE
        )
        assertEquals(after.overallCoveragePercent, result.predictedCoverage, 0.0001)
    }

    @Test
    fun temperatureOptimizationIgnoresHumidityForSameTypeOverlap() {
        val greenhouse = Greenhouse()
        val sensors = listOf(
            Sensor("S1", SensorType.TEMPERATURE, 2.0, 2.0),
            Sensor("S2", SensorType.HUMIDITY, 3.0, 3.0),
            Sensor("S3", SensorType.HUMIDITY, 4.0, 4.0)
        )
        val result = SensorPlacementOptimizer.optimize(
            greenhouse, sensors, SensorType.TEMPERATURE, 1
        )
        val humidityBefore = CoverageCalculator.calculateForType(
            greenhouse, sensors, SensorType.HUMIDITY
        )
        val tempOnly = listOf(sensors[0]) + Sensor(
            "R1",
            SensorType.TEMPERATURE,
            result.recommendedPositions.single().x,
            result.recommendedPositions.single().y
        )
        val tempAfter = CoverageCalculator.calculateForType(
            greenhouse, tempOnly, SensorType.TEMPERATURE
        )
        assertEquals(result.predictedCoverage, tempAfter.overallCoveragePercent, 0.0001)
        assertEquals(result.predictedOverlap, tempAfter.overlapCells)
        // Humidity overlap is unchanged by temperature recommendation math.
        assertEquals(
            humidityBefore.overlapCells,
            CoverageCalculator.calculateForType(greenhouse, sensors, SensorType.HUMIDITY).overlapCells
        )
    }

    @Test
    fun occupiedCellsAreNeverRecommended() {
        val greenhouse = Greenhouse(6, 4)
        val sensors = listOf(
            Sensor("S1", SensorType.TEMPERATURE, 0.0, 0.0),
            Sensor("S2", SensorType.HUMIDITY, 1.0, 0.0, status = SensorStatus.INACTIVE)
        )
        val occupied = setOf(0.0 to 0.0, 1.0 to 0.0)
        val result = SensorPlacementOptimizer.optimize(
            greenhouse, sensors, SensorType.TEMPERATURE, 4
        )
        result.recommendedPositions.forEach { pos ->
            assertFalse(occupied.contains(pos.x to pos.y))
        }
    }

    @Test
    fun duplicateRecommendationPositionsAreImpossible() {
        val result = SensorPlacementOptimizer.optimize(
            Greenhouse(),
            emptyList(),
            SensorType.SOIL_MOISTURE,
            4
        )
        val keys = result.recommendedPositions.map { it.x to it.y }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun recommendationCountIsRespected() {
        val result = SensorPlacementOptimizer.optimize(
            Greenhouse(),
            emptyList(),
            SensorType.LIGHT_INTENSITY,
            3
        )
        assertEquals(3, result.requestedSensorCount)
        assertEquals(3, result.recommendedPositions.size)
        assertEquals(listOf(1, 2, 3), result.recommendedPositions.map { it.rank })
    }

    @Test
    fun recommendationCountLargerThanEmptyCellsIsCapped() {
        val greenhouse = Greenhouse(2, 2)
        val sensors = listOf(
            Sensor("S1", SensorType.TEMPERATURE, 0.0, 0.0),
            Sensor("S2", SensorType.TEMPERATURE, 1.0, 0.0),
            Sensor("S3", SensorType.TEMPERATURE, 0.0, 1.0)
        )
        val result = SensorPlacementOptimizer.optimize(
            greenhouse, sensors, SensorType.TEMPERATURE, 4
        )
        assertTrue(result.recommendedPositions.size <= 1)
        result.recommendedPositions.forEach {
            assertEquals(1.0, it.x, 0.0)
            assertEquals(1.0, it.y, 0.0)
        }
    }

    @Test
    fun deterministicSameInputsSameRecommendations() {
        val greenhouse = Greenhouse(10, 8)
        val sensors = listOf(
            Sensor("S1", SensorType.TEMPERATURE, 2.0, 2.0),
            Sensor("S2", SensorType.HUMIDITY, 5.0, 5.0)
        )
        val a = SensorPlacementOptimizer.optimize(
            greenhouse, sensors, SensorType.TEMPERATURE, 3
        )
        val b = SensorPlacementOptimizer.optimize(
            greenhouse, sensors, SensorType.TEMPERATURE, 3
        )
        assertEquals(a.recommendedPositions, b.recommendedPositions)
        assertEquals(a.predictedCoverage, b.predictedCoverage, 0.0)
        assertEquals(a.predictedBlindSpots, b.predictedBlindSpots)
        assertEquals(a.predictedOverlap, b.predictedOverlap)
    }

    @Test
    fun inactiveSensorsDoNotContributeToOptimizationCoverage() {
        val greenhouse = Greenhouse()
        val active = Sensor("S1", SensorType.TEMPERATURE, 1.0, 1.0, status = SensorStatus.ACTIVE)
        val inactive = Sensor("S2", SensorType.TEMPERATURE, 6.0, 6.0, status = SensorStatus.INACTIVE)
        val withInactive = SensorPlacementOptimizer.optimize(
            greenhouse, listOf(active, inactive), SensorType.TEMPERATURE, 1
        )
        val activeOnly = SensorPlacementOptimizer.optimize(
            greenhouse, listOf(active), SensorType.TEMPERATURE, 1
        )
        assertEquals(withInactive.beforeCoverage, activeOnly.beforeCoverage, 0.0)
        assertEquals(withInactive.beforeBlindSpots, activeOnly.beforeBlindSpots)
        // Inactive cell is still occupied and cannot be recommended.
        assertFalse(
            withInactive.recommendedPositions.any { it.x == 6.0 && it.y == 6.0 }
        )
    }

    @Test
    fun worksForDifferentGreenhouseDimensions() {
        listOf(Greenhouse(10, 8), Greenhouse(12, 8), Greenhouse(20, 10)).forEach { gh ->
            val result = SensorPlacementOptimizer.optimize(
                gh,
                listOf(Sensor("S1", SensorType.TEMPERATURE, 1.0, 1.0)),
                SensorType.TEMPERATURE,
                2
            )
            assertEquals(2, result.recommendedPositions.size)
            result.recommendedPositions.forEach { assertTrue(gh.contains(it.x, it.y)) }
            assertTrue(result.predictedCoverage >= result.beforeCoverage)
        }
    }

    @Test
    fun sameTypeOverlapComesFromCoverageCalculator() {
        val greenhouse = Greenhouse(8, 8)
        // Two close sensors create overlap; optimizer should report overlap via calculator.
        val sensors = listOf(
            Sensor("S1", SensorType.TEMPERATURE, 3.0, 3.0),
            Sensor("S2", SensorType.TEMPERATURE, 4.0, 3.0)
        )
        val calc = CoverageCalculator.calculateForType(
            greenhouse, sensors, SensorType.TEMPERATURE
        )
        val result = SensorPlacementOptimizer.optimize(
            greenhouse, sensors, SensorType.TEMPERATURE, 1
        )
        assertEquals(calc.overlapCells, result.beforeOverlap)
        assertTrue(result.beforeOverlap > 0)
    }

    @Test
    fun crossTypeCoverageIsNotSameTypeOverlap() {
        val greenhouse = Greenhouse()
        val sensors = listOf(
            Sensor("S1", SensorType.TEMPERATURE, 3.0, 3.0),
            Sensor("S2", SensorType.HUMIDITY, 3.0, 3.5)
        )
        val temp = CoverageCalculator.calculateForType(
            greenhouse, sensors, SensorType.TEMPERATURE
        )
        assertEquals(0, temp.overlapCells)
        val result = SensorPlacementOptimizer.optimize(
            greenhouse, sensors, SensorType.TEMPERATURE, 1
        )
        assertEquals(0, result.beforeOverlap)
    }
}
