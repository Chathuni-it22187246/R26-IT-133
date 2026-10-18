package com.greenhands.app.harvest.ui

import com.greenhands.app.harvest.detection.ClusterScanCaptureController
import com.greenhands.app.harvest.detection.HarvestScanPhase
import com.greenhands.app.harvest.detection.HybridTargetValidator
import com.greenhands.app.harvest.detection.ScanTargetType
import com.greenhands.app.harvest.detection.TargetAutoCaptureController
import com.greenhands.app.harvest.detection.TargetCaptureTick
import com.greenhands.app.harvest.detection.TargetDetection
import com.greenhands.app.harvest.detection.TargetDetector
import com.greenhands.app.harvest.domain.HarvestArgbFrame
import java.util.concurrent.atomic.AtomicBoolean

class HarvestTargetCameraSession(
    val expected: ScanTargetType,
    val hybrid: HybridTargetValidator,
    val experimentalDetector: TargetDetector? = null,
    val controller: TargetAutoCaptureController,
    val analyzing: AtomicBoolean,
    val onTick: (TargetCaptureTick, HarvestArgbFrame) -> Unit,
    val onAutoCapture: (HarvestArgbFrame, HarvestArgbFrame, TargetDetection?) -> Unit,
    val clusterMode: AtomicBoolean = AtomicBoolean(false),
    val clusterController: ClusterScanCaptureController = ClusterScanCaptureController(),
    val onClusterTick: (List<TargetDetection>, HarvestArgbFrame, HarvestScanPhase) -> Unit = { _, _, _ -> },
    val onClusterAutoCapture: (HarvestArgbFrame, List<TargetDetection>) -> Unit = { _, _ -> }
)
