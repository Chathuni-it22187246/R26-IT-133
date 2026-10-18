package com.greenhands.app.harvest.ar

/**
 * Already-computed scan result for AR visualization only.
 * AR must not rerun HSV, harvest, or disease classification.
 *
 * Future fruit/leaf tracking can reuse this payload without changing analysis.
 */
enum class ArResultType {
    HARVEST,
    PLANT_HEALTH
}

data class ArResultData(
    val title: String,
    val status: String,
    val detail: String?,
    val confidencePercent: Int?,
    val resultType: ArResultType
)
