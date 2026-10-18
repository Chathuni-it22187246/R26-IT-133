package com.greenhands.app.heat.simulation.model

/**
 * Immutable equipment configuration. Airflow, diameter and water flow are inputs,
 * never hidden as magic constants (PHYSICS_MODEL.md §9, §19).
 *
 * User-visible Preview profiles are out of Phase 3A. Test fixtures must be named
 * as mathematical fixtures and classified PREVIEW_ASSUMPTION.
 */
data class CirculationFanProfile(
    val id: String,
    val positionM: Vec3,
    val unitDirection: Vec3,
    val outletDiameterM: Double,
    val ratedAirflowM3s: Double,
    val speedFraction: Double,
    val jetCoreLengthM: Double,
    val jetDecayCoefficient: Double,
    val jetSpreadRate: Double,
    val maximumModelVelocityMps: Double,
    val airflowProvenance: ProvenanceClass,
    val diameterProvenance: ProvenanceClass,
    val jetCoefficientProvenance: ProvenanceClass = ProvenanceClass.CALIBRATED_COEFFICIENT
) {
    init {
        require(id.isNotBlank()) { "circulation fan id is required" }
        require(outletDiameterM > 0.0 && outletDiameterM.isFinite()) { "outletDiameterM must be > 0 m" }
        require(ratedAirflowM3s >= 0.0 && ratedAirflowM3s.isFinite()) { "ratedAirflowM3s must be >= 0 m³/s" }
        require(speedFraction in 0.0..1.0 && speedFraction.isFinite()) { "speedFraction must be in 0..1" }
        require(jetCoreLengthM.isFinite()) { "jetCoreLengthM must be finite" }
        require(jetDecayCoefficient.isFinite() && jetDecayCoefficient >= 0.0) { "jetDecayCoefficient invalid" }
        require(jetSpreadRate.isFinite() && jetSpreadRate >= 0.0) { "jetSpreadRate invalid" }
        require(maximumModelVelocityMps > 0.0 && maximumModelVelocityMps.isFinite()) {
            "maximumModelVelocityMps must be > 0 m/s"
        }
        require(unitDirection.lengthM() > 0.0) { "unitDirection must be non-zero" }
    }

    fun directionHat(): Vec3 = unitDirection.unitOrZero()

    fun outletAreaM2(): Double = Math.PI * outletDiameterM * outletDiameterM / 4.0

    fun volumeFlowM3s(activeSpeedFraction: Double): Double =
        ratedAirflowM3s * activeSpeedFraction.coerceIn(0.0, 1.0)
}

data class ExhaustFanProfile(
    val id: String,
    val positionM: Vec3,
    val outwardNormal: Vec3,
    val ratedAirflowM3s: Double,
    val speedFraction: Double,
    val airflowProvenance: ProvenanceClass
) {
    init {
        require(id.isNotBlank()) { "exhaust fan id is required" }
        require(ratedAirflowM3s >= 0.0 && ratedAirflowM3s.isFinite()) { "ratedAirflowM3s must be >= 0 m³/s" }
        require(speedFraction in 0.0..1.0) { "speedFraction must be in 0..1" }
        require(outwardNormal.lengthM() > 0.0) { "outwardNormal must be non-zero" }
    }

    fun volumeFlowM3s(activeSpeedFraction: Double): Double =
        ratedAirflowM3s * activeSpeedFraction.coerceIn(0.0, 1.0)
}

data class IntakeOpening(
    val id: String,
    val positionM: Vec3,
    val inwardNormal: Vec3,
    val areaM2: Double
) {
    init {
        require(areaM2 >= 0.0 && areaM2.isFinite()) { "intake areaM2 must be >= 0 m²" }
    }
}

data class FoggerProfile(
    val id: String,
    val positionM: Vec3,
    val unitDirection: Vec3,
    val waterFlowKgS: Double,
    val evaporationEfficiency: Double,
    val plumeLengthM: Double,
    val plumeAngleDeg: Double,
    val waterTemperatureC: Double? = null,
    val waterFlowProvenance: ProvenanceClass,
    val evaporationEfficiencyProvenance: ProvenanceClass = ProvenanceClass.CALIBRATED_COEFFICIENT
) {
    init {
        require(id.isNotBlank()) { "fogger id is required" }
        require(waterFlowKgS >= 0.0 && waterFlowKgS.isFinite()) { "waterFlowKgS must be >= 0 kg/s" }
        require(evaporationEfficiency in 0.0..1.0) { "evaporationEfficiency must be in 0..1" }
        require(plumeLengthM > 0.0 && plumeLengthM.isFinite()) { "plumeLengthM must be > 0 m" }
        require(plumeAngleDeg > 0.0 && plumeAngleDeg < 180.0) { "plumeAngleDeg must be in (0, 180)" }
        require(unitDirection.lengthM() > 0.0) { "fogger unitDirection must be non-zero" }
    }

    fun directionHat(): Vec3 = unitDirection.unitOrZero()
}
