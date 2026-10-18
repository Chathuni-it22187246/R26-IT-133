package com.greenhands.app.heat.visualization

import com.greenhands.app.ar.domain.ArTabletopPhase
import com.greenhands.app.ar.domain.MyGreenhousePersistence
import com.greenhands.app.ar.render.ArCirculationRingHost
import com.greenhands.app.ar.render.CirculationRingSceneMath
import com.greenhands.app.ar.domain.ArTabletopScale
import com.greenhands.app.equipment.data.EquipmentLayoutCodec
import com.greenhands.app.equipment.data.InMemoryEquipmentLayoutRepository
import com.greenhands.app.equipment.model.EquipmentInstance
import com.greenhands.app.equipment.model.EquipmentLayout
import com.greenhands.app.equipment.model.EquipmentRuntimeState
import com.greenhands.app.equipment.model.EquipmentType
import com.greenhands.app.equipment.ui.EquipmentPlacementViewModel
import com.greenhands.app.equipment.ui.OverlayClimate
import com.greenhands.app.heat.domain.HeatFormulas
import com.greenhands.app.heat.runtime.PhysicsFieldRuntime
import com.greenhands.app.heat.runtime.Phase3D2AFixtures
import com.greenhands.app.heat.runtime.adapter.EquipmentSpecificationResolver
import com.greenhands.app.heat.runtime.adapter.UserPhysicsCoordinateAdapter
import com.greenhands.app.heat.runtime.adapter.UuidEquipmentReadiness
import com.greenhands.app.heat.runtime.contracts.EquipmentProbeAxis
import com.greenhands.app.heat.runtime.contracts.EquipmentProbeSample
import com.greenhands.app.heat.runtime.contracts.FieldValidationStatus
import com.greenhands.app.heat.runtime.contracts.GridGeometrySnapshot
import com.greenhands.app.heat.runtime.contracts.InsideClimateSource
import com.greenhands.app.heat.runtime.contracts.PhysicsFieldSnapshot
import com.greenhands.app.heat.runtime.contracts.ProbeAvailability
import com.greenhands.app.heat.runtime.contracts.WeatherBoundary
import com.greenhands.app.heat.runtime.contracts.WeatherBoundaryKind
import com.greenhands.app.heat.runtime.contracts.WeatherBoundaryProvenance
import com.greenhands.app.heat.runtime.contracts.WeatherBoundaryStatus
import com.greenhands.app.heat.simulation.probe.DistanceProbeSampler
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
class CirculationRingPhase3D2B1Test {

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
    }

    @After
    fun reset() {
        Dispatchers.resetMain()
    }

    @Test
    fun runningOnReadyValidSnapshotProducesCirculationFrame() {
        val fan = Phase3D2AFixtures.instance(EquipmentType.CIRCULATION_FAN, "circ-1", 1, xM = 1.0, yM = 5.0)
        val snapshot = liveSnapshot(listOf(fan), mapOf(fan.id to EquipmentRuntimeState.ON))
        val frame = CirculationEffectPlanner.plan(
            visualizationRunning = true,
            instances = listOf(fan),
            runtimeById = mapOf(fan.id to EquipmentRuntimeState.ON),
            snapshot = snapshot,
            thresholds = thresholds,
            targetTemperatureC = 25.0
        )
        assertFalse(frame.isEmpty)
        assertEquals(listOf("circ-1"), frame.groups.map { it.equipmentUuid })
        assertEquals(CirculationRingVisual.MAX_RINGS_PER_FAN, frame.groups.single().ringCount)
        assertTrue(frame.groups.single().validProbeDistancesM.isNotEmpty())
        assertNull(frame.limitation)
    }

    @Test
    fun offNotRunningNotEvaluatedAndGhostProduceNoRings() {
        val fan = Phase3D2AFixtures.instance(EquipmentType.CIRCULATION_FAN, "circ-1", 1, xM = 1.0, yM = 5.0)
        val ghost = fan.copy(id = "ghost-1", isGhost = true)
        val snapshot = liveSnapshot(listOf(fan), mapOf(fan.id to EquipmentRuntimeState.ON))
        fun plan(
            running: Boolean,
            instances: List<EquipmentInstance>,
            state: EquipmentRuntimeState?
        ) =
            CirculationEffectPlanner.plan(
                visualizationRunning = running,
                instances = instances,
                runtimeById = state?.let { mapOf(instances.first().id to it) }.orEmpty(),
                snapshot = snapshot,
                thresholds = thresholds,
                targetTemperatureC = 25.0
            )
        assertTrue(plan(true, listOf(fan), EquipmentRuntimeState.OFF).isEmpty)
        assertTrue(plan(true, listOf(fan), EquipmentRuntimeState.NOT_RUNNING).isEmpty)
        assertTrue(plan(false, listOf(fan), EquipmentRuntimeState.ON).isEmpty)
        assertTrue(plan(true, listOf(ghost), EquipmentRuntimeState.ON).isEmpty)
        assertTrue(plan(true, listOf(fan), null).isEmpty)
    }

    @Test
    fun multipleFansStayIndependentAndDeletingFan1DoesNotTransferToFan2() {
        val fan1 = Phase3D2AFixtures.instance(EquipmentType.CIRCULATION_FAN, "keep-uuid-1", 1, xM = 1.0, yM = 2.0)
        val fan2 = Phase3D2AFixtures.instance(EquipmentType.CIRCULATION_FAN, "keep-uuid-2", 2, xM = 1.0, yM = 8.0)
        val both = listOf(fan1, fan2)
        val runtime = mapOf(fan1.id to EquipmentRuntimeState.ON, fan2.id to EquipmentRuntimeState.ON)
        val snapshot = liveSnapshot(both, runtime)
        val frame = CirculationEffectPlanner.plan(true, both, runtime, snapshot, thresholds, 25.0)
        assertEquals(setOf("keep-uuid-1", "keep-uuid-2"), frame.groups.map { it.equipmentUuid }.toSet())
        assertNotEquals(frame.group("keep-uuid-1")!!.originM, frame.group("keep-uuid-2")!!.originM)

        val remainingRuntime = mapOf(fan2.id to EquipmentRuntimeState.ON)
        val remainingSnap = liveSnapshot(listOf(fan2), remainingRuntime)
        val afterDelete = CirculationEffectPlanner.plan(true, listOf(fan2), remainingRuntime, remainingSnap, thresholds, 25.0)
        assertEquals(listOf("keep-uuid-2"), afterDelete.groups.map { it.equipmentUuid })
        assertNull(afterDelete.group("keep-uuid-1"))
        assertEquals("keep-uuid-2", afterDelete.groups.single().equipmentUuid)
    }

    @Test
    fun circulationForwardMatchesYawAndUsesUnclampedOneToFiveMetreProbes() {
        val yaw0 = Phase3D2AFixtures.instance(EquipmentType.CIRCULATION_FAN, "yaw0", 1, xM = 1.0, yM = 5.0, yawDeg = 0.0)
        val yaw90 = Phase3D2AFixtures.instance(EquipmentType.CIRCULATION_FAN, "yaw90", 2, xM = 2.5, yM = 1.0, yawDeg = 90.0)
        val (dx0, dy0, _) = UserPhysicsCoordinateAdapter.yawToDomainForward(0.0)
        val (dx90, dy90, _) = UserPhysicsCoordinateAdapter.yawToDomainForward(90.0)
        assertEquals(1.0, dx0, 1e-12)
        assertEquals(0.0, dy0, 1e-12)
        assertEquals(0.0, UserPhysicsCoordinateAdapter.yawToDomainForward(0.0).third, 1e-12)
        assertEquals(0.0, dx90, 1e-12)
        assertEquals(1.0, dy90, 1e-12)
        assertEquals(0.0, UserPhysicsCoordinateAdapter.yawToDomainForward(90.0).third, 1e-12)

        val runtime = mapOf(yaw0.id to EquipmentRuntimeState.ON, yaw90.id to EquipmentRuntimeState.ON)
        val snapshot = liveSnapshot(listOf(yaw0, yaw90), runtime)
        val frame = CirculationEffectPlanner.plan(true, listOf(yaw0, yaw90), runtime, snapshot, thresholds, 25.0)
        val g0 = frame.group("yaw0")!!
        val g90 = frame.group("yaw90")!!
        assertEquals(dx0, g0.forwardDomainDx, 1e-12)
        assertEquals(dy0, g0.forwardDomainDy, 1e-12)
        assertEquals(dx90, g90.forwardDomainDx, 1e-12)
        assertEquals(dy90, g90.forwardDomainDy, 1e-12)
        g0.validProbeDistancesM.forEach { distance ->
            assertTrue(DistanceProbeSampler.DEFAULT_DISTANCES_M.any { kotlin.math.abs(it - distance) < 1e-9 })
        }
        assertTrue(g0.validProbeDistancesM.none { kotlin.math.abs(it - 5.0) < 1e-9 })
        snapshot.probes.filter { it.equipmentUuid == yaw0.id && it.distanceM == 5.0 }.forEach { probe ->
            assertEquals(ProbeAvailability.OUTSIDE_GREENHOUSE, probe.availability)
        }
        assertTrue(g0.validProbeDistancesM.all { it <= 4.0 + 1e-9 })
        assertEquals(CirculationRingVisual.MAX_RINGS_PER_FAN, g0.ringCount)
        assertTrue(g0.validProbeDistancesM.size <= 5)
        assertTrue(g0.ringCount <= CirculationRingVisual.MAX_RINGS_PER_FAN)
    }

    @Test
    fun fiveValidProbesStillCapAtFourRings() {
        val samples = DistanceProbeSampler.DEFAULT_DISTANCES_M.map { distance ->
            probe("cap-1", distance, ProbeAvailability.PREDICTED_UNCALIBRATED, 26.0)
        }
        val fan = Phase3D2AFixtures.instance(EquipmentType.CIRCULATION_FAN, "cap-1", 1, xM = 2.5, yM = 5.0)
        val snapshot = snapshotOf(listOf(fan), mapOf(fan.id to EquipmentRuntimeState.ON), samples)
        val frame = CirculationEffectPlanner.plan(
            true, listOf(fan), mapOf(fan.id to EquipmentRuntimeState.ON), snapshot, thresholds, 25.0
        )
        assertEquals(5, frame.groups.single().validProbeDistancesM.size)
        assertEquals(CirculationRingVisual.MAX_RINGS_PER_FAN, frame.groups.single().ringCount)
    }

    @Test
    fun onWithoutPredictedProbesPublishesLimitation() {
        val fan = Phase3D2AFixtures.instance(EquipmentType.CIRCULATION_FAN, "circ-1", 1)
        val outside = DistanceProbeSampler.DEFAULT_DISTANCES_M.map { distance ->
            probe("circ-1", distance, ProbeAvailability.OUTSIDE_GREENHOUSE, temperatureC = null)
        }
        val snapshot = snapshotOf(listOf(fan), mapOf(fan.id to EquipmentRuntimeState.ON), outside)
        val frame = CirculationEffectPlanner.plan(
            true, listOf(fan), mapOf(fan.id to EquipmentRuntimeState.ON), snapshot, thresholds, 25.0
        )
        assertFalse(frame.isEmpty)
        assertEquals("circ-1", frame.groups.single().equipmentUuid)
        assertEquals(0.0, frame.groups.single().minDistanceM, 0.0)
        assertTrue(frame.groups.single().validProbeDistancesM.isEmpty())
        assertNull(frame.limitation)
    }

    @Test
    fun ringFrameReachesGreenhouseRootBindingWithSingleTabletopScale() {
        val fan = Phase3D2AFixtures.instance(EquipmentType.CIRCULATION_FAN, "circ-1", 1, xM = 1.0, yM = 5.0)
        val snapshot = liveSnapshot(listOf(fan), mapOf(fan.id to EquipmentRuntimeState.ON))
        val frame = CirculationEffectPlanner.plan(
            true, listOf(fan), mapOf(fan.id to EquipmentRuntimeState.ON), snapshot, thresholds, 25.0
        )
        val host = ArCirculationRingHost()
        val request = ArCirculationRingHost.request(frame, ArTabletopPhase.PLACED)
        assertEquals(CirculationRingSceneMath.GREENHOUSE_ROOT_NAME, request.parentName)
        assertFalse(request.applyTabletopScaleToChildren)
        assertFalse(CirculationRingSceneMath.APPLIES_TABLETOP_SCALE_TO_CHILDREN)
        host.bind(request)
        assertEquals(CirculationRingSceneMath.GREENHOUSE_ROOT_NAME, host.parentName)
        assertEquals(listOf("circ-1"), host.attachedUuids)
        val layout = ArTabletopScale.layout(DemoGreenhouseProfile.dimensions(), 3)
        val sceneDistance = CirculationRingSceneMath.sceneDistance(2.0, layout)
        assertEquals(2.0 * layout.uniformScale, sceneDistance, 0.0)
        assertNotEquals(sceneDistance * ArTabletopScale.rootScale(), sceneDistance, 0.0)
        val epoch = host.resourceEpoch
        host.noteRecomposition()
        host.bind(request)
        assertEquals(epoch, host.resourceEpoch)
        assertEquals(listOf("circ-1"), host.attachedUuids)
    }

    @Test
    fun stopClearsRingsWithoutClearingLayoutReadingsOrEquipment() = runTest {
        val vm = demoVm("cfg-rings-stop")
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.CIRCULATION_FAN)
        vm.setSimulatedTemperature(30.0)
        vm.setSimulatedHumidity(60.0)
        vm.startBehavior()
        val before = vm.state.value
        assertTrue(before.behaviorRunning)
        assertFalse(vm.circulationEffectFrame().isEmpty)
        val equipmentId = before.instances.single().id
        val temperature = before.simulatedTemperatureC
        vm.stopBehavior()
        val after = vm.state.value
        assertTrue(vm.circulationEffectFrame().isEmpty)
        assertNull(after.physicsSnapshot)
        assertEquals(1, after.instances.size)
        assertEquals(equipmentId, after.instances.single().id)
        assertEquals(temperature, after.simulatedTemperatureC)
        assertEquals(60.0, after.simulatedHumidityPercent)
        assertEquals(EquipmentRuntimeState.NOT_RUNNING, after.runtimeById[equipmentId])
        val encoded = EquipmentLayoutCodec.encode(
            MyGreenhousePersistence.persistableLayout(
                EquipmentLayout(
                    layoutId = "lay",
                    configurationId = "cfg-rings-stop",
                    userId = "grower@greenhands.app",
                    lengthM = DemoGreenhouseProfile.LENGTH_M,
                    widthM = DemoGreenhouseProfile.WIDTH_M,
                    heightM = DemoGreenhouseProfile.HEIGHT_M,
                    cropRowCount = DemoGreenhouseProfile.CROP_ROW_COUNT,
                    instances = after.instances
                )
            )
        )
        assertFalse(encoded.contains("circ-ring"))
        assertFalse(encoded.contains("CirculationEffect"))
        assertFalse(encoded.contains(VisualizationCopy.CIRCULATION_AIRFLOW_UNAVAILABLE))
        assertFalse(MyGreenhousePersistence.containsForbiddenPersistedState(encoded))
        assertFalse(Phase3D2B1.PERSISTS_EFFECTS)
        assertFalse(Phase3D2B1.RENDERS_EXHAUST)
        assertFalse(Phase3D2B1.RENDERS_FOG)
        assertFalse(Phase3D2B1.RENDERS_FIELD_LAYERS)
        assertFalse(Phase3D2B1.ADDS_LAYER_LEGEND)
    }

    @Test
    fun visualAnimationDoesNotAdvanceThePhysicsSolver() {
        val fan = Phase3D2AFixtures.instance(EquipmentType.CIRCULATION_FAN, "circ-1", 1, xM = 1.0, yM = 5.0)
        val request = Phase3D2AFixtures.demoRequest(
            listOf(fan),
            30.0,
            60.0,
            weather = WeatherBoundary.demoSimulated(25.0, 70.0),
            lengthM = DemoGreenhouseProfile.LENGTH_M,
            widthM = DemoGreenhouseProfile.WIDTH_M,
            heightM = DemoGreenhouseProfile.HEIGHT_M
        )
        val runtime = PhysicsFieldRuntime()
        val started = runtime.start(request)
        val elapsed = runtime.elapsedTimeS()
        val mean = started.meanTemperatureC
        repeat(12) { step ->
            CirculationRingAnimator.distanceM(1.0, 4.0, 0.016 * step, 0, 4)
            CirculationRingAnimator.fade(0.016 * step, 0, 4)
        }
        assertEquals(elapsed, runtime.elapsedTimeS(), 0.0)
        assertEquals(0.0, runtime.elapsedTimeS(), 0.0)
        assertEquals(mean, runtime.snapshot()!!.meanTemperatureC, 0.0)
        runtime.stop()
    }

    @Test
    fun sceneForwardForYawZeroIsPerpendicularRingAxisOnDemo() {
        val layout = ArTabletopScale.layout(DemoGreenhouseProfile.dimensions(), 3)
        assertTrue(layout.lengthMappedToX)
        val (sx, sy, sz) = CirculationRingSceneMath.domainForwardToScene(1.0, 0.0, 0.0, layout)
        assertEquals(0.0, sx, 1e-12)
        assertEquals(0.0, sy, 1e-12)
        assertTrue(sz > 0.0)
        val yaw90 = CirculationRingSceneMath.sceneYawDeg(90.0, layout)
        assertEquals(90.0, yaw90, 0.0)
    }

    private fun liveSnapshot(
        instances: List<EquipmentInstance>,
        runtime: Map<String, EquipmentRuntimeState>
    ): PhysicsFieldSnapshot {
        val request = Phase3D2AFixtures.demoRequest(
            instances,
            30.0,
            60.0,
            weather = WeatherBoundary.demoSimulated(25.0, 70.0),
            lengthM = DemoGreenhouseProfile.LENGTH_M,
            widthM = DemoGreenhouseProfile.WIDTH_M,
            heightM = DemoGreenhouseProfile.HEIGHT_M,
            runtimeById = runtime
        )
        return PhysicsFieldRuntime().run {
            val snap = start(request)
            stop()
            snap
        }
    }

    private fun snapshotOf(
        instances: List<EquipmentInstance>,
        runtime: Map<String, EquipmentRuntimeState>,
        probes: List<EquipmentProbeSample>
    ): PhysicsFieldSnapshot {
        val specs = EquipmentSpecificationResolver.resolveAll(PreviewMode.DEMO, instances)
        val readiness = instances.map { inst ->
            val spec = specs.getValue(inst.id)
            UuidEquipmentReadiness(
                equipmentUuid = inst.id,
                type = inst.type,
                runtimeState = runtime[inst.id] ?: EquipmentRuntimeState.NOT_RUNNING,
                specification = spec,
                contributesActiveSource = runtime[inst.id] == EquipmentRuntimeState.ON && spec.quantitativeReady(),
                quantitativeReady = spec.quantitativeReady(),
                unavailableReason = spec.unavailableReason
            )
        }
        return PhysicsFieldSnapshot(
            elapsedTimeS = 0.0,
            grid = GridGeometrySnapshot(10.0, 5.0, 3.0, 0.5, 20, 10, 6, 1200, 1200),
            temperatureC = DoubleArray(8) { 30.0 },
            humidityRatioKgKg = DoubleArray(8) { 0.01 },
            relativeHumidityPercent = DoubleArray(8) { 60.0 },
            meanTemperatureC = 30.0,
            meanRelativeHumidityPercent = 60.0,
            referenceTemperatureC = 30.0,
            referenceRelativeHumidityPercent = 60.0,
            insideSource = InsideClimateSource.SIMULATED,
            insideSourceLabel = "Demo simulation",
            weatherStatus = WeatherBoundaryStatus.AVAILABLE,
            weatherSourceLabel = "Demo simulated outside boundary — not measured weather.",
            weatherProvenance = WeatherBoundaryProvenance.DEMO_ONLY_ASSUMPTION,
            weatherKind = WeatherBoundaryKind.DEMO_SIMULATED,
            validationStatus = FieldValidationStatus.UNCALIBRATED,
            equipment = readiness,
            runtimeById = runtime,
            warnings = emptyList(),
            unavailableModuleReasons = emptyMap(),
            probes = probes,
            wellMixedVentilation = false,
            finiteValues = true
        )
    }

    private fun probe(
        uuid: String,
        distanceM: Double,
        availability: ProbeAvailability,
        temperatureC: Double?
    ): EquipmentProbeSample = EquipmentProbeSample(
        equipmentUuid = uuid,
        equipmentType = EquipmentType.CIRCULATION_FAN,
        axis = EquipmentProbeAxis.CIRCULATION_FORWARD,
        distanceM = distanceM,
        temperatureC = temperatureC,
        relativeHumidityPercent = 60.0.takeIf { temperatureC != null },
        airSpeedMps = 0.4.takeIf { temperatureC != null },
        availability = availability,
        validationStatus = if (availability == ProbeAvailability.PREDICTED_UNCALIBRATED) {
            FieldValidationStatus.UNCALIBRATED
        } else {
            FieldValidationStatus.MODULE_UNAVAILABLE
        },
        reason = null
    )

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
