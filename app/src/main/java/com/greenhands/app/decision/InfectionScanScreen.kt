package com.greenhands.app.decision

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Paint
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

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
    var targetInfection by remember { mutableStateOf<DetectedInfection?>(null) }
    var decision by remember { mutableStateOf<InfectionDecisionResponse?>(null) }
    var loadingDecision by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val busy = remember { AtomicBoolean(false) }
    val lastInferAt = remember { AtomicLong(0L) }
    val leafHits = remember { AtomicInteger(0) }
    val leafMisses = remember { AtomicInteger(0) }
    val confirmedLeaf = remember { AtomicBoolean(false) }
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }

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
        }
    }

    LaunchedEffect(stableLabel, crop, targetFound) {
        if (!targetFound) return@LaunchedEffect
        val label = stableLabel ?: return@LaunchedEffect
        loadingDecision = true
        delay(350)
        if (!targetFound) {
            loadingDecision = false
            return@LaunchedEffect
        }
        decision = fetchInfectionDecision(api, catalog, crop, label)
        loadingDecision = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        CameraPreview(
            modifier = Modifier.fillMaxSize(),
            onFrame = { bitmap ->
                val now = System.currentTimeMillis()
                if (now - lastInferAt.get() < 280L || !busy.compareAndSet(false, true)) {
                    bitmap.recycle()
                    return@CameraPreview
                }
                lastInferAt.set(now)
                try {
                    val result = detector.analyze(bitmap)
                    val confirmed = if (result.target.targetFound) {
                        leafMisses.set(0)
                        leafHits.incrementAndGet() >= 2
                    } else {
                        leafHits.set(0)
                        leafMisses.incrementAndGet() < 3 && confirmedLeaf.get()
                    }
                    confirmedLeaf.set(confirmed)
                    val primary = if (confirmed) {
                        selectSingleTarget(result.infections, updateRecord?.infectionName)
                    } else {
                        null
                    }
                    mainExecutor.execute {
                        targetFound = confirmed
                        targetKind = if (confirmed) result.target.kind else PlantTargetKind.None
                        targetInfection = primary
                        if (!confirmed) {
                            decision = null
                            loadingDecision = false
                        }
                    }
                } finally {
                    if (!bitmap.isRecycled) bitmap.recycle()
                    busy.set(false)
                }
            }
        )

        if (targetFound && targetInfection != null) {
            InfectionOverlay(detection = targetInfection)
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
                modifier = Modifier.fillMaxWidth()
            )
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
    onFrame: (Bitmap) -> Unit,
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
            }
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener(
                {
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also { useCase ->
                        useCase.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val analysis = ImageAnalysis.Builder()
                        .setTargetResolution(Size(640, 480))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .build()
                    analysis.setAnalyzer(analysisExecutor) { image ->
                        val bitmap = image.toAnalysisBitmap()
                        image.close()
                        if (bitmap != null) onFrame(bitmap)
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
private fun InfectionOverlay(detection: DetectedInfection?) {
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
        val left = isolated.box.left * size.width
        val top = isolated.box.top * size.height
        val width = isolated.box.width() * size.width
        val height = isolated.box.height() * size.height
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
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp)
            .heightIn(min = 96.dp, max = 220.dp)
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
                        decision != null -> decision.infectionShortName
                        else -> if (updateMode) "Re-scan the same plant" else "${targetKind.name} confirmed — scanning infections"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (targetFound && target != null) {
                Text(
                    "Highest-priority infection on $crop ${targetKind.name.lowercase()} · ${(target.score * 100).toInt()}% confidence",
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
                    SeverityChip(decision.severityLevel)
                    Spacer(modifier = Modifier.height(8.dp))
                    GuideLine("Symptoms", decision.visibleSymptoms)
                    GuideLine("Treatment", decision.treatmentDescription)
                    GuideLine("Biological", decision.biologicalControl)
                    GuideLine("Chemical", decision.chemicalControl)
                    GuideLine("Prevention", decision.preventionSteps)
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
private fun SeverityChip(severity: String) {
    val color = when (severity.lowercase()) {
        "critical" -> Color(0xFFE53935)
        "high" -> Color(0xFFFF9800)
        "medium" -> Color(0xFFFBC02D)
        else -> ForestEmerald
    }
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

@Composable
private fun GuideLine(title: String, body: String) {
    if (body.isBlank()) return
    Text(title, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
    Text(
        body,
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

private suspend fun fetchInfectionDecision(
    api: DecisionApiService,
    catalog: List<InfectionRecord>,
    crop: String,
    label: String
): InfectionDecisionResponse {
    return try {
        withContext(Dispatchers.IO) {
            api.getInfectionDecision(
                InfectionDecisionRequest(
                    query = label,
                    infectionName = label,
                    plantType = crop
                )
            )
        }
    } catch (_: Exception) {
        InfectionCatalog.match(
            records = catalog,
            query = label,
            infectionName = label,
            plantType = crop
        )?.toDecisionResponse() ?: InfectionDecisionResponse(
            plantType = crop,
            infectionShortName = label,
            infectionFullName = label,
            severityLevel = "Medium",
            visibleSymptoms = "Visual lesion pattern consistent with $label.",
            treatmentDescription = "Isolate the plant and confirm the match in the infection dataset.",
            biologicalControl = "Apply labeled biologicals for $label.",
            chemicalControl = "Use a labeled chemical only after confirmation.",
            preventionSteps = "Improve airflow and reduce leaf wetness."
        )
    }
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
