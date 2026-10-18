package com.greenhands.app

import com.greenhands.app.sensor.ar.ArCoverageCell
import com.greenhands.app.sensor.ar.ArGreenhouseFrameGeometry
import com.greenhands.app.sensor.ar.ArGreenhousePose
import com.greenhands.app.sensor.ar.ArOriginPlacementController
import com.greenhands.app.sensor.ar.ArOriginPlacementPhase
import com.greenhands.app.sensor.ar.ArRealCoveragePlacement
import com.greenhands.app.sensor.ar.ArRealRecommendationPlacement
import com.greenhands.app.sensor.ar.ArRealScale
import com.greenhands.app.sensor.ar.ArRealSensorPlacement
import com.greenhands.app.sensor.ar.ArRealTapPlacement
import com.greenhands.app.sensor.ar.ArRecommendationMarker
import com.greenhands.app.sensor.ar.ArSensorMarker
import com.greenhands.app.sensor.ar.ArVisualizationSnapshot
import com.greenhands.app.sensor.ar.ArWorldMapper
import com.greenhands.app.sensor.ar.RealArLayerVisibility
import com.greenhands.app.sensor.ar.RealArUxHelpers
import com.greenhands.app.sensor.domain.CoverageCalculator
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Real AR overlay — camera-first 1:1 greenhouse wireframe after ALIGNED, plus tap placement.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RealArRealWorldOverlayTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val physical = GreenhousePhysicalConfig(12.0, 8.0, 4.0, 1.0)
    private val greenhouse = Greenhouse(12, 8)

    @Before
    fun setMain() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun reset() {
        Dispatchers.resetMain()
    }

    @Test
    fun realWorldScaleIsOneMeterPerUnit() {
        assertEquals(1f, ArRealScale.METERS_PER_UNIT, 0f)
        assertEquals(1f, ArRealScale.rootScale(), 0f)
        assertEquals(12f, ArRealScale.localMetersToSceneUnits(12f), 0f)
        assertTrue(ArRealScale.isRealWorldMeters())
    }

    @Test
    fun coverageLayersFilterByCellState() {
        val snap = ArVisualizationSnapshot(
            physical = GreenhousePhysicalConfig(6.0, 6.0, 3.0, 1.0),
            grid = Greenhouse(6, 6),
            sensors = emptyList(),
            coverageCells = listOf(
                ArCoverageCell(0, 0, CellCoverageState.BLIND_SPOT),
                ArCoverageCell(1, 0, CellCoverageState.COVERED),
                ArCoverageCell(2, 0, CellCoverageState.OVERLAP)
            ),
            recommendations = emptyList(),
            coverageRadiusMeters = 1.0,
            selectedTypeFilter = null,
            generatedAtStep = SensorWorkflowStep.COVERAGE
        )
        val pose = ArWorldMapper.alignedPose(0f, 0f, 0f, 1f, 0f)
        val blindOnly = RealArLayerVisibility(
            covered = false,
            blindSpots = true,
            overlap = false
        )
        val cells = ArRealCoveragePlacement.buildRenderCells(pose, snap, blindOnly)
        assertEquals(1, cells.size)
        assertEquals(CellCoverageState.BLIND_SPOT, cells.single().cell.state)
    }

    @Test
    fun defaultLayersShowGreenhouseAfterAlignment() {
        val layers = RealArUxHelpers.defaultLayers()
        assertTrue(layers.greenhouse)
        assertTrue(layers.sensors)
        assertTrue(layers.covered)
        assertTrue(layers.blindSpots)
        assertTrue(layers.overlap)
        assertTrue(layers.recommendations)
        assertTrue(layers.shouldAttachGreenhouseGeometry(aligned = true))
        assertFalse(layers.shouldAttachGreenhouseGeometry(aligned = false))
        assertTrue(layers.shouldAttachSensors(aligned = true))
        assertTrue(layers.shouldAttachAnyCoverage(aligned = true))
    }

    @Test
    fun realArFrameNodesBuildSharedStructureAfterAlignment() {
        val frameNodes = java.io.File(
            "src/main/java/com/greenhands/app/sensor/ar/ArGreenhouseFrameNodes.kt"
        ).readText()
        assertTrue(frameNodes.contains("ArGreenhouseStructurePlan") || frameNodes.contains("structurePanels"))
        assertTrue(frameNodes.contains("CubeNode"))
        assertTrue(frameNodes.contains("FRAME_ROOT_NAME") || frameNodes.contains("greenhouse_frame_root"))
        assertFalse(frameNodes.contains("PlaneNode"))
        assertFalse(frameNodes.contains("ArGreenhouseStructureMaterials"))
        val plan = com.greenhands.app.sensor.ar.ArGreenhouseStructurePlan.plan(physical)
        assertTrue(com.greenhands.app.sensor.ar.ArGreenhouseStructurePlan.hasFloorGrid(plan))
        assertTrue(com.greenhands.app.sensor.ar.ArGreenhouseStructurePlan.hasPosts(plan))
        assertTrue(com.greenhands.app.sensor.ar.ArGreenhouseStructurePlan.hasPitchedRoof(plan))
        assertFalse(com.greenhands.app.sensor.ar.ArGreenhouseStructurePlan.hasOpaqueFloorSlab(plan))
    }

    @Test
    fun realArScreenAttachesGreenhouseWithoutOpaqueFootprint() {
        val screen = java.io.File(
            "src/main/java/com/greenhands/app/sensor/ui/RealGreenhouseArScreen.kt"
        ).readText()
        assertTrue(screen.contains("attachStructure"))
        assertFalse(screen.contains("greenhouse_origin"))
        assertFalse(screen.contains("addOriginMarker"))
        assertTrue(screen.contains("ArRealTapPlacement"))
        assertTrue(screen.contains("onAddSensor"))
        assertTrue(screen.contains("planeRenderer = showPlaneRenderer"))
        assertTrue(screen.contains("AMBIENT_INTENSITY") || screen.contains("ArSceneLighting"))
        assertFalse(screen.contains("CoverageCalculator"))
        assertFalse(screen.contains("SensorPlacementOptimizer"))
        assertFalse(screen.contains("scaleFactor"))
        assertFalse(screen.contains("ArTabletopScale"))
    }

    @Test
    fun worldToLocalRoundTripsWithLocalToWorld() {
        val pose = ArWorldMapper.alignedPose(1f, 0.2f, -2f, forwardX = 1f, forwardZ = 0f)
        val world = ArWorldMapper.localToWorld(pose, 5f, 1.1f, 3f)!!
        val local = ArWorldMapper.worldToLocal(pose, world.x, world.y, world.z)!!
        assertEquals(5f, local.x, 1e-4f)
        assertEquals(1.1f, local.y, 1e-4f)
        assertEquals(3f, local.z, 1e-4f)
    }

    @Test
    fun tapConvertsWorldToNearestGridCell() {
        val pose = ArWorldMapper.alignedPose(0f, 0f, 0f, 1f, 0f)
        val world = ArWorldMapper.localToWorld(pose, 3.2f, 0f, 1.7f)!!
        val result = ArRealTapPlacement.worldHitToGrid(
            pose = pose,
            worldX = world.x,
            worldY = world.y,
            worldZ = world.z,
            physical = physical,
            greenhouse = greenhouse,
            type = SensorType.TEMPERATURE
        ) as ArRealTapPlacement.PlacementResult.Ok
        assertEquals(3.0, result.gridX, 1e-6)
        assertEquals(1.0, result.gridY, 1e-6)
        assertEquals(SensorType.TEMPERATURE, result.type)
        assertEquals(3.5f, result.localXMeters, 1e-4f)
        assertEquals(1.5f, result.localZMeters, 1e-4f)
    }

    @Test
    fun tapOutsideGreenhouseIsOutOfBounds() {
        val pose = ArWorldMapper.alignedPose(0f, 0f, 0f, 1f, 0f)
        val world = ArWorldMapper.localToWorld(pose, 20f, 0f, 1f)!!
        val result = ArRealTapPlacement.worldHitToGrid(
            pose, world.x, world.y, world.z, physical, greenhouse, SensorType.HUMIDITY
        )
        assertTrue(result is ArRealTapPlacement.PlacementResult.OutOfBounds)
    }

    @Test
    fun manualPlacementCreatesSensorAndRecalculatesCoverage() {
        val vm = SensorPlacementViewModel()
        vm.createOrUpdateGreenhouse(physical)
        assertTrue(vm.addSensor(2.0, 3.0, type = SensorType.TEMPERATURE))
        assertEquals(1, vm.state.value.sensors.size)
        assertEquals(SensorType.TEMPERATURE, vm.state.value.sensors.single().type)
        assertEquals(2.0, vm.state.value.sensors.single().x, 1e-6)
        assertEquals(3.0, vm.state.value.sensors.single().y, 1e-6)
        val coverage = vm.state.value.coverage
        assertNotNull(coverage)
        val calc = CoverageCalculator.calculateByType(greenhouse, vm.state.value.sensors)
        assertEquals(calc.monitoring.overallCoveragePercent, coverage!!.overallCoveragePercent, 1e-6)
        assertEquals(calc.monitoring.blindSpotCells, coverage.blindSpotCells)
    }

    @Test
    fun multiplePlacementsAndRemovalUpdateState() {
        val vm = SensorPlacementViewModel()
        vm.createOrUpdateGreenhouse(physical)
        assertTrue(vm.addSensor(0.0, 0.0, type = SensorType.TEMPERATURE))
        assertTrue(vm.addSensor(5.0, 4.0, type = SensorType.HUMIDITY))
        assertTrue(vm.addSensor(8.0, 2.0, type = SensorType.TEMPERATURE))
        assertEquals(3, vm.state.value.sensors.size)
        val id = vm.state.value.sensors.first().id
        assertTrue(vm.removeSensor(id))
        assertEquals(2, vm.state.value.sensors.size)
        assertFalse(vm.state.value.sensors.any { it.id == id })
    }

    @Test
    fun differentTypesDoNotCreateSameTypeOverlapAlone() {
        val sensors = listOf(
            Sensor("T1", SensorType.TEMPERATURE, 2.0, 2.0),
            Sensor("H1", SensorType.HUMIDITY, 2.0, 2.0)
        )
        val byType = CoverageCalculator.calculateByType(greenhouse, sensors)
        val tempCell = byType.byType.getValue(SensorType.TEMPERATURE).cells
            .first { it.x == 2 && it.y == 2 }
        assertEquals(CellCoverageState.COVERED, tempCell.state)
        val monitoringCell = byType.monitoring.cells.first { it.x == 2 && it.y == 2 }
        assertTrue(
            monitoringCell.state == CellCoverageState.COVERED ||
                monitoringCell.state == CellCoverageState.OVERLAP
        )
        // Same-type overlap requires 2+ of one type
        val sameTypeOverlap = byType.byType.getValue(SensorType.TEMPERATURE).cells
            .none { it.state == CellCoverageState.OVERLAP }
        assertTrue(sameTypeOverlap)
    }

    @Test
    fun sameTypeOverlapMatchesCoverageCalculator() {
        val sensors = listOf(
            Sensor("T1", SensorType.TEMPERATURE, 2.0, 2.0),
            Sensor("T2", SensorType.TEMPERATURE, 3.0, 2.0)
        )
        val byType = CoverageCalculator.calculateByType(greenhouse, sensors)
        assertTrue(
            byType.byType.getValue(SensorType.TEMPERATURE).cells
                .any { it.state == CellCoverageState.OVERLAP }
        )
    }

    @Test
    fun coverageUsesConfiguredDimensionsNotHardCoded() {
        val custom = GreenhousePhysicalConfig(10.0, 6.0, 3.0, 0.5)
        val snap = ArVisualizationSnapshot(
            physical = custom,
            grid = Greenhouse(20, 12),
            sensors = emptyList(),
            coverageCells = listOf(ArCoverageCell(0, 0, CellCoverageState.BLIND_SPOT)),
            recommendations = emptyList(),
            coverageRadiusMeters = 1.25,
            selectedTypeFilter = null,
            generatedAtStep = SensorWorkflowStep.COVERAGE
        )
        val pose = ArWorldMapper.alignedPose(0f, 0f, 0f, 1f, 0f)
        val cells = ArRealCoveragePlacement.buildRenderCells(pose, snap)
        assertEquals(0.5f, cells.single().cellSizeMeters, 1e-4f)
    }

    @Test
    fun sensorsRemainSeparateFromRecommendations() {
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
                    gridX = 4.0,
                    gridY = 4.0,
                    xMeters = 4.5,
                    zMeters = 4.5,
                    selected = true
                )
            ),
            coverageRadiusMeters = 2.5,
            selectedTypeFilter = null,
            generatedAtStep = SensorWorkflowStep.OPTIMIZE
        )
        assertEquals(1, snap.sensors.size)
        assertEquals(1, snap.recommendations.size)
        assertEquals("S1", snap.sensors.single().id)
        assertEquals("P1", snap.recommendations.single().label)
    }

    @Test
    fun applyRecommendationsAddsSensorsKeepLeavesUnchanged() {
        val vm = SensorPlacementViewModel()
        vm.createOrUpdateGreenhouse(physical)
        vm.addSensor(1.0, 1.0, type = SensorType.TEMPERATURE)
        val before = vm.state.value.sensors
        vm.selectOptimizationSensorType(SensorType.TEMPERATURE)
        vm.calculateOptimization()
        val eval = vm.state.value.optimizationEvaluation
        if (eval != null && (eval.recommendedAdditionalCount ?: 0) > 0) {
            val countBeforeApply = vm.state.value.sensors.size
            vm.keepCurrentPlacement()
            assertEquals(countBeforeApply, vm.state.value.sensors.size)
            assertEquals(before.map { it.id }, vm.state.value.sensors.map { it.id })
            vm.calculateOptimization()
            if (vm.state.value.optimizationEvaluation != null) {
                vm.applyOptimization()
                assertTrue(vm.state.value.sensors.size >= countBeforeApply)
            }
        } else {
            vm.keepCurrentPlacement()
            assertEquals(before.size, vm.state.value.sensors.size)
        }
    }

    @Test
    fun coverageUsesFloorOutlinesNotOpaqueSlabs() {
        val coverageNodes = java.io.File(
            "src/main/java/com/greenhands/app/sensor/ar/ArCoverageNodes.kt"
        ).readText()
        assertTrue(coverageNodes.contains("OUTLINE_THICK"))
        assertTrue(coverageNodes.contains("addOutlineBar"))
        assertFalse(coverageNodes.contains("QUAD_THICKNESS"))
    }

    @Test
    fun layerTogglesAndResetsDoNotMutateSimulation() {
        val vm = SensorPlacementViewModel()
        vm.createOrUpdateGreenhouse(physical)
        vm.addSensor(1.0, 1.0, type = SensorType.TEMPERATURE)
        val sensorsBefore = vm.state.value.sensors
        var layers = RealArUxHelpers.defaultLayers().toggleCovered()
        assertFalse(layers.covered)
        layers = layers.toggleBlindSpots()
        assertFalse(layers.blindSpots)
        var pose = ArOriginPlacementController.onHorizontalPlaneDetected(ArGreenhousePose())
        pose = ArOriginPlacementController.onOriginPlaced(pose, 0f, 0f, 0f)
        pose = ArOriginPlacementController.beginSetDirection(pose)
        pose = ArOriginPlacementController.onDirectionPoint(pose, 3f, 0f, 0f).first
        val resetAlign = ArOriginPlacementController.resetAlignment(pose)
        assertEquals(ArOriginPlacementPhase.SETTING_DIRECTION, resetAlign.phase)
        assertEquals(0f, resetAlign.worldTranslationX!!, 1e-4f)
        val resetOrigin = ArOriginPlacementController.resetOrigin(pose)
        assertEquals(ArOriginPlacementPhase.SCANNING, resetOrigin.phase)
        assertEquals(sensorsBefore, vm.state.value.sensors)
    }

    @Test
    fun originAndYawWorldMappingAtRealScale() {
        val pose = ArWorldMapper.alignedPose(1f, 0.2f, -2f, forwardX = 1f, forwardZ = 0f)
        val farLength = ArWorldMapper.localToWorld(pose, 12f, 0f, 0f)!!
        assertEquals(13f, farLength.x, 1e-4f)
        assertEquals(-2f, farLength.z, 1e-4f)
        val farWidth = ArWorldMapper.localToWorld(pose, 0f, 0f, 8f)!!
        assertEquals(1f, farWidth.x, 1e-4f)
        assertEquals(6f, farWidth.z, 1e-4f)
    }

    @Test
    fun sensorLocalToWorldUsesSnapshotMeters() {
        val pose = ArWorldMapper.alignedPose(0f, 0f, 0f, 1f, 0f)
        val sensor = ArSensorMarker(
            id = "T1",
            type = SensorType.TEMPERATURE,
            status = SensorStatus.ACTIVE,
            gridX = 3.0,
            gridY = 2.0,
            xMeters = 3.0,
            zMeters = 2.0,
            coverageRadiusMeters = 1.0
        )
        val world = ArRealSensorPlacement.worldPosition(pose, sensor, physical)!!
        assertEquals(3f, world.x, 1e-4f)
        assertEquals(2f, world.z, 1e-4f)
        val mountY = ArRealSensorPlacement.mountHeightMeters(physical)
        assertEquals(mountY, world.y, 1e-4f)
    }
}
