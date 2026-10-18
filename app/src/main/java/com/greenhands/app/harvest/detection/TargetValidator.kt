package com.greenhands.app.harvest.detection

import kotlin.math.abs

/**
 * Validates a detector output against the scan's intended target.
 * Stability is applied separately by [StabilityTracker].
 *
 * Single Tomato Scan ([ScanTargetType.TOMATO_FRUIT]) uses
 * [SingleTomatoFruitSelector] so implausible / opposite-edge-clamped fruit
 * boxes are not lock candidates. Leaf Scan keeps max-confidence expected
 * hits unchanged. Cluster Scan does not use this validator.
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
        val expectedHits = if (expected == ScanTargetType.TOMATO_FRUIT) {
            SingleTomatoFruitSelector.plausibleFruits(detections)
        } else {
            detections.filter { it.targetType == expected }
        }
        val otherHits = detections.filter { it.targetType != expected }
        val lockReady = expectedHits.filter { hit ->
            hit.confidence >= calibration.minDetectionConfidence &&
                isLargeEnough(hit) &&
                isCentered(hit)
        }
        val candidate = if (expected == ScanTargetType.TOMATO_FRUIT) {
            SingleTomatoFruitSelector.select(detections, calibration)
        } else {
            lockReady.maxByOrNull { it.confidence }
                ?: expectedHits.maxByOrNull { it.confidence }
        }
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
