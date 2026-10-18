package com.greenhands.app.harvest.domain

import com.greenhands.app.harvest.model.VarietyReference

/**
 * Remaining days until the expected maturity window opens.
 * Returns null when min/max days or a transplant date are missing.
 * Zero means the crop is already inside or past the expected window.
 */
class DaysRemainingEstimator {
    fun estimateDaysRemaining(
        daysAfterTransplant: Int? = null,
        expectedMaturityMinDays: Int? = null,
        expectedMaturityMaxDays: Int? = null
    ): Int? = MaturityCalculator.assess(
        daysSinceTransplant = daysAfterTransplant,
        minDays = expectedMaturityMinDays,
        maxDays = expectedMaturityMaxDays
    ).estimatedDaysRemaining

    fun estimateTomatoDaysRemaining(
        daysAfterTransplant: Int?,
        variety: VarietyReference?
    ): Int? = MaturityCalculator.assessTomato(daysAfterTransplant, variety).estimatedDaysRemaining
}
