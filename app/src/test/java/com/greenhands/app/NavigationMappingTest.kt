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
}
