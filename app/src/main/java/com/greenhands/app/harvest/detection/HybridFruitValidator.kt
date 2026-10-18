package com.greenhands.app.harvest.detection

/**
 * Fruit-scan geometry + color checks. Supports green, turning, yellow/orange,
 * and red tomato-like regions. Ripeness is decided later by the harvest engine.
 */
class HybridFruitValidator(
    private val calibration: HybridTargetCalibration = HybridTargetCalibration.PROJECT
) {
    fun evaluate(features: HybridTargetFeatures): HybridValidationStatus {
        if (!features.hasCandidate) {
            return if (features.tomatoColorPixelRatio < calibration.fruitMinPrimaryColorRatio) {
                HybridValidationStatus.NO_FRUIT_LIKE_REGION
            } else {
                HybridValidationStatus.INSUFFICIENT_COLOR_EVIDENCE
            }
        }
        val primaryFruit = HybridColorClassifier.isPrimaryFruit(features.dominantColorFamily) ||
            features.tomatoColorPixelRatio >= calibration.fruitMinPrimaryColorRatio
        if (!primaryFruit ||
            features.tomatoColorPixelRatio < calibration.fruitMinPrimaryColorRatio
        ) {
            return HybridValidationStatus.INSUFFICIENT_COLOR_EVIDENCE
        }
        if (!isFruitLikeShape(features) || features.areaRatio > calibration.fruitMaxAreaRatio) {
            return HybridValidationStatus.SHAPE_NOT_FRUIT_LIKE
        }
        if (features.areaRatio < calibration.fruitMinAreaRatio) {
            return HybridValidationStatus.TARGET_TOO_SMALL
        }
        if (!isReasonablyCentered(
                features.centerX,
                features.centerY,
                calibration.fruitCenterTolerance
            )
        ) {
            return HybridValidationStatus.TARGET_NOT_CENTERED
        }
        return HybridValidationStatus.VALID_FRUIT_TARGET
    }

    private fun isFruitLikeShape(features: HybridTargetFeatures): Boolean {
        val elongation = features.aspectElongation
        if (elongation > calibration.fruitMaxAspect) return false
        if (features.extent < calibration.fruitMinExtent) return false
        if (features.extent > calibration.fruitMaxExtent) return false
        // Digital circularity is noisy; keep it as a sparse-mask guard only.
        if (features.circularity < calibration.fruitMinCircularity && features.extent < 0.70f) {
            return false
        }
        return true
    }
}
