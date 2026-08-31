package com.greenhands.app.ui.navigation

import android.app.Application
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.greenhands.app.environment.UnconnectedGreenhouseEnvironmentRepository
import com.greenhands.app.heat.data.DataStoreHeatConfigRepository
import com.greenhands.app.heat.ui.CirculationFanScreen
import com.greenhands.app.heat.ui.ClimateTargetsScreen
import com.greenhands.app.heat.ui.ConfigurationSummaryScreen
import com.greenhands.app.heat.ui.DemoSimulationNextScreen
import com.greenhands.app.heat.ui.ExhaustFanScreen
import com.greenhands.app.heat.ui.FoggerSettingsScreen
import com.greenhands.app.heat.ui.HeatConfigViewModel
import com.greenhands.app.heat.ui.HeatConfigViewModelFactory
import com.greenhands.app.heat.ui.SelectCropScreen
import com.greenhands.app.heat.ui.SelectStageScreen
import com.greenhands.app.heat.ui.SourcesScreen
import com.greenhands.app.harvest.ar.ArResultMapper
import com.greenhands.app.harvest.ar.ArResultStore
import com.greenhands.app.harvest.ar.ResultArScreen
import com.greenhands.app.harvest.data.AssetHarvestReferenceRepository
import com.greenhands.app.harvest.data.RoomScanHistoryRepository
import com.greenhands.app.harvest.ui.FruitScanScreen
import com.greenhands.app.harvest.ui.HarvestHistoryDetailScreen
import com.greenhands.app.harvest.ui.HarvestHubScreen
import com.greenhands.app.harvest.ui.HarvestResultScreen
import com.greenhands.app.harvest.ui.HarvestViewModel
import com.greenhands.app.harvest.ui.HarvestViewModelFactory
import com.greenhands.app.harvest.ui.LeafScanScreen
import com.greenhands.app.harvest.ui.PlantHealthResultScreen
import com.greenhands.app.harvest.integration.HarvestDecisionMakingBridge
import com.greenhands.app.harvest.ui.ScanHistoryScreen
import com.greenhands.app.session.AppSessionViewModel
import com.greenhands.app.ui.screens.AboutScreen
import com.greenhands.app.ui.screens.AccountHomeScreen
import com.greenhands.app.ui.screens.ComingSoonScreen
import com.greenhands.app.ui.screens.DashboardScreen
import com.greenhands.app.ui.screens.EditProfileScreen
import com.greenhands.app.ui.screens.ForgotPasswordScreen
import com.greenhands.app.ui.screens.HelpScreen
import com.greenhands.app.ui.screens.LoginScreen
import com.greenhands.app.ui.screens.LogoutConfirmScreen
import com.greenhands.app.ui.screens.NotificationsScreen
import com.greenhands.app.ui.screens.PrivacyScreen
import com.greenhands.app.ui.screens.ProfileScreen
import com.greenhands.app.ui.screens.RegistrationScreen
import com.greenhands.app.ui.screens.SettingsScreen
import com.greenhands.app.ui.screens.SplashScreen
import com.greenhands.app.ui.screens.WelcomeScreen

@Composable
fun GreenHandsNavGraph(
    navController: NavHostController,
    sessionViewModel: AppSessionViewModel,
    modifier: Modifier = Modifier,
    startDestination: String = Routes.SPLASH,
    heatViewModel: HeatConfigViewModel? = null
) {
    val session by sessionViewModel.state.collectAsState()
    val app = LocalContext.current.applicationContext as Application
    val defaultHeatVm: HeatConfigViewModel = viewModel(
        factory = HeatConfigViewModelFactory(DataStoreHeatConfigRepository(app))
    )
    val heatVm = heatViewModel ?: defaultHeatVm
    val harvestVm: HarvestViewModel = viewModel(
        factory = HarvestViewModelFactory(
            scanHistoryRepository = RoomScanHistoryRepository(app),
            referenceRepository = AssetHarvestReferenceRepository(app),
            environmentRepository = UnconnectedGreenhouseEnvironmentRepository()
        )
    )
    val harvestState by harvestVm.state.collectAsState()
    val heatState by heatVm.state.collectAsState()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = currentRoute != null &&
        currentRoute !in Routes.unauthenticated &&
        currentRoute !in Routes.harvestCameraRoutes
    val heatNavigation = Routes.shouldShowHeatNavigation(currentRoute, session.heatWorkspaceActive)

    LaunchedEffect(currentRoute) {
        when {
            currentRoute == Routes.DASHBOARD -> sessionViewModel.exitHeatWorkspace()
            Routes.isHeatContentRoute(currentRoute) -> sessionViewModel.enterHeatWorkspace()
        }
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                GreenHandsBottomBar(
                    currentRoute = currentRoute,
                    heatNavigation = heatNavigation
                ) { dest ->
                    navController.navigateTopLevel(dest)
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(padding)
        ) {
            composable(Routes.SPLASH) {
                SplashScreen(
                    onFinished = {
                        navController.navigate(Routes.WELCOME) {
                            popUpTo(Routes.SPLASH) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(Routes.WELCOME) {
                WelcomeScreen(
                    onGetStarted = {
                        navController.navigate(Routes.REGISTER) { launchSingleTop = true }
                    },
                    onHaveAccount = {
                        navController.navigate(Routes.LOGIN) { launchSingleTop = true }
                    }
                )
            }
            composable(Routes.LOGIN) {
                LoginScreen(
                    initialEmail = if (session.rememberMe) session.rememberedEmail else "",
                    banner = session.loginBanner,
                    onBannerShown = { sessionViewModel.consumeLoginBanner() },
                    onLogin = { email, rememberMe ->
                        sessionViewModel.login(email, rememberMe)
                        NavActions.loginToDashboard(navController)
                    },
                    onForgotPassword = { navController.navigate(Routes.FORGOT_PASSWORD) { launchSingleTop = true } },
                    onRegister = { navController.navigate(Routes.REGISTER) { launchSingleTop = true } },
                    onBack = {
                        if (navController.previousBackStackEntry != null) {
                            navController.popBackStack()
                        } else {
                            navController.navigate(Routes.WELCOME) {
                                popUpTo(navController.graph.id) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    }
                )
            }
            composable(Routes.REGISTER) {
                RegistrationScreen(
                    onRegistered = { name, email, photoPath ->
                        sessionViewModel.registerDemoAccount(name, email, photoPath)
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(Routes.REGISTER) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.FORGOT_PASSWORD) {
                ForgotPasswordScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.DASHBOARD) {
                DashboardScreen(
                    session = session,
                    environment = harvestState.environment,
                    onOpenModule = { module ->
                        when {
                            module.isHeat -> {
                                sessionViewModel.enterHeatWorkspace()
                                navController.navigate(Routes.CROPS) { launchSingleTop = true }
                            }
                            module.id == Routes.HARVESTING -> {
                                navController.navigate(Routes.HARVESTING) { launchSingleTop = true }
                            }
                            else -> {
                                navController.navigate(Routes.comingSoon(module.id)) { launchSingleTop = true }
                            }
                        }
                    },
                    onOpenProfile = { navController.navigate(Routes.PROFILE) { launchSingleTop = true } },
                    onOpenNotifications = { navController.navigate(Routes.NOTIFICATIONS) { launchSingleTop = true } }
                )
            }
            composable(
                route = Routes.COMING_SOON,
                arguments = listOf(navArgument(Routes.ARG_COMPONENT_ID) { type = NavType.StringType })
            ) { entry ->
                val id = entry.arguments?.getString(Routes.ARG_COMPONENT_ID).orEmpty()
                ComingSoonScreen(componentId = id, onBack = { navController.popBackStack() })
            }
            composable(Routes.HARVESTING) {
                HarvestHubScreen(
                    onScanFruit = {
                        navController.navigate(Routes.HARVEST_FRUIT_SCAN) { launchSingleTop = true }
                    },
                    onScanLeaf = {
                        navController.navigate(Routes.HARVEST_LEAF_SCAN) { launchSingleTop = true }
                    },
                    onOpenHistory = {
                        navController.navigate(Routes.HARVEST_HISTORY) { launchSingleTop = true }
                    },
                    onOpenRecord = { recordId ->
                        navController.navigate(Routes.harvestRecord(recordId)) { launchSingleTop = true }
                    },
                    onBack = { navController.popBackStack() },
                    viewModel = harvestVm
                )
            }
            composable(Routes.HARVEST_FRUIT_SCAN) {
                FruitScanScreen(
                    onBack = { navController.popBackStack() },
                    onViewDetails = { mockId ->
                        navController.navigate(Routes.harvestResult(mockId)) { launchSingleTop = true }
                    },
                    harvestViewModel = harvestVm
                )
            }
            composable(Routes.HARVEST_LEAF_SCAN) {
                LeafScanScreen(
                    onBack = { navController.popBackStack() },
                    onViewDetails = { mockId ->
                        navController.navigate(Routes.harvestPlantHealth(mockId)) { launchSingleTop = true }
                    },
                    harvestViewModel = harvestVm,
                    onOpenDecisionMaking = {
                        HarvestDecisionMakingBridge.navigateSafely { route ->
                            navController.navigate(route) { launchSingleTop = true }
                        }
                    }
                )
            }
            composable(
                route = Routes.HARVEST_RESULT,
                arguments = listOf(navArgument(Routes.ARG_MOCK_ID) { type = NavType.StringType })
            ) { entry ->
                val mockId = entry.arguments?.getString(Routes.ARG_MOCK_ID).orEmpty()
                HarvestResultScreen(
                    mockId = mockId,
                    onBack = { navController.popBackStack() },
                    onViewInAr = {
                        ArResultStore.current = ArResultMapper.fromHarvest(it)
                        navController.navigate(Routes.HARVEST_AR) { launchSingleTop = true }
                    },
                    harvestViewModel = harvestVm
                )
            }
            composable(
                route = Routes.HARVEST_PLANT_HEALTH,
                arguments = listOf(navArgument(Routes.ARG_MOCK_ID) { type = NavType.StringType })
            ) { entry ->
                val mockId = entry.arguments?.getString(Routes.ARG_MOCK_ID).orEmpty()
                PlantHealthResultScreen(
                    mockId = mockId,
                    onBack = { navController.popBackStack() },
                    onViewInAr = {
                        ArResultStore.current = ArResultMapper.fromPlantHealth(it)
                        navController.navigate(Routes.HARVEST_AR) { launchSingleTop = true }
                    },
                    harvestViewModel = harvestVm,
                    onOpenDecisionMaking = {
                        HarvestDecisionMakingBridge.navigateSafely { route ->
                            navController.navigate(route) { launchSingleTop = true }
                        }
                    }
                )
            }
            composable(Routes.HARVEST_AR) {
                ResultArScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.HARVEST_HISTORY) {
                ScanHistoryScreen(
                    onBack = { navController.popBackStack() },
                    onOpenRecord = { recordId ->
                        navController.navigate(Routes.harvestRecord(recordId)) { launchSingleTop = true }
                    },
                    viewModel = harvestVm
                )
            }
            composable(
                route = Routes.HARVEST_RECORD,
                arguments = listOf(navArgument(Routes.ARG_RECORD_ID) { type = NavType.StringType })
            ) { entry ->
                val recordId = entry.arguments?.getString(Routes.ARG_RECORD_ID).orEmpty()
                HarvestHistoryDetailScreen(
                    recordId = recordId,
                    onBack = { navController.popBackStack() },
                    harvestViewModel = harvestVm
                )
            }
            composable(Routes.HEAT_DISTRIBUTION) {
                HeatCropsHub(
                    heatVm = heatVm,
                    navController = navController,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.CROPS) {
                HeatCropsHub(
                    heatVm = heatVm,
                    navController = navController,
                    onBack = null
                )
            }
            composable(Routes.HEAT_SELECT_CROP) {
                HeatCropsHub(
                    heatVm = heatVm,
                    navController = navController,
                    onBack = null
                )
            }
            composable(Routes.HEAT_SELECT_STAGE) {
                SelectStageScreen(
                    crop = heatState.config.crop,
                    currentStage = heatState.config.stage,
                    pendingStage = heatState.pendingStage,
                    onSelect = { stage ->
                        if (heatVm.onStageClicked(stage)) {
                            navController.navigate(Routes.HEAT_CLIMATE) { launchSingleTop = true }
                        }
                    },
                    onConfirmChange = {
                        heatVm.confirmStageChange()
                        navController.navigate(Routes.HEAT_CLIMATE) { launchSingleTop = true }
                    },
                    onCancelChange = { heatVm.cancelStageChange() },
                    onBack = { navController.popBackStack() },
                    onContinue = {
                        val stage = heatState.config.stage
                        if (stage != null && heatVm.onStageClicked(stage)) {
                            navController.navigate(Routes.HEAT_CLIMATE) { launchSingleTop = true }
                        }
                    }
                )
            }
            composable(Routes.HEAT_CLIMATE) {
                ClimateTargetsScreen(
                    ui = heatState,
                    onPeriod = heatVm::selectPeriod,
                    onDayTempInput = heatVm::onDayTempInput,
                    onNightTempInput = heatVm::onNightTempInput,
                    onDayRhInput = heatVm::onDayRhInput,
                    onNightRhInput = heatVm::onNightRhInput,
                    onEdit = { heatVm.startClimateEdit() },
                    onReset = { heatVm.resetClimateToRecommended() },
                    onSaveContinue = {
                        heatVm.saveClimate { ok ->
                            if (ok) navController.heatForward(heatVm, Routes.HEAT_CIRCULATION)
                        }
                    },
                    onBack = {
                        heatVm.requestLeaveClimate { navController.popBackStack() }
                    },
                    onConfirmDiscard = {
                        heatVm.confirmDiscardClimate { navController.popBackStack() }
                    },
                    onCancelDiscard = { heatVm.cancelDiscardClimate() }
                )
            }
            composable(Routes.HEAT_CIRCULATION) {
                CirculationFanScreen(
                    ui = heatState,
                    onPeriod = heatVm::selectPeriod,
                    onMode = heatVm::requestControlMode,
                    onCsp = heatVm::onCspInput,
                    onCdp = heatVm::onCdpInput,
                    onCon = heatVm::onConInput,
                    onReset = { heatVm.resetCirculationToFormula() },
                    onSaveContinue = {
                        heatVm.saveCirculation { ok ->
                            if (ok) navController.heatForward(heatVm, Routes.HEAT_EXHAUST)
                        }
                    },
                    onBack = { navController.popBackStack() },
                    onConfirmAdvanced = heatVm::confirmAdvancedMode,
                    onCancelAdvanced = heatVm::cancelAdvancedMode
                )
            }
            composable(Routes.HEAT_EXHAUST) {
                ExhaustFanScreen(
                    ui = heatState,
                    onPeriod = heatVm::selectPeriod,
                    onMode = heatVm::requestControlMode,
                    onEsp = heatVm::onEspInput,
                    onEon = heatVm::onEonInput,
                    onReset = { heatVm.resetExhaustToFormula() },
                    onSaveContinue = {
                        heatVm.saveExhaust { ok ->
                            if (ok) navController.heatForward(heatVm, Routes.HEAT_FOGGER)
                        }
                    },
                    onBack = { navController.popBackStack() },
                    onConfirmAdvanced = heatVm::confirmAdvancedMode,
                    onCancelAdvanced = heatVm::cancelAdvancedMode
                )
            }
            composable(Routes.HEAT_FOGGER) {
                FoggerSettingsScreen(
                    ui = heatState,
                    onPeriod = heatVm::selectPeriod,
                    onMode = heatVm::requestControlMode,
                    onFsp = heatVm::onFspInput,
                    onFon = heatVm::onFonInput,
                    onFdp = heatVm::onFdpInput,
                    onReset = { heatVm.resetFoggerToFormula() },
                    onSaveContinue = {
                        heatVm.saveFogger { ok ->
                            if (ok) navController.heatForward(heatVm, Routes.HEAT_SUMMARY)
                        }
                    },
                    onBack = { navController.popBackStack() },
                    onConfirmAdvanced = heatVm::confirmAdvancedMode,
                    onCancelAdvanced = heatVm::cancelAdvancedMode
                )
            }
            composable(Routes.HEAT_SUMMARY) {
                ConfigurationSummaryScreen(
                    ui = heatState,
                    onEditClimate = {
                        heatVm.markReturnToSummary()
                        navController.navigate(Routes.HEAT_CLIMATE) { launchSingleTop = true }
                    },
                    onEditCirculation = {
                        heatVm.markReturnToSummary()
                        navController.navigate(Routes.HEAT_CIRCULATION) { launchSingleTop = true }
                    },
                    onEditExhaust = {
                        heatVm.markReturnToSummary()
                        navController.navigate(Routes.HEAT_EXHAUST) { launchSingleTop = true }
                    },
                    onEditFogger = {
                        heatVm.markReturnToSummary()
                        navController.navigate(Routes.HEAT_FOGGER) { launchSingleTop = true }
                    },
                    onResetEntire = {
                        heatVm.resetEntireConfiguration()
                        val popped = navController.popBackStack(Routes.CROPS, false) ||
                            navController.popBackStack(Routes.HEAT_SELECT_CROP, false) ||
                            navController.popBackStack(Routes.HEAT_DISTRIBUTION, false)
                        if (!popped) {
                            navController.navigate(Routes.CROPS) { launchSingleTop = true }
                        }
                    },
                    onContinueSimulation = {
                        heatVm.saveConfiguration { ok ->
                            if (ok) {
                                navController.navigate(Routes.SIMULATION) { launchSingleTop = true }
                            }
                        }
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.HEAT_SIMULATION_NEXT) {
                DemoSimulationNextScreen(
                    cropName = heatState.config.crop?.displayName,
                    stage = heatState.config.stage,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.SIMULATION) {
                DemoSimulationNextScreen(
                    cropName = heatState.config.crop?.displayName,
                    stage = heatState.config.stage,
                    onBack = null
                )
            }
            composable(Routes.SOURCES) {
                SourcesScreen(onBack = null)
            }
            composable(Routes.ACCOUNT) {
                AccountHomeScreen(
                    session = session,
                    onProfile = { navController.navigate(Routes.PROFILE) { launchSingleTop = true } },
                    onEditProfile = { navController.navigate(Routes.EDIT_PROFILE) { launchSingleTop = true } },
                    onSettings = { navController.navigate(Routes.SETTINGS) { launchSingleTop = true } },
                    onHelp = { navController.navigate(Routes.HELP) { launchSingleTop = true } },
                    onAbout = { navController.navigate(Routes.ABOUT) { launchSingleTop = true } },
                    onPrivacy = { navController.navigate(Routes.PRIVACY) { launchSingleTop = true } },
                    onLogout = { navController.navigate(Routes.LOGOUT_CONFIRM) { launchSingleTop = true } }
                )
            }
            composable(Routes.PROFILE) {
                ProfileScreen(
                    session = session,
                    onEdit = { navController.navigate(Routes.EDIT_PROFILE) },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.EDIT_PROFILE) {
                EditProfileScreen(
                    session = session,
                    onSave = { name, email, photoPath, removePhoto ->
                        sessionViewModel.updateProfile(name, email)
                        if (removePhoto) {
                            sessionViewModel.clearProfilePhoto()
                        } else {
                            sessionViewModel.applyProfilePhoto(photoPath)
                        }
                        navController.popBackStack()
                    },
                    onCancel = { navController.popBackStack() }
                )
            }
            composable(Routes.SETTINGS) {
                val previous = navController.previousBackStackEntry?.destination?.route
                SettingsScreen(
                    session = session,
                    onThemeChange = sessionViewModel::setThemeMode,
                    onDemoModeChange = sessionViewModel::setDemoMode,
                    onNotificationsChange = sessionViewModel::setNotificationsEnabled,
                    onReset = sessionViewModel::resetDemoSettings,
                    onBack = if (previous == Routes.ACCOUNT) {
                        { navController.popBackStack() }
                    } else {
                        null
                    }
                )
            }
            composable(Routes.HELP) {
                HelpScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.ABOUT) {
                AboutScreen(
                    onBack = { navController.popBackStack() },
                    onPrivacy = { navController.navigate(Routes.PRIVACY) { launchSingleTop = true } }
                )
            }
            composable(Routes.PRIVACY) {
                PrivacyScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.LOGOUT_CONFIRM) {
                LogoutConfirmScreen(
                    onConfirm = {
                        sessionViewModel.logout()
                        NavActions.logoutToLogin(navController)
                    },
                    onCancel = { navController.popBackStack() }
                )
            }
            composable(Routes.NOTIFICATIONS) {
                NotificationsScreen(
                    notificationsEnabled = session.notificationsEnabled,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}

@Composable
private fun HeatCropsHub(
    heatVm: HeatConfigViewModel,
    navController: NavHostController,
    onBack: (() -> Unit)?
) {
    val heatState by heatVm.state.collectAsState()
    val recent = heatState.workspace.configurations.values
        .filter { it.crop != null }
        .toList()
    SelectCropScreen(
        onSelectCrop = { crop ->
            heatVm.selectCrop(crop)
            navController.navigate(Routes.HEAT_SELECT_STAGE) { launchSingleTop = true }
        },
        onBack = onBack,
        activeConfig = heatState.config.takeIf { it.crop != null },
        recentConfigs = recent,
        onResumeConfiguration = {
            val config = heatState.config
            when {
                config.crop != null && config.stage != null ->
                    navController.navigate(Routes.HEAT_CLIMATE) { launchSingleTop = true }
                config.crop != null ->
                    navController.navigate(Routes.HEAT_SELECT_STAGE) { launchSingleTop = true }
            }
        },
        onCreateNewConfiguration = { }
    )
}

private fun NavHostController.heatForward(viewModel: HeatConfigViewModel, next: String) {
    if (viewModel.consumeReturnToSummary()) {
        popBackStack()
    } else {
        navigate(next) { launchSingleTop = true }
    }
}
