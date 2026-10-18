package com.greenhands.app.harvest.detection

import com.greenhands.app.harvest.domain.HarvestArgbFrame
import com.greenhands.app.harvest.domain.HarvestDecisionEngine
import com.greenhands.app.harvest.domain.HsvAnalyzer
import com.greenhands.app.harvest.domain.MaturityAssessment

/**
 * Optional per-fruit cluster analysis. Live Crop Scan still captures one
 * fruit through [TargetAutoCaptureController]. This processor is a separate
 * path and no-ops unless [enabled] is true.
 *
 * Hybrid colour blobs are never split: only supplied detection boxes are used.
 */
class MultiTomatoClusterProcessor(
    private val cropper: TargetRegionCropper = TargetRegionCropper(),
    private val hsvAnalyzer: HsvAnalyzer = HsvAnalyzer(centerCropFraction = 1f),
    private val decisionEngine: HarvestDecisionEngine = HarvestDecisionEngine(),
    private val tracker: MultiTomatoFruitTracker = MultiTomatoFruitTracker(),
    private val calibration: MultiTomatoClusterCalibration = MultiTomatoClusterCalibration.PROJECT
) {
    fun resetTracks() {
        tracker.reset()
    }

    fun analyze(
        frame: HarvestArgbFrame,
        detections: List<TargetDetection>,
        maturity: MaturityAssessment,
        enabled: Boolean = HybridScanConfig.ENABLE_MULTI_TOMATO_CLUSTER_PROCESSING
    ): MultiTomatoClusterResult {
        if (!enabled) {
            return MultiTomatoClusterResult.disabled()
        }
        val selected = MultiTomatoFruitSelector.select(detections, calibration)
        val tracked = tracker.assign(selected.fruits)
        val fruits = tracked.map { trackedFruit ->
            val crop = cropper.crop(frame, trackedFruit.detection.boundingBox)
            val measurement = hsvAnalyzer.analyzeFruit(crop)
            MultiTomatoFruitResult(
                trackId = trackedFruit.trackId,
                detection = trackedFruit.detection,
                measurement = measurement,
                harvest = decisionEngine.decideTomato(measurement, maturity)
            )
        }
        val (numbered, snapshot) = ClusterFruitIdentity.freeze(fruits, frame)
        return MultiTomatoClusterResult(
            enabled = true,
            fruits = numbered,
            suppressedDuplicateCount = selected.suppressedDuplicateCount,
            rejectedCount = selected.rejectedCount,
            snapshot = snapshot
        )
    }
}
