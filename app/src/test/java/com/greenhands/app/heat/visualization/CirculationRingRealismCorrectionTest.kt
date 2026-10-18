package com.greenhands.app.heat.visualization

import com.greenhands.app.ar.domain.ArTabletopPhase
import com.greenhands.app.ar.render.ArCirculationRingHost
import com.greenhands.app.ar.render.CirculationRingSceneMath
import com.greenhands.app.equipment.data.InMemoryEquipmentLayoutRepository
import com.greenhands.app.equipment.model.EquipmentInstance
import com.greenhands.app.equipment.model.EquipmentRuntimeState
import com.greenhands.app.equipment.model.EquipmentType
import com.greenhands.app.equipment.ui.EquipmentPlacementViewModel
import com.greenhands.app.equipment.ui.OverlayClimate
import com.greenhands.app.heat.domain.HeatFormulas
import com.greenhands.app.heat.runtime.DemoPhysicsScheduler
import com.greenhands.app.heat.runtime.DemoPhysicsSchedulerConfig
import com.greenhands.app.heat.runtime.PhysicsFieldRuntime
import com.greenhands.app.heat.runtime.Phase3D2AFixtures
import com.greenhands.app.heat.runtime.adapter.UserPhysicsCoordinateAdapter
import com.greenhands.app.heat.runtime.contracts.PhysicsFieldSnapshot
import com.greenhands.app.heat.runtime.contracts.WeatherBoundary
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
class CirculationRingRealismCorrectionTest {

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
    fun displayPublicationIsFiveSecondsAtThirtySixtyAndNinetyFpsWhilePhysicsStaysOneSecond() {
        val elapsedS = 10.0
        assertEquals(10, physicsSteps(30, elapsedS))
        assertEquals(10, physicsSteps(60, elapsedS))
        assertEquals(10, physicsSteps(90, elapsedS))
        assertEquals(2, displaySteps(30, elapsedS))
        assertEquals(2, displaySteps(60, elapsedS))
        assertEquals(2, displaySteps(90, elapsedS))
        assertEquals(5.0, DemoDisplayPublication.INTERVAL_S, 0.0)
        assertTrue(DemoDisplayPublication.PAUSE_RESET_THRESHOLD_S > DemoDisplayPublication.INTERVAL_S)
    }

    @Test
    fun viewModelPhysicsAdvancesEverySecondButHudPublishesEveryFive() = runTest {
        val physics = DemoPhysicsScheduler()
        val display = DemoPhysicsScheduler(
            DemoPhysicsSchedulerConfig(
                publicationIntervalS = DemoDisplayPublication.INTERVAL_S,
                pauseResetThresholdS = DemoDisplayPublication.PAUSE_RESET_THRESHOLD_S
            )
        )
        val vm = EquipmentPlacementViewModel(
            InMemoryEquipmentLayoutRepository(),
            physicsScheduler = physics,
            displayScheduler = display
        )
        attach(vm)
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.CIRCULATION_FAN)
        vm.setSimulatedTemperature(30.0)
        vm.setSimulatedHumidity(60.0)
        vm.startBehavior()
        val initialDisplayed = vm.state.value.displayedTemperatureC!!
        assertEquals(30.0, initialDisplayed, 1e-9)
        assertEquals(0.0, vm.state.value.physicsSnapshot!!.elapsedTimeS, 0.0)

        val dt = 1.0 / 60.0
        repeat(60) { vm.advanceVisualization(dt) }
        assertEquals(1.0, vm.state.value.physicsSnapshot!!.elapsedTimeS, 1e-9)
        assertEquals(initialDisplayed, vm.state.value.displayedTemperatureC!!, 0.0)
        assertEquals(30.0, vm.state.value.simulatedTemperatureC!!, 0.0)

        repeat(240) { vm.advanceVisualization(dt) }
        assertEquals(5.0, vm.state.value.physicsSnapshot!!.elapsedTimeS, 1e-9)
        assertEquals(
            vm.state.value.physicsSnapshot!!.referenceTemperatureC,
            vm.state.value.displayedTemperatureC!!,
            0.0
        )
        assertEquals(
            vm.state.value.physicsSnapshot!!.referenceRelativeHumidityPercent,
            vm.state.value.displayedHumidityPercent!!,
            0.0
        )
        assertTrue(vm.state.value.displayedFieldSnapshot === vm.state.value.physicsSnapshot)
        vm.stopBehavior()
        assertNull(vm.state.value.displayedFieldSnapshot)
        assertEquals(0.0, physics.accumulatorS(), 0.0)
        assertEquals(0.0, display.accumulatorS(), 0.0)
    }

    @Test
    fun pauseDoesNotCatchUpDisplayOrPhysics() {
        val physics = DemoPhysicsScheduler()
        val display = DemoPhysicsScheduler(
            DemoPhysicsSchedulerConfig(
                publicationIntervalS = DemoDisplayPublication.INTERVAL_S,
                pauseResetThresholdS = DemoDisplayPublication.PAUSE_RESET_THRESHOLD_S
            )
        )
        repeat(10) {
            physics.acceptFrame(1.0 / 60.0)
            display.acceptFrame(1.0 / 60.0)
        }
        assertEquals(0, physics.acceptFrame(30.0).stepCount)
        assertEquals(0, display.acceptFrame(30.0).stepCount)
        assertEquals(0.0, physics.accumulatorS(), 0.0)
        assertEquals(0.0, display.accumulatorS(), 0.0)
    }

    @Test
    fun uniformCirculationOnlyFieldDoesNotForceAColourWalk() {
        val fan = Phase3D2AFixtures.instance(EquipmentType.CIRCULATION_FAN, "circ-1", 1, xM = 1.0, yM = 5.0)
        val snapshot = liveSnapshot(listOf(fan), mapOf(fan.id to EquipmentRuntimeState.ON))
        val frame = CirculationEffectPlanner.plan(
            true, listOf(fan), mapOf(fan.id to EquipmentRuntimeState.ON), snapshot, thresholds, 25.0
        )
        val group = frame.groups.single()
        val near = CirculationRingAnimator.colorAtDistance(group, 1.0, snapshot)
        val mid = CirculationRingAnimator.colorAtDistance(group, 2.5, snapshot)
        val far = CirculationRingAnimator.colorAtDistance(group, group.maxDistanceM, snapshot)
        assertEquals(near, mid)
        assertEquals(near, far)
        val nearT = CirculationRingAnimator.temperatureAtDistance(group, 1.0, snapshot)!!
        val farT = CirculationRingAnimator.temperatureAtDistance(group, group.maxDistanceM, snapshot)!!
        assertEquals(nearT, farT, 1e-6)
        assertNotEquals(VisualizationRgb.UNAVAILABLE, near)
    }

    @Test
    fun colourUsesFieldSampleAndApprovedBoundariesWithoutInventingCooling() {
        val fan = Phase3D2AFixtures.instance(EquipmentType.CIRCULATION_FAN, "circ-1", 1, xM = 1.0, yM = 5.0)
        val snapshot = liveSnapshot(listOf(fan), mapOf(fan.id to EquipmentRuntimeState.ON))
        snapshot.temperatureC.indices.forEach { idx ->
            val j = (idx / snapshot.grid.nx) % snapshot.grid.ny
            snapshot.temperatureC[idx] = 30.0 + j * 0.4
        }
        SnapshotFieldSampler.clearCache()
        assertNotNull(SnapshotFieldSampler.gridOf(snapshot))
        val frame = CirculationEffectPlanner.plan(
            true, listOf(fan), mapOf(fan.id to EquipmentRuntimeState.ON), snapshot, thresholds, 25.0
        )
        val group = frame.groups.single()
        val nearT = CirculationRingAnimator.temperatureAtDistance(group, 1.0, snapshot)!!
        val farT = CirculationRingAnimator.temperatureAtDistance(group, group.maxDistanceM, snapshot)!!
        assertTrue(farT > nearT)
        val nearColor = CirculationRingAnimator.colorAtDistance(group, 1.0, snapshot)
        val farColor = CirculationRingAnimator.colorAtDistance(group, group.maxDistanceM, snapshot)
        assertEquals(
            TemperatureLegendClassifier.blendedColor(nearT, 25.0, circ.cdp, circ.con, exhaust.eon),
            nearColor
        )
        assertEquals(
            TemperatureLegendClassifier.blendedColor(farT, 25.0, circ.cdp, circ.con, exhaust.eon),
            farColor
        )
        val againNear = CirculationRingAnimator.colorAtDistance(group, 1.0, snapshot)
        assertEquals(nearColor, againNear)
    }

    @Test
    fun selectedFanProbeLabelsUseSnapshotProbesAndOmitOutsidePoints() {
        val yaw0 = Phase3D2AFixtures.instance(
            EquipmentType.CIRCULATION_FAN, "yaw0", 1, xM = 1.0, yM = 5.0, yawDeg = 0.0
        )
        val other = Phase3D2AFixtures.instance(
            EquipmentType.CIRCULATION_FAN, "other", 2, xM = 1.0, yM = 8.0, yawDeg = 0.0
        )
        val runtime = mapOf(yaw0.id to EquipmentRuntimeState.ON, other.id to EquipmentRuntimeState.ON)
        val snapshot = liveSnapshot(listOf(yaw0, other), runtime)
        val selected = CirculationProbeLabelPlanner.plan(
            visualizationRunning = true,
            labelsVisible = true,
            selectedId = yaw0.id,
            instances = listOf(yaw0, other),
            runtimeById = runtime,
            snapshot = snapshot
        )
        assertTrue(selected.isNotEmpty())
        assertTrue(selected.all { it.equipmentUuid == "yaw0" })
        assertTrue(selected.none { kotlin.math.abs(it.distanceM - 5.0) < 1e-9 })
        val (dx, dy, dz) = UserPhysicsCoordinateAdapter.yawToDomainForward(0.0)
        selected.forEach { marker ->
            assertEquals(yaw0.positionM.xM + dx * marker.distanceM, marker.domain.xM, 1e-12)
            assertEquals(yaw0.positionM.yM + dy * marker.distanceM, marker.domain.yM, 1e-12)
            assertEquals(yaw0.positionM.zM + dz * marker.distanceM, marker.domain.zM, 1e-12)
            assertTrue(marker.text.contains("°C"))
            assertTrue(marker.text.contains("m ·"))
            assertEquals("${marker.distanceM.toInt()} m", marker.compactDistanceText)
            assertFalse(marker.compactDistanceText.contains("°C"))
        }
        val none = CirculationProbeLabelPlanner.plan(
            true, true, other.id, listOf(yaw0, other), runtime, snapshot
        )
        assertTrue(none.all { it.equipmentUuid == "other" })
        assertTrue(
            CirculationProbeLabelPlanner.plan(
                true, true, yaw0.id, listOf(yaw0, other),
                mapOf(yaw0.id to EquipmentRuntimeState.OFF, other.id to EquipmentRuntimeState.ON),
                snapshot
            ).isEmpty()
        )
        assertTrue(
            CirculationProbeLabelPlanner.plan(
                true, false, yaw0.id, listOf(yaw0, other), runtime, snapshot
            ).isEmpty()
        )
        assertTrue(
            CirculationProbeLabelPlanner.plan(
                false, true, yaw0.id, listOf(yaw0, other), runtime, snapshot
            ).isEmpty()
        )
        assertTrue(
            CirculationProbeLabelPlanner.plan(
                true, true, null, listOf(yaw0, other), runtime, snapshot
            ).isEmpty()
        )
    }

    @Test
    fun probeValuesRefreshFromTheNewestAcceptedSnapshot() = runTest {
        val vm = demoVm("cfg-probe-refresh")
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.CIRCULATION_FAN)
        vm.setSimulatedTemperature(30.0)
        vm.setSimulatedHumidity(60.0)
        vm.startBehavior()
        val fanId = vm.state.value.instances.single().id
        vm.select(fanId)
        val dt = 1.0 / 60.0
        repeat(300) { vm.advanceVisualization(dt) }
        val published = vm.circulationProbeMarkers()
        assertEquals(vm.state.value.physicsSnapshot, vm.state.value.displayedFieldSnapshot)
        assertEquals(fanId, published.firstOrNull()?.equipmentUuid)
        val expected = CirculationProbeLabelPlanner.plan(
            true, true, fanId, vm.state.value.instances, vm.state.value.runtimeById,
            vm.state.value.displayedFieldSnapshot,
            targetTemperatureC = 25.0,
            cdpC = circ.cdp,
            conC = circ.con,
            eonC = exhaust.eon
        )
        assertEquals(expected, published)
    }

    @Test
    fun independentRingGroupsStayOnStableUuidsAndRespectCaps() {
        val fans = (1..6).map { n ->
            Phase3D2AFixtures.instance(
                EquipmentType.CIRCULATION_FAN, "fan-$n", n, xM = 1.0, yM = 1.0 + n
            )
        }
        val runtime = fans.associate { it.id to EquipmentRuntimeState.ON }
        val snapshot = liveSnapshot(fans, runtime)
        val frame = CirculationEffectPlanner.plan(true, fans, runtime, snapshot, thresholds, 25.0)
        assertTrue(frame.groups.size <= CirculationRingVisual.MAX_RING_GROUPS)
        assertTrue(frame.ringObjectCount <= CirculationRingVisual.MAX_TOTAL_RINGS)
        frame.groups.forEach { group ->
            assertTrue(group.ringCount in 1..CirculationRingVisual.MAX_RINGS_PER_FAN)
        }
        assertEquals(CirculationRingVisual.MAX_RINGS_PER_FAN, frame.groups.first().ringCount)
        val again = CirculationEffectPlanner.plan(true, fans, runtime, snapshot, thresholds, 25.0)
        assertEquals(frame.groups.map { it.equipmentUuid }, again.groups.map { it.equipmentUuid })
        assertEquals(frame.ringObjectCount, again.ringObjectCount)
        assertEquals(
            CirculationRingVisual.RENDER_METHOD,
            "pooled GeometryNode annulus"
        )
        assertFalse(CirculationRingSceneMath.APPLIES_TABLETOP_SCALE_TO_CHILDREN)
        val host = ArCirculationRingHost()
        host.bind(ArCirculationRingHost.request(frame, ArTabletopPhase.PLACED))
        assertEquals(frame.groups.map { it.equipmentUuid }, host.attachedUuids)
    }

    @Test
    fun stopOffDeleteAndRepositionClearLabelsWithoutMovingUuids() = runTest {
        val vm = demoVm("cfg-probe-lifecycle")
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.CIRCULATION_FAN)
        vm.duplicateSelected()
        vm.setSimulatedTemperature(30.0)
        vm.setSimulatedHumidity(60.0)
        vm.startBehavior()
        val ids = vm.state.value.instances.map { it.id }
        assertEquals(2, ids.size)
        vm.select(ids[0])
        assertEquals(ids[0], vm.circulationProbeMarkers().firstOrNull()?.equipmentUuid)
        val frameBoth = vm.circulationEffectFrame()
        assertEquals(ids.toSet(), frameBoth.groups.map { it.equipmentUuid }.toSet())

        vm.select(ids[1])
        assertTrue(vm.circulationProbeMarkers().isNotEmpty())
        assertTrue(vm.circulationProbeMarkers().all { it.equipmentUuid == ids[1] })

        val remaining = ids[1]
        vm.select(ids[0])
        vm.requestDelete()
        vm.deleteSelected()
        assertNull(vm.circulationEffectFrame().group(ids[0]))
        assertNotNull(vm.circulationEffectFrame().group(remaining))
        vm.select(remaining)
        assertTrue(vm.circulationProbeMarkers().all { it.equipmentUuid == remaining })

        vm.onArReposition()
        assertTrue(vm.state.value.behaviorRunning)
        vm.stopBehavior()
        assertTrue(vm.circulationEffectFrame().isEmpty)
        assertTrue(vm.circulationProbeMarkers().isEmpty())
        assertEquals(remaining, vm.state.value.instances.single().id)
        assertNotNull(vm.state.value.displayedTemperatureC)
        assertNotNull(vm.state.value.simulatedTemperatureC)
    }

    @Test
    fun blendedColourStaysOnApprovedBoundaries() {
        val atTarget = TemperatureLegendClassifier.blendedColor(25.0, 25.0, circ.cdp, circ.con, exhaust.eon)
        assertEquals(VisualizationRgb.LIGHT_BLUE, atTarget)
        val uniformA = TemperatureLegendClassifier.blendedColor(30.0, 25.0, circ.cdp, circ.con, exhaust.eon)
        val uniformB = TemperatureLegendClassifier.blendedColor(30.0, 25.0, circ.cdp, circ.con, exhaust.eon)
        assertEquals(uniformA, uniformB)
        val below = TemperatureLegendClassifier.blendedColor(circ.cdp - 1.0, 25.0, circ.cdp, circ.con, exhaust.eon)
        assertEquals(VisualizationRgb.PURPLE_DARK_BLUE, below)
        val aboveEon = TemperatureLegendClassifier.blendedColor(exhaust.eon + 2.0, 25.0, circ.cdp, circ.con, exhaust.eon)
        assertEquals(VisualizationRgb.RED, aboveEon)
        val mid = TemperatureLegendClassifier.blendedColor(
            (circ.con + exhaust.eon) / 2.0, 25.0, circ.cdp, circ.con, exhaust.eon
        )
        assertFalse(mid.isUnavailable)
        assertNotEquals(VisualizationRgb.PURPLE_DARK_BLUE, mid)
    }

    @Test
    fun ringsDoNotRotateLikeGearsAndExpandWithDistance() {
        val start = CirculationRingAnimator.expandScale(0.0)
        val end = CirculationRingAnimator.expandScale(1.0)
        assertTrue(end > start)
        assertEquals(CirculationRingVisual.EXPAND_START.toFloat(), start, 0.0f)
        assertEquals(CirculationRingVisual.EXPAND_END.toFloat(), end, 0.0f)
        assertTrue(CirculationRingAnimator.fadeAtProgress(1.0) < CirculationRingAnimator.fadeAtProgress(0.1))
        assertEquals(7, CirculationRingVisual.MAX_RINGS_PER_FAN)
        assertEquals(24, CirculationRingVisual.MAX_TOTAL_RINGS)
        assertTrue(CirculationRingVisual.TUBE_M < 0.04)
        assertTrue(CirculationRingVisual.SEGMENT_COUNT >= 32)
        assertEquals(CirculationRingAppearance.STYLE_NAME, "glass-visible annulus")
        assertEquals(CirculationRingVisual.RENDER_METHOD, "pooled GeometryNode annulus")
        assertTrue(CirculationRingAppearance.MATERIAL_ALPHA >= 0.62f)
        assertTrue(CirculationRingAppearance.MATERIAL_ALPHA <= 0.88f)
        assertTrue(CirculationRingAppearance.TUBE_M >= 0.028)
        assertTrue(CirculationRingAppearance.TUBE_M < 0.04)
        assertTrue(CirculationRingAppearance.DOUBLE_SIDED)
        assertFalse(CirculationRingAppearance.CULLING)
        val classified = TemperatureLegendClassifier.blendedColor(30.0, 25.0, circ.cdp, circ.con, exhaust.eon)
        val selected = CirculationRingAppearance.emphasize(classified, true)
        val unselected = CirculationRingAppearance.emphasize(classified, false)
        assertEquals(classified, unselected)
        assertEquals(classified.red > 0f, selected.red >= classified.red)
        assertNotEquals(TemperatureLegendClassifier.band(30.0, 25.0, circ.cdp, circ.con, exhaust.eon), TemperatureLegendBand.UNAVAILABLE)
        assertEquals(
            TemperatureLegendClassifier.band(30.0, 25.0, circ.cdp, circ.con, exhaust.eon),
            TemperatureLegendClassifier.band(30.0, 25.0, circ.cdp, circ.con, exhaust.eon)
        )
    }

    private fun physicsSteps(fps: Int, elapsedS: Double): Int {
        val scheduler = DemoPhysicsScheduler()
        return acceptedSteps(scheduler, fps, elapsedS)
    }

    private fun displaySteps(fps: Int, elapsedS: Double): Int {
        val scheduler = DemoPhysicsScheduler(
            DemoPhysicsSchedulerConfig(
                publicationIntervalS = DemoDisplayPublication.INTERVAL_S,
                pauseResetThresholdS = DemoDisplayPublication.PAUSE_RESET_THRESHOLD_S
            )
        )
        return acceptedSteps(scheduler, fps, elapsedS)
    }

    private fun acceptedSteps(scheduler: DemoPhysicsScheduler, fps: Int, elapsedS: Double): Int {
        val dt = 1.0 / fps.toDouble()
        val frames = kotlin.math.round(elapsedS / dt).toInt()
        var steps = 0
        repeat(frames) { steps += scheduler.acceptFrame(dt).stepCount }
        return steps
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

    private fun demoVm(configId: String): EquipmentPlacementViewModel {
        val vm = EquipmentPlacementViewModel(InMemoryEquipmentLayoutRepository())
        attach(vm, configId)
        return vm
    }

    private fun attach(vm: EquipmentPlacementViewModel, configId: String = "cfg-display") {
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
    }
}
