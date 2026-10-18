package com.greenhands.app.decision

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.filament.Engine
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.TrackingFailureReason
import com.greenhands.app.ui.theme.ClimateTeal
import com.greenhands.app.ui.theme.ForestEmerald
import com.greenhands.app.ui.theme.NightElevated
import com.greenhands.app.ui.theme.NightText
import io.github.sceneview.ar.ARScene
import io.github.sceneview.ar.arcore.createAnchorOrNull
import io.github.sceneview.ar.arcore.isValid
import io.github.sceneview.ar.getDescription
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.ar.rememberARCameraNode
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.math.Position
import io.github.sceneview.math.Size
import io.github.sceneview.node.CubeNode
import io.github.sceneview.rememberCollisionSystem
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNodes
import io.github.sceneview.rememberOnGestureListener
import io.github.sceneview.rememberView
import java.util.Locale

@Composable
fun ArGreenhouseScreen(
    widthMeters: Float,
    heightMeters: Float,
    onBack: () -> Unit,
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
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("ar_greenhouse_screen")
    ) {
        if (!hasCameraPermission) {
            CameraPermissionGate(
                onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                onBack = onBack
            )
        } else {
            ArPlacementSession(
                widthMeters = widthMeters,
                heightMeters = heightMeters,
                onBack = onBack
            )
        }
    }
}

@Composable
private fun ArPlacementSession(
    widthMeters: Float,
    heightMeters: Float,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)
    val cameraNode = rememberARCameraNode(engine)
    val childNodes = rememberNodes()
    val view = rememberView(engine)
    val collisionSystem = rememberCollisionSystem(view)

    // Use MutableState objects so gesture callbacks always read/write the latest values.
    val planeRendererState = remember { mutableStateOf(true) }
    val isPlacedState = remember { mutableStateOf(false) }
    val frameState = remember { mutableStateOf<Frame?>(null) }
    var trackingFailureReason by remember { mutableStateOf<TrackingFailureReason?>(null) }
    var sessionError by remember { mutableStateOf<String?>(null) }

    val dimensionLabel = remember(widthMeters, heightMeters) {
        String.format(Locale.US, "%.1f m × %.1f m", widthMeters, heightMeters)
    }

    fun resetPlacement() {
        childNodes.clear()
        isPlacedState.value = false
        planeRendererState.value = true
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ARScene(
            modifier = Modifier.fillMaxSize(),
            childNodes = childNodes,
            engine = engine,
            view = view,
            modelLoader = modelLoader,
            collisionSystem = collisionSystem,
            sessionConfiguration = { session, config ->
                config.depthMode =
                    if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                        Config.DepthMode.AUTOMATIC
                    } else {
                        Config.DepthMode.DISABLED
                    }
                config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
                config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                config.instantPlacementMode = Config.InstantPlacementMode.DISABLED
            },
            cameraNode = cameraNode,
            planeRenderer = planeRendererState.value,
            onTrackingFailureChanged = { reason ->
                trackingFailureReason = reason
            },
            onSessionFailed = { exception ->
                sessionError = exception.localizedMessage ?: "AR session failed"
            },
            onSessionUpdated = { _, updatedFrame ->
                frameState.value = updatedFrame
            },
            onGestureListener = rememberOnGestureListener(
                onSingleTapConfirmed = { motionEvent, node ->
                    if (isPlacedState.value || node != null) return@rememberOnGestureListener
                    val hitResults = frameState.value?.hitTest(motionEvent.x, motionEvent.y)
                    val anchor = hitResults
                        ?.firstOrNull { hit ->
                            hit.isValid(depthPoint = false, point = false)
                        }
                        ?.createAnchorOrNull()
                    if (anchor != null) {
                        childNodes += createGreenhouseAnchorNode(
                            engine = engine,
                            materialLoader = materialLoader,
                            anchor = anchor,
                            widthMeters = widthMeters,
                            heightMeters = heightMeters
                        )
                        isPlacedState.value = true
                        planeRendererState.value = false
                    }
                }
            )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(NightElevated.copy(alpha = 0.92f))
                        .testTag("ar_back")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = NightText
                    )
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = NightElevated.copy(alpha = 0.92f),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ar_status_badge")
                ) {
                    Text(
                        text = when {
                            sessionError != null -> sessionError!!
                            trackingFailureReason != null ->
                                trackingFailureReason!!.getDescription(context)
                            isPlacedState.value ->
                                "Greenhouse anchored · $dimensionLabel"
                            else ->
                                "Scan your surroundings and tap to place greenhouse"
                        },
                        color = NightText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }

            if (isPlacedState.value) {
                Button(
                    onClick = { resetPlacement() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("ar_reset_position"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NightElevated,
                        contentColor = NightText
                    )
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Reset", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = NightElevated.copy(alpha = 0.88f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Tap a detected desk or floor plane",
                            color = NightText,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Virtual greenhouse size: $dimensionLabel",
                            color = ClimateTeal,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

private fun createGreenhouseAnchorNode(
    engine: Engine,
    materialLoader: MaterialLoader,
    anchor: Anchor,
    widthMeters: Float,
    heightMeters: Float
): AnchorNode {
    val depthMeters = (widthMeters * 0.75f).coerceIn(1.5f, 4.0f)
    val glass = materialLoader.createColorInstance(Color(0x662EC4B6))
    val frameMat = materialLoader.createColorInstance(Color(0xE62FBF71))
    val baseMat = materialLoader.createColorInstance(Color(0xCC1A7A48))

    val anchorNode = AnchorNode(engine = engine, anchor = anchor).apply {
        // Lock the placed greenhouse in world space — walk around to inspect it.
        isEditable = false
    }

    fun box(size: Size, center: Position, material: com.google.android.filament.MaterialInstance) =
        CubeNode(
            engine = engine,
            size = size,
            center = center,
            materialInstance = material
        ).apply {
            isEditable = false
        }

    // Exact physical footprint: width × height × depth in meters.
    anchorNode.addChildNode(
        box(
            size = Size(x = widthMeters, y = heightMeters, z = depthMeters),
            center = Position(x = 0f, y = heightMeters / 2f, z = 0f),
            material = glass
        )
    )
    anchorNode.addChildNode(
        box(
            size = Size(x = widthMeters, y = 0.04f, z = depthMeters),
            center = Position(x = 0f, y = 0.02f, z = 0f),
            material = baseMat
        )
    )
    anchorNode.addChildNode(
        box(
            size = Size(x = widthMeters * 0.98f, y = 0.06f, z = depthMeters * 0.2f),
            center = Position(x = 0f, y = heightMeters + 0.03f, z = 0f),
            material = frameMat
        )
    )

    val post = Size(x = 0.06f, y = heightMeters, z = 0.06f)
    val halfW = widthMeters / 2f - 0.03f
    val halfD = depthMeters / 2f - 0.03f
    val postY = heightMeters / 2f
    listOf(
        Position(-halfW, postY, -halfD),
        Position(halfW, postY, -halfD),
        Position(-halfW, postY, halfD),
        Position(halfW, postY, halfD)
    ).forEach { center ->
        anchorNode.addChildNode(box(post, center, frameMat))
    }

    return anchorNode
}

@Composable
internal fun CameraPermissionGate(
    onRequest: () -> Unit,
    onBack: () -> Unit,
    body: String = "Allow camera access to scan surfaces and place your greenhouse in AR."
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0F0C))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp)
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(42.dp)
                .clip(CircleShape)
                .background(NightElevated)
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = NightText)
        }
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.ViewInAr,
                contentDescription = null,
                tint = ForestEmerald,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Camera permission required",
                color = NightText,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = body,
                color = Color(0xFF8A9A91),
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onRequest,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ForestEmerald,
                    contentColor = Color.Black
                )
            ) {
                Text("Grant camera access", fontWeight = FontWeight.Bold)
            }
        }
    }
}