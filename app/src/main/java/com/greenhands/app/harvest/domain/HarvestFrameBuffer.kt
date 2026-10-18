package com.greenhands.app.harvest.domain

import androidx.camera.core.ImageProxy
import com.greenhands.app.harvest.detection.TargetDetectionCalibration
import java.nio.ByteBuffer

data class HarvestArgbFrame(
    val argb: IntArray,
    val width: Int,
    val height: Int,
    val rotationDegrees: Int = 0
)

/**
 * Rotates a downsampled ARGB buffer using ImageProxy.rotationDegrees so
 * analysis matches an upright preview. No-op when rotation is 0.
 *
 * When [dest] is a correctly sized distinct array, rotation writes into it
 * instead of allocating. In-place rotation of 90/180/270 is not used.
 */
object HarvestArgbRotate {
    fun apply(
        argb: IntArray,
        width: Int,
        height: Int,
        rotationDegrees: Int,
        dest: IntArray? = null
    ): HarvestArgbFrame {
        val degrees = ((rotationDegrees % 360) + 360) % 360
        return when (degrees) {
            90 -> rotate90(argb, width, height, degrees, dest)
            180 -> rotate180(argb, width, height, degrees, dest)
            270 -> rotate270(argb, width, height, degrees, dest)
            else -> {
                if (dest != null && dest.size == argb.size && dest !== argb) {
                    System.arraycopy(argb, 0, dest, 0, argb.size)
                    HarvestArgbFrame(dest, width, height, degrees)
                } else {
                    HarvestArgbFrame(argb, width, height, degrees)
                }
            }
        }
    }

    private fun rotate90(
        src: IntArray,
        width: Int,
        height: Int,
        rotationDegrees: Int,
        dest: IntArray?
    ): HarvestArgbFrame {
        val outW = height
        val outH = width
        val out = output(dest, outW * outH, src)
        for (y in 0 until height) {
            val srcRow = y * width
            for (x in 0 until width) {
                out[x * outW + (outW - 1 - y)] = src[srcRow + x]
            }
        }
        return HarvestArgbFrame(out, outW, outH, rotationDegrees)
    }

    private fun rotate180(
        src: IntArray,
        width: Int,
        height: Int,
        rotationDegrees: Int,
        dest: IntArray?
    ): HarvestArgbFrame {
        val out = output(dest, src.size, src)
        val last = src.size - 1
        for (i in src.indices) out[last - i] = src[i]
        return HarvestArgbFrame(out, width, height, rotationDegrees)
    }

    private fun rotate270(
        src: IntArray,
        width: Int,
        height: Int,
        rotationDegrees: Int,
        dest: IntArray?
    ): HarvestArgbFrame {
        val outW = height
        val outH = width
        val out = output(dest, outW * outH, src)
        for (y in 0 until height) {
            val srcRow = y * width
            for (x in 0 until width) {
                out[(outH - 1 - x) * outW + y] = src[srcRow + x]
            }
        }
        return HarvestArgbFrame(out, outW, outH, rotationDegrees)
    }

    private fun output(dest: IntArray?, needed: Int, src: IntArray): IntArray {
        if (dest != null && dest.size == needed && dest !== src) return dest
        return IntArray(needed)
    }
}

/**
 * Holds the latest downsampled camera frame for capture-time HSV analysis.
 * Callers must copy() — the analyzer may replace the buffer at any time.
 */
class HarvestFrameBuffer {
    private val lock = Any()
    private var argb: IntArray? = null
    private var width: Int = 0
    private var height: Int = 0
    private var rotationDegrees: Int = 0
    private var rgbaBytes: ByteArray? = null
    private var decodePixels: IntArray? = null

    fun updateFromRgbaImage(image: ImageProxy, maxSide: Int = MAX_SIDE) {
        val plane = image.planes.firstOrNull() ?: return
        updateFromRgbaBuffer(
            buffer = plane.buffer,
            srcWidth = image.width,
            srcHeight = image.height,
            pixelStride = plane.pixelStride,
            rowStride = plane.rowStride,
            rotationDegrees = image.imageInfo.rotationDegrees,
            maxSide = maxSide
        )
    }

    fun updateFromRgbaBuffer(
        buffer: ByteBuffer,
        srcWidth: Int,
        srcHeight: Int,
        pixelStride: Int,
        rowStride: Int,
        rotationDegrees: Int,
        maxSide: Int = MAX_SIDE
    ) {
        if (srcWidth <= 0 || srcHeight <= 0) return
        val bytes = HarvestRgbaConverter.copyToBytes(buffer, rgbaBytes)
        rgbaBytes = bytes
        val decoded = HarvestRgbaConverter.decode(
            rgba = bytes,
            srcWidth = srcWidth,
            srcHeight = srcHeight,
            pixelStride = pixelStride,
            rowStride = rowStride,
            maxSide = maxSide,
            rotationDegrees = rotationDegrees,
            reusePixels = decodePixels
        )
        decodePixels = decoded.argb
        synchronized(lock) {
            argb = decoded.argb
            width = decoded.width
            height = decoded.height
            this.rotationDegrees = decoded.rotationDegrees
        }
    }

    fun update(frame: HarvestArgbFrame) {
        synchronized(lock) {
            argb = frame.argb
            width = frame.width
            height = frame.height
            rotationDegrees = frame.rotationDegrees
        }
    }

    fun copy(): HarvestArgbFrame? = synchronized(lock) {
        val pixels = argb ?: return null
        if (width <= 0 || height <= 0) return null
        HarvestArgbFrame(pixels.copyOf(), width, height, rotationDegrees)
    }

    companion object {
        const val MAX_SIDE = 160
        const val ANALYSIS_MAX_SIDE = TargetDetectionCalibration.ANALYSIS_MAX_SIDE
    }
}
