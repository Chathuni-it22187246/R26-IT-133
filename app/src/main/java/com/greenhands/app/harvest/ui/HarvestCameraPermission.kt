package com.greenhands.app.harvest.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

data class HarvestCameraPermissionState(
    val granted: Boolean,
    val showDeniedMessage: Boolean,
    val requestOrRetry: () -> Unit
)

@Composable
fun rememberHarvestCameraPermissionState(): HarvestCameraPermissionState {
    val context = LocalContext.current
    val inspectionMode = LocalInspectionMode.current
    var granted by remember { mutableStateOf(hasCameraPermission(context)) }
    var requestedOnce by rememberSaveable { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        requestedOnce = true
        granted = isGranted
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = hasCameraPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(inspectionMode) {
        if (!inspectionMode && !granted && !requestedOnce) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    return HarvestCameraPermissionState(
        granted = granted,
        showDeniedMessage = requestedOnce && !granted,
        requestOrRetry = {
            val activity = context.findActivity()
            val permanentlyDenied = requestedOnce &&
                activity != null &&
                !granted &&
                !ActivityCompat.shouldShowRequestPermissionRationale(
                    activity,
                    Manifest.permission.CAMERA
                )
            if (permanentlyDenied) {
                openAppSettings(context)
            } else {
                launcher.launch(Manifest.permission.CAMERA)
            }
        }
    )
}

private fun hasCameraPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
    }
    context.startActivity(intent)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
