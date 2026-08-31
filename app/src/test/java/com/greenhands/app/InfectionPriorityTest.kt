package com.greenhands.app

import android.graphics.RectF
import com.greenhands.app.decision.InfectionPriority
import com.greenhands.app.decision.RiskSample
import com.greenhands.app.decision.RiskTrend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertTrue(InfectionPriority.isLocalizedSpotBox(RectF(0.42f, 0.44f, 0.56f, 0.58f)))
        assertFalse(InfectionPriority.isLocalizedSpotBox(RectF(0.28f, 0.28f, 0.72f, 0.72f)))
        assertFalse(InfectionPriority.isLocalizedSpotBox(RectF(0f, 0f, 1f, 1f)))
    }

    @Test
    fun infectionScanRouteSupportsUpdateRecordId() {
        val add = com.greenhands.app.ui.navigation.Routes.infectionScan("Tomato")
        val update = com.greenhands.app.ui.navigation.Routes.infectionScan("Tomato", "abc-123")
        assertTrue(add.endsWith("/_"))
        assertTrue(update.endsWith("/abc-123"))
    }
}
