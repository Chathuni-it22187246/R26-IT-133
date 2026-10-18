package com.greenhands.app.harvest.detection

import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min

/**
 * Largest 8-connected region on a binary mask. Kotlin flood-fill — no OpenCV.
 */
data class ConnectedRegion(
    val pixelArea: Int,
    val minX: Int,
    val minY: Int,
    val maxX: Int,
    val maxY: Int,
    val sumX: Long,
    val sumY: Long,
    val perimeter: Int
) {
    val width: Int get() = maxX - minX + 1
    val height: Int get() = maxY - minY + 1
    val bboxArea: Int get() = (width * height).coerceAtLeast(1)
    val extent: Float get() = pixelArea.toFloat() / bboxArea.toFloat()
    val solidity: Float get() = extent
    val widthHeightRatio: Float get() = width.toFloat() / height.coerceAtLeast(1).toFloat()
    val circularity: Float
        get() {
            if (perimeter <= 0) return 0f
            return ((4.0 * PI * pixelArea) / (perimeter.toDouble() * perimeter)).toFloat()
        }
    val centroidX: Float get() = sumX.toFloat() / pixelArea.coerceAtLeast(1).toFloat()
    val centroidY: Float get() = sumY.toFloat() / pixelArea.coerceAtLeast(1).toFloat()

    fun normalizedBox(frameWidth: Int, frameHeight: Int): NormalizedRect {
        val w = frameWidth.coerceAtLeast(1).toFloat()
        val h = frameHeight.coerceAtLeast(1).toFloat()
        return NormalizedRect(
            left = minX / w,
            top = minY / h,
            right = (maxX + 1) / w,
            bottom = (maxY + 1) / h
        ).clamp()
    }
}

object ConnectedRegionExtractor {
    fun largest(
        mask: BooleanArray,
        width: Int,
        height: Int,
        minPixels: Int
    ): ConnectedRegion? {
        if (width <= 0 || height <= 0 || mask.size < width * height) return null
        val seen = BooleanArray(width * height)
        val stack = IntArray(width * height)
        var best: ConnectedRegion? = null
        for (start in 0 until width * height) {
            if (!mask[start] || seen[start]) continue
            var sp = 0
            stack[sp++] = start
            seen[start] = true
            var area = 0
            var minX = width
            var minY = height
            var maxX = 0
            var maxY = 0
            var sumX = 0L
            var sumY = 0L
            var perimeter = 0
            while (sp > 0) {
                val p = stack[--sp]
                val x = p % width
                val y = p / width
                area++
                sumX += x
                sumY += y
                minX = min(minX, x)
                minY = min(minY, y)
                maxX = max(maxX, x)
                maxY = max(maxY, y)
                for (dx in intArrayOf(-1, 0, 1)) {
                    for (dy in intArrayOf(-1, 0, 1)) {
                        if (dx == 0 && dy == 0) continue
                        val nx = x + dx
                        val ny = y + dy
                        val inside = nx in 0 until width && ny in 0 until height
                        val nMask = inside && mask[ny * width + nx]
                        if (!nMask && (dx == 0 || dy == 0)) {
                            perimeter++
                        }
                        if (nMask && !seen[ny * width + nx]) {
                            seen[ny * width + nx] = true
                            stack[sp++] = ny * width + nx
                        }
                    }
                }
            }
            if (area < minPixels) continue
            if (best == null || area > best.pixelArea) {
                best = ConnectedRegion(
                    pixelArea = area,
                    minX = minX,
                    minY = minY,
                    maxX = maxX,
                    maxY = maxY,
                    sumX = sumX,
                    sumY = sumY,
                    perimeter = perimeter
                )
            }
        }
        return best
    }

    /**
     * Same as [largest], but also returns a mask of only the winning component.
     */
    fun largestComponent(
        mask: BooleanArray,
        width: Int,
        height: Int,
        minPixels: Int
    ): Pair<ConnectedRegion, BooleanArray>? {
        if (width <= 0 || height <= 0 || mask.size < width * height) return null
        val seen = BooleanArray(width * height)
        val stack = IntArray(width * height)
        val currentPixels = IntArray(width * height)
        var best: ConnectedRegion? = null
        var bestPixels: IntArray? = null
        var bestCount = 0
        for (start in 0 until width * height) {
            if (!mask[start] || seen[start]) continue
            var sp = 0
            stack[sp++] = start
            seen[start] = true
            var area = 0
            var minX = width
            var minY = height
            var maxX = 0
            var maxY = 0
            var sumX = 0L
            var sumY = 0L
            var perimeter = 0
            while (sp > 0) {
                val p = stack[--sp]
                currentPixels[area] = p
                val x = p % width
                val y = p / width
                area++
                sumX += x
                sumY += y
                minX = min(minX, x)
                minY = min(minY, y)
                maxX = max(maxX, x)
                maxY = max(maxY, y)
                for (dx in intArrayOf(-1, 0, 1)) {
                    for (dy in intArrayOf(-1, 0, 1)) {
                        if (dx == 0 && dy == 0) continue
                        val nx = x + dx
                        val ny = y + dy
                        val inside = nx in 0 until width && ny in 0 until height
                        val nMask = inside && mask[ny * width + nx]
                        if (!nMask && (dx == 0 || dy == 0)) {
                            perimeter++
                        }
                        if (nMask && !seen[ny * width + nx]) {
                            seen[ny * width + nx] = true
                            stack[sp++] = ny * width + nx
                        }
                    }
                }
            }
            if (area < minPixels) continue
            if (best == null || area > best.pixelArea) {
                best = ConnectedRegion(
                    pixelArea = area,
                    minX = minX,
                    minY = minY,
                    maxX = maxX,
                    maxY = maxY,
                    sumX = sumX,
                    sumY = sumY,
                    perimeter = perimeter
                )
                bestPixels = currentPixels.copyOf(area)
                bestCount = area
            }
        }
        val region = best ?: return null
        val component = BooleanArray(width * height)
        val pixels = bestPixels ?: return region to component
        for (i in 0 until bestCount) component[pixels[i]] = true
        return region to component
    }
}
