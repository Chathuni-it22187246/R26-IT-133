package com.greenhands.app.harvest.domain

/**
 * Standard HSV conversion (H 0–360, S/V 0–1).
 * Color-wheel math only — not a crop or harvest threshold.
 */
data class HsvPixel(
    val hue: Float,
    val saturation: Float,
    val value: Float
)

object HsvConverter {
    fun fromRgb(red: Int, green: Int, blue: Int): HsvPixel {
        val r = red.coerceIn(0, 255) / 255f
        val g = green.coerceIn(0, 255) / 255f
        val b = blue.coerceIn(0, 255) / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min
        val hue = when {
            delta == 0f -> 0f
            max == r -> 60f * (((g - b) / delta) % 6f)
            max == g -> 60f * (((b - r) / delta) + 2f)
            else -> 60f * (((r - g) / delta) + 4f)
        }.let { if (it < 0f) it + 360f else it }
        val saturation = if (max == 0f) 0f else delta / max
        return HsvPixel(hue = hue, saturation = saturation, value = max)
    }

    fun fromArgb(argb: Int): HsvPixel = fromRgb(
        red = (argb shr 16) and 0xFF,
        green = (argb shr 8) and 0xFF,
        blue = argb and 0xFF
    )
}
