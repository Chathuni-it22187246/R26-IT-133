package com.greenhands.app.harvest.detection

/**
 * Developer flags for harvest target validation.
 *
 * The production scan path is [HybridTargetValidator]: project-calibrated visual
 * target validation using color, geometry, position, size, and temporal
 * stability. That is not true object recognition.
 *
 * TFLite / YOLO must not block scanning. Enable the experimental detector only
 * while debugging; it is never combined into hybrid validity unless this flag
 * is on.
 */
object HybridScanConfig {
    /**
     * When true and the TFLite asset loads, CameraX uses [TfliteTargetDetector]
     * instead of hybrid validation. Default false — normal user scans work
     * without TFLite detection.
     */
    const val USE_EXPERIMENTAL_TFLITE_DETECTOR: Boolean = false
}
