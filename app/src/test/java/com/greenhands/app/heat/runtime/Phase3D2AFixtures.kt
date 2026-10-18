package com.greenhands.app.heat.runtime

import com.greenhands.app.ar.domain.EquipmentRuntimeEvaluator
import com.greenhands.app.ar.domain.EquipmentRuntimeThresholds
import com.greenhands.app.equipment.model.DomainPoint
import com.greenhands.app.equipment.model.EquipmentAssetCatalog
import com.greenhands.app.equipment.model.EquipmentInstance
import com.greenhands.app.equipment.model.EquipmentRuntimeState
import com.greenhands.app.equipment.model.EquipmentType
import com.greenhands.app.heat.domain.HeatFormulas
import com.greenhands.app.heat.runtime.adapter.AdaptiveGridPolicy
import com.greenhands.app.heat.runtime.contracts.EquipmentPhysicsSpecification
import com.greenhands.app.heat.runtime.contracts.FreshnessStatus
import com.greenhands.app.heat.runtime.contracts.InputValidity
import com.greenhands.app.heat.runtime.contracts.InsideClimateReading
import com.greenhands.app.heat.runtime.contracts.InsideClimateSource
import com.greenhands.app.heat.runtime.contracts.QuantitativeReadiness
import com.greenhands.app.heat.runtime.contracts.SpecificationProvenance
import com.greenhands.app.heat.runtime.contracts.WeatherBoundary
import com.greenhands.app.heat.simulation.model.NumericalSettings
import com.greenhands.app.heat.simulation.model.ProvenanceClass
import com.greenhands.app.preview3d.model.PreviewMode

internal object Phase3D2AFixtures {
    val thresholds: EquipmentRuntimeThresholds = EquipmentRuntimeThresholds(
        circulation = HeatFormulas.circulation(25.0),
        exhaust = HeatFormulas.exhaustFromCirculation(HeatFormulas.circulation(25.0)),
        fogger = HeatFormulas.fogger(70.0)
    )

    fun instance(
        type: EquipmentType,
        id: String,
        number: Int,
        xM: Double = 1.0,
        yM: Double = 1.0,
        zM: Double = 1.5,
        yawDeg: Double = 0.0
    ): EquipmentInstance = EquipmentAssetCatalog.newInstance(
        type = type,
        id = id,
        positionM = DomainPoint(xM, yM, zM),
        yawDeg = yawDeg,
        displayNumber = number
    )

    fun statesOn(instances: List<EquipmentInstance>): Map<String, EquipmentRuntimeState> =
        instances.associate { it.id to EquipmentRuntimeState.ON }

    fun evaluate(
        instances: List<EquipmentInstance>,
        temperatureC: Double,
        humidityPercent: Double,
        previous: Map<String, EquipmentRuntimeState> = emptyMap()
    ): Map<String, EquipmentRuntimeState> = EquipmentRuntimeEvaluator.evaluateAll(
        instances = instances,
        previousById = previous,
        thresholds = thresholds,
        temperatureC = temperatureC,
        humidityPercent = humidityPercent
    )

    fun demoRequest(
        instances: List<EquipmentInstance>,
        temperatureC: Double,
        humidityPercent: Double,
        weather: WeatherBoundary = WeatherBoundary.missing(),
        lengthM: Double = 6.0,
        widthM: Double = 3.0,
        heightM: Double = 2.5,
        cellEdgeM: Double = 0.50,
        runtimeById: Map<String, EquipmentRuntimeState>? = null,
        dtS: Double = 0.25,
        fanPad: com.greenhands.app.heat.runtime.contracts.FanPadBoundaryInput? = null
    ): PhysicsRuntimeRequest {
        val states = runtimeById ?: evaluate(instances, temperatureC, humidityPercent)
        return PhysicsRuntimeRequest(
            previewMode = PreviewMode.DEMO,
            lengthM = lengthM,
            widthM = widthM,
            heightM = heightM,
            cropRowCount = 3,
            canopyHeightM = heightM * 0.26,
            inside = InsideClimateReading.simulated(temperatureC, humidityPercent),
            weather = weather,
            instances = instances,
            runtimeById = states,
            thresholds = thresholds,
            gridConfig = AdaptiveGridPolicy.Config(preferredCellEdgeM = cellEdgeM),
            numerical = NumericalSettings(requestedTimeStepS = dtS),
            fanPad = fanPad
        )
    }

    fun liveInside(temperatureC: Double, humidityPercent: Double, timestampMillis: Long = 1_700_000_000_000L) =
        InsideClimateReading(
            temperatureC = temperatureC,
            relativeHumidityPercent = humidityPercent,
            timestampMillis = timestampMillis,
            sensorPositionM = null,
            source = InsideClimateSource.ACTUAL_IOT,
            provenance = ProvenanceClass.MEASURED_INPUT,
            sourceLabel = PhysicsRuntimeCopy.LIVE_READING,
            validity = InputValidity.VALID,
            freshness = FreshnessStatus.FRESH
        )

    fun manufacturerSpec(instance: EquipmentInstance): EquipmentPhysicsSpecification {
        return when (instance.type) {
            EquipmentType.CIRCULATION_FAN -> EquipmentPhysicsSpecification(
                equipmentUuid = instance.id,
                equipmentType = instance.type,
                ratedAirflowM3s = 0.55,
                fanDiameterM = 0.38,
                fanSpeedFraction = 1.0,
                foggerWaterFlowKgS = null,
                foggerPressurePa = null,
                foggerEvaporationEfficiency = null,
                positionM = instance.positionM,
                yawDeg = instance.yawDeg,
                provenance = SpecificationProvenance.MANUFACTURER_INPUT,
                provenanceClass = ProvenanceClass.MANUFACTURER_INPUT,
                readiness = QuantitativeReadiness.READY,
                unavailableReason = null
            )
            EquipmentType.EXHAUST_FAN -> EquipmentPhysicsSpecification(
                equipmentUuid = instance.id,
                equipmentType = instance.type,
                ratedAirflowM3s = 2.0,
                fanDiameterM = null,
                fanSpeedFraction = 1.0,
                foggerWaterFlowKgS = null,
                foggerPressurePa = null,
                foggerEvaporationEfficiency = null,
                positionM = instance.positionM,
                yawDeg = instance.yawDeg,
                provenance = SpecificationProvenance.MANUFACTURER_INPUT,
                provenanceClass = ProvenanceClass.MANUFACTURER_INPUT,
                readiness = QuantitativeReadiness.READY,
                unavailableReason = null
            )
            EquipmentType.FOGGER -> EquipmentPhysicsSpecification(
                equipmentUuid = instance.id,
                equipmentType = instance.type,
                ratedAirflowM3s = null,
                fanDiameterM = null,
                fanSpeedFraction = null,
                foggerWaterFlowKgS = 0.0015,
                foggerPressurePa = null,
                foggerEvaporationEfficiency = 0.60,
                positionM = instance.positionM,
                yawDeg = instance.yawDeg,
                provenance = SpecificationProvenance.MANUFACTURER_INPUT,
                provenanceClass = ProvenanceClass.MANUFACTURER_INPUT,
                readiness = QuantitativeReadiness.READY,
                unavailableReason = null
            )
        }
    }

    fun manufacturerSpecs(instances: List<EquipmentInstance>): Map<String, EquipmentPhysicsSpecification> =
        instances.filter { !it.isGhost }.associate { it.id to manufacturerSpec(it) }

    fun myGreenhouseRequest(
        instances: List<EquipmentInstance>,
        temperatureC: Double,
        humidityPercent: Double,
        weather: WeatherBoundary = WeatherBoundary.missing(),
        lengthM: Double = 6.0,
        widthM: Double = 3.0,
        heightM: Double = 2.5,
        cellEdgeM: Double = 0.50,
        runtimeById: Map<String, EquipmentRuntimeState>? = null,
        dtS: Double = 0.25
    ): PhysicsRuntimeRequest {
        val states = runtimeById ?: evaluate(instances, temperatureC, humidityPercent)
        return PhysicsRuntimeRequest(
            previewMode = PreviewMode.MY_GREENHOUSE,
            lengthM = lengthM,
            widthM = widthM,
            heightM = heightM,
            cropRowCount = 3,
            canopyHeightM = heightM * 0.26,
            inside = liveInside(temperatureC, humidityPercent),
            weather = weather,
            instances = instances,
            runtimeById = states,
            thresholds = thresholds,
            manufacturerSpecsById = manufacturerSpecs(instances),
            gridConfig = AdaptiveGridPolicy.Config(preferredCellEdgeM = cellEdgeM),
            numerical = NumericalSettings(requestedTimeStepS = dtS)
        )
    }
}
