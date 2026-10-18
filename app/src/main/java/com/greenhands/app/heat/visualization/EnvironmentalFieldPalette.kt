package com.greenhands.app.heat.visualization

/**
 * Field-layer colours mapped from the accepted temperature and humidity
 * classification bands. Band boundaries stay in the existing classifiers.
 */
object EnvironmentalFieldPalette {
    fun temperatureColor(band: TemperatureLegendBand): VisualizationRgb = when (band) {
        TemperatureLegendBand.BELOW_CDP -> VisualizationRgb.BLUE
        TemperatureLegendBand.BELOW_TARGET -> VisualizationRgb.CYAN_FIELD
        TemperatureLegendBand.TARGET -> VisualizationRgb.GREEN
        TemperatureLegendBand.ABOVE_TARGET -> VisualizationRgb.YELLOW
        TemperatureLegendBand.CON_TO_EON -> VisualizationRgb.ORANGE
        TemperatureLegendBand.EON_OR_ABOVE -> VisualizationRgb.RED
        TemperatureLegendBand.UNAVAILABLE -> VisualizationRgb.UNAVAILABLE
    }

    fun humidityColor(band: HumidityLegendBand): VisualizationRgb = when (band) {
        HumidityLegendBand.FOGGER_DEMAND -> VisualizationRgb.AMBER
        HumidityLegendBand.APPROACHING_TARGET -> VisualizationRgb.GOLD
        HumidityLegendBand.TARGET_CONTROL -> VisualizationRgb.TEAL
        HumidityLegendBand.ABOVE_CONTROL -> HumidityLegendClassifier.ABOVE
        HumidityLegendBand.UNAVAILABLE -> VisualizationRgb.UNAVAILABLE
    }

    fun colorFor(
        layer: EnvironmentalFieldLayer,
        temperatureBand: TemperatureLegendBand,
        humidityBand: HumidityLegendBand
    ): VisualizationRgb = when (layer) {
        EnvironmentalFieldLayer.TEMPERATURE -> temperatureColor(temperatureBand)
        EnvironmentalFieldLayer.HUMIDITY -> humidityColor(humidityBand)
        EnvironmentalFieldLayer.OFF -> VisualizationRgb.UNAVAILABLE
    }
}
