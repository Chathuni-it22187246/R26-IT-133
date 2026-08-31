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

    fun shouldShowHeatNavigation(route: String?, heatWorkspaceActive: Boolean): Boolean {
        if (route == null || route in unauthenticated) return false
        if (route == DASHBOARD) return false
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
    const val HARVESTING = "harvesting"
    const val HARVEST_FRUIT_SCAN = "harvesting/fruit_scan"
    const val HARVEST_LEAF_SCAN = "harvesting/leaf_scan"
    const val HARVEST_HISTORY = "harvesting/history"
    const val HARVEST_RECORD = "harvesting/record/{recordId}"
    const val HARVEST_RESULT = "harvesting/result/{mockId}"
    const val HARVEST_PLANT_HEALTH = "harvesting/plant_health/{mockId}"
    const val HARVEST_AR = "harvesting/ar"
    const val DECISION_MAKING = "decision_making"

    const val ARG_MOCK_ID = "mockId"
    const val ARG_RECORD_ID = "recordId"

    val harvestCameraRoutes = setOf(HARVEST_FRUIT_SCAN, HARVEST_LEAF_SCAN, HARVEST_AR)

    fun harvestResult(mockId: String): String = "harvesting/result/$mockId"

    fun harvestPlantHealth(mockId: String): String = "harvesting/plant_health/$mockId"

    fun harvestRecord(recordId: String): String = "harvesting/record/$recordId"

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
