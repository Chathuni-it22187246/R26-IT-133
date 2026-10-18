package com.greenhands.app.harvest.domain

import com.greenhands.app.harvest.model.FruitColorMeasurement

/**
 * Maps measured fruit HSV bin percentages into broad ripeness evidence.
 *
 * Existing project stages are reused:
 * - DARK_GREEN / LIGHT_GREEN split the former predominantly-green bin using
 *   green-pixel Value on the fruit crop (not background).
 * - GREEN_YELLOW_TRANSITION covers yellow / orange harvest-stage colour.
 * - PREDOMINANTLY_RED_RIPE covers red.
 *
 * Numeric cut-offs come from [TomatoRipenessCalibration.PROJECT].
 */
class TomatoRipenessClassifier(
    private val calibration: TomatoRipenessCalibration = TomatoRipenessCalibration.PROJECT
) {
    fun classify(measurement: FruitColorMeasurement?): RipenessEvidence {
        if (measurement == null || !measurement.hasSamples ||
            measurement.sampledPixelCount < calibration.minSampledPixels
        ) {
            return RipenessEvidence(
                state = TomatoRipenessState.INSUFFICIENT,
                label = "Insufficient measurement",
                reason = TomatoHarvestReasons.INSUFFICIENT_FRAME
            )
        }
        val green = measurement.greenPercent
        val yellow = measurement.yellowPercent
        val red = measurement.redPercent
        val other = measurement.otherPercent

        val predominantlyGreen =
            green >= calibration.predominantlyGreenMinPercent &&
                (yellow + red) <= calibration.immatureYellowPlusRedMaxPercent &&
                green >= yellow &&
                green >= red

        val predominantlyRed =
            red >= calibration.redDominantMinPercent &&
                red > green &&
                red > yellow

        val greenYellowTransition =
            yellow >= calibration.transitionYellowMinPercent &&
                (green + yellow) >= calibration.transitionGreenPlusYellowMinPercent &&
                red < calibration.transitionRedMaxPercent

        val tooMuchOther = other >= calibration.mixedOtherMaxPercent

        val state = when {
            tooMuchOther && !predominantlyGreen && !predominantlyRed && !greenYellowTransition ->
                TomatoRipenessState.MIXED_UNCERTAIN
            predominantlyGreen -> greenShade(measurement)
            predominantlyRed -> TomatoRipenessState.PREDOMINANTLY_RED_RIPE
            greenYellowTransition -> TomatoRipenessState.GREEN_YELLOW_TRANSITION
            else -> TomatoRipenessState.MIXED_UNCERTAIN
        }
        return when (state) {
            TomatoRipenessState.DARK_GREEN -> RipenessEvidence(
                state = state,
                label = "Dark Green",
                reason = TomatoHarvestReasons.DARK_GREEN
            )
            TomatoRipenessState.LIGHT_GREEN -> RipenessEvidence(
                state = state,
                label = "Light Green / Mature Green",
                reason = TomatoHarvestReasons.LIGHT_GREEN
            )
            TomatoRipenessState.GREEN_YELLOW_TRANSITION -> RipenessEvidence(
                state = state,
                label = "Yellow / Orange",
                reason = TomatoHarvestReasons.HARVEST_STAGE_COLOR
            )
            TomatoRipenessState.PREDOMINANTLY_RED_RIPE -> RipenessEvidence(
                state = state,
                label = "Red",
                reason = TomatoHarvestReasons.RIPE_RED_COLOR
            )
            TomatoRipenessState.MIXED_UNCERTAIN -> RipenessEvidence(
                state = state,
                label = "Mixed / Uncertain",
                reason = TomatoHarvestReasons.MIXED_COLOR
            )
            TomatoRipenessState.INSUFFICIENT -> RipenessEvidence(
                state = state,
                label = "Insufficient measurement",
                reason = TomatoHarvestReasons.INSUFFICIENT_FRAME
            )
        }
    }

    private fun greenShade(measurement: FruitColorMeasurement): TomatoRipenessState {
        val greenValue = measurement.greenValueMean ?: measurement.valueMean
        return if (greenValue < calibration.darkGreenMaxValueMean) {
            TomatoRipenessState.DARK_GREEN
        } else {
            TomatoRipenessState.LIGHT_GREEN
        }
    }
}
