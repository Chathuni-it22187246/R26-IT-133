package com.greenhands.app.ar

import com.greenhands.app.ar.domain.ArHudContent
import com.greenhands.app.ar.domain.ArViewportSummary
import com.greenhands.app.equipment.model.EquipmentRuntimeState
import com.greenhands.app.equipment.model.EquipmentType
import com.greenhands.app.equipment.ui.OverlayClimate
import com.greenhands.app.heat.domain.HeatFormulas
import com.greenhands.app.heat.model.Crop
import com.greenhands.app.heat.runtime.Phase3D2AFixtures
import com.greenhands.app.heat.visualization.VisualizationCopy
import com.greenhands.app.preview3d.model.PreviewMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArViewportSummaryTest {

    @Test
    fun demoSummaryUsesPublishedValuesAndOnCount() {
        val circ = HeatFormulas.circulation(25.0)
        val climate = OverlayClimate(
            crop = Crop.TOMATO,
            stage = com.greenhands.app.heat.domain.CropDefaults.stage(Crop.TOMATO, "vegetative"),
            temperatureLabel = "25.0°C",
            humidityLabel = "60.0 % RH",
            vpdLabel = "Not published",
            circulation = circ,
            exhaust = HeatFormulas.exhaustFromCirculation(circ),
            fogger = HeatFormulas.fogger(70.0),
            optimalTemperatureC = 25.0,
            optimalHumidityPercent = 60.0
        )
        val fan = Phase3D2AFixtures.instance(EquipmentType.CIRCULATION_FAN, "fan-1", 1, xM = 1.0, yM = 5.0)
        val hud = ArHudContent.from(
            climate,
            PreviewMode.DEMO,
            instances = listOf(fan),
            runtimeById = mapOf(fan.id to EquipmentRuntimeState.ON),
            simulatedTemperatureC = 27.0,
            simulatedHumidityPercent = 60.0,
            visualizationRunning = true
        )
        val line = ArViewportSummary.climateLine(hud, PreviewMode.DEMO)
        assertTrue(line.contains("Tomato"))
        assertTrue(line.contains("Vegetative"))
        assertTrue(line.contains("27.0°C"))
        assertTrue(line.contains("60.0% RH"))
        assertTrue(line.contains("1 ON"))
        assertFalse(line.contains(ArHudContent.LIVE_TEMPERATURE_PLACEHOLDER))
        assertEquals(1, ArViewportSummary.onCount(hud))
    }

    @Test
    fun myGreenhouseDisconnectedSummaryDoesNotInventReadings() {
        val climate = OverlayClimate(
            crop = Crop.TOMATO,
            stage = com.greenhands.app.heat.domain.CropDefaults.stage(Crop.TOMATO, "vegetative"),
            temperatureLabel = "—",
            humidityLabel = "—",
            vpdLabel = "Not published",
            circulation = null,
            exhaust = null,
            fogger = null
        )
        val hud = ArHudContent.from(climate, PreviewMode.MY_GREENHOUSE)
        val line = ArViewportSummary.climateLine(hud, PreviewMode.MY_GREENHOUSE)
        assertTrue(line.contains("Tomato"))
        assertTrue(line.contains(ArHudContent.LIVE_TEMPERATURE_PLACEHOLDER))
        assertTrue(line.contains(ArHudContent.LIVE_HUMIDITY_PLACEHOLDER))
        assertTrue(line.contains(ArHudContent.SENSOR_NOT_CONNECTED))
        assertFalse(line.contains("27.0"))
        assertFalse(line.contains("25.5"))
    }

    @Test
    fun selectedChipUpdatesLiveCoordinates() {
        val line = ArViewportSummary.selectedLine("Circulation Fan 2", 4.71, 8.80, 1.63)
        assertEquals("Circulation Fan 2 · X 4.71 · Y 8.80 · Z 1.63", line)
        val moved = ArViewportSummary.selectedLine("Circulation Fan 2", 4.90, 8.80, 1.63)
        assertTrue(moved.contains("X 4.90"))
        assertFalse(moved.contains("X 4.71"))
    }

    @Test
    fun compactBandLabelsMapApprovedLegendBands() {
        assertEquals("Very high", VisualizationCopy.compactBandLabel(
            com.greenhands.app.heat.visualization.TemperatureLegendBand.EON_OR_ABOVE
        ))
        assertEquals("High", VisualizationCopy.compactBandLabel(
            com.greenhands.app.heat.visualization.TemperatureLegendBand.CON_TO_EON
        ))
        assertEquals("Near target", VisualizationCopy.compactBandLabel(
            com.greenhands.app.heat.visualization.TemperatureLegendBand.TARGET
        ))
        assertEquals("Very cool", VisualizationCopy.compactBandLabel(
            com.greenhands.app.heat.visualization.TemperatureLegendBand.BELOW_CDP
        ))
    }
}
