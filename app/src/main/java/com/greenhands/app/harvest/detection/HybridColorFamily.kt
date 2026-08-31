package com.greenhands.app.harvest.detection

import com.greenhands.app.harvest.domain.HsvConverter
import com.greenhands.app.harvest.domain.HsvPixel

/**
 * Color families used only by hybrid target validation.
 * Separate from [com.greenhands.app.harvest.domain.HsvColorBins] measurement bins.
 */
enum class HybridColorFamily {
    GREEN,
    YELLOW,
    ORANGE,
    RED,
    DARK_RED_BROWN,
    BROWN,
    PALE,
    OTHER
}

object HybridColorClassifier {
    fun family(
        pixel: HsvPixel,
        calibration: HybridTargetCalibration = HybridTargetCalibration.PROJECT
    ): HybridColorFamily {
        val h = pixel.hue
        val s = pixel.saturation
        val v = pixel.value
        if (s < calibration.leafPaleMaxSaturation) {
            return if (v >= calibration.leafPaleMinValue) HybridColorFamily.PALE
            else HybridColorFamily.OTHER
        }
        if (v < calibration.fruitDarkRedMinValue) return HybridColorFamily.OTHER

        val green = h >= calibration.fruitGreenHueStart && h < calibration.fruitGreenHueEnd &&
            s >= calibration.fruitMinSaturation && v >= calibration.fruitMinValue
        if (green) return HybridColorFamily.GREEN

        val yellow = h >= calibration.fruitYellowHueStart && h < calibration.fruitYellowHueEnd &&
            s >= calibration.fruitMinSaturation && v >= calibration.fruitMinValue
        if (yellow) return HybridColorFamily.YELLOW

        val orange = h >= calibration.fruitOrangeHueStart && h < calibration.fruitOrangeHueEnd &&
            s >= calibration.fruitMinSaturation && v >= calibration.fruitOrangeMinValue
        if (orange) return HybridColorFamily.ORANGE

        val red = (h < calibration.fruitRedHueWrap || h >= 360f - calibration.fruitRedHueWrap) &&
            s >= calibration.fruitMinSaturation && v >= calibration.fruitMinValue
        if (red) return HybridColorFamily.RED

        val darkRedBrown =
            ((h < 28f || h >= 340f) &&
                s >= 0.22f &&
                v >= calibration.fruitDarkRedMinValue &&
                v < calibration.fruitDarkRedMaxValue) ||
                (h >= calibration.fruitOrangeHueStart &&
                    h < calibration.fruitYellowHueEnd &&
                    s >= calibration.leafMinSaturation &&
                    v >= calibration.leafMinValue &&
                    v < calibration.fruitOrangeMinValue)
        if (darkRedBrown) return HybridColorFamily.DARK_RED_BROWN

        val brown = h >= 12f && h < 55f &&
            s >= calibration.leafMinSaturation &&
            v >= calibration.leafMinValue &&
            v <= calibration.leafBrownMaxValue
        if (brown) return HybridColorFamily.BROWN

        return HybridColorFamily.OTHER
    }

    fun familyFromArgb(
        argb: Int,
        calibration: HybridTargetCalibration = HybridTargetCalibration.PROJECT
    ): HybridColorFamily = family(HsvConverter.fromArgb(argb), calibration)

    fun isPrimaryFruit(family: HybridColorFamily): Boolean = when (family) {
        HybridColorFamily.GREEN,
        HybridColorFamily.YELLOW,
        HybridColorFamily.ORANGE,
        HybridColorFamily.RED -> true
        else -> false
    }

    fun isFruitMask(family: HybridColorFamily): Boolean =
        isPrimaryFruit(family) || family == HybridColorFamily.DARK_RED_BROWN

    fun isLeafVegetation(family: HybridColorFamily): Boolean = when (family) {
        HybridColorFamily.GREEN,
        HybridColorFamily.YELLOW,
        HybridColorFamily.ORANGE,
        HybridColorFamily.BROWN,
        HybridColorFamily.DARK_RED_BROWN,
        HybridColorFamily.PALE -> true
        else -> false
    }

    fun isNonPaleVegetation(family: HybridColorFamily): Boolean =
        isLeafVegetation(family) && family != HybridColorFamily.PALE
}
