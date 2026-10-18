package com.greenhands.app.harvest.detection

import com.greenhands.app.harvest.domain.HarvestArgbFrame

/**
 * Project-calibrated visual target validation using color, geometry, position,
 * size, and (via [TargetAutoCaptureController]) temporal stability.
 *
 * This is not true object recognition. It does not use a CNN for the primary
 * scan path and does not claim object-recognition accuracy.
 */
class HybridTargetValidator(
    private val calibration: HybridTargetCalibration = HybridTargetCalibration.PROJECT,
    private val extractor: HybridFeatureExtractor = HybridFeatureExtractor(calibration),
    private val fruitValidator: HybridFruitValidator = HybridFruitValidator(calibration),
    private val leafValidator: HybridLeafValidator = HybridLeafValidator(calibration)
) {
    fun validate(
        frame: HarvestArgbFrame,
        expected: ScanTargetType,
        timestampMs: Long
    ): HybridValidationResult {
        val features = extractor.extract(frame, expected)
        val status = when (expected) {
            ScanTargetType.TOMATO_FRUIT -> fruitValidator.evaluate(features)
            ScanTargetType.TOMATO_LEAF -> leafValidator.evaluate(features)
        }
        val score = if (
            status == HybridValidationStatus.VALID_FRUIT_TARGET ||
            status == HybridValidationStatus.VALID_LEAF_TARGET
        ) {
            calibration.visualScoreWhenValid
        } else {
            0f
        }
        return HybridValidationResult(
            expected = expected,
            status = status,
            features = features,
            timestampMs = timestampMs,
            visualScore = score
        )
    }
}

/**
 * Optional [TargetDetector] wrapper around [HybridTargetValidator].
 * Used when a detector-shaped API is convenient; TFLite is not required.
 */
class HybridVisualDetector(
    private val expected: ScanTargetType,
    private val validator: HybridTargetValidator = HybridTargetValidator()
) : TargetDetector {
    override val isModelReady: Boolean = true

    override fun detect(frame: HarvestArgbFrame, timestampMs: Long): List<TargetDetection> {
        val result = validator.validate(frame, expected, timestampMs)
        return listOfNotNull(result.toDetection().takeIf { result.isValidTarget })
    }
}
