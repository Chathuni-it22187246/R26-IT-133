package com.greenhands.app.harvest.domain

import com.greenhands.app.harvest.model.FruitColorMeasurement
import com.greenhands.app.harvest.model.LeafColorMeasurement

/**
 * Counts HSV color-wheel bins on a downsampled ARGB frame.
 * Does not produce harvest decisions or disease diagnoses.
 */
class HsvAnalyzer(
    private val centerCropFraction: Float = 0.55f
) {
    fun analyzeFruit(frame: HarvestArgbFrame): FruitColorMeasurement {
        val stats = accumulate(frame)
        val n = stats.count.coerceAtLeast(1)
        return FruitColorMeasurement(
            sampledPixelCount = stats.count,
            hueMean = stats.hueSum / n,
            saturationMean = stats.satSum / n,
            valueMean = stats.valSum / n,
            greenPercent = percent(stats.green, stats.count),
            yellowPercent = percent(stats.yellow, stats.count),
            redPercent = percent(stats.red, stats.count),
            brownDarkPercent = percent(stats.brownDark, stats.count),
            otherPercent = percent(stats.other, stats.count),
            greenSampledCount = stats.green,
            greenValueMean = if (stats.green > 0) stats.greenValSum / stats.green else null,
            greenSaturationMean = if (stats.green > 0) stats.greenSatSum / stats.green else null
        )
    }

    fun analyzeLeaf(frame: HarvestArgbFrame): LeafColorMeasurement {
        val stats = accumulate(frame)
        val n = stats.count.coerceAtLeast(1)
        val discolored = stats.yellow + stats.brownDark + stats.whitePale
        return LeafColorMeasurement(
            sampledPixelCount = stats.count,
            hueMean = stats.hueSum / n,
            saturationMean = stats.satSum / n,
            valueMean = stats.valSum / n,
            greenPercent = percent(stats.green, stats.count),
            yellowPercent = percent(stats.yellow, stats.count),
            brownDarkPercent = percent(stats.brownDark, stats.count),
            whitePalePercent = percent(stats.whitePale, stats.count),
            discoloredPercent = percent(discolored, stats.count),
            otherPercent = percent(stats.other, stats.count)
        )
    }

    private fun accumulate(frame: HarvestArgbFrame): BinStats {
        val stats = BinStats()
        val insetX = ((1f - centerCropFraction) / 2f * frame.width).toInt()
        val insetY = ((1f - centerCropFraction) / 2f * frame.height).toInt()
        val x0 = insetX
        val y0 = insetY
        val x1 = (frame.width - insetX).coerceAtLeast(x0 + 1)
        val y1 = (frame.height - insetY).coerceAtLeast(y0 + 1)
        for (y in y0 until y1) {
            val row = y * frame.width
            for (x in x0 until x1) {
                val hsv = HsvConverter.fromArgb(frame.argb[row + x])
                if (hsv.value < 0.06f) continue
                stats.count++
                stats.hueSum += hsv.hue
                stats.satSum += hsv.saturation
                stats.valSum += hsv.value
                when (HsvColorBins.classify(hsv)) {
                    HsvColorBin.GREEN -> {
                        stats.green++
                        stats.greenValSum += hsv.value
                        stats.greenSatSum += hsv.saturation
                    }
                    HsvColorBin.YELLOW -> stats.yellow++
                    HsvColorBin.RED -> stats.red++
                    HsvColorBin.BROWN_DARK -> stats.brownDark++
                    HsvColorBin.WHITE_PALE -> stats.whitePale++
                    HsvColorBin.OTHER -> stats.other++
                }
            }
        }
        return stats
    }

    private fun percent(part: Int, total: Int): Float {
        if (total <= 0) return 0f
        return (part * 100f) / total
    }

    private class BinStats {
        var count: Int = 0
        var hueSum: Float = 0f
        var satSum: Float = 0f
        var valSum: Float = 0f
        var green: Int = 0
        var greenValSum: Float = 0f
        var greenSatSum: Float = 0f
        var yellow: Int = 0
        var red: Int = 0
        var brownDark: Int = 0
        var whitePale: Int = 0
        var other: Int = 0
    }
}
