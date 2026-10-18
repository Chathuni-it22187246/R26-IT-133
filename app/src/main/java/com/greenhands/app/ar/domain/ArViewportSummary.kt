package com.greenhands.app.ar.domain

import com.greenhands.app.equipment.model.EquipmentRuntimeState
import com.greenhands.app.equipment.model.formatMetres2
import com.greenhands.app.preview3d.model.PreviewMode

/**
 * Compact AR viewport copy. Full crop, climate and equipment details stay in
 * the climate sheet; this line is the collapsed camera control only.
 */
object ArViewportSummary {
    fun onCount(model: ArHudModel): Int =
        model.instanceStatuses.count { it.runtime == EquipmentRuntimeState.ON }

    fun climateLine(model: ArHudModel, mode: PreviewMode): String {
        val crop = model.cropName
        val stage = model.stageName.takeIf {
            it.isNotBlank() && it != ArHudContent.UNAVAILABLE
        }
        val temperature = compactTemperature(model.liveTemperatureLabel)
        val humidity = compactHumidity(model.liveHumidityLabel)
        val trailing = if (mode == PreviewMode.DEMO) {
            "${onCount(model)} ON"
        } else if (
            model.liveStatusLabel == ArHudContent.SENSOR_NOT_CONNECTED ||
            model.liveStatusLabel == ArHudContent.NOT_CONNECTED ||
            model.liveStatusLabel == ArHudContent.SENSOR_CONNECTING ||
            model.liveStatusLabel == ArHudContent.SENSOR_STALE ||
            model.liveStatusLabel == ArHudContent.SENSOR_DISCONNECTED
        ) {
            model.liveStatusLabel
        } else {
            "${onCount(model)} ON"
        }
        return listOfNotNull(crop, stage, temperature, humidity, trailing).joinToString(" · ")
    }

    fun selectedLine(name: String, xM: Double, yM: Double, zM: Double): String =
        "$name · X ${formatMetres2(xM)} · Y ${formatMetres2(yM)} · Z ${formatMetres2(zM)}"

    fun compactTemperature(label: String): String =
        if (label == ArHudContent.LIVE_TEMPERATURE_PLACEHOLDER) {
            label
        } else {
            label.replace(" °C", "°C")
        }

    fun compactHumidity(label: String): String =
        if (label == ArHudContent.LIVE_HUMIDITY_PLACEHOLDER) {
            label
        } else {
            label.replace(" % RH", "% RH")
        }
}
