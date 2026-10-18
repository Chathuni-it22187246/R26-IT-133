package com.greenhands.app.harvest.domain

import com.greenhands.app.harvest.model.FruitColorMeasurement

/**
 * Broad visible fruit quality from measured dark/spot share.
 * Does not diagnose disease. Numeric cut-offs are project calibration.
 * Brown/dark HSV bin % is used as a visible damage/spot proxy; there is no
 * separate fruit-damage flag in the current measurement model.
 */
class TomatoQualityClassifier(
    private val calibration: TomatoQualityCalibration = TomatoQualityCalibration.PROJECT
) {
    fun classify(measurement: FruitColorMeasurement?): QualityEvidence {
        if (measurement == null || !measurement.hasSamples) {
            return QualityEvidence(
                state = TomatoQualityState.INSUFFICIENT,
                label = "Insufficient measurement",
                reason = TomatoHarvestReasons.INSUFFICIENT_FRAME
            )
        }
        val brown = measurement.brownDarkPercent
        return when {
            brown >= calibration.poorBrownDarkMinPercent -> QualityEvidence(
                state = TomatoQualityState.POOR_DAMAGED,
                label = "Poor / Damaged",
                reason = TomatoHarvestReasons.DARK_SPOTS
            )
            brown >= calibration.reviewBrownDarkMinPercent -> QualityEvidence(
                state = TomatoQualityState.REVIEW_NEEDED,
                label = "Review Needed",
                reason = TomatoHarvestReasons.QUALITY_REVIEW
            )
            else -> QualityEvidence(
                state = TomatoQualityState.ACCEPTABLE,
                label = "Acceptable",
                reason = TomatoHarvestReasons.NO_DAMAGE
            )
        }
    }
}
