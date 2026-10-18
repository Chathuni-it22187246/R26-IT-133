package com.greenhands.app.harvest.detection

/**
 * PROJECT CALIBRATION parameters for on-device target detection and auto-capture.
 *
 * These are research-build thresholds for this app, not Sri Lanka DOA / HORDI
 * scientific facts, and not a claim that detection is 100% accurate.
 */
data class TargetDetectionCalibration(
    val minDetectionConfidence: Float,
    val minTargetAreaRatio: Float,
    val centerTolerance: Float,
    val requiredStableFrames: Int,
    val stabilityIouMin: Float,
    val stabilityCenterMaxDelta: Float,
    val stabilityAreaMaxDelta: Float,
    val autoCaptureCooldownMs: Long,
    val cropPaddingRatio: Float,
    val detectorScoreFloor: Float,
    val focusMinStreak: Int,
    val nmsIouThreshold: Float
) {
    companion object {
        const val MIN_DETECTION_CONFIDENCE = 0.65f
        const val MIN_TARGET_AREA_RATIO = 0.10f
        const val CENTER_TOLERANCE = 0.22f
        const val REQUIRED_STABLE_FRAMES = 8
        const val STABILITY_IOU_MIN = 0.55f
        const val STABILITY_CENTER_MAX_DELTA = 0.05f
        const val STABILITY_AREA_MAX_DELTA = 0.08f
        const val AUTO_CAPTURE_COOLDOWN_MS = 2500L
        const val CROP_PADDING_RATIO = 0.10f
        const val DETECTOR_SCORE_FLOOR = 0.25f
        const val FOCUS_MIN_STREAK = 2
        const val ANALYSIS_MAX_SIDE = 640
        const val NMS_IOU_THRESHOLD = 0.45f

        val PROJECT = TargetDetectionCalibration(
            minDetectionConfidence = MIN_DETECTION_CONFIDENCE,
            minTargetAreaRatio = MIN_TARGET_AREA_RATIO,
            centerTolerance = CENTER_TOLERANCE,
            requiredStableFrames = REQUIRED_STABLE_FRAMES,
            stabilityIouMin = STABILITY_IOU_MIN,
            stabilityCenterMaxDelta = STABILITY_CENTER_MAX_DELTA,
            stabilityAreaMaxDelta = STABILITY_AREA_MAX_DELTA,
            autoCaptureCooldownMs = AUTO_CAPTURE_COOLDOWN_MS,
            cropPaddingRatio = CROP_PADDING_RATIO,
            detectorScoreFloor = DETECTOR_SCORE_FLOOR,
            focusMinStreak = FOCUS_MIN_STREAK,
            nmsIouThreshold = NMS_IOU_THRESHOLD
        )
    }
}
