package com.greenhands.app.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.greenhands.app.sensor.ui.CoverageScreen
import com.greenhands.app.sensor.ui.PlaceSensorsScreen
import com.greenhands.app.sensor.ui.ScanGreenhouseScreen
import com.greenhands.app.sensor.ui.SensorPlacementViewModel
import com.greenhands.app.sensor.ui.SensorPlacementViewModelFactory
import com.greenhands.app.sensor.ui.SensorWorkflowStep
import com.greenhands.app.ui.theme.GreenHandsTheme
import com.greenhands.app.ui.theme.ThemeMode

/**
 * DEBUG-ONLY preview for Component 1 screens already implemented.
 * Not compiled into release builds. Does not change production navigation.
 */
class SensorPlacementPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GreenHandsTheme(themeMode = ThemeMode.DARK) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SensorPlacementPreviewHost(onClose = { finish() })
                }
            }
        }
    }
}

private enum class SensorPreviewPage {
    SCAN,
    PLACE,
    COVERAGE
}

@Composable
private fun SensorPlacementPreviewHost(
    onClose: () -> Unit,
    viewModel: SensorPlacementViewModel = viewModel(factory = SensorPlacementViewModelFactory())
) {
    val ui by viewModel.state.collectAsState()
    var page by rememberSaveable { mutableStateOf(SensorPreviewPage.SCAN) }

    when (page) {
        SensorPreviewPage.SCAN -> ScanGreenhouseScreen(
            ui = ui,
            onStartScan = viewModel::startScan,
            onResetScan = viewModel::resetScan,
            onContinueToPlacement = {
                viewModel.goToStep(SensorWorkflowStep.PLACE)
                page = SensorPreviewPage.PLACE
            },
            onBack = onClose
        )
        SensorPreviewPage.PLACE -> PlaceSensorsScreen(
            ui = ui,
            onAddSensor = { x, y -> viewModel.addSensor(x, y) },
            onSelectSensor = { viewModel.selectSensor(it) },
            onMoveSensor = viewModel::moveSensor,
            onDeleteSensor = { viewModel.removeSensor(it) },
            onSetSensorActive = viewModel::setSensorActive,
            onDeselectSensor = viewModel::deselectSensor,
            onResetSensors = viewModel::resetSensors,
            onOpenSensorTypePicker = viewModel::openSensorTypePicker,
            onSelectPendingSensorType = viewModel::selectPendingSensorType,
            onConfirmPendingSensorType = viewModel::confirmPendingSensorType,
            onCancelSensorTypePicker = viewModel::cancelSensorTypePicker,
            onContinueToCoverage = {
                viewModel.goToStep(SensorWorkflowStep.COVERAGE)
                page = SensorPreviewPage.COVERAGE
            },
            onBack = {
                viewModel.goToStep(SensorWorkflowStep.SCAN)
                page = SensorPreviewPage.SCAN
            }
        )
        SensorPreviewPage.COVERAGE -> CoverageScreen(
            ui = ui,
            onBack = {
                viewModel.goToStep(SensorWorkflowStep.PLACE)
                page = SensorPreviewPage.PLACE
            }
        )
    }
}
