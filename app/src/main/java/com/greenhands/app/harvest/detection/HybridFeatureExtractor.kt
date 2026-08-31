package com.greenhands.app.harvest.detection

import com.greenhands.app.harvest.domain.HarvestArgbFrame
import kotlin.math.abs
import kotlin.math.max

/**
 * Downscales a frame and builds fruit/leaf color masks plus the largest region.
 * Lightweight Kotlin path for Galaxy A12 — no OpenCV.
 */
class HybridFeatureExtractor(
    private val calibration: HybridTargetCalibration = HybridTargetCalibration.PROJECT
) {
    fun extract(frame: HarvestArgbFrame, expected: ScanTargetType): HybridTargetFeatures {
        val scaled = downscale(frame)
        val width = scaled.width
        val height = scaled.height
        val total = (width * height).coerceAtLeast(1)
        val mask = BooleanArray(total)
        val familyCounts = IntArray(HybridColorFamily.entries.size)
        val maskFamilyCounts = IntArray(HybridColorFamily.entries.size)
        var tomatoPrimary = 0
        var vegetation = 0
        var nonPale = 0
        var pale = 0
        for (i in 0 until total) {
            val family = HybridColorClassifier.familyFromArgb(scaled.argb[i], calibration)
            familyCounts[family.ordinal]++
            val fruitMask = HybridColorClassifier.isFruitMask(family)
            val leafVegetation = HybridColorClassifier.isLeafVegetation(family)
            val leafConnectedMask = HybridColorClassifier.isNonPaleVegetation(family)
            if (HybridColorClassifier.isPrimaryFruit(family)) tomatoPrimary++
            if (leafVegetation) vegetation++
            if (leafConnectedMask) nonPale++
            if (family == HybridColorFamily.PALE) pale++
            // Leaf connected region excludes pale paper/walls so a leaf on a
            // notebook is not merged into one giant rectangle. Fruit mask is unchanged.
            val use = if (expected == ScanTargetType.TOMATO_FRUIT) fruitMask else leafConnectedMask
            mask[i] = use
            if (use) maskFamilyCounts[family.ordinal]++
        }
        val dominant = dominantFamilyFromCounts(maskFamilyCounts).takeUnless {
            maskFamilyCounts[it.ordinal] == 0
        } ?: dominantFamilyFromCounts(familyCounts)
        val tomatoRatio = tomatoPrimary.toFloat() / total
        val vegetationRatio = vegetation.toFloat() / total
        val nonPaleRatio = nonPale.toFloat() / total
        val paleFraction = if (vegetation > 0) pale.toFloat() / vegetation.toFloat() else 0f
        val region = ConnectedRegionExtractor.largest(
            mask = mask,
            width = width,
            height = height,
            minPixels = calibration.minComponentPixels
        )
        if (region == null) {
            return HybridTargetFeatures.empty(width, height).copy(
                tomatoColorPixelRatio = tomatoRatio,
                vegetationPixelRatio = vegetationRatio,
                nonPaleVegetationRatio = nonPaleRatio,
                paleFractionOfMask = paleFraction,
                backgroundRatio = 1f - if (expected == ScanTargetType.TOMATO_FRUIT) {
                    tomatoRatio
                } else {
                    vegetationRatio
                },
                dominantColorFamily = dominant
            )
        }
        val evidenceRatio = if (expected == ScanTargetType.TOMATO_FRUIT) {
            tomatoRatio
        } else {
            vegetationRatio
        }
        return HybridTargetFeatures(
            tomatoColorPixelRatio = tomatoRatio,
            vegetationPixelRatio = vegetationRatio,
            nonPaleVegetationRatio = nonPaleRatio,
            paleFractionOfMask = paleFraction,
            backgroundRatio = 1f - evidenceRatio,
            dominantColorFamily = dominant.takeUnless { it == HybridColorFamily.OTHER }
                ?: dominantFamilyFromCounts(familyCounts),
            hasCandidate = true,
            areaRatio = region.pixelArea.toFloat() / total.toFloat(),
            boundingBox = region.normalizedBox(width, height),
            widthHeightRatio = region.widthHeightRatio,
            circularity = region.circularity,
            solidity = region.solidity,
            extent = region.extent,
            centerX = region.centroidX / width.toFloat(),
            centerY = region.centroidY / height.toFloat(),
            candidatePixelCount = region.pixelArea,
            analysisWidth = width,
            analysisHeight = height
        )
    }

    private fun downscale(frame: HarvestArgbFrame): HarvestArgbFrame {
        val longSide = max(frame.width, frame.height)
        if (longSide <= calibration.analysisMaxSide) return frame
        val scale = longSide.toFloat() / calibration.analysisMaxSide.toFloat()
        val outW = (frame.width / scale).toInt().coerceAtLeast(1)
        val outH = (frame.height / scale).toInt().coerceAtLeast(1)
        return ArgbFrameScaler.scale(frame, outW, outH)
    }

    private fun dominantFamilyFromCounts(counts: IntArray): HybridColorFamily {
        var best = HybridColorFamily.OTHER
        var bestCount = -1
        HybridColorFamily.entries.forEach { family ->
            if (family == HybridColorFamily.OTHER) return@forEach
            val n = counts[family.ordinal]
            if (n > bestCount) {
                bestCount = n
                best = family
            }
        }
        return best
    }
}

internal fun isReasonablyCentered(x: Float, y: Float, tolerance: Float): Boolean =
    abs(x - 0.5f) <= tolerance && abs(y - 0.5f) <= tolerance
