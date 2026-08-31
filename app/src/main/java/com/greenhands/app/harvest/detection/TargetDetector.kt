package com.greenhands.app.harvest.detection

import com.greenhands.app.harvest.domain.HarvestArgbFrame

/**
 * Optional on-device detector API.
 *
 * The primary harvest scan path is [HybridTargetValidator] (project-calibrated
 * visual target validation). TFLite implementations are experimental and must
 * not be required for scanning.
 *
 * When a TFLite asset is missing, [isModelReady] is false and [detect] returns
 * an empty list — never a fabricated target.
 */
interface TargetDetector : AutoCloseable {
    val isModelReady: Boolean

    fun detect(frame: HarvestArgbFrame, timestampMs: Long): List<TargetDetection>

    override fun close() = Unit
}

object UnavailableTargetDetector : TargetDetector {
    override val isModelReady: Boolean = false

    override fun detect(frame: HarvestArgbFrame, timestampMs: Long): List<TargetDetection> =
        emptyList()
}
