package com.greenhands.app.heat.runtime

/**
 * Fan-and-pad product copy. Not a manufacturer rating and not a Phase 3A coefficient.
 */
object FanPadCopy {
    const val DEMO_OUTSIDE_WEATHER =
        "Demo simulated outside weather — not measured."
    const val DEMO_PAD_EFFICIENCY =
        "Demo cooling-pad efficiency — illustrative, not manufacturer-rated."
    const val DEMO_EFFICIENCY_ASSUMPTION =
        "Demo-only research-guided assumption — replace with manufacturer or calibrated efficiency."
    const val PREDICTED_UNCALIBRATED = "Predicted — uncalibrated"
    const val COMBINED_FIELD_NOTE =
        "Values come from the combined reduced-order fan-and-pad field."
    const val MISSING_DIMENSIONS =
        "Quantitative pad cooling unavailable — pad width and height are required."
    const val MISSING_WIDTH =
        "Quantitative pad cooling unavailable — pad width is required."
    const val MISSING_HEIGHT =
        "Quantitative pad cooling unavailable — pad height is required."
    const val MISSING_EFFICIENCY =
        "Quantitative pad cooling unavailable — pad efficiency is required."
    const val INVALID_EFFICIENCY =
        "Quantitative pad cooling unavailable — pad efficiency must be in (0, 1]."
    const val MISSING_WEATHER =
        "Quantitative pad cooling unavailable — outside temperature and humidity are missing or stale."
    const val CONFLICTING_EXHAUST_WALLS =
        "Quantitative fan-and-pad prediction unavailable — exhaust fans are on conflicting walls. " +
            "A single pad-to-exhaust path cannot be formed."
    const val EXHAUST_NOT_ON_END_WALL =
        "Quantitative fan-and-pad prediction unavailable — exhaust fans are not on a single greenhouse wall."
    const val PAD_NOT_PRESENT =
        "Cooling pad is not present."
    const val PAD_DISABLED =
        "Fan-and-pad boundary is disabled."
    const val NO_EXHAUST =
        "Quantitative fan-and-pad prediction unavailable — no exhaust fan is placed."
}
