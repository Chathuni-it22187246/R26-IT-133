package com.greenhands.app.heat.visualization

import com.greenhands.app.ar.domain.ArTabletopPhase
import com.greenhands.app.ar.render.ArCirculationRingHost
import com.greenhands.app.equipment.data.InMemoryEquipmentLayoutRepository
import com.greenhands.app.equipment.domain.PlacementContext
import com.greenhands.app.equipment.domain.PlacementSuggester
import com.greenhands.app.equipment.model.DomainPoint
import com.greenhands.app.equipment.model.EquipmentInstance
import com.greenhands.app.equipment.model.EquipmentRuntimeState
import com.greenhands.app.equipment.model.EquipmentType
import com.greenhands.app.equipment.model.SelectedEquipmentTool
import com.greenhands.app.equipment.ui.EquipmentPlacementViewModel
import com.greenhands.app.equipment.ui.OverlayClimate
import com.greenhands.app.heat.domain.HeatFormulas
import com.greenhands.app.heat.runtime.EquipmentDistanceProbes
import com.greenhands.app.heat.runtime.PhysicsFieldRuntime
import com.greenhands.app.heat.runtime.Phase3D2AFixtures
import com.greenhands.app.heat.runtime.adapter.EquipmentSpecificationResolver
import com.greenhands.app.heat.runtime.adapter.UserPhysicsCoordinateAdapter
import com.greenhands.app.heat.runtime.adapter.UuidEquipmentReadiness
import com.greenhands.app.heat.runtime.contracts.FieldValidationStatus
import com.greenhands.app.heat.runtime.contracts.InsideClimateSource
import com.greenhands.app.heat.runtime.contracts.PhysicsFieldSnapshot
import com.greenhands.app.heat.runtime.contracts.WeatherBoundary
import com.greenhands.app.heat.runtime.contracts.WeatherBoundaryKind
import com.greenhands.app.heat.runtime.contracts.WeatherBoundaryProvenance
import com.greenhands.app.heat.runtime.contracts.WeatherBoundaryStatus
import com.greenhands.app.heat.runtime.contracts.toSnapshot
import com.greenhands.app.heat.simulation.model.GridState
import com.greenhands.app.heat.simulation.model.Vec3
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
class CirculationRingPhysicalCorrectionTest {

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
    fun tapCreatedFanReceivesDemoReadinessAndRings() = runTest {
        val vm = demoVm("cfg-tap-rings")
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.CIRCULATION_FAN)
        val fan = vm.state.value.instances.single()
        assertFalse(fan.fromSuggestion)
        vm.setSimulatedTemperature(30.0)
        vm.setSimulatedHumidity(60.0)
        vm.startBehavior()
        val snap = vm.state.value.physicsSnapshot!!
        val ready = snap.equipment.single { it.equipmentUuid == fan.id }
        assertTrue(ready.quantitativeReady)
        val frame = vm.circulationEffectFrame()
        assertEquals(listOf(fan.id), frame.groups.map { it.equipmentUuid })
        assertEquals(0.0, frame.groups.single().minDistanceM, 0.0)
        assertEquals(fan.yawDeg, frame.groups.single().yawDeg, 0.0)
        assertEquals(fan.positionM, frame.groups.single().originM)
    }

    @Test
    fun dragCreatedFanReceivesDemoReadinessAndRings() = runTest {
        val vm = demoVm("cfg-drag-rings")
        vm.enterPlacementMode()
        vm.beginPaletteDrag(EquipmentType.CIRCULATION_FAN)
        vm.paletteDragTo(EquipmentType.CIRCULATION_FAN, DomainPoint(2.4, 4.2, 1.6))
        vm.endPaletteDrag(overViewport = true)
        val fan = vm.state.value.instances.single()
        assertFalse(fan.isGhost)
        assertFalse(fan.fromSuggestion)
        vm.setSimulatedTemperature(30.0)
        vm.setSimulatedHumidity(60.0)
        vm.startBehavior()
        assertTrue(
            vm.state.value.physicsSnapshot!!.equipment.single { it.equipmentUuid == fan.id }.quantitativeReady
        )
        val frame = vm.circulationEffectFrame()
        assertEquals(setOf(fan.id), frame.groups.map { it.equipmentUuid }.toSet())
        assertEquals(fan.positionM, frame.groups.single().originM)
        assertEquals(fan.yawDeg, frame.groups.single().yawDeg, 0.0)
    }

    @Test
    fun applyLayoutTwoOnFansCreateTwoUuidGroups() = runTest {
        val vm = demoVm("cfg-apply-two")
        vm.enterPlacementMode()
        vm.applySuggestionLayout(EquipmentType.CIRCULATION_FAN)
        val fans = vm.state.value.instances.filter { it.type == EquipmentType.CIRCULATION_FAN && !it.isGhost }
        assertEquals(2, fans.size)
        assertEquals(2, fans.map { it.id }.toSet().size)
        vm.setSimulatedTemperature(30.0)
        vm.setSimulatedHumidity(60.0)
        vm.startBehavior()
        val frame = vm.circulationEffectFrame()
        assertEquals(fans.map { it.id }.toSet(), frame.groups.map { it.equipmentUuid }.toSet())
        val a = frame.group(fans[0].id)!!
        val b = frame.group(fans[1].id)!!
        assertNotEquals(a.originM, b.originM)
        assertNotEquals(a.yawDeg, b.yawDeg)
        assertNotEquals(a.animationPhase, b.animationPhase)
        assertEquals(fans[0].yawDeg, a.yawDeg, 0.0)
        assertEquals(fans[1].yawDeg, b.yawDeg, 0.0)
    }

    @Test
    fun applyLayoutLoopPosesStayIndependentInPlanner() {
        val context = PlacementContext(
            DemoGreenhouseProfile.LENGTH_M,
            DemoGreenhouseProfile.WIDTH_M,
            DemoGreenhouseProfile.HEIGHT_M,
            DemoGreenhouseProfile.CROP_ROW_COUNT,
            DemoGreenhouseProfile.HEIGHT_M * 0.26
        )
        val generated = PlacementSuggester.circulationLoop(context, 2) { _, index -> "layout-$index" }
        val runtime = generated.associate { it.id to EquipmentRuntimeState.ON }
        val snapshot = liveSnapshot(generated, runtime)
        val frame = CirculationEffectPlanner.plan(true, generated, runtime, snapshot, thresholds, 25.0)
        assertEquals(setOf("layout-0", "layout-1"), frame.groups.map { it.equipmentUuid }.toSet())
        assertNotEquals(frame.group("layout-0")!!.originM, frame.group("layout-1")!!.originM)
        assertEquals(generated[0].yawDeg, frame.group("layout-0")!!.yawDeg, 0.0)
        assertEquals(generated[1].yawDeg, frame.group("layout-1")!!.yawDeg, 0.0)
    }

    @Test
    fun duplicateCreatesIndependentUuidGroup() = runTest {
        val vm = demoVm("cfg-dup-group")
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.CIRCULATION_FAN)
        vm.duplicateSelected()
        val ids = vm.state.value.instances.map { it.id }
        assertEquals(2, ids.toSet().size)
        vm.setSimulatedTemperature(30.0)
        vm.setSimulatedHumidity(60.0)
        vm.startBehavior()
        val frame = vm.circulationEffectFrame()
        assertEquals(ids.toSet(), frame.groups.map { it.equipmentUuid }.toSet())
        assertNotEquals(frame.group(ids[0])!!.originM, frame.group(ids[1])!!.originM)
        assertNotEquals(
            CirculationRingAnimator.phaseOffset(ids[0]),
            CirculationRingAnimator.phaseOffset(ids[1])
        )
    }

    @Test
    fun rendererMapCannotOverwriteAnotherUuid() {
        val fanA = Phase3D2AFixtures.instance(EquipmentType.CIRCULATION_FAN, "uuid-a", 1, xM = 1.0, yM = 2.0)
        val fanB = Phase3D2AFixtures.instance(EquipmentType.CIRCULATION_FAN, "uuid-b", 2, xM = 3.5, yM = 8.0, yawDeg = 90.0)
        val runtime = mapOf(fanA.id to EquipmentRuntimeState.ON, fanB.id to EquipmentRuntimeState.ON)
        val snapshot = liveSnapshot(listOf(fanA, fanB), runtime)
        val frame = CirculationEffectPlanner.plan(true, listOf(fanA, fanB), runtime, snapshot, thresholds, 25.0)
        val host = ArCirculationRingHost()
        host.bind(ArCirculationRingHost.request(frame, ArTabletopPhase.PLACED))
        assertEquals(setOf("uuid-a", "uuid-b"), host.attachedUuids.toSet())
        assertEquals(fanA.positionM, host.originByUuid["uuid-a"])
        assertEquals(fanB.positionM, host.originByUuid["uuid-b"])
        assertEquals(fanA.yawDeg, host.yawByUuid["uuid-a"])
        assertEquals(fanB.yawDeg, host.yawByUuid["uuid-b"])
        val reversed = CirculationEffectFrame(groups = frame.groups.reversed(), snapshot = snapshot)
        host.bind(ArCirculationRingHost.request(reversed, ArTabletopPhase.PLACED))
        assertEquals(fanA.positionM, host.originByUuid["uuid-a"])
        assertEquals(fanB.positionM, host.originByUuid["uuid-b"])
        assertEquals(2, host.originByUuid.size)
    }

    @Test
    fun nearWallFanKeepsOutletRingsWhenLaterProbesLeave() {
        val fan = Phase3D2AFixtures.instance(
            EquipmentType.CIRCULATION_FAN, "wall-1", 1, xM = 4.7, yM = 5.0, zM = 1.5, yawDeg = 0.0
        )
        val runtime = mapOf(fan.id to EquipmentRuntimeState.ON)
        val snapshot = liveSnapshot(listOf(fan), runtime)
        val outsideCount = snapshot.probes.count {
            it.equipmentUuid == fan.id &&
                it.availability == com.greenhands.app.heat.runtime.contracts.ProbeAvailability.OUTSIDE_GREENHOUSE
        }
        assertTrue(outsideCount >= 4)
        val frame = CirculationEffectPlanner.plan(true, listOf(fan), runtime, snapshot, thresholds, 25.0)
        val group = frame.groups.single()
        assertEquals("wall-1", group.equipmentUuid)
        assertEquals(0.0, group.minDistanceM, 0.0)
        assertTrue(group.maxDistanceM < 1.0)
        assertTrue(CirculationAirflowEnvelope.contains(snapshot, group.domainPointAt(0.0)))
        assertFalse(CirculationAirflowEnvelope.contains(snapshot, fan.positionM.copy(xM = fan.positionM.xM + 1.0)))
    }

    @Test
    fun tableModeAndLabelsDoNotSuppressRings() = runTest {
        val vm = demoVm("cfg-table-labels")
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.CIRCULATION_FAN)
        vm.setSimulatedTemperature(30.0)
        vm.setSimulatedHumidity(60.0)
        vm.startBehavior()
        val withLabels = vm.circulationEffectFrame()
        assertFalse(withLabels.isEmpty)
        vm.toggleLabelsVisible()
        assertFalse(vm.state.value.labelsVisible)
        assertTrue(vm.circulationProbeMarkers().isEmpty())
        assertEquals(withLabels.groups.map { it.equipmentUuid }, vm.circulationEffectFrame().groups.map { it.equipmentUuid })
        vm.setSelectedTool(SelectedEquipmentTool.EXACT)
        assertFalse(vm.circulationEffectFrame().isEmpty)
        vm.select(null)
        assertTrue(vm.circulationProbeMarkers().isEmpty())
        assertFalse(vm.circulationEffectFrame().isEmpty)
    }

    @Test
    fun hostChromeAndSelectionDoNotDisposeRingGroups() {
        val fanA = Phase3D2AFixtures.instance(EquipmentType.CIRCULATION_FAN, "uuid-a", 1, xM = 1.0, yM = 2.0)
        val fanB = Phase3D2AFixtures.instance(EquipmentType.CIRCULATION_FAN, "uuid-b", 2, xM = 3.5, yM = 8.0, yawDeg = 90.0)
        val runtime = mapOf(fanA.id to EquipmentRuntimeState.ON, fanB.id to EquipmentRuntimeState.ON)
        val snapshot = liveSnapshot(listOf(fanA, fanB), runtime)
        val frame = CirculationEffectPlanner.plan(true, listOf(fanA, fanB), runtime, snapshot, thresholds, 25.0)
        val host = ArCirculationRingHost()
        host.bind(ArCirculationRingHost.request(frame, ArTabletopPhase.PLACED))
        val epoch = host.resourceEpoch
        val attached = host.attachedUuids
        host.noteRecomposition()
        host.uiChromeChanged()
        host.bind(ArCirculationRingHost.request(frame, ArTabletopPhase.PLACED))
        assertEquals(epoch, host.resourceEpoch)
        assertEquals(attached, host.attachedUuids)
        assertEquals(setOf("uuid-a", "uuid-b"), host.attachedUuids.toSet())
        val selectedSameFrame = CirculationEffectPlanner.plan(true, listOf(fanA, fanB), runtime, snapshot, thresholds, 25.0)
        assertEquals(frame.groups.map { it.equipmentUuid }.toSet(), selectedSameFrame.groups.map { it.equipmentUuid }.toSet())
    }

    @Test
    fun compactProbeMarkersKeepIndependentClassification() {
        val fan = Phase3D2AFixtures.instance(
            EquipmentType.CIRCULATION_FAN, "yaw0", 1, xM = 1.0, yM = 5.0, yawDeg = 0.0
        )
        val runtime = mapOf(fan.id to EquipmentRuntimeState.ON)
        val snapshot = liveSnapshot(listOf(fan), runtime)
        val markers = CirculationProbeLabelPlanner.plan(
            visualizationRunning = true,
            labelsVisible = true,
            selectedId = fan.id,
            instances = listOf(fan),
            runtimeById = runtime,
            snapshot = snapshot,
            targetTemperatureC = 25.0,
            cdpC = circ.cdp,
            conC = circ.con,
            eonC = exhaust.eon
        )
        assertTrue(markers.isNotEmpty())
        markers.forEach { marker ->
            assertEquals("${marker.distanceM.toInt()} m", marker.compactDistanceText)
            assertFalse(marker.compactDistanceText.contains("°C"))
            assertTrue(marker.predictedTemperatureText.contains("°C"))
            assertTrue(marker.text.contains(marker.compactDistanceText))
            assertNotEquals(TemperatureLegendBand.UNAVAILABLE, marker.band)
            assertEquals(VisualizationCopy.compactBandLabel(marker.band), marker.classificationLabel)
            assertEquals(
                TemperatureLegendClassifier.band(
                    marker.temperatureC, 25.0, circ.cdp, circ.con, exhaust.eon
                ),
                marker.band
            )
        }
        assertFalse(CirculationProbeLabelPlanner.controlAvailable(true, false, fan.id, listOf(fan), runtime))
        assertTrue(CirculationProbeLabelPlanner.controlAvailable(true, true, fan.id, listOf(fan), runtime))
    }

    @Test
    fun selectionMovesProbeLabelsToSelectedUuid() = runTest {
        val vm = demoVm("cfg-select-probes")
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.CIRCULATION_FAN)
        vm.duplicateSelected()
        vm.setSimulatedTemperature(30.0)
        vm.setSimulatedHumidity(60.0)
        vm.startBehavior()
        val ids = vm.state.value.instances.map { it.id }
        vm.select(ids[0])
        assertTrue(vm.circulationProbeMarkers().all { it.equipmentUuid == ids[0] })
        vm.select(ids[1])
        assertTrue(vm.circulationProbeMarkers().isNotEmpty())
        assertTrue(vm.circulationProbeMarkers().all { it.equipmentUuid == ids[1] })
    }

    @Test
    fun exactPositionAndRotateNinetyUseStoredPose() = runTest {
        val vm = demoVm("cfg-exact-yaw")
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.CIRCULATION_FAN)
        vm.applyNumericEdit("2.20", "4.10", "1.50", "90")
        val fan = vm.state.value.instances.single()
        assertEquals(2.20, fan.positionM.xM, 1e-9)
        assertEquals(4.10, fan.positionM.yM, 1e-9)
        assertEquals(90.0, fan.yawDeg, 0.0)
        vm.setSimulatedTemperature(30.0)
        vm.setSimulatedHumidity(60.0)
        vm.startBehavior()
        val group = vm.circulationEffectFrame().groups.single()
        assertEquals(fan.id, group.equipmentUuid)
        assertEquals(fan.positionM, group.originM)
        assertEquals(90.0, group.yawDeg, 0.0)
        assertEquals(0.0, group.forwardDomainDx, 1e-12)
        assertEquals(1.0, group.forwardDomainDy, 1e-12)
        vm.rotateSelected(90.0)
        val rotated = vm.circulationEffectFrame().groups.single()
        assertEquals(180.0, rotated.yawDeg, 0.0)
        assertEquals(vm.state.value.instances.single().yawDeg, rotated.yawDeg, 0.0)
    }

    @Test
    fun uniformFieldProbeValuesEqualSampleNotPoisonedMean() {
        val fan = Phase3D2AFixtures.instance(
            EquipmentType.CIRCULATION_FAN, "uni-1", 1, xM = 2.5, yM = 2.0, zM = 1.5, yawDeg = 90.0
        )
        val runtime = mapOf(fan.id to EquipmentRuntimeState.ON)
        val snapshot = syntheticSnapshot(listOf(fan), runtime, meanOverride = 99.0) { 26.0 }
        val markers = CirculationProbeLabelPlanner.plan(
            true, true, fan.id, listOf(fan), runtime, snapshot
        )
        assertEquals(5, markers.size)
        markers.forEach { marker ->
            assertEquals(26.0, marker.temperatureC, 1e-6)
            assertNotEquals(snapshot.meanTemperatureC, marker.temperatureC, 0.0)
            assertFalse(marker.text.contains("99"))
        }
        assertEquals(markers.first().temperatureC, markers.last().temperatureC, 0.0)
        val engine = PhysicsFieldRuntime()
        val request = Phase3D2AFixtures.demoRequest(
            listOf(fan),
            30.0,
            60.0,
            weather = WeatherBoundary.demoSimulated(25.0, 70.0),
            lengthM = DemoGreenhouseProfile.LENGTH_M,
            widthM = DemoGreenhouseProfile.WIDTH_M,
            heightM = DemoGreenhouseProfile.HEIGHT_M,
            runtimeById = runtime
        )
        val mean0 = engine.start(request).meanTemperatureC
        repeat(5) { engine.advance(1.0) }
        assertEquals(mean0, engine.snapshot()!!.meanTemperatureC, 5e-3)
        engine.stop()
    }

    @Test
    fun knownGradientProbesUseOwnCoordinatesNotTheMean() {
        val fan = Phase3D2AFixtures.instance(
            EquipmentType.CIRCULATION_FAN, "grad-1", 1, xM = 2.5, yM = 2.0, zM = 1.5, yawDeg = 90.0
        )
        val runtime = mapOf(fan.id to EquipmentRuntimeState.ON)
        val snapshot = syntheticSnapshot(listOf(fan), runtime, meanOverride = 40.0) { cell ->
            20.0 + cell.xM
        }
        val markers = CirculationProbeLabelPlanner.plan(
            true, true, fan.id, listOf(fan), runtime, snapshot
        )
        assertEquals(5, markers.size)
        val origin = UserPhysicsCoordinateAdapter.positionToPhysics(fan.positionM)
        val forward = UserPhysicsCoordinateAdapter.yawToPhysicsForward(fan.yawDeg)
        markers.forEach { marker ->
            val physics = origin.plus(forward.times(marker.distanceM))
            val expected = 20.0 + physics.xM
            assertEquals(expected, marker.temperatureC, 1e-6)
            assertNotEquals(snapshot.meanTemperatureC, marker.temperatureC, 1e-6)
            val resampled = CirculationAirflowEnvelope.predictedTemperatureC(snapshot, marker.domain)!!
            assertEquals(expected, resampled, 1e-6)
        }
        val distinct = markers.map { it.temperatureC }.toSet()
        assertEquals(5, distinct.size)
        val group = CirculationEffectPlanner.plan(
            true, listOf(fan), runtime, snapshot, thresholds, 25.0
        ).groups.single()
        val near = CirculationRingAnimator.temperatureAtDistance(group, 1.0, snapshot)!!
        val far = CirculationRingAnimator.temperatureAtDistance(group, 4.0, snapshot)!!
        assertEquals(20.0 + origin.xM + 1.0, near, 1e-6)
        assertEquals(20.0 + origin.xM + 4.0, far, 1e-6)
        val nearColor = CirculationRingAnimator.colorAtDistance(group, 1.0, snapshot)
        val farColor = CirculationRingAnimator.colorAtDistance(group, 4.0, snapshot)
        assertNotEquals(nearColor, farColor)
        assertNotEquals(
            TemperatureLegendClassifier.blendedColor(40.0, 25.0, circ.cdp, circ.con, exhaust.eon),
            nearColor
        )
    }

    @Test
    fun changingAcceptedFieldPublishesAtFiveTenFifteenSeconds() = runTest {
        val vm = demoVm("cfg-display-15")
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.CIRCULATION_FAN)
        vm.tapPalette(EquipmentType.EXHAUST_FAN)
        vm.setSimulatedTemperature(32.0)
        vm.setSimulatedHumidity(60.0)
        vm.startBehavior()
        val fanId = vm.state.value.instances.first { it.type == EquipmentType.CIRCULATION_FAN }.id
        vm.select(fanId)
        val initialDisplayed = vm.state.value.displayedTemperatureC!!
        val initialMean = vm.state.value.physicsSnapshot!!.meanTemperatureC
        assertEquals(0.0, vm.state.value.physicsSnapshot!!.elapsedTimeS, 0.0)
        assertEquals(initialMean, initialDisplayed, 0.0)
        val dt = 1.0 / 60.0
        repeat(60) { vm.advanceVisualization(dt) }
        assertEquals(1.0, vm.state.value.physicsSnapshot!!.elapsedTimeS, 1e-9)
        assertEquals(initialDisplayed, vm.state.value.displayedTemperatureC!!, 0.0)
        repeat(240) { vm.advanceVisualization(dt) }
        assertEquals(5.0, vm.state.value.physicsSnapshot!!.elapsedTimeS, 1e-9)
        assertEquals(
            vm.state.value.physicsSnapshot!!.referenceTemperatureC,
            vm.state.value.displayedTemperatureC!!,
            0.0
        )
        assertTrue(vm.state.value.displayedFieldSnapshot === vm.state.value.physicsSnapshot)
        val at5 = vm.state.value.displayedTemperatureC!!
        val probes5 = vm.circulationProbeMarkers()
        repeat(300) { vm.advanceVisualization(dt) }
        assertEquals(10.0, vm.state.value.physicsSnapshot!!.elapsedTimeS, 1e-9)
        assertEquals(
            vm.state.value.physicsSnapshot!!.referenceTemperatureC,
            vm.state.value.displayedTemperatureC!!,
            0.0
        )
        val at10 = vm.state.value.displayedTemperatureC!!
        repeat(300) { vm.advanceVisualization(dt) }
        assertEquals(15.0, vm.state.value.physicsSnapshot!!.elapsedTimeS, 1e-9)
        assertEquals(
            vm.state.value.physicsSnapshot!!.referenceTemperatureC,
            vm.state.value.displayedTemperatureC!!,
            0.0
        )
        val at15 = vm.state.value.displayedTemperatureC!!
        assertNotEquals(initialMean, vm.state.value.physicsSnapshot!!.meanTemperatureC, 1e-6)
        if (at5 == at10) {
            assertEquals(vm.state.value.physicsSnapshot!!.referenceTemperatureC, at15, 0.0)
        } else {
            assertEquals(vm.state.value.physicsSnapshot!!.referenceTemperatureC, at15, 0.0)
        }
        val publishedUnchanged = at5 == initialDisplayed
        if (publishedUnchanged) {
            assertEquals(initialDisplayed, at5, 0.0)
        }
        assertEquals(probes5.map { it.distanceM }, vm.circulationProbeMarkers().map { it.distanceM })
        assertEquals(5.0, DemoDisplayPublication.INTERVAL_S, 0.0)
    }

    @Test
    fun offDeleteStopAndRepositionCleanTheCorrectUuid() = runTest {
        val vm = demoVm("cfg-clean-uuid")
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.CIRCULATION_FAN)
        vm.duplicateSelected()
        vm.setSimulatedTemperature(30.0)
        vm.setSimulatedHumidity(60.0)
        vm.startBehavior()
        val ids = vm.state.value.instances.map { it.id }
        val first = ids[0]
        val second = ids[1]
        val snapshot = vm.state.value.physicsSnapshot
        val offFirst = CirculationEffectPlanner.plan(
            true,
            vm.state.value.instances,
            mapOf(first to EquipmentRuntimeState.OFF, second to EquipmentRuntimeState.ON),
            snapshot,
            thresholds,
            25.0
        )
        assertNull(offFirst.group(first))
        assertNotNull(offFirst.group(second))
        assertEquals(second, offFirst.group(second)!!.equipmentUuid)
        vm.select(first)
        vm.requestDelete()
        vm.deleteSelected()
        assertNull(vm.circulationEffectFrame().group(first))
        assertNotNull(vm.circulationEffectFrame().group(second))
        assertEquals(second, vm.state.value.instances.single().id)
        vm.onArReposition()
        assertTrue(vm.state.value.behaviorRunning)
        assertNotNull(vm.circulationEffectFrame().group(second))
        vm.stopBehavior()
        assertTrue(vm.circulationEffectFrame().isEmpty)
        assertTrue(vm.circulationProbeMarkers().isEmpty())
        assertEquals(second, vm.state.value.instances.single().id)
        assertNotNull(vm.state.value.displayedTemperatureC)
    }

    @Test
    fun currentGlobalRenderCapsRemainEnforced() {
        val fans = (1..6).map { n ->
            Phase3D2AFixtures.instance(EquipmentType.CIRCULATION_FAN, "cap-$n", n, xM = 1.0, yM = 1.0 + n)
        }
        val runtime = fans.associate { it.id to EquipmentRuntimeState.ON }
        val snapshot = liveSnapshot(fans, runtime)
        val frame = CirculationEffectPlanner.plan(true, fans, runtime, snapshot, thresholds, 25.0)
        assertTrue(frame.groups.size <= CirculationRingVisual.MAX_RING_GROUPS)
        assertTrue(frame.ringObjectCount <= CirculationRingVisual.MAX_TOTAL_RINGS)
        frame.groups.forEach { group ->
            assertTrue(group.ringCount <= CirculationRingVisual.MAX_RINGS_PER_FAN)
            assertEquals(0.0, group.minDistanceM, 0.0)
        }
        assertEquals(7, CirculationRingVisual.MAX_RINGS_PER_FAN)
        assertEquals(4, CirculationRingVisual.MAX_RING_GROUPS)
        assertEquals(24, CirculationRingVisual.MAX_TOTAL_RINGS)
    }

    @Test
    fun compactChipIsNotTheLongViewportCaption() {
        assertEquals("Predicted · 5 s", VisualizationCopy.CIRCULATION_PROBE_CHIP)
        assertTrue(VisualizationCopy.CIRCULATION_PROBE_CAPTION.contains("Predicted — uncalibrated"))
        assertTrue(VisualizationCopy.CIRCULATION_PROBE_CAPTION.contains("Demo simulation"))
        assertTrue(VisualizationCopy.CIRCULATION_PROBE_CAPTION.contains("not cooling caused solely by the fan"))
        assertTrue(VisualizationCopy.CIRCULATION_PROBE_CHIP.length < VisualizationCopy.CIRCULATION_PROBE_CAPTION.length)
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

    private fun syntheticSnapshot(
        instances: List<EquipmentInstance>,
        runtime: Map<String, EquipmentRuntimeState>,
        meanOverride: Double? = null,
        fill: (Vec3) -> Double
    ): PhysicsFieldSnapshot {
        val geometry = UserPhysicsCoordinateAdapter.geometry(
            DemoGreenhouseProfile.LENGTH_M,
            DemoGreenhouseProfile.WIDTH_M,
            DemoGreenhouseProfile.HEIGHT_M,
            0.50
        )
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
