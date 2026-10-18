package com.greenhands.app.heat.visualization

import com.greenhands.app.ar.domain.ArTabletopPhase
import com.greenhands.app.ar.render.ArCirculationColorMaterial
import com.greenhands.app.ar.render.ArFoggerMistHost
import com.greenhands.app.ar.render.ArLiveRendererOwnership
import com.greenhands.app.ar.render.ArPhysicsEffectCleanup
import com.greenhands.app.ar.render.ArPhysicsEffectLifecycle
import com.greenhands.app.equipment.data.InMemoryEquipmentLayoutRepository
import com.greenhands.app.equipment.model.EquipmentAssetCatalog
import com.greenhands.app.equipment.model.EquipmentInstance
import com.greenhands.app.equipment.model.EquipmentRuntimeState
import com.greenhands.app.equipment.model.EquipmentType
import com.greenhands.app.equipment.model.SelectedEquipmentTool
import com.greenhands.app.equipment.ui.EquipmentPlacementViewModel
import com.greenhands.app.equipment.ui.OverlayClimate
import com.greenhands.app.heat.domain.HeatFormulas
import com.greenhands.app.heat.runtime.DemoPhysicsSchedulerConfig
import com.greenhands.app.heat.runtime.PhysicsFieldRuntime
import com.greenhands.app.heat.runtime.Phase3D2AFixtures
import com.greenhands.app.heat.runtime.adapter.UncalibratedPhase3AProfileSettings
import com.greenhands.app.heat.runtime.adapter.UserPhysicsCoordinateAdapter
import com.greenhands.app.heat.runtime.contracts.WeatherBoundary
import com.greenhands.app.preview3d.model.DemoGreenhouseProfile
import com.greenhands.app.preview3d.model.PreviewMode
import kotlin.math.abs
import kotlin.math.hypot
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FoggerMistPlumeCorrectionTest {

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
    fun plannedParticlesAreNotCollinearAndHaveLateralAndDownwardSpread() {
        val group = onGroup("fog-plume-a")
        val points = FoggerMistAnimator.particlePositions(group, visualTimeS = 0.55)
        assertTrue(points.size >= 3)
        assertFalse(FogMistDistribution.areCollinear(points))
        val (spanX, spanY) = FogMistDistribution.horizontalSpanM(points)
        assertTrue("need lateral spread on X, got $spanX", spanX > 0.04)
        assertTrue("need lateral spread on Y, got $spanY", spanY > 0.04)
        assertTrue(FogMistDistribution.downwardSpanM(points) > 0.12)
        val (latA, latB) = FoggerNozzleLayout.laterals(group.yawDeg)
        assertTrue(abs(latA.third) < 1e-9 && abs(latB.third) < 1e-9)
        val alongA = points.map { offsetAlong(it, group.nozzleOrigin(0), latA) }
        val alongB = points.map { offsetAlong(it, group.nozzleOrigin(0), latB) }
        assertTrue(alongA.max() - alongA.min() > 0.03)
        assertTrue(alongB.max() - alongB.min() > 0.03)
    }

    @Test
    fun particlesOriginateAtTheDocumentedNozzleNotTheModelCentre() {
        val fog = overheadFogger("fog-nozzle", 1)
        val group = onGroup(fog)
        val aabb = EquipmentAssetCatalog.fogger.localAabb
        val localY = FoggerNozzleLayout.LOCAL_OUTLETS.single().second
        assertEquals(1, FoggerNozzleLayout.DOCUMENTED_OUTLET_COUNT)
        assertTrue(FoggerNozzleLayout.PIVOT_IS_NOZZLE_OUTLET)
        assertTrue(localY < FoggerNozzleLayout.AABB_CENTER_LOCAL_Y)
        assertTrue(abs(localY - aabb.minY) < abs(localY - FoggerNozzleLayout.AABB_CENTER_LOCAL_Y))
        val nozzle = group.nozzleOrigins.single()
        assertEquals(fog.positionM.xM, nozzle.xM, 1e-9)
        assertEquals(fog.positionM.yM, nozzle.yM, 1e-9)
        assertEquals(fog.positionM.zM + localY, nozzle.zM, 1e-9)
        assertTrue(nozzle.zM < fog.positionM.zM)
        val near = nearNozzlePoints(group)
        assertTrue(near.isNotEmpty())
        near.forEach { point ->
            val dx = point.xM - nozzle.xM
            val dy = point.yM - nozzle.yM
            val dz = point.zM - nozzle.zM
            val radial = hypot(dx, dy)
            assertTrue("start must stay on the nozzle face, radial=$radial", radial < 0.14)
            assertTrue(dz <= 0.02)
            assertTrue(point.zM < fog.positionM.zM + 0.01)
        }
    }

    @Test
    fun uuidSeededDistributionIsDeterministicAndIndependentPerUuid() {
        val a = onGroup("fog-seed-a")
        val again = onGroup("fog-seed-a")
        val b = onGroup("fog-seed-b")
        val posA = FoggerMistAnimator.particlePositions(a, 0.4)
        val posAgain = FoggerMistAnimator.particlePositions(again, 0.4)
        val posB = FoggerMistAnimator.particlePositions(b, 0.4)
        assertEquals(posA, posAgain)
        assertEquals(a.particles, again.particles)
        assertNotEquals(a.particles.map { it.azimuthRad }, b.particles.map { it.azimuthRad })
        assertNotEquals(posA, posB)
    }

    @Test
    fun particlesStayInsideAndFadeRecycleAtTheEnvelope() {
        val fog = overheadFogger("fog-env", 1)
        val snapshot = liveSnapshot(fog, EquipmentRuntimeState.ON)
        val group = FoggerEffectPlanner.plan(
            true, listOf(fog), mapOf(fog.id to EquipmentRuntimeState.ON), snapshot, thresholds
        ).groups.single()
        assertTrue(group.maxDistanceM > FogMistVisual.UNUSED_PLUME_LENGTH_M)
        assertTrue(group.maxDistanceM <= group.acceptedPlumeLengthM + 1e-9)
        assertEquals(FoggerVisualEnvelope.acceptedHalfAngleDeg(), group.plumeHalfAngleDeg, 0.0)
        assertFalse(
            FoggerVisualEnvelope.usesArbitraryVisualFallback(
                group.plumeHalfAngleDeg,
                group.acceptedPlumeLengthM
            )
        )
        val canopy = DemoGreenhouseProfile.HEIGHT_M * com.greenhands.app.preview3d.domain.CropRowGeometry.HEIGHT_FILL
        listOf(0.0, 0.35, 0.9, 1.7).forEach { time ->
            FoggerMistAnimator.particlePositions(group, time).forEach { point ->
                assertTrue(FoggerMistEnvelope.contains(snapshot, point))
                assertTrue(point.zM + 1e-9 >= 0.0)
                assertTrue(point.zM <= DemoGreenhouseProfile.HEIGHT_M + 1e-9)
            }
        }
        assertTrue(FoggerMistAnimator.particlePositions(group, 0.0).any { it.zM < canopy })
        assertFalse(FoggerVisualEnvelope.HARD_CLIPS_CANOPY)
        assertTrue(FoggerMistAnimator.recyclesAtEnvelopeBoundary(0.90))
        assertTrue(FoggerMistAnimator.fadeAtProgress(0.95) < FoggerMistAnimator.fadeAtProgress(0.35))
        assertTrue(FogMistAppearance.DROPLET_SIZE_M > 0.030)
        assertTrue(FogMistAppearance.DROPLET_SIZE_M < 0.12)
        assertTrue(FogMistAppearance.MATERIAL_ALPHA < 0.50f)
    }

    @Test
    fun onShowsMistOffNotRunningAndNotEvaluatedDoNot() {
        val fog = overheadFogger("fog-state", 1)
        val snapshot = liveSnapshot(fog, EquipmentRuntimeState.ON)
        val on = FoggerEffectPlanner.plan(
            true, listOf(fog), mapOf(fog.id to EquipmentRuntimeState.ON), snapshot, thresholds
        )
        assertFalse(on.isEmpty)
        assertTrue(on.groups.single().particleCount >= 3)
        assertTrue(
            FoggerEffectPlanner.plan(
                true, listOf(fog), mapOf(fog.id to EquipmentRuntimeState.OFF), snapshot, thresholds
            ).isEmpty
        )
        assertTrue(
            FoggerEffectPlanner.plan(
                true, listOf(fog), mapOf(fog.id to EquipmentRuntimeState.NOT_RUNNING), snapshot, thresholds
            ).isEmpty
        )
        assertTrue(
            FoggerEffectPlanner.plan(true, listOf(fog), emptyMap(), snapshot, thresholds).isEmpty
        )
        assertTrue(
            FoggerEffectPlanner.plan(
                false, listOf(fog), mapOf(fog.id to EquipmentRuntimeState.ON), snapshot, thresholds
            ).isEmpty
        )
    }

    @Test
    fun multipleFoggersRetainIndependentPlumes() = runTest {
        val vm = demoVm("cfg-fog-plume-multi")
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.FOGGER)
        vm.duplicateSelected()
        vm.setSimulatedTemperature(35.0)
        vm.setSimulatedHumidity(40.0)
        vm.startBehavior()
        val foggers = vm.state.value.instances.filter { it.type == EquipmentType.FOGGER && !it.isGhost }
        assertEquals(2, foggers.size)
        val fog1 = foggers.first { it.displayNumber == 1 }
        val fog2 = foggers.first { it.displayNumber == 2 }
        val frame = vm.foggerEffectFrame()
        assertEquals(setOf(fog1.id, fog2.id), frame.groups.map { it.equipmentUuid }.toSet())
        val a = frame.group(fog1.id)!!
        val b = frame.group(fog2.id)!!
        assertNotEquals(a.equipmentUuid, b.equipmentUuid)
        assertNotEquals(
            FoggerMistAnimator.particlePositions(a, 0.5),
            FoggerMistAnimator.particlePositions(b, 0.5)
        )
        vm.select(fog1.id)
        vm.requestDelete()
        vm.deleteSelected()
        val after = vm.foggerEffectFrame()
        assertEquals(listOf(fog2.id), after.groups.map { it.equipmentUuid })
        assertEquals(fog2.id, vm.state.value.instances.single { it.type == EquipmentType.FOGGER }.id)
    }

    @Test
    fun selectionLabelsAndSheetsDoNotRemoveMist() = runTest {
        val vm = demoVm("cfg-fog-plume-chrome")
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.FOGGER)
        vm.setSimulatedTemperature(35.0)
        vm.setSimulatedHumidity(40.0)
        vm.startBehavior()
        val before = vm.foggerEffectFrame().groups.single().equipmentUuid
        assertFalse(vm.foggerEffectFrame().isEmpty)
        vm.toggleLabelsVisible()
        vm.setSelectedTool(SelectedEquipmentTool.EXACT)
        vm.toggleBehaviorSheet()
        val host = ArFoggerMistHost(81)
        host.lifecycle.attach(81)
        host.bind(ArFoggerMistHost.request(vm.foggerEffectFrame(), ArTabletopPhase.PLACED))
        val epoch = host.resourceEpoch
        host.noteRecomposition()
        host.uiChromeChanged()
        assertEquals(before, vm.foggerEffectFrame().groups.single().equipmentUuid)
        assertFalse(vm.foggerEffectFrame().isEmpty)
        assertEquals(epoch, host.resourceEpoch)
        assertFalse(FogMistVisual.ANIMATION_IS_MEASURED_VELOCITY)
    }

    @Test
    fun stopRepositionAndLeaveArRemainLifecycleSafe() = runTest {
        val vm = demoVm("cfg-fog-plume-life")
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.FOGGER)
        vm.setSimulatedTemperature(35.0)
        vm.setSimulatedHumidity(40.0)
        vm.startBehavior()
        val host = ArFoggerMistHost(82)
        host.lifecycle.attach(82)
        host.bind(ArFoggerMistHost.request(vm.foggerEffectFrame(), ArTabletopPhase.PLACED))
        assertEquals(1, host.attachedUuids.size)
        vm.stopBehavior()
        host.bind(ArFoggerMistHost.request(vm.foggerEffectFrame(), ArTabletopPhase.PLACED))
        assertTrue(vm.foggerEffectFrame().isEmpty)
        assertTrue(host.attachedUuids.isEmpty())
        assertEquals(1, vm.state.value.instances.size)
        vm.startBehavior()
        host.bind(ArFoggerMistHost.request(vm.foggerEffectFrame(), ArTabletopPhase.PLACED))
        vm.onArReposition()
        val reposition = host.lifecycle.repositionSequence(
            visualizationStillRunning = vm.state.value.behaviorRunning
        )
        assertEquals(ArPhysicsEffectCleanup.REPOSITION, reposition)
        assertTrue(
            reposition.indexOf(ArPhysicsEffectCleanup.DETACH_NODES) <
                reposition.indexOf(ArPhysicsEffectCleanup.REPLACE_ANCHOR)
        )
        val leave = ArPhysicsEffectLifecycle(83)
        assertTrue(leave.attach(83))
        assertTrue(leave.bindGroups(1))
        val order = leave.leaveArSequence()
        assertEquals(ArPhysicsEffectCleanup.LEAVE_AR, order)
        assertTrue(
            order.indexOf(ArPhysicsEffectCleanup.DETACH_NODES) <
                order.indexOf(ArPhysicsEffectCleanup.HOST_DESTROY)
        )
        val dead = ArPhysicsEffectLifecycle(84)
        dead.markHostUnavailable()
        val engine = dead.engineOperationCount
        val scene = dead.sceneOperationCount
        assertFalse(dead.clearEffectsWhileSceneAlive())
        assertTrue(dead.abandonAfterHostUnavailable())
        assertEquals(engine, dead.engineOperationCount)
        assertEquals(scene, dead.sceneOperationCount)
    }

    @Test
    fun physicsValuesAndSchedulesStayUnchangedAndMaterialsStaySupported() {
        assertEquals(2.0, UncalibratedPhase3AProfileSettings.FOGGER_PLUME_LENGTH_M, 0.0)
        assertEquals(40.0, UncalibratedPhase3AProfileSettings.FOGGER_PLUME_ANGLE_DEG, 0.0)
        val group = onGroup("fog-profile-envelope")
        assertEquals(FoggerVisualEnvelope.acceptedHalfAngleDeg(), group.plumeHalfAngleDeg, 0.0)
        assertEquals(FoggerVisualEnvelope.acceptedPlumeLengthM(), group.acceptedPlumeLengthM, 0.0)
        assertEquals(20.0, group.plumeHalfAngleDeg, 0.0)
        assertEquals(2.0, group.acceptedPlumeLengthM, 0.0)
        assertNotEquals(FogMistVisual.UNUSED_PLUME_HALF_ANGLE_DEG, group.plumeHalfAngleDeg, 0.0)
        assertNotEquals(FogMistVisual.UNUSED_PLUME_LENGTH_M, group.acceptedPlumeLengthM, 0.0)
        assertEquals(1.0, DemoPhysicsSchedulerConfig().publicationIntervalS, 0.0)
        assertEquals(5.0, DemoDisplayPublication.INTERVAL_S, 0.0)
        assertNotEquals(
            UncalibratedPhase3AProfileSettings.FOGGER_PLUME_ANGLE_DEG,
            FogMistVisual.PLUME_FULL_ANGLE_DEG,
            0.0
        )
        assertFalse(FogMistVisual.CONE_ANGLE_IS_MANUFACTURER_SPEC)
        assertFalse(FogMistVisual.PARTICLE_COUNT_IS_WATER_FLOW)
        assertFalse(Phase3D2B3.CHANGES_PHASE_3A)
        assertFalse(Phase3D2B3.WRITES_EMISSIVE_FACTOR)
        assertFalse(FogMistAppearance.WRITES_EMISSIVE_FACTOR)
        assertFalse(ArCirculationColorMaterial.maySetParameter("emissiveFactor"))
        assertTrue(ArCirculationColorMaterial.maySetParameter("color"))
        val write = ArCirculationColorMaterial.frameColorWrite(FogMistAppearance.MIST, 0.4f)
        assertFalse(write.requestsEmissiveFactor)
        assertEquals(setOf("color"), write.uniformNames)
        assertTrue("fogFrame" in ArLiveRendererOwnership.FORBIDDEN_REMEMBER_KEYS)
        assertEquals(setOf("engine"), ArLiveRendererOwnership.REMEMBER_KEYS)
        assertEquals(
            FogMistVisual.MAX_FOGGER_GROUPS * FogMistVisual.MAX_PARTICLES_PER_FOGGER,
            FogMistVisual.MAX_TOTAL_PARTICLES
        )
        assertEquals(3, FogMistVisual.MAX_SCENE_NODES_PER_FOGGER)
        assertFalse(FogMistVisual.CLOUD_IS_STATIC)
        assertTrue(FogMistVisual.ANIMATES_BY_VERTEX_BUFFER)
    }

    @Test
    fun sprayAxisRemainsAcceptedDownwardDomainMinusZ() {
        val (dx, dy, dz) = UserPhysicsCoordinateAdapter.foggerSprayDomain()
        assertEquals(0.0, dx, 0.0)
        assertEquals(0.0, dy, 0.0)
        assertEquals(-1.0, dz, 0.0)
        val group = onGroup("fog-axis")
        assertEquals(dx, group.sprayDomainDx, 0.0)
        assertEquals(dy, group.sprayDomainDy, 0.0)
        assertEquals(dz, group.sprayDomainDz, 0.0)
        val physics = UserPhysicsCoordinateAdapter.foggerSprayPhysics()
        assertTrue(physics.zM < 0.0)
    }

    private fun nearNozzlePoints(group: FoggerMistGroup) =
        group.particles.indices.mapNotNull { index ->
            val times = DoubleArray(24) { it * FogMistVisual.CYCLE_S / 24.0 }
            val near = times.minBy { time ->
                FoggerMistAnimator.particleProgress(group, index, time)
            }
            val progress = FoggerMistAnimator.particleProgress(group, index, near)
            FoggerMistAnimator.particleDomainPoint(group, index, near).takeIf { progress < 0.12 }
        }

    private fun offsetAlong(
        point: com.greenhands.app.equipment.model.DomainPoint,
        origin: com.greenhands.app.equipment.model.DomainPoint,
        axis: Triple<Double, Double, Double>
    ): Double =
        (point.xM - origin.xM) * axis.first +
            (point.yM - origin.yM) * axis.second +
            (point.zM - origin.zM) * axis.third

    private fun onGroup(id: String): FoggerMistGroup = onGroup(overheadFogger(id, 1))

    private fun onGroup(fog: EquipmentInstance): FoggerMistGroup {
        val snapshot = liveSnapshot(fog, EquipmentRuntimeState.ON)
        return FoggerEffectPlanner.plan(
            true, listOf(fog), mapOf(fog.id to EquipmentRuntimeState.ON), snapshot, thresholds
        ).groups.single()
    }

    private fun overheadFogger(id: String, number: Int): EquipmentInstance =
        Phase3D2AFixtures.instance(
            EquipmentType.FOGGER, id, number, xM = 2.5, yM = 5.0, zM = 2.2, yawDeg = 0.0
        )

    private fun liveSnapshot(
        fog: EquipmentInstance,
        state: EquipmentRuntimeState
    ) = liveSnapshot(listOf(fog), mapOf(fog.id to state))

    private fun liveSnapshot(
        instances: List<EquipmentInstance>,
        runtime: Map<String, EquipmentRuntimeState>
    ) = PhysicsFieldRuntime().run {
        val snap = start(
            Phase3D2AFixtures.demoRequest(
                instances,
                35.0,
                40.0,
                weather = WeatherBoundary.demoSimulated(25.0, 70.0),
                lengthM = DemoGreenhouseProfile.LENGTH_M,
                widthM = DemoGreenhouseProfile.WIDTH_M,
                heightM = DemoGreenhouseProfile.HEIGHT_M,
                runtimeById = runtime
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
