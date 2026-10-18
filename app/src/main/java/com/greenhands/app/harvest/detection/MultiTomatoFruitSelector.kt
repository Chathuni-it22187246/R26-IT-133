package com.greenhands.app.harvest.detection

/**
 * Filters YOLO fruit boxes for optional cluster analysis.
 * Does not split hybrid colour blobs and does not invent extra detections.
 */
object MultiTomatoFruitSelector {

    fun select(
        detections: List<TargetDetection>,
        calibration: MultiTomatoClusterCalibration = MultiTomatoClusterCalibration.PROJECT
    ): MultiTomatoSelection {
        val accepted = ArrayList<TargetDetection>()
        var rejected = 0
        detections.forEach { detection ->
            if (isValidFruit(detection, calibration)) {
                accepted += detection
            } else {
                rejected++
            }
        }
        val afterNms = YoloV8OutputParser.nms(
            detections = accepted,
            iouThreshold = calibration.nmsIouThreshold,
            maxKeep = calibration.maxFruits
        )
        return MultiTomatoSelection(
            fruits = afterNms,
            suppressedDuplicateCount = (accepted.size - afterNms.size).coerceAtLeast(0),
            rejectedCount = rejected
        )
    }

    fun isValidFruit(
        detection: TargetDetection,
        calibration: MultiTomatoClusterCalibration = MultiTomatoClusterCalibration.PROJECT
    ): Boolean {
        if (detection.targetType != ScanTargetType.TOMATO_FRUIT) return false
        if (detection.confidence < calibration.minDetectionConfidence) return false
        val box = detection.boundingBox
        if (!isFiniteBox(box)) return false
        if (box.left >= box.right || box.top >= box.bottom) return false
        if (box.right <= 0f || box.bottom <= 0f || box.left >= 1f || box.top >= 1f) return false
        val clamped = box.clamp()
        if (clamped.area < calibration.minFruitAreaRatio) return false
        return true
    }

    private fun isFiniteBox(box: NormalizedRect): Boolean =
        box.left.isFinite() &&
            box.top.isFinite() &&
            box.right.isFinite() &&
            box.bottom.isFinite()
}
