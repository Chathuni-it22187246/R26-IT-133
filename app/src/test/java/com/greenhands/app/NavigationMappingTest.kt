package com.greenhands.app

import com.greenhands.app.ui.navigation.Routes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationMappingTest {

    @Test
    fun globalBottomNavigationIsDashboardSettingsAccount() {
        assertEquals(
            listOf(Routes.DASHBOARD, Routes.SETTINGS, Routes.ACCOUNT),
            Routes.globalBottomNavRoutes
        )
        assertFalse(Routes.globalBottomNavRoutes.contains(Routes.CROPS))
        assertFalse(Routes.globalBottomNavRoutes.contains(Routes.SIMULATION))
        assertFalse(Routes.globalBottomNavRoutes.contains(Routes.SOURCES))
    }

    @Test
    fun heatBottomNavigationContainsHeatDestinations() {
        assertEquals(
            listOf(Routes.DASHBOARD, Routes.CROPS, Routes.SIMULATION, Routes.SOURCES, Routes.ACCOUNT),
            Routes.heatBottomNavRoutes
        )
    }

    @Test
    fun heatDestinationsAreAbsentBeforeHeatIsSelected() {
        assertFalse(Routes.shouldShowHeatNavigation(Routes.DASHBOARD, heatWorkspaceActive = false))
        assertFalse(Routes.shouldShowHeatNavigation(Routes.SETTINGS, heatWorkspaceActive = false))
        assertFalse(Routes.shouldShowHeatNavigation(Routes.ACCOUNT, heatWorkspaceActive = false))
        assertFalse(Routes.isHeatContentRoute(Routes.DASHBOARD))
        assertFalse(Routes.isHeatContentRoute(Routes.SETTINGS))
        assertFalse(Routes.isHeatContentRoute(Routes.ACCOUNT))
    }

    @Test
    fun heatContentActivatesHeatNavigationAndDashboardExitsIt() {
        assertTrue(Routes.shouldShowHeatNavigation(Routes.CROPS, heatWorkspaceActive = false))
        assertTrue(Routes.shouldShowHeatNavigation(Routes.HEAT_SELECT_CROP, false))
        assertTrue(Routes.shouldShowHeatNavigation(Routes.SIMULATION, false))
        assertTrue(Routes.shouldShowHeatNavigation(Routes.SOURCES, false))
        assertTrue(Routes.shouldShowHeatNavigation(Routes.ACCOUNT, heatWorkspaceActive = true))
        assertFalse(Routes.shouldShowHeatNavigation(Routes.DASHBOARD, heatWorkspaceActive = true))
        assertTrue(Routes.shouldShowHeatNavigation(Routes.SETTINGS, heatWorkspaceActive = true))
    }

    @Test
    fun unauthenticatedRoutesHaveNoBottomNavigationMappingRequirement() {
        Routes.unauthenticated.forEach { route ->
            assertFalse(Routes.shouldShowHeatNavigation(route, true))
        }
    }

    @Test
    fun topLevelMappingSelectsCropsForHeatWorkflow() {
        assertEquals(Routes.CROPS, Routes.topLevelFor(Routes.HEAT_CLIMATE, heatNavigation = true))
        assertEquals(Routes.SETTINGS, Routes.topLevelFor(Routes.SETTINGS, heatNavigation = false))
        assertEquals(Routes.ACCOUNT, Routes.topLevelFor(Routes.SETTINGS, heatNavigation = true))
        assertEquals(Routes.ACCOUNT, Routes.topLevelFor(Routes.ABOUT, heatNavigation = false))
    }

    @Test
    fun greenhousePreviewRoutesStayOnSimulationTopLevel() {
        assertTrue(Routes.isHeatContentRoute(Routes.GREENHOUSE_PREVIEW_SETUP))
        assertTrue(Routes.isHeatContentRoute(Routes.GREENHOUSE_PREVIEW))
        assertTrue(Routes.isHeatContentRoute(Routes.HEAT_ACTIVE_CONFIG))
        assertTrue(Routes.isHeatContentRoute(Routes.HEAT_RECENT_CONFIG))
        assertEquals(Routes.CROPS, Routes.topLevelFor(Routes.HEAT_ACTIVE_CONFIG, heatNavigation = true))
        assertEquals(Routes.CROPS, Routes.topLevelFor(Routes.HEAT_RECENT_CONFIG, heatNavigation = true))
        assertEquals(Routes.SIMULATION, Routes.topLevelFor(Routes.GREENHOUSE_PREVIEW_SETUP, heatNavigation = true))
        assertEquals(Routes.SIMULATION, Routes.topLevelFor(Routes.GREENHOUSE_PREVIEW, heatNavigation = true))
        assertEquals(Routes.SIMULATION, Routes.topLevelFor(Routes.GREENHOUSE_AR, heatNavigation = true))
        assertTrue(Routes.isHeatContentRoute(Routes.GREENHOUSE_AR))
        assertTrue(Routes.shouldShowHeatNavigation(Routes.GREENHOUSE_PREVIEW_SETUP, false))
        assertTrue(Routes.immersivePreviewRoutes.contains(Routes.GREENHOUSE_PREVIEW))
        assertTrue(Routes.immersivePreviewRoutes.contains(Routes.GREENHOUSE_AR))
        assertFalse(Routes.immersivePreviewRoutes.contains(Routes.GREENHOUSE_PREVIEW_SETUP))
        assertEquals(
            "greenhouse_ar/DEMO/10.0/5.0/3.0/3",
            Routes.greenhouseArRoute("DEMO", 10.0, 5.0, 3.0, 3)
        )
    }

    @Test
    fun harvestRoutesDoNotActivateHeatNavigation() {
        assertFalse(Routes.isHeatContentRoute(Routes.HARVESTING))
        assertFalse(Routes.isHeatContentRoute(Routes.HARVEST_FRUIT_SCAN))
        assertFalse(Routes.isHeatContentRoute(Routes.HARVEST_AR))
        assertTrue(Routes.isHarvestRoute(Routes.HARVESTING))
        assertTrue(Routes.isHarvestRoute(Routes.HARVEST_FRUIT_SCAN))
        assertTrue(Routes.harvestCameraRoutes.contains(Routes.HARVEST_AR))
        assertEquals(Routes.DASHBOARD, Routes.topLevelFor(Routes.HARVESTING, heatNavigation = false))
        assertEquals(Routes.DASHBOARD, Routes.topLevelFor(Routes.HARVEST_HISTORY, heatNavigation = false))
        assertFalse(Routes.shouldShowHeatNavigation(Routes.HARVESTING, heatWorkspaceActive = false))
        assertEquals("harvesting/result/scan-1", Routes.harvestResult("scan-1"))
        assertEquals("harvesting/plant_health/leaf-1", Routes.harvestPlantHealth("leaf-1"))
        assertEquals("harvesting/record/rec-1", Routes.harvestRecord("rec-1"))
    }

    @Test
    fun sensorRoutesDoNotActivateHeatNavigation() {
        assertTrue(Routes.isSensorContentRoute(Routes.SENSOR_SETUP))
        assertTrue(Routes.isSensorContentRoute(Routes.SENSOR_SCAN))
        assertTrue(Routes.isSensorContentRoute(Routes.SENSOR_PLACE))
        assertTrue(Routes.isSensorContentRoute(Routes.SENSOR_COVERAGE))
        assertTrue(Routes.isSensorContentRoute(Routes.SENSOR_OPTIMIZE))
        assertTrue(Routes.isSensorContentRoute(Routes.SENSOR_VIRTUAL_PREVIEW))
        assertTrue(Routes.isSensorContentRoute(Routes.SENSOR_REAL_AR))
        assertFalse(Routes.isSensorContentRoute(Routes.SENSOR_PLACEMENT))
        assertFalse(Routes.isHeatContentRoute(Routes.SENSOR_SETUP))
        assertFalse(Routes.isHeatContentRoute(Routes.SENSOR_REAL_AR))
        assertEquals(Routes.DASHBOARD, Routes.topLevelFor(Routes.SENSOR_SETUP))
        assertEquals(Routes.DASHBOARD, Routes.topLevelFor(Routes.SENSOR_REAL_AR))
        assertFalse(Routes.shouldShowHeatNavigation(Routes.SENSOR_SETUP, heatWorkspaceActive = true))
        assertTrue(Routes.sensorImmersiveRoutes.contains(Routes.SENSOR_REAL_AR))
        assertTrue(Routes.sensorImmersiveRoutes.contains(Routes.SENSOR_VIRTUAL_PREVIEW))
    }

    @Test
    fun decisionSupportOpensHeaterFansFilter() {
        assertEquals(Routes.DECISION_MAKING, Routes.DECISION_SUPPORT)
        assertEquals("decision_making/heater_fans", Routes.decisionSupportDestination())
        assertEquals(
            Routes.decisionMaking(com.greenhands.app.decision.DecisionSection.HEATER_FANS),
            Routes.decisionSupportDestination()
        )
        assertFalse(Routes.isHeatContentRoute(Routes.decisionSupportDestination()))
        assertEquals(Routes.SIMULATION, Routes.topLevelFor(Routes.GREENHOUSE_AR, heatNavigation = true))
    }
}
