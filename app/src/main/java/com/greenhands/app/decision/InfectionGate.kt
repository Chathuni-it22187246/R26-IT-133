package com.greenhands.app.decision

/**
 * Confidence and temporal checks for Advanced Infection Detection.
 * Scores passed through here are model probabilities. Nothing in this file
 * raises a low score into a high one.
 */
data class RankedPrediction(
    val label: String,
    val score: Float
)

object InfectionConfidenceGate {
    fun accept(
        scores: FloatArray,
        labels: List<String>,
        minTop1: Float,
        minMargin: Float
    ): RankedPrediction? {
        val count = minOf(scores.size, labels.size)
        if (count <= 0) return null
        var best = 0
        var second = -1
        for (index in 1 until count) {
            if (scores[index] > scores[best]) {
                second = best
                best = index
            } else if (second < 0 || scores[index] > scores[second]) {
                second = index
            }
        }
        val top = scores[best]
        val runnerUp = if (second >= 0) scores[second] else 0f
        if (top < minTop1) return null
        if (top - runnerUp < minMargin) return null
        val label = labels[best].trim()
        if (label.isEmpty()) return null
        return RankedPrediction(label, top)
    }
}

/**
 * A clear, high-confidence frame is shown immediately.
 * A moderate score needs two agreeing frames out of the last three.
 * Mixed labels stay unconfirmed.
 */
class PredictionStabilizer(
    private val immediateConfidence: Float = 0.90f
) {
    private val recent = ArrayDeque<RankedPrediction>()
    private var contextKey: String? = null
    private var confirmed: RankedPrediction? = null
    private var uncertainStreak = 0

    fun reset() {
        recent.clear()
        contextKey = null
        confirmed = null
        uncertainStreak = 0
    }

    /** Drop the current vote window without treating the next frame as a new target. */
    fun invalidate() {
        recent.clear()
        confirmed = null
        uncertainStreak = 0
    }

    fun observe(contextKey: String, prediction: RankedPrediction?): RankedPrediction? {
        if (this.contextKey != contextKey) {
            reset()
            this.contextKey = contextKey
        }
        if (prediction == null) {
            uncertainStreak++
            if (uncertainStreak >= 2) {
                recent.clear()
                confirmed = null
                uncertainStreak = 0
                return null
            }
            return confirmed
        }
        uncertainStreak = 0
        if (prediction.score >= immediateConfidence) {
            recent.clear()
            recent.addLast(prediction)
            confirmed = prediction
            return prediction
        }
        recent.addLast(prediction)
        while (recent.size > 3) {
            recent.removeFirst()
        }
        confirmed = agreeingRecent()
        return confirmed
    }

    private fun agreeingRecent(): RankedPrediction? {
        if (recent.size < 2) return null
        val latest = recent.last()
        val previous = recent[recent.size - 2]
        if (latest.label == previous.label) {
            return RankedPrediction(latest.label, (latest.score + previous.score) / 2f)
        }
        if (recent.size < 3) return null
        val counts = LinkedHashMap<String, Int>()
        val scoreSums = LinkedHashMap<String, Float>()
        for (item in recent) {
            counts[item.label] = (counts[item.label] ?: 0) + 1
            scoreSums[item.label] = (scoreSums[item.label] ?: 0f) + item.score
        }
        val best = counts.maxByOrNull { it.value } ?: return null
        if (best.value < 2) return null
        val votes = best.value
        return RankedPrediction(best.key, (scoreSums[best.key] ?: 0f) / votes)
    }
}
