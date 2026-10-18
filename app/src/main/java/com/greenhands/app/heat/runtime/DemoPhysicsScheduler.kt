package com.greenhands.app.heat.runtime

/**
 * Fixed-step scheduler between the AR frame callback and the physics solver.
 *
 * [publicationIntervalS] is a product/runtime HUD interval, not a validated
 * scientific constant. The frozen Phase 3A engine still chooses CFL substeps
 * inside each accepted outer step.
 */
data class DemoPhysicsSchedulerConfig(
    val publicationIntervalS: Double = 1.0,
    val pauseResetThresholdS: Double = 2.0,
    val maxAcceptedStepsPerTick: Int = 1
) {
    init {
        require(publicationIntervalS.isFinite() && publicationIntervalS > 0.0) {
            "publicationIntervalS must be finite and > 0 s"
        }
        require(pauseResetThresholdS.isFinite() && pauseResetThresholdS > publicationIntervalS) {
            "pauseResetThresholdS must be finite and greater than publicationIntervalS"
        }
        require(maxAcceptedStepsPerTick >= 1) {
            "maxAcceptedStepsPerTick must be >= 1"
        }
    }
}

data class AcceptedPhysicsWork(
    val stepCount: Int,
    val stepSizeS: Double
)

class DemoPhysicsScheduler(
    val config: DemoPhysicsSchedulerConfig = DemoPhysicsSchedulerConfig()
) {
    private var accumulatorS: Double = 0.0

    fun accumulatorS(): Double = accumulatorS

    fun reset() {
        accumulatorS = 0.0
    }

    fun acceptFrame(dtS: Double): AcceptedPhysicsWork {
        val interval = config.publicationIntervalS
        if (!dtS.isFinite() || dtS <= 0.0) {
            return AcceptedPhysicsWork(0, interval)
        }
        if (dtS >= config.pauseResetThresholdS) {
            accumulatorS = 0.0
            return AcceptedPhysicsWork(0, interval)
        }
        accumulatorS += dtS
        var steps = 0
        while (hasFullStep() && steps < config.maxAcceptedStepsPerTick) {
            accumulatorS = (accumulatorS - interval).coerceAtLeast(0.0)
            if (accumulatorS < TIME_EPSILON_S) accumulatorS = 0.0
            steps++
        }
        if (hasFullStep()) {
            accumulatorS %= interval
            if (accumulatorS < TIME_EPSILON_S || accumulatorS > interval - TIME_EPSILON_S) {
                accumulatorS = 0.0
            }
        }
        return AcceptedPhysicsWork(steps, interval)
    }

    private fun hasFullStep(): Boolean =
        accumulatorS >= config.publicationIntervalS - TIME_EPSILON_S

    companion object {
        private const val TIME_EPSILON_S = 1e-9
    }
}
