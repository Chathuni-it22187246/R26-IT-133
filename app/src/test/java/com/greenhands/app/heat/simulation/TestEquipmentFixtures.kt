package com.greenhands.app.heat.simulation

import com.greenhands.app.heat.domain.HeatFormulas
import com.greenhands.app.heat.simulation.model.AxisAlignedRegion
import com.greenhands.app.heat.simulation.model.CirculationFanProfile
import com.greenhands.app.heat.simulation.model.ControlSource
import com.greenhands.app.heat.simulation.model.EquipmentDriveMode
import com.greenhands.app.heat.simulation.model.ExhaustFanProfile
import com.greenhands.app.heat.simulation.model.FoggerProfile
import com.greenhands.app.heat.simulation.model.ForcedEquipmentState
import com.greenhands.app.heat.simulation.model.GreenhouseGeometry
import com.greenhands.app.heat.simulation.model.IntakeOpening
import com.greenhands.app.heat.simulation.model.NumericalSettings
import com.greenhands.app.heat.simulation.model.ParameterRecord
import com.greenhands.app.heat.simulation.model.ProvenanceClass
import com.greenhands.app.heat.simulation.model.SimulationScenario
import com.greenhands.app.heat.simulation.model.Vec3
import com.greenhands.app.heat.simulation.physics.AirProperties

/**
 * Mathematical fixtures only. Classified PREVIEW_ASSUMPTION / test-only.
 * These numbers must not appear in normal app UI.
 */
object TestEquipmentFixtures {
    fun testCirculationFan(
        positionM: Vec3 = Vec3(0.5, 1.5, 1.0),
        direction: Vec3 = Vec3(1.0, 0.0, 0.0),
        outletDiameterM: Double = 0.40,
        ratedAirflowM3s: Double = 0.50,
        speedFraction: Double = 1.0,
        jetCoreLengthM: Double = 0.40,
        jetDecayCoefficient: Double = 6.0,
        jetSpreadRate: Double = 0.12,
        maximumModelVelocityMps: Double = 12.0
    ): CirculationFanProfile = CirculationFanProfile(
        id = "TestCirculationFan",
        positionM = positionM,
        unitDirection = direction,
        outletDiameterM = outletDiameterM,
        ratedAirflowM3s = ratedAirflowM3s,
        speedFraction = speedFraction,
        jetCoreLengthM = jetCoreLengthM,
        jetDecayCoefficient = jetDecayCoefficient,
        jetSpreadRate = jetSpreadRate,
        maximumModelVelocityMps = maximumModelVelocityMps,
        airflowProvenance = ProvenanceClass.PREVIEW_ASSUMPTION,
        diameterProvenance = ProvenanceClass.PREVIEW_ASSUMPTION,
        jetCoefficientProvenance = ProvenanceClass.CALIBRATED_COEFFICIENT
    )

    fun testExhaustFan(
        positionM: Vec3 = Vec3(5.5, 1.5, 1.2),
        ratedAirflowM3s: Double = 0.24
    ): ExhaustFanProfile = ExhaustFanProfile(
        id = "TestExhaustFan",
        positionM = positionM,
        outwardNormal = Vec3(1.0, 0.0, 0.0),
        ratedAirflowM3s = ratedAirflowM3s,
        speedFraction = 1.0,
        airflowProvenance = ProvenanceClass.PREVIEW_ASSUMPTION
    )

    fun testFogger(
        positionM: Vec3 = Vec3(1.0, 1.5, 1.2),
        waterFlowKgS: Double = 0.002,
        evaporationEfficiency: Double = 0.50
    ): FoggerProfile = FoggerProfile(
        id = "TestFogger",
        positionM = positionM,
        unitDirection = Vec3(1.0, 0.0, 0.0),
        waterFlowKgS = waterFlowKgS,
        evaporationEfficiency = evaporationEfficiency,
        plumeLengthM = 2.0,
        plumeAngleDeg = 40.0,
        waterTemperatureC = 20.0,
        waterFlowProvenance = ProvenanceClass.PREVIEW_ASSUMPTION,
        evaporationEfficiencyProvenance = ProvenanceClass.CALIBRATED_COEFFICIENT
    )

    fun boxGeometry(
        lengthM: Double = 6.0,
        widthM: Double = 3.0,
        heightM: Double = 2.5,
        cellEdgeM: Double = 0.50
    ): GreenhouseGeometry = GreenhouseGeometry(
        lengthM = lengthM,
        widthM = widthM,
        wallHeightM = heightM,
        ridgeHeightM = heightM,
        cellEdgeM = cellEdgeM
    )

    fun scenario(
        geometry: GreenhouseGeometry = boxGeometry(),
        indoorTemperatureC: Double = 30.0,
        indoorRhPercent: Double = 70.0,
        outsideTemperatureC: Double = 25.0,
        outsideRhPercent: Double = 60.0,
        toptC: Double = 25.0,
        rhoptPercent: Double? = 70.0,
        circulationFans: List<CirculationFanProfile> = emptyList(),
        exhaustFans: List<ExhaustFanProfile> = emptyList(),
        intakes: List<IntakeOpening> = emptyList(),
        intakeAirTemperatureC: Double? = null,
        intakeAirHumidityRatioKgKg: Double? = null,
        foggers: List<FoggerProfile> = emptyList(),
        cropZone: AxisAlignedRegion? = null,
        controlSource: ControlSource = ControlSource.CROP_ZONE_MEAN,
        driveMode: EquipmentDriveMode = EquipmentDriveMode.CONTROLLER,
        forced: ForcedEquipmentState = ForcedEquipmentState(),
        alphaM2s: Double = 0.02,
        dEffM2s: Double = 0.02,
        envelopeUaWPerK: Double? = null,
        solarW: Double? = null,
        dtS: Double = 0.25,
        horizonS: Double = 600.0,
        velocityOverride: Vec3? = null,
        sensorPositionM: Vec3? = null
    ): SimulationScenario {
        val circ = HeatFormulas.circulation(toptC)
        val exhaust = HeatFormulas.exhaustFromCirculation(circ)
        val fog = rhoptPercent?.let { HeatFormulas.fogger(it) }
        val records = mutableListOf(
            ParameterRecord(
                "effectiveThermalDiffusivityM2s",
                alphaM2s.toString(),
                "m²/s",
                ProvenanceClass.CALIBRATED_COEFFICIENT,
                "Coarse-grid mixing coefficient, not molecular diffusivity"
            ),
            ParameterRecord(
                "effectiveMoistureDiffusivityM2s",
                dEffM2s.toString(),
                "m²/s",
                ProvenanceClass.CALIBRATED_COEFFICIENT,
                "Coarse-grid mixing coefficient, not molecular diffusivity"
            ),
            ParameterRecord(
                "CSP/CDP/CON",
                "${circ.csp}/${circ.cdp}/${circ.con}",
                "°C",
                ProvenanceClass.PROJECT_CONTROL_RULE,
                "HeatFormulas.circulation — DEMO_CONTROL_LOGIC.md"
            ),
            ParameterRecord(
                "ESP/EDP/EON",
                "${exhaust.esp}/${exhaust.edp}/${exhaust.eon}",
                "°C",
                ProvenanceClass.PROJECT_CONTROL_RULE,
                "HeatFormulas.exhaustFromCirculation — DEMO_CONTROL_LOGIC.md"
            )
        )
        if (fog != null) {
            records += ParameterRecord(
                "FSP/FON/FDP",
                "${fog.fsp}/${fog.fon}/${fog.fdp}",
                "% RH",
                ProvenanceClass.PROJECT_CONTROL_RULE,
                "HeatFormulas.fogger — DEMO_CONTROL_LOGIC.md"
            )
        }
        return SimulationScenario(
            geometry = geometry,
            airProperties = AirProperties.chen2014Preview(),
            initialIndoorTemperatureC = indoorTemperatureC,
            initialIndoorRhPercent = indoorRhPercent,
            outsideTemperatureC = outsideTemperatureC,
            outsideRhPercent = outsideRhPercent,
            circulationFans = circulationFans,
            exhaustFans = exhaustFans,
            intakes = intakes,
            intakeAirTemperatureC = intakeAirTemperatureC,
            intakeAirHumidityRatioKgKg = intakeAirHumidityRatioKgKg,
            foggers = foggers,
            cropZone = cropZone,
            controlSensorPositionM = sensorPositionM,
            envelopeUaWPerK = envelopeUaWPerK,
            netSolarAndInternalHeatLoadW = solarW,
            effectiveThermalDiffusivityM2s = alphaM2s,
            effectiveMoistureDiffusivityM2s = dEffM2s,
            circulationThresholds = circ,
            exhaustThresholds = exhaust,
            foggerThresholds = fog,
            controlSource = controlSource,
            equipmentDriveMode = driveMode,
            forcedEquipment = forced,
            numerical = NumericalSettings(
                requestedTimeStepS = dtS,
                forecastHorizonS = horizonS
            ),
            prescribedVelocityOverrideMps = velocityOverride,
            parameterRecords = records
        )
    }
}
