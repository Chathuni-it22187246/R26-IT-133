package com.greenhands.app.harvest.domain

import android.util.Log
import com.greenhands.app.harvest.data.DiseaseClassReference
import com.greenhands.app.harvest.detection.TomatoDiseaseDebug
import com.greenhands.app.harvest.detection.TomatoDiseaseLabels
import com.greenhands.app.harvest.detection.TomatoDiseasePrediction
import com.greenhands.app.harvest.model.LeafColorMeasurement
import com.greenhands.app.harvest.model.PlantHealthAssessment
import com.greenhands.app.harvest.model.PlantHealthReasons
import com.greenhands.app.harvest.model.hsvPercentLabel

enum class ClassifierHsvAgreement {
    AGREE_HEALTHY,
    AGREE_UNHEALTHY,
    DISAGREE,
    INSUFFICIENT
}

/**
 * Maps a classifier prediction plus supplemental HSV into [PlantHealthAssessment].
 * DiseaseMatcher must not override this result. HSV never invents a disease name.
 */
object LeafHealthClassifierAssessor {
    fun fromClassifier(
        measurement: LeafColorMeasurement,
        prediction: TomatoDiseasePrediction,
        reference: DiseaseClassReference,
        calibration: TomatoDiseaseClassificationCalibration =
            TomatoDiseaseClassificationCalibration.PROJECT,
        hsvCalibration: TomatoLeafHealthCalibration = TomatoLeafHealthCalibration.PROJECT,
        roiReliable: Boolean = true
    ): PlantHealthAssessment {
        val percent = (prediction.confidence * 100f).toInt().coerceIn(0, 100)
        val healthyClass = prediction.isHealthyClass ||
            TomatoDiseaseLabels.isHealthy(prediction.rawClassName)
        val hsvAbnormal = hsvShowsAbnormalDiscoloration(measurement, hsvCalibration)
        val hsvHealthy = hsvLooksPredominantlyHealthy(measurement, hsvCalibration)
        val margin = top1Top2Margin(prediction)
        val confidenceOk = prediction.meetsThreshold &&
            prediction.confidence >= calibration.confidenceThreshold
        val marginOk = margin >= calibration.minTop1Top2Margin

        val agreement: ClassifierHsvAgreement
        val status: PlantHealthStatus
        val possibleDisease: String
        val extraReason: String
        if (!roiReliable || !confidenceOk) {
            status = PlantHealthStatus.UNCERTAIN
            possibleDisease = PlantHealthReasons.UNCERTAIN_DISEASE
            extraReason = PlantHealthReasons.CLASSIFIER_BELOW_THRESHOLD
            agreement = ClassifierHsvAgreement.INSUFFICIENT
        } else if (healthyClass) {
            status = PlantHealthStatus.HEALTHY
            possibleDisease = PlantHealthReasons.NONE
            extraReason = PlantHealthReasons.MOSTLY_GREEN
            agreement = when {
                hsvHealthy -> ClassifierHsvAgreement.AGREE_HEALTHY
                hsvAbnormal -> ClassifierHsvAgreement.DISAGREE
                else -> ClassifierHsvAgreement.INSUFFICIENT
            }
        } else {
            possibleDisease = reference.displayName
            val diseaseUnhealthy = marginOk && hsvAbnormal
            if (diseaseUnhealthy) {
                status = PlantHealthStatus.UNHEALTHY
                extraReason = "Classifier top class is ${reference.displayName}."
                agreement = ClassifierHsvAgreement.AGREE_UNHEALTHY
            } else {
                status = PlantHealthStatus.UNCERTAIN
                extraReason = when {
                    !marginOk -> PlantHealthReasons.CLASSIFIER_MARGIN_TOO_SMALL
                    hsvHealthy -> PlantHealthReasons.CLASSIFIER_HSV_DISAGREE
                    else -> PlantHealthReasons.CLASSIFIER_HSV_INSUFFICIENT
                }
                agreement = when {
                    !marginOk -> ClassifierHsvAgreement.INSUFFICIENT
                    hsvHealthy -> ClassifierHsvAgreement.DISAGREE
                    else -> ClassifierHsvAgreement.INSUFFICIENT
                }
            }
        }
        val debug = fusionDebugLine(
            prediction = prediction,
            margin = margin,
            roiReliable = roiReliable,
            hsvAbnormal = hsvAbnormal,
            measurement = measurement,
            agreement = agreement,
            status = status
        )
        logFusion(debug)
        val symptoms = if (status == PlantHealthStatus.UNHEALTHY) {
            reference.documentedSymptoms
        } else {
            emptyList()
        }
        val recommendation = when (status) {
            PlantHealthStatus.HEALTHY -> PlantHealthReasons.RECOMMEND_HEALTHY
            PlantHealthStatus.UNCERTAIN -> PlantHealthReasons.RECOMMEND_GENERIC
            else -> reference.recommendation
        }
        return PlantHealthAssessment(
            status = status,
            possibleDisease = possibleDisease,
            confidencePercent = percent,
            matchedSymptoms = symptoms,
            reasons = listOf(
                extraReason,
                PlantHealthReasons.HSV_SUPPLEMENTAL,
                hsvSummary(measurement),
                debug
            ),
            recommendation = recommendation,
            sourceReference = reference.sourceNote,
            diagnosisNote = null,
            scanRequired = false,
            leafMeasurement = measurement
        )
    }

    fun unreliableRoi(measurement: LeafColorMeasurement): PlantHealthAssessment {
        val hsvAbnormal = hsvShowsAbnormalDiscoloration(
            measurement,
            TomatoLeafHealthCalibration.PROJECT
        )
        val debug = "classifierTop1=n/a classifierTop2=n/a margin=n/a " +
            "roiReliable=false hsvAbnormal=$hsvAbnormal " +
            "hsvGreen=${fmt(measurement.greenPercent)} " +
            "hsvDiscolored=${fmt(measurement.discoloredPercent)} " +
            "fusionAgreement=${ClassifierHsvAgreement.INSUFFICIENT} " +
            "finalStatus=${PlantHealthStatus.UNCERTAIN}"
        logFusion(debug)
        return PlantHealthAssessment(
            status = PlantHealthStatus.UNCERTAIN,
            possibleDisease = PlantHealthReasons.UNCERTAIN_DISEASE,
            confidencePercent = null,
            matchedSymptoms = emptyList(),
            reasons = listOf(
                PlantHealthReasons.CLASSIFIER_ROI_UNRELIABLE,
                PlantHealthReasons.HSV_SUPPLEMENTAL,
                hsvSummary(measurement),
                debug
            ),
            recommendation = PlantHealthReasons.RECOMMEND_GENERIC,
            sourceReference = PlantHealthReasons.SOURCE_CLASSIFIER,
            diagnosisNote = null,
            scanRequired = false,
            leafMeasurement = measurement
        )
    }

    fun hsvShowsAbnormalDiscoloration(
        measurement: LeafColorMeasurement,
        calibration: TomatoLeafHealthCalibration = TomatoLeafHealthCalibration.PROJECT
    ): Boolean =
        measurement.discoloredPercent >= calibration.warningDiscolorationMinPercent ||
            measurement.yellowPercent >= calibration.warningYellowMinPercent ||
            measurement.brownDarkPercent >= calibration.warningBrownDarkMinPercent ||
            measurement.whitePalePercent >= calibration.warningWhiteMinPercent

    fun hsvLooksPredominantlyHealthy(
        measurement: LeafColorMeasurement,
        calibration: TomatoLeafHealthCalibration = TomatoLeafHealthCalibration.PROJECT
    ): Boolean =
        measurement.greenPercent >= calibration.healthyGreenMinPercent &&
            measurement.discoloredPercent <= calibration.healthyDiscolorationMaxPercent &&
            measurement.yellowPercent <= calibration.healthyYellowMaxPercent &&
            measurement.brownDarkPercent <= calibration.healthyBrownDarkMaxPercent &&
            measurement.whitePalePercent <= calibration.healthyWhiteMaxPercent

    fun top1Top2Margin(prediction: TomatoDiseasePrediction): Float {
        val ranked = prediction.topPredictions.sortedByDescending { it.confidence }
        val top1 = ranked.getOrNull(0)?.confidence ?: prediction.confidence
        val top2 = ranked.getOrNull(1)?.confidence ?: 0f
        return (top1 - top2).coerceAtLeast(0f)
    }

    fun fusionDebugFrom(assessment: PlantHealthAssessment): String? =
        assessment.reasons.firstOrNull { it.startsWith("classifierTop1=") }

    private fun fusionDebugLine(
        prediction: TomatoDiseasePrediction,
        margin: Float,
        roiReliable: Boolean,
        hsvAbnormal: Boolean,
        measurement: LeafColorMeasurement,
        agreement: ClassifierHsvAgreement,
        status: PlantHealthStatus
    ): String {
        val ranked = prediction.topPredictions.sortedByDescending { it.confidence }
        val top1 = ranked.getOrNull(0)
        val top2 = ranked.getOrNull(1)
        val top1Text = top1?.let {
            "${it.rawClassName} ${fmt(it.confidence)}"
        } ?: "${prediction.rawClassName} ${fmt(prediction.confidence)}"
        val top2Text = top2?.let { "${it.rawClassName} ${fmt(it.confidence)}" } ?: "n/a"
        return "classifierTop1=$top1Text classifierTop2=$top2Text " +
            "margin=${fmt(margin)} roiReliable=$roiReliable hsvAbnormal=$hsvAbnormal " +
            "hsvGreen=${fmt(measurement.greenPercent)} " +
            "hsvDiscolored=${fmt(measurement.discoloredPercent)} " +
            "fusionAgreement=$agreement finalStatus=$status"
    }

    private fun logFusion(debug: String) {
        try {
            Log.i(TomatoDiseaseDebug.TAG, debug)
        } catch (_: Throwable) {
        }
    }

    private fun fmt(value: Float): String = String.format(java.util.Locale.US, "%.2f", value)

    private fun hsvSummary(measurement: LeafColorMeasurement): String =
        "Supplemental HSV: green ${hsvPercentLabel(measurement.greenPercent)}, " +
            "yellow ${hsvPercentLabel(measurement.yellowPercent)}, " +
            "brown/dark ${hsvPercentLabel(measurement.brownDarkPercent)}."
}
