package com.greenhands.app

import com.greenhands.app.environment.GreenhouseConnectionState
import com.greenhands.app.environment.UnconnectedEnvironment
import com.greenhands.app.harvest.ar.HarvestArPlacementController
import com.greenhands.app.harvest.data.HarvestRecordFactory
import com.greenhands.app.harvest.data.HarvestTelemetryEnvironmentRepository
import com.greenhands.app.harvest.data.InMemoryScanHistoryRepository
import com.greenhands.app.harvest.domain.HarvestDecisionEngine
import com.greenhands.app.harvest.domain.HarvestSensorUiState
import com.greenhands.app.harvest.domain.MaturityCalculator
import com.greenhands.app.harvest.integration.HarvestDecisionMakingBridge
import com.greenhands.app.harvest.model.FruitColorMeasurement
import com.greenhands.app.harvest.ui.HarvestCameraBindings
import com.greenhands.app.harvest.ui.HarvestViewModel
import com.greenhands.app.heat.advisory.TooColdAdvisoryEvaluator
import com.greenhands.app.telemetry.FakeGreenhouseTelemetryRepository
import com.greenhands.app.telemetry.GreenhouseTelemetryState
import com.greenhands.app.telemetry.ReferenceCountedGreenhouseTelemetryRepository
import com.greenhands.app.telemetry.TelemetryFixtures
import com.greenhands.app.ui.navigation.Routes
import com.greenhands.app.ui.screens.DashboardModule
import com.greenhands.app.ui.screens.dashboardModuleRoute
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HarvestCanonicalIntegrationTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val engine = HarvestDecisionEngine()

    @Before
    fun setMain() {
        Dispatchers.setMain(dispatcher)
        HarvestDecisionMakingBridge.clear()
        HarvestCameraBindings.resetForTests()
    }

    @After
    fun reset() {
        Dispatchers.resetMain()
        HarvestDecisionMakingBridge.clear()
        HarvestCameraBindings.resetForTests()
    }

    @Test
    fun dashboardHarvestingButtonOpensHarvestHomeNotComingSoon() {
        val heat = DashboardModule(Routes.HEAT_DISTRIBUTION, "Heat", "body", isHeat = true)
        val harvest = DashboardModule(Routes.HARVESTING, "Harvesting", "body")
        val decision = DashboardModule(Routes.DECISION_MAKING, "Decision", "body")
        val sensor = DashboardModule(Routes.SENSOR_PLACEMENT, "Sensor", "body")
        assertEquals(Routes.CROPS, dashboardModuleRoute(heat))
        assertEquals(Routes.HARVESTING, dashboardModuleRoute(harvest))
        assertNotEquals(Routes.comingSoon(Routes.HARVESTING), dashboardModuleRoute(harvest))
        assertEquals(
            Routes.decisionMaking(com.greenhands.app.decision.DecisionSection.ALL),
            dashboardModuleRoute(decision)
        )
        assertEquals(Routes.SENSOR_SETUP, dashboardModuleRoute(sensor))
    }

    @Test
    fun harvestBackMapsToExistingDashboardWithoutHeatWorkspace() {
        assertEquals(Routes.DASHBOARD, Routes.topLevelFor(Routes.HARVESTING))
        assertFalse(Routes.isHeatContentRoute(Routes.HARVESTING))
        assertFalse(Routes.shouldShowHeatNavigation(Routes.HARVESTING, heatWorkspaceActive = false))
        assertFalse(Routes.shouldShowHeatNavigation(Routes.DASHBOARD, heatWorkspaceActive = true))
    }

    @Test
    fun freshTemperatureCSuppliesHarvestTemperature() {
        val live = GreenhouseTelemetryState.Live(TelemetryFixtures.accepted(temperatureC = 28.41))
        val snapshot = HarvestTelemetryEnvironmentRepository.map(live)
        assertEquals(GreenhouseConnectionState.LIVE, snapshot.connectionState)
        assertEquals(28.41, snapshot.temperatureC!!, 0.0)
        assertEquals("28.4°C", HarvestSensorUiState.from(snapshot).temperatureText)
    }

    @Test
    fun freshHumidityPctSuppliesHarvestHumidity() {
        val live = GreenhouseTelemetryState.Live(TelemetryFixtures.accepted(humidityPct = 61.25))
        val snapshot = HarvestTelemetryEnvironmentRepository.map(live)
        assertEquals(61.25, snapshot.relativeHumidityPercent!!, 0.0)
        assertEquals("61%", HarvestSensorUiState.from(snapshot).humidityText)
        assertEquals(HarvestSensorUiState.STATUS_LIVE, HarvestSensorUiState.from(snapshot).statusText)
    }

    @Test
    fun rawHtu21dTemperatureIsNeverMappedAsAppTemperature() {
        val dto = TelemetryFixtures.current(temperatureC = 28.41, htu21dTemperatureC = 40.00)
        val live = GreenhouseTelemetryState.Live(
            TelemetryFixtures.accepted(temperatureC = dto.temperatureC!!, humidityPct = dto.humidityPct!!)
        )
        val snapshot = HarvestTelemetryEnvironmentRepository.map(live)
        assertEquals(28.41, snapshot.temperatureC!!, 0.0)
        assertNotEquals(40.00, snapshot.temperatureC)
        assertNotEquals((28.41 + 40.00) / 2.0, snapshot.temperatureC)
    }

    @Test
    fun staleDisconnectedInvalidTelemetryIsNotLive() {
        val stale = HarvestTelemetryEnvironmentRepository.map(
            GreenhouseTelemetryState.Stale(TelemetryFixtures.accepted())
        )
        val disconnected = HarvestTelemetryEnvironmentRepository.map(
            GreenhouseTelemetryState.Disconnected(TelemetryFixtures.accepted())
        )
        val connecting = HarvestTelemetryEnvironmentRepository.map(GreenhouseTelemetryState.Connecting)
        assertEquals(GreenhouseConnectionState.OFFLINE_DELAYED, stale.connectionState)
        assertNull(stale.temperatureC)
        assertNull(stale.relativeHumidityPercent)
        assertEquals(HarvestSensorUiState.STATUS_STALE, HarvestSensorUiState.from(stale).statusText)
        assertFalse(HarvestSensorUiState.from(stale).showsLiveValues)
        assertEquals(GreenhouseConnectionState.DISCONNECTED, disconnected.connectionState)
        assertNull(disconnected.temperatureC)
        assertEquals(HarvestSensorUiState.STATUS_NO_DATA, HarvestSensorUiState.from(disconnected).statusText)
        assertEquals(GreenhouseConnectionState.CONNECTING, connecting.connectionState)
        assertEquals(HarvestSensorUiState.STATUS_CONNECTING, HarvestSensorUiState.from(connecting).statusText)
    }

    @Test
    fun staleDataIsNotSavedAsLiveScanContext() {
        val staleMapped = HarvestTelemetryEnvironmentRepository.map(
            GreenhouseTelemetryState.Stale(TelemetryFixtures.accepted(temperatureC = 28.41, humidityPct = 61.25))
        )
        val record = HarvestRecordFactory.fruit(
            decision = engine.decideTomato(
                fruit(green = 38f, yellow = 34f, red = 18f, brown = 6f),
                MaturityCalculator.assess(75, 65, 90)
            ),
            cropType = "Tomato",
            variety = null,
            transplantDateUtcMillis = null,
            daysSinceTransplant = 75,
            environment = staleMapped,
            id = "stale-1",
            scannedAtEpochMillis = 1L
        )!!
        assertNull(record.temperatureC)
        assertNull(record.humidityPercent)
        assertNotEquals("LIVE", record.environmentSource)
    }

    @Test
    fun harvestingReusesOneTelemetryListener() {
        val inner = FakeGreenhouseTelemetryRepository()
        val shared = ReferenceCountedGreenhouseTelemetryRepository(inner)
        val harvest = HarvestTelemetryEnvironmentRepository(shared)
        harvest.acquire()
        shared.startMonitoring()
        assertEquals(1, inner.startCount)
        assertEquals(2, shared.subscriberCount)
        assertSame(inner, shared.innerRepository)
        harvest.release()
        assertEquals(0, inner.stopCount)
        assertTrue(inner.monitoring)
        shared.stopMonitoring()
        assertEquals(1, inner.stopCount)
        assertFalse(inner.monitoring)
    }

    @Test
    fun multipleTelemetrySubscribersCannotStopEachOther() {
        val inner = FakeGreenhouseTelemetryRepository()
        val shared = ReferenceCountedGreenhouseTelemetryRepository(inner)
        shared.startMonitoring()
        shared.startMonitoring()
        assertEquals(1, inner.startCount)
        assertEquals(2, shared.subscriberCount)
        shared.stopMonitoring()
        assertEquals(0, inner.stopCount)
        assertTrue(inner.monitoring)
        shared.stopMonitoring()
        assertEquals(1, inner.stopCount)
        assertFalse(inner.monitoring)
    }

    @Test
    fun finalSubscriberRemovesTheListener() {
        val inner = FakeGreenhouseTelemetryRepository()
        val shared = ReferenceCountedGreenhouseTelemetryRepository(inner)
        shared.startMonitoring()
        shared.startMonitoring()
        shared.stopMonitoring()
        assertTrue(inner.monitoring)
        shared.stopMonitoring()
        assertFalse(inner.monitoring)
        assertEquals(1, inner.stopCount)
        shared.stopMonitoring()
        assertEquals(1, inner.stopCount)
    }

    @Test
    fun harvestEnterLeaveDoesNotStartOnDashboardAlone() {
        val inner = FakeGreenhouseTelemetryRepository()
        val shared = ReferenceCountedGreenhouseTelemetryRepository(inner)
        val adapter = HarvestTelemetryEnvironmentRepository(shared)
        val vm = HarvestViewModel(environmentRepository = adapter)
        assertEquals(0, inner.startCount)
        assertEquals(UnconnectedEnvironment.snapshot.connectionState, vm.state.value.environment.connectionState)
        vm.enterHarvesting()
        assertEquals(1, inner.startCount)
        vm.leaveHarvesting()
        assertEquals(1, inner.stopCount)
        assertFalse(inner.monitoring)
    }

    @Test
    fun recompositionDoesNotDuplicateHarvestTelemetryAcquire() = runTest {
        val inner = FakeGreenhouseTelemetryRepository()
        val adapter = HarvestTelemetryEnvironmentRepository(
            ReferenceCountedGreenhouseTelemetryRepository(inner)
        )
        val vm = HarvestViewModel(environmentRepository = adapter)
        vm.enterHarvesting()
        vm.enterHarvesting()
        assertEquals(1, inner.startCount)
        vm.leaveHarvesting()
        vm.leaveHarvesting()
        assertEquals(1, inner.stopCount)
    }

    @Test
    fun cameraBindingsReleaseAfterLeavingScan() {
        HarvestCameraBindings.resetForTests()
        HarvestCameraBindings.onBound()
        assertEquals(1, HarvestCameraBindings.active)
        HarvestCameraBindings.onReleased()
        assertEquals(0, HarvestCameraBindings.active)
        assertEquals(1, HarvestCameraBindings.releaseCount)
        assertEquals(HarvestCameraBindings.bindCount, HarvestCameraBindings.releaseCount)
    }

    @Test
    fun harvestHistorySurvivesViewModelRecreation() = runTest {
        val repo = InMemoryScanHistoryRepository()
        val first = HarvestViewModel(
            scanHistoryRepository = repo,
            clock = { 10L },
            newRecordId = { "kept" }
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            first.state.collect {}
        }
        val decision = engine.decideTomato(
            fruit(green = 38f, yellow = 34f, red = 18f, brown = 6f),
            MaturityCalculator.assess(75, 65, 90)
        )
        first.saveFruitScan(decision)
        val second = HarvestViewModel(scanHistoryRepository = repo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            second.state.collect {}
        }
        assertEquals("kept", second.state.value.recentScans.single().id)
        assertEquals(1, second.state.value.recentScans.size)
    }

    @Test
    fun decisionMakingUsesExistingCanonicalDestination() {
        assertEquals(
            Routes.decisionMaking(com.greenhands.app.decision.DecisionSection.INFECTIONS),
            HarvestDecisionMakingBridge.destinationRoute()
        )
        assertNotEquals(Routes.decisionSupportDestination(), HarvestDecisionMakingBridge.destinationRoute())
        assertTrue(HarvestDecisionMakingBridge.isDecisionMakingScreenImplemented())
    }

    @Test
    fun heatDemoMakesZeroFirebaseCalls() {
        val telemetry = FakeGreenhouseTelemetryRepository()
        val vm = com.greenhands.app.equipment.ui.EquipmentPlacementViewModel(
            com.greenhands.app.equipment.data.InMemoryEquipmentLayoutRepository(),
            telemetryRepository = telemetry
        )
        vm.attach(
            "grower@greenhands.app",
            "cfg-telemetry",
            com.greenhands.app.preview3d.model.DemoGreenhouseProfile.LENGTH_M,
            com.greenhands.app.preview3d.model.DemoGreenhouseProfile.WIDTH_M,
            com.greenhands.app.preview3d.model.DemoGreenhouseProfile.HEIGHT_M,
            com.greenhands.app.preview3d.model.DemoGreenhouseProfile.CROP_ROW_COUNT,
            com.greenhands.app.equipment.ui.OverlayClimate(
                crop = null,
                stage = null,
                temperatureLabel = "25.0°C",
                humidityLabel = "70.0 % RH",
                vpdLabel = "Not published",
                circulation = com.greenhands.app.heat.domain.HeatFormulas.circulation(25.0),
                exhaust = com.greenhands.app.heat.domain.HeatFormulas.exhaustFromCirculation(
                    com.greenhands.app.heat.domain.HeatFormulas.circulation(25.0)
                ),
                fogger = com.greenhands.app.heat.domain.HeatFormulas.fogger(70.0),
                optimalTemperatureC = 25.0,
                optimalHumidityPercent = 70.0
            ),
            com.greenhands.app.preview3d.model.PreviewMode.DEMO
        )
        assertEquals(0, telemetry.startCount)
        assertFalse(telemetry.monitoring)
    }

    @Test
    fun tooColdAdvisoryEvaluatorRemainsPresent() {
        assertTrue(TooColdAdvisoryEvaluator.javaClass.name.contains("TooCold"))
    }

    @Test
    fun harvestArPlacementDestroyIsIdempotentAndNonCrashing() {
        val controller = HarvestArPlacementController()
        controller.markPlaced(true)
        assertTrue(controller.placed)
        controller.destroy()
        assertTrue(controller.destroyed)
        assertFalse(controller.placed)
        controller.markPlaced(true)
        assertFalse(controller.placed)
        controller.destroy()
        assertTrue(controller.destroyed)
    }

    private fun fruit(
        green: Float,
        yellow: Float,
        red: Float,
        brown: Float,
        samples: Int = 1000
    ) = FruitColorMeasurement(
        sampledPixelCount = samples,
        hueMean = 80f,
        saturationMean = 0.55f,
        valueMean = 0.52f,
        greenPercent = green,
        yellowPercent = yellow,
        redPercent = red,
        brownDarkPercent = brown,
        otherPercent = (100f - green - yellow - red - brown).coerceAtLeast(0f)
    )
}
