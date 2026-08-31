package com.greenhands.app

import com.greenhands.app.decision.InfectionDetector
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LeafVerificationTest {

    @Test
    fun greenLeafTissueIsAccepted() {
        val hsv = floatArrayOf(120f, 0.48f, 0.42f)
        assertTrue(InfectionDetector.isVegetationPixel(hsv, 36, 140, 48))
    }

    @Test
    fun chloroticYellowLeafTissueIsAccepted() {
        val hsv = floatArrayOf(52f, 0.55f, 0.70f)
        assertTrue(InfectionDetector.isVegetationPixel(hsv, 200, 190, 70))
    }

    @Test
    fun blueSkyAndGrayWallsAreRejected() {
        val sky = floatArrayOf(210f, 0.42f, 0.78f)
        val wall = floatArrayOf(0f, 0.02f, 0.86f)
        assertFalse(InfectionDetector.isVegetationPixel(sky, 90, 140, 220))
        assertFalse(InfectionDetector.isVegetationPixel(wall, 220, 220, 220))
    }

    @Test
    fun ripeTomatoFruitIsAccepted() {
        val hsv = floatArrayOf(6f, 0.72f, 0.62f)
        assertTrue(InfectionDetector.isFruitPixel(hsv, 210, 48, 36))
    }

    @Test
    fun skinToneIsNotCountedAsFruit() {
        val hsv = floatArrayOf(18f, 0.36f, 0.72f)
        assertFalse(InfectionDetector.isFruitPixel(hsv, 210, 170, 140))
    }
}
