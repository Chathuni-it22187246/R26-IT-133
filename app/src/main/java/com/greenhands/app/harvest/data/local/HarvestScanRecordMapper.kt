package com.greenhands.app.harvest.data.local

import com.greenhands.app.harvest.model.HarvestDecision
import com.greenhands.app.harvest.model.ScanRecord
import com.greenhands.app.harvest.model.ScanType

object HarvestScanRecordMapper {
    private const val LIST_SEPARATOR = "\n"

    fun toEntity(record: ScanRecord): HarvestScanRecordEntity = HarvestScanRecordEntity(
        id = record.id,
        scanType = record.scanType.name,
        scannedAtEpochMillis = record.scannedAtEpochMillis,
        cropType = record.cropType,
        variety = record.variety,
        transplantDateUtcMillis = record.transplantDateUtcMillis,
        daysSinceTransplant = record.daysSinceTransplant,
        maturityReferenceKind = record.maturityReferenceKind,
        maturityMinDays = record.maturityMinDays,
        maturityMaxDays = record.maturityMaxDays,
        maturityStatus = record.maturityStatus,
        estimatedDaysRemaining = record.estimatedDaysRemaining,
        hueMean = record.hueMean,
        saturationMean = record.saturationMean,
        valueMean = record.valueMean,
        greenPercent = record.greenPercent,
        yellowPercent = record.yellowPercent,
        redPercent = record.redPercent,
        brownDarkPercent = record.brownDarkPercent,
        whitePalePercent = record.whitePalePercent,
        discoloredPercent = record.discoloredPercent,
        ripenessEvidence = record.ripenessEvidence,
        qualityStatus = record.qualityStatus,
        harvestDecision = record.harvestDecision?.name,
        harvestDecisionLabel = record.harvestDecisionLabel,
        decisionReasons = encodeList(record.decisionReasons),
        plantHealthStatus = record.plantHealthStatus,
        possibleDisease = record.possibleDisease,
        matchingConfidencePercent = record.matchingConfidencePercent,
        matchedSymptoms = encodeList(record.matchedSymptoms),
        recommendation = record.recommendation,
        temperatureC = record.temperatureC,
        humidityPercent = record.humidityPercent,
        environmentSource = record.environmentSource
    )

    fun toDomain(entity: HarvestScanRecordEntity): ScanRecord = ScanRecord(
        id = entity.id,
        scanType = ScanType.valueOf(entity.scanType),
        scannedAtEpochMillis = entity.scannedAtEpochMillis,
        cropType = entity.cropType,
        variety = entity.variety,
        transplantDateUtcMillis = entity.transplantDateUtcMillis,
        daysSinceTransplant = entity.daysSinceTransplant,
        maturityReferenceKind = entity.maturityReferenceKind,
        maturityMinDays = entity.maturityMinDays,
        maturityMaxDays = entity.maturityMaxDays,
        maturityStatus = entity.maturityStatus,
        estimatedDaysRemaining = entity.estimatedDaysRemaining,
        hueMean = entity.hueMean,
        saturationMean = entity.saturationMean,
        valueMean = entity.valueMean,
        greenPercent = entity.greenPercent,
        yellowPercent = entity.yellowPercent,
        redPercent = entity.redPercent,
        brownDarkPercent = entity.brownDarkPercent,
        whitePalePercent = entity.whitePalePercent,
        discoloredPercent = entity.discoloredPercent,
        ripenessEvidence = entity.ripenessEvidence,
        qualityStatus = entity.qualityStatus,
        harvestDecision = entity.harvestDecision?.let { HarvestDecision.valueOf(it) },
        harvestDecisionLabel = entity.harvestDecisionLabel,
        decisionReasons = decodeList(entity.decisionReasons),
        plantHealthStatus = entity.plantHealthStatus,
        possibleDisease = entity.possibleDisease,
        matchingConfidencePercent = entity.matchingConfidencePercent,
        matchedSymptoms = decodeList(entity.matchedSymptoms),
        recommendation = entity.recommendation,
        temperatureC = entity.temperatureC,
        humidityPercent = entity.humidityPercent,
        environmentSource = entity.environmentSource
    )

    fun encodeList(items: List<String>): String? =
        items.takeIf { it.isNotEmpty() }?.joinToString(LIST_SEPARATOR)

    fun decodeList(raw: String?): List<String> =
        raw?.split(LIST_SEPARATOR)?.filter { it.isNotEmpty() } ?: emptyList()
}
