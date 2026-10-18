package com.greenhands.app.harvest.domain

/**
 * Crop / fruit scan is part of Harvesting. A planting (transplant) date is
 * required before Scan Crop may run.
 */
object CropScanGate {
    const val PLANTING_DATE_REQUIRED_MESSAGE =
        "Please select the planting date before scanning the crop."

    fun allowScan(plantingDateUtcMillis: Long?): Boolean = plantingDateUtcMillis != null
}
