package com.greenhands.app

import com.greenhands.app.decision.bufferNormRect
import com.greenhands.app.decision.InfectionConfidenceGate
import com.greenhands.app.decision.InfectionDetector
import com.greenhands.app.decision.PredictionStabilizer
import com.greenhands.app.decision.RankedPrediction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InfectionGateTest {

    @Test
    fun lowConfidenceIsRejectedWithoutRaisingTheScore() {
        val scores = floatArrayOf(0.22f, 0.18f, 0.11f)
        val accepted = InfectionConfidenceGate.accept(
            scores,
            listOf("Early Blight", "Healthy", "Leaf Spot"),
            minTop1 = 0.55f,
            minMargin = 0.12f
        )
        assertNull(accepted)
    }

    @Test
    fun closeTopTwoPredictionsAreUncertain() {
        val scores = floatArrayOf(0.46f, 0.41f, 0.13f)
        val accepted = InfectionConfidenceGate.accept(
            scores,
            listOf("Powdery Mildew", "Mosaic Virus", "Healthy"),
            minTop1 = 0.40f,
            minMargin = 0.12f
        )
        assertNull(accepted)
    }

    @Test
    fun acceptedScoreIsTheModelProbability() {
        val scores = floatArrayOf(0.12f, 0.81f, 0.07f)
        val accepted = InfectionConfidenceGate.accept(
            scores,
            listOf("Healthy", "Late Blight", "Unknown"),
            minTop1 = 0.60f,
            minMargin = 0.15f
        )
        assertEquals("Late Blight", accepted?.label)
        assertEquals(0.81f, accepted?.score ?: 0f, 0.0001f)
    }

    @Test
    fun stableWindowConfirmsOneLabel() {
        val stabilizer = PredictionStabilizer()
        var shown: RankedPrediction? = null
        repeat(5) {
            shown = stabilizer.observe("Leaf", RankedPrediction("Powdery Mildew", 0.86f))
        }
        assertEquals("Powdery Mildew", shown?.label)
        assertEquals(0.86f, shown?.score ?: 0f, 0.0001f)
    }

    @Test
    fun mixedWindowStaysUnconfirmed() {
        val stabilizer = PredictionStabilizer()
        val labels = listOf(
            "Powdery Mildew",
            "Mosaic Virus",
            "Early Blight",
            "Powdery Mildew",
            "Mosaic Virus",
            "Late Blight"
        )
        var shown: RankedPrediction? = null
        for (label in labels) {
            shown = stabilizer.observe("Leaf", RankedPrediction(label, 0.8f))
        }
        assertNull(shown)
    }

    @Test
    fun clearFrameIsAcceptedImmediatelyAtItsRealScore() {
        val stabilizer = PredictionStabilizer()
        val shown = stabilizer.observe("Leaf", RankedPrediction("Powdery Mildew", 0.93f))
        assertEquals("Powdery Mildew", shown?.label)
        assertEquals(0.93f, shown?.score ?: 0f, 0.0001f)
    }

    @Test
    fun moderateScoreNeedsOneAgreeingFrame() {
        val stabilizer = PredictionStabilizer()
        assertNull(stabilizer.observe("Leaf", RankedPrediction("Early Blight", 0.80f)))
        val shown = stabilizer.observe("Leaf", RankedPrediction("Early Blight", 0.82f))
        assertEquals("Early Blight", shown?.label)
        assertEquals(0.81f, shown?.score ?: 0f, 0.0001f)
    }

    @Test
    fun oneDissentingFrameDoesNotReplaceAConfirmedLabel() {
        val stabilizer = PredictionStabilizer()
        stabilizer.observe("Leaf", RankedPrediction("Early Blight", 0.80f))
        assertEquals("Early Blight", stabilizer.observe("Leaf", RankedPrediction("Early Blight", 0.80f))?.label)
        assertEquals("Early Blight", stabilizer.observe("Leaf", RankedPrediction("Leaf Spot", 0.80f))?.label)
        assertEquals("Leaf Spot", stabilizer.observe("Leaf", RankedPrediction("Leaf Spot", 0.80f))?.label)
    }

    @Test
    fun changingTargetDropsThePreviousDiseaseUntilTheNewFrameIsClear() {
        val stabilizer = PredictionStabilizer()
        stabilizer.observe("Leaf", RankedPrediction("Mosaic Virus", 0.95f))
        assertNull(stabilizer.observe("Fruit", RankedPrediction("Healthy", 0.70f)))
        val shown = stabilizer.observe("Fruit", RankedPrediction("Healthy", 0.93f))
        assertEquals("Healthy", shown?.label)
        assertEquals(0.93f, shown?.score ?: 0f, 0.0001f)
    }

    @Test
    fun oneUncertainFrameHoldsAndTheNextClears() {
        val stabilizer = PredictionStabilizer()
        assertEquals("Healthy", stabilizer.observe("Leaf", RankedPrediction("Healthy", 0.90f))?.label)
        assertEquals("Healthy", stabilizer.observe("Leaf", null)?.label)
        assertNull(stabilizer.observe("Leaf", null))
    }

    @Test
    fun confidentUnknownInvalidatesAPreviousDisease() {
        val stabilizer = PredictionStabilizer()
        repeat(5) {
            stabilizer.observe("Leaf", RankedPrediction("Mosaic Virus", 0.88f))
        }
        stabilizer.invalidate()
        assertNull(stabilizer.observe("Leaf", RankedPrediction("Mosaic Virus", 0.88f)))
        assertNull(stabilizer.observe("Leaf", null))
    }

    @Test
    fun lesionBoxUsesTheActiveRegionAndRejectsTheWholeMap() {
        val grid = 7
        val activation = FloatArray(grid * grid)
        activation[2 * grid + 3] = 1f
        activation[2 * grid + 4] = 0.8f
        activation[3 * grid + 3] = 0.7f
        val box = InfectionDetector.lesionNormRect(activation, grid)
        assertEquals(3f / grid, box?.left ?: -1f, 0.0001f)
        assertEquals(2f / grid, box?.top ?: -1f, 0.0001f)
        assertEquals(5f / grid, box?.right ?: -1f, 0.0001f)
        assertEquals(4f / grid, box?.bottom ?: -1f, 0.0001f)
        assertNull(InfectionDetector.lesionNormRect(FloatArray(grid * grid) { 1f }, grid))
        assertNull(InfectionDetector.lesionNormRect(FloatArray(grid * grid), grid))
    }

    @Test
    fun targetBoxFollowsTheActiveCells() {
        val grid = 7
        val activation = FloatArray(grid * grid)
        activation[1 * grid + 4] = 1f
        activation[2 * grid + 4] = 0.8f
        activation[2 * grid + 5] = 0.5f
        val box = InfectionDetector.targetNormRect(activation, grid)
        assertEquals(4f / grid, box?.left ?: -1f, 0.0001f)
        assertEquals(1f / grid, box?.top ?: -1f, 0.0001f)
        assertEquals(6f / grid, box?.right ?: -1f, 0.0001f)
        assertEquals(3f / grid, box?.bottom ?: -1f, 0.0001f)
        assertNull(InfectionDetector.targetNormRect(FloatArray(grid * grid), grid))
        assertNull(InfectionDetector.targetNormRect(FloatArray(grid * grid) { 1f }, grid))
    }

    @Test
    fun rotatedLeafBoxStaysFiniteAndInsideTheBuffer() {
        val upright = bufferNormRect(0f, 0f, 0.2f, 0.4f, 90)
        assertEquals(0f, upright[0], 0.0001f)
        assertEquals(0.8f, upright[1], 0.0001f)
        assertEquals(0.4f, upright[2], 0.0001f)
        assertEquals(1f, upright[3], 0.0001f)
        val same = bufferNormRect(0.25f, 0.10f, 0.70f, 0.55f, 0)
        assertEquals(0.25f, same[0], 0.0001f)
        assertEquals(0.10f, same[1], 0.0001f)
        assertTrue(same[2] > same[0])
        assertTrue(same[3] > same[1])
    }
}
