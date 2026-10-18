package com.greenhands.app.harvest.detection

import com.greenhands.app.R

/**
 * Single Tomato Scan copy for validation reasons. Leaf Scan uses the same
 * [TargetRejectReason] values with leaf-specific strings elsewhere.
 */
object SingleTomatoGuidance {
    fun stringRes(reason: TargetRejectReason?): Int? = when (reason) {
        TargetRejectReason.LOW_CONFIDENCE -> R.string.harvest_detect_low_confidence_fruit
        TargetRejectReason.TARGET_NOT_DETECTED -> R.string.harvest_detect_no_fruit
        TargetRejectReason.TARGET_TOO_SMALL -> R.string.harvest_detect_too_small_fruit
        TargetRejectReason.TARGET_NOT_CENTERED -> R.string.harvest_detect_not_centered_fruit
        TargetRejectReason.WRONG_TARGET,
        TargetRejectReason.SHAPE_NOT_FRUIT_LIKE,
        TargetRejectReason.NO_FRUIT_LIKE_REGION,
        TargetRejectReason.INSUFFICIENT_COLOR_EVIDENCE -> R.string.harvest_detect_not_fruit_like
        else -> null
    }
}
