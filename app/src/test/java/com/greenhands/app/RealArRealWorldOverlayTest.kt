package com.greenhands.app

import com.greenhands.app.sensor.ar.ArCoverageCell
import com.greenhands.app.sensor.ar.ArCoverageNodes
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
import com.greenhands.app.sensor.ar.ArVisualizationMapper
import com.greenhands.app.sensor.ar.ArVisualizationSnapshot
import com.greenhands.app.sensor.ar.ArWorldMapper
import com.greenhands.app.sensor.ar.RealArLayerVisibility
import com.greenhands.app.sensor.ar.RealArUxHelpers
import com.greenhands.app.sensor.domain.CoverageCalculator
import com.greenhands.app.sensor.domain.GreenhouseConfigFactory
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
 * Real AR overlay — camera-first, no virtual greenhouse model, manual tap placement.
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
    fun tabletopRootScaleShrinksTwelveMeterHouseToPointSeventyTwo() {
        val physical = GreenhousePhysicalConfig(12.0, 8.0, 4.0, 1.0)
        assertEquals(0.06f, ArRealScale.rootScale(physical), 1e-6f)
        val (dispL, dispW, dispH) = ArRealScale.displayedSizeMeters(physical)
        assertEquals(0.72f, dispL, 1e-4f)
        assertEquals(0.48f, dispW, 1e-4f)
        assertEquals(0.24f, dispH, 1e-4f)
        assertEquals(0.72f, ArRealScale.localMetersToSceneUnits(12f, physical), 1e-4f)
        assertTrue(ArRealScale.isTabletopDisplayScale(physical))
        // Logical config unchanged
        assertEquals(12.0, physical.lengthMeters, 0.0)
        assertEquals(8.0, physical.widthMeters, 0.0)
        assertEquals(4.0, physical.heightMeters, 0.0)
    }

    @Test
    fun displayScaleRoundTripsLocalToWorld() {
        val physical = GreenhousePhysicalConfig(12.0, 8.0, 4.0, 1.0)
        val scale = ArRealScale.rootScale(physical)
        val pose = ArWorldMapper.alignedPose(1f, 0.2f, -2f, forwardX = 1f, forwardZ = 0f)
        val world = ArWorldMapper.localToWorld(pose, 6f, 1f, 4f, scale)!!
        val local = ArWorldMapper.worldToLocal(pose, world.x, world.y, world.z, scale)!!
        assertEquals(6f, local.x, 1e-4f)
        assertEquals(1f, local.y, 1e-4f)
        assertEquals(4f, local.z, 1e-4f)
        // Displayed offset along +X is 6 * 0.06 = 0.36 m
        assertEquals(1f + 0.36f, world.x, 1e-4f)
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
    fun defaultLayersShowVirtualParityGreenhouse() {
        val layers = RealArUxHelpers.defaultLayers()
        assertTrue(layers.guide)
        assertTrue(layers.greenhouse)
        assertTrue(layers.sensors)
        assertTrue(layers.covered)
        assertTrue(layers.blindSpots)
        assertTrue(layers.overlap)
        assertTrue(layers.recommendations)
        assertTrue(layers.shouldAttachGuide(aligned = true))
        assertTrue(layers.shouldAttachGreenhouseGeometry(aligned = true))
        assertTrue(layers.shouldAttachSensors(aligned = true))
        assertTrue(layers.shouldAttachAnyCoverage(aligned = true))
        assertFalse(layers.shouldAttachGreenhouseGeometry(aligned = false))
    }

    @Test
    fun realArFrameNodesAttachTranslucentStructureNotOpaqueCuboid() {
        val frameNodes = java.io.File(
            "src/main/java/com/greenhands/app/sensor/ar/ArGreenhouseFrameNodes.kt"
        ).readText()
        assertTrue(frameNodes.contains("structurePanels"))
        assertTrue(frameNodes.contains("structureFrameEdges"))
        assertTrue(frameNodes.contains("floorGridEdges"))
        assertTrue(frameNodes.contains("setScale"))
        assertTrue(frameNodes.contains("ArRealScale.rootScale"))
        assertTrue(frameNodes.contains("PANEL_THICK"))
        assertFalse(frameNodes.contains("PlaneNode"))
        // Never a single L×W×H opaque volume
        assertFalse(frameNodes.contains("Size(length, height, width)"))
        assertFalse(frameNodes.contains("opaqueColor"))
    }

    @Test
    fun realArScreenBuildsTransparentStructureOnAnchor() {
        val screen = java.io.File(
            "src/main/java/com/greenhands/app/sensor/ui/RealGreenhouseArScreen.kt"
        ).readText()
        assertTrue(screen.contains("includeStructure"))
        assertTrue(screen.contains("AR_FLOOR_ALPHA"))
        assertTrue(screen.contains("AR_WALL_ALPHA"))
        assertTrue(screen.contains("AR_ROOF_ALPHA"))
        assertTrue(screen.contains("arOverlay"))
        assertTrue(screen.contains("ArRealTapPlacement"))
        assertTrue(screen.contains("onAddSensor"))
        assertTrue(screen.contains("planeRenderer = showPlaneRenderer"))
        assertTrue(screen.contains("ENVIRONMENTAL_HDR"))
        assertFalse(screen.contains("CoverageCalculator"))
        assertFalse(screen.contains("SensorPlacementOptimizer"))
        assertFalse(screen.contains("NightElevated"))
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
        val scale = ArRealScale.rootScale(physical)
        val world = ArWorldMapper.localToWorld(pose, 3.2f, 0f, 1.7f, scale)!!
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
        val scale = ArRealScale.rootScale(physical)
        val world = ArWorldMapper.localToWorld(pose, 20f, 0f, 1f, scale)!!
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
    fun coverageUsesIndependentTranslucentCellsNotFullCuboid() {
        val coverageNodes = java.io.File(
            "src/main/java/com/greenhands/app/sensor/ar/ArCoverageNodes.kt"
        ).readText()
        assertTrue(coverageNodes.contains("CELL_BODY_HEIGHT"))
        assertTrue(coverageNodes.contains("cellGeometry"))
        assertTrue(coverageNodes.contains("_body"))
        assertFalse(coverageNodes.contains("PlaneNode("))
        assertFalse(coverageNodes.contains("structurePanels("))
        assertFalse(coverageNodes.contains("QUAD_THICKNESS"))

        val geom = ArCoverageNodes.cellGeometry(1f)
        assertEquals(0.88f, geom.bodySizeX, 1e-4f)
        assertEquals(ArCoverageNodes.CELL_BODY_HEIGHT, geom.bodySizeY, 1e-4f)
        assertFalse(
            ArCoverageNodes.isFullGreenhouseCuboid(
                geom.bodySizeX,
                geom.bodySizeY,
                geom.bodySizeZ,
                lengthMeters = 12f,
                widthMeters = 8f,
                heightMeters = 4f
            )
        )
    }

    @Test
    fun twelveByEightGreenhouseHasNinetySixFloorCells() {
        val physical = GreenhousePhysicalConfig(12.0, 8.0, 4.0, 1.0)
        val greenhouse = GreenhouseConfigFactory.toGreenhouseOrNull(physical)!!
        assertEquals(12, greenhouse.widthCells)
        assertEquals(8, greenhouse.heightCells)
        assertEquals(96, greenhouse.widthCells * greenhouse.heightCells)

        val vm = SensorPlacementViewModel()
        vm.createOrUpdateGreenhouse(physical)
        vm.addSensor(2.0, 2.0, type = SensorType.TEMPERATURE)
        val snap = ArVisualizationMapper.from(vm.state.value, SensorType.TEMPERATURE)
        assertEquals(96, snap.coverageCells.size)

        val pose = ArWorldMapper.alignedPose(0f, 0f, 0f, 1f, 0f)
        val cells = ArRealCoveragePlacement.buildRenderCells(pose, snap)
        assertEquals(96, cells.size)
        assertEquals(96, cells.map { "${it.cell.column},${it.cell.row}" }.toSet().size)

        cells.forEach { cell ->
            val geom = ArCoverageNodes.cellGeometry(cell.cellSizeMeters)
            assertFalse(
                ArCoverageNodes.isFullGreenhouseCuboid(
                    geom.bodySizeX,
                    geom.bodySizeY,
                    geom.bodySizeZ,
                    12f,
                    8f,
                    4f
                )
            )
        }
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
