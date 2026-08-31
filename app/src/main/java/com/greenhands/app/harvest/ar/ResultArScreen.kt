package com.greenhands.app.harvest.ar

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.greenhands.app.R
import com.greenhands.app.ui.components.DemoNotice
import com.greenhands.app.ui.components.PrimaryActionButton
import com.greenhands.app.ui.components.ScreenScaffold
import com.greenhands.app.ui.components.ScrollScreen
import com.greenhands.app.ui.components.SecondaryActionButton
import com.greenhands.app.ui.theme.Spacing

/**
 * Optional AR visualization of an already-computed harvest or plant-health result.
 * Does not rerun HSV, harvest decision, or disease classification.
 */
@Composable
fun ResultArScreen(onBack: () -> Unit) {
    val data = ArResultStore.current
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    var availability by remember { mutableStateOf(ArAvailability.check(context)) }
    var cameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        cameraGranted = granted
    }

    LaunchedEffect(activity, availability) {
        if (activity != null &&
            availability != ArAvailability.State.SUPPORTED &&
            availability != ArAvailability.State.UNSUPPORTED
        ) {
            availability = ArAvailability.requestInstallIfNeeded(activity)
        }
    }

    when {
        data == null -> ArUnavailablePane(
            message = stringResource(R.string.ar_no_result),
            onBack = onBack
        )
        !cameraGranted -> ArUnavailablePane(
            message = stringResource(R.string.ar_camera_permission),
            onBack = onBack,
            actionLabel = stringResource(R.string.ar_grant_camera),
            onAction = { permissionLauncher.launch(Manifest.permission.CAMERA) }
        )
        availability == ArAvailability.State.UNSUPPORTED -> ArUnavailablePane(
            message = stringResource(R.string.ar_not_available),
            onBack = onBack
        )
        availability == ArAvailability.State.INSTALL_REQUESTED ||
            availability == ArAvailability.State.UNKNOWN -> ArUnavailablePane(
            message = stringResource(R.string.ar_checking),
            onBack = onBack
        )
        else -> ArSessionPane(data = data, onBack = onBack)
    }
}

@Composable
private fun ArSessionPane(data: ArResultData, onBack: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var host by remember { mutableStateOf<ArResultSceneHost?>(null) }
    var placed by remember { mutableStateOf(false) }
    var sessionError by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner, host) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> host?.resumeSession()
                Lifecycle.Event.ON_PAUSE -> host?.pauseSession()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            host?.pauseSession()
            host?.destroySession()
        }
    }

    Box(Modifier.fillMaxSize().testTag("harvest_ar_session")) {
        if (!sessionError) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    try {
                        ArResultSceneHost(ctx).also { created ->
                            created.bind(data)
                            created.onPlacementChanged = { placed = it }
                            host = created
                            created.resumeSession()
                        }
                    } catch (_: Throwable) {
                        sessionError = true
                        android.widget.FrameLayout(ctx)
                    }
                },
                update = { view ->
                    (view as? ArResultSceneHost)?.bind(data)
                }
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(Color(0x990B0F0C))
                .statusBarsPadding()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("harvest_ar_back")) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.ar_back),
                        tint = Color.White
                    )
                }
                Text(
                    text = data.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    modifier = Modifier.padding(start = Spacing.sm)
                )
            }
            Text(
                text = if (placed) {
                    stringResource(R.string.ar_placed_hint)
                } else {
                    stringResource(R.string.ar_tap_hint)
                },
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.padding(start = Spacing.xl, bottom = Spacing.sm)
            )
        }
        if (placed) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(Spacing.lg)
            ) {
                SecondaryActionButton(
                    text = stringResource(R.string.ar_reset),
                    onClick = { host?.resetPlacement() },
                    modifier = Modifier.testTag("harvest_ar_reset")
                )
            }
        }
        if (sessionError) {
            ArUnavailablePane(
                message = stringResource(R.string.ar_not_available),
                onBack = onBack
            )
        }
    }
}

@Composable
private fun ArUnavailablePane(
    message: String,
    onBack: () -> Unit,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    ScreenScaffold(
        title = stringResource(R.string.ar_screen_title),
        onBack = onBack
    ) { padding ->
        ScrollScreen(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag("harvest_ar_unavailable")
        ) {
            DemoNotice(message)
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(Spacing.section))
                PrimaryActionButton(text = actionLabel, onClick = onAction)
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
