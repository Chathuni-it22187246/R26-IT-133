package com.greenhands.app.harvest.domain

/**
 * Project-calibrated tomato harvest evidence thresholds.
 *
 * Scientific labelling:
 * - Sri Lanka DOA / HORDI provide qualitative harvest-colour guidance:
 *   harvest when fruits reach the green-yellow stage. They do not publish
 *   HSV or colour-percentage cut-offs.
 * - Numeric percentage thresholds below are PROJECT CALIBRATION PARAMETERS
 *   for this research build. They are not DOA facts and are not 100% accurate.
 * - The general 65–90 day maturity window is a supporting time reference,
 *   not a harvest guarantee and not a stand-alone READY / NOT READY rule.
 */
data class TomatoRipenessCalibration(
    val minSampledPixels: Int,
    val predominantlyGreenMinPercent: Float,
    val immatureYellowPlusRedMaxPercent: Float,
    val transitionYellowMinPercent: Float,
    val transitionGreenPlusYellowMinPercent: Float,
    val transitionRedMaxPercent: Float,
    val redDominantMinPercent: Float,
    val mixedOtherMaxPercent: Float,
    val darkGreenMaxValueMean: Float
) {
    companion object {
        /** PROJECT CALIBRATION THRESHOLDS — not DOA numeric facts. */
        val PROJECT = TomatoRipenessCalibration(
            minSampledPixels = 200,
            predominantlyGreenMinPercent = 50f,
            immatureYellowPlusRedMaxPercent = 28f,
            transitionYellowMinPercent = 15f,
            transitionGreenPlusYellowMinPercent = 45f,
            transitionRedMaxPercent = 38f,
            redDominantMinPercent = 40f,
            mixedOtherMaxPercent = 45f,
            // Green-bin Value on the fruit crop. Below this is dark green;
            // at/above is light / mature green. Not a DOA numeric fact.
            darkGreenMaxValueMean = 0.44f
        )
    }
}

data class TomatoQualityCalibration(
    val reviewBrownDarkMinPercent: Float,
    val poorBrownDarkMinPercent: Float
) {
    companion object {
        /** PROJECT CALIBRATION THRESHOLDS — not DOA numeric facts. */
        val PROJECT = TomatoQualityCalibration(
            reviewBrownDarkMinPercent = 12f,
            poorBrownDarkMinPercent = 22f
        )
    }
}

enum class TomatoRipenessState {
    DARK_GREEN,
    LIGHT_GREEN,
    GREEN_YELLOW_TRANSITION,
    PREDOMINANTLY_RED_RIPE,
    MIXED_UNCERTAIN,
    INSUFFICIENT
}

enum class TomatoQualityState {
    ACCEPTABLE,
    REVIEW_NEEDED,
    POOR_DAMAGED,
    INSUFFICIENT
}

data class RipenessEvidence(
    val state: TomatoRipenessState,
    val label: String,
    val reason: String
)

data class QualityEvidence(
    val state: TomatoQualityState,
    val label: String,
    val reason: String
)
