package com.greenhands.app.heat.visualization

import com.greenhands.app.ar.domain.ArOverlayLayout
import com.greenhands.app.ar.domain.ArTabletopPhase
import com.greenhands.app.ar.domain.MyGreenhousePersistence
import com.greenhands.app.ar.render.ArCirculationColorMaterial
import com.greenhands.app.ar.render.ArExhaustAirflowHost
import com.greenhands.app.ar.render.CirculationRingSceneMath
import com.greenhands.app.ar.domain.ArTabletopScale
import com.greenhands.app.ar.domain.EquipmentRuntimeThresholds
import com.greenhands.app.equipment.data.EquipmentLayoutCodec
import com.greenhands.app.equipment.data.InMemoryEquipmentLayoutRepository
import com.greenhands.app.equipment.model.EquipmentInstance
import com.greenhands.app.equipment.model.EquipmentLayout
import com.greenhands.app.equipment.model.EquipmentRuntimeState
import com.greenhands.app.equipment.model.EquipmentType
import com.greenhands.app.equipment.model.SelectedEquipmentTool
import com.greenhands.app.equipment.ui.EquipmentPlacementViewModel
import com.greenhands.app.equipment.ui.OverlayClimate
import com.greenhands.app.heat.domain.HeatFormulas
import com.greenhands.app.heat.runtime.DemoPhysicsScheduler
import com.greenhands.app.heat.runtime.DemoPhysicsSchedulerConfig
import com.greenhands.app.heat.runtime.EquipmentDistanceProbes
import com.greenhands.app.heat.runtime.PhysicsFieldRuntime
import com.greenhands.app.heat.runtime.PhysicsRuntimeCopy
import com.greenhands.app.heat.runtime.Phase3D2AFixtures
import com.greenhands.app.heat.runtime.adapter.EquipmentSpecificationResolver
import com.greenhands.app.heat.runtime.adapter.UserPhysicsCoordinateAdapter
import com.greenhands.app.heat.runtime.adapter.UuidEquipmentReadiness
import com.greenhands.app.heat.runtime.contracts.EquipmentProbeAxis
import com.greenhands.app.heat.runtime.contracts.FieldValidationStatus
import com.greenhands.app.heat.runtime.contracts.GridGeometrySnapshot
import com.greenhands.app.heat.runtime.contracts.InsideClimateSource
import com.greenhands.app.heat.runtime.contracts.PhysicsFieldSnapshot
import com.greenhands.app.heat.runtime.contracts.ProbeAvailability
import com.greenhands.app.heat.runtime.contracts.WeatherBoundary
import com.greenhands.app.heat.runtime.contracts.WeatherBoundaryKind
import com.greenhands.app.heat.runtime.contracts.WeatherBoundaryProvenance
import com.greenhands.app.heat.runtime.contracts.WeatherBoundaryStatus
import com.greenhands.app.heat.runtime.contracts.toSnapshot
import com.greenhands.app.heat.simulation.model.GridState
import com.greenhands.app.heat.simulation.model.Vec3
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
import kotlin.math.abs

@OptIn(ExperimentalCoroutinesApi::class)
class ExhaustAirflowPhase3D2B2Test {

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
    fun onReadyValidSnapshotCreatesExhaustEffect() {
        val fan = farWallExhaust("ex-1", 1)
        val runtime = mapOf(fan.id to EquipmentRuntimeState.ON)
        val snapshot = liveSnapshot(listOf(fan), runtime)
        val frame = ExhaustEffectPlanner.plan(true, listOf(fan), runtime, snapshot, thresholds, 25.0)
        assertFalse(frame.isEmpty)
        assertEquals(listOf("ex-1"), frame.groups.map { it.equipmentUuid })
        assertEquals(ExhaustAirflowMode.QUANTITATIVE, frame.groups.single().mode)
        assertTrue(frame.groups.single().indoorArrowCount > 0)
        assertTrue(frame.groups.single().outdoorArrowCount > 0)
        assertNull(frame.limitation)
    }

    @Test
    fun offNotRunningNotEvaluatedAndGhostProduceNoExhaust() {
        val fan = farWallExhaust("ex-1", 1)
        val ghost = fan.copy(id = "ghost-1", isGhost = true)
        val snapshot = liveSnapshot(listOf(fan), mapOf(fan.id to EquipmentRuntimeState.ON))
        fun plan(running: Boolean, instances: List<EquipmentInstance>, state: EquipmentRuntimeState?) =
            ExhaustEffectPlanner.plan(
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
    fun paletteGhostAndInvalidSnapshotCreateNone() {
        val fan = farWallExhaust("ex-1", 1)
        val ghost = fan.copy(isGhost = true)
        val runtime = mapOf(fan.id to EquipmentRuntimeState.ON)
        val valid = liveSnapshot(listOf(fan), runtime)
        assertTrue(
            ExhaustEffectPlanner.plan(true, listOf(ghost), runtime, valid, thresholds, 25.0).isEmpty
        )
        val invalid = snapshotOf(listOf(fan), runtime, valid.probes, finite = false)
        assertTrue(
            ExhaustEffectPlanner.plan(true, listOf(fan), runtime, invalid, thresholds, 25.0).isEmpty
        )
        assertTrue(
            ExhaustEffectPlanner.plan(true, listOf(fan), runtime, null, thresholds, 25.0).isEmpty
        )
    }

    @Test
    fun multipleExhaustUuidsStayIndependentAndDeletingFan1DoesNotTransferFan2() {
        val fan1 = farWallExhaust("keep-ex-1", 1, xM = 1.4)
        val fan2 = farWallExhaust("keep-ex-2", 2, xM = 3.6)
        val both = listOf(fan1, fan2)
        val runtime = mapOf(fan1.id to EquipmentRuntimeState.ON, fan2.id to EquipmentRuntimeState.ON)
        val snapshot = liveSnapshot(both, runtime)
        val frame = ExhaustEffectPlanner.plan(true, both, runtime, snapshot, thresholds, 25.0)
        assertEquals(setOf("keep-ex-1", "keep-ex-2"), frame.groups.map { it.equipmentUuid }.toSet())
        assertNotEquals(frame.group("keep-ex-1")!!.originM, frame.group("keep-ex-2")!!.originM)

        val remainingRuntime = mapOf(fan2.id to EquipmentRuntimeState.ON)
        val remainingSnap = liveSnapshot(listOf(fan2), remainingRuntime)
        val afterDelete = ExhaustEffectPlanner.plan(
            true, listOf(fan2), remainingRuntime, remainingSnap, thresholds, 25.0
        )
        assertEquals(listOf("keep-ex-2"), afterDelete.groups.map { it.equipmentUuid })
        assertNull(afterDelete.group("keep-ex-1"))
        assertEquals(2, fan2.displayNumber)
    }

    @Test
    fun inwardAndOutwardFollowStoredYawNotCameraOrScreen() {
        val yaw90 = farWallExhaust("yaw90", 1, yawDeg = 90.0)
        val yaw270 = Phase3D2AFixtures.instance(
            EquipmentType.EXHAUST_FAN, "yaw270", 2, xM = 2.5, yM = 0.0, zM = 1.5, yawDeg = 270.0
        )
        val (out90x, out90y, out90z) = ExhaustAirflowEnvelope.outwardDomain(90.0)
        val (in90x, in90y, _) = ExhaustAirflowEnvelope.inwardDomain(90.0)
        assertEquals(0.0, out90x, 1e-12)
        assertEquals(1.0, out90y, 1e-12)
        assertEquals(0.0, out90z, 1e-12)
        assertEquals(0.0, in90x, 1e-12)
        assertEquals(-1.0, in90y, 1e-12)
        assertEquals(-out90x, in90x, 0.0)
        assertEquals(-out90y, in90y, 0.0)

        val runtime = mapOf(yaw90.id to EquipmentRuntimeState.ON, yaw270.id to EquipmentRuntimeState.ON)
        val snapshot = liveSnapshot(listOf(yaw90, yaw270), runtime)
        val frame = ExhaustEffectPlanner.plan(
            true, listOf(yaw90, yaw270), runtime, snapshot, thresholds, 25.0
        )
        val g90 = frame.group("yaw90")!!
        val g270 = frame.group("yaw270")!!
        assertEquals(0.0, g90.outwardDomainDx, 1e-12)
        assertEquals(1.0, g90.outwardDomainDy, 1e-12)
        assertEquals(0.0, g90.inwardDomainDx, 1e-12)
        assertEquals(-1.0, g90.inwardDomainDy, 1e-12)
        assertEquals(0.0, g270.outwardDomainDx, 1e-12)
        assertEquals(-1.0, g270.outwardDomainDy, 1e-12)
        assertEquals(0.0, g270.inwardDomainDx, 1e-12)
        assertEquals(1.0, g270.inwardDomainDy, 1e-12)
        assertTrue(ExhaustAirflowEnvelope.cameraDoesNotAffectDomainDirection())
        val (probeDx, probeDy, _) = ExhaustProbeLabelPlanner.cameraDoesNotAffectAxis(90.0)
        assertEquals(g90.inwardDomainDx, probeDx, 1e-12)
        assertEquals(g90.inwardDomainDy, probeDy, 1e-12)
    }

    @Test
    fun oneToFiveMetreProbesUseInwardAxisAndOmitOutsidePoints() {
        val fan = Phase3D2AFixtures.instance(
            EquipmentType.EXHAUST_FAN, "short-1", 1, xM = 1.5, yM = 4.0, zM = 1.2, yawDeg = 90.0
        )
        val runtime = mapOf(fan.id to EquipmentRuntimeState.ON)
        val snapshot = syntheticSnapshot(
            listOf(fan),
            runtime,
            lengthM = 4.0,
            widthM = 5.0,
            heightM = 3.0
        ) { 32.0 }
        snapshot.probes.filter { it.equipmentUuid == fan.id && it.distanceM == 5.0 }.forEach { probe ->
            assertEquals(ProbeAvailability.OUTSIDE_GREENHOUSE, probe.availability)
            assertNull(probe.temperatureC)
        }
        val frame = ExhaustEffectPlanner.plan(true, listOf(fan), runtime, snapshot, thresholds, 25.0)
        val group = frame.groups.single()
        assertTrue(group.validProbeDistancesM.all { it <= 4.0 + 1e-9 })
        assertFalse(group.validProbeDistancesM.any { abs(it - 5.0) < 1e-9 })
        val markers = ExhaustProbeLabelPlanner.plan(
            true, true, fan.id, listOf(fan), runtime, snapshot, 25.0, circ.cdp, circ.con, exhaust.eon
        )
        assertTrue(markers.none { abs(it.distanceM - 5.0) < 1e-9 })
        markers.forEach { marker ->
            assertEquals("${marker.distanceM.toInt()} m", marker.compactDistanceText)
            assertFalse(marker.compactDistanceText.contains("°C"))
            val expected = fan.positionM.copy(yM = fan.positionM.yM - marker.distanceM)
            assertEquals(expected.xM, marker.domain.xM, 1e-9)
            assertEquals(expected.yM, marker.domain.yM, 1e-9)
        }
    }

    @Test
    fun uniformFieldMayReturnEqualIndependentSamplesNotCopiedMean() {
        val fan = farWallExhaust("uni-1", 1)
        val runtime = mapOf(fan.id to EquipmentRuntimeState.ON)
        val snapshot = syntheticSnapshot(listOf(fan), runtime, meanOverride = 99.0) { 32.0 }
        val frame = ExhaustEffectPlanner.plan(true, listOf(fan), runtime, snapshot, thresholds, 25.0)
        val samples = frame.groups.single().samples
        assertTrue(samples.size >= 2)
        assertTrue(samples.all { abs(it.temperatureC - 32.0) < 0.05 })
        assertTrue(samples.none { abs(it.temperatureC - 99.0) < 1.0 })
        assertTrue(samples.none { abs(it.temperatureC - snapshot.meanTemperatureC) < 1.0 })
        val distinct = samples.map { it.temperatureC }.toSet()
        assertEquals(1, distinct.size)
    }

    @Test
    fun gradientFieldReturnsDifferentIndependentSamples() {
        val fan = farWallExhaust("grad-1", 1)
        val runtime = mapOf(fan.id to EquipmentRuntimeState.ON)
        val snapshot = syntheticSnapshot(listOf(fan), runtime, meanOverride = 50.0) { physics ->
            20.0 + physics.xM
        }
        val frame = ExhaustEffectPlanner.plan(true, listOf(fan), runtime, snapshot, thresholds, 25.0)
        val samples = frame.groups.single().samples
        assertTrue(samples.size >= 2)
        assertTrue(samples.none { abs(it.temperatureC - 50.0) < 0.5 })
        val first = samples.first().temperatureC
        val last = samples.last().temperatureC
        assertTrue("gradient should differ: $first vs $last", abs(first - last) > 0.2)
        val markers = ExhaustProbeLabelPlanner.plan(
            true, true, fan.id, listOf(fan), runtime, snapshot, 25.0, circ.cdp, circ.con, exhaust.eon
        )
        assertTrue(markers.size >= 2)
        assertNotEquals(markers.first().temperatureC, markers.last().temperatureC, 0.05)
    }

    @Test
    fun validDemoBoundaryPermitsQuantitativeColours() {
        val fan = farWallExhaust("demo-q", 1)
        val runtime = mapOf(fan.id to EquipmentRuntimeState.ON)
        val snapshot = liveSnapshot(listOf(fan), runtime)
        assertEquals(WeatherBoundaryStatus.AVAILABLE, snapshot.weatherStatus)
        assertEquals(PhysicsRuntimeCopy.DEMO_SIMULATED_OUTSIDE_BOUNDARY, snapshot.weatherSourceLabel)
        val frame = ExhaustEffectPlanner.plan(true, listOf(fan), runtime, snapshot, thresholds, 25.0)
        assertEquals(ExhaustAirflowMode.QUANTITATIVE, frame.groups.single().mode)
        val color = ExhaustAirflowAnimator.colorAtIndoorDistance(
            frame.groups.single(),
            1.0,
            snapshot
        )
        assertFalse(color.isUnavailable)
        assertNotEquals(VisualizationRgb.NEUTRAL_AIRFLOW.red, color.red, 0f)
    }

    @Test
    fun missingOrStaleBoundaryPermitsOnlyNeutralQualitativeDirection() {
        val fan = farWallExhaust("qual-1", 1)
        val runtime = mapOf(fan.id to EquipmentRuntimeState.ON)
        val missing = qualitativeSnapshot(listOf(fan), runtime, WeatherBoundaryStatus.MISSING)
        val stale = qualitativeSnapshot(listOf(fan), runtime, WeatherBoundaryStatus.STALE)
        listOf(missing, stale).forEach { snapshot ->
            val frame = ExhaustEffectPlanner.plan(true, listOf(fan), runtime, snapshot, thresholds, 25.0)
            assertFalse(frame.isEmpty)
            assertEquals(ExhaustAirflowMode.QUALITATIVE, frame.groups.single().mode)
            assertTrue(frame.groups.single().samples.isEmpty())
            assertEquals(PhysicsRuntimeCopy.MODULE_UNAVAILABLE_WEATHER, frame.limitation)
            val color = ExhaustAirflowAnimator.colorAtIndoorDistance(frame.groups.single(), 1.0, snapshot)
            assertEquals(VisualizationRgb.NEUTRAL_AIRFLOW.red, color.red, 1e-6f)
            assertEquals(VisualizationRgb.NEUTRAL_AIRFLOW.green, color.green, 1e-6f)
            assertEquals(VisualizationRgb.NEUTRAL_AIRFLOW.blue, color.blue, 1e-6f)
            val markers = ExhaustProbeLabelPlanner.plan(
                true, true, fan.id, listOf(fan), runtime, snapshot, 25.0, circ.cdp, circ.con, exhaust.eon
            )
            assertTrue(markers.isEmpty())
        }
    }

    @Test
    fun myGreenhouseDisconnectedProducesNoExhaustEffect() = runTest {
        val vm = EquipmentPlacementViewModel(InMemoryEquipmentLayoutRepository())
        vm.attach(
            "grower@greenhands.app",
            "cfg-exh-mygh",
            DemoGreenhouseProfile.LENGTH_M,
            DemoGreenhouseProfile.WIDTH_M,
            DemoGreenhouseProfile.HEIGHT_M,
            DemoGreenhouseProfile.CROP_ROW_COUNT,
            climate,
            PreviewMode.MY_GREENHOUSE
        )
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.EXHAUST_FAN)
        vm.startBehavior()
        assertFalse(vm.state.value.behaviorRunning)
        assertTrue(vm.exhaustEffectFrame().isEmpty)
        assertNull(vm.state.value.physicsSnapshot)
    }

    @Test
    fun everyApprovedTemperatureBoundaryAndInvalidThresholds() {
        val fan = farWallExhaust("band-1", 1)
        fun colorAt(temperatureC: Double, cdp: Double = circ.cdp, con: Double = circ.con, eon: Double = exhaust.eon) =
            TemperatureLegendClassifier.blendedColor(temperatureC, 25.0, cdp, con, eon)

        assertEquals(TemperatureLegendBand.BELOW_CDP, TemperatureLegendClassifier.band(22.999, 25.0, circ.cdp, circ.con, exhaust.eon))
        assertEquals(VisualizationRgb.PURPLE_DARK_BLUE, TemperatureLegendClassifier.color(22.999, 25.0, circ.cdp, circ.con, exhaust.eon))
        assertEquals(TemperatureLegendBand.BELOW_TARGET, TemperatureLegendClassifier.band(circ.cdp, 25.0, circ.cdp, circ.con, exhaust.eon))
        assertEquals(TemperatureLegendBand.TARGET, TemperatureLegendClassifier.band(25.0, 25.0, circ.cdp, circ.con, exhaust.eon))
        assertEquals(TemperatureLegendBand.ABOVE_TARGET, TemperatureLegendClassifier.band(26.0, 25.0, circ.cdp, circ.con, exhaust.eon))
        assertEquals(TemperatureLegendBand.CON_TO_EON, TemperatureLegendClassifier.band(circ.con, 25.0, circ.cdp, circ.con, exhaust.eon))
        assertEquals(TemperatureLegendBand.EON_OR_ABOVE, TemperatureLegendClassifier.band(exhaust.eon, 25.0, circ.cdp, circ.con, exhaust.eon))
        assertFalse(colorAt(32.0).isUnavailable)

        val invalid = TemperatureLegendClassifier.blendedColor(32.0, 25.0, 30.0, 20.0, 40.0)
        assertTrue(invalid.isUnavailable)
        val runtime = mapOf(fan.id to EquipmentRuntimeState.ON)
        val snapshot = liveSnapshot(listOf(fan), runtime)
        val badThresholds = EquipmentRuntimeThresholds(
            circulation = com.greenhands.app.heat.model.CirculationThresholds(csp = 25.0, cdp = 30.0, con = 20.0),
            exhaust = exhaust,
            fogger = fogger
        )
        val frame = ExhaustEffectPlanner.plan(true, listOf(fan), runtime, snapshot, badThresholds, 25.0)
        assertFalse(frame.isEmpty)
        frame.groups.single().samples.forEach { sample ->
            assertEquals(TemperatureLegendBand.UNAVAILABLE, sample.band)
            assertTrue(sample.color.isUnavailable)
        }
        val write = ArCirculationColorMaterial.frameColorWrite(invalid, 1f)
        assertFalse(write.requestsEmissiveFactor)
    }

    @Test
    fun sameSnapshotIsDeterministicAndVisualFramesDoNotAdvanceSolver() {
        val fan = farWallExhaust("det-1", 1)
        val runtime = mapOf(fan.id to EquipmentRuntimeState.ON)
        val snapshot = liveSnapshot(listOf(fan), runtime)
        val a = ExhaustEffectPlanner.plan(true, listOf(fan), runtime, snapshot, thresholds, 25.0)
        val b = ExhaustEffectPlanner.plan(true, listOf(fan), runtime, snapshot, thresholds, 25.0)
        assertEquals(a.groups.single().equipmentUuid, b.groups.single().equipmentUuid)
        assertEquals(a.groups.single().originM, b.groups.single().originM)
        assertEquals(a.groups.single().yawDeg, b.groups.single().yawDeg, 0.0)
        assertEquals(a.groups.single().inwardDomainDy, b.groups.single().inwardDomainDy, 0.0)
        assertEquals(a.groups.single().validProbeDistancesM, b.groups.single().validProbeDistancesM)
        assertEquals(a.groups.single().indoorArrowCount, b.groups.single().indoorArrowCount)

        val request = Phase3D2AFixtures.demoRequest(
            listOf(fan),
            35.0,
            60.0,
            weather = WeatherBoundary.demoSimulated(25.0, 70.0),
            lengthM = DemoGreenhouseProfile.LENGTH_M,
            widthM = DemoGreenhouseProfile.WIDTH_M,
            heightM = DemoGreenhouseProfile.HEIGHT_M,
            runtimeById = runtime
        )
        val physics = PhysicsFieldRuntime()
        val started = physics.start(request)
        val elapsed = physics.elapsedTimeS()
        val mean = started.meanTemperatureC
        repeat(12) { step ->
            ExhaustAirflowAnimator.indoorDistanceM(0.0, 5.0, 0.016 * step, 0, 6)
            ExhaustAirflowAnimator.outdoorDistanceM(0.55, 0.016 * step, 0, 2)
            ExhaustAirflowAnimator.fadeAtProgress(0.3)
        }
        assertEquals(elapsed, physics.elapsedTimeS(), 0.0)
        assertEquals(0.0, physics.elapsedTimeS(), 0.0)
        assertEquals(mean, physics.snapshot()!!.meanTemperatureC, 0.0)
        physics.stop()
    }

    @Test
    fun fiveSecondDisplayPublicationDoesNotChangeOneSecondPhysicsScheduler() {
        val elapsedS = 10.0
        assertEquals(10, physicsSteps(30, elapsedS))
        assertEquals(10, physicsSteps(60, elapsedS))
        assertEquals(10, physicsSteps(90, elapsedS))
        assertEquals(2, displaySteps(30, elapsedS))
        assertEquals(2, displaySteps(60, elapsedS))
        assertEquals(2, displaySteps(90, elapsedS))
        assertEquals(5.0, DemoDisplayPublication.INTERVAL_S, 0.0)
        assertEquals(1.0, DemoPhysicsSchedulerConfig().publicationIntervalS, 0.0)
    }

    @Test
    fun indoorArrowsTravelTowardFanAndOutdoorContinuesOutward() {
        val d0 = ExhaustAirflowAnimator.indoorDistanceM(0.0, 5.0, 0.0, 0, 1, 2.8, 0.0)
        val dHalf = ExhaustAirflowAnimator.indoorDistanceM(0.0, 5.0, 1.4, 0, 1, 2.8, 0.0)
        val dEnd = ExhaustAirflowAnimator.indoorDistanceM(0.0, 5.0, 2.799, 0, 1, 2.8, 0.0)
        assertEquals(5.0, d0, 1e-9)
        assertTrue(dHalf < d0)
        assertTrue(dEnd < dHalf)
        assertTrue(dEnd < 0.05)
        val o0 = ExhaustAirflowAnimator.outdoorDistanceM(0.55, 0.0, 0, 1, 2.2, 0.0)
        val o1 = ExhaustAirflowAnimator.outdoorDistanceM(0.55, 1.1, 0, 1, 2.2, 0.0)
        assertEquals(0.0, o0, 1e-9)
        assertTrue(o1 > o0)
        assertEquals(ExhaustAirflowVisual.OUTWARD_LENGTH_M, 0.55, 0.0)
        assertTrue(ExhaustAirflowAnimator.outdoorColor().red > 0.5f)
    }

    @Test
    fun renderCapsAreEnforced() {
        val fans = (1..6).map { n -> farWallExhaust("cap-$n", n, xM = 0.8 + n * 0.5) }
        val runtime = fans.associate { it.id to EquipmentRuntimeState.ON }
        val snapshot = liveSnapshot(fans, runtime)
        val frame = ExhaustEffectPlanner.plan(true, fans, runtime, snapshot, thresholds, 25.0)
        assertTrue(frame.groups.size <= ExhaustAirflowVisual.MAX_EXHAUST_GROUPS)
        assertTrue(frame.arrowObjectCount <= ExhaustAirflowVisual.MAX_TOTAL_ARROWS)
        frame.groups.forEach { group ->
            assertTrue(group.indoorArrowCount <= ExhaustAirflowVisual.MAX_INDOOR_ARROWS_PER_FAN)
            assertTrue(group.outdoorArrowCount <= ExhaustAirflowVisual.MAX_OUTDOOR_ARROWS_PER_FAN)
            assertTrue(group.arrowCount <= ExhaustAirflowVisual.MAX_ARROWS_PER_FAN)
        }
        assertEquals(4, ExhaustAirflowVisual.MAX_EXHAUST_GROUPS)
        assertEquals(24, ExhaustAirflowVisual.MAX_TOTAL_ARROWS)
        assertEquals(30.0, ExhaustAirflowVisual.MAX_VISUAL_HZ, 0.0)
    }

    @Test
    fun demoHighTemperatureTurnsExhaustOnThenOffRemovesOnlyExhaustArrows() = runTest {
        val vm = demoVm("cfg-exh-eon")
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.EXHAUST_FAN)
        vm.tapPalette(EquipmentType.CIRCULATION_FAN)
        vm.setSimulatedTemperature(35.0)
        vm.setSimulatedHumidity(60.0)
        vm.startBehavior()
        val exhaustId = vm.state.value.instances.single { it.type == EquipmentType.EXHAUST_FAN }.id
        val circId = vm.state.value.instances.single { it.type == EquipmentType.CIRCULATION_FAN }.id
        assertEquals(EquipmentRuntimeState.ON, vm.state.value.runtimeById[exhaustId])
        assertFalse(vm.exhaustEffectFrame().isEmpty)
        assertEquals(exhaustId, vm.exhaustEffectFrame().groups.single().equipmentUuid)
        assertEquals(ExhaustAirflowMode.QUANTITATIVE, vm.exhaustEffectFrame().groups.single().mode)
        assertFalse(vm.circulationEffectFrame().isEmpty)
        val indoor = vm.exhaustEffectFrame().groups.single()
        assertTrue(indoor.inwardDomainDy < 0.0)
        assertTrue(indoor.outwardDomainDy > 0.0)
        assertTrue(indoor.indoorMaxDistanceM > indoor.indoorMinDistanceM)

        vm.select(exhaustId)
        assertFalse(vm.exhaustEffectFrame().isEmpty)
        assertFalse(vm.circulationEffectFrame().isEmpty)
        vm.toggleLabelsVisible()
        vm.toggleLabelsVisible()
        vm.setSelectedTool(SelectedEquipmentTool.EXACT)
        vm.setSelectedTool(SelectedEquipmentTool.MOVE)
        assertFalse(vm.exhaustEffectFrame().isEmpty)

        vm.setSimulatedTemperature(26.5)
        assertEquals(EquipmentRuntimeState.OFF, vm.state.value.runtimeById[exhaustId])
        assertTrue(vm.exhaustEffectFrame().isEmpty)
        assertEquals(EquipmentRuntimeState.ON, vm.state.value.runtimeById[circId])
        assertFalse(vm.circulationEffectFrame().isEmpty)
    }

    @Test
    fun stopClearsExhaustWithoutClearingLayoutReadingsOrEquipment() = runTest {
        val vm = demoVm("cfg-exh-stop")
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.EXHAUST_FAN)
        vm.setSimulatedTemperature(35.0)
        vm.setSimulatedHumidity(60.0)
        vm.startBehavior()
        val before = vm.state.value
        val equipmentId = before.instances.single().id
        val temperature = before.simulatedTemperatureC
        assertFalse(vm.exhaustEffectFrame().isEmpty)
        vm.stopBehavior()
        val after = vm.state.value
        assertTrue(vm.exhaustEffectFrame().isEmpty)
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
                    configurationId = "cfg-exh-stop",
                    userId = "grower@greenhands.app",
                    lengthM = DemoGreenhouseProfile.LENGTH_M,
                    widthM = DemoGreenhouseProfile.WIDTH_M,
                    heightM = DemoGreenhouseProfile.HEIGHT_M,
                    cropRowCount = DemoGreenhouseProfile.CROP_ROW_COUNT,
                    instances = after.instances
                )
            )
        )
        assertFalse(encoded.contains("exh-arrow"))
        assertFalse(encoded.contains("ExhaustEffect"))
        assertFalse(encoded.contains(PhysicsRuntimeCopy.MODULE_UNAVAILABLE_WEATHER))
        assertFalse(MyGreenhousePersistence.containsForbiddenPersistedState(encoded))
        assertFalse(Phase3D2B2.PERSISTS_EFFECTS)
        assertTrue(Phase3D2B2.RENDERS_EXHAUST)
        assertFalse(Phase3D2B2.RENDERS_FOG)
        assertFalse(Phase3D2B2.RENDERS_FIELD_LAYERS)
        assertFalse(Phase3D2B2.ADDS_LAYER_LEGEND)
        assertFalse(Phase3D2B2.ADDS_NETWORKING)
        assertFalse(Phase3D2B2.CHANGES_PHASE_3A)
        assertFalse(Phase3D2B2.CHANGES_PHASE_3D2A)
        assertFalse(Phase3D2B2.CHANGES_CIRCULATION_RINGS)
        assertFalse(Phase3D2B1.RENDERS_EXHAUST)
    }

    @Test
    fun selectionLabelsAndSheetsDoNotRemoveExhaustEffects() = runTest {
        val vm = demoVm("cfg-exh-select")
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.EXHAUST_FAN)
        vm.duplicateSelected()
        vm.setSimulatedTemperature(35.0)
        vm.setSimulatedHumidity(60.0)
        vm.startBehavior()
        val ids = vm.state.value.instances.map { it.id }
        val host = ArExhaustAirflowHost()
        host.bind(ArExhaustAirflowHost.request(vm.exhaustEffectFrame(), ArTabletopPhase.PLACED))
        val epoch = host.resourceEpoch
        vm.select(ids[0])
        host.noteRecomposition()
        host.uiChromeChanged()
        host.bind(ArExhaustAirflowHost.request(vm.exhaustEffectFrame(), ArTabletopPhase.PLACED))
        vm.select(ids[1])
        vm.toggleLabelsVisible()
        vm.setSelectedTool(SelectedEquipmentTool.EXACT)
        assertEquals(epoch, host.resourceEpoch)
        assertEquals(2, vm.exhaustEffectFrame().groups.size)
        assertEquals(CirculationRingSceneMath.GREENHOUSE_ROOT_NAME, host.parentName)
        assertFalse(host.applyTabletopScaleToChildren)
    }

    @Test
    fun exhaustProbesSheetUsesCombinedFieldCaptionAndDemoBoundary() {
        assertTrue(VisualizationCopy.EXHAUST_PROBE_CAPTION.contains(VisualizationCopy.PREDICTED_UNCALIBRATED))
        assertTrue(VisualizationCopy.EXHAUST_PROBE_CAPTION.contains(PhysicsRuntimeCopy.DEMO_SIMULATED_OUTSIDE_BOUNDARY))
        assertTrue(VisualizationCopy.EXHAUST_PROBE_CAPTION.contains(VisualizationCopy.EXHAUST_COMBINED_FIELD_NOTE))
        val caption = VisualizationCopy.exhaustProbeCaption(PhysicsRuntimeCopy.DEMO_SIMULATED_OUTSIDE_BOUNDARY)
        assertTrue(caption.contains("Predicted — uncalibrated"))
        assertTrue(caption.contains("Demo simulated outside boundary — not measured weather."))
        assertEquals("Predicted · 5 s", VisualizationCopy.EXHAUST_PROBE_CHIP)
        assertFalse(ExhaustAirflowAppearance.WRITES_EMISSIVE_FACTOR)
        assertFalse(ArCirculationColorMaterial.writesEmissiveFactor())
        val write = ArCirculationColorMaterial.frameColorWrite(VisualizationRgb.NEUTRAL_AIRFLOW, 0.5f)
        assertEquals(setOf("color"), write.uniformNames)
        assertFalse(write.requestsEmissiveFactor)
        assertFalse("emissiveFactor" in ArCirculationColorMaterial.allowedUniforms)
    }

    @Test
    fun compactPortraitAndLandscapeAllocationRemainsValid() {
        val portrait = ArOverlayLayout.compactPortrait(360f, 800f, placed = true)
        val landscape = ArOverlayLayout.compactLandscape(800f, 360f, placed = true)
        assertTrue(portrait.overlappingPairs().isEmpty())
        assertTrue(landscape.overlappingPairs().isEmpty())
        assertTrue(portrait.viewport.height >= ArOverlayLayout.MIN_VIEWPORT_DP)
        assertEquals(ArOverlayLayout.HEADER_LANDSCAPE_DP, landscape.header.height, 0.01f)
        val chip = ArOverlayLayout.selectedBar(portrait.viewport, landscape = false)
        assertEquals(ArOverlayLayout.SELECTED_BAR_DP, chip.height, 0.01f)
        val landscapeChip = ArOverlayLayout.selectedBar(landscape.viewport, landscape = true)
        assertTrue(landscapeChip.bottom <= landscape.viewport.bottom + 0.01f)
    }

    @Test
    fun tabletopRotationDoesNotChangeGreenhouseLocalPhysicsDirection() {
        val fan = farWallExhaust("rot-1", 1, yawDeg = 90.0)
        val runtime = mapOf(fan.id to EquipmentRuntimeState.ON)
        val snapshot = liveSnapshot(listOf(fan), runtime)
        val frame = ExhaustEffectPlanner.plan(true, listOf(fan), runtime, snapshot, thresholds, 25.0)
        val layout = ArTabletopScale.layout(DemoGreenhouseProfile.dimensions(), 3)
        val group = frame.groups.single()
        val (sx, _, _) = CirculationRingSceneMath.domainForwardToScene(
            group.outwardDomainDx,
            group.outwardDomainDy,
            group.outwardDomainDz,
            layout
        )
        assertTrue(layout.lengthMappedToX)
        assertTrue(sx > 0.0)
        assertEquals(90.0, CirculationRingSceneMath.sceneYawDeg(90.0, layout), 0.0)
        assertEquals(-1.0, group.inwardDomainDy, 1e-12)
        assertEquals(1.0, group.outwardDomainDy, 1e-12)
    }

    @Test
    fun hostBindsGreenhouseRootWithSingleTabletopScale() {
        val fan = farWallExhaust("bind-1", 1)
        val runtime = mapOf(fan.id to EquipmentRuntimeState.ON)
        val snapshot = liveSnapshot(listOf(fan), runtime)
        val frame = ExhaustEffectPlanner.plan(true, listOf(fan), runtime, snapshot, thresholds, 25.0)
        val host = ArExhaustAirflowHost()
        val request = ArExhaustAirflowHost.request(frame, ArTabletopPhase.PLACED)
        assertEquals(CirculationRingSceneMath.GREENHOUSE_ROOT_NAME, request.parentName)
        assertFalse(request.applyTabletopScaleToChildren)
        host.bind(request)
        assertEquals(listOf("bind-1"), host.attachedUuids)
        val layout = ArTabletopScale.layout(DemoGreenhouseProfile.dimensions(), 3)
        val sceneDistance = CirculationRingSceneMath.sceneDistance(2.0, layout)
        assertEquals(2.0 * layout.uniformScale, sceneDistance, 0.0)
        assertNotEquals(sceneDistance * ArTabletopScale.rootScale(), sceneDistance, 0.0)
    }

    private fun farWallExhaust(
        id: String,
        number: Int,
        xM: Double = 2.5,
        yawDeg: Double = 90.0
    ): EquipmentInstance = Phase3D2AFixtures.instance(
        EquipmentType.EXHAUST_FAN,
        id,
        number,
        xM = xM,
        yM = DemoGreenhouseProfile.LENGTH_M,
        zM = 1.5,
        yawDeg = yawDeg
    )

    private fun liveSnapshot(
        instances: List<EquipmentInstance>,
        runtime: Map<String, EquipmentRuntimeState>
    ): PhysicsFieldSnapshot {
        val request = Phase3D2AFixtures.demoRequest(
            instances,
            35.0,
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

    private fun qualitativeSnapshot(
        instances: List<EquipmentInstance>,
        runtime: Map<String, EquipmentRuntimeState>,
        weatherStatus: WeatherBoundaryStatus
    ): PhysicsFieldSnapshot {
        val base = liveSnapshot(instances, runtime)
        val specs = EquipmentSpecificationResolver.resolveAll(PreviewMode.DEMO, instances)
        val readiness = instances.map { inst ->
            val spec = specs.getValue(inst.id)
            UuidEquipmentReadiness(
                equipmentUuid = inst.id,
                type = inst.type,
                runtimeState = runtime[inst.id] ?: EquipmentRuntimeState.NOT_RUNNING,
                specification = spec,
                contributesActiveSource = false,
                quantitativeReady = false,
                unavailableReason = PhysicsRuntimeCopy.MODULE_UNAVAILABLE_WEATHER
            )
        }
        return PhysicsFieldSnapshot(
            elapsedTimeS = base.elapsedTimeS,
            grid = base.grid,
            temperatureC = base.temperatureC,
            humidityRatioKgKg = base.humidityRatioKgKg,
            relativeHumidityPercent = base.relativeHumidityPercent,
            meanTemperatureC = base.meanTemperatureC,
            meanRelativeHumidityPercent = base.meanRelativeHumidityPercent,
            referenceTemperatureC = base.referenceTemperatureC,
            referenceRelativeHumidityPercent = base.referenceRelativeHumidityPercent,
            insideSource = base.insideSource,
            insideSourceLabel = base.insideSourceLabel,
            weatherStatus = weatherStatus,
            weatherSourceLabel = PhysicsRuntimeCopy.MODULE_UNAVAILABLE_WEATHER,
            weatherProvenance = WeatherBoundaryProvenance.MISSING,
            weatherKind = WeatherBoundaryKind.MISSING,
            validationStatus = FieldValidationStatus.MODULE_UNAVAILABLE,
            equipment = readiness,
            runtimeById = runtime,
            warnings = listOf(PhysicsRuntimeCopy.MODULE_UNAVAILABLE_WEATHER),
            unavailableModuleReasons = instances.associate { it.id to PhysicsRuntimeCopy.MODULE_UNAVAILABLE_WEATHER },
            probes = base.probes.map { probe ->
                if (probe.axis != EquipmentProbeAxis.EXHAUST_INWARD) probe else probe.copy(
                    temperatureC = null,
                    relativeHumidityPercent = null,
                    airSpeedMps = null,
                    availability = ProbeAvailability.MODULE_UNAVAILABLE,
                    validationStatus = FieldValidationStatus.MODULE_UNAVAILABLE,
                    reason = PhysicsRuntimeCopy.MODULE_UNAVAILABLE_WEATHER
                )
            },
            wellMixedVentilation = base.wellMixedVentilation,
            finiteValues = true
        )
    }

    private fun snapshotOf(
        instances: List<EquipmentInstance>,
        runtime: Map<String, EquipmentRuntimeState>,
        probes: List<com.greenhands.app.heat.runtime.contracts.EquipmentProbeSample>,
        finite: Boolean
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
            temperatureC = DoubleArray(8) { 32.0 },
            humidityRatioKgKg = DoubleArray(8) { 0.01 },
            relativeHumidityPercent = DoubleArray(8) { 60.0 },
            meanTemperatureC = 32.0,
            meanRelativeHumidityPercent = 60.0,
            referenceTemperatureC = 32.0,
            referenceRelativeHumidityPercent = 60.0,
            insideSource = InsideClimateSource.SIMULATED,
            insideSourceLabel = "Demo simulation",
            weatherStatus = WeatherBoundaryStatus.AVAILABLE,
            weatherSourceLabel = PhysicsRuntimeCopy.DEMO_SIMULATED_OUTSIDE_BOUNDARY,
            weatherProvenance = WeatherBoundaryProvenance.DEMO_ONLY_ASSUMPTION,
            weatherKind = WeatherBoundaryKind.DEMO_SIMULATED,
            validationStatus = FieldValidationStatus.UNCALIBRATED,
            equipment = readiness,
            runtimeById = runtime,
            warnings = emptyList(),
            unavailableModuleReasons = emptyMap(),
            probes = probes,
            wellMixedVentilation = false,
            finiteValues = finite
        )
    }

    private fun syntheticSnapshot(
        instances: List<EquipmentInstance>,
        runtime: Map<String, EquipmentRuntimeState>,
        meanOverride: Double? = null,
        lengthM: Double = DemoGreenhouseProfile.LENGTH_M,
        widthM: Double = DemoGreenhouseProfile.WIDTH_M,
        heightM: Double = DemoGreenhouseProfile.HEIGHT_M,
        fill: (Vec3) -> Double
    ): PhysicsFieldSnapshot {
        val geometry = UserPhysicsCoordinateAdapter.geometry(lengthM, widthM, heightM, 0.50)
        val grid = GridState.build(geometry, 0.0, 0.01)
        var sum = 0.0
        var n = 0
        grid.forEachActive { idx, i, j, k ->
            val temperature = fill(geometry.cellCenterM(i, j, k))
            grid.temperatureC[idx] = temperature
            sum += temperature
            n++
        }
        val trueMean = if (n == 0) 0.0 else sum / n
        val publishedMean = meanOverride ?: trueMean
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
        val probes = EquipmentDistanceProbes.sampleAll(grid, instances, 101_325.0, true)
        return PhysicsFieldSnapshot(
            elapsedTimeS = 0.0,
            grid = geometry.toSnapshot(grid.activeCellCount),
            temperatureC = grid.temperatureC.copyOf(),
            humidityRatioKgKg = grid.humidityRatioKgKg.copyOf(),
            relativeHumidityPercent = DoubleArray(grid.size) { 60.0 },
            meanTemperatureC = publishedMean,
            meanRelativeHumidityPercent = 60.0,
            referenceTemperatureC = publishedMean,
            referenceRelativeHumidityPercent = 60.0,
            insideSource = InsideClimateSource.SIMULATED,
            insideSourceLabel = "Demo simulation",
            weatherStatus = WeatherBoundaryStatus.AVAILABLE,
            weatherSourceLabel = PhysicsRuntimeCopy.DEMO_SIMULATED_OUTSIDE_BOUNDARY,
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

    private fun physicsSteps(fps: Int, elapsedS: Double): Int {
        val scheduler = DemoPhysicsScheduler(DemoPhysicsSchedulerConfig(publicationIntervalS = 1.0))
        val dt = 1.0 / fps
        var steps = 0
        var t = 0.0
        while (t < elapsedS - 1e-9) {
            steps += scheduler.acceptFrame(dt).stepCount
            t += dt
        }
        return steps
    }

    private fun displaySteps(fps: Int, elapsedS: Double): Int {
        val scheduler = DemoPhysicsScheduler(
            DemoPhysicsSchedulerConfig(
                publicationIntervalS = DemoDisplayPublication.INTERVAL_S,
                pauseResetThresholdS = DemoDisplayPublication.PAUSE_RESET_THRESHOLD_S
            )
        )
        val dt = 1.0 / fps
        var steps = 0
        var t = 0.0
        while (t < elapsedS - 1e-9) {
            steps += scheduler.acceptFrame(dt).stepCount
            t += dt
        }
        return steps
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
