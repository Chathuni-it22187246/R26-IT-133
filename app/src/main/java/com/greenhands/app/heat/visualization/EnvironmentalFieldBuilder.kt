package com.greenhands.app.heat.visualization

import com.greenhands.app.heat.model.formatOneDecimal
import com.greenhands.app.heat.runtime.adapter.UserPhysicsCoordinateAdapter
import com.greenhands.app.heat.runtime.contracts.PhysicsFieldSnapshot
import com.greenhands.app.preview3d.model.PreviewMode

/**
 * Builds an immutable environmental field snapshot from an accepted physics
 * snapshot. Local T and humidity ratio come from [SnapshotFieldSampler]; RH is
 * derived with the accepted psychrometric conversion. Does not advance the solver.
 */
object EnvironmentalFieldBuilder {
    fun build(
        physics: PhysicsFieldSnapshot?,
        previewMode: PreviewMode,
        widthM: Double,
        lengthM: Double,
        heightM: Double,
        canopyHeightM: Double?,
        thresholds: EnvironmentalFieldThresholds,
        cropName: String,
        stageName: String,
        generation: Int,
        visualizationRunning: Boolean
    ): EnvironmentalFieldSnapshot {
        if (!widthM.isFinite() || widthM <= 0.0 ||
            !lengthM.isFinite() || lengthM <= 0.0 ||
            !heightM.isFinite() || heightM <= 0.0
        ) {
            return EnvironmentalFieldSnapshot.unavailable(
                previewMode,
                VisualizationCopy.FIELD_INVALID_DIMENSIONS,
                cropName,
                stageName,
                EnvironmentalFieldIdentity(0.0, generation, 0),
                thresholds
            )
        }
        if (!visualizationRunning) {
            val lattice = EnvironmentalFieldLattice.plan(widthM, lengthM, heightM, canopyHeightM)
                ?: return EnvironmentalFieldSnapshot.unavailable(
                    previewMode,
                    VisualizationCopy.FIELD_INVALID_DIMENSIONS,
                    cropName,
                    stageName,
                    EnvironmentalFieldIdentity(0.0, generation, 0),
                    thresholds
                )
            return EnvironmentalFieldSnapshot(
                identity = EnvironmentalFieldIdentity(0.0, generation, 0),
                previewMode = previewMode,
                widthM = widthM,
                lengthM = lengthM,
                heightM = heightM,
                lattice = lattice,
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
                availability = EnvironmentalFieldAvailability.HIDDEN_BEFORE_START,
                unavailableReason = VisualizationCopy.FIELD_START_TO_DISPLAY,
                physicsSnapshot = null
            )
        }
        if (physics == null || !physics.finiteValues) {
            val reason = when {
                previewMode != PreviewMode.DEMO -> VisualizationCopy.FIELD_MY_GREENHOUSE_UNAVAILABLE
                else -> VisualizationCopy.FIELD_MISSING_SNAPSHOT
            }
            return EnvironmentalFieldSnapshot.unavailable(
                previewMode,
                reason,
                cropName,
                stageName,
                EnvironmentalFieldIdentity(0.0, generation, 0),
                thresholds
            )
        }
        if (!thresholds.temperatureReady && !thresholds.humidityReady) {
            return EnvironmentalFieldSnapshot.unavailable(
                previewMode,
                VisualizationCopy.FIELD_MISSING_THRESHOLDS,
                cropName,
                stageName,
                EnvironmentalFieldIdentity(physics.elapsedTimeS, generation, System.identityHashCode(physics)),
                thresholds
            )
        }
        val lattice = EnvironmentalFieldLattice.plan(widthM, lengthM, heightM, canopyHeightM)
            ?: return EnvironmentalFieldSnapshot.unavailable(
                previewMode,
                VisualizationCopy.FIELD_INVALID_DIMENSIONS,
                cropName,
                stageName,
                EnvironmentalFieldIdentity(physics.elapsedTimeS, generation, System.identityHashCode(physics)),
                thresholds
            )
        val samples = ArrayList<EnvironmentalFieldSample>(lattice.cellCount)
        for (index in lattice.positions.indices) {
            val domain = lattice.positions[index]
            val level = lattice.levels[index]
            val physicsPoint = UserPhysicsCoordinateAdapter.positionToPhysics(domain)
            val temperature = SnapshotFieldSampler.temperatureC(physics, physicsPoint)
            val omega = SnapshotFieldSampler.humidityRatioKgKg(physics, physicsPoint)
            val rh = SnapshotFieldSampler.relativeHumidityPercent(physics, physicsPoint)
            val temperatureBand = if (thresholds.temperatureReady) {
                TemperatureLegendClassifier.band(
                    temperature,
                    thresholds.targetTemperatureC,
                    thresholds.cdpC,
                    thresholds.conC,
                    thresholds.eonC,
                    thresholds.halfWidthC
                )
            } else {
                TemperatureLegendBand.UNAVAILABLE
            }
            val humidityBand = if (thresholds.humidityReady) {
                HumidityLegendClassifier.band(
                    rh,
                    thresholds.fonPercent,
                    thresholds.fspPercent,
                    thresholds.fdpPercent
                )
            } else {
                HumidityLegendBand.UNAVAILABLE
            }
            samples += EnvironmentalFieldSample(
                positionM = domain,
                level = level,
                temperatureC = temperature,
                humidityRatioKgKg = omega,
                relativeHumidityPercent = rh,
                temperatureBand = temperatureBand,
                humidityBand = humidityBand
            )
        }
        val temperatures = samples.mapNotNull { sample ->
            sample.temperatureC?.takeIf { it.isFinite() }
        }
        val humidities = samples.mapNotNull { sample ->
            sample.relativeHumidityPercent?.takeIf { it.isFinite() }
        }
        return EnvironmentalFieldSnapshot(
            identity = EnvironmentalFieldIdentity(
                elapsedTimeS = physics.elapsedTimeS,
                generation = generation,
                physicsSnapshotIdentity = System.identityHashCode(physics)
            ),
            previewMode = previewMode,
            widthM = widthM,
            lengthM = lengthM,
            heightM = heightM,
            lattice = lattice,
            samples = samples,
            minTemperatureC = temperatures.minOrNull(),
            averageTemperatureC = meanOrNull(temperatures),
            maxTemperatureC = temperatures.maxOrNull(),
            minRelativeHumidityPercent = humidities.minOrNull(),
            averageRelativeHumidityPercent = meanOrNull(humidities),
            maxRelativeHumidityPercent = humidities.maxOrNull(),
            thresholds = thresholds,
            cropName = cropName,
            stageName = stageName,
            availability = EnvironmentalFieldAvailability.AVAILABLE,
            unavailableReason = null,
            physicsSnapshot = physics
        )
    }

    fun temperatureRangeText(
        band: TemperatureLegendBand,
        thresholds: EnvironmentalFieldThresholds
    ): String {
        val target = thresholds.targetTemperatureC ?: return VisualizationCopy.COLOUR_UNAVAILABLE
        val cdp = thresholds.cdpC ?: return VisualizationCopy.COLOUR_UNAVAILABLE
        val con = thresholds.conC ?: return VisualizationCopy.COLOUR_UNAVAILABLE
        val eon = thresholds.eonC ?: return VisualizationCopy.COLOUR_UNAVAILABLE
        val half = thresholds.halfWidthC
        val low = target - half
        val high = target + half
        return when (band) {
            TemperatureLegendBand.BELOW_CDP -> "< ${deg(cdp)}"
            TemperatureLegendBand.BELOW_TARGET -> "${deg(cdp)}–${deg(low)}"
            TemperatureLegendBand.TARGET -> "${deg(low)}–${deg(high)}"
            TemperatureLegendBand.ABOVE_TARGET -> "${deg(high)}–${deg(con)}"
            TemperatureLegendBand.CON_TO_EON -> "${deg(con)}–${deg(eon)}"
            TemperatureLegendBand.EON_OR_ABOVE -> "≥ ${deg(eon)}"
            TemperatureLegendBand.UNAVAILABLE -> VisualizationCopy.COLOUR_UNAVAILABLE
        }
    }

    fun humidityRangeText(
        band: HumidityLegendBand,
        thresholds: EnvironmentalFieldThresholds
    ): String {
        val fon = thresholds.fonPercent ?: return VisualizationCopy.COLOUR_UNAVAILABLE
        val fsp = thresholds.fspPercent ?: return VisualizationCopy.COLOUR_UNAVAILABLE
        val fdp = thresholds.fdpPercent ?: return VisualizationCopy.COLOUR_UNAVAILABLE
        return when (band) {
            HumidityLegendBand.FOGGER_DEMAND -> "≤ ${rh(fon)}"
            HumidityLegendBand.APPROACHING_TARGET -> "${rh(fon)}–${rh(fsp)}"
            HumidityLegendBand.TARGET_CONTROL -> "${rh(fsp)}–${rh(fdp)}"
            HumidityLegendBand.ABOVE_CONTROL -> "> ${rh(fdp)}"
            HumidityLegendBand.UNAVAILABLE -> VisualizationCopy.COLOUR_UNAVAILABLE
        }
    }

    private fun deg(value: Double): String = "${formatOneDecimal(value)}${VisualizationCopy.FIELD_UNIT_C}"

    private fun rh(value: Double): String = "${formatOneDecimal(value)} ${VisualizationCopy.FIELD_UNIT_RH}"

    private fun meanOrNull(values: List<Double>): Double? {
        if (values.isEmpty()) return null
        return values.sum() / values.size
    }
}
