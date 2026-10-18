package com.greenhands.app.harvest.detection

/**
 * Timing for one YOLO detect call. Does not change boxes or scores.
 */
data class HarvestYoloDetectTiming(
    val detections: List<TargetDetection>,
    val preprocessMs: Long = 0L,
    val inferMs: Long = 0L,
    val parseMs: Long = 0L,
    val rawAboveFloor: Int = 0,
    val layoutCached: Boolean = false,
    val layout: YoloTensorLayout? = null,
    val layoutFromShape: Boolean = false,
    val sigmoidApplied: Boolean = false,
    val rawMax0: Float? = null,
    val rawMax1: Float? = null,
    val interpMax0: Float? = null,
    val interpMax1: Float? = null,
    val outputShape: IntArray? = null,
    val preNmsFruit: Int = 0,
    val preNmsLeaf: Int = 0,
    val nmsDropped: Int = 0
) {
    companion object {
        val NONE = HarvestYoloDetectTiming(detections = emptyList())
    }
}
