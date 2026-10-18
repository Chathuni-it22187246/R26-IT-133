package com.greenhands.app.heat.runtime.adapter

import com.greenhands.app.equipment.model.EquipmentInstance
import com.greenhands.app.equipment.model.EquipmentType
import com.greenhands.app.heat.runtime.PhysicsRuntimeCopy
import com.greenhands.app.heat.runtime.contracts.EquipmentPhysicsSpecification
import com.greenhands.app.heat.runtime.contracts.QuantitativeReadiness
import com.greenhands.app.heat.runtime.contracts.SpecificationProvenance
import com.greenhands.app.heat.simulation.model.ProvenanceClass
import com.greenhands.app.preview3d.model.PreviewMode

/**
 * Resolves per-UUID physics specifications.
 * Demo Mode may use labelled [DemoPhysicsRatings]. My Greenhouse never receives those values.
 */
object EquipmentSpecificationResolver {
    fun resolveAll(
        mode: PreviewMode,
        instances: List<EquipmentInstance>,
        manufacturerSpecsById: Map<String, EquipmentPhysicsSpecification> = emptyMap()
    ): Map<String, EquipmentPhysicsSpecification> {
        return instances.filter { !it.isGhost }.associate { inst ->
            inst.id to resolve(mode, inst, manufacturerSpecsById[inst.id])
        }
    }

    fun resolve(
        mode: PreviewMode,
        instance: EquipmentInstance,
        manufacturerSpec: EquipmentPhysicsSpecification? = null
    ): EquipmentPhysicsSpecification {
        return when (mode) {
            PreviewMode.DEMO -> demoSpec(instance)
            PreviewMode.MY_GREENHOUSE -> myGreenhouseSpec(instance, manufacturerSpec)
        }
    }

    private fun demoSpec(instance: EquipmentInstance): EquipmentPhysicsSpecification {
        return when (instance.type) {
            EquipmentType.CIRCULATION_FAN -> EquipmentPhysicsSpecification(
                equipmentUuid = instance.id,
                equipmentType = instance.type,
                ratedAirflowM3s = DemoPhysicsRatings.circulationRatedM3s(),
                fanDiameterM = DemoPhysicsRatings.CIRCULATION_OUTLET_DIAMETER_M,
                fanSpeedFraction = 1.0,
                foggerWaterFlowKgS = null,
                foggerPressurePa = null,
                foggerEvaporationEfficiency = null,
                positionM = instance.positionM,
                yawDeg = instance.yawDeg,
                provenance = SpecificationProvenance.DEMO_PREVIEW_ASSUMPTION,
                provenanceClass = DemoPhysicsRatings.PROVENANCE,
                readiness = QuantitativeReadiness.READY,
                unavailableReason = null
            )
            EquipmentType.EXHAUST_FAN -> EquipmentPhysicsSpecification(
                equipmentUuid = instance.id,
                equipmentType = instance.type,
                ratedAirflowM3s = DemoPhysicsRatings.exhaustRatedAirflowM3s(),
                fanDiameterM = null,
                fanSpeedFraction = 1.0,
                foggerWaterFlowKgS = null,
                foggerPressurePa = null,
                foggerEvaporationEfficiency = null,
                positionM = instance.positionM,
                yawDeg = instance.yawDeg,
                provenance = SpecificationProvenance.DEMO_PREVIEW_ASSUMPTION,
                provenanceClass = DemoPhysicsRatings.PROVENANCE,
                readiness = QuantitativeReadiness.READY,
                unavailableReason = null
            )
            EquipmentType.FOGGER -> EquipmentPhysicsSpecification(
                equipmentUuid = instance.id,
                equipmentType = instance.type,
                ratedAirflowM3s = null,
                fanDiameterM = null,
                fanSpeedFraction = null,
                foggerWaterFlowKgS = DemoPhysicsRatings.foggerWaterFlowKgS(),
                foggerPressurePa = null,
                foggerEvaporationEfficiency = DemoPhysicsRatings.foggerEvaporationEfficiency(),
                positionM = instance.positionM,
                yawDeg = instance.yawDeg,
                provenance = SpecificationProvenance.DEMO_PREVIEW_ASSUMPTION,
                provenanceClass = DemoPhysicsRatings.PROVENANCE,
                readiness = QuantitativeReadiness.READY,
                unavailableReason = null
            )
        }
    }

    private fun myGreenhouseSpec(
        instance: EquipmentInstance,
        manufacturerSpec: EquipmentPhysicsSpecification?
    ): EquipmentPhysicsSpecification {
        if (manufacturerSpec == null) {
            return EquipmentPhysicsSpecification.missing(
                instance.id,
                instance.type,
                instance.positionM,
                instance.yawDeg
            )
        }
        require(manufacturerSpec.equipmentUuid == instance.id) {
            "Manufacturer spec UUID does not match placed instance"
        }
        require(manufacturerSpec.provenance != SpecificationProvenance.DEMO_PREVIEW_ASSUMPTION) {
            "My Greenhouse must not use Demo preview ratings"
        }
        val ready = quantitativeReady(instance.type, manufacturerSpec)
        return manufacturerSpec.copy(
            equipmentType = instance.type,
            positionM = instance.positionM,
            yawDeg = instance.yawDeg,
            readiness = if (ready) QuantitativeReadiness.READY else QuantitativeReadiness.QUALITATIVE_ONLY,
            unavailableReason = if (ready) {
                null
            } else {
                PhysicsRuntimeCopy.QUANTITATIVE_UNAVAILABLE_RATING
            },
            provenanceClass = if (ready) {
                ProvenanceClass.MANUFACTURER_INPUT
            } else {
                manufacturerSpec.provenanceClass
            }
        )
    }

    private fun quantitativeReady(
        type: EquipmentType,
        spec: EquipmentPhysicsSpecification
    ): Boolean {
        return when (type) {
            EquipmentType.CIRCULATION_FAN ->
                positive(spec.ratedAirflowM3s) &&
                    (positive(spec.effectRadiusM) || positive(spec.fanDiameterM))
            EquipmentType.EXHAUST_FAN -> positive(spec.ratedAirflowM3s)
            EquipmentType.FOGGER ->
                positive(spec.foggerWaterFlowKgS) &&
                    (
                        positive(spec.effectRadiusM) ||
                            (
                                spec.foggerEvaporationEfficiency != null &&
                                    spec.foggerEvaporationEfficiency.isFinite() &&
                                    spec.foggerEvaporationEfficiency in 0.0..1.0
                                )
                        )
        }
    }

    private fun positive(value: Double?): Boolean = value != null && value.isFinite() && value > 0.0
}
