package com.greenhands.app.harvest.detection

import com.greenhands.app.harvest.domain.HarvestArgbFrame

/**
 * Stable on-screen fruit numbers from captured-box position:
 * left-to-right, then top-to-bottom. Does not invent detections.
 */
object ClusterFruitIdentity {
    fun sorted(detections: List<TargetDetection>): List<TargetDetection> =
        detections.sortedWith(
            compareBy(
                { it.boundingBox.centerX },
                { it.boundingBox.centerY },
                { -it.confidence }
            )
        )

    fun displayBoxes(detections: List<TargetDetection>): List<ClusterDisplayBox> =
        sorted(detections).mapIndexed { index, detection ->
            ClusterDisplayBox(
                displayId = index + 1,
                box = detection.boundingBox,
                confidence = detection.confidence
            )
        }

    fun freeze(
        fruits: List<MultiTomatoFruitResult>,
        snapshot: HarvestArgbFrame?
    ): Pair<List<MultiTomatoFruitResult>, HarvestArgbFrame?> {
        val ordered = fruits.sortedWith(
            compareBy(
                { it.detection.boundingBox.centerX },
                { it.detection.boundingBox.centerY },
                { -it.detection.confidence }
            )
        )
        val numbered = ordered.mapIndexed { index, fruit ->
            fruit.copy(displayId = index + 1)
        }
        return numbered to snapshot
    }
}

data class ClusterDisplayBox(
    val displayId: Int,
    val box: NormalizedRect,
    val confidence: Float = 0f,
    val highlighted: Boolean = false
)
