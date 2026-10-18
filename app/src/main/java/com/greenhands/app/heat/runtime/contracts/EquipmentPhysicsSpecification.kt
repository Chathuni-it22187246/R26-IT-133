package com.greenhands.app.heat.runtime.contracts

import com.greenhands.app.equipment.model.DomainPoint
import com.greenhands.app.equipment.model.EquipmentType
import com.greenhands.app.heat.runtime.PhysicsRuntimeCopy
import com.greenhands.app.heat.simulation.model.ProvenanceClass

enum class SpecificationProvenance {
    DEMO_PREVIEW_ASSUMPTION,
    MANUFACTURER_INPUT,
    MISSING
}

enum class QuantitativeReadiness {
    READY,
    QUALITATIVE_ONLY,
    UNAVAILABLE
}

/**
 * Per-UUID physics specification. My Greenhouse never receives Demo ratings through this type.
 */
data class EquipmentPhysicsSpecification(
    val equipmentUuid: String,
    val equipmentType: EquipmentType,
    val ratedAirflowM3s: Double?,
    val fanDiameterM: Double?,
    val fanSpeedFraction: Double?,
    val foggerWaterFlowKgS: Double?,
    val foggerPressurePa: Double?,
    val foggerEvaporationEfficiency: Double?,
    val effectRadiusM: Double? = null,
    val positionM: DomainPoint,
    val yawDeg: Double,
    val provenance: SpecificationProvenance,
    val provenanceClass: ProvenanceClass,
    val readiness: QuantitativeReadiness,
    val unavailableReason: String?
) {
    fun quantitativeReady(): Boolean = readiness == QuantitativeReadiness.READY

    companion object {
        fun missing(
            equipmentUuid: String,
            equipmentType: EquipmentType,
            positionM: DomainPoint,
            yawDeg: Double
        ): EquipmentPhysicsSpecification = EquipmentPhysicsSpecification(
            equipmentUuid = equipmentUuid,
            equipmentType = equipmentType,
            ratedAirflowM3s = null,
            fanDiameterM = null,
            fanSpeedFraction = null,
            foggerWaterFlowKgS = null,
            foggerPressurePa = null,
            foggerEvaporationEfficiency = null,
            effectRadiusM = null,
            positionM = positionM,
            yawDeg = yawDeg,
            provenance = SpecificationProvenance.MISSING,
            provenanceClass = ProvenanceClass.PREVIEW_ASSUMPTION,
            readiness = QuantitativeReadiness.QUALITATIVE_ONLY,
            unavailableReason = PhysicsRuntimeCopy.QUANTITATIVE_UNAVAILABLE_RATING
        )
    }
}

enum class FieldValidationStatus {
    UNCALIBRATED,
    VALIDATION_PENDING,
    UNCALIBRATED_PREDICTION,
    QUALITATIVE_ONLY,
    MODULE_UNAVAILABLE,
    ESTIMATED_OUTSIDE_WEATHER,
    VALIDATED_FOR_THIS_GREENHOUSE
}

enum class EquipmentProbeAxis {
    CIRCULATION_FORWARD,
    EXHAUST_INWARD,
    FOGGER_SPRAY
}

enum class ProbeAvailability {
    PREDICTED_UNCALIBRATED,
    OUTSIDE_GREENHOUSE,
    MODULE_UNAVAILABLE
}
