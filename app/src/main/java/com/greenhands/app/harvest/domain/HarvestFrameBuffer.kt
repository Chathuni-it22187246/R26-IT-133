package com.greenhands.app.harvest.domain

import androidx.camera.core.ImageProxy
import com.greenhands.app.harvest.detection.TargetDetectionCalibration
import java.nio.ByteBuffer

data class HarvestArgbFrame(
    val argb: IntArray,
    val width: Int,
    val height: Int
)

/**
 * Holds the latest downsampled camera frame for capture-time HSV analysis.
 * Callers must copy() — the analyzer may replace the buffer at any time.
 */
class HarvestFrameBuffer {
    private val lock = Any()
    private var argb: IntArray? = null
    private var width: Int = 0
    private var height: Int = 0

    fun updateFromRgbaImage(image: ImageProxy, maxSide: Int = MAX_SIDE) {
        val srcW = image.width
        val srcH = image.height
        if (srcW <= 0 || srcH <= 0) return
        val plane = image.planes.firstOrNull() ?: return
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val scale = maxOf(srcW, srcH).toFloat() / maxSide.toFloat()
        val dstW = (srcW / scale).toInt().coerceAtLeast(1)
        val dstH = (srcH / scale).toInt().coerceAtLeast(1)
        val out = IntArray(dstW * dstH)
        var i = 0
        for (y in 0 until dstH) {
            val srcY = (y * srcH) / dstH
            for (x in 0 until dstW) {
                val srcX = (x * srcW) / dstW
                out[i++] = readRgba8888(buffer, srcX, srcY, pixelStride, rowStride)
            }
        }
        synchronized(lock) {
            argb = out
            width = dstW
            height = dstH
        }
    }

    fun update(frame: HarvestArgbFrame) {
        synchronized(lock) {
            argb = frame.argb
            width = frame.width
            height = frame.height
        }
    }

    fun copy(): HarvestArgbFrame? = synchronized(lock) {
        val pixels = argb ?: return null
        if (width <= 0 || height <= 0) return null
        HarvestArgbFrame(pixels.copyOf(), width, height)
    }

    companion object {
        const val MAX_SIDE = 160
        const val ANALYSIS_MAX_SIDE = TargetDetectionCalibration.ANALYSIS_MAX_SIDE
    }
}

private fun readRgba8888(
    buffer: ByteBuffer,
    x: Int,
    y: Int,
    pixelStride: Int,
    rowStride: Int
): Int {
    val index = y * rowStride + x * pixelStride
    val r = buffer.get(index).toInt() and 0xFF
    val g = buffer.get(index + 1).toInt() and 0xFF
    val b = buffer.get(index + 2).toInt() and 0xFF
    return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}
