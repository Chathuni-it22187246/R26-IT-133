package com.greenhands.app.harvest.domain

import com.greenhands.app.harvest.model.FruitColorMeasurement
import com.greenhands.app.harvest.model.HarvestDecision
import com.greenhands.app.harvest.model.HarvestDecisionResult
import com.greenhands.app.harvest.model.LeafHealthResult
import com.greenhands.app.harvest.model.RipenessStage

/**
 * Harvesting crop decision.
 *
 * Missing planting date is blocked before scan. A clearly
 * [TomatoRipenessState.PREDOMINANTLY_RED_RIPE] fruit is READY even before
 * the expected maturity date. Other colours stay NOT READY until that date;
 * after it, dark green stays NOT READY and light green or more advanced
 * colour is READY.
 */
class HarvestDecisionEngine(
    private val ripenessClassifier: TomatoRipenessClassifier = TomatoRipenessClassifier(),
    private val qualityClassifier: TomatoQualityClassifier = TomatoQualityClassifier()
) {
    fun decideTomato(
        measurement: FruitColorMeasurement?,
        maturity: MaturityAssessment
    ): HarvestDecisionResult {
        val ripeness = ripenessClassifier.classify(measurement)
        val quality = qualityClassifier.classify(measurement)

        if (measurement == null ||
            !measurement.hasSamples ||
            ripeness.state == TomatoRipenessState.INSUFFICIENT
        ) {
            val reason = if (measurement == null || !measurement.hasSamples) {
                TomatoHarvestReasons.SCAN_REQUIRED
            } else {
                TomatoHarvestReasons.INSUFFICIENT_FRAME
            }
            return HarvestDecisionResult(
                decision = HarvestDecision.UNCERTAIN,
                ripeness = ripeness,
                quality = quality,
                maturity = maturity,
                reasons = listOf(reason),
                fruitMeasurement = measurement,
                scanRequired = true,
                harvestReason = reason
            )
        }

        val missingDate = maturity.timing == MaturityTiming.NEEDS_TRANSPLANT_DATE ||
            maturity.timing == MaturityTiming.DATA_UNAVAILABLE
        if (missingDate) {
            return HarvestDecisionResult(
                decision = HarvestDecision.UNCERTAIN,
                ripeness = ripeness,
                quality = quality,
                maturity = maturity,
                reasons = listOf(TomatoHarvestReasons.WAITING_DATE),
                fruitMeasurement = measurement,
                harvestReason = TomatoHarvestReasons.WAITING_DATE
            )
        }

        val timeReached = maturity.timing == MaturityTiming.WITHIN_WINDOW ||
            maturity.timing == MaturityTiming.PAST_WINDOW
        val timeBefore = maturity.timing == MaturityTiming.BEFORE_WINDOW
        val clearlyRedRipe = ripeness.state == TomatoRipenessState.PREDOMINANTLY_RED_RIPE

        if (clearlyRedRipe) {
            val harvestReason = if (timeBefore) {
                TomatoHarvestReasons.EARLY_VISUALLY_RIPE
            } else {
                TomatoHarvestReasons.DATE_REACHED_HARVEST_COLOUR
            }
            return HarvestDecisionResult(
                decision = HarvestDecision.READY_TO_HARVEST,
                ripeness = ripeness,
                quality = quality,
                maturity = maturity,
                reasons = buildList {
                    add(harvestReason)
                    addAll(maturityReasons(maturity))
                    add(ripeness.reason)
                    if (quality.state == TomatoQualityState.POOR_DAMAGED ||
                        quality.state == TomatoQualityState.REVIEW_NEEDED
                    ) {
                        add(quality.reason)
                    }
                }.distinct(),
                fruitMeasurement = measurement,
                harvestReason = harvestReason
            )
        }

        if (timeBefore) {
            return HarvestDecisionResult(
                decision = HarvestDecision.NOT_READY,
                ripeness = ripeness,
                quality = quality,
                maturity = maturity,
                reasons = buildList {
                    add(TomatoHarvestReasons.MATURITY_NOT_YET_REACHED)
                    addAll(maturityReasons(maturity))
                    add(ripeness.reason)
                }.distinct(),
                fruitMeasurement = measurement,
                harvestReason = TomatoHarvestReasons.MATURITY_NOT_YET_REACHED
            )
        }

        if (timeReached) {
            val (decision, harvestReason) = when (ripeness.state) {
                TomatoRipenessState.DARK_GREEN ->
                    HarvestDecision.NOT_READY to TomatoHarvestReasons.DATE_REACHED_STILL_DARK_GREEN
                TomatoRipenessState.LIGHT_GREEN ->
                    HarvestDecision.READY_TO_HARVEST to TomatoHarvestReasons.DATE_REACHED_LIGHT_GREEN
                TomatoRipenessState.GREEN_YELLOW_TRANSITION,
                TomatoRipenessState.PREDOMINANTLY_RED_RIPE,
                TomatoRipenessState.MIXED_UNCERTAIN ->
                    HarvestDecision.READY_TO_HARVEST to TomatoHarvestReasons.DATE_REACHED_HARVEST_COLOUR
                TomatoRipenessState.INSUFFICIENT ->
                    HarvestDecision.UNCERTAIN to TomatoHarvestReasons.INSUFFICIENT_FRAME
            }
            return HarvestDecisionResult(
                decision = decision,
                ripeness = ripeness,
                quality = quality,
                maturity = maturity,
                reasons = buildList {
                    add(harvestReason)
                    addAll(maturityReasons(maturity))
                    add(ripeness.reason)
                    if (quality.state == TomatoQualityState.POOR_DAMAGED ||
                        quality.state == TomatoQualityState.REVIEW_NEEDED
                    ) {
                        add(quality.reason)
                    }
                }.distinct(),
                fruitMeasurement = measurement,
                harvestReason = harvestReason
            )
        }

        return HarvestDecisionResult(
            decision = HarvestDecision.UNCERTAIN,
            ripeness = ripeness,
            quality = quality,
            maturity = maturity,
            reasons = listOf(TomatoHarvestReasons.WAITING_DATE),
            fruitMeasurement = measurement,
            harvestReason = TomatoHarvestReasons.WAITING_DATE
        )
    }

    /**
     * Legacy stub kept so existing call sites compile. Tomato fruit scans
     * must use [decideTomato]; this method does not produce a harvest decision.
     */
    @Deprecated("Use decideTomato(measurement, maturity)")
    fun decide(
        ripenessStage: RipenessStage? = null,
        estimatedDaysRemaining: Int? = null,
        leafHealth: LeafHealthResult? = null
    ): HarvestDecision {
        return HarvestDecision.UNCERTAIN
    }

    private fun maturityReasons(maturity: MaturityAssessment): List<String> {
        val reasons = mutableListOf<String>()
        when (maturity.timing) {
            MaturityTiming.WITHIN_WINDOW -> reasons += TomatoHarvestReasons.WITHIN_WINDOW
            MaturityTiming.PAST_WINDOW -> reasons += TomatoHarvestReasons.PAST_WINDOW
            MaturityTiming.BEFORE_WINDOW -> {
                val remaining = maturity.estimatedDaysRemaining
                if (remaining != null) {
                    reasons += TomatoHarvestReasons.daysRemainBeforeWindow(remaining)
                }
            }
            MaturityTiming.NEEDS_TRANSPLANT_DATE,
            MaturityTiming.DATA_UNAVAILABLE ->
                reasons += TomatoHarvestReasons.WAITING_DATE
        }
        return reasons
    }
}
