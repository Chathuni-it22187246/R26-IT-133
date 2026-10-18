package com.greenhands.app.harvest.detection

import com.greenhands.app.harvest.domain.HarvestArgbFrame

/**
 * Crops a detected target region (plus padding) from an ARGB frame.
 * HSV analysis should run on this crop, not the full camera frame.
 */
class TargetRegionCropper(
    private val calibration: TargetDetectionCalibration = TargetDetectionCalibration.PROJECT
) {
    fun crop(frame: HarvestArgbFrame, box: NormalizedRect): HarvestArgbFrame {
        val padded = box.padded(calibration.cropPaddingRatio)
        val x0 = (padded.left * frame.width).toInt().coerceIn(0, frame.width - 1)
        val y0 = (padded.top * frame.height).toInt().coerceIn(0, frame.height - 1)
        val x1 = (padded.right * frame.width).toInt().coerceIn(x0 + 1, frame.width)
        val y1 = (padded.bottom * frame.height).toInt().coerceIn(y0 + 1, frame.height)
        val width = x1 - x0
        val height = y1 - y0
        val out = IntArray(width * height)
        var i = 0
        for (y in y0 until y1) {
            val row = y * frame.width
            for (x in x0 until x1) {
                out[i++] = frame.argb[row + x]
            }
        }
        return HarvestArgbFrame(out, width, height)
    }
}
