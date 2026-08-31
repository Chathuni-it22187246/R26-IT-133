package com.greenhands.app.harvest.ar

import com.greenhands.app.harvest.model.HarvestDecisionResult
import com.greenhands.app.harvest.model.PlantHealthAssessment

/**
 * Maps existing harvest / plant-health results into AR display fields.
 * Does not run analysis. Plant-health AR copies the same final status
 * shown on PlantHealthResultScreen.
 */
object ArResultMapper {
    fun fromHarvest(result: HarvestDecisionResult): ArResultData {
        return ArResultData(
            title = "Harvest Status",
            status = result.displayLabel,
            detail = result.maturityReasonLabel,
            confidencePercent = null,
            resultType = ArResultType.HARVEST
        )
    }

    fun fromPlantHealth(result: PlantHealthAssessment): ArResultData {
        val detail = listOfNotNull(result.liveCardIssueLine, result.liveCardDiseaseLine)
            .joinToString(" · ")
            .ifBlank { null }
        return ArResultData(
            title = "Plant Health",
            status = result.simpleHealthStatusLabel,
            detail = detail,
            confidencePercent = null,
            resultType = ArResultType.PLANT_HEALTH
        )
    }
}
