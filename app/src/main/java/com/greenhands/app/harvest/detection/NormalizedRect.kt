package com.greenhands.app.harvest.detection

/**
 * Axis-aligned box in normalized image coordinates (0–1).
 * left/top are the origin; right/bottom are exclusive-style extents clamped to 1.
 */
data class NormalizedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)
    val area: Float get() = width * height
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    fun clamp(): NormalizedRect = NormalizedRect(
        left = left.coerceIn(0f, 1f),
        top = top.coerceIn(0f, 1f),
        right = right.coerceIn(0f, 1f),
        bottom = bottom.coerceIn(0f, 1f)
    )

    fun padded(ratio: Float): NormalizedRect {
        val padX = width * ratio
        val padY = height * ratio
        return NormalizedRect(
            left = left - padX,
            top = top - padY,
            right = right + padX,
            bottom = bottom + padY
        ).clamp()
    }

    fun iou(other: NormalizedRect): Float {
        val interLeft = maxOf(left, other.left)
        val interTop = maxOf(top, other.top)
        val interRight = minOf(right, other.right)
        val interBottom = minOf(bottom, other.bottom)
        val interW = (interRight - interLeft).coerceAtLeast(0f)
        val interH = (interBottom - interTop).coerceAtLeast(0f)
        val inter = interW * interH
        val union = area + other.area - inter
        if (union <= 0f) return 0f
        return inter / union
    }

    companion object {
        fun fromMinMax(ymin: Float, xmin: Float, ymax: Float, xmax: Float): NormalizedRect =
            NormalizedRect(left = xmin, top = ymin, right = xmax, bottom = ymax).clamp()
    }
}
