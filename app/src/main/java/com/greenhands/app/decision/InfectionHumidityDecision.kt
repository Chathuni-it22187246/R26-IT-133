package com.greenhands.app.decision

import java.util.Locale

internal enum class HumidityBand {
    UNKNOWN,
    LOW,
    OPTIMAL,
    HIGH
}

internal enum class InfectionClimateProfile {
    NONE,
    WET_CANOPY,
    POWDERY,
    VIRUS,
    IRRIGATION,
    OTHER
}

internal enum class GreenhouseResponse {
    MONITORING,
    VENTILATION,
    HUMIDITY_CONTROL,
    IRRIGATION
}

internal data class InfectionHumidityDecision(
    val detected: String,
    val reason: String,
    val action: String,
    val response: GreenhouseResponse
) {
    fun explain(): String = listOf(detected, reason, action).filter { it.isNotBlank() }.joinToString(" ")
}

internal fun humidityBand(humidityPercent: Double?): HumidityBand = when {
    humidityPercent == null || humidityPercent.isNaN() -> HumidityBand.UNKNOWN
    humidityPercent < 55.0 -> HumidityBand.LOW
    humidityPercent > 75.0 -> HumidityBand.HIGH
    else -> HumidityBand.OPTIMAL
}

internal fun infectionClimateProfile(infectionName: String): InfectionClimateProfile {
    val name = infectionName.trim().lowercase(Locale.US)
    if (name.isBlank() || name == "healthy" || name == "none") return InfectionClimateProfile.NONE
    return when {
        name.contains("powdery") -> InfectionClimateProfile.POWDERY
        name.contains("mosaic") || name.contains("yellow leaf curl") || name.contains("virus") ->
            InfectionClimateProfile.VIRUS
        name.contains("blossom") || name.contains("edema") -> InfectionClimateProfile.IRRIGATION
        name.contains("blight") || name.contains("leaf spot") || name.contains("bacterial") ||
            name.contains("gray mold") || name.contains("botrytis") ||
            name.contains("downy") || name.contains("anthracnose") ->
            InfectionClimateProfile.WET_CANOPY
        else -> InfectionClimateProfile.OTHER
    }
}

internal fun catalogGuidance(
    catalog: List<InfectionRecord>,
    plantType: String,
    infectionName: String
): String {
    if (infectionName.isBlank() || infectionClimateProfile(infectionName) == InfectionClimateProfile.NONE) {
        return ""
    }
    val named = catalog.filter { it.infectionShortName.equals(infectionName, ignoreCase = true) }
    val row = named.firstOrNull { it.plantType.equals(plantType, ignoreCase = true) } ?: named.firstOrNull()
    val prevention = row?.preventionSteps?.trim().orEmpty()
    val treatment = row?.treatmentDescription?.trim().orEmpty()
    return when {
        prevention.isNotBlank() -> prevention
        else -> treatment
    }
}

internal fun decideFromInfectionAndHumidity(
    infectionName: String,
    severity: String,
    plantType: String,
    humidityPercent: Double?,
    guidance: String = ""
): InfectionHumidityDecision {
    val profile = infectionClimateProfile(infectionName)
    val band = humidityBand(humidityPercent)
    val response = greenhouseResponse(profile, band)
    val detected = detectedSentence(profile, infectionName, severity, plantType)
    val reason = humidityReason(humidityPercent, band, profile)
    val action = actionSentence(response, profile, guidance)
    return InfectionHumidityDecision(detected, reason, action, response)
}

private fun greenhouseResponse(
    profile: InfectionClimateProfile,
    band: HumidityBand
): GreenhouseResponse {
    if (band == HumidityBand.UNKNOWN) return GreenhouseResponse.MONITORING
    return when (profile) {
        InfectionClimateProfile.NONE -> when (band) {
            HumidityBand.HIGH -> GreenhouseResponse.VENTILATION
            HumidityBand.LOW -> GreenhouseResponse.HUMIDITY_CONTROL
            else -> GreenhouseResponse.MONITORING
        }
        InfectionClimateProfile.WET_CANOPY -> when (band) {
            HumidityBand.HIGH -> GreenhouseResponse.VENTILATION
            HumidityBand.OPTIMAL -> GreenhouseResponse.IRRIGATION
            else -> GreenhouseResponse.MONITORING
        }
        InfectionClimateProfile.POWDERY -> when (band) {
            HumidityBand.HIGH -> GreenhouseResponse.VENTILATION
            else -> GreenhouseResponse.MONITORING
        }
        InfectionClimateProfile.VIRUS -> when (band) {
            HumidityBand.HIGH -> GreenhouseResponse.VENTILATION
            HumidityBand.LOW -> GreenhouseResponse.HUMIDITY_CONTROL
            else -> GreenhouseResponse.MONITORING
        }
        InfectionClimateProfile.IRRIGATION -> GreenhouseResponse.IRRIGATION
        InfectionClimateProfile.OTHER -> when (band) {
            HumidityBand.HIGH -> GreenhouseResponse.VENTILATION
            HumidityBand.LOW -> GreenhouseResponse.HUMIDITY_CONTROL
            else -> GreenhouseResponse.MONITORING
        }
    }
}

private fun detectedSentence(
    profile: InfectionClimateProfile,
    infectionName: String,
    severity: String,
    plantType: String
): String {
    if (profile == InfectionClimateProfile.NONE) return "No infection is recorded."
    val subject = listOf(plantType.trim(), infectionName.trim()).filter { it.isNotEmpty() }.joinToString(" ")
    val severityBit = severity.trim().takeIf { it.isNotEmpty() }?.let { " Severity is $it." }.orEmpty()
    return "Detected $subject.$severityBit"
}

private fun humidityReason(
    humidityPercent: Double?,
    band: HumidityBand,
    profile: InfectionClimateProfile
): String {
    val reading = when (band) {
        HumidityBand.UNKNOWN ->
            "Current humidity is not available from the greenhouse sensors."
        HumidityBand.LOW ->
            "Humidity is ${humidityPercent!!.toInt()}%, below the 55–75% greenhouse band."
        HumidityBand.OPTIMAL ->
            "Humidity is ${humidityPercent!!.toInt()}%, inside the 55–75% greenhouse band."
        HumidityBand.HIGH ->
            "Humidity is ${humidityPercent!!.toInt()}%, above the 55–75% greenhouse band."
    }
    val because = when (profile) {
        InfectionClimateProfile.WET_CANOPY ->
            " This infection is associated with a wet canopy, so humidity decides whether to ventilate or hold irrigation."
        InfectionClimateProfile.POWDERY ->
            " Powdery mildew guidance is to lower relative humidity rather than add moisture."
        InfectionClimateProfile.VIRUS ->
            " This infection is not cured by humidity; humidity only selects the greenhouse climate response."
        InfectionClimateProfile.IRRIGATION ->
            " The recorded condition is managed by irrigation adjustment."
        InfectionClimateProfile.OTHER ->
            " The greenhouse response follows this infection together with the humidity band."
        InfectionClimateProfile.NONE -> ""
    }
    return reading + because
}

private fun actionSentence(
    response: GreenhouseResponse,
    profile: InfectionClimateProfile,
    guidance: String
): String {
    val action = when (response) {
        GreenhouseResponse.VENTILATION -> when (profile) {
            InfectionClimateProfile.WET_CANOPY, InfectionClimateProfile.POWDERY ->
                "Run cooling-fan ventilation to lower humidity, and hold irrigation so the foliage can dry."
            InfectionClimateProfile.NONE ->
                "Run cooling-fan ventilation to bring humidity back into the 55–75% band."
            InfectionClimateProfile.OTHER ->
                "Run cooling-fan ventilation to bring humidity back into the 55–75% band, and keep monitoring this infection."
            else ->
                "Run cooling-fan ventilation to bring humidity back into the 55–75% band, and keep monitoring the infection."
        }
        GreenhouseResponse.HUMIDITY_CONTROL -> when (profile) {
            InfectionClimateProfile.NONE ->
                "Use humidity control to recover toward the 55–75% band."
            InfectionClimateProfile.OTHER ->
                "Use humidity control to recover toward the 55–75% band, and keep monitoring this infection."
            else ->
                "Use humidity control to recover toward the 55–75% band, and keep monitoring. This does not treat the infection itself."
        }
        GreenhouseResponse.IRRIGATION -> when (profile) {
            InfectionClimateProfile.WET_CANOPY ->
                "Hold irrigation so leaves stay dry, and keep monitoring this infection."
            else ->
                "Adjust irrigation to the recorded condition and keep monitoring humidity."
        }
        GreenhouseResponse.MONITORING -> when (profile) {
            InfectionClimateProfile.WET_CANOPY ->
                "Do not raise humidity for this infection. Continue monitoring."
            InfectionClimateProfile.POWDERY ->
                "Do not add moisture. Continue monitoring and avoid overhead irrigation."
            InfectionClimateProfile.NONE ->
                "Continue monitoring. No ventilation, humidity-control, or irrigation change is indicated."
            else ->
                "Continue monitoring. No ventilation, humidity-control, or irrigation change is indicated."
        }
    }
    val recorded = guidance.trim()
    return if (recorded.isEmpty() || profile == InfectionClimateProfile.NONE) {
        action
    } else {
        "$action Recorded guidance: $recorded"
    }
}
