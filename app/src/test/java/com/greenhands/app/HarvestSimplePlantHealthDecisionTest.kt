package com.greenhands.app

import com.greenhands.app.harvest.ar.ArResultMapper
import com.greenhands.app.harvest.data.HarvestMeasurementStore
import com.greenhands.app.harvest.detection.TomatoDiseaseClassScore
import com.greenhands.app.harvest.detection.TomatoDiseaseLabels
import com.greenhands.app.harvest.detection.TomatoDiseasePrediction
import com.greenhands.app.harvest.domain.PlantHealthStatus
import com.greenhands.app.harvest.domain.SimplePlantHealthDecider
import com.greenhands.app.harvest.model.LeafColorMeasurement
import com.greenhands.app.harvest.model.PlantHealthReasons
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HarvestSimplePlantHealthDecisionTest {

    @Before
    fun resetLeafScanHysteresis() {
        HarvestMeasurementStore.beginNewLeafScan()
        HarvestMeasurementStore.lastLeafHealth = null
    }

    @Test
    fun clearlyHealthyHsvIsHealthy() {
        val assessment = SimplePlantHealthDecider.decide(healthy(green = 78f, yellow = 8f, brown = 4f, pale = 4f))
        assertEquals(PlantHealthStatus.HEALTHY, assessment.status)
        assertEquals("HEALTHY", assessment.simpleHealthStatusLabel)
    }

    @Test
    fun clearlyUnhealthyHsvIsUnhealthy() {
        val assessment = SimplePlantHealthDecider.decide(
            unhealthy(green = 40f, yellow = 32f, brown = 18f, pale = 6f)
        )
        assertEquals(PlantHealthStatus.UNHEALTHY, assessment.status)
        assertEquals("UNHEALTHY", assessment.simpleHealthStatusLabel)
    }

    @Test
    fun smallHealthyVariationsStayHealthy() {
        val bases = listOf(
            healthy(green = 76f, yellow = 9f, brown = 5f, pale = 5f),
            healthy(green = 72f, yellow = 11f, brown = 6f, pale = 6f),
            healthy(green = 70f, yellow = 12f, brown = 7f, pale = 7f),
            healthy(green = 68f, yellow = 14f, brown = 6f, pale = 8f)
        )
        bases.forEach { hsv ->
            val assessment = SimplePlantHealthDecider.decide(
                hsv,
                previousStatus = PlantHealthStatus.HEALTHY
            )
            assertEquals(hsv.toString(), PlantHealthStatus.HEALTHY, assessment.status)
        }
    }

    @Test
    fun smallUnhealthyVariationsStayUnhealthy() {
        val bases = listOf(
            unhealthy(green = 38f, yellow = 30f, brown = 20f, pale = 7f),
            unhealthy(green = 42f, yellow = 28f, brown = 18f, pale = 8f),
            unhealthy(green = 36f, yellow = 26f, brown = 22f, pale = 6f)
        )
        bases.forEach { hsv ->
            val assessment = SimplePlantHealthDecider.decide(
                hsv,
                previousStatus = PlantHealthStatus.UNHEALTHY
            )
            assertEquals(hsv.toString(), PlantHealthStatus.UNHEALTHY, assessment.status)
        }
    }

    @Test
    fun classifierRoiFailureDoesNotForceUncertainWhenHsvIsGood() {
        val assessment = SimplePlantHealthDecider.decide(
            measurement = healthy(green = 74f, yellow = 10f, brown = 6f, pale = 4f),
            classifierAvailable = false
        )
        assertEquals(PlantHealthStatus.HEALTHY, assessment.status)
        assertTrue(assessment.reasons.any { it.contains("classifierAvailable=false") })
        assertTrue(assessment.reasons.any { it.contains("finalStatus=HEALTHY") })
    }

    @Test
    fun classifierFalsePositiveDoesNotOverrideHealthyHsv() {
        val hsv = healthy(green = 80f, yellow = 8f, brown = 4f, pale = 3f)
        val assessment = SimplePlantHealthDecider.decide(
            measurement = hsv,
            classifierAvailable = true,
            prediction = diseasePrediction("Early_blight", 0.91f),
            roiReliable = true
        )
        assertEquals(PlantHealthStatus.HEALTHY, assessment.status)
        assertEquals(PlantHealthReasons.POSSIBLE_NONE, assessment.possibleDisease)
        assertNull(assessment.confidencePercent)
        assertEquals(PlantHealthReasons.VISIBLE_NONE, assessment.visibleIssue)
        assertNull(assessment.liveCardDiseaseLine)
        assertFalse(assessment.showsNamedDisease)
        assertTrue(assessment.reasons.any { it.contains("classifierAvailable=true") })
        assertTrue(assessment.reasons.any { it.contains("finalStatus=HEALTHY") })
    }

    @Test
    fun unhealthyHsvWithReliableEarlyBlightShowsPossibleDisease() {
        val assessment = SimplePlantHealthDecider.decide(
            measurement = brownSpots(),
            classifierAvailable = true,
            prediction = diseasePrediction("Early_blight", 0.82f),
            roiReliable = true
        )
        assertEquals(PlantHealthStatus.UNHEALTHY, assessment.status)
        assertEquals(PlantHealthReasons.VISIBLE_DARK_BROWN, assessment.visibleIssue)
        assertEquals("Early Blight", assessment.possibleDisease)
        assertEquals(82, assessment.confidencePercent)
        assertEquals("Early Blight", assessment.liveCardDiseaseLine)
        assertEquals(PlantHealthReasons.VISIBLE_DARK_BROWN, assessment.liveCardIssueLine)
        assertTrue(assessment.showsNamedDisease)
        assertFalse(assessment.possibleDisease.contains("_"))
    }

    @Test
    fun unhealthyHsvWithLowConfidenceClassifierIsUnable() {
        val assessment = SimplePlantHealthDecider.decide(
            measurement = brownSpots(),
            classifierAvailable = true,
            prediction = diseasePrediction("Early_blight", 0.41f),
            roiReliable = true
        )
        assertEquals(PlantHealthStatus.UNHEALTHY, assessment.status)
        assertEquals(PlantHealthReasons.POSSIBLE_UNABLE, assessment.possibleDisease)
        assertNull(assessment.confidencePercent)
        assertFalse(assessment.showsNamedDisease)
    }

    @Test
    fun classifierRoiFailureKeepsHsvStatus() {
        val healthy = SimplePlantHealthDecider.decide(
            measurement = healthy(green = 74f, yellow = 10f, brown = 6f, pale = 4f),
            classifierAvailable = false,
            prediction = null,
            roiReliable = false
        )
        assertEquals(PlantHealthStatus.HEALTHY, healthy.status)
        assertEquals(PlantHealthReasons.POSSIBLE_NONE, healthy.possibleDisease)

        val unhealthy = SimplePlantHealthDecider.decide(
            measurement = brownSpots(),
            classifierAvailable = false,
            prediction = null,
            roiReliable = false
        )
        assertEquals(PlantHealthStatus.UNHEALTHY, unhealthy.status)
        assertEquals(PlantHealthReasons.POSSIBLE_UNABLE, unhealthy.possibleDisease)
        assertTrue(unhealthy.reasons.any { it.contains("roiReliable=false") })
    }

    @Test
    fun uncertainHsvDoesNotShowDiseaseDiagnosis() {
        val assessment = SimplePlantHealthDecider.decide(
            measurement = null,
            classifierAvailable = true,
            prediction = diseasePrediction("Early_blight", 0.88f),
            roiReliable = true
        )
        assertEquals(PlantHealthStatus.UNCERTAIN, assessment.status)
        assertEquals("UNCERTAIN", assessment.simpleHealthStatusLabel)
        assertEquals("", assessment.possibleDisease)
        assertNull(assessment.liveCardDiseaseLine)
        assertEquals(PlantHealthReasons.UNCERTAIN_SCAN_AGAIN, assessment.liveCardIssueLine)
        assertFalse(assessment.showsNamedDisease)
    }

    @Test
    fun visibleIssueMappingWorks() {
        assertEquals(
            PlantHealthReasons.VISIBLE_NONE,
            SimplePlantHealthDecider.decide(healthy(green = 78f, yellow = 8f, brown = 4f, pale = 4f)).visibleIssue
        )
        assertEquals(
            PlantHealthReasons.VISIBLE_DARK_BROWN,
            SimplePlantHealthDecider.decide(brownSpots()).visibleIssue
        )
        assertEquals(
            PlantHealthReasons.VISIBLE_YELLOWING,
            SimplePlantHealthDecider.decide(
                unhealthy(green = 50f, yellow = 30f, brown = 8f, pale = 4f)
            ).visibleIssue
        )
        assertEquals(
            PlantHealthReasons.VISIBLE_PALE,
            SimplePlantHealthDecider.decide(
                unhealthy(green = 50f, yellow = 8f, brown = 5f, pale = 25f)
            ).visibleIssue
        )
        assertEquals(
            PlantHealthReasons.VISIBLE_MIXED,
            SimplePlantHealthDecider.decide(
                unhealthy(green = 40f, yellow = 32f, brown = 18f, pale = 6f)
            ).visibleIssue
        )
    }

    @Test
    fun liveArCardAndDetailsUseSameFinalResult() {
        val healthy = SimplePlantHealthDecider.decide(
            measurement = healthy(green = 77f, yellow = 9f, brown = 5f, pale = 4f),
            prediction = diseasePrediction("Late_blight", 0.93f),
            roiReliable = true
        )
        val unhealthy = SimplePlantHealthDecider.decide(
            measurement = brownSpots(),
            prediction = diseasePrediction("Early_blight", 0.82f),
            roiReliable = true
        )
        val mappedHealthy = ArResultMapper.fromPlantHealth(healthy)
        val mappedUnhealthy = ArResultMapper.fromPlantHealth(unhealthy)
        assertEquals(healthy.simpleHealthStatusLabel, mappedHealthy.status)
        assertEquals(unhealthy.simpleHealthStatusLabel, mappedUnhealthy.status)
        assertEquals(healthy.visibleIssue, healthy.liveCardIssueLine)
        assertEquals(unhealthy.visibleIssue, unhealthy.liveCardIssueLine)
        assertEquals(unhealthy.possibleDisease, unhealthy.liveCardDiseaseLine)
        assertTrue(mappedUnhealthy.detail!!.contains("Dark/Brown Spots"))
        assertTrue(mappedUnhealthy.detail!!.contains("Early Blight"))
        assertTrue(mappedHealthy.detail!!.contains("No significant discoloration"))
        assertFalse(mappedHealthy.detail!!.contains("Late Blight"))
        assertEquals("Plant Health", mappedHealthy.title)
        assertNull(mappedHealthy.confidencePercent)
        assertNull(mappedUnhealthy.confidencePercent)
    }

    @Test
    fun unusableFewPixelsIsUncertain() {
        val assessment = SimplePlantHealthDecider.decide(
            LeafColorMeasurement(
                sampledPixelCount = 40,
                hueMean = 95f,
                saturationMean = 0.4f,
                valueMean = 0.5f,
                greenPercent = 80f,
                yellowPercent = 8f,
                brownDarkPercent = 4f,
                whitePalePercent = 4f,
                discoloredPercent = 16f,
                otherPercent = 4f
            )
        )
        assertEquals(PlantHealthStatus.UNCERTAIN, assessment.status)
        assertEquals("UNCERTAIN", assessment.simpleHealthStatusLabel)
        assertTrue(assessment.reasons.any { it.contains("too_few_sampled_pixels") })
    }

    @Test
    fun mostlyBackgroundIsUncertain() {
        val assessment = SimplePlantHealthDecider.decide(
            LeafColorMeasurement(
                sampledPixelCount = 800,
                hueMean = 40f,
                saturationMean = 0.15f,
                valueMean = 0.5f,
                greenPercent = 8f,
                yellowPercent = 4f,
                brownDarkPercent = 3f,
                whitePalePercent = 5f,
                discoloredPercent = 12f,
                otherPercent = 80f
            )
        )
        assertEquals(PlantHealthStatus.UNCERTAIN, assessment.status)
        assertTrue(assessment.reasons.any { it.contains("insufficient_leaf_colour") })
    }

    @Test
    fun hysteresisHoldsUnhealthyUntilClearlyHealthy() {
        val borderline = LeafColorMeasurement(
            sampledPixelCount = 900,
            hueMean = 70f,
            saturationMean = 0.4f,
            valueMean = 0.5f,
            greenPercent = 55f,
            yellowPercent = 18f,
            brownDarkPercent = 10f,
            whitePalePercent = 6f,
            discoloredPercent = 34f,
            otherPercent = 11f
        )
        val first = SimplePlantHealthDecider.decide(borderline)
        assertEquals(PlantHealthStatus.UNHEALTHY, first.status)
        val slightlyLess = borderline.copy(
            yellowPercent = 19f,
            discoloredPercent = 31f,
            greenPercent = 58f
        )
        val second = SimplePlantHealthDecider.decide(
            slightlyLess,
            previousStatus = PlantHealthStatus.UNHEALTHY
        )
        assertEquals(PlantHealthStatus.UNHEALTHY, second.status)
    }

    @Test
    fun newLeafScanIgnoresPreviousLeafStatus() {
        HarvestMeasurementStore.lastLeafHealth = SimplePlantHealthDecider.decide(
            healthy(green = 78f, yellow = 8f, brown = 4f, pale = 4f)
        )
        HarvestMeasurementStore.activeLeafScanHysteresis = PlantHealthStatus.HEALTHY
        assertEquals(PlantHealthStatus.HEALTHY, HarvestMeasurementStore.lastLeafHealth?.status)

        HarvestMeasurementStore.beginNewLeafScan()
        assertNull(HarvestMeasurementStore.activeLeafScanHysteresis)

        val leafB = SimplePlantHealthDecider.decide(
            measurement = unhealthy(green = 35f, yellow = 30f, brown = 20f, pale = 8f),
            previousStatus = HarvestMeasurementStore.activeLeafScanHysteresis
        )
        assertEquals(PlantHealthStatus.UNHEALTHY, leafB.status)
        HarvestMeasurementStore.activeLeafScanHysteresis = leafB.status
        HarvestMeasurementStore.lastLeafHealth = leafB

        HarvestMeasurementStore.beginNewLeafScan()
        assertNull(HarvestMeasurementStore.activeLeafScanHysteresis)

        val leafC = SimplePlantHealthDecider.decide(
            measurement = healthy(green = 76f, yellow = 9f, brown = 5f, pale = 4f),
            previousStatus = HarvestMeasurementStore.activeLeafScanHysteresis
        )
        assertEquals(PlantHealthStatus.HEALTHY, leafC.status)
    }

    @Test
    fun lastLeafHealthMustNotBeUsedAsHysteresisForANewScan() {
        val previousUnhealthy = SimplePlantHealthDecider.decide(
            unhealthy(green = 38f, yellow = 28f, brown = 18f, pale = 8f)
        )
        HarvestMeasurementStore.lastLeafHealth = previousUnhealthy
        HarvestMeasurementStore.beginNewLeafScan()
        val independent = SimplePlantHealthDecider.decide(
            measurement = healthy(green = 80f, yellow = 8f, brown = 4f, pale = 3f),
            previousStatus = HarvestMeasurementStore.activeLeafScanHysteresis
        )
        assertEquals(PlantHealthStatus.HEALTHY, independent.status)
        assertEquals(PlantHealthStatus.UNHEALTHY, HarvestMeasurementStore.lastLeafHealth?.status)
    }

    @Test
    fun arStatusMatchesSimplifiedDecision() {
        val healthy = SimplePlantHealthDecider.decide(healthy(green = 77f, yellow = 9f, brown = 5f, pale = 4f))
        val unhealthy = SimplePlantHealthDecider.decide(
            unhealthy(green = 35f, yellow = 30f, brown = 20f, pale = 8f)
        )
        val uncertain = SimplePlantHealthDecider.decide(null)
        assertEquals(healthy.simpleHealthStatusLabel, ArResultMapper.fromPlantHealth(healthy).status)
        assertEquals(unhealthy.simpleHealthStatusLabel, ArResultMapper.fromPlantHealth(unhealthy).status)
        assertEquals(uncertain.simpleHealthStatusLabel, ArResultMapper.fromPlantHealth(uncertain).status)
        assertEquals("Plant Health", ArResultMapper.fromPlantHealth(healthy).title)
        assertEquals(healthy.liveCardIssueLine, healthy.visibleIssue)
        assertEquals(unhealthy.liveCardIssueLine, unhealthy.visibleIssue)
        assertEquals(PlantHealthReasons.UNCERTAIN_SCAN_AGAIN, ArResultMapper.fromPlantHealth(uncertain).detail)
        assertNull(ArResultMapper.fromPlantHealth(healthy).confidencePercent)
    }

    private fun brownSpots(): LeafColorMeasurement =
        unhealthy(green = 52f, yellow = 10f, brown = 22f, pale = 4f)

    private fun diseasePrediction(raw: String, confidence: Float): TomatoDiseasePrediction {
        val display = TomatoDiseaseLabels.displayName(raw)
        val index = TomatoDiseaseLabels.DEFAULT_ORDER.indexOf(raw).coerceAtLeast(0)
        return TomatoDiseasePrediction(
            classIndex = index,
            rawClassName = raw,
            displayName = display,
            confidence = confidence,
            meetsThreshold = confidence >= 0.70f,
            isHealthyClass = TomatoDiseaseLabels.isHealthy(raw),
            appliedSoftmax = true,
            topPredictions = listOf(
                TomatoDiseaseClassScore(index, raw, display, confidence)
            )
        )
    }

    private fun healthy(green: Float, yellow: Float, brown: Float, pale: Float): LeafColorMeasurement {
        val discolored = yellow + brown + pale
        return LeafColorMeasurement(
            sampledPixelCount = 900,
            hueMean = 100f,
            saturationMean = 0.45f,
            valueMean = 0.52f,
            greenPercent = green,
            yellowPercent = yellow,
            brownDarkPercent = brown,
            whitePalePercent = pale,
            discoloredPercent = discolored,
            otherPercent = (100f - green - discolored).coerceAtLeast(0f)
        )
    }

    private fun unhealthy(green: Float, yellow: Float, brown: Float, pale: Float): LeafColorMeasurement {
        val discolored = yellow + brown + pale
        return LeafColorMeasurement(
            sampledPixelCount = 900,
            hueMean = 50f,
            saturationMean = 0.42f,
            valueMean = 0.48f,
            greenPercent = green,
            yellowPercent = yellow,
            brownDarkPercent = brown,
            whitePalePercent = pale,
            discoloredPercent = discolored,
            otherPercent = (100f - green - discolored).coerceAtLeast(0f)
        )
    }
}
