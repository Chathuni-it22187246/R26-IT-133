package com.greenhands.app.harvest.domain

/**
 * PROJECT CALIBRATION for the simplified HEALTHY / UNHEALTHY demo decision.
 * Derived from [TomatoLeafHealthCalibration] warning/severe bands, then widened
 * so small camera/lighting swings do not flip status. Not DOA facts.
 */
data class SimplePlantHealthCalibration(
    val minSampledPixels: Int,
    val minLeafColourPercent: Float,
    val minValueMean: Float,
    val maxValueMean: Float,
    val blownOutMaxSaturation: Float,
    val healthyDiscoloredMax: Float,
    val healthyYellowMax: Float,
    val healthyBrownMax: Float,
    val healthyPaleMax: Float,
    val unhealthyDiscoloredMin: Float,
    val unhealthyYellowMin: Float,
    val unhealthyBrownMin: Float,
    val unhealthyPaleMin: Float
) {
    companion object {
        val PROJECT = SimplePlantHealthCalibration(
            minSampledPixels = TomatoLeafHealthCalibration.PROJECT.minSampledPixels,
            minLeafColourPercent = 40f,
            minValueMean = 0.12f,
            maxValueMean = 0.96f,
            blownOutMaxSaturation = 0.10f,
            // Existing healthy/warning were 18/12/8/8 — too tight for phone cameras.
            healthyDiscoloredMax = 26f,
            healthyYellowMax = 20f,
            healthyBrownMax = 14f,
            healthyPaleMax = 16f,
            // Existing warning/severe discoloration 18 / 40; split well above lighting noise.
            unhealthyDiscoloredMin = 32f,
            unhealthyYellowMin = 24f,
            unhealthyBrownMin = 16f,
            unhealthyPaleMin = 20f
        )
    }
}
