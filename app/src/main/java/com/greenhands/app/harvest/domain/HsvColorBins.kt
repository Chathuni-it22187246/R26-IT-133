package com.greenhands.app.harvest.domain

/**
 * Descriptive HSV color-wheel bins for measurement only.
 * These are not calibrated tomato ripeness or disease thresholds.
 */
enum class HsvColorBin {
    GREEN,
    YELLOW,
    RED,
    BROWN_DARK,
    WHITE_PALE,
    OTHER
}

object HsvColorBins {
    fun classify(pixel: HsvPixel): HsvColorBin {
        val h = pixel.hue
        val s = pixel.saturation
        val v = pixel.value
        if (v < 0.22f && s >= 0.12f) return HsvColorBin.BROWN_DARK
        if (s < 0.18f && v >= 0.70f) return HsvColorBin.WHITE_PALE
        if (s < 0.18f) return HsvColorBin.OTHER
        if (h >= 70f && h < 170f) return HsvColorBin.GREEN
        if (h >= 40f && h < 70f) return HsvColorBin.YELLOW
        if (h < 15f || h >= 345f) return HsvColorBin.RED
        if (h >= 15f && h < 40f && v < 0.50f) return HsvColorBin.BROWN_DARK
        if (h >= 15f && h < 40f) return HsvColorBin.RED
        return HsvColorBin.OTHER
    }
}
