package com.greenhands.app

import com.greenhands.app.sensor.ar.RealArLayerVisibility
import com.greenhands.app.sensor.ar.RealArNavigation
import com.greenhands.app.sensor.ar.RealArUxHelpers
import com.greenhands.app.sensor.ar.ArGreenhousePose
import com.greenhands.app.sensor.ar.ArOriginPlacementController
import com.greenhands.app.sensor.ar.ArOriginPlacementPhase
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RealArUxParityTest {

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
    fun defaultLayersPreferVirtualParityGreenhouse() {
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
        assertTrue(layers.shouldAttachRecommendations(aligned = true))
        assertFalse(layers.shouldAttachSensors(aligned = false))
    }

    @Test
    fun toggleGreenhouseSensorsCoverageRecommendations() {
        var layers = RealArLayerVisibility()
        assertTrue(layers.guide)
        layers = layers.toggleGuide()
        assertFalse(layers.guide)
        assertFalse(layers.shouldAttachGuide(true))
        layers = layers.toggleSensors()
        assertFalse(layers.sensors)
        layers = layers.toggleCovered()
        assertFalse(layers.covered)
        layers = layers.toggleBlindSpots()
        assertFalse(layers.blindSpots)
        layers = layers.toggleOverlap()
        assertFalse(layers.overlap)
        layers = layers.toggleRecommendations()
        assertFalse(layers.recommendations)
        layers = layers.toggleGuide()
            .toggleSensors()
            .toggleCovered()
            .toggleBlindSpots()
            .toggleOverlap()
            .toggleRecommendations()
        assertEquals(RealArUxHelpers.defaultLayers(), layers)
    }

    @Test
    fun resetAlignmentKeepsOriginClearsYaw() {
        var state = ArOriginPlacementController.onHorizontalPlaneDetected(ArGreenhousePose())
        state = ArOriginPlacementController.onOriginPlaced(state, 1f, 0.1f, -2f)
        state = ArOriginPlacementController.beginSetDirection(state)
        state = ArOriginPlacementController.onDirectionPoint(state, 3f, 0.1f, -2f).first
        assertEquals(ArOriginPlacementPhase.ALIGNED, state.phase)
        val reset = ArOriginPlacementController.resetAlignment(state)
        assertEquals(ArOriginPlacementPhase.SETTING_DIRECTION, reset.phase)
        assertEquals(1f, reset.worldTranslationX!!, 1e-4f)
        assertEquals(null, reset.yawRadians)
    }

    @Test
    fun virtualFallbackAndRealArRoutes() {
        assertEquals(Routes.SENSOR_VIRTUAL_PREVIEW, RealArUxHelpers.virtualFallbackRoute())
        assertEquals(Routes.SENSOR_REAL_AR, RealArUxHelpers.realArRoute())
        assertTrue(RealArUxHelpers.isSensorContentRoute(Routes.SENSOR_REAL_AR))
    }

    @Test
    fun layerTogglesDoNotChangeViewModelSensors() {
        val vm = SensorPlacementViewModel()
        vm.createOrUpdateGreenhouse(GreenhousePhysicalConfig(12.0, 8.0, 4.0, 1.0))
        vm.addSensor(1.0, 1.0, type = SensorType.TEMPERATURE)
        val before = vm.state.value.sensors
        var layers = RealArUxHelpers.defaultLayers()
        layers = layers.toggleCovered().toggleSensors().toggleGuide().toggleRecommendations()
        assertEquals(before, vm.state.value.sensors)
        assertFalse(layers.guide)
        assertFalse(layers.sensors)
    }

    @Test
    fun realArNavigationHelpers() {
        assertEquals(Routes.SENSOR_REAL_AR, RealArNavigation.ROUTE)
        assertEquals(Routes.SENSOR_VIRTUAL_PREVIEW, RealArNavigation.virtualFallbackRoute())
    }
}
