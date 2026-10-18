package com.greenhands.app.harvest.detection

/**
 * Single Tomato Scan candidate quality. Uses normalized analysis-space boxes
 * (0–1), not preview pixels or device-specific sizes.
 *
 * A box is [severelyClamped] when it sits on two opposite frame edges. That
 * matches YOLO predictions snapped by [NormalizedRect.clamp] across the full
 * width or height. A fruit touching only one edge or a corner is kept.
 *
 * Does not change scores, parse floor (0.25), or lock confidence (0.65).
 * Cluster Scan and Leaf Scan do not use this selector.
 */
object SingleTomatoFruitSelector {
    const val OPPOSITE_EDGE_EPS = 0.02f

    fun isSeverelyClamped(box: NormalizedRect, edgeEps: Float = OPPOSITE_EDGE_EPS): Boolean {
        val spansWidth = box.left <= edgeEps && box.right >= 1f - edgeEps
        val spansHeight = box.top <= edgeEps && box.bottom >= 1f - edgeEps
        return spansWidth || spansHeight
    }

    fun isPlausibleFruitBox(box: NormalizedRect): Boolean =
        box.area > 0f &&
            box.left < box.right &&
            box.top < box.bottom &&
            !isSeverelyClamped(box)

    fun isDrawableFruit(detection: TargetDetection?): Boolean =
        detection != null &&
            detection.targetType == ScanTargetType.TOMATO_FRUIT &&
            isPlausibleFruitBox(detection.boundingBox)

    fun plausibleFruits(detections: List<TargetDetection>): List<TargetDetection> =
        detections.filter { it.targetType == ScanTargetType.TOMATO_FRUIT && isPlausibleFruitBox(it.boundingBox) }

    /**
     * Lock-ready first (confidence, size, center), else highest-score plausible
     * fruit. Never returns a severely clamped box. Never raises a score.
     */
    fun select(
        detections: List<TargetDetection>,
        calibration: TargetDetectionCalibration = TargetDetectionCalibration.PROJECT
    ): TargetDetection? {
        val plausible = plausibleFruits(detections)
        if (plausible.isEmpty()) return null
        val lockReady = plausible.filter { fruit ->
            fruit.confidence >= calibration.minDetectionConfidence &&
                fruit.boundingBox.area >= calibration.minTargetAreaRatio &&
                isCentered(fruit.boundingBox, calibration)
        }
        return lockReady.maxByOrNull { it.confidence }
            ?: plausible.maxByOrNull { it.confidence }
    }

    fun overlayBox(detection: TargetDetection?): NormalizedRect? =
        if (isDrawableFruit(detection)) detection?.boundingBox else null

    fun isCentered(
        box: NormalizedRect,
        calibration: TargetDetectionCalibration = TargetDetectionCalibration.PROJECT
    ): Boolean {
        val dx = kotlin.math.abs(box.centerX - 0.5f)
        val dy = kotlin.math.abs(box.centerY - 0.5f)
        return dx <= calibration.centerTolerance && dy <= calibration.centerTolerance
    }
}
