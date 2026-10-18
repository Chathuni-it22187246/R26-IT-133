package com.greenhands.app.telemetry

import com.greenhands.app.ar.domain.ArHudContent
import com.greenhands.app.ar.domain.ArRuntimeCopy
import com.greenhands.app.equipment.data.InMemoryEquipmentLayoutRepository
import com.greenhands.app.equipment.model.EquipmentType
import com.greenhands.app.equipment.ui.EquipmentPlacementViewModel
import com.greenhands.app.equipment.ui.OverlayClimate
import com.greenhands.app.heat.domain.HeatFormulas
import com.greenhands.app.preview3d.model.DemoGreenhouseProfile
import com.greenhands.app.preview3d.model.PreviewMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EquipmentPlacementTelemetryTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val climate = OverlayClimate(
        crop = null,
        stage = null,
        temperatureLabel = "25.0°C",
        humidityLabel = "70.0 % RH",
        vpdLabel = "Not published",
        circulation = HeatFormulas.circulation(25.0),
        exhaust = HeatFormulas.exhaustFromCirculation(HeatFormulas.circulation(25.0)),
        fogger = HeatFormulas.fogger(70.0),
        optimalTemperatureC = 25.0,
        optimalHumidityPercent = 70.0
    )

    @Before
    fun setMain() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun reset() {
        Dispatchers.resetMain()
    }

    @Test
    fun demoModeDoesNotStartFirebaseAuthOrListeners() = runTest {
        val telemetry = FakeGreenhouseTelemetryRepository()
        val vm = EquipmentPlacementViewModel(
            InMemoryEquipmentLayoutRepository(),
            telemetryRepository = telemetry
        )
        attach(vm, PreviewMode.DEMO)
        assertEquals(0, telemetry.startCount)
        assertFalse(telemetry.monitoring)
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.CIRCULATION_FAN)
        vm.setSimulatedTemperature(26.4)
        vm.setSimulatedHumidity(70.0)
        vm.startBehavior()
        assertTrue(vm.state.value.behaviorRunning)
        assertEquals(26.4, vm.state.value.simulatedTemperatureC)
        assertEquals(70.0, vm.state.value.simulatedHumidityPercent)
        assertNotEquals(28.41, vm.state.value.simulatedTemperatureC)
        assertEquals(ArHudContent.DEMO_SIMULATION, hud(vm).liveStatusLabel)
        assertEquals(0, telemetry.startCount)
    }

    @Test
    fun myGreenhouseConsumesFreshTelemetryAndDoesNotFallBackToDemoValues() = runTest {
        val telemetry = FakeGreenhouseTelemetryRepository()
        val vm = EquipmentPlacementViewModel(
            InMemoryEquipmentLayoutRepository(),
            telemetryRepository = telemetry
        )
        attach(vm, PreviewMode.MY_GREENHOUSE)
        assertEquals(1, telemetry.startCount)
        assertTrue(telemetry.monitoring)
        telemetry.publish(GreenhouseTelemetryState.Live(TelemetryFixtures.accepted()))
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.CIRCULATION_FAN)
        assertNull(vm.startBlockReason())
        assertNull(vm.state.value.simulatedTemperatureC)
        assertNull(vm.state.value.simulatedHumidityPercent)
        assertEquals(28.41, vm.state.value.liveEnvironment.temperatureC)
        assertEquals(61.25, vm.state.value.liveEnvironment.relativeHumidityPercent)
        assertNotEquals(25.0, vm.state.value.liveEnvironment.temperatureC)
        assertNotEquals(70.0, vm.state.value.liveEnvironment.relativeHumidityPercent)
        assertEquals(TelemetryCopy.LIVE, hud(vm).liveStatusLabel)
        assertEquals("28.41 °C", hud(vm).liveTemperatureLabel)
        assertEquals("61.25 % RH", hud(vm).liveHumidityLabel)
        vm.startBehavior()
        assertTrue(vm.state.value.behaviorRunning)
        assertNotNull(vm.state.value.physicsSnapshot)
        assertEquals(28.41, vm.state.value.physicsSnapshot!!.meanTemperatureC, 1e-9)
        assertEquals(28.41, vm.runtimeInput().temperatureC)
        assertEquals(61.25, vm.runtimeInput().humidityPercent)
        assertNotEquals(25.0, vm.state.value.physicsSnapshot!!.meanTemperatureC, 0.0)
        assertNotEquals(70.0, vm.state.value.physicsSnapshot!!.meanRelativeHumidityPercent, 0.0)
    }

    @Test
    fun startIsBlockedWithoutFreshData() = runTest {
        val telemetry = FakeGreenhouseTelemetryRepository()
        val vm = EquipmentPlacementViewModel(
            InMemoryEquipmentLayoutRepository(),
            telemetryRepository = telemetry
        )
        attach(vm, PreviewMode.MY_GREENHOUSE)
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.CIRCULATION_FAN)
        telemetry.publish(GreenhouseTelemetryState.Connecting)
        assertEquals(ArRuntimeCopy.MY_GREENHOUSE_START_DISABLED, vm.startBlockReason())
        vm.startBehavior()
        assertFalse(vm.state.value.behaviorRunning)
        telemetry.publish(GreenhouseTelemetryState.Stale(TelemetryFixtures.accepted()))
        assertEquals(ArRuntimeCopy.MY_GREENHOUSE_START_DISABLED, vm.startBlockReason())
        telemetry.publish(GreenhouseTelemetryState.Disconnected())
        assertEquals(ArRuntimeCopy.MY_GREENHOUSE_START_DISABLED, vm.startBlockReason())
        assertNull(vm.state.value.simulatedTemperatureC)
    }

    @Test
    fun staleDataTriggersSafeStopOnlyOnceAndRetainsPadPanel() = runTest {
        val telemetry = FakeGreenhouseTelemetryRepository()
        val vm = EquipmentPlacementViewModel(
            InMemoryEquipmentLayoutRepository(),
            telemetryRepository = telemetry
        )
        attach(vm, PreviewMode.MY_GREENHOUSE)
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.CIRCULATION_FAN)
        vm.setMyPadPresent(true)
        vm.setMyPadWidthInput("2.0")
        vm.setMyPadHeightInput("1.5")
        telemetry.publish(GreenhouseTelemetryState.Live(TelemetryFixtures.accepted()))
        vm.startBehavior()
        assertTrue(vm.state.value.behaviorRunning)
        telemetry.publish(GreenhouseTelemetryState.Stale(TelemetryFixtures.accepted()))
        assertFalse(vm.state.value.behaviorRunning)
        assertEquals(ArRuntimeCopy.INPUT_UNAVAILABLE, vm.state.value.behaviorError)
        assertEquals(true, vm.state.value.myPadPresent)
        assertEquals("2.0", vm.state.value.myPadWidthInput)
        assertEquals("1.5", vm.state.value.myPadHeightInput)
        val errorAfterFirst = vm.state.value.behaviorError
        telemetry.publish(GreenhouseTelemetryState.Stale(TelemetryFixtures.accepted()))
        telemetry.publish(GreenhouseTelemetryState.Disconnected(TelemetryFixtures.accepted()))
        assertFalse(vm.state.value.behaviorRunning)
        assertEquals(errorAfterFirst, vm.state.value.behaviorError)
        assertEquals(true, vm.state.value.myPadPresent)
        assertEquals(TelemetryCopy.DISCONNECTED, hud(vm).liveStatusLabel)
        assertNotEquals(TelemetryCopy.LIVE, hud(vm).liveStatusLabel)
    }

    private fun attach(vm: EquipmentPlacementViewModel, mode: PreviewMode) {
        vm.attach(
            "grower@greenhands.app",
            "cfg-telemetry",
            DemoGreenhouseProfile.LENGTH_M,
            DemoGreenhouseProfile.WIDTH_M,
            DemoGreenhouseProfile.HEIGHT_M,
            DemoGreenhouseProfile.CROP_ROW_COUNT,
            climate,
            mode
        )
    }

    private fun hud(vm: EquipmentPlacementViewModel) = ArHudContent.from(
        climate,
        vm.state.value.previewMode,
        live = vm.state.value.liveEnvironment,
        instances = vm.state.value.instances,
        runtimeById = vm.state.value.runtimeById,
        simulatedTemperatureC = vm.state.value.simulatedTemperatureC,
        simulatedHumidityPercent = vm.state.value.simulatedHumidityPercent,
        visualizationRunning = vm.state.value.behaviorRunning
    )
}
