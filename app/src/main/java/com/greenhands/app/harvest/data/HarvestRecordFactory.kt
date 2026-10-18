package com.greenhands.app.harvest.data

import com.greenhands.app.environment.GreenhouseConnectionState
import com.greenhands.app.environment.GreenhouseEnvironmentSnapshot
import com.greenhands.app.harvest.domain.HarvestEnvironmentContext
import com.greenhands.app.harvest.domain.MaturityAssessment
import com.greenhands.app.harvest.model.HarvestDecisionResult
import com.greenhands.app.harvest.model.PlantHealthAssessment
import com.greenhands.app.harvest.model.ScanRecord
import com.greenhands.app.harvest.model.ScanType

/**
 * Builds immutable history snapshots from a completed scan result.
 * Returns null when there is no valid real measurement to save.
 */
object HarvestRecordFactory {

    fun fruit(
        decision: HarvestDecisionResult,
        cropType: String,
        variety: String?,
        transplantDateUtcMillis: Long?,
        daysSinceTransplant: Int?,
        environment: GreenhouseEnvironmentSnapshot,
        id: String,
        scannedAtEpochMillis: Long
    ): ScanRecord? {
        val measurement = decision.fruitMeasurement
        if (decision.scanRequired || measurement == null || !measurement.hasSamples) return null
        return ScanRecord(
            id = id,
            scanType = ScanType.FRUIT_SCAN,
            scannedAtEpochMillis = scannedAtEpochMillis,
            cropType = cropType,
            variety = variety,
            transplantDateUtcMillis = transplantDateUtcMillis,
            daysSinceTransplant = daysSinceTransplant,
            maturityReferenceKind = decision.maturity.referenceKind.name,
            maturityMinDays = decision.maturity.expectedMinDays,
            maturityMaxDays = decision.maturity.expectedMaxDays,
            maturityStatus = decision.maturity.timing.name,
            estimatedDaysRemaining = decision.maturity.estimatedDaysRemaining,
            hueMean = measurement.hueMean,
            saturationMean = measurement.saturationMean,
            valueMean = measurement.valueMean,
            greenPercent = measurement.greenPercent,
            yellowPercent = measurement.yellowPercent,
            redPercent = measurement.redPercent,
            brownDarkPercent = measurement.brownDarkPercent,
            ripenessEvidence = decision.ripeness.label,
            qualityStatus = decision.quality.label,
            harvestDecision = decision.decision,
            harvestDecisionLabel = decision.displayLabel,
            decisionReasons = decision.reasons,
            temperatureC = liveTemperature(environment),
            humidityPercent = liveHumidity(environment),
            environmentSource = HarvestEnvironmentContext.sourceLabel(environment.connectionState)
        )
    }

    fun leaf(
        assessment: PlantHealthAssessment,
        cropType: String,
        variety: String?,
        transplantDateUtcMillis: Long?,
        environment: GreenhouseEnvironmentSnapshot,
        id: String,
        scannedAtEpochMillis: Long,
        maturity: MaturityAssessment? = null,
        daysSinceTransplant: Int? = null
    ): ScanRecord? {
        val measurement = assessment.leafMeasurement
        if (assessment.scanRequired || measurement == null || !measurement.hasSamples) return null
        return ScanRecord(
            id = id,
            scanType = ScanType.LEAF_SCAN,
            scannedAtEpochMillis = scannedAtEpochMillis,
            cropType = cropType,
            variety = variety,
            transplantDateUtcMillis = transplantDateUtcMillis,
            daysSinceTransplant = daysSinceTransplant,
            maturityReferenceKind = maturity?.referenceKind?.name,
            maturityMinDays = maturity?.expectedMinDays,
            maturityMaxDays = maturity?.expectedMaxDays,
            maturityStatus = maturity?.timing?.name,
            estimatedDaysRemaining = maturity?.estimatedDaysRemaining,
            hueMean = measurement.hueMean,
            saturationMean = measurement.saturationMean,
            valueMean = measurement.valueMean,
            greenPercent = measurement.greenPercent,
            yellowPercent = measurement.yellowPercent,
            brownDarkPercent = measurement.brownDarkPercent,
            whitePalePercent = measurement.whitePalePercent,
            discoloredPercent = measurement.discoloredPercent,
            plantHealthStatus = assessment.statusLabel,
            possibleDisease = assessment.possibleDisease,
            matchingConfidencePercent = assessment.confidencePercent,
            matchedSymptoms = assessment.matchedSymptoms,
            recommendation = assessment.recommendation,
            temperatureC = liveTemperature(environment),
            humidityPercent = liveHumidity(environment),
            environmentSource = HarvestEnvironmentContext.sourceLabel(environment.connectionState)
        )
    }

    /**
     * Only a currently trusted Live snapshot is stored as scan climate.
     * Stale last-known values and Preview samples are never saved as Live.
     */
    fun liveTemperature(environment: GreenhouseEnvironmentSnapshot): Double? =
        environment.temperatureC.takeIf { environment.connectionState == GreenhouseConnectionState.LIVE }

    fun liveHumidity(environment: GreenhouseEnvironmentSnapshot): Double? =
        environment.relativeHumidityPercent.takeIf {
            environment.connectionState == GreenhouseConnectionState.LIVE
        }
}
