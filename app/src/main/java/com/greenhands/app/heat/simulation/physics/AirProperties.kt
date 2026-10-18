package com.greenhands.app.heat.simulation.physics

import com.greenhands.app.heat.simulation.model.ParameterRecord
import com.greenhands.app.heat.simulation.model.ProvenanceClass

/**
 * Immutable moist-air properties used by the solver (PHYSICS_MODEL.md §7.3).
 * Values are not hard-coded inside the transport loop.
 */
data class AirProperties(
    val pressurePa: Double,
    val airDensityKgM3: Double,
    val dryAirDensityKgM3: Double,
    val specificHeatJkgK: Double,
    val provenance: List<ParameterRecord>
) {
    init {
        require(pressurePa > 0.0 && pressurePa.isFinite()) { "pressurePa must be finite and > 0 Pa" }
        require(airDensityKgM3 > 0.0 && airDensityKgM3.isFinite()) { "airDensityKgM3 must be > 0 kg/m³" }
        require(dryAirDensityKgM3 > 0.0 && dryAirDensityKgM3.isFinite()) { "dryAirDensityKgM3 must be > 0 kg/m³" }
        require(specificHeatJkgK > 0.0 && specificHeatJkgK.isFinite()) { "specificHeatJkgK must be > 0 J/(kg·K)" }
    }

    companion object {
        /**
         * Preview profile near atmospheric conditions, citing Chen et al. (2014)
         * rho = 1.225 kg/m³, cp = 1005 J/(kg·K). Pressure 101325 Pa is a Preview assumption.
         */
        fun chen2014Preview(): AirProperties = AirProperties(
            pressurePa = 101_325.0,
            airDensityKgM3 = 1.225,
            dryAirDensityKgM3 = 1.225,
            specificHeatJkgK = 1005.0,
            provenance = listOf(
                ParameterRecord(
                    name = "pressurePa",
                    valueText = "101325",
                    unit = "Pa",
                    provenance = ProvenanceClass.PREVIEW_ASSUMPTION,
                    note = "Standard sea-level pressure used only as a Preview/test value"
                ),
                ParameterRecord(
                    name = "airDensityKgM3",
                    valueText = "1.225",
                    unit = "kg/m³",
                    provenance = ProvenanceClass.REFERENCE_PROPERTY,
                    note = "Chen et al. (2014) greenhouse CFD case, doi:10.1155/2014/712740"
                ),
                ParameterRecord(
                    name = "dryAirDensityKgM3",
                    valueText = "1.225",
                    unit = "kg/m³",
                    provenance = ProvenanceClass.PREVIEW_ASSUMPTION,
                    note = "Taken equal to Chen moist-air density for this Preview profile"
                ),
                ParameterRecord(
                    name = "specificHeatJkgK",
                    valueText = "1005",
                    unit = "J/(kg·K)",
                    provenance = ProvenanceClass.REFERENCE_PROPERTY,
                    note = "Chen et al. (2014) cp for the cited CFD case"
                )
            )
        )
    }
}
