package com.greenhands.app.harvest.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "harvest_scan_records")
data class HarvestScanRecordEntity(
    @PrimaryKey val id: String,
    val scanType: String,
    val scannedAtEpochMillis: Long,
    val cropType: String,
    val variety: String?,
    val transplantDateUtcMillis: Long?,
    val daysSinceTransplant: Int?,
    val maturityReferenceKind: String?,
    val maturityMinDays: Int?,
    val maturityMaxDays: Int?,
    val maturityStatus: String?,
    val estimatedDaysRemaining: Int?,
    val hueMean: Float?,
    val saturationMean: Float?,
    val valueMean: Float?,
    val greenPercent: Float?,
    val yellowPercent: Float?,
    val redPercent: Float?,
    val brownDarkPercent: Float?,
    val whitePalePercent: Float?,
    val discoloredPercent: Float?,
    val ripenessEvidence: String?,
    val qualityStatus: String?,
    val harvestDecision: String?,
    val harvestDecisionLabel: String?,
    val decisionReasons: String?,
    val plantHealthStatus: String?,
    val possibleDisease: String?,
    val matchingConfidencePercent: Int?,
    val matchedSymptoms: String?,
    val recommendation: String?,
    val temperatureC: Double?,
    val humidityPercent: Double?,
    val environmentSource: String
)
