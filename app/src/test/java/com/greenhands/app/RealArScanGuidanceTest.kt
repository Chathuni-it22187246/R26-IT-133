package com.greenhands.app

import com.greenhands.app.sensor.ar.ArOriginPlacementPhase
import com.greenhands.app.sensor.ar.RealArScanGuidance
import com.greenhands.app.sensor.model.GreenhousePhysicalConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealArScanGuidanceTest {

    private val greenhouse12x8 = GreenhousePhysicalConfig(
        lengthMeters = 12.0,
        widthMeters = 8.0,
        heightMeters = 4.0,
        cellSizeMeters = 1.0
    )

    @Test
    fun instructionPhaseKeysMatchManualPlacementFlow() {
        assertEquals("scan", RealArScanGuidance.instructionPhaseKey(ArOriginPlacementPhase.SCANNING))
        assertEquals("use_area", RealArScanGuidance.instructionPhaseKey(ArOriginPlacementPhase.PLANE_FOUND))
        assertEquals(
            "origin_placed",
            RealArScanGuidance.instructionPhaseKey(ArOriginPlacementPhase.ORIGIN_PLACED)
        )
        assertEquals(
            "set_direction",
            RealArScanGuidance.instructionPhaseKey(ArOriginPlacementPhase.SETTING_DIRECTION)
        )
        assertEquals("aligned", RealArScanGuidance.instructionPhaseKey(ArOriginPlacementPhase.ALIGNED))
    }

    @Test
    fun noticeRemainsManualPlacementNotAutoDetection() {
        assertTrue(RealArScanGuidance.noticeIsManualPlacement())
    }

    @Test
    fun largeConfiguredGreenhouseExceedsSmallDetectedPlane() {
        assertTrue(
            RealArScanGuidance.configuredGreenhouseExceedsDetectedArea(
                physical = greenhouse12x8,
                planeExtentXMeters = 3f,
                planeExtentZMeters = 2f
            )
        )
    }

    @Test
    fun greenhouseFitsWhenPlaneLargeEnoughBestEffort() {
        assertFalse(
            RealArScanGuidance.configuredGreenhouseExceedsDetectedArea(
                physical = greenhouse12x8,
                planeExtentXMeters = 12f,
                planeExtentZMeters = 8f
            )
        )
        assertFalse(
            RealArScanGuidance.configuredGreenhouseExceedsDetectedArea(
                physical = greenhouse12x8,
                planeExtentXMeters = 8f,
                planeExtentZMeters = 12f
            )
        )
    }

    @Test
    fun zeroOrInvalidExtentsDoNotWarn() {
        assertFalse(
            RealArScanGuidance.configuredGreenhouseExceedsDetectedArea(
                physical = greenhouse12x8,
                planeExtentXMeters = 0f,
                planeExtentZMeters = 8f
            )
        )
    }

    @Test
    fun exceedsDoesNotResizeConfiguredDimensions() {
        val before = greenhouse12x8.copy()
        RealArScanGuidance.configuredGreenhouseExceedsDetectedArea(
            physical = before,
            planeExtentXMeters = 1f,
            planeExtentZMeters = 1f
        )
        assertEquals(12.0, before.lengthMeters, 0.0)
        assertEquals(8.0, before.widthMeters, 0.0)
        assertEquals(4.0, before.heightMeters, 0.0)
        assertEquals(1.0, before.cellSizeMeters, 0.0)
    }
}
