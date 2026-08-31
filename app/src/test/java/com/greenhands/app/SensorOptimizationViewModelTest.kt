package com.greenhands.app

import com.greenhands.app.sensor.ar.ArVisualizationMapper
import com.greenhands.app.sensor.domain.CoverageCalculator
import com.greenhands.app.sensor.model.SensorType
import com.greenhands.app.sensor.ui.SensorPlacementViewModel
import com.greenhands.app.sensor.ui.SensorWorkflowStep
import com.greenhands.app.sensor.ui.optimizationPositionKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SensorOptimizationViewModelTest {

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
    fun calculateOptimizationStoresResultWithoutAddingSensors() {
        val vm = SensorPlacementViewModel()
        vm.addSensor(1.0, 1.0, type = SensorType.TEMPERATURE)
        val beforeCount = vm.state.value.sensorCount
        vm.selectOptimizationSensorType(SensorType.TEMPERATURE)
        vm.calculateOptimization()
        val evaluation = vm.state.value.optimizationEvaluation
        val result = vm.state.value.optimizationResult
        assertNotNull(evaluation)
        assertNotNull(result)
        assertTrue(evaluation!!.candidates.isNotEmpty())
        assertEquals(beforeCount, vm.state.value.sensorCount)
        assertFalse(vm.state.value.isOptimizing)
    }

    @Test
    fun applyOptimizationCreatesSensorsPreservesExistingIds() {
        val vm = SensorPlacementViewModel()
        assertTrue(vm.addSensor(1.0, 1.0, type = SensorType.TEMPERATURE))
        val existingId = vm.state.value.sensors.single().id
        vm.selectOptimizationSensorType(SensorType.TEMPERATURE)
        vm.calculateOptimization()
        val recommended = vm.state.value.optimizationResult!!.recommendedPositions
        assertTrue(vm.applyOptimization())
        assertNull(vm.state.value.optimizationResult)
        assertEquals(1 + recommended.size, vm.state.value.sensorCount)
        assertTrue(vm.state.value.sensors.any { it.id == existingId })
        val newIds = vm.state.value.sensors.map { it.id }.filter { it != existingId }
        assertEquals(recommended.size, newIds.size)
        assertTrue(newIds.all { it.startsWith("S") })
        recommended.forEach { pos ->
            assertTrue(
                vm.state.value.sensors.any {
                    it.x == pos.x && it.y == pos.y && it.type == SensorType.TEMPERATURE
                }
            )
        }
        val expected = CoverageCalculator.calculateByType(
            vm.state.value.greenhouse,
            vm.state.value.sensors
        )
        assertEquals(
            expected.forType(SensorType.TEMPERATURE).overallCoveragePercent,
            vm.state.value.coverageByType.forType(SensorType.TEMPERATURE).overallCoveragePercent,
            0.0001
        )
    }

    @Test
    fun toggleDeselectsRecommendationAndApplyUsesSelectionOnly() {
        val vm = SensorPlacementViewModel()
        vm.selectOptimizationSensorType(SensorType.HUMIDITY)
        vm.calculateOptimization()
        val evaluation = vm.state.value.optimizationEvaluation ?: return
        val multi = evaluation.candidates.firstOrNull {
            it.result.recommendedPositions.size >= 2
        } ?: return
        vm.selectOptimizationAlternative(multi.additionalCount)
        val first = vm.state.value.optimizationResult!!.recommendedPositions.first()
        vm.toggleOptimizationPosition(first.x, first.y)
        assertFalse(
            optimizationPositionKey(first.x, first.y) in
                vm.state.value.selectedOptimizationPositions
        )
        assertTrue(vm.applyOptimization())
        assertEquals(1, vm.state.value.sensorCount)
        assertFalse(
            vm.state.value.sensors.any { it.x == first.x && it.y == first.y }
        )
    }

    @Test
    fun clearOptimizationRemovesResult() {
        val vm = SensorPlacementViewModel()
        vm.calculateOptimization()
        assertNotNull(vm.state.value.optimizationResult)
        vm.clearOptimization()
        assertNull(vm.state.value.optimizationResult)
        assertNull(vm.state.value.optimizationEvaluation)
        assertTrue(vm.state.value.selectedOptimizationPositions.isEmpty())
    }

    @Test
    fun changingTypeClearsPriorResult() {
        val vm = SensorPlacementViewModel()
        vm.calculateOptimization()
        assertNotNull(vm.state.value.optimizationResult)
        vm.selectOptimizationSensorType(SensorType.SOIL_MOISTURE)
        assertNull(vm.state.value.optimizationResult)
        vm.calculateOptimization()
        assertNotNull(vm.state.value.optimizationResult)
    }

    @Test
    fun goToOptimizeStepDoesNotClearSensors() {
        val vm = SensorPlacementViewModel()
        vm.addSensor(2.0, 2.0)
        vm.goToStep(SensorWorkflowStep.OPTIMIZE)
        assertEquals(SensorWorkflowStep.OPTIMIZE, vm.state.value.step)
        assertEquals(1, vm.state.value.sensorCount)
    }

    @Test
    fun keepCurrentPlacementRejectsRecommendationsWithoutChangingSensors() {
        val vm = SensorPlacementViewModel()
        vm.addSensor(1.0, 1.0, type = SensorType.TEMPERATURE)
        val sensorsBefore = vm.state.value.sensors
        vm.calculateOptimization()
        assertNotNull(vm.state.value.optimizationResult)
        vm.keepCurrentPlacement()
        assertNull(vm.state.value.optimizationResult)
        assertEquals(sensorsBefore, vm.state.value.sensors)
    }

    @Test
    fun applyOptimizationStoresBeforeAfterSummaryAndRecalculatesCoverage() {
        val vm = SensorPlacementViewModel()
        vm.addSensor(1.0, 1.0, type = SensorType.TEMPERATURE)
        val beforeCount = vm.state.value.sensorCount
        val beforeCoverage = vm.state.value.coverageByType.forType(SensorType.TEMPERATURE)
        vm.selectOptimizationSensorType(SensorType.TEMPERATURE)
        vm.calculateOptimization()
        assertTrue(vm.applyOptimization())
        val summary = vm.state.value.lastOptimizationApply
        assertNotNull(summary)
        assertEquals(beforeCount, summary!!.beforeSensorCount)
        assertTrue(summary.afterSensorCount > beforeCount)
        assertEquals(beforeCoverage.overallCoveragePercent, summary.beforeCoveragePercent, 0.0001)
        assertTrue(summary.afterCoveragePercent >= summary.beforeCoveragePercent)
        assertTrue(summary.coverageImprovement >= 0.0)
        val expected = CoverageCalculator.calculateByType(
            vm.state.value.greenhouse,
            vm.state.value.sensors
        ).forType(SensorType.TEMPERATURE)
        assertEquals(expected.overallCoveragePercent, summary.afterCoveragePercent, 0.0001)
        assertEquals(expected.blindSpotCells, summary.afterBlindSpotCells)
    }

    @Test
    fun dismissOptimizationApplySummaryClearsComparisonOnly() {
        val vm = SensorPlacementViewModel()
        vm.calculateOptimization()
        vm.applyOptimization()
        assertNotNull(vm.state.value.lastOptimizationApply)
        val sensors = vm.state.value.sensors
        vm.dismissOptimizationApplySummary()
        assertNull(vm.state.value.lastOptimizationApply)
        assertEquals(sensors, vm.state.value.sensors)
    }

    @Test
    fun arSnapshotKeepsRecommendationsSeparateFromSensorsUntilApplied() {
        val vm = SensorPlacementViewModel()
        vm.addSensor(0.0, 0.0, type = SensorType.TEMPERATURE)
        vm.calculateOptimization()
        val pending = ArVisualizationMapper.from(vm.state.value)
        assertEquals(1, pending.sensors.size)
        assertTrue(pending.recommendations.isNotEmpty())
        assertTrue(pending.recommendations.none { rec -> pending.sensors.any { it.id == rec.label } })
        assertTrue(vm.applyOptimization())
        val applied = ArVisualizationMapper.from(vm.state.value)
        assertTrue(applied.sensors.size > 1)
        assertTrue(applied.recommendations.isEmpty())
    }
}
