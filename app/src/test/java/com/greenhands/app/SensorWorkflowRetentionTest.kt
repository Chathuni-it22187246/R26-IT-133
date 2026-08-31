package com.greenhands.app

import com.greenhands.app.sensor.model.GreenhousePhysicalConfig
import com.greenhands.app.sensor.model.SensorType
import com.greenhands.app.sensor.ui.SensorPlacementViewModel
import com.greenhands.app.sensor.ui.SensorWorkflowStep
import com.greenhands.app.ui.navigation.Routes
import com.greenhands.app.ui.navigation.SensorNavigation
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
class SensorWorkflowRetentionTest {

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
    fun topLevelPopUpToIsDashboardNotSplash() {
        assertEquals(Routes.DASHBOARD, SensorNavigation.TOP_LEVEL_POP_UP_TO)
        assertFalse(SensorNavigation.TOP_LEVEL_POP_UP_TO == Routes.SPLASH)
        assertFalse(SensorNavigation.TOP_LEVEL_POP_UP_TO == Routes.LOGIN)
    }

    @Test
    fun resumeRouteUsesLastWorkflowStepWhenGreenhouseConfigured() {
        assertEquals(
            Routes.SENSOR_SETUP,
            SensorNavigation.resumeRoute(greenhouseConfigured = false, step = SensorWorkflowStep.PLACE)
        )
        assertEquals(
            Routes.SENSOR_PLACE,
            SensorNavigation.resumeRoute(greenhouseConfigured = true, step = SensorWorkflowStep.PLACE)
        )
        assertEquals(
            Routes.SENSOR_COVERAGE,
            SensorNavigation.resumeRoute(greenhouseConfigured = true, step = SensorWorkflowStep.COVERAGE)
        )
        assertEquals(
            Routes.SENSOR_SCAN,
            SensorNavigation.resumeRoute(greenhouseConfigured = true, step = SensorWorkflowStep.SCAN)
        )
        assertEquals(
            Routes.SENSOR_SETUP,
            SensorNavigation.resumeRoute(greenhouseConfigured = true, step = SensorWorkflowStep.SETUP)
        )
    }

    @Test
    fun workflowStackPreservesComponent01BackChain() {
        assertEquals(listOf(Routes.SENSOR_SETUP), SensorNavigation.workflowStackTo(Routes.SENSOR_SETUP))
        assertEquals(
            listOf(Routes.SENSOR_SETUP, Routes.SENSOR_SCAN),
            SensorNavigation.workflowStackTo(Routes.SENSOR_SCAN)
        )
        assertEquals(
            listOf(Routes.SENSOR_SETUP, Routes.SENSOR_SCAN, Routes.SENSOR_PLACE),
            SensorNavigation.workflowStackTo(Routes.SENSOR_PLACE)
        )
        assertEquals(
            listOf(
                Routes.SENSOR_SETUP,
                Routes.SENSOR_SCAN,
                Routes.SENSOR_PLACE,
                Routes.SENSOR_COVERAGE
            ),
            SensorNavigation.workflowStackTo(Routes.SENSOR_COVERAGE)
        )
        assertEquals(
            listOf(
                Routes.SENSOR_SETUP,
                Routes.SENSOR_SCAN,
                Routes.SENSOR_PLACE,
                Routes.SENSOR_COVERAGE,
                Routes.SENSOR_OPTIMIZE
            ),
            SensorNavigation.workflowStackTo(Routes.SENSOR_OPTIMIZE)
        )
    }

    @Test
    fun placeThenLeaveTabsKeepsGreenhouseAndSensorsInSameViewModel() {
        val vm = SensorPlacementViewModel()
        assertTrue(
            vm.createOrUpdateGreenhouse(
                GreenhousePhysicalConfig(
                    lengthMeters = 10.0,
                    widthMeters = 8.0,
                    heightMeters = 4.0,
                    cellSizeMeters = 1.0
                )
            )
        )
        vm.goToStep(SensorWorkflowStep.PLACE)
        vm.addSensor(2.0, 2.0, type = SensorType.TEMPERATURE)
        vm.addSensor(5.0, 4.0, type = SensorType.TEMPERATURE)
        vm.selectSensor("S1")

        // Simulate Account/Dashboard tab switch: same Activity-scoped VM instance, no reset.
        val afterTabs = vm.state.value
        assertTrue(afterTabs.greenhouseConfigured)
        assertEquals(10, afterTabs.greenhouse.widthCells)
        assertEquals(8, afterTabs.greenhouse.heightCells)
        assertEquals(2, afterTabs.sensors.size)
        assertEquals("S1", afterTabs.sensors[0].id)
        assertEquals("S2", afterTabs.sensors[1].id)
        assertEquals(SensorType.TEMPERATURE, afterTabs.sensors[0].type)
        assertEquals(SensorType.TEMPERATURE, afterTabs.sensors[1].type)
        assertEquals(2.0, afterTabs.sensors[0].x, 0.0)
        assertEquals(2.0, afterTabs.sensors[0].y, 0.0)
        assertEquals(5.0, afterTabs.sensors[1].x, 0.0)
        assertEquals(4.0, afterTabs.sensors[1].y, 0.0)
        assertEquals("S1", afterTabs.selectedSensorId)
        assertEquals(Routes.SENSOR_PLACE, SensorNavigation.resumeRoute(afterTabs))
        assertEquals(
            listOf(Routes.SENSOR_SETUP, Routes.SENSOR_SCAN, Routes.SENSOR_PLACE),
            SensorNavigation.workflowStackTo(SensorNavigation.resumeRoute(afterTabs))
        )
    }

    @Test
    fun coverageStepResumesCoverageStackAfterDashboard() {
        val vm = SensorPlacementViewModel()
        vm.createOrUpdateGreenhouse(
            GreenhousePhysicalConfig(10.0, 8.0, 4.0, 1.0)
        )
        vm.goToStep(SensorWorkflowStep.PLACE)
        vm.addSensor(1.0, 1.0, type = SensorType.TEMPERATURE)
        vm.goToStep(SensorWorkflowStep.COVERAGE)

        val resume = SensorNavigation.resumeRoute(vm.state.value)
        assertEquals(Routes.SENSOR_COVERAGE, resume)
        assertEquals(
            listOf(
                Routes.SENSOR_SETUP,
                Routes.SENSOR_SCAN,
                Routes.SENSOR_PLACE,
                Routes.SENSOR_COVERAGE
            ),
            SensorNavigation.workflowStackTo(resume)
        )
        assertEquals(1, vm.state.value.sensors.size)
        assertTrue(vm.state.value.greenhouseConfigured)
    }

    @Test
    fun accountSettingsPreservePolicyStillHoldsForPlaceAndCoverageStacks() {
        val placeStack = listOf(
            Routes.DASHBOARD,
            Routes.SENSOR_SETUP,
            Routes.SENSOR_SCAN,
            Routes.SENSOR_PLACE
        )
        val coverageStack = placeStack + Routes.SENSOR_COVERAGE
        assertTrue(Routes.shouldPreserveSensorWorkflowForTopLevel(Routes.ACCOUNT, placeStack))
        assertTrue(Routes.shouldPreserveSensorWorkflowForTopLevel(Routes.SETTINGS, coverageStack))
        assertFalse(Routes.shouldPreserveSensorWorkflowForTopLevel(Routes.DASHBOARD, placeStack))
    }

    @Test
    fun placeThenAccountThenDashboardPopsToDashboardAndResumesPlace() {
        val placeThenAccount = listOf(
            Routes.DASHBOARD,
            Routes.SENSOR_SETUP,
            Routes.SENSOR_SCAN,
            Routes.SENSOR_PLACE,
            Routes.ACCOUNT
        )
        assertEquals(
            Routes.TopLevelNavAction.POP_TO_DASHBOARD,
            Routes.topLevelNavAction(Routes.DASHBOARD, placeThenAccount)
        )

        val vm = SensorPlacementViewModel()
        vm.createOrUpdateGreenhouse(GreenhousePhysicalConfig(10.0, 8.0, 4.0, 1.0))
        vm.goToStep(SensorWorkflowStep.PLACE)
        vm.addSensor(2.0, 2.0, type = SensorType.TEMPERATURE)
        // Tab leave clears routes via POP_TO_DASHBOARD; Activity-scoped VM keeps state.
        val resume = SensorNavigation.resumeRoute(vm.state.value)
        assertEquals(Routes.SENSOR_PLACE, resume)
        assertEquals(
            listOf(Routes.SENSOR_SETUP, Routes.SENSOR_SCAN, Routes.SENSOR_PLACE),
            SensorNavigation.workflowStackTo(resume)
        )
        assertEquals(1, vm.state.value.sensors.size)
        assertTrue(vm.state.value.greenhouseConfigured)
    }

    @Test
    fun optimizeStepResumesOptimizeStack() {
        assertEquals(
            Routes.SENSOR_OPTIMIZE,
            SensorNavigation.resumeRoute(greenhouseConfigured = true, step = SensorWorkflowStep.OPTIMIZE)
        )
        assertEquals(
            listOf(
                Routes.SENSOR_SETUP,
                Routes.SENSOR_SCAN,
                Routes.SENSOR_PLACE,
                Routes.SENSOR_COVERAGE,
                Routes.SENSOR_OPTIMIZE
            ),
            SensorNavigation.workflowStackTo(Routes.SENSOR_OPTIMIZE)
        )
    }

    @Test
    fun coverageThenAccountThenDashboardPopsToDashboardAndResumesCoverage() {
        val coverageThenAccount = listOf(
            Routes.DASHBOARD,
            Routes.SENSOR_SETUP,
            Routes.SENSOR_SCAN,
            Routes.SENSOR_PLACE,
            Routes.SENSOR_COVERAGE,
            Routes.ACCOUNT
        )
        assertEquals(
            Routes.TopLevelNavAction.POP_TO_DASHBOARD,
            Routes.topLevelNavAction(Routes.DASHBOARD, coverageThenAccount)
        )

        val vm = SensorPlacementViewModel()
        vm.createOrUpdateGreenhouse(GreenhousePhysicalConfig(10.0, 8.0, 4.0, 1.0))
        vm.goToStep(SensorWorkflowStep.PLACE)
        vm.addSensor(1.0, 1.0, type = SensorType.HUMIDITY)
        vm.goToStep(SensorWorkflowStep.COVERAGE)
        assertEquals(Routes.SENSOR_COVERAGE, SensorNavigation.resumeRoute(vm.state.value))
        assertEquals(SensorType.HUMIDITY, vm.state.value.sensors.single().type)
    }
}
