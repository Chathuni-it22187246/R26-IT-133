package com.greenhands.app.harvest.detection

/**
 * PROJECT CALIBRATION PARAMETERS — FIELD VALIDATION REQUIRED
 *
 * Initial practical defaults for hybrid visual target validation on this
 * research build (Galaxy A12 class devices). They are not DOA / HORDI facts
 * and are not [com.greenhands.app.harvest.domain.HsvAnalyzer] measurement bins.
 *
 * Hybrid validation is not true object recognition and does not guarantee
 * identification of tomato fruit or leaf.
 */
data class HybridTargetCalibration(
    val analysisMaxSide: Int,
    val minComponentPixels: Int,
    val fruitMinPrimaryColorRatio: Float,
    val fruitMinAreaRatio: Float,
    val fruitMaxAreaRatio: Float,
    val fruitMaxAspect: Float,
    val fruitMinExtent: Float,
    val fruitMaxExtent: Float,
    val fruitMinCircularity: Float,
    val fruitCenterTolerance: Float,
    val fruitGreenHueStart: Float,
    val fruitGreenHueEnd: Float,
    val fruitYellowHueStart: Float,
    val fruitYellowHueEnd: Float,
    val fruitOrangeHueStart: Float,
    val fruitOrangeHueEnd: Float,
    val fruitRedHueWrap: Float,
    val fruitMinSaturation: Float,
    val fruitMinValue: Float,
    val fruitOrangeMinValue: Float,
    val fruitDarkRedMaxValue: Float,
    val fruitDarkRedMinValue: Float,
    val leafMinVegetationRatio: Float,
    val leafMinNonPaleRatio: Float,
    val leafMaxPaleFractionOfMask: Float,
    val leafMinAreaRatio: Float,
    val leafMaxAreaRatio: Float,
    val leafMinElongation: Float,
    val leafFruitLikeAspectMin: Float,
    val leafFruitLikeAspectMax: Float,
    val leafFruitLikeExtentMin: Float,
    val leafFruitLikeExtentMax: Float,
    val leafFruitLikeMinCircularity: Float,
    val leafFruitLikeRejectMinCircularity: Float,
    val leafFruitLikeRequiredIndicators: Int,
    val leafMinGeometryScore: Int,
    val leafMaxRectangleExtent: Float,
    val leafRectangleMinArea: Float,
    val leafMaxSolidFillExtent: Float,
    val leafIrregularExtentMax: Float,
    val leafCenterTolerance: Float,
    val leafMinSaturation: Float,
    val leafMinValue: Float,
    val leafBrownMaxValue: Float,
    val leafPaleMaxSaturation: Float,
    val leafPaleMinValue: Float,
    val visualScoreWhenValid: Float
) {
    companion object {
        // PROJECT CALIBRATION PARAMETERS — FIELD VALIDATION REQUIRED
        const val ANALYSIS_MAX_SIDE = 96
        const val MIN_COMPONENT_PIXELS = 8

        const val FRUIT_MIN_PRIMARY_COLOR_RATIO = 0.012f
        const val FRUIT_MIN_AREA_RATIO = 0.08f
        const val FRUIT_MAX_AREA_RATIO = 0.72f
        const val FRUIT_MAX_ASPECT = 1.48f
        const val FRUIT_MIN_EXTENT = 0.54f
        const val FRUIT_MAX_EXTENT = 0.90f
        const val FRUIT_MIN_CIRCULARITY = 0.28f
        const val FRUIT_CENTER_TOLERANCE = 0.22f

        const val FRUIT_GREEN_HUE_START = 70f
        const val FRUIT_GREEN_HUE_END = 165f
        const val FRUIT_YELLOW_HUE_START = 38f
        const val FRUIT_YELLOW_HUE_END = 70f
        const val FRUIT_ORANGE_HUE_START = 14f
        const val FRUIT_ORANGE_HUE_END = 38f
        const val FRUIT_RED_HUE_WRAP = 14f
        const val FRUIT_MIN_SATURATION = 0.28f
        const val FRUIT_MIN_VALUE = 0.18f
        const val FRUIT_ORANGE_MIN_VALUE = 0.32f
        const val FRUIT_DARK_RED_MAX_VALUE = 0.42f
        const val FRUIT_DARK_RED_MIN_VALUE = 0.12f

        const val LEAF_MIN_VEGETATION_RATIO = 0.012f
        const val LEAF_MIN_NON_PALE_RATIO = 0.028f
        const val LEAF_MAX_PALE_FRACTION_OF_MASK = 0.92f
        const val LEAF_MIN_AREA_RATIO = 0.055f
        const val LEAF_MAX_AREA_RATIO = 0.82f
        // Supporting evidence only — not a hard reject when below this.
        const val LEAF_MIN_ELONGATION = 1.12f
        const val LEAF_FRUIT_LIKE_ASPECT_MIN = 0.90f
        const val LEAF_FRUIT_LIKE_ASPECT_MAX = 1.10f
        const val LEAF_FRUIT_LIKE_EXTENT_MIN = 0.72f
        const val LEAF_FRUIT_LIKE_EXTENT_MAX = 0.86f
        const val LEAF_FRUIT_LIKE_MIN_CIRCULARITY = 0.48f
        const val LEAF_FRUIT_LIKE_REJECT_MIN_CIRCULARITY = 0.56f
        const val LEAF_FRUIT_LIKE_REQUIRED_INDICATORS = 3
        const val LEAF_MIN_GEOMETRY_SCORE = 1
        const val LEAF_MAX_RECTANGLE_EXTENT = 0.88f
        const val LEAF_RECTANGLE_MIN_AREA = 0.42f
        const val LEAF_MAX_SOLID_FILL_EXTENT = 0.90f
        const val LEAF_IRREGULAR_EXTENT_MAX = 0.78f
        const val LEAF_CENTER_TOLERANCE = 0.22f
        const val LEAF_MIN_SATURATION = 0.22f
        const val LEAF_MIN_VALUE = 0.14f
        const val LEAF_BROWN_MAX_VALUE = 0.48f
        const val LEAF_PALE_MAX_SATURATION = 0.18f
        const val LEAF_PALE_MIN_VALUE = 0.70f

        const val VISUAL_SCORE_WHEN_VALID = 0.90f

        val PROJECT = HybridTargetCalibration(
            analysisMaxSide = ANALYSIS_MAX_SIDE,
            minComponentPixels = MIN_COMPONENT_PIXELS,
            fruitMinPrimaryColorRatio = FRUIT_MIN_PRIMARY_COLOR_RATIO,
            fruitMinAreaRatio = FRUIT_MIN_AREA_RATIO,
            fruitMaxAreaRatio = FRUIT_MAX_AREA_RATIO,
            fruitMaxAspect = FRUIT_MAX_ASPECT,
            fruitMinExtent = FRUIT_MIN_EXTENT,
            fruitMaxExtent = FRUIT_MAX_EXTENT,
            fruitMinCircularity = FRUIT_MIN_CIRCULARITY,
            fruitCenterTolerance = FRUIT_CENTER_TOLERANCE,
            fruitGreenHueStart = FRUIT_GREEN_HUE_START,
            fruitGreenHueEnd = FRUIT_GREEN_HUE_END,
            fruitYellowHueStart = FRUIT_YELLOW_HUE_START,
            fruitYellowHueEnd = FRUIT_YELLOW_HUE_END,
            fruitOrangeHueStart = FRUIT_ORANGE_HUE_START,
            fruitOrangeHueEnd = FRUIT_ORANGE_HUE_END,
            fruitRedHueWrap = FRUIT_RED_HUE_WRAP,
            fruitMinSaturation = FRUIT_MIN_SATURATION,
            fruitMinValue = FRUIT_MIN_VALUE,
            fruitOrangeMinValue = FRUIT_ORANGE_MIN_VALUE,
            fruitDarkRedMaxValue = FRUIT_DARK_RED_MAX_VALUE,
            fruitDarkRedMinValue = FRUIT_DARK_RED_MIN_VALUE,
            leafMinVegetationRatio = LEAF_MIN_VEGETATION_RATIO,
            leafMinNonPaleRatio = LEAF_MIN_NON_PALE_RATIO,
            leafMaxPaleFractionOfMask = LEAF_MAX_PALE_FRACTION_OF_MASK,
            leafMinAreaRatio = LEAF_MIN_AREA_RATIO,
            leafMaxAreaRatio = LEAF_MAX_AREA_RATIO,
            leafMinElongation = LEAF_MIN_ELONGATION,
            leafFruitLikeAspectMin = LEAF_FRUIT_LIKE_ASPECT_MIN,
            leafFruitLikeAspectMax = LEAF_FRUIT_LIKE_ASPECT_MAX,
            leafFruitLikeExtentMin = LEAF_FRUIT_LIKE_EXTENT_MIN,
            leafFruitLikeExtentMax = LEAF_FRUIT_LIKE_EXTENT_MAX,
            leafFruitLikeMinCircularity = LEAF_FRUIT_LIKE_MIN_CIRCULARITY,
            leafFruitLikeRejectMinCircularity = LEAF_FRUIT_LIKE_REJECT_MIN_CIRCULARITY,
            leafFruitLikeRequiredIndicators = LEAF_FRUIT_LIKE_REQUIRED_INDICATORS,
            leafMinGeometryScore = LEAF_MIN_GEOMETRY_SCORE,
            leafMaxRectangleExtent = LEAF_MAX_RECTANGLE_EXTENT,
            leafRectangleMinArea = LEAF_RECTANGLE_MIN_AREA,
            leafMaxSolidFillExtent = LEAF_MAX_SOLID_FILL_EXTENT,
            leafIrregularExtentMax = LEAF_IRREGULAR_EXTENT_MAX,
            leafCenterTolerance = LEAF_CENTER_TOLERANCE,
            leafMinSaturation = LEAF_MIN_SATURATION,
            leafMinValue = LEAF_MIN_VALUE,
            leafBrownMaxValue = LEAF_BROWN_MAX_VALUE,
            leafPaleMaxSaturation = LEAF_PALE_MAX_SATURATION,
            leafPaleMinValue = LEAF_PALE_MIN_VALUE,
            visualScoreWhenValid = VISUAL_SCORE_WHEN_VALID
        )
    }
}
