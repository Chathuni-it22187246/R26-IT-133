package com.greenhands.app.harvest.detection

/**
 * Result of project-calibrated visual target validation using color, geometry,
 * position, and size. Temporal stability is applied by [StabilityTracker].
 *
 * Not a CNN / object-recognition label.
 */
enum class HybridValidationStatus {
    VALID_FRUIT_TARGET,
    NO_FRUIT_LIKE_REGION,
    INSUFFICIENT_COLOR_EVIDENCE,
    SHAPE_NOT_FRUIT_LIKE,
    VALID_LEAF_TARGET,
    NO_LEAF_LIKE_REGION,
    INSUFFICIENT_VEGETATION_EVIDENCE,
    SHAPE_NOT_LEAF_LIKE,
    TARGET_TOO_SMALL,
    TARGET_NOT_CENTERED,
    TARGET_NOT_STABLE
}

data class HybridValidationResult(
    val expected: ScanTargetType,
    val status: HybridValidationStatus,
    val features: HybridTargetFeatures,
    val timestampMs: Long,
    val visualScore: Float = 0f
) {
    val isValidTarget: Boolean
        get() = status == HybridValidationStatus.VALID_FRUIT_TARGET ||
            status == HybridValidationStatus.VALID_LEAF_TARGET

    val showGuideBox: Boolean
        get() = isValidTarget ||
            status == HybridValidationStatus.TARGET_TOO_SMALL ||
            status == HybridValidationStatus.TARGET_NOT_CENTERED

    fun toDetection(): TargetDetection? {
        val box = features.boundingBox ?: return null
        if (!showGuideBox && !isValidTarget) return null
        val score = if (isValidTarget) visualScore else 0.20f
        return TargetDetection(
            targetType = expected,
            confidence = score,
            boundingBox = box,
            timestampMs = timestampMs
        )
    }

    fun toTargetValidationResult(stable: Boolean): TargetValidationResult {
        val valid = isValidTarget
        val reason = when {
            valid && !stable -> TargetRejectReason.TARGET_NOT_STABLE
            valid -> null
            else -> status.toRejectReason()
        }
        val detection = when {
            showGuideBox || valid -> toDetection()
            else -> null
        }
        return TargetValidationResult(
            detected = valid ||
                status == HybridValidationStatus.TARGET_TOO_SMALL ||
                status == HybridValidationStatus.TARGET_NOT_CENTERED,
            correctTarget = valid ||
                status == HybridValidationStatus.TARGET_TOO_SMALL ||
                status == HybridValidationStatus.TARGET_NOT_CENTERED,
            confidence = if (valid) visualScore else 0f,
            sufficientlyLarge = valid || status == HybridValidationStatus.TARGET_NOT_CENTERED,
            centered = valid || status == HybridValidationStatus.TARGET_TOO_SMALL,
            stable = valid && stable,
            reason = reason,
            detection = detection
        )
    }
}

fun HybridValidationStatus.toRejectReason(): TargetRejectReason = when (this) {
    HybridValidationStatus.VALID_FRUIT_TARGET,
    HybridValidationStatus.VALID_LEAF_TARGET -> TargetRejectReason.TARGET_NOT_STABLE
    HybridValidationStatus.NO_FRUIT_LIKE_REGION -> TargetRejectReason.NO_FRUIT_LIKE_REGION
    HybridValidationStatus.NO_LEAF_LIKE_REGION -> TargetRejectReason.NO_LEAF_LIKE_REGION
    HybridValidationStatus.INSUFFICIENT_COLOR_EVIDENCE ->
        TargetRejectReason.INSUFFICIENT_COLOR_EVIDENCE
    HybridValidationStatus.INSUFFICIENT_VEGETATION_EVIDENCE ->
        TargetRejectReason.INSUFFICIENT_VEGETATION_EVIDENCE
    HybridValidationStatus.SHAPE_NOT_FRUIT_LIKE -> TargetRejectReason.SHAPE_NOT_FRUIT_LIKE
    HybridValidationStatus.SHAPE_NOT_LEAF_LIKE -> TargetRejectReason.SHAPE_NOT_LEAF_LIKE
    HybridValidationStatus.TARGET_TOO_SMALL -> TargetRejectReason.TARGET_TOO_SMALL
    HybridValidationStatus.TARGET_NOT_CENTERED -> TargetRejectReason.TARGET_NOT_CENTERED
    HybridValidationStatus.TARGET_NOT_STABLE -> TargetRejectReason.TARGET_NOT_STABLE
}
