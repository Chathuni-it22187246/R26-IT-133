package com.greenhands.app.decision

fun neededClimateActuator(
    temperatureC: Double?,
    humidityPercent: Double?
): String? {
    val tempOffLow = temperatureC != null && temperatureC < 22.0
    val tempOffHigh = temperatureC != null && temperatureC > 28.0
    val humidityLow = humidityPercent != null && humidityPercent < 55.0
    val humidityHigh = humidityPercent != null && humidityPercent > 75.0
    if (tempOffLow || humidityLow) return "heater"
    if (tempOffHigh || humidityHigh) return "fan"
    return null
}

fun withSingleActiveClimateAction(
    decisions: List<DecisionResponse>,
    temperatureC: Double?,
    humidityPercent: Double?
): List<DecisionResponse> {
    if (temperatureC == null && humidityPercent == null) {
        return decisions
    }
    val needed = neededClimateActuator(temperatureC, humidityPercent)
    var activeIndex = -1
    if (needed != null) {
        for (index in decisions.indices.reversed()) {
            val decision = decisions[index]
            val matches = when (needed) {
                "heater" -> decision.isHeaterAction
                "fan" -> decision.isFanAction
                else -> false
            }
            if (matches) {
                activeIndex = index
                break
            }
        }
    }
    return decisions.mapIndexed { index, decision ->
        if (!decision.isHeaterAction && !decision.isFanAction) {
            decision
        } else {
            decision.copy(lifecycle = if (index == activeIndex) "Active" else "Completed")
        }
    }
}
