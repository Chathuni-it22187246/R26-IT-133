package com.greenhands.app.harvest.model

/**
 * Measured fruit color from a camera frame.
 * Percentages are HSV bin counts, not a harvest decision.
 */
data class FruitColorMeasurement(
    val sampledPixelCount: Int,
    val hueMean: Float,
    val saturationMean: Float,
    val valueMean: Float,
    val greenPercent: Float,
    val yellowPercent: Float,
    val redPercent: Float,
    val brownDarkPercent: Float,
    val otherPercent: Float,
    val greenSampledCount: Int = 0,
    val greenValueMean: Float? = null,
    val greenSaturationMean: Float? = null
) {
    val hasSamples: Boolean get() = sampledPixelCount > 0

    fun summaryLine(): String = listOf(
        "Green ${hsvPercentLabel(greenPercent)}",
        "Yellow ${hsvPercentLabel(yellowPercent)}",
        "Red ${hsvPercentLabel(redPercent)}",
        "Brown/Dark ${hsvPercentLabel(brownDarkPercent)}"
    ).joinToString(" · ")
}
