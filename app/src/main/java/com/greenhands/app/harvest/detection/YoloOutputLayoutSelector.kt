package com.greenhands.app.harvest.detection

/**
 * Chooses YOLOv8 flat-output layout.
 *
 * Unambiguous tensor shapes are decoded from rank-3 geometry only:
 * - [1, 6, N] / [1, C, N] with C <= 16 -> CHANNEL_MAJOR
 * - [1, N, 6] / [1, N, C] with C <= 16 -> PRED_MAJOR
 *
 * The 1.5x score heuristic must not override those shapes. It remains only
 * as a documented fallback when both trailing dimensions look like
 * prediction counts.
 */
enum class YoloTensorLayout {
    CHANNEL_MAJOR,
    PRED_MAJOR
}

class YoloOutputLayoutSelector(
    private val confirmationsRequired: Int = 3,
    private val predMajorMargin: Float = 1.5f,
    private val minValidateScore: Float = 0.05f
) {
    var cached: YoloTensorLayout? = null
        private set

    private var pending: YoloTensorLayout? = null
    private var pendingCount: Int = 0

    val isCached: Boolean get() = cached != null

    fun preferredLayout(channelMajorMax: Float, predMajorMax: Float): YoloTensorLayout {
        val predWins = predMajorMax > channelMajorMax * predMajorMargin &&
            predMajorMax > minValidateScore
        return if (predWins) YoloTensorLayout.PRED_MAJOR else YoloTensorLayout.CHANNEL_MAJOR
    }

    fun observeAndChoose(channelMajorMax: Float, predMajorMax: Float): YoloTensorLayout {
        cached?.let { return it }
        val chosen = preferredLayout(channelMajorMax, predMajorMax)
        val strongest = maxOf(channelMajorMax, predMajorMax)
        if (strongest >= minValidateScore) {
            if (chosen == pending) {
                pendingCount++
            } else {
                pending = chosen
                pendingCount = 1
            }
            if (pendingCount >= confirmationsRequired) {
                cached = chosen
            }
        }
        return chosen
    }

    fun reset() {
        cached = null
        pending = null
        pendingCount = 0
    }

    companion object {
        const val ATTRIBUTE_DIM_MAX = 16

        val AMBIGUOUS_FALLBACK = YoloTensorLayout.CHANNEL_MAJOR

        fun fromOutputShape(shape: IntArray): YoloTensorLayout? {
            if (shape.size != 3 || shape[0] != 1) return null
            val dim1 = shape[1]
            val dim2 = shape[2]
            val dim1IsAttrs = dim1 in 1..ATTRIBUTE_DIM_MAX && dim2 > dim1
            val dim2IsAttrs = dim2 in 1..ATTRIBUTE_DIM_MAX && dim1 > dim2
            return when {
                dim1IsAttrs && !dim2IsAttrs -> YoloTensorLayout.CHANNEL_MAJOR
                dim2IsAttrs && !dim1IsAttrs -> YoloTensorLayout.PRED_MAJOR
                else -> null
            }
        }

        fun resolve(shape: IntArray): YoloTensorLayout =
            fromOutputShape(shape) ?: AMBIGUOUS_FALLBACK

        fun isFromShape(shape: IntArray): Boolean = fromOutputShape(shape) != null
    }
}
