package com.greenhands.app

import com.greenhands.app.sensor.ar.ArVisualizationMapper
import com.greenhands.app.sensor.ar.OrbitCameraState
import com.greenhands.app.sensor.ar.VirtualGreenhouseLabels
import com.greenhands.app.sensor.ar.VirtualGreenhouseMath
import com.greenhands.app.sensor.ar.defaultOrbitForSnapshot
import com.greenhands.app.sensor.model.CellCoverageState
import com.greenhands.app.sensor.model.GreenhousePhysicalConfig
import com.greenhands.app.sensor.model.SensorType
import com.greenhands.app.sensor.ui.SensorPlacementViewModel
import com.greenhands.app.sensor.ui.resetOrbitCamera
import com.greenhands.app.ui.navigation.Routes
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

@OptIn(ExperimentalCoroutinesApi::class)
class VirtualGreenhousePreviewTest {

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
    fun virtualPreviewRouteExistsAndIsSensorContent() {
        assertEquals("sensor_virtual_preview", Routes.SENSOR_VIRTUAL_PREVIEW)
        assertTrue(Routes.isSensorContentRoute(Routes.SENSOR_VIRTUAL_PREVIEW))
        assertEquals(Routes.DASHBOARD, Routes.topLevelFor(Routes.SENSOR_VIRTUAL_PREVIEW))
    }

    @Test
    fun optimizeAndCoverageCanNavigateToVirtualPreviewWithoutLeavingSensorGraph() {
        assertTrue(Routes.SENSOR_OPTIMIZE in Routes.sensorContentRoutes)
        assertTrue(Routes.SENSOR_COVERAGE in Routes.sensorContentRoutes)
        assertTrue(Routes.SENSOR_VIRTUAL_PREVIEW in Routes.sensorContentRoutes)
        assertFalse(Routes.SENSOR_VIRTUAL_PREVIEW == Routes.SENSOR_OPTIMIZE)
    }

    @Test
    fun snapshotUsesPhysicalDimensionsForDynamicGreenhouses() {
        data class Case(val config: GreenhousePhysicalConfig, val w: Int, val h: Int)
        listOf(
            Case(GreenhousePhysicalConfig(10.0, 8.0, 3.0, 1.0), 10, 8),
            Case(GreenhousePhysicalConfig(12.0, 8.0, 4.0, 1.0), 12, 8),
            Case(GreenhousePhysicalConfig(20.0, 10.0, 4.0, 0.5), 40, 20)
        ).forEach { case ->
            val vm = SensorPlacementViewModel()
            assertTrue(vm.createOrUpdateGreenhouse(case.config))
            val snap = ArVisualizationMapper.from(vm.state.value)
            assertEquals(case.config.lengthMeters, snap.physical.lengthMeters, 0.0)
            assertEquals(case.config.widthMeters, snap.physical.widthMeters, 0.0)
            assertEquals(case.config.heightMeters, snap.physical.heightMeters, 0.0)
            assertEquals(case.w, snap.grid.widthCells)
            assertEquals(case.h, snap.grid.heightCells)
            val center = VirtualGreenhouseMath.greenhouseCenter(
                case.config.lengthMeters.toFloat(),
                case.config.widthMeters.toFloat(),
                case.config.heightMeters.toFloat()
            )
            assertEquals(case.config.lengthMeters.toFloat() * 0.5f, center.x, 1e-4f)
            assertEquals(case.config.widthMeters.toFloat() * 0.5f, center.z, 1e-4f)
            // Display strings come from snapshot physical (no hard-coded 12×8).
            assertEquals(
                VirtualGreenhouseLabels.greenhouseSizeLine(case.config),
                VirtualGreenhouseLabels.greenhouseSizeLine(snap.physical)
            )
            assertTrue(VirtualGreenhouseLabels.greenhouseSizeLine(snap.physical).contains("m × "))
            assertEquals(
                VirtualGreenhouseLabels.cellSizeLine(case.config),
                VirtualGreenhouseLabels.cellSizeLine(snap.physical)
            )
        }
    }

    @Test
    fun greenhouseDimensionLabelsComeFromSnapshotPhysical() {
        val vm = SensorPlacementViewModel()
        vm.createOrUpdateGreenhouse(GreenhousePhysicalConfig(10.0, 8.0, 3.0, 1.0))
        val snap = ArVisualizationMapper.from(vm.state.value)
        assertEquals("10.0m × 8.0m × 3.0m", VirtualGreenhouseLabels.greenhouseSizeLine(snap.physical))
        assertEquals("1.0m", VirtualGreenhouseLabels.cellSizeLine(snap.physical))
    }

    @Test
    fun sensorMarkerLabelsPreserveIdAndTypeAbbreviation() {
        val vm = SensorPlacementViewModel()
        vm.createOrUpdateGreenhouse(GreenhousePhysicalConfig(10.0, 8.0, 3.0, 1.0))
        vm.addSensor(2.0, 3.0, type = SensorType.TEMPERATURE)
        vm.addSensor(4.0, 3.0, type = SensorType.HUMIDITY)
        val snap = ArVisualizationMapper.from(vm.state.value)
        assertEquals(2, snap.sensors.size)
        val t = snap.sensors.first { it.type == SensorType.TEMPERATURE }
        val h = snap.sensors.first { it.type == SensorType.HUMIDITY }
        assertEquals("T" to "S1", VirtualGreenhouseLabels.sensorMarkerLines(t))
        assertEquals("H" to "S2", VirtualGreenhouseLabels.sensorMarkerLines(h))
        assertEquals(2.5, t.xMeters, 1e-9)
        assertEquals(3.5, t.zMeters, 1e-9)
    }

    @Test
    fun previewSnapshotPassesMeterCoordinatesAndCoverageWithoutRecalc() {
        val vm = SensorPlacementViewModel()
        vm.createOrUpdateGreenhouse(GreenhousePhysicalConfig(10.0, 8.0, 3.0, 1.0))
        vm.addSensor(2.0, 3.0, type = SensorType.TEMPERATURE)
        val ui = vm.state.value
        val expected = ui.coverageByType.forType(SensorType.TEMPERATURE)
        val snap = ArVisualizationMapper.from(ui, SensorType.TEMPERATURE)
        assertEquals(1, snap.sensors.size)
        assertEquals("S1", snap.sensors.single().id)
        assertEquals(2.5, snap.sensors.single().xMeters, 1e-9)
        assertEquals(3.5, snap.sensors.single().zMeters, 1e-9)
        assertEquals(expected.cells.size, snap.coverageCells.size)
        expected.cells.zip(snap.coverageCells).forEach { (src, mapped) ->
            assertEquals(src.x, mapped.column)
            assertEquals(src.y, mapped.row)
            assertEquals(src.state, mapped.state)
        }
        assertTrue(snap.coverageCells.any { it.state == CellCoverageState.COVERED })
        assertTrue(snap.coverageCells.any { it.state == CellCoverageState.BLIND_SPOT })
    }

    @Test
    fun recommendationsAppearInSnapshotThenVanishAfterApply() {
        val vm = SensorPlacementViewModel()
        vm.createOrUpdateGreenhouse(GreenhousePhysicalConfig(12.0, 8.0, 4.0, 1.0))
        vm.addSensor(1.0, 1.0, type = SensorType.TEMPERATURE)
        vm.selectOptimizationSensorType(SensorType.TEMPERATURE)
        vm.calculateOptimization()
        val withRecs = ArVisualizationMapper.from(vm.state.value)
        assertTrue(withRecs.recommendations.isNotEmpty())
        assertEquals(
            vm.state.value.optimizationResult!!.recommendedPositions.size,
            withRecs.recommendations.size
        )
        val recCount = withRecs.recommendations.size
        assertTrue(withRecs.recommendations.all { it.label.startsWith("P") })
        assertTrue(VirtualGreenhouseLabels.shouldDrawRecommendations(true, withRecs))
        assertEquals("Recommended", VirtualGreenhouseLabels.recommendationSecondary())
        assertTrue(vm.applyOptimization())
        val after = ArVisualizationMapper.from(vm.state.value)
        assertTrue(after.recommendations.isEmpty())
        assertFalse(VirtualGreenhouseLabels.shouldDrawRecommendations(true, after))
        assertEquals(1 + recCount, after.sensors.size)
    }

    @Test
    fun recommendationsHiddenWhenLayerDisabledEvenIfPresent() {
        val vm = SensorPlacementViewModel()
        vm.createOrUpdateGreenhouse(GreenhousePhysicalConfig(12.0, 8.0, 4.0, 1.0))
        vm.addSensor(1.0, 1.0, type = SensorType.TEMPERATURE)
        vm.selectOptimizationSensorType(SensorType.TEMPERATURE)
        vm.calculateOptimization()
        val snap = ArVisualizationMapper.from(vm.state.value)
        assertTrue(snap.recommendations.isNotEmpty())
        assertFalse(VirtualGreenhouseLabels.shouldDrawRecommendations(false, snap))
        assertTrue(VirtualGreenhouseLabels.shouldDrawRecommendations(true, snap))
    }

    @Test
    fun typeFilterAllUsesMonitoringWithoutSameTypeOverlapRequirement() {
        val vm = SensorPlacementViewModel()
        vm.createOrUpdateGreenhouse(GreenhousePhysicalConfig(12.0, 8.0, 4.0, 1.0))
        vm.addSensor(3.0, 3.0, type = SensorType.TEMPERATURE)
        vm.addSensor(3.5, 3.0, type = SensorType.HUMIDITY)
        val allSnap = ArVisualizationMapper.from(vm.state.value, selectedTypeFilter = null)
        assertEquals(null, allSnap.selectedTypeFilter)
        assertEquals(0, allSnap.coverageCells.count { it.state == CellCoverageState.OVERLAP })
        val tempSnap = ArVisualizationMapper.from(vm.state.value, SensorType.TEMPERATURE)
        assertEquals(SensorType.TEMPERATURE, tempSnap.selectedTypeFilter)
        assertEquals(
            vm.state.value.coverageByType.forType(SensorType.TEMPERATURE).cells.size,
            tempSnap.coverageCells.size
        )
    }

    @Test
    fun resetCameraReturnsToDefaultOrbitForSnapshot() {
        val vm = SensorPlacementViewModel()
        vm.createOrUpdateGreenhouse(GreenhousePhysicalConfig(10.0, 8.0, 3.0, 1.0))
        val snap = ArVisualizationMapper.from(vm.state.value)
        val defaults = defaultOrbitForSnapshot(snap)
        val moved = OrbitCameraState(
            yawDeg = 90f,
            pitchDeg = 60f,
            distance = defaults.distance + 8f,
            panX = 2f,
            panZ = -1f
        )
        assertNotEquals(defaults.yawDeg, moved.yawDeg)
        val reset = resetOrbitCamera(snap)
        assertEquals(defaults, reset)
        assertEquals(defaults.distance, VirtualGreenhouseMath.defaultDistance(10f, 8f, 3f), 1e-4f)
    }

    @Test
    fun defaultDistanceKeepsDynamicGreenhousesReadable() {
        listOf(
            Triple(10f, 8f, 3f),
            Triple(12f, 8f, 4f),
            Triple(20f, 10f, 4f)
        ).forEach { (l, w, h) ->
            val d = VirtualGreenhouseMath.defaultDistance(l, w, h)
            assertTrue("distance too far for ${l}x${w}x${h}: $d", d <= maxOf(l, w) * 1.6f)
            assertTrue("distance too close for ${l}x${w}x${h}: $d", d >= 5.5f)
        }
    }

    @Test
    fun blindCellCountComesFromSnapshotCoverageCells() {
        val vm = SensorPlacementViewModel()
        vm.createOrUpdateGreenhouse(GreenhousePhysicalConfig(10.0, 8.0, 3.0, 1.0))
        val empty = ArVisualizationMapper.from(vm.state.value)
        assertEquals(
            empty.coverageCells.count { it.state == CellCoverageState.BLIND_SPOT },
            VirtualGreenhouseLabels.blindCellCount(empty)
        )
        vm.addSensor(2.0, 3.0, type = SensorType.TEMPERATURE)
        val withSensor = ArVisualizationMapper.from(vm.state.value)
        assertTrue(VirtualGreenhouseLabels.blindCellCount(withSensor) < empty.coverageCells.size)
    }

    @Test
    fun virtualPreviewDoesNotRequireArcoreOrCameraPermission() {
        assertTrue(Routes.SENSOR_VIRTUAL_PREVIEW.isNotBlank())
        val vm = SensorPlacementViewModel()
        val snap = ArVisualizationMapper.from(vm.state.value)
        assertTrue(snap.physical.lengthMeters > 0)
        val cam = VirtualGreenhouseMath.cameraPosition(
            VirtualGreenhouseMath.greenhouseCenter(10f, 8f, 3f),
            OrbitCameraState()
        )
        assertTrue(cam.length() > 0f)
    }
}
