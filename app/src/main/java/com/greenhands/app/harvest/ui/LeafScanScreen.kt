package com.greenhands.app.harvest.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.greenhands.app.R
import com.greenhands.app.harvest.data.HarvestMeasurementStore
import com.greenhands.app.harvest.detection.ScanTargetType
import com.greenhands.app.harvest.detection.TomatoDiseaseClassifier
import com.greenhands.app.harvest.detection.TomatoDiseaseClassifyResult
import com.greenhands.app.harvest.domain.HsvAnalyzer
import com.greenhands.app.harvest.domain.PlantHealthStatus
import com.greenhands.app.harvest.domain.SimplePlantHealthDecider
import com.greenhands.app.harvest.integration.HarvestDecisionMakingAction
import com.greenhands.app.harvest.integration.HarvestDecisionMakingBridge
import com.greenhands.app.harvest.model.PlantHealthAssessment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@Composable
fun LeafScanScreen(
    onBack: () -> Unit,
    onViewDetails: (mockId: String) -> Unit,
    harvestViewModel: HarvestViewModel,
    onOpenDecisionMaking: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val harvestState by harvestViewModel.state.collectAsState()
    val appContext = LocalContext.current.applicationContext
    val analyzer = remember { HsvAnalyzer(centerCropFraction = 1f) }
    val classifier = remember(appContext) { TomatoDiseaseClassifier.tryOpen(appContext) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(classifier) {
        onDispose { classifier?.close() }
    }
    DisposableEffect(lifecycleOwner) {
        HarvestMeasurementStore.beginNewLeafScan()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                HarvestMeasurementStore.beginNewLeafScan()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            HarvestMeasurementStore.beginNewLeafScan()
        }
    }
    var captured by rememberSaveable { mutableStateOf(false) }
    var assessment by remember { mutableStateOf<PlantHealthAssessment?>(null) }
    var analyzing by remember { mutableStateOf(false) }

    val status = when {
        analyzing -> stringResource(R.string.harvest_scan_status_analyzing)
        !captured -> stringResource(R.string.harvest_leaf_scan_status_idle)
        assessment?.scanRequired == true -> stringResource(R.string.harvest_scan_status_insufficient)
        else -> stringResource(R.string.harvest_leaf_scan_status_decided)
    }

    MockCameraScanScaffold(
        title = stringResource(R.string.harvest_leaf_scan_title),
        instruction = stringResource(R.string.harvest_leaf_scan_instruction),
        statusText = status,
        onBack = onBack,
        targetType = ScanTargetType.TOMATO_LEAF,
        analyzing = analyzing,
        hasResult = captured && !analyzing && assessment != null,
        showDemoChip = false,
        onValidatedCapture = capture@{ crop ->
            if (analyzing) return@capture
            captured = true
            analyzing = true
            scope.launch {
                try {
                    val result = withContext(Dispatchers.Default) {
                        val hsv = analyzer.analyzeLeaf(crop)
                        val outcome = classifier?.classify(crop)
                        val prediction = (outcome as? TomatoDiseaseClassifyResult.Success)?.prediction
                        val roiReliable = outcome is TomatoDiseaseClassifyResult.Success
                        val previous = HarvestMeasurementStore.activeLeafScanHysteresis
                        val health = SimplePlantHealthDecider.decide(
                            measurement = hsv,
                            previousStatus = previous,
                            classifierAvailable = roiReliable,
                            prediction = prediction,
                            roiReliable = roiReliable
                        )
                        HarvestMeasurementStore.activeLeafScanHysteresis = health.status
                        hsv to health
                    }
                    assessment = result.second
                    HarvestMeasurementStore.lastLeaf = result.first
                    HarvestMeasurementStore.lastLeafHealth = result.second
                    HarvestMeasurementStore.lastLeafScanId = UUID.randomUUID().toString()
                } finally {
                    analyzing = false
                }
            }
        },
        overlay = assessment?.takeIf { captured && !analyzing }?.let { sample ->
            {
                MockArOverlayCard(
                    heading = stringResource(R.string.harvest_plant_health_title),
                    status = sample.simpleHealthStatusLabel,
                    lineOne = sample.liveCardIssueLine?.let { issue ->
                        if (sample.status == PlantHealthStatus.UNHEALTHY) {
                            stringResource(R.string.harvest_overlay_detected_issue, issue)
                        } else {
                            issue
                        }
                    },
                    lineTwo = sample.liveCardDiseaseLine?.let { disease ->
                        stringResource(R.string.harvest_overlay_possible_disease, disease)
                    },
                    statusColor = plantHealthStatusColor(sample.status, sample.scanRequired),
                    showDemoChip = false,
                    onViewDetails = { onViewDetails("live") },
                    onDetectedIssueClick = if (HarvestDecisionMakingBridge.canOpenFrom(sample)) {
                        {
                            openDecisionMakingFromLeaf(
                                assessment = sample,
                                cropType = harvestState.cropType,
                                onOpenDecisionMaking = onOpenDecisionMaking
                            )
                        }
                    } else {
                        null
                    }
                )
            }
        },
        testTag = "harvest_leaf_scan"
    )
}

internal fun openDecisionMakingFromLeaf(
    assessment: PlantHealthAssessment,
    cropType: String?,
    onOpenDecisionMaking: () -> Unit
) {
    val action = HarvestDecisionMakingBridge.commitFromLeaf(
        assessment = assessment,
        cropType = cropType,
        scanResultId = HarvestMeasurementStore.lastLeafScanId
    )
    if (action is HarvestDecisionMakingAction.Open) {
        onOpenDecisionMaking()
    }
}
