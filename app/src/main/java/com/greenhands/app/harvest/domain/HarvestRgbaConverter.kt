package com.greenhands.app.harvest.domain

import java.nio.ByteBuffer

/**
 * Camera RGBA_8888 -> upright ARGB. Same nearest-neighbor scale and rotation
 * as the previous per-pixel ImageProxy path, with bulk byte copies and one
 * output buffer.
 *
 * Does not change crop mapping: output is still analysis-frame pixels after
 * [HarvestArgbRotate] geometry.
 */
object HarvestRgbaConverter {
    fun downsampleSize(srcWidth: Int, srcHeight: Int, maxSide: Int): Pair<Int, Int> {
        val srcW = srcWidth.coerceAtLeast(1)
        val srcH = srcHeight.coerceAtLeast(1)
        val side = maxSide.coerceAtLeast(1)
        val scale = maxOf(srcW, srcH).toFloat() / side.toFloat()
        val dstW = (srcW / scale).toInt().coerceAtLeast(1)
        val dstH = (srcH / scale).toInt().coerceAtLeast(1)
        return dstW to dstH
    }

    fun copyToBytes(buffer: ByteBuffer, reuse: ByteArray?): ByteArray {
        val n = buffer.capacity().coerceAtLeast(0)
        val out = if (reuse != null && reuse.size >= n) reuse else ByteArray(n)
        if (n == 0) return out
        val dup = buffer.duplicate()
        dup.clear()
        dup.get(out, 0, n)
        return out
    }

    fun decode(
        rgba: ByteArray,
        srcWidth: Int,
        srcHeight: Int,
        pixelStride: Int,
        rowStride: Int,
        maxSide: Int,
        rotationDegrees: Int,
        reusePixels: IntArray? = null
    ): HarvestArgbFrame {
        val srcW = srcWidth.coerceAtLeast(1)
        val srcH = srcHeight.coerceAtLeast(1)
        val (dstW, dstH) = downsampleSize(srcW, srcH, maxSide)
        val degrees = ((rotationDegrees % 360) + 360) % 360
        val (outW, outH) = when (degrees) {
            90, 270 -> dstH to dstW
            else -> dstW to dstH
        }
        val needed = outW * outH
        val out = if (reusePixels != null && reusePixels.size == needed) {
            reusePixels
        } else {
            IntArray(needed)
        }
        val stride = pixelStride.coerceAtLeast(1)
        val row = rowStride.coerceAtLeast(srcW * stride)
        fun sample(x: Int, y: Int): Int {
            val srcX = (x * srcW) / dstW
            val srcY = (y * srcH) / dstH
            return readRgba8888(rgba, srcX, srcY, stride, row)
        }
        when (degrees) {
            90 -> {
                for (y in 0 until dstH) {
                    for (x in 0 until dstW) {
                        out[x * outW + (outW - 1 - y)] = sample(x, y)
                    }
                }
            }
            180 -> {
                val last = needed - 1
                var i = 0
                for (y in 0 until dstH) {
                    for (x in 0 until dstW) {
                        out[last - i] = sample(x, y)
                        i++
                    }
                }
            }
            270 -> {
                for (y in 0 until dstH) {
                    for (x in 0 until dstW) {
                        out[(dstW - 1 - x) * outW + y] = sample(x, y)
                    }
                }
            }
            else -> {
                var i = 0
                for (y in 0 until dstH) {
                    for (x in 0 until dstW) {
                        out[i++] = sample(x, y)
                    }
                }
            }
        }
        return HarvestArgbFrame(out, outW, outH, degrees)
    }

    fun readRgba8888(
        rgba: ByteArray,
        x: Int,
        y: Int,
        pixelStride: Int,
        rowStride: Int
    ): Int {
        val index = y * rowStride + x * pixelStride
        if (index < 0 || index + 2 >= rgba.size) return 0
        val r = rgba[index].toInt() and 0xFF
        val g = rgba[index + 1].toInt() and 0xFF
        val b = rgba[index + 2].toInt() and 0xFF
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
}
