package com.greenhands.app

import com.greenhands.app.harvest.detection.LetterboxTransform
import com.greenhands.app.harvest.detection.TargetDetectionCalibration
import com.greenhands.app.harvest.detection.YoloInputPacker
import com.greenhands.app.harvest.detection.YoloLetterbox
import com.greenhands.app.harvest.detection.YoloV8OutputParser
import com.greenhands.app.harvest.domain.HarvestArgbFrame
import com.greenhands.app.harvest.domain.HarvestArgbRotate
import com.greenhands.app.harvest.domain.HarvestFrameBuffer
import com.greenhands.app.harvest.domain.HarvestRgbaConverter
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class HarvestPreprocessOptTest {

    @Test
    fun rgbaDecodeMatchesTwoStepDownsampleAndRotate() {
        for (rotation in intArrayOf(0, 90, 180, 270)) {
            val srcW = 6
            val srcH = 4
            val packed = packedRgbaGradient(srcW, srcH, pixelStride = 4, rowStride = 6 * 4)
            val optimized = HarvestRgbaConverter.decode(
                rgba = packed,
                srcWidth = srcW,
                srcHeight = srcH,
                pixelStride = 4,
                rowStride = srcW * 4,
                maxSide = 6,
                rotationDegrees = rotation
            )
            val reference = twoStepDecode(packed, srcW, srcH, 4, srcW * 4, 6, rotation)
            assertEquals("rot=$rotation width", reference.width, optimized.width)
            assertEquals("rot=$rotation height", reference.height, optimized.height)
            assertEquals(rotation, optimized.rotationDegrees)
            assertArrayEquals("rot=$rotation pixels", reference.argb, optimized.argb)
        }
    }

    @Test
    fun rgbaKeepsRgbChannelOrder() {
        val packed = ByteArray(4)
        packed[0] = 0xC8.toByte()
        packed[1] = 0x40.toByte()
        packed[2] = 0x20.toByte()
        packed[3] = 0xFF.toByte()
        val frame = HarvestRgbaConverter.decode(
            rgba = packed,
            srcWidth = 1,
            srcHeight = 1,
            pixelStride = 4,
            rowStride = 4,
            maxSide = 1,
            rotationDegrees = 0
        )
        val px = frame.argb[0]
        assertEquals(0xC8, (px shr 16) and 0xFF)
        assertEquals(0x40, (px shr 8) and 0xFF)
        assertEquals(0x20, px and 0xFF)
        assertEquals(0xFF, (px ushr 24) and 0xFF)
    }

    @Test
    fun rotateReuseWritesIntoDistinctDestination() {
        val src = intArrayOf(1, 2, 3, 4, 5, 6)
        val dest = IntArray(6)
        val rotated = HarvestArgbRotate.apply(src, width = 2, height = 3, rotationDegrees = 90, dest = dest)
        assertSame(dest, rotated.argb)
        assertNotSame(src, rotated.argb)
        assertEquals(3, rotated.width)
        assertEquals(2, rotated.height)
        assertEquals(5, rotated.argb[0])
        assertEquals(2, rotated.argb[5])
        val overlapping = HarvestArgbRotate.apply(src, 2, 3, 90, dest = src)
        assertNotSame(src, overlapping.argb)
        assertArrayEquals(rotated.argb, overlapping.argb)
    }

    @Test
    fun letterboxMatchesOriginalBilinearOnFrozenGradient() {
        val srcW = 80
        val srcH = 60
        val frame = gradientFrame(srcW, srcH)
        val optimized = YoloLetterbox.fit(frame, 64, 64)
        val reference = letterboxReference(frame, 64, 64)
        assertEquals(64, optimized.frame.width)
        assertEquals(64, optimized.frame.height)
        assertEquals(reference.transform.gain, optimized.transform.gain, 0.0f)
        assertEquals(reference.transform.padX, optimized.transform.padX, 0.0f)
        assertEquals(reference.transform.padY, optimized.transform.padY, 0.0f)
        assertEquals(0, maxChannelDiff(reference.frame.argb, optimized.frame.argb))
        val pad = optimized.frame.argb[1]
        assertEquals(YoloLetterbox.PAD_RGB, (pad shr 16) and 0xFF)
        assertEquals(YoloLetterbox.PAD_RGB, (pad shr 8) and 0xFF)
        assertEquals(YoloLetterbox.PAD_RGB, pad and 0xFF)
    }

    @Test
    fun productionLetterboxStays416AndMapsBoxesIdentically() {
        val frame = gradientFrame(640, 480)
        val optimized = YoloLetterbox.fit(frame, 416, 416, reuseArgb = IntArray(416 * 416))
        val reference = letterboxReference(frame, 416, 416)
        assertEquals(416, optimized.frame.width)
        assertEquals(416, optimized.frame.height)
        assertEquals(416f / 640f, optimized.transform.gain, 0.0f)
        assertEquals(0, maxChannelDiff(reference.frame.argb, optimized.frame.argb))
        val box = YoloV8OutputParser.xywhToSourceNormalized(
            cx = 208f, cy = 208f, w = 80f, h = 60f,
            inputWidth = 416, inputHeight = 416, transform = optimized.transform
        )
        val boxRef = YoloV8OutputParser.xywhToSourceNormalized(
            cx = 208f, cy = 208f, w = 80f, h = 60f,
            inputWidth = 416, inputHeight = 416, transform = reference.transform
        )
        assertEquals(boxRef.left, box.left, 0.0f)
        assertEquals(boxRef.top, box.top, 0.0f)
        assertEquals(boxRef.right, box.right, 0.0f)
        assertEquals(boxRef.bottom, box.bottom, 0.0f)
    }

    @Test
    fun inputPackMatchesOriginalFloatAndByteLoops() {
        val pixels = gradientFrame(8, 6).argb
        for (nchw in arrayOf(false, true)) {
            for (quantized in arrayOf(false, true)) {
                val bytes = if (quantized) 8 * 6 * 3 else 8 * 6 * 3 * 4
                val optimized = ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder())
                val reference = ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder())
                YoloInputPacker.pack(
                    pixels = pixels,
                    width = 8,
                    height = 6,
                    nchw = nchw,
                    quantized = quantized,
                    dest = optimized
                )
                YoloInputPacker.packReference(
                    pixels = pixels,
                    width = 8,
                    height = 6,
                    nchw = nchw,
                    quantized = quantized,
                    dest = reference
                )
                assertBuffersEqual(reference, optimized, quantized, "nchw=$nchw quant=$quantized")
            }
        }
        val nhwc = YoloInputPacker.packFloats(intArrayOf(0xFFCC4020.toInt()), 1, nchw = false)
        assertEquals(0xCC / 255f, nhwc[0], 0.0f)
        assertEquals(0x40 / 255f, nhwc[1], 0.0f)
        assertEquals(0x20 / 255f, nhwc[2], 0.0f)
    }

    @Test
    fun frameBufferCopyIsIndependentOfLaterDecode() {
        val first = packedRgbaSolid(4, 4, r = 10, g = 20, b = 30)
        val second = packedRgbaSolid(4, 4, r = 200, g = 10, b = 5)
        val buffer = HarvestFrameBuffer()
        buffer.updateFromRgbaBuffer(
            buffer = ByteBuffer.wrap(first),
            srcWidth = 4,
            srcHeight = 4,
            pixelStride = 4,
            rowStride = 16,
            rotationDegrees = 0,
            maxSide = 4
        )
        val snapshot = buffer.copy()!!
        buffer.updateFromRgbaBuffer(
            buffer = ByteBuffer.wrap(second),
            srcWidth = 4,
            srcHeight = 4,
            pixelStride = 4,
            rowStride = 16,
            rotationDegrees = 90,
            maxSide = 4
        )
        val next = buffer.copy()!!
        assertEquals(0xFF0A141E.toInt(), snapshot.argb[0])
        assertNotEquals(snapshot.argb[0], next.argb[0])
        assertNotSame(snapshot.argb, next.argb)
        snapshot.argb[0] = 1
        val again = buffer.copy()!!
        assertEquals(next.argb[0], again.argb[0])
        assertEquals(4, next.width)
        assertEquals(4, next.height)
        assertEquals(90, next.rotationDegrees)
    }

    @Test
    fun letterboxReuseDoesNotMutateSourceFrame() {
        val frame = gradientFrame(32, 24)
        val before = frame.argb.copyOf()
        val reuse = IntArray(32 * 32)
        YoloLetterbox.fit(frame, 32, 32, reuseArgb = reuse)
        assertArrayEquals(before, frame.argb)
        assertNotSame(frame.argb, reuse)
    }

    @Test
    fun analysisMaxSideAndInputSizeUnchanged() {
        assertEquals(640, TargetDetectionCalibration.ANALYSIS_MAX_SIDE)
        assertEquals(0.25f, TargetDetectionCalibration.DETECTOR_SCORE_FLOOR, 0.0f)
        assertEquals(0.65f, TargetDetectionCalibration.MIN_DETECTION_CONFIDENCE, 0.0f)
        assertEquals(8, TargetDetectionCalibration.REQUIRED_STABLE_FRAMES)
    }

    private fun twoStepDecode(
        rgba: ByteArray,
        srcW: Int,
        srcH: Int,
        pixelStride: Int,
        rowStride: Int,
        maxSide: Int,
        rotation: Int
    ): HarvestArgbFrame {
        val scale = maxOf(srcW, srcH).toFloat() / maxSide.toFloat()
        val dstW = (srcW / scale).toInt().coerceAtLeast(1)
        val dstH = (srcH / scale).toInt().coerceAtLeast(1)
        val out = IntArray(dstW * dstH)
        var i = 0
        for (y in 0 until dstH) {
            val srcY = (y * srcH) / dstH
            for (x in 0 until dstW) {
                val srcX = (x * srcW) / dstW
                out[i++] = HarvestRgbaConverter.readRgba8888(rgba, srcX, srcY, pixelStride, rowStride)
            }
        }
        return HarvestArgbRotate.apply(out, dstW, dstH, rotation)
    }

    private fun letterboxReference(frame: HarvestArgbFrame, inputWidth: Int, inputHeight: Int): com.greenhands.app.harvest.detection.LetterboxedImage {
        val srcW = frame.width.coerceAtLeast(1)
        val srcH = frame.height.coerceAtLeast(1)
        val outW = inputWidth.coerceAtLeast(1)
        val outH = inputHeight.coerceAtLeast(1)
        val gain = minOf(outW.toFloat() / srcW, outH.toFloat() / srcH)
        val newW = (srcW * gain).toInt().coerceAtLeast(1).coerceAtMost(outW)
        val newH = (srcH * gain).toInt().coerceAtLeast(1).coerceAtMost(outH)
        val padX = (outW - newW) / 2f
        val padY = (outH - newH) / 2f
        val padArgb = (0xFF shl 24) or (YoloLetterbox.PAD_RGB shl 16) or
            (YoloLetterbox.PAD_RGB shl 8) or YoloLetterbox.PAD_RGB
        val out = IntArray(outW * outH) { padArgb }
        val x0 = padX.toInt()
        val y0 = padY.toInt()
        for (y in 0 until newH) {
            val srcY = ((y + 0.5f) * srcH / newH) - 0.5f
            val outRow = (y0 + y) * outW + x0
            for (x in 0 until newW) {
                val srcX = ((x + 0.5f) * srcW / newW) - 0.5f
                out[outRow + x] = YoloLetterbox.sampleBilinear(frame, srcX, srcY)
            }
        }
        return com.greenhands.app.harvest.detection.LetterboxedImage(
            frame = HarvestArgbFrame(out, outW, outH),
            transform = LetterboxTransform(srcW, srcH, outW, outH, gain, padX, padY)
        )
    }

    private fun gradientFrame(width: Int, height: Int): HarvestArgbFrame {
        val argb = IntArray(width * height)
        var i = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val r = (x * 255) / (width - 1).coerceAtLeast(1)
                val g = (y * 255) / (height - 1).coerceAtLeast(1)
                val b = ((x + y) * 255) / (width + height - 2).coerceAtLeast(1)
                argb[i++] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        return HarvestArgbFrame(argb, width, height)
    }

    private fun packedRgbaGradient(width: Int, height: Int, pixelStride: Int, rowStride: Int): ByteArray {
        val out = ByteArray(rowStride * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val i = y * rowStride + x * pixelStride
                out[i] = ((x * 40) and 0xFF).toByte()
                out[i + 1] = ((y * 50) and 0xFF).toByte()
                out[i + 2] = (((x + y) * 15) and 0xFF).toByte()
                if (pixelStride > 3) out[i + 3] = 0xFF.toByte()
            }
        }
        return out
    }

    private fun packedRgbaSolid(width: Int, height: Int, r: Int, g: Int, b: Int): ByteArray {
        val out = ByteArray(width * height * 4)
        var i = 0
        repeat(width * height) {
            out[i++] = r.toByte()
            out[i++] = g.toByte()
            out[i++] = b.toByte()
            out[i++] = 0xFF.toByte()
        }
        return out
    }

    private fun maxChannelDiff(a: IntArray, b: IntArray): Int {
        assertEquals(a.size, b.size)
        var max = 0
        for (i in a.indices) {
            max = maxOf(max, channelDiff(a[i], b[i], 16))
            max = maxOf(max, channelDiff(a[i], b[i], 8))
            max = maxOf(max, channelDiff(a[i], b[i], 0))
        }
        return max
    }

    private fun channelDiff(a: Int, b: Int, shift: Int): Int =
        kotlin.math.abs(((a shr shift) and 0xFF) - ((b shr shift) and 0xFF))

    private fun assertBuffersEqual(
        reference: ByteBuffer,
        optimized: ByteBuffer,
        quantized: Boolean,
        label: String
    ) {
        reference.rewind()
        optimized.rewind()
        assertEquals(label, reference.remaining(), optimized.remaining())
        if (quantized) {
            while (reference.hasRemaining()) {
                assertEquals(label, reference.get(), optimized.get())
            }
        } else {
            val rf = reference.asFloatBuffer()
            val of = optimized.asFloatBuffer()
            while (rf.hasRemaining()) {
                assertEquals(label, rf.get(), of.get(), 0.0f)
            }
        }
    }
}
