package com.greenhands.app

import com.greenhands.app.sensor.ar.ArGreenhouseFrameGeometry
import com.greenhands.app.sensor.ar.ArGreenhousePose
import com.greenhands.app.sensor.ar.ArOriginPlacementController
import com.greenhands.app.sensor.ar.ArOriginPlacementPhase
import com.greenhands.app.sensor.ar.ArVisualizationMapper
import com.greenhands.app.sensor.ar.ArWorldMapper
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.abs

@OptIn(ExperimentalCoroutinesApi::class)
class ArGreenhouseFrameGeometryTest {

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
    fun dimensionsTenByEightByThreeMapToLocalExtents() {
        val corners = ArGreenhouseFrameGeometry.localCorners(10f, 8f, 3f)
        assertEquals(0f, corners.minOf { it.x }, 1e-5f)
        assertEquals(10f, corners.maxOf { it.x }, 1e-5f)
        assertEquals(0f, corners.minOf { it.z }, 1e-5f)
        assertEquals(8f, corners.maxOf { it.z }, 1e-5f)
        assertEquals(0f, corners.minOf { it.y }, 1e-5f)
        assertEquals(3f * ArGreenhouseFrameGeometry.ROOF_PEAK_FACTOR, corners.maxOf { it.y }, 1e-5f)
        assertEquals(
            "10.0m × 8.0m × 3.0m",
            ArGreenhouseFrameGeometry.dimensionLine(GreenhousePhysicalConfig(10.0, 8.0, 3.0, 1.0))
        )
    }

    @Test
    fun dimensionsTwelveByEightByFourMapCorrectly() {
        val corners = ArGreenhouseFrameGeometry.localCorners(12f, 8f, 4f)
        assertEquals(12f, corners.maxOf { it.x }, 1e-5f)
        assertEquals(8f, corners.maxOf { it.z }, 1e-5f)
        assertTrue(corners.any { abs(it.y - 4f) < 1e-4f })
        assertEquals(
            "12.0m × 8.0m × 4.0m",
            ArGreenhouseFrameGeometry.dimensionLine(GreenhousePhysicalConfig(12.0, 8.0, 4.0, 1.0))
        )
    }

    @Test
    fun dimensionsTwentyByTenByFourMapCorrectly() {
        val physical = GreenhousePhysicalConfig(20.0, 10.0, 4.0, 0.5)
        val corners = ArGreenhouseFrameGeometry.localCorners(20f, 10f, 4f)
        assertEquals(20f, corners.maxOf { it.x }, 1e-5f)
        assertEquals(10f, corners.maxOf { it.z }, 1e-5f)
        assertEquals("20.0m × 10.0m × 4.0m", ArGreenhouseFrameGeometry.dimensionLine(physical))
        assertEquals("0.5m", ArGreenhouseFrameGeometry.cellSizeLine(physical))
    }

    @Test
    fun worldCornersMapOriginAndAxesViaWorldMapper() {
        val pose = ArWorldMapper.alignedPose(1f, 0.2f, -2f, forwardX = 1f, forwardZ = 0f)
        val physical = GreenhousePhysicalConfig(10.0, 8.0, 3.0, 1.0)
        val world = ArGreenhouseFrameGeometry.worldCorners(pose, physical)!!
        assertTrue(
            world.any { abs(it.x - 1f) < 1e-4f && abs(it.z + 2f) < 1e-4f && abs(it.y - 0.2f) < 1e-4f }
        )
        // local (10,0,0) → +X length
        val farX = ArWorldMapper.localToWorld(pose, 10f, 0f, 0f)!!
        assertEquals(11f, farX.x, 1e-4f)
        assertEquals(-2f, farX.z, 1e-4f)
        // local (0,0,8) → +Z width
        val farZ = ArWorldMapper.localToWorld(pose, 0f, 0f, 8f)!!
        assertEquals(1f, farZ.x, 1e-4f)
        assertEquals(6f, farZ.z, 1e-4f)
        // local (0,3,0) → +Y
        val top = ArWorldMapper.localToWorld(pose, 0f, 3f, 0f)!!
        assertEquals(3.2f, top.y, 1e-4f)
    }

    @Test
    fun localPlusXFollowsSelectedLengthDirection() {
        val pose = ArWorldMapper.alignedPose(0f, 0f, 0f, forwardX = 0f, forwardZ = 1f)
        val far = ArWorldMapper.localToWorld(pose, 5f, 0f, 0f)!!
        assertEquals(0f, far.x, 1e-4f)
        assertEquals(5f, far.z, 1e-4f)
    }

    @Test
    fun localPlusZFollowsWidthPerpendicular() {
        val pose = ArWorldMapper.alignedPose(0f, 0f, 0f, forwardX = 0f, forwardZ = 1f)
        // forward=(0,1), right=(-1,0) → local +Z goes to -X
        val far = ArWorldMapper.localToWorld(pose, 0f, 0f, 4f)!!
        assertEquals(-4f, far.x, 1e-4f)
        assertEquals(0f, far.z, 1e-4f)
    }

    @Test
    fun frameHiddenUntilAligned() {
        assertFalse(ArGreenhouseFrameGeometry.shouldShowFrame(ArOriginPlacementPhase.SCANNING))
        assertFalse(ArGreenhouseFrameGeometry.shouldShowFrame(ArOriginPlacementPhase.ORIGIN_PLACED))
        assertFalse(ArGreenhouseFrameGeometry.shouldShowFrame(ArOriginPlacementPhase.SETTING_DIRECTION))
        assertTrue(ArGreenhouseFrameGeometry.shouldShowFrame(ArOriginPlacementPhase.ALIGNED))
        val pose = ArOriginPlacementController.onOriginPlaced(
            ArOriginPlacementController.onHorizontalPlaneDetected(ArGreenhousePose()),
            0f, 0f, 0f
        )
        assertNull(
            ArGreenhouseFrameGeometry.worldCorners(
                pose,
                GreenhousePhysicalConfig(10.0, 8.0, 3.0, 1.0)
            )
        )
    }

    @Test
    fun resetAlignmentHidesFrameVisibility() {
        var state = ArOriginPlacementController.onHorizontalPlaneDetected(ArGreenhousePose())
        state = ArOriginPlacementController.onOriginPlaced(state, 0f, 0f, 0f)
        state = ArOriginPlacementController.beginSetDirection(state)
        state = ArOriginPlacementController.onDirectionPoint(state, 2f, 0f, 0f).first
        assertTrue(ArGreenhouseFrameGeometry.shouldShowFrame(state.phase))
        val reset = ArOriginPlacementController.resetAlignment(state)
        assertFalse(ArGreenhouseFrameGeometry.shouldShowFrame(reset.phase))
    }

    @Test
    fun snapshotUnchangedByFrameGeometry() {
        val vm = SensorPlacementViewModel()
        assertTrue(vm.createOrUpdateGreenhouse(GreenhousePhysicalConfig(10.0, 8.0, 3.0, 1.0)))
        vm.addSensor(1.0, 1.0, type = SensorType.TEMPERATURE)
        val before = ArVisualizationMapper.from(vm.state.value)
        val pose = ArWorldMapper.alignedPose(0f, 0f, 0f, 1f, 0f)
        assertNotNull(ArGreenhouseFrameGeometry.worldEdges(pose, before.physical))
        val after = ArVisualizationMapper.from(vm.state.value)
        assertEquals(before.physical, after.physical)
        assertEquals(before.sensors.size, after.sensors.size)
        assertEquals(before.coverageCells.size, after.coverageCells.size)
    }

    @Test
    fun guideEdgesRemainLightweightOptionalWireframe() {
        val withRoof = ArGreenhouseFrameGeometry.guideEdges(10f, 8f, 3f)
        assertEquals(16, withRoof.size)
        val floorOnly = ArGreenhouseFrameGeometry.guideEdges(10f, 8f, 3f, includeRoofGuides = false)
        assertEquals(12, floorOnly.size)
        assertTrue(ArGreenhouseFrameGeometry.localEdges(10f, 8f, 3f).size >= 12)
        val frameNodes = java.io.File(
            "src/main/java/com/greenhands/app/sensor/ar/ArGreenhouseFrameNodes.kt"
        ).readText()
        assertTrue(frameNodes.contains("structurePanels"))
        assertTrue(frameNodes.contains("structureFrameEdges"))
        assertFalse(frameNodes.contains("PlaneNode"))
        assertTrue(frameNodes.contains("CubeNode"))
    }

    @Test
    fun frameGeometryHasNoCoverageOrOptimizerImports() {
        val edges = ArGreenhouseFrameGeometry.localEdges(10f, 8f, 3f)
        assertTrue(edges.size >= 12)
        assertEquals(9, ArGreenhouseFrameGeometry.localCorners(10f, 8f, 3f).size)
    }

    @Test
    fun rendererSourcesAvoidCoverageAndOptimizer() {
        val roots = listOf(
            "src/main/java/com/greenhands/app/sensor/ar/ArGreenhouseFrameGeometry.kt",
            "src/main/java/com/greenhands/app/sensor/ar/ArGreenhouseFrameNodes.kt",
            "src/main/java/com/greenhands/app/sensor/ui/RealGreenhouseArScreen.kt"
        )
        roots.forEach { relative ->
            val text = java.io.File(relative).takeIf { it.exists() }?.readText()
                ?: java.io.File("../$relative").takeIf { it.exists() }?.readText()
                ?: error("Missing $relative")
            assertFalse(text.contains("CoverageCalculator"))
            assertFalse(text.contains("SensorPlacementOptimizer"))
        }
    }
}
