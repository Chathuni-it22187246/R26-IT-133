package com.greenhands.app.harvest.ui

import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.FocusMeteringAction
import androidx.camera.view.PreviewView
import com.greenhands.app.harvest.detection.NormalizedRect
import java.util.concurrent.TimeUnit

/**
 * Requests AF/AE near a detected box center. Failures are ignored.
 */
class TargetFocusRequester {
    fun request(camera: Camera?, previewView: PreviewView, box: NormalizedRect) {
        try {
            val width = previewView.width
            val height = previewView.height
            if (width <= 0 || height <= 0) return
            val point = previewView.meteringPointFactory.createPoint(
                box.centerX * width,
                box.centerY * height
            )
            val action = FocusMeteringAction.Builder(
                point,
                FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
            )
                .setAutoCancelDuration(3, TimeUnit.SECONDS)
                .build()
            camera?.cameraControl?.startFocusAndMetering(action)
        } catch (t: Throwable) {
            Log.w(TAG, "Focus request skipped", t)
        }
    }

    companion object {
        private const val TAG = "TargetFocus"
    }
}
