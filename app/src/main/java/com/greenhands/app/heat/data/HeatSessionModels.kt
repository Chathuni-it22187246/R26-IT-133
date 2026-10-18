package com.greenhands.app.heat.data

import com.greenhands.app.heat.model.HeatConfiguration
import com.greenhands.app.preview3d.model.DemoGreenhouseProfile
import com.greenhands.app.preview3d.model.GreenhouseDimensions
import com.greenhands.app.preview3d.model.PreviewMode

/**
 * Preview/session configuration record for Active and Recent lists.
 * Crop-row count is a visual/configuration property, not a Phase 3A physics input.
 */
data class HeatSessionRecord(
    val id: String,
    val createdAtMillis: Long,
    val cropId: String? = null,
    val cropDisplayName: String? = null,
    val stageId: String? = null,
    val stageDisplayName: String? = null,
    val dayTemperatureC: Double? = null,
    val nightTemperatureC: Double? = null,
    val dayHumidityPercent: Double? = null,
    val nightHumidityPercent: Double? = null,
    val previewMode: String? = null,
    val lengthM: Double? = null,
    val widthM: Double? = null,
    val heightM: Double? = null,
    val cropRowCount: Int = DEFAULT_CROP_ROW_COUNT
) {
    val resolvedCropRowCount: Int
        get() = CropRowCount.resolved(cropRowCount)

    fun sameLogicalConfiguration(other: HeatSessionRecord): Boolean {
        if (cropId != other.cropId || stageId != other.stageId) return false
        val thisMode = previewMode
        val otherMode = other.previewMode
        if (thisMode != null && otherMode != null && thisMode != otherMode) return false
        return true
    }

    companion object {
        const val DEFAULT_CROP_ROW_COUNT = CropRowCount.DEFAULT
    }
}

data class UserHeatSession(
    val userId: String,
    val active: HeatSessionRecord? = null,
    val recent: List<HeatSessionRecord> = emptyList()
)

object CropRowCount {
    const val DEFAULT = 3
    const val MIN = 1
    const val MAX = 20

    fun resolved(value: Int?): Int {
        val rows = value ?: DEFAULT
        return if (rows in MIN..MAX) rows else DEFAULT
    }

    fun forPreview(mode: PreviewMode, customCount: Int): Int =
        if (mode == PreviewMode.DEMO) DemoGreenhouseProfile.CROP_ROW_COUNT else resolved(customCount)
}

fun HeatConfiguration.toSessionDraft(
    id: String,
    createdAtMillis: Long,
    previewMode: PreviewMode? = null,
    dimensions: GreenhouseDimensions? = null,
    cropRowCount: Int = CropRowCount.DEFAULT
): HeatSessionRecord = HeatSessionRecord(
    id = id,
    createdAtMillis = createdAtMillis,
    cropId = crop?.id,
    cropDisplayName = crop?.displayName,
    stageId = stage?.id,
    stageDisplayName = stage?.displayName,
    dayTemperatureC = dayTemperatureC ?: targetTemperatureC,
    nightTemperatureC = nightTemperatureC,
    dayHumidityPercent = dayHumidityPercent ?: targetHumidityPercent,
    nightHumidityPercent = nightHumidityPercent,
    previewMode = previewMode?.name,
    lengthM = dimensions?.lengthM,
    widthM = dimensions?.widthM,
    heightM = dimensions?.heightM,
    cropRowCount = CropRowCount.resolved(cropRowCount)
)

fun String.normalizedUserId(): String? = trim().lowercase().takeIf { it.isNotBlank() }
