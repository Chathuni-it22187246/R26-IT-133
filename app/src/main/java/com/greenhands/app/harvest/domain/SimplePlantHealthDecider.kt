package com.greenhands.app.harvest.domain

import android.util.Log
import com.greenhands.app.harvest.detection.TomatoDiseaseLabels
import com.greenhands.app.harvest.detection.TomatoDiseasePrediction
import com.greenhands.app.harvest.model.LeafColorMeasurement
import com.greenhands.app.harvest.model.PlantHealthAssessment
import com.greenhands.app.harvest.model.PlantHealthReasons

/**
 * Harvesting leaf decision. HSV is the only source of HEALTHY / UNHEALTHY /
 * UNCERTAIN. The trained tomato disease classifier may add a Possible Disease
 * suggestion for UNHEALTHY leaves; it must never change HEALTHY to UNHEALTHY.
 *
 * Usable leaves use two wide HSV zones. Optional [previousStatus] hysteresis
 * applies only within one active leaf-scan camera session. A new Scan Leaf
 * visit must pass null so the previous leaf cannot affect this one.
 * UNCERTAIN is only for unusable or empty captures.
 */
object SimplePlantHealthDecider {
    const val TAG = "PlantHealthDecision"

    fun decide(
        measurement: LeafColorMeasurement?,
        previousStatus: PlantHealthStatus? = null,
        classifierAvailable: Boolean = false,
        prediction: TomatoDiseasePrediction? = null,
        roiReliable: Boolean = false,
        calibration: SimplePlantHealthCalibration = SimplePlantHealthCalibration.PROJECT,
        classifierCalibration: TomatoDiseaseClassificationCalibration =
            TomatoDiseaseClassificationCalibration.PROJECT
    ): PlantHealthAssessment {
        if (measurement == null || !measurement.hasSamples) {
            return finish(
                measurement = measurement,
                status = PlantHealthStatus.UNCERTAIN,
                usable = false,
                classifierAvailable = classifierAvailable,
                prediction = prediction,
                roiReliable = roiReliable,
                calibration = calibration,
                classifierCalibration = classifierCalibration,
                reason = "no_leaf_measurement"
            )
        }
        val usable = isUsableLeaf(measurement, calibration)
        if (!usable) {
            return finish(
                measurement = measurement,
                status = PlantHealthStatus.UNCERTAIN,
                usable = false,
                classifierAvailable = classifierAvailable,
                prediction = prediction,
                roiReliable = roiReliable,
                calibration = calibration,
                classifierCalibration = classifierCalibration,
                reason = unusableReason(measurement, calibration)
            )
        }
        val clearlyHealthy = isClearlyHealthy(measurement, calibration)
        val clearlyUnhealthy = isClearlyUnhealthy(measurement, calibration)
        val (status, reason) = when (previousStatus) {
            PlantHealthStatus.UNHEALTHY -> {
                if (clearlyHealthy) {
                    PlantHealthStatus.HEALTHY to "hysteresis_return_to_healthy"
                } else {
                    PlantHealthStatus.UNHEALTHY to "hysteresis_hold_unhealthy"
                }
            }
            else -> {
                if (clearlyUnhealthy) {
                    PlantHealthStatus.UNHEALTHY to "clear_abnormal_discoloration"
                } else {
                    PlantHealthStatus.HEALTHY to "usable_not_clearly_unhealthy"
                }
            }
        }
        return finish(
            measurement = measurement,
            status = status,
            usable = true,
            classifierAvailable = classifierAvailable,
            prediction = prediction,
            roiReliable = roiReliable,
            calibration = calibration,
            classifierCalibration = classifierCalibration,
            reason = reason
        )
    }

    fun isUsableLeaf(
        measurement: LeafColorMeasurement,
        calibration: SimplePlantHealthCalibration = SimplePlantHealthCalibration.PROJECT
    ): Boolean {
        if (measurement.sampledPixelCount < calibration.minSampledPixels) return false
        val leafColour = measurement.greenPercent +
            measurement.yellowPercent +
            measurement.brownDarkPercent +
            measurement.whitePalePercent
        if (leafColour < calibration.minLeafColourPercent) return false
        if (measurement.valueMean < calibration.minValueMean) return false
        if (measurement.valueMean > calibration.maxValueMean &&
            measurement.saturationMean < calibration.blownOutMaxSaturation
        ) {
            return false
        }
        return true
    }

    fun isClearlyHealthy(
        measurement: LeafColorMeasurement,
        calibration: SimplePlantHealthCalibration = SimplePlantHealthCalibration.PROJECT
    ): Boolean =
        measurement.discoloredPercent <= calibration.healthyDiscoloredMax &&
            measurement.yellowPercent <= calibration.healthyYellowMax &&
            measurement.brownDarkPercent <= calibration.healthyBrownMax &&
            measurement.whitePalePercent <= calibration.healthyPaleMax

    fun isClearlyUnhealthy(
        measurement: LeafColorMeasurement,
        calibration: SimplePlantHealthCalibration = SimplePlantHealthCalibration.PROJECT
    ): Boolean =
        measurement.discoloredPercent >= calibration.unhealthyDiscoloredMin ||
            measurement.yellowPercent >= calibration.unhealthyYellowMin ||
            measurement.brownDarkPercent >= calibration.unhealthyBrownMin ||
            measurement.whitePalePercent >= calibration.unhealthyPaleMin

    fun visibleIssueLabel(
        status: PlantHealthStatus,
        measurement: LeafColorMeasurement?,
        calibration: SimplePlantHealthCalibration = SimplePlantHealthCalibration.PROJECT
    ): String {
        if (status == PlantHealthStatus.HEALTHY) return PlantHealthReasons.VISIBLE_NONE
        if (status != PlantHealthStatus.UNHEALTHY || measurement == null) return ""
        val yellowHit = measurement.yellowPercent >= calibration.unhealthyYellowMin
        val brownHit = measurement.brownDarkPercent >= calibration.unhealthyBrownMin
        val paleHit = measurement.whitePalePercent >= calibration.unhealthyPaleMin
        val hits = listOf(yellowHit, brownHit, paleHit).count { it }
        if (hits >= 2) return PlantHealthReasons.VISIBLE_MIXED
        if (brownHit) return PlantHealthReasons.VISIBLE_DARK_BROWN
        if (yellowHit) return PlantHealthReasons.VISIBLE_YELLOWING
        if (paleHit) return PlantHealthReasons.VISIBLE_PALE
        val yellow = measurement.yellowPercent
        val brown = measurement.brownDarkPercent
        val pale = measurement.whitePalePercent
        val max = maxOf(yellow, brown, pale)
        if (max <= 0f) return PlantHealthReasons.VISIBLE_MIXED
        val tied = listOf(yellow == max, brown == max, pale == max).count { it } >= 2
        if (tied) return PlantHealthReasons.VISIBLE_MIXED
        return when (max) {
            brown -> PlantHealthReasons.VISIBLE_DARK_BROWN
            yellow -> PlantHealthReasons.VISIBLE_YELLOWING
            else -> PlantHealthReasons.VISIBLE_PALE
        }
    }

    fun possibleDiseaseLabel(
        status: PlantHealthStatus,
        prediction: TomatoDiseasePrediction?,
        roiReliable: Boolean,
        classifierCalibration: TomatoDiseaseClassificationCalibration =
            TomatoDiseaseClassificationCalibration.PROJECT
    ): Pair<String, Int?> {
        if (status == PlantHealthStatus.HEALTHY) {
            return PlantHealthReasons.POSSIBLE_NONE to null
        }
        if (status != PlantHealthStatus.UNHEALTHY) {
            return "" to null
        }
        if (!roiReliable || prediction == null) {
            return PlantHealthReasons.POSSIBLE_UNABLE to null
        }
        val healthyClass = prediction.isHealthyClass ||
            TomatoDiseaseLabels.isHealthy(prediction.rawClassName)
        if (healthyClass) {
            return PlantHealthReasons.POSSIBLE_UNABLE to null
        }
        val confidenceOk = prediction.meetsThreshold &&
            prediction.confidence >= classifierCalibration.confidenceThreshold
        if (!confidenceOk) {
            return PlantHealthReasons.POSSIBLE_UNABLE to null
        }
        val name = prediction.displayName.ifBlank {
            TomatoDiseaseLabels.displayName(prediction.rawClassName)
        }
        val percent = (prediction.confidence * 100f).toInt().coerceIn(0, 100)
        return name to percent
    }

    private fun unusableReason(
        measurement: LeafColorMeasurement,
        calibration: SimplePlantHealthCalibration
    ): String {
        if (measurement.sampledPixelCount < calibration.minSampledPixels) {
            return "too_few_sampled_pixels"
        }
        val leafColour = measurement.greenPercent +
            measurement.yellowPercent +
            measurement.brownDarkPercent +
            measurement.whitePalePercent
        if (leafColour < calibration.minLeafColourPercent) {
            return "insufficient_leaf_colour"
        }
        if (measurement.valueMean < calibration.minValueMean) return "underexposed"
        if (measurement.valueMean > calibration.maxValueMean &&
            measurement.saturationMean < calibration.blownOutMaxSaturation
        ) {
            return "overexposed"
        }
        return "unusable_leaf_image"
    }

    private fun finish(
        measurement: LeafColorMeasurement?,
        status: PlantHealthStatus,
        usable: Boolean,
        classifierAvailable: Boolean,
        prediction: TomatoDiseasePrediction?,
        roiReliable: Boolean,
        calibration: SimplePlantHealthCalibration,
        classifierCalibration: TomatoDiseaseClassificationCalibration,
        reason: String
    ): PlantHealthAssessment {
        val visible = visibleIssueLabel(status, measurement, calibration)
        val (disease, confidence) = possibleDiseaseLabel(
            status = status,
            prediction = prediction,
            roiReliable = roiReliable,
            classifierCalibration = classifierCalibration
        )
        val predictedName = prediction?.rawClassName ?: "n/a"
        val predictedConf = prediction?.let { fmt(it.confidence * 100f) } ?: "n/a"
        val line =
            "usableLeaf=$usable " +
                "green=${fmt(measurement?.greenPercent)} " +
                "yellow=${fmt(measurement?.yellowPercent)} " +
                "brownDark=${fmt(measurement?.brownDarkPercent)} " +
                "pale=${fmt(measurement?.whitePalePercent)} " +
                "discolored=${fmt(measurement?.discoloredPercent)} " +
                "classifierAvailable=$classifierAvailable " +
                "roiReliable=$roiReliable " +
                "classifierClass=$predictedName " +
                "classifierConfidence=$predictedConf " +
                "finalStatus=$status " +
                "possibleDisease=$disease " +
                "reason=$reason"
        try {
            Log.i(TAG, line)
        } catch (_: Throwable) {
        }
        val recommendation = when (status) {
            PlantHealthStatus.HEALTHY -> PlantHealthReasons.RECOMMEND_HEALTHY
            PlantHealthStatus.UNCERTAIN -> PlantHealthReasons.UNCERTAIN_SCAN_AGAIN
            else -> PlantHealthReasons.RECOMMEND_GENERIC
        }
        return PlantHealthAssessment(
            status = status,
            possibleDisease = disease.ifBlank {
                if (status == PlantHealthStatus.UNCERTAIN) "" else PlantHealthReasons.POSSIBLE_UNABLE
            },
            confidencePercent = confidence,
            matchedSymptoms = emptyList(),
            reasons = listOf(line),
            recommendation = recommendation,
            sourceReference = if (status == PlantHealthStatus.UNHEALTHY && confidence != null) {
                PlantHealthReasons.SOURCE_CLASSIFIER
            } else {
                null
            },
            diagnosisNote = if (status == PlantHealthStatus.HEALTHY || status == PlantHealthStatus.UNHEALTHY) {
                PlantHealthReasons.DISEASE_DISCLAIMER
            } else {
                null
            },
            scanRequired = false,
            leafMeasurement = measurement,
            visibleIssue = visible
        )
    }

    private fun fmt(value: Float?): String =
        if (value == null) "n/a" else String.format(java.util.Locale.US, "%.1f", value)
}
