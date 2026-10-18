package com.greenhands.app

import com.greenhands.app.decision.ApiConfig
import com.greenhands.app.decision.DecisionFilterSession
import com.greenhands.app.decision.DecisionIotClimate
import com.greenhands.app.decision.DecisionResponse
import com.greenhands.app.decision.DecisionSection
import com.greenhands.app.decision.GreenhouseTelemetryResponse
import com.greenhands.app.decision.applyRouteFilterOnce
import com.greenhands.app.decision.formatDecisionHumidityField
import com.greenhands.app.decision.formatDecisionTemperatureField
import com.greenhands.app.decision.heaterPredictRequestOrNull
import com.greenhands.app.decision.mergeDecisionEnvironment
import com.greenhands.app.decision.neededClimateActuator
import com.greenhands.app.environment.GreenhouseConnectionState
import com.greenhands.app.harvest.integration.HarvestDecisionMakingBridge
import com.greenhands.app.telemetry.FakeGreenhouseTelemetryRepository
import com.greenhands.app.telemetry.GreenhouseTelemetryState
import com.greenhands.app.telemetry.ReferenceCountedGreenhouseTelemetryRepository
import com.greenhands.app.telemetry.TelemetryFixtures
import com.greenhands.app.ui.navigation.NavActions
import com.greenhands.app.ui.navigation.Routes
import com.greenhands.app.ui.screens.DashboardModule
import com.greenhands.app.ui.screens.dashboardModuleRoute
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class DecisionMakingIntegrationTest {

    @Before
    fun clearBridge() {
        HarvestDecisionMakingBridge.clear()
    }

    @After
    fun resetBridge() {
        HarvestDecisionMakingBridge.clear()
    }

    @Test
    fun dashboardOpensDecisionWithAllFilter() {
        val module = DashboardModule(Routes.DECISION_MAKING, "Decision", "body")
        assertEquals("decision_making/all", dashboardModuleRoute(module))
        assertEquals(Routes.decisionMaking(DecisionSection.ALL), dashboardModuleRoute(module))
        assertEquals("All", DecisionSection.fromRoute("all").existingChipLabel)
    }

    @Test
    fun harvestingOpensExistingInfectionsFilter() {
        val destinations = mutableListOf<String>()
        HarvestDecisionMakingBridge.navigateSafely { destinations += it }
        assertEquals(listOf("decision_making/infections"), destinations)
        assertEquals(DecisionSection.INFECTIONS, DecisionSection.fromRoute("infections"))
        assertEquals("Infections", DecisionSection.INFECTIONS.existingChipLabel)
    }

    @Test
    fun tooColdOpensExistingHeaterFansFilter() {
        assertEquals("decision_making/heater_fans", Routes.decisionSupportDestination())
        assertEquals("Heater/Fans", DecisionSection.HEATER_FANS.existingChipLabel)
        assertEquals(
            DecisionSection.HEATER_FANS,
            DecisionSection.fromRoute("heater_fans")
        )
    }

    @Test
    fun eachOriginNavigatesOnceToItsRoute() {
        val destinations = mutableListOf<String>()
        destinations += dashboardModuleRoute(DashboardModule(Routes.DECISION_MAKING, "D", "b"))
        HarvestDecisionMakingBridge.navigateSafely { destinations += it }
        destinations += Routes.decisionSupportDestination()
        assertEquals(
            listOf(
                "decision_making/all",
                "decision_making/infections",
                "decision_making/heater_fans"
            ),
            destinations
        )
        assertEquals(1, destinations.count { it.endsWith("/all") })
        assertEquals(1, destinations.count { it.endsWith("/infections") })
        assertEquals(1, destinations.count { it.endsWith("/heater_fans") })
    }

    @Test
    fun recompositionDoesNotNavigate() {
        var navCount = 0
        val dashboard = DashboardModule(Routes.DECISION_MAKING, "D", "b")
        repeat(8) { dashboardModuleRoute(dashboard) }
        repeat(8) { Routes.decisionSupportDestination() }
        HarvestDecisionMakingBridge.navigateSafely { navCount += 1 }
        assertEquals(1, navCount)
        assertEquals(NavActions::openDecisionSupport.name, "openDecisionSupport")
        val actions = File("src/main/java/com/greenhands/app/ui/navigation/Routes.kt")
            .takeIf { it.exists() }
            ?: File("app/src/main/java/com/greenhands/app/ui/navigation/Routes.kt")
        val openSupport = actions.readText()
            .substringAfter("fun openDecisionSupport")
            .substringBefore("fun ")
        assertFalse(openSupport.contains("popUpTo"))
        assertTrue(openSupport.contains("launchSingleTop = true"))
    }

    @Test
    fun initialRouteDoesNotResetManualFilterSelection() {
        var session: DecisionFilterSession? = null
        session = applyRouteFilterOnce(session, DecisionSection.INFECTIONS)
        assertEquals(DecisionSection.INFECTIONS, session.selected)
        session.select(DecisionSection.ALL)
        session = applyRouteFilterOnce(session, DecisionSection.INFECTIONS)
        assertEquals(DecisionSection.ALL, session.selected)
        session = applyRouteFilterOnce(session, DecisionSection.HEATER_FANS)
        assertEquals(DecisionSection.ALL, session.selected)
    }

    @Test
    fun backReturnsToOriginatingScreen() {
        val fromDashboard = mutableListOf(Routes.DASHBOARD, Routes.decisionMaking(DecisionSection.ALL))
        fromDashboard.removeAt(fromDashboard.lastIndex)
        assertEquals(Routes.DASHBOARD, fromDashboard.last())

        val fromHarvest = mutableListOf(
            Routes.DASHBOARD,
            Routes.HARVESTING,
            Routes.decisionMaking(DecisionSection.INFECTIONS)
        )
        fromHarvest.removeAt(fromHarvest.lastIndex)
        assertEquals(Routes.HARVESTING, fromHarvest.last())

        val fromHeat = mutableListOf(
            Routes.CROPS,
            Routes.GREENHOUSE_PREVIEW,
            Routes.decisionMaking(DecisionSection.HEATER_FANS)
        )
        fromHeat.removeAt(fromHeat.lastIndex)
        assertEquals(Routes.GREENHOUSE_PREVIEW, fromHeat.last())
        assertFalse(
            Routes.heatContentRoutes.contains(Routes.decisionMaking(DecisionSection.HEATER_FANS))
        )
    }

    @Test
    fun firebaseDs18b20MapsToDecisionTemperatureField() {
        val live = GreenhouseTelemetryState.Live(TelemetryFixtures.accepted(temperatureC = 28.41))
        val iot = DecisionIotClimate.from(live)
        val backend = GreenhouseTelemetryResponse(temperatureC = 0.0, humidityPercent = 0.0)
        val snapshot = mergeDecisionEnvironment(backend, iot)
        assertEquals(28.41, snapshot.temperatureC!!, 0.0)
        assertEquals("28.4 °C", formatDecisionTemperatureField(snapshot.temperatureC))
        assertEquals(GreenhouseConnectionState.LIVE, snapshot.connectionState)
        assertNotEquals(0.0, snapshot.temperatureC)
        val adapter = decisionSource("DecisionIotAdapter.kt")
        val screen = decisionSource("DecisionMakingScreen.kt")
        assertFalse(adapter.contains("htu21dTemperatureC"))
        assertFalse(screen.contains("htu21dTemperatureC"))
        assertTrue(adapter.contains("snapshot.temperatureC"))
        assertTrue(adapter.contains("humidityPct"))
    }

    @Test
    fun firebaseHtu21dMapsToDecisionHumidityField() {
        val live = GreenhouseTelemetryState.Live(TelemetryFixtures.accepted(humidityPct = 61.25))
        val snapshot = mergeDecisionEnvironment(
            GreenhouseTelemetryResponse(temperatureC = 19.0, humidityPercent = 99.0),
            DecisionIotClimate.from(live)
        )
        assertEquals(61.25, snapshot.relativeHumidityPercent!!, 0.0)
        assertEquals("61 %", formatDecisionHumidityField(snapshot.relativeHumidityPercent))
        assertNotEquals(99.0, snapshot.relativeHumidityPercent)
    }

    @Test
    fun staleDisconnectedDataIsNotZeroOrLive() {
        val stale = mergeDecisionEnvironment(
            GreenhouseTelemetryResponse(temperatureC = 0.0, humidityPercent = 0.0),
            DecisionIotClimate.from(GreenhouseTelemetryState.Stale(TelemetryFixtures.accepted()))
        )
        val disconnected = mergeDecisionEnvironment(
            GreenhouseTelemetryResponse(temperatureC = 0.0, humidityPercent = 0.0),
            DecisionIotClimate.from(GreenhouseTelemetryState.Disconnected(TelemetryFixtures.accepted()))
        )
        assertNull(stale.temperatureC)
        assertNull(stale.relativeHumidityPercent)
        assertNotEquals(0.0, stale.temperatureC ?: Double.NaN)
        assertEquals(GreenhouseConnectionState.OFFLINE_DELAYED, stale.connectionState)
        assertNotEquals(GreenhouseConnectionState.LIVE, stale.connectionState)
        assertEquals("-- °C", formatDecisionTemperatureField(stale.temperatureC))
        assertEquals("-- %", formatDecisionHumidityField(stale.relativeHumidityPercent))
        assertNull(disconnected.temperatureC)
        assertEquals(GreenhouseConnectionState.DISCONNECTED, disconnected.connectionState)
        assertNotEquals(GreenhouseConnectionState.LIVE, disconnected.connectionState)
    }

    @Test
    fun decisionDoesNotCreateASecondFirebaseListener() {
        val inner = FakeGreenhouseTelemetryRepository()
        val shared = ReferenceCountedGreenhouseTelemetryRepository(inner)
        shared.startMonitoring()
        shared.startMonitoring()
        assertEquals(1, inner.startCount)
        assertEquals(2, shared.subscriberCount)
        assertSame(inner, shared.innerRepository)
        shared.stopMonitoring()
        assertEquals(0, inner.stopCount)
        assertTrue(inner.monitoring)
        shared.stopMonitoring()
        assertEquals(1, inner.stopCount)
        assertFalse(inner.monitoring)
        val adapter = decisionSource("DecisionIotAdapter.kt")
        val session = decisionSource("DecisionTelemetrySession.kt")
        assertTrue(session.contains("telemetry.startMonitoring()"))
        assertTrue(session.contains("telemetry.stopMonitoring()"))
        assertFalse(adapter.contains("FirebaseAnonymousAuthClient"))
        assertFalse(session.contains("FirebaseAnonymousAuthClient"))
        assertFalse(adapter.contains("FirebaseApp."))
        assertFalse(session.contains("addValueEventListener"))
        assertFalse(adapter.contains("htu21dTemperatureC"))
        assertTrue(session.contains("GreenHandsTelemetry").not())
        assertTrue(session.contains("GreenhouseTelemetryRepository"))
    }

    @Test
    fun port8002AiRemainsConnected() {
        assertEquals(8002, ApiConfig.API_PORT)
        assertTrue(ApiConfig.baseUrl.endsWith(":8002/"))
        assertTrue(ApiConfig.baseUrl.startsWith("http://"))
        val api = decisionSource("ApiService.kt")
        assertTrue(api.contains("api/v1/ai-decision/active"))
        assertTrue(api.contains("api/v1/predict-heater"))
        assertTrue(api.contains("api/v1/infection-decision"))
        assertTrue(api.contains("ApiConfig.baseUrl"))
        val screen = decisionSource("DecisionMakingScreen.kt")
        assertTrue(screen.contains("getActiveAiDecisions"))
        assertTrue(screen.contains("predictHeater"))
        assertTrue(screen.contains("AI Decision Making"))
        assertTrue(screen.contains("Heater/Fans"))
        assertTrue(screen.contains("Infections"))
        assertTrue(screen.contains("Manual"))
        assertTrue(screen.contains("Automated"))
    }

    @Test
    fun infectionAndHeaterAiBehaviorIsPreserved() {
        val heater = DecisionResponse(kind = "heater", category = "heater_fans", heaterSpeed = 62.5)
        val infection = DecisionResponse(kind = "infection", category = "infections")
        assertTrue(heater.isHeaterAction)
        assertFalse(infection.isHeaterAction)
        assertEquals("heater", neededClimateActuator(18.4, 64.0))
        val live = DecisionIotClimate.from(
            GreenhouseTelemetryState.Live(TelemetryFixtures.accepted(temperatureC = 18.4, humidityPct = 64.0))
        )
        val request = heaterPredictRequestOrNull(
            GreenhouseTelemetryResponse(targetTemperature = 26.0, climateOptimal = false),
            live
        )
        assertNotNull(request)
        assertEquals(18.4, request!!.currentTemperature, 0.0)
        assertEquals(64.0, request.humidity, 0.0)
        val ignored = heaterPredictRequestOrNull(
            GreenhouseTelemetryResponse(temperatureC = 18.4, humidityPercent = 64.0, climateOptimal = false),
            DecisionIotClimate.Unavailable
        )
        assertNull(ignored)
    }

    @Test
    fun publicSectionMapsOntoExistingPrivateFilters() {
        assertEquals("All", DecisionSection.ALL.existingChipLabel)
        assertEquals("Infections", DecisionSection.INFECTIONS.existingChipLabel)
        assertEquals("Heater/Fans", DecisionSection.HEATER_FANS.existingChipLabel)
        assertEquals("Water Pump", DecisionSection.WATER_PUMP.existingChipLabel)
        assertEquals("Other actions", DecisionSection.OTHER.existingChipLabel)
        assertEquals(DecisionSection.ALL, DecisionSection.fromRoute(null))
        assertEquals(DecisionSection.ALL, DecisionSection.fromRoute("all"))
    }

    private fun decisionSource(name: String): String {
        val candidates = listOf(
            File("src/main/java/com/greenhands/app/decision/$name"),
            File("app/src/main/java/com/greenhands/app/decision/$name")
        )
        return candidates.first { it.exists() }.readText()
    }
}
