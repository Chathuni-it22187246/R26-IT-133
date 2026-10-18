package com.greenhands.app.heat.runtime.adapter

import com.greenhands.app.equipment.model.EquipmentInstance
import com.greenhands.app.equipment.model.EquipmentRuntimeState
import com.greenhands.app.equipment.model.EquipmentType
import com.greenhands.app.heat.runtime.PhysicsRuntimeCopy
import com.greenhands.app.heat.runtime.contracts.EquipmentPhysicsSpecification
import com.greenhands.app.heat.runtime.contracts.WeatherBoundary
import com.greenhands.app.heat.simulation.model.CirculationFanProfile
import com.greenhands.app.heat.simulation.model.ExhaustFanProfile
import com.greenhands.app.heat.simulation.model.FoggerProfile

data class UuidEquipmentReadiness(
    val equipmentUuid: String,
    val type: EquipmentType,
    val runtimeState: EquipmentRuntimeState,
    val specification: EquipmentPhysicsSpecification,
    val contributesActiveSource: Boolean,
    val quantitativeReady: Boolean,
    val unavailableReason: String?
)

data class ActivePhysicsSources(
    val circulation: List<CirculationFanProfile>,
    val exhaust: List<ExhaustFanProfile>,
    val foggers: List<FoggerProfile>,
    val readiness: List<UuidEquipmentReadiness>,
    val warnings: List<String>
) {
    fun activeUuidSet(): Set<String> =
        readiness.filter { it.contributesActiveSource }.map { it.equipmentUuid }.toSet()
}

/**
 * Bridges Phase 3C UUID runtime with type-based Phase 3A profiles.
 * Only ON + quantitatively ready equipment becomes an active source.
 * Multiple instances reuse the same module with distinct poses and UUIDs.
 */
object PerUuidEquipmentBridge {
    fun buildActiveSources(
        instances: List<EquipmentInstance>,
        runtimeById: Map<String, EquipmentRuntimeState>,
        specsById: Map<String, EquipmentPhysicsSpecification>,
        weather: WeatherBoundary
    ): ActivePhysicsSources {
        val circulation = ArrayList<CirculationFanProfile>()
        val exhaust = ArrayList<ExhaustFanProfile>()
        val foggers = ArrayList<FoggerProfile>()
        val readiness = ArrayList<UuidEquipmentReadiness>()
        val warnings = ArrayList<String>()
        instances.filter { !it.isGhost }.forEach { inst ->
            val spec = specsById[inst.id] ?: EquipmentPhysicsSpecification.missing(
                inst.id,
                inst.type,
                inst.positionM,
                inst.yawDeg
            )
            val state = runtimeById[inst.id] ?: EquipmentRuntimeState.NOT_RUNNING
            val quantitative = spec.quantitativeReady()
            var reason = spec.unavailableReason
            var contributes = false
            if (state != EquipmentRuntimeState.ON) {
                contributes = false
            } else if (!quantitative) {
                reason = reason ?: PhysicsRuntimeCopy.QUANTITATIVE_UNAVAILABLE_RATING
                warnings += "${inst.displayLabel()}: ${PhysicsRuntimeCopy.QUANTITATIVE_UNAVAILABLE_RATING}"
            } else if (inst.type == EquipmentType.EXHAUST_FAN && !weather.permitsQuantitativeExhaust()) {
                reason = PhysicsRuntimeCopy.MODULE_UNAVAILABLE_WEATHER
                warnings += "${inst.displayLabel()}: ${PhysicsRuntimeCopy.MODULE_UNAVAILABLE_WEATHER}"
            } else {
                when (inst.type) {
                    EquipmentType.CIRCULATION_FAN -> {
                        if (positive(spec.fanDiameterM)) {
                            contributes = true
                            circulation += circulationProfile(inst, spec)
                        }
                    }
                    EquipmentType.EXHAUST_FAN -> {
                        contributes = true
                        exhaust += exhaustProfile(inst, spec)
                    }
                    EquipmentType.FOGGER -> {
                        if (validEta(spec.foggerEvaporationEfficiency)) {
                            contributes = true
                            foggers += foggerProfile(inst, spec)
                        }
                    }
                }
            }
            readiness += UuidEquipmentReadiness(
                equipmentUuid = inst.id,
                type = inst.type,
                runtimeState = state,
                specification = spec,
                contributesActiveSource = contributes,
                quantitativeReady = quantitative &&
                    (inst.type != EquipmentType.EXHAUST_FAN || weather.permitsQuantitativeExhaust()),
                unavailableReason = if (contributes) null else reason
            )
        }
        return ActivePhysicsSources(circulation, exhaust, foggers, readiness, warnings)
    }

    private fun circulationProfile(
        instance: EquipmentInstance,
        spec: EquipmentPhysicsSpecification
    ): CirculationFanProfile {
        val airflow = requirePositive(spec.ratedAirflowM3s, "circulation ratedAirflowM3s")
        val diameter = requirePositive(spec.fanDiameterM, "circulation fanDiameterM")
        val speed = spec.fanSpeedFraction?.coerceIn(0.0, 1.0) ?: 1.0
        return CirculationFanProfile(
            id = instance.id,
            positionM = UserPhysicsCoordinateAdapter.positionToPhysics(instance.positionM),
            unitDirection = UserPhysicsCoordinateAdapter.yawToPhysicsForward(instance.yawDeg),
            outletDiameterM = diameter,
            ratedAirflowM3s = airflow,
            speedFraction = speed,
            jetCoreLengthM = spec.effectRadiusM?.takeIf { it.isFinite() && it > 0.0 }
                ?: UncalibratedPhase3AProfileSettings.JET_CORE_LENGTH_M,
            jetDecayCoefficient = UncalibratedPhase3AProfileSettings.JET_DECAY_COEFFICIENT,
            jetSpreadRate = UncalibratedPhase3AProfileSettings.JET_SPREAD_RATE,
            maximumModelVelocityMps = UncalibratedPhase3AProfileSettings.MAXIMUM_MODEL_VELOCITY_MPS,
            airflowProvenance = spec.provenanceClass,
            diameterProvenance = spec.provenanceClass,
            jetCoefficientProvenance = UncalibratedPhase3AProfileSettings.PROVENANCE
        )
    }

    private fun exhaustProfile(
        instance: EquipmentInstance,
        spec: EquipmentPhysicsSpecification
    ): ExhaustFanProfile {
        val airflow = requirePositive(spec.ratedAirflowM3s, "exhaust ratedAirflowM3s")
        val speed = spec.fanSpeedFraction?.coerceIn(0.0, 1.0) ?: 1.0
        return ExhaustFanProfile(
            id = instance.id,
            positionM = UserPhysicsCoordinateAdapter.positionToPhysics(instance.positionM),
            outwardNormal = UserPhysicsCoordinateAdapter.yawToPhysicsOutwardNormal(instance.yawDeg),
            ratedAirflowM3s = airflow,
            speedFraction = speed,
            airflowProvenance = spec.provenanceClass
        )
    }

    private fun foggerProfile(
        instance: EquipmentInstance,
        spec: EquipmentPhysicsSpecification
    ): FoggerProfile {
        val flow = requirePositive(spec.foggerWaterFlowKgS, "fogger waterFlowKgS")
        val eta = spec.foggerEvaporationEfficiency
            ?: throw IllegalArgumentException("fogger evaporationEfficiency is required")
        return FoggerProfile(
            id = instance.id,
            positionM = UserPhysicsCoordinateAdapter.positionToPhysics(instance.positionM),
            unitDirection = UserPhysicsCoordinateAdapter.foggerSprayPhysics(),
            waterFlowKgS = flow,
            evaporationEfficiency = eta,
            plumeLengthM = spec.effectRadiusM?.takeIf { it.isFinite() && it > 0.0 }
                ?: UncalibratedPhase3AProfileSettings.FOGGER_PLUME_LENGTH_M,
            plumeAngleDeg = UncalibratedPhase3AProfileSettings.FOGGER_PLUME_ANGLE_DEG,
            waterTemperatureC = null,
            waterFlowProvenance = spec.provenanceClass,
            evaporationEfficiencyProvenance = spec.provenanceClass
        )
    }

    private fun requirePositive(value: Double?, name: String): Double {
        require(value != null && value.isFinite() && value > 0.0) { "$name must be finite and > 0" }
        return value
    }

    private fun positive(value: Double?): Boolean = value != null && value.isFinite() && value > 0.0

    private fun validEta(value: Double?): Boolean =
        value != null && value.isFinite() && value in 0.0..1.0
}
