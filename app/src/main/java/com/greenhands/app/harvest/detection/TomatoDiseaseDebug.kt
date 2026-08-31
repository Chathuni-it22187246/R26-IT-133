package com.greenhands.app.harvest.detection

import com.greenhands.app.harvest.domain.HarvestArgbFrame

/**
 * Temporary inference diagnostics. Logcat: adb logcat -s TomatoDiseaseDebug
 */
object TomatoDiseaseDebug {
    const val TAG = "TomatoDiseaseDebug"

    fun formatScores(
        values: FloatArray,
        labels: List<String> = TomatoDiseaseLabels.DEFAULT_ORDER
    ): String = values.mapIndexed { i, v ->
        val name = TomatoDiseaseLabels.labelAt(i, labels)
        "$i:$name=${"%.6f".format(v)}"
    }.joinToString(" | ")

    fun roiStats(frame: HarvestArgbFrame): String {
        val n = frame.argb.size.coerceAtLeast(1)
        var sumR = 0L
        var sumG = 0L
        var sumB = 0L
        var greenDominant = 0
        var pale = 0
        for (px in frame.argb) {
            val r = (px shr 16) and 0xFF
            val g = (px shr 8) and 0xFF
            val b = px and 0xFF
            sumR += r
            sumG += g
            sumB += b
            if (g > r && g > b) greenDominant++
            val max = maxOf(r, g, b)
            val min = minOf(r, g, b)
            if (max >= 200 && (max - min) <= 40) pale++
        }
        val first = frame.argb.first()
        return "size=${frame.width}x${frame.height} " +
            "meanRGB=${sumR / n},${sumG / n},${sumB / n} " +
            "greenDominant=${"%.3f".format(greenDominant.toFloat() / n)} " +
            "pale=${"%.3f".format(pale.toFloat() / n)} " +
            "firstPixelRGB=${(first shr 16) and 0xFF},${(first shr 8) and 0xFF},${first and 0xFF}"
    }
}
