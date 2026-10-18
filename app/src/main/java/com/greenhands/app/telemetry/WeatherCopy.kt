package com.greenhands.app.telemetry

/**
 * Compact My Greenhouse modelled-weather copy. Demo Mode never displays these strings.
 * Outside weather is an Open-Meteo model, not an IoT sensor measurement.
 */
object WeatherCopy {
    const val CONNECTING = "Weather connecting…"
    const val LIVE = TelemetryCopy.LIVE
    const val STALE = "Weather data stale"
    const val UNAVAILABLE = "Weather unavailable"
    const val MODELLED_OPEN_METEO = "Modelled weather · Open-Meteo"
    const val SOURCE_OPEN_METEO = "Open-Meteo"
    const val AUTHORITATIVE_TEMPERATURE = "outside_temperature_c"
    const val AUTHORITATIVE_HUMIDITY = "outside_humidity_pct"
    const val AUTHORITATIVE_INDOOR_TEMPERATURE = TelemetryCopy.AUTHORITATIVE_TEMPERATURE
    const val AUTHORITATIVE_INDOOR_HUMIDITY = TelemetryCopy.AUTHORITATIVE_HUMIDITY
}

object OpenMeteoAcceptance {
    const val MIN_C = -60.0
    const val MAX_C = 60.0
}
