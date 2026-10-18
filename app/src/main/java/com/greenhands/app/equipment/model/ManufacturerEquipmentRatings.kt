package com.greenhands.app.equipment.model

import com.greenhands.app.equipment.domain.AirflowUnits
import com.greenhands.app.heat.runtime.PhysicsRuntimeCopy
import com.greenhands.app.heat.runtime.contracts.EquipmentPhysicsSpecification
import com.greenhands.app.heat.runtime.contracts.QuantitativeReadiness
import com.greenhands.app.heat.runtime.contracts.SpecificationProvenance
import com.greenhands.app.heat.simulation.model.ProvenanceClass

/**
 * User-entered or saved My Greenhouse manufacturer ratings.
 * Demo Mode never writes these values and never reads them as physics inputs.
 */
data class ManufacturerEquipmentRatings(
    val circulationAirflowCfm: Double? = null,
    val circulationAirflowM3s: Double? = null,
    val circulationThrowRadiusM: Double? = null,
    val exhaustAirflowM3s: Double? = null,
    val exhaustAirflowCfm: Double? = null,
    val foggerWaterFlowKgS: Double? = null,
    val foggerWaterFlowLph: Double? = null,
    val foggerSprayRadiusM: Double? = null,
    val foggerEvaporationEfficiency: Double? = null
) {
    fun resolvedCirculationAirflowM3s(): Double? =
        firstPositive(circulationAirflowM3s)
            ?: circulationAirflowCfm?.let { AirflowUnits.cubicFeetPerMinuteToM3s(it) }?.takeIf { it > 0.0 }

    fun resolvedExhaustAirflowM3s(): Double? =
        firstPositive(exhaustAirflowM3s)
            ?: exhaustAirflowCfm?.let { AirflowUnits.cubicFeetPerMinuteToM3s(it) }?.takeIf { it > 0.0 }

    fun resolvedFoggerWaterFlowKgS(): Double? =
        firstPositive(foggerWaterFlowKgS)
            ?: foggerWaterFlowLph?.let { AirflowUnits.litresPerHourToKgS(it) }?.takeIf { it > 0.0 }

    fun resolvedCirculationThrowRadiusM(): Double? = firstPositive(circulationThrowRadiusM)

    fun resolvedFoggerSprayRadiusM(): Double? = firstPositive(foggerSprayRadiusM)

    fun resolvedFoggerEvaporationEfficiency(): Double? =
        foggerEvaporationEfficiency?.takeIf { it.isFinite() && it in 0.0..1.0 }

    fun missingLabels(placedTypes: Set<EquipmentType>): List<String> {
        val missing = ArrayList<String>()
        if (EquipmentType.CIRCULATION_FAN in placedTypes) {
            if (resolvedCirculationAirflowM3s() == null) missing += ManufacturerSpecCopy.CIRCULATION_AIRFLOW
            if (resolvedCirculationThrowRadiusM() == null) missing += ManufacturerSpecCopy.CIRCULATION_THROW
        }
        if (EquipmentType.EXHAUST_FAN in placedTypes) {
            if (resolvedExhaustAirflowM3s() == null) missing += ManufacturerSpecCopy.EXHAUST_AIRFLOW
        }
        if (EquipmentType.FOGGER in placedTypes) {
            if (resolvedFoggerWaterFlowKgS() == null) missing += ManufacturerSpecCopy.FOGGER_FLOW
            if (resolvedFoggerSprayRadiusM() == null) missing += ManufacturerSpecCopy.FOGGER_SPRAY
        }
        return missing
    }

    fun quantitativeEffectsReady(placedTypes: Set<EquipmentType>): Boolean =
        placedTypes.isEmpty() || missingLabels(placedTypes).isEmpty()

    private fun firstPositive(value: Double?): Double? =
        value?.takeIf { it.isFinite() && it > 0.0 }

    companion object {
        val EMPTY = ManufacturerEquipmentRatings()
    }
}

object ManufacturerSpecCopy {
    const val TITLE = "Equipment specifications"
    const val ENTER = "Enter equipment specifications"
    const val SAVED = "Equipment specifications saved"
    const val INCOMPLETE = "Quantitative visualization is incomplete"
    const val CIRCULATION_AIRFLOW = "Circulation fan: rated airflow (CFM or m³/s)"
    const val CIRCULATION_THROW = "Circulation fan: effect/throw radius (m)"
    const val EXHAUST_AIRFLOW = "Exhaust fan: rated airflow (m³/s or CFM)"
    const val FOGGER_FLOW = "Fogger: rated water/mass flow (kg/s or L/h)"
    const val FOGGER_SPRAY = "Fogger: spray/effect radius (m)"
    const val CIRCULATION_CFM = "Circulation rated airflow (CFM)"
    const val CIRCULATION_M3S = "Circulation rated airflow (m³/s)"
    const val CIRCULATION_THROW_FIELD = "Circulation effect/throw radius (m)"
    const val EXHAUST_M3S = "Exhaust rated airflow (m³/s)"
    const val EXHAUST_CFM = "Exhaust rated airflow (CFM)"
    const val FOGGER_KG_S = "Fogger rated water/mass flow (kg/s)"
    const val FOGGER_LPH = "Fogger rated water/mass flow (L/h)"
    const val FOGGER_SPRAY_FIELD = "Fogger spray/effect radius (m)"
    const val FOGGER_ETA_OPTIONAL = "Fogger evaporation efficiency (0–1, optional)"
    const val UNIT_NOTE =
        "Enter manufacturer ratings. CFM converts to m³/s and L/h converts to kg/s. " +
            "Demo and test values are never substituted."
}

object SavedManufacturerSpecFactory {
    fun fromRatings(
        instances: List<EquipmentInstance>,
        ratings: ManufacturerEquipmentRatings
    ): Map<String, EquipmentPhysicsSpecification> =
        instances.filter { !it.isGhost }.associate { inst ->
            inst.id to specFor(inst, ratings)
        }

    fun specFor(
        instance: EquipmentInstance,
        ratings: ManufacturerEquipmentRatings
    ): EquipmentPhysicsSpecification {
        return when (instance.type) {
            EquipmentType.CIRCULATION_FAN -> circulationSpec(instance, ratings)
            EquipmentType.EXHAUST_FAN -> exhaustSpec(instance, ratings)
            EquipmentType.FOGGER -> foggerSpec(instance, ratings)
        }
    }

    private fun circulationSpec(
        instance: EquipmentInstance,
        ratings: ManufacturerEquipmentRatings
    ): EquipmentPhysicsSpecification {
        val airflow = ratings.resolvedCirculationAirflowM3s()
        val throwM = ratings.resolvedCirculationThrowRadiusM()
        if (airflow == null && throwM == null) {
            return EquipmentPhysicsSpecification.missing(
                instance.id,
                instance.type,
                instance.positionM,
                instance.yawDeg
            )
        }
        return manufacturerSpec(
            instance = instance,
            ratedAirflowM3s = airflow,
            effectRadiusM = throwM
        )
    }

    private fun exhaustSpec(
        instance: EquipmentInstance,
        ratings: ManufacturerEquipmentRatings
    ): EquipmentPhysicsSpecification {
        val airflow = ratings.resolvedExhaustAirflowM3s()
            ?: return EquipmentPhysicsSpecification.missing(
                instance.id,
                instance.type,
                instance.positionM,
                instance.yawDeg
            )
        return manufacturerSpec(
            instance = instance,
            ratedAirflowM3s = airflow
        )
    }

    private fun foggerSpec(
        instance: EquipmentInstance,
        ratings: ManufacturerEquipmentRatings
    ): EquipmentPhysicsSpecification {
        val flow = ratings.resolvedFoggerWaterFlowKgS()
        val spray = ratings.resolvedFoggerSprayRadiusM()
        val eta = ratings.resolvedFoggerEvaporationEfficiency()
        if (flow == null && spray == null && eta == null) {
            return EquipmentPhysicsSpecification.missing(
                instance.id,
                instance.type,
                instance.positionM,
                instance.yawDeg
            )
        }
        return manufacturerSpec(
            instance = instance,
            foggerWaterFlowKgS = flow,
            foggerEvaporationEfficiency = eta,
            effectRadiusM = spray
        )
    }

    private fun manufacturerSpec(
        instance: EquipmentInstance,
        ratedAirflowM3s: Double? = null,
        foggerWaterFlowKgS: Double? = null,
        foggerEvaporationEfficiency: Double? = null,
        effectRadiusM: Double? = null
    ): EquipmentPhysicsSpecification = EquipmentPhysicsSpecification(
        equipmentUuid = instance.id,
        equipmentType = instance.type,
        ratedAirflowM3s = ratedAirflowM3s,
        fanDiameterM = null,
        fanSpeedFraction = if (instance.type == EquipmentType.FOGGER) null else 1.0,
        foggerWaterFlowKgS = foggerWaterFlowKgS,
        foggerPressurePa = null,
        foggerEvaporationEfficiency = foggerEvaporationEfficiency,
        effectRadiusM = effectRadiusM,
        positionM = instance.positionM,
        yawDeg = instance.yawDeg,
        provenance = SpecificationProvenance.MANUFACTURER_INPUT,
        provenanceClass = ProvenanceClass.MANUFACTURER_INPUT,
        readiness = QuantitativeReadiness.QUALITATIVE_ONLY,
        unavailableReason = PhysicsRuntimeCopy.QUANTITATIVE_UNAVAILABLE_RATING
    )
}
