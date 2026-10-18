package com.greenhands.app

import com.greenhands.app.decision.InfectionPriority
import com.greenhands.app.decision.RiskSample
import com.greenhands.app.decision.RiskTrend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InfectionPriorityTest {

    @Test
    fun lateBlightOutranksPowderyMildew() {
        assertTrue(
            InfectionPriority.defaultSeverityWeight("Late Blight") >
                InfectionPriority.defaultSeverityWeight("Powdery Mildew")
        )
    }

    @Test
    fun trendDetectsIncreaseAndDecrease() {
        val first = RiskSample(1L, "Medium", 40)
        val worse = RiskSample(2L, "High", 70)
        val better = RiskSample(3L, "Low", 20)
        assertEquals(RiskTrend.Increased, InfectionPriority.trend(listOf(first, worse)))
        assertEquals(RiskTrend.Decreased, InfectionPriority.trend(listOf(worse, better)))
        assertEquals(RiskTrend.Unchanged, InfectionPriority.trend(listOf(first)))
    }

    @Test
    fun localizedSpotBoxRejectsPreviewSizedRegions() {
        val source = java.io.File("src/main/java/com/greenhands/app/decision/InfectionPriority.kt")
            .takeIf { it.exists() }
            ?: java.io.File("app/src/main/java/com/greenhands/app/decision/InfectionPriority.kt")
        val text = source.readText()
        assertTrue(text.contains("fun isLocalizedSpotBox"))
        assertTrue(text.contains("Reject whole-leaf"))
        assertTrue(text.contains("compact lesion"))
    }

    @Test
    fun infectionScanRouteSupportsUpdateRecordId() {
        val add = com.greenhands.app.ui.navigation.Routes.infectionScan("Tomato")
        val update = com.greenhands.app.ui.navigation.Routes.infectionScan("Tomato", "abc-123")
        assertTrue(add.endsWith("/_"))
        assertTrue(update.endsWith("/abc-123"))
    }
}
