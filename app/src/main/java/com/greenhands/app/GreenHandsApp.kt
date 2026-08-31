package com.greenhands.app

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.greenhands.app.heat.ui.HeatConfigViewModel
import com.greenhands.app.profile.DataStoreProfilePhotoRepository
import com.greenhands.app.sensor.ui.SensorPlacementViewModel
import com.greenhands.app.sensor.ui.SensorPlacementViewModelFactory
import com.greenhands.app.session.AppSessionViewModel
import com.greenhands.app.session.AppSessionViewModelFactory
import com.greenhands.app.ui.navigation.GreenHandsNavGraph
import com.greenhands.app.ui.navigation.Routes
import com.greenhands.app.ui.theme.GreenHandsTheme

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun GreenHandsApp(
    sessionViewModel: AppSessionViewModel? = null,
    heatViewModel: HeatConfigViewModel? = null,
    sensorViewModel: SensorPlacementViewModel? = null,
    startDestination: String = Routes.SPLASH
) {
    val app = LocalContext.current.applicationContext as Application
    val resolvedSession: AppSessionViewModel = sessionViewModel ?: viewModel(
        factory = AppSessionViewModelFactory(DataStoreProfilePhotoRepository(app))
    )
    // Activity-scoped: survives Dashboard/Settings/Account tab switches in-session.
    val resolvedSensor: SensorPlacementViewModel = sensorViewModel ?: viewModel(
        factory = SensorPlacementViewModelFactory()
    )
    val session by resolvedSession.state.collectAsState()
    val navController = rememberNavController()

    GreenHandsTheme(themeMode = session.themeMode) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .semantics { testTagsAsResourceId = true }
        ) {
            GreenHandsNavGraph(
                navController = navController,
                sessionViewModel = resolvedSession,
                startDestination = startDestination,
                heatViewModel = heatViewModel,
                sensorViewModel = resolvedSensor
            )
        }
    }
}
