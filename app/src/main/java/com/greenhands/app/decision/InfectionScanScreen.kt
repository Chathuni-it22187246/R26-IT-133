package com.greenhands.app.decision

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.camera.view.transform.CoordinateTransform
import androidx.camera.view.transform.ImageProxyTransformFactory
import androidx.camera.view.transform.OutputTransform
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as ComposeSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.greenhands.app.ui.theme.ForestEmerald
import com.greenhands.app.ui.theme.NightElevated
import com.greenhands.app.ui.theme.NightText
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

private const val SCAN_LOG_TAG = "InfectionScan"

@Composable
fun InfectionScanScreen(
    crop: String,
    updateRecordId: String? = null,
    onBack: () -> Unit,
    onSaved: () -> Unit = onBack,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("infection_scan_screen")
    ) {
        if (!hasCameraPermission) {
            CameraPermissionGate(
                onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                onBack = onBack,
                body = "Allow camera access to scan plant leaves or fruit and isolate the top infection."
            )
        } else {
            LiveInfectionScanner(
                crop = crop,
                updateRecordId = updateRecordId,
                onBack = onBack,
                onSaved = onSaved
            )
        }
    }
}

@Composable
private fun LiveInfectionScanner(
    crop: String,
    updateRecordId: String?,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val detector = remember { InfectionDetector(context) }
    val catalog = remember { InfectionCatalog.loadFromAssets(context) }
    val api = remember { DecisionApiService.create() }
    val tracker = remember { InfectionRecordRepository.get(context) }
    val scope = rememberCoroutineScope()
    val updateRecord = remember(updateRecordId) {
        updateRecordId?.takeIf { it.isNotBlank() && it != "_" }?.let { tracker.record(it) }
    }
    var targetFound by remember { mutableStateOf(false) }
    var targetKind by remember { mutableStateOf(PlantTargetKind.None) }
    var targetBox by remember { mutableStateOf(RectF()) }
    var lesionBox by remember { mutableStateOf(RectF()) }
    var targetInfection by remember { mutableStateOf<DetectedInfection?>(null) }
    var decision by remember { mutableStateOf<InfectionDecisionResponse?>(null) }
    var loadingDecision by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var resultLocked by remember { mutableStateOf(false) }
    val busy = remember { AtomicBoolean(false) }
    val scanActive = remember { AtomicBoolean(true) }
    val scanGeneration = remember { AtomicInteger(0) }
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }

    fun restartScan() {
        scanGeneration.incrementAndGet()
        scanActive.set(false)
        detector.reset()
        targetFound = false
        targetKind = PlantTargetKind.None
        targetBox = RectF()
        lesionBox = RectF()
        targetInfection = null
        decision = null
        loadingDecision = false
        resultLocked = false
        scanActive.set(true)
    }

    DisposableEffect(Unit) {
        onDispose { detector.close() }
    }

    val stableLabel = remember(targetFound, targetInfection) {
        if (targetFound) targetInfection?.label else null
    }

    LaunchedEffect(targetFound) {
        if (!targetFound) {
            targetInfection = null
            decision = null
            loadingDecision = false
            targetKind = PlantTargetKind.None
            targetBox = RectF()
            lesionBox = RectF()
        }
    }

    LaunchedEffect(stableLabel, crop, targetFound) {
        if (!targetFound) return@LaunchedEffect
        val label = stableLabel ?: return@LaunchedEffect
        decision = decisionForScan(catalog, crop, label)
        loadingDecision = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        CameraPreview(
            modifier = Modifier.fillMaxSize(),
            scanning = scanActive,
            onFrame = { bitmap, mapBox ->
                if (!scanActive.get() || !busy.compareAndSet(false, true)) {
                    bitmap.recycle()
                    return@CameraPreview
                }
                try {
                    if (!scanActive.get()) return@CameraPreview
                    val result = detector.analyze(bitmap)
                    if (!scanActive.get()) return@CameraPreview
                    val confirmed = result.target.targetFound
                    val primary = if (confirmed) {
                        selectSingleTarget(result.infections, updateRecord?.infectionName)
                    } else {
                        null
                    }
                    val locked = if (confirmed) primary else null
                    val normalizedTarget = RectF(result.target.box)
                    val normalizedLesion = if (primary != null) RectF(primary.box) else RectF()
                    val ticket = scanGeneration.get()
                    if (locked != null) {
                        scanActive.set(false)
                        val label = locked.label
                        val kind = result.target.kind
                        mainExecutor.execute {
                            if (scanGeneration.get() != ticket) return@execute
                            val objectBox = finiteRect(mapBox(normalizedTarget))
                            val mappedLesion = finiteRect(mapBox(normalizedLesion))
                            targetFound = true
                            targetKind = kind
                            targetBox = objectBox
                            lesionBox = mappedLesion
                            targetInfection = locked
                            decision = decisionForScan(catalog, crop, label)
                            loadingDecision = false
                            val showsMarker = objectBox.width() > 0f && objectBox.height() > 0f ||
                                mappedLesion.width() > 0f && mappedLesion.height() > 0f
                            if (!showsMarker) {
                                resultLocked = true
                            } else {
                                resultLocked = false
                                val lockTicket = scanGeneration.incrementAndGet()
                                scope.launch {
                                    withFrameNanos { }
                                    withFrameNanos { }
                                    if (scanGeneration.get() == lockTicket) resultLocked = true
                                }
                            }
                        }
                    } else {
                        mainExecutor.execute {
                            if (scanGeneration.get() != ticket || !scanActive.get()) return@execute
                            val objectBox = if (confirmed) finiteRect(mapBox(normalizedTarget)) else RectF()
                            val mappedLesion = if (primary != null) finiteRect(mapBox(normalizedLesion)) else RectF()
                            targetFound = confirmed
                            targetKind = if (confirmed) result.target.kind else PlantTargetKind.None
                            targetBox = objectBox
                            lesionBox = mappedLesion
                            targetInfection = primary
                            if (!confirmed) {
                                decision = null
                                loadingDecision = false
                            }
                        }
                    }
                } finally {
                    if (!bitmap.isRecycled) bitmap.recycle()
                    busy.set(false)
                }
            }
        )

        if (targetFound && !resultLocked && targetBox.width() > 0f && targetBox.height() > 0f) {
            TargetBoundsOverlay(box = targetBox)
        }
        val lesion = targetInfection
        if (targetFound && lesion != null && !resultLocked && lesionBox.width() > 0f && lesionBox.height() > 0f) {
            InfectionOverlay(detection = lesion, box = lesionBox)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(NightElevated.copy(alpha = 0.82f))
                        .testTag("infection_scan_back")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = NightText)
                }
                Text(
                    "Advanced Infection Checkup",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp)
                )
            }
            PlantTargetBanner(kind = if (targetFound) targetKind else PlantTargetKind.None)
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            LiveDecisionSheet(
                crop = crop,
                targetFound = targetFound,
                targetKind = targetKind,
                target = targetInfection,
                decision = decision,
                loading = loadingDecision,
                updateMode = updateRecord != null,
                locked = resultLocked,
                modifier = Modifier.fillMaxWidth()
            )
            if (resultLocked) {
                Button(
                    onClick = { restartScan() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 8.dp)
                        .height(52.dp)
                        .testTag("infection_rescan_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ForestEmerald)
                ) {
                    Text(
                        "Re-scan",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
            if (targetFound && targetInfection != null && decision != null) {
                val detection = targetInfection
                Button(
                    onClick = {
                        val scan = detection ?: return@Button
                        val guide = decision ?: return@Button
                        saving = true
                        scope.launch {
                            try {
                                if (updateRecord != null) {
                                    tracker.appendScanUpdate(updateRecord.id, guide, scan)
                                } else {
                                    tracker.addFromScan(crop, guide, scan, targetKind)
                                    try {
                                        api.getAiDecision(
                                            PlantRequest(
                                                crop = crop,
                                                stage = "Scan",
                                                disease = guide.infectionShortName
                                            )
                                        )
                                    } catch (_: Exception) {
                                        // Local record is enough if the log append is offline.
                                    }
                                }
                                onSaved()
                            } finally {
                                saving = false
                            }
                        }
                    },
                    enabled = !saving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                        .height(52.dp)
                        .testTag(if (updateRecord != null) "save_infection_update" else "add_infection_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ForestEmerald)
                ) {
                    Text(
                        if (saving) {
                            "Saving…"
                        } else if (updateRecord != null) {
                            "Save Update"
                        } else {
                            "Add Infection"
                        },
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}

private fun selectSingleTarget(
    detections: List<DetectedInfection>,
    preferredName: String?
): DetectedInfection? {
    if (detections.isEmpty()) return null
    val preferred = preferredName?.takeIf { it.isNotBlank() }
        ?.let { name ->
            detections.filter { it.label.equals(name, ignoreCase = true) }
        }
        .orEmpty()
    return InfectionPriority.pickHighestPriority(preferred.ifEmpty { detections })
}

@Composable
private fun PlantTargetBanner(kind: PlantTargetKind) {
    val found = kind != PlantTargetKind.None
    val background = if (found) Color(0xFF2E7D32) else Color(0xFFC62828)
    val message = when (kind) {
        PlantTargetKind.Leaf -> "Leaf Detected"
        PlantTargetKind.Fruit -> "Fruit Detected"
        PlantTargetKind.None -> "Leaf or Fruit Not Found - Point camera at a plant"
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .testTag(if (found) "plant_target_detected_banner" else "plant_target_missing_banner"),
        color = background,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (found) Icons.Default.CheckCircle else Icons.Default.Cancel,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = message,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun CameraPreview(
    onFrame: (Bitmap, (RectF) -> RectF) -> Unit,
    scanning: AtomicBoolean,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose { analysisExecutor.shutdown() }
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            val previewView = PreviewView(context).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener(
                {
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also { useCase ->
                        useCase.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val analysis = ImageAnalysis.Builder()
                        .setTargetResolution(Size(480, 360))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .build()
                    analysis.setAnalyzer(analysisExecutor) { image ->
                        var bitmap: Bitmap? = null
                        try {
                            if (!scanning.get()) return@setAnalyzer
                            val rotationDegrees = image.imageInfo.rotationDegrees
                            val analysisTransform = runCatching {
                                ImageProxyTransformFactory().apply {
                                    setUsingRotationDegrees(true)
                                }.getOutputTransform(image)
                            }.getOrNull()
                            bitmap = image.toAnalysisBitmap()
                            val frame = bitmap ?: return@setAnalyzer
                            val sourceWidth = frame.width
                            val sourceHeight = frame.height
                            onFrame(frame) { normalized ->
                                mapDetectionToPreview(
                                    normalized,
                                    sourceWidth,
                                    sourceHeight,
                                    rotationDegrees,
                                    analysisTransform,
                                    previewView
                                )
                            }
                        } catch (error: IllegalStateException) {
                            Log.e(SCAN_LOG_TAG, "Skipped camera frame", error)
                        } catch (error: IllegalArgumentException) {
                            Log.e(SCAN_LOG_TAG, "Skipped camera frame", error)
                        } finally {
                            image.close()
                            val leftover = bitmap
                            if (leftover != null && !leftover.isRecycled) leftover.recycle()
                        }
                    }
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis
                    )
                },
                ContextCompat.getMainExecutor(context)
            )
            previewView
        }
    )
}

@Composable
private fun TargetBoundsOverlay(box: RectF) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .testTag("plant_target_bbox")
    ) {
        val drawn = clipRectToCanvas(box, size.width, size.height) ?: return@Canvas
        drawRect(
            color = Color(0xFF76FF03),
            topLeft = Offset(drawn.left, drawn.top),
            size = ComposeSize(drawn.width(), drawn.height()),
            style = Stroke(width = 4.dp.toPx())
        )
    }
}

private fun mapDetectionToPreview(
    normalized: RectF,
    sourceWidth: Int,
    sourceHeight: Int,
    rotationDegrees: Int,
    analysisTransform: OutputTransform?,
    previewView: PreviewView
): RectF {
    if (normalized.width() <= 0f || normalized.height() <= 0f || sourceWidth <= 0 || sourceHeight <= 0) {
        return RectF()
    }
    val viewWidth = previewView.width.toFloat()
    val viewHeight = previewView.height.toFloat()
    val previewTransform = runCatching { previewView.outputTransform }.getOrNull()
    if (analysisTransform != null && previewTransform != null) {
        val buffer = bufferNormRect(
            normalized.left,
            normalized.top,
            normalized.right,
            normalized.bottom,
            rotationDegrees
        )
        val rect = RectF(buffer[0], buffer[1], buffer[2], buffer[3])
        val mapped = runCatching {
            CoordinateTransform(analysisTransform, previewTransform).mapRect(rect)
            rect
        }.getOrNull()
        val finite = mapped?.let(::finiteRect)
        if (finite != null && finite.width() > 1f && finite.height() > 1f) return finite
    }
    return finiteRect(
        mapAnalysisBoxToPreview(
            normalized,
            sourceWidth.toFloat(),
            sourceHeight.toFloat(),
            viewWidth,
            viewHeight
        )
    )
}

internal fun uprightUnitToBufferUnit(u: Float, v: Float, rotationDegrees: Int): Pair<Float, Float> {
    val turns = ((rotationDegrees % 360) + 360) % 360
    return when (turns) {
        90 -> v to (1f - u)
        180 -> (1f - u) to (1f - v)
        270 -> (1f - v) to u
        else -> u to v
    }
}

internal fun bufferNormRect(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    rotationDegrees: Int
): FloatArray {
    val corners = arrayOf(
        uprightUnitToBufferUnit(left, top, rotationDegrees),
        uprightUnitToBufferUnit(right, top, rotationDegrees),
        uprightUnitToBufferUnit(left, bottom, rotationDegrees),
        uprightUnitToBufferUnit(right, bottom, rotationDegrees)
    )
    var minX = Float.POSITIVE_INFINITY
    var minY = Float.POSITIVE_INFINITY
    var maxX = Float.NEGATIVE_INFINITY
    var maxY = Float.NEGATIVE_INFINITY
    for ((x, y) in corners) {
        if (x < minX) minX = x
        if (y < minY) minY = y
        if (x > maxX) maxX = x
        if (y > maxY) maxY = y
    }
    return floatArrayOf(minX, minY, maxX, maxY)
}

private fun finiteRect(box: RectF): RectF {
    val left = box.left
    val top = box.top
    val right = box.right
    val bottom = box.bottom
    if (!left.isFinite() || !top.isFinite() || !right.isFinite() || !bottom.isFinite()) return RectF()
    val orderedLeft = kotlin.math.min(left, right)
    val orderedRight = kotlin.math.max(left, right)
    val orderedTop = kotlin.math.min(top, bottom)
    val orderedBottom = kotlin.math.max(top, bottom)
    if (orderedRight - orderedLeft < 1f || orderedBottom - orderedTop < 1f) return RectF()
    return RectF(orderedLeft, orderedTop, orderedRight, orderedBottom)
}

private fun clipRectToCanvas(box: RectF, canvasWidth: Float, canvasHeight: Float): RectF? {
    if (canvasWidth <= 0f || canvasHeight <= 0f) return null
    val finite = finiteRect(box)
    if (finite.width() <= 0f || finite.height() <= 0f) return null
    val left = finite.left.coerceIn(0f, canvasWidth)
    val top = finite.top.coerceIn(0f, canvasHeight)
    val right = finite.right.coerceIn(0f, canvasWidth)
    val bottom = finite.bottom.coerceIn(0f, canvasHeight)
    if (right - left < 1f || bottom - top < 1f) return null
    return RectF(left, top, right, bottom)
}

internal fun mapAnalysisBoxToPreview(
    box: RectF,
    imageWidth: Float,
    imageHeight: Float,
    viewWidth: Float,
    viewHeight: Float
): RectF {
    if (imageWidth <= 0f || imageHeight <= 0f || viewWidth <= 0f || viewHeight <= 0f) {
        return RectF(
            box.left * viewWidth,
            box.top * viewHeight,
            box.right * viewWidth,
            box.bottom * viewHeight
        )
    }
    val scale = kotlin.math.max(viewWidth / imageWidth, viewHeight / imageHeight)
    val displayedWidth = imageWidth * scale
    val displayedHeight = imageHeight * scale
    val offsetX = (viewWidth - displayedWidth) / 2f
    val offsetY = (viewHeight - displayedHeight) / 2f
    return RectF(
        offsetX + box.left * displayedWidth,
        offsetY + box.top * displayedHeight,
        offsetX + box.right * displayedWidth,
        offsetY + box.bottom * displayedHeight
    )
}

@Composable
private fun InfectionOverlay(
    detection: DetectedInfection?,
    box: RectF
) {
    val isolated = detection ?: return
    val textPaint = remember {
        Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.BLACK
            textSize = 34f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
    }
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .testTag("infection_bbox_overlay")
    ) {
        val drawn = clipRectToCanvas(box, size.width, size.height) ?: return@Canvas
        val left = drawn.left
        val top = drawn.top
        val width = drawn.width()
        val height = drawn.height()
        drawRect(
            color = Color(0xFF76FF03),
            topLeft = Offset(left, top),
            size = ComposeSize(width, height),
            style = Stroke(width = 4.dp.toPx())
        )
        val label = "${isolated.label} ${(isolated.score * 100).toInt()}%"
        val labelWidth = textPaint.measureText(label) + 20f
        drawRect(
            color = Color(0xCC76FF03),
            topLeft = Offset(left, (top - 42f).coerceAtLeast(0f)),
            size = ComposeSize(labelWidth, 40f)
        )
        drawContext.canvas.nativeCanvas.drawText(
            label,
            left + 10f,
            (top - 12f).coerceAtLeast(28f),
            textPaint
        )
    }
}

@Composable
private fun LiveDecisionSheet(
    crop: String,
    targetFound: Boolean,
    targetKind: PlantTargetKind,
    target: DetectedInfection?,
    decision: InfectionDecisionResponse?,
    loading: Boolean,
    updateMode: Boolean,
    locked: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp)
            .heightIn(min = 96.dp, max = if (locked && decision != null) 440.dp else 220.dp)
            .testTag("infection_live_sheet"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.DocumentScanner,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    when {
                        !targetFound -> "Waiting for a leaf or fruit"
                        locked && target != null && target.label.equals("Healthy", ignoreCase = true) -> "Healthy"
                        locked && target != null -> "Unhealthy"
                        decision != null -> decision.infectionShortName
                        else -> if (updateMode) "Re-scan the same plant" else "${targetKind.name} confirmed — scanning infections"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (targetFound && target != null) {
                val confidence = "${(target.score * 100).toInt()}% confidence"
                val healthy = target.label.equals("Healthy", ignoreCase = true)
                Text(
                    when {
                        locked && healthy -> "${targetKind.name} · $confidence"
                        locked -> "${target.label} · $confidence"
                        else -> "Highest-priority infection on $crop ${targetKind.name.lowercase()} · $confidence"
                    },
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            when {
                !targetFound -> {
                    Text(
                        "AI decisions stay off until a valid leaf or fruit is in view. Background objects, hands, and greenhouse equipment are ignored.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
                loading && decision == null -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp
                        )
                    }
                }
                decision != null -> {
                    InfectionGuide(decision = decision, confidence = target?.score)
                }
                else -> {
                    Text(
                        "Live TFLite scan is watching for powdery, blight, mosaic, and bacterial lesions. Matching treatment loads from the 500-row infection dataset as soon as a spot is found.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun InfectionGuide(
    decision: InfectionDecisionResponse,
    confidence: Float?
) {
    val severity = decision.severityLevel.ifBlank { "Low" }
    val accent = severityColor(severity)
    if (decision.infectionFullName.isNotBlank() &&
        !decision.infectionFullName.equals(decision.infectionShortName, ignoreCase = true)
    ) {
        Text(
            decision.infectionFullName,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
    SeverityChip(severity)
    Spacer(modifier = Modifier.height(8.dp))
    SeverityScale(level = decision.severityLevel)
    if (confidence != null) {
        Spacer(modifier = Modifier.height(8.dp))
        ConfidenceBar(score = confidence)
    }
    val hasGuide = listOf(
        decision.visibleSymptoms,
        decision.treatmentDescription,
        decision.biologicalControl,
        decision.chemicalControl,
        decision.preventionSteps
    ).any { it.isNotBlank() }
    if (!hasGuide) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "No matching guide is in the infection dataset for this result.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp
        )
        return
    }
    Spacer(modifier = Modifier.height(10.dp))
    GuideSection("Visible symptoms", decision.visibleSymptoms, accent)
    GuideSection("Treatment", decision.treatmentDescription, accent)
    GuideSection("Biological control", decision.biologicalControl, accent)
    GuideSection("Chemical control", decision.chemicalControl, accent)
    GuideSection("Prevention", decision.preventionSteps, accent)
}

@Composable
private fun SeverityScale(level: String) {
    if (level.isBlank()) return
    val steps = listOf("Low", "Medium", "High", "Critical")
    val active = steps.indexOfFirst { it.equals(level, ignoreCase = true) }
    if (active < 0) return
    val color = severityColor(level)
    Text(
        "Severity",
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurface
    )
    Spacer(modifier = Modifier.height(4.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        steps.forEachIndexed { index, _ ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (index <= active) color else color.copy(alpha = 0.16f))
            )
        }
    }
}

@Composable
private fun ConfidenceBar(score: Float) {
    val fraction = score.coerceIn(0f, 1f)
    val color = MaterialTheme.colorScheme.primary
    Text(
        "Model confidence ${(fraction * 100).toInt()}%",
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurface
    )
    Spacer(modifier = Modifier.height(4.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.16f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction)
                .background(color)
        )
    }
}

@Composable
private fun GuideSection(title: String, body: String, accent: Color) {
    if (body.isBlank()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(bottom = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .padding(top = 2.dp, bottom = 2.dp)
                .width(3.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(accent)
        )
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(
                title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                body,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SeverityChip(severity: String) {
    if (severity.isBlank()) return
    val color = severityColor(severity)
    Surface(color = color.copy(alpha = 0.16f), shape = RoundedCornerShape(8.dp)) {
        Text(
            "Severity: $severity",
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

private fun severityColor(severity: String): Color = when (severity.lowercase()) {
    "critical" -> Color(0xFFE53935)
    "high" -> Color(0xFFFF9800)
    "medium" -> Color(0xFFFBC02D)
    else -> ForestEmerald
}

private fun decisionForScan(
    catalog: List<InfectionRecord>,
    crop: String,
    label: String
): InfectionDecisionResponse {
    if (label.equals("Healthy", ignoreCase = true)) {
        return InfectionDecisionResponse(
            plantType = crop,
            infectionShortName = "Healthy",
            infectionFullName = "Healthy leaf or fruit",
            severityLevel = "Low",
            visibleSymptoms = "No supported infection pattern was confirmed on this leaf or fruit.",
            treatmentDescription = "No treatment is indicated from this scan.",
            biologicalControl = "",
            chemicalControl = "",
            preventionSteps = "Keep the leaf or fruit in steady light and scan again if symptoms appear."
        )
    }
    val named = catalog.filter { it.infectionShortName.equals(label, ignoreCase = true) }
    val record = named.firstOrNull { it.plantType.equals(crop, ignoreCase = true) } ?: named.firstOrNull()
    return record?.toDecisionResponse() ?: InfectionDecisionResponse(
        plantType = crop,
        infectionShortName = label,
        infectionFullName = label
    )
}

private fun ImageProxy.toAnalysisBitmap(): Bitmap? {
    return try {
        val plane = planes[0]
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val rowPadding = rowStride - pixelStride * width
        val raw = Bitmap.createBitmap(
            width + rowPadding / pixelStride,
            height,
            Bitmap.Config.ARGB_8888
        )
        raw.copyPixelsFromBuffer(plane.buffer)
        val cropped = if (raw.width != width) {
            Bitmap.createBitmap(raw, 0, 0, width, height).also { raw.recycle() }
        } else {
            raw
        }
        if (imageInfo.rotationDegrees == 0) {
            cropped
        } else {
            val matrix = Matrix().apply { postRotate(imageInfo.rotationDegrees.toFloat()) }
            Bitmap.createBitmap(cropped, 0, 0, cropped.width, cropped.height, matrix, true).also {
                if (it != cropped) cropped.recycle()
            }
        }
    } catch (_: Exception) {
        null
    }
}
