package com.greenhands.app.harvest.detection

import java.nio.ByteBuffer

/**
 * Packs letterboxed ARGB into the YOLO input tensor.
 * RGB order and /255 float32 (or uint8) match the previous fillInput loops.
 */
object YoloInputPacker {
    fun pack(
        pixels: IntArray,
        width: Int,
        height: Int,
        nchw: Boolean,
        quantized: Boolean,
        dest: ByteBuffer,
        reuseFloats: FloatArray? = null,
        reuseBytes: ByteArray? = null
    ): Pair<FloatArray?, ByteArray?> {
        val count = (width * height).coerceAtLeast(0)
        val limit = minOf(count, pixels.size)
        dest.rewind()
        return if (quantized) {
            val bytes = packBytes(pixels, limit, nchw, reuseBytes)
            dest.put(bytes)
            dest.rewind()
            reuseFloats to bytes
        } else {
            val floats = packFloats(pixels, limit, nchw, reuseFloats)
            dest.asFloatBuffer().put(floats)
            dest.rewind()
            floats to reuseBytes
        }
    }

    fun packFloats(
        pixels: IntArray,
        count: Int,
        nchw: Boolean,
        dest: FloatArray? = null
    ): FloatArray {
        val needed = count * 3
        val out = if (dest != null && dest.size == needed) dest else FloatArray(needed)
        if (nchw) {
            var r = 0
            var g = count
            var b = count * 2
            for (i in 0 until count) {
                val p = pixels[i]
                out[r++] = ((p shr 16) and 0xFF) / 255f
                out[g++] = ((p shr 8) and 0xFF) / 255f
                out[b++] = (p and 0xFF) / 255f
            }
        } else {
            var o = 0
            for (i in 0 until count) {
                val p = pixels[i]
                out[o++] = ((p shr 16) and 0xFF) / 255f
                out[o++] = ((p shr 8) and 0xFF) / 255f
                out[o++] = (p and 0xFF) / 255f
            }
        }
        return out
    }

    fun packBytes(
        pixels: IntArray,
        count: Int,
        nchw: Boolean,
        dest: ByteArray? = null
    ): ByteArray {
        val needed = count * 3
        val out = if (dest != null && dest.size == needed) dest else ByteArray(needed)
        if (nchw) {
            var r = 0
            var g = count
            var b = count * 2
            for (i in 0 until count) {
                val p = pixels[i]
                out[r++] = ((p shr 16) and 0xFF).toByte()
                out[g++] = ((p shr 8) and 0xFF).toByte()
                out[b++] = (p and 0xFF).toByte()
            }
        } else {
            var o = 0
            for (i in 0 until count) {
                val p = pixels[i]
                out[o++] = ((p shr 16) and 0xFF).toByte()
                out[o++] = ((p shr 8) and 0xFF).toByte()
                out[o++] = (p and 0xFF).toByte()
            }
        }
        return out
    }

    /** Original per-channel ByteBuffer.putFloat / put loops, for equivalence tests. */
    fun packReference(
        pixels: IntArray,
        width: Int,
        height: Int,
        nchw: Boolean,
        quantized: Boolean,
        dest: ByteBuffer
    ) {
        val count = width * height
        dest.rewind()
        if (nchw) {
            if (quantized) {
                for (i in 0 until count) {
                    dest.put(((pixels[i] shr 16) and 0xFF).toByte())
                }
                for (i in 0 until count) {
                    dest.put(((pixels[i] shr 8) and 0xFF).toByte())
                }
                for (i in 0 until count) {
                    dest.put((pixels[i] and 0xFF).toByte())
                }
            } else {
                for (i in 0 until count) {
                    dest.putFloat(((pixels[i] shr 16) and 0xFF) / 255f)
                }
                for (i in 0 until count) {
                    dest.putFloat(((pixels[i] shr 8) and 0xFF) / 255f)
                }
                for (i in 0 until count) {
                    dest.putFloat((pixels[i] and 0xFF) / 255f)
                }
            }
        } else {
            if (quantized) {
                for (i in 0 until count) {
                    val pixel = pixels[i]
                    dest.put(((pixel shr 16) and 0xFF).toByte())
                    dest.put(((pixel shr 8) and 0xFF).toByte())
                    dest.put((pixel and 0xFF).toByte())
                }
            } else {
                for (i in 0 until count) {
                    val pixel = pixels[i]
                    dest.putFloat(((pixel shr 16) and 0xFF) / 255f)
                    dest.putFloat(((pixel shr 8) and 0xFF) / 255f)
                    dest.putFloat((pixel and 0xFF) / 255f)
                }
            }
        }
        dest.rewind()
    }
}
