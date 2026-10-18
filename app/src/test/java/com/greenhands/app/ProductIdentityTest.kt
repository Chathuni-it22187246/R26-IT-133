package com.greenhands.app

import com.greenhands.app.identity.GreenHandsCopy
import com.greenhands.app.ui.navigation.Routes
import com.greenhands.app.ui.screens.comingSoonCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductIdentityTest {

    @Test
    fun platformIdentityIsGreenHandsNotAClimatePlanner() {
        assertEquals("GreenHands", GreenHandsCopy.APP_NAME)
        assertEquals("AR–IoT Smart Greenhouse Platform", GreenHandsCopy.PLATFORM_NAME)
        assertFalse(GreenHandsCopy.isClimatePlannerWording(GreenHandsCopy.WELCOME_HEADLINE))
        assertFalse(GreenHandsCopy.isClimatePlannerWording(GreenHandsCopy.WELCOME_BODY))
        assertFalse(GreenHandsCopy.isClimatePlannerWording(GreenHandsCopy.ABOUT_BODY))
        assertFalse(GreenHandsCopy.WELCOME_HEADLINE.contains("climate planner", ignoreCase = true))
        assertFalse(GreenHandsCopy.ABOUT_BODY.contains("climate planning", ignoreCase = true))
        assertTrue(GreenHandsCopy.ABOUT_BODY.contains("AR–IoT Smart Greenhouse Platform"))
        assertTrue(GreenHandsCopy.ABOUT_BODY.contains("designed to"))
    }

    @Test
    fun welcomeCopyMatchesOfficialHeadlineAndDescription() {
        assertEquals("Smarter greenhouse decisions, from sensing to harvest", GreenHandsCopy.WELCOME_HEADLINE)
        assertTrue(GreenHandsCopy.WELCOME_BODY.contains("sensor placement"))
        assertTrue(GreenHandsCopy.WELCOME_BODY.contains("heat-distribution"))
        assertTrue(GreenHandsCopy.WELCOME_BODY.contains("harvest"))
        assertTrue(GreenHandsCopy.WELCOME_BODY.contains("recommended actions"))
    }

    @Test
    fun fourComponentDescriptionsMatchOfficialPurposes() {
        assertEquals(
            "Find optimal sensor positions and identify coverage gaps for reliable greenhouse monitoring.",
            GreenHandsCopy.SENSOR_PLACEMENT
        )
        assertEquals(
            "Visualize greenhouse heat patterns and explore how cooling equipment responds in AR.",
            GreenHandsCopy.HEAT_DISTRIBUTION
        )
        assertFalse(GreenHandsCopy.HEAT_DISTRIBUTION.contains("crop-target configuration"))
        assertEquals(
            "Assess crop health and predict the expected harvesting date.",
            GreenHandsCopy.HARVESTING
        )
        assertEquals(
            "Receive recommended decisions and practical actions based on greenhouse and crop conditions.",
            GreenHandsCopy.DECISION_MAKING
        )
        assertEquals("Coming Soon", GreenHandsCopy.STATUS_COMING_SOON)
        assertEquals("Available", GreenHandsCopy.STATUS_AVAILABLE)
    }

    @Test
    fun automaticAndAdvancedWordingIsClear() {
        assertEquals("Automatic Calculation — Recommended", GreenHandsCopy.AUTOMATIC_TITLE)
        assertTrue(GreenHandsCopy.AUTOMATIC_BODY.contains("Day/Night"))
        assertEquals("Advanced Manual Settings", GreenHandsCopy.ADVANCED_TITLE)
        assertTrue(GreenHandsCopy.ADVANCED_BODY.contains("checked before saving"))
        assertEquals("Save & Continue", GreenHandsCopy.SAVE_CONTINUE)
        assertEquals("Save & Continue to Simulation", GreenHandsCopy.SAVE_CONTINUE_SIMULATION)
    }

    @Test
    fun comingSoonCopyDescribesCorrectPurposesWithoutTodo() {
        val sensor = comingSoonCopy(Routes.SENSOR_PLACEMENT)
        val harvest = comingSoonCopy(Routes.HARVESTING)
        val decision = comingSoonCopy(Routes.DECISION_MAKING)
        assertTrue(sensor.second.contains("sensor positions"))
        assertTrue(harvest.second.contains("harvesting date"))
        assertTrue(decision.second.contains("recommended decisions"))
        listOf(sensor, harvest, decision).forEach { (_, body) ->
            assertTrue(body.length > 40)
            assertFalse(body.contains("TODO", ignoreCase = true))
            assertFalse(body.contains("Phase 1", ignoreCase = true))
        }
    }
}
