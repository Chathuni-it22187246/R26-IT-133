package com.greenhands.app

import com.greenhands.app.sensor.domain.CoverageCalculator
import com.greenhands.app.sensor.model.DEFAULT_COVERAGE_RADIUS_CELLS
import com.greenhands.app.sensor.model.Greenhouse
import com.greenhands.app.sensor.model.SensorStatus
import com.greenhands.app.sensor.model.SensorType
import com.greenhands.app.sensor.ui.SensorPlacementViewModel
import com.greenhands.app.sensor.ui.SensorWorkflowStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SensorPlacementViewModelTest {

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
    fun initialStateHasEmptyPlacementAndZeroCoverage() {
        val vm = SensorPlacementViewModel()
        val state = vm.state.value
        assertEquals(12, state.greenhouse.widthCells)
        assertEquals(8, state.greenhouse.heightCells)
        assertTrue(state.sensors.isEmpty())
        assertEquals(0, state.sensorCount)
        assertNull(state.selectedSensorId)
        assertNull(state.selectedSensor)
        assertEquals(SensorWorkflowStep.SETUP, state.step)
        assertEquals(12.0, state.physicalConfig.lengthMeters, 0.0)
        assertEquals(8.0, state.physicalConfig.widthMeters, 0.0)
        assertEquals(4.0, state.physicalConfig.heightMeters, 0.0)
        assertEquals(1.0, state.physicalConfig.cellSizeMeters, 0.0)
        assertFalse(state.greenhouseConfigured)
        assertNull(state.configError)
        assertEquals(0.0, state.coverage.overallCoveragePercent, 0.0)
        assertEquals(state.greenhouse.totalCells, state.coverage.blindSpotCells)
        assertNull(state.placementError)
        assertEquals(
            CoverageCalculator.calculate(state.greenhouse, emptyList()),
            state.coverage
        )
        assertEquals(
            CoverageCalculator.calculateByType(state.greenhouse, emptyList()),
            state.coverageByType
        )
    }

    @Test
    fun openSensorTypePickerDoesNotCreateASensor() {
        val vm = SensorPlacementViewModel()
        vm.openSensorTypePicker()
        assertTrue(vm.state.value.showSensorTypePicker)
        assertEquals(SensorType.TEMPERATURE, vm.state.value.pendingSensorType)
        assertFalse(vm.state.value.awaitingCellPlacement)
        assertTrue(vm.state.value.sensors.isEmpty())
        assertEquals(0, vm.state.value.sensorCount)
    }

    @Test
    fun selectingSensorTypeDoesNotPlaceSensorUntilCellTap() {
        val vm = SensorPlacementViewModel()
        vm.openSensorTypePicker()
        vm.selectPendingSensorType(SensorType.HUMIDITY)
        assertEquals(SensorType.HUMIDITY, vm.state.value.pendingSensorType)
        assertTrue(vm.state.value.sensors.isEmpty())
        vm.confirmPendingSensorType()
        assertFalse(vm.state.value.showSensorTypePicker)
        assertTrue(vm.state.value.awaitingCellPlacement)
        assertTrue(vm.state.value.sensors.isEmpty())
    }

    @Test
    fun placeUsesTemperatureTypeWhenSelected() {
        assertPlacedType(SensorType.TEMPERATURE)
    }

    @Test
    fun placeUsesHumidityTypeWhenSelected() {
        assertPlacedType(SensorType.HUMIDITY)
    }

    @Test
    fun placeUsesSoilMoistureTypeWhenSelected() {
        assertPlacedType(SensorType.SOIL_MOISTURE)
    }

    @Test
    fun placeUsesLightIntensityTypeWhenSelected() {
        assertPlacedType(SensorType.LIGHT_INTENSITY)
    }

    @Test
    fun sequentialIdsContinueAcrossDifferentSensorTypes() {
        val vm = SensorPlacementViewModel()
        placeType(vm, SensorType.TEMPERATURE, 0.0, 0.0)
        placeType(vm, SensorType.HUMIDITY, 1.0, 0.0)
        placeType(vm, SensorType.SOIL_MOISTURE, 2.0, 0.0)
        placeType(vm, SensorType.LIGHT_INTENSITY, 3.0, 0.0)
        assertEquals(listOf("S1", "S2", "S3", "S4"), vm.state.value.sensors.map { it.id })
        assertEquals(
            listOf(
                SensorType.TEMPERATURE,
                SensorType.HUMIDITY,
                SensorType.SOIL_MOISTURE,
                SensorType.LIGHT_INTENSITY
            ),
            vm.state.value.sensors.map { it.type }
        )
    }

    private fun assertPlacedType(type: SensorType) {
        val vm = SensorPlacementViewModel()
        placeType(vm, type, 2.0, 3.0)
        val sensor = vm.state.value.sensors.single()
        assertEquals(type, sensor.type)
        assertEquals("S1", sensor.id)
        assertEquals(DEFAULT_COVERAGE_RADIUS_CELLS, sensor.coverageRadius, 0.0)
        assertFalse(vm.state.value.awaitingCellPlacement)
        assertNull(vm.state.value.pendingSensorType)
        assertFalse(vm.state.value.showSensorTypePicker)
    }

    private fun placeType(
        vm: SensorPlacementViewModel,
        type: SensorType,
        x: Double,
        y: Double
    ) {
        vm.openSensorTypePicker()
        vm.selectPendingSensorType(type)
        vm.confirmPendingSensorType()
        assertTrue(vm.addSensor(x, y))
    }

    @Test
    fun addSensorPlacesTemperatureSensorWithSequentialId() {
        val vm = SensorPlacementViewModel()
        assertTrue(vm.addSensor(2.0, 3.0))
        val sensor = vm.state.value.sensors.single()
        assertEquals("S1", sensor.id)
        assertEquals(SensorType.TEMPERATURE, sensor.type)
        assertEquals(2.0, sensor.x, 0.0)
        assertEquals(3.0, sensor.y, 0.0)
        assertEquals(DEFAULT_COVERAGE_RADIUS_CELLS, sensor.coverageRadius, 0.0)
        assertEquals(SensorStatus.ACTIVE, sensor.status)
        assertEquals("S1", vm.state.value.selectedSensorId)
        assertNull(vm.state.value.placementError)
    }

    @Test
    fun addMultipleSensorsAssignsSequentialIds() {
        val vm = SensorPlacementViewModel()
        assertTrue(vm.addSensor(0.0, 0.0))
        assertTrue(vm.addSensor(1.0, 0.0))
        assertTrue(vm.addSensor(2.0, 1.0))
        assertEquals(listOf("S1", "S2", "S3"), vm.state.value.sensors.map { it.id })
        assertEquals(3, vm.state.value.sensorCount)
        assertEquals("S3", vm.state.value.selectedSensorId)
    }

    @Test
    fun selectSensorUpdatesSelection() {
        val vm = SensorPlacementViewModel()
        vm.addSensor(0.0, 0.0)
        vm.addSensor(4.0, 1.0)
        assertTrue(vm.selectSensor("S1"))
        assertEquals("S1", vm.state.value.selectedSensorId)
        assertEquals(0.0, vm.state.value.selectedSensor?.x)
        assertFalse(vm.selectSensor("missing"))
        assertEquals("S1", vm.state.value.selectedSensorId)
    }

    @Test
    fun deselectSensorClearsSelection() {
        val vm = SensorPlacementViewModel()
        vm.addSensor(1.0, 1.0)
        assertNotNull(vm.state.value.selectedSensorId)
        vm.deselectSensor()
        assertNull(vm.state.value.selectedSensorId)
        assertNull(vm.state.value.selectedSensor)
    }

    @Test
    fun moveSensorUpdatesGridCoordinates() {
        val vm = SensorPlacementViewModel()
        vm.addSensor(1.0, 1.0)
        assertTrue(vm.moveSensor("S1", 6.0, 4.0))
        val sensor = vm.state.value.sensors.single()
        assertEquals(6.0, sensor.x, 0.0)
        assertEquals(4.0, sensor.y, 0.0)
        assertEquals("S1", vm.state.value.selectedSensorId)
    }

    @Test
    fun removeSensorDropsThatSensorAndClearsSelectionIfNeeded() {
        val vm = SensorPlacementViewModel()
        vm.addSensor(0.0, 0.0)
        vm.addSensor(5.0, 2.0)
        vm.selectSensor("S2")
        assertTrue(vm.removeSensor("S2"))
        assertEquals(listOf("S1"), vm.state.value.sensors.map { it.id })
        assertNull(vm.state.value.selectedSensorId)
        assertTrue(vm.addSensor(3.0, 3.0))
        assertEquals(listOf("S1", "S3"), vm.state.value.sensors.map { it.id })
    }

    @Test
    fun deactivateSensorMarksItInactive() {
        val vm = SensorPlacementViewModel()
        vm.addSensor(2.0, 2.0)
        assertTrue(vm.setSensorActive("S1", active = false))
        assertEquals(SensorStatus.INACTIVE, vm.state.value.sensors.single().status)
    }

    @Test
    fun reactivateSensorMarksItActive() {
        val vm = SensorPlacementViewModel()
        vm.addSensor(2.0, 2.0)
        vm.setSensorActive("S1", active = false)
        assertTrue(vm.setSensorActive("S1", active = true))
        assertEquals(SensorStatus.ACTIVE, vm.state.value.sensors.single().status)
    }

    @Test
    fun resetSensorsClearsListAndRestartsIds() {
        val vm = SensorPlacementViewModel()
        vm.addSensor(0.0, 0.0)
        vm.addSensor(1.0, 1.0)
        vm.resetSensors()
        assertTrue(vm.state.value.sensors.isEmpty())
        assertNull(vm.state.value.selectedSensorId)
        assertEquals(0.0, vm.state.value.coverage.overallCoveragePercent, 0.0)
        assertTrue(vm.addSensor(4.0, 4.0))
        assertEquals("S1", vm.state.value.sensors.single().id)
    }

    @Test
    fun coverageRecalculatesAfterAddingSensor() {
        val vm = SensorPlacementViewModel()
        val before = vm.state.value.coverage.overallCoveragePercent
        vm.addSensor(0.0, 0.0)
        val after = vm.state.value.coverage
        assertTrue(after.overallCoveragePercent > before)
        assertEquals(
            CoverageCalculator.calculate(vm.state.value.greenhouse, vm.state.value.sensors),
            after
        )
        assertEquals(
            CoverageCalculator.calculateByType(vm.state.value.greenhouse, vm.state.value.sensors),
            vm.state.value.coverageByType
        )
    }

    @Test
    fun coverageByTypeIsIndependentForDifferentSensorTypes() {
        val vm = SensorPlacementViewModel()
        placeType(vm, SensorType.TEMPERATURE, 0.0, 0.0)
        placeType(vm, SensorType.HUMIDITY, 0.0, 0.0)
        val byType = vm.state.value.coverageByType
        assertEquals(0, byType.forType(SensorType.TEMPERATURE).overlapCells)
        assertEquals(0, byType.forType(SensorType.HUMIDITY).overlapCells)
        assertEquals(0, byType.monitoring.overlapCells)
        assertEquals(
            CoverageCalculator.calculateByType(vm.state.value.greenhouse, vm.state.value.sensors),
            byType
        )
    }

    @Test
    fun coverageRecalculatesAfterMovingSensor() {
        val vm = SensorPlacementViewModel()
        vm.addSensor(0.0, 0.0)
        val before = vm.state.value.coverage
        vm.moveSensor("S1", 11.0, 7.0)
        val after = vm.state.value.coverage
        assertNotEquals(before, after)
        assertEquals(
            CoverageCalculator.calculate(vm.state.value.greenhouse, vm.state.value.sensors),
            after
        )
    }

    @Test
    fun coverageRecalculatesAfterRemovingSensor() {
        val vm = SensorPlacementViewModel()
        vm.addSensor(0.0, 0.0)
        vm.addSensor(11.0, 7.0)
        val before = vm.state.value.coverage.coveredCells
        vm.removeSensor("S2")
        val after = vm.state.value.coverage
        assertTrue(after.coveredCells < before)
        assertEquals(
            CoverageCalculator.calculate(vm.state.value.greenhouse, vm.state.value.sensors),
            after
        )
    }

    @Test
    fun invalidPlacementIsRejectedSafely() {
        val greenhouse = Greenhouse()
        val vm = SensorPlacementViewModel(greenhouse)
        assertFalse(vm.addSensor(-1.0, 0.0))
        assertFalse(vm.addSensor(12.0, 0.0))
        assertFalse(vm.addSensor(0.0, 8.0))
        assertTrue(vm.state.value.sensors.isEmpty())
        assertEquals(SensorPlacementViewModel.OUT_OF_BOUNDS, vm.state.value.placementError)
        assertEquals(0.0, vm.state.value.coverage.overallCoveragePercent, 0.0)

        assertTrue(vm.addSensor(1.0, 1.0))
        assertFalse(vm.moveSensor("S1", 12.0, 1.0))
        val sensor = vm.state.value.sensors.single()
        assertEquals(1.0, sensor.x, 0.0)
        assertEquals(1.0, sensor.y, 0.0)
        assertEquals(SensorPlacementViewModel.OUT_OF_BOUNDS, vm.state.value.placementError)
        assertEquals(
            CoverageCalculator.calculate(greenhouse, vm.state.value.sensors),
            vm.state.value.coverage
        )
    }

    @Test
    fun createOrUpdateGreenhouseAppliesValidConfigAndClearsSensors() {
        val vm = SensorPlacementViewModel()
        assertTrue(vm.addSensor(0.0, 0.0))
        assertEquals(1, vm.state.value.sensorCount)

        val ok = vm.createOrUpdateGreenhouse(
            com.greenhands.app.sensor.model.GreenhousePhysicalConfig(
                lengthMeters = 10.0,
                widthMeters = 6.0,
                heightMeters = 3.0,
                cellSizeMeters = 1.0
            )
        )
        assertTrue(ok)
        val state = vm.state.value
        assertTrue(state.greenhouseConfigured)
        assertNull(state.configError)
        assertEquals(10, state.greenhouse.widthCells)
        assertEquals(6, state.greenhouse.heightCells)
        assertEquals(60, state.greenhouse.totalCells)
        assertTrue(state.sensors.isEmpty())
        assertNull(state.selectedSensorId)
        assertFalse(state.showSensorTypePicker)
        assertFalse(state.awaitingCellPlacement)
        assertEquals(60, state.coverage.blindSpotCells)
        assertEquals(0.0, state.coverage.overallCoveragePercent, 0.0)
    }

    @Test
    fun createOrUpdateGreenhouseRejectsInvalidConfigWithoutChangingGrid() {
        val vm = SensorPlacementViewModel()
        assertTrue(vm.addSensor(1.0, 1.0))
        val before = vm.state.value

        val ok = vm.createOrUpdateGreenhouse(
            com.greenhands.app.sensor.model.GreenhousePhysicalConfig(
                lengthMeters = 0.0,
                widthMeters = 8.0,
                heightMeters = 4.0,
                cellSizeMeters = 1.0
            )
        )
        assertFalse(ok)
        val state = vm.state.value
        assertFalse(state.greenhouseConfigured)
        assertNotNull(state.configError)
        assertEquals(before.greenhouse, state.greenhouse)
        assertEquals(before.sensors, state.sensors)
        assertEquals(1, state.sensorCount)
    }

    @Test
    fun createOrUpdateGreenhouseHalfMeterCellsProducesFortyByTwenty() {
        val vm = SensorPlacementViewModel()
        assertTrue(
            vm.createOrUpdateGreenhouse(
                com.greenhands.app.sensor.model.GreenhousePhysicalConfig(
                    lengthMeters = 20.0,
                    widthMeters = 10.0,
                    heightMeters = 4.0,
                    cellSizeMeters = 0.5
                )
            )
        )
        assertEquals(40, vm.state.value.greenhouse.widthCells)
        assertEquals(20, vm.state.value.greenhouse.heightCells)
        assertTrue(vm.addSensor(39.0, 19.0))
        assertFalse(vm.addSensor(40.0, 0.0))
        assertFalse(vm.addSensor(0.0, 20.0))
    }
}
