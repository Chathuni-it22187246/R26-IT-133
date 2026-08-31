package com.greenhands.app

import com.greenhands.app.sensor.ar.ArVisualizationMapper
import com.greenhands.app.sensor.domain.GreenhouseConfigFactory
import com.greenhands.app.sensor.model.CellCoverageState
import com.greenhands.app.sensor.model.DEFAULT_COVERAGE_RADIUS_CELLS
import com.greenhands.app.sensor.model.GreenhousePhysicalConfig
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ArVisualizationMapperTest {

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
    fun gridToMeterConversionAtOriginCellCentre() {
        val physical = GreenhousePhysicalConfig(cellSizeMeters = 1.0)
        assertEquals(0.5, GreenhouseConfigFactory.physicalXMeters(0.0, physical), 1e-9)
        assertEquals(0.5, GreenhouseConfigFactory.physicalYMeters(0.0, physical), 1e-9)

        val vm = SensorPlacementViewModel()
        vm.createOrUpdateGreenhouse(
            GreenhousePhysicalConfig(12.0, 8.0, 4.0, 1.0)
        )
        vm.addSensor(0.0, 0.0, type = SensorType.TEMPERATURE)
        val marker = ArVisualizationMapper.from(vm.state.value).sensors.single()
        assertEquals(0.5, marker.xMeters, 1e-9)
        assertEquals(0.5, marker.zMeters, 1e-9)
    }

    @Test
    fun fractionalCellSizeConversion() {
        val physical = GreenhousePhysicalConfig(
            lengthMeters = 20.0,
            widthMeters = 10.0,
            heightMeters = 4.0,
            cellSizeMeters = 0.5
        )
        assertEquals(1.75, GreenhouseConfigFactory.physicalXMeters(3.0, physical), 1e-9)
        assertEquals(2.25, GreenhouseConfigFactory.physicalYMeters(4.0, physical), 1e-9)

        val vm = SensorPlacementViewModel()
        assertTrue(vm.createOrUpdateGreenhouse(physical))
        vm.addSensor(3.0, 4.0, type = SensorType.HUMIDITY)
        val marker = ArVisualizationMapper.from(vm.state.value).sensors.single()
        assertEquals(1.75, marker.xMeters, 1e-9)
        assertEquals(2.25, marker.zMeters, 1e-9)
    }

    @Test
    fun coverageRadiusConvertsCellsToMeters() {
        val physical = GreenhousePhysicalConfig(cellSizeMeters = 0.5)
        val expected = DEFAULT_COVERAGE_RADIUS_CELLS * 0.5
        assertEquals(1.25, expected, 1e-9)

        val vm = SensorPlacementViewModel()
        vm.createOrUpdateGreenhouse(
            GreenhousePhysicalConfig(20.0, 10.0, 4.0, 0.5)
        )
        vm.addSensor(1.0, 1.0)
        val snap = ArVisualizationMapper.from(vm.state.value)
        assertEquals(1.25, snap.coverageRadiusMeters, 1e-9)
        assertEquals(1.25, snap.sensors.single().coverageRadiusMeters, 1e-9)
    }

    @Test
    fun sensorMappingPreservesIdentityAndDerivesMeters() {
        val vm = SensorPlacementViewModel()
        vm.createOrUpdateGreenhouse(GreenhousePhysicalConfig(12.0, 8.0, 4.0, 1.0))
        vm.addSensor(2.0, 3.0, type = SensorType.SOIL_MOISTURE)
        vm.setSensorActive(vm.state.value.sensors.single().id, false)

        val marker = ArVisualizationMapper.from(vm.state.value).sensors.single()
        assertEquals("S1", marker.id)
        assertEquals(SensorType.SOIL_MOISTURE, marker.type)
        assertEquals(SensorStatus.INACTIVE, marker.status)
        assertEquals(2.0, marker.gridX, 0.0)
        assertEquals(3.0, marker.gridY, 0.0)
        assertEquals(2.5, marker.xMeters, 1e-9)
        assertEquals(3.5, marker.zMeters, 1e-9)
    }

    @Test
    fun coverageMappingMatchesExistingUiStateResult() {
        val vm = SensorPlacementViewModel()
        vm.createOrUpdateGreenhouse(GreenhousePhysicalConfig(10.0, 8.0, 3.0, 1.0))
        vm.addSensor(1.0, 1.0, type = SensorType.TEMPERATURE)
        val ui = vm.state.value
        val typeCoverage = ui.coverageByType.forType(SensorType.TEMPERATURE)
        val snap = ArVisualizationMapper.from(ui, selectedTypeFilter = SensorType.TEMPERATURE)

        assertEquals(typeCoverage.cells.size, snap.coverageCells.size)
        typeCoverage.cells.forEach { cell ->
            val mapped = snap.coverageCells.single { it.column == cell.x && it.row == cell.y }
            assertEquals(cell.state, mapped.state)
        }
        assertTrue(snap.coverageCells.any { it.state == CellCoverageState.COVERED })
        assertTrue(snap.coverageCells.any { it.state == CellCoverageState.BLIND_SPOT })
    }

    @Test
    fun recommendationsAppearOnlyWhenOptimizationResultExists() {
        val vm = SensorPlacementViewModel()
        vm.createOrUpdateGreenhouse(GreenhousePhysicalConfig(12.0, 8.0, 4.0, 1.0))
        vm.addSensor(1.0, 1.0, type = SensorType.TEMPERATURE)

        assertTrue(ArVisualizationMapper.from(vm.state.value).recommendations.isEmpty())

        vm.selectOptimizationSensorType(SensorType.TEMPERATURE)
        vm.calculateOptimization()
        assertNotNull(vm.state.value.optimizationResult)

        val snap = ArVisualizationMapper.from(vm.state.value)
        val expectedCount = vm.state.value.optimizationResult!!.recommendedPositions.size
        assertEquals(expectedCount, snap.recommendations.size)
        assertEquals(
            (1..expectedCount).map { "P$it" },
            snap.recommendations.map { it.label }
        )
        assertEquals(SensorType.TEMPERATURE, snap.recommendations.first().type)
        assertTrue(snap.recommendations.all { it.selected })
        snap.recommendations.forEach { rec ->
            assertEquals(
                GreenhouseConfigFactory.physicalXMeters(rec.gridX, vm.state.value.physicalConfig),
                rec.xMeters,
                1e-9
            )
            assertEquals(
                GreenhouseConfigFactory.physicalYMeters(rec.gridY, vm.state.value.physicalConfig),
                rec.zMeters,
                1e-9
            )
        }
    }

    @Test
    fun afterApplyRecommendationsDisappearAndNewSensorsAppear() {
        val vm = SensorPlacementViewModel()
        vm.createOrUpdateGreenhouse(GreenhousePhysicalConfig(12.0, 8.0, 4.0, 1.0))
        vm.addSensor(1.0, 1.0, type = SensorType.TEMPERATURE)
        val existingId = vm.state.value.sensors.single().id

        vm.selectOptimizationSensorType(SensorType.TEMPERATURE)
        vm.calculateOptimization()
        val recommended = vm.state.value.optimizationResult!!.recommendedPositions
        assertTrue(vm.applyOptimization())
        assertNull(vm.state.value.optimizationResult)

        val snap = ArVisualizationMapper.from(vm.state.value)
        assertTrue(snap.recommendations.isEmpty())
        assertEquals(1 + recommended.size, snap.sensors.size)
        assertTrue(snap.sensors.any { it.id == existingId })
        val newIds = snap.sensors.map { it.id }.filter { it != existingId }
        assertEquals(recommended.size, newIds.size)
        assertTrue(newIds.all { it.startsWith("S") && it != "S0" })
        recommended.forEach { pos ->
            assertTrue(
                snap.sensors.any { it.gridX == pos.x && it.gridY == pos.y && it.type == SensorType.TEMPERATURE }
            )
        }
    }

    @Test
    fun dynamicGreenhouseConfigurationsPreservePhysicalAndGrid() {
        data class Case(
            val config: GreenhousePhysicalConfig,
            val widthCells: Int,
            val heightCells: Int
        )
        val cases = listOf(
            Case(GreenhousePhysicalConfig(10.0, 8.0, 3.0, 1.0), 10, 8),
            Case(GreenhousePhysicalConfig(12.0, 8.0, 4.0, 1.0), 12, 8),
            Case(GreenhousePhysicalConfig(20.0, 10.0, 4.0, 0.5), 40, 20)
        )
        cases.forEach { case ->
            val vm = SensorPlacementViewModel()
            assertTrue(vm.createOrUpdateGreenhouse(case.config))
            val snap = ArVisualizationMapper.from(vm.state.value)
            assertEquals(case.config.lengthMeters, snap.physical.lengthMeters, 0.0)
            assertEquals(case.config.widthMeters, snap.physical.widthMeters, 0.0)
            assertEquals(case.config.heightMeters, snap.physical.heightMeters, 0.0)
            assertEquals(case.config.cellSizeMeters, snap.physical.cellSizeMeters, 0.0)
            assertEquals(case.widthCells, snap.grid.widthCells)
            assertEquals(case.heightCells, snap.grid.heightCells)
            assertEquals(case.widthCells * case.heightCells, snap.coverageCells.size)
            assertFalse(snap.physical.lengthMeters == 12.0 && case.widthCells == 10)
        }
    }

    @Test
    fun mapperIsDeterministicForSameUiState() {
        val vm = SensorPlacementViewModel()
        vm.createOrUpdateGreenhouse(GreenhousePhysicalConfig(10.0, 8.0, 3.0, 1.0))
        vm.addSensor(2.0, 2.0, type = SensorType.LIGHT_INTENSITY)
        vm.goToStep(SensorWorkflowStep.COVERAGE)
        val ui = vm.state.value
        val a = ArVisualizationMapper.from(ui, SensorType.LIGHT_INTENSITY)
        val b = ArVisualizationMapper.from(ui, SensorType.LIGHT_INTENSITY)
        assertEquals(a, b)
    }

    @Test
    fun mapperDoesNotRequireAndroidApis() {
        // Pure JVM unit test: constructing a snapshot needs only domain/UI state models.
        val vm = SensorPlacementViewModel()
        val snap = ArVisualizationMapper.from(vm.state.value)
        assertEquals(SensorWorkflowStep.SETUP, snap.generatedAtStep)
        assertEquals(12, snap.grid.widthCells)
        assertEquals(8, snap.grid.heightCells)
        assertTrue(snap.sensors.isEmpty())
        assertTrue(snap.recommendations.isEmpty())
        assertNull(snap.selectedTypeFilter)
    }
}
