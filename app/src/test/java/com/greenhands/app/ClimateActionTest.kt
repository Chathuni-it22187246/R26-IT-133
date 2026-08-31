package com.greenhands.app

import com.greenhands.app.decision.DecisionResponse
import com.greenhands.app.decision.neededClimateActuator
import com.greenhands.app.decision.withSingleActiveClimateAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClimateActionTest {

    @Test
    fun heaterDecisionIsNotCountedAsInfection() {
        val heater = DecisionResponse(
            title = "Heating Unit: 63% AI Output",
            urgency = "High",
            immediateAction = "Set heater output to 63% immediately.",
            kind = "heater",
            category = "heater_fans",
            heaterSpeed = 62.5,
            climateStatus = "Low Temperature"
        )
        assertTrue(heater.isHeaterAction)
        assertFalse(heater.isFanAction)
        assertFalse(heater.isWaterAction)
        assertEquals("High", heater.displayUrgency)
        assertTrue(heater.hasDetailedGuide)
    }

    @Test
    fun fanAndWaterKindsMapToActuators() {
        val fan = DecisionResponse(kind = "fan", category = "heater_fans")
        val water = DecisionResponse(kind = "water", category = "water_pump")
        val infection = DecisionResponse(kind = "infection", category = "infections")
        assertTrue(fan.isFanAction)
        assertTrue(water.isWaterAction)
        assertFalse(infection.isHeaterAction)
        assertFalse(infection.isFanAction)
    }

    @Test
    fun onlyLatestMatchingHeaterIsActiveWhenCold() {
        val decisions = listOf(
            DecisionResponse(kind = "heater", category = "heater_fans", lifecycle = "Active"),
            DecisionResponse(kind = "fan", category = "heater_fans", lifecycle = "Active"),
            DecisionResponse(kind = "heater", category = "heater_fans", heaterSpeed = 58.0)
        )
        val resolved = withSingleActiveClimateAction(decisions, 18.4, 64.0)
        assertEquals("Completed", resolved[0].lifecycle)
        assertEquals("Completed", resolved[1].lifecycle)
        assertEquals("Active", resolved[2].lifecycle)
        assertTrue(resolved[2].isLiveClimateAction)
        assertTrue(resolved[0].isCompletedClimateAction)
    }

    @Test
    fun fanIsActiveWhenHotAndHeatersComplete() {
        val decisions = listOf(
            DecisionResponse(kind = "heater", category = "heater_fans"),
            DecisionResponse(kind = "fan", category = "heater_fans")
        )
        val resolved = withSingleActiveClimateAction(decisions, 30.0, 70.0)
        assertEquals("Completed", resolved[0].lifecycle)
        assertEquals("Active", resolved[1].lifecycle)
        assertEquals("fan", neededClimateActuator(30.0, 70.0))
    }
}
