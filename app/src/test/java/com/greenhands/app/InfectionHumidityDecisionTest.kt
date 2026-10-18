package com.greenhands.app

import com.greenhands.app.decision.GreenhouseResponse
import com.greenhands.app.decision.InfectionRecord
import com.greenhands.app.decision.catalogGuidance
import com.greenhands.app.decision.decideFromInfectionAndHumidity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InfectionHumidityDecisionTest {

    @Test
    fun wetInfectionAtHighHumidityVentilates() {
        val decision = decideFromInfectionAndHumidity(
            infectionName = "Late Blight",
            severity = "Critical",
            plantType = "Tomato",
            humidityPercent = 82.0,
            guidance = "Keep canopy dry; run fans after irrigation"
        )
        assertEquals(GreenhouseResponse.VENTILATION, decision.response)
        val text = decision.explain()
        assertTrue(text.contains("Late Blight"))
        assertTrue(text.contains("Critical"))
        assertTrue(text.contains("82%"))
        assertTrue(text.contains("ventilation"))
        assertTrue(text.contains("Keep canopy dry"))
    }

    @Test
    fun sameInfectionChangesWhenHumidityDropsIntoBand() {
        val high = decideFromInfectionAndHumidity("Late Blight", "Critical", "Tomato", 82.0)
        val inside = decideFromInfectionAndHumidity("Late Blight", "Critical", "Tomato", 64.0)
        assertEquals(GreenhouseResponse.VENTILATION, high.response)
        assertEquals(GreenhouseResponse.IRRIGATION, inside.response)
        assertTrue(inside.explain().contains("Hold irrigation"))
        assertFalse(inside.explain().contains("82%"))
    }

    @Test
    fun wetInfectionDoesNotRaiseLowHumidity() {
        val decision = decideFromInfectionAndHumidity("Early Blight", "High", "Tomato", 42.0)
        assertEquals(GreenhouseResponse.MONITORING, decision.response)
        assertTrue(decision.explain().contains("Do not raise humidity"))
        assertFalse(decision.explain().contains("humidity control"))
    }

    @Test
    fun powderyMildewUsesVentilationOnlyWhenHumidityIsHigh() {
        val humid = decideFromInfectionAndHumidity("Powdery Mildew", "Medium", "Cucumber", 80.0)
        val dry = decideFromInfectionAndHumidity("Powdery Mildew", "Medium", "Cucumber", 40.0)
        assertEquals(GreenhouseResponse.VENTILATION, humid.response)
        assertEquals(GreenhouseResponse.MONITORING, dry.response)
        assertTrue(dry.explain().contains("Do not add moisture"))
    }

    @Test
    fun virusFollowsHumidityWithoutClaimingACure() {
        val dry = decideFromInfectionAndHumidity("Mosaic Virus", "High", "Tomato", 48.0)
        val humid = decideFromInfectionAndHumidity("Mosaic Virus", "High", "Tomato", 88.0)
        assertEquals(GreenhouseResponse.HUMIDITY_CONTROL, dry.response)
        assertEquals(GreenhouseResponse.VENTILATION, humid.response)
        assertTrue(dry.explain().contains("does not treat the infection"))
    }

    @Test
    fun changingInfectionChangesTheDecision() {
        val blight = decideFromInfectionAndHumidity("Late Blight", "Critical", "Tomato", 64.0)
        val powdery = decideFromInfectionAndHumidity("Powdery Mildew", "Low", "Tomato", 64.0)
        assertEquals(GreenhouseResponse.IRRIGATION, blight.response)
        assertEquals(GreenhouseResponse.MONITORING, powdery.response)
    }

    @Test
    fun noInfectionUsesHumidityOnlyAndDoesNotInventADisease() {
        val decision = decideFromInfectionAndHumidity("", "", "", 62.0)
        assertEquals(GreenhouseResponse.MONITORING, decision.response)
        val text = decision.explain()
        assertTrue(text.contains("No infection is recorded"))
        assertTrue(text.contains("62%"))
        assertFalse(text.contains("Blight"))
        assertFalse(text.contains("Mildew"))
    }

    @Test
    fun missingHumidityDoesNotInventAReading() {
        val decision = decideFromInfectionAndHumidity("Leaf Spot", "Medium", "Pepper", null)
        assertEquals(GreenhouseResponse.MONITORING, decision.response)
        assertTrue(decision.explain().contains("not available"))
        assertFalse(decision.explain().contains("%,"))
    }

    @Test
    fun guidanceComesFromTheMatchingCatalogRow() {
        val catalog = listOf(
            InfectionRecord(
                plantType = "Tomato",
                infectionShortName = "Late Blight",
                infectionFullName = "Late Blight",
                severityLevel = "Critical",
                visibleSymptoms = "",
                treatmentDescription = "Stop leaf wetness",
                biologicalControl = "",
                chemicalControl = "",
                preventionSteps = "Keep canopy dry; run fans after irrigation"
            ),
            InfectionRecord(
                plantType = "Potato",
                infectionShortName = "Late Blight",
                infectionFullName = "Late Blight",
                severityLevel = "High",
                visibleSymptoms = "",
                treatmentDescription = "Cull symptomatic plants",
                biologicalControl = "",
                chemicalControl = "",
                preventionSteps = "Eliminate cull piles"
            )
        )
        assertEquals(
            "Keep canopy dry; run fans after irrigation",
            catalogGuidance(catalog, "Tomato", "Late Blight")
        )
        assertEquals("", catalogGuidance(catalog, "Tomato", "Healthy"))
    }
}
