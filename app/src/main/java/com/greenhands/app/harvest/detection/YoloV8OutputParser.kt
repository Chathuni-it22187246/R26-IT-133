package com.greenhands.app.harvest.detection

/**
 * Parses Ultralytics YOLOv8 TFLite output.
 *
 * Trained classes:
 * 0 = tomato_fruit
 * 1 = tomato_leaf
 *
 * Channels-first layout used by this project's export: [1, 6, 3549]
 * where the 6 channels are cx, cy, w, h, class0, class1.
 * Coordinates are xywh in the letterboxed input (pixels, or 0–1).
 */
object YoloV8OutputParser {
    const val BOX_CHANNELS = 4
    const val CLASS_TOMATO_FRUIT = 0
    const val CLASS_TOMATO_LEAF = 1
    const val MAX_KEEP = 20

    fun parseChannelsFirst(
        channels: Array<FloatArray>,
        inputWidth: Int,
        inputHeight: Int,
        transform: LetterboxTransform,
        calibration: TargetDetectionCalibration = TargetDetectionCalibration.PROJECT,
        timestampMs: Long,
        labels: List<String> = listOf(
            TargetDetectionLabels.TOMATO_FRUIT,
            TargetDetectionLabels.TOMATO_LEAF
        ),
        scoreFloor: Float = calibration.detectorScoreFloor,
        applySigmoidIfLogits: Boolean = true
    ): List<TargetDetection> {
        if (channels.size < BOX_CHANNELS + 1 || channels[0].isEmpty()) return emptyList()
        val activated = if (applySigmoidIfLogits) activatedClassChannels(channels) else channels
        val preds = activated[0].size
        val candidates = ArrayList<TargetDetection>(32)
        for (i in 0 until preds) {
            val scored = bestClass(activated, i) ?: continue
            if (scored.score < scoreFloor) continue
            val type = classIndexToType(scored.classIndex, labels) ?: continue
            val box = xywhToSourceNormalized(
                cx = activated[0][i],
                cy = activated[1][i],
                w = activated[2][i],
                h = activated[3][i],
                inputWidth = inputWidth,
                inputHeight = inputHeight,
                transform = transform
            )
            if (box.area <= 0f) continue
            candidates.add(
                TargetDetection(
                    targetType = type,
                    confidence = scored.score,
                    boundingBox = box,
                    timestampMs = timestampMs
                )
            )
        }
        return nms(candidates, calibration.nmsIouThreshold)
    }

    fun parseDetectionsFirst(
        rows: Array<FloatArray>,
        inputWidth: Int,
        inputHeight: Int,
        transform: LetterboxTransform,
        calibration: TargetDetectionCalibration = TargetDetectionCalibration.PROJECT,
        timestampMs: Long,
        labels: List<String> = listOf(
            TargetDetectionLabels.TOMATO_FRUIT,
            TargetDetectionLabels.TOMATO_LEAF
        )
    ): List<TargetDetection> {
        if (rows.isEmpty()) return emptyList()
        val attrs = rows[0].size
        if (attrs < BOX_CHANNELS + 1) return emptyList()
        val channels = Array(attrs) { c -> FloatArray(rows.size) { i -> rows[i][c] } }
        return parseChannelsFirst(
            channels = channels,
            inputWidth = inputWidth,
            inputHeight = inputHeight,
            transform = transform,
            calibration = calibration,
            timestampMs = timestampMs,
            labels = labels,
            scoreFloor = calibration.detectorScoreFloor
        )
    }

    fun nms(
        detections: List<TargetDetection>,
        iouThreshold: Float,
        maxKeep: Int = MAX_KEEP
    ): List<TargetDetection> {
        val kept = ArrayList<TargetDetection>(minOf(maxKeep, detections.size))
        detections.groupBy { it.targetType }.forEach { (_, group) ->
            val sorted = group.sortedByDescending { it.confidence }
            val suppressed = BooleanArray(sorted.size)
            for (i in sorted.indices) {
                if (suppressed[i]) continue
                kept.add(sorted[i])
                if (kept.size >= maxKeep) return kept.sortedByDescending { it.confidence }
                for (j in i + 1 until sorted.size) {
                    if (!suppressed[j] && sorted[i].boundingBox.iou(sorted[j].boundingBox) > iouThreshold) {
                        suppressed[j] = true
                    }
                }
            }
        }
        return kept.sortedByDescending { it.confidence }
    }

    fun classIndexToType(index: Int, labels: List<String>): ScanTargetType? {
        if (index in labels.indices) {
            TargetDetectionLabels.fromModelLabel(labels[index])?.let { return it }
        }
        return when (index) {
            CLASS_TOMATO_FRUIT -> ScanTargetType.TOMATO_FRUIT
            CLASS_TOMATO_LEAF -> ScanTargetType.TOMATO_LEAF
            else -> null
        }
    }

    internal fun xywhToSourceNormalized(
        cx: Float,
        cy: Float,
        w: Float,
        h: Float,
        inputWidth: Int,
        inputHeight: Int,
        transform: LetterboxTransform
    ): NormalizedRect {
        val pixelCoords = maxOf(kotlin.math.abs(cx), kotlin.math.abs(cy), kotlin.math.abs(w), kotlin.math.abs(h)) > 1.5f
        val px = if (pixelCoords) cx else cx * inputWidth
        val py = if (pixelCoords) cy else cy * inputHeight
        val pw = if (pixelCoords) w else w * inputWidth
        val ph = if (pixelCoords) h else h * inputHeight
        return transform.toSourceNormalized(
            x1Input = px - pw / 2f,
            y1Input = py - ph / 2f,
            x2Input = px + pw / 2f,
            y2Input = py + ph / 2f
        )
    }

    fun channelsFromFlat(
        floats: FloatArray,
        channels: Int,
        predictions: Int,
        channelMajor: Boolean
    ): Array<FloatArray> {
        val out = Array(channels) { FloatArray(predictions) }
        for (c in 0 until channels) {
            for (i in 0 until predictions) {
                val index = if (channelMajor) {
                    c * predictions + i
                } else {
                    i * channels + c
                }
                if (index in floats.indices) {
                    out[c][i] = floats[index]
                }
            }
        }
        return out
    }

    fun maxClassScore(channels: Array<FloatArray>, classOffset: Int): Float {
        val plane = channels.getOrNull(BOX_CHANNELS + classOffset) ?: return Float.NEGATIVE_INFINITY
        var max = Float.NEGATIVE_INFINITY
        for (v in plane) if (v > max) max = v
        return max
    }

    fun minClassScore(channels: Array<FloatArray>, classOffset: Int): Float {
        val plane = channels.getOrNull(BOX_CHANNELS + classOffset) ?: return Float.POSITIVE_INFINITY
        var min = Float.POSITIVE_INFINITY
        for (v in plane) if (v < min) min = v
        return min
    }

    fun boxRange(channels: Array<FloatArray>): Pair<Float, Float> {
        var min = Float.POSITIVE_INFINITY
        var max = Float.NEGATIVE_INFINITY
        val limit = minOf(BOX_CHANNELS, channels.size)
        for (c in 0 until limit) {
            for (v in channels[c]) {
                if (v < min) min = v
                if (v > max) max = v
            }
        }
        return min to max
    }

    fun topPredictions(
        channels: Array<FloatArray>,
        count: Int = 5
    ): List<RawPrediction> {
        if (channels.size < BOX_CHANNELS + 1) return emptyList()
        val preds = channels[0].size
        val scored = ArrayList<RawPrediction>(preds)
        for (i in 0 until preds) {
            val c0 = channels.getOrNull(4)?.getOrNull(i) ?: 0f
            val c1 = channels.getOrNull(5)?.getOrNull(i) ?: 0f
            val best = if (c0 >= c1) 0 to c0 else 1 to c1
            scored.add(
                RawPrediction(
                    index = i,
                    cx = channels[0][i],
                    cy = channels[1][i],
                    w = channels[2][i],
                    h = channels[3][i],
                    class0 = c0,
                    class1 = c1,
                    bestClass = best.first,
                    bestScore = best.second
                )
            )
        }
        return scored.sortedByDescending { it.bestScore }.take(count)
    }

    data class RawPrediction(
        val index: Int,
        val cx: Float,
        val cy: Float,
        val w: Float,
        val h: Float,
        val class0: Float,
        val class1: Float,
        val bestClass: Int,
        val bestScore: Float
    )

    internal fun activatedClassChannels(channels: Array<FloatArray>): Array<FloatArray> {
        if (channels.size <= BOX_CHANNELS) return channels
        var min = Float.POSITIVE_INFINITY
        var max = Float.NEGATIVE_INFINITY
        for (c in BOX_CHANNELS until channels.size) {
            for (v in channels[c]) {
                if (v < min) min = v
                if (v > max) max = v
            }
        }
        if (!TomatoYoloDiagnostics.looksLikeLogits(min, max)) return channels
        return Array(channels.size) { c ->
            if (c < BOX_CHANNELS) channels[c]
            else FloatArray(channels[c].size) { i -> TomatoYoloDiagnostics.sigmoid(channels[c][i]) }
        }
    }

    private fun bestClass(channels: Array<FloatArray>, index: Int): ClassScore? {
        val classChannels = channels.size - BOX_CHANNELS
        if (classChannels <= 0) return null
        var bestIndex = 0
        var bestScore = channels[BOX_CHANNELS][index]
        for (c in 1 until classChannels) {
            val score = channels[BOX_CHANNELS + c][index]
            if (score > bestScore) {
                bestScore = score
                bestIndex = c
            }
        }
        return ClassScore(bestIndex, bestScore)
    }

    private data class ClassScore(val classIndex: Int, val score: Float)
}
