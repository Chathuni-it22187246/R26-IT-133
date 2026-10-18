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
import com.greenhands.app.harvest.detection.ClusterDisplayBox
import com.greenhands.app.harvest.detection.ClusterScanSummary
import com.greenhands.app.harvest.detection.CropScanMode
import com.greenhands.app.harvest.detection.HarvestScanDiag
import com.greenhands.app.harvest.detection.MultiTomatoClusterProcessor
import com.greenhands.app.harvest.detection.MultiTomatoClusterResult
import com.greenhands.app.harvest.detection.ScanTargetType
import com.greenhands.app.harvest.domain.CropScanGate
import com.greenhands.app.harvest.domain.HarvestDecisionEngine
import com.greenhands.app.harvest.domain.HsvAnalyzer
import com.greenhands.app.harvest.model.FruitColorMeasurement
import com.greenhands.app.harvest.model.HarvestDecision
import com.greenhands.app.ui.theme.AmberWarning
import com.greenhands.app.ui.theme.ForestEmerald
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
    val clusterProcessor = remember { MultiTomatoClusterProcessor() }
    val harvestState by harvestViewModel.state.collectAsState()
    var scanMode by rememberSaveable { mutableStateOf(CropScanMode.SINGLE) }
    var captured by rememberSaveable { mutableStateOf(false) }
    var measurement by remember { mutableStateOf<FruitColorMeasurement?>(null) }
    var clusterResult by remember { mutableStateOf<MultiTomatoClusterResult?>(null) }
    var analyzing by remember { mutableStateOf(false) }

    val canScan = CropScanGate.allowScan(harvestState.plantingDateUtcMillis)
    val clusterScan = scanMode == CropScanMode.CLUSTER
    val decision = remember(measurement, harvestState.maturity, captured, analyzing, clusterScan) {
        if (clusterScan || !captured || analyzing) null
        else engine.decideTomato(measurement, harvestState.maturity)
    }
    val clusterComplete = clusterScan && captured && !analyzing && clusterResult != null
    val status = when {
        !canScan -> stringResource(R.string.harvest_crop_scan_requires_date)
        analyzing -> stringResource(R.string.harvest_scan_status_analyzing)
        !captured && clusterScan -> stringResource(R.string.harvest_cluster_status_idle)
        !captured -> stringResource(R.string.harvest_fruit_scan_status_idle)
        clusterComplete && (clusterResult?.fruitCount ?: 0) == 0 ->
            stringResource(R.string.harvest_cluster_status_empty)
        clusterComplete -> stringResource(
            R.string.harvest_cluster_status_decided,
            clusterResult?.fruitCount ?: 0
        )
        decision?.scanRequired == true -> stringResource(R.string.harvest_scan_status_insufficient)
        else -> stringResource(R.string.harvest_fruit_scan_status_decided)
    }

    fun resetCaptureState() {
        captured = false
        analyzing = false
        measurement = null
        clusterResult = null
        clusterProcessor.resetTracks()
    }

    MockCameraScanScaffold(
        title = stringResource(R.string.harvest_fruit_scan_title),
        instruction = if (clusterScan) {
            stringResource(R.string.harvest_fruit_scan_instruction_cluster)
        } else {
            stringResource(R.string.harvest_fruit_scan_instruction)
        },
        statusText = status,
        onBack = onBack,
        targetType = ScanTargetType.TOMATO_FRUIT,
        analyzing = analyzing,
        hasResult = if (clusterScan) clusterComplete else captured && !analyzing && decision != null,
        showDemoChip = false,
        captureEnabled = canScan,
        clusterMode = clusterScan,
        frozenClusterBoxes = clusterResult?.fruits?.map { fruit ->
            ClusterDisplayBox(
                displayId = fruit.displayId,
                box = fruit.detection.boundingBox
            )
        },
        onValidatedCapture = capture@{ crop ->
            if (!canScan || analyzing || clusterScan) return@capture
            captured = true
            analyzing = true
            scope.launch {
                try {
                    val result = withContext(Dispatchers.Default) { analyzer.analyzeFruit(crop) }
                    val decisionResult = engine.decideTomato(result, harvestState.maturity)
                    measurement = result
                    HarvestMeasurementStore.lastFruit = result
                    HarvestMeasurementStore.lastFruitDecision = decisionResult
                    HarvestMeasurementStore.lastClusterResult = null
                    val capture = HarvestMeasurementStore.lastScanCapture
                    HarvestScanDiag.log(
                        HarvestScanDiag.singleFruitLine(
                            rotationDegrees = capture?.rotationDegrees ?: crop.rotationDegrees,
                            frameWidth = capture?.frameWidth ?: crop.width,
                            frameHeight = capture?.frameHeight ?: crop.height,
                            detection = capture?.detection,
                            measurement = result,
                            result = decisionResult,
                            daysSincePlanting = harvestState.daysSincePlanting
                        )
                    )
                } finally {
                    analyzing = false
                }
            }
        },
        onClusterCapture = capture@{ frame, detections ->
            if (!canScan || analyzing || !clusterScan) return@capture
            captured = true
            analyzing = true
            scope.launch {
                try {
                    val result = withContext(Dispatchers.Default) {
                        clusterProcessor.analyze(
                            frame = frame,
                            detections = detections,
                            maturity = harvestState.maturity,
                            enabled = true
                        )
                    }
                    clusterResult = result
                    measurement = null
                    HarvestMeasurementStore.lastClusterResult = result
                    HarvestMeasurementStore.lastFruit = null
                    HarvestMeasurementStore.lastFruitDecision = null
                    val capture = HarvestMeasurementStore.lastScanCapture
                    HarvestScanDiag.log(
                        HarvestScanDiag.clusterLine(
                            rotationDegrees = capture?.rotationDegrees ?: frame.rotationDegrees,
                            frameWidth = capture?.frameWidth ?: frame.width,
                            frameHeight = capture?.frameHeight ?: frame.height,
                            result = result,
                            daysSincePlanting = harvestState.daysSincePlanting
                        )
                    )
                } finally {
                    analyzing = false
                }
            }
        },
        belowInstruction = {
            CropScanModeRow(
                mode = scanMode,
                onModeChange = { next ->
                    if (next != scanMode) {
                        scanMode = next
                        resetCaptureState()
                    }
                }
            )
        },
        overlay = if (clusterComplete) {
            {
                val fruits = clusterResult?.fruits.orEmpty()
                val statusColor = when {
                    fruits.isEmpty() -> AmberWarning
                    fruits.all {
                        it.harvest.decision == HarvestDecision.READY_TO_HARVEST
                    } -> ForestEmerald
                    else -> AmberWarning
                }
                MockArOverlayCard(
                    heading = stringResource(R.string.harvest_cluster_overlay_heading),
                    status = stringResource(
                        R.string.harvest_cluster_overlay_status,
                        fruits.size
                    ),
                    lineOne = if (fruits.isEmpty()) {
                        stringResource(R.string.harvest_cluster_status_empty)
                    } else {
                        ClusterScanSummary.overlayLines(fruits).joinToString("\n")
                    },
                    lineTwo = stringResource(R.string.harvest_cluster_disclaimer),
                    statusColor = statusColor,
                    showDemoChip = false,
                    onViewDetails = { onViewDetails("live") }
                )
            }
        } else decision?.let { sample ->
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
