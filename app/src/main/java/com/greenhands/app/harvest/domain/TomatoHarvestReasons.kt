package com.greenhands.app.harvest.domain

/**
 * Human-readable harvest decision reasons. These are evidence explanations,
 * not a claim of 100% accuracy.
 */
object TomatoHarvestReasons {
    const val SCAN_REQUIRED =
        "Scan required. No valid fruit measurement is available."
    const val INSUFFICIENT_FRAME =
        "The captured frame has too few usable pixels for a reliable colour reading."
    const val PREDOMINANTLY_GREEN =
        "Fruit is still predominantly green."
    const val DARK_GREEN =
        "Fruit colour is still dark green."
    const val LIGHT_GREEN =
        "Fruit colour is light green / mature green."
    const val MATURITY_NOT_YET_REACHED =
        "Maturity period has not yet been reached."
    const val DATE_REACHED_STILL_DARK_GREEN =
        "Maturity period has been reached, but the fruit is still dark green. Check again soon."
    const val DATE_REACHED_LIGHT_GREEN =
        "Maturity period has been reached and fruit colour indicates sufficient maturity."
    const val DATE_REACHED_HARVEST_COLOUR =
        "Maturity period has been reached and fruit colour indicates harvest readiness."
    const val EARLY_VISUALLY_RIPE =
        "The fruit appears visually ripe even though the expected maturity date has not yet been reached."
    const val HARVEST_STAGE_COLOR =
        "Fruit color reached harvest-stage transition."
    const val RIPE_RED_COLOR =
        "Fruit color is predominantly red/ripe."
    const val MIXED_COLOR =
        "Fruit color evidence is mixed or uncertain."
    const val MATURITY_REACHED =
        "Maturity period has been reached."
    const val MATURITY_NOT_REACHED =
        MATURITY_NOT_YET_REACHED
    const val WITHIN_WINDOW =
        "Plant is within expected maturity window."
    const val PAST_WINDOW =
        "Plant is past expected maturity window."
    const val NO_DAMAGE =
        "No major visible damage detected."
    const val DARK_SPOTS =
        "Significant dark/spot regions detected."
    const val QUALITY_REVIEW =
        "Visual quality requires inspection."
    const val COLOR_TIME_CONFLICT =
        "Fruit color and maturity-time evidence conflict."
    const val WAITING_DATE =
        "Transplant date is missing, so maturity-time evidence is incomplete."

    fun daysRemainBeforeWindow(days: Int): String =
        "Approximately $days days remain before expected maturity window."
}
