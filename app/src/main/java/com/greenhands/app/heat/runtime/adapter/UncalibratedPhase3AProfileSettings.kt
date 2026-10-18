package com.greenhands.app.heat.runtime.adapter

import com.greenhands.app.heat.simulation.model.ProvenanceClass

/**
 * Frozen Phase 3A profile fields required to construct [com.greenhands.app.heat.simulation.model.CirculationFanProfile]
 * and [com.greenhands.app.heat.simulation.model.FoggerProfile]. These are uncalibrated
 * CALIBRATED_COEFFICIENT values, not Demo UI ratings and not scientific evidence.
 * They must not be copied into My Greenhouse as manufacturer inputs.
 */
object UncalibratedPhase3AProfileSettings {
    const val EFFECTIVE_THERMAL_DIFFUSIVITY_M2S = 0.02
    const val EFFECTIVE_MOISTURE_DIFFUSIVITY_M2S = 0.02
    const val JET_CORE_LENGTH_M = 0.40
    const val JET_DECAY_COEFFICIENT = 6.0
    const val JET_SPREAD_RATE = 0.12
    const val MAXIMUM_MODEL_VELOCITY_MPS = 12.0
    const val FOGGER_PLUME_LENGTH_M = 2.0
    const val FOGGER_PLUME_ANGLE_DEG = 40.0

    val PROVENANCE: ProvenanceClass = ProvenanceClass.CALIBRATED_COEFFICIENT

    const val NOTE =
        "Uncalibrated frozen Phase 3A profile fields required to construct the existing solver profiles. " +
            "Not Demo UI ratings, not manufacturer inputs, and not literature coefficients."
}
