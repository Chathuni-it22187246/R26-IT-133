package com.greenhands.app

import com.greenhands.app.ui.navigation.Routes
import com.greenhands.app.ui.navigation.SensorNavigation
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
    fun sensorRoutesStayOnGlobalNavigationAndAreNotHeatContent() {
        assertTrue(Routes.isSensorContentRoute(Routes.SENSOR_SETUP))
        assertTrue(Routes.isSensorContentRoute(Routes.SENSOR_SCAN))
        assertTrue(Routes.isSensorContentRoute(Routes.SENSOR_PLACE))
        assertTrue(Routes.isSensorContentRoute(Routes.SENSOR_COVERAGE))
        assertTrue(Routes.isSensorContentRoute(Routes.SENSOR_OPTIMIZE))
        assertFalse(Routes.isSensorContentRoute(Routes.SENSOR_PLACEMENT))
        assertFalse(Routes.isHeatContentRoute(Routes.SENSOR_SETUP))
        assertFalse(Routes.isHeatContentRoute(Routes.SENSOR_SCAN))
        assertFalse(Routes.isHeatContentRoute(Routes.SENSOR_PLACE))
        assertFalse(Routes.isHeatContentRoute(Routes.SENSOR_COVERAGE))
        assertFalse(Routes.isHeatContentRoute(Routes.SENSOR_OPTIMIZE))
        assertFalse(Routes.shouldShowHeatNavigation(Routes.SENSOR_SETUP, heatWorkspaceActive = false))
        assertFalse(Routes.shouldShowHeatNavigation(Routes.SENSOR_SCAN, heatWorkspaceActive = false))
        assertFalse(Routes.shouldShowHeatNavigation(Routes.SENSOR_PLACE, heatWorkspaceActive = false))
        assertFalse(Routes.shouldShowHeatNavigation(Routes.SENSOR_COVERAGE, heatWorkspaceActive = false))
        assertFalse(Routes.shouldShowHeatNavigation(Routes.SENSOR_OPTIMIZE, heatWorkspaceActive = false))
        assertFalse(Routes.shouldShowHeatNavigation(Routes.SENSOR_SETUP, heatWorkspaceActive = true))
        assertFalse(Routes.shouldShowHeatNavigation(Routes.SENSOR_SCAN, heatWorkspaceActive = true))
        assertFalse(Routes.shouldShowHeatNavigation(Routes.SENSOR_PLACE, heatWorkspaceActive = true))
        assertFalse(Routes.shouldShowHeatNavigation(Routes.SENSOR_COVERAGE, heatWorkspaceActive = true))
        assertFalse(Routes.shouldShowHeatNavigation(Routes.SENSOR_OPTIMIZE, heatWorkspaceActive = true))
        assertEquals(Routes.DASHBOARD, Routes.topLevelFor(Routes.SENSOR_SETUP))
        assertEquals(Routes.DASHBOARD, Routes.topLevelFor(Routes.SENSOR_SCAN))
        assertEquals(Routes.DASHBOARD, Routes.topLevelFor(Routes.SENSOR_PLACE))
        assertEquals(Routes.DASHBOARD, Routes.topLevelFor(Routes.SENSOR_COVERAGE))
        assertEquals(Routes.DASHBOARD, Routes.topLevelFor(Routes.SENSOR_OPTIMIZE))
    }

    @Test
    fun topLevelAccountSettingsDoNotClearSensorBackStack() {
        val sensorStack = listOf(
            Routes.DASHBOARD,
            Routes.SENSOR_SETUP,
            Routes.SENSOR_SCAN,
            Routes.SENSOR_PLACE
        )
        assertTrue(Routes.shouldPreserveSensorWorkflowForTopLevel(Routes.ACCOUNT, sensorStack))
        assertTrue(Routes.shouldPreserveSensorWorkflowForTopLevel(Routes.SETTINGS, sensorStack))
        assertFalse(Routes.shouldPreserveSensorWorkflowForTopLevel(Routes.DASHBOARD, sensorStack))
        assertFalse(
            Routes.shouldPreserveSensorWorkflowForTopLevel(
                Routes.ACCOUNT,
                listOf(Routes.DASHBOARD, Routes.CROPS)
            )
        )
    }

    @Test
    fun authenticatedTopLevelPopUpToIsDashboard() {
        assertEquals(Routes.DASHBOARD, SensorNavigation.TOP_LEVEL_POP_UP_TO)
    }

    @Test
    fun accountToDashboardUsesPopNotRestoreCycle() {
        val accountStack = listOf(Routes.DASHBOARD, Routes.ACCOUNT)
        assertEquals(
            Routes.TopLevelNavAction.POP_TO_DASHBOARD,
            Routes.topLevelNavAction(Routes.DASHBOARD, accountStack)
        )
        assertFalse(Routes.topLevelNavAction(Routes.DASHBOARD, accountStack) == Routes.TopLevelNavAction.SWITCH_TAB)
    }

    @Test
    fun dashboardToAccountUsesTabSwitch() {
        assertEquals(
            Routes.TopLevelNavAction.SWITCH_TAB,
            Routes.topLevelNavAction(Routes.ACCOUNT, listOf(Routes.DASHBOARD))
        )
    }

    @Test
    fun accountToSettingsUsesTabSwitch() {
        assertEquals(
            Routes.TopLevelNavAction.SWITCH_TAB,
            Routes.topLevelNavAction(
                Routes.SETTINGS,
                listOf(Routes.DASHBOARD, Routes.ACCOUNT)
            )
        )
    }

    @Test
    fun settingsToDashboardUsesPopNotRestoreCycle() {
        assertEquals(
            Routes.TopLevelNavAction.POP_TO_DASHBOARD,
            Routes.topLevelNavAction(
                Routes.DASHBOARD,
                listOf(Routes.DASHBOARD, Routes.SETTINGS)
            )
        )
    }

    @Test
    fun settingsToAccountUsesTabSwitch() {
        assertEquals(
            Routes.TopLevelNavAction.SWITCH_TAB,
            Routes.topLevelNavAction(
                Routes.ACCOUNT,
                listOf(Routes.DASHBOARD, Routes.SETTINGS)
            )
        )
    }

    @Test
    fun topLevelTabActionsNeverTargetSplashOrLogin() {
        val stacks = listOf(
            listOf(Routes.DASHBOARD),
            listOf(Routes.DASHBOARD, Routes.ACCOUNT),
            listOf(Routes.DASHBOARD, Routes.SETTINGS),
            listOf(Routes.DASHBOARD, Routes.SENSOR_PLACE, Routes.ACCOUNT)
        )
        val targets = listOf(Routes.DASHBOARD, Routes.SETTINGS, Routes.ACCOUNT)
        for (stack in stacks) {
            for (target in targets) {
                val action = Routes.topLevelNavAction(target, stack)
                assertTrue(
                    action == Routes.TopLevelNavAction.PRESERVE_SENSOR_PUSH ||
                        action == Routes.TopLevelNavAction.POP_TO_DASHBOARD ||
                        action == Routes.TopLevelNavAction.SWITCH_TAB
                )
                assertEquals(Routes.DASHBOARD, SensorNavigation.TOP_LEVEL_POP_UP_TO)
                assertFalse(SensorNavigation.TOP_LEVEL_POP_UP_TO == Routes.SPLASH)
                assertFalse(SensorNavigation.TOP_LEVEL_POP_UP_TO == Routes.LOGIN)
            }
        }
    }
}
