package com.greenhands.app

import com.greenhands.app.ui.navigation.Routes
import com.greenhands.app.ui.screens.DashboardModule
import com.greenhands.app.ui.screens.navigationRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensorPlacementNavigationTest {

    @Test
    fun sensorDashboardCardOpensGreenhouseSetupNotComingSoon() {
        val module = DashboardModule(
            id = Routes.SENSOR_PLACEMENT,
            title = "Sensor Placement",
            subtitle = "Find optimal sensor positions",
            isSensor = true
        )
        assertEquals(Routes.SENSOR_SETUP, module.navigationRoute())
        assertFalse(module.navigationRoute().startsWith("coming_soon"))
    }

    @Test
    fun setupScanPlaceCoverageAreProductionRoutes() {
        assertEquals("sensor_setup", Routes.SENSOR_SETUP)
        assertEquals("sensor_scan", Routes.SENSOR_SCAN)
        assertEquals("sensor_place", Routes.SENSOR_PLACE)
        assertEquals("sensor_coverage", Routes.SENSOR_COVERAGE)
        assertEquals("sensor_optimize", Routes.SENSOR_OPTIMIZE)
        assertTrue(Routes.SENSOR_SETUP in Routes.sensorContentRoutes)
        assertTrue(Routes.SENSOR_SCAN in Routes.sensorContentRoutes)
        assertTrue(Routes.SENSOR_PLACE in Routes.sensorContentRoutes)
        assertTrue(Routes.SENSOR_COVERAGE in Routes.sensorContentRoutes)
        assertTrue(Routes.SENSOR_OPTIMIZE in Routes.sensorContentRoutes)
        assertFalse(Routes.SENSOR_PLACEMENT in Routes.sensorContentRoutes)
    }

    @Test
    fun optimizeRouteFollowsCoverageInWorkflow() {
        assertTrue(Routes.isSensorContentRoute(Routes.SENSOR_OPTIMIZE))
        assertEquals(Routes.DASHBOARD, Routes.topLevelFor(Routes.SENSOR_OPTIMIZE))
        assertTrue(
            Routes.shouldPreserveSensorWorkflowForTopLevel(
                Routes.ACCOUNT,
                listOf(Routes.DASHBOARD, Routes.SENSOR_OPTIMIZE)
            )
        )
    }

    @Test
    fun coverageRouteFollowsPlaceSensorsInWorkflow() {
        assertTrue(Routes.isSensorContentRoute(Routes.SENSOR_COVERAGE))
        assertEquals(Routes.DASHBOARD, Routes.topLevelFor(Routes.SENSOR_COVERAGE))
        assertTrue(Routes.isSensorContentRoute(Routes.SENSOR_SETUP))
        assertEquals(Routes.DASHBOARD, Routes.topLevelFor(Routes.SENSOR_SETUP))
    }

    @Test
    fun accountAndSettingsFromSensorScreensPreserveBackStack() {
        val placeStack = listOf(
            Routes.DASHBOARD,
            Routes.SENSOR_SETUP,
            Routes.SENSOR_SCAN,
            Routes.SENSOR_PLACE
        )
        val coverageStack = placeStack + Routes.SENSOR_COVERAGE
        assertTrue(
            Routes.shouldPreserveSensorWorkflowForTopLevel(Routes.ACCOUNT, placeStack)
        )
        assertTrue(
            Routes.shouldPreserveSensorWorkflowForTopLevel(Routes.SETTINGS, placeStack)
        )
        assertTrue(
            Routes.shouldPreserveSensorWorkflowForTopLevel(Routes.ACCOUNT, coverageStack)
        )
        assertTrue(
            Routes.shouldPreserveSensorWorkflowForTopLevel(Routes.SETTINGS, coverageStack)
        )
        assertFalse(
            Routes.shouldPreserveSensorWorkflowForTopLevel(Routes.DASHBOARD, placeStack)
        )
        assertFalse(
            Routes.shouldPreserveSensorWorkflowForTopLevel(
                Routes.ACCOUNT,
                listOf(Routes.DASHBOARD)
            )
        )
        // Place → Account → Settings still keeps sensor routes underneath.
        assertTrue(
            Routes.shouldPreserveSensorWorkflowForTopLevel(
                Routes.SETTINGS,
                placeStack + Routes.ACCOUNT
            )
        )
    }

    @Test
    fun settingsToolbarBackAppearsWhenOpenedFromSensorOrAccount() {
        assertTrue(Routes.settingsShowsToolbarBack(Routes.ACCOUNT))
        assertTrue(Routes.settingsShowsToolbarBack(Routes.SENSOR_PLACE))
        assertTrue(Routes.settingsShowsToolbarBack(Routes.SENSOR_COVERAGE))
        assertTrue(Routes.settingsShowsToolbarBack(Routes.SENSOR_SETUP))
        assertTrue(Routes.settingsShowsToolbarBack(Routes.SENSOR_SCAN))
        assertFalse(Routes.settingsShowsToolbarBack(Routes.DASHBOARD))
        assertFalse(Routes.settingsShowsToolbarBack(null))
    }

    @Test
    fun heatAndComingSoonDashboardCardsKeepExistingDestinations() {
        val heat = DashboardModule(
            id = Routes.HEAT_DISTRIBUTION,
            title = "Heat Distribution",
            subtitle = "Visualize greenhouse heat patterns",
            isHeat = true
        )
        val harvest = DashboardModule(
            id = Routes.HARVESTING,
            title = "Harvesting",
            subtitle = "Assess crop health"
        )
        val decision = DashboardModule(
            id = Routes.DECISION_MAKING,
            title = "Decision Making",
            subtitle = "Receive recommended decisions"
        )
        assertEquals(Routes.CROPS, heat.navigationRoute())
        assertEquals(Routes.comingSoon(Routes.HARVESTING), harvest.navigationRoute())
        assertEquals(Routes.comingSoon(Routes.DECISION_MAKING), decision.navigationRoute())
    }
}
