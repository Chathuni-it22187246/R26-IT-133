package com.greenhands.app.heat.visualization

import com.greenhands.app.equipment.model.DomainPoint
import com.greenhands.app.heat.model.formatOneDecimal
import com.greenhands.app.heat.runtime.contracts.PhysicsFieldSnapshot
import com.greenhands.app.preview3d.model.PreviewMode

enum class EnvironmentalFieldLayer {
    OFF,
    TEMPERATURE,
    HUMIDITY
}

enum class EnvironmentalFieldHeight {
    WHOLE,
    CROP,
    MIDDLE,
    UPPER
}

enum class EnvironmentalFieldLevel {
    CROP,
    MIDDLE,
    UPPER
}

enum class EnvironmentalFieldAvailability {
    AVAILABLE,
    HIDDEN_BEFORE_START,
    LAYER_OFF,
    UNAVAILABLE
}

enum class EnvironmentalFieldGeometryUpdateReason {
    NONE,
    SNAPSHOT,
    VIEW
}

data class EnvironmentalFieldIdentity(
    val elapsedTimeS: Double,
    val generation: Int,
    val physicsSnapshotIdentity: Int
)

data class EnvironmentalFieldSample(
    val positionM: DomainPoint,
    val level: EnvironmentalFieldLevel,
    val temperatureC: Double?,
    val humidityRatioKgKg: Double?,
    val relativeHumidityPercent: Double?,
    val temperatureBand: TemperatureLegendBand,
    val humidityBand: HumidityLegendBand
) {
    val hasFiniteTemperature: Boolean
        get() = temperatureC != null && temperatureC.isFinite()
    val hasFiniteHumidityRatio: Boolean
        get() = humidityRatioKgKg != null && humidityRatioKgKg.isFinite() && humidityRatioKgKg >= 0.0
    val hasFiniteRelativeHumidity: Boolean
        get() = relativeHumidityPercent != null && relativeHumidityPercent.isFinite()
}

data class EnvironmentalFieldLatticePlan(
    val widthM: Double,
    val lengthM: Double,
    val heightM: Double,
    val countX: Int,
    val countY: Int,
    val countZ: Int,
    val spacingXM: Double,
    val spacingYM: Double,
    val cropZM: Double,
    val middleZM: Double,
    val upperZM: Double,
    val positions: List<DomainPoint>,
    val levels: List<EnvironmentalFieldLevel>
) {
    val cellCount: Int get() = positions.size

    init {
        require(countX >= 1 && countY >= 1 && countZ >= 1)
        require(positions.size == levels.size)
        require(positions.size <= EnvironmentalFieldVisual.MAX_CELLS)
    }
}

data class EnvironmentalFieldThresholds(
    val targetTemperatureC: Double?,
    val cdpC: Double?,
    val conC: Double?,
    val eonC: Double?,
    val fonPercent: Double?,
    val fspPercent: Double?,
    val fdpPercent: Double?,
    val halfWidthC: Double = VisualizationBandSettings.TARGET_BAND_HALF_WIDTH_C
) {
    val temperatureReady: Boolean
        get() = finite(targetTemperatureC) && finite(cdpC) && finite(conC) && finite(eonC) &&
            halfWidthC > 0.0 &&
            cdpC!! < conC!! && conC < eonC!!

    val humidityReady: Boolean
        get() = finite(fonPercent) && finite(fspPercent) && finite(fdpPercent) &&
            fonPercent!! < fspPercent!! && fspPercent < fdpPercent!!

    private fun finite(value: Double?): Boolean = value != null && value.isFinite()
}

data class EnvironmentalFieldSnapshot(
    val identity: EnvironmentalFieldIdentity,
    val previewMode: PreviewMode,
    val widthM: Double,
    val lengthM: Double,
    val heightM: Double,
    val lattice: EnvironmentalFieldLatticePlan,
    val samples: List<EnvironmentalFieldSample>,
    val minTemperatureC: Double?,
    val averageTemperatureC: Double?,
    val maxTemperatureC: Double?,
    val minRelativeHumidityPercent: Double?,
    val averageRelativeHumidityPercent: Double?,
    val maxRelativeHumidityPercent: Double?,
    val thresholds: EnvironmentalFieldThresholds,
    val cropName: String,
    val stageName: String,
    val availability: EnvironmentalFieldAvailability,
    val unavailableReason: String?,
    val predictionLabel: String = VisualizationCopy.PREDICTED_UNCALIBRATED,
    val physicsSnapshot: PhysicsFieldSnapshot? = null
) {
    val cellCount: Int get() = samples.size
    val predictedUncalibrated: Boolean
        get() = predictionLabel == VisualizationCopy.PREDICTED_UNCALIBRATED

    fun samplesAt(height: EnvironmentalFieldHeight): List<EnvironmentalFieldSample> =
        when (height) {
            EnvironmentalFieldHeight.WHOLE -> samples
            EnvironmentalFieldHeight.CROP -> samples.filter { it.level == EnvironmentalFieldLevel.CROP }
            EnvironmentalFieldHeight.MIDDLE -> samples.filter { it.level == EnvironmentalFieldLevel.MIDDLE }
            EnvironmentalFieldHeight.UPPER -> samples.filter { it.level == EnvironmentalFieldLevel.UPPER }
        }

    companion object {
        fun unavailable(
            previewMode: PreviewMode,
            reason: String,
            cropName: String = VisualizationCopy.COLOUR_UNAVAILABLE,
            stageName: String = VisualizationCopy.COLOUR_UNAVAILABLE,
            identity: EnvironmentalFieldIdentity = EnvironmentalFieldIdentity(0.0, 0, 0),
            thresholds: EnvironmentalFieldThresholds = EnvironmentalFieldThresholds(
                null, null, null, null, null, null, null
            )
        ): EnvironmentalFieldSnapshot = EnvironmentalFieldSnapshot(
            identity = identity,
            previewMode = previewMode,
            widthM = Double.NaN,
            lengthM = Double.NaN,
            heightM = Double.NaN,
            lattice = EnvironmentalFieldLattice.empty(),
            samples = emptyList(),
            minTemperatureC = null,
            averageTemperatureC = null,
            maxTemperatureC = null,
            minRelativeHumidityPercent = null,
            averageRelativeHumidityPercent = null,
            maxRelativeHumidityPercent = null,
            thresholds = thresholds,
            cropName = cropName,
            stageName = stageName,
            availability = EnvironmentalFieldAvailability.UNAVAILABLE,
            unavailableReason = reason
        )
    }
}

data class EnvironmentalFieldCellBox(
    val sample: EnvironmentalFieldSample,
    val minXM: Double,
    val maxXM: Double,
    val minYM: Double,
    val maxYM: Double,
    val minZM: Double,
    val maxZM: Double
)

data class EnvironmentalFieldBandMesh(
    val key: String,
    val temperatureBand: TemperatureLegendBand?,
    val humidityBand: HumidityLegendBand?,
    val color: VisualizationRgb,
    val cells: List<EnvironmentalFieldCellBox>
) {
    val cellCount: Int get() = cells.size
}

data class EnvironmentalFieldLegendBand(
    val name: String,
    val rangeText: String,
    val color: VisualizationRgb
)

data class EnvironmentalFieldLegendModel(
    val layer: EnvironmentalFieldLayer,
    val height: EnvironmentalFieldHeight,
    val title: String,
    val unit: String,
    val cropName: String,
    val stageName: String,
    val predictionLabel: String,
    val bands: List<EnvironmentalFieldLegendBand>,
    val minValue: Double?,
    val averageValue: Double?,
    val maxValue: Double?,
    val availability: EnvironmentalFieldAvailability,
    val message: String?,
    val expanded: Boolean
) {
    val cropStageText: String = "$cropName · $stageName"
    val showsLiveIot: Boolean = false

    fun minText(): String = formatStat(minValue)
    fun averageText(): String = formatStat(averageValue)
    fun maxText(): String = formatStat(maxValue)

    private fun formatStat(value: Double?): String {
        if (value == null || !value.isFinite()) return VisualizationCopy.COLOUR_UNAVAILABLE
        return "${formatOneDecimal(value)} $unit"
    }

    companion object {
        val HIDDEN = EnvironmentalFieldLegendModel(
            layer = EnvironmentalFieldLayer.OFF,
            height = EnvironmentalFieldHeight.WHOLE,
            title = "",
            unit = "",
            cropName = "",
            stageName = "",
            predictionLabel = VisualizationCopy.PREDICTED_UNCALIBRATED,
            bands = emptyList(),
            minValue = null,
            averageValue = null,
            maxValue = null,
            availability = EnvironmentalFieldAvailability.LAYER_OFF,
            message = null,
            expanded = false
        )
    }
}

data class EnvironmentalFieldFrame(
    val layer: EnvironmentalFieldLayer,
    val height: EnvironmentalFieldHeight,
    val snapshot: EnvironmentalFieldSnapshot? = null,
    val bands: List<EnvironmentalFieldBandMesh> = emptyList(),
    val visibleSamples: List<EnvironmentalFieldSample> = emptyList(),
    val minValue: Double? = null,
    val averageValue: Double? = null,
    val maxValue: Double? = null,
    val availability: EnvironmentalFieldAvailability = EnvironmentalFieldAvailability.LAYER_OFF,
    val message: String? = null,
    val legend: EnvironmentalFieldLegendModel = EnvironmentalFieldLegendModel.HIDDEN,
    val geometryUpdateReason: EnvironmentalFieldGeometryUpdateReason =
        EnvironmentalFieldGeometryUpdateReason.NONE,
    val signature: String = "off"
) {
    val isEmpty: Boolean get() = bands.isEmpty() || visibleSamples.isEmpty()
    val cellCount: Int get() = visibleSamples.size
    val activeNodeCount: Int get() = bands.size
    val nodePerCell: Boolean get() = EnvironmentalFieldVisual.NODE_PER_CELL
    val exceedsCellCap: Boolean get() = cellCount > EnvironmentalFieldVisual.MAX_CELLS
    val exceedsNodeCap: Boolean get() = activeNodeCount > EnvironmentalFieldVisual.MAX_GEOMETRY_NODES

    companion object {
        val EMPTY = EnvironmentalFieldFrame(
            layer = EnvironmentalFieldLayer.OFF,
            height = EnvironmentalFieldHeight.WHOLE
        )
    }
}
