package com.greenhands.app.heat.visualization

import com.greenhands.app.ar.domain.ArTabletopPhase
import com.greenhands.app.ar.domain.ArTabletopScale
import com.greenhands.app.ar.render.ArCirculationColorMaterial
import com.greenhands.app.ar.render.ArFanPadAirflowHost
import com.greenhands.app.ar.render.ArFoggerMistHost
import com.greenhands.app.ar.render.ArLiveRendererOwnership
import com.greenhands.app.ar.render.ArPhysicsEffectCleanup
import com.greenhands.app.ar.render.ArPhysicsEffectLifecycle
import com.greenhands.app.equipment.data.InMemoryEquipmentLayoutRepository
import com.greenhands.app.equipment.model.EquipmentRuntimeState
import com.greenhands.app.equipment.model.EquipmentType
import com.greenhands.app.equipment.ui.EquipmentPlacementViewModel
import com.greenhands.app.equipment.ui.OverlayClimate
import com.greenhands.app.heat.domain.HeatFormulas
import com.greenhands.app.heat.runtime.Phase3D2AFixtures
import com.greenhands.app.heat.runtime.PhysicsFieldRuntime
import com.greenhands.app.heat.runtime.adapter.UncalibratedPhase3AProfileSettings
import com.greenhands.app.heat.runtime.contracts.GreenhouseEndWall
import com.greenhands.app.heat.runtime.contracts.WeatherBoundary
import com.greenhands.app.preview3d.domain.CropRowGeometry
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
class OppoVisualCloudAndPadCorrectionTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val circ = HeatFormulas.circulation(25.0)
    private val climate = OverlayClimate(
        crop = null,
        stage = null,
        temperatureLabel = "25.0°C",
        humidityLabel = "70.0 % RH",
        vpdLabel = "Not published",
        circulation = circ,
        exhaust = HeatFormulas.exhaustFromCirculation(circ),
        fogger = HeatFormulas.fogger(70.0),
        optimalTemperatureC = 25.0,
        optimalHumidityPercent = 70.0
    )
    private val thresholds = Phase3D2AFixtures.thresholds

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
    fun canopyIsPorousAttenuationNotAHardClipWhileWallsAndFloorStillClip() {
        val fog = overheadFogger("fog-cloud-canopy")
        val snapshot = liveSnapshot(fog)
        val group = FoggerEffectPlanner.plan(
            true, listOf(fog), mapOf(fog.id to EquipmentRuntimeState.ON), snapshot, thresholds
        ).groups.single()
        val canopy = DemoGreenhouseProfile.HEIGHT_M * CropRowGeometry.HEIGHT_FILL
        assertFalse(FoggerVisualEnvelope.HARD_CLIPS_CANOPY)
        assertTrue(FoggerVisualEnvelope.FLOOR_IS_HARD_BOUNDARY)
        assertTrue(FoggerVisualEnvelope.WALLS_ARE_HARD_BOUNDARY)
        assertEquals(2.0, UncalibratedPhase3AProfileSettings.FOGGER_PLUME_LENGTH_M, 0.0)
        assertTrue(group.maxDistanceM > canopy)
        assertEquals(
            0.40,
            FoggerVisualEnvelope.clippedPathM(2.0, 10.0, 0.40),
            1e-9
        )
        assertTrue(FoggerVisualEnvelope.clippedPathM(2.0, 10.0, 2.2) <= 2.0 + 1e-9)
        val points = FoggerMistAnimator.particlePositions(group, 0.0)
        assertTrue(points.any { it.zM < canopy - 0.05 })
        val later = FoggerMistAnimator.particlePositions(group, 1.8)
        assertNotEquals(points, later)
        assertEquals(
            FoggerMistAnimator.particlePositions(group, 0.0),
            FoggerMistAnimator.particlePositions(group, 0.0)
        )
        points.forEach { point ->
            assertTrue(
                FoggerVisualEnvelope.insideHardBoundaries(
                    point,
                    DemoGreenhouseProfile.LENGTH_M,
                    DemoGreenhouseProfile.WIDTH_M,
                    DemoGreenhouseProfile.HEIGHT_M
                )
            )
            assertTrue(FoggerMistEnvelope.contains(snapshot, point))
        }
        val above = FoggerVisualEnvelope.canopyAttenuation(canopy + 0.4, canopy)
        val mid = FoggerVisualEnvelope.canopyAttenuation(canopy * 0.5, canopy)
        val deep = FoggerVisualEnvelope.canopyAttenuation(0.05, canopy)
        assertEquals(1f, above, 0f)
        assertTrue(mid < above)
        assertTrue(deep < mid)
        assertTrue(deep >= FoggerVisualEnvelope.MIN_CANOPY_ALPHA)
        assertNotEquals(
            FoggerMistAnimator.particlePositions(group, 0.2),
            FoggerMistAnimator.particlePositions(group, 1.8)
        )
    }

    @Test
    fun mistCloudCoversRadialDirectionsAndDepthLayersWithoutADottedLine() {
        val group = onGroup("fog-cloud-fill")
        val points = FoggerMistAnimator.particlePositions(group, 0.0)
        assertEquals(FogMistVisual.MAX_PARTICLES_PER_FOGGER, group.particleCount)
        assertTrue(group.particleCount > FogMistVisual.MAX_SCENE_NODES_PER_FOGGER)
        assertEquals(FogMistVisual.MAX_SCENE_NODES_PER_FOGGER, FogMistVisual.sceneNodesPerFogger())
        assertTrue(FogMistDistribution.combinedMeshIncreasesVisibleSpecksWithoutNodeGrowth())
        assertFalse(FogMistDistribution.areCollinear(points))
        assertTrue(FogMistDistribution.coversMultipleRadialDirections(points, group.nozzleOrigin(0)))
        assertTrue(FogMistDistribution.uniqueDistanceLayers(group.particles) >= 6)
        assertTrue(FogMistDistribution.uniqueAzimuthBuckets(group.particles) >= 6)
        val (spanX, spanY) = FogMistDistribution.horizontalSpanM(points)
        assertTrue(spanX > 0.10)
        assertTrue(spanY > 0.10)
        val fractions = group.particles.map { it.distanceFraction }
        assertTrue(fractions.min() < 0.20)
        assertTrue(fractions.max() > 0.75)
        val along = points.mapIndexed { index, point ->
            FoggerMistAnimator.alongSprayM(group, point, group.particles[index].nozzleIndex)
        }
        val near = points.zip(along).filter { it.second < group.maxDistanceM * 0.34 }
        val far = points.zip(along).filter { it.second > group.maxDistanceM * 0.67 }
        assertTrue(near.isNotEmpty() && far.isNotEmpty())
        val nearRadius = near.map { hypotOffset(it.first, group.nozzleOrigin(0)) }.average()
        val farRadius = far.map { hypotOffset(it.first, group.nozzleOrigin(0)) }.average()
        assertTrue(farRadius > nearRadius)
        assertFalse(FogMistVisual.CLOUD_IS_STATIC)
        assertTrue(FogMistVisual.ANIMATES_BY_VERTEX_BUFFER)
        assertFalse(FogMistVisual.CREATES_OR_DESTROYS_NODES_PER_FRAME)
        val tabletop = ArTabletopScale.visualMetresPerDomainMetre(DemoGreenhouseProfile.dimensions())
        val speckOnTable = FogMistAppearance.STREAK_LENGTH_M * tabletop
        assertTrue(speckOnTable > 0.0015)
        assertTrue(speckOnTable < 0.010)
        assertTrue(FogMistAppearance.NEAR_ALPHA > FogMistAppearance.FAR_ALPHA)
        assertFalse(FogMistVisual.PARTICLE_COUNT_IS_WATER_FLOW)
        assertEquals(40.0, UncalibratedPhase3AProfileSettings.FOGGER_PLUME_ANGLE_DEG, 0.0)
        assertEquals(2.0, UncalibratedPhase3AProfileSettings.FOGGER_PLUME_LENGTH_M, 0.0)
    }

    @Test
    fun uuidSeededCloudPatternIsDeterministic() {
        val a = onGroup("fog-cloud-seed-a")
        val again = onGroup("fog-cloud-seed-a")
        val b = onGroup("fog-cloud-seed-b")
        assertEquals(a.particles, again.particles)
        assertEquals(FoggerMistAnimator.particlePositions(a, 0.4), FoggerMistAnimator.particlePositions(again, 0.4))
        assertNotEquals(FoggerMistAnimator.particlePositions(a, 0.4), FoggerMistAnimator.particlePositions(a, 1.1))
        assertNotEquals(a.particles.map { it.azimuthRad }, b.particles.map { it.azimuthRad })
        assertNotEquals(
            FoggerMistAnimator.particlePositions(a, 0.0),
            FoggerMistAnimator.particlePositions(b, 0.0)
        )
    }

    @Test
    fun padStaysInsideWallWithBoundedFrameAndInternalCorrugation() {
        val (w, h) = PadPanelLayout.sizeInsideWall(5.0, 3.0, 5.0, 3.0)
        assertTrue(w < 5.0)
        assertTrue(h < 3.0)
        val origin = PadPanelLayout.visualOrigin(
            DemoGreenhouseProfile.LENGTH_M,
            DemoGreenhouseProfile.WIDTH_M,
            GreenhouseEndWall.NEAR_Y0,
            h
        )
        assertTrue(
            PadPanelLayout.boundingBoxInsideWall(
                origin,
                w,
                h,
                GreenhouseEndWall.NEAR_Y0,
                DemoGreenhouseProfile.LENGTH_M,
                DemoGreenhouseProfile.WIDTH_M,
                DemoGreenhouseProfile.HEIGHT_M
            )
        )
        assertTrue(PadPanelLayout.hasBoundedFrame(w, h))
        assertTrue(PadPanelLayout.ribsStayInsideFrame(w, h))
        val quads = PadPanelLayout.quads(w, h)
        val frames = quads.filter { it.isFrame }
        val ribs = quads.filter { !it.isFrame }
        assertEquals(4, frames.size)
        assertTrue(ribs.size >= PadPanelLayout.RIB_COUNT)
        ribs.forEach { rib ->
            val vertical = rib.height >= rib.width
            if (vertical) {
                assertTrue(rib.width < w * 0.35)
            } else {
                assertTrue(rib.height < h * 0.35)
            }
        }
        val compact = PadPanelLayout.sizeInsideWall(1.2, 1.0, 5.0, 3.0)
        assertEquals(1.2, compact.first, 1e-9)
        assertEquals(1.0, compact.second, 1e-9)
        assertTrue(PadPanelLayout.SEPARATE_FROM_INLET_ARROWS)
        assertNotEquals(PadPanelLayout.RENDER_DESCRIPTION, PadPanelLayout.INLET_ARROW_DESCRIPTION)
        assertTrue(PadInletVisual.PANEL_IS_FIXED_STRUCTURE)
        assertTrue(PadInletVisual.INLET_ARROWS_ARE_SEPARATE)
        assertEquals("Cooling Pad", PadInletVisual.LABEL)
        assertFalse(Phase3D2B2A.ADDS_PAD_GLB)
        assertTrue(PadPanelLayout.FACE_OFFSET_M > 0.0)
        assertTrue(PadPanelLayout.RIB_WIDTH_M < 0.05)
    }

    @Test
    fun plannedDemoPadFitsTheSelectedWallAndKeepsArrowsSeparate() = runTest {
        val vm = demoVm("cfg-cloud-pad")
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.EXHAUST_FAN)
        val before = vm.padInletEffectFrame()
        val panel = before.panel
        assertNotNull(panel)
        assertNull(before.stream)
        assertTrue(
            PadPanelLayout.boundingBoxInsideWall(
                panel!!.originM,
                panel.widthM,
                panel.heightM,
                panel.wall,
                DemoGreenhouseProfile.LENGTH_M,
                DemoGreenhouseProfile.WIDTH_M,
                DemoGreenhouseProfile.HEIGHT_M
            )
        )
        when (panel.wall) {
            GreenhouseEndWall.NEAR_Y0 ->
                assertEquals(PadPanelLayout.FACE_OFFSET_M, panel.originM.yM, 1e-9)
            GreenhouseEndWall.FAR_Y_LENGTH ->
                assertEquals(
                    DemoGreenhouseProfile.LENGTH_M - PadPanelLayout.FACE_OFFSET_M,
                    panel.originM.yM,
                    1e-9
                )
            GreenhouseEndWall.NEAR_X0 ->
                assertEquals(PadPanelLayout.FACE_OFFSET_M, panel.originM.xM, 1e-9)
            GreenhouseEndWall.FAR_X_WIDTH ->
                assertEquals(
                    DemoGreenhouseProfile.WIDTH_M - PadPanelLayout.FACE_OFFSET_M,
                    panel.originM.xM,
                    1e-9
                )
        }
        assertTrue(panel.widthM < FanPadWallRun(panel.wall))
        assertTrue(panel.heightM < DemoGreenhouseProfile.HEIGHT_M)
        vm.setSimulatedTemperature(35.0)
        vm.setSimulatedHumidity(40.0)
        vm.startBehavior()
        val wet = vm.padInletEffectFrame()
        assertNotNull(wet.panel)
        assertNotNull(wet.stream)
        assertTrue(wet.stream!!.claimsEvaporativeCooling)
        assertNotEquals(PadPanelLayout.RENDER_DESCRIPTION, PadPanelLayout.INLET_ARROW_DESCRIPTION)
        vm.setDemoFanPadWetActive(false)
        val dry = vm.padInletEffectFrame()
        assertNotNull(dry.panel)
        assertFalse(dry.panel!!.wetPadActive)
        assertNull(dry.stream)
        vm.setDemoFanPadWetActive(true)
        vm.stopBehavior()
        val stopped = vm.padInletEffectFrame()
        assertNotNull(stopped.panel)
        assertNull(stopped.stream)
        assertTrue(PadInletVisual.PANEL_REMAINS_WHEN_EXHAUST_OFF)
        assertEquals(stopped.panel!!.widthM, panel.widthM, 1e-9)
        assertEquals(stopped.panel!!.heightM, panel.heightM, 1e-9)
    }

    @Test
    fun sceneViewLifecycleAndMaterialSafetyRemainUnchanged() = runTest {
        val vm = demoVm("cfg-cloud-life")
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.EXHAUST_FAN)
        vm.tapPalette(EquipmentType.FOGGER)
        vm.setSimulatedTemperature(35.0)
        vm.setSimulatedHumidity(40.0)
        vm.startBehavior()
        val padHost = ArFanPadAirflowHost(101)
        padHost.lifecycle.attach(101)
        padHost.bind(ArFanPadAirflowHost.request(vm.padInletEffectFrame(), ArTabletopPhase.PLACED))
        val fogHost = ArFoggerMistHost(102)
        fogHost.lifecycle.attach(102)
        fogHost.bind(ArFoggerMistHost.request(vm.foggerEffectFrame(), ArTabletopPhase.PLACED))
        val fogFrame = vm.foggerEffectFrame()
        assertEquals(3, fogFrame.sceneNodeObjectCount)
        assertTrue(fogFrame.particleObjectCount > fogFrame.sceneNodeObjectCount)
        vm.stopBehavior()
        padHost.bind(ArFanPadAirflowHost.request(vm.padInletEffectFrame(), ArTabletopPhase.PLACED))
        fogHost.bind(ArFoggerMistHost.request(vm.foggerEffectFrame(), ArTabletopPhase.PLACED))
        assertNotNull(vm.padInletEffectFrame().panel)
        assertNull(vm.padInletEffectFrame().stream)
        assertTrue(vm.foggerEffectFrame().isEmpty)
        val leave = ArPhysicsEffectLifecycle(103)
        assertTrue(leave.attach(103))
        assertEquals(ArPhysicsEffectCleanup.LEAVE_AR, leave.leaveArSequence())
        val dead = ArPhysicsEffectLifecycle(104)
        dead.markHostUnavailable()
        val engine = dead.engineOperationCount
        val scene = dead.sceneOperationCount
        assertFalse(dead.clearEffectsWhileSceneAlive())
        assertTrue(dead.abandonAfterHostUnavailable())
        assertEquals(engine, dead.engineOperationCount)
        assertEquals(scene, dead.sceneOperationCount)
        assertFalse(ArCirculationColorMaterial.maySetParameter("emissiveFactor"))
        assertTrue(ArCirculationColorMaterial.maySetParameter("color"))
        assertEquals(setOf("engine"), ArLiveRendererOwnership.REMEMBER_KEYS)
        assertTrue("fogFrame" in ArLiveRendererOwnership.FORBIDDEN_REMEMBER_KEYS)
        assertTrue("padFrame" in ArLiveRendererOwnership.FORBIDDEN_REMEMBER_KEYS)
        assertFalse(FogMistAppearance.WRITES_EMISSIVE_FACTOR)
        assertFalse(PadInletAppearance.WRITES_EMISSIVE_FACTOR)
        assertEquals(7, CirculationRingVisual.MAX_RINGS_PER_FAN)
        assertEquals(6, ExhaustAirflowVisual.MAX_INDOOR_ARROWS_PER_FAN)
        assertEquals(2, ExhaustAirflowVisual.MAX_OUTDOOR_ARROWS_PER_FAN)
        assertFalse(OfficialValidationTransect.SHOWS_FOGGER_MARKERS)
        assertFalse(Phase3D2B3.CHANGES_PHASE_3A)
        assertFalse(Phase3D2B3.WRITES_EMISSIVE_FACTOR)
    }

    private fun FanPadWallRun(wall: GreenhouseEndWall): Double =
        com.greenhands.app.heat.runtime.adapter.FanPadLayoutResolver.wallRunM(
            wall,
            DemoGreenhouseProfile.LENGTH_M,
            DemoGreenhouseProfile.WIDTH_M
        )

    private fun hypotOffset(
        point: com.greenhands.app.equipment.model.DomainPoint,
        origin: com.greenhands.app.equipment.model.DomainPoint
    ): Double {
        val dx = point.xM - origin.xM
        val dy = point.yM - origin.yM
        return kotlin.math.hypot(dx, dy)
    }

    private fun onGroup(id: String): FoggerMistGroup {
        val fog = overheadFogger(id)
        val snapshot = liveSnapshot(fog)
        return FoggerEffectPlanner.plan(
            true, listOf(fog), mapOf(fog.id to EquipmentRuntimeState.ON), snapshot, thresholds
        ).groups.single()
    }

    private fun overheadFogger(id: String) =
        Phase3D2AFixtures.instance(
            EquipmentType.FOGGER, id, 1, xM = 2.5, yM = 5.0, zM = 2.2, yawDeg = 0.0
        )

    private fun liveSnapshot(fog: com.greenhands.app.equipment.model.EquipmentInstance) =
        PhysicsFieldRuntime().run {
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
