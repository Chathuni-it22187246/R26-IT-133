package com.greenhands.app

import com.greenhands.app.harvest.domain.PlantHealthStatus
import com.greenhands.app.harvest.integration.HarvestDecisionMakingAction
import com.greenhands.app.harvest.integration.HarvestDecisionMakingBridge
import com.greenhands.app.harvest.integration.HarvestVisibleIssue
import com.greenhands.app.harvest.model.PlantHealthAssessment
import com.greenhands.app.harvest.model.PlantHealthReasons
import com.greenhands.app.ui.navigation.Routes
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HarvestDecisionMakingHandoffTest {

    @Before
    fun clearBridge() {
        HarvestDecisionMakingBridge.clear()
    }

    @After
    fun resetBridge() {
        HarvestDecisionMakingBridge.clear()
    }

    @Test
    fun unhealthyIssueAndPossibleDiseaseArePassedCorrectly() {
        val assessment = assessment(
            status = PlantHealthStatus.UNHEALTHY,
            visibleIssue = PlantHealthReasons.VISIBLE_DARK_BROWN,
            possibleDisease = "Early Blight",
            confidence = 82
        )
        val action = HarvestDecisionMakingBridge.commitFromLeaf(
            assessment = assessment,
            cropType = "Tomato",
            scanResultId = "leaf-1"
        )
        val handoff = (action as HarvestDecisionMakingAction.Open).handoff
        assertEquals(PlantHealthStatus.UNHEALTHY, handoff.healthStatus)
        assertEquals(HarvestVisibleIssue.DARK_BROWN_SPOTS, handoff.detectedVisibleIssue)
        assertEquals("Dark/Brown Spots", handoff.detectedVisibleIssueLabel)
        assertEquals("Early Blight", handoff.possibleDisease)
        assertEquals(82, handoff.classifierConfidence)
        assertEquals("Tomato", handoff.selectedCropType)
        assertEquals("leaf-1", handoff.scanResultId)
        assertEquals(handoff, HarvestDecisionMakingBridge.latestHandoff)
        assertTrue(HarvestDecisionMakingBridge.canOpenFrom(assessment))
    }

    @Test
    fun healthyDataIsPassedWithoutADiseaseName() {
        val assessment = assessment(
            status = PlantHealthStatus.HEALTHY,
            visibleIssue = PlantHealthReasons.VISIBLE_NONE,
            possibleDisease = PlantHealthReasons.POSSIBLE_NONE,
            confidence = 91
        )
        val action = HarvestDecisionMakingBridge.prepare(assessment, "Tomato", "leaf-healthy")
        val handoff = (action as HarvestDecisionMakingAction.Open).handoff
        assertEquals(PlantHealthStatus.HEALTHY, handoff.healthStatus)
        assertEquals(HarvestVisibleIssue.NO_SIGNIFICANT_DISCOLORATION, handoff.detectedVisibleIssue)
        assertEquals(PlantHealthReasons.POSSIBLE_NONE, handoff.possibleDisease)
        assertNull(handoff.classifierConfidence)
        assertFalse(handoff.possibleDisease!!.contains("Blight"))
    }

    @Test
    fun uncertainDoesNotPassMisleadingDiseaseData() {
        val assessment = assessment(
            status = PlantHealthStatus.UNCERTAIN,
            visibleIssue = "",
            possibleDisease = "Early Blight",
            confidence = 88
        )
        val action = HarvestDecisionMakingBridge.commitFromLeaf(assessment, "Tomato", "leaf-u")
        assertTrue(action is HarvestDecisionMakingAction.RescanRequired)
        assertEquals(
            PlantHealthReasons.UNCERTAIN_SCAN_AGAIN,
            (action as HarvestDecisionMakingAction.RescanRequired).message
        )
        assertNull(HarvestDecisionMakingBridge.latestHandoff)
        assertFalse(HarvestDecisionMakingBridge.canOpenFrom(assessment))
    }

    @Test
    fun detectedIssueClickNavigatesWithStructuredHandoff() {
        val assessment = assessment(
            status = PlantHealthStatus.UNHEALTHY,
            visibleIssue = PlantHealthReasons.VISIBLE_YELLOWING,
            possibleDisease = "Late Blight",
            confidence = 75
        )
        val destinations = mutableListOf<String>()
        HarvestDecisionMakingBridge.commitFromLeaf(assessment, "Tomato", "scan-9")
        val navigated = HarvestDecisionMakingBridge.navigateSafely { destinations += it }
        assertTrue(navigated)
        assertEquals(listOf(Routes.comingSoon(Routes.DECISION_MAKING)), destinations)
        assertEquals("Late Blight", HarvestDecisionMakingBridge.latestHandoff?.possibleDisease)
        assertEquals(HarvestVisibleIssue.YELLOWING, HarvestDecisionMakingBridge.latestHandoff?.detectedVisibleIssue)
        assertFalse(HarvestDecisionMakingBridge.isDecisionMakingScreenImplemented())
    }

    @Test
    fun missingDecisionMakingDestinationDoesNotCrash() {
        assertTrue(HarvestDecisionMakingBridge.navigateSafely { error("nav missing") }.not())
        assertFalse(HarvestDecisionMakingBridge.navigateSafely(null))
        assertEquals(Routes.comingSoon(Routes.DECISION_MAKING), HarvestDecisionMakingBridge.destinationRoute())
    }

    private fun assessment(
        status: PlantHealthStatus,
        visibleIssue: String,
        possibleDisease: String,
        confidence: Int?
    ) = PlantHealthAssessment(
        status = status,
        possibleDisease = possibleDisease,
        confidencePercent = confidence,
        matchedSymptoms = emptyList(),
        reasons = emptyList(),
        recommendation = "",
        sourceReference = null,
        diagnosisNote = null,
        scanRequired = false,
        leafMeasurement = null,
        visibleIssue = visibleIssue
    )
}
