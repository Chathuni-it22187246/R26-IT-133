package com.greenhands.app.heat.runtime

import com.greenhands.app.ar.domain.ArLiveEnvironmentReading
import com.greenhands.app.equipment.data.EquipmentLayoutCodec
import com.greenhands.app.equipment.data.InMemoryEquipmentLayoutRepository
import com.greenhands.app.equipment.model.EquipmentRuntimeState
import com.greenhands.app.equipment.model.EquipmentType
import com.greenhands.app.equipment.ui.EquipmentPlacementViewModel
import com.greenhands.app.equipment.ui.OverlayClimate
import com.greenhands.app.heat.domain.HeatFormulas
import com.greenhands.app.heat.runtime.contracts.WeatherBoundaryKind
import com.greenhands.app.heat.runtime.contracts.WeatherBoundaryProvenance
import com.greenhands.app.heat.runtime.contracts.WeatherBoundaryStatus
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
class DemoPhysicsViewModelTest {

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
    fun demoStartStopLifecycleCleansTheRuntime() = runTest {
        val vm = demoVm("cfg-physics-lifecycle")
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.CIRCULATION_FAN)
        vm.setSimulatedTemperature(30.0)
        vm.setSimulatedHumidity(60.0)
        vm.startBehavior()
        assertTrue(vm.state.value.behaviorRunning)
        assertNotNull(vm.state.value.physicsSnapshot)
        assertEquals(0.0, vm.state.value.physicsSnapshot!!.elapsedTimeS, 0.0)
        assertEquals(PhysicsRuntimeCopy.DEMO_SIMULATION, vm.state.value.physicsSnapshot!!.insideSourceLabel)
        vm.advanceVisualization(0.25)
        assertEquals(0.0, vm.state.value.physicsSnapshot!!.elapsedTimeS, 0.0)
        vm.advanceVisualization(1.0)
        assertEquals(1.0, vm.state.value.physicsSnapshot!!.elapsedTimeS, 1e-12)
        assertEquals(30.0, vm.state.value.displayedTemperatureC!!, 1e-9)
        assertEquals(30.0, vm.state.value.simulatedTemperatureC!!, 1e-9)
        vm.stopBehavior()
        assertFalse(vm.state.value.behaviorRunning)
        assertNull(vm.state.value.physicsSnapshot)
        vm.state.value.runtimeById.values.forEach { assertEquals(EquipmentRuntimeState.NOT_RUNNING, it) }
        vm.onLeaveArScreen()
        assertFalse(vm.state.value.behaviorRunning)
    }

    @Test
    fun myGreenhouseStartConnectsTheFieldRuntimeWithLiveReadings() = runTest {
        val vm = EquipmentPlacementViewModel(InMemoryEquipmentLayoutRepository())
        attach(vm, "cfg-physics-live", PreviewMode.MY_GREENHOUSE)
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.CIRCULATION_FAN)
        vm.onLiveEnvironment(
            ArLiveEnvironmentReading(temperatureC = 31.0, relativeHumidityPercent = 60.0, connected = true)
        )
        vm.startBehavior()
        assertTrue(vm.state.value.behaviorRunning)
        assertNotNull(vm.state.value.physicsSnapshot)
        assertEquals(31.0, vm.state.value.physicsSnapshot!!.meanTemperatureC, 1e-9)
        assertNotEquals(25.0, vm.state.value.physicsSnapshot!!.meanTemperatureC, 0.0)
        assertNotEquals(70.0, vm.state.value.physicsSnapshot!!.meanRelativeHumidityPercent, 0.0)
        assertEquals(EquipmentRuntimeState.ON, vm.state.value.runtimeById.values.single())
    }

    @Test
    fun runtimeIsNotWrittenIntoTheLayoutRepository() = runTest {
        val repo = InMemoryEquipmentLayoutRepository()
        val vm = EquipmentPlacementViewModel(repo)
        attach(vm, "cfg-physics-save", PreviewMode.MY_GREENHOUSE)
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.CIRCULATION_FAN)
        vm.onLiveEnvironment(
            ArLiveEnvironmentReading(temperatureC = 27.0, relativeHumidityPercent = 60.0, connected = true)
        )
        vm.startBehavior()
        assertNotNull(vm.state.value.physicsSnapshot)
        vm.saveLayout()
        val stored = repo.snapshot("grower@greenhands.app").single()
        stored.instances.forEach { inst ->
            assertEquals(EquipmentRuntimeState.NOT_RUNNING, inst.runtimeState)
        }
        val encoded = EquipmentLayoutCodec.encode(stored)
        assertFalse(encoded.contains("behaviorRunning"))
        assertFalse(encoded.contains("physicsSnapshot"))
        assertFalse(encoded.contains("elapsedTime"))
        assertTrue(encoded.contains("NOT_RUNNING"))
    }

    @Test
    fun demoAssimilateOnSliderChangeDoesNotLeaveALeakedRuntime() = runTest {
        val vm = demoVm("cfg-physics-assimilate")
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.CIRCULATION_FAN)
        vm.setSimulatedTemperature(30.0)
        vm.startBehavior()
        val first = vm.state.value.physicsSnapshot!!
        vm.setSimulatedTemperature(22.0)
        val second = vm.state.value.physicsSnapshot!!
        assertEquals(22.0, second.meanTemperatureC, 1e-6)
        assertNotNull(second)
        assertTrue(first.elapsedTimeS == second.elapsedTimeS)
        vm.onLeaveArScreen()
        assertNull(vm.state.value.physicsSnapshot)
    }

    @Test
    fun demoStartObtainsAvailableSimulatedBoundaryFromCropTargets() = runTest {
        val vm = demoVm("cfg-physics-demo-weather")
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.CIRCULATION_FAN)
        vm.setSimulatedTemperature(32.0)
        vm.setSimulatedHumidity(50.0)
        vm.startBehavior()
        val snap = vm.state.value.physicsSnapshot!!
        assertEquals(WeatherBoundaryStatus.AVAILABLE, snap.weatherStatus)
        assertEquals(PhysicsRuntimeCopy.DEMO_SIMULATED_OUTSIDE_WEATHER, snap.weatherSourceLabel)
        assertEquals(WeatherBoundaryProvenance.DEMO_ONLY_ASSUMPTION, snap.weatherProvenance)
        assertEquals(WeatherBoundaryKind.DEMO_SIMULATED, snap.weatherKind)
        assertFalse(snap.weatherSourceLabel.contains("Open-Meteo"))
        assertNotEquals(PhysicsRuntimeCopy.ESTIMATED_OUTSIDE_WEATHER, snap.weatherSourceLabel)
        vm.setDemoFanPadEnabled(false)
        vm.stopBehavior()
        vm.startBehavior()
        assertEquals(
            PhysicsRuntimeCopy.DEMO_SIMULATED_OUTSIDE_BOUNDARY,
            vm.state.value.physicsSnapshot!!.weatherSourceLabel
        )
    }

    @Test
    fun myGreenhouseDoesNotReceiveTheDemoSimulatedBoundary() = runTest {
        val vm = EquipmentPlacementViewModel(InMemoryEquipmentLayoutRepository())
        attach(vm, "cfg-physics-live-weather", PreviewMode.MY_GREENHOUSE)
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.CIRCULATION_FAN)
        vm.onLiveEnvironment(
            ArLiveEnvironmentReading(temperatureC = 32.0, relativeHumidityPercent = 50.0, connected = true)
        )
        vm.startBehavior()
        assertTrue(vm.state.value.behaviorRunning)
        assertNotNull(vm.state.value.physicsSnapshot)
        assertNotEquals(
            WeatherBoundaryKind.DEMO_SIMULATED,
            vm.state.value.physicsSnapshot!!.weatherKind
        )
        assertNotEquals(
            WeatherBoundaryProvenance.DEMO_ONLY_ASSUMPTION,
            vm.state.value.physicsSnapshot!!.weatherProvenance
        )
    }

    @Test
    fun stopAndLeaveArClearThePhysicsAccumulator() = runTest {
        val scheduler = DemoPhysicsScheduler()
        val vm = EquipmentPlacementViewModel(
            InMemoryEquipmentLayoutRepository(),
            physicsScheduler = scheduler
        )
        attach(vm, "cfg-physics-accumulator", PreviewMode.DEMO)
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.CIRCULATION_FAN)
        vm.setSimulatedTemperature(30.0)
        vm.startBehavior()
        vm.advanceVisualization(0.4)
        assertTrue(scheduler.accumulatorS() > 0.0)
        vm.stopBehavior()
        assertEquals(0.0, scheduler.accumulatorS(), 0.0)

        vm.startBehavior()
        vm.advanceVisualization(0.4)
        assertTrue(scheduler.accumulatorS() > 0.0)
        vm.onLeaveArScreen()
        assertEquals(0.0, scheduler.accumulatorS(), 0.0)
    }

    private fun demoVm(configId: String): EquipmentPlacementViewModel {
        val vm = EquipmentPlacementViewModel(InMemoryEquipmentLayoutRepository())
        attach(vm, configId, PreviewMode.DEMO)
        return vm
    }

    private fun attach(vm: EquipmentPlacementViewModel, configId: String, mode: PreviewMode) {
        vm.attach(
            "grower@greenhands.app",
            configId,
            DemoGreenhouseProfile.LENGTH_M,
            DemoGreenhouseProfile.WIDTH_M,
            DemoGreenhouseProfile.HEIGHT_M,
            DemoGreenhouseProfile.CROP_ROW_COUNT,
            climate,
            mode
        )
    }
}
