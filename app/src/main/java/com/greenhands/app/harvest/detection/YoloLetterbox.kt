package com.greenhands.app.harvest.detection

import com.greenhands.app.harvest.domain.HarvestArgbFrame

/**
 * YOLOv8 letterbox: keep aspect ratio, pad to the model input size with RGB 114.
 * Boxes predicted in letterboxed input space must be mapped back with [LetterboxTransform].
 *
 * Bilinear sampling matches the original `sampleBilinear` / `lerpArgb` formula.
 * Row Y terms and per-column X terms are hoisted; pad fill and bilinear weights
 * are unchanged.
 */
data class LetterboxTransform(
    val sourceWidth: Int,
    val sourceHeight: Int,
    val inputWidth: Int,
    val inputHeight: Int,
    val gain: Float,
    val padX: Float,
    val padY: Float
) {
    fun toSourceNormalized(
        x1Input: Float,
        y1Input: Float,
        x2Input: Float,
        y2Input: Float
    ): NormalizedRect {
        if (gain <= 0f || sourceWidth <= 0 || sourceHeight <= 0) {
            return NormalizedRect(0f, 0f, 0f, 0f)
        }
        val left = ((x1Input - padX) / gain) / sourceWidth
        val top = ((y1Input - padY) / gain) / sourceHeight
        val right = ((x2Input - padX) / gain) / sourceWidth
        val bottom = ((y2Input - padY) / gain) / sourceHeight
        return NormalizedRect(left, top, right, bottom).clamp()
    }
}

data class LetterboxedImage(
    val frame: HarvestArgbFrame,
    val transform: LetterboxTransform
)

object YoloLetterbox {
    const val PAD_RGB = 114
    private val padArgb: Int = (0xFF shl 24) or (PAD_RGB shl 16) or (PAD_RGB shl 8) or PAD_RGB

    @Volatile
    private var srcXTable: FloatArray? = null
    @Volatile
    private var srcXTableNewW: Int = -1
    @Volatile
    private var srcXTableSrcW: Int = -1

    fun fit(
        frame: HarvestArgbFrame,
        inputWidth: Int,
        inputHeight: Int,
        reuseArgb: IntArray? = null
    ): LetterboxedImage {
        val srcW = frame.width.coerceAtLeast(1)
        val srcH = frame.height.coerceAtLeast(1)
        val outW = inputWidth.coerceAtLeast(1)
        val outH = inputHeight.coerceAtLeast(1)
        val gain = minOf(outW.toFloat() / srcW, outH.toFloat() / srcH)
        val newW = (srcW * gain).toInt().coerceAtLeast(1).coerceAtMost(outW)
        val newH = (srcH * gain).toInt().coerceAtLeast(1).coerceAtMost(outH)
        val padX = (outW - newW) / 2f
        val padY = (outH - newH) / 2f
        val needed = outW * outH
        val out = if (reuseArgb != null && reuseArgb.size == needed) {
            reuseArgb
        } else {
            IntArray(needed)
        }
        out.fill(padArgb)
        val src = frame.argb
        val srcLastX = srcW - 1
        val srcLastY = srcH - 1
        val xTable = srcXLookup(newW, srcW)
        val x0 = padX.toInt()
        val y0 = padY.toInt()
        for (y in 0 until newH) {
            val srcY = ((y + 0.5f) * srcH / newH) - 0.5f
            val sy0 = srcY.toInt().coerceIn(0, srcLastY)
            val sy1 = (sy0 + 1).coerceAtMost(srcLastY)
            val fy = (srcY - sy0).coerceIn(0f, 1f)
            val row0 = sy0 * srcW
            val row1 = sy1 * srcW
            val outRow = (y0 + y) * outW + x0
            for (x in 0 until newW) {
                val srcX = xTable[x]
                val sx0 = srcX.toInt().coerceIn(0, srcLastX)
                val sx1 = (sx0 + 1).coerceAtMost(srcLastX)
                val fx = (srcX - sx0).coerceIn(0f, 1f)
                out[outRow + x] = lerpArgb(
                    lerpArgb(src[row0 + sx0], src[row0 + sx1], fx),
                    lerpArgb(src[row1 + sx0], src[row1 + sx1], fx),
                    fy
                )
            }
        }
        return LetterboxedImage(
            frame = HarvestArgbFrame(out, outW, outH),
            transform = LetterboxTransform(
                sourceWidth = srcW,
                sourceHeight = srcH,
                inputWidth = outW,
                inputHeight = outH,
                gain = gain,
                padX = padX,
                padY = padY
            )
        )
    }

    fun sampleBilinear(frame: HarvestArgbFrame, x: Float, y: Float): Int {
        val x0 = x.toInt().coerceIn(0, frame.width - 1)
        val y0 = y.toInt().coerceIn(0, frame.height - 1)
        val x1 = (x0 + 1).coerceAtMost(frame.width - 1)
        val y1 = (y0 + 1).coerceAtMost(frame.height - 1)
        val fx = (x - x0).coerceIn(0f, 1f)
        val fy = (y - y0).coerceIn(0f, 1f)
        val c00 = frame.argb[y0 * frame.width + x0]
        val c10 = frame.argb[y0 * frame.width + x1]
        val c01 = frame.argb[y1 * frame.width + x0]
        val c11 = frame.argb[y1 * frame.width + x1]
        return lerpArgb(lerpArgb(c00, c10, fx), lerpArgb(c01, c11, fx), fy)
    }

    fun lerpArgb(a: Int, b: Int, t: Float): Int {
        fun ch(v: Int, shift: Int) = (v shr shift) and 0xFF
        fun mix(ca: Int, cb: Int) = (ca + ((cb - ca) * t)).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or
            (mix(ch(a, 16), ch(b, 16)) shl 16) or
            (mix(ch(a, 8), ch(b, 8)) shl 8) or
            mix(ch(a, 0), ch(b, 0))
    }

    private fun srcXLookup(newW: Int, srcW: Int): FloatArray {
        val existing = srcXTable
        if (existing != null && srcXTableNewW == newW && srcXTableSrcW == srcW) {
            return existing
        }
        val table = FloatArray(newW) { x -> ((x + 0.5f) * srcW / newW) - 0.5f }
        srcXTable = table
        srcXTableNewW = newW
        srcXTableSrcW = srcW
        return table
    }
}
