package com.greenhands.app

import com.greenhands.app.harvest.ar.ArResultMapper
import com.greenhands.app.harvest.domain.CropScanGate
import com.greenhands.app.harvest.domain.HarvestDecisionEngine
import com.greenhands.app.harvest.domain.MaturityCalculator
import com.greenhands.app.harvest.domain.MaturityTiming
import com.greenhands.app.harvest.domain.TomatoHarvestReasons
import com.greenhands.app.harvest.domain.TomatoQualityClassifier
import com.greenhands.app.harvest.domain.TomatoQualityState
import com.greenhands.app.harvest.domain.TomatoRipenessClassifier
import com.greenhands.app.harvest.domain.TomatoRipenessState
import com.greenhands.app.harvest.model.FruitColorMeasurement
import com.greenhands.app.harvest.model.HarvestDecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HarvestDecisionEngineTest {

    private val engine = HarvestDecisionEngine()
    private val ripeness = TomatoRipenessClassifier()
    private val quality = TomatoQualityClassifier()

    @Test
    fun dateNotReachedDarkGreenIsNotReady() {
        val result = engine.decideTomato(darkGreenFruit(), beforeWindow(daysSince = 50))
        assertEquals(HarvestDecision.NOT_READY, result.decision)
        assertEquals("NOT READY TO HARVEST", result.displayLabel)
        assertEquals(TomatoRipenessState.DARK_GREEN, result.ripeness.state)
        assertEquals(TomatoHarvestReasons.MATURITY_NOT_YET_REACHED, result.harvestReasonLabel)
        assertTrue(result.reasons.contains(TomatoHarvestReasons.daysRemainBeforeWindow(15)))
        assertFalse(result.scanRequired)
        assertEquals(result.displayLabel, ArResultMapper.fromHarvest(result).status)
        assertEquals(result.harvestReasonLabel, ArResultMapper.fromHarvest(result).detail)
    }

    @Test
    fun dateNotReachedLightGreenIsNotReady() {
        val result = engine.decideTomato(lightGreenFruit(), beforeWindow(daysSince = 40))
        assertEquals(HarvestDecision.NOT_READY, result.decision)
        assertEquals(TomatoRipenessState.LIGHT_GREEN, result.ripeness.state)
        assertEquals(TomatoHarvestReasons.MATURITY_NOT_YET_REACHED, result.harvestReasonLabel)
    }

    @Test
    fun dateNotReachedYellowOrangeIsNotReady() {
        val result = engine.decideTomato(yellowOrangeFruit(), beforeWindow(daysSince = 40))
        assertEquals(HarvestDecision.NOT_READY, result.decision)
        assertEquals(TomatoRipenessState.GREEN_YELLOW_TRANSITION, result.ripeness.state)
        assertEquals(TomatoHarvestReasons.MATURITY_NOT_YET_REACHED, result.harvestReasonLabel)
    }

    @Test
    fun dateNotReachedClearlyRedRipeIsReady() {
        val result = engine.decideTomato(redFruit(), beforeWindow(daysSince = 40))
        assertEquals(HarvestDecision.READY_TO_HARVEST, result.decision)
        assertEquals("READY TO HARVEST", result.displayLabel)
        assertEquals(TomatoRipenessState.PREDOMINANTLY_RED_RIPE, result.ripeness.state)
        assertEquals(TomatoHarvestReasons.EARLY_VISUALLY_RIPE, result.harvestReasonLabel)
        assertEquals(result.displayLabel, ArResultMapper.fromHarvest(result).status)
        assertEquals(result.harvestReasonLabel, ArResultMapper.fromHarvest(result).detail)
    }

    @Test
    fun dateNotReachedWeakRedPatchesStayNotReady() {
        val mixed = engine.decideTomato(
            fruit(green = 33f, yellow = 10f, red = 33f, brown = 5f, valueMean = 0.55f),
            beforeWindow(daysSince = 40)
        )
        val greenWithLittleRed = engine.decideTomato(
            lightGreenFruit(),
            beforeWindow(daysSince = 40)
        )
        assertEquals(TomatoRipenessState.MIXED_UNCERTAIN, mixed.ripeness.state)
        assertEquals(HarvestDecision.NOT_READY, mixed.decision)
        assertEquals(HarvestDecision.NOT_READY, greenWithLittleRed.decision)
        assertEquals(TomatoHarvestReasons.MATURITY_NOT_YET_REACHED, mixed.harvestReasonLabel)
    }

    @Test
    fun dateReachedDarkGreenIsNotReady() {
        val result = engine.decideTomato(darkGreenFruit(), withinWindow())
        assertEquals(HarvestDecision.NOT_READY, result.decision)
        assertEquals("NOT READY TO HARVEST", result.displayLabel)
        assertEquals(TomatoRipenessState.DARK_GREEN, result.ripeness.state)
        assertEquals(TomatoHarvestReasons.DATE_REACHED_STILL_DARK_GREEN, result.harvestReasonLabel)
        assertEquals(result.displayLabel, ArResultMapper.fromHarvest(result).status)
        assertEquals(result.harvestReasonLabel, ArResultMapper.fromHarvest(result).detail)
    }

    @Test
    fun dateReachedLightGreenIsReady() {
        val result = engine.decideTomato(lightGreenFruit(), withinWindow())
        assertEquals(HarvestDecision.READY_TO_HARVEST, result.decision)
        assertEquals("READY TO HARVEST", result.displayLabel)
        assertEquals(TomatoRipenessState.LIGHT_GREEN, result.ripeness.state)
        assertEquals(TomatoHarvestReasons.DATE_REACHED_LIGHT_GREEN, result.harvestReasonLabel)
        assertEquals(result.displayLabel, ArResultMapper.fromHarvest(result).status)
        assertEquals(result.harvestReasonLabel, ArResultMapper.fromHarvest(result).detail)
    }

    @Test
    fun dateReachedYellowOrangeIsReady() {
        val result = engine.decideTomato(yellowOrangeFruit(), withinWindow())
        assertEquals(HarvestDecision.READY_TO_HARVEST, result.decision)
        assertEquals(TomatoRipenessState.GREEN_YELLOW_TRANSITION, result.ripeness.state)
        assertEquals(TomatoHarvestReasons.DATE_REACHED_HARVEST_COLOUR, result.harvestReasonLabel)
    }

    @Test
    fun dateReachedRedIsReady() {
        val result = engine.decideTomato(redFruit(), withinWindow())
        assertEquals(HarvestDecision.READY_TO_HARVEST, result.decision)
        assertEquals(TomatoRipenessState.PREDOMINANTLY_RED_RIPE, result.ripeness.state)
        assertEquals(TomatoHarvestReasons.DATE_REACHED_HARVEST_COLOUR, result.harvestReasonLabel)
    }

    @Test
    fun pastWindowDarkGreenIsNotReady() {
        val result = engine.decideTomato(darkGreenFruit(), pastWindow())
        assertEquals(HarvestDecision.NOT_READY, result.decision)
        assertEquals(TomatoHarvestReasons.DATE_REACHED_STILL_DARK_GREEN, result.harvestReasonLabel)
    }

    @Test
    fun highDamageDoesNotOverrideLightGreenWhenDateReached() {
        val result = engine.decideTomato(
            fruit(green = 38f, yellow = 30f, red = 10f, brown = 28f, valueMean = 0.55f),
            withinWindow()
        )
        assertEquals(HarvestDecision.READY_TO_HARVEST, result.decision)
        assertEquals(TomatoQualityState.POOR_DAMAGED, result.quality.state)
        assertTrue(result.reasons.contains(TomatoHarvestReasons.DARK_SPOTS))
        assertEquals(TomatoHarvestReasons.DATE_REACHED_HARVEST_COLOUR, result.harvestReasonLabel)
    }

    @Test
    fun missingMeasurementIsUncertainScanRequired() {
        val result = engine.decideTomato(null, withinWindow())
        assertEquals(HarvestDecision.UNCERTAIN, result.decision)
        assertTrue(result.scanRequired)
        assertEquals("SCAN REQUIRED", result.displayLabel)
        assertTrue(result.reasons.contains(TomatoHarvestReasons.SCAN_REQUIRED))
    }

    @Test
    fun insufficientPixelsIsUncertainScanRequired() {
        val result = engine.decideTomato(
            fruit(green = 38f, yellow = 34f, red = 18f, brown = 6f, samples = 40),
            withinWindow()
        )
        assertEquals(HarvestDecision.UNCERTAIN, result.decision)
        assertTrue(result.scanRequired)
        assertEquals(TomatoRipenessState.INSUFFICIENT, result.ripeness.state)
    }

    @Test
    fun mixedColorWithinMaturityIsReady() {
        val result = engine.decideTomato(
            fruit(green = 33f, yellow = 10f, red = 33f, brown = 5f, valueMean = 0.55f),
            withinWindow()
        )
        assertEquals(HarvestDecision.READY_TO_HARVEST, result.decision)
        assertEquals(TomatoRipenessState.MIXED_UNCERTAIN, result.ripeness.state)
        assertEquals(TomatoHarvestReasons.DATE_REACHED_HARVEST_COLOUR, result.harvestReasonLabel)
    }

    @Test
    fun daysWithoutFruitScanDoNotProduceReady() {
        val noFruit = engine.decideTomato(null, pastWindow())
        assertTrue(noFruit.decision != HarvestDecision.READY_TO_HARVEST)
        assertTrue(noFruit.scanRequired)
    }

    @Test
    fun missingPlantingDateBlocksScanAndDoesNotMarkReady() {
        assertFalse(CropScanGate.allowScan(null))
        assertEquals(
            "Please select the planting date before scanning the crop.",
            CropScanGate.PLANTING_DATE_REQUIRED_MESSAGE
        )
        val yellow = engine.decideTomato(
            yellowOrangeFruit(),
            MaturityCalculator.assessTomato(null, null)
        )
        val red = engine.decideTomato(
            redFruit(),
            MaturityCalculator.assessTomato(null, null)
        )
        assertEquals(HarvestDecision.UNCERTAIN, yellow.decision)
        assertEquals(HarvestDecision.UNCERTAIN, red.decision)
        assertTrue(yellow.decision != HarvestDecision.READY_TO_HARVEST)
        assertTrue(red.decision != HarvestDecision.READY_TO_HARVEST)
        assertEquals(TomatoHarvestReasons.WAITING_DATE, yellow.harvestReasonLabel)
        assertEquals(TomatoHarvestReasons.WAITING_DATE, red.harvestReasonLabel)
    }

    @Test
    fun liveCardAndDetailsUseTheSameHarvestResult() {
        val notReady = engine.decideTomato(darkGreenFruit(), withinWindow())
        val readyAfterDate = engine.decideTomato(yellowOrangeFruit(), withinWindow())
        val earlyRed = engine.decideTomato(redFruit(), beforeWindow(daysSince = 40))
        assertEquals(notReady.displayLabel, ArResultMapper.fromHarvest(notReady).status)
        assertEquals(notReady.harvestReasonLabel, ArResultMapper.fromHarvest(notReady).detail)
        assertEquals(readyAfterDate.displayLabel, ArResultMapper.fromHarvest(readyAfterDate).status)
        assertEquals(readyAfterDate.harvestReasonLabel, ArResultMapper.fromHarvest(readyAfterDate).detail)
        assertEquals(earlyRed.displayLabel, ArResultMapper.fromHarvest(earlyRed).status)
        assertEquals(earlyRed.harvestReasonLabel, ArResultMapper.fromHarvest(earlyRed).detail)
        assertEquals(notReady.harvestReasonLabel, notReady.maturityReasonLabel)
        assertEquals(earlyRed.harvestReasonLabel, earlyRed.maturityReasonLabel)
        assertEquals("READY TO HARVEST", earlyRed.displayLabel)
        assertEquals(TomatoHarvestReasons.EARLY_VISUALLY_RIPE, earlyRed.harvestReasonLabel)
    }

    @Test
    fun ripenessClassifierSplitsDarkAndLightGreen() {
        assertEquals(TomatoRipenessState.DARK_GREEN, ripeness.classify(darkGreenFruit()).state)
        assertEquals(TomatoRipenessState.LIGHT_GREEN, ripeness.classify(lightGreenFruit()).state)
        assertEquals(
            TomatoRipenessState.GREEN_YELLOW_TRANSITION,
            ripeness.classify(yellowOrangeFruit()).state
        )
        assertEquals(TomatoRipenessState.PREDOMINANTLY_RED_RIPE, ripeness.classify(redFruit()).state)
        assertEquals(
            TomatoQualityState.ACCEPTABLE,
            quality.classify(yellowOrangeFruit()).state
        )
        assertEquals(
            TomatoQualityState.REVIEW_NEEDED,
            quality.classify(fruit(green = 40f, yellow = 30f, red = 12f, brown = 15f)).state
        )
        assertEquals(
            TomatoQualityState.POOR_DAMAGED,
            quality.classify(fruit(green = 38f, yellow = 30f, red = 10f, brown = 28f)).state
        )
    }

    private fun darkGreenFruit() = fruit(
        green = 78f,
        yellow = 8f,
        red = 6f,
        brown = 5f,
        valueMean = 0.34f,
        saturationMean = 0.62f,
        greenValueMean = 0.33f
    )

    private fun lightGreenFruit() = fruit(
        green = 62f,
        yellow = 14f,
        red = 8f,
        brown = 6f,
        valueMean = 0.58f,
        saturationMean = 0.48f,
        greenValueMean = 0.56f
    )

    private fun yellowOrangeFruit() = fruit(
        green = 38f,
        yellow = 34f,
        red = 18f,
        brown = 6f,
        valueMean = 0.58f
    )

    private fun redFruit() = fruit(
        green = 10f,
        yellow = 18f,
        red = 62f,
        brown = 5f,
        valueMean = 0.52f
    )

    private fun fruit(
        green: Float,
        yellow: Float,
        red: Float,
        brown: Float,
        samples: Int = 1000,
        other: Float? = null,
        valueMean: Float = 0.52f,
        saturationMean: Float = 0.55f,
        greenValueMean: Float? = null
    ) = FruitColorMeasurement(
        sampledPixelCount = samples,
        hueMean = 80f,
        saturationMean = saturationMean,
        valueMean = valueMean,
        greenPercent = green,
        yellowPercent = yellow,
        redPercent = red,
        brownDarkPercent = brown,
        otherPercent = other ?: (100f - green - yellow - red - brown).coerceAtLeast(0f),
        greenSampledCount = if (samples >= 200) (green * samples / 100f).toInt() else 0,
        greenValueMean = greenValueMean ?: valueMean,
        greenSaturationMean = saturationMean
    )

    private fun beforeWindow(daysSince: Int) =
        MaturityCalculator.assess(daysSince, 65, 90).also {
            assertEquals(MaturityTiming.BEFORE_WINDOW, it.timing)
        }

    private fun withinWindow() = MaturityCalculator.assess(75, 65, 90)

    private fun pastWindow() = MaturityCalculator.assess(100, 65, 90)
}
