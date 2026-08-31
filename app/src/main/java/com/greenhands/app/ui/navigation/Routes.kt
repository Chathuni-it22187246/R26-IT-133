package com.greenhands.app.ui.navigation

object Routes {
    const val SPLASH = "splash"
    const val WELCOME = "welcome"
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val FORGOT_PASSWORD = "forgot_password"
    const val DASHBOARD = "dashboard"
    const val CROPS = "crops"
    const val SIMULATION = "simulation"
    const val SOURCES = "sources"
    const val ACCOUNT = "account"
    const val COMING_SOON = "coming_soon/{componentId}"
    const val HEAT_DISTRIBUTION = "heat_distribution"
    const val HEAT_SELECT_CROP = "heat_select_crop"
    const val HEAT_SELECT_STAGE = "heat_select_stage"
    const val HEAT_CLIMATE = "heat_climate"
    const val HEAT_CIRCULATION = "heat_circulation"
    const val HEAT_EXHAUST = "heat_exhaust"
    const val HEAT_FOGGER = "heat_fogger"
    const val HEAT_SUMMARY = "heat_summary"
    const val HEAT_SIMULATION_NEXT = "heat_simulation_next"
    const val CROP_CONFIGURATION_NEXT = "crop_configuration_next"
    const val PROFILE = "profile"
    const val EDIT_PROFILE = "edit_profile"
    const val SETTINGS = "settings"
    const val HELP = "help"
    const val ABOUT = "about"
    const val PRIVACY = "privacy"
    const val LOGOUT_CONFIRM = "logout_confirm"
    const val NOTIFICATIONS = "notifications"

    val unauthenticated = setOf(SPLASH, WELCOME, LOGIN, REGISTER, FORGOT_PASSWORD)

    val cropsGraph = setOf(
        CROPS,
        HEAT_DISTRIBUTION,
        HEAT_SELECT_CROP,
        HEAT_SELECT_STAGE,
        HEAT_CLIMATE,
        HEAT_CIRCULATION,
        HEAT_EXHAUST,
        HEAT_FOGGER,
        HEAT_SUMMARY
    )

    val accountGraph = setOf(
        ACCOUNT,
        PROFILE,
        EDIT_PROFILE,
        SETTINGS,
        HELP,
        ABOUT,
        PRIVACY,
        LOGOUT_CONFIRM
    )

    val heatContentRoutes = cropsGraph + setOf(SIMULATION, SOURCES, HEAT_SIMULATION_NEXT)

    val globalBottomNavRoutes = listOf(DASHBOARD, SETTINGS, ACCOUNT)

    val heatBottomNavRoutes = listOf(DASHBOARD, CROPS, SIMULATION, SOURCES, ACCOUNT)

    fun isHeatContentRoute(route: String?): Boolean =
        route != null && route in heatContentRoutes

    fun isSensorContentRoute(route: String?): Boolean =
        route != null && route in sensorContentRoutes

    /**
     * Account/Settings opened from Component 01 must push onto the back stack
     * (not popUpTo start) so Back restores the exact sensor screen.
     */
    fun shouldPreserveSensorWorkflowForTopLevel(
        toRoute: String,
        backStackRoutes: List<String?>
    ): Boolean {
        if (toRoute != ACCOUNT && toRoute != SETTINGS) return false
        return backStackRoutes.any { isSensorContentRoute(it) }
    }

    /**
     * How bottom-nav should move between authenticated top-level destinations.
     *
     * Navigating *to* [DASHBOARD] with popUpTo(DASHBOARD)+restoreState re-applies the
     * just-saved Account/Settings child and appears stuck on Account — use pop instead.
     */
    enum class TopLevelNavAction {
        /** Push Account/Settings above an active Component 01 stack. */
        PRESERVE_SENSOR_PUSH,
        /** Clear everything above Dashboard (no restoreState). */
        POP_TO_DASHBOARD,
        /** Switch Settings/Account/Heat tabs with save/restore under Dashboard. */
        SWITCH_TAB
    }

    fun topLevelNavAction(toRoute: String, backStackRoutes: List<String?>): TopLevelNavAction = when {
        shouldPreserveSensorWorkflowForTopLevel(toRoute, backStackRoutes) ->
            TopLevelNavAction.PRESERVE_SENSOR_PUSH
        toRoute == DASHBOARD -> TopLevelNavAction.POP_TO_DASHBOARD
        else -> TopLevelNavAction.SWITCH_TAB
    }

    fun settingsShowsToolbarBack(previousRoute: String?): Boolean =
        previousRoute == ACCOUNT || isSensorContentRoute(previousRoute)

    fun shouldShowHeatNavigation(route: String?, heatWorkspaceActive: Boolean): Boolean {
        if (route == null || route in unauthenticated) return false
        if (route == DASHBOARD) return false
        if (isSensorContentRoute(route)) return false
        if (isHeatContentRoute(route)) return true
        return heatWorkspaceActive
    }

    fun topLevelFor(route: String?, heatNavigation: Boolean = false): String = when {
        route == null -> DASHBOARD
        route == SETTINGS -> if (heatNavigation) ACCOUNT else SETTINGS
        route == SIMULATION || route == HEAT_SIMULATION_NEXT -> SIMULATION
        route == SOURCES -> SOURCES
        route in cropsGraph -> CROPS
        route in accountGraph || route == NOTIFICATIONS -> ACCOUNT
        else -> DASHBOARD
    }

    const val ARG_COMPONENT_ID = "componentId"

    const val SENSOR_PLACEMENT = "sensor_placement"
    const val SENSOR_SETUP = "sensor_setup"
    const val SENSOR_SCAN = "sensor_scan"
    const val SENSOR_PLACE = "sensor_place"
    const val SENSOR_COVERAGE = "sensor_coverage"
    const val SENSOR_OPTIMIZE = "sensor_optimize"
    const val SENSOR_VIRTUAL_PREVIEW = "sensor_virtual_preview"
    /** Side route: Real AR entry (Phase 10E-B). Does not replace virtual preview. */
    const val SENSOR_REAL_AR = "sensor_real_ar"
    const val HARVESTING = "harvesting"
    const val DECISION_MAKING = "decision_making"

    val sensorContentRoutes = setOf(
        SENSOR_SETUP,
        SENSOR_SCAN,
        SENSOR_PLACE,
        SENSOR_COVERAGE,
        SENSOR_OPTIMIZE,
        SENSOR_VIRTUAL_PREVIEW,
        SENSOR_REAL_AR
    )

    fun comingSoon(componentId: String): String = "coming_soon/$componentId"
}

object NavActions {
    fun loginToDashboard(navController: androidx.navigation.NavHostController) {
        navController.navigate(Routes.DASHBOARD) {
            popUpTo(navController.graph.id) {
                inclusive = true
            }
            launchSingleTop = true
        }
    }

    fun logoutToLogin(navController: androidx.navigation.NavHostController) {
        navController.navigate(Routes.LOGIN) {
            popUpTo(navController.graph.id) {
                inclusive = true
            }
            launchSingleTop = true
        }
    }
}
