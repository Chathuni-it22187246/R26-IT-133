package com.greenhands.app.harvest.data

import com.greenhands.app.harvest.detection.TargetDetection

/**
 * Geometry from the camera frame that produced the latest completed capture.
 * Used only for diagnostics and cluster identity; not persisted.
 */
data class HarvestScanCaptureInfo(
    val frameWidth: Int,
    val frameHeight: Int,
    val rotationDegrees: Int,
    val detection: TargetDetection? = null,
    val detections: List<TargetDetection> = emptyList()
)
