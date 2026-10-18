package com.greenhands.app.harvest.domain

/**
 * Crop-level tomato maturity-time window used when a selected variety has no
 * sourced expected_maturity_min_days / expected_maturity_max_days.
 *
 * This is a supporting indicator only. It must not, by itself, produce a
 * READY / NOT READY harvest decision.
 *
 * Days-to-maturity range: University of Maryland Extension, 65–90 days from
 * transplant. Sri Lanka DOA / HORDI describe transplant after the seedling
 * stage and visual harvest readiness at the green-yellow fruit stage; they
 * are not the source of the 65–90 day numbers.
 */
data class GeneralTomatoMaturityReference(
    val minDaysAfterTransplant: Int,
    val maxDaysAfterTransplant: Int,
    val label: String,
    val description: String,
    val daysToMaturitySource: String,
    val localContextNote: String
) {
    companion object {
        const val MIN_DAYS_AFTER_TRANSPLANT = 65
        const val MAX_DAYS_AFTER_TRANSPLANT = 90
        const val LABEL = "General tomato maturity reference"

        val DEFAULT = GeneralTomatoMaturityReference(
            minDaysAfterTransplant = MIN_DAYS_AFTER_TRANSPLANT,
            maxDaysAfterTransplant = MAX_DAYS_AFTER_TRANSPLANT,
            label = LABEL,
            description = "Maturity-time reference after transplant. Supporting indicator only; not a READY / NOT READY harvest decision.",
            daysToMaturitySource = "University of Maryland Extension: tomato days to maturity 65–90 days from transplant.",
            localContextNote = "Sri Lanka Department of Agriculture / HORDI: tomato seedlings are transplanted after the seedling stage; harvest readiness is visually associated with fruit reaching the green-yellow stage. The 65–90 day range is not a DOA figure."
        )
    }
}

enum class MaturityReferenceKind {
    NONE,
    VARIETY_SPECIFIC,
    GENERAL_TOMATO
}

object MaturityReferenceLabels {
    const val VARIETY_SPECIFIC = "Variety-specific maturity reference"
    const val GENERAL_TOMATO = GeneralTomatoMaturityReference.LABEL
}
