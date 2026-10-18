package com.greenhands.app.harvest.detection

import kotlin.math.abs

/**
 * Validates a detector output against the scan's intended target.
 * Stability is applied separately by [StabilityTracker].
 */
class TargetValidator(
    private val calibration: TargetDetectionCalibration = TargetDetectionCalibration.PROJECT
) {
    fun validate(
        expected: ScanTargetType,
        detections: List<TargetDetection>,
        modelReady: Boolean,
        stable: Boolean = false
    ): TargetValidationResult {
        if (!modelReady) {
            return TargetValidationResult(
                detected = false,
                correctTarget = false,
                confidence = 0f,
                sufficientlyLarge = false,
                centered = false,
                stable = false,
                reason = TargetRejectReason.MODEL_UNAVAILABLE
            )
        }
        val expectedHits = detections.filter { it.targetType == expected }
        val otherHits = detections.filter { it.targetType != expected }
        val candidate = expectedHits.maxByOrNull { it.confidence }
        if (candidate == null) {
            val wrong = otherHits.maxByOrNull { it.confidence }
            return if (wrong != null) {
                TargetValidationResult(
                    detected = true,
                    correctTarget = false,
                    confidence = wrong.confidence,
                    sufficientlyLarge = isLargeEnough(wrong),
                    centered = isCentered(wrong),
                    stable = false,
                    reason = TargetRejectReason.WRONG_TARGET,
                    detection = wrong
                )
            } else {
                TargetValidationResult(
                    detected = false,
                    correctTarget = false,
                    confidence = 0f,
                    sufficientlyLarge = false,
                    centered = false,
                    stable = false,
                    reason = TargetRejectReason.TARGET_NOT_DETECTED
                )
            }
        }
        val large = isLargeEnough(candidate)
        val centered = isCentered(candidate)
        val confident = candidate.confidence >= calibration.minDetectionConfidence
        val reason = when {
            !confident -> TargetRejectReason.LOW_CONFIDENCE
            !large -> TargetRejectReason.TARGET_TOO_SMALL
            !centered -> TargetRejectReason.TARGET_NOT_CENTERED
            !stable -> TargetRejectReason.TARGET_NOT_STABLE
            else -> null
        }
        return TargetValidationResult(
            detected = true,
            correctTarget = true,
            confidence = candidate.confidence,
            sufficientlyLarge = large,
            centered = centered,
            stable = stable && reason == null,
            reason = reason,
            detection = candidate
        )
    }

    private fun isLargeEnough(detection: TargetDetection): Boolean =
        detection.boundingBox.area >= calibration.minTargetAreaRatio

    private fun isCentered(detection: TargetDetection): Boolean {
        val dx = abs(detection.boundingBox.centerX - 0.5f)
        val dy = abs(detection.boundingBox.centerY - 0.5f)
        return dx <= calibration.centerTolerance && dy <= calibration.centerTolerance
    }
}
