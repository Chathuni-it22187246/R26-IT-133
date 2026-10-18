package com.greenhands.app.harvest.detection

import android.util.Log
import kotlin.math.exp

/**
 * Temporary on-device inference diagnostics for the YOLOv8 TFLite detector.
 * Filter Logcat with tag [TAG].
 */
object TomatoYoloDiagnostics {
    const val TAG = "TomatoYoloDiag"

    /** Temporary parse floor for logging / weak UI hints. Capture still uses 0.65 validation. */
    const val DEBUG_PARSE_FLOOR = 0.01f
    const val USE_DEBUG_PARSE_FLOOR = true
    const val ENABLED = true

    @Volatile
    private var framesSeen: Int = 0

    fun shouldLog(): Boolean {
        if (!ENABLED) return false
        val n = framesSeen++
        return n < 10 || n % 15 == 0
    }

    fun resetFrameCount() {
        framesSeen = 0
    }

    fun sigmoid(x: Float): Float = (1f / (1f + exp(-x.toDouble()))).toFloat()

    fun looksLikeLogits(minScore: Float, maxScore: Float): Boolean =
        minScore < -0.05f || maxScore > 1.05f

    fun looksLikePixelBoxes(minBox: Float, maxBox: Float): Boolean =
        maxOf(kotlin.math.abs(minBox), kotlin.math.abs(maxBox)) > 1.5f
}
