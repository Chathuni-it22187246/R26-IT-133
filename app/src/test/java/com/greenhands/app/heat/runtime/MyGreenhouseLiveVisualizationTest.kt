package com.greenhands.app.heat.runtime

import com.greenhands.app.ar.domain.ArHudContent
import com.greenhands.app.ar.domain.ArRuntimeCopy
import com.greenhands.app.equipment.data.InMemoryEquipmentLayoutRepository
import com.greenhands.app.equipment.model.EquipmentRuntimeState
import com.greenhands.app.equipment.model.EquipmentType
import com.greenhands.app.equipment.ui.EquipmentPlacementViewModel
import com.greenhands.app.equipment.ui.OverlayClimate
import com.greenhands.app.heat.domain.HeatFormulas
import com.greenhands.app.heat.runtime.contracts.InsideClimateSource
import com.greenhands.app.heat.runtime.contracts.WeatherBoundaryKind
import com.greenhands.app.heat.runtime.contracts.WeatherBoundaryStatus
import com.greenhands.app.heat.visualization.EnvironmentalFieldAvailability
import com.greenhands.app.preview3d.model.DemoGreenhouseProfile
import com.greenhands.app.preview3d.model.PreviewMode
import com.greenhands.app.telemetry.FakeGreenhouseTelemetryRepository
import com.greenhands.app.telemetry.GreenhouseTelemetryState
import com.greenhands.app.telemetry.GreenhouseWeatherState
import com.greenhands.app.telemetry.TelemetryCopy
import com.greenhands.app.telemetry.TelemetryFixtures
import com.greenhands.app.telemetry.WeatherCopy
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
class MyGreenhouseLiveVisualizationTest {

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
    fun fieldRuntimeReceivesLiveIndoorAndFreshOutsideBoundaries() = runTest {
        val telemetry = FakeGreenhouseTelemetryRepository()
        val vm = liveVm("cfg-live-field", telemetry)
        telemetry.publish(GreenhouseTelemetryState.Live(TelemetryFixtures.accepted()))
        telemetry.publishWeather(TelemetryFixtures.liveWeatherState())
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.CIRCULATION_FAN)
        vm.startBehavior()
        val snap = vm.state.value.physicsSnapshot
        assertNotNull(snap)
        assertEquals(InsideClimateSource.ACTUAL_IOT, snap!!.insideSource)
        assertEquals(28.41, snap.meanTemperatureC, 1e-9)
        assertEquals(WeatherBoundaryStatus.AVAILABLE, snap.weatherStatus)
        assertEquals(WeatherBoundaryKind.ESTIMATED_OUTSIDE_WEATHER, snap.weatherKind)
        assertEquals(WeatherCopy.MODELLED_OPEN_METEO, snap.weatherSourceLabel)
        assertNotEquals(25.0, snap.meanTemperatureC, 0.0)
        assertNotEquals(70.0, snap.meanRelativeHumidityPercent, 0.0)
        assertFalse(snap.warnings.contains(PhysicsRuntimeCopy.DEMO_SIMULATION_DISCLAIMER))
        val field = vm.environmentalFieldSnapshot()
        assertEquals(EnvironmentalFieldAvailability.AVAILABLE, field.availability)
        assertEquals(28.41, field.physicsSnapshot!!.meanTemperatureC, 1e-9)
        assertNotEquals(
            snap.weatherSourceLabel,
            PhysicsRuntimeCopy.DEMO_SIMULATED_OUTSIDE_BOUNDARY
        )
    }

    @Test
    fun circulationRingsFollowCirculationOnOff() = runTest {
        val telemetry = FakeGreenhouseTelemetryRepository()
        val vm = liveVm("cfg-live-circ", telemetry)
        telemetry.publish(GreenhouseTelemetryState.Live(TelemetryFixtures.accepted(32.0, 60.0)))
        telemetry.publishWeather(TelemetryFixtures.liveWeatherState())
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.CIRCULATION_FAN)
        vm.startBehavior()
        val id = vm.state.value.instances.single().id
        assertEquals(EquipmentRuntimeState.ON, vm.state.value.runtimeById[id])
        assertFalse(vm.circulationEffectFrame().isEmpty)
        assertEquals(id, vm.circulationEffectFrame().groups.single().equipmentUuid)
        telemetry.publish(GreenhouseTelemetryState.Live(TelemetryFixtures.accepted(22.0, 60.0)))
        assertEquals(EquipmentRuntimeState.OFF, vm.state.value.runtimeById[id])
        assertTrue(vm.circulationEffectFrame().isEmpty)
        vm.stopBehavior()
        assertTrue(vm.circulationEffectFrame().isEmpty)
    }

    @Test
    fun exhaustArrowsFollowExhaustOnOffAndSurviveMissingWeather() = runTest {
        val telemetry = FakeGreenhouseTelemetryRepository()
        val vm = liveVm("cfg-live-exh", telemetry)
        telemetry.publish(GreenhouseTelemetryState.Live(TelemetryFixtures.accepted(34.0, 50.0)))
        telemetry.publishWeather(TelemetryFixtures.liveWeatherState())
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.EXHAUST_FAN)
        vm.startBehavior()
        val id = vm.state.value.instances.single().id
        assertEquals(EquipmentRuntimeState.ON, vm.state.value.runtimeById[id])
        assertFalse(vm.exhaustEffectFrame().isEmpty)
        assertEquals(id, vm.exhaustEffectFrame().groups.single().equipmentUuid)
        telemetry.publishWeather(GreenhouseWeatherState.Unavailable())
        assertTrue(vm.state.value.behaviorRunning)
        assertFalse(vm.exhaustEffectFrame().isEmpty)
        telemetry.publish(GreenhouseTelemetryState.Live(TelemetryFixtures.accepted(24.0, 50.0)))
        assertEquals(EquipmentRuntimeState.OFF, vm.state.value.runtimeById[id])
        assertTrue(vm.exhaustEffectFrame().isEmpty)
        vm.stopBehavior()
        assertTrue(vm.exhaustEffectFrame().isEmpty)
    }

    @Test
    fun fogMistFollowsFoggerOnOff() = runTest {
        val telemetry = FakeGreenhouseTelemetryRepository()
        val vm = liveVm("cfg-live-fog", telemetry)
        telemetry.publish(GreenhouseTelemetryState.Live(TelemetryFixtures.accepted(30.0, 40.0)))
        telemetry.publishWeather(TelemetryFixtures.liveWeatherState())
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.FOGGER)
        vm.startBehavior()
        val id = vm.state.value.instances.single().id
        assertEquals(EquipmentRuntimeState.ON, vm.state.value.runtimeById[id])
        assertFalse(vm.foggerEffectFrame().isEmpty)
        assertEquals(id, vm.foggerEffectFrame().groups.single().equipmentUuid)
        telemetry.publish(GreenhouseTelemetryState.Live(TelemetryFixtures.accepted(30.0, 80.0)))
        assertEquals(EquipmentRuntimeState.OFF, vm.state.value.runtimeById[id])
        assertTrue(vm.foggerEffectFrame().isEmpty)
        vm.stopBehavior()
        assertTrue(vm.foggerEffectFrame().isEmpty)
    }

    @Test
    fun staleWeatherIsNeverLiveAndDoesNotStopIndoorVisualization() = runTest {
        val telemetry = FakeGreenhouseTelemetryRepository()
        val vm = liveVm("cfg-live-stale-weather", telemetry)
        telemetry.publish(GreenhouseTelemetryState.Live(TelemetryFixtures.accepted()))
        val liveWeather = TelemetryFixtures.liveWeatherState()
        telemetry.publishWeather(liveWeather)
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.CIRCULATION_FAN)
        vm.startBehavior()
        assertTrue(vm.state.value.behaviorRunning)
        assertEquals(WeatherCopy.LIVE, ArHudContent.outsideStatusLabel(vm.state.value.weatherUi))
        val stale = com.greenhands.app.telemetry.GreenhouseWeatherEvaluator.classify(
            TelemetryFixtures.acceptedWeather(),
            TelemetryFixtures.NOW_MS + com.greenhands.app.telemetry.WeatherFreshnessPolicy.DEFAULT_STALE_AFTER_MS + 1L
        ).state
        telemetry.publishWeather(stale)
        assertTrue(vm.state.value.behaviorRunning)
        assertNotNull(vm.state.value.physicsSnapshot)
        assertEquals(WeatherCopy.STALE, ArHudContent.outsideStatusLabel(vm.state.value.weatherUi))
        assertNotEquals(WeatherCopy.LIVE, ArHudContent.outsideStatusLabel(vm.state.value.weatherUi))
        assertFalse(vm.state.value.weatherBoundary.permitsQuantitativeExhaust())
        assertEquals(TelemetryCopy.LIVE, ArHudContent.liveStatusLabel(vm.state.value.liveEnvironment))
    }

    @Test
    fun staleIndoorStopsOnceAndNeverLabelsCachedDataLive() = runTest {
        val telemetry = FakeGreenhouseTelemetryRepository()
        val vm = liveVm("cfg-live-stop-once", telemetry)
        telemetry.publish(GreenhouseTelemetryState.Live(TelemetryFixtures.accepted()))
        telemetry.publishWeather(TelemetryFixtures.liveWeatherState())
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.CIRCULATION_FAN)
        vm.startBehavior()
        telemetry.publish(GreenhouseTelemetryState.Stale(TelemetryFixtures.accepted()))
        assertFalse(vm.state.value.behaviorRunning)
        assertEquals(ArRuntimeCopy.INPUT_UNAVAILABLE, vm.state.value.behaviorError)
        val error = vm.state.value.behaviorError
        telemetry.publish(GreenhouseTelemetryState.Stale(TelemetryFixtures.accepted()))
        telemetry.publish(GreenhouseTelemetryState.Disconnected(TelemetryFixtures.accepted()))
        assertFalse(vm.state.value.behaviorRunning)
        assertEquals(error, vm.state.value.behaviorError)
        assertEquals(TelemetryCopy.DISCONNECTED, ArHudContent.liveStatusLabel(vm.state.value.liveEnvironment))
        assertNotEquals(TelemetryCopy.LIVE, ArHudContent.liveStatusLabel(vm.state.value.liveEnvironment))
        assertTrue(vm.circulationEffectFrame().isEmpty)
        assertTrue(vm.environmentalFieldSnapshot().samples.isEmpty())
    }

    @Test
    fun demoModeNeverStartsFirebaseAuthOrListeners() = runTest {
        val telemetry = FakeGreenhouseTelemetryRepository()
        val vm = EquipmentPlacementViewModel(
            InMemoryEquipmentLayoutRepository(),
            telemetryRepository = telemetry,
            manufacturerSpecsFor = { Phase3D2AFixtures.manufacturerSpecs(it) }
        )
        attach(vm, "cfg-demo-isolated", PreviewMode.DEMO)
        assertEquals(0, telemetry.startCount)
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.CIRCULATION_FAN)
        vm.setSimulatedTemperature(26.4)
        vm.setSimulatedHumidity(70.0)
        vm.startBehavior()
        assertTrue(vm.state.value.behaviorRunning)
        assertEquals(0, telemetry.startCount)
        assertFalse(telemetry.monitoring)
        assertEquals(ArHudContent.DEMO_SIMULATION, hud(vm).liveStatusLabel)
        assertFalse(hud(vm).showsOutsideWeather)
        assertNotEquals(WeatherCopy.MODELLED_OPEN_METEO, vm.state.value.physicsSnapshot!!.weatherSourceLabel)
    }

    private fun liveVm(
        configId: String,
        telemetry: FakeGreenhouseTelemetryRepository
    ): EquipmentPlacementViewModel {
        val vm = EquipmentPlacementViewModel(
            InMemoryEquipmentLayoutRepository(),
            telemetryRepository = telemetry,
            manufacturerSpecsFor = { Phase3D2AFixtures.manufacturerSpecs(it) }
        )
        attach(vm, configId, PreviewMode.MY_GREENHOUSE)
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
        weather = vm.state.value.weatherUi
    )
}
