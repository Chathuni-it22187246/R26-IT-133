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
    const val HEAT_ACTIVE_CONFIG = "heat_active_config"
    const val HEAT_RECENT_CONFIG = "heat_recent_config"
    const val HEAT_CLIMATE = "heat_climate"
    const val HEAT_CIRCULATION = "heat_circulation"
    const val HEAT_EXHAUST = "heat_exhaust"
    const val HEAT_FOGGER = "heat_fogger"
    const val HEAT_SUMMARY = "heat_summary"
    const val HEAT_SIMULATION_NEXT = "heat_simulation_next"
    const val GREENHOUSE_PREVIEW_SETUP = "greenhouse_preview_setup"
    const val ARG_PREVIEW_MODE = "mode"
    const val ARG_PREVIEW_LENGTH = "length"
    const val ARG_PREVIEW_WIDTH = "width"
    const val ARG_PREVIEW_HEIGHT = "height"
    const val ARG_PREVIEW_ROWS = "rows"
    const val GREENHOUSE_PREVIEW =
        "greenhouse_preview/{$ARG_PREVIEW_MODE}/{$ARG_PREVIEW_LENGTH}/{$ARG_PREVIEW_WIDTH}/{$ARG_PREVIEW_HEIGHT}/{$ARG_PREVIEW_ROWS}"
    const val GREENHOUSE_AR =
        "greenhouse_ar/{$ARG_PREVIEW_MODE}/{$ARG_PREVIEW_LENGTH}/{$ARG_PREVIEW_WIDTH}/{$ARG_PREVIEW_HEIGHT}/{$ARG_PREVIEW_ROWS}"
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
        HEAT_ACTIVE_CONFIG,
        HEAT_RECENT_CONFIG,
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

    val heatContentRoutes = cropsGraph + setOf(
        SIMULATION,
        SOURCES,
        HEAT_SIMULATION_NEXT,
        GREENHOUSE_PREVIEW_SETUP,
        GREENHOUSE_PREVIEW,
        GREENHOUSE_AR
    )

    val immersivePreviewRoutes = setOf(GREENHOUSE_PREVIEW, GREENHOUSE_AR)

    fun greenhousePreviewRoute(
        mode: String,
        lengthM: Double,
        widthM: Double,
        heightM: Double,
        cropRowCount: Int
    ): String = "greenhouse_preview/$mode/$lengthM/$widthM/$heightM/$cropRowCount"

    fun greenhouseArRoute(
        mode: String,
        lengthM: Double,
        widthM: Double,
        heightM: Double,
        cropRowCount: Int
    ): String = "greenhouse_ar/$mode/$lengthM/$widthM/$heightM/$cropRowCount"

    val globalBottomNavRoutes = listOf(DASHBOARD, SETTINGS, ACCOUNT)

    val heatBottomNavRoutes = listOf(DASHBOARD, CROPS, SIMULATION, SOURCES, ACCOUNT)

    fun isHeatContentRoute(route: String?): Boolean =
        route != null && route in heatContentRoutes

    fun isSensorContentRoute(route: String?): Boolean =
        route != null && route in sensorContentRoutes

    /**
     * Account/Settings opened from Sensor Placement must push onto the back stack
     * so Back restores the exact sensor screen.
     */
    fun shouldPreserveSensorWorkflowForTopLevel(
        toRoute: String,
        backStackRoutes: List<String?>
    ): Boolean {
        if (toRoute != ACCOUNT && toRoute != SETTINGS) return false
        return backStackRoutes.any { isSensorContentRoute(it) }
    }

    enum class TopLevelNavAction {
        PRESERVE_SENSOR_PUSH,
        POP_TO_DASHBOARD,
        SWITCH_TAB
    }

    fun topLevelNavAction(toRoute: String, backStackRoutes: List<String?>): TopLevelNavAction = when {
        shouldPreserveSensorWorkflowForTopLevel(toRoute, backStackRoutes) ->
            TopLevelNavAction.PRESERVE_SENSOR_PUSH
        toRoute == DASHBOARD && backStackRoutes.any { isSensorContentRoute(it) } ->
            TopLevelNavAction.POP_TO_DASHBOARD
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
        route == SIMULATION || route == HEAT_SIMULATION_NEXT ||
            route == GREENHOUSE_PREVIEW_SETUP || route == GREENHOUSE_PREVIEW ||
            route == GREENHOUSE_AR -> SIMULATION
        route == SOURCES -> SOURCES
        route in cropsGraph -> CROPS
        route in harvestGraph -> DASHBOARD
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
    const val SENSOR_REAL_AR = "sensor_real_ar"
    const val HARVESTING = "harvesting"
    const val HARVEST_FRUIT_SCAN = "harvesting/fruit_scan"
    const val HARVEST_LEAF_SCAN = "harvesting/leaf_scan"
    const val HARVEST_HISTORY = "harvesting/history"
    const val HARVEST_RECORD = "harvesting/record/{recordId}"
    const val HARVEST_RESULT = "harvesting/result/{mockId}"
    const val HARVEST_PLANT_HEALTH = "harvesting/plant_health/{mockId}"
    const val HARVEST_AR = "harvesting/ar"
    const val DECISION_MAKING = "decision_making"
    const val DECISION_SUPPORT = DECISION_MAKING
    const val ARG_DECISION_SECTION = "section"
    const val DECISION_MAKING_ROUTE = "decision_making/{$ARG_DECISION_SECTION}"
    const val ARG_CROP = "crop"
    const val ARG_WIDTH = "width"
    const val ARG_HEIGHT = "height"
    const val DECISION_AR = "ar_screen/{width}/{height}"
    const val INFECTION_SCAN = "infection_scan/{crop}/{recordId}"
    const val INFECTION_RECORD = "infection_record/{recordId}"

    const val ARG_MOCK_ID = "mockId"
    const val ARG_RECORD_ID = "recordId"

    val sensorContentRoutes = setOf(
        SENSOR_SETUP,
        SENSOR_SCAN,
        SENSOR_PLACE,
        SENSOR_COVERAGE,
        SENSOR_OPTIMIZE,
        SENSOR_VIRTUAL_PREVIEW,
        SENSOR_REAL_AR
    )

    val sensorImmersiveRoutes = setOf(SENSOR_VIRTUAL_PREVIEW, SENSOR_REAL_AR)

    val harvestGraph = setOf(
        HARVESTING,
        HARVEST_FRUIT_SCAN,
        HARVEST_LEAF_SCAN,
        HARVEST_HISTORY,
        HARVEST_RECORD,
        HARVEST_RESULT,
        HARVEST_PLANT_HEALTH,
        HARVEST_AR
    )

    val harvestCameraRoutes = setOf(HARVEST_FRUIT_SCAN, HARVEST_LEAF_SCAN, HARVEST_AR)

    fun harvestResult(mockId: String): String = "harvesting/result/$mockId"

    fun harvestPlantHealth(mockId: String): String = "harvesting/plant_health/$mockId"

    fun harvestRecord(recordId: String): String = "harvesting/record/$recordId"

    fun isHarvestRoute(route: String?): Boolean {
        if (route == null) return false
        return route == HARVESTING || route.startsWith("$HARVESTING/")
    }

    fun comingSoon(componentId: String): String = "coming_soon/$componentId"

    fun decisionMaking(
        section: com.greenhands.app.decision.DecisionSection =
            com.greenhands.app.decision.DecisionSection.ALL
    ): String = "decision_making/${section.routeArg}"

    fun infectionScan(crop: String, recordId: String? = null): String {
        val safe = crop.ifBlank { "Plant" }.replace("/", "-")
        val rec = recordId?.ifBlank { null } ?: "_"
        return "infection_scan/$safe/$rec"
    }

    fun infectionRecord(recordId: String): String = "infection_record/$recordId"

    fun decisionArScreen(width: Float, height: Float): String = "ar_screen/$width/$height"

    fun isDecisionMakingRoute(route: String?): Boolean {
        if (route == null) return false
        return route == DECISION_MAKING_ROUTE ||
            route == DECISION_MAKING ||
            route.startsWith("$DECISION_MAKING/")
    }

    fun isDecisionImmersiveRoute(route: String?): Boolean {
        if (route == null) return false
        return route.startsWith("infection_scan") ||
            route.startsWith("infection_record") ||
            route.startsWith("ar_screen")
    }

    /** Heat "Too cold" View suggestions → existing Heater/Fans chip. */
    fun decisionSupportDestination(): String =
        decisionMaking(com.greenhands.app.decision.DecisionSection.HEATER_FANS)
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

    /**
     * Opens Dasuntha’s Decision Making screen (Heater/Fans) without popping Heat.
     * System Back returns to the previous Heat route.
     */
    fun openDecisionSupport(navController: androidx.navigation.NavHostController) {
        navController.navigate(Routes.decisionSupportDestination()) {
            launchSingleTop = true
        }
    }
}
