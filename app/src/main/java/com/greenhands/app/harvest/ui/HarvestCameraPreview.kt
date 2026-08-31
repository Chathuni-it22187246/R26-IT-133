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
import com.greenhands.app.harvest.detection.HybridScanConfig
import com.greenhands.app.harvest.domain.HarvestFrameBuffer
import com.greenhands.app.ui.theme.NightBg
import com.greenhands.app.ui.theme.NightText
import com.greenhands.app.ui.theme.Spacing
import java.util.concurrent.Executors

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
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val analysisExecutor = Executors.newSingleThreadExecutor()
        var cameraProvider: ProcessCameraProvider? = null
        var imageAnalysis: ImageAnalysis? = null
        var camera: Camera? = null
        var disposed = false

        cameraProviderFuture.addListener({
            if (disposed) return@addListener
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
                                    frameBuffer.updateFromRgbaImage(
                                        proxy,
                                        HarvestFrameBuffer.ANALYSIS_MAX_SIDE
                                    )
                                    val session = targetSession
                                    val frame = frameBuffer.copy()
                                    if (session != null && frame != null) {
                                        val now = System.currentTimeMillis()
                                        val experimental = session.experimentalDetector
                                        val tick = if (
                                            HybridScanConfig.USE_EXPERIMENTAL_TFLITE_DETECTOR &&
                                            experimental != null &&
                                            experimental.isModelReady
                                        ) {
                                            session.controller.onFrame(
                                                frame = frame,
                                                detections = experimental.detect(frame, now),
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
                                        mainExecutor.execute {
                                            if (disposed) return@execute
                                            if (tick.shouldRequestFocus) {
                                                tick.detection?.boundingBox?.let { box ->
                                                    focusRequester.request(camera, previewView, box)
                                                }
                                            }
                                            session.onTick(tick, frame)
                                            if (crop != null) {
                                                session.onAutoCapture(crop)
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
            disposed = true
            try {
                imageAnalysis?.clearAnalyzer()
                cameraProvider?.unbindAll()
            } catch (t: Throwable) {
                Log.w(TAG, "Camera unbind failed", t)
            }
            analysisExecutor.shutdown()
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
