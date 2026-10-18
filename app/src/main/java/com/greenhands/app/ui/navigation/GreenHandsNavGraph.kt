package com.greenhands.app.ui.navigation

import android.app.Application
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.greenhands.app.decision.ArGreenhouseScreen
import com.greenhands.app.decision.DecisionIotClimate
import com.greenhands.app.decision.DecisionMakingScreen
import com.greenhands.app.decision.DecisionSection
import com.greenhands.app.decision.DecisionTelemetrySession
import com.greenhands.app.decision.InfectionRecordDetailScreen
import com.greenhands.app.decision.InfectionScanScreen
import com.greenhands.app.ar.ui.GreenhouseArScreen
import com.greenhands.app.equipment.data.DataStoreEquipmentLayoutRepository
import com.greenhands.app.equipment.ui.EquipmentPlacementViewModel
import com.greenhands.app.equipment.ui.EquipmentPlacementViewModelFactory
import com.greenhands.app.equipment.ui.OverlayClimateMapper
import com.greenhands.app.harvest.ar.ArResultMapper
import com.greenhands.app.harvest.ar.ArResultStore
import com.greenhands.app.harvest.ar.ResultArScreen
import com.greenhands.app.harvest.data.AssetHarvestReferenceRepository
import com.greenhands.app.harvest.data.HarvestTelemetryEnvironmentRepository
import com.greenhands.app.harvest.data.RoomScanHistoryRepository
import com.greenhands.app.harvest.integration.HarvestDecisionMakingBridge
import com.greenhands.app.harvest.ui.FruitScanScreen
import com.greenhands.app.harvest.ui.HarvestHistoryDetailScreen
import com.greenhands.app.harvest.ui.HarvestHubScreen
import com.greenhands.app.harvest.ui.HarvestResultScreen
import com.greenhands.app.harvest.ui.HarvestViewModel
import com.greenhands.app.harvest.ui.HarvestViewModelFactory
import com.greenhands.app.harvest.ui.LeafScanScreen
import com.greenhands.app.harvest.ui.PlantHealthResultScreen
import com.greenhands.app.harvest.ui.ScanHistoryScreen
import com.greenhands.app.heat.data.DataStoreHeatConfigRepository
import com.greenhands.app.heat.data.DataStoreHeatSessionRepository
import com.greenhands.app.heat.ui.ActiveConfigurationScreen
import com.greenhands.app.heat.ui.CirculationFanScreen
import com.greenhands.app.heat.ui.ClimateTargetsScreen
import com.greenhands.app.heat.ui.ConfigurationSummaryScreen
import com.greenhands.app.heat.ui.DemoSimulationNextScreen
import com.greenhands.app.heat.ui.ExhaustFanScreen
import com.greenhands.app.heat.ui.FoggerSettingsScreen
import com.greenhands.app.heat.ui.HeatConfigViewModel
import com.greenhands.app.heat.ui.HeatConfigViewModelFactory
import com.greenhands.app.heat.ui.RecentConfigurationsScreen
import com.greenhands.app.heat.ui.SelectCropScreen
import com.greenhands.app.heat.ui.SelectStageScreen
import com.greenhands.app.heat.ui.SourcesScreen
import com.greenhands.app.preview3d.model.DemoGreenhouseProfile
import com.greenhands.app.preview3d.model.GreenhouseDimensions
import com.greenhands.app.preview3d.model.PreviewMode
import com.greenhands.app.preview3d.ui.GreenhousePreviewScreen
import com.greenhands.app.preview3d.ui.GreenhousePreviewSetupRoute
import com.greenhands.app.heat.simulation.model.ProvenanceClass
import com.greenhands.app.sensor.telemetry.SensorArTelemetrySession
import com.greenhands.app.sensor.ui.CoverageScreen
import com.greenhands.app.sensor.ui.GreenhouseSetupScreen
import com.greenhands.app.sensor.ui.OptimizePlacementScreen
import com.greenhands.app.sensor.ui.PlaceSensorsScreen
import com.greenhands.app.sensor.ui.RealGreenhouseArScreen
import com.greenhands.app.sensor.ui.ScanGreenhouseScreen
import com.greenhands.app.sensor.ui.SensorPlacementViewModel
import com.greenhands.app.sensor.ui.SensorPlacementViewModelFactory
import com.greenhands.app.sensor.ui.SensorWorkflowStep
import com.greenhands.app.sensor.ui.VirtualGreenhousePreviewScreen
import com.greenhands.app.session.AppSessionViewModel
import com.greenhands.app.ui.screens.AboutScreen
import com.greenhands.app.ui.screens.AccountHomeScreen
import com.greenhands.app.ui.screens.ComingSoonScreen
import com.greenhands.app.ui.screens.DashboardScreen
import com.greenhands.app.ui.screens.dashboardModuleRoute
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
    heatViewModel: HeatConfigViewModel? = null,
    sensorViewModel: SensorPlacementViewModel? = null
) {
    val session by sessionViewModel.state.collectAsState()
    val app = LocalContext.current.applicationContext as Application
    val defaultHeatVm: HeatConfigViewModel = viewModel(
        factory = HeatConfigViewModelFactory(
            DataStoreHeatConfigRepository(app),
            DataStoreHeatSessionRepository(app)
        )
    )
    val heatVm = heatViewModel ?: defaultHeatVm
    val heatState by heatVm.state.collectAsState()
    val placementVm: EquipmentPlacementViewModel = viewModel(
        factory = EquipmentPlacementViewModelFactory(
            DataStoreEquipmentLayoutRepository(app),
            com.greenhands.app.telemetry.GreenHandsTelemetry.sharedRepository()
        )
    )
    val harvestFactory = remember(app) {
        HarvestViewModelFactory(
            scanHistoryRepository = RoomScanHistoryRepository(app),
            referenceRepository = AssetHarvestReferenceRepository(app),
            environmentRepository = HarvestTelemetryEnvironmentRepository(
                com.greenhands.app.telemetry.GreenHandsTelemetry.sharedRepository()
            )
        )
    }
    val harvestVm: HarvestViewModel = viewModel(factory = harvestFactory)
    val sensorVm: SensorPlacementViewModel = sensorViewModel ?: viewModel(
        factory = SensorPlacementViewModelFactory()
    )
    val sensorState by sensorVm.state.collectAsState()
    val sensorArTelemetry = remember(app) {
        SensorArTelemetrySession(
            com.greenhands.app.telemetry.GreenHandsTelemetry.sharedRepository()
        )
    }
    val sensorTelemetry by sensorArTelemetry.states.collectAsState()
    val decisionTelemetry = remember(app) {
        DecisionTelemetrySession(
            com.greenhands.app.telemetry.GreenHandsTelemetry.sharedRepository()
        )
    }
    val decisionIotState by decisionTelemetry.states.collectAsState()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = currentRoute != null &&
        currentRoute !in Routes.unauthenticated &&
        currentRoute !in Routes.immersivePreviewRoutes &&
        currentRoute !in Routes.harvestCameraRoutes &&
        currentRoute !in Routes.sensorImmersiveRoutes &&
        !Routes.isDecisionImmersiveRoute(currentRoute)
    val heatNavigation = Routes.shouldShowHeatNavigation(currentRoute, session.heatWorkspaceActive)

    LaunchedEffect(session.isLoggedIn, session.userEmail) {
        if (session.isLoggedIn) {
            heatVm.bindUserSession(session.userEmail)
        } else {
            heatVm.unbindUserSession()
        }
    }

    LaunchedEffect(currentRoute) {
        when {
            currentRoute == Routes.DASHBOARD -> sessionViewModel.exitHeatWorkspace()
            Routes.isSensorContentRoute(currentRoute) -> sessionViewModel.exitHeatWorkspace()
            Routes.isHeatContentRoute(currentRoute) -> sessionViewModel.enterHeatWorkspace()
        }
    }

    val onHarvestRoute = Routes.isHarvestRoute(currentRoute)
    DisposableEffect(onHarvestRoute) {
        if (onHarvestRoute) harvestVm.enterHarvesting()
        onDispose {
            if (onHarvestRoute) harvestVm.leaveHarvesting()
        }
    }

    val onSensorRealAr = currentRoute == Routes.SENSOR_REAL_AR
    DisposableEffect(onSensorRealAr) {
        if (onSensorRealAr) sensorArTelemetry.acquire()
        onDispose {
            if (onSensorRealAr) sensorArTelemetry.release()
        }
    }

    val onDecisionMaking = Routes.isDecisionMakingRoute(currentRoute)
    DisposableEffect(onDecisionMaking) {
        if (onDecisionMaking) decisionTelemetry.acquire()
        onDispose {
            if (onDecisionMaking) decisionTelemetry.release()
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
                    onOpenModule = { module ->
                        val dest = dashboardModuleRoute(module)
                        if (module.isHeat) {
                            sessionViewModel.enterHeatWorkspace()
                        }
                        if (module.isSensor || module.id == Routes.SENSOR_PLACEMENT) {
                            navController.navigateToSensorWorkflow(
                                SensorNavigation.resumeRoute(sensorState)
                            )
                        } else {
                            navController.navigate(dest) { launchSingleTop = true }
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
            composable(
                route = Routes.DECISION_MAKING_ROUTE,
                arguments = listOf(
                    navArgument(Routes.ARG_DECISION_SECTION) {
                        type = NavType.StringType
                        defaultValue = DecisionSection.ALL.routeArg
                    }
                )
            ) { entry ->
                val section = DecisionSection.fromRoute(
                    entry.arguments?.getString(Routes.ARG_DECISION_SECTION)
                )
                DecisionMakingScreen(
                    onBack = { navController.popBackStack() },
                    onOpenInfectionScan = { crop, recordId ->
                        navController.navigate(Routes.infectionScan(crop, recordId)) {
                            launchSingleTop = true
                        }
                    },
                    onOpenInfectionRecord = { recordId ->
                        navController.navigate(Routes.infectionRecord(recordId)) {
                            launchSingleTop = true
                        }
                    },
                    initialSection = section,
                    iotClimate = DecisionIotClimate.from(decisionIotState)
                )
            }
            composable(
                route = Routes.DECISION_AR,
                arguments = listOf(
                    navArgument(Routes.ARG_WIDTH) { type = NavType.StringType },
                    navArgument(Routes.ARG_HEIGHT) { type = NavType.StringType }
                )
            ) { entry ->
                val width = entry.arguments?.getString(Routes.ARG_WIDTH)?.toFloatOrNull() ?: 3.0f
                val height = entry.arguments?.getString(Routes.ARG_HEIGHT)?.toFloatOrNull() ?: 2.5f
                ArGreenhouseScreen(
                    widthMeters = width,
                    heightMeters = height,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = Routes.INFECTION_SCAN,
                arguments = listOf(
                    navArgument(Routes.ARG_CROP) { type = NavType.StringType },
                    navArgument(Routes.ARG_RECORD_ID) { type = NavType.StringType }
                )
            ) { entry ->
                val crop = entry.arguments?.getString(Routes.ARG_CROP).orEmpty().ifBlank { "Tomato" }
                val recordId = entry.arguments?.getString(Routes.ARG_RECORD_ID)
                    ?.takeIf { it.isNotBlank() && it != "_" }
                InfectionScanScreen(
                    crop = crop,
                    updateRecordId = recordId,
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() }
                )
            }
            composable(
                route = Routes.INFECTION_RECORD,
                arguments = listOf(
                    navArgument(Routes.ARG_RECORD_ID) { type = NavType.StringType }
                )
            ) { entry ->
                val recordId = entry.arguments?.getString(Routes.ARG_RECORD_ID).orEmpty()
                InfectionRecordDetailScreen(
                    recordId = recordId,
                    onBack = { navController.popBackStack() },
                    onUpdateScan = { record ->
                        navController.navigate(Routes.infectionScan(record.plantType, record.id)) {
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(Routes.SENSOR_SETUP) {
                GreenhouseSetupScreen(
                    ui = sensorState,
                    onCreateGreenhouse = sensorVm::createOrUpdateGreenhouse,
                    onContinueToScan = {
                        sensorVm.goToStep(SensorWorkflowStep.SCAN)
                        navController.navigate(Routes.SENSOR_SCAN) { launchSingleTop = true }
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.SENSOR_SCAN) {
                ScanGreenhouseScreen(
                    ui = sensorState,
                    onStartScan = sensorVm::startScan,
                    onResetScan = sensorVm::resetScan,
                    onContinueToPlacement = {
                        sensorVm.goToStep(SensorWorkflowStep.PLACE)
                        navController.navigate(Routes.SENSOR_PLACE) { launchSingleTop = true }
                    },
                    onBack = {
                        sensorVm.goToStep(SensorWorkflowStep.SETUP)
                        navController.popBackStack()
                    }
                )
            }
            composable(Routes.SENSOR_PLACE) {
                PlaceSensorsScreen(
                    ui = sensorState,
                    onAddSensor = { x, y -> sensorVm.addSensor(x, y) },
                    onSelectSensor = { sensorVm.selectSensor(it) },
                    onMoveSensor = sensorVm::moveSensor,
                    onDeleteSensor = { sensorVm.removeSensor(it) },
                    onSetSensorActive = sensorVm::setSensorActive,
                    onDeselectSensor = sensorVm::deselectSensor,
                    onResetSensors = sensorVm::resetSensors,
                    onOpenSensorTypePicker = sensorVm::openSensorTypePicker,
                    onSelectPendingSensorType = sensorVm::selectPendingSensorType,
                    onConfirmPendingSensorType = sensorVm::confirmPendingSensorType,
                    onCancelSensorTypePicker = sensorVm::cancelSensorTypePicker,
                    onContinueToCoverage = {
                        sensorVm.goToStep(SensorWorkflowStep.COVERAGE)
                        navController.navigate(Routes.SENSOR_COVERAGE) { launchSingleTop = true }
                    },
                    onBack = {
                        sensorVm.goToStep(SensorWorkflowStep.SCAN)
                        navController.popBackStack()
                    }
                )
            }
            composable(Routes.SENSOR_COVERAGE) {
                CoverageScreen(
                    ui = sensorState,
                    onContinueToOptimize = {
                        sensorVm.goToStep(SensorWorkflowStep.OPTIMIZE)
                        navController.navigate(Routes.SENSOR_OPTIMIZE) { launchSingleTop = true }
                    },
                    onOpenVirtualPreview = {
                        navController.navigate(Routes.SENSOR_VIRTUAL_PREVIEW) {
                            launchSingleTop = true
                        }
                    },
                    onOpenRealAr = {
                        navController.navigate(Routes.SENSOR_REAL_AR) {
                            launchSingleTop = true
                        }
                    },
                    onBack = {
                        sensorVm.goToStep(SensorWorkflowStep.PLACE)
                        navController.popBackStack()
                    }
                )
            }
            composable(Routes.SENSOR_OPTIMIZE) {
                OptimizePlacementScreen(
                    ui = sensorState,
                    onSelectType = sensorVm::selectOptimizationSensorType,
                    onAnalyze = sensorVm::calculateOptimization,
                    onSelectAlternative = sensorVm::selectOptimizationAlternative,
                    onTogglePosition = sensorVm::toggleOptimizationPosition,
                    onApply = { sensorVm.applyOptimization() },
                    onKeepCurrent = sensorVm::keepCurrentPlacement,
                    onDismissApplySummary = sensorVm::dismissOptimizationApplySummary,
                    onOpenVirtualPreview = {
                        navController.navigate(Routes.SENSOR_VIRTUAL_PREVIEW) {
                            launchSingleTop = true
                        }
                    },
                    onOpenRealAr = {
                        navController.navigate(Routes.SENSOR_REAL_AR) {
                            launchSingleTop = true
                        }
                    },
                    onBack = {
                        sensorVm.goToStep(SensorWorkflowStep.COVERAGE)
                        navController.popBackStack()
                    }
                )
            }
            composable(Routes.SENSOR_VIRTUAL_PREVIEW) {
                VirtualGreenhousePreviewScreen(
                    ui = sensorState,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.SENSOR_REAL_AR) {
                RealGreenhouseArScreen(
                    ui = sensorState,
                    onBack = { navController.popBackStack() },
                    onOpenVirtualPreview = {
                        navController.navigate(Routes.SENSOR_VIRTUAL_PREVIEW) {
                            launchSingleTop = true
                        }
                    },
                    onAddSensor = { x, y, type -> sensorVm.addSensor(x, y, type = type) },
                    onRemoveSensor = { id -> sensorVm.removeSensor(id) },
                    onResetSensors = { sensorVm.resetSensors() },
                    telemetryState = sensorTelemetry
                )
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
            composable(Routes.HEAT_ACTIVE_CONFIG) {
                ActiveConfigurationScreen(
                    record = heatState.activeSession,
                    onBack = { navController.popBackStack() },
                    onResume = {
                        if (heatVm.restoreFromActive()) {
                            val config = heatVm.state.value.config
                            when {
                                config.crop != null && config.stage != null ->
                                    navController.navigate(Routes.HEAT_CLIMATE) { launchSingleTop = true }
                                config.crop != null ->
                                    navController.navigate(Routes.HEAT_SELECT_STAGE) { launchSingleTop = true }
                            }
                        }
                    }
                )
            }
            composable(Routes.HEAT_RECENT_CONFIG) {
                RecentConfigurationsScreen(
                    records = heatState.recentSessions,
                    pendingDeleteId = heatState.pendingDeleteRecentId,
                    onBack = { navController.popBackStack() },
                    onDelete = heatVm::requestDeleteRecent,
                    onConfirmDelete = heatVm::confirmDeleteRecent,
                    onCancelDelete = heatVm::cancelDeleteRecent
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
                                heatVm.recordStartedSimulation()
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
                    onBack = { navController.popBackStack() },
                    onOpenGreenhousePreview = {
                        navController.navigate(Routes.GREENHOUSE_PREVIEW_SETUP) { launchSingleTop = true }
                    }
                )
            }
            composable(Routes.SIMULATION) {
                DemoSimulationNextScreen(
                    cropName = heatState.config.crop?.displayName,
                    stage = heatState.config.stage,
                    onBack = null,
                    onOpenGreenhousePreview = {
                        navController.navigate(Routes.GREENHOUSE_PREVIEW_SETUP) { launchSingleTop = true }
                    }
                )
            }
            composable(Routes.GREENHOUSE_PREVIEW_SETUP) {
                GreenhousePreviewSetupRoute(
                    onBack = { navController.popBackStack() },
                    onOpenPreview = { mode, dimensions, cropRowCount ->
                        heatVm.recordStartedPreview(mode, dimensions, cropRowCount)
                        navController.navigate(
                            Routes.greenhousePreviewRoute(
                                mode.name,
                                dimensions.lengthM,
                                dimensions.widthM,
                                dimensions.heightM,
                                cropRowCount
                            )
                        )
                    }
                )
            }
            composable(
                route = Routes.GREENHOUSE_PREVIEW,
                arguments = listOf(
                    navArgument(Routes.ARG_PREVIEW_MODE) { type = NavType.StringType },
                    navArgument(Routes.ARG_PREVIEW_LENGTH) { type = NavType.StringType },
                    navArgument(Routes.ARG_PREVIEW_WIDTH) { type = NavType.StringType },
                    navArgument(Routes.ARG_PREVIEW_HEIGHT) { type = NavType.StringType },
                    navArgument(Routes.ARG_PREVIEW_ROWS) { type = NavType.StringType }
                )
            ) { entry ->
                val mode = PreviewMode.fromRoute(entry.arguments?.getString(Routes.ARG_PREVIEW_MODE))
                val length = entry.arguments?.getString(Routes.ARG_PREVIEW_LENGTH)?.toDoubleOrNull()
                val width = entry.arguments?.getString(Routes.ARG_PREVIEW_WIDTH)?.toDoubleOrNull()
                val height = entry.arguments?.getString(Routes.ARG_PREVIEW_HEIGHT)?.toDoubleOrNull()
                val cropRowCount = entry.arguments?.getString(Routes.ARG_PREVIEW_ROWS)?.toIntOrNull()
                    ?: DemoGreenhouseProfile.CROP_ROW_COUNT
                val dimensions = if (
                    mode != null &&
                    length != null && length.isFinite() && length > 0.0 &&
                    width != null && width.isFinite() && width > 0.0 &&
                    height != null && height.isFinite() && height > 0.0
                ) {
                    if (mode == PreviewMode.DEMO) {
                        DemoGreenhouseProfile.dimensions()
                    } else {
                        GreenhouseDimensions(
                            lengthM = length,
                            widthM = width,
                            heightM = height,
                            provenance = ProvenanceClass.MEASURED_INPUT,
                            provenanceNote = "User-entered greenhouse envelope (metres). Not a Phase 3A simulation input."
                        )
                    }
                } else {
                    null
                }
                if (mode == null || dimensions == null) {
                    LaunchedEffect(entry.id) { navController.popBackStack() }
                } else {
                    GreenhousePreviewScreen(
                        mode = mode,
                        dimensions = dimensions,
                        cropRowCount = cropRowCount,
                        onBack = { navController.popBackStack() },
                        placementViewModel = placementVm,
                        overlayClimate = OverlayClimateMapper.fromActiveConfiguration(
                            config = heatState.config,
                            period = heatState.schedulePeriod,
                            temperatureC = heatState.activeTemperature(),
                            humidityPercent = heatState.activeHumidity()
                        ),
                        userId = session.userEmail.orEmpty(),
                        configurationId = heatState.activeSession?.id.orEmpty(),
                        onViewInAr = {
                            navController.navigate(
                                Routes.greenhouseArRoute(
                                    mode.name,
                                    dimensions.lengthM,
                                    dimensions.widthM,
                                    dimensions.heightM,
                                    cropRowCount
                                )
                            )
                        },
                        onPersistMyGreenhouse = { heatVm.saveMyGreenhouse() },
                        onViewDecisionSupport = { NavActions.openDecisionSupport(navController) }
                    )
                }
            }
            composable(
                route = Routes.GREENHOUSE_AR,
                arguments = listOf(
                    navArgument(Routes.ARG_PREVIEW_MODE) { type = NavType.StringType },
                    navArgument(Routes.ARG_PREVIEW_LENGTH) { type = NavType.StringType },
                    navArgument(Routes.ARG_PREVIEW_WIDTH) { type = NavType.StringType },
                    navArgument(Routes.ARG_PREVIEW_HEIGHT) { type = NavType.StringType },
                    navArgument(Routes.ARG_PREVIEW_ROWS) { type = NavType.StringType }
                )
            ) { entry ->
                val parsed = previewDestinationFrom(entry.arguments)
                if (parsed == null) {
                    LaunchedEffect(entry.id) { navController.popBackStack() }
                } else {
                    GreenhouseArScreen(
                        mode = parsed.mode,
                        dimensions = parsed.dimensions,
                        cropRowCount = parsed.cropRowCount,
                        onBackToPreview = { navController.popBackStack() },
                        placementViewModel = placementVm,
                        overlayClimate = OverlayClimateMapper.fromActiveConfiguration(
                            config = heatState.config,
                            period = heatState.schedulePeriod,
                            temperatureC = heatState.activeTemperature(),
                            humidityPercent = heatState.activeHumidity()
                        ),
                        userId = session.userEmail.orEmpty(),
                        configurationId = heatState.activeSession?.id.orEmpty(),
                        onPersistMyGreenhouse = { heatVm.saveMyGreenhouse() },
                        onViewDecisionSupport = { NavActions.openDecisionSupport(navController) }
                    )
                }
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
                    onBack = if (Routes.settingsShowsToolbarBack(previous)) {
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
    SelectCropScreen(
        onSelectCrop = { crop ->
            heatVm.selectCrop(crop)
            navController.navigate(Routes.HEAT_SELECT_STAGE) { launchSingleTop = true }
        },
        onBack = onBack,
        onOpenActive = {
            navController.navigate(Routes.HEAT_ACTIVE_CONFIG) { launchSingleTop = true }
        },
        onOpenRecent = {
            navController.navigate(Routes.HEAT_RECENT_CONFIG) { launchSingleTop = true }
        }
    )
}

private fun NavHostController.heatForward(viewModel: HeatConfigViewModel, next: String) {
    if (viewModel.consumeReturnToSummary()) {
        popBackStack()
    } else {
        navigate(next) { launchSingleTop = true }
    }
}

private data class PreviewDestination(
    val mode: PreviewMode,
    val dimensions: GreenhouseDimensions,
    val cropRowCount: Int
)

private fun previewDestinationFrom(args: android.os.Bundle?): PreviewDestination? {
    val mode = PreviewMode.fromRoute(args?.getString(Routes.ARG_PREVIEW_MODE))
    val length = args?.getString(Routes.ARG_PREVIEW_LENGTH)?.toDoubleOrNull()
    val width = args?.getString(Routes.ARG_PREVIEW_WIDTH)?.toDoubleOrNull()
    val height = args?.getString(Routes.ARG_PREVIEW_HEIGHT)?.toDoubleOrNull()
    val cropRowCount = args?.getString(Routes.ARG_PREVIEW_ROWS)?.toIntOrNull()
        ?: DemoGreenhouseProfile.CROP_ROW_COUNT
    if (
        mode == null ||
        length == null || !length.isFinite() || length <= 0.0 ||
        width == null || !width.isFinite() || width <= 0.0 ||
        height == null || !height.isFinite() || height <= 0.0
    ) {
        return null
    }
    val dimensions = if (mode == PreviewMode.DEMO) {
        DemoGreenhouseProfile.dimensions()
    } else {
        GreenhouseDimensions(
            lengthM = length,
            widthM = width,
            heightM = height,
            provenance = ProvenanceClass.MEASURED_INPUT,
            provenanceNote = "User-entered greenhouse envelope (metres). Not a Phase 3A simulation input."
        )
    }
    return PreviewDestination(mode, dimensions, cropRowCount)
}
