package com.greenhands.app.decision

import android.graphics.RectF
import java.util.concurrent.TimeUnit

data class RiskSample(
    val recordedAtMillis: Long,
    val riskLevel: String,
    val riskScore: Int
)

data class TrackedInfectionRecord(
    val id: String,
    val plantType: String,
    val infectionName: String,
    val infectionFullName: String,
    val description: String,
    val createdAtMillis: Long,
    val formedAtMillis: Long,
    val history: List<RiskSample>,
    val targetKind: String? = "Leaf"
) {
    val currentRisk: RiskSample
        get() = history.lastOrNull() ?: RiskSample(createdAtMillis, "Low", 15)

    fun daysAgoFormed(nowMillis: Long = System.currentTimeMillis()): Int {
        val elapsed = (nowMillis - formedAtMillis).coerceAtLeast(0L)
        return TimeUnit.MILLISECONDS.toDays(elapsed).toInt().coerceAtLeast(0)
    }
}

enum class RiskTrend {
    Increased,
    Decreased,
    Unchanged
}

object InfectionPriority {
    /** Reject whole-leaf / preview-sized regions; keep only compact lesion spots. */
    fun isLocalizedSpotBox(box: RectF): Boolean {
        if (box.isEmpty || box.width() <= 0f || box.height() <= 0f) return false
        val area = box.width() * box.height()
        if (area < MIN_SPOT_AREA || area > MAX_SPOT_AREA) return false
        if (box.width() > MAX_SPOT_DIMENSION || box.height() > MAX_SPOT_DIMENSION) return false
        return true
    }

    private const val MIN_SPOT_AREA = 0.0004f
    private const val MAX_SPOT_AREA = 0.065f
    private const val MAX_SPOT_DIMENSION = 0.28f

    fun detectionRank(detection: DetectedInfection): Float {
        val area = (detection.box.width() * detection.box.height()).coerceIn(0f, 1f)
        return defaultSeverityWeight(detection.label) * 12f +
            detection.score * 8f +
            area * 10f
    }

    fun pickHighestPriority(detections: List<DetectedInfection>): DetectedInfection? {
        return detections.maxByOrNull(::detectionRank)
    }

    fun computeRisk(detection: DetectedInfection, datasetSeverity: String): RiskSample {
        val base = severityScore(datasetSeverity.ifBlank { defaultLevel(detection.label) })
        val areaBoost = ((detection.box.width() * detection.box.height()) * 36f).toInt()
        val confidenceBoost = (detection.score * 22f).toInt()
        val score = (base + areaBoost + confidenceBoost).coerceIn(8, 100)
        return RiskSample(
            recordedAtMillis = System.currentTimeMillis(),
            riskLevel = scoreToLevel(score),
            riskScore = score
        )
    }

    fun estimateFormedAtMillis(risk: RiskSample, nowMillis: Long = System.currentTimeMillis()): Long {
        val days = when (risk.riskLevel.lowercase()) {
            "critical" -> 10
            "high" -> 6
            "medium" -> 3
            else -> 1
        }
        return nowMillis - TimeUnit.DAYS.toMillis(days.toLong())
    }

    fun trend(history: List<RiskSample>): RiskTrend {
        if (history.size < 2) return RiskTrend.Unchanged
        val latest = history.last().riskScore
        val previous = history[history.lastIndex - 1].riskScore
        return when {
            latest > previous + 2 -> RiskTrend.Increased
            latest < previous - 2 -> RiskTrend.Decreased
            else -> RiskTrend.Unchanged
        }
    }

    fun defaultLevel(label: String): String = when (label.lowercase()) {
        "late blight", "bacterial wilt", "bacterial canker", "bacterial leaf spot" -> "Critical"
        "early blight", "anthracnose", "downy mildew", "fusarium wilt", "gray mold" -> "High"
        "powdery mildew", "leaf spot", "mosaic virus", "tomato yellow leaf curl" -> "Medium"
        else -> "Low"
    }

    fun defaultSeverityWeight(label: String): Float = when (defaultLevel(label)) {
        "Critical" -> 4f
        "High" -> 3f
        "Medium" -> 2f
        else -> 1f
    }

    fun severityScore(level: String): Int = when (level.lowercase()) {
        "critical" -> 78
        "high" -> 58
        "medium", "moderate" -> 38
        else -> 18
    }

    fun scoreToLevel(score: Int): String = when {
        score >= 75 -> "Critical"
        score >= 55 -> "High"
        score >= 30 -> "Medium"
        else -> "Low"
    }
}
