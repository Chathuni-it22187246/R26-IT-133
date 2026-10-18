package com.greenhands.app.heat.runtime.contracts

import com.greenhands.app.heat.runtime.FanPadCopy
import com.greenhands.app.heat.simulation.model.IntakeOpening
import com.greenhands.app.heat.simulation.model.Vec3
import com.greenhands.app.heat.simulation.physics.EvaporativePadOutlet

enum class GreenhouseEndWall {
    NEAR_Y0,
    FAR_Y_LENGTH,
    NEAR_X0,
    FAR_X_WIDTH
}

enum class FanPadWallSelection {
    AUTO,
    NEAR_Y0,
    FAR_Y_LENGTH,
    NEAR_X0,
    FAR_X_WIDTH
}

/**
 * Narrow fan-and-pad inlet-boundary contract. The pad is not a fourth equipment object.
 */
data class FanPadBoundaryInput(
    val present: Boolean,
    val enabled: Boolean,
    val wetPadActive: Boolean,
    val wallSelection: FanPadWallSelection = FanPadWallSelection.AUTO,
    val widthM: Double?,
    val heightM: Double?,
    val efficiencyFraction: Double?,
    val sourceLabel: String,
    val efficiencySourceLabel: String? = null,
    val measuredPadOutletTemperatureC: Double? = null,
    val measuredPadOutletRhPercent: Double? = null
)

data class ResolvedFanPadBoundary(
    val present: Boolean,
    val enabled: Boolean,
    val wetPadActive: Boolean,
    val quantitativeAvailable: Boolean,
    val wall: GreenhouseEndWall?,
    val widthM: Double?,
    val heightM: Double?,
    val areaM2: Double?,
    val efficiencyFraction: Double?,
    val outsideTemperatureC: Double?,
    val outsideRelativeHumidityPercent: Double?,
    val outsideReadingTimestampMillis: Long?,
    val outsideStatus: WeatherBoundaryStatus,
    val outsideProvenance: WeatherBoundaryProvenance,
    val sourceLabel: String,
    val efficiencySourceLabel: String?,
    val missingInputs: List<String>,
    val layoutExplanation: String?,
    val intakes: List<IntakeOpening>,
    val intakeAirTemperatureC: Double?,
    val intakeAirHumidityRatioKgKg: Double?,
    val padOutlet: EvaporativePadOutlet.Result?,
    val measuredPadOutletTemperatureC: Double? = null,
    val measuredPadOutletRhPercent: Double? = null,
    val airflowSpanM: Double? = null
) {
    val areaFromDimensionsM2: Double?
        get() {
            val w = widthM
            val h = heightM
            return if (w != null && h != null && w.isFinite() && h.isFinite() && w > 0.0 && h > 0.0) {
                w * h
            } else {
                null
            }
        }

    companion object {
        fun inactive(sourceLabel: String = FanPadCopy.PAD_NOT_PRESENT): ResolvedFanPadBoundary =
            ResolvedFanPadBoundary(
                present = false,
                enabled = false,
                wetPadActive = false,
                quantitativeAvailable = false,
                wall = null,
                widthM = null,
                heightM = null,
                areaM2 = null,
                efficiencyFraction = null,
                outsideTemperatureC = null,
                outsideRelativeHumidityPercent = null,
                outsideReadingTimestampMillis = null,
                outsideStatus = WeatherBoundaryStatus.MISSING,
                outsideProvenance = WeatherBoundaryProvenance.MISSING,
                sourceLabel = sourceLabel,
                efficiencySourceLabel = null,
                missingInputs = emptyList(),
                layoutExplanation = null,
                intakes = emptyList(),
                intakeAirTemperatureC = null,
                intakeAirHumidityRatioKgKg = null,
                padOutlet = null
            )
    }
}

data class FanPadFieldSnapshot(
    val present: Boolean,
    val enabled: Boolean,
    val wetPadActive: Boolean,
    val quantitativeAvailable: Boolean,
    val wall: GreenhouseEndWall?,
    val sourceLabel: String?,
    val efficiencySourceLabel: String?,
    val missingInputs: List<String>,
    val layoutExplanation: String?,
    val padOutletTemperatureC: Double?,
    val padOutletRelativeHumidityPercent: Double?,
    val padOutletHumidityRatioKgKg: Double?,
    val outsideTemperatureC: Double?,
    val outsideRelativeHumidityPercent: Double?,
    val inwardPhysics: Vec3? = null,
    val airflowSpanM: Double? = null
) {
    companion object {
        val NONE = FanPadFieldSnapshot(
            present = false,
            enabled = false,
            wetPadActive = false,
            quantitativeAvailable = false,
            wall = null,
            sourceLabel = null,
            efficiencySourceLabel = null,
            missingInputs = emptyList(),
            layoutExplanation = null,
            padOutletTemperatureC = null,
            padOutletRelativeHumidityPercent = null,
            padOutletHumidityRatioKgKg = null,
            outsideTemperatureC = null,
            outsideRelativeHumidityPercent = null
        )
    }
}
