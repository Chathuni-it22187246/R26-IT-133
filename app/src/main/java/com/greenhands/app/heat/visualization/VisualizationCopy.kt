package com.greenhands.app.heat.visualization

object VisualizationCopy {
    const val CIRCULATION_AIRFLOW_UNAVAILABLE =
        "Circulation airflow unavailable — predicted probe data not available."
    const val COLOUR_UNAVAILABLE = "Unavailable"
    const val CONTROL_BAND_DISCLAIMER =
        "Temperature colours are control/visualization bands, not crop-safety limits."
    const val PREDICTED_UNCALIBRATED = "Predicted — uncalibrated"
    const val CIRCULATION_PROBE_CAPTION =
        "Predicted — uncalibrated along circulation airflow. Demo simulation — not a live IoT reading. " +
            "These are predicted field values, not cooling caused solely by the fan."
    const val CIRCULATION_PROBE_CHIP = "Predicted · 5 s"
    const val EXHAUST_COMBINED_FIELD_NOTE =
        "Values come from the combined reduced-order field."
    const val FAN_PAD_COMBINED_FIELD_NOTE =
        com.greenhands.app.heat.runtime.FanPadCopy.COMBINED_FIELD_NOTE
    const val FAN_PAD_PREDICTED_UNCALIBRATED =
        com.greenhands.app.heat.runtime.FanPadCopy.PREDICTED_UNCALIBRATED
    const val EXHAUST_PROBE_CAPTION =
        "Predicted — uncalibrated along the exhaust inward axis. " +
            com.greenhands.app.heat.runtime.PhysicsRuntimeCopy.DEMO_SIMULATED_OUTSIDE_BOUNDARY +
            " " +
            EXHAUST_COMBINED_FIELD_NOTE +
            " Values are not a hand-written cooling curve."
    const val EXHAUST_PROBE_CHIP = "Predicted · 5 s"
    const val EXHAUST_QUALITATIVE_DISCHARGE =
        "Outdoor arrows show qualitative discharge direction only."
    const val BAND_VERY_HIGH = "Very high"
    const val BAND_HIGH = "High"
    const val BAND_WARM = "Warm"
    const val BAND_NEAR_TARGET = "Near target"
    const val BAND_COOL = "Cool"
    const val BAND_VERY_COOL = "Very cool"
    const val FOG_MIST_QUALITATIVE =
        "Fog mist is a qualitative visualization. Plume angle and range come from the accepted fogger specification. Temperature and humidity values are " +
            PREDICTED_UNCALIBRATED +
            "."
    const val FOGGER_VISUALIZATION_UNAVAILABLE =
        "Fog visualization unavailable — predicted fogger data not available."
    const val HUMIDITY_BAND_DEMAND = "Fogger demand"
    const val HUMIDITY_BAND_APPROACHING = "Approaching target"
    const val HUMIDITY_BAND_TARGET = "Target/control band"
    const val HUMIDITY_BAND_ABOVE = "Above control band"
    const val FOG_DETAILS_TITLE = "Fog details"
    const val AIRFLOW_FOG_VISIBLE = "Airflow / Fog"
    const val QUANTITATIVE_READY = "Quantitatively ready"
    const val QUANTITATIVE_UNAVAILABLE = "Quantitative visualization unavailable"

    fun compactHumidityLabel(band: HumidityLegendBand): String = when (band) {
        HumidityLegendBand.FOGGER_DEMAND -> HUMIDITY_BAND_DEMAND
        HumidityLegendBand.APPROACHING_TARGET -> HUMIDITY_BAND_APPROACHING
        HumidityLegendBand.TARGET_CONTROL -> HUMIDITY_BAND_TARGET
        HumidityLegendBand.ABOVE_CONTROL -> HUMIDITY_BAND_ABOVE
        HumidityLegendBand.UNAVAILABLE -> COLOUR_UNAVAILABLE
    }

    const val FIELD_TEMPERATURE_TITLE = "Temperature Distribution"
    const val FIELD_HUMIDITY_TITLE = "Relative Humidity Distribution"
    const val FIELD_UNIT_C = "°C"
    const val FIELD_UNIT_RH = "%RH"
    const val FIELD_LAYER_OFF = "Off"
    const val FIELD_LAYER_TEMPERATURE = "Temperature"
    const val FIELD_LAYER_HUMIDITY = "Humidity"
    const val FIELD_HEIGHT_WHOLE = "Whole"
    const val FIELD_HEIGHT_CROP = "Crop"
    const val FIELD_HEIGHT_MIDDLE = "Middle"
    const val FIELD_HEIGHT_UPPER = "Upper/Roof"
    const val FIELD_START_TO_DISPLAY =
        "Start Visualization to display the predicted field"
    const val FIELD_MY_GREENHOUSE_UNAVAILABLE =
        "Predicted field unavailable — My Greenhouse has no connected physics snapshot. Demo values are not used."
    const val FIELD_MISSING_SNAPSHOT =
        "Predicted field unavailable — no accepted simulation snapshot."
    const val FIELD_MISSING_THRESHOLDS =
        "Predicted field unavailable — crop or growth-stage thresholds are missing."
    const val FIELD_INVALID_DIMENSIONS =
        "Predicted field unavailable — greenhouse dimensions are missing or invalid."
    const val FIELD_STALE_SNAPSHOT =
        "Predicted field unavailable — snapshot is stale."
    const val FIELD_NON_FINITE =
        "Predicted field unavailable — field values are not finite."
    const val FIELD_BAND_OPTIMAL = "Optimal"
    const val FIELD_BAND_CRITICAL = "Critical heat"
    const val FIELD_BAND_HIGH_HEAT = "High heat"
    const val FIELD_HUMIDITY_TOO_DRY = "Too dry"
    const val FIELD_HUMIDITY_HUMID = "Humid"
    const val FIELD_HUMIDITY_HIGH = "High humidity"
    const val LIVE_IOT_FORBIDDEN_LABEL = "Live IoT"

    fun fieldTemperatureBandLabel(band: TemperatureLegendBand): String = when (band) {
        TemperatureLegendBand.TARGET -> FIELD_BAND_OPTIMAL
        TemperatureLegendBand.CON_TO_EON -> FIELD_BAND_HIGH_HEAT
        TemperatureLegendBand.EON_OR_ABOVE -> FIELD_BAND_CRITICAL
        else -> compactBandLabel(band)
    }

    fun fieldHumidityBandLabel(band: HumidityLegendBand): String = when (band) {
        HumidityLegendBand.FOGGER_DEMAND -> FIELD_HUMIDITY_TOO_DRY
        HumidityLegendBand.APPROACHING_TARGET -> HUMIDITY_BAND_APPROACHING
        HumidityLegendBand.TARGET_CONTROL -> FIELD_BAND_OPTIMAL
        HumidityLegendBand.ABOVE_CONTROL -> FIELD_HUMIDITY_HIGH
        HumidityLegendBand.UNAVAILABLE -> COLOUR_UNAVAILABLE
    }

    fun compactBandLabel(band: TemperatureLegendBand): String = when (band) {
        TemperatureLegendBand.EON_OR_ABOVE -> BAND_VERY_HIGH
        TemperatureLegendBand.CON_TO_EON -> BAND_HIGH
        TemperatureLegendBand.ABOVE_TARGET -> BAND_WARM
        TemperatureLegendBand.TARGET -> BAND_NEAR_TARGET
        TemperatureLegendBand.BELOW_TARGET -> BAND_COOL
        TemperatureLegendBand.BELOW_CDP -> BAND_VERY_COOL
        TemperatureLegendBand.UNAVAILABLE -> COLOUR_UNAVAILABLE
    }

    fun exhaustProbeCaption(
        boundarySourceLabel: String?,
        fanPadSourceLabel: String? = null,
        demoSimulation: Boolean = false
    ): String {
        val boundary = boundarySourceLabel?.takeIf { it.isNotBlank() }
            ?: com.greenhands.app.heat.runtime.PhysicsRuntimeCopy.DEMO_SIMULATED_OUTSIDE_BOUNDARY
        val pad = fanPadSourceLabel?.takeIf { it.isNotBlank() }
        val field = if (pad != null) FAN_PAD_COMBINED_FIELD_NOTE else EXHAUST_COMBINED_FIELD_NOTE
        val parts = ArrayList<String>()
        parts += PREDICTED_UNCALIBRATED
        if (demoSimulation) {
            parts += com.greenhands.app.heat.runtime.PhysicsRuntimeCopy.DEMO_SIMULATION_DISCLAIMER
            if (com.greenhands.app.heat.runtime.PhysicsRuntimeCopy.DEMO_SIMULATED_OUTSIDE_BOUNDARY !in boundary) {
                parts += com.greenhands.app.heat.runtime.PhysicsRuntimeCopy.DEMO_SIMULATED_OUTSIDE_BOUNDARY
            }
        }
        parts += boundary
        if (pad != null) parts += pad
        parts += field
        return parts.joinToString(" ")
    }
}
