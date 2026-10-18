package com.greenhands.app.harvest.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.greenhands.app.R
import com.greenhands.app.harvest.data.HarvestMeasurementStore
import com.greenhands.app.harvest.detection.ScanTargetType
import com.greenhands.app.harvest.domain.CropScanGate
import com.greenhands.app.harvest.domain.HarvestDecisionEngine
import com.greenhands.app.harvest.domain.HsvAnalyzer
import com.greenhands.app.harvest.model.FruitColorMeasurement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun FruitScanScreen(
    onBack: () -> Unit,
    onViewDetails: (mockId: String) -> Unit,
    harvestViewModel: HarvestViewModel
) {
    val scope = rememberCoroutineScope()
    val analyzer = remember { HsvAnalyzer(centerCropFraction = 1f) }
    val engine = remember { HarvestDecisionEngine() }
    val harvestState by harvestViewModel.state.collectAsState()
    var captured by rememberSaveable { mutableStateOf(false) }
    var measurement by remember { mutableStateOf<FruitColorMeasurement?>(null) }
    var analyzing by remember { mutableStateOf(false) }

    val canScan = CropScanGate.allowScan(harvestState.plantingDateUtcMillis)
    val decision = remember(measurement, harvestState.maturity, captured, analyzing) {
        if (!captured || analyzing) null
        else engine.decideTomato(measurement, harvestState.maturity)
    }
    val status = when {
        !canScan -> stringResource(R.string.harvest_crop_scan_requires_date)
        analyzing -> stringResource(R.string.harvest_scan_status_analyzing)
        !captured -> stringResource(R.string.harvest_fruit_scan_status_idle)
        decision?.scanRequired == true -> stringResource(R.string.harvest_scan_status_insufficient)
        else -> stringResource(R.string.harvest_fruit_scan_status_decided)
    }

    MockCameraScanScaffold(
        title = stringResource(R.string.harvest_fruit_scan_title),
        instruction = stringResource(R.string.harvest_fruit_scan_instruction),
        statusText = status,
        onBack = onBack,
        targetType = ScanTargetType.TOMATO_FRUIT,
        analyzing = analyzing,
        hasResult = captured && !analyzing && decision != null,
        showDemoChip = false,
        captureEnabled = canScan,
        onValidatedCapture = capture@{ crop ->
            if (!canScan || analyzing) return@capture
            captured = true
            analyzing = true
            scope.launch {
                try {
                    val result = withContext(Dispatchers.Default) { analyzer.analyzeFruit(crop) }
                    measurement = result
                    HarvestMeasurementStore.lastFruit = result
                    HarvestMeasurementStore.lastFruitDecision =
                        engine.decideTomato(result, harvestState.maturity)
                } finally {
                    analyzing = false
                }
            }
        },
        overlay = decision?.let { sample ->
            {
                MockArOverlayCard(
                    heading = stringResource(R.string.harvest_overlay_harvest_status),
                    status = sample.displayLabel,
                    lineOne = sample.maturityReasonLabel,
                    lineTwo = null,
                    statusColor = harvestDecisionColor(sample.decision, sample.scanRequired),
                    showDemoChip = false,
                    onViewDetails = { onViewDetails("live") }
                )
            }
        },
        testTag = "harvest_fruit_scan"
    )
}
