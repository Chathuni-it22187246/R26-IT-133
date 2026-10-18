package com.greenhands.app.heat.visualization

import com.greenhands.app.ar.domain.ArTabletopPhase
import com.greenhands.app.ar.render.ArCirculationRingHost
import com.greenhands.app.ar.render.ArEffectHostState
import com.greenhands.app.ar.render.ArExhaustAirflowHost
import com.greenhands.app.ar.render.ArPhysicsEffectCleanup
import com.greenhands.app.equipment.data.InMemoryEquipmentLayoutRepository
import com.greenhands.app.equipment.model.EquipmentRuntimeState
import com.greenhands.app.equipment.model.EquipmentType
import com.greenhands.app.equipment.model.SelectedEquipmentTool
import com.greenhands.app.equipment.ui.EquipmentPlacementViewModel
import com.greenhands.app.equipment.ui.OverlayClimate
import com.greenhands.app.heat.domain.HeatFormulas
import com.greenhands.app.heat.runtime.DemoPhysicsSchedulerConfig
import com.greenhands.app.heat.runtime.FanPadCopy
import com.greenhands.app.heat.runtime.Phase3D2AFixtures
import com.greenhands.app.heat.runtime.PhysicsFieldRuntime
import com.greenhands.app.heat.runtime.PhysicsRuntimeCopy
import com.greenhands.app.heat.runtime.adapter.DemoFanPadProfile
import com.greenhands.app.heat.runtime.adapter.FanPadBoundaryResolver
import com.greenhands.app.heat.runtime.adapter.FanPadIntakeFactory
import com.greenhands.app.heat.runtime.adapter.FanPadLayoutResolver
import com.greenhands.app.heat.runtime.adapter.UserPhysicsCoordinateAdapter
import com.greenhands.app.heat.runtime.contracts.FanPadBoundaryInput
import com.greenhands.app.heat.runtime.contracts.FanPadWallSelection
import com.greenhands.app.heat.runtime.contracts.GreenhouseEndWall
import com.greenhands.app.heat.runtime.contracts.WeatherBoundary
import com.greenhands.app.heat.simulation.TestEquipmentFixtures
import com.greenhands.app.heat.simulation.model.EquipmentDriveMode
import com.greenhands.app.heat.simulation.model.ForcedEquipmentState
import com.greenhands.app.heat.simulation.model.IntakeOpening
import com.greenhands.app.heat.simulation.model.Vec3
import com.greenhands.app.heat.simulation.physics.AirProperties
import com.greenhands.app.heat.simulation.physics.GreenhouseSimulationEngine
import com.greenhands.app.heat.simulation.physics.Psychrometrics
import com.greenhands.app.heat.simulation.probe.DistanceProbeSampler
import com.greenhands.app.preview3d.model.DemoGreenhouseProfile
import com.greenhands.app.preview3d.model.PreviewMode
import kotlin.math.abs
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
class ExhaustValidationTransectCorrectionTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val circ = HeatFormulas.circulation(25.0)
    private val exhaust = HeatFormulas.exhaustFromCirculation(circ)
    private val climate = OverlayClimate(
        crop = null,
        stage = null,
        temperatureLabel = "25.0°C",
        humidityLabel = "70.0 % RH",
        vpdLabel = "Not published",
        circulation = circ,
        exhaust = exhaust,
        fogger = HeatFormulas.fogger(70.0),
        optimalTemperatureC = 25.0,
        optimalHumidityPercent = 70.0
    )
    private val air = AirProperties.chen2014Preview()

    @Before
    fun setMain() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun reset() {
        Dispatchers.resetMain()
        SnapshotFieldSampler.clearCache()
    }

    @Test
    fun officialTransectIsExhaustOnlyAndDoesNotWriteDecay() {
        assertTrue(
            OfficialValidationTransect.DISTANCES_M.contentEquals(DistanceProbeSampler.DEFAULT_DISTANCES_M)
        )
        assertTrue(OfficialValidationTransect.isPublicEquipment(EquipmentType.EXHAUST_FAN))
        assertFalse(OfficialValidationTransect.isPublicEquipment(EquipmentType.CIRCULATION_FAN))
        assertFalse(OfficialValidationTransect.isPublicEquipment(EquipmentType.FOGGER))
        assertFalse(OfficialValidationTransect.SHOWS_CIRCULATION_MARKERS)
        assertFalse(OfficialValidationTransect.SHOWS_FOGGER_MARKERS)
        assertFalse(OfficialValidationTransect.WRITES_DISTANCE_TEMPERATURE_DECAY)
        assertFalse(CirculationProbeLabelPlanner.SHOWS_PUBLIC_VALIDATION_TRANSECT)
        assertFalse(Phase3D2B2A.PUBLIC_CIRCULATION_PROBES)
        assertFalse(Phase3D2B2A.PUBLIC_FOGGER_PROBES)
        assertFalse(Phase3D2B2A.WRITES_RENDERER_DISTANCE_DECAY)
    }

    @Test
    fun circulationSelectionProducesNoPublicProbesAndKeepsDiagnosticRings() = runTest {
        val vm = demoVm("cfg-transect-circ")
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.CIRCULATION_FAN)
        vm.setSimulatedTemperature(30.0)
        vm.setSimulatedHumidity(60.0)
        vm.startBehavior()
        val id = vm.state.value.selectedId
        assertNotNull(id)
        assertTrue(vm.visualizationProbeMarkers().isEmpty())
        assertFalse(vm.visualizationProbeControlAvailable())
        assertTrue(vm.circulationProbeMarkers().isNotEmpty())
        assertFalse(vm.circulationEffectFrame().isEmpty)
        assertEquals(id, vm.circulationEffectFrame().groups.single().equipmentUuid)
        assertEquals(
            VisualizationCopy.PREDICTED_UNCALIBRATED,
            vm.visualizationProbeCaption()
        )
    }

    @Test
    fun circulationRingsRemainAfterSelectionAndOpeningControls() = runTest {
        val vm = demoVm("cfg-transect-circ-controls")
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.CIRCULATION_FAN)
        vm.setSimulatedTemperature(30.0)
        vm.setSimulatedHumidity(60.0)
        vm.startBehavior()
        val host = ArCirculationRingHost(81)
        assertTrue(host.lifecycle.attach(81))
        host.bind(ArCirculationRingHost.request(vm.circulationEffectFrame(), ArTabletopPhase.PLACED))
        val epoch = host.resourceEpoch
        val uuid = vm.state.value.instances.single().id
        vm.select(uuid)
        vm.setSelectedTool(SelectedEquipmentTool.EXACT)
        vm.setShowExactEditor(true)
        host.noteRecomposition()
        host.uiChromeChanged()
        host.bind(ArCirculationRingHost.request(vm.circulationEffectFrame(), ArTabletopPhase.PLACED))
        assertEquals(epoch, host.resourceEpoch)
        assertEquals(listOf(uuid), host.attachedUuids)
        assertFalse(vm.circulationEffectFrame().isEmpty)
        assertTrue(vm.visualizationProbeMarkers().isEmpty())
        assertFalse(vm.visualizationProbeControlAvailable())
    }

    @Test
    fun foggerSelectionProducesNoValidationTransect() = runTest {
        val vm = demoVm("cfg-transect-fog")
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.FOGGER)
        vm.setSimulatedTemperature(35.0)
        vm.setSimulatedHumidity(40.0)
        vm.startBehavior()
        assertEquals(EquipmentType.FOGGER, vm.state.value.selected?.type)
        assertTrue(vm.visualizationProbeMarkers().isEmpty())
        assertFalse(vm.visualizationProbeControlAvailable())
        assertTrue(vm.circulationProbeMarkers().isEmpty())
        assertTrue(vm.exhaustProbeMarkers().isEmpty())
    }

    @Test
    fun exhaustSelectionProducesInwardCombinedFieldSamples() {
        val fan = lengthWallExhaust("ex-main", xM = 2.5)
        val runtime = mapOf(fan.id to EquipmentRuntimeState.ON)
        val snap = fieldSnapshot(listOf(fan), runtime, advanceS = 8.0)
        assertTrue(snap.fanPad.quantitativeAvailable)
        assertFalse(snap.wellMixedVentilation)
        val markers = ExhaustProbeLabelPlanner.plan(
            true, true, fan.id, listOf(fan), runtime, snap, 25.0, circ.cdp, circ.con, exhaust.eon
        )
        assertEquals(listOf(1.0, 2.0, 3.0, 4.0, 5.0), markers.map { it.distanceM })
        assertTrue(markers.all { it.equipmentUuid == fan.id })
        val (inDx, inDy, inDz) = ExhaustAirflowEnvelope.inwardDomain(fan.yawDeg)
        assertEquals(0.0, inDx, 1e-12)
        assertTrue(inDy < 0.0)
        assertEquals(0.0, inDz, 1e-12)
        markers.forEach { marker ->
            assertEquals(fan.positionM.xM + inDx * marker.distanceM, marker.domain.xM, 1e-12)
            assertEquals(fan.positionM.yM + inDy * marker.distanceM, marker.domain.yM, 1e-12)
            val physics = UserPhysicsCoordinateAdapter.positionToPhysics(marker.domain)
            val sampled = SnapshotFieldSampler.temperatureC(snap, physics)!!
            assertEquals(sampled, marker.temperatureC, 1e-9)
        }
        assertFalse(OfficialValidationTransect.WRITES_DISTANCE_TEMPERATURE_DECAY)
    }

    @Test
    fun circulationAndFoggerAlterLaterExhaustSamplesThroughTheSolver() {
        val intake = IntakeOpening("pad", Vec3(0.25, 1.5, 1.0), Vec3(1.0, 0.0, 0.0), 1.2)
        val exhaustFan = TestEquipmentFixtures.testExhaustFan(Vec3(7.5, 1.5, 1.0), 0.30)
        val omegaPad = Psychrometrics.humidityRatioKgKg(22.0, 80.0, air.pressurePa)
        val baseline = transectAfter(
            intakes = listOf(intake),
            exhaustFans = listOf(exhaustFan),
            intakeAirTemperatureC = 22.0,
            intakeAirHumidityRatioKgKg = omegaPad
        )
        val withFogger = transectAfter(
            intakes = listOf(intake),
            exhaustFans = listOf(exhaustFan),
            intakeAirTemperatureC = 22.0,
            intakeAirHumidityRatioKgKg = omegaPad,
            foggers = listOf(TestEquipmentFixtures.testFogger(Vec3(4.5, 1.5, 1.2), 0.02, 0.80))
        )
        val withCirculation = transectAfter(
            intakes = listOf(intake),
            exhaustFans = listOf(exhaustFan),
            intakeAirTemperatureC = 22.0,
            intakeAirHumidityRatioKgKg = omegaPad,
            circulationFans = listOf(
                TestEquipmentFixtures.testCirculationFan(
                    positionM = Vec3(3.0, 1.5, 1.0),
                    direction = Vec3(1.0, 0.0, 0.0),
                    ratedAirflowM3s = 1.20
                )
            ),
            velocityOverride = null
        )
        val circulationBaseline = transectAfter(
            intakes = listOf(intake),
            exhaustFans = listOf(exhaustFan),
            intakeAirTemperatureC = 22.0,
            intakeAirHumidityRatioKgKg = omegaPad,
            velocityOverride = null
        )
        assertEquals(5, baseline.size)
        assertTrue(
            "fogger moisture/latent cooling must change a later exhaust sample",
            baseline.zip(withFogger).any { (a, b) ->
                abs(a.first - b.first) > 1e-4 || abs(a.second - b.second) > 1e-6
            }
        )
        assertTrue(
            "circulation mixing must change a later exhaust sample",
            circulationBaseline.zip(withCirculation).any { (a, b) ->
                abs(a.first - b.first) > 1e-4 || abs(a.second - b.second) > 1e-6
            }
        )
    }

    @Test
    fun distanceFromPadUsesLengthWallAirflowSpan() {
        val fan = lengthWallExhaust("ex-len")
        val runtime = mapOf(fan.id to EquipmentRuntimeState.ON)
        val snap = fieldSnapshot(listOf(fan), runtime, advanceS = 1.0)
        assertEquals(DemoGreenhouseProfile.LENGTH_M, snap.fanPad.airflowSpanM!!, 0.0)
        val markers = ExhaustProbeLabelPlanner.plan(
            true, true, fan.id, listOf(fan), runtime, snap, 25.0, circ.cdp, circ.con, exhaust.eon
        )
        assertEquals(5, markers.size)
        markers.forEach { marker ->
            assertEquals(
                DemoGreenhouseProfile.LENGTH_M - marker.distanceM,
                marker.distanceFromPadM!!,
                1e-12
            )
        }
        assertEquals(
            DemoGreenhouseProfile.LENGTH_M,
            FanPadLayoutResolver.airflowSpanM(
                GreenhouseEndWall.NEAR_Y0,
                DemoGreenhouseProfile.LENGTH_M,
                DemoGreenhouseProfile.WIDTH_M
            ),
            0.0
        )
    }

    @Test
    fun distanceFromPadUsesWidthWallAirflowSpan() {
        val fan = widthWallExhaust("ex-wid")
        val layout = FanPadLayoutResolver.resolve(
            listOf(fan),
            DemoGreenhouseProfile.LENGTH_M,
            DemoGreenhouseProfile.WIDTH_M,
            FanPadWallSelection.AUTO
        )
        assertEquals(GreenhouseEndWall.FAR_X_WIDTH, layout.commonWall)
        assertEquals(GreenhouseEndWall.NEAR_X0, layout.padWall)
        assertFalse(layout.conflict)
        assertEquals(
            DemoGreenhouseProfile.WIDTH_M,
            FanPadLayoutResolver.airflowSpanM(
                layout.padWall!!,
                DemoGreenhouseProfile.LENGTH_M,
                DemoGreenhouseProfile.WIDTH_M
            ),
            0.0
        )
        val openings = FanPadIntakeFactory.openings(
            DemoGreenhouseProfile.LENGTH_M,
            DemoGreenhouseProfile.WIDTH_M,
            DemoGreenhouseProfile.HEIGHT_M,
            0.50,
            GreenhouseEndWall.NEAR_X0,
            4.0,
            2.5
        )
        assertTrue(openings.isNotEmpty())
        assertTrue(openings.all { it.positionM.yM < 1.0 })
        assertTrue(PadInletEffectPlanner.inletTravelsPadTowardExhaust(GreenhouseEndWall.NEAR_X0))
        assertTrue(PadInletEffectPlanner.inletTravelsPadTowardExhaust(GreenhouseEndWall.FAR_X_WIDTH))
        val runtime = mapOf(fan.id to EquipmentRuntimeState.ON)
        val snap = fieldSnapshot(listOf(fan), runtime, advanceS = 1.0)
        assertEquals(DemoGreenhouseProfile.WIDTH_M, snap.fanPad.airflowSpanM!!, 0.0)
        val markers = ExhaustProbeLabelPlanner.plan(
            true, true, fan.id, listOf(fan), runtime, snap, 25.0, circ.cdp, circ.con, exhaust.eon
        )
        assertEquals(5, markers.size)
        val (inDx, inDy, _) = ExhaustAirflowEnvelope.inwardDomain(fan.yawDeg)
        assertTrue(inDx < 0.0)
        assertEquals(0.0, inDy, 1e-12)
        markers.forEach { marker ->
            assertEquals(fan.positionM.xM + inDx * marker.distanceM, marker.domain.xM, 1e-12)
            assertEquals(fan.positionM.yM, marker.domain.yM, 1e-12)
            assertEquals(
                DemoGreenhouseProfile.WIDTH_M - marker.distanceM,
                marker.distanceFromPadM!!,
                1e-12
            )
        }
    }

    @Test
    fun conflictingExhaustWallsDisableQuantitativeProbes() {
        val far = lengthWallExhaust("far")
        val near = Phase3D2AFixtures.instance(
            EquipmentType.EXHAUST_FAN, "near", 2,
            xM = 2.5, yM = 0.0, zM = 1.5, yawDeg = 270.0
        )
        val layout = FanPadLayoutResolver.resolve(
            listOf(far, near),
            DemoGreenhouseProfile.LENGTH_M,
            DemoGreenhouseProfile.WIDTH_M,
            FanPadWallSelection.AUTO
        )
        assertTrue(layout.conflict)
        assertEquals(FanPadCopy.CONFLICTING_EXHAUST_WALLS, layout.explanation)
        val resolved = FanPadBoundaryResolver.resolve(
            PreviewMode.DEMO,
            DemoGreenhouseProfile.LENGTH_M,
            DemoGreenhouseProfile.WIDTH_M,
            DemoGreenhouseProfile.HEIGHT_M,
            0.50,
            listOf(far, near),
            WeatherBoundary.demoSimulated(35.0, 40.0, FanPadCopy.DEMO_OUTSIDE_WEATHER),
            DemoFanPadProfile.DEFAULT.toBoundaryInput(
                DemoGreenhouseProfile.WIDTH_M,
                DemoGreenhouseProfile.HEIGHT_M
            )
        )
        assertFalse(resolved.quantitativeAvailable)
        val runtime = mapOf(far.id to EquipmentRuntimeState.ON, near.id to EquipmentRuntimeState.ON)
        val snap = fieldSnapshot(listOf(far, near), runtime, advanceS = 0.0)
        assertTrue(snap.fanPad.present)
        assertFalse(snap.fanPad.quantitativeAvailable)
        assertTrue(
            ExhaustProbeLabelPlanner.plan(
                true, true, far.id, listOf(far, near), runtime, snap, 25.0, circ.cdp, circ.con, exhaust.eon
            ).isEmpty()
        )
    }

    @Test
    fun mixedWidthAndLengthExhaustWallsDisableQuantitativeProbes() {
        val yFan = lengthWallExhaust("y-wall")
        val xFan = widthWallExhaust("x-wall")
        val layout = FanPadLayoutResolver.resolve(
            listOf(yFan, xFan),
            DemoGreenhouseProfile.LENGTH_M,
            DemoGreenhouseProfile.WIDTH_M,
            FanPadWallSelection.AUTO
        )
        assertTrue(layout.conflict)
        assertEquals(FanPadCopy.CONFLICTING_EXHAUST_WALLS, layout.explanation)
    }

    @Test
    fun userForcedNonOppositeWallDisablesQuantitativeProbes() {
        val fan = lengthWallExhaust("forced")
        val layout = FanPadLayoutResolver.resolve(
            listOf(fan),
            DemoGreenhouseProfile.LENGTH_M,
            DemoGreenhouseProfile.WIDTH_M,
            FanPadWallSelection.NEAR_X0
        )
        assertTrue(layout.conflict)
        assertNull(layout.padWall)
        assertEquals(FanPadCopy.CONFLICTING_EXHAUST_WALLS, layout.explanation)
    }

    @Test
    fun outsidePointsAreOmittedNeverClamped() {
        val fan = Phase3D2AFixtures.instance(
            EquipmentType.EXHAUST_FAN, "edge", 1,
            xM = 1.5, yM = 4.0, zM = 1.5, yawDeg = 90.0
        )
        val runtime = mapOf(fan.id to EquipmentRuntimeState.ON)
        val snap = fieldSnapshot(
            listOf(fan),
            runtime,
            advanceS = 0.0,
            lengthM = 4.0,
            widthM = 3.0,
            heightM = 2.5,
            fanPad = FanPadBoundaryInput(
                present = true,
                enabled = true,
                wetPadActive = true,
                wallSelection = FanPadWallSelection.AUTO,
                widthM = 2.5,
                heightM = 2.0,
                efficiencyFraction = 0.80,
                sourceLabel = FanPadCopy.DEMO_OUTSIDE_WEATHER
            )
        )
        val markers = ExhaustProbeLabelPlanner.plan(
            true, true, fan.id, listOf(fan), runtime, snap, 25.0, circ.cdp, circ.con, exhaust.eon
        )
        assertTrue(markers.none { it.distanceM == 5.0 })
        markers.forEach { marker ->
            assertTrue(ExhaustAirflowEnvelope.contains(snap, marker.domain))
            assertTrue(marker.domain.yM >= -1e-9)
            assertTrue(marker.domain.yM <= 4.0 + 1e-9)
        }
        val fiveM = fan.positionM.copy(yM = fan.positionM.yM - 5.0)
        assertTrue(fiveM.yM < 0.0)
        assertFalse(ExhaustAirflowEnvelope.contains(snap, fiveM))
        assertNull(ExhaustAirflowEnvelope.predictedTemperatureC(snap, fiveM))
    }

    @Test
    fun uniformFieldMayProduceEqualIndependentSamples() {
        val fan = lengthWallExhaust("uniform")
        val runtime = mapOf(fan.id to EquipmentRuntimeState.ON)
        val snap = fieldSnapshot(listOf(fan), runtime, advanceS = 0.0)
        val markers = ExhaustProbeLabelPlanner.plan(
            true, true, fan.id, listOf(fan), runtime, snap, 25.0, circ.cdp, circ.con, exhaust.eon
        )
        assertEquals(5, markers.size)
        val first = markers.first().temperatureC
        markers.forEach { marker ->
            assertEquals(first, marker.temperatureC, 1e-9)
            val physics = UserPhysicsCoordinateAdapter.positionToPhysics(marker.domain)
            assertEquals(first, SnapshotFieldSampler.temperatureC(snap, physics)!!, 1e-9)
        }
    }

    @Test
    fun oneSecondPhysicsAndFiveSecondDisplayRemainUnchanged() {
        assertEquals(1.0, DemoPhysicsSchedulerConfig().publicationIntervalS, 0.0)
        assertEquals(5.0, DemoDisplayPublication.INTERVAL_S, 0.0)
        assertNotEquals(
            DemoDisplayPublication.INTERVAL_S,
            DemoPhysicsSchedulerConfig().publicationIntervalS
        )
        assertEquals(2.0, DemoPhysicsSchedulerConfig().pauseResetThresholdS, 0.0)
        assertEquals(8.0, DemoDisplayPublication.PAUSE_RESET_THRESHOLD_S, 0.0)
    }

    @Test
    fun multipleExhaustUuidsRemainIndependent() {
        val a = lengthWallExhaust("uuid-a", xM = 1.5)
        val b = lengthWallExhaust("uuid-b", xM = 3.5)
        val instances = listOf(a, b)
        val runtime = mapOf(a.id to EquipmentRuntimeState.ON, b.id to EquipmentRuntimeState.ON)
        val snap = fieldSnapshot(instances, runtime, advanceS = 1.0)
        val markersA = ExhaustProbeLabelPlanner.plan(
            true, true, a.id, instances, runtime, snap, 25.0, circ.cdp, circ.con, exhaust.eon
        )
        val markersB = ExhaustProbeLabelPlanner.plan(
            true, true, b.id, instances, runtime, snap, 25.0, circ.cdp, circ.con, exhaust.eon
        )
        assertTrue(markersA.isNotEmpty())
        assertTrue(markersB.isNotEmpty())
        assertTrue(markersA.all { it.equipmentUuid == a.id })
        assertTrue(markersB.all { it.equipmentUuid == b.id })
        assertEquals(a.positionM.xM, markersA.first().domain.xM, 1e-12)
        assertEquals(b.positionM.xM, markersB.first().domain.xM, 1e-12)
        assertNotEquals(a.id, b.id)
    }

    @Test
    fun labelsToggleHidesExhaustMarkersNotRingsOrArrows() = runTest {
        val vm = demoVm("cfg-transect-labels")
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.CIRCULATION_FAN)
        vm.tapPalette(EquipmentType.EXHAUST_FAN)
        vm.setSimulatedTemperature(35.0)
        vm.setSimulatedHumidity(60.0)
        vm.startBehavior()
        val exhaustId = vm.state.value.instances.single { it.type == EquipmentType.EXHAUST_FAN }.id
        vm.select(exhaustId)
        assertTrue(vm.visualizationProbeMarkers().isNotEmpty())
        assertFalse(vm.exhaustEffectFrame().isEmpty)
        assertFalse(vm.circulationEffectFrame().isEmpty)
        vm.toggleLabelsVisible()
        assertFalse(vm.state.value.labelsVisible)
        assertTrue(vm.visualizationProbeMarkers().isEmpty())
        assertFalse(vm.visualizationProbeControlAvailable())
        assertFalse(vm.exhaustEffectFrame().isEmpty)
        assertFalse(vm.circulationEffectFrame().isEmpty)
    }

    @Test
    fun stopRepositionAndLeaveRemainLifecycleSafe() = runTest {
        val vm = demoVm("cfg-transect-life")
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.CIRCULATION_FAN)
        vm.tapPalette(EquipmentType.EXHAUST_FAN)
        vm.duplicateSelected()
        vm.setSimulatedTemperature(35.0)
        vm.setSimulatedHumidity(60.0)
        vm.startBehavior()
        val circHost = ArCirculationRingHost(91)
        val exhHost = ArExhaustAirflowHost(92)
        assertTrue(circHost.lifecycle.attach(91))
        assertTrue(exhHost.lifecycle.attach(92))
        circHost.bind(ArCirculationRingHost.request(vm.circulationEffectFrame(), ArTabletopPhase.PLACED))
        exhHost.bind(ArExhaustAirflowHost.request(vm.exhaustEffectFrame(), ArTabletopPhase.PLACED))
        val circDispose = circHost.lifecycle.disposeCount
        val exhDispose = exhHost.lifecycle.disposeCount
        val ids = vm.state.value.instances.filter { it.type == EquipmentType.EXHAUST_FAN }.map { it.id }
        assertEquals(2, ids.size)
        vm.select(ids[0])
        assertTrue(vm.visualizationProbeMarkers().all { it.equipmentUuid == ids[0] })
        vm.select(ids[1])
        assertTrue(vm.visualizationProbeMarkers().all { it.equipmentUuid == ids[1] })
        vm.onArReposition()
        val order = exhHost.lifecycle.repositionSequence(visualizationStillRunning = vm.state.value.behaviorRunning)
        assertEquals(ArPhysicsEffectCleanup.REPOSITION, order)
        assertTrue(vm.state.value.behaviorRunning)
        assertFalse(vm.exhaustEffectFrame().isEmpty)
        assertFalse(vm.circulationEffectFrame().isEmpty)
        vm.stopBehavior()
        assertTrue(vm.visualizationProbeMarkers().isEmpty())
        assertTrue(vm.exhaustEffectFrame().isEmpty)
        assertTrue(vm.circulationEffectFrame().isEmpty)
        assertTrue(circHost.lifecycle.clearEffectsWhileSceneAlive())
        assertTrue(exhHost.lifecycle.clearEffectsWhileSceneAlive())
        circHost.clear()
        exhHost.clear()
        assertEquals(circDispose, circHost.lifecycle.disposeCount)
        assertEquals(exhDispose, exhHost.lifecycle.disposeCount)
        assertEquals(ArEffectHostState.SCENE_ALIVE, circHost.lifecycle.state)
        assertEquals(ArEffectHostState.SCENE_ALIVE, exhHost.lifecycle.state)
        vm.startBehavior()
        vm.onLeaveArScreen()
        assertFalse(vm.state.value.behaviorRunning)
        assertTrue(vm.visualizationProbeMarkers().isEmpty())
        val leave = circHost.lifecycle.leaveArSequence()
        assertEquals(ArPhysicsEffectCleanup.LEAVE_AR, leave)
    }

    @Test
    fun demoCaptionKeepsPredictedUncalibratedAndSimulatedBoundary() = runTest {
        val vm = demoVm("cfg-transect-caption")
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.EXHAUST_FAN)
        vm.setSimulatedTemperature(35.0)
        vm.setSimulatedHumidity(60.0)
        vm.startBehavior()
        val caption = vm.visualizationProbeCaption()
        assertTrue(caption.contains(VisualizationCopy.PREDICTED_UNCALIBRATED))
        assertTrue(caption.contains(PhysicsRuntimeCopy.DEMO_SIMULATION_DISCLAIMER))
        assertTrue(
            caption.contains(PhysicsRuntimeCopy.DEMO_SIMULATED_OUTSIDE_BOUNDARY) ||
                caption.contains(FanPadCopy.DEMO_OUTSIDE_WEATHER)
        )
        assertTrue(caption.contains(VisualizationCopy.FAN_PAD_COMBINED_FIELD_NOTE))
        val publicMarkers = vm.visualizationProbeMarkers()
        assertEquals(vm.exhaustProbeMarkers().map { it.id }, publicMarkers.map { it.id })
        assertTrue(vm.visualizationProbeControlAvailable())
    }

    private fun lengthWallExhaust(id: String, xM: Double = 2.5) = Phase3D2AFixtures.instance(
        EquipmentType.EXHAUST_FAN,
        id,
        1,
        xM = xM,
        yM = DemoGreenhouseProfile.LENGTH_M,
        zM = 1.5,
        yawDeg = 90.0
    )

    private fun widthWallExhaust(id: String) = Phase3D2AFixtures.instance(
        EquipmentType.EXHAUST_FAN,
        id,
        1,
        xM = DemoGreenhouseProfile.WIDTH_M,
        yM = DemoGreenhouseProfile.LENGTH_M * 0.5,
        zM = 1.5,
        yawDeg = 0.0
    )

    private fun fieldSnapshot(
        instances: List<com.greenhands.app.equipment.model.EquipmentInstance>,
        runtime: Map<String, EquipmentRuntimeState>,
        advanceS: Double,
        lengthM: Double = DemoGreenhouseProfile.LENGTH_M,
        widthM: Double = DemoGreenhouseProfile.WIDTH_M,
        heightM: Double = DemoGreenhouseProfile.HEIGHT_M,
        fanPad: FanPadBoundaryInput? = DemoFanPadProfile.DEFAULT.toBoundaryInput(widthM, heightM)
    ) = PhysicsFieldRuntime().run {
        start(
            Phase3D2AFixtures.demoRequest(
                instances,
                35.0,
                50.0,
                weather = WeatherBoundary.demoSimulated(35.0, 40.0, FanPadCopy.DEMO_OUTSIDE_WEATHER),
                lengthM = lengthM,
                widthM = widthM,
                heightM = heightM,
                runtimeById = runtime,
                fanPad = fanPad
            )
        )
        if (advanceS > 0.0) {
            var remaining = advanceS
            while (remaining > 1e-12) {
                val step = minOf(1.0, remaining)
                advance(step)
                remaining -= step
            }
        }
        val snap = snapshot()!!
        stop()
        snap
    }

    private fun transectAfter(
        intakes: List<IntakeOpening>,
        exhaustFans: List<com.greenhands.app.heat.simulation.model.ExhaustFanProfile>,
        intakeAirTemperatureC: Double,
        intakeAirHumidityRatioKgKg: Double,
        foggers: List<com.greenhands.app.heat.simulation.model.FoggerProfile> = emptyList(),
        circulationFans: List<com.greenhands.app.heat.simulation.model.CirculationFanProfile> = emptyList(),
        velocityOverride: Vec3? = Vec3(0.25, 0.0, 0.0)
    ): List<Pair<Double, Double>> {
        val scenario = TestEquipmentFixtures.scenario(
            geometry = TestEquipmentFixtures.boxGeometry(8.0, 3.0, 2.5, 0.50),
            indoorTemperatureC = 35.0,
            indoorRhPercent = 50.0,
            outsideTemperatureC = 35.0,
            outsideRhPercent = 40.0,
            exhaustFans = exhaustFans,
            intakes = intakes,
            intakeAirTemperatureC = intakeAirTemperatureC,
            intakeAirHumidityRatioKgKg = intakeAirHumidityRatioKgKg,
            foggers = foggers,
            circulationFans = circulationFans,
            driveMode = EquipmentDriveMode.FORCED,
            forced = ForcedEquipmentState(
                exhaustOn = true,
                foggerOn = foggers.isNotEmpty(),
                circulationOn = circulationFans.isNotEmpty()
            ),
            alphaM2s = 0.02,
            dEffM2s = 0.02,
            dtS = 0.25,
            velocityOverride = velocityOverride
        )
        val engine = GreenhouseSimulationEngine.create(scenario)
        repeat(80) { engine.step(0.25) }
        val grid = engine.gridState()
        return DistanceProbeSampler.DEFAULT_DISTANCES_M.map { d ->
            val point = Vec3(7.5 - d, 1.5, 1.0)
            val t = grid.interpolateScalar(grid.temperatureC, point)!!
            val w = grid.interpolateScalar(grid.humidityRatioKgKg, point)!!
            t to w
        }
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
