package com.greenhands.app.ar

import com.greenhands.app.ar.domain.ArHudContent
import com.greenhands.app.ar.domain.ArLiveEnvironmentReading
import com.greenhands.app.ar.domain.ArRuntimeCopy
import com.greenhands.app.ar.domain.EquipmentPresentationCopy
import com.greenhands.app.ar.domain.InMemoryLiveEnvironmentSource
import com.greenhands.app.equipment.data.EquipmentLayoutCodec
import com.greenhands.app.equipment.data.InMemoryEquipmentLayoutRepository
import com.greenhands.app.equipment.model.EquipmentRuntimeState
import com.greenhands.app.equipment.model.EquipmentType
import com.greenhands.app.equipment.ui.EquipmentPlacementViewModel
import com.greenhands.app.equipment.ui.OverlayClimate
import com.greenhands.app.heat.domain.HeatFormulas
import com.greenhands.app.heat.model.Crop
import com.greenhands.app.heat.runtime.contracts.FakeRuntimeClock
import com.greenhands.app.heat.runtime.contracts.FreshnessPolicy
import com.greenhands.app.heat.runtime.contracts.InMemoryWeatherBoundarySource
import com.greenhands.app.heat.runtime.contracts.WeatherBoundary
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
class ArTruthStateLifecycleTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val circ = HeatFormulas.circulation(25.0)
    private val climate = OverlayClimate(
        crop = Crop.TOMATO,
        stage = null,
        temperatureLabel = "—",
        humidityLabel = "—",
        vpdLabel = "Not published",
        circulation = circ,
        exhaust = HeatFormulas.exhaustFromCirculation(circ),
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
    fun initialMyGreenhouseEntryHasNoConfigurationChangedMessage() = runTest {
        val vm = EquipmentPlacementViewModel(InMemoryEquipmentLayoutRepository())
        attach(vm, "cfg-truth-entry", PreviewMode.MY_GREENHOUSE)
        assertNull(vm.state.value.behaviorError)
        assertFalse(vm.state.value.behaviorRunning)
    }

    @Test
    fun initialConfigurationBindHasNoStopReason() = runTest {
        val vm = EquipmentPlacementViewModel(InMemoryEquipmentLayoutRepository())
        assertNull(vm.state.value.behaviorError)
        attach(vm, "cfg-truth-bind", PreviewMode.MY_GREENHOUSE)
        assertNull(vm.state.value.behaviorError)
        attach(vm, "cfg-truth-bind", PreviewMode.MY_GREENHOUSE)
        assertNull(vm.state.value.behaviorError)
    }

    @Test
    fun rebindingIdenticalConfigurationHasNoStopReason() = runTest {
        val vm = demoVm("cfg-truth-rebind")
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.CIRCULATION_FAN)
        vm.startBehavior()
        vm.stopBehavior()
        attach(vm, "cfg-truth-rebind", PreviewMode.DEMO)
        assertNull(vm.state.value.behaviorError)
        assertFalse(vm.state.value.behaviorRunning)
    }

    @Test
    fun openingTheSheetWhileStoppedHasNoStopReason() = runTest {
        val vm = EquipmentPlacementViewModel(InMemoryEquipmentLayoutRepository())
        attach(vm, "cfg-truth-sheet", PreviewMode.MY_GREENHOUSE)
        vm.openBehaviorSheet()
        assertNull(vm.state.value.behaviorError)
        assertTrue(vm.state.value.behaviorSheetOpen)
        vm.closeBehaviorSheet()
        assertNull(vm.state.value.behaviorError)
    }

    @Test
    fun sensorDisconnectedWhileIdleDoesNotUseConfigurationReason() = runTest {
        val vm = EquipmentPlacementViewModel(InMemoryEquipmentLayoutRepository())
        attach(vm, "cfg-truth-idle-disc", PreviewMode.MY_GREENHOUSE)
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.CIRCULATION_FAN)
        vm.onLiveEnvironment(ArLiveEnvironmentReading.DISCONNECTED)
        assertFalse(vm.state.value.behaviorRunning)
        assertNotEquals(ArRuntimeCopy.CONFIGURATION_CHANGED, vm.state.value.behaviorError)
        assertNull(vm.state.value.behaviorError)
    }

    @Test
    fun realConfigurationChangeWhileRunningStopsAndShowsTheReason() = runTest {
        val vm = demoVm("cfg-truth-change")
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.CIRCULATION_FAN)
        vm.startBehavior()
        assertTrue(vm.state.value.behaviorRunning)
        attach(vm, "cfg-truth-changed", PreviewMode.DEMO)
        assertFalse(vm.state.value.behaviorRunning)
        assertEquals(ArRuntimeCopy.CONFIGURATION_CHANGED, vm.state.value.behaviorError)
        assertEquals(1, vm.state.value.instances.size)
    }

    @Test
    fun scientificDimensionChangeWhileRunningStopsVisualization() = runTest {
        val vm = demoVm("cfg-truth-dims")
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.CIRCULATION_FAN)
        vm.startBehavior()
        vm.attach(
            "grower@greenhands.app",
            "cfg-truth-dims",
            DemoGreenhouseProfile.LENGTH_M + 1.0,
            DemoGreenhouseProfile.WIDTH_M,
            DemoGreenhouseProfile.HEIGHT_M,
            DemoGreenhouseProfile.CROP_ROW_COUNT,
            climate,
            PreviewMode.DEMO
        )
        assertFalse(vm.state.value.behaviorRunning)
        assertEquals(ArRuntimeCopy.CONFIGURATION_CHANGED, vm.state.value.behaviorError)
    }

    @Test
    fun freshArSessionClearsAnOldConfigurationReason() = runTest {
        val vm = demoVm("cfg-truth-stale")
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.CIRCULATION_FAN)
        vm.startBehavior()
        attach(vm, "cfg-truth-stale-2", PreviewMode.DEMO)
        assertEquals(ArRuntimeCopy.CONFIGURATION_CHANGED, vm.state.value.behaviorError)
        vm.onLeaveArScreen()
        assertNull(vm.state.value.behaviorError)
        attach(vm, "cfg-truth-stale-2", PreviewMode.DEMO)
        assertNull(vm.state.value.behaviorError)
    }

    @Test
    fun demoInitializationDoesNotShowTheConfigurationReason() = runTest {
        val vm = demoVm("cfg-truth-demo-init")
        assertNull(vm.state.value.behaviorError)
        assertFalse(vm.state.value.behaviorRunning)
        vm.openBehaviorSheet()
        assertNull(vm.state.value.behaviorError)
    }

    @Test
    fun demoStoppedShowsNotRunningAndRunningShowsSimulated() = runTest {
        val vm = demoVm("cfg-truth-demo-labels")
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.CIRCULATION_FAN)
        assertEquals("Circulation Fan (1) — Not running", hud(vm).equipment[0].displayLine())
        assertEquals("Circulation Fan 1 — Not running", hud(vm).instanceStatuses.single().displayLine())
        vm.setSimulatedTemperature(31.0)
        vm.startBehavior()
        assertEquals("Circulation Fan (1) — 1 Simulated ON", hud(vm).equipment[0].displayLine())
        assertEquals("Circulation Fan 1 — Simulated ON", hud(vm).instanceStatuses.single().displayLine())
        vm.stopBehavior()
        assertEquals("Circulation Fan (1) — Not running", hud(vm).equipment[0].displayLine())
        assertEquals(31.0, vm.state.value.simulatedTemperatureC)
        assertEquals(1, vm.state.value.instances.size)
    }

    @Test
    fun myGreenhouseMissingAndStaleAndFreshStoppedAreNotEvaluated() = runTest {
        val clock = FakeRuntimeClock(0L)
        val vm = EquipmentPlacementViewModel(
            InMemoryEquipmentLayoutRepository(),
            clock = clock
        )
        attach(vm, "cfg-truth-eval", PreviewMode.MY_GREENHOUSE)
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.CIRCULATION_FAN)
        assertEquals("Circulation Fan (1) — Not evaluated", hud(vm).equipment[0].displayLine())
        assertEquals(EquipmentPresentationCopy.NOT_EVALUATED, hud(vm).instanceStatuses.single().state)
        assertFalse(ArHudContent.usesOnOffRuntimeLabel(hud(vm).instanceStatuses.single().state))

        vm.onLiveEnvironment(
            ArLiveEnvironmentReading(31.0, 60.0, connected = true, timestampMillis = 0L)
        )
        clock.millis = FreshnessPolicy.DEFAULT_IOT_STALE_AFTER_MS
        assertEquals(ArRuntimeCopy.MY_GREENHOUSE_START_DISABLED, vm.startBlockReason())
        assertEquals("Circulation Fan (1) — Not evaluated", hud(vm).equipment[0].displayLine())

        clock.millis = 0L
        assertNull(vm.startBlockReason())
        assertEquals("Circulation Fan (1) — Not evaluated", hud(vm).equipment[0].displayLine())
        assertEquals("31.0 °C", hud(vm).liveTemperatureLabel)
        assertEquals("60.0 % RH", hud(vm).liveHumidityLabel)
    }

    @Test
    fun myGreenhouseRunningWithoutRelayShowsPredictedAndActualSurvivesStop() = runTest {
        val vm = EquipmentPlacementViewModel(InMemoryEquipmentLayoutRepository())
        attach(vm, "cfg-truth-predicted", PreviewMode.MY_GREENHOUSE)
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.CIRCULATION_FAN)
        val id = vm.state.value.instances.single().id
        vm.onLiveEnvironment(ArLiveEnvironmentReading(31.0, 60.0, connected = true))
        vm.startBehavior()
        assertEquals("Circulation Fan (1) — 1 Predicted ON", hud(vm).equipment[0].displayLine())
        assertEquals("Circulation Fan 1 — Predicted ON", hud(vm).instanceStatuses.single().displayLine())
        assertEquals(hud(vm).instanceStatuses.single().state, hud(vm).equipment[0].state.replace("1 ", ""))

        vm.onActualRelayState(mapOf(id to EquipmentRuntimeState.ON))
        assertEquals("Circulation Fan 1 — Actual ON", hud(vm).instanceStatuses.single().displayLine())
        vm.stopBehavior()
        assertFalse(vm.state.value.behaviorRunning)
        assertEquals("Circulation Fan 1 — Actual ON", hud(vm).instanceStatuses.single().displayLine())
        assertEquals("Circulation Fan (1) — 1 Actual ON", hud(vm).equipment[0].displayLine())
        assertEquals(EquipmentRuntimeState.NOT_RUNNING, vm.state.value.runtimeById[id])
        assertEquals(1, vm.state.value.instances.single().displayNumber)
    }

    @Test
    fun stopVisualizationRetainsActualReadingAndDoesNotDisconnectSources() = runTest {
        val liveSource = InMemoryLiveEnvironmentSource()
        val weatherSource = InMemoryWeatherBoundarySource(
            WeatherBoundary.estimated(22.0, 55.0, 0L, 0L, 7.0, 80.0, 0L, FreshnessPolicy())
        )
        val vm = EquipmentPlacementViewModel(InMemoryEquipmentLayoutRepository())
        attach(vm, "cfg-truth-live", PreviewMode.MY_GREENHOUSE)
        vm.bindLiveEnvironmentSource(liveSource)
        vm.bindWeatherBoundarySource(weatherSource)
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.CIRCULATION_FAN)
        val beforePose = vm.state.value.instances.single().positionM
        val beforeYaw = vm.state.value.instances.single().yawDeg
        vm.onLiveEnvironment(ArLiveEnvironmentReading(31.0, 60.0, connected = true))
        liveSource.publish(ArLiveEnvironmentReading(31.0, 60.0, connected = true))
        vm.startBehavior()
        assertTrue(vm.state.value.behaviorRunning)
        vm.stopBehavior()
        assertEquals(0, liveSource.disconnectCount)
        assertEquals(0, liveSource.closeCount)
        assertEquals(0, weatherSource.disconnectCount)
        assertEquals(0, weatherSource.closeCount)
        assertEquals(31.0, vm.state.value.liveEnvironment.temperatureC)
        assertEquals(60.0, vm.state.value.liveEnvironment.relativeHumidityPercent)
        assertTrue(vm.state.value.liveEnvironment.connected)
        assertEquals(WeatherBoundaryStatus.AVAILABLE, vm.state.value.weatherBoundary.status)
        assertEquals(weatherSource.latest().outsideTemperatureC, vm.state.value.weatherBoundary.outsideTemperatureC)
        assertEquals(beforePose, vm.state.value.instances.single().positionM)
        assertEquals(beforeYaw, vm.state.value.instances.single().yawDeg, 0.0)
        assertEquals(1, vm.state.value.instances.size)

        vm.onLiveEnvironment(ArLiveEnvironmentReading(28.4, 58.0, connected = true))
        assertEquals("28.4 °C", hud(vm).liveTemperatureLabel)
        assertEquals("58.0 % RH", hud(vm).liveHumidityLabel)
        assertFalse(vm.state.value.behaviorRunning)
        assertEquals(EquipmentPresentationCopy.NOT_EVALUATED, hud(vm).instanceStatuses.single().state)
    }

    @Test
    fun stopDoesNotPersistPredictedStatesOrClearLayout() = runTest {
        val repo = InMemoryEquipmentLayoutRepository()
        val vm = EquipmentPlacementViewModel(repo)
        attach(vm, "cfg-truth-save", PreviewMode.MY_GREENHOUSE)
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.CIRCULATION_FAN)
        vm.onLiveEnvironment(ArLiveEnvironmentReading(31.0, 60.0, connected = true))
        vm.startBehavior()
        assertEquals(EquipmentRuntimeState.ON, vm.state.value.runtimeById.values.single())
        vm.stopBehavior()
        vm.saveLayout()
        val stored = repo.snapshot("grower@greenhands.app").single()
        assertEquals(EquipmentRuntimeState.NOT_RUNNING, stored.instances.single().runtimeState)
        assertFalse(EquipmentLayoutCodec.encode(stored).contains(",ON,"))
        assertEquals(1, stored.instances.size)
        assertEquals(vm.state.value.instances.single().id, stored.instances.single().id)
    }

    @Test
    fun weatherMissingDoesNotBlockStartWhenCirculationCanRun() = runTest {
        val vm = EquipmentPlacementViewModel(InMemoryEquipmentLayoutRepository())
        attach(vm, "cfg-truth-weather", PreviewMode.MY_GREENHOUSE)
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.CIRCULATION_FAN)
        vm.onLiveEnvironment(ArLiveEnvironmentReading(31.0, 60.0, connected = true))
        assertNull(vm.state.value.weatherBoundary.outsideTemperatureC)
        assertNull(vm.startBlockReason())
        vm.startBehavior()
        assertTrue(vm.state.value.behaviorRunning)
        assertEquals("Circulation Fan 1 — Predicted ON", hud(vm).instanceStatuses.single().displayLine())
    }

    @Test
    fun leavingArCleansPhysicsWithoutDisconnectingLiveSource() = runTest {
        val liveSource = InMemoryLiveEnvironmentSource()
        val vm = demoVm("cfg-truth-leave")
        vm.bindLiveEnvironmentSource(liveSource)
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.CIRCULATION_FAN)
        vm.startBehavior()
        assertNotNull(vm.state.value.physicsSnapshot)
        vm.onLeaveArScreen()
        assertNull(vm.state.value.physicsSnapshot)
        assertFalse(vm.state.value.behaviorRunning)
        assertEquals(0, liveSource.disconnectCount)
        assertEquals(0, liveSource.closeCount)
        assertEquals(1, vm.state.value.instances.size)
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

    private fun hud(vm: EquipmentPlacementViewModel) = ArHudContent.from(
        climate,
        vm.state.value.previewMode,
        live = vm.state.value.liveEnvironment,
        instances = vm.state.value.instances,
        runtimeById = vm.state.value.runtimeById,
        simulatedTemperatureC = vm.state.value.simulatedTemperatureC,
        simulatedHumidityPercent = vm.state.value.simulatedHumidityPercent,
        visualizationRunning = vm.state.value.behaviorRunning,
        actualRelayById = vm.state.value.actualRelayById
    )
}
