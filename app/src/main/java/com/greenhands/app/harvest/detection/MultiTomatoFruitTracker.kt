package com.greenhands.app.harvest.detection

/**
 * Matches fruit boxes across frames so the same tomato is not counted twice.
 * Greedy IoU matching using the existing stability IoU floor.
 */
class MultiTomatoFruitTracker(
    private val calibration: MultiTomatoClusterCalibration = MultiTomatoClusterCalibration.PROJECT
) {
    private var nextTrackId: Int = 1
    private var previous: List<TrackedTomatoDetection> = emptyList()

    fun reset() {
        nextTrackId = 1
        previous = emptyList()
    }

    fun assign(detections: List<TargetDetection>): List<TrackedTomatoDetection> {
        val unmatchedPrev = previous.toMutableList()
        val assigned = ArrayList<TrackedTomatoDetection>(detections.size)
        detections.forEach { detection ->
            val matchIndex = bestMatchIndex(detection, unmatchedPrev)
            if (matchIndex >= 0) {
                val matched = unmatchedPrev.removeAt(matchIndex)
                assigned += TrackedTomatoDetection(matched.trackId, detection)
            } else {
                assigned += TrackedTomatoDetection(nextTrackId++, detection)
            }
        }
        previous = assigned
        return assigned
    }

    private fun bestMatchIndex(
        detection: TargetDetection,
        candidates: List<TrackedTomatoDetection>
    ): Int {
        var bestIndex = -1
        var bestIou = calibration.trackMatchIouMin
        candidates.forEachIndexed { index, candidate ->
            val iou = detection.boundingBox.iou(candidate.detection.boundingBox)
            if (iou >= bestIou) {
                bestIou = iou
                bestIndex = index
            }
        }
        return bestIndex
    }
}
