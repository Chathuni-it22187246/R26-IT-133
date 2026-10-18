package com.greenhands.app.harvest.detection

import com.greenhands.app.harvest.domain.HarvestArgbFrame

/**
 * YOLOv8 letterbox: keep aspect ratio, pad to the model input size with RGB 114.
 * Boxes predicted in letterboxed input space must be mapped back with [LetterboxTransform].
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

    fun fit(frame: HarvestArgbFrame, inputWidth: Int, inputHeight: Int): LetterboxedImage {
        val srcW = frame.width.coerceAtLeast(1)
        val srcH = frame.height.coerceAtLeast(1)
        val outW = inputWidth.coerceAtLeast(1)
        val outH = inputHeight.coerceAtLeast(1)
        val gain = minOf(outW.toFloat() / srcW, outH.toFloat() / srcH)
        val newW = (srcW * gain).toInt().coerceAtLeast(1).coerceAtMost(outW)
        val newH = (srcH * gain).toInt().coerceAtLeast(1).coerceAtMost(outH)
        val padX = (outW - newW) / 2f
        val padY = (outH - newH) / 2f
        val out = IntArray(outW * outH) { padArgb }
        val x0 = padX.toInt()
        val y0 = padY.toInt()
        for (y in 0 until newH) {
            val srcY = ((y + 0.5f) * srcH / newH) - 0.5f
            val outRow = (y0 + y) * outW + x0
            for (x in 0 until newW) {
                val srcX = ((x + 0.5f) * srcW / newW) - 0.5f
                out[outRow + x] = sampleBilinear(frame, srcX, srcY)
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

    private fun sampleBilinear(frame: HarvestArgbFrame, x: Float, y: Float): Int {
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

    private fun lerpArgb(a: Int, b: Int, t: Float): Int {
        fun ch(v: Int, shift: Int) = (v shr shift) and 0xFF
        fun mix(ca: Int, cb: Int) = (ca + ((cb - ca) * t)).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or
            (mix(ch(a, 16), ch(b, 16)) shl 16) or
            (mix(ch(a, 8), ch(b, 8)) shl 8) or
            mix(ch(a, 0), ch(b, 0))
    }
}
