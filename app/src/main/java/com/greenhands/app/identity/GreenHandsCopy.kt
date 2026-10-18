package com.greenhands.app.identity

/**
 * Canonical GreenHands product copy for Phase 2.1.3.
 * Keep these values aligned with `strings.xml`. Unit tests assert this object
 * so identity cannot silently drift back to climate-planner wording.
 */
object GreenHandsCopy {
    const val APP_NAME = "GreenHands"
    const val PLATFORM_NAME = "AR–IoT Smart Greenhouse Platform"
    const val PLATFORM_LINE = "GreenHands — AR–IoT Smart Greenhouse Platform"

    const val WELCOME_HEADLINE = "Smarter greenhouse decisions, from sensing to harvest"
    const val WELCOME_BODY =
        "GreenHands brings accurate sensor placement, AR heat-distribution visualization, crop health and harvest prediction, and recommended actions into one intelligent greenhouse workspace."

    const val ABOUT_BODY =
        "GreenHands is an AR–IoT Smart Greenhouse Platform designed to help growers monitor greenhouse conditions, optimize sensor placement, visualize heat distribution, assess crop health, predict harvesting time, and receive recommended actions. It brings greenhouse information, augmented-reality visualization, crop analysis and decision support into one mobile workspace."

    const val SENSOR_PLACEMENT =
        "Find optimal sensor positions and identify coverage gaps for reliable greenhouse monitoring."
    const val HEAT_DISTRIBUTION =
        "Visualize greenhouse heat patterns and explore how cooling equipment responds in AR."
    const val HARVESTING =
        "Assess crop health and predict the expected harvesting date."
    const val DECISION_MAKING =
        "Receive recommended decisions and practical actions based on greenhouse and crop conditions."

    const val STATUS_AVAILABLE = "Available"
    const val STATUS_COMING_SOON = "Coming Soon"

    const val PREVIEW_MODE = "Preview Mode"
    const val SAMPLE_VALUES = "Sample values"
    const val PREVIEW_EXPLANATION =
        "Sample values for interface preview. No live greenhouse is connected."

    const val AUTOMATIC_TITLE = "Automatic Calculation — Recommended"
    const val AUTOMATIC_BODY =
        "Equipment thresholds are calculated from the selected crop, growth stage and Day/Night climate targets."
    const val ADVANCED_TITLE = "Advanced Manual Settings"
    const val ADVANCED_BODY =
        "Experienced users can adjust equipment thresholds manually. Values are checked before saving."

    const val SAVE_CONTINUE = "Save & Continue"
    const val SAVE_RETURN_SUMMARY = "Save Changes & Return to Summary"
    const val SAVE_CONTINUE_SIMULATION = "Save & Continue to Simulation"

    fun isClimatePlannerWording(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("climate planner") ||
            lower.contains("greenhouse climate planning") ||
            lower.contains("smarter greenhouse climate planning") ||
            lower.contains("smart-greenhouse climate planning")
    }
}
