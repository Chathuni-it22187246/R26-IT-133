package com.greenhands.app.harvest.detection

/**
 * Thresholds for the optional cluster path only.
 * Live single-fruit lock still uses [TargetDetectionCalibration.PROJECT].
 */
data class MultiTomatoClusterCalibration(
    val minDetectionConfidence: Float,
    val minFruitAreaRatio: Float,
    val nmsIouThreshold: Float,
    val trackMatchIouMin: Float,
    val maxFruits: Int
) {
    companion object {
        /**
         * Confidence and NMS match the existing YOLO scan calibration.
         * Min area is lower than the single-lock 0.10 ratio because several
         * fruits share one frame; 0.02 still drops tiny noise boxes.
         * Not a labelled-cluster accuracy claim.
         */
        const val MIN_FRUIT_AREA_RATIO = 0.02f

        val PROJECT = MultiTomatoClusterCalibration(
            minDetectionConfidence = TargetDetectionCalibration.MIN_DETECTION_CONFIDENCE,
            minFruitAreaRatio = MIN_FRUIT_AREA_RATIO,
            nmsIouThreshold = TargetDetectionCalibration.NMS_IOU_THRESHOLD,
            trackMatchIouMin = TargetDetectionCalibration.STABILITY_IOU_MIN,
            maxFruits = YoloV8OutputParser.MAX_KEEP
        )
    }
}
