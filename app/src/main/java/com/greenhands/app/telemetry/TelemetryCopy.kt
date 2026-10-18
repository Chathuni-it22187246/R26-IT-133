package com.greenhands.app.telemetry

/**
 * Compact My Greenhouse sensor copy. Demo Mode never displays these strings.
 */
object TelemetryCopy {
    const val CONNECTING = "Sensor connecting…"
    const val LIVE = "Live"
    const val STALE = "Sensor data stale"
    const val DISCONNECTED = "Sensor disconnected"
    const val CONNECTION_ERROR = "Sensor disconnected"
    const val RETRY_OR_DEMO =
        "Retry the sensor connection, or use Demo Mode."
    const val AUTHORITATIVE_TEMPERATURE = "temperature_c"
    const val AUTHORITATIVE_HUMIDITY = "humidity_pct"
    const val RAW_HTU21D_TEMPERATURE = "htu21d_temperature_c"
}
