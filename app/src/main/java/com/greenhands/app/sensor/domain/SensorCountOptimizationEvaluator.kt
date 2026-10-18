package com.greenhands.app.sensor.domain

import com.greenhands.app.sensor.model.CoverageResult
import com.greenhands.app.sensor.model.Greenhouse
import com.greenhands.app.sensor.model.OptimizationCountCandidate
import com.greenhands.app.sensor.model.OptimizationEvaluation
import com.greenhands.app.sensor.model.OptimizationSelectionReason
import com.greenhands.app.sensor.model.Sensor
import com.greenhands.app.sensor.model.SensorType
import kotlin.math.abs

/**
 * Evaluates multiple additional-sensor counts using the existing greedy
 * [SensorPlacementOptimizer] and selects a recommended minimum-practical count.
 *
 * No invented coverage threshold — selection is lexicographic:
 * 1. Maximize predicted coverage
 * 2. Minimize predicted blind spots
 * 3. Minimize additional sensor count (deployment proxy)
 * 4. Minimize same-type overlap
 */
object SensorCountOptimizationEvaluator {

    /** Matches the prior UI cap; also bounded by empty grid cells. */
    const val MAX_ADDITIONAL_SENSORS = 4

    fun evaluate(
        greenhouse: Greenhouse,
        sensors: List<Sensor>,
        sensorType: SensorType,
        maxAdditional: Int = MAX_ADDITIONAL_SENSORS
    ): OptimizationEvaluation {
        val before = CoverageCalculator.calculateForType(greenhouse, sensors, sensorType)
        val maxEval = maxEvaluableCount(greenhouse, sensors, maxAdditional)
        if (maxEval <= 0) {
            return emptyEvaluation(sensorType, sensors.size, before)
        }

        var prevCoverage = before.overallCoveragePercent
        var prevBlind = before.blindSpotCells
        val candidates = mutableListOf<OptimizationCountCandidate>()

        for (count in 1..maxEval) {
            val result = SensorPlacementOptimizer.optimize(
                greenhouse = greenhouse,
                sensors = sensors,
                sensorType = sensorType,
                recommendationCount = count
            )
            if (result.recommendedPositions.isEmpty()) break

            val marginalCov = result.predictedCoverage - prevCoverage
            val marginalBlind = prevBlind - result.predictedBlindSpots
            val blindPct = if (before.totalCells > 0) {
                result.predictedBlindSpots * 100.0 / before.totalCells.toDouble()
            } else {
                0.0
            }
            candidates += OptimizationCountCandidate(
                additionalCount = count,
                actualAdditionalCount = result.recommendedPositions.size,
                result = result,
                finalSensorCount = sensors.size + result.recommendedPositions.size,
                totalCoverageGain = result.predictedCoverage - before.overallCoveragePercent,
                marginalCoverageGain = marginalCov,
                marginalBlindSpotReduction = marginalBlind,
                predictedBlindSpotPercent = blindPct
            )
            prevCoverage = result.predictedCoverage
            prevBlind = result.predictedBlindSpots
        }

        val recommended = selectRecommended(candidates)
        val reason = when {
            recommended == null -> OptimizationSelectionReason.NO_IMPROVEMENT
            else -> OptimizationSelectionReason.MINIMUM_COUNT_AT_BEST_COVERAGE
        }
        val marked = candidates.map { c ->
            c.copy(isRecommended = recommended != null && c.additionalCount == recommended.additionalCount)
        }

        return OptimizationEvaluation(
            sensorType = sensorType,
            currentSensorCount = sensors.size,
            beforeCoverage = before.overallCoveragePercent,
            beforeBlindSpotPercent = before.blindSpotPercent,
            beforeBlindSpotCells = before.blindSpotCells,
            beforeOverlap = before.overlapCells,
            candidates = marked,
            recommendedAdditionalCount = recommended?.additionalCount,
            selectionReason = reason
        )
    }

    /**
     * Smallest additional-sensor count among evaluated options that achieves the
     * best coverage, then fewest blind spots, then lowest overlap.
     */
    fun selectRecommended(
        candidates: List<OptimizationCountCandidate>
    ): OptimizationCountCandidate? {
        if (candidates.isEmpty()) return null
        val improving = candidates.filter { c ->
            c.result.coverageImprovement > 1e-9 || c.result.blindSpotReduction > 0
        }
        if (improving.isEmpty()) return null

        val maxCoverage = improving.maxOf { it.result.predictedCoverage }
        val atMaxCoverage = improving.filter {
            abs(it.result.predictedCoverage - maxCoverage) < 1e-6
        }
        val minBlind = atMaxCoverage.minOf { it.result.predictedBlindSpots }
        val atMinBlind = atMaxCoverage.filter { it.result.predictedBlindSpots == minBlind }

        return atMinBlind.minWithOrNull(
            compareBy<OptimizationCountCandidate> { it.actualAdditionalCount }
                .thenBy { it.result.predictedOverlap }
                .thenBy { it.additionalCount }
        )
    }

    fun maxEvaluableCount(
        greenhouse: Greenhouse,
        sensors: List<Sensor>,
        cap: Int = MAX_ADDITIONAL_SENSORS
    ): Int {
        val occupied = sensors.map {
            kotlin.math.floor(it.x).toInt() to kotlin.math.floor(it.y).toInt()
        }.toSet()
        val emptyCells = (greenhouse.totalCells - occupied.size).coerceAtLeast(0)
        return minOf(cap.coerceAtLeast(0), emptyCells)
    }

    private fun emptyEvaluation(
        sensorType: SensorType,
        currentSensorCount: Int,
        before: CoverageResult
    ) = OptimizationEvaluation(
        sensorType = sensorType,
        currentSensorCount = currentSensorCount,
        beforeCoverage = before.overallCoveragePercent,
        beforeBlindSpotPercent = before.blindSpotPercent,
        beforeBlindSpotCells = before.blindSpotCells,
        beforeOverlap = before.overlapCells,
        candidates = emptyList(),
        recommendedAdditionalCount = null,
        selectionReason = OptimizationSelectionReason.NO_IMPROVEMENT
    )
}
