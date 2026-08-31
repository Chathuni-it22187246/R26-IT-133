package com.greenhands.app

import com.greenhands.app.sensor.ar.ArDirectionTapResult
import com.greenhands.app.sensor.ar.ArGreenhousePose
import com.greenhands.app.sensor.ar.ArOriginPlacementController
import com.greenhands.app.sensor.ar.ArOriginPlacementPhase
import com.greenhands.app.sensor.ar.ArVisualizationMapper
import com.greenhands.app.sensor.ar.RealArNavigation
import com.greenhands.app.sensor.model.GreenhousePhysicalConfig
import com.greenhands.app.sensor.model.SensorType
import com.greenhands.app.sensor.ui.SensorPlacementViewModel
import com.greenhands.app.ui.navigation.Routes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ArOriginPlacementTest {

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
    fun initialStateIsScanning() {
        val pose = ArGreenhousePose()
        assertEquals(ArOriginPlacementPhase.SCANNING, pose.phase)
        assertFalse(pose.isOriginPlaced)
        assertNull(pose.worldTranslationX)
        assertEquals(0f, ArGreenhousePose.LOCAL_ORIGIN_X)
    }

    @Test
    fun planeDetectedTransitionsToPlaneFound() {
        val next = ArOriginPlacementController.onHorizontalPlaneDetected(ArGreenhousePose())
        assertEquals(ArOriginPlacementPhase.PLANE_FOUND, next.phase)
        assertTrue(ArOriginPlacementController.canAcceptOriginTap(next))
    }

    @Test
    fun originPlacementStateRecordsWorldTranslation() {
        val plane = ArOriginPlacementController.onHorizontalPlaneDetected(ArGreenhousePose())
        val placed = ArOriginPlacementController.onOriginPlaced(plane, 1.5f, 0.02f, -0.8f)
        assertEquals(ArOriginPlacementPhase.ORIGIN_PLACED, placed.phase)
        assertTrue(placed.isOriginPlaced)
        assertEquals(1.5f, placed.worldTranslationX!!, 1e-5f)
        assertFalse(ArOriginPlacementController.canAcceptOriginTap(placed))
    }

    @Test
    fun resetOriginReturnsToScanning() {
        val placed = ArOriginPlacementController.onOriginPlaced(
            ArOriginPlacementController.onHorizontalPlaneDetected(ArGreenhousePose()),
            2f,
            0f,
            3f
        )
        val reset = ArOriginPlacementController.resetOrigin(placed)
        assertEquals(ArOriginPlacementPhase.SCANNING, reset.phase)
        assertFalse(reset.isOriginPlaced)
        assertNull(reset.worldTranslationX)
    }

    @Test
    fun stateTransitionsIgnoreInvalidTaps() {
        val scanning = ArGreenhousePose()
        assertFalse(ArOriginPlacementController.canAcceptOriginTap(scanning))
        val ignored = ArOriginPlacementController.onOriginPlaced(scanning, 1f, 2f, 3f)
        assertEquals(ArOriginPlacementPhase.SCANNING, ignored.phase)
    }

    @Test
    fun planeDetectedDoesNotOverrideLaterPhases() {
        val placed = ArOriginPlacementController.onOriginPlaced(
            ArOriginPlacementController.onHorizontalPlaneDetected(ArGreenhousePose()),
            4f,
            0f,
            5f
        )
        assertEquals(
            ArOriginPlacementPhase.ORIGIN_PLACED,
            ArOriginPlacementController.onHorizontalPlaneDetected(placed).phase
        )
    }

    @Test
    fun originPlacementDoesNotModifySimulationSnapshot() {
        val vm = SensorPlacementViewModel()
        assertTrue(vm.createOrUpdateGreenhouse(GreenhousePhysicalConfig(10.0, 8.0, 3.0, 1.0)))
        vm.addSensor(2.0, 3.0, type = SensorType.TEMPERATURE)
        val before = ArVisualizationMapper.from(vm.state.value)

        var arPose = ArOriginPlacementController.onHorizontalPlaneDetected(ArGreenhousePose())
        arPose = ArOriginPlacementController.onOriginPlaced(arPose, 1f, 0f, 1f)
        arPose = ArOriginPlacementController.beginSetDirection(arPose)
        val (aligned, result) = ArOriginPlacementController.onDirectionPoint(arPose, 3f, 0f, 1f)
        assertEquals(ArDirectionTapResult.OK, result)
        assertTrue(aligned.isAligned)

        val after = ArVisualizationMapper.from(vm.state.value)
        assertEquals(before.sensors.single().xMeters, after.sensors.single().xMeters, 0.0)
        assertEquals(before.physical, after.physical)
    }

    @Test
    fun virtualFallbackRemainsAvailable() {
        assertEquals(Routes.SENSOR_VIRTUAL_PREVIEW, RealArNavigation.virtualFallbackRoute())
        assertTrue(Routes.isSensorContentRoute(Routes.SENSOR_REAL_AR))
    }

    @Test
    fun instructionPhaseTracksState() {
        var state = ArGreenhousePose()
        assertEquals(ArOriginPlacementPhase.SCANNING, ArOriginPlacementController.instructionPhase(state))
        state = ArOriginPlacementController.onHorizontalPlaneDetected(state)
        assertEquals(ArOriginPlacementPhase.PLANE_FOUND, ArOriginPlacementController.instructionPhase(state))
        state = ArOriginPlacementController.onOriginPlaced(state, 0f, 0f, 0f)
        assertEquals(ArOriginPlacementPhase.ORIGIN_PLACED, ArOriginPlacementController.instructionPhase(state))
    }
}
