package com.greenhands.app.harvest.detection

/**
 * Read-only Single Tomato Scan candidate diagnostics. Does not change
 * scores, NMS, selection, overlay, or capture.
 *
 * Compact tags:
 * CLAMP = opposite-edge clamped (Stage B4 reject)
 * LOW / SMALL / OFF = existing lock gates
 * OK = lock-ready geometry + confidence
 */
object SingleTomatoCandidateDiag {
    const val WHY_LOCK_READY = "LOCK_READY"
    const val WHY_PLAUSIBLE = "PLAUSIBLE"
    const val WHY_NONE = "NONE"
    const val OVERLAY_SEL = "SEL"
    const val OVERLAY_NONE = "NONE"
    const val OVERLAY_MISMATCH = "MISMATCH"

    fun classify(
        detection: TargetDetection,
        calibration: TargetDetectionCalibration = TargetDetectionCalibration.PROJECT
    ): String {
        val box = detection.boundingBox
        if (SingleTomatoFruitSelector.isSeverelyClamped(box)) return "CLAMP"
        val tags = ArrayList<String>(3)
        if (detection.confidence < calibration.minDetectionConfidence) tags += "LOW"
        if (box.area < calibration.minTargetAreaRatio) tags += "SMALL"
        if (!SingleTomatoFruitSelector.isCentered(box, calibration)) tags += "OFF"
        return if (tags.isEmpty()) "OK" else tags.joinToString("+")
    }

    fun observe(
        detections: List<TargetDetection>,
        selected: TargetDetection?,
        calibration: TargetDetectionCalibration = TargetDetectionCalibration.PROJECT
    ): SingleTomatoCandidateSnapshot {
        val fruits = detections.filter { it.targetType == ScanTargetType.TOMATO_FRUIT }
            .sortedByDescending { it.confidence }
        val leaves = detections.filter { it.targetType == ScanTargetType.TOMATO_LEAF }
        val plausible = SingleTomatoFruitSelector.plausibleFruits(detections)
        val overlay = SingleTomatoFruitSelector.overlayBox(selected)
        val selectedFruit = selected?.takeIf { it.targetType == ScanTargetType.TOMATO_FRUIT }
        val selWhy = when {
            selectedFruit == null -> WHY_NONE
            selectedFruit.confidence >= calibration.minDetectionConfidence &&
                selectedFruit.boundingBox.area >= calibration.minTargetAreaRatio &&
                SingleTomatoFruitSelector.isCentered(selectedFruit.boundingBox, calibration) &&
                SingleTomatoFruitSelector.isPlausibleFruitBox(selectedFruit.boundingBox) -> WHY_LOCK_READY
            else -> WHY_PLAUSIBLE
        }
        val overlayState = when {
            overlay == null -> OVERLAY_NONE
            selectedFruit != null && overlay == selectedFruit.boundingBox -> OVERLAY_SEL
            else -> OVERLAY_MISMATCH
        }
        val top = fruits.take(3).map { fruit ->
            CompactFruitCandidate(
                confidence = fruit.confidence,
                area = fruit.boundingBox.area,
                dx = fruit.boundingBox.centerX - 0.5f,
                dy = fruit.boundingBox.centerY - 0.5f,
                box = fruit.boundingBox,
                tag = classify(fruit, calibration)
            )
        }
        return SingleTomatoCandidateSnapshot(
            leafMaxConf = leaves.maxByOrNull { it.confidence }?.confidence,
            fruitMaxConf = fruits.maxByOrNull { it.confidence }?.confidence,
            selWhy = selWhy,
            overlayState = overlayState,
            overlayBox = overlay,
            topFruits = top,
            clampedFruitCount = fruits.count { SingleTomatoFruitSelector.isSeverelyClamped(it.boundingBox) },
            plausibleFruitCount = plausible.size
        )
    }

    fun compactTop(top: List<CompactFruitCandidate>): String {
        if (top.isEmpty()) return "none"
        return top.joinToString(" ") { c ->
            String.format(
                java.util.Locale.US,
                "%.2f/%.3f/%+.2f/%+.2f/%s",
                c.confidence,
                c.area,
                c.dx,
                c.dy,
                c.tag
            )
        }
    }
}

data class CompactFruitCandidate(
    val confidence: Float,
    val area: Float,
    val dx: Float,
    val dy: Float,
    val box: NormalizedRect,
    val tag: String
)

data class SingleTomatoCandidateSnapshot(
    val leafMaxConf: Float?,
    val fruitMaxConf: Float?,
    val selWhy: String,
    val overlayState: String,
    val overlayBox: NormalizedRect?,
    val topFruits: List<CompactFruitCandidate>,
    val clampedFruitCount: Int,
    val plausibleFruitCount: Int
) {
    companion object {
        val EMPTY = SingleTomatoCandidateSnapshot(
            leafMaxConf = null,
            fruitMaxConf = null,
            selWhy = SingleTomatoCandidateDiag.WHY_NONE,
            overlayState = SingleTomatoCandidateDiag.OVERLAY_NONE,
            overlayBox = null,
            topFruits = emptyList(),
            clampedFruitCount = 0,
            plausibleFruitCount = 0
        )
    }
}
