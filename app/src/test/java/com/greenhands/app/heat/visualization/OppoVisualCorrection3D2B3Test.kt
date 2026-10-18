package com.greenhands.app.heat.visualization

import com.greenhands.app.ar.domain.ArTabletopPhase
import com.greenhands.app.ar.domain.ArTabletopScale
import com.greenhands.app.ar.domain.EquipmentPresentationContext
import com.greenhands.app.ar.render.ArCirculationColorMaterial
import com.greenhands.app.ar.render.ArFanPadAirflowHost
import com.greenhands.app.ar.render.ArFoggerMistHost
import com.greenhands.app.ar.render.ArLiveRendererOwnership
import com.greenhands.app.ar.render.ArPhysicsEffectCleanup
import com.greenhands.app.ar.render.ArPhysicsEffectLifecycle
import com.greenhands.app.ar.render.CirculationRingSceneMath
import com.greenhands.app.equipment.data.InMemoryEquipmentLayoutRepository
import com.greenhands.app.equipment.domain.EquipmentLabelCatalog
import com.greenhands.app.equipment.model.EquipmentInstance
import com.greenhands.app.equipment.model.EquipmentRuntimeState
import com.greenhands.app.equipment.model.EquipmentType
import com.greenhands.app.equipment.ui.EquipmentPlacementViewModel
import com.greenhands.app.equipment.ui.OverlayClimate
import com.greenhands.app.heat.domain.HeatFormulas
import com.greenhands.app.heat.runtime.Phase3D2AFixtures
import com.greenhands.app.heat.runtime.PhysicsFieldRuntime
import com.greenhands.app.heat.runtime.adapter.AdaptiveGridPolicy
import com.greenhands.app.heat.runtime.adapter.DemoFanPadProfile
import com.greenhands.app.heat.runtime.adapter.FanPadBoundaryResolver
import com.greenhands.app.heat.runtime.adapter.FanPadLayoutResolver
import com.greenhands.app.heat.runtime.adapter.UncalibratedPhase3AProfileSettings
import com.greenhands.app.heat.runtime.adapter.UserPhysicsCoordinateAdapter
import com.greenhands.app.heat.runtime.contracts.FanPadWallSelection
import com.greenhands.app.heat.runtime.contracts.GreenhouseEndWall
import com.greenhands.app.heat.runtime.contracts.WeatherBoundary
import com.greenhands.app.preview3d.domain.CropRowGeometry
import com.greenhands.app.preview3d.domain.SceneScaleMapper
import com.greenhands.app.preview3d.model.DemoGreenhouseProfile
import com.greenhands.app.preview3d.model.PreviewMode
import kotlin.math.abs
import kotlin.math.tan
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
class OppoVisualCorrection3D2B3Test {

    private val dispatcher = UnconfinedTestDispatcher()
    private val circ = HeatFormulas.circulation(25.0)
    private val exhaust = HeatFormulas.exhaustFromCirculation(circ)
    private val fogger = HeatFormulas.fogger(70.0)
    private val thresholds = Phase3D2AFixtures.thresholds
    private val climate = OverlayClimate(
        crop = null,
        stage = null,
        temperatureLabel = "25.0°C",
        humidityLabel = "70.0 % RH",
        vpdLabel = "Not published",
        circulation = circ,
        exhaust = exhaust,
        fogger = fogger,
        optimalTemperatureC = 25.0,
        optimalHumidityPercent = 70.0
    )

    @Before
    fun setMain() {
        Dispatchers.setMain(dispatcher)
        SnapshotFieldSampler.clearCache()
    }

    @After
    fun reset() {
        Dispatchers.resetMain()
        SnapshotFieldSampler.clearCache()
    }

    @Test
    fun foggerVisualEnvelopeUsesAcceptedReadyProfileNotArbitrary18Deg058M() {
        val group = onFogGroup("fog-oppo-env")
        assertEquals(UncalibratedPhase3AProfileSettings.FOGGER_PLUME_ANGLE_DEG * 0.5, group.plumeHalfAngleDeg, 0.0)
        assertEquals(UncalibratedPhase3AProfileSettings.FOGGER_PLUME_LENGTH_M, group.acceptedPlumeLengthM, 0.0)
        assertEquals(20.0, group.plumeHalfAngleDeg, 0.0)
        assertEquals(2.0, group.acceptedPlumeLengthM, 0.0)
        assertEquals(18.0, FogMistVisual.UNUSED_PLUME_HALF_ANGLE_DEG, 0.0)
        assertEquals(0.58, FogMistVisual.UNUSED_PLUME_LENGTH_M, 0.0)
        assertNotEquals(FogMistVisual.UNUSED_PLUME_HALF_ANGLE_DEG, group.plumeHalfAngleDeg, 0.0)
        assertNotEquals(FogMistVisual.UNUSED_PLUME_LENGTH_M, group.acceptedPlumeLengthM, 0.0)
        assertTrue(group.maxDistanceM > FogMistVisual.UNUSED_PLUME_LENGTH_M)
        assertTrue(group.maxDistanceM <= group.acceptedPlumeLengthM + 1e-9)
        assertFalse(
            FoggerVisualEnvelope.usesArbitraryVisualFallback(
                group.plumeHalfAngleDeg,
                group.acceptedPlumeLengthM
            )
        )
        val (dx, dy, dz) = UserPhysicsCoordinateAdapter.foggerSprayDomain()
        assertEquals(0.0, dx, 0.0)
        assertEquals(0.0, dy, 0.0)
        assertEquals(-1.0, dz, 0.0)
        assertEquals(dx, group.sprayDomainDx, 0.0)
        assertEquals(dy, group.sprayDomainDy, 0.0)
        assertEquals(dz, group.sprayDomainDz, 0.0)
        assertTrue(VisualizationCopy.FOG_MIST_QUALITATIVE.contains(VisualizationCopy.PREDICTED_UNCALIBRATED))
        assertFalse(FogMistVisual.PARTICLE_COUNT_IS_WATER_FLOW)
        assertFalse(FogMistVisual.PARTICLE_DENSITY_IS_FOG_CONCENTRATION)
    }

    @Test
    fun mistSpreadsLaterallyAndDownwardAndStaysClippedToGreenhouse() {
        val fog = overheadFogger("fog-oppo-spread")
        val snapshot = liveSnapshot(fog)
        val group = FoggerEffectPlanner.plan(
            true, listOf(fog), mapOf(fog.id to EquipmentRuntimeState.ON), snapshot, thresholds
        ).groups.single()
        val canopy = DemoGreenhouseProfile.HEIGHT_M * CropRowGeometry.HEIGHT_FILL
        val points = FoggerMistAnimator.particlePositions(group, 0.55)
        assertFalse(FogMistDistribution.areCollinear(points))
        val (spanX, spanY) = FogMistDistribution.horizontalSpanM(points)
        assertTrue(spanX > 0.04)
        assertTrue(spanY > 0.04)
        assertTrue(FogMistDistribution.downwardSpanM(points) > 0.12)
        assertTrue(
            FogMistDistribution.coversMultipleRadialDirections(points, group.nozzleOrigin(0))
        )
        assertTrue(FogMistDistribution.uniqueDistanceLayers(group.particles) >= 6)
        assertTrue(FogMistDistribution.uniqueAzimuthBuckets(group.particles) >= 6)
        val radiusAtEnd = group.maxDistanceM * tan(Math.toRadians(group.plumeHalfAngleDeg))
        assertTrue(radiusAtEnd > FogMistVisual.UNUSED_PLUME_LENGTH_M * tan(Math.toRadians(18.0)))
        assertFalse(FoggerVisualEnvelope.HARD_CLIPS_CANOPY)
        assertTrue(points.any { it.zM < canopy - 0.02 })
        listOf(0.0, 0.4, 0.9, 1.6).forEach { time ->
            FoggerMistAnimator.particlePositions(group, time).forEach { point ->
                assertTrue(FoggerMistEnvelope.contains(snapshot, point))
                assertTrue(point.zM + 1e-6 >= 0.0)
                assertTrue(point.zM <= DemoGreenhouseProfile.HEIGHT_M + 1e-9)
                assertTrue(point.xM + 1e-9 >= 0.0)
                assertTrue(point.xM <= DemoGreenhouseProfile.WIDTH_M + 1e-9)
                assertTrue(point.yM + 1e-9 >= 0.0)
                assertTrue(point.yM <= DemoGreenhouseProfile.LENGTH_M + 1e-9)
            }
        }
        val inCanopy = points.filter { it.zM < canopy }
        assertTrue(inCanopy.isNotEmpty())
        inCanopy.forEach { point ->
            val att = FoggerVisualEnvelope.canopyAttenuation(point.zM, canopy)
            assertTrue(att < 1f)
            assertTrue(att >= FoggerVisualEnvelope.MIN_CANOPY_ALPHA)
        }
        assertEquals(1f, FoggerVisualEnvelope.canopyAttenuation(canopy + 0.2, canopy), 0f)
    }

    @Test
    fun mistDistributionRemainsDeterministicPerUuid() {
        val a = onFogGroup("fog-oppo-a")
        val again = onFogGroup("fog-oppo-a")
        val b = onFogGroup("fog-oppo-b")
        assertEquals(a.particles, again.particles)
        assertEquals(FoggerMistAnimator.particlePositions(a, 0.33), FoggerMistAnimator.particlePositions(again, 0.33))
        assertNotEquals(a.particles.map { it.azimuthRad }, b.particles.map { it.azimuthRad })
        assertNotEquals(FoggerMistAnimator.particlePositions(a, 0.33), FoggerMistAnimator.particlePositions(b, 0.33))
    }

    @Test
    fun humidityHaloCirclesAreNotEmittedIntoTheArSceneWhileClassifierRemains() {
        val fog = overheadFogger("fog-oppo-halo")
        val snapshot = liveSnapshot(fog)
        val frame = FoggerEffectPlanner.plan(
            true, listOf(fog), mapOf(fog.id to EquipmentRuntimeState.ON), snapshot, thresholds
        )
        val group = frame.groups.single()
        assertEquals(0, group.haloCount)
        assertEquals(0, frame.haloObjectCount)
        assertFalse(FogMistVisual.RENDERS_SCENE_HUMIDITY_HALOS)
        assertTrue(group.humiditySamples.isNotEmpty())
        val details = FoggerEffectPlanner.details(
            visualizationRunning = true,
            selected = fog,
            runtimeById = mapOf(fog.id to EquipmentRuntimeState.ON),
            snapshot = snapshot,
            thresholds = thresholds,
            presentation = EquipmentPresentationContext(PreviewMode.DEMO, true)
        )!!
        assertNotNull(details.localRhPercent)
        assertNotEquals(HumidityLegendBand.UNAVAILABLE, details.humidityBand)
        assertTrue(details.classificationLabel.isNotBlank())
        assertTrue(details.qualitativeNote.contains(VisualizationCopy.PREDICTED_UNCALIBRATED))
        assertEquals(HumidityLegendClassifier.band(details.localRhPercent, fogger.fon, fogger.fsp, fogger.fdp), details.humidityBand)
    }

    @Test
    fun demoCoolingPadIsVisibleOnTheWallOppositeTheExhaustBeforeStart() = runTest {
        val vm = demoVm("cfg-oppo-pad-before")
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.EXHAUST_FAN)
        assertFalse(vm.state.value.behaviorRunning)
        val frame = vm.padInletEffectFrame()
        val panel = frame.panel
        assertNotNull(panel)
        val exhausts = vm.state.value.instances.filter { it.type == EquipmentType.EXHAUST_FAN && !it.isGhost }
        val layout = FanPadLayoutResolver.resolve(
            exhausts,
            DemoGreenhouseProfile.LENGTH_M,
            DemoGreenhouseProfile.WIDTH_M,
            FanPadWallSelection.AUTO
        )
        assertFalse(layout.conflict)
        assertEquals(layout.padWall, panel!!.wall)
        assertEquals(layout.commonWall?.let { FanPadLayoutResolver.opposite(it) }, panel.wall)
        assertNull(frame.stream)
        assertEquals(PadInletVisual.LABEL, "Cooling Pad")
        assertTrue(PadInletVisual.PANEL_VISIBLE_BEFORE_START)
        assertFalse(Phase3D2B2A.ADDS_PAD_GLB)
        assertNull(DemoFanPadProfile.neverForMyGreenhouse(PreviewMode.MY_GREENHOUSE))
    }

    @Test
    fun oneExhaustWallWithMultipleFansProducesOneSharedPad() = runTest {
        val vm = demoVm("cfg-oppo-pad-share")
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.EXHAUST_FAN)
        vm.duplicateSelected()
        val exhausts = vm.state.value.instances.filter { it.type == EquipmentType.EXHAUST_FAN && !it.isGhost }
        assertEquals(2, exhausts.size)
        val layout = FanPadLayoutResolver.resolve(
            exhausts,
            DemoGreenhouseProfile.LENGTH_M,
            DemoGreenhouseProfile.WIDTH_M,
            FanPadWallSelection.AUTO
        )
        assertFalse(layout.conflict)
        val frame = vm.padInletEffectFrame()
        assertNotNull(frame.panel)
        assertEquals(1, PadInletEffectPlanner.sharedPanelCount(exhausts.size))
        assertEquals(layout.padWall, frame.panel!!.wall)
    }

    @Test
    fun panelRemainsWhileExhaustIsOffAndWetActivePadPermitsCoolInlet() = runTest {
        val vm = demoVm("cfg-oppo-pad-wet")
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.EXHAUST_FAN)
        val before = vm.padInletEffectFrame()
        assertNotNull(before.panel)
        assertNull(before.stream)
        vm.setSimulatedTemperature(35.0)
        vm.setSimulatedHumidity(40.0)
        vm.startBehavior()
        val id = vm.state.value.instances.single { it.type == EquipmentType.EXHAUST_FAN }.id
        assertEquals(EquipmentRuntimeState.ON, vm.state.value.runtimeById[id])
        val wet = vm.padInletEffectFrame()
        assertNotNull(wet.panel)
        assertTrue(wet.panel!!.wetPadActive)
        assertNotNull(wet.stream)
        assertTrue(wet.stream!!.claimsEvaporativeCooling)
        assertTrue(wet.stream!!.wetPadActive)
        assertTrue(PadInletEffectPlanner.inletTravelsPadTowardExhaust(wet.panel!!.wall))
        vm.stopBehavior()
        val stopped = vm.padInletEffectFrame()
        assertNotNull(stopped.panel)
        assertNull(stopped.stream)
        assertTrue(PadInletVisual.PANEL_REMAINS_WHEN_EXHAUST_OFF)
        assertTrue(vm.foggerEffectFrame().isEmpty)
        assertTrue(vm.exhaustEffectFrame().isEmpty)
        assertTrue(vm.circulationEffectFrame().isEmpty)
    }

    @Test
    fun dryInactivePadDoesNotClaimEvaporativeCooling() = runTest {
        val vm = demoVm("cfg-oppo-pad-dry")
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.EXHAUST_FAN)
        vm.setDemoFanPadWetActive(false)
        vm.setSimulatedTemperature(35.0)
        vm.setSimulatedHumidity(40.0)
        vm.startBehavior()
        val frame = vm.padInletEffectFrame()
        assertNotNull(frame.panel)
        assertFalse(frame.panel!!.wetPadActive)
        assertNull(frame.stream)
        assertTrue(PadInletVisual.INLET_ARROWS_ARE_SEPARATE)
    }

    @Test
    fun myGreenhouseWithoutPadConfigurationShowsNoPad() = runTest {
        val vm = EquipmentPlacementViewModel(InMemoryEquipmentLayoutRepository())
        vm.attach(
            "grower@greenhands.app",
            "cfg-oppo-my-none",
            DemoGreenhouseProfile.LENGTH_M,
            DemoGreenhouseProfile.WIDTH_M,
            DemoGreenhouseProfile.HEIGHT_M,
            DemoGreenhouseProfile.CROP_ROW_COUNT,
            climate,
            PreviewMode.MY_GREENHOUSE
        )
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.EXHAUST_FAN)
        assertNull(vm.state.value.myPadPresent)
        assertTrue(vm.padInletEffectFrame().isEmpty)
        assertNull(vm.padInletEffectFrame().panel)
        assertNull(DemoFanPadProfile.neverForMyGreenhouse(PreviewMode.MY_GREENHOUSE))
        assertNotEquals(
            DemoFanPadProfile.ILLUSTRATIVE_EFFICIENCY,
            vm.resolvedFanPadBoundary().efficiencyFraction ?: -1.0,
            0.0
        )
    }

    @Test
    fun conflictingExhaustWallsDoNotInventAPadPairing() {
        val far = Phase3D2AFixtures.instance(
            EquipmentType.EXHAUST_FAN, "far-oppo", 1,
            xM = 2.5, yM = DemoGreenhouseProfile.LENGTH_M, zM = 1.5, yawDeg = 90.0
        )
        val near = Phase3D2AFixtures.instance(
            EquipmentType.EXHAUST_FAN, "near-oppo", 2,
            xM = 2.5, yM = 0.0, zM = 1.5, yawDeg = 270.0
        )
        val resolved = FanPadBoundaryResolver.resolve(
            PreviewMode.DEMO,
            DemoGreenhouseProfile.LENGTH_M,
            DemoGreenhouseProfile.WIDTH_M,
            DemoGreenhouseProfile.HEIGHT_M,
            AdaptiveGridPolicy.select(
                DemoGreenhouseProfile.LENGTH_M,
                DemoGreenhouseProfile.WIDTH_M,
                DemoGreenhouseProfile.HEIGHT_M
            ).cellEdgeM,
            listOf(far, near),
            WeatherBoundary.demoSimulated(35.0, 40.0),
            DemoFanPadProfile.DEFAULT.toBoundaryInput(
                DemoGreenhouseProfile.WIDTH_M,
                DemoGreenhouseProfile.HEIGHT_M
            )
        )
        assertNull(resolved.wall)
        assertFalse(resolved.quantitativeAvailable)
        val frame = PadInletEffectPlanner.plan(
            visualizationRunning = false,
            exhaustOn = false,
            instances = listOf(far, near),
            runtimeById = emptyMap(),
            snapshot = null,
            lengthM = DemoGreenhouseProfile.LENGTH_M,
            widthM = DemoGreenhouseProfile.WIDTH_M,
            heightM = DemoGreenhouseProfile.HEIGHT_M,
            thresholds = thresholds,
            targetTemperatureC = 25.0,
            resolved = resolved,
            showActiveEffects = true
        )
        assertNull(frame.panel)
        assertNull(frame.stream)
    }

    @Test
    fun padFollowsGreenhouseScaleRotationAndAnchor() {
        val wall = GreenhouseEndWall.NEAR_Y0
        val origin = com.greenhands.app.heat.runtime.adapter.FanPadIntakeFactory.padCenterDomain(
            DemoGreenhouseProfile.LENGTH_M,
            DemoGreenhouseProfile.WIDTH_M,
            wall,
            DemoGreenhouseProfile.HEIGHT_M
        )
        val dims = DemoGreenhouseProfile.dimensions()
        val layoutA = SceneScaleMapper.layout(dims, targetLongSpan = 10.0)
        val layoutB = SceneScaleMapper.layout(dims, targetLongSpan = 15.0)
        val a = CirculationRingSceneMath.scenePosition(origin, layoutA)
        val b = CirculationRingSceneMath.scenePosition(origin, layoutB)
        assertEquals(1.5, b.x / a.x, 1e-9)
        assertEquals(1.5, b.y / a.y, 1e-9)
        assertEquals(0.0, a.z, 1e-9)
        assertEquals(0.0, b.z, 1e-9)
        val yaw = CirculationRingSceneMath.sceneYawDeg(90.0, layoutA)
        assertTrue(yaw.isFinite())
        assertFalse(CirculationRingSceneMath.APPLIES_TABLETOP_SCALE_TO_CHILDREN)
        assertEquals("ar_greenhouse_root", CirculationRingSceneMath.GREENHOUSE_ROOT_NAME)
        val pinch = CirculationRingSceneMath.childWouldDoubleScaleIfMultipliedByRootScale(1.0, 2.0)
        assertTrue(abs(pinch - ArTabletopScale.worldScale(2.0)) < 1e-12)
    }

    @Test
    fun stopClearsActiveEffectsButNotTheFixedPadPanelAndLifecycleStaysSafe() = runTest {
        val vm = demoVm("cfg-oppo-life")
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.EXHAUST_FAN)
        vm.tapPalette(EquipmentType.FOGGER)
        vm.setSimulatedTemperature(35.0)
        vm.setSimulatedHumidity(40.0)
        val beforeStart = vm.padInletEffectFrame()
        assertNotNull(beforeStart.panel)
        vm.startBehavior()
        assertFalse(vm.foggerEffectFrame().isEmpty)
        assertFalse(vm.exhaustEffectFrame().isEmpty)
        assertNotNull(vm.padInletEffectFrame().stream)
        val padHost = ArFanPadAirflowHost(91)
        padHost.lifecycle.attach(91)
        padHost.bind(ArFanPadAirflowHost.request(vm.padInletEffectFrame(), ArTabletopPhase.PLACED))
        assertTrue(padHost.attached)
        val fogHost = ArFoggerMistHost(92)
        fogHost.lifecycle.attach(92)
        fogHost.bind(ArFoggerMistHost.request(vm.foggerEffectFrame(), ArTabletopPhase.PLACED))
        vm.stopBehavior()
        padHost.bind(ArFanPadAirflowHost.request(vm.padInletEffectFrame(), ArTabletopPhase.PLACED))
        fogHost.bind(ArFoggerMistHost.request(vm.foggerEffectFrame(), ArTabletopPhase.PLACED))
        assertNotNull(vm.padInletEffectFrame().panel)
        assertNull(vm.padInletEffectFrame().stream)
        assertTrue(padHost.attached)
        assertTrue(vm.foggerEffectFrame().isEmpty)
        assertTrue(fogHost.attachedUuids.isEmpty())
        assertTrue(vm.exhaustEffectFrame().isEmpty)
        vm.setAirflowFogVisible(false)
        assertNotNull(vm.padInletEffectFrame().panel)
        assertTrue(vm.foggerEffectFrame().isEmpty)
        vm.onArReposition()
        val reposition = padHost.lifecycle.repositionSequence(
            visualizationStillRunning = vm.state.value.behaviorRunning
        )
        assertEquals(ArPhysicsEffectCleanup.REPOSITION, reposition)
        assertTrue(
            reposition.indexOf(ArPhysicsEffectCleanup.DETACH_NODES) <
                reposition.indexOf(ArPhysicsEffectCleanup.REPLACE_ANCHOR)
        )
        val leave = ArPhysicsEffectLifecycle(93)
        assertTrue(leave.attach(93))
        val order = leave.leaveArSequence()
        assertEquals(ArPhysicsEffectCleanup.LEAVE_AR, order)
        val dead = ArPhysicsEffectLifecycle(94)
        dead.markHostUnavailable()
        val engine = dead.engineOperationCount
        val scene = dead.sceneOperationCount
        assertFalse(dead.clearEffectsWhileSceneAlive())
        assertTrue(dead.abandonAfterHostUnavailable())
        assertEquals(engine, dead.engineOperationCount)
        assertEquals(scene, dead.sceneOperationCount)
        assertFalse(ArCirculationColorMaterial.maySetParameter("emissiveFactor"))
        assertTrue(ArCirculationColorMaterial.maySetParameter("color"))
        assertTrue("padFrame" in ArLiveRendererOwnership.FORBIDDEN_REMEMBER_KEYS)
        assertTrue("fogFrame" in ArLiveRendererOwnership.FORBIDDEN_REMEMBER_KEYS)
        assertEquals(setOf("engine"), ArLiveRendererOwnership.REMEMBER_KEYS)
    }

    @Test
    fun circulationRingsExhaustArrowsAndExhaustOnlyProbesRemainUnchanged() = runTest {
        assertEquals(7, CirculationRingVisual.MAX_RINGS_PER_FAN)
        assertEquals(24, CirculationRingVisual.MAX_TOTAL_RINGS)
        assertEquals(6, ExhaustAirflowVisual.MAX_INDOOR_ARROWS_PER_FAN)
        assertEquals(2, ExhaustAirflowVisual.MAX_OUTDOOR_ARROWS_PER_FAN)
        assertFalse(OfficialValidationTransect.SHOWS_FOGGER_MARKERS)
        assertFalse(OfficialValidationTransect.SHOWS_CIRCULATION_MARKERS)
        assertTrue(OfficialValidationTransect.isPublicEquipment(EquipmentType.EXHAUST_FAN))
        assertFalse(OfficialValidationTransect.isPublicEquipment(EquipmentType.FOGGER))
        assertFalse(OfficialValidationTransect.isPublicEquipment(EquipmentType.CIRCULATION_FAN))
        assertFalse(Phase3D2B3.PUBLIC_FOGGER_PROBES)
        assertFalse(Phase3D2B3.PUBLIC_CIRCULATION_PROBES)
        assertFalse(Phase3D2B3.WRITES_RENDERER_DISTANCE_DECAY)
        assertFalse(Phase3D2B3.CHANGES_PHASE_3A)
        val vm = demoVm("cfg-oppo-frozen")
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.EXHAUST_FAN)
        vm.setSimulatedTemperature(35.0)
        vm.setSimulatedHumidity(40.0)
        vm.startBehavior()
        val exhaustId = vm.state.value.instances.single { it.type == EquipmentType.EXHAUST_FAN }.id
        vm.select(exhaustId)
        assertTrue(vm.visualizationProbeMarkers().isNotEmpty())
        val fogVm = demoVm("cfg-oppo-frozen-fog")
        fogVm.enterPlacementMode()
        fogVm.tapPalette(EquipmentType.FOGGER)
        fogVm.setSimulatedTemperature(35.0)
        fogVm.setSimulatedHumidity(40.0)
        fogVm.startBehavior()
        fogVm.select(fogVm.state.value.instances.single { it.type == EquipmentType.FOGGER }.id)
        assertTrue(fogVm.visualizationProbeMarkers().isEmpty())
        val circVm = demoVm("cfg-oppo-frozen-circ")
        circVm.enterPlacementMode()
        circVm.tapPalette(EquipmentType.CIRCULATION_FAN)
        circVm.setSimulatedTemperature(35.0)
        circVm.setSimulatedHumidity(40.0)
        circVm.startBehavior()
        circVm.select(circVm.state.value.instances.single { it.type == EquipmentType.CIRCULATION_FAN }.id)
        assertTrue(circVm.visualizationProbeMarkers().isEmpty())
        assertFalse(EquipmentLabelCatalog.contents(vm.state.value.instances, exhaustId).any {
            it.id == PadInletVisual.LABEL_ID
        })
        assertEquals("Cooling Pad", PadInletVisual.LABEL)
    }

    private fun onFogGroup(id: String): FoggerMistGroup {
        val fog = overheadFogger(id)
        val snapshot = liveSnapshot(fog)
        return FoggerEffectPlanner.plan(
            true, listOf(fog), mapOf(fog.id to EquipmentRuntimeState.ON), snapshot, thresholds
        ).groups.single()
    }

    private fun overheadFogger(id: String): EquipmentInstance =
        Phase3D2AFixtures.instance(
            EquipmentType.FOGGER, id, 1, xM = 2.5, yM = 5.0, zM = 2.2, yawDeg = 0.0
        )

    private fun liveSnapshot(fog: EquipmentInstance) = PhysicsFieldRuntime().run {
        val snap = start(
            Phase3D2AFixtures.demoRequest(
                listOf(fog),
                35.0,
                40.0,
                weather = WeatherBoundary.demoSimulated(25.0, 70.0),
                lengthM = DemoGreenhouseProfile.LENGTH_M,
                widthM = DemoGreenhouseProfile.WIDTH_M,
                heightM = DemoGreenhouseProfile.HEIGHT_M,
                runtimeById = mapOf(fog.id to EquipmentRuntimeState.ON)
            )
        )
        stop()
        snap
    }

    private fun demoVm(configId: String): EquipmentPlacementViewModel {
        val vm = EquipmentPlacementViewModel(InMemoryEquipmentLayoutRepository())
        vm.attach(
            "grower@greenhands.app",
            configId,
            DemoGreenhouseProfile.LENGTH_M,
            DemoGreenhouseProfile.WIDTH_M,
            DemoGreenhouseProfile.HEIGHT_M,
            DemoGreenhouseProfile.CROP_ROW_COUNT,
            climate,
            PreviewMode.DEMO
        )
        return vm
    }
}
