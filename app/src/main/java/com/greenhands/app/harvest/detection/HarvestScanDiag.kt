package com.greenhands.app.harvest.detection

import android.util.Log
import com.greenhands.app.harvest.domain.MaturityAssessment
import com.greenhands.app.harvest.model.FruitColorMeasurement
import com.greenhands.app.harvest.model.HarvestDecisionResult
import com.greenhands.app.harvest.model.LeafColorMeasurement
import com.greenhands.app.harvest.model.PlantHealthAssessment

/**
 * Capture-time harvest lines plus rate-limited Crop Scan live timing.
 * Does not log calendar planting dates.
 */
object HarvestScanDiag {
    const val TAG = "HarvestScanDiag"

    const val LIVE_BOOTSTRAP_FRAMES = 3
    const val LIVE_INTERVAL_MS = 1000L
    const val LIVE_REASON_CHANGE_MIN_MS = 400L
    const val LIVE_FOCUS_MIN_MS = 250L

    @Volatile
    private var liveFrames: Int = 0
    @Volatile
    private var lastLiveLogAtMs: Long = 0L
    @Volatile
    private var lastReasonKey: String = ""

    fun boxText(box: NormalizedRect): String =
        String.format(
            java.util.Locale.US,
            "ltrb=%.3f,%.3f,%.3f,%.3f",
            box.left,
            box.top,
            box.right,
            box.bottom
        )

    fun fruitHsvText(measurement: FruitColorMeasurement?): String {
        if (measurement == null || !measurement.hasSamples) return "hsv=none"
        return String.format(
            java.util.Locale.US,
            "samples=%d g=%.1f y=%.1f r=%.1f brown=%.1f other=%.1f vMean=%.3f gV=%s",
            measurement.sampledPixelCount,
            measurement.greenPercent,
            measurement.yellowPercent,
            measurement.redPercent,
            measurement.brownDarkPercent,
            measurement.otherPercent,
            measurement.valueMean,
            measurement.greenValueMean?.let { String.format(java.util.Locale.US, "%.3f", it) } ?: "n/a"
        )
    }

    fun leafHsvText(measurement: LeafColorMeasurement?): String {
        if (measurement == null || !measurement.hasSamples) return "hsv=none"
        return String.format(
            java.util.Locale.US,
            "samples=%d g=%.1f y=%.1f brown=%.1f pale=%.1f disc=%.1f other=%.1f vMean=%.3f",
            measurement.sampledPixelCount,
            measurement.greenPercent,
            measurement.yellowPercent,
            measurement.brownDarkPercent,
            measurement.whitePalePercent,
            measurement.discoloredPercent,
            measurement.otherPercent,
            measurement.valueMean
        )
    }

    fun maturityText(maturity: MaturityAssessment, daysSincePlanting: Int?): String =
        "timing=${maturity.timing} daysSince=${daysSincePlanting ?: "n/a"} " +
            "window=${maturity.expectedMinDays}-${maturity.expectedMaxDays}"

    fun singleFruitLine(
        rotationDegrees: Int,
        frameWidth: Int,
        frameHeight: Int,
        detection: TargetDetection?,
        measurement: FruitColorMeasurement?,
        result: HarvestDecisionResult,
        daysSincePlanting: Int?
    ): String {
        val box = detection?.boundingBox
        return "mode=SINGLE_FRUIT " +
            "frame=${frameWidth}x${frameHeight} rot=$rotationDegrees " +
            "conf=${detection?.let { String.format(java.util.Locale.US, "%.3f", it.confidence) } ?: "n/a"} " +
            "box=${box?.let { boxText(it) } ?: "n/a"} " +
            "${fruitHsvText(measurement)} " +
            "ripeness=${result.ripeness.state} " +
            "${maturityText(result.maturity, daysSincePlanting)} " +
            "decision=${result.decision} reason=${result.harvestReason}"
    }

    fun clusterLine(
        rotationDegrees: Int,
        frameWidth: Int,
        frameHeight: Int,
        result: MultiTomatoClusterResult,
        daysSincePlanting: Int?
    ): String {
        val fruits = result.fruits.sortedBy { it.displayId }
        val parts = fruits.joinToString(" | ") { fruit ->
            "id=${fruit.displayId} conf=${String.format(java.util.Locale.US, "%.3f", fruit.detection.confidence)} " +
                "${boxText(fruit.detection.boundingBox)} " +
                "${fruitHsvText(fruit.measurement)} " +
                "ripeness=${fruit.harvest.ripeness.state} " +
                "decision=${fruit.harvest.decision} reason=${fruit.harvest.harvestReason}"
        }
        val maturity = fruits.firstOrNull()?.harvest?.maturity
        val maturityPart = if (maturity != null) {
            maturityText(maturity, daysSincePlanting)
        } else {
            "timing=n/a daysSince=${daysSincePlanting ?: "n/a"}"
        }
        return "mode=CLUSTER frame=${frameWidth}x${frameHeight} rot=$rotationDegrees " +
            "count=${result.fruitCount} rejected=${result.rejectedCount} " +
            "suppressed=${result.suppressedDuplicateCount} $maturityPart fruits=[$parts]"
    }

    fun leafLine(
        rotationDegrees: Int,
        frameWidth: Int,
        frameHeight: Int,
        detection: TargetDetection?,
        measurement: LeafColorMeasurement?,
        assessment: PlantHealthAssessment
    ): String {
        val box = detection?.boundingBox
        val reason = assessment.reasons.lastOrNull()
            ?.substringAfter("reason=", missingDelimiterValue = "")
            ?.ifBlank { "n/a" }
            ?: "n/a"
        return "mode=LEAF " +
            "frame=${frameWidth}x${frameHeight} rot=$rotationDegrees " +
            "conf=${detection?.let { String.format(java.util.Locale.US, "%.3f", it.confidence) } ?: "n/a"} " +
            "box=${box?.let { boxText(it) } ?: "n/a"} " +
            "${leafHsvText(measurement)} " +
            "status=${assessment.status} issue=${assessment.visibleIssue} " +
            "decisionReason=$reason"
    }

    fun liveLine(sample: HarvestScanLiveSample): String {
        val mode = if (sample.cluster) "CLUSTER_LIVE" else "SINGLE_LIVE"
        val conf = fmtScore(sample.selectedConfidence)
        val area = sample.selectedArea?.let {
            String.format(java.util.Locale.US, "%.3f", it)
        } ?: "n/a"
        val fps = String.format(java.util.Locale.US, "%.1f", sample.fps)
        val layout = when (sample.layout) {
            YoloTensorLayout.CHANNEL_MAJOR -> "CH"
            YoloTensorLayout.PRED_MAJOR -> "PRED"
            null -> "n/a"
        }
        val sel = when (sample.selectedType) {
            ScanTargetType.TOMATO_FRUIT -> "FRUIT"
            ScanTargetType.TOMATO_LEAF -> "LEAF"
            null -> "NONE"
        }
        return "mode=$mode convertMs=${sample.convertMs} preMs=${sample.preprocessMs} " +
            "inferMs=${sample.inferMs} parseMs=${sample.parseMs} totalMs=${sample.totalMs} " +
            "fps=$fps rawFruit=${sample.rawFruitCount} rawLeaf=${sample.rawLeafCount} " +
            "acceptedFruit=${sample.acceptedFruitCount} " +
            "layout=$layout fromShape=${if (sample.layoutFromShape) 1 else 0} " +
            "sel=$sel class=${sample.selectedClassIndex ?: "n/a"} score=$conf " +
            "c0=${fmtScore(sample.class0Score)} c1=${fmtScore(sample.class1Score)} " +
            "c0Raw=${fmtScore(sample.rawMax0)} c1Raw=${fmtScore(sample.rawMax1)} " +
            "sigmoid=${if (sample.sigmoidApplied) 1 else 0} " +
            "area=$area reason=${sample.reason ?: "none"} streak=${sample.streak} " +
            "focus=${if (sample.focusRequested) 1 else 0} " +
            "capture=${if (sample.autoCapture) 1 else 0} " +
            "cached=${if (sample.layoutCached) 1 else 0}" +
            fruitStabilitySuffix(sample)
    }

    fun fruitStabilitySuffix(sample: HarvestScanLiveSample): String {
        if (sample.cluster) return ""
        val box = sample.fruitBox?.let { boxText(it) } ?: "n/a"
        val iou = sample.fruitIou?.let {
            String.format(java.util.Locale.US, "%.3f", it)
        } ?: "n/a"
        val dx = fmtSigned(sample.fruitDx)
        val dy = fmtSigned(sample.fruitDy)
        val fruitArea = sample.fruitArea?.let {
            String.format(java.util.Locale.US, "%.3f", it)
        } ?: "n/a"
        return " fruitSel=${fmtScore(sample.fruitSelConf)} fruitMax=${fmtScore(sample.fruitMaxConf)} " +
            "leafMax=${fmtScore(sample.leafMaxConf)} " +
            "fruitBox=$box fruitArea=$fruitArea dx=$dx dy=$dy iou=$iou " +
            "reset=${sample.stabilityResetReason ?: "n/a"} " +
            "gateConf=${fmtGate(sample.gateConf)} gateSize=${fmtGate(sample.gateSize)} " +
            "gateCenter=${fmtGate(sample.gateCenter)} gateStable=${fmtGate(sample.gateStable)} " +
            "nmsDrop=${sample.nmsDropped ?: "n/a"} " +
            "preNmsF=${sample.preNmsFruit ?: "n/a"} preNmsL=${sample.preNmsLeaf ?: "n/a"} " +
            "selWhy=${sample.selWhy ?: "n/a"} overlay=${sample.overlayState ?: "n/a"} " +
            "top=${sample.topFruits ?: "none"}"
    }

    fun fmtSigned(value: Float?): String {
        if (value == null || value.isNaN()) return "n/a"
        return String.format(java.util.Locale.US, "%+.3f", value)
    }

    fun fmtGate(value: Boolean?): String = when (value) {
        true -> "1"
        false -> "0"
        null -> "n/a"
    }

    fun fmtScore(value: Float?): String {
        if (value == null || value.isNaN()) return "n/a"
        return String.format(java.util.Locale.US, "%.3f", value)
    }

    fun tensorLine(
        outputShape: IntArray,
        classCount: Int,
        layout: YoloTensorLayout,
        fromShape: Boolean
    ): String = "mode=YOLO_TENSOR shape=${outputShape.contentToString()} " +
        "classes=$classCount layout=${if (layout == YoloTensorLayout.CHANNEL_MAJOR) "CH" else "PRED"} " +
        "fromShape=${if (fromShape) 1 else 0}"

    fun scoreRangeLine(
        sigmoidApplied: Boolean,
        rawMax0: Float,
        rawMax1: Float,
        interpMax0: Float,
        interpMax1: Float,
        boxMin: Float,
        boxMax: Float
    ): String = "mode=YOLO_SCORES sigmoid=${if (sigmoidApplied) 1 else 0} " +
        "rawMax0=${fmtScore(rawMax0)} rawMax1=${fmtScore(rawMax1)} " +
        "interpMax0=${fmtScore(interpMax0)} interpMax1=${fmtScore(interpMax1)} " +
        "boxMin=${fmtScore(boxMin)} boxMax=${fmtScore(boxMax)}"

    fun shouldEmitLive(
        frameIndex: Int,
        nowMs: Long,
        lastLogAtMs: Long,
        autoCapture: Boolean,
        focusRequested: Boolean,
        reasonChanged: Boolean
    ): Boolean {
        if (autoCapture) return true
        if (frameIndex <= LIVE_BOOTSTRAP_FRAMES) return true
        val elapsed = nowMs - lastLogAtMs
        if (focusRequested && elapsed >= LIVE_FOCUS_MIN_MS) return true
        if (reasonChanged && elapsed >= LIVE_REASON_CHANGE_MIN_MS) return true
        return elapsed >= LIVE_INTERVAL_MS
    }

    fun resetLiveSession() {
        liveFrames = 0
        lastLiveLogAtMs = 0L
        lastReasonKey = ""
        HarvestFruitStabilityDiag.reset()
    }

    fun maybeLogCropLive(nowMs: Long, sample: HarvestScanLiveSample) {
        val nextIndex = liveFrames + 1
        val reasonKey =
            "${sample.cluster}|${sample.reason}|${sample.autoCapture}|${sample.stabilityResetReason}|${sample.selWhy}|${sample.overlayState}"
        val reasonChanged = reasonKey != lastReasonKey
        if (!shouldEmitLive(
                frameIndex = nextIndex,
                nowMs = nowMs,
                lastLogAtMs = lastLiveLogAtMs,
                autoCapture = sample.autoCapture,
                focusRequested = sample.focusRequested,
                reasonChanged = reasonChanged
            )
        ) {
            liveFrames = nextIndex
            return
        }
        liveFrames = nextIndex
        lastLiveLogAtMs = nowMs
        lastReasonKey = reasonKey
        log(liveLine(sample))
    }

    fun log(line: String) {
        try {
            Log.i(TAG, line)
        } catch (_: Throwable) {
        }
    }
}
