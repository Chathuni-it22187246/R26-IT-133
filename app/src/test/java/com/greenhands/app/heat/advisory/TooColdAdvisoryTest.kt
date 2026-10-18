package com.greenhands.app.heat.advisory

import com.greenhands.app.ar.domain.ArLiveEnvironmentReading
import com.greenhands.app.ar.domain.LiveSensorUiState
import com.greenhands.app.equipment.data.InMemoryEquipmentLayoutRepository
import com.greenhands.app.equipment.ui.EquipmentPlacementViewModel
import com.greenhands.app.equipment.ui.OverlayClimate
import com.greenhands.app.heat.domain.CropDefaults
import com.greenhands.app.heat.domain.HeatFormulas
import com.greenhands.app.heat.model.Crop
import com.greenhands.app.heat.visualization.VisualizationBandSettings
import com.greenhands.app.preview3d.model.DemoGreenhouseProfile
import com.greenhands.app.preview3d.model.PreviewMode
import com.greenhands.app.telemetry.FakeGreenhouseTelemetryRepository
import com.greenhands.app.telemetry.GreenhouseTelemetryState
import com.greenhands.app.telemetry.TelemetryFixtures
import com.greenhands.app.ui.navigation.NavActions
import com.greenhands.app.ui.navigation.Routes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
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
class TooColdAdvisoryTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val stage = CropDefaults.stage(Crop.TOMATO, "vegetative")
    private val optimalC = 25.0
    private val climate = OverlayClimate(
        crop = Crop.TOMATO,
        stage = stage,
        temperatureLabel = "Optimal temperature: 25.0°C",
        humidityLabel = "Optimal humidity: 75.0 % RH",
        vpdLabel = "Not published",
        circulation = HeatFormulas.circulation(optimalC),
        exhaust = HeatFormulas.exhaustFromCirculation(HeatFormulas.circulation(optimalC)),
        fogger = HeatFormulas.fogger(75.0),
        optimalTemperatureC = optimalC,
        optimalHumidityPercent = 75.0
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
    fun thresholdSourceIsResolvedOptimalAndExistingTargetBand() {
        assertEquals(0.5, VisualizationBandSettings.TARGET_BAND_HALF_WIDTH_C, 0.0)
        assertEquals(24.5, TooColdAdvisoryEvaluator.lowerOptimalBoundaryC(optimalC), 0.0)
        assertEquals(23.0, climate.circulation!!.cdp, 0.0)
        assertTrue(TooColdAdvisoryCopy.THRESHOLD_SOURCE.contains("optimalTemperatureC"))
        assertTrue(TooColdAdvisoryCopy.THRESHOLD_SOURCE.contains("TARGET"))
    }

    @Test
    fun freshTemperatureBelowOptimalShowsTooCold() {
        val shown = TooColdAdvisoryEvaluator.evaluate(
            mode = PreviewMode.MY_GREENHOUSE,
            climate = climate,
            simulatedTemperatureC = 99.0,
            live = live(22.0),
            previouslyVisible = false
        )
        assertNotNull(shown)
        assertEquals(22.0, shown!!.currentTemperatureC, 0.0)
        assertEquals(optimalC, shown.optimalTemperatureC, 0.0)
        assertEquals(24.5, shown.lowerBoundaryC, 0.0)
        assertEquals("22.0 °C", shown.currentLabel)
        assertEquals("25.0 °C", shown.optimalLabel)
        assertEquals("Too cold", TooColdAdvisoryCopy.TITLE)
        assertEquals(
            "The greenhouse temperature is below the optimal level for the selected crop stage.",
            TooColdAdvisoryCopy.BODY
        )
    }

    @Test
    fun temperatureAtOrAboveOptimalHidesIt() {
        assertNull(
            TooColdAdvisoryEvaluator.evaluate(
                mode = PreviewMode.MY_GREENHOUSE,
                climate = climate,
                simulatedTemperatureC = null,
                live = live(optimalC),
                previouslyVisible = true
            )
        )
        assertNull(
            TooColdAdvisoryEvaluator.evaluate(
                mode = PreviewMode.MY_GREENHOUSE,
                climate = climate,
                simulatedTemperatureC = null,
                live = live(26.0),
                previouslyVisible = true
            )
        )
    }

    @Test
    fun staleOrDisconnectedMyGreenhouseDataNeverTriggers() {
        val cold = 18.0
        assertNull(
            TooColdAdvisoryEvaluator.evaluate(
                mode = PreviewMode.MY_GREENHOUSE,
                climate = climate,
                simulatedTemperatureC = cold,
                live = ArLiveEnvironmentReading(
                    temperatureC = cold,
                    connected = false,
                    timestampMillis = TelemetryFixtures.NOW_MS,
                    uiState = LiveSensorUiState.STALE
                ),
                previouslyVisible = false
            )
        )
        assertNull(
            TooColdAdvisoryEvaluator.evaluate(
                mode = PreviewMode.MY_GREENHOUSE,
                climate = climate,
                simulatedTemperatureC = cold,
                live = ArLiveEnvironmentReading(
                    temperatureC = cold,
                    connected = false,
                    timestampMillis = TelemetryFixtures.NOW_MS,
                    uiState = LiveSensorUiState.DISCONNECTED
                ),
                previouslyVisible = true
            )
        )
        assertNull(
            TooColdAdvisoryEvaluator.evaluate(
                mode = PreviewMode.MY_GREENHOUSE,
                climate = climate,
                simulatedTemperatureC = cold,
                live = ArLiveEnvironmentReading.DISCONNECTED,
                previouslyVisible = false
            )
        )
        assertNull(
            TooColdAdvisoryEvaluator.evaluate(
                mode = PreviewMode.MY_GREENHOUSE,
                climate = climate,
                simulatedTemperatureC = cold,
                live = live(Double.NaN),
                previouslyVisible = false
            )
        )
        assertNull(
            TooColdAdvisoryEvaluator.trustedTemperatureC(
                PreviewMode.MY_GREENHOUSE,
                simulatedTemperatureC = 10.0,
                live = live(18.0).copy(uiState = LiveSensorUiState.CONNECTING, connected = false)
            )
        )
    }

    @Test
    fun demoUsesSimulatedTemperatureWithoutFirebase() = runTest {
        val telemetry = FakeGreenhouseTelemetryRepository()
        val vm = EquipmentPlacementViewModel(
            InMemoryEquipmentLayoutRepository(),
            telemetryRepository = telemetry
        )
        attach(vm, PreviewMode.DEMO)
        telemetry.publish(GreenhouseTelemetryState.Live(TelemetryFixtures.accepted(temperatureC = 10.0)))
        vm.onLiveEnvironment(
            ArLiveEnvironmentReading(
                temperatureC = 10.0,
                relativeHumidityPercent = 60.0,
                connected = true,
                timestampMillis = TelemetryFixtures.NOW_MS,
                uiState = LiveSensorUiState.LIVE
            )
        )
        assertEquals(0, telemetry.startCount)
        assertFalse(telemetry.monitoring)
        assertEquals(optimalC, vm.state.value.simulatedTemperatureC)
        assertNull(vm.state.value.tooColdAdvisory)

        vm.setSimulatedTemperature(20.0)
        val advisory = vm.state.value.tooColdAdvisory
        assertNotNull(advisory)
        assertEquals(20.0, advisory!!.currentTemperatureC, 0.0)
        assertEquals(optimalC, advisory.optimalTemperatureC, 0.0)
        assertEquals(0, telemetry.startCount)
        assertEquals(20.0, vm.runtimeInput().temperatureC)
    }

    @Test
    fun alertDoesNotFlickerAcrossExistingHysteresisBoundary() {
        val lower = TooColdAdvisoryEvaluator.lowerOptimalBoundaryC(optimalC)
        assertEquals(24.5, lower, 0.0)

        var visible = evaluateDemo(24.4, previouslyVisible = false)
        assertNotNull(visible)

        visible = evaluateDemo(24.5, previouslyVisible = true)
        assertNotNull(visible)
        visible = evaluateDemo(24.9, previouslyVisible = true)
        assertNotNull(visible)

        visible = evaluateDemo(25.0, previouslyVisible = true)
        assertNull(visible)
        visible = evaluateDemo(24.9, previouslyVisible = false)
        assertNull(visible)
        visible = evaluateDemo(24.5, previouslyVisible = false)
        assertNull(visible)

        visible = evaluateDemo(24.499, previouslyVisible = false)
        assertNotNull(visible)
    }

    @Test
    fun viewSuggestionsInvokesDecisionSupportNavigationCallbackOnce() = runTest {
        val vm = demoVm()
        vm.setSimulatedTemperature(21.0)
        assertNotNull(vm.state.value.tooColdAdvisory)
        var calls = 0
        vm.onViewTooColdSuggestions { calls += 1 }
        assertEquals(1, calls)
        vm.onViewTooColdSuggestions { calls += 1 }
        assertEquals(2, calls)
        TooColdAdvisoryEvaluator.onViewSuggestions { calls += 1 }
        assertEquals(3, calls)
        assertEquals(
            Routes.decisionMaking(com.greenhands.app.decision.DecisionSection.HEATER_FANS),
            Routes.decisionSupportDestination()
        )
        assertEquals("decision_making/heater_fans", Routes.decisionSupportDestination())
    }

    @Test
    fun recompositionDoesNotNavigate() = runTest {
        val vm = demoVm()
        vm.setSimulatedTemperature(21.0)
        var calls = 0
        repeat(8) {
            val again = TooColdAdvisoryEvaluator.evaluate(
                mode = PreviewMode.DEMO,
                climate = climate,
                simulatedTemperatureC = vm.state.value.simulatedTemperatureC,
                live = live(8.0),
                previouslyVisible = vm.state.value.tooColdAdvisory != null
            )
            assertNotNull(again)
        }
        vm.setSimulatedTemperature(21.0)
        vm.setSimulatedTemperature(20.5)
        assertEquals(0, calls)
        vm.onViewTooColdSuggestions { calls += 1 }
        assertEquals(1, calls)
    }

    @Test
    fun backNavigationRemainsOnHeatDistribution() {
        val stack = mutableListOf(
            Routes.HEAT_DISTRIBUTION,
            Routes.GREENHOUSE_PREVIEW,
            Routes.GREENHOUSE_AR
        )
        val destination = Routes.decisionSupportDestination()
        assertEquals("decision_making/heater_fans", destination)
        stack += destination
        assertEquals(destination, stack.last())
        stack.removeAt(stack.lastIndex)
        assertEquals(Routes.GREENHOUSE_AR, stack.last())
        assertTrue(Routes.isHeatContentRoute(Routes.GREENHOUSE_AR))
        assertEquals(Routes.SIMULATION, Routes.topLevelFor(Routes.GREENHOUSE_AR, heatNavigation = true))
        assertFalse(Routes.heatContentRoutes.contains(destination))
        assertEquals(NavActions::openDecisionSupport.name, "openDecisionSupport")
    }

    @Test
    fun myGreenhouseLiveBelowOptimalShowsAndStaleHides() = runTest {
        val telemetry = FakeGreenhouseTelemetryRepository()
        val vm = EquipmentPlacementViewModel(
            InMemoryEquipmentLayoutRepository(),
            telemetryRepository = telemetry
        )
        attach(vm, PreviewMode.MY_GREENHOUSE)
        assertNull(vm.state.value.tooColdAdvisory)
        vm.onLiveEnvironment(live(19.0))
        assertNotNull(vm.state.value.tooColdAdvisory)
        assertEquals(19.0, vm.state.value.tooColdAdvisory!!.currentTemperatureC, 0.0)
        vm.onLiveEnvironment(
            ArLiveEnvironmentReading(
                temperatureC = 19.0,
                relativeHumidityPercent = 60.0,
                connected = false,
                timestampMillis = TelemetryFixtures.NOW_MS,
                uiState = LiveSensorUiState.STALE
            )
        )
        assertNull(vm.state.value.tooColdAdvisory)
        vm.onLiveEnvironment(live(19.0))
        assertNotNull(vm.state.value.tooColdAdvisory)
        vm.onLiveEnvironment(
            ArLiveEnvironmentReading(
                temperatureC = 19.0,
                connected = false,
                uiState = LiveSensorUiState.DISCONNECTED
            )
        )
        assertNull(vm.state.value.tooColdAdvisory)
    }

    @Test
    fun hiddenAdvisoryDoesNotNavigate() = runTest {
        val vm = demoVm()
        assertNull(vm.state.value.tooColdAdvisory)
        var calls = 0
        vm.onViewTooColdSuggestions { calls += 1 }
        assertEquals(0, calls)
    }

    private fun evaluateDemo(temperatureC: Double, previouslyVisible: Boolean): TooColdAdvisory? =
        TooColdAdvisoryEvaluator.evaluate(
            mode = PreviewMode.DEMO,
            climate = climate,
            simulatedTemperatureC = temperatureC,
            live = live(8.0),
            previouslyVisible = previouslyVisible
        )

    private fun live(temperatureC: Double): ArLiveEnvironmentReading = ArLiveEnvironmentReading(
        temperatureC = temperatureC,
        relativeHumidityPercent = 70.0,
        connected = true,
        timestampMillis = TelemetryFixtures.NOW_MS,
        uiState = LiveSensorUiState.LIVE
    )

    private fun demoVm(): EquipmentPlacementViewModel {
        val vm = EquipmentPlacementViewModel(InMemoryEquipmentLayoutRepository())
        attach(vm, PreviewMode.DEMO)
        return vm
    }

    private fun attach(vm: EquipmentPlacementViewModel, mode: PreviewMode) {
        vm.attach(
            "grower@greenhands.app",
            "cfg-too-cold",
            DemoGreenhouseProfile.LENGTH_M,
            DemoGreenhouseProfile.WIDTH_M,
            DemoGreenhouseProfile.HEIGHT_M,
            DemoGreenhouseProfile.CROP_ROW_COUNT,
            climate,
            mode
        )
    }
}
