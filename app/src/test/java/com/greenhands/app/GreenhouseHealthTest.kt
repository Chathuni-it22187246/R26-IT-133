package com.greenhands.app

import com.greenhands.app.environment.GreenhouseHealthLevel
import com.greenhands.app.environment.combinedHealthFromReadings
import com.greenhands.app.environment.formatHealthSummary
import com.greenhands.app.environment.parseGreenhouseHealthLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GreenhouseHealthTest {

    @Test
    fun optimalWhenClimateOnTargetAndNoInfections() {
        assertEquals(
            GreenhouseHealthLevel.OPTIMAL,
            combinedHealthFromReadings(24.0, 62.0, 0)
        )
        assertEquals(GreenhouseHealthLevel.OPTIMAL, parseGreenhouseHealthLevel("Optimal"))
        assertEquals("green", parseGreenhouseHealthLevel("green").let {
            if (it == GreenhouseHealthLevel.OPTIMAL) "green" else it.label
        })
    }

    @Test
    fun warningWhenMildClimateOrSingleInfection() {
        assertEquals(
            GreenhouseHealthLevel.WARNING,
            combinedHealthFromReadings(21.0, 62.0, 0)
        )
        assertEquals(
            GreenhouseHealthLevel.WARNING,
            combinedHealthFromReadings(24.0, 62.0, 1)
        )
        assertEquals(GreenhouseHealthLevel.WARNING, parseGreenhouseHealthLevel("yellow"))
    }

    @Test
    fun criticalWhenSevereClimateOrMultipleInfections() {
        assertEquals(
            GreenhouseHealthLevel.CRITICAL,
            combinedHealthFromReadings(18.0, 64.0, 0)
        )
        assertEquals(
            GreenhouseHealthLevel.CRITICAL,
            combinedHealthFromReadings(24.0, 62.0, 3)
        )
        val summary = formatHealthSummary(GreenhouseHealthLevel.CRITICAL, 24.0, 62.0, 3)
        assertTrue(summary.contains("Critical"))
        assertTrue(summary.contains("3 unresolved"))
    }

    @Test
    fun standbyWhenNoReadingsYet() {
        assertEquals(
            GreenhouseHealthLevel.STANDBY,
            combinedHealthFromReadings(null, null, 0)
        )
        assertEquals(
            "Waiting for live greenhouse readings.",
            formatHealthSummary(GreenhouseHealthLevel.STANDBY, null, null, 0)
        )
    }
}
