package com.greenhands.app.harvest.detection

import android.util.Log
import kotlin.math.abs

/**
 * Generic leaf-like target gate for leaf scan. This is not a tomato-species
 * check and does not identify a specific crop.
 *
 * Accepts a reasonably sized, centered, connected plant region (green,
 * yellow-green, yellow, brown, or necrotic). Pale paper is not treated as
 * the leaf itself. Yellowing / diseased leaves are allowed.
 *
 * Still rejects filled walls, table-like rectangles, tiny noise, and
 * strongly fruit-like circles so fruit and leaf scan modes stay separate.
 *
 * PROJECT CALIBRATION PARAMETERS — FIELD VALIDATION REQUIRED
 */
class HybridLeafValidator(
    private val calibration: HybridTargetCalibration = HybridTargetCalibration.PROJECT
) {
    fun evaluate(features: HybridTargetFeatures): HybridValidationStatus {
        val status = evaluateInternal(features)
        logCandidate(features, status)
        return status
    }

    private fun evaluateInternal(features: HybridTargetFeatures): HybridValidationStatus {
        if (!features.hasCandidate) {
            return if (features.vegetationPixelRatio < calibration.leafMinVegetationRatio) {
                HybridValidationStatus.NO_LEAF_LIKE_REGION
            } else {
                HybridValidationStatus.INSUFFICIENT_VEGETATION_EVIDENCE
            }
        }
        // Pale paper around a leaf must not fail the gate. Require green,
        // yellow-green, yellow, brown, or necrotic pixels — not pale-only.
        if (features.nonPaleVegetationRatio < calibration.leafMinNonPaleRatio) {
            return HybridValidationStatus.INSUFFICIENT_VEGETATION_EVIDENCE
        }
        if (features.areaRatio < calibration.leafMinAreaRatio) {
            return HybridValidationStatus.TARGET_TOO_SMALL
        }
        if (!isReasonablyCentered(
                features.centerX,
                features.centerY,
                calibration.leafCenterTolerance
            )
        ) {
            return HybridValidationStatus.TARGET_NOT_CENTERED
        }
        if (!isLeafLikeGeometry(features)) {
            return HybridValidationStatus.SHAPE_NOT_LEAF_LIKE
        }
        return HybridValidationStatus.VALID_LEAF_TARGET
    }

    /**
     * Generic leaf-like shape: not a filled wall/table, not a tiny spec, not a
     * strongly circular fruit. Aspect near 1 is allowed for a front-on leaf.
     */
    private fun isLeafLikeGeometry(features: HybridTargetFeatures): Boolean {
        if (features.areaRatio > calibration.leafMaxAreaRatio) return false
        if (isRectangularBackground(features)) return false
        val stronglyFruitLike =
            fruitLikeHits(features) >= calibration.leafFruitLikeRequiredIndicators &&
                features.circularity >= calibration.leafFruitLikeRejectMinCircularity
        if (stronglyFruitLike) return false
        return geometryScore(features) >= calibration.leafMinGeometryScore
    }

    private fun isRectangularBackground(features: HybridTargetFeatures): Boolean {
        val largeFilledWall = features.extent >= calibration.leafMaxRectangleExtent &&
            features.areaRatio >= calibration.leafRectangleMinArea
        val solidRectangle = features.extent >= calibration.leafMaxSolidFillExtent
        return largeFilledWall || solidRectangle
    }

    private fun fruitLikeHits(features: HybridTargetFeatures): Int {
        val elongation = features.aspectElongation
        var hits = 0
        if (elongation >= calibration.leafFruitLikeAspectMin &&
            elongation <= calibration.leafFruitLikeAspectMax
        ) {
            hits++
        }
        if (features.extent >= calibration.leafFruitLikeExtentMin &&
            features.extent <= calibration.leafFruitLikeExtentMax
        ) {
            hits++
        }
        if (features.circularity >= calibration.leafFruitLikeMinCircularity) {
            hits++
        }
        return hits
    }

    private fun geometryScore(features: HybridTargetFeatures): Int {
        var score = 0
        if (features.aspectElongation >= calibration.leafMinElongation) score++
        if (features.extent <= calibration.leafIrregularExtentMax) score++
        if (features.extent < calibration.leafMaxSolidFillExtent) score++
        if (features.circularity < calibration.leafFruitLikeMinCircularity) score++
        return score
    }

    private fun logCandidate(features: HybridTargetFeatures, status: HybridValidationStatus) {
        val cxOff = abs(features.centerX - 0.5f)
        val cyOff = abs(features.centerY - 0.5f)
        val message =
            "veg=${fmt(features.vegetationPixelRatio)} nonPale=${fmt(features.nonPaleVegetationRatio)} " +
                "area=${fmt(features.areaRatio)} aspect=${fmt(features.aspectElongation)} " +
                "extent=${fmt(features.extent)} solidity=${fmt(features.solidity)} " +
                "circ=${fmt(features.circularity)} cxOff=${fmt(cxOff)} cyOff=${fmt(cyOff)} " +
                "score=${geometryScore(features)} fruitHits=${fruitLikeHits(features)} " +
                "wall=${isRectangularBackground(features)} status=$status"
        try {
            Log.d(TAG, message)
        } catch (_: Throwable) {
            // android.util.Log is unavailable in plain JVM unit tests.
        }
    }

    private fun fmt(value: Float): String = "%.3f".format(value)

    companion object {
        const val TAG = "HybridLeafDiag"
    }
}
