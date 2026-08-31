package com.greenhands.app.sensor.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.view.MotionEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Plane
import com.google.ar.core.TrackingState
import com.greenhands.app.R
import com.greenhands.app.sensor.ar.ArCoverageNodes
import com.greenhands.app.sensor.ar.ArDirectionTapResult
import com.greenhands.app.sensor.ar.ArGreenhouseFrameGeometry
import com.greenhands.app.sensor.ar.ArGreenhouseFrameNodes
import com.greenhands.app.sensor.ar.ArGreenhousePose
import com.greenhands.app.sensor.ar.ArOriginPlacementController
import com.greenhands.app.sensor.ar.ArOriginPlacementPhase
import com.greenhands.app.sensor.ar.ArRealCoveragePlacement
import com.greenhands.app.sensor.ar.ArRealMaterials
import com.greenhands.app.sensor.ar.ArRealRecommendationPlacement
import com.greenhands.app.sensor.ar.ArRealScale
import com.greenhands.app.sensor.ar.ArRealSensorPlacement
import com.greenhands.app.sensor.ar.ArRealTapPlacement
import com.greenhands.app.sensor.ar.ArRecommendationNodes
import com.greenhands.app.sensor.ar.ArSensorMarkerNodes
import com.greenhands.app.sensor.ar.ArVisualizationMapper
import com.greenhands.app.sensor.ar.ArWorldMapper
import com.greenhands.app.sensor.ar.RealArAvailabilityUi
import com.greenhands.app.sensor.ar.RealArCameraPermissionUi
import com.greenhands.app.sensor.ar.RealArGateMapper
import com.greenhands.app.sensor.ar.RealArLayerVisibility
import com.greenhands.app.sensor.ar.RealArSessionUi
import com.greenhands.app.sensor.ar.RealArUxHelpers
import com.greenhands.app.sensor.model.SensorType
import com.greenhands.app.ui.components.DemoNotice
import com.greenhands.app.ui.components.InfoCard
import com.greenhands.app.ui.components.PrimaryActionButton
import com.greenhands.app.ui.components.ScreenScaffold
import com.greenhands.app.ui.components.SecondaryActionButton
import com.greenhands.app.ui.theme.AmberWarning
import com.greenhands.app.ui.theme.ClimateTeal
import com.greenhands.app.ui.theme.ForestEmerald
import com.greenhands.app.ui.theme.SoftError
import com.greenhands.app.ui.theme.Spacing
import io.github.sceneview.ar.ARScene
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.arcore.getUpdatedTrackables
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironment
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberNodes
import io.github.sceneview.rememberOnGestureListener
import kotlinx.coroutines.delay

/**
 * Real AR: live camera of the physical greenhouse + lightweight sensor/coverage overlays.
 * No virtual greenhouse model (floor/walls/roof). Manual floor-tap placement after alignment.
 */
@Composable
fun RealGreenhouseArScreen(
    ui: SensorPlacementUiState,
    onBack: () -> Unit,
    onOpenVirtualPreview: () -> Unit,
    onAddSensor: (x: Double, y: Double, type: SensorType) -> Boolean = { _, _, _ -> false },
    onRemoveSensor: (id: String) -> Boolean = { false },
    onResetSensors: () -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context as? Activity

    var availability by remember { mutableStateOf(RealArAvailabilityUi.CHECKING) }
    var cameraUi by remember { mutableStateOf(RealArCameraPermissionUi.NOT_REQUESTED) }
    var hasRequestedCamera by remember { mutableStateOf(false) }
    var sessionUi by remember { mutableStateOf(RealArSessionUi.IDLE) }
    var sessionError by remember { mutableStateOf<String?>(null) }
    var userInstallAttempted by remember { mutableStateOf(false) }
    var greenhousePose by remember { mutableStateOf(ArGreenhousePose()) }
    var directionHint by remember { mutableStateOf<String?>(null) }
    var sensorTypeFilter by remember { mutableStateOf<SensorType?>(null) }
    var layers by remember { mutableStateOf(RealArUxHelpers.defaultLayers()) }
    var floorConfirmed by remember { mutableStateOf(false) }
    var placeMode by remember { mutableStateOf(false) }
    var placeType by remember { mutableStateOf(SensorType.TEMPERATURE) }
    var placeHint by remember { mutableStateOf<String?>(null) }
    var pendingRemoveId by remember { mutableStateOf<String?>(null) }
    var confirmReset by remember { mutableStateOf(false) }

    // Remap snapshot when UI or type filter changes — uses precomputed coverage from UiState
    // (mapper selects monitoring vs by-type results; no domain recalculation in AR).
    val snapshot = remember(ui, sensorTypeFilter) {
        ArVisualizationMapper.from(ui, selectedTypeFilter = sensorTypeFilter)
    }
    val uxSummary = remember(snapshot) { RealArUxHelpers.summaryFromSnapshot(snapshot) }

    val engine = rememberEngine()
    val materialLoader = rememberMaterialLoader(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)
    // Neutral IBL baseline — lit PBR procedural geometry (coverage cells, frame beams)
    // needs indirect light; ARCore ENVIRONMENTAL_HDR updates it each frame via LightEstimator.
    val environment = rememberEnvironment(environmentLoader, isOpaque = false)
    val childNodes = rememberNodes()
    var arSceneView by remember { mutableStateOf<ARSceneView?>(null) }
    val poseLatest = rememberUpdatedState(greenhousePose)
    val snapshotLatest = rememberUpdatedState(snapshot)
    val layersLatest = rememberUpdatedState(layers)
    val placeModeLatest = rememberUpdatedState(placeMode)
    val placeTypeLatest = rememberUpdatedState(placeType)
    val onAddSensorLatest = rememberUpdatedState(onAddSensor)
    val onRemoveSensorLatest = rememberUpdatedState(onRemoveSensor)

    fun refreshCameraUi() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        val rationale = activity?.let {
            ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA)
        } ?: false
        cameraUi = RealArGateMapper.mapCameraPermission(
            granted = granted,
            shouldShowRationale = rationale,
            hasRequestedOnce = hasRequestedCamera
        )
    }

    fun refreshAvailability() {
        val raw = ArCoreApk.getInstance().checkAvailability(context)
        availability = RealArGateMapper.mapAvailability(raw.name)
    }

    fun clearSceneNodes() {
        childNodes.toList().forEach { node ->
            node.destroy()
            childNodes -= node
        }
    }

    fun arMaterial(color: Color) = ArRealMaterials.opaqueColor(materialLoader, color)

    fun arOverlay(color: Color, alpha: Float) =
        ArRealMaterials.transparentOverlay(materialLoader, color, alpha)

    fun rebuildGreenhouseFrame(pose: ArGreenhousePose = poseLatest.value) {
        ArGreenhouseFrameNodes.clearFrame(childNodes)
        if (!ArGreenhouseFrameGeometry.shouldShowFrame(pose.phase)) return
        val snap = snapshotLatest.value
        val layer = layersLatest.value
        val aligned = pose.phase == ArOriginPlacementPhase.ALIGNED
        val includeStructure = layer.shouldAttachGreenhouseGeometry(aligned)
        // Anchored greenhouse root — translucent Virtual-parity structure + overlays.
        val root = ArGreenhouseFrameNodes.buildFrame(
            engine = engine,
            pose = pose,
            physical = snap.physical,
            session = arSceneView?.session,
            includeStructure = includeStructure,
            floorMaterial = arOverlay(
                ArGreenhouseFrameNodes.StructureColors.floor,
                ArGreenhouseFrameGeometry.AR_FLOOR_ALPHA
            ),
            wallMaterial = arOverlay(
                ArGreenhouseFrameNodes.StructureColors.wall,
                ArGreenhouseFrameGeometry.AR_WALL_ALPHA
            ),
            roofMaterial = arOverlay(
                ArGreenhouseFrameNodes.StructureColors.roof,
                ArGreenhouseFrameGeometry.AR_ROOF_ALPHA
            ),
            frameMaterial = arMaterial(ArGreenhouseFrameNodes.StructureColors.frame),
            gridMaterial = arOverlay(
                ArGreenhouseFrameNodes.StructureColors.grid,
                0.55f
            )
        ) ?: return
        childNodes += root

        if (layer.shouldAttachAnyCoverage(aligned)) {
            val coverageCells = ArRealCoveragePlacement.buildRenderCells(
                pose = pose,
                snapshot = snap,
                layers = layer
            )
            // Per-cell translucent 3D boxes — never opaqueColor / full-floor slab.
            ArCoverageNodes.attachCoverage(
                engine = engine,
                frameRoot = root,
                cells = coverageCells,
                bodyMaterialFor = { color ->
                    arOverlay(color, ArRealMaterials.CELL_BODY_ALPHA)
                },
                edgeMaterialFor = { color ->
                    arOverlay(color, ArRealMaterials.CELL_EDGE_ALPHA)
                }
            )
        }

        if (layer.shouldAttachSensors(aligned)) {
            val markers = ArRealSensorPlacement.buildRenderMarkers(
                pose = pose,
                snapshot = snap,
                typeFilter = snap.selectedTypeFilter
            )
            ArSensorMarkerNodes.attachSensors(
                engine = engine,
                frameRoot = root,
                markers = markers,
                materialFor = { color -> arMaterial(color) }
            )
        }

        if (layer.shouldAttachRecommendations(aligned)) {
            val recommendations = ArRealRecommendationPlacement.buildRenderMarkers(
                pose = pose,
                snapshot = snap,
                typeFilter = snap.selectedTypeFilter
            )
            ArRecommendationNodes.attachRecommendations(
                engine = engine,
                frameRoot = root,
                markers = recommendations,
                materialFor = { color -> arMaterial(color) }
            )
        }
    }

    fun resetOrigin() {
        clearSceneNodes()
        directionHint = null
        floorConfirmed = false
        greenhousePose = ArOriginPlacementController.resetOrigin(greenhousePose)
    }

    fun resetAlignment() {
        ArRecommendationNodes.clearRecommendationsFromScene(childNodes)
        ArCoverageNodes.clearCoverageFromScene(childNodes)
        ArSensorMarkerNodes.clearSensorsFromScene(childNodes)
        ArGreenhouseFrameNodes.clearFrame(childNodes)
        directionHint = null
        greenhousePose = ArOriginPlacementController.resetAlignment(poseLatest.value)
    }

    fun placeOriginFromTap(event: MotionEvent) {
        if (!ArOriginPlacementController.canAcceptOriginTap(poseLatest.value)) return
        if (!floorConfirmed) return
        val view = arSceneView ?: return
        val hit = view.hitTestAR(
            xPx = event.x,
            yPx = event.y,
            planeTypes = setOf(Plane.Type.HORIZONTAL_UPWARD_FACING)
        ) ?: return
        val anchor = try {
            hit.createAnchor()
        } catch (_: Exception) {
            null
        } ?: return
        clearSceneNodes()
        val arPose = anchor.pose
        directionHint = null
        greenhousePose = ArOriginPlacementController.onOriginPlaced(
            poseLatest.value,
            worldTx = arPose.tx(),
            worldTy = arPose.ty(),
            worldTz = arPose.tz()
        )
        // No 3D origin marker — a prior 0.12 m lit PBR calibration cube rendered near-black
        // under AR lighting and appeared as a large cuboid over the camera feed.
        anchor.detach()
    }

    fun placeDirectionFromTap(event: MotionEvent) {
        if (!ArOriginPlacementController.canAcceptDirectionTap(poseLatest.value)) return
        val view = arSceneView ?: return
        val hit = view.hitTestAR(
            xPx = event.x,
            yPx = event.y,
            planeTypes = setOf(Plane.Type.HORIZONTAL_UPWARD_FACING)
        ) ?: return
        val hitPose = hit.hitPose
        val (next, result) = ArOriginPlacementController.onDirectionPoint(
            poseLatest.value,
            worldPx = hitPose.tx(),
            worldPy = hitPose.ty(),
            worldPz = hitPose.tz()
        )
        when (result) {
            ArDirectionTapResult.TOO_CLOSE -> {
                directionHint = context.getString(R.string.sensor_real_ar_direction_too_close)
            }
            ArDirectionTapResult.OK -> {
                directionHint = context.getString(R.string.sensor_real_ar_direction_set)
                greenhousePose = next
                clearSceneNodes()
                rebuildGreenhouseFrame(next)
                placeMode = true
                placeHint = context.getString(R.string.sensor_real_ar_aligned_ready)
            }
            ArDirectionTapResult.MISSING_ORIGIN,
            ArDirectionTapResult.INVALID_STATE -> Unit
        }
    }

    fun placeSensorFromTap(event: MotionEvent) {
        if (poseLatest.value.phase != ArOriginPlacementPhase.ALIGNED) return
        if (!placeModeLatest.value) return
        val view = arSceneView ?: return
        val hit = view.hitTestAR(
            xPx = event.x,
            yPx = event.y,
            planeTypes = setOf(Plane.Type.HORIZONTAL_UPWARD_FACING)
        ) ?: return
        val hitPose = hit.hitPose
        val result = ArRealTapPlacement.worldHitToGrid(
            pose = poseLatest.value,
            worldX = hitPose.tx(),
            worldY = hitPose.ty(),
            worldZ = hitPose.tz(),
            physical = snapshotLatest.value.physical,
            greenhouse = snapshotLatest.value.grid,
            type = placeTypeLatest.value
        )
        when (result) {
            is ArRealTapPlacement.PlacementResult.Ok -> {
                val added = onAddSensorLatest.value(result.gridX, result.gridY, result.type)
                placeHint = if (added) {
                    context.getString(R.string.sensor_real_ar_sensor_placed)
                } else {
                    context.getString(R.string.sensor_real_ar_placement_out_of_bounds)
                }
            }
            ArRealTapPlacement.PlacementResult.OutOfBounds -> {
                placeHint = context.getString(R.string.sensor_real_ar_placement_out_of_bounds)
            }
            ArRealTapPlacement.PlacementResult.NotAligned -> Unit
        }
    }

    fun selectSensorForRemoval(event: MotionEvent) {
        if (poseLatest.value.phase != ArOriginPlacementPhase.ALIGNED) return
        if (placeModeLatest.value) return
        val view = arSceneView ?: return
        val hit = view.hitTestAR(
            xPx = event.x,
            yPx = event.y,
            planeTypes = setOf(Plane.Type.HORIZONTAL_UPWARD_FACING)
        ) ?: return
        val hitPose = hit.hitPose
        val local = ArWorldMapper.worldToLocal(
            poseLatest.value,
            hitPose.tx(),
            hitPose.ty(),
            hitPose.tz(),
            displayScale = ArRealScale.rootScale(snapshotLatest.value.physical)
        ) ?: return
        val id = ArRealTapPlacement.nearestSensorId(
            localXMeters = local.x,
            localZMeters = local.z,
            sensors = snapshotLatest.value.sensors
        )
        pendingRemoveId = id
        placeHint = if (id != null) {
            context.getString(R.string.sensor_real_ar_sensor_selected_remove, id)
        } else {
            null
        }
        confirmReset = false
    }

    fun confirmRemoveSelected() {
        val id = pendingRemoveId ?: return
        if (onRemoveSensorLatest.value(id)) {
            placeHint = context.getString(R.string.sensor_real_ar_sensor_removed, id)
        }
        pendingRemoveId = null
    }

    fun onArTap(event: MotionEvent) {
        when (poseLatest.value.phase) {
            ArOriginPlacementPhase.PLANE_FOUND -> placeOriginFromTap(event)
            ArOriginPlacementPhase.SETTING_DIRECTION -> placeDirectionFromTap(event)
            ArOriginPlacementPhase.ALIGNED -> {
                if (placeModeLatest.value) {
                    pendingRemoveId = null
                    placeSensorFromTap(event)
                } else {
                    selectSensorForRemoval(event)
                }
            }
            else -> Unit
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasRequestedCamera = true
        val rationale = activity?.let {
            ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA)
        } ?: false
        cameraUi = RealArGateMapper.mapCameraPermission(
            granted = granted,
            shouldShowRationale = rationale,
            hasRequestedOnce = true
        )
    }

    val gestureListener = rememberOnGestureListener(
        onSingleTapConfirmed = { event, _ -> onArTap(event) }
    )

    LaunchedEffect(Unit) {
        refreshAvailability()
        refreshCameraUi()
    }

    LaunchedEffect(availability) {
        var guard = 0
        while (availability == RealArAvailabilityUi.CHECKING && guard < 20) {
            delay(250)
            refreshAvailability()
            guard++
        }
        if (availability == RealArAvailabilityUi.CHECKING) {
            availability = RealArAvailabilityUi.UNAVAILABLE
        }
    }

    LaunchedEffect(availability, cameraUi, userInstallAttempted) {
        if (
            availability == RealArAvailabilityUi.NEEDS_INSTALL_OR_UPDATE &&
            cameraUi == RealArCameraPermissionUi.GRANTED &&
            activity != null &&
            !userInstallAttempted
        ) {
            userInstallAttempted = true
            try {
                val status = ArCoreApk.getInstance().requestInstall(activity, true)
                if (status == ArCoreApk.InstallStatus.INSTALLED) {
                    availability = RealArAvailabilityUi.SUPPORTED
                }
            } catch (_: Exception) {
                // Stay on NEEDS_INSTALL_OR_UPDATE with virtual fallback.
            }
        }
    }

    LaunchedEffect(greenhousePose.phase) {
        if (greenhousePose.phase != ArOriginPlacementPhase.PLANE_FOUND) {
            floorConfirmed = false
        }
    }

    LaunchedEffect(
        greenhousePose.phase,
        snapshot.physical.lengthMeters,
        snapshot.physical.widthMeters,
        snapshot.physical.heightMeters,
        snapshot.physical.cellSizeMeters,
        snapshot.sensors.size,
        snapshot.coverageCells.size,
        snapshot.recommendations.size,
        snapshot.recommendations.count { it.selected },
        snapshot.selectedTypeFilter,
        sensorTypeFilter,
        layers.guide,
        layers.sensors,
        layers.covered,
        layers.blindSpots,
        layers.overlap,
        layers.recommendations
    ) {
        if (greenhousePose.phase == ArOriginPlacementPhase.ALIGNED) {
            rebuildGreenhouseFrame(greenhousePose)
        } else {
            ArRecommendationNodes.clearRecommendationsFromScene(childNodes)
            ArCoverageNodes.clearCoverageFromScene(childNodes)
            ArSensorMarkerNodes.clearSensorsFromScene(childNodes)
            ArGreenhouseFrameNodes.clearFrame(childNodes)
        }
    }

    val canStart = RealArGateMapper.canStartArSession(availability, cameraUi)
    val showPlaneRenderer = greenhousePose.phase == ArOriginPlacementPhase.SCANNING ||
        greenhousePose.phase == ArOriginPlacementPhase.PLANE_FOUND

    ScreenScaffold(
        title = stringResource(R.string.sensor_real_ar_title),
        onBack = onBack
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .then(
                    if (canStart) Modifier else Modifier.padding(horizontal = Spacing.lg)
                )
                .testTag("real_greenhouse_ar")
        ) {
            if (!canStart) {
                Text(
                    text = stringResource(R.string.sensor_real_ar_heading),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier
                        .semantics { heading() }
                        .testTag("real_ar_heading")
                )
                Spacer(Modifier.height(Spacing.sm))
                DemoNotice(
                    text = stringResource(R.string.sensor_real_ar_notice),
                    modifier = Modifier.testTag("real_ar_notice")
                )
                Spacer(Modifier.height(Spacing.md))

                InfoCard(modifier = Modifier.testTag("real_ar_status_card")) {
                    Text(
                        text = statusMessage(
                            availability = availability,
                            camera = cameraUi,
                            session = sessionUi,
                            originPhase = greenhousePose.phase,
                            sessionError = sessionError,
                            directionHint = directionHint
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.testTag("real_ar_status_message")
                    )
                }

                Spacer(Modifier.height(Spacing.md))
            }

            when {
                availability == RealArAvailabilityUi.CHECKING -> Unit
                RealArGateMapper.shouldOfferVirtualFallback(availability) -> {
                    GateActions(
                        primaryLabel = stringResource(R.string.sensor_real_ar_back_virtual),
                        onPrimary = onOpenVirtualPreview,
                        secondaryLabel = stringResource(R.string.sensor_real_ar_back),
                        onSecondary = onBack,
                        primaryTag = "real_ar_fallback_virtual",
                        secondaryTag = "real_ar_back"
                    )
                }
                cameraUi == RealArCameraPermissionUi.NOT_REQUESTED ||
                    cameraUi == RealArCameraPermissionUi.DENIED_CAN_RETRY -> {
                    GateActions(
                        primaryLabel = if (cameraUi == RealArCameraPermissionUi.NOT_REQUESTED) {
                            stringResource(R.string.sensor_real_ar_allow_camera)
                        } else {
                            stringResource(R.string.sensor_real_ar_try_again)
                        },
                        onPrimary = {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        },
                        secondaryLabel = stringResource(R.string.sensor_real_ar_back_virtual),
                        onSecondary = onOpenVirtualPreview,
                        primaryTag = "real_ar_request_camera",
                        secondaryTag = "real_ar_fallback_virtual"
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    SecondaryActionButton(
                        text = stringResource(R.string.sensor_real_ar_back),
                        onClick = onBack,
                        modifier = Modifier.testTag("real_ar_back")
                    )
                }
                cameraUi == RealArCameraPermissionUi.DENIED_PERMANENT -> {
                    Text(
                        text = stringResource(R.string.sensor_real_ar_camera_settings_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("real_ar_camera_settings_hint")
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    GateActions(
                        primaryLabel = stringResource(R.string.sensor_real_ar_back_virtual),
                        onPrimary = onOpenVirtualPreview,
                        secondaryLabel = stringResource(R.string.sensor_real_ar_back),
                        onSecondary = onBack,
                        primaryTag = "real_ar_fallback_virtual",
                        secondaryTag = "real_ar_back"
                    )
                }
                canStart -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .testTag("real_ar_viewport")
                    ) {
                        ARScene(
                            modifier = Modifier.fillMaxSize(),
                            engine = engine,
                            materialLoader = materialLoader,
                            environmentLoader = environmentLoader,
                            environment = environment,
                            childNodes = childNodes,
                            planeRenderer = showPlaneRenderer,
                            onGestureListener = gestureListener,
                            sessionConfiguration = { _, config ->
                                config.planeFindingMode =
                                    Config.PlaneFindingMode.HORIZONTAL
                                config.lightEstimationMode =
                                    Config.LightEstimationMode.ENVIRONMENTAL_HDR
                                config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                            },
                            onViewCreated = { arSceneView = this },
                            onSessionCreated = {
                                sessionUi = RealArSessionUi.RUNNING
                                sessionError = null
                            },
                            onSessionFailed = { ex ->
                                sessionUi = RealArSessionUi.FAILED
                                sessionError = ex.message
                            },
                            onTrackingFailureChanged = { reason ->
                                sessionUi = if (reason == null) {
                                    RealArSessionUi.RUNNING
                                } else {
                                    RealArSessionUi.TRACKING_LIMITED
                                }
                            },
                            onSessionUpdated = { _, frame ->
                                if (poseLatest.value.phase == ArOriginPlacementPhase.SCANNING) {
                                    val planes = frame.getUpdatedTrackables(Plane::class.java)
                                    val found = planes.any { plane ->
                                        plane.trackingState == TrackingState.TRACKING &&
                                            plane.type == Plane.Type.HORIZONTAL_UPWARD_FACING
                                    }
                                    if (found) {
                                        greenhousePose =
                                            ArOriginPlacementController.onHorizontalPlaneDetected(
                                                poseLatest.value
                                            )
                                    }
                                }
                            }
                        )
                        if (sessionUi == RealArSessionUi.FAILED) {
                            Text(
                                text = stringResource(
                                    R.string.sensor_real_ar_session_failed,
                                    sessionError ?: ""
                                ),
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(Spacing.lg)
                                    .testTag("real_ar_session_failed")
                            )
                        }

                        Column(
                            Modifier
                                .align(Alignment.TopCenter)
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                        ) {
                            RealArOverlayChip(
                                text = originInstruction(
                                    phase = greenhousePose.phase,
                                    floorConfirmed = floorConfirmed
                                ),
                                testTag = "real_ar_floor_instruction"
                            )
                            if (sessionUi == RealArSessionUi.TRACKING_LIMITED) {
                                RealArOverlayChip(
                                    text = stringResource(
                                        R.string.sensor_real_ar_status_tracking_limited
                                    ),
                                    testTag = "real_ar_tracking_limited",
                                    tint = AmberWarning
                                )
                            }
                            if (greenhousePose.phase == ArOriginPlacementPhase.PLANE_FOUND && !floorConfirmed) {
                                RealArOverlayChip(
                                    text = stringResource(R.string.sensor_real_ar_confirm_floor_hint),
                                    testTag = "real_ar_confirm_floor_hint",
                                    tint = ClimateTeal
                                )
                            }
                            if (greenhousePose.isAligned) {
                                RealArOverlayChip(
                                    text = stringResource(R.string.sensor_real_ar_greenhouse_aligned) +
                                        " · ${formatDimM(snapshot.physical.lengthMeters)}×" +
                                        "${formatDimM(snapshot.physical.widthMeters)}×" +
                                        "${formatDimM(snapshot.physical.heightMeters)} m",
                                    testTag = "real_ar_aligned_stats",
                                    tint = ForestEmerald
                                )
                                RealArOverlayChip(
                                    text = stringResource(
                                        R.string.sensor_real_ar_aligned_overlay_stats,
                                        uxSummary.sensorCount,
                                        formatPct(uxSummary.coveragePercent),
                                        uxSummary.blindSpotCells,
                                        uxSummary.overlapCells,
                                        uxSummary.recommendationCount
                                    ),
                                    testTag = "real_ar_coverage_chip"
                                )
                                if (!placeHint.isNullOrBlank()) {
                                    RealArOverlayChip(
                                        text = placeHint!!,
                                        testTag = "real_ar_place_hint",
                                        tint = ClimateTeal
                                    )
                                }
                            }
                            if (!greenhousePose.isAligned) {
                                RealArOverlayChip(
                                    text = stringResource(
                                        R.string.sensor_real_ar_dimensions_chip,
                                        "${formatDimM(snapshot.physical.lengthMeters)}×" +
                                            "${formatDimM(snapshot.physical.widthMeters)}×" +
                                            "${formatDimM(snapshot.physical.heightMeters)} m",
                                        "${formatDimM(snapshot.physical.cellSizeMeters)} m"
                                    ),
                                    testTag = "real_ar_dimensions_chip"
                                )
                            }
                            if (!directionHint.isNullOrBlank()) {
                                RealArOverlayChip(
                                    text = directionHint!!,
                                    testTag = "real_ar_direction_hint",
                                    tint = AmberWarning
                                )
                            }
                        }

                        Column(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.78f)
                                )
                                .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                        ) {
                            if (greenhousePose.isAligned) {
                                RealArPlacementControls(
                                    placeType = placeType,
                                    placeMode = placeMode,
                                    onSelectType = {
                                        placeType = it
                                        pendingRemoveId = null
                                        confirmReset = false
                                    },
                                    onTogglePlaceMode = {
                                        placeMode = !placeMode
                                        pendingRemoveId = null
                                        confirmReset = false
                                    },
                                    layers = layers,
                                    onLayersChange = { layers = it },
                                    sensorTypeFilter = sensorTypeFilter,
                                    onSensorTypeFilter = { sensorTypeFilter = it },
                                    pendingRemoveId = pendingRemoveId,
                                    onConfirmRemove = { confirmRemoveSelected() },
                                    confirmReset = confirmReset,
                                    onResetPlacement = {
                                        if (!confirmReset) {
                                            confirmReset = true
                                            placeHint = context.getString(
                                                R.string.sensor_real_ar_reset_placement_confirm
                                            )
                                        } else {
                                            confirmReset = false
                                            pendingRemoveId = null
                                            onResetSensors()
                                            placeHint = context.getString(
                                                R.string.sensor_real_ar_placement_reset
                                            )
                                        }
                                    }
                                )
                                Spacer(Modifier.height(Spacing.xs))
                            } else if (greenhousePose.isOriginPlaced) {
                                Text(
                                    text = stringResource(
                                        R.string.sensor_real_ar_origin_marker_label
                                    ),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = ClimateTeal,
                                    modifier = Modifier.testTag("real_ar_origin_label")
                                )
                                if (greenhousePose.phase ==
                                    ArOriginPlacementPhase.SETTING_DIRECTION
                                ) {
                                    Text(
                                        text = stringResource(
                                            R.string.sensor_real_ar_direction_indicator
                                        ),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = AmberWarning,
                                        modifier = Modifier.testTag(
                                            "real_ar_direction_indicator"
                                        )
                                    )
                                }
                                Spacer(Modifier.height(Spacing.sm))
                            }

                            if (sessionUi == RealArSessionUi.FAILED) {
                                GateActions(
                                    primaryLabel = stringResource(
                                        R.string.sensor_real_ar_back_virtual
                                    ),
                                    onPrimary = onOpenVirtualPreview,
                                    secondaryLabel = stringResource(
                                        R.string.sensor_real_ar_back
                                    ),
                                    onSecondary = onBack,
                                    primaryTag = "real_ar_fallback_virtual",
                                    secondaryTag = "real_ar_back"
                                )
                            } else {
                                when (greenhousePose.phase) {
                                    ArOriginPlacementPhase.PLANE_FOUND -> {
                                        if (!floorConfirmed) {
                                            PrimaryActionButton(
                                                text = stringResource(
                                                    R.string.sensor_real_ar_confirm_floor
                                                ),
                                                onClick = { floorConfirmed = true },
                                                modifier = Modifier.testTag("real_ar_confirm_floor")
                                            )
                                            Spacer(Modifier.height(Spacing.xs))
                                        }
                                    }
                                    ArOriginPlacementPhase.ORIGIN_PLACED -> {
                                        PrimaryActionButton(
                                            text = stringResource(
                                                R.string.sensor_real_ar_set_direction
                                            ),
                                            onClick = {
                                                directionHint = null
                                                greenhousePose =
                                                    ArOriginPlacementController.beginSetDirection(
                                                        poseLatest.value
                                                    )
                                            },
                                            modifier = Modifier.testTag("real_ar_set_direction")
                                        )
                                        Spacer(Modifier.height(Spacing.xs))
                                        SecondaryActionButton(
                                            text = stringResource(
                                                R.string.sensor_real_ar_reset_origin
                                            ),
                                            onClick = { resetOrigin() },
                                            modifier = Modifier.testTag("real_ar_reset_origin")
                                        )
                                    }
                                    ArOriginPlacementPhase.SETTING_DIRECTION -> {
                                        SecondaryActionButton(
                                            text = stringResource(
                                                R.string.sensor_real_ar_reset_alignment
                                            ),
                                            onClick = { resetAlignment() },
                                            modifier = Modifier.testTag(
                                                "real_ar_reset_alignment"
                                            )
                                        )
                                        Spacer(Modifier.height(Spacing.xs))
                                        SecondaryActionButton(
                                            text = stringResource(
                                                R.string.sensor_real_ar_reset_origin
                                            ),
                                            onClick = { resetOrigin() },
                                            modifier = Modifier.testTag("real_ar_reset_origin")
                                        )
                                    }
                                    ArOriginPlacementPhase.ALIGNED -> {
                                        SecondaryActionButton(
                                            text = stringResource(
                                                R.string.sensor_real_ar_reset_alignment
                                            ),
                                            onClick = { resetAlignment() },
                                            modifier = Modifier.testTag(
                                                "real_ar_reset_alignment"
                                            )
                                        )
                                        Spacer(Modifier.height(Spacing.xs))
                                        SecondaryActionButton(
                                            text = stringResource(
                                                R.string.sensor_real_ar_reset_origin
                                            ),
                                            onClick = { resetOrigin() },
                                            modifier = Modifier.testTag("real_ar_reset_origin")
                                        )
                                    }
                                    else -> Unit
                                }
                                Spacer(Modifier.height(Spacing.xs))
                                SecondaryActionButton(
                                    text = stringResource(R.string.sensor_real_ar_back),
                                    onClick = onBack,
                                    modifier = Modifier.testTag("real_ar_back")
                                )
                                Spacer(Modifier.height(Spacing.xs))
                                SecondaryActionButton(
                                    text = stringResource(R.string.sensor_real_ar_back_virtual),
                                    onClick = onOpenVirtualPreview,
                                    modifier = Modifier.testTag("real_ar_fallback_virtual")
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun originInstruction(
    phase: ArOriginPlacementPhase,
    floorConfirmed: Boolean
): String = when (phase) {
    ArOriginPlacementPhase.SCANNING ->
        stringResource(R.string.sensor_real_ar_scan_floor)
    ArOriginPlacementPhase.PLANE_FOUND ->
        if (floorConfirmed) {
            stringResource(R.string.sensor_real_ar_tap_origin)
        } else {
            stringResource(R.string.sensor_real_ar_confirm_floor_hint)
        }
    ArOriginPlacementPhase.ORIGIN_PLACED ->
        stringResource(R.string.sensor_real_ar_origin_placed)
    ArOriginPlacementPhase.SETTING_DIRECTION ->
        stringResource(R.string.sensor_real_ar_tap_direction)
    ArOriginPlacementPhase.ALIGNED ->
        stringResource(R.string.sensor_real_ar_greenhouse_aligned)
}

@Composable
private fun RealArOverlayChip(
    text: String,
    testTag: String,
    tint: Color = MaterialTheme.colorScheme.onSurface
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = tint,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

private fun formatDimM(meters: Double): String =
    String.format(java.util.Locale.US, "%.0f", meters)

private fun formatPct(value: Double): String =
    String.format(java.util.Locale.US, "%.0f", value)

@Composable
private fun RealArSummaryCard(summary: RealArUxHelpers.Summary) {
    InfoCard(modifier = Modifier.testTag("real_ar_summary_card")) {
        Text(
            text = stringResource(R.string.sensor_real_ar_summary_title),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = stringResource(
                R.string.sensor_real_ar_summary_dims,
                formatDimM(summary.lengthMeters),
                formatDimM(summary.widthMeters),
                formatDimM(summary.heightMeters)
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("real_ar_summary_dims")
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = stringResource(
                R.string.sensor_real_ar_summary_counts,
                summary.sensorCount,
                summary.recommendationCount
            ),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.testTag("real_ar_summary_counts")
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = stringResource(
                R.string.sensor_real_ar_coverage_metrics,
                formatPct(summary.coveragePercent),
                summary.blindSpotCells,
                summary.overlapCells
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("real_ar_coverage_metrics")
        )
    }
}

@Composable
private fun RealArPlacementControls(
    placeType: SensorType,
    placeMode: Boolean,
    onSelectType: (SensorType) -> Unit,
    onTogglePlaceMode: () -> Unit,
    layers: RealArLayerVisibility,
    onLayersChange: (RealArLayerVisibility) -> Unit,
    sensorTypeFilter: SensorType?,
    onSensorTypeFilter: (SensorType?) -> Unit,
    pendingRemoveId: String?,
    onConfirmRemove: () -> Unit,
    confirmReset: Boolean,
    onResetPlacement: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .testTag("real_ar_placement_controls"),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .testTag("real_ar_place_types"),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            RealArFilterChip(
                label = "T",
                selected = placeType == SensorType.TEMPERATURE,
                onClick = { onSelectType(SensorType.TEMPERATURE) },
                testTag = "real_ar_place_TEMPERATURE"
            )
            RealArFilterChip(
                label = "H",
                selected = placeType == SensorType.HUMIDITY,
                onClick = { onSelectType(SensorType.HUMIDITY) },
                testTag = "real_ar_place_HUMIDITY"
            )
            RealArFilterChip(
                label = "SM",
                selected = placeType == SensorType.SOIL_MOISTURE,
                onClick = { onSelectType(SensorType.SOIL_MOISTURE) },
                testTag = "real_ar_place_SOIL_MOISTURE"
            )
            RealArFilterChip(
                label = "L",
                selected = placeType == SensorType.LIGHT_INTENSITY,
                onClick = { onSelectType(SensorType.LIGHT_INTENSITY) },
                testTag = "real_ar_place_LIGHT_INTENSITY"
            )
            RealArFilterChip(
                label = stringResource(R.string.sensor_real_ar_place_sensor),
                selected = placeMode,
                onClick = onTogglePlaceMode,
                testTag = "real_ar_place_mode"
            )
        }
        if (!placeMode && pendingRemoveId != null) {
            PrimaryActionButton(
                text = stringResource(R.string.sensor_real_ar_remove_sensor),
                onClick = onConfirmRemove,
                modifier = Modifier.testTag("real_ar_remove_sensor")
            )
        } else if (!placeMode) {
            Text(
                text = stringResource(R.string.sensor_real_ar_tap_to_remove_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("real_ar_remove_hint")
            )
        }
        RealArSensorTypeFilter(
            selected = sensorTypeFilter,
            onSelect = onSensorTypeFilter
        )
        RealArLayerControls(
            layers = layers,
            onChange = onLayersChange
        )
        SecondaryActionButton(
            text = if (confirmReset) {
                stringResource(R.string.sensor_real_ar_reset_placement_confirm)
            } else {
                stringResource(R.string.sensor_real_ar_reset_placement)
            },
            onClick = onResetPlacement,
            modifier = Modifier.testTag("real_ar_reset_placement")
        )
    }
}

@Composable
private fun RealArLayerControls(
    layers: RealArLayerVisibility,
    onChange: (RealArLayerVisibility) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .testTag("real_ar_layers"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        RealArFilterChip(
            label = stringResource(R.string.sensor_real_ar_layer_greenhouse),
            selected = layers.guide,
            onClick = { onChange(layers.toggleGreenhouse()) },
            testTag = "real_ar_layer_greenhouse"
        )
        RealArFilterChip(
            label = stringResource(R.string.sensor_virtual_preview_layer_sensors),
            selected = layers.sensors,
            onClick = { onChange(layers.toggleSensors()) },
            testTag = "real_ar_layer_sensors"
        )
        RealArFilterChip(
            label = stringResource(R.string.sensor_real_ar_layer_covered),
            selected = layers.covered,
            onClick = { onChange(layers.toggleCovered()) },
            testTag = "real_ar_layer_covered"
        )
        RealArFilterChip(
            label = stringResource(R.string.sensor_real_ar_layer_blind_spots),
            selected = layers.blindSpots,
            onClick = { onChange(layers.toggleBlindSpots()) },
            testTag = "real_ar_layer_blind_spots"
        )
        RealArFilterChip(
            label = stringResource(R.string.sensor_real_ar_layer_overlap),
            selected = layers.overlap,
            onClick = { onChange(layers.toggleOverlap()) },
            testTag = "real_ar_layer_overlap"
        )
        RealArFilterChip(
            label = stringResource(R.string.sensor_real_ar_layer_p),
            selected = layers.recommendations,
            onClick = { onChange(layers.toggleRecommendations()) },
            testTag = "real_ar_layer_recommendations"
        )
    }
}

@Composable
private fun RealArCompactLegend(monitoringMode: Boolean) {
    Column(
        Modifier
            .fillMaxWidth()
            .testTag("real_ar_compact_legend"),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = stringResource(R.string.sensor_real_ar_legend_sensor),
            style = MaterialTheme.typography.labelSmall,
            color = ForestEmerald
        )
        Text(
            text = stringResource(R.string.sensor_real_ar_legend_recommendation),
            style = MaterialTheme.typography.labelSmall,
            color = ClimateTeal
        )
        Text(
            text = stringResource(R.string.sensor_coverage_legend_covered),
            style = MaterialTheme.typography.labelSmall,
            color = ForestEmerald
        )
        Text(
            text = stringResource(R.string.sensor_coverage_legend_overlap),
            style = MaterialTheme.typography.labelSmall,
            color = AmberWarning
        )
        Text(
            text = stringResource(R.string.sensor_coverage_legend_blind),
            style = MaterialTheme.typography.labelSmall,
            color = SoftError
        )
        if (monitoringMode) {
            Text(
                text = stringResource(R.string.sensor_real_ar_coverage_all_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("real_ar_coverage_all_hint")
            )
        }
    }
}

@Composable
private fun RealArSensorTypeFilter(
    selected: SensorType?,
    onSelect: (SensorType?) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .testTag("real_ar_sensor_filter"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        RealArFilterChip(
            label = stringResource(R.string.sensor_coverage_filter_all),
            selected = selected == null,
            onClick = { onSelect(null) },
            testTag = "real_ar_filter_all"
        )
        RealArFilterChip(
            label = stringResource(R.string.sensor_type_name_temperature),
            selected = selected == SensorType.TEMPERATURE,
            onClick = { onSelect(SensorType.TEMPERATURE) },
            testTag = "real_ar_filter_TEMPERATURE"
        )
        RealArFilterChip(
            label = stringResource(R.string.sensor_type_name_humidity),
            selected = selected == SensorType.HUMIDITY,
            onClick = { onSelect(SensorType.HUMIDITY) },
            testTag = "real_ar_filter_HUMIDITY"
        )
        RealArFilterChip(
            label = stringResource(R.string.sensor_type_name_soil_moisture),
            selected = selected == SensorType.SOIL_MOISTURE,
            onClick = { onSelect(SensorType.SOIL_MOISTURE) },
            testTag = "real_ar_filter_SOIL_MOISTURE"
        )
        RealArFilterChip(
            label = stringResource(R.string.sensor_type_name_light_intensity),
            selected = selected == SensorType.LIGHT_INTENSITY,
            onClick = { onSelect(SensorType.LIGHT_INTENSITY) },
            testTag = "real_ar_filter_LIGHT_INTENSITY"
        )
    }
}

@Composable
private fun RealArFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val border = if (selected) ForestEmerald else MaterialTheme.colorScheme.outline
    Surface(
        color = if (selected) ForestEmerald.copy(alpha = 0.16f) else Color.Transparent,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .border(BorderStroke(1.dp, border), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) ForestEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun GateActions(
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String,
    onSecondary: () -> Unit,
    primaryTag: String,
    secondaryTag: String
) {
    PrimaryActionButton(
        text = primaryLabel,
        onClick = onPrimary,
        modifier = Modifier.testTag(primaryTag)
    )
    Spacer(Modifier.height(Spacing.sm))
    SecondaryActionButton(
        text = secondaryLabel,
        onClick = onSecondary,
        modifier = Modifier.testTag(secondaryTag)
    )
}

@Composable
private fun statusMessage(
    availability: RealArAvailabilityUi,
    camera: RealArCameraPermissionUi,
    session: RealArSessionUi,
    originPhase: ArOriginPlacementPhase,
    sessionError: String?,
    directionHint: String?
): String {
    if (!directionHint.isNullOrBlank()) return directionHint
    return when {
        availability == RealArAvailabilityUi.CHECKING ->
            stringResource(R.string.sensor_real_ar_status_checking)
        availability == RealArAvailabilityUi.UNSUPPORTED_DEVICE ->
            stringResource(R.string.sensor_real_ar_status_unsupported)
        availability == RealArAvailabilityUi.UNAVAILABLE ->
            stringResource(R.string.sensor_real_ar_status_unavailable)
        availability == RealArAvailabilityUi.NEEDS_INSTALL_OR_UPDATE ->
            stringResource(R.string.sensor_real_ar_status_needs_install)
        camera == RealArCameraPermissionUi.NOT_REQUESTED ->
            stringResource(R.string.sensor_real_ar_status_need_camera)
        camera == RealArCameraPermissionUi.DENIED_CAN_RETRY ->
            stringResource(R.string.sensor_real_ar_status_camera_denied)
        camera == RealArCameraPermissionUi.DENIED_PERMANENT ->
            stringResource(R.string.sensor_real_ar_status_camera_denied)
        session == RealArSessionUi.FAILED ->
            stringResource(R.string.sensor_real_ar_session_failed, sessionError ?: "")
        session == RealArSessionUi.TRACKING_LIMITED ->
            stringResource(R.string.sensor_real_ar_status_tracking_limited)
        originPhase == ArOriginPlacementPhase.ALIGNED ->
            stringResource(R.string.sensor_real_ar_greenhouse_aligned)
        originPhase == ArOriginPlacementPhase.SETTING_DIRECTION ->
            stringResource(R.string.sensor_real_ar_tap_direction)
        originPhase == ArOriginPlacementPhase.ORIGIN_PLACED ->
            stringResource(R.string.sensor_real_ar_origin_placed)
        originPhase == ArOriginPlacementPhase.PLANE_FOUND ->
            stringResource(R.string.sensor_real_ar_tap_origin)
        session == RealArSessionUi.RUNNING ->
            stringResource(R.string.sensor_real_ar_scan_floor)
        else ->
            stringResource(R.string.sensor_real_ar_status_ready)
    }
}
