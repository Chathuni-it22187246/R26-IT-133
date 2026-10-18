package com.greenhands.app.sensor.domain

import com.greenhands.app.sensor.model.CoverageResult
import com.greenhands.app.sensor.model.DEFAULT_COVERAGE_RADIUS_CELLS
import com.greenhands.app.sensor.model.Greenhouse
import com.greenhands.app.sensor.model.OptimizationResult
import com.greenhands.app.sensor.model.RecommendedPosition
import com.greenhands.app.sensor.model.Sensor
import com.greenhands.app.sensor.model.SensorStatus
import com.greenhands.app.sensor.model.SensorType

/**
 * Deterministic greedy same-type coverage optimizer.
 *
 * Recommends additional empty-cell positions that improve [CoverageCalculator.calculateForType]
 * coverage. Does not mutate the caller's sensor list.
 */
object SensorPlacementOptimizer {

    private const val CANDIDATE_ID_PREFIX = "__opt_cand_"

    fun optimize(
        greenhouse: Greenhouse,
        sensors: List<Sensor>,
        sensorType: SensorType,
        recommendationCount: Int
    ): OptimizationResult {
        val cappedRequest = recommendationCount.coerceAtLeast(0)
        val before = CoverageCalculator.calculateForType(greenhouse, sensors, sensorType)

        if (cappedRequest == 0) {
            return emptyResult(sensorType, 0, before)
        }

        val occupied = occupiedCells(sensors)
        val simulated = sensors.toMutableList()
        val recommendations = mutableListOf<RecommendedPosition>()
        var stepBefore = before

        while (recommendations.size < cappedRequest) {
            val emptyCells = emptyCandidateCells(greenhouse, occupied)
            if (emptyCells.isEmpty()) break

            val best = emptyCells
                .map { (cx, cy) ->
                    evaluateCandidate(
                        greenhouse = greenhouse,
                        sensors = simulated,
                        sensorType = sensorType,
                        cellX = cx,
                        cellY = cy,
                        before = stepBefore
                    )
                }
                // compareByDescending → best candidate sorts first (do not use maxWith).
                .sortedWith(CandidateEvaluation.RANKING)
                .firstOrNull()
                ?: break

            // Stop when the best remaining cell does not improve coverage or blinds.
            if (best.coverageImprovement <= 0.0 && best.blindSpotReduction <= 0) break

            val rank = recommendations.size + 1
            recommendations += RecommendedPosition(
                x = best.x,
                y = best.y,
                rank = rank,
                coverageImprovement = best.coverageImprovement,
                blindSpotReduction = best.blindSpotReduction,
                score = best.score
            )
            occupied += cellKey(best.x, best.y)
            simulated += temporarySensor(sensorType, best.x, best.y, rank)
            stepBefore = best.after
        }

        val predicted = if (recommendations.isEmpty()) {
            before
        } else {
            CoverageCalculator.calculateForType(greenhouse, simulated, sensorType)
        }

        return OptimizationResult(
            sensorType = sensorType,
            requestedSensorCount = cappedRequest,
            recommendedPositions = recommendations,
            beforeCoverage = before.overallCoveragePercent,
            predictedCoverage = predicted.overallCoveragePercent,
            coverageImprovement = predicted.overallCoveragePercent - before.overallCoveragePercent,
            beforeBlindSpots = before.blindSpotCells,
            predictedBlindSpots = predicted.blindSpotCells,
            blindSpotReduction = before.blindSpotCells - predicted.blindSpotCells,
            beforeOverlap = before.overlapCells,
            predictedOverlap = predicted.overlapCells
        )
    }

    private fun emptyResult(
        sensorType: SensorType,
        requested: Int,
        before: CoverageResult
    ) = OptimizationResult(
        sensorType = sensorType,
        requestedSensorCount = requested,
        recommendedPositions = emptyList(),
        beforeCoverage = before.overallCoveragePercent,
        predictedCoverage = before.overallCoveragePercent,
        coverageImprovement = 0.0,
        beforeBlindSpots = before.blindSpotCells,
        predictedBlindSpots = before.blindSpotCells,
        blindSpotReduction = 0,
        beforeOverlap = before.overlapCells,
        predictedOverlap = before.overlapCells
    )

    private fun evaluateCandidate(
        greenhouse: Greenhouse,
        sensors: List<Sensor>,
        sensorType: SensorType,
        cellX: Int,
        cellY: Int,
        before: CoverageResult
    ): CandidateEvaluation {
        val x = cellX.toDouble()
        val y = cellY.toDouble()
        val trial = sensors + temporarySensor(sensorType, x, y, trialId = -1)
        val after = CoverageCalculator.calculateForType(greenhouse, trial, sensorType)
        val coverageImprovement = after.overallCoveragePercent - before.overallCoveragePercent
        val blindSpotReduction = before.blindSpotCells - after.blindSpotCells
        val overlapChange = after.overlapCells - before.overlapCells
        val minDistance = minDistanceToSameTypeActive(sensors, sensorType, x, y)
        val score = compositeScore(
            coverageImprovement = coverageImprovement,
            blindSpotReduction = blindSpotReduction,
            overlapChange = overlapChange,
            minDistance = minDistance
        )
        return CandidateEvaluation(
            x = x,
            y = y,
            coverageImprovement = coverageImprovement,
            blindSpotReduction = blindSpotReduction,
            overlapChange = overlapChange,
            minDistance = minDistance,
            score = score,
            after = after
        )
    }

    /**
     * Higher is better. Encodes ranking priorities into a single deterministic value.
     * Coordinate tie-breaks are applied separately in [CandidateEvaluation.RANKING].
     */
    fun compositeScore(
        coverageImprovement: Double,
        blindSpotReduction: Int,
        overlapChange: Int,
        minDistance: Double
    ): Double =
        coverageImprovement * 1_000_000.0 +
            blindSpotReduction * 1_000.0 -
            overlapChange * 10.0 +
            minDistance.coerceAtMost(1_000.0)

    private fun minDistanceToSameTypeActive(
        sensors: List<Sensor>,
        sensorType: SensorType,
        x: Double,
        y: Double
    ): Double {
        val peers = sensors.filter {
            it.type == sensorType &&
                it.status == SensorStatus.ACTIVE
        }
        if (peers.isEmpty()) return 1_000.0
        return peers.minOf { CoverageCalculator.distance(it.x, it.y, x, y) }
    }

    private fun occupiedCells(sensors: List<Sensor>): MutableSet<Long> =
        sensors.mapTo(mutableSetOf()) { cellKey(it.x, it.y) }

    private fun emptyCandidateCells(
        greenhouse: Greenhouse,
        occupied: Set<Long>
    ): List<Pair<Int, Int>> {
        val cells = ArrayList<Pair<Int, Int>>(greenhouse.totalCells - occupied.size)
        for (y in greenhouse.rows) {
            for (x in greenhouse.columns) {
                if (cellKey(x.toDouble(), y.toDouble()) !in occupied) {
                    cells += x to y
                }
            }
        }
        return cells
    }

    private fun cellKey(x: Double, y: Double): Long {
        val cx = kotlin.math.floor(x).toInt()
        val cy = kotlin.math.floor(y).toInt()
        return (cx.toLong() shl 32) or (cy.toLong() and 0xffffffffL)
    }

    private fun temporarySensor(
        type: SensorType,
        x: Double,
        y: Double,
        trialId: Int
    ): Sensor = Sensor(
        id = "$CANDIDATE_ID_PREFIX$trialId",
        type = type,
        x = x,
        y = y,
        coverageRadius = DEFAULT_COVERAGE_RADIUS_CELLS,
        status = SensorStatus.ACTIVE
    )

    private data class CandidateEvaluation(
        val x: Double,
        val y: Double,
        val coverageImprovement: Double,
        val blindSpotReduction: Int,
        val overlapChange: Int,
        val minDistance: Double,
        val score: Double,
        val after: CoverageResult
    ) {
        companion object {
            val RANKING: Comparator<CandidateEvaluation> =
                compareByDescending<CandidateEvaluation> { it.coverageImprovement }
                    .thenByDescending { it.blindSpotReduction }
                    .thenBy { it.overlapChange }
                    .thenByDescending { it.minDistance }
                    .thenBy { it.x }
                    .thenBy { it.y }
        }
    }
}
