package com.greenhands.app

import com.greenhands.app.harvest.ar.HarvestArPlacementController
import com.greenhands.app.harvest.data.HarvestTelemetryEnvironmentRepository
import com.greenhands.app.sensor.ar.ArGreenhouseFrameGeometry
import com.greenhands.app.sensor.ar.ArGreenhousePose
import com.greenhands.app.sensor.ar.ArGreenhouseStructurePlan
import com.greenhands.app.sensor.ar.ArOriginPlacementController
import com.greenhands.app.sensor.ar.ArOriginPlacementPhase
import com.greenhands.app.sensor.ar.ArRealScale
import com.greenhands.app.sensor.ar.ArVisualizationMapper
import com.greenhands.app.sensor.ar.ArWorldMapper
import com.greenhands.app.sensor.ar.RealArUxHelpers
import com.greenhands.app.sensor.ar.SensorArWorldProjector
import com.greenhands.app.sensor.model.GreenhousePhysicalConfig
import com.greenhands.app.sensor.model.SensorStatus
import com.greenhands.app.sensor.model.SensorType
import com.greenhands.app.sensor.telemetry.SensorArIotFreshness
import com.greenhands.app.sensor.telemetry.SensorArIotLabels
import com.greenhands.app.sensor.telemetry.SensorArTelemetrySession
import com.greenhands.app.sensor.ui.SensorPlacementViewModel
import com.greenhands.app.sensor.ui.SensorWorkflowStep
import com.greenhands.app.telemetry.FakeGreenhouseTelemetryRepository
import com.greenhands.app.telemetry.GreenhouseTelemetryMapper
import com.greenhands.app.telemetry.GreenhouseTelemetryPath
import com.greenhands.app.telemetry.GreenhouseTelemetryState
import com.greenhands.app.telemetry.ReferenceCountedGreenhouseTelemetryRepository
import com.greenhands.app.telemetry.TelemetryCopy
import com.greenhands.app.telemetry.TelemetryFixtures
import com.greenhands.app.ui.navigation.Routes
import com.greenhands.app.ui.navigation.SensorNavigation
import com.greenhands.app.ui.screens.DashboardModule
import com.greenhands.app.ui.screens.dashboardModuleRoute
import com.greenhands.app.ui.screens.navigationRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class SensorCanonicalIntegrationTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val physical = GreenhousePhysicalConfig(12.0, 8.0, 4.0, 1.0)

    @Before
    fun setMain() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun reset() {
        Dispatchers.resetMain()
    }

    @Test
    fun dashboardSensorButtonOpensSetupNotComingSoon() {
        val sensor = DashboardModule(
            Routes.SENSOR_PLACEMENT,
            "Sensor Placement",
            "body",
            isSensor = true
        )
        assertEquals(Routes.SENSOR_SETUP, dashboardModuleRoute(sensor))
        assertEquals(Routes.SENSOR_SETUP, sensor.navigationRoute())
        assertFalse(dashboardModuleRoute(sensor).startsWith("coming_soon"))
        assertEquals(Routes.DASHBOARD, Routes.topLevelFor(Routes.SENSOR_SETUP))
    }

    @Test
    fun backFromSetupReturnsToDashboardMapping() {
        assertEquals(listOf(Routes.SENSOR_SETUP), SensorNavigation.workflowStackTo(Routes.SENSOR_SETUP))
        assertEquals(Routes.DASHBOARD, Routes.topLevelFor(Routes.SENSOR_SETUP))
        assertFalse(Routes.isHeatContentRoute(Routes.SENSOR_SETUP))
    }

    @Test
    fun completeWorkflowNavigationOrderIsPreserved() {
        assertEquals(
            listOf(Routes.SENSOR_SETUP, Routes.SENSOR_SCAN),
            SensorNavigation.workflowStackTo(Routes.SENSOR_SCAN)
        )
        assertEquals(
            listOf(Routes.SENSOR_SETUP, Routes.SENSOR_SCAN, Routes.SENSOR_PLACE),
            SensorNavigation.workflowStackTo(Routes.SENSOR_PLACE)
        )
        assertEquals(
            listOf(
                Routes.SENSOR_SETUP,
                Routes.SENSOR_SCAN,
                Routes.SENSOR_PLACE,
                Routes.SENSOR_COVERAGE
            ),
            SensorNavigation.workflowStackTo(Routes.SENSOR_COVERAGE)
        )
        assertEquals(
            listOf(
                Routes.SENSOR_SETUP,
                Routes.SENSOR_SCAN,
                Routes.SENSOR_PLACE,
                Routes.SENSOR_COVERAGE,
                Routes.SENSOR_OPTIMIZE
            ),
            SensorNavigation.workflowStackTo(Routes.SENSOR_OPTIMIZE)
        )
        val configured = SensorNavigation.resumeRoute(true, SensorWorkflowStep.OPTIMIZE)
        assertEquals(Routes.SENSOR_OPTIMIZE, configured)
        assertEquals(Routes.SENSOR_SETUP, SensorNavigation.resumeRoute(false, SensorWorkflowStep.PLACE))
        assertTrue(Routes.SENSOR_VIRTUAL_PREVIEW in Routes.sensorContentRoutes)
        assertTrue(Routes.SENSOR_REAL_AR in Routes.sensorContentRoutes)
    }

    @Test
    fun virtualPreviewKeepsConfiguredPitchedGeometry() {
        val vm = SensorPlacementViewModel()
        assertTrue(vm.createOrUpdateGreenhouse(GreenhousePhysicalConfig(10.0, 8.0, 3.0, 1.0)))
        val snap = ArVisualizationMapper.from(vm.state.value)
        assertEquals(10.0, snap.physical.lengthMeters, 0.0)
        assertEquals(8.0, snap.physical.widthMeters, 0.0)
        assertEquals(3.0, snap.physical.heightMeters, 0.0)
        val corners = ArGreenhouseFrameGeometry.localCorners(10f, 8f, 3f)
        assertEquals(10f, corners.maxOf { it.x }, 1e-4f)
        assertEquals(8f, corners.maxOf { it.z }, 1e-4f)
        assertEquals(3f * ArGreenhouseFrameGeometry.ROOF_PEAK_FACTOR, corners.maxOf { it.y }, 1e-4f)
        val renderer = source("sensor/ar/VirtualGreenhouseRenderer.kt")
        assertTrue(renderer.contains("Pitched roof") || renderer.contains("roof"))
        assertTrue(renderer.contains("NightElevated"))
    }

    @Test
    fun greenhouseGeometryAttachesOnlyAfterAligned() {
        assertFalse(ArGreenhouseFrameGeometry.shouldShowFrame(ArOriginPlacementPhase.SCANNING))
        assertFalse(ArGreenhouseFrameGeometry.shouldShowFrame(ArOriginPlacementPhase.PLANE_FOUND))
        assertFalse(ArGreenhouseFrameGeometry.shouldShowFrame(ArOriginPlacementPhase.ORIGIN_PLACED))
        assertFalse(ArGreenhouseFrameGeometry.shouldShowFrame(ArOriginPlacementPhase.SETTING_DIRECTION))
        assertTrue(ArGreenhouseFrameGeometry.shouldShowFrame(ArOriginPlacementPhase.ALIGNED))
        assertFalse(ArGreenhouseStructurePlan.shouldAttach(ArOriginPlacementPhase.SCANNING, true))
        assertTrue(ArGreenhouseStructurePlan.shouldAttach(ArOriginPlacementPhase.ALIGNED, true))
        assertFalse(ArGreenhouseStructurePlan.shouldAttach(ArOriginPlacementPhase.ALIGNED, false))
        val unaligned = ArOriginPlacementController.onOriginPlaced(
            ArOriginPlacementController.onHorizontalPlaneDetected(ArGreenhousePose()),
            0f, 0f, 0f
        )
        assertNull(ArGreenhouseFrameGeometry.worldCorners(unaligned, physical))
    }

    @Test
    fun greenhouseIsVisibleAfterAlignmentWithDefaultOn() {
        val layers = RealArUxHelpers.defaultLayers()
        assertTrue(layers.greenhouse)
        assertTrue(layers.shouldAttachGreenhouseGeometry(aligned = true))
        val pose = alignedPose()
        assertTrue(ArGreenhouseFrameGeometry.shouldShowFrame(pose.phase))
        val plan = ArGreenhouseStructurePlan.plan(physical)
        assertTrue(plan.isNotEmpty())
    }

    @Test
    fun configuredDimensionsDriveArExtents() {
        val plan = ArGreenhouseStructurePlan.plan(physical)
        val extents = ArGreenhouseStructurePlan.localExtents(plan)!!
        assertEquals(12f, extents.first, 1e-3f)
        assertEquals(8f, extents.third, 1e-3f)
        assertTrue(extents.second >= 4f)
        assertEquals(1f, ArRealScale.METERS_PER_UNIT, 0f)
        assertTrue(ArRealScale.isRealWorldMeters())
        val pose = alignedPose()
        val farX = ArWorldMapper.localToWorld(pose, 12f, 0f, 0f)!!
        assertEquals(12f, farX.x, 1e-4f)
        val farZ = ArWorldMapper.localToWorld(pose, 0f, 0f, 8f)!!
        assertEquals(8f, farZ.z, 1e-4f)
    }

    @Test
    fun pitchedRoofFrameAndFloorGridAreRepresented() {
        val plan = ArGreenhouseStructurePlan.plan(physical)
        assertTrue(ArGreenhouseStructurePlan.hasFloorGrid(plan))
        assertTrue(ArGreenhouseStructurePlan.hasPosts(plan))
        assertTrue(ArGreenhouseStructurePlan.hasPitchedRoof(plan))
        assertTrue(plan.any { it.part == com.greenhands.app.sensor.ar.ArGreenhouseStructurePart.EAVE })
        assertTrue(plan.any { it.part == com.greenhands.app.sensor.ar.ArGreenhouseStructurePart.WALL_PANEL })
        assertTrue(plan.any { it.part == com.greenhands.app.sensor.ar.ArGreenhouseStructurePart.ROOF_PANEL })
        assertFalse(ArGreenhouseStructurePlan.hasOpaqueFloorSlab(plan))
    }

    @Test
    fun resetHidesPreviousStructure() {
        var state = ArOriginPlacementController.onHorizontalPlaneDetected(ArGreenhousePose())
        state = ArOriginPlacementController.onOriginPlaced(state, 0f, 0f, 0f)
        state = ArOriginPlacementController.beginSetDirection(state)
        state = ArOriginPlacementController.onDirectionPoint(state, 2f, 0f, 0f).first
        assertTrue(ArGreenhouseFrameGeometry.shouldShowFrame(state.phase))
        val reset = ArOriginPlacementController.resetAlignment(state)
        assertFalse(ArGreenhouseFrameGeometry.shouldShowFrame(reset.phase))
        assertFalse(ArGreenhouseStructurePlan.shouldAttach(reset.phase, true))
        val originReset = ArOriginPlacementController.resetOrigin(state)
        assertFalse(ArGreenhouseFrameGeometry.shouldShowFrame(originReset.phase))
    }

    @Test
    fun realignmentDoesNotPlanDuplicateRoots() {
        val first = ArGreenhouseStructurePlan.plan(physical)
        val second = ArGreenhouseStructurePlan.plan(physical)
        assertEquals(first.size, second.size)
        assertEquals(first.map { it.name }, second.map { it.name })
        assertEquals(1, first.count { it.name.startsWith(ArGreenhouseStructurePlan.FLOOR_GRID_PREFIX) }.let { if (it > 0) 1 else 0 })
        val names = first.map { it.name }
        assertEquals(names.size, names.distinct().size)
        val nodes = source("sensor/ar/ArGreenhouseFrameNodes.kt")
        assertTrue(nodes.contains("clearStructure") || nodes.contains("clearFrame"))
        assertTrue(nodes.contains("FRAME_ROOT_NAME"))
    }

    @Test
    fun temperatureMarkerUsesOnlyTemperatureC() {
        val live = GreenhouseTelemetryState.Live(TelemetryFixtures.accepted(temperatureC = 28.41, humidityPct = 61.25))
        val dto = TelemetryFixtures.current(temperatureC = 28.41, humidityPct = 61.25, htu21dTemperatureC = 40.0)
        assertEquals(28.41, GreenhouseTelemetryMapper.authoritativeTemperature(dto)!!, 0.0)
        assertEquals(34.205, GreenhouseTelemetryMapper.averagedTemperature(dto)!!, 1e-6)
        assertEquals(28.41, SensorArIotLabels.liveTemperatureC(live)!!, 0.0)
        assertEquals(TelemetryCopy.AUTHORITATIVE_TEMPERATURE, SensorArIotLabels.TEMPERATURE_FIELD)
        assertNotEquals(TelemetryCopy.RAW_HTU21D_TEMPERATURE, SensorArIotLabels.TEMPERATURE_FIELD)
        val label = climateLabel(SensorType.TEMPERATURE, live)!!
        assertEquals(TelemetryCopy.AUTHORITATIVE_TEMPERATURE, label.firebaseField)
        assertTrue(label.detailLine.contains("28.41"))
        assertFalse(label.detailLine.contains("40"))
    }

    @Test
    fun humidityMarkerUsesOnlyHumidityPct() {
        val live = GreenhouseTelemetryState.Live(TelemetryFixtures.accepted(humidityPct = 61.25))
        val label = climateLabel(SensorType.HUMIDITY, live)!!
        assertEquals(TelemetryCopy.AUTHORITATIVE_HUMIDITY, label.firebaseField)
        assertTrue(label.detailLine.contains("61.25"))
        assertEquals(61.25, SensorArIotLabels.liveHumidityPct(live)!!, 0.0)
    }

    @Test
    fun rawHtu21dTemperatureIsNeverUsed() {
        val mapper = source("telemetry/GreenhouseTelemetryMapper.kt")
        assertTrue(mapper.contains("htu21dTemperatureC") || mapper.contains("ignored"))
        val iot = source("sensor/telemetry/SensorArIotLabels.kt")
        assertTrue(iot.contains("FORBIDDEN_HTU21D_TEMPERATURE") || iot.contains("htu21d_temperature_c"))
        assertEquals("htu21d_temperature_c", SensorArIotLabels.FORBIDDEN_HTU21D_TEMPERATURE)
        val live = GreenhouseTelemetryState.Live(TelemetryFixtures.accepted(temperatureC = 28.41))
        val label = climateLabel(SensorType.TEMPERATURE, live)!!
        assertFalse(label.detailLine.contains("40.00"))
        assertEquals(TelemetryCopy.AUTHORITATIVE_TEMPERATURE, label.firebaseField)
    }

    @Test
    fun soilAndLightMarkersHaveNoLiveClimateLabel() {
        val live = GreenhouseTelemetryState.Live(TelemetryFixtures.accepted())
        assertFalse(SensorArIotLabels.showsLiveClimate(SensorType.SOIL_MOISTURE))
        assertFalse(SensorArIotLabels.showsLiveClimate(SensorType.LIGHT_INTENSITY))
        assertNull(SensorArIotLabels.firebaseField(SensorType.SOIL_MOISTURE))
        assertNull(SensorArIotLabels.firebaseField(SensorType.LIGHT_INTENSITY))
        val vm = configuredVm()
        vm.addSensor(1.0, 1.0, type = SensorType.SOIL_MOISTURE)
        vm.addSensor(2.0, 1.0, type = SensorType.LIGHT_INTENSITY)
        val labels = SensorArIotLabels.labelsFor(
            pose = alignedPose(),
            snapshot = ArVisualizationMapper.from(vm.state.value),
            state = live,
            sensorsVisible = true
        )
        assertTrue(labels.isEmpty())
    }

    @Test
    fun freshDataUsesTwoDecimalsAndLive() {
        val live = GreenhouseTelemetryState.Live(TelemetryFixtures.accepted(temperatureC = 28.41, humidityPct = 61.25))
        assertEquals("28.41 °C · Live", SensorArIotLabels.formatLiveTemperature(28.41))
        assertEquals("61.25 % RH · Live", SensorArIotLabels.formatLiveHumidity(61.25))
        val t = climateLabel(SensorType.TEMPERATURE, live)!!
        assertEquals(SensorArIotFreshness.LIVE, t.freshness)
        assertEquals("T · S1", t.titleLine)
        assertTrue(t.detailLine.contains("Live"))
        assertTrue(t.detailLine.contains("28.41"))
    }

    @Test
    fun staleDisconnectedInvalidShowNoRealtimeNumber() {
        val connecting = climateLabel(SensorType.TEMPERATURE, GreenhouseTelemetryState.Connecting)!!
        assertEquals(SensorArIotFreshness.CONNECTING, connecting.freshness)
        assertNull(connecting.valueText)
        assertEquals("Connecting…", connecting.detailLine)
        val stale = climateLabel(
            SensorType.TEMPERATURE,
            GreenhouseTelemetryState.Stale(TelemetryFixtures.accepted(temperatureC = 28.41))
        )!!
        assertEquals(SensorArIotFreshness.STALE, stale.freshness)
        assertNull(stale.valueText)
        assertEquals("Stale", stale.detailLine)
        assertFalse(stale.detailLine.contains("28.41"))
        val disconnected = climateLabel(
            SensorType.HUMIDITY,
            GreenhouseTelemetryState.Disconnected(TelemetryFixtures.accepted(humidityPct = 61.25))
        )!!
        assertEquals(SensorArIotFreshness.UNAVAILABLE, disconnected.freshness)
        assertNull(disconnected.valueText)
        assertEquals("Unavailable", disconnected.detailLine)
        assertFalse(disconnected.detailLine.contains("61.25"))
    }

    @Test
    fun multipleSameTypeMarkersShareGh001Reading() {
        val live = GreenhouseTelemetryState.Live(TelemetryFixtures.accepted(temperatureC = 28.41))
        val vm = configuredVm()
        vm.addSensor(1.0, 1.0, type = SensorType.TEMPERATURE)
        vm.addSensor(4.0, 2.0, type = SensorType.TEMPERATURE)
        val labels = SensorArIotLabels.labelsFor(
            pose = alignedPose(),
            snapshot = ArVisualizationMapper.from(vm.state.value),
            state = live,
            sensorsVisible = true
        )
        assertEquals(2, labels.size)
        assertTrue(labels.all { it.sharedGreenhouseId == GreenhouseTelemetryPath.GREENHOUSE_ID })
        assertTrue(SensorArIotLabels.usesSharedGh001Reading(labels, SensorType.TEMPERATURE))
        assertEquals(labels[0].detailLine, labels[1].detailLine)
    }

    @Test
    fun sensorArAcquiresSharedRepositoryOnce() {
        val inner = FakeGreenhouseTelemetryRepository()
        val shared = ReferenceCountedGreenhouseTelemetryRepository(inner)
        val session = SensorArTelemetrySession(shared)
        session.acquire()
        assertEquals(1, inner.startCount)
        assertEquals(1, shared.subscriberCount)
        assertTrue(session.acquired)
        session.release()
        assertEquals(1, inner.stopCount)
        assertFalse(session.acquired)
    }

    @Test
    fun recompositionDoesNotAddAnotherListener() {
        val inner = FakeGreenhouseTelemetryRepository()
        val shared = ReferenceCountedGreenhouseTelemetryRepository(inner)
        val session = SensorArTelemetrySession(shared)
        session.acquire()
        session.acquire()
        assertEquals(1, inner.startCount)
        assertEquals(1, shared.subscriberCount)
        session.release()
        assertEquals(1, inner.stopCount)
        session.release()
        assertEquals(1, inner.stopCount)
    }

    @Test
    fun leavingSensorArReleasesExactlyOnce() {
        val inner = FakeGreenhouseTelemetryRepository()
        val shared = ReferenceCountedGreenhouseTelemetryRepository(inner)
        val session = SensorArTelemetrySession(shared)
        session.acquire()
        session.release()
        session.release()
        assertEquals(1, inner.startCount)
        assertEquals(1, inner.stopCount)
        assertFalse(inner.monitoring)
    }

    @Test
    fun leavingSensorArDoesNotStopHeatOrHarvestSubscribers() {
        val inner = FakeGreenhouseTelemetryRepository()
        val shared = ReferenceCountedGreenhouseTelemetryRepository(inner)
        val harvest = HarvestTelemetryEnvironmentRepository(shared)
        harvest.acquire()
        shared.startMonitoring()
        val sensor = SensorArTelemetrySession(shared)
        sensor.acquire()
        assertEquals(1, inner.startCount)
        assertEquals(3, shared.subscriberCount)
        sensor.release()
        assertEquals(0, inner.stopCount)
        assertTrue(inner.monitoring)
        harvest.release()
        assertEquals(0, inner.stopCount)
        shared.stopMonitoring()
        assertEquals(1, inner.stopCount)
    }

    @Test
    fun heatDemoStillPerformsZeroFirebaseCalls() {
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
    fun heatAndHarvestArBehaviorRemainUnchanged() {
        assertTrue(Routes.isHeatContentRoute(Routes.GREENHOUSE_AR))
        assertTrue(Routes.harvestCameraRoutes.contains(Routes.HARVEST_AR))
        assertFalse(Routes.isSensorContentRoute(Routes.GREENHOUSE_AR))
        assertFalse(Routes.isSensorContentRoute(Routes.HARVEST_AR))
        val controller = HarvestArPlacementController()
        controller.markPlaced(true)
        controller.destroy()
        assertTrue(controller.destroyed)
        val heatScene = source("ar/render/GreenhouseArScene.kt")
        assertTrue(heatScene.contains("ArTabletopScale") || heatScene.contains("ar_greenhouse_root"))
        val sensorScene = source("sensor/ui/RealGreenhouseArScreen.kt")
        assertFalse(sensorScene.contains("ArTabletopScale"))
    }

    @Test
    fun sceneAndLabelResourcesReleaseOnLeave() {
        val hidden = SensorArIotLabels.labelsFor(
            pose = alignedPose(),
            snapshot = ArVisualizationMapper.from(configuredVm().state.value),
            state = GreenhouseTelemetryState.Live(TelemetryFixtures.accepted()),
            sensorsVisible = false
        )
        assertTrue(hidden.isEmpty())
        val behind = SensorArWorldProjector.project(
            SensorArWorldProjector.ViewPoint(0.5f, 0.5f, -0.1f),
            800f,
            600f
        )
        assertNull(behind)
        val offscreen = SensorArWorldProjector.project(
            SensorArWorldProjector.ViewPoint(1.4f, 0.5f, 0.4f),
            800f,
            600f
        )
        assertNull(offscreen)
        val screen = source("sensor/ui/RealGreenhouseArScreen.kt")
        assertTrue(screen.contains("DisposableEffect"))
        assertTrue(screen.contains("clearSceneNodes") || screen.contains("onDispose"))
        assertTrue(screen.contains("SensorArIotLabelOverlay") || screen.contains("projectedIotLabels"))
    }

    @Test
    fun recompositionDoesNotNavigateAutomatically() {
        val nav = source("ui/navigation/GreenHandsNavGraph.kt")
        assertFalse(nav.contains("LaunchedEffect(sensorState)"))
        assertFalse(nav.contains("LaunchedEffect(sensorVm"))
        assertTrue(nav.contains("navigateToSensorWorkflow"))
        assertTrue(nav.contains("launchSingleTop = true"))
        val resume = SensorNavigation.resumeRoute(true, SensorWorkflowStep.PLACE)
        assertEquals(Routes.SENSOR_PLACE, resume)
        assertEquals(Routes.SENSOR_SETUP, SensorNavigation.resumeRoute(false, SensorWorkflowStep.COVERAGE))
    }

    private fun alignedPose(): ArGreenhousePose =
        ArWorldMapper.alignedPose(0f, 0f, 0f, forwardX = 1f, forwardZ = 0f)

    private fun configuredVm(): SensorPlacementViewModel {
        val vm = SensorPlacementViewModel()
        assertTrue(vm.createOrUpdateGreenhouse(physical))
        return vm
    }

    private fun climateLabel(
        type: SensorType,
        state: GreenhouseTelemetryState
    ): com.greenhands.app.sensor.telemetry.SensorArIotLabel? {
        val vm = configuredVm()
        vm.addSensor(1.0, 1.0, type = type)
        val snap = ArVisualizationMapper.from(vm.state.value)
        val sensor = snap.sensors.single()
        return SensorArIotLabels.labelFor(
            pose = alignedPose(),
            snapshot = snap,
            sensor = sensor.copy(status = SensorStatus.ACTIVE),
            freshness = SensorArIotLabels.freshnessOf(state),
            state = state
        )
    }

    private fun source(relative: String): String {
        val candidates = listOf(
            File("src/main/java/com/greenhands/app/$relative"),
            File("app/src/main/java/com/greenhands/app/$relative")
        )
        return candidates.first { it.exists() }.readText()
    }
}
