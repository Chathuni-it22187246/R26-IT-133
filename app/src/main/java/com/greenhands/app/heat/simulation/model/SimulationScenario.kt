package com.greenhands.app.heat.simulation.model

import com.greenhands.app.heat.model.CirculationThresholds
import com.greenhands.app.heat.model.ExhaustThresholds
import com.greenhands.app.heat.model.FoggerThresholds
import com.greenhands.app.heat.simulation.physics.AirProperties
import com.greenhands.app.heat.simulation.physics.SimulationException

enum class ControlSource {
    CONTROL_SENSOR,
    CROP_ZONE_MEAN,
    GREENHOUSE_MEAN
}

enum class EquipmentDriveMode {
    CONTROLLER,
    FORCED
}

data class ForcedEquipmentState(
    val circulationOn: Boolean = false,
    val exhaustOn: Boolean = false,
    val foggerOn: Boolean = false
)

data class NumericalSettings(
    val requestedTimeStepS: Double = 0.25,
    val cflSafetyFactor: Double = 0.40,
    val diffusionSafetyFactor: Double = 0.40,
    val forecastHorizonS: Double = 1_800.0,
    val maxSubstepsPerOuterStep: Int = 10_000,
    val stabilizationDurationS: Double = 30.0
) {
    init {
        require(requestedTimeStepS > 0.0 && requestedTimeStepS.isFinite()) {
            "requestedTimeStepS must be finite and > 0 s"
        }
        require(cflSafetyFactor > 0.0 && cflSafetyFactor <= 0.45) {
            "cflSafetyFactor must be in (0, 0.45]"
        }
        require(diffusionSafetyFactor > 0.0 && diffusionSafetyFactor <= 0.45) {
            "diffusionSafetyFactor must be in (0, 0.45]"
        }
        require(forecastHorizonS > 0.0 && forecastHorizonS.isFinite()) {
            "forecastHorizonS must be finite and > 0 s"
        }
        require(maxSubstepsPerOuterStep >= 1)
        require(stabilizationDurationS > 0.0)
    }
}

data class SimulationScenario(
    val geometry: GreenhouseGeometry,
    val airProperties: AirProperties,
    val initialIndoorTemperatureC: Double,
    val initialIndoorRhPercent: Double,
    val outsideTemperatureC: Double,
    val outsideRhPercent: Double,
    val circulationFans: List<CirculationFanProfile> = emptyList(),
    val exhaustFans: List<ExhaustFanProfile> = emptyList(),
    val intakes: List<IntakeOpening> = emptyList(),
    /**
     * Optional spatial-intake air state. Null means make-up air uses
     * [outsideTemperatureC] / outdoor humidity ratio (frozen Phase 3A default).
     * When a cooling pad is modelled, these hold pad-outlet \(T\) and \(\omega\)
     * without changing the [com.greenhands.app.heat.simulation.physics.ExhaustBoundary]
     * mixing formula. Envelope UA still uses [outsideTemperatureC].
     */
    val intakeAirTemperatureC: Double? = null,
    val intakeAirHumidityRatioKgKg: Double? = null,
    val foggers: List<FoggerProfile> = emptyList(),
    val cropZone: AxisAlignedRegion? = null,
    val controlSensorPositionM: Vec3? = null,
    val envelopeUaWPerK: Double? = null,
    val netSolarAndInternalHeatLoadW: Double? = null,
    val groundTemperatureC: Double? = null,
    val canopyDragCoefficient: Double? = null,
    val leafAreaIndex: Double? = null,
    val effectiveThermalDiffusivityM2s: Double,
    val effectiveMoistureDiffusivityM2s: Double,
    val circulationThresholds: CirculationThresholds,
    val exhaustThresholds: ExhaustThresholds,
    val foggerThresholds: FoggerThresholds?,
    val controlSource: ControlSource = ControlSource.CROP_ZONE_MEAN,
    val equipmentDriveMode: EquipmentDriveMode = EquipmentDriveMode.CONTROLLER,
    val forcedEquipment: ForcedEquipmentState = ForcedEquipmentState(),
    val numerical: NumericalSettings = NumericalSettings(),
    val prescribedVelocityOverrideMps: Vec3? = null,
    val parameterRecords: List<ParameterRecord>
) {
    init {
        if (!initialIndoorTemperatureC.isFinite() || !outsideTemperatureC.isFinite()) {
            throw SimulationException("Indoor/outdoor temperatures must be finite")
        }
        if (!initialIndoorRhPercent.isFinite() || initialIndoorRhPercent < 0.0) {
            throw SimulationException("initialIndoorRhPercent must be finite and >= 0")
        }
        if (!outsideRhPercent.isFinite() || outsideRhPercent < 0.0) {
            throw SimulationException("outsideRhPercent must be finite and >= 0")
        }
        if (!effectiveThermalDiffusivityM2s.isFinite() || effectiveThermalDiffusivityM2s < 0.0) {
            throw SimulationException("effectiveThermalDiffusivityM2s must be finite and >= 0 m²/s")
        }
        if (!effectiveMoistureDiffusivityM2s.isFinite() || effectiveMoistureDiffusivityM2s < 0.0) {
            throw SimulationException("effectiveMoistureDiffusivityM2s must be finite and >= 0 m²/s")
        }
        envelopeUaWPerK?.let {
            if (!it.isFinite() || it < 0.0) throw SimulationException("envelopeUaWPerK must be >= 0")
        }
        netSolarAndInternalHeatLoadW?.let {
            if (!it.isFinite()) throw SimulationException("netSolarAndInternalHeatLoadW must be finite")
        }
        if (controlSource == ControlSource.CONTROL_SENSOR && controlSensorPositionM == null) {
            throw SimulationException("CONTROL_SENSOR requires controlSensorPositionM")
        }
        if ((intakeAirTemperatureC == null) != (intakeAirHumidityRatioKgKg == null)) {
            throw SimulationException("intakeAirTemperatureC and intakeAirHumidityRatioKgKg must both be set or both be null")
        }
        intakeAirTemperatureC?.let { temperature ->
            if (!temperature.isFinite()) {
                throw SimulationException("intakeAirTemperatureC must be finite")
            }
        }
        intakeAirHumidityRatioKgKg?.let { omega ->
            if (!omega.isFinite() || omega < 0.0) {
                throw SimulationException("intakeAirHumidityRatioKgKg must be finite and >= 0 kg/kg")
            }
        }
    }

    fun usesWellMixedVentilation(): Boolean = intakes.isEmpty() && exhaustFans.isNotEmpty()
}
