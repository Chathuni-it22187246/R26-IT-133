package com.greenhands.app.harvest.detection

import com.greenhands.app.harvest.model.FruitColorMeasurement
import com.greenhands.app.harvest.model.HarvestDecisionResult

data class MultiTomatoSelection(
    val fruits: List<TargetDetection>,
    val suppressedDuplicateCount: Int,
    val rejectedCount: Int
)

data class TrackedTomatoDetection(
    val trackId: Int,
    val detection: TargetDetection
)

data class MultiTomatoFruitResult(
    val trackId: Int,
    val detection: TargetDetection,
    val measurement: FruitColorMeasurement?,
    val harvest: HarvestDecisionResult,
    val displayId: Int = 0
)

data class MultiTomatoClusterResult(
    val enabled: Boolean,
    val fruits: List<MultiTomatoFruitResult>,
    val suppressedDuplicateCount: Int,
    val rejectedCount: Int,
    val snapshot: com.greenhands.app.harvest.domain.HarvestArgbFrame? = null
) {
    val fruitCount: Int get() = fruits.size

    companion object {
        fun disabled(): MultiTomatoClusterResult = MultiTomatoClusterResult(
            enabled = false,
            fruits = emptyList(),
            suppressedDuplicateCount = 0,
            rejectedCount = 0,
            snapshot = null
        )
    }
}
