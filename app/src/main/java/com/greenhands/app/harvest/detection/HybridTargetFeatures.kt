package com.greenhands.app.harvest.detection

/**
 * Features derived from a downscaled camera region for hybrid validation.
 * These are visual heuristics, not CNN embeddings or object-class scores.
 */
data class HybridTargetFeatures(
    val tomatoColorPixelRatio: Float,
    val vegetationPixelRatio: Float,
    val nonPaleVegetationRatio: Float,
    val paleFractionOfMask: Float,
    val backgroundRatio: Float,
    val dominantColorFamily: HybridColorFamily,
    val hasCandidate: Boolean,
    val areaRatio: Float,
    val boundingBox: NormalizedRect?,
    val widthHeightRatio: Float,
    val circularity: Float,
    val solidity: Float,
    val extent: Float,
    val centerX: Float,
    val centerY: Float,
    val candidatePixelCount: Int,
    val analysisWidth: Int,
    val analysisHeight: Int
) {
    val aspectElongation: Float
        get() {
            val ratio = widthHeightRatio
            if (ratio <= 0f) return 1f
            return if (ratio >= 1f) ratio else 1f / ratio
        }

    companion object {
        fun empty(analysisWidth: Int, analysisHeight: Int): HybridTargetFeatures =
            HybridTargetFeatures(
                tomatoColorPixelRatio = 0f,
                vegetationPixelRatio = 0f,
                nonPaleVegetationRatio = 0f,
                paleFractionOfMask = 0f,
                backgroundRatio = 1f,
                dominantColorFamily = HybridColorFamily.OTHER,
                hasCandidate = false,
                areaRatio = 0f,
                boundingBox = null,
                widthHeightRatio = 0f,
                circularity = 0f,
                solidity = 0f,
                extent = 0f,
                centerX = 0.5f,
                centerY = 0.5f,
                candidatePixelCount = 0,
                analysisWidth = analysisWidth,
                analysisHeight = analysisHeight
            )
    }
}
