package com.greenhands.app.heat.runtime

import com.greenhands.app.ar.domain.ArRuntimeCopy

/**
 * Phase 3D.2A scientific-runtime copy. Visualization effects are not created here.
 */
object Phase3D2A {
    const val STATUS = "Phase 3D.2A deterministic physics field runtime foundation"
    const val RENDERS_AR_EFFECTS = false
    const val ADDS_NETWORKING = false
    const val ADDS_ML = false
    const val PERSISTS_RUNTIME = false
}

object PhysicsRuntimeCopy {
    const val QUANTITATIVE_UNAVAILABLE_RATING =
        "Quantitative prediction unavailable — equipment rating required."
    const val ESTIMATED_OUTSIDE_WEATHER = "Estimated outside weather"
    const val DEMO_SIMULATED_OUTSIDE_BOUNDARY =
        "Demo simulated outside boundary — not measured weather."
    const val DEMO_SIMULATED_OUTSIDE_WEATHER =
        FanPadCopy.DEMO_OUTSIDE_WEATHER
    const val DEMO_SIMULATION_DISCLAIMER = ArRuntimeCopy.SIMULATED_DISCLAIMER
    const val DEMO_SIMULATION = ArRuntimeCopy.INPUT_DEMO
    const val RECORDED_DATA = "Recorded data — not a live IoT reading"
    const val LIVE_READING = ArRuntimeCopy.INPUT_LIVE
    const val UNCALIBRATED_PREDICTION = "Uncalibrated prediction"
    const val VALIDATION_PENDING = "Validation pending"
    const val MODULE_UNAVAILABLE_WEATHER =
        "Quantitative exhaust prediction unavailable — outside weather missing or stale."
    const val OUTSIDE_GREENHOUSE = "Requested probe point is outside the greenhouse volume."
    const val REQUIRES_OPPO_BENCHMARK =
        "Adaptive coarse-grid policy requires a physical Oppo A78 5G benchmark before freezing resolution."
    const val DEMO_ASSIMILATION_RULE =
        "UNIFORM_REINITIALIZE: an explicit Demo T/RH change replaces the entire field " +
            "with a uniform indoor state from the new values. Previous cell values are discarded."
}
