package com.greenhands.app.decision

/**
 * Public navigation section mapped onto Dasuntha’s existing private
 * [AutomatedActionFilter] chips. Route args only; the Decision UI is unchanged.
 */
enum class DecisionSection(
    val routeArg: String,
    val existingChipLabel: String
) {
    ALL("all", "All"),
    INFECTIONS("infections", "Infections"),
    HEATER_FANS("heater_fans", "Heater/Fans"),
    WATER_PUMP("water_pump", "Water Pump"),
    OTHER("other", "Other actions");

    companion object {
        fun fromRoute(raw: String?): DecisionSection {
            val key = raw?.trim().orEmpty()
            return entries.firstOrNull {
                it.routeArg.equals(key, ignoreCase = true) ||
                    it.name.equals(key, ignoreCase = true)
            } ?: ALL
        }
    }
}

/**
 * Holds the Automated Mode chip selection. Constructed once from the route
 * section; later recompositions must reuse the same instance so a manual chip
 * tap is not reset.
 */
class DecisionFilterSession(initial: DecisionSection) {
    var selected: DecisionSection = initial
        private set

    fun select(next: DecisionSection) {
        selected = next
    }
}

fun applyRouteFilterOnce(
    existing: DecisionFilterSession?,
    fromRoute: DecisionSection
): DecisionFilterSession = existing ?: DecisionFilterSession(fromRoute)
