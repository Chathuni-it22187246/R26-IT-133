package com.greenhands.app.heat.visualization

import com.greenhands.app.ar.domain.ArTabletopPhase
import com.greenhands.app.ar.render.ArCirculationColorMaterial
import com.greenhands.app.ar.render.ArFoggerMistHost
import com.greenhands.app.ar.render.ArLiveRendererOwnership
import com.greenhands.app.ar.render.ArPhysicsEffectCleanup
import com.greenhands.app.ar.render.ArPhysicsEffectLifecycle
import com.greenhands.app.ar.render.FogCloudFlake
import com.greenhands.app.ar.render.FogDropletMesh
import com.greenhands.app.equipment.data.InMemoryEquipmentLayoutRepository
import com.greenhands.app.equipment.model.EquipmentInstance
import com.greenhands.app.equipment.model.EquipmentRuntimeState
import com.greenhands.app.equipment.model.EquipmentType
import com.greenhands.app.equipment.ui.EquipmentPlacementViewModel
import com.greenhands.app.equipment.ui.OverlayClimate
import com.greenhands.app.heat.domain.HeatFormulas
import com.greenhands.app.heat.runtime.Phase3D2AFixtures
import com.greenhands.app.heat.runtime.PhysicsFieldRuntime
import com.greenhands.app.heat.runtime.adapter.UncalibratedPhase3AProfileSettings
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FoggerMistAnimationCorrectionTest {

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
    fun positionsChangeWithTimeButStayDeterministicPerUuidAndTime() {
        val a = onGroup("fog-anim-a")
        val again = onGroup("fog-anim-a")
        val b = onGroup("fog-anim-b")
        val t0 = FoggerMistAnimator.particlePositions(a, 0.35)
        val t1 = FoggerMistAnimator.particlePositions(a, 0.95)
        assertNotEquals(t0, t1)
        assertEquals(t0, FoggerMistAnimator.particlePositions(again, 0.35))
        assertEquals(a.particles, again.particles)
        assertTrue(FogMistDistribution.uniquePhases(a.particles) >= 20)
        assertNotEquals(a.particles.map { it.phase }, b.particles.map { it.phase })
        assertNotEquals(
            FoggerMistAnimator.particlePositions(a, 0.35),
            FoggerMistAnimator.particlePositions(b, 0.35)
        )
        assertFalse(FogMistVisual.CLOUD_IS_STATIC)
        assertFalse(FogMistVisual.ANIMATION_IS_MEASURED_VELOCITY)
        assertEquals(2.0, UncalibratedPhase3AProfileSettings.FOGGER_PLUME_LENGTH_M, 0.0)
        assertEquals(20.0, FoggerVisualEnvelope.acceptedHalfAngleDeg(), 0.0)
    }

    @Test
    fun movingParticlesFillTheConeStayInsideAndFadeRecycleContinuously() {
        val fog = overheadFogger("fog-anim-cone")
        val snapshot = liveSnapshot(fog)
        val group = FoggerEffectPlanner.plan(
            true, listOf(fog), mapOf(fog.id to EquipmentRuntimeState.ON), snapshot, thresholds
        ).groups.single()
        val canopy = DemoGreenhouseProfile.HEIGHT_M * CropRowGeometry.HEIGHT_FILL
        listOf(0.0, 0.4, 0.9, 1.6).forEach { time ->
            val points = FoggerMistAnimator.particlePositions(group, time)
            assertFalse(FogMistDistribution.areCollinear(points))
            assertTrue(FogMistDistribution.coversMultipleRadialDirections(points, group.nozzleOrigin(0)))
            val alongs = points.mapIndexed { index, point ->
                FoggerMistAnimator.alongSprayM(group, point, group.particles[index].nozzleIndex)
            }
            val layers = alongs.map {
                ((it / group.maxDistanceM) * FogMistVisual.DEPTH_LAYERS).toInt().coerceIn(0, 7)
            }.toSet()
            assertTrue(layers.size >= 4)
            points.forEachIndexed { index, point ->
                assertTrue(FoggerMistAnimator.insideAcceptedCone(group, point, group.particles[index].nozzleIndex))
                assertTrue(
                    FoggerVisualEnvelope.insideHardBoundaries(
                        point,
                        DemoGreenhouseProfile.LENGTH_M,
                        DemoGreenhouseProfile.WIDTH_M,
                        DemoGreenhouseProfile.HEIGHT_M
                    )
                )
                assertTrue(FoggerMistEnvelope.contains(snapshot, point))
                val along = alongs[index]
                assertTrue(along + 1e-9 >= 0.0)
                assertTrue(along <= group.maxDistanceM + FogMistVisual.CONE_SLACK_M)
            }
        }
        assertTrue(FoggerMistAnimator.particlePositions(group, 0.55).any { it.zM < canopy - 0.05 })
        assertFalse(FoggerVisualEnvelope.HARD_CLIPS_CANOPY)
        val above = FoggerVisualEnvelope.canopyAttenuation(canopy + 0.3, canopy)
        val deep = FoggerVisualEnvelope.canopyAttenuation(0.1, canopy)
        assertEquals(1f, above, 0f)
        assertTrue(deep < above)
        assertTrue(deep >= FoggerVisualEnvelope.MIN_CANOPY_ALPHA)
        assertTrue(FoggerMistAnimator.recycleIsBoundedAndContinuous())
        assertTrue(FoggerMistAnimator.recyclesAtEnvelopeBoundary(0.90))
        assertTrue(FoggerMistAnimator.fadeAtProgress(0.0) <= 0.05f)
        assertTrue(FoggerMistAnimator.fadeAtProgress(0.99) < FoggerMistAnimator.fadeAtProgress(0.40))
        assertEquals(0.0, FoggerVisualEnvelope.clippedPathM(2.0, 10.0, 0.0), 0.0)
        assertEquals(0.40, FoggerVisualEnvelope.clippedPathM(2.0, 10.0, 0.40), 1e-9)
    }

    @Test
    fun animationFramesDoNotAdvanceTheSolverOrAllocatePerFrameNodes() {
        val fog = overheadFogger("fog-anim-solver")
        val runtime = PhysicsFieldRuntime()
        val started = runtime.start(
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
        val group = FoggerEffectPlanner.plan(
            true, listOf(fog), mapOf(fog.id to EquipmentRuntimeState.ON), started, thresholds
        ).groups.single()
        val elapsed = runtime.elapsedTimeS()
        val mean = started.meanTemperatureC
        repeat(16) { step ->
            FoggerMistAnimator.particlePositions(group, 0.05 * step)
            FoggerMistAnimator.fadeAtProgress(FoggerMistAnimator.particleProgress(group, 0, 0.05 * step))
        }
        assertEquals(elapsed, runtime.elapsedTimeS(), 0.0)
        assertEquals(mean, runtime.snapshot()!!.meanTemperatureC, 0.0)
        runtime.stop()
        assertTrue(FogMistVisual.ANIMATES_BY_VERTEX_BUFFER)
        assertEquals("GeometryNode.updateGeometry", FogMistVisual.SCENE_VIEW_VERTEX_UPDATE_API)
        assertFalse(FogMistVisual.CREATES_OR_DESTROYS_NODES_PER_FRAME)
        assertEquals(3, FogMistVisual.MAX_SCENE_NODES_PER_FOGGER)
        assertEquals(
            FogMistVisual.MAX_FOGGER_GROUPS * FogMistVisual.MAX_PARTICLES_PER_FOGGER,
            FogMistVisual.MAX_TOTAL_PARTICLES
        )
        assertEquals(12, FogMistVisual.MAX_TOTAL_SCENE_NODES)
        assertFalse(FogMistAppearance.WRITES_EMISSIVE_FACTOR)
        assertFalse(ArCirculationColorMaterial.maySetParameter("emissiveFactor"))
        assertTrue(ArCirculationColorMaterial.maySetParameter("color"))
        assertEquals(setOf("engine"), ArLiveRendererOwnership.REMEMBER_KEYS)
        assertFalse(Phase3D2B3.CHANGES_PHASE_3A)
        assertEquals(7, CirculationRingVisual.MAX_RINGS_PER_FAN)
        assertEquals(6, ExhaustAirflowVisual.MAX_INDOOR_ARROWS_PER_FAN)
        assertTrue(PadInletVisual.PANEL_IS_FIXED_STRUCTURE)
        assertFalse(OfficialValidationTransect.SHOWS_FOGGER_MARKERS)
        val visible = FogCloudFlake(
            0f, 0f, 0f,
            FogMistAppearance.DROPLET_SIZE_M.toFloat(),
            FogMistAppearance.STREAK_LENGTH_M.toFloat(),
            1f, 0.25f, -0.90f, 0.35f
        )
        val fading = FogCloudFlake(
            0.2f, 0f, 0f,
            FogMistAppearance.FAR_DROPLET_SIZE_M.toFloat(),
            FogMistAppearance.FAR_STREAK_LENGTH_M.toFloat(),
            0.20f, 0.35f, -0.85f, -0.20f
        )
        val verts = FogDropletMesh.verticesOf(listOf(visible, fading))
        assertEquals(FogDropletMesh.VERTICES_PER_FLAKE * 2, verts.size)
        val padded = FogDropletMesh.padded(listOf(visible), 4)
        assertEquals(4, padded.size)
        assertEquals(FogCloudFlake.HIDDEN, padded.last())
        assertEquals(
            FogDropletMesh.VERTICES_PER_FLAKE * 4,
            FogDropletMesh.verticesOf(padded).size
        )
        val ys = verts.map { it.position.y }
        assertTrue(ys.max() - ys.min() > 0.02f)
        assertTrue(FogDropletMesh.visibleFromOrthogonalViews(verts.take(FogDropletMesh.VERTICES_PER_FLAKE)))
        assertTrue(
            FogDropletMesh.clampedAspect(
                FogMistAppearance.DROPLET_SIZE_M.toFloat(),
                FogMistAppearance.STREAK_LENGTH_M.toFloat()
            ) <= FogMistAppearance.MAX_STREAK_TO_WIDTH + 1e-6f
        )
        assertEquals(
            FoggerMistAnimator.particlePositions(group, 0.0),
            FoggerMistAnimator.particlePositions(group, FogMistVisual.CYCLE_S)
        )
    }

    @Test
    fun offStopDeleteRepositionAndLeaveArClearTheCorrectPlume() = runTest {
        val vm = demoVm("cfg-fog-anim-life")
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.FOGGER)
        vm.duplicateSelected()
        vm.setSimulatedTemperature(35.0)
        vm.setSimulatedHumidity(40.0)
        vm.startBehavior()
        val foggers = vm.state.value.instances.filter { it.type == EquipmentType.FOGGER && !it.isGhost }
        assertEquals(2, foggers.size)
        val host = ArFoggerMistHost(121)
        host.lifecycle.attach(121)
        host.bind(ArFoggerMistHost.request(vm.foggerEffectFrame(), ArTabletopPhase.PLACED))
        assertEquals(2, host.attachedUuids.size)
        val epoch = host.resourceEpoch
        val keep = foggers.first { it.displayNumber == 2 }
        vm.select(foggers.first { it.displayNumber == 1 }.id)
        vm.requestDelete()
        vm.deleteSelected()
        host.bind(ArFoggerMistHost.request(vm.foggerEffectFrame(), ArTabletopPhase.PLACED))
        assertEquals(listOf(keep.id), vm.foggerEffectFrame().groups.map { it.equipmentUuid })
        assertEquals(listOf(keep.id), host.attachedUuids)
        assertTrue(host.resourceEpoch > epoch)
        vm.stopBehavior()
        host.bind(ArFoggerMistHost.request(vm.foggerEffectFrame(), ArTabletopPhase.PLACED))
        assertTrue(vm.foggerEffectFrame().isEmpty)
        assertTrue(host.attachedUuids.isEmpty())
        vm.startBehavior()
        host.bind(ArFoggerMistHost.request(vm.foggerEffectFrame(), ArTabletopPhase.PLACED))
        vm.onArReposition()
        val reposition = host.lifecycle.repositionSequence(
            visualizationStillRunning = vm.state.value.behaviorRunning
        )
        assertEquals(ArPhysicsEffectCleanup.REPOSITION, reposition)
        val leave = ArPhysicsEffectLifecycle(122)
        assertTrue(leave.attach(122))
        assertEquals(ArPhysicsEffectCleanup.LEAVE_AR, leave.leaveArSequence())
        val dead = ArPhysicsEffectLifecycle(123)
        dead.markHostUnavailable()
        val engine = dead.engineOperationCount
        val scene = dead.sceneOperationCount
        assertFalse(dead.clearEffectsWhileSceneAlive())
        assertTrue(dead.abandonAfterHostUnavailable())
        assertEquals(engine, dead.engineOperationCount)
        assertEquals(scene, dead.sceneOperationCount)
        assertNull(
            FoggerEffectPlanner.plan(
                true,
                listOf(keep.copy(id = keep.id)),
                mapOf(keep.id to EquipmentRuntimeState.OFF),
                vm.foggerEffectFrame().snapshot,
                thresholds
            ).group(keep.id)
        )
    }

    private fun onGroup(id: String): FoggerMistGroup {
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
