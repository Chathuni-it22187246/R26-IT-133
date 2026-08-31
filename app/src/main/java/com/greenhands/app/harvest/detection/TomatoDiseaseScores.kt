package com.greenhands.app.harvest.detection

import kotlin.math.abs
import kotlin.math.exp

/**
 * Converts classifier logits to probabilities only when the tensor does not
 * already look like a probability distribution.
 */
object TomatoDiseaseScores {
    fun asProbabilities(raw: FloatArray): Pair<FloatArray, Boolean> {
        if (raw.isEmpty()) return raw to false
        if (looksLikeProbabilities(raw)) return raw to false
        return softmax(raw) to true
    }

    fun looksLikeProbabilities(raw: FloatArray): Boolean {
        if (raw.isEmpty()) return false
        var min = Float.POSITIVE_INFINITY
        var max = Float.NEGATIVE_INFINITY
        var sum = 0f
        for (v in raw) {
            if (v < min) min = v
            if (v > max) max = v
            sum += v
        }
        return min >= -1.0e-4f && max <= 1.0001f && abs(sum - 1f) <= 0.08f
    }

    fun softmax(logits: FloatArray): FloatArray {
        if (logits.isEmpty()) return logits
        var max = Float.NEGATIVE_INFINITY
        for (v in logits) if (v > max) max = v
        val out = FloatArray(logits.size)
        var sum = 0.0
        for (i in logits.indices) {
            val e = exp((logits[i] - max).toDouble())
            out[i] = e.toFloat()
            sum += e
        }
        if (sum <= 0.0) return out
        for (i in out.indices) out[i] = (out[i] / sum).toFloat()
        return out
    }

    fun topK(probabilities: FloatArray, k: Int): List<IndexedValue<Float>> {
        return probabilities.withIndex()
            .sortedByDescending { it.value }
            .take(k.coerceAtLeast(0))
    }
}
