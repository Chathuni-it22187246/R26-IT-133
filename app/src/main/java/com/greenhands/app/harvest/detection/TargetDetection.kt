package com.greenhands.app.harvest.detection

data class TargetDetection(
    val targetType: ScanTargetType,
    val confidence: Float,
    val boundingBox: NormalizedRect,
    val timestampMs: Long
)

enum class TargetRejectReason {
    TARGET_NOT_DETECTED,
    WRONG_TARGET,
    LOW_CONFIDENCE,
    TARGET_TOO_SMALL,
    TARGET_NOT_CENTERED,
    TARGET_NOT_STABLE,
    MODEL_UNAVAILABLE,
    NO_FRUIT_LIKE_REGION,
    NO_LEAF_LIKE_REGION,
    INSUFFICIENT_COLOR_EVIDENCE,
    INSUFFICIENT_VEGETATION_EVIDENCE,
    SHAPE_NOT_FRUIT_LIKE,
    SHAPE_NOT_LEAF_LIKE
}

enum class HarvestScanPhase {
    SEARCHING,
    TARGET_DETECTED,
    HOLD_STEADY,
    CAPTURING,
    ANALYZING,
    RESULT,
    MODEL_UNAVAILABLE
}

data class TargetValidationResult(
    val detected: Boolean,
    val correctTarget: Boolean,
    val confidence: Float,
    val sufficientlyLarge: Boolean,
    val centered: Boolean,
    val stable: Boolean,
    val reason: TargetRejectReason?,
    val detection: TargetDetection? = null
) {
    val geometryReady: Boolean
        get() = detected &&
            correctTarget &&
            reason != TargetRejectReason.LOW_CONFIDENCE &&
            sufficientlyLarge &&
            centered

    val readyForManualCapture: Boolean
        get() = geometryReady && reason != TargetRejectReason.MODEL_UNAVAILABLE

    val readyForAutoCapture: Boolean
        get() = readyForManualCapture && stable
}

data class TargetCaptureTick(
    val phase: HarvestScanPhase,
    val validation: TargetValidationResult,
    val detection: TargetDetection?,
    val shouldRequestFocus: Boolean,
    val captureFrame: com.greenhands.app.harvest.domain.HarvestArgbFrame?
) {
    fun <T> withCroppedFrame(block: (com.greenhands.app.harvest.domain.HarvestArgbFrame) -> T): T? =
        captureFrame?.let(block)
}
