package com.greenhands.app.harvest.domain

/**
 * PROJECT CALIBRATION PARAMETERS — FIELD VALIDATION REQUIRED
 *
 * Thresholds for the on-device tomato disease classifier.
 * Not DOA / HORDI facts and not a confirmed diagnosis.
 */
data class TomatoDiseaseClassificationCalibration(
    val confidenceThreshold: Float,
    val minTop1Top2Margin: Float,
    val expectedClassCount: Int,
    val targetInputSide: Int
) {
    companion object {
        // PROJECT CALIBRATION PARAMETERS — FIELD VALIDATION REQUIRED
        const val CONFIDENCE_THRESHOLD = 0.70f
        const val MIN_TOP1_TOP2_MARGIN = 0.10f
        const val EXPECTED_CLASS_COUNT = 11
        const val TARGET_INPUT_SIDE = 224

        val PROJECT = TomatoDiseaseClassificationCalibration(
            confidenceThreshold = CONFIDENCE_THRESHOLD,
            minTop1Top2Margin = MIN_TOP1_TOP2_MARGIN,
            expectedClassCount = EXPECTED_CLASS_COUNT,
            targetInputSide = TARGET_INPUT_SIDE
        )
    }
}
