package com.greenhands.app

import com.greenhands.app.harvest.data.InMemoryHarvestReferenceRepository
import com.greenhands.app.harvest.data.csv.AssetCsvLoader
import com.greenhands.app.harvest.data.csv.VarietyReferenceCsvParser
import com.greenhands.app.harvest.domain.DaysRemainingEstimator
import com.greenhands.app.harvest.domain.MaturityCalculator
import com.greenhands.app.harvest.domain.MaturityReferenceKind
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.Calendar
import java.util.TimeZone

@OptIn(ExperimentalCoroutinesApi::class)
class HarvestMaturityTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setMain() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun reset() {
        Dispatchers.resetMain()
    }

    @Test
    fun beforeMaturityMinimumReportsRemainingDays() {
        val result = MaturityCalculator.assess(
            daysSinceTransplant = 50,
            minDays = 70,
            maxDays = 90
        )
        assertEquals(MaturityTiming.BEFORE_WINDOW, result.timing)
        assertEquals(20, result.estimatedDaysRemaining)
        assertEquals(70, result.expectedMinDays)
        assertEquals(90, result.expectedMaxDays)
        assertEquals(20, DaysRemainingEstimator().estimateDaysRemaining(50, 70, 90))
    }

    @Test
    fun insideMaturityRangeReportsZeroRemaining() {
        val atMin = MaturityCalculator.assess(70, 70, 90)
        assertEquals(MaturityTiming.WITHIN_WINDOW, atMin.timing)
        assertEquals(0, atMin.estimatedDaysRemaining)

        val mid = MaturityCalculator.assess(80, 70, 90)
        assertEquals(MaturityTiming.WITHIN_WINDOW, mid.timing)
        assertEquals(0, mid.estimatedDaysRemaining)

        val atMax = MaturityCalculator.assess(90, 70, 90)
        assertEquals(MaturityTiming.WITHIN_WINDOW, atMax.timing)
        assertEquals(0, atMax.estimatedDaysRemaining)
        assertEquals(0, DaysRemainingEstimator().estimateDaysRemaining(80, 70, 90))
    }

    @Test
    fun afterMaturityMaximumReportsPastWindowAndZeroRemaining() {
        val result = MaturityCalculator.assess(
            daysSinceTransplant = 100,
            minDays = 70,
            maxDays = 90
        )
        assertEquals(MaturityTiming.PAST_WINDOW, result.timing)
        assertEquals(0, result.estimatedDaysRemaining)
        assertEquals(0, DaysRemainingEstimator().estimateDaysRemaining(100, 70, 90))
    }

    @Test
    fun missingExplicitMinMaxDoesNotInventRemainingDays() {
        assertEquals(MaturityTiming.DATA_UNAVAILABLE, MaturityCalculator.assess(40, null, 90).timing)
        assertNull(MaturityCalculator.assess(40, null, 90).estimatedDaysRemaining)
        assertEquals(MaturityTiming.DATA_UNAVAILABLE, MaturityCalculator.assess(40, 70, null).timing)
        assertNull(MaturityCalculator.assess(40, 70, null).estimatedDaysRemaining)
        assertEquals(MaturityTiming.DATA_UNAVAILABLE, MaturityCalculator.assess(40, null, null).timing)
        assertNull(DaysRemainingEstimator().estimateDaysRemaining(40, null, null))
        assertEquals(MaturityTiming.DATA_UNAVAILABLE, MaturityCalculator.assess(40, 90, 70).timing)
    }

    @Test
    fun noTransplantDateDoesNotCalculateRemainingEvenWhenWindowExists() {
        val result = MaturityCalculator.assess(
            daysSinceTransplant = null,
            minDays = 70,
            maxDays = 90
        )
        assertEquals(MaturityTiming.NEEDS_TRANSPLANT_DATE, result.timing)
        assertNull(result.estimatedDaysRemaining)
        assertEquals(70, result.expectedMinDays)
        assertEquals(90, result.expectedMaxDays)
        assertNull(DaysRemainingEstimator().estimateDaysRemaining(null, 70, 90))
    }

    @Test
    fun bundledTomatoVarietiesStillHaveNoVarietySpecificMaturityDays() {
        val assetsDir = listOf(
            File("src/main/assets"),
            File("app/src/main/assets")
        ).first { it.isDirectory }
        val csv = File(assetsDir, AssetCsvLoader.VARIETY_REFERENCE).readText(Charsets.UTF_8)
        val varieties = VarietyReferenceCsvParser().parse(csv)
        assertEquals(13, varieties.size)
        val usable = varieties.filter { MaturityCalculator.hasUsableMaturityWindow(it) }
        assertTrue(
            "CSV variety rows must not be filled with fake maturity days: $usable",
            usable.isEmpty()
        )
        varieties.forEach { variety ->
            assertNull(variety.expectedMaturityMinDays)
            assertNull(variety.expectedMaturityMaxDays)
            val window = MaturityCalculator.resolveTomatoWindow(variety)
            assertEquals(MaturityReferenceKind.GENERAL_TOMATO, window.kind)
            assertEquals(65, window.minDays)
            assertEquals(90, window.maxDays)
        }
    }

    @Test
    fun viewModelSelectsVarietyThenClearKeepsGeneralFallback() = runTest {
        val sourced = variety("Test Sourced", min = 70, max = 90, ripe = "red")
        val unsourced = variety("KWR", min = null, max = null, ripe = null)
        val vm = HarvestViewModel(
            referenceRepository = InMemoryHarvestReferenceRepository(
                HarvestReferenceData(
                    crops = emptyList(),
                    varieties = listOf(sourced, unsourced),
                    diseases = emptyList(),
                    harvestRules = emptyList()
                )
            )
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.state.collect {}
        }
        assertEquals(2, vm.state.value.tomatoVarieties.size)
        assertNull(vm.state.value.selectedVariety)
        assertEquals(MaturityTiming.NEEDS_TRANSPLANT_DATE, vm.state.value.maturity.timing)
        assertEquals(MaturityReferenceKind.GENERAL_TOMATO, vm.state.value.maturity.referenceKind)

        vm.selectVariety("Test Sourced")
        assertEquals("Test Sourced", vm.state.value.selectedVariety?.variety)
        assertEquals(MaturityTiming.NEEDS_TRANSPLANT_DATE, vm.state.value.maturity.timing)
        assertNull(vm.state.value.maturity.estimatedDaysRemaining)
        assertEquals(MaturityReferenceKind.VARIETY_SPECIFIC, vm.state.value.maturity.referenceKind)

        vm.setPlantingDateUtcMillis(utcMidnightDaysAgo(50))
        assertEquals(50, vm.state.value.daysSincePlanting)
        assertEquals(MaturityTiming.BEFORE_WINDOW, vm.state.value.maturity.timing)
        assertEquals(20, vm.state.value.maturity.estimatedDaysRemaining)

        vm.clearVariety()
        assertNull(vm.state.value.selectedVariety)
        assertEquals(50, vm.state.value.daysSincePlanting)
        assertEquals(MaturityTiming.BEFORE_WINDOW, vm.state.value.maturity.timing)
        assertEquals(15, vm.state.value.maturity.estimatedDaysRemaining)
        assertEquals(MaturityReferenceKind.GENERAL_TOMATO, vm.state.value.maturity.referenceKind)
    }

    @Test
    fun viewModelIgnoresUnknownVarietyNames() = runTest {
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
        vm.selectVariety("Not A Real Variety")
        assertNull(vm.state.value.selectedVariety)
    }

    private fun variety(
        name: String,
        min: Int? = null,
        max: Int? = null,
        ripe: String? = null,
        status: String? = "TO_BE_SOURCED_OR_FIELD_VALIDATED"
    ) = VarietyReference(
        cropType = "Tomato",
        variety = name,
        growthHabit = null,
        documentedRipeColor = ripe,
        averageFruitWeightG = null,
        fruitShape = null,
        yieldTHa = null,
        bacterialWiltResponse = null,
        leafCurlResponse = null,
        otherNotes = null,
        expectedMaturityMinDays = min,
        expectedMaturityMaxDays = max,
        maturityStatus = status,
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
