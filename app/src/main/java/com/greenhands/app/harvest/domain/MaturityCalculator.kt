package com.greenhands.app.harvest.domain

import com.greenhands.app.harvest.model.VarietyReference

/**
 * Maturity-time assessment from transplant date and a labelled day window.
 * Does not produce READY / NOT READY harvest decisions.
 *
 * Variety-specific CSV min/max days are used when both are valid sourced
 * integers. Otherwise the general tomato 65–90 day reference is used.
 * Remaining days are calculated only when a transplant date exists.
 */
enum class MaturityTiming {
    DATA_UNAVAILABLE,
    NEEDS_TRANSPLANT_DATE,
    BEFORE_WINDOW,
    WITHIN_WINDOW,
    PAST_WINDOW
}

data class MaturityWindow(
    val minDays: Int,
    val maxDays: Int,
    val kind: MaturityReferenceKind
)

data class MaturityAssessment(
    val timing: MaturityTiming,
    val estimatedDaysRemaining: Int?,
    val expectedMinDays: Int?,
    val expectedMaxDays: Int?,
    val referenceKind: MaturityReferenceKind = MaturityReferenceKind.NONE
) {
    val dataAvailable: Boolean
        get() = expectedMinDays != null && expectedMaxDays != null

    val referenceLabel: String?
        get() = when (referenceKind) {
            MaturityReferenceKind.VARIETY_SPECIFIC -> MaturityReferenceLabels.VARIETY_SPECIFIC
            MaturityReferenceKind.GENERAL_TOMATO -> MaturityReferenceLabels.GENERAL_TOMATO
            MaturityReferenceKind.NONE -> null
        }
}

object MaturityCalculator {
    const val TOMATO_CROP = "Tomato"

    fun hasUsableMaturityWindow(minDays: Int?, maxDays: Int?): Boolean {
        if (minDays == null || maxDays == null) return false
        if (minDays < 0 || maxDays < 0) return false
        return maxDays >= minDays
    }

    fun hasUsableMaturityWindow(variety: VarietyReference?): Boolean =
        variety != null &&
            hasUsableMaturityWindow(
                variety.expectedMaturityMinDays,
                variety.expectedMaturityMaxDays
            )

    fun resolveTomatoWindow(
        variety: VarietyReference?,
        general: GeneralTomatoMaturityReference = GeneralTomatoMaturityReference.DEFAULT
    ): MaturityWindow {
        return if (hasUsableMaturityWindow(variety)) {
            MaturityWindow(
                minDays = variety!!.expectedMaturityMinDays!!,
                maxDays = variety.expectedMaturityMaxDays!!,
                kind = MaturityReferenceKind.VARIETY_SPECIFIC
            )
        } else {
            MaturityWindow(
                minDays = general.minDaysAfterTransplant,
                maxDays = general.maxDaysAfterTransplant,
                kind = MaturityReferenceKind.GENERAL_TOMATO
            )
        }
    }

    /**
     * Tomato crop path: variety-specific sourced days if present, otherwise
     * [GeneralTomatoMaturityReference]. Does not invent CSV row values.
     */
    fun assessTomato(
        daysSinceTransplant: Int?,
        variety: VarietyReference?,
        general: GeneralTomatoMaturityReference = GeneralTomatoMaturityReference.DEFAULT
    ): MaturityAssessment {
        val window = resolveTomatoWindow(variety, general)
        return assess(
            daysSinceTransplant = daysSinceTransplant,
            minDays = window.minDays,
            maxDays = window.maxDays
        ).copy(referenceKind = window.kind)
    }

    fun assess(
        daysSinceTransplant: Int?,
        minDays: Int?,
        maxDays: Int?
    ): MaturityAssessment {
        if (!hasUsableMaturityWindow(minDays, maxDays)) {
            return unavailable()
        }
        val min = minDays!!
        val max = maxDays!!
        if (daysSinceTransplant == null) {
            return MaturityAssessment(
                timing = MaturityTiming.NEEDS_TRANSPLANT_DATE,
                estimatedDaysRemaining = null,
                expectedMinDays = min,
                expectedMaxDays = max
            )
        }
        return when {
            daysSinceTransplant < min -> MaturityAssessment(
                timing = MaturityTiming.BEFORE_WINDOW,
                estimatedDaysRemaining = min - daysSinceTransplant,
                expectedMinDays = min,
                expectedMaxDays = max
            )
            daysSinceTransplant <= max -> MaturityAssessment(
                timing = MaturityTiming.WITHIN_WINDOW,
                estimatedDaysRemaining = 0,
                expectedMinDays = min,
                expectedMaxDays = max
            )
            else -> MaturityAssessment(
                timing = MaturityTiming.PAST_WINDOW,
                estimatedDaysRemaining = 0,
                expectedMinDays = min,
                expectedMaxDays = max
            )
        }
    }

    fun unavailable(): MaturityAssessment = MaturityAssessment(
        timing = MaturityTiming.DATA_UNAVAILABLE,
        estimatedDaysRemaining = null,
        expectedMinDays = null,
        expectedMaxDays = null,
        referenceKind = MaturityReferenceKind.NONE
    )
}
