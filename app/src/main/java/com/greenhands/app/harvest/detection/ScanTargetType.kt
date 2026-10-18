package com.greenhands.app.harvest.detection

/**
 * Intended camera target for a harvest scan.
 * Fruit Scan accepts only [TOMATO_FRUIT]. Leaf Scan accepts only [TOMATO_LEAF].
 */
enum class ScanTargetType {
    TOMATO_FRUIT,
    TOMATO_LEAF
}

fun ScanTargetType.modelLabel(): String = when (this) {
    ScanTargetType.TOMATO_FRUIT -> TargetDetectionLabels.TOMATO_FRUIT
    ScanTargetType.TOMATO_LEAF -> TargetDetectionLabels.TOMATO_LEAF
}

object TargetDetectionLabels {
    const val TOMATO_FRUIT = "tomato_fruit"
    const val TOMATO_LEAF = "tomato_leaf"

    fun fromModelLabel(label: String): ScanTargetType? {
        val normalized = label.trim().lowercase().replace(' ', '_')
        return when (normalized) {
            TOMATO_FRUIT, "tomato", "fruit" -> ScanTargetType.TOMATO_FRUIT
            TOMATO_LEAF, "leaf", "tomato_leaves" -> ScanTargetType.TOMATO_LEAF
            else -> null
        }
    }
}
