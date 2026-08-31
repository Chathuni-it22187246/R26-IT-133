package com.greenhands.app.ui.navigation

import androidx.navigation.NavHostController
import com.greenhands.app.sensor.ui.SensorPlacementUiState
import com.greenhands.app.sensor.ui.SensorWorkflowStep

/**
 * Component 01 entry/resume helpers. State lives in the Activity-scoped
 * [com.greenhands.app.sensor.ui.SensorPlacementViewModel]; these only restore routes.
 */
object SensorNavigation {

    /** Authenticated bottom-nav root — never Splash — so tab switches cannot restore auth. */
    const val TOP_LEVEL_POP_UP_TO = Routes.DASHBOARD

    fun resumeRoute(ui: SensorPlacementUiState): String =
        resumeRoute(ui.greenhouseConfigured, ui.step)

    fun resumeRoute(greenhouseConfigured: Boolean, step: SensorWorkflowStep): String = when {
        !greenhouseConfigured -> Routes.SENSOR_SETUP
        step == SensorWorkflowStep.OPTIMIZE -> Routes.SENSOR_OPTIMIZE
        step == SensorWorkflowStep.COVERAGE -> Routes.SENSOR_COVERAGE
        step == SensorWorkflowStep.PLACE -> Routes.SENSOR_PLACE
        step == SensorWorkflowStep.SCAN -> Routes.SENSOR_SCAN
        else -> Routes.SENSOR_SETUP
    }

    /**
     * Ordered destinations to push so Back keeps Setup→Scan→Place→Coverage→Optimize.
     */
    fun workflowStackTo(resumeRoute: String): List<String> = when (resumeRoute) {
        Routes.SENSOR_SCAN -> listOf(Routes.SENSOR_SETUP, Routes.SENSOR_SCAN)
        Routes.SENSOR_PLACE -> listOf(
            Routes.SENSOR_SETUP,
            Routes.SENSOR_SCAN,
            Routes.SENSOR_PLACE
        )
        Routes.SENSOR_COVERAGE -> listOf(
            Routes.SENSOR_SETUP,
            Routes.SENSOR_SCAN,
            Routes.SENSOR_PLACE,
            Routes.SENSOR_COVERAGE
        )
        Routes.SENSOR_OPTIMIZE -> listOf(
            Routes.SENSOR_SETUP,
            Routes.SENSOR_SCAN,
            Routes.SENSOR_PLACE,
            Routes.SENSOR_COVERAGE,
            Routes.SENSOR_OPTIMIZE
        )
        else -> listOf(Routes.SENSOR_SETUP)
    }
}

fun NavHostController.navigateToSensorWorkflow(resumeRoute: String) {
    SensorNavigation.workflowStackTo(resumeRoute).forEach { route ->
        navigate(route) { launchSingleTop = true }
    }
}
