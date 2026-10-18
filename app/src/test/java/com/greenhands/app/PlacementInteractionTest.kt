package com.greenhands.app

import com.greenhands.app.sensor.domain.GridTapResult
import com.greenhands.app.sensor.domain.PlacementInteraction
import com.greenhands.app.sensor.model.Greenhouse
import com.greenhands.app.sensor.model.Sensor
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
class PlacementInteractionTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val greenhouse = Greenhouse()

    @Before
    fun setMain() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun reset() {
        Dispatchers.resetMain()
    }

    @Test
    fun emptyCellAddsSensorAtGridCoordinates() {
        val result = PlacementInteraction.onCellTapped(
            column = 3,
            row = 2,
            greenhouse = greenhouse,
            sensors = emptyList(),
            selectedSensorId = null
        )
        assertEquals(GridTapResult.Add(3.0, 2.0), result)
    }

    @Test
    fun occupiedCellSelectsThatSensor() {
        val sensors = listOf(Sensor(id = "S1", x = 3.0, y = 2.0), Sensor(id = "S2", x = 5.0, y = 1.0))
        val result = PlacementInteraction.onCellTapped(3, 2, greenhouse, sensors, null)
        assertEquals(GridTapResult.Select("S1"), result)
    }

    @Test
    fun emptyCellWithSelectionMovesSelectedSensor() {
        val sensors = listOf(Sensor(id = "S1", x = 0.0, y = 0.0))
        val result = PlacementInteraction.onCellTapped(4, 5, greenhouse, sensors, "S1")
        assertEquals(GridTapResult.Move("S1", 4.0, 5.0), result)
    }

    @Test
    fun forceAddPlacesEvenWhenASensorIsSelected() {
        val sensors = listOf(Sensor(id = "S1", x = 0.0, y = 0.0))
        val result = PlacementInteraction.onCellTapped(
            column = 4,
            row = 5,
            greenhouse = greenhouse,
            sensors = sensors,
            selectedSensorId = "S1",
            forceAdd = true
        )
        assertEquals(GridTapResult.Add(4.0, 5.0), result)
    }

    @Test
    fun outOfBoundsTapIsIgnored() {
        val result = PlacementInteraction.onCellTapped(12, 0, greenhouse, emptyList(), null)
        assertEquals(GridTapResult.Ignore, result)
        assertEquals(
            GridTapResult.Ignore,
            PlacementInteraction.onCellTapped(-1, 0, greenhouse, emptyList(), null)
        )
    }

    @Test
    fun viewModelAppliesTapAddThenMove() {
        val vm = SensorPlacementViewModel()
        val add = PlacementInteraction.onCellTapped(1, 1, vm.state.value.greenhouse, vm.state.value.sensors, null)
        val addResult = add as GridTapResult.Add
        assertTrue(vm.addSensor(addResult.x, addResult.y))
        val move = PlacementInteraction.onCellTapped(
            6,
            3,
            vm.state.value.greenhouse,
            vm.state.value.sensors,
            vm.state.value.selectedSensorId
        )
        val moveResult = move as GridTapResult.Move
        assertTrue(vm.moveSensor(moveResult.id, moveResult.x, moveResult.y))
        val sensor = vm.state.value.sensors.single()
        assertEquals("S1", sensor.id)
        assertEquals(6.0, sensor.x, 0.0)
        assertEquals(3.0, sensor.y, 0.0)
        assertTrue(vm.state.value.coverage.overallCoveragePercent > 0.0)
    }
}
