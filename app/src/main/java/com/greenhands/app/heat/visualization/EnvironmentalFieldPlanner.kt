package com.greenhands.app.heat.visualization

/**
 * Builds a pooled render frame from an immutable field snapshot. Layer and
 * height switches reuse the snapshot and never advance the solver.
 */
object EnvironmentalFieldPlanner {
    fun plan(
        snapshot: EnvironmentalFieldSnapshot?,
        layer: EnvironmentalFieldLayer,
        height: EnvironmentalFieldHeight,
        generation: Int,
        legendExpanded: Boolean
    ): EnvironmentalFieldFrame {
        if (layer == EnvironmentalFieldLayer.OFF) {
            return off(height, snapshot, legendExpanded)
        }
        val crop = snapshot?.cropName ?: VisualizationCopy.COLOUR_UNAVAILABLE
        val stage = snapshot?.stageName ?: VisualizationCopy.COLOUR_UNAVAILABLE
        val thresholds = snapshot?.thresholds ?: EnvironmentalFieldThresholds(
            null, null, null, null, null, null, null
        )
        if (snapshot == null) {
            return unavailable(
                layer,
                height,
                VisualizationCopy.FIELD_MISSING_SNAPSHOT,
                crop,
                stage,
                thresholds,
                legendExpanded
            )
        }
        if (snapshot.identity.generation != generation) {
            return unavailable(
                layer,
                height,
                VisualizationCopy.FIELD_STALE_SNAPSHOT,
                crop,
                stage,
                thresholds,
                legendExpanded,
                snapshot
            )
        }
        if (snapshot.availability == EnvironmentalFieldAvailability.HIDDEN_BEFORE_START) {
            return hiddenBeforeStart(layer, height, snapshot, legendExpanded)
        }
        if (snapshot.availability == EnvironmentalFieldAvailability.UNAVAILABLE) {
            return unavailable(
                layer,
                height,
                snapshot.unavailableReason ?: VisualizationCopy.FIELD_MISSING_SNAPSHOT,
                crop,
                stage,
                thresholds,
                legendExpanded,
                snapshot
            )
        }
        val visible = snapshot.samplesAt(height)
            .filter { sample -> classified(layer, sample) }
            .take(EnvironmentalFieldVisual.MAX_CELLS)
        val stats = statsOf(layer, visible)
        val bands = groupBands(layer, snapshot.lattice, visible)
        val legend = legendOf(
            layer = layer,
            height = height,
            snapshot = snapshot,
            visible = visible,
            stats = stats,
            availability = EnvironmentalFieldAvailability.AVAILABLE,
            message = null,
            expanded = legendExpanded
        )
        return EnvironmentalFieldFrame(
            layer = layer,
            height = height,
            snapshot = snapshot,
            bands = bands,
            visibleSamples = visible,
            minValue = stats.min,
            averageValue = stats.average,
            maxValue = stats.max,
            availability = EnvironmentalFieldAvailability.AVAILABLE,
            message = null,
            legend = legend,
            geometryUpdateReason = EnvironmentalFieldGeometryUpdateReason.SNAPSHOT,
            signature = signature(snapshot, layer, height)
        )
    }

    fun off(
        height: EnvironmentalFieldHeight = EnvironmentalFieldHeight.WHOLE,
        snapshot: EnvironmentalFieldSnapshot? = null,
        legendExpanded: Boolean = false
    ): EnvironmentalFieldFrame = EnvironmentalFieldFrame(
        layer = EnvironmentalFieldLayer.OFF,
        height = height,
        snapshot = snapshot,
        availability = EnvironmentalFieldAvailability.LAYER_OFF,
        legend = EnvironmentalFieldLegendModel.HIDDEN.copy(
            height = height,
            expanded = legendExpanded
        ),
        signature = "off:$height"
    )

    fun hiddenBeforeStart(
        layer: EnvironmentalFieldLayer,
        height: EnvironmentalFieldHeight,
        snapshot: EnvironmentalFieldSnapshot,
        legendExpanded: Boolean
    ): EnvironmentalFieldFrame = EnvironmentalFieldFrame(
        layer = layer,
        height = height,
        snapshot = snapshot,
        availability = EnvironmentalFieldAvailability.HIDDEN_BEFORE_START,
        message = VisualizationCopy.FIELD_START_TO_DISPLAY,
        legend = legendOf(
            layer = layer,
            height = height,
            snapshot = snapshot,
            visible = emptyList(),
            stats = FieldStats(null, null, null),
            availability = EnvironmentalFieldAvailability.HIDDEN_BEFORE_START,
            message = VisualizationCopy.FIELD_START_TO_DISPLAY,
            expanded = legendExpanded
        ),
        geometryUpdateReason = EnvironmentalFieldGeometryUpdateReason.NONE,
        signature = "hidden:$layer:$height:${snapshot.identity.generation}"
    )

    fun unavailable(
        layer: EnvironmentalFieldLayer,
        height: EnvironmentalFieldHeight,
        reason: String,
        cropName: String,
        stageName: String,
        thresholds: EnvironmentalFieldThresholds,
        legendExpanded: Boolean,
        snapshot: EnvironmentalFieldSnapshot? = null
    ): EnvironmentalFieldFrame {
        val legendSnapshot = snapshot ?: EnvironmentalFieldSnapshot.unavailable(
            com.greenhands.app.preview3d.model.PreviewMode.MY_GREENHOUSE,
            reason,
            cropName,
            stageName,
            thresholds = thresholds
        )
        return EnvironmentalFieldFrame(
            layer = layer,
            height = height,
            snapshot = snapshot,
            availability = EnvironmentalFieldAvailability.UNAVAILABLE,
            message = reason,
            legend = legendOf(
                layer = layer,
                height = height,
                snapshot = legendSnapshot,
                visible = emptyList(),
                stats = FieldStats(null, null, null),
                availability = EnvironmentalFieldAvailability.UNAVAILABLE,
                message = reason,
                expanded = legendExpanded
            ),
            signature = "unavailable:$layer:$height:$reason"
        )
    }

    internal fun groupBands(
        layer: EnvironmentalFieldLayer,
        lattice: EnvironmentalFieldLatticePlan,
        samples: List<EnvironmentalFieldSample>
    ): List<EnvironmentalFieldBandMesh> {
        if (layer == EnvironmentalFieldLayer.OFF || samples.isEmpty()) return emptyList()
        val grouped = LinkedHashMap<String, MutableList<EnvironmentalFieldCellBox>>()
        val order = if (layer == EnvironmentalFieldLayer.TEMPERATURE) {
            TemperatureLegendBand.entries.filter { it != TemperatureLegendBand.UNAVAILABLE }
        } else {
            HumidityLegendBand.entries.filter { it != HumidityLegendBand.UNAVAILABLE }
        }
        order.forEach { key -> grouped[bandKey(layer, key)] = ArrayList() }
        var remaining = EnvironmentalFieldVisual.MAX_CELLS
        samples.forEach { sample ->
            if (remaining <= 0) return@forEach
            val key = when (layer) {
                EnvironmentalFieldLayer.TEMPERATURE -> bandKey(layer, sample.temperatureBand)
                EnvironmentalFieldLayer.HUMIDITY -> bandKey(layer, sample.humidityBand)
                EnvironmentalFieldLayer.OFF -> return@forEach
            }
            val cells = grouped[key] ?: return@forEach
            cells += EnvironmentalFieldLattice.cellBox(lattice, sample)
            remaining--
        }
        val meshes = grouped.entries.mapNotNull { (key, cells) ->
            if (cells.isEmpty()) return@mapNotNull null
            val temperatureBand = temperatureBandOf(key)
            val humidityBand = humidityBandOf(key)
            EnvironmentalFieldBandMesh(
                key = key,
                temperatureBand = temperatureBand,
                humidityBand = humidityBand,
                color = EnvironmentalFieldPalette.colorFor(
                    layer,
                    temperatureBand ?: TemperatureLegendBand.UNAVAILABLE,
                    humidityBand ?: HumidityLegendBand.UNAVAILABLE
                ).withAlpha(EnvironmentalFieldAppearance.MATERIAL_ALPHA),
                cells = cells
            )
        }
        return meshes.take(EnvironmentalFieldVisual.MAX_GEOMETRY_NODES)
    }

    fun signature(
        snapshot: EnvironmentalFieldSnapshot,
        layer: EnvironmentalFieldLayer,
        height: EnvironmentalFieldHeight
    ): String = listOf(
        snapshot.identity.generation.toString(),
        snapshot.identity.elapsedTimeS.toString(),
        snapshot.identity.physicsSnapshotIdentity.toString(),
        layer.name,
        height.name,
        snapshot.cellCount.toString()
    ).joinToString(":")

    private fun classified(layer: EnvironmentalFieldLayer, sample: EnvironmentalFieldSample): Boolean =
        when (layer) {
            EnvironmentalFieldLayer.TEMPERATURE ->
                sample.temperatureBand != TemperatureLegendBand.UNAVAILABLE && sample.hasFiniteTemperature
            EnvironmentalFieldLayer.HUMIDITY ->
                sample.humidityBand != HumidityLegendBand.UNAVAILABLE && sample.hasFiniteRelativeHumidity
            EnvironmentalFieldLayer.OFF -> false
        }

    private fun statsOf(layer: EnvironmentalFieldLayer, samples: List<EnvironmentalFieldSample>): FieldStats {
        val values = samples.mapNotNull { sample ->
            when (layer) {
                EnvironmentalFieldLayer.TEMPERATURE -> sample.temperatureC?.takeIf { it.isFinite() }
                EnvironmentalFieldLayer.HUMIDITY -> sample.relativeHumidityPercent?.takeIf { it.isFinite() }
                EnvironmentalFieldLayer.OFF -> null
            }
        }
        if (values.isEmpty()) return FieldStats(null, null, null)
        return FieldStats(values.min(), values.sum() / values.size, values.max())
    }

    private fun legendOf(
        layer: EnvironmentalFieldLayer,
        height: EnvironmentalFieldHeight,
        snapshot: EnvironmentalFieldSnapshot,
        visible: List<EnvironmentalFieldSample>,
        stats: FieldStats,
        availability: EnvironmentalFieldAvailability,
        message: String?,
        expanded: Boolean
    ): EnvironmentalFieldLegendModel {
        val temperature = layer != EnvironmentalFieldLayer.HUMIDITY
        val bands = if (temperature) {
            TemperatureLegendBand.entries
                .filter { it != TemperatureLegendBand.UNAVAILABLE }
                .map { band ->
                    EnvironmentalFieldLegendBand(
                        name = VisualizationCopy.fieldTemperatureBandLabel(band),
                        rangeText = EnvironmentalFieldBuilder.temperatureRangeText(band, snapshot.thresholds),
                        color = EnvironmentalFieldPalette.temperatureColor(band)
                    )
                }
        } else {
            HumidityLegendBand.entries
                .filter { it != HumidityLegendBand.UNAVAILABLE }
                .map { band ->
                    EnvironmentalFieldLegendBand(
                        name = VisualizationCopy.fieldHumidityBandLabel(band),
                        rangeText = EnvironmentalFieldBuilder.humidityRangeText(band, snapshot.thresholds),
                        color = EnvironmentalFieldPalette.humidityColor(band)
                    )
                }
        }
        val viewStats = if (visible.isEmpty() && availability == EnvironmentalFieldAvailability.AVAILABLE) {
            if (temperature) {
                FieldStats(
                    snapshot.minTemperatureC,
                    snapshot.averageTemperatureC,
                    snapshot.maxTemperatureC
                )
            } else {
                FieldStats(
                    snapshot.minRelativeHumidityPercent,
                    snapshot.averageRelativeHumidityPercent,
                    snapshot.maxRelativeHumidityPercent
                )
            }
        } else {
            stats
        }
        return EnvironmentalFieldLegendModel(
            layer = layer,
            height = height,
            title = if (temperature) {
                VisualizationCopy.FIELD_TEMPERATURE_TITLE
            } else {
                VisualizationCopy.FIELD_HUMIDITY_TITLE
            },
            unit = if (temperature) VisualizationCopy.FIELD_UNIT_C else VisualizationCopy.FIELD_UNIT_RH,
            cropName = snapshot.cropName,
            stageName = snapshot.stageName,
            predictionLabel = VisualizationCopy.PREDICTED_UNCALIBRATED,
            bands = bands,
            minValue = viewStats.min,
            averageValue = viewStats.average,
            maxValue = viewStats.max,
            availability = availability,
            message = message,
            expanded = expanded
        )
    }

    private fun bandKey(layer: EnvironmentalFieldLayer, band: Enum<*>): String = "${layer.name}:${band.name}"

    private fun temperatureBandOf(key: String): TemperatureLegendBand? {
        val name = key.substringAfter(':')
        return TemperatureLegendBand.entries.firstOrNull { it.name == name }
    }

    private fun humidityBandOf(key: String): HumidityLegendBand? {
        val name = key.substringAfter(':')
        return HumidityLegendBand.entries.firstOrNull { it.name == name }
    }

    private data class FieldStats(
        val min: Double?,
        val average: Double?,
        val max: Double?
    )
}
