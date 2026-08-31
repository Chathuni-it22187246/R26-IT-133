package com.greenhands.app

import com.greenhands.app.sensor.ar.ArCoverageCell
import com.greenhands.app.sensor.ar.ArOriginPlacementPhase
import com.greenhands.app.sensor.ar.ArRealCoveragePlacement
import com.greenhands.app.sensor.ar.ArRealTapPlacement
import com.greenhands.app.sensor.ar.ArRecommendationMarker
import com.greenhands.app.sensor.ar.ArSensorMarker
import com.greenhands.app.sensor.ar.ArVisualizationMapper
import com.greenhands.app.sensor.ar.ArVisualizationSnapshot
import com.greenhands.app.sensor.ar.ArWorldMapper
import com.greenhands.app.sensor.ar.RealArLayerVisibility
import com.greenhands.app.sensor.ar.RealArUxHelpers
import com.greenhands.app.sensor.domain.CoverageCalculator
import com.greenhands.app.sensor.domain.SensorIdFactory
import com.greenhands.app.sensor.model.CellCoverageState
import com.greenhands.app.sensor.model.Greenhouse
import com.greenhands.app.sensor.model.GreenhousePhysicalConfig
import com.greenhands.app.sensor.model.Sensor
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Real AR research workflow — world mapping, manual placement, coverage layers.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ArRealManualPlacementTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val physical = GreenhousePhysicalConfig(12.0, 8.0, 4.0, 1.0)
    private val greenhouse = Greenhouse(12, 8)
    private val displayScale get() = com.greenhands.app.sensor.ar.ArRealScale.rootScale(physical)

    private fun worldOf(
        pose: com.greenhands.app.sensor.ar.ArGreenhousePose,
        lx: Float,
        ly: Float,
        lz: Float
    ) = ArWorldMapper.localToWorld(pose, lx, ly, lz, displayScale)!!

    @Before
    fun setMain() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun reset() {
        Dispatchers.resetMain()
    }

    @Test
    fun worldHitConvertsToLocalMeters() {
        val pose = ArWorldMapper.alignedPose(2f, 0.1f, -1f, forwardX = 1f, forwardZ = 0f)
        val world = worldOf(pose, 4.2f, 0f, 3.1f)
        val local = ArWorldMapper.worldToLocal(pose, world.x, world.y, world.z, displayScale)!!
        assertEquals(4.2f, local.x, 1e-4f)
        assertEquals(3.1f, local.z, 1e-4f)
    }

    @Test
    fun yawInverseMappingRoundTrips() {
        val pose = ArWorldMapper.alignedPose(0f, 0f, 0f, forwardX = 0f, forwardZ = 1f)
        val world = worldOf(pose, 5f, 0.5f, 2f)
        val local = ArWorldMapper.worldToLocal(pose, world.x, world.y, world.z, displayScale)!!
        assertEquals(5f, local.x, 1e-4f)
        assertEquals(0.5f, local.y, 1e-4f)
        assertEquals(2f, local.z, 1e-4f)
    }

    @Test
    fun localMetersMapToNearestGridCell() {
        val pose = ArWorldMapper.alignedPose(0f, 0f, 0f, 1f, 0f)
        val world = worldOf(pose, 7.8f, 0f, 2.1f)
        val ok = ArRealTapPlacement.worldHitToGrid(
            pose, world.x, world.y, world.z, physical, greenhouse, SensorType.TEMPERATURE
        ) as ArRealTapPlacement.PlacementResult.Ok
        assertEquals(7.0, ok.gridX, 0.0)
        assertEquals(2.0, ok.gridY, 0.0)
    }

    @Test
    fun outOfBoundsTapIsRejected() {
        val pose = ArWorldMapper.alignedPose(0f, 0f, 0f, 1f, 0f)
        val world = worldOf(pose, -1f, 0f, 1f)
        val result = ArRealTapPlacement.worldHitToGrid(
            pose, world.x, world.y, world.z, physical, greenhouse, SensorType.HUMIDITY
        )
        assertTrue(result is ArRealTapPlacement.PlacementResult.OutOfBounds)
    }

    @Test
    fun notAlignedRejectsPlacement() {
        val pose = ArWorldMapper.alignedPose(0f, 0f, 0f, 1f, 0f)
            .copy(phase = ArOriginPlacementPhase.ORIGIN_PLACED)
        val result = ArRealTapPlacement.worldHitToGrid(
            pose, 1f, 0f, 1f, physical, greenhouse, SensorType.TEMPERATURE
        )
        assertTrue(result is ArRealTapPlacement.PlacementResult.NotAligned)
    }

    @Test
    fun placeTemperatureHumiditySoilLight() {
        val vm = SensorPlacementViewModel()
        vm.createOrUpdateGreenhouse(physical)
        assertTrue(vm.addSensor(0.0, 0.0, type = SensorType.TEMPERATURE))
        assertTrue(vm.addSensor(1.0, 0.0, type = SensorType.HUMIDITY))
        assertTrue(vm.addSensor(2.0, 0.0, type = SensorType.SOIL_MOISTURE))
        assertTrue(vm.addSensor(3.0, 0.0, type = SensorType.LIGHT_INTENSITY))
        val types = vm.state.value.sensors.map { it.type }.toSet()
        assertEquals(
            setOf(
                SensorType.TEMPERATURE,
                SensorType.HUMIDITY,
                SensorType.SOIL_MOISTURE,
                SensorType.LIGHT_INTENSITY
            ),
            types
        )
    }

    @Test
    fun sensorIdsUseFactorySequence() {
        assertEquals("S1", SensorIdFactory.idFor(1))
        assertEquals("S2", SensorIdFactory.idFor(2))
        val vm = SensorPlacementViewModel()
        vm.createOrUpdateGreenhouse(physical)
        vm.addSensor(0.0, 0.0, type = SensorType.TEMPERATURE)
        vm.addSensor(1.0, 1.0, type = SensorType.HUMIDITY)
        assertEquals(listOf("S1", "S2"), vm.state.value.sensors.map { it.id })
    }

    @Test
    fun placementUpdatesSharedSensorState() {
        val vm = SensorPlacementViewModel()
        vm.createOrUpdateGreenhouse(physical)
        assertEquals(0, vm.state.value.sensors.size)
        val pose = ArWorldMapper.alignedPose(0f, 0f, 0f, 1f, 0f)
        val world = worldOf(pose, 4.2f, 0f, 1.1f)
        val tap = ArRealTapPlacement.worldHitToGrid(
            pose, world.x, world.y, world.z, physical, greenhouse, SensorType.TEMPERATURE
        ) as ArRealTapPlacement.PlacementResult.Ok
        assertTrue(vm.addSensor(tap.gridX, tap.gridY, type = tap.type))
        assertEquals(1, vm.state.value.sensors.size)
        assertEquals(SensorType.TEMPERATURE, vm.state.value.sensors.single().type)
        assertEquals(4.0, vm.state.value.sensors.single().x, 0.0)
        assertEquals(1.0, vm.state.value.sensors.single().y, 0.0)
    }

    @Test
    fun coverageAndBlindSpotsUpdateAfterPlacement() {
        val vm = SensorPlacementViewModel()
        vm.createOrUpdateGreenhouse(physical)
        val beforeBlind = vm.state.value.coverage!!.blindSpotCells
        assertEquals(greenhouse.totalCells, beforeBlind)
        vm.addSensor(5.0, 4.0, type = SensorType.TEMPERATURE)
        val after = vm.state.value.coverage!!
        assertTrue(after.coveredCells > 0)
        assertTrue(after.blindSpotCells < beforeBlind)
        val calc = CoverageCalculator.calculateByType(greenhouse, vm.state.value.sensors)
        assertEquals(calc.monitoring.blindSpotCells, after.blindSpotCells)
        assertEquals(calc.monitoring.coveredCells, after.coveredCells)
    }

    @Test
    fun sameTypeOverlapUpdates() {
        val vm = SensorPlacementViewModel()
        vm.createOrUpdateGreenhouse(physical)
        vm.addSensor(2.0, 2.0, type = SensorType.TEMPERATURE)
        vm.addSensor(3.0, 2.0, type = SensorType.TEMPERATURE)
        val byType = vm.state.value.coverageByType!!
        assertTrue(
            byType.byType.getValue(SensorType.TEMPERATURE).cells
                .any { it.state == CellCoverageState.OVERLAP }
        )
    }

    @Test
    fun crossTypeDoesNotCreateSameTypeOverlap() {
        val sensors = listOf(
            Sensor("S1", SensorType.TEMPERATURE, 2.0, 2.0),
            Sensor("S2", SensorType.HUMIDITY, 2.0, 2.0)
        )
        val byType = CoverageCalculator.calculateByType(greenhouse, sensors)
        assertFalse(
            byType.byType.getValue(SensorType.TEMPERATURE).cells
                .any { it.state == CellCoverageState.OVERLAP }
        )
        assertFalse(
            byType.byType.getValue(SensorType.HUMIDITY).cells
                .any { it.state == CellCoverageState.OVERLAP }
        )
    }

    @Test
    fun removalRecalculatesCoverage() {
        val vm = SensorPlacementViewModel()
        vm.createOrUpdateGreenhouse(physical)
        vm.addSensor(2.0, 2.0, type = SensorType.TEMPERATURE)
        vm.addSensor(8.0, 6.0, type = SensorType.TEMPERATURE)
        val withTwo = vm.state.value.coverage!!.coveredCells
        val id = vm.state.value.sensors.first().id
        assertTrue(vm.removeSensor(id))
        assertEquals(1, vm.state.value.sensors.size)
        assertTrue(vm.state.value.coverage!!.coveredCells < withTwo)
    }

    @Test
    fun resetPlacementClearsSensorsKeepsDimensions() {
        val vm = SensorPlacementViewModel()
        vm.createOrUpdateGreenhouse(physical)
        vm.addSensor(1.0, 1.0, type = SensorType.TEMPERATURE)
        vm.resetSensors()
        assertTrue(vm.state.value.sensors.isEmpty())
        assertEquals(physical, vm.state.value.physicalConfig)
        assertEquals(greenhouse.totalCells, vm.state.value.coverage!!.blindSpotCells)
    }

    @Test
    fun typeFilterSelectsMonitoringVsByType() {
        val vm = SensorPlacementViewModel()
        vm.createOrUpdateGreenhouse(physical)
        vm.addSensor(2.0, 2.0, type = SensorType.TEMPERATURE)
        vm.addSensor(6.0, 5.0, type = SensorType.HUMIDITY)
        val all = ArVisualizationMapper.from(vm.state.value, selectedTypeFilter = null)
        val temp = ArVisualizationMapper.from(vm.state.value, selectedTypeFilter = SensorType.TEMPERATURE)
        assertNotEquals(all.coverageCells, temp.coverageCells)
        assertTrue(all.sensors.size >= temp.sensors.size)
    }

    @Test
    fun coveredBlindOverlapLayerFiltering() {
        val snap = ArVisualizationSnapshot(
            physical = physical,
            grid = greenhouse,
            sensors = emptyList(),
            coverageCells = listOf(
                ArCoverageCell(0, 0, CellCoverageState.COVERED),
                ArCoverageCell(1, 0, CellCoverageState.BLIND_SPOT),
                ArCoverageCell(2, 0, CellCoverageState.OVERLAP)
            ),
            recommendations = emptyList(),
            coverageRadiusMeters = 2.5,
            selectedTypeFilter = null,
            generatedAtStep = SensorWorkflowStep.COVERAGE
        )
        val pose = ArWorldMapper.alignedPose(0f, 0f, 0f, 1f, 0f)
        assertEquals(
            1,
            ArRealCoveragePlacement.buildRenderCells(
                pose, snap, RealArLayerVisibility(covered = true, blindSpots = false, overlap = false)
            ).size
        )
        assertEquals(
            1,
            ArRealCoveragePlacement.buildRenderCells(
                pose, snap, RealArLayerVisibility(covered = false, blindSpots = true, overlap = false)
            ).size
        )
        assertEquals(
            1,
            ArRealCoveragePlacement.buildRenderCells(
                pose, snap, RealArLayerVisibility(covered = false, blindSpots = false, overlap = true)
            ).size
        )
    }

    @Test
    fun pSharpRemainsSeparateFromActualSensor() {
        val snap = ArVisualizationSnapshot(
            physical = physical,
            grid = greenhouse,
            sensors = listOf(
                ArSensorMarker(
                    id = "S1",
                    type = SensorType.TEMPERATURE,
                    status = SensorStatus.ACTIVE,
                    gridX = 1.0,
                    gridY = 1.0,
                    xMeters = 1.5,
                    zMeters = 1.5,
                    coverageRadiusMeters = 2.5
                )
            ),
            coverageCells = emptyList(),
            recommendations = listOf(
                ArRecommendationMarker(
                    rank = 1,
                    label = "P1",
                    type = SensorType.TEMPERATURE,
                    gridX = 5.0,
                    gridY = 5.0,
                    xMeters = 5.5,
                    zMeters = 5.5,
                    selected = true
                )
            ),
            coverageRadiusMeters = 2.5,
            selectedTypeFilter = null,
            generatedAtStep = SensorWorkflowStep.OPTIMIZE
        )
        assertEquals("S1", snap.sensors.single().id)
        assertEquals("P1", snap.recommendations.single().label)
        assertFalse(snap.sensors.any { it.id.startsWith("P") })
    }

    @Test
    fun realArAttachesVirtualParityGreenhouseStructure() {
        val nodes = java.io.File(
            "src/main/java/com/greenhands/app/sensor/ar/ArGreenhouseFrameNodes.kt"
        ).readText()
        assertTrue(nodes.contains("structurePanels"))
        assertTrue(nodes.contains("setScale"))
        assertTrue(nodes.contains("ArRealScale.rootScale"))
        assertFalse(nodes.contains("PlaneNode"))
        val screen = java.io.File(
            "src/main/java/com/greenhands/app/sensor/ui/RealGreenhouseArScreen.kt"
        ).readText()
        assertTrue(screen.contains("includeStructure"))
        assertTrue(screen.contains("AR_FLOOR_ALPHA"))
        assertTrue(screen.contains("ArRealTapPlacement"))
        assertTrue(screen.contains("onAddSensor"))
        assertTrue(screen.contains("clearSceneNodes()"))
        assertFalse(screen.contains("greenhouse_origin"))
        assertFalse(screen.contains("addOriginMarker"))
        assertFalse(screen.contains("NightElevated"))
        val virtual = java.io.File(
            "src/main/java/com/greenhands/app/sensor/ar/VirtualGreenhouseRenderer.kt"
        ).readText()
        assertTrue(virtual.contains("NightElevated"))
        assertTrue(virtual.contains("Pitched roof") || virtual.contains("roof"))
    }

    @Test
    fun defaultLayersIncludeGreenhouseStructure() {
        val layers = RealArUxHelpers.defaultLayers()
        assertTrue(layers.guide)
        assertTrue(layers.shouldAttachGuide(true))
        assertTrue(layers.sensors)
        assertTrue(layers.covered)
        assertTrue(layers.blindSpots)
        assertTrue(layers.overlap)
        assertTrue(layers.recommendations)
    }
}
