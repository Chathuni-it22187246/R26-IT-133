package com.greenhands.app.harvest.detection

/**
 * One class score from the tomato disease classifier.
 */
data class TomatoDiseaseClassScore(
    val classIndex: Int,
    val rawClassName: String,
    val displayName: String,
    val confidence: Float
)

/**
 * Result of on-device leaf disease classification.
 * This is a possible-disease suggestion, not a confirmed diagnosis.
 */
data class TomatoDiseasePrediction(
    val classIndex: Int,
    val rawClassName: String,
    val displayName: String,
    val confidence: Float,
    val meetsThreshold: Boolean,
    val isHealthyClass: Boolean,
    val appliedSoftmax: Boolean,
    val topPredictions: List<TomatoDiseaseClassScore>
)
