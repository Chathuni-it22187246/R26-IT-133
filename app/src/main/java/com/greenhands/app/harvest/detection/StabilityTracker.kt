package com.greenhands.app.harvest.detection

import kotlin.math.abs
import kotlin.math.hypot

/**
 * Counts consecutive frames whose target box stays similar in position and size.
 * Auto-capture must not fire on the first detected frame.
 *
 * When [pauseOnSingleMiss] is true (Single Tomato Scan only), one failed
 * frame keeps the current streak without incrementing it. Two consecutive
 * failed frames reset. Capture still requires a lock-ready current frame.
 */
class StabilityTracker(
    private val calibration: TargetDetectionCalibration = TargetDetectionCalibration.PROJECT,
    private val pauseOnSingleMiss: Boolean = false
) {
    private var last: TargetDetection? = null
    private var consecutiveMisses: Int = 0
    var consecutiveStableFrames: Int = 0
        private set
    var paused: Boolean = false
        private set

    fun reset() {
        last = null
        consecutiveStableFrames = 0
        consecutiveMisses = 0
        paused = false
    }

    fun update(detection: TargetDetection?): Int {
        if (detection == null) {
            if (!pauseOnSingleMiss) {
                reset()
                return 0
            }
            consecutiveMisses += 1
            if (consecutiveMisses >= 2) {
                reset()
                return 0
            }
            paused = true
            return consecutiveStableFrames
        }
        consecutiveMisses = 0
        paused = false
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
