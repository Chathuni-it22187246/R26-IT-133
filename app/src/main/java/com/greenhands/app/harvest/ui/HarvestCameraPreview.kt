package com.greenhands.app.harvest.ui

import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.greenhands.app.R
import com.greenhands.app.harvest.detection.HarvestFruitStabilityDiag
import com.greenhands.app.harvest.detection.HarvestFruitStabilitySnapshot
import com.greenhands.app.harvest.detection.HarvestScanDiag
import com.greenhands.app.harvest.detection.HarvestScanLiveSample
import com.greenhands.app.harvest.detection.HarvestYoloDetectTiming
import com.greenhands.app.harvest.detection.HybridScanConfig
import com.greenhands.app.harvest.detection.MultiTomatoFruitSelector
import com.greenhands.app.harvest.detection.PreviewFillCenterMapper
import com.greenhands.app.harvest.detection.ScanTargetType
import com.greenhands.app.harvest.detection.SingleTomatoCandidateDiag
import com.greenhands.app.harvest.detection.SingleTomatoCandidateSnapshot
import com.greenhands.app.harvest.detection.TargetDetection
import com.greenhands.app.harvest.detection.TargetDetectionCalibration
import com.greenhands.app.harvest.detection.TargetRejectReason
import com.greenhands.app.harvest.domain.HarvestFrameBuffer
import com.greenhands.app.ui.theme.NightBg
import com.greenhands.app.ui.theme.NightText
import com.greenhands.app.ui.theme.Spacing
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "HarvestCamera"

@Composable
fun HarvestCameraPreview(
    frameBuffer: HarvestFrameBuffer,
    modifier: Modifier = Modifier,
    targetSession: HarvestTargetCameraSession? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    var bindFailed by remember { mutableStateOf(false) }
    val focusRequester = remember { TargetFocusRequester() }

    DisposableEffect(lifecycleOwner, frameBuffer, targetSession) {
        HarvestScanDiag.resetLiveSession()
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val analysisExecutor = Executors.newSingleThreadExecutor()
        var cameraProvider: ProcessCameraProvider? = null
        var imageAnalysis: ImageAnalysis? = null
        var camera: Camera? = null
        val disposed = AtomicBoolean(false)

        cameraProviderFuture.addListener({
            if (disposed.get()) return@addListener
            try {
                val provider = cameraProviderFuture.get()
                cameraProvider = provider
                val rotation = previewView.display?.rotation ?: Surface.ROTATION_0
                val preview = Preview.Builder()
                    .setTargetRotation(rotation)
                    .build()
                    .also { useCase ->
                        useCase.setSurfaceProvider(previewView.surfaceProvider)
                    }
                val analysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(640, 480))
                    .setTargetRotation(rotation)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                    .also { useCase ->
                        useCase.setAnalyzer(analysisExecutor) { imageProxy ->
                            imageProxy.use { proxy ->
                                try {
                                    if (disposed.get()) return@use
                                    val analysisStartNs = System.nanoTime()
                                    val convertStartNs = System.nanoTime()
                                    frameBuffer.updateFromRgbaImage(
                                        proxy,
                                        HarvestFrameBuffer.ANALYSIS_MAX_SIDE
                                    )
                                    val session = targetSession
                                    val frame = frameBuffer.copy()
                                    val convertMs = nsToMs(convertStartNs)
                                    if (session != null && frame != null) {
                                        if (disposed.get()) return@use
                                        val now = System.currentTimeMillis()
                                        val experimental = session.experimentalDetector
                                        val yoloReady =
                                            !disposed.get() &&
                                                HybridScanConfig.USE_EXPERIMENTAL_TFLITE_DETECTOR &&
                                                experimental != null &&
                                                experimental.isModelReady
                                        val cropScan = session.expected == ScanTargetType.TOMATO_FRUIT ||
                                            session.clusterMode.get()
                                        if (session.clusterMode.get()) {
                                            val timed = if (yoloReady && experimental != null) {
                                                experimental.detectTimed(frame, now)
                                            } else {
                                                HarvestYoloDetectTiming.NONE
                                            }
                                            val detections = timed.detections
                                            val selected = MultiTomatoFruitSelector.select(detections)
                                            val clusterTick = session.clusterController.onFrame(
                                                fruits = selected.fruits,
                                                analyzing = session.analyzing.get()
                                            )
                                            val focusBox = clusterTick.fruits.maxByOrNull { it.confidence }
                                                ?.boundingBox
                                            val captureFruits = clusterTick.fruits
                                            if (cropScan) {
                                                logCropLive(
                                                    nowMs = now,
                                                    cluster = true,
                                                    convertMs = convertMs,
                                                    timed = timed,
                                                    totalMs = nsToMs(analysisStartNs).coerceAtLeast(1L),
                                                    detections = detections,
                                                    acceptedFruitCount = selected.fruits.size,
                                                    selected = clusterTick.fruits.maxByOrNull { it.confidence },
                                                    reason = clusterReason(clusterTick.capture, clusterTick.fruits, clusterTick.stabilityStreak),
                                                    streak = clusterTick.stabilityStreak,
                                                    focusRequested = clusterTick.shouldRequestFocus,
                                                    autoCapture = clusterTick.capture
                                                )
                                            }
                                            mainExecutor.execute {
                                                if (disposed.get()) return@execute
                                                if (clusterTick.shouldRequestFocus) {
                                                    focusBox?.let { box ->
                                                        val mapped = PreviewFillCenterMapper.mapToView(
                                                            box = box,
                                                            frameWidth = frame.width,
                                                            frameHeight = frame.height,
                                                            viewWidth = previewView.width,
                                                            viewHeight = previewView.height
                                                        )
                                                        focusRequester.request(camera, previewView, mapped)
                                                    }
                                                }
                                                session.onClusterTick(clusterTick.fruits, frame, clusterTick.phase)
                                                if (clusterTick.capture) {
                                                    session.onClusterAutoCapture(frame, captureFruits)
                                                }
                                            }
                                        } else {
                                            val timed = if (yoloReady && experimental != null) {
                                                experimental.detectTimed(frame, now)
                                            } else {
                                                HarvestYoloDetectTiming.NONE
                                            }
                                            val tick = if (yoloReady && experimental != null) {
                                                session.controller.onFrame(
                                                    frame = frame,
                                                    detections = timed.detections,
                                                    analyzing = session.analyzing.get()
                                                )
                                            } else {
                                                session.controller.onHybridFrame(
                                                    frame = frame,
                                                    result = session.hybrid.validate(
                                                        frame,
                                                        session.expected,
                                                        now
                                                    ),
                                                    analyzing = session.analyzing.get()
                                                )
                                            }
                                            val crop = tick.captureFrame
                                            if (cropScan) {
                                                val rawFruit = if (yoloReady) {
                                                    timed.detections.count {
                                                        it.targetType == ScanTargetType.TOMATO_FRUIT
                                                    }
                                                } else {
                                                    if (tick.detection?.targetType == ScanTargetType.TOMATO_FRUIT) 1 else 0
                                                }
                                                logCropLive(
                                                    nowMs = now,
                                                    cluster = false,
                                                    convertMs = convertMs,
                                                    timed = timed,
                                                    totalMs = nsToMs(analysisStartNs).coerceAtLeast(1L),
                                                    detections = timed.detections,
                                                    acceptedFruitCount = if (tick.validation.readyForManualCapture) 1 else 0,
                                                    selected = tick.detection,
                                                    reason = tick.validation.reason,
                                                    streak = tick.stabilityStreak,
                                                    focusRequested = tick.shouldRequestFocus,
                                                    autoCapture = crop != null,
                                                    rawFruitOverride = rawFruit
                                                )
                                            }
                                            mainExecutor.execute {
                                                if (disposed.get()) return@execute
                                                if (tick.shouldRequestFocus) {
                                                    tick.detection?.boundingBox?.let { box ->
                                                        val mapped = PreviewFillCenterMapper.mapToView(
                                                            box = box,
                                                            frameWidth = frame.width,
                                                            frameHeight = frame.height,
                                                            viewWidth = previewView.width,
                                                            viewHeight = previewView.height
                                                        )
                                                        focusRequester.request(camera, previewView, mapped)
                                                    }
                                                }
                                                session.onTick(tick, frame)
                                                if (crop != null) {
                                                    session.onAutoCapture(crop, frame, tick.detection)
                                                }
                                            }
                                        }
                                    }
                                } catch (t: Throwable) {
                                    Log.w(TAG, "Frame analysis failed", t)
                                }
                            }
                        }
                    }
                imageAnalysis = analysis
                provider.unbindAll()
                camera = provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis
                )
                bindFailed = false
            } catch (t: Throwable) {
                Log.w(TAG, "Camera preview could not start", t)
                bindFailed = true
            }
        }, mainExecutor)

        onDispose {
            disposed.set(true)
            try {
                imageAnalysis?.clearAnalyzer()
                cameraProvider?.unbindAll()
            } catch (t: Throwable) {
                Log.w(TAG, "Camera unbind failed", t)
            }
            analysisExecutor.shutdown()
            try {
                if (!analysisExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                    analysisExecutor.shutdownNow()
                    analysisExecutor.awaitTermination(1, TimeUnit.SECONDS)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Analysis executor shutdown interrupted", t)
                analysisExecutor.shutdownNow()
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NightBg)
    ) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier
                .fillMaxSize()
                .testTag("harvest_camera_preview")
        )
        if (bindFailed) {
            Text(
                text = stringResource(R.string.harvest_camera_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                color = NightText,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(Spacing.xl)
                    .testTag("harvest_camera_unavailable")
            )
        }
    }
}

private fun nsToMs(startNs: Long): Long =
    ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(0L)

private fun clusterReason(
    capture: Boolean,
    fruits: List<TargetDetection>,
    streak: Int
): TargetRejectReason? = when {
    fruits.isEmpty() -> TargetRejectReason.TARGET_NOT_DETECTED
    capture -> null
    streak < TargetDetectionCalibration.REQUIRED_STABLE_FRAMES -> TargetRejectReason.TARGET_NOT_STABLE
    else -> null
}

private fun logCropLive(
    nowMs: Long,
    cluster: Boolean,
    convertMs: Long,
    timed: HarvestYoloDetectTiming,
    totalMs: Long,
    detections: List<TargetDetection>,
    acceptedFruitCount: Int,
    selected: TargetDetection?,
    reason: TargetRejectReason?,
    streak: Int,
    focusRequested: Boolean,
    autoCapture: Boolean,
    rawFruitOverride: Int? = null
) {
    val rawFruit = rawFruitOverride ?: detections.count { it.targetType == ScanTargetType.TOMATO_FRUIT }
    val rawLeaf = detections.count { it.targetType == ScanTargetType.TOMATO_LEAF }
    val fruitStab = if (cluster) {
        HarvestFruitStabilitySnapshot.EMPTY
    } else {
        HarvestFruitStabilityDiag.observe(
            detections = detections,
            selected = selected,
            streak = streak
        )
    }
    val cand = if (cluster) {
        SingleTomatoCandidateSnapshot.EMPTY
    } else {
        SingleTomatoCandidateDiag.observe(detections, selected)
    }
    HarvestScanDiag.maybeLogCropLive(
        nowMs = nowMs,
        sample = HarvestScanLiveSample(
            cluster = cluster,
            convertMs = convertMs,
            preprocessMs = timed.preprocessMs,
            inferMs = timed.inferMs,
            parseMs = timed.parseMs,
            totalMs = totalMs,
            fps = 1000f / totalMs.toFloat(),
            rawFruitCount = rawFruit,
            acceptedFruitCount = acceptedFruitCount,
            selectedConfidence = selected?.confidence,
            selectedArea = selected?.boundingBox?.area,
            reason = reason,
            streak = streak,
            focusRequested = focusRequested,
            autoCapture = autoCapture,
            layoutCached = timed.layoutCached,
            layout = timed.layout,
            layoutFromShape = timed.layoutFromShape,
            rawLeafCount = rawLeaf,
            selectedType = selected?.targetType,
            selectedClassIndex = selected?.classIndex?.takeIf { it >= 0 },
            class0Score = selected?.class0Score,
            class1Score = selected?.class1Score,
            sigmoidApplied = timed.sigmoidApplied,
            rawMax0 = timed.rawMax0,
            rawMax1 = timed.rawMax1,
            fruitSelConf = fruitStab.fruitSelConf,
            fruitMaxConf = fruitStab.fruitMaxConf,
            fruitBox = fruitStab.fruitBox,
            fruitArea = fruitStab.fruitArea,
            fruitDx = fruitStab.fruitDx,
            fruitDy = fruitStab.fruitDy,
            fruitIou = fruitStab.fruitIou,
            stabilityResetReason = if (cluster) null else fruitStab.resetReason,
            gateConf = if (cluster) null else fruitStab.gateConf,
            gateSize = if (cluster) null else fruitStab.gateSize,
            gateCenter = if (cluster) null else fruitStab.gateCenter,
            gateStable = if (cluster) null else fruitStab.gateStable,
            leafMaxConf = if (cluster) null else cand.leafMaxConf,
            nmsDropped = if (cluster) null else timed.nmsDropped,
            preNmsFruit = if (cluster) null else timed.preNmsFruit,
            preNmsLeaf = if (cluster) null else timed.preNmsLeaf,
            selWhy = if (cluster) null else cand.selWhy,
            overlayState = if (cluster) null else cand.overlayState,
            topFruits = if (cluster) null else SingleTomatoCandidateDiag.compactTop(cand.topFruits)
        )
    )
}
