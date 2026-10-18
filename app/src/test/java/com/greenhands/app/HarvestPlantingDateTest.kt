package com.greenhands.app

import com.greenhands.app.harvest.domain.CropScanGate
import com.greenhands.app.harvest.domain.PlantingDates
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

@OptIn(ExperimentalCoroutinesApi::class)
class HarvestPlantingDateTest {

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
    fun todayIsSelectableAndHasZeroElapsedDays() {
        val todayUtc = utcMidnightDaysAgo(0)
        assertTrue(PlantingDates.isNotAfterToday(todayUtc, nowLocalNoon()))
        assertEquals(0, PlantingDates.daysSincePlanting(todayUtc, nowLocalNoon()))
    }

    @Test
    fun pastDateCountsElapsedLocalDays() {
        val threeDaysAgo = utcMidnightDaysAgo(3)
        assertEquals(3, PlantingDates.daysSincePlanting(threeDaysAgo, nowLocalNoon()))
        assertTrue(PlantingDates.isNotAfterToday(threeDaysAgo, nowLocalNoon()))
    }

    @Test
    fun futureDateIsRejectedAndDoesNotInventDays() {
        val tomorrow = utcMidnightDaysAgo(-1)
        assertFalse(PlantingDates.isNotAfterToday(tomorrow, nowLocalNoon()))
        assertNull(PlantingDates.daysSincePlanting(tomorrow, nowLocalNoon()))
    }

    @Test
    fun viewModelStoresDateAndDaysThenClearResetsBoth() = runTest {
        val vm = HarvestViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.state.collect {}
        }
        assertNull(vm.state.value.plantingDateUtcMillis)
        assertNull(vm.state.value.daysSincePlanting)

        val planted = utcMidnightDaysAgo(5)
        vm.setPlantingDateUtcMillis(planted)
        assertEquals(planted, vm.state.value.plantingDateUtcMillis)
        assertEquals(5, vm.state.value.daysSincePlanting)

        vm.clearPlantingDate()
        assertNull(vm.state.value.plantingDateUtcMillis)
        assertNull(vm.state.value.daysSincePlanting)
        assertFalse(vm.state.value.canScanCrop)
    }

    @Test
    fun cropScanIsBlockedWithoutPlantingDateAndAllowedWithDate() = runTest {
        val vm = HarvestViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.state.collect {}
        }
        assertFalse(CropScanGate.allowScan(null))
        assertFalse(vm.state.value.canScanCrop)
        assertEquals(
            "Please select the planting date before scanning the crop.",
            CropScanGate.PLANTING_DATE_REQUIRED_MESSAGE
        )

        vm.setPlantingDateUtcMillis(utcMidnightDaysAgo(10))
        assertTrue(CropScanGate.allowScan(vm.state.value.plantingDateUtcMillis))
        assertTrue(vm.state.value.canScanCrop)
    }

    private fun nowLocalNoon(): Long =
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

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
