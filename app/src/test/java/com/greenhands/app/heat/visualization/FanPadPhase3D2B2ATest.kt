package com.greenhands.app.heat.visualization

import com.greenhands.app.ar.domain.ArOverlayLayout
import com.greenhands.app.ar.domain.ArTabletopPhase
import com.greenhands.app.ar.domain.EquipmentRuntimeEvaluator
import com.greenhands.app.ar.domain.MyGreenhousePersistence
import com.greenhands.app.ar.render.ArCirculationColorMaterial
import com.greenhands.app.ar.render.ArEffectHostState
import com.greenhands.app.ar.render.ArFanPadAirflowHost
import com.greenhands.app.ar.render.ArLiveRendererOwnership
import com.greenhands.app.ar.render.ArPhysicsEffectLifecycle
import com.greenhands.app.equipment.data.EquipmentLayoutCodec
import com.greenhands.app.equipment.model.EquipmentLayout
import com.greenhands.app.equipment.model.EquipmentRuntimeState
import com.greenhands.app.equipment.model.EquipmentType
import com.greenhands.app.equipment.model.LayoutOrigin
import com.greenhands.app.heat.domain.HeatFormulas
import com.greenhands.app.heat.runtime.FanPadCopy
import com.greenhands.app.heat.runtime.Phase3D2AFixtures
import com.greenhands.app.heat.runtime.PhysicsFieldRuntime
import com.greenhands.app.heat.runtime.PhysicsRuntimeCopy
import com.greenhands.app.heat.runtime.adapter.DemoFanPadProfile
import com.greenhands.app.heat.runtime.adapter.FanPadBoundaryResolver
import com.greenhands.app.heat.runtime.adapter.FanPadLayoutResolver
import com.greenhands.app.heat.runtime.adapter.SimulationScenarioAdapter
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
import com.greenhands.app.heat.simulation.physics.EvaporativePadOutlet
import com.greenhands.app.heat.simulation.physics.GreenhouseSimulationEngine
import com.greenhands.app.heat.simulation.physics.Psychrometrics
import com.greenhands.app.heat.simulation.physics.SimulationException
import com.greenhands.app.heat.simulation.probe.DistanceProbeSampler
import com.greenhands.app.preview3d.model.DemoGreenhouseProfile
import com.greenhands.app.preview3d.model.PreviewMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FanPadPhase3D2B2ATest {

    private val air = AirProperties.chen2014Preview()
    private val circ = HeatFormulas.circulation(25.0)

    @Test
    fun padTemperatureEquationBoundariesAndEta() {
        val wet = EvaporativePadOutlet.evaluate(35.0, 40.0, air.pressurePa, air.specificHeatJkgK, 1.0, true)
        val twb = Psychrometrics.wetBulbTemperatureC(35.0, 40.0, air.pressurePa, air.specificHeatJkgK)
        assertEquals(twb, wet.temperatureC, 1e-6)
        assertTrue(wet.temperatureC + 1e-9 >= twb)
        val mid = EvaporativePadOutlet.evaluate(35.0, 40.0, air.pressurePa, air.specificHeatJkgK, 0.80, true)
        assertTrue(mid.temperatureC < 35.0)
        assertTrue(mid.temperatureC > twb - 1e-9)
        val dry = EvaporativePadOutlet.evaluate(35.0, 40.0, air.pressurePa, air.specificHeatJkgK, null, false)
        assertEquals(35.0, dry.temperatureC, 0.0)
        assertEquals(dry.outsideHumidityRatioKgKg, dry.humidityRatioKgKg, 1e-12)
    }

    @Test(expected = SimulationException::class)
    fun etaZeroIsRejectedForWetPad() {
        EvaporativePadOutlet.evaluate(35.0, 40.0, air.pressurePa, air.specificHeatJkgK, 0.0, true)
    }

    @Test(expected = SimulationException::class)
    fun invalidEfficiencyIsRejected() {
        EvaporativePadOutlet.evaluate(35.0, 40.0, air.pressurePa, air.specificHeatJkgK, 1.2, true)
    }

    @Test
    fun wetPadAddsMoistureAndStaysAtOrBelow100Rh() {
        val out = EvaporativePadOutlet.evaluate(35.0, 40.0, air.pressurePa, air.specificHeatJkgK, 0.80, true)
        assertTrue(out.humidityRatioKgKg > out.outsideHumidityRatioKgKg)
        assertTrue(out.relativeHumidityPercent <= 100.0)
        assertTrue(out.relativeHumidityPercent >= 0.0)
        val omegaSat = Psychrometrics.saturationHumidityRatioKgKg(out.temperatureC, air.pressurePa)
        assertTrue(out.humidityRatioKgKg <= omegaSat + 1e-12)
    }

    @Test
    fun dryPadDoesNotAddMoisture() {
        val omegaOut = Psychrometrics.humidityRatioKgKg(32.0, 55.0, air.pressurePa)
        val dry = EvaporativePadOutlet.evaluate(32.0, 55.0, air.pressurePa, air.specificHeatJkgK, 0.80, false)
        assertEquals(32.0, dry.temperatureC, 0.0)
        assertEquals(omegaOut, dry.humidityRatioKgKg, 1e-12)
    }

    @Test
    fun rhIsDerivedNotIndependentlyAnimated() {
        val out = EvaporativePadOutlet.evaluate(35.0, 40.0, air.pressurePa, air.specificHeatJkgK, 0.80, true)
        val derived = Psychrometrics.displayedRelativeHumidityPercent(
            out.temperatureC,
            out.humidityRatioKgKg,
            air.pressurePa
        )
        assertEquals(derived, out.relativeHumidityPercent, 1e-9)
    }

    @Test
    fun demoValuesNeverLeakIntoMyGreenhouseLayout() {
        val layout = EquipmentLayout(
            layoutId = "lay-pad",
            configurationId = "cfg-pad",
            userId = "grower@greenhands.app",
            lengthM = 10.0,
            widthM = 5.0,
            heightM = 3.0,
            cropRowCount = 3,
            origin = LayoutOrigin.MANUAL,
            explicitlySaved = true
        )
        val encoded = EquipmentLayoutCodec.encode(layout)
        assertTrue(MyGreenhousePersistence.layoutKeysAreAllowed(encoded))
        val decoded = EquipmentLayoutCodec.decode(encoded)!!
        assertNull(decoded.coolingPadPresent)
        assertNull(decoded.coolingPadEfficiencyFraction)
        assertFalse(encoded.contains("0.80") && decoded.coolingPadEfficiencyFraction == DemoFanPadProfile.ILLUSTRATIVE_EFFICIENCY)
        assertNotEquals(DemoFanPadProfile.DEFAULT_OUTSIDE_TEMPERATURE_C, decoded.coolingPadWidthM)
    }

    @Test
    fun missingMyGreenhousePadInputsDisableOnlyQuantitativePadCooling() {
        val exhaust = Phase3D2AFixtures.instance(
            EquipmentType.EXHAUST_FAN, "e1", 1,
            xM = 2.5, yM = DemoGreenhouseProfile.LENGTH_M, zM = 1.5, yawDeg = 90.0
        )
        val weather = WeatherBoundary.estimated(32.0, 40.0, 0L, 0L, 7.0, 80.0, 0L, com.greenhands.app.heat.runtime.contracts.FreshnessPolicy())
        val missing = FanPadBoundaryResolver.resolve(
            PreviewMode.MY_GREENHOUSE,
            DemoGreenhouseProfile.LENGTH_M,
            DemoGreenhouseProfile.WIDTH_M,
            DemoGreenhouseProfile.HEIGHT_M,
            0.50,
            listOf(exhaust),
            weather,
            FanPadBoundaryInput(
                present = true,
                enabled = true,
                wetPadActive = true,
                wallSelection = FanPadWallSelection.AUTO,
                widthM = null,
                heightM = null,
                efficiencyFraction = null,
                sourceLabel = PhysicsRuntimeCopy.ESTIMATED_OUTSIDE_WEATHER
            )
        )
        assertFalse(missing.quantitativeAvailable)
        assertTrue(missing.missingInputs.any { it.contains("width") || it.contains("height") || it.contains("efficiency") })
        assertTrue(missing.intakes.isEmpty())
    }

    @Test
    fun padSelectsOppositeEndWallAndConflictDisablesQuantitativePath() {
        val far = Phase3D2AFixtures.instance(
            EquipmentType.EXHAUST_FAN, "far", 1,
            xM = 2.5, yM = DemoGreenhouseProfile.LENGTH_M, zM = 1.5, yawDeg = 90.0
        )
        val near = Phase3D2AFixtures.instance(
            EquipmentType.EXHAUST_FAN, "near", 2,
            xM = 2.5, yM = 0.0, zM = 1.5, yawDeg = 270.0
        )
        val one = FanPadLayoutResolver.resolve(listOf(far), 10.0, 5.0, FanPadWallSelection.AUTO)
        assertEquals(GreenhouseEndWall.FAR_Y_LENGTH, one.commonWall)
        assertEquals(GreenhouseEndWall.NEAR_Y0, one.padWall)
        assertFalse(one.conflict)
        val conflict = FanPadLayoutResolver.resolve(listOf(far, near), 10.0, 5.0, FanPadWallSelection.AUTO)
        assertTrue(conflict.conflict)
        assertEquals(FanPadCopy.CONFLICTING_EXHAUST_WALLS, conflict.explanation)
    }

    @Test
    fun userXyzMapsToPhysicsForPadInlet() {
        val domain = com.greenhands.app.equipment.model.DomainPoint(1.2, 0.04, 1.1)
        val physics = UserPhysicsCoordinateAdapter.positionToPhysics(domain)
        assertEquals(0.04, physics.xM, 0.0)
        assertEquals(1.2, physics.yM, 0.0)
        assertEquals(1.1, physics.zM, 0.0)
        val inward = FanPadLayoutResolver.inwardPhysics(GreenhouseEndWall.NEAR_Y0)
        assertTrue(inward.xM > 0.0)
    }

    @Test
    fun inletDirectionIsPadTowardExhaustAndExhaustArrowsRemainOutward() {
        assertTrue(PadInletEffectPlanner.inletTravelsPadTowardExhaust(GreenhouseEndWall.NEAR_Y0))
        assertTrue(PadInletEffectPlanner.inletTravelsPadTowardExhaust(GreenhouseEndWall.FAR_Y_LENGTH))
        val (inDx, inDy, _) = com.greenhands.app.heat.visualization.ExhaustAirflowEnvelope.inwardDomain(90.0)
        val (outDx, outDy, _) = com.greenhands.app.heat.visualization.ExhaustAirflowEnvelope.outwardDomain(90.0)
        assertTrue(inDy < 0.0)
        assertTrue(outDy > 0.0)
        assertEquals(-outDx, inDx, 0.0)
        assertFalse(PadInletEffectPlanner.writesRendererDistanceDecay())
        assertFalse(Phase3D2B2A.WRITES_RENDERER_DISTANCE_DECAY)
    }

    @Test
    fun spatialPadBoundaryProducesGradientAndIndependentProbes() {
        val intake = IntakeOpening("pad", Vec3(0.25, 1.5, 1.0), Vec3(1.0, 0.0, 0.0), 1.0)
        val exhaust = TestEquipmentFixtures.testExhaustFan(Vec3(7.5, 1.5, 1.0), 0.24)
        val omegaPad = Psychrometrics.humidityRatioKgKg(22.0, 80.0, air.pressurePa)
        val scenario = TestEquipmentFixtures.scenario(
            geometry = TestEquipmentFixtures.boxGeometry(8.0, 3.0, 2.5, 0.50),
            indoorTemperatureC = 35.0,
            indoorRhPercent = 50.0,
            outsideTemperatureC = 35.0,
            outsideRhPercent = 40.0,
            exhaustFans = listOf(exhaust),
            intakes = listOf(intake),
            intakeAirTemperatureC = 22.0,
            intakeAirHumidityRatioKgKg = omegaPad,
            driveMode = EquipmentDriveMode.FORCED,
            forced = ForcedEquipmentState(exhaustOn = true),
            alphaM2s = 0.02,
            dEffM2s = 0.02,
            dtS = 0.25,
            velocityOverride = Vec3(0.25, 0.0, 0.0)
        )
        val engine = GreenhouseSimulationEngine.create(scenario)
        repeat(80) { engine.step(0.25) }
        val grid = engine.gridState()
        val padT = grid.interpolateScalar(grid.temperatureC, Vec3(0.4, 1.5, 1.0))!!
        val exhaustT = grid.interpolateScalar(grid.temperatureC, Vec3(7.2, 1.5, 1.0))!!
        assertTrue("pad side should be cooler than exhaust side: $padT vs $exhaustT", padT < exhaustT)
        val samples = DistanceProbeSampler.DEFAULT_DISTANCES_M.map { d ->
            DistanceProbeSampler.samplePoint(grid, Vec3(7.5 - d, 1.5, 1.0), d, air.pressurePa)
        }
        assertEquals(5, samples.size)
        samples.forEach { assertNotNull(it.temperatureC) }
        assertFalse(engine.step(0.25).diagnostics.wellMixedVentilation)
    }

    @Test
    fun uniformFieldMayReturnEqualProbeSamples() {
        val geometry = TestEquipmentFixtures.boxGeometry(6.0, 3.0, 2.5, 0.50)
        val grid = com.greenhands.app.heat.simulation.model.GridState.build(geometry, 30.0, 0.01)
        val a = DistanceProbeSampler.samplePoint(grid, Vec3(1.0, 1.5, 1.0), 1.0, air.pressurePa)
        val b = DistanceProbeSampler.samplePoint(grid, Vec3(5.0, 1.5, 1.0), 5.0, air.pressurePa)
        assertEquals(a.temperatureC!!, b.temperatureC!!, 1e-9)
    }

    @Test
    fun outsideProbesOmittedFromMarkers() {
        val fan = Phase3D2AFixtures.instance(
            EquipmentType.EXHAUST_FAN, "edge", 1,
            xM = 2.5, yM = 0.2, zM = 1.5, yawDeg = 90.0
        )
        val request = Phase3D2AFixtures.demoRequest(
            listOf(fan),
            35.0,
            50.0,
            weather = WeatherBoundary.demoSimulated(35.0, 40.0, FanPadCopy.DEMO_OUTSIDE_WEATHER),
            lengthM = 6.0,
            widthM = 3.0,
            heightM = 2.5,
            runtimeById = mapOf(fan.id to EquipmentRuntimeState.ON)
        )
        val snap = PhysicsFieldRuntime().run {
            val started = start(request)
            stop()
            started
        }
        val markers = ExhaustProbeLabelPlanner.plan(
            true, true, fan.id, listOf(fan), mapOf(fan.id to EquipmentRuntimeState.ON),
            snap, 25.0, circ.cdp, circ.con, HeatFormulas.exhaustFromCirculation(circ).eon
        )
        markers.forEach { marker ->
            assertTrue(marker.domain.yM >= 0.0)
            assertTrue(marker.domain.yM <= 6.0)
        }
    }

    @Test
    fun oneSecondPhysicsAndFiveSecondDisplayRemainSeparate() {
        assertEquals(1.0, com.greenhands.app.heat.runtime.DemoPhysicsSchedulerConfig().publicationIntervalS, 0.0)
        assertEquals(5.0, DemoDisplayPublication.INTERVAL_S, 0.0)
        assertNotEquals(
            DemoDisplayPublication.INTERVAL_S,
            com.greenhands.app.heat.runtime.DemoPhysicsSchedulerConfig().publicationIntervalS
        )
    }

    @Test
    fun circulationHysteresisRemainsOnAt2698WhenAlreadyOn() {
        val on = EquipmentRuntimeEvaluator.nextCirculation(EquipmentRuntimeState.ON, 26.98, circ)
        assertEquals(EquipmentRuntimeState.ON, on)
        assertEquals(EquipmentRuntimeState.OFF, EquipmentRuntimeEvaluator.nextCirculation(EquipmentRuntimeState.OFF, 26.98, circ))
        assertEquals(EquipmentRuntimeState.OFF, EquipmentRuntimeEvaluator.nextCirculation(EquipmentRuntimeState.ON, 23.0, circ))
        assertEquals(EquipmentRuntimeState.ON, EquipmentRuntimeEvaluator.nextCirculation(EquipmentRuntimeState.OFF, 27.0, circ))
    }

    @Test
    fun drierReplacementAirCanReduceHumidityRatio() {
        val intake = IntakeOpening("pad", Vec3(0.25, 1.5, 1.0), Vec3(1.0, 0.0, 0.0), 1.0)
        val omegaDry = Psychrometrics.humidityRatioKgKg(30.0, 20.0, air.pressurePa)
        val scenario = TestEquipmentFixtures.scenario(
            geometry = TestEquipmentFixtures.boxGeometry(4.0, 3.0, 2.0, 0.50),
            indoorTemperatureC = 30.0,
            indoorRhPercent = 70.0,
            outsideTemperatureC = 30.0,
            outsideRhPercent = 20.0,
            exhaustFans = listOf(TestEquipmentFixtures.testExhaustFan(Vec3(3.5, 1.5, 1.0), 0.4)),
            intakes = listOf(intake),
            intakeAirTemperatureC = 30.0,
            intakeAirHumidityRatioKgKg = omegaDry,
            driveMode = EquipmentDriveMode.FORCED,
            forced = ForcedEquipmentState(exhaustOn = true)
        )
        val engine = GreenhouseSimulationEngine.create(scenario)
        val w0 = engine.gridState().meanHumidityRatioKgKg()
        repeat(20) { engine.step(1.0) }
        assertTrue(engine.gridState().meanHumidityRatioKgKg() < w0)
    }

    @Test
    fun adapterFillsSpatialIntakesForDemoPadAndLeavesWellMixedWhenDisabled() {
        val exhaust = Phase3D2AFixtures.instance(
            EquipmentType.EXHAUST_FAN, "e1", 1,
            xM = 2.5, yM = DemoGreenhouseProfile.LENGTH_M, zM = 1.5, yawDeg = 90.0
        )
        val weather = WeatherBoundary.demoSimulated(35.0, 40.0, FanPadCopy.DEMO_OUTSIDE_WEATHER)
        val specs = com.greenhands.app.heat.runtime.adapter.EquipmentSpecificationResolver.resolveAll(
            PreviewMode.DEMO, listOf(exhaust)
        )
        val withPad = SimulationScenarioAdapter.build(
            mode = PreviewMode.DEMO,
            lengthM = DemoGreenhouseProfile.LENGTH_M,
            widthM = DemoGreenhouseProfile.WIDTH_M,
            heightM = DemoGreenhouseProfile.HEIGHT_M,
            cropRowCount = 3,
            canopyHeightM = 0.78,
            inside = com.greenhands.app.heat.runtime.contracts.InsideClimateReading.simulated(35.0, 50.0),
            weather = weather,
            instances = listOf(exhaust),
            runtimeById = mapOf(exhaust.id to EquipmentRuntimeState.ON),
            thresholds = Phase3D2AFixtures.thresholds,
            specsById = specs,
            fanPad = DemoFanPadProfile.DEFAULT.toBoundaryInput(5.0, 3.0)
        )
        assertTrue(withPad.scenario.intakes.isNotEmpty())
        assertFalse(withPad.scenario.usesWellMixedVentilation())
        assertNotNull(withPad.scenario.intakeAirTemperatureC)
        assertTrue(withPad.fanPad.quantitativeAvailable)
        val without = SimulationScenarioAdapter.build(
            mode = PreviewMode.DEMO,
            lengthM = DemoGreenhouseProfile.LENGTH_M,
            widthM = DemoGreenhouseProfile.WIDTH_M,
            heightM = DemoGreenhouseProfile.HEIGHT_M,
            cropRowCount = 3,
            canopyHeightM = 0.78,
            inside = com.greenhands.app.heat.runtime.contracts.InsideClimateReading.simulated(35.0, 50.0),
            weather = weather,
            instances = listOf(exhaust),
            runtimeById = mapOf(exhaust.id to EquipmentRuntimeState.ON),
            thresholds = Phase3D2AFixtures.thresholds,
            specsById = specs,
            fanPad = null
        )
        assertTrue(without.scenario.intakes.isEmpty())
        assertTrue(without.scenario.usesWellMixedVentilation())
    }

    @Test
    fun uuidIndependenceAndNoUnsupportedFilamentUniforms() {
        assertTrue(ArCirculationColorMaterial.maySetParameter("color"))
        assertFalse(ArCirculationColorMaterial.maySetParameter("emissiveFactor"))
        assertFalse(PadInletAppearance.WRITES_EMISSIVE_FACTOR)
        assertFalse(ExhaustAirflowAppearance.WRITES_EMISSIVE_FACTOR)
        assertTrue("padFrame" in ArLiveRendererOwnership.FORBIDDEN_REMEMBER_KEYS)
        assertEquals(setOf("engine"), ArLiveRendererOwnership.REMEMBER_KEYS)
        val a = Phase3D2AFixtures.instance(EquipmentType.EXHAUST_FAN, "uuid-a", 1, yM = 10.0, yawDeg = 90.0)
        val b = Phase3D2AFixtures.instance(EquipmentType.EXHAUST_FAN, "uuid-b", 2, yM = 10.0, yawDeg = 90.0)
        assertNotEquals(a.id, b.id)
    }

    @Test
    fun compactPortraitAndLandscapeAllocationRemainsValid() {
        val portrait = ArOverlayLayout.compactPortrait(360f, 800f, placed = true)
        val landscape = ArOverlayLayout.compactLandscape(800f, 360f, placed = true)
        assertTrue(portrait.overlappingPairs().isEmpty())
        assertTrue(landscape.overlappingPairs().isEmpty())
    }

    @Test
    fun screenshotValuesComeFromTheSameSnapshot() {
        val fan = Phase3D2AFixtures.instance(
            EquipmentType.EXHAUST_FAN, "ex", 1,
            xM = 2.5, yM = 10.0, zM = 1.5, yawDeg = 90.0
        )
        val request = Phase3D2AFixtures.demoRequest(
            listOf(fan),
            35.0,
            50.0,
            weather = WeatherBoundary.demoSimulated(35.0, 40.0, FanPadCopy.DEMO_OUTSIDE_WEATHER),
            lengthM = 10.0,
            widthM = 5.0,
            heightM = 3.0,
            runtimeById = mapOf(fan.id to EquipmentRuntimeState.ON),
            fanPad = DemoFanPadProfile.DEFAULT.toBoundaryInput(5.0, 3.0)
        )
        val runtime = PhysicsFieldRuntime()
        runtime.start(request)
        runtime.advance(1.0)
        val published = runtime.snapshot()!!
        val markers = ExhaustProbeLabelPlanner.plan(
            true, true, fan.id, listOf(fan), mapOf(fan.id to EquipmentRuntimeState.ON),
            published, 25.0, circ.cdp, circ.con, HeatFormulas.exhaustFromCirculation(circ).eon
        )
        markers.forEach { marker ->
            val physics = UserPhysicsCoordinateAdapter.positionToPhysics(marker.domain)
            val sampled = SnapshotFieldSampler.temperatureC(published, physics)!!
            assertEquals(sampled, marker.temperatureC, 1e-9)
        }
        runtime.stop()
    }

    @Test
    fun missingWeatherDisablesQuantitativePadCoolingOnly() {
        val exhaust = Phase3D2AFixtures.instance(
            EquipmentType.EXHAUST_FAN, "e-w", 1,
            xM = 2.5, yM = DemoGreenhouseProfile.LENGTH_M, zM = 1.5, yawDeg = 90.0
        )
        val missing = FanPadBoundaryResolver.resolve(
            PreviewMode.MY_GREENHOUSE,
            DemoGreenhouseProfile.LENGTH_M,
            DemoGreenhouseProfile.WIDTH_M,
            DemoGreenhouseProfile.HEIGHT_M,
            0.50,
            listOf(exhaust),
            WeatherBoundary.missing(),
            FanPadBoundaryInput(
                present = true,
                enabled = true,
                wetPadActive = true,
                wallSelection = FanPadWallSelection.AUTO,
                widthM = 4.0,
                heightM = 2.5,
                efficiencyFraction = 0.75,
                sourceLabel = "none"
            )
        )
        assertFalse(missing.quantitativeAvailable)
        assertTrue(missing.intakes.isEmpty())
        assertTrue(missing.missingInputs.any { it.contains("weather") || it.contains("temperature") })
    }

    @Test
    fun missingPadInputsHideNumericExhaustProbes() {
        val fan = Phase3D2AFixtures.instance(
            EquipmentType.EXHAUST_FAN, "ex-miss", 1,
            xM = 2.5, yM = 10.0, zM = 1.5, yawDeg = 90.0
        )
        val request = Phase3D2AFixtures.demoRequest(
            listOf(fan),
            35.0,
            50.0,
            weather = WeatherBoundary.demoSimulated(35.0, 40.0, FanPadCopy.DEMO_OUTSIDE_WEATHER),
            lengthM = 10.0,
            widthM = 5.0,
            heightM = 3.0,
            runtimeById = mapOf(fan.id to EquipmentRuntimeState.ON),
            fanPad = FanPadBoundaryInput(
                present = true,
                enabled = true,
                wetPadActive = true,
                wallSelection = FanPadWallSelection.AUTO,
                widthM = null,
                heightM = null,
                efficiencyFraction = null,
                sourceLabel = FanPadCopy.DEMO_OUTSIDE_WEATHER
            )
        )
        val snap = PhysicsFieldRuntime().run {
            val started = start(request)
            stop()
            started
        }
        assertTrue(snap.fanPad.present)
        assertFalse(snap.fanPad.quantitativeAvailable)
        val markers = ExhaustProbeLabelPlanner.plan(
            true, true, fan.id, listOf(fan), mapOf(fan.id to EquipmentRuntimeState.ON),
            snap, 25.0, circ.cdp, circ.con, HeatFormulas.exhaustFromCirculation(circ).eon
        )
        assertTrue(markers.isEmpty())
    }

    @Test
    fun coolingAtConstantHumidityRatioRaisesRh() {
        val omega = Psychrometrics.humidityRatioKgKg(35.0, 40.0, air.pressurePa)
        val rhHot = Psychrometrics.displayedRelativeHumidityPercent(35.0, omega, air.pressurePa)
        val rhCool = Psychrometrics.displayedRelativeHumidityPercent(26.0, omega, air.pressurePa)
        assertTrue(rhCool > rhHot)
        assertTrue(rhCool <= 100.0)
    }

    @Test
    fun padHostStopRepositionLeaveCleanupIsIdempotent() {
        val host = ArFanPadAirflowHost(71)
        assertTrue(host.lifecycle.attach(71))
        host.bind(ArFanPadAirflowHost.request(PadInletEffectFrame.EMPTY, ArTabletopPhase.PLACED))
        val dispose = host.lifecycle.disposeCount
        host.lifecycle.stopVisualUpdates()
        host.lifecycle.clearEffectsWhileSceneAlive()
        host.clear()
        assertEquals(dispose, host.lifecycle.disposeCount)
        host.lifecycle.disposeBeforeHostDestruction()
        host.lifecycle.disposeBeforeHostDestruction()
        host.lifecycle.abandonAfterHostUnavailable()
        assertTrue(host.lifecycle.disposeCount >= 1)
        val foreign = ArPhysicsEffectLifecycle(72)
        assertTrue(foreign.attach(72))
        assertFalse(foreign.acceptEngineBoundResource(71, "pad-root"))
        assertEquals(ArEffectHostState.SCENE_ALIVE, foreign.state)
    }
}
