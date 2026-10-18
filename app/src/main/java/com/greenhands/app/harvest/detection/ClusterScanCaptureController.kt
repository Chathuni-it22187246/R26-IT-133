package com.greenhands.app.harvest.detection

/**
 * Stability and one-shot capture for Cluster Scan.
 * Does not run HSV. Live frames only pass already-filtered fruit boxes.
 */
class ClusterScanCaptureController(
    private val calibration: TargetDetectionCalibration = TargetDetectionCalibration.PROJECT,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    private var previous: List<TargetDetection> = emptyList()
    private var consecutiveStableFrames: Int = 0
    private var lastCaptureAtMs: Long = 0L
    private var capturedThisLock: Boolean = false
    private var focusedThisLock: Boolean = false

    fun reset() {
        previous = emptyList()
        consecutiveStableFrames = 0
        capturedThisLock = false
        focusedThisLock = false
    }

    fun onFrame(
        fruits: List<TargetDetection>,
        analyzing: Boolean
    ): ClusterCaptureTick {
        if (fruits.isEmpty()) {
            reset()
            return ClusterCaptureTick(
                fruits = emptyList(),
                phase = HarvestScanPhase.SEARCHING,
                shouldRequestFocus = false,
                capture = false,
                stabilityStreak = 0
            )
        }
        val similar = previous.isNotEmpty() && sameSet(previous, fruits)
        consecutiveStableFrames = if (similar) consecutiveStableFrames + 1 else 1
        previous = fruits
        val now = clock()
        val cooldownElapsed = now - lastCaptureAtMs >= calibration.autoCaptureCooldownMs
        val shouldFocus = !focusedThisLock && consecutiveStableFrames >= calibration.focusMinStreak
        if (shouldFocus) focusedThisLock = true
        val canCapture = !analyzing &&
            !capturedThisLock &&
            cooldownElapsed &&
            consecutiveStableFrames >= calibration.requiredStableFrames
        if (canCapture) {
            capturedThisLock = true
            lastCaptureAtMs = now
            return ClusterCaptureTick(
                fruits = fruits,
                phase = HarvestScanPhase.CAPTURING,
                shouldRequestFocus = shouldFocus,
                capture = true,
                stabilityStreak = consecutiveStableFrames
            )
        }
        val phase = when {
            analyzing -> HarvestScanPhase.ANALYZING
            capturedThisLock -> HarvestScanPhase.RESULT
            consecutiveStableFrames >= calibration.focusMinStreak -> HarvestScanPhase.HOLD_STEADY
            else -> HarvestScanPhase.TARGET_DETECTED
        }
        return ClusterCaptureTick(
            fruits = fruits,
            phase = phase,
            shouldRequestFocus = shouldFocus,
            capture = false,
            stabilityStreak = consecutiveStableFrames
        )
    }

    private fun sameSet(
        previousFruits: List<TargetDetection>,
        currentFruits: List<TargetDetection>
    ): Boolean {
        if (previousFruits.size != currentFruits.size) return false
        val used = BooleanArray(currentFruits.size)
        previousFruits.forEach { previousFruit ->
            var bestIndex = -1
            var bestIou = calibration.stabilityIouMin
            currentFruits.forEachIndexed { index, currentFruit ->
                if (used[index]) return@forEachIndexed
                val iou = previousFruit.boundingBox.iou(currentFruit.boundingBox)
                if (iou >= bestIou) {
                    bestIou = iou
                    bestIndex = index
                }
            }
            if (bestIndex < 0) return false
            used[bestIndex] = true
        }
        return true
    }
}

data class ClusterCaptureTick(
    val fruits: List<TargetDetection>,
    val phase: HarvestScanPhase,
    val shouldRequestFocus: Boolean,
    val capture: Boolean,
    val stabilityStreak: Int = 0
)
