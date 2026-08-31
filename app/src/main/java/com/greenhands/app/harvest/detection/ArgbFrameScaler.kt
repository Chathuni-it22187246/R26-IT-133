package com.greenhands.app.harvest.detection

import com.greenhands.app.harvest.domain.HarvestArgbFrame

/** Nearest-neighbor scale used to size frames for a TFLite input tensor. */
object ArgbFrameScaler {
    fun scale(frame: HarvestArgbFrame, outWidth: Int, outHeight: Int): HarvestArgbFrame {
        if (outWidth <= 0 || outHeight <= 0) return frame
        if (frame.width == outWidth && frame.height == outHeight) return frame
        val out = IntArray(outWidth * outHeight)
        var i = 0
        for (y in 0 until outHeight) {
            val srcY = (y * frame.height) / outHeight
            val row = srcY * frame.width
            for (x in 0 until outWidth) {
                val srcX = (x * frame.width) / outWidth
                out[i++] = frame.argb[row + srcX]
            }
        }
        return HarvestArgbFrame(out, outWidth, outHeight)
    }
}
