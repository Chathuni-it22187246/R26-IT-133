package com.greenhands.app.harvest.detection

import kotlin.math.abs
import kotlin.math.hypot

/**
 * Read-only Crop Scan fruit-lock diagnostics. Does not update
 * [StabilityTracker] or change capture decisions.
 *
 * [lastBestFruitBox] is the previous frame's highest-scoring fruit box, kept
 * across lock misses so IoU still explains fruit/leaf flicker.
 * [lastLockBox] mirrors the tracker candidate (cleared on lock miss).
 */
object HarvestFruitStabilityDiag {
    const val RESET_NONE = "none"
    const val RESET_FIRST = "FIRST"
    const val RESET_NO_FRUIT = "NO_FRUIT"
    const val RESET_CLAMPED = "CLAMPED"
    const val RESET_WRONG_TARGET = "WRONG_TARGET"
    const val RESET_LOW_CONF = "LOW_CONF"
    const val RESET_TOO_SMALL = "TOO_SMALL"
    const val RESET_OFF_CENTER = "OFF_CENTER"
    const val RESET_IOU = "IOU"
    const val RESET_CENTER = "CENTER"
    const val RESET_AREA = "AREA"

    @Volatile
    private var lastBestFruitBox: NormalizedRect? = null
    @Volatile
    private var lastLockBox: NormalizedRect? = null

    fun reset() {
        lastBestFruitBox = null
        lastLockBox = null
    }

    fun observe(
        detections: List<TargetDetection>,
        selected: TargetDetection?,
        streak: Int,
        calibration: TargetDetectionCalibration = TargetDetectionCalibration.PROJECT
    ): HarvestFruitStabilitySnapshot {
        val fruits = detections.filter { it.targetType == ScanTargetType.TOMATO_FRUIT }
        val leaves = detections.filter { it.targetType == ScanTargetType.TOMATO_LEAF }
        val plausible = SingleTomatoFruitSelector.plausibleFruits(detections)
        val bestFruit = fruits.maxByOrNull { it.confidence }
        val bestPlausible = plausible.maxByOrNull { it.confidence }
        val selectedFruit = selected?.takeIf {
            it.targetType == ScanTargetType.TOMATO_FRUIT &&
                SingleTomatoFruitSelector.isPlausibleFruitBox(it.boundingBox)
        }
        val fruitForBox = selectedFruit ?: bestPlausible
        val box = fruitForBox?.boundingBox
        val prevFruit = lastBestFruitBox
        val iou = if (box != null && prevFruit != null) box.iou(prevFruit) else null
        val confPass = fruitForBox != null &&
            fruitForBox.confidence >= calibration.minDetectionConfidence
        val sizePass = fruitForBox != null &&
            fruitForBox.boundingBox.area >= calibration.minTargetAreaRatio
        val centerPass = fruitForBox != null && isCentered(fruitForBox.boundingBox, calibration)
        val lockReady = confPass && sizePass && centerPass
        val stablePass = lockReady && streak >= calibration.requiredStableFrames
        val reset = resetReason(
            fruitCount = fruits.size,
            plausibleCount = plausible.size,
            leafCount = leaves.size,
            confPass = confPass,
            sizePass = sizePass,
            centerPass = centerPass,
            lockReady = lockReady,
            currentLockBox = if (lockReady) box else null,
            previousLockBox = lastLockBox,
            calibration = calibration
        )
        lastBestFruitBox = box ?: lastBestFruitBox
        lastLockBox = if (lockReady) box else null
        return HarvestFruitStabilitySnapshot(
            fruitSelConf = selectedFruit?.confidence,
            fruitMaxConf = bestFruit?.confidence,
            fruitBox = box,
            fruitArea = box?.area,
            fruitDx = box?.let { it.centerX - 0.5f },
            fruitDy = box?.let { it.centerY - 0.5f },
            fruitIou = iou,
            resetReason = reset,
            gateConf = if (fruitForBox == null) false else confPass,
            gateSize = if (fruitForBox == null) false else sizePass,
            gateCenter = if (fruitForBox == null) false else centerPass,
            gateStable = stablePass
        )
    }

    private fun resetReason(
        fruitCount: Int,
        plausibleCount: Int,
        leafCount: Int,
        confPass: Boolean,
        sizePass: Boolean,
        centerPass: Boolean,
        lockReady: Boolean,
        currentLockBox: NormalizedRect?,
        previousLockBox: NormalizedRect?,
        calibration: TargetDetectionCalibration
    ): String {
        if (plausibleCount == 0) {
            return when {
                fruitCount > 0 -> RESET_CLAMPED
                leafCount > 0 -> RESET_WRONG_TARGET
                else -> RESET_NO_FRUIT
            }
        }
        if (!confPass) return RESET_LOW_CONF
        if (!sizePass) return RESET_TOO_SMALL
        if (!centerPass) return RESET_OFF_CENTER
        if (!lockReady || currentLockBox == null) return RESET_LOW_CONF
        if (previousLockBox == null) return RESET_FIRST
        val iou = previousLockBox.iou(currentLockBox)
        val centerDelta = hypot(
            previousLockBox.centerX - currentLockBox.centerX,
            previousLockBox.centerY - currentLockBox.centerY
        )
        val areaDelta = abs(previousLockBox.area - currentLockBox.area)
        if (iou < calibration.stabilityIouMin) return RESET_IOU
        if (centerDelta > calibration.stabilityCenterMaxDelta) return RESET_CENTER
        if (areaDelta > calibration.stabilityAreaMaxDelta) return RESET_AREA
        return RESET_NONE
    }

    private fun isCentered(box: NormalizedRect, calibration: TargetDetectionCalibration): Boolean {
        val dx = abs(box.centerX - 0.5f)
        val dy = abs(box.centerY - 0.5f)
        return dx <= calibration.centerTolerance && dy <= calibration.centerTolerance
    }
}

data class HarvestFruitStabilitySnapshot(
    val fruitSelConf: Float?,
    val fruitMaxConf: Float?,
    val fruitBox: NormalizedRect?,
    val fruitArea: Float?,
    val fruitDx: Float?,
    val fruitDy: Float?,
    val fruitIou: Float?,
    val resetReason: String,
    val gateConf: Boolean,
    val gateSize: Boolean,
    val gateCenter: Boolean,
    val gateStable: Boolean
) {
    companion object {
        val EMPTY = HarvestFruitStabilitySnapshot(
            fruitSelConf = null,
            fruitMaxConf = null,
            fruitBox = null,
            fruitArea = null,
            fruitDx = null,
            fruitDy = null,
            fruitIou = null,
            resetReason = HarvestFruitStabilityDiag.RESET_NO_FRUIT,
            gateConf = false,
            gateSize = false,
            gateCenter = false,
            gateStable = false
        )
    }
}
