package com.greenhands.app.harvest.detection

import android.util.Log
import com.greenhands.app.harvest.domain.HarvestArgbFrame

/**
 * Serializes [detectTimed] and [close] on one monitor so CameraX analysis
 * cannot run inference on a closed interpreter, and close waits for in-flight
 * work. Does not change boxes, scores, or harvest rules.
 */
class SynchronizedTargetDetector(
    private val delegate: TargetDetector
) : TargetDetector {
    private val lock = Any()

    @Volatile
    private var closed: Boolean = false

    @Volatile
    private var loggedClosed: Boolean = false

    override val isModelReady: Boolean
        get() = !closed && delegate.isModelReady

    override fun detect(frame: HarvestArgbFrame, timestampMs: Long): List<TargetDetection> =
        detectTimed(frame, timestampMs).detections

    override fun detectTimed(
        frame: HarvestArgbFrame,
        timestampMs: Long
    ): HarvestYoloDetectTiming {
        synchronized(lock) {
            if (closed || !delegate.isModelReady) return HarvestYoloDetectTiming.NONE
            return try {
                delegate.detectTimed(frame, timestampMs)
            } catch (t: Throwable) {
                if (closed || isInterpreterClosed(t)) {
                    logClosedOnce(t)
                    HarvestYoloDetectTiming.NONE
                } else {
                    try {
                        Log.e(TAG, "TFLite detect failed", t)
                    } catch (_: Throwable) {
                    }
                    HarvestYoloDetectTiming.NONE
                }
            }
        }
    }

    override fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            try {
                delegate.close()
            } catch (_: Throwable) {
            }
        }
    }

    private fun logClosedOnce(t: Throwable) {
        if (loggedClosed) return
        loggedClosed = true
        try {
            Log.w(TAG, "YOLO detector is closed; skipping inference", t)
        } catch (_: Throwable) {
        }
    }

    companion object {
        private const val TAG = "HarvestDetector"

        fun isInterpreterClosed(error: Throwable): Boolean {
            var current: Throwable? = error
            while (current != null) {
                val message = current.message.orEmpty()
                if (message.contains("Interpreter has already", ignoreCase = true) ||
                    (message.contains("already been closed", ignoreCase = true) &&
                        message.contains("Interpreter", ignoreCase = true))
                ) {
                    return true
                }
                current = current.cause
            }
            return false
        }
    }
}
