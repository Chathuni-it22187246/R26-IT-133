package com.greenhands.app

import com.greenhands.app.sensor.domain.CoverageCalculator
import com.greenhands.app.sensor.domain.OptimizationPreview
import com.greenhands.app.sensor.model.SensorType
import com.greenhands.app.sensor.ui.SensorPlacementViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OptimizationPreviewTest {

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
    fun predictForSelectedEmptyMatchesCurrentCoverage() {
        val vm = SensorPlacementViewModel()
        vm.addSensor(1.0, 1.0, type = SensorType.TEMPERATURE)
        val current = CoverageCalculator.calculateForType(
            vm.state.value.greenhouse,
            vm.state.value.sensors,
            SensorType.TEMPERATURE
        )
        val preview = OptimizationPreview.predictForSelected(
            vm.state.value.greenhouse,
            vm.state.value.sensors,
            SensorType.TEMPERATURE,
            emptyList()
        )
        assertEquals(current.overallCoveragePercent, preview.overallCoveragePercent, 0.0001)
    }

    @Test
    fun predictForSelectedAllMatchesOptimizerPredictedCoverage() {
        val vm = SensorPlacementViewModel()
        vm.addSensor(1.0, 1.0, type = SensorType.TEMPERATURE)
        vm.selectOptimizationSensorType(SensorType.TEMPERATURE)
        vm.calculateOptimization()
        val result = vm.state.value.optimizationResult!!
        val preview = OptimizationPreview.predictForSelected(
            vm.state.value.greenhouse,
            vm.state.value.sensors,
            SensorType.TEMPERATURE,
            result.recommendedPositions
        )
        assertEquals(result.predictedCoverage, preview.overallCoveragePercent, 0.0001)
        assertEquals(result.predictedBlindSpots, preview.blindSpotCells)
    }

    @Test
    fun predictForSelectedSubsetDoesNotExceedAllSelected() {
        val vm = SensorPlacementViewModel()
        vm.selectOptimizationSensorType(SensorType.TEMPERATURE)
        vm.calculateOptimization()
        val result = vm.state.value.optimizationResult!!
        if (result.recommendedPositions.size < 2) return
        val all = OptimizationPreview.predictForSelected(
            vm.state.value.greenhouse,
            vm.state.value.sensors,
            SensorType.TEMPERATURE,
            result.recommendedPositions
        )
        val subset = OptimizationPreview.predictForSelected(
            vm.state.value.greenhouse,
            vm.state.value.sensors,
            SensorType.TEMPERATURE,
            result.recommendedPositions.take(1)
        )
        assertTrue(subset.overallCoveragePercent <= all.overallCoveragePercent + 0.0001)
    }
}
