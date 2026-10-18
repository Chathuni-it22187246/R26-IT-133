package com.greenhands.app

import com.greenhands.app.harvest.ar.ArResultMapper
import com.greenhands.app.harvest.ar.ArResultType
import com.greenhands.app.harvest.domain.MaturityAssessment
import com.greenhands.app.harvest.domain.MaturityTiming
import com.greenhands.app.harvest.domain.PlantHealthStatus
import com.greenhands.app.harvest.domain.QualityEvidence
import com.greenhands.app.harvest.domain.RipenessEvidence
import com.greenhands.app.harvest.domain.TomatoHarvestReasons
import com.greenhands.app.harvest.domain.TomatoQualityState
import com.greenhands.app.harvest.domain.TomatoRipenessState
import com.greenhands.app.harvest.model.HarvestDecision
import com.greenhands.app.harvest.model.HarvestDecisionResult
import com.greenhands.app.harvest.model.PlantHealthAssessment
import com.greenhands.app.harvest.model.PlantHealthReasons
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HarvestArResultMapperTest {

    @Test
    fun harvestReadyMapsWithoutInventedConfidence() {
        val mapped = ArResultMapper.fromHarvest(
            HarvestDecisionResult(
                decision = HarvestDecision.READY_TO_HARVEST,
                ripeness = RipenessEvidence(
                    TomatoRipenessState.PREDOMINANTLY_RED_RIPE,
                    "Predominantly Red / Ripe",
                    "Ripe colour evidence."
                ),
                quality = QualityEvidence(
                    TomatoQualityState.ACCEPTABLE,
                    "Acceptable",
                    "Quality acceptable."
                ),
                maturity = MaturityAssessment(
                    MaturityTiming.WITHIN_WINDOW,
                    0,
                    65,
                    90
                ),
                reasons = listOf(TomatoHarvestReasons.MATURITY_REACHED),
                fruitMeasurement = null,
                scanRequired = false,
                harvestReason = TomatoHarvestReasons.DATE_REACHED_HARVEST_COLOUR
            )
        )
        assertEquals(ArResultType.HARVEST, mapped.resultType)
        assertEquals("Harvest Status", mapped.title)
        assertEquals("READY TO HARVEST", mapped.status)
        assertEquals(TomatoHarvestReasons.DATE_REACHED_HARVEST_COLOUR, mapped.detail)
        assertNull(mapped.confidencePercent)
    }

    @Test
    fun plantHealthUnhealthyShowsIssueAndPossibleDisease() {
        val mapped = ArResultMapper.fromPlantHealth(
            PlantHealthAssessment(
                status = PlantHealthStatus.UNHEALTHY,
                possibleDisease = "Early Blight",
                confidencePercent = 82,
                matchedSymptoms = listOf("spots"),
                reasons = emptyList(),
                recommendation = "Inspect",
                sourceReference = PlantHealthReasons.SOURCE_CLASSIFIER,
                diagnosisNote = null,
                scanRequired = false,
                leafMeasurement = null,
                visibleIssue = PlantHealthReasons.VISIBLE_DARK_BROWN
            )
        )
        assertEquals(ArResultType.PLANT_HEALTH, mapped.resultType)
        assertEquals("Plant Health", mapped.title)
        assertEquals("UNHEALTHY", mapped.status)
        assertTrue(mapped.detail!!.contains("Dark/Brown Spots"))
        assertTrue(mapped.detail!!.contains("Early Blight"))
        assertNull(mapped.confidencePercent)
    }

    @Test
    fun plantHealthHealthyMatchesResultScreenLabel() {
        val mapped = ArResultMapper.fromPlantHealth(
            PlantHealthAssessment(
                status = PlantHealthStatus.HEALTHY,
                possibleDisease = PlantHealthReasons.POSSIBLE_NONE,
                confidencePercent = null,
                matchedSymptoms = emptyList(),
                reasons = emptyList(),
                recommendation = "",
                sourceReference = PlantHealthReasons.SOURCE_CLASSIFIER,
                diagnosisNote = null,
                scanRequired = false,
                leafMeasurement = null,
                visibleIssue = PlantHealthReasons.VISIBLE_NONE
            )
        )
        assertEquals("HEALTHY", mapped.status)
        assertEquals(PlantHealthReasons.VISIBLE_NONE, mapped.detail)
        assertNull(mapped.confidencePercent)
    }

    @Test
    fun plantHealthUncertainMatchesResultScreenLabel() {
        val mapped = ArResultMapper.fromPlantHealth(
            PlantHealthAssessment(
                status = PlantHealthStatus.UNCERTAIN,
                possibleDisease = "Septoria Leaf Spot",
                confidencePercent = 76,
                matchedSymptoms = emptyList(),
                reasons = emptyList(),
                recommendation = "",
                sourceReference = PlantHealthReasons.SOURCE_CLASSIFIER,
                diagnosisNote = null,
                scanRequired = false,
                leafMeasurement = null
            )
        )
        assertEquals("UNCERTAIN", mapped.status)
        assertEquals(PlantHealthReasons.UNCERTAIN_SCAN_AGAIN, mapped.detail)
        assertNull(mapped.confidencePercent)
        assertNull(mapped.confidencePercent)
    }

    @Test
    fun plantHealthWarningDisplaysAsUncertainOnAr() {
        val mapped = ArResultMapper.fromPlantHealth(
            PlantHealthAssessment(
                status = PlantHealthStatus.WARNING,
                possibleDisease = PlantHealthReasons.UNCERTAIN_DISEASE,
                confidencePercent = 50,
                matchedSymptoms = emptyList(),
                reasons = emptyList(),
                recommendation = "",
                sourceReference = PlantHealthReasons.SOURCE_CLASSIFIER,
                diagnosisNote = null,
                scanRequired = false,
                leafMeasurement = null
            )
        )
        assertEquals("UNCERTAIN", mapped.status)
        assertEquals(PlantHealthReasons.UNCERTAIN_SCAN_AGAIN, mapped.detail)
        assertNull(mapped.confidencePercent)
    }
}
