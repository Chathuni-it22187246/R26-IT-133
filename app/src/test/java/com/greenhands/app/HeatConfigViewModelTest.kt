package com.greenhands.app

import com.greenhands.app.heat.data.HeatConfigCodec
import com.greenhands.app.heat.data.HeatWorkspace
import com.greenhands.app.heat.data.InMemoryHeatConfigRepository
import com.greenhands.app.heat.model.ControlMode
import com.greenhands.app.heat.model.Crop
import com.greenhands.app.heat.model.HeatConfiguration
import com.greenhands.app.heat.model.PROFILE_VERSION
import com.greenhands.app.heat.profile.CropProfileRegistry
import com.greenhands.app.heat.ui.HeatConfigViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HeatConfigViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setMain() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun reset() {
        Dispatchers.resetMain()
    }

    private fun tomatoNursery() = CropProfileRegistry.stageProfile(Crop.TOMATO, "germination").stage
    private fun tomatoFlower() = CropProfileRegistry.stageProfile(Crop.TOMATO, "flowering").stage
    private fun tomatoRipe() = CropProfileRegistry.stageProfile(Crop.TOMATO, "ripening").stage
    private fun cucumberVeg() = CropProfileRegistry.stageProfile(Crop.SALAD_CUCUMBER, "vegetative").stage

    @Test
    fun saveClimateRejectsHardLimitAndKeepsWarningSaveable() {
        val vm = HeatConfigViewModel(InMemoryHeatConfigRepository())
        vm.selectCrop(Crop.TOMATO)
        assertTrue(vm.onStageClicked(tomatoNursery()))

        vm.onTempInput("3")
        vm.onRhInput("70")
        assertFalse(vm.saveClimate())
        assertNotNull(vm.state.value.tempError)

        vm.onTempInput("21.0")
        vm.onRhInput("70")
        assertTrue(vm.saveClimate())
        assertNull(vm.state.value.tempError)
        assertNotNull(vm.state.value.tempWarning)
        assertEquals(21.0, vm.state.value.config.targetTemperatureC)
        assertTrue(vm.state.value.config.saved)
    }

    @Test
    fun invalidInputDoesNotNavigateFlag() {
        val vm = HeatConfigViewModel(InMemoryHeatConfigRepository())
        vm.selectCrop(Crop.TOMATO)
        vm.onStageClicked(tomatoNursery())
        vm.onTempInput("")
        vm.onRhInput("70")
        var navigated = false
        vm.saveClimate { ok -> if (ok) navigated = true }
        assertFalse(navigated)
        assertEquals(25.05, vm.state.value.config.targetTemperatureC!!, 0.0001)
        assertNotNull(vm.state.value.tempError)
    }

    @Test
    fun suggestedValuesCanBeSavedUnchanged() {
        val vm = HeatConfigViewModel(InMemoryHeatConfigRepository())
        vm.selectCrop(Crop.TOMATO)
        vm.onStageClicked(tomatoNursery())
        assertTrue(vm.saveClimate())
        assertEquals(25.05, vm.state.value.config.targetTemperatureC!!, 0.0001)
        assertEquals(75.0, vm.state.value.config.targetHumidityPercent)
        assertFalse(vm.state.value.config.valuesAreCustomised)
    }

    @Test
    fun resetDoesNotPersistUntilSaveAndContinue() {
        val repo = InMemoryHeatConfigRepository()
        val vm = HeatConfigViewModel(repo)
        vm.selectCrop(Crop.TOMATO)
        vm.onStageClicked(tomatoNursery())
        vm.onTempInput("28.0")
        vm.onRhInput("70")
        assertTrue(vm.saveClimate())
        assertEquals(28.0, repo.snapshot().current().targetTemperatureC)

        vm.resetClimateToRecommended()
        assertEquals(28.0, repo.snapshot().current().targetTemperatureC)
        assertEquals("25.05", vm.state.value.tempInput)
        assertTrue(vm.isClimateDirty())
        assertTrue(vm.saveClimate())
        assertEquals(25.05, repo.snapshot().current().targetTemperatureC!!, 0.0001)
    }

    @Test
    fun advancedCirculationRejectsInvalidOrder() {
        val vm = HeatConfigViewModel(InMemoryHeatConfigRepository())
        vm.selectCrop(Crop.TOMATO)
        vm.onStageClicked(tomatoNursery())
        vm.setControlMode(ControlMode.ADVANCED)
        vm.onCdpInput("25.0")
        vm.onCspInput("25.0")
        vm.onConInput("26.0")
        assertFalse(vm.saveCirculation())
        assertNotNull(vm.state.value.circulationError)
    }

    @Test
    fun configurationSurvivesNewViewModelOnSameRepository() {
        val repo = InMemoryHeatConfigRepository()
        val first = HeatConfigViewModel(repo)
        first.selectCrop(Crop.TOMATO)
        first.onStageClicked(tomatoFlower())
        assertTrue(first.saveClimate())

        val second = HeatConfigViewModel(repo)
        assertEquals(Crop.TOMATO, second.state.value.config.crop)
        assertEquals("flowering", second.state.value.config.stage?.id)
        assertEquals(25.5, second.state.value.config.targetTemperatureC)
        assertEquals(29.5, second.state.value.config.circulation!!.csp + 4.0, 0.0)
    }

    @Test
    fun switchingCropDoesNotOverwriteTheOtherCrop() {
        val repo = InMemoryHeatConfigRepository()
        val vm = HeatConfigViewModel(repo)
        vm.selectCrop(Crop.TOMATO)
        vm.onStageClicked(tomatoNursery())
        vm.onTempInput("28.0")
        vm.onRhInput("80")
        assertTrue(vm.saveClimate())

        vm.selectCrop(Crop.SALAD_CUCUMBER)
        vm.onStageClicked(cucumberVeg())
        vm.onTempInput("26.0")
        vm.onRhInput("60")
        assertTrue(vm.saveClimate())

        val tomato = repo.snapshot().configurations.getValue(HeatConfigCodec.key("tomato", "germination"))
        val cucumber = repo.snapshot().configurations.getValue(HeatConfigCodec.key("salad_cucumber", "vegetative"))
        assertEquals(28.0, tomato.targetTemperatureC)
        assertEquals(80.0, tomato.targetHumidityPercent)
        assertEquals(26.0, cucumber.targetTemperatureC)
        assertEquals(60.0, cucumber.targetHumidityPercent)
    }

    @Test
    fun stageChangeDialogThenConfirmResetsDefaults() {
        val vm = HeatConfigViewModel(InMemoryHeatConfigRepository())
        vm.selectCrop(Crop.TOMATO)
        vm.onStageClicked(tomatoNursery())
        vm.onTempInput("28.0")
        vm.onRhInput("70")
        assertTrue(vm.saveClimate())

        assertFalse(vm.onStageClicked(tomatoRipe()))
        assertEquals("ripening", vm.state.value.pendingStage?.id)
        vm.cancelStageChange()
        assertEquals("germination", vm.state.value.config.stage?.id)
        assertEquals(28.0, vm.state.value.config.targetTemperatureC)

        assertFalse(vm.onStageClicked(tomatoRipe()))
        vm.confirmStageChange()
        assertEquals("ripening", vm.state.value.config.stage?.id)
        assertEquals(25.5, vm.state.value.config.targetTemperatureC)
        assertNull(vm.state.value.pendingStage)
    }

    @Test
    fun customisedValuesSurviveProfile211Update() {
        val stage = CropProfileRegistry.stageProfile(Crop.SALAD_CUCUMBER, "vegetative").stage
        val saved = HeatConfiguration(
            crop = Crop.SALAD_CUCUMBER,
            stage = stage,
            targetTemperatureC = 26.0,
            targetHumidityPercent = 55.0,
            saved = true,
            valuesAreCustomised = true,
            profileVersion = "2.1.0",
            lastSuggestedProfileVersion = "2.1.0"
        )
        val workspace = HeatWorkspace(
            selectedCrop = Crop.SALAD_CUCUMBER,
            selectedStageByCrop = mapOf("salad_cucumber" to "vegetative"),
            configurations = mapOf(HeatConfigCodec.key("salad_cucumber", "vegetative") to saved)
        )
        val repo = InMemoryHeatConfigRepository(workspace)
        val vm = HeatConfigViewModel(repo)
        assertEquals(26.0, vm.state.value.config.targetTemperatureC)
        assertEquals(55.0, vm.state.value.config.targetHumidityPercent)
        assertEquals("2.1.0", vm.state.value.config.lastSuggestedProfileVersion)
        assertTrue(vm.state.value.persistError.orEmpty().contains("Newer suggested values"))
        assertEquals(PROFILE_VERSION, "2.1.2")
        assertEquals(26.0, repo.snapshot().configurations.getValue("salad_cucumber_vegetative").targetTemperatureC)
        assertEquals(55.0, repo.snapshot().configurations.getValue("salad_cucumber_vegetative").targetHumidityPercent)
    }

    @Test
    fun cucumberSaveStoresBothDayAndNight() {
        val vm = HeatConfigViewModel(InMemoryHeatConfigRepository())
        vm.selectCrop(Crop.SALAD_CUCUMBER)
        vm.onStageClicked(cucumberVeg())
        assertEquals("25.0", vm.state.value.dayTempInput)
        assertEquals("15.0", vm.state.value.nightTempInput)
        assertTrue(vm.saveClimate())
        assertEquals(25.0, vm.state.value.config.dayTemperatureC)
        assertEquals(15.0, vm.state.value.config.nightTemperatureC)
        assertEquals(25.0, vm.state.value.config.targetTemperatureC)
        assertFalse(vm.state.value.config.valuesAreCustomised)
    }

    @Test
    fun cucumberResetRestoresDay25Night15() {
        val vm = HeatConfigViewModel(InMemoryHeatConfigRepository())
        vm.selectCrop(Crop.SALAD_CUCUMBER)
        vm.onStageClicked(cucumberVeg())
        vm.onDayTempInput("28")
        vm.onNightTempInput("18")
        vm.resetClimateToRecommended()
        assertEquals("25.0", vm.state.value.dayTempInput)
        assertEquals("15.0", vm.state.value.nightTempInput)
    }

    @Test
    fun switchingPeriodDoesNotClearTheOtherPeriod() {
        val vm = HeatConfigViewModel(InMemoryHeatConfigRepository())
        vm.selectCrop(Crop.TOMATO)
        vm.onStageClicked(tomatoFlower())
        vm.onDayTempInput("26.0")
        vm.selectPeriod(com.greenhands.app.heat.model.SchedulePeriod.NIGHT)
        vm.onNightTempInput("18.0")
        vm.selectPeriod(com.greenhands.app.heat.model.SchedulePeriod.DAY)
        assertEquals("26.0", vm.state.value.dayTempInput)
        assertEquals("18.0", vm.state.value.nightTempInput)
        assertTrue(vm.saveClimate())
        assertEquals(26.0, vm.state.value.config.dayTemperatureC)
        assertEquals(18.0, vm.state.value.config.nightTemperatureC)
    }

    @Test
    fun automaticPreviewFollowsSelectedPeriod() {
        val vm = HeatConfigViewModel(InMemoryHeatConfigRepository())
        vm.selectCrop(Crop.TOMATO)
        vm.onStageClicked(tomatoFlower())
        assertTrue(vm.saveClimate())
        assertEquals("25.5", vm.state.value.cspInput)
        vm.selectPeriod(com.greenhands.app.heat.model.SchedulePeriod.NIGHT)
        assertEquals("19.0", vm.state.value.cspInput)
        vm.selectPeriod(com.greenhands.app.heat.model.SchedulePeriod.DAY)
        assertEquals("25.5", vm.state.value.cspInput)
    }

    @Test
    fun advancedManualSettingsPersistPerPeriod() {
        val vm = HeatConfigViewModel(InMemoryHeatConfigRepository())
        vm.selectCrop(Crop.TOMATO)
        vm.onStageClicked(tomatoFlower())
        assertTrue(vm.saveClimate())
        vm.setControlMode(ControlMode.ADVANCED)
        vm.onCspInput("26.0")
        vm.onCdpInput("23.0")
        vm.onConInput("28.0")
        assertTrue(vm.saveCirculation())
        assertEquals(26.0, vm.state.value.config.circulation!!.csp, 0.0)
        vm.selectPeriod(com.greenhands.app.heat.model.SchedulePeriod.NIGHT)
        vm.onCspInput("18.0")
        vm.onCdpInput("16.0")
        vm.onConInput("20.0")
        assertTrue(vm.saveCirculation())
        assertEquals(18.0, vm.state.value.config.nightCirculation!!.csp, 0.0)
        assertEquals(26.0, vm.state.value.config.circulation!!.csp, 0.0)
        vm.resetCirculationToFormula()
        assertEquals(19.0, vm.state.value.config.nightCirculation!!.csp, 0.0)
    }

    @Test
    fun lastSelectedPeriodSurvivesRepositoryRecreation() {
        val repo = InMemoryHeatConfigRepository()
        val first = HeatConfigViewModel(repo)
        first.selectCrop(Crop.TOMATO)
        first.onStageClicked(tomatoFlower())
        assertTrue(first.saveClimate())
        first.selectPeriod(com.greenhands.app.heat.model.SchedulePeriod.NIGHT)
        assertEquals(com.greenhands.app.heat.model.SchedulePeriod.NIGHT, first.state.value.schedulePeriod)
        val second = HeatConfigViewModel(repo)
        assertEquals(com.greenhands.app.heat.model.SchedulePeriod.NIGHT, second.state.value.schedulePeriod)
        assertEquals("19.0", second.state.value.nightTempInput)
        assertEquals(19.0, second.state.value.config.nightTemperatureC)
        second.selectPeriod(com.greenhands.app.heat.model.SchedulePeriod.DAY)
        assertEquals("25.5", second.state.value.dayTempInput)
        assertEquals(25.5, second.state.value.config.dayTemperatureC)
    }

    @Test
    fun saveConfigurationRequiresCropAndStageThenMarksSaved() {
        val repo = InMemoryHeatConfigRepository()
        val vm = HeatConfigViewModel(repo)
        var savedEmpty = true
        vm.saveConfiguration { ok -> savedEmpty = ok }
        assertFalse(savedEmpty)
        vm.selectCrop(Crop.TOMATO)
        vm.onStageClicked(tomatoFlower())
        assertTrue(vm.saveClimate())
        var saved = false
        vm.saveConfiguration { ok -> saved = ok }
        assertTrue(saved)
        assertTrue(vm.state.value.config.saved)
        assertTrue(repo.snapshot().current().saved)
    }
}
