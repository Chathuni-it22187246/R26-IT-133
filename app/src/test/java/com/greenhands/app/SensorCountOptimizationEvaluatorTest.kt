package com.greenhands.app

import com.greenhands.app.sensor.domain.CoverageCalculator
import com.greenhands.app.sensor.domain.SensorCountOptimizationEvaluator
import com.greenhands.app.sensor.model.Greenhouse
import com.greenhands.app.sensor.model.OptimizationSelectionReason
import com.greenhands.app.sensor.model.Sensor
import com.greenhands.app.sensor.model.SensorType
import com.greenhands.app.sensor.ui.SensorPlacementViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SensorCountOptimizationEvaluatorTest {

    @Test
    fun evaluatesMultipleSensorCountsWithoutMutatingSensors() {
        val greenhouse = Greenhouse(12, 8)
        val sensors = listOf(Sensor("S1", SensorType.TEMPERATURE, 1.0, 1.0))
        val evaluation = SensorCountOptimizationEvaluator.evaluate(
            greenhouse, sensors, SensorType.TEMPERATURE
        )
        assertTrue(evaluation.candidates.size >= 2)
        assertTrue(evaluation.candidates.size <= SensorCountOptimizationEvaluator.MAX_ADDITIONAL_SENSORS)
        evaluation.candidates.forEach { candidate ->
            assertEquals(
                CoverageCalculator.calculateForType(
                    greenhouse,
                    sensors + candidate.result.recommendedPositions.map {
                        Sensor(
                            "T",
                            SensorType.TEMPERATURE,
                            it.x,
                            it.y
                        )
                    },
                    SensorType.TEMPERATURE
                ).overallCoveragePercent,
                candidate.result.predictedCoverage,
                0.0001
            )
        }
    }

    @Test
    fun marginalCoverageGainIsComputedAgainstPreviousCount() {
        val evaluation = SensorCountOptimizationEvaluator.evaluate(
            Greenhouse(12, 8),
            emptyList(),
            SensorType.TEMPERATURE
        )
        if (evaluation.candidates.size < 2) return
        val first = evaluation.candidates[0]
        val second = evaluation.candidates[1]
        assertEquals(
            first.result.predictedCoverage - evaluation.beforeCoverage,
            first.marginalCoverageGain,
            0.0001
        )
        assertEquals(
            second.result.predictedCoverage - first.result.predictedCoverage,
            second.marginalCoverageGain,
            0.0001
        )
    }

    @Test
    fun recommendedCountIsMinimumAtBestCoverage() {
        val evaluation = SensorCountOptimizationEvaluator.evaluate(
            Greenhouse(12, 8),
            listOf(Sensor("S1", SensorType.TEMPERATURE, 2.0, 2.0)),
            SensorType.TEMPERATURE
        )
        val recommended = evaluation.recommendedCandidate
        assertNotNull(recommended)
        assertNotNull(evaluation.recommendedAdditionalCount)
        val maxCov = evaluation.candidates.maxOf { it.result.predictedCoverage }
        assertEquals(maxCov, recommended!!.result.predictedCoverage, 0.0001)
        val atMax = evaluation.candidates.filter {
            kotlin.math.abs(it.result.predictedCoverage - maxCov) < 1e-6
        }
        val minCountAtMax = atMax.minOf { it.actualAdditionalCount }
        assertEquals(minCountAtMax, recommended.actualAdditionalCount)
        assertEquals(
            OptimizationSelectionReason.MINIMUM_COUNT_AT_BEST_COVERAGE,
            evaluation.selectionReason
        )
    }

    @Test
    fun noImprovementWhenGridFullyOccupied() {
        val greenhouse = Greenhouse(2, 2)
        val sensors = listOf(
            Sensor("S1", SensorType.TEMPERATURE, 0.0, 0.0),
            Sensor("S2", SensorType.TEMPERATURE, 1.0, 0.0),
            Sensor("S3", SensorType.TEMPERATURE, 0.0, 1.0),
            Sensor("S4", SensorType.TEMPERATURE, 1.0, 1.0)
        )
        val evaluation = SensorCountOptimizationEvaluator.evaluate(
            greenhouse, sensors, SensorType.TEMPERATURE
        )
        assertTrue(evaluation.candidates.isEmpty())
        assertNull(evaluation.recommendedAdditionalCount)
        assertEquals(OptimizationSelectionReason.NO_IMPROVEMENT, evaluation.selectionReason)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SensorCountOptimizationViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setMain() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun reset() {
        Dispatchers.resetMain()
    }

    @Test
    fun calculateOptimizationEvaluatesCountsWithoutAddingSensors() {
        val vm = SensorPlacementViewModel()
        vm.addSensor(1.0, 1.0, type = SensorType.TEMPERATURE)
        val before = vm.state.value.sensorCount
        vm.selectOptimizationSensorType(SensorType.TEMPERATURE)
        vm.calculateOptimization()
        val evaluation = vm.state.value.optimizationEvaluation
        assertNotNull(evaluation)
        assertTrue(evaluation!!.candidates.isNotEmpty())
        assertEquals(before, vm.state.value.sensorCount)
        assertNotNull(vm.state.value.optimizationResult)
        assertNotNull(vm.state.value.selectedOptimizationAlternative)
    }

    @Test
    fun selectAlternativeChangesPreviewWithoutApplying() {
        val vm = SensorPlacementViewModel()
        vm.calculateOptimization()
        val evaluation = vm.state.value.optimizationEvaluation!!
        if (evaluation.candidates.size < 2) return
        val sensorsBefore = vm.state.value.sensors
        val alt = evaluation.candidates[1].additionalCount
        vm.selectOptimizationAlternative(alt)
        assertEquals(alt, vm.state.value.selectedOptimizationAlternative)
        assertEquals(sensorsBefore, vm.state.value.sensors)
        assertEquals(
            evaluation.candidateFor(alt)!!.result.recommendedPositions.size,
            vm.state.value.optimizationResult!!.recommendedPositions.size
        )
    }

    @Test
    fun applyAddsOnlySelectedAlternativePositions() {
        val vm = SensorPlacementViewModel()
        vm.addSensor(1.0, 1.0, type = SensorType.TEMPERATURE)
        vm.calculateOptimization()
        val before = vm.state.value.sensorCount
        assertTrue(vm.applyOptimization())
        assertTrue(vm.state.value.sensorCount > before)
        assertNull(vm.state.value.optimizationEvaluation)
    }
}
