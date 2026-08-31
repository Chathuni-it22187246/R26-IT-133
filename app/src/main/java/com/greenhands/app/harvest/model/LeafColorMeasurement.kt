package com.greenhands.app.harvest.model

/**
 * Measured leaf color from a camera frame.
 * [discoloredPercent] is non-green HSV bins (yellow + brown/dark + white/pale).
 * It is not a disease diagnosis.
 */
data class LeafColorMeasurement(
    val sampledPixelCount: Int,
    val hueMean: Float,
    val saturationMean: Float,
    val valueMean: Float,
    val greenPercent: Float,
    val yellowPercent: Float,
    val brownDarkPercent: Float,
    val whitePalePercent: Float,
    val discoloredPercent: Float,
    val otherPercent: Float
) {
    val hasSamples: Boolean get() = sampledPixelCount > 0

    fun summaryLine(): String = listOf(
        "Green ${hsvPercentLabel(greenPercent)}",
        "Yellow ${hsvPercentLabel(yellowPercent)}",
        "Brown/Dark ${hsvPercentLabel(brownDarkPercent)}",
        "White/Pale ${hsvPercentLabel(whitePalePercent)}",
        "Discolored ${hsvPercentLabel(discoloredPercent)}"
    ).joinToString(" · ")
}
