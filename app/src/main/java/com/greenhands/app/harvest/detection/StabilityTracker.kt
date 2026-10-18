package com.greenhands.app.harvest.detection

import kotlin.math.abs
import kotlin.math.hypot

/**
 * Counts consecutive frames whose target box stays similar in position and size.
 * Auto-capture must not fire on the first detected frame.
 */
class StabilityTracker(
    private val calibration: TargetDetectionCalibration = TargetDetectionCalibration.PROJECT
) {
    private var last: TargetDetection? = null
    var consecutiveStableFrames: Int = 0
        private set

    fun reset() {
        last = null
        consecutiveStableFrames = 0
    }

    fun update(detection: TargetDetection?): Int {
        if (detection == null) {
            reset()
            return 0
        }
        val previous = last
        last = detection
        if (previous == null || previous.targetType != detection.targetType) {
            consecutiveStableFrames = 1
            return consecutiveStableFrames
        }
        val iou = previous.boundingBox.iou(detection.boundingBox)
        val centerDelta = hypot(
            previous.boundingBox.centerX - detection.boundingBox.centerX,
            previous.boundingBox.centerY - detection.boundingBox.centerY
        )
        val areaDelta = abs(previous.boundingBox.area - detection.boundingBox.area)
        val similar = iou >= calibration.stabilityIouMin &&
            centerDelta <= calibration.stabilityCenterMaxDelta &&
            areaDelta <= calibration.stabilityAreaMaxDelta
        consecutiveStableFrames = if (similar) consecutiveStableFrames + 1 else 1
        return consecutiveStableFrames
    }

    fun isStable(): Boolean =
        consecutiveStableFrames >= calibration.requiredStableFrames
}
