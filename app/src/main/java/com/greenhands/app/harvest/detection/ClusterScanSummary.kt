package com.greenhands.app.harvest.detection

/**
 * Text helpers for Cluster Scan summaries. Count is detections in one scan,
 * not a plant-level tomato census.
 */
object ClusterScanSummary {
    const val OVERLAY_FRUIT_LIMIT = 5

    fun fruitLine(displayId: Int, displayLabel: String, ripenessLabel: String): String =
        "Fruit $displayId: $displayLabel · $ripenessLabel"

    fun overlayLines(fruits: List<MultiTomatoFruitResult>): List<String> =
        fruits.sortedBy { fruit ->
            if (fruit.displayId > 0) fruit.displayId else Int.MAX_VALUE
        }.take(OVERLAY_FRUIT_LIMIT).mapIndexed { index, fruit ->
            fruitLine(
                displayId = if (fruit.displayId > 0) fruit.displayId else index + 1,
                displayLabel = fruit.harvest.displayLabel,
                ripenessLabel = fruit.harvest.ripeness.label
            )
        }

    fun readyCount(fruits: List<MultiTomatoFruitResult>): Int =
        fruits.count {
            it.harvest.decision == com.greenhands.app.harvest.model.HarvestDecision.READY_TO_HARVEST
        }

    fun notReadyCount(fruits: List<MultiTomatoFruitResult>): Int =
        fruits.count {
            it.harvest.decision == com.greenhands.app.harvest.model.HarvestDecision.NOT_READY
        }

    fun uncertainCount(fruits: List<MultiTomatoFruitResult>): Int =
        fruits.count {
            it.harvest.decision == com.greenhands.app.harvest.model.HarvestDecision.UNCERTAIN ||
                it.harvest.scanRequired
        }
}
