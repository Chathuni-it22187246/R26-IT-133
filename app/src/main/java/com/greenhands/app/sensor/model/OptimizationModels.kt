package com.greenhands.app.sensor.model

/**
 * One recommended additional sensor position from the greedy optimizer.
 * Not a real [Sensor] until the user applies recommendations.
 */
data class RecommendedPosition(
    val x: Double,
    val y: Double,
    /** 1-based rank in the greedy selection order. */
    val rank: Int,
    /** Same-type overallCoveragePercent gain for this greedy step. */
    val coverageImprovement: Double,
    /** Blind-spot cell count reduced in this greedy step. */
    val blindSpotReduction: Int,
    /** Deterministic composite score used for ranking this step. */
    val score: Double
)

/**
 * Full result of a greedy same-type placement optimization run.
 * Metrics compare the initial type coverage to the simulated set with all recommendations.
 */
data class OptimizationResult(
    val sensorType: SensorType,
    val requestedSensorCount: Int,
    val recommendedPositions: List<RecommendedPosition>,
    val beforeCoverage: Double,
    val predictedCoverage: Double,
    val coverageImprovement: Double,
    val beforeBlindSpots: Int,
    val predictedBlindSpots: Int,
    val blindSpotReduction: Int,
    val beforeOverlap: Int,
    val predictedOverlap: Int
) {
    val appliedRecommendationCount: Int get() = recommendedPositions.size
}

/**
 * One evaluated "add N sensors" scenario from [SensorCountOptimizationEvaluator].
 */
data class OptimizationCountCandidate(
    /** Evaluated additional-sensor count (1..max). */
    val additionalCount: Int,
    /** Positions the greedy optimizer actually placed (may be &lt; [additionalCount]). */
    val actualAdditionalCount: Int,
    val result: OptimizationResult,
    val finalSensorCount: Int,
    /** Coverage gain vs the configuration before any additions. */
    val totalCoverageGain: Double,
    /** Coverage gain vs the previous evaluated count (0 for the first row). */
    val marginalCoverageGain: Double,
    /** Blind-spot cell reduction vs the previous evaluated count. */
    val marginalBlindSpotReduction: Int,
    val predictedBlindSpotPercent: Double,
    val isRecommended: Boolean = false
)

enum class OptimizationSelectionReason {
    /** Highest coverage, then fewest blind spots, then fewest additional sensors. */
    MINIMUM_COUNT_AT_BEST_COVERAGE,
    /** No evaluated count improved coverage or blind spots. */
    NO_IMPROVEMENT,
    /** User chose a non-recommended evaluated alternative. */
    USER_ALTERNATIVE
}

/**
 * Multi-count evaluation: runs [SensorPlacementOptimizer] for each candidate count.
 */
data class OptimizationEvaluation(
    val sensorType: SensorType,
    val currentSensorCount: Int,
    val beforeCoverage: Double,
    val beforeBlindSpotPercent: Double,
    val beforeBlindSpotCells: Int,
    val beforeOverlap: Int,
    val candidates: List<OptimizationCountCandidate>,
    val recommendedAdditionalCount: Int?,
    val selectionReason: OptimizationSelectionReason
) {
    val recommendedCandidate: OptimizationCountCandidate?
        get() = candidates.firstOrNull { it.isRecommended }

    fun candidateFor(additionalCount: Int): OptimizationCountCandidate? =
        candidates.firstOrNull { it.additionalCount == additionalCount }
}

/**
 * Captured when the user explicitly applies selected recommendations.
 * Shown as a before/after comparison; does not mutate sensors on its own.
 */
data class OptimizationApplySummary(
    val sensorType: SensorType,
    val beforeSensorCount: Int,
    val afterSensorCount: Int,
    val appliedRecommendationCount: Int,
    val beforeCoveragePercent: Double,
    val afterCoveragePercent: Double,
    val coverageImprovement: Double,
    val beforeBlindSpotPercent: Double,
    val afterBlindSpotPercent: Double,
    val beforeBlindSpotCells: Int,
    val afterBlindSpotCells: Int,
    val beforeOverlapCells: Int,
    val afterOverlapCells: Int
)
