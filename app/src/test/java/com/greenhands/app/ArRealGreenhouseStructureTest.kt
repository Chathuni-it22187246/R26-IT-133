package com.greenhands.app

import com.greenhands.app.sensor.ar.ArGreenhouseFrameGeometry
import com.greenhands.app.sensor.ar.ArRealScale
import com.greenhands.app.sensor.ar.ArWorldMapper
import com.greenhands.app.sensor.ar.RealArUxHelpers
import com.greenhands.app.sensor.ar.StructurePanelKind
import com.greenhands.app.sensor.domain.GreenhouseConfigFactory
import com.greenhands.app.sensor.model.GreenhousePhysicalConfig
import com.greenhands.app.sensor.model.SensorType
import com.greenhands.app.sensor.ui.SensorPlacementViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Virtual-parity Real AR greenhouse structure + tabletop scale.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ArRealGreenhouseStructureTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val physical = GreenhousePhysicalConfig(12.0, 8.0, 4.0, 1.0)

    @Before
    fun setMain() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun reset() {
        Dispatchers.resetMain()
    }

    @Test
    fun logicalDimensionsRemainTwelveByEightByFour() {
        assertEquals(12.0, physical.lengthMeters, 0.0)
        assertEquals(8.0, physical.widthMeters, 0.0)
        assertEquals(4.0, physical.heightMeters, 0.0)
        assertEquals(1.0, physical.cellSizeMeters, 0.0)
        val g = GreenhouseConfigFactory.toGreenhouseOrNull(physical)!!
        assertEquals(96, g.widthCells * g.heightCells)
    }

    @Test
    fun structurePanelsIncludeFloorWallsRoof() {
        val panels = ArGreenhouseFrameGeometry.structurePanels(12f, 8f, 4f)
        assertEquals(9, panels.size)
        assertEquals(1, panels.count { it.kind == StructurePanelKind.FLOOR })
        assertEquals(4, panels.count { it.kind == StructurePanelKind.WALL })
        assertEquals(4, panels.count { it.kind == StructurePanelKind.ROOF })
        val floor = panels.single { it.kind == StructurePanelKind.FLOOR }
        assertEquals(12f, floor.width, 1e-4f)
        assertEquals(8f, floor.height, 1e-4f)
        assertTrue(floor.normalY > 0.9f)
        // Floor stays horizontal; roof ridge above eaves
        val ridgeY = 4f * ArGreenhouseFrameGeometry.ROOF_PEAK_FACTOR
        val corners = ArGreenhouseFrameGeometry.localCorners(12f, 8f, 4f)
        assertEquals(0f, corners[0].y, 0f)
        assertEquals(4f, corners[4].y, 0f)
        assertEquals(ridgeY, corners[8].y, 1e-4f)
        assertTrue(corners[8].y > corners[4].y)
    }

    @Test
    fun structureFrameMatchesVirtualEdgeCount() {
        // 4 floor + 4 posts + 4 eave + 4 mid studs + 6 roof spokes = 22
        val edges = ArGreenhouseFrameGeometry.structureFrameEdges(12f, 8f, 4f)
        assertEquals(22, edges.size)
    }

    @Test
    fun floorGridProducesNinetySixCellsWorthOfLines() {
        val edges = ArGreenhouseFrameGeometry.floorGridEdges(12f, 8f, 1f)
        // 13 lines along length + 9 along width
        assertEquals(13 + 9, edges.size)
    }

    @Test
    fun noPanelIsFullVolumeCuboid() {
        val panels = ArGreenhouseFrameGeometry.structurePanels(12f, 8f, 4f)
        // Each panel is a thin sheet descriptor — never L×W×H volume.
        panels.forEach { panel ->
            assertTrue(panel.width > 0f)
            assertTrue(panel.height > 0f)
        }
        assertEquals(9, panels.size)
        assertTrue(ArGreenhouseFrameGeometry.AR_FLOOR_ALPHA < 1f)
        assertTrue(ArGreenhouseFrameGeometry.AR_WALL_ALPHA < 1f)
        assertTrue(ArGreenhouseFrameGeometry.AR_ROOF_ALPHA < 1f)
        val nodes = java.io.File(
            "src/main/java/com/greenhands/app/sensor/ar/ArGreenhouseFrameNodes.kt"
        ).readText()
        assertTrue(nodes.contains("PANEL_THICK"))
        assertFalse(nodes.contains("Size(length, height, width)"))
    }

    @Test
    fun rootScaleIsUniformPointZeroSixForDefaultHouse() {
        assertEquals(0.06f, ArRealScale.rootScale(physical), 1e-6f)
        val (l, w, h) = ArRealScale.displayedSizeMeters(physical)
        assertEquals(0.72f, l, 1e-4f)
        assertEquals(0.48f, w, 1e-4f)
        assertEquals(0.24f, h, 1e-4f)
    }

    @Test
    fun defaultLayerShowsGreenhouse() {
        assertTrue(RealArUxHelpers.defaultLayers().shouldAttachGreenhouseGeometry(true))
    }

    @Test
    fun sensorPlacementStillUpdatesSharedViewModel() {
        val vm = SensorPlacementViewModel()
        assertTrue(vm.createOrUpdateGreenhouse(physical))
        assertTrue(vm.addSensor(2.0, 3.0, type = SensorType.TEMPERATURE))
        assertEquals(1, vm.state.value.sensors.size)
        assertTrue(vm.state.value.coverage != null)
    }

    @Test
    fun virtualPreviewSourceStillDrawsCanvasGreenhouse() {
        val virtual = java.io.File(
            "src/main/java/com/greenhands/app/sensor/ar/VirtualGreenhouseRenderer.kt"
        ).readText()
        assertTrue(virtual.contains("Pitched roof") || virtual.contains("ridge") || virtual.contains("ROOF"))
        assertTrue(virtual.contains("NightElevated") || virtual.contains("floor"))
        // Real AR reuses geometry helper — does not delete Virtual renderer
        assertTrue(
            java.io.File("src/main/java/com/greenhands/app/sensor/ar/ArGreenhouseFrameGeometry.kt")
                .readText()
                .contains("structurePanels")
        )
    }

    @Test
    fun uprightMappingKeepsFloorHorizontalAndRoofAbove() {
        val pose = ArWorldMapper.alignedPose(0f, 0f, 0f, 1f, 0f)
        val scale = ArRealScale.rootScale(physical)
        val floor = ArWorldMapper.localToWorld(pose, 6f, 0f, 4f, scale)!!
        val eave = ArWorldMapper.localToWorld(pose, 6f, 4f, 4f, scale)!!
        val ridge = ArWorldMapper.localToWorld(
            pose,
            6f,
            4f * ArGreenhouseFrameGeometry.ROOF_PEAK_FACTOR,
            4f,
            scale
        )!!
        assertEquals(floor.y, 0f, 1e-4f)
        assertTrue(eave.y > floor.y)
        assertTrue(ridge.y > eave.y)
    }
}
