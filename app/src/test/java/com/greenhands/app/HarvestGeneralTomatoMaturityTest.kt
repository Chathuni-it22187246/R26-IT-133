package com.greenhands.app

import com.greenhands.app.harvest.data.InMemoryHarvestReferenceRepository
import com.greenhands.app.harvest.domain.DaysRemainingEstimator
import com.greenhands.app.harvest.domain.GeneralTomatoMaturityReference
import com.greenhands.app.harvest.domain.MaturityCalculator
import com.greenhands.app.harvest.domain.MaturityReferenceKind
import com.greenhands.app.harvest.domain.MaturityReferenceLabels
import com.greenhands.app.harvest.domain.MaturityTiming
import com.greenhands.app.harvest.model.HarvestReferenceData
import com.greenhands.app.harvest.model.VarietyReference
import com.greenhands.app.harvest.ui.HarvestViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

@OptIn(ExperimentalCoroutinesApi::class)
class HarvestGeneralTomatoMaturityTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val general = GeneralTomatoMaturityReference.DEFAULT

    @Before
    fun setMain() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun reset() {
        Dispatchers.resetMain()
    }

    @Test
    fun generalReferenceStoresNamedSixtyFiveToNinetyWindow() {
        assertEquals(65, general.minDaysAfterTransplant)
        assertEquals(90, general.maxDaysAfterTransplant)
        assertEquals(65, GeneralTomatoMaturityReference.MIN_DAYS_AFTER_TRANSPLANT)
        assertEquals(90, GeneralTomatoMaturityReference.MAX_DAYS_AFTER_TRANSPLANT)
        assertEquals("General tomato maturity reference", general.label)
        assertEquals(MaturityReferenceLabels.GENERAL_TOMATO, general.label)
    }

    @Test
    fun day0HasSixtyFiveDaysRemaining() {
        assertGeneral(days = 0, remaining = 65, timing = MaturityTiming.BEFORE_WINDOW)
    }

    @Test
    fun day50HasFifteenDaysRemaining() {
        assertGeneral(days = 50, remaining = 15, timing = MaturityTiming.BEFORE_WINDOW)
    }

    @Test
    fun day64HasOneDayRemaining() {
        assertGeneral(days = 64, remaining = 1, timing = MaturityTiming.BEFORE_WINDOW)
    }

    @Test
    fun day65IsWithinWindowWithZeroRemaining() {
        assertGeneral(days = 65, remaining = 0, timing = MaturityTiming.WITHIN_WINDOW)
    }

    @Test
    fun day75IsWithinWindowWithZeroRemaining() {
        assertGeneral(days = 75, remaining = 0, timing = MaturityTiming.WITHIN_WINDOW)
    }

    @Test
    fun day90IsWithinWindowWithZeroRemaining() {
        assertGeneral(days = 90, remaining = 0, timing = MaturityTiming.WITHIN_WINDOW)
    }

    @Test
    fun day91IsPastWindowWithZeroRemaining() {
        assertGeneral(days = 91, remaining = 0, timing = MaturityTiming.PAST_WINDOW)
    }

    @Test
    fun noTransplantDateDoesNotCalculateMaturityOrRemaining() {
        val result = MaturityCalculator.assessTomato(null, variety("Thilina"))
        assertEquals(MaturityTiming.NEEDS_TRANSPLANT_DATE, result.timing)
        assertNull(result.estimatedDaysRemaining)
        assertEquals(65, result.expectedMinDays)
        assertEquals(90, result.expectedMaxDays)
        assertEquals(MaturityReferenceKind.GENERAL_TOMATO, result.referenceKind)
        assertNull(DaysRemainingEstimator().estimateTomatoDaysRemaining(null, variety("Thilina")))
    }

    @Test
    fun missingVarietySpecificRangeUsesGeneralFallback() {
        val unsourced = variety("Thilina")
        val result = MaturityCalculator.assessTomato(50, unsourced)
        assertEquals(MaturityReferenceKind.GENERAL_TOMATO, result.referenceKind)
        assertEquals("General tomato maturity reference", result.referenceLabel)
        assertEquals(65, result.expectedMinDays)
        assertEquals(90, result.expectedMaxDays)
        assertEquals(15, result.estimatedDaysRemaining)
        assertEquals(MaturityTiming.BEFORE_WINDOW, result.timing)
        assertEquals(15, DaysRemainingEstimator().estimateTomatoDaysRemaining(50, unsourced))
    }

    @Test
    fun varietySpecificValidRangeOverridesGeneralFallback() {
        val sourced = variety("Test Sourced", min = 70, max = 85)
        val result = MaturityCalculator.assessTomato(50, sourced)
        assertEquals(MaturityReferenceKind.VARIETY_SPECIFIC, result.referenceKind)
        assertEquals("Variety-specific maturity reference", result.referenceLabel)
        assertEquals(70, result.expectedMinDays)
        assertEquals(85, result.expectedMaxDays)
        assertEquals(20, result.estimatedDaysRemaining)
        assertEquals(MaturityTiming.BEFORE_WINDOW, result.timing)
        assertEquals(20, DaysRemainingEstimator().estimateTomatoDaysRemaining(50, sourced))
    }

    @Test
    fun viewModelUsesGeneralFallbackWhenVarietyLacksSourcedDays() = runTest {
        val vm = HarvestViewModel(
            referenceRepository = InMemoryHarvestReferenceRepository(
                HarvestReferenceData(
                    crops = emptyList(),
                    varieties = listOf(variety("Thilina")),
                    diseases = emptyList(),
                    harvestRules = emptyList()
                )
            )
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.state.collect {}
        }
        vm.selectVariety("Thilina")
        vm.setPlantingDateUtcMillis(utcMidnightDaysAgo(50))
        assertEquals("Thilina", vm.state.value.selectedVariety?.variety)
        assertEquals(50, vm.state.value.daysSincePlanting)
        assertEquals(MaturityTiming.BEFORE_WINDOW, vm.state.value.maturity.timing)
        assertEquals(15, vm.state.value.maturity.estimatedDaysRemaining)
        assertEquals(65, vm.state.value.maturity.expectedMinDays)
        assertEquals(90, vm.state.value.maturity.expectedMaxDays)
        assertEquals(MaturityReferenceKind.GENERAL_TOMATO, vm.state.value.maturity.referenceKind)
    }

    private fun assertGeneral(days: Int, remaining: Int, timing: MaturityTiming) {
        val result = MaturityCalculator.assessTomato(days, variety("KWR"))
        assertEquals(timing, result.timing)
        assertEquals(remaining, result.estimatedDaysRemaining)
        assertEquals(65, result.expectedMinDays)
        assertEquals(90, result.expectedMaxDays)
        assertEquals(MaturityReferenceKind.GENERAL_TOMATO, result.referenceKind)
        assertEquals(remaining, DaysRemainingEstimator().estimateTomatoDaysRemaining(days, variety("KWR")))
    }

    private fun variety(
        name: String,
        min: Int? = null,
        max: Int? = null
    ) = VarietyReference(
        cropType = "Tomato",
        variety = name,
        growthHabit = null,
        documentedRipeColor = null,
        averageFruitWeightG = null,
        fruitShape = null,
        yieldTHa = null,
        bacterialWiltResponse = null,
        leafCurlResponse = null,
        otherNotes = null,
        expectedMaturityMinDays = min,
        expectedMaturityMaxDays = max,
        maturityStatus = "TO_BE_SOURCED_OR_FIELD_VALIDATED",
        sourceUrl = null
    )

    private fun utcMidnightDaysAgo(daysAgo: Int): Long {
        val local = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -daysAgo)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(
                local.get(Calendar.YEAR),
                local.get(Calendar.MONTH),
                local.get(Calendar.DAY_OF_MONTH),
                0,
                0,
                0
            )
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
