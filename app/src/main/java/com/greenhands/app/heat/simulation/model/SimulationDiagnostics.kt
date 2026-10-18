package com.greenhands.app.heat.simulation.model

data class SaturationCorrectionReport(
    val condensedVapourKg: Double = 0.0,
    val affectedCellCount: Int = 0,
    val foggerEvaporationLimitedKgS: Double = 0.0
) {
    val applied: Boolean
        get() = condensedVapourKg > 0.0 || foggerEvaporationLimitedKgS > 0.0 || affectedCellCount > 0
}

data class SimulationDiagnostics(
    val simulatedTimeS: Double,
    val outerTimeStepS: Double,
    val stableTimeStepS: Double,
    val substepCount: Int,
    val finiteValues: Boolean,
    val wellMixedVentilation: Boolean,
    val intakeVolumeFlowM3s: Double,
    val exhaustVolumeFlowM3s: Double,
    val volumeFlowBalanced: Boolean,
    val velocityCapCellCount: Int,
    val maxAbsVelocityMps: Double,
    val advectionMeanCorrectionK: Double = 0.0,
    val advectionMeanCorrectionKgKg: Double = 0.0,
    val saturation: SaturationCorrectionReport,
    val wholeAirMeanTemperatureC: Double,
    val wholeAirMeanRhPercent: Double,
    val cropZoneMeanTemperatureC: Double,
    val cropZoneMeanRhPercent: Double,
    val controlTemperatureC: Double,
    val controlRhPercent: Double?,
    val controlSource: ControlSource,
    val notes: List<String>,
    val parameterRecords: List<ParameterRecord>
)
