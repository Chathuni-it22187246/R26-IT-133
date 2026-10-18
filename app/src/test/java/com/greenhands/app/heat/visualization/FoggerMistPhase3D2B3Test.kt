package com.greenhands.app.heat.visualization

import com.greenhands.app.ar.domain.ArOverlayLayout
import com.greenhands.app.ar.domain.ArRuntimeCopy
import com.greenhands.app.ar.domain.EquipmentPresentationContext
import com.greenhands.app.ar.render.ArCirculationColorMaterial
import com.greenhands.app.ar.render.ArLiveRendererOwnership
import com.greenhands.app.ar.render.CirculationRingSceneMath
import com.greenhands.app.ar.domain.ArTabletopScale
import com.greenhands.app.equipment.data.InMemoryEquipmentLayoutRepository
import com.greenhands.app.equipment.model.EquipmentInstance
import com.greenhands.app.equipment.model.EquipmentRuntimeState
import com.greenhands.app.equipment.model.EquipmentType
import com.greenhands.app.equipment.ui.EquipmentPlacementViewModel
import com.greenhands.app.equipment.ui.OverlayClimate
import com.greenhands.app.heat.domain.HeatFormulas
import com.greenhands.app.heat.runtime.PhysicsFieldRuntime
import com.greenhands.app.heat.runtime.PhysicsRuntimeCopy
import com.greenhands.app.heat.runtime.Phase3D2AFixtures
import com.greenhands.app.heat.runtime.adapter.EquipmentSpecificationResolver
import com.greenhands.app.heat.runtime.adapter.SimulationScenarioAdapter
import com.greenhands.app.heat.runtime.adapter.UserPhysicsCoordinateAdapter
import com.greenhands.app.heat.runtime.adapter.UuidEquipmentReadiness
import com.greenhands.app.heat.runtime.contracts.EquipmentPhysicsSpecification
import com.greenhands.app.heat.runtime.contracts.FieldValidationStatus
import com.greenhands.app.heat.runtime.contracts.GridGeometrySnapshot
import com.greenhands.app.heat.runtime.contracts.InsideClimateReading
import com.greenhands.app.heat.runtime.contracts.InsideClimateSource
import com.greenhands.app.heat.runtime.contracts.PhysicsFieldSnapshot
import com.greenhands.app.heat.runtime.contracts.WeatherBoundary
import com.greenhands.app.heat.runtime.contracts.WeatherBoundaryKind
import com.greenhands.app.heat.runtime.contracts.WeatherBoundaryProvenance
import com.greenhands.app.heat.runtime.contracts.WeatherBoundaryStatus
import com.greenhands.app.heat.simulation.physics.Psychrometrics
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
class FoggerMistPhase3D2B3Test {

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
    fun humidityLegendUsesInclusiveExclusiveBoundaries() {
        val fon = 68.0
        val fsp = 70.0
        val fdp = 72.0
        assertEquals(HumidityLegendBand.FOGGER_DEMAND, HumidityLegendClassifier.band(67.9, fon, fsp, fdp))
        assertEquals(HumidityLegendBand.FOGGER_DEMAND, HumidityLegendClassifier.band(68.0, fon, fsp, fdp))
        assertEquals(HumidityLegendBand.APPROACHING_TARGET, HumidityLegendClassifier.band(68.0001, fon, fsp, fdp))
        assertEquals(HumidityLegendBand.APPROACHING_TARGET, HumidityLegendClassifier.band(69.9, fon, fsp, fdp))
        assertEquals(HumidityLegendBand.TARGET_CONTROL, HumidityLegendClassifier.band(70.0, fon, fsp, fdp))
        assertEquals(HumidityLegendBand.TARGET_CONTROL, HumidityLegendClassifier.band(72.0, fon, fsp, fdp))
        assertEquals(HumidityLegendBand.ABOVE_CONTROL, HumidityLegendClassifier.band(72.0001, fon, fsp, fdp))
        assertEquals(VisualizationRgb.RED, HumidityLegendClassifier.color(68.0, fon, fsp, fdp))
        assertEquals(VisualizationRgb.YELLOW, HumidityLegendClassifier.color(69.0, fon, fsp, fdp))
        assertEquals(VisualizationRgb.LIGHT_BLUE, HumidityLegendClassifier.color(71.0, fon, fsp, fdp))
        assertEquals(HumidityLegendClassifier.ABOVE, HumidityLegendClassifier.color(80.0, fon, fsp, fdp))
    }

    @Test
    fun humidityLegendRejectsInvalidOrderingAndNonFiniteValues() {
        assertEquals(HumidityLegendBand.UNAVAILABLE, HumidityLegendClassifier.band(65.0, 70.0, 68.0, 72.0))
        assertEquals(HumidityLegendBand.UNAVAILABLE, HumidityLegendClassifier.band(65.0, 68.0, 72.0, 70.0))
        assertEquals(HumidityLegendBand.UNAVAILABLE, HumidityLegendClassifier.band(65.0, 68.0, 68.0, 72.0))
        assertEquals(HumidityLegendBand.UNAVAILABLE, HumidityLegendClassifier.band(Double.NaN, 68.0, 70.0, 72.0))
        assertEquals(HumidityLegendBand.UNAVAILABLE, HumidityLegendClassifier.band(65.0, Double.POSITIVE_INFINITY, 70.0, 72.0))
        assertEquals(HumidityLegendBand.UNAVAILABLE, HumidityLegendClassifier.band(null, 68.0, 70.0, 72.0))
        assertEquals(HumidityLegendBand.UNAVAILABLE, HumidityLegendClassifier.band(65.0, null, 70.0, 72.0))
        assertEquals(HumidityLegendBand.UNAVAILABLE, HumidityLegendClassifier.band(-1.0, 68.0, 70.0, 72.0))
        assertEquals(HumidityLegendBand.UNAVAILABLE, HumidityLegendClassifier.band(101.0, 68.0, 70.0, 72.0))
        assertTrue(HumidityLegendClassifier.color(Double.NaN, 68.0, 70.0, 72.0).isUnavailable)
    }

    @Test
    fun temperatureAndHumidityLegendsRemainSeparate() {
        val humidity = HumidityLegendClassifier.band(68.0, 68.0, 70.0, 72.0)
        val temperature = TemperatureLegendClassifier.band(25.0, 25.0, 23.0, 27.0, 31.0)
        assertEquals(HumidityLegendBand.FOGGER_DEMAND, humidity)
        assertEquals(TemperatureLegendBand.TARGET, temperature)
        assertEquals(VisualizationRgb.RED, HumidityLegendClassifier.colorOf(humidity))
        assertEquals(VisualizationRgb.LIGHT_BLUE, TemperatureLegendClassifier.colorOf(temperature))
        assertFalse(OfficialValidationTransect.SHOWS_FOGGER_MARKERS)
        assertFalse(OfficialValidationTransect.SHOWS_CIRCULATION_MARKERS)
        assertTrue(Phase3D2B3.RENDERS_FOG)
        assertFalse(Phase3D2B3.PUBLIC_FOGGER_PROBES)
        assertFalse(Phase3D2B3.CHANGES_PHASE_3A)
    }

    @Test
    fun onReadyUuidProducesMistAndOffNotRunningGhostDoNot() {
        val fog = overheadFogger("fog-1", 1)
        val snapshot = liveSnapshot(listOf(fog), mapOf(fog.id to EquipmentRuntimeState.ON))
        val on = FoggerEffectPlanner.plan(
            true, listOf(fog), mapOf(fog.id to EquipmentRuntimeState.ON), snapshot, thresholds
        )
        assertFalse(on.isEmpty)
        assertEquals(listOf("fog-1"), on.groups.map { it.equipmentUuid })
        assertTrue(on.groups.single().particleCount > 0)
        assertTrue(on.groups.single().quantitativeReady)
        assertTrue(FoggerEffectPlanner.plan(true, listOf(fog), mapOf(fog.id to EquipmentRuntimeState.OFF), snapshot, thresholds).isEmpty)
        assertTrue(FoggerEffectPlanner.plan(true, listOf(fog), mapOf(fog.id to EquipmentRuntimeState.NOT_RUNNING), snapshot, thresholds).isEmpty)
        assertTrue(FoggerEffectPlanner.plan(false, listOf(fog), mapOf(fog.id to EquipmentRuntimeState.ON), snapshot, thresholds).isEmpty)
        assertTrue(FoggerEffectPlanner.plan(true, listOf(fog), emptyMap(), snapshot, thresholds).isEmpty)
        assertTrue(
            FoggerEffectPlanner.plan(
                true,
                listOf(fog.copy(id = "ghost-1", isGhost = true)),
                mapOf("ghost-1" to EquipmentRuntimeState.ON),
                snapshot,
                thresholds
            ).isEmpty
        )
        assertTrue(FoggerEffectPlanner.plan(true, listOf(fog), mapOf(fog.id to EquipmentRuntimeState.ON), null, thresholds).isEmpty)
    }

    @Test
    fun missingRatingProducesNoQuantitativeEffect() {
        val fog = overheadFogger("fog-missing", 1)
        val snapshot = unreadinessSnapshot(fog)
        val frame = FoggerEffectPlanner.plan(
            true, listOf(fog), mapOf(fog.id to EquipmentRuntimeState.ON), snapshot, thresholds
        )
        assertTrue(frame.isEmpty)
        assertEquals(VisualizationCopy.FOGGER_VISUALIZATION_UNAVAILABLE, frame.limitation)
        val details = FoggerEffectPlanner.details(
            visualizationRunning = true,
            selected = fog,
            runtimeById = mapOf(fog.id to EquipmentRuntimeState.ON),
            snapshot = snapshot,
            thresholds = thresholds,
            presentation = EquipmentPresentationContext(PreviewMode.DEMO, true)
        )
        assertNotNull(details)
        assertFalse(details!!.quantitativeReady)
        assertEquals(PhysicsRuntimeCopy.QUANTITATIVE_UNAVAILABLE_RATING, details.limitation)
    }

    @Test
    fun multipleFoggerUuidsStayIndependentAndDeletingFogger1KeepsFogger2() = runTest {
        val vm = demoVm("cfg-fog-uuid")
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.FOGGER)
        vm.duplicateSelected()
        vm.setSimulatedTemperature(35.0)
        vm.setSimulatedHumidity(40.0)
        vm.startBehavior()
        val placed = vm.state.value.instances.filter { it.type == EquipmentType.FOGGER && !it.isGhost }
        assertEquals(2, placed.size)
        val fog1 = placed.first { it.displayNumber == 1 }
        val fog2 = placed.first { it.displayNumber == 2 }
        val frame = vm.foggerEffectFrame()
        assertEquals(setOf(fog1.id, fog2.id), frame.groups.map { it.equipmentUuid }.toSet())
        assertNotEquals(frame.group(fog1.id)!!.originM, frame.group(fog2.id)!!.originM)
        vm.select(fog1.id)
        vm.requestDelete()
        vm.deleteSelected()
        val remaining = vm.state.value.instances.single { it.type == EquipmentType.FOGGER }
        assertEquals(fog2.id, remaining.id)
        assertEquals(2, remaining.displayNumber)
        assertEquals("Fogger 2", remaining.displayLabel())
        val after = vm.foggerEffectFrame()
        assertEquals(listOf(fog2.id), after.groups.map { it.equipmentUuid })
        assertNull(after.group(fog1.id))
    }

    @Test
    fun foggerSprayDirectionIsAcceptedDownwardAxisAndParticlesStayInside() {
        val fog = overheadFogger("fog-spray", 1)
        val snapshot = liveSnapshot(listOf(fog), mapOf(fog.id to EquipmentRuntimeState.ON))
        val frame = FoggerEffectPlanner.plan(
            true, listOf(fog), mapOf(fog.id to EquipmentRuntimeState.ON), snapshot, thresholds
        )
        val group = frame.groups.single()
        val (dx, dy, dz) = UserPhysicsCoordinateAdapter.foggerSprayDomain()
        assertEquals(0.0, dx, 0.0)
        assertEquals(0.0, dy, 0.0)
        assertEquals(-1.0, dz, 0.0)
        assertEquals(dx, group.sprayDomainDx, 0.0)
        assertEquals(dy, group.sprayDomainDy, 0.0)
        assertEquals(dz, group.sprayDomainDz, 0.0)
        val physics = UserPhysicsCoordinateAdapter.foggerSprayPhysics()
        assertEquals(0.0, physics.xM, 0.0)
        assertEquals(0.0, physics.yM, 0.0)
        assertTrue(physics.zM < 0.0)
        val layout = ArTabletopScale.layout(DemoGreenhouseProfile.dimensions(), 3)
        val scene = CirculationRingSceneMath.domainForwardToScene(dx, dy, dz, layout)
        assertTrue(scene.second < 0.0)
        repeat(group.particleCount) { index ->
            val distance = FoggerMistAnimator.distanceM(
                group.minDistanceM,
                group.maxDistanceM,
                0.4,
                index,
                group.particleCount,
                FogMistVisual.CYCLE_S,
                group.animationPhase
            )
            assertTrue(distance + 1e-9 >= group.minDistanceM)
            assertTrue(distance <= group.maxDistanceM + 1e-9)
            val domain = group.domainPointAt(distance)
            assertTrue(FoggerMistEnvelope.contains(snapshot, domain))
            assertTrue(domain.zM + 1e-9 >= 0.0)
            assertTrue(domain.zM <= DemoGreenhouseProfile.HEIGHT_M + 1e-9)
        }
        assertTrue(FoggerMistAnimator.recyclesAtEnvelopeBoundary(0.90))
        assertTrue(FoggerMistAnimator.fadeAtProgress(0.95) < FoggerMistAnimator.fadeAtProgress(0.40))
        assertFalse(FogMistVisual.ANIMATION_IS_MEASURED_VELOCITY)
        assertFalse(FogMistVisual.PARTICLE_COUNT_IS_WATER_FLOW)
        assertFalse(FogMistVisual.PARTICLE_DENSITY_IS_FOG_CONCENTRATION)
    }

    @Test
    fun noFoggerPublicProbesAndCirculationProbesRemainHidden() = runTest {
        val vm = demoVm("cfg-fog-probes")
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
        assertFalse(OfficialValidationTransect.isPublicEquipment(EquipmentType.FOGGER))
        assertFalse(OfficialValidationTransect.isPublicEquipment(EquipmentType.CIRCULATION_FAN))
        assertTrue(OfficialValidationTransect.isPublicEquipment(EquipmentType.EXHAUST_FAN))
        assertFalse(CirculationProbeLabelPlanner.SHOWS_PUBLIC_VALIDATION_TRANSECT)
        assertFalse(OfficialValidationTransect.SHOWS_FOGGER_MARKERS)
        assertFalse(OfficialValidationTransect.SHOWS_CIRCULATION_MARKERS)
    }

    @Test
    fun foggerChangesLaterCombinedFieldAndExhaustProbesCanReflectIt() {
        val fog = overheadFogger("fog-field", 1, xM = 2.5, yM = 5.0)
        val fan = Phase3D2AFixtures.instance(
            EquipmentType.EXHAUST_FAN, "ex-field", 1,
            xM = 2.5, yM = DemoGreenhouseProfile.LENGTH_M, zM = 1.5, yawDeg = 90.0
        )
        val instances = listOf(fog, fan)
        val withFog = fieldSnapshot(
            instances,
            mapOf(fog.id to EquipmentRuntimeState.ON, fan.id to EquipmentRuntimeState.ON)
        )
        val withoutFog = fieldSnapshot(
            instances,
            mapOf(fog.id to EquipmentRuntimeState.OFF, fan.id to EquipmentRuntimeState.ON)
        )
        val humidityChanged = withFog.humidityRatioKgKg.indices.any { i ->
            kotlin.math.abs(withFog.humidityRatioKgKg[i] - withoutFog.humidityRatioKgKg[i]) > 1e-12
        }
        val temperatureChanged = withFog.temperatureC.indices.any { i ->
            kotlin.math.abs(withFog.temperatureC[i] - withoutFog.temperatureC[i]) > 1e-12
        }
        assertTrue("fogger moisture/latent cooling must change the combined field", humidityChanged || temperatureChanged)
        val withMarkers = ExhaustProbeLabelPlanner.plan(
            true, true, fan.id, instances,
            mapOf(fog.id to EquipmentRuntimeState.ON, fan.id to EquipmentRuntimeState.ON),
            withFog, 25.0, circ.cdp, circ.con, exhaust.eon
        )
        val withoutMarkers = ExhaustProbeLabelPlanner.plan(
            true, true, fan.id, instances,
            mapOf(fog.id to EquipmentRuntimeState.OFF, fan.id to EquipmentRuntimeState.ON),
            withoutFog, 25.0, circ.cdp, circ.con, exhaust.eon
        )
        if (withMarkers.isNotEmpty() && withoutMarkers.isNotEmpty()) {
            val probeChanged = withMarkers.zip(withoutMarkers).any { (a, b) ->
                kotlin.math.abs(a.temperatureC - b.temperatureC) > 1e-9 ||
                    a.relativeHumidityPercent != b.relativeHumidityPercent
            }
            assertTrue(probeChanged || humidityChanged || temperatureChanged)
        }
        val physics = UserPhysicsCoordinateAdapter.positionToPhysics(fog.positionM)
        val omega = SnapshotFieldSampler.humidityRatioKgKg(withFog, physics)
        val temperature = SnapshotFieldSampler.temperatureC(withFog, physics)
        val derived = SnapshotFieldSampler.relativeHumidityPercent(withFog, physics)
        assertNotNull(omega)
        assertNotNull(temperature)
        assertNotNull(derived)
        val expected = Psychrometrics.displayedRelativeHumidityPercent(
            temperature!!, omega!!, withFog.airPressurePa
        )
        assertEquals(expected, derived!!, 1e-9)
    }

    @Test
    fun renderCapsDoNotAlterPhysicsAndAnimationDoesNotAdvanceSolver() {
        val foggers = (1..5).map { n ->
            overheadFogger("fog-cap-$n", n, xM = 0.8 + n * 0.6, yM = 2.0 + n)
        }
        val runtime = foggers.associate { it.id to EquipmentRuntimeState.ON }
        val request = Phase3D2AFixtures.demoRequest(
            foggers,
            35.0,
            40.0,
            weather = WeatherBoundary.demoSimulated(25.0, 70.0),
            lengthM = DemoGreenhouseProfile.LENGTH_M,
            widthM = DemoGreenhouseProfile.WIDTH_M,
            heightM = DemoGreenhouseProfile.HEIGHT_M,
            runtimeById = runtime
        )
        val physics = PhysicsFieldRuntime()
        val started = physics.start(request)
        val specs = EquipmentSpecificationResolver.resolveAll(PreviewMode.DEMO, foggers)
        val built = SimulationScenarioAdapter.build(
            mode = PreviewMode.DEMO,
            lengthM = DemoGreenhouseProfile.LENGTH_M,
            widthM = DemoGreenhouseProfile.WIDTH_M,
            heightM = DemoGreenhouseProfile.HEIGHT_M,
            cropRowCount = 3,
            canopyHeightM = 0.78,
            inside = InsideClimateReading.simulated(35.0, 40.0),
            weather = WeatherBoundary.demoSimulated(25.0, 70.0),
            instances = foggers,
            runtimeById = runtime,
            thresholds = thresholds,
            specsById = specs
        )
        assertEquals(5, built.sources.foggers.size)
        val frame = FoggerEffectPlanner.plan(true, foggers, runtime, started, thresholds)
        assertTrue(frame.groups.size <= FogMistVisual.MAX_FOGGER_GROUPS)
        assertTrue(frame.particleObjectCount <= FogMistVisual.MAX_TOTAL_PARTICLES)
        assertEquals(0, frame.haloObjectCount)
        assertFalse(FogMistVisual.RENDERS_SCENE_HUMIDITY_HALOS)
        assertEquals(5, built.scenario.foggers.size)
        val elapsed = physics.elapsedTimeS()
        val mean = started.meanTemperatureC
        repeat(12) { step ->
            FoggerMistAnimator.distanceM(0.0, 1.5, 0.05 * step, 0, 10)
            FoggerMistAnimator.fade(0.05 * step, 0, 10)
        }
        assertEquals(elapsed, physics.elapsedTimeS(), 0.0)
        assertEquals(mean, physics.snapshot()!!.meanTemperatureC, 0.0)
        assertEquals(20.0, FogMistVisual.MAX_VISUAL_HZ, 0.0)
        physics.stop()
    }

    @Test
    fun labelsDoNotTurnMistOffAndVisibilityToggleHidesEffectsOnly() = runTest {
        val vm = demoVm("cfg-fog-labels")
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.FOGGER)
        vm.setSimulatedTemperature(35.0)
        vm.setSimulatedHumidity(40.0)
        vm.startBehavior()
        val before = vm.foggerEffectFrame().groups.map { it.equipmentUuid }
        assertTrue(before.isNotEmpty())
        vm.toggleLabelsVisible()
        assertFalse(vm.state.value.labelsVisible)
        assertEquals(before, vm.foggerEffectFrame().groups.map { it.equipmentUuid })
        assertTrue(vm.state.value.behaviorRunning)
        vm.setAirflowFogVisible(false)
        assertTrue(vm.foggerEffectFrame().isEmpty)
        assertTrue(vm.circulationEffectFrame().isEmpty)
        assertTrue(vm.state.value.behaviorRunning)
        assertNotNull(vm.state.value.physicsSnapshot)
        vm.setAirflowFogVisible(true)
        assertEquals(before, vm.foggerEffectFrame().groups.map { it.equipmentUuid })
    }

    @Test
    fun hysteresisTurnsMistOffImmediatelyAtFdp() = runTest {
        val vm = demoVm("cfg-fog-hyst")
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.FOGGER)
        vm.setSimulatedTemperature(35.0)
        vm.setSimulatedHumidity(40.0)
        vm.startBehavior()
        val id = vm.state.value.instances.single().id
        assertEquals(EquipmentRuntimeState.ON, vm.state.value.runtimeById[id])
        assertFalse(vm.foggerEffectFrame().isEmpty)
        vm.setSimulatedHumidity(70.0)
        assertEquals(EquipmentRuntimeState.ON, vm.state.value.runtimeById[id])
        assertFalse(vm.foggerEffectFrame().isEmpty)
        vm.setSimulatedHumidity(80.0)
        assertEquals(EquipmentRuntimeState.OFF, vm.state.value.runtimeById[id])
        assertTrue(vm.foggerEffectFrame().isEmpty)
    }

    @Test
    fun stopClearsFogButKeepsLayoutAndReadings() = runTest {
        val vm = demoVm("cfg-fog-stop")
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.FOGGER)
        vm.setSimulatedTemperature(35.0)
        vm.setSimulatedHumidity(40.0)
        vm.startBehavior()
        val id = vm.state.value.instances.single().id
        assertFalse(vm.foggerEffectFrame().isEmpty)
        vm.stopBehavior()
        assertTrue(vm.foggerEffectFrame().isEmpty)
        assertEquals(1, vm.state.value.instances.size)
        assertEquals(id, vm.state.value.instances.single().id)
        assertEquals(35.0, vm.state.value.simulatedTemperatureC)
        assertEquals(40.0, vm.state.value.simulatedHumidityPercent)
        assertEquals(EquipmentRuntimeState.NOT_RUNNING, vm.state.value.runtimeById[id])
        assertFalse(Phase3D2B3.PERSISTS_EFFECTS)
        assertFalse(Phase3D2B3.ADDS_NETWORKING)
        assertFalse(Phase3D2B3.ANIMATES_GLB)
    }

    @Test
    fun disconnectedMyGreenhouseHasNoFogEffects() = runTest {
        val vm = EquipmentPlacementViewModel(InMemoryEquipmentLayoutRepository())
        vm.attach(
            "grower@greenhands.app",
            "cfg-fog-my",
            DemoGreenhouseProfile.LENGTH_M,
            DemoGreenhouseProfile.WIDTH_M,
            DemoGreenhouseProfile.HEIGHT_M,
            DemoGreenhouseProfile.CROP_ROW_COUNT,
            climate,
            PreviewMode.MY_GREENHOUSE
        )
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.FOGGER)
        assertEquals(ArRuntimeCopy.MY_GREENHOUSE_START_DISABLED, vm.startBlockReason())
        vm.startBehavior()
        assertFalse(vm.state.value.behaviorRunning)
        assertTrue(vm.foggerEffectFrame().isEmpty)
        assertNull(vm.state.value.physicsSnapshot)
    }

    @Test
    fun compactPortraitAndLandscapeAllocationRemainsValid() {
        val portrait = ArOverlayLayout.compactPortrait(360f, 800f, placed = true)
        val landscape = ArOverlayLayout.compactLandscape(800f, 360f, placed = true)
        assertTrue(portrait.overlappingPairs().isEmpty())
        assertTrue(landscape.overlappingPairs().isEmpty())
        assertTrue(portrait.viewport.area() > 0f)
        assertTrue(landscape.viewport.area() > 0f)
    }

    @Test
    fun noEmissiveFactorAndRememberKeysStayOnTheEngine() {
        assertFalse(FogMistAppearance.WRITES_EMISSIVE_FACTOR)
        assertFalse(Phase3D2B3.WRITES_EMISSIVE_FACTOR)
        assertFalse(ArCirculationColorMaterial.writesEmissiveFactor())
        assertFalse(ArCirculationColorMaterial.maySetParameter("emissiveFactor"))
        assertTrue(ArCirculationColorMaterial.maySetParameter("color"))
        assertTrue("fogFrame" in ArLiveRendererOwnership.FORBIDDEN_REMEMBER_KEYS)
        assertEquals(setOf("engine"), ArLiveRendererOwnership.REMEMBER_KEYS)
        val write = ArCirculationColorMaterial.frameColorWrite(FogMistAppearance.MIST, 0.5f)
        assertFalse(write.requestsEmissiveFactor)
        assertEquals(setOf(ArCirculationColorMaterial.COLOR_UNIFORM), write.uniformNames)
    }

    private fun overheadFogger(
        id: String,
        number: Int,
        xM: Double = 2.5,
        yM: Double = 5.0,
        zM: Double = 2.2
    ): EquipmentInstance = Phase3D2AFixtures.instance(
        EquipmentType.FOGGER, id, number, xM = xM, yM = yM, zM = zM, yawDeg = 0.0
    )

    private fun liveSnapshot(
        instances: List<EquipmentInstance>,
        runtime: Map<String, EquipmentRuntimeState>
    ): PhysicsFieldSnapshot {
        val request = Phase3D2AFixtures.demoRequest(
            instances,
            35.0,
            40.0,
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

    private fun fieldSnapshot(
        instances: List<EquipmentInstance>,
        runtime: Map<String, EquipmentRuntimeState>
    ): PhysicsFieldSnapshot {
        val request = Phase3D2AFixtures.demoRequest(
            instances,
            35.0,
            40.0,
            weather = WeatherBoundary.demoSimulated(32.0, 45.0),
            lengthM = DemoGreenhouseProfile.LENGTH_M,
            widthM = DemoGreenhouseProfile.WIDTH_M,
            heightM = DemoGreenhouseProfile.HEIGHT_M,
            runtimeById = runtime
        )
        return PhysicsFieldRuntime().run {
            start(request)
            advance(8.0)
            val snap = snapshot()!!
            stop()
            snap
        }
    }

    private fun unreadinessSnapshot(fog: EquipmentInstance): PhysicsFieldSnapshot {
        val spec = EquipmentPhysicsSpecification.missing(
            fog.id, fog.type, fog.positionM, fog.yawDeg
        )
        val readiness = UuidEquipmentReadiness(
            equipmentUuid = fog.id,
            type = fog.type,
            runtimeState = EquipmentRuntimeState.ON,
            specification = spec,
            contributesActiveSource = false,
            quantitativeReady = false,
            unavailableReason = PhysicsRuntimeCopy.QUANTITATIVE_UNAVAILABLE_RATING
        )
        return PhysicsFieldSnapshot(
            elapsedTimeS = 1.0,
            grid = GridGeometrySnapshot(
                DemoGreenhouseProfile.LENGTH_M,
                DemoGreenhouseProfile.WIDTH_M,
                DemoGreenhouseProfile.HEIGHT_M,
                0.5, 20, 10, 6, 8, 8
            ),
            temperatureC = DoubleArray(8) { 30.0 },
            humidityRatioKgKg = DoubleArray(8) { 0.01 },
            relativeHumidityPercent = DoubleArray(8) { 40.0 },
            meanTemperatureC = 30.0,
            meanRelativeHumidityPercent = 40.0,
            referenceTemperatureC = 30.0,
            referenceRelativeHumidityPercent = 40.0,
            insideSource = InsideClimateSource.SIMULATED,
            insideSourceLabel = "Demo simulation",
            weatherStatus = WeatherBoundaryStatus.AVAILABLE,
            weatherSourceLabel = "Demo simulated outside boundary — not measured weather.",
            weatherProvenance = WeatherBoundaryProvenance.DEMO_ONLY_ASSUMPTION,
            weatherKind = WeatherBoundaryKind.DEMO_SIMULATED,
            validationStatus = FieldValidationStatus.UNCALIBRATED,
            equipment = listOf(readiness),
            runtimeById = mapOf(fog.id to EquipmentRuntimeState.ON),
            warnings = emptyList(),
            unavailableModuleReasons = emptyMap(),
            probes = emptyList(),
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
