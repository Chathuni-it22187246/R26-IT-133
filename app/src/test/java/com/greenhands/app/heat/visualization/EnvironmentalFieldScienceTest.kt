package com.greenhands.app.heat.visualization

import com.greenhands.app.equipment.model.EquipmentRuntimeState
import com.greenhands.app.equipment.model.EquipmentType
import com.greenhands.app.heat.domain.HeatFormulas
import com.greenhands.app.heat.runtime.PhysicsFieldRuntime
import com.greenhands.app.heat.runtime.Phase3D2AFixtures
import com.greenhands.app.heat.runtime.adapter.UserPhysicsCoordinateAdapter
import com.greenhands.app.heat.runtime.contracts.WeatherBoundary
import com.greenhands.app.heat.simulation.physics.Psychrometrics
import com.greenhands.app.preview3d.model.DemoGreenhouseProfile
import com.greenhands.app.preview3d.model.PreviewMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class EnvironmentalFieldScienceTest {

    private val circ = HeatFormulas.circulation(25.0)
    private val exhaust = HeatFormulas.exhaustFromCirculation(circ)
    private val fogger = HeatFormulas.fogger(70.0)
    private val thresholds = EnvironmentalFieldThresholds(
        targetTemperatureC = 25.0,
        cdpC = circ.cdp,
        conC = circ.con,
        eonC = exhaust.eon,
        fonPercent = fogger.fon,
        fspPercent = fogger.fsp,
        fdpPercent = fogger.fdp
    )

    @Test
    fun localTemperatureAndHumidityComeFromAcceptedSamplerAndPsychrometrics() {
        val snap = startedSnapshot()
        val field = build(snap)
        assertTrue(field.samples.isNotEmpty())
        field.samples.forEach { sample ->
            val physicsPoint = UserPhysicsCoordinateAdapter.positionToPhysics(sample.positionM)
            val expectedT = SnapshotFieldSampler.temperatureC(snap, physicsPoint)
            val expectedW = SnapshotFieldSampler.humidityRatioKgKg(snap, physicsPoint)
            val expectedRh = SnapshotFieldSampler.relativeHumidityPercent(snap, physicsPoint)
            assertEquals(expectedT, sample.temperatureC)
            assertEquals(expectedW, sample.humidityRatioKgKg)
            assertEquals(expectedRh, sample.relativeHumidityPercent)
            if (sample.temperatureC != null && sample.humidityRatioKgKg != null) {
                val derived = Psychrometrics.displayedRelativeHumidityPercent(
                    sample.temperatureC!!,
                    sample.humidityRatioKgKg!!,
                    snap.airPressurePa
                )
                assertEquals(derived, sample.relativeHumidityPercent!!, 1e-9)
            }
        }
    }

    @Test
    fun uniformCalculatedTemperatureProducesUniformClassification() {
        val snap = startedSnapshot()
        val field = build(snap)
        val bands = field.samples.map { it.temperatureBand }.toSet()
        assertEquals(1, bands.size)
        assertFalse(bands.single() == TemperatureLegendBand.UNAVAILABLE)
        field.samples.forEach { sample ->
            assertEquals(field.samples.first().temperatureC!!, sample.temperatureC!!, 1e-6)
        }
    }

    @Test
    fun temperatureBandsUseAcceptedCropStageThresholds() {
        assertEquals(
            TemperatureLegendBand.TARGET,
            TemperatureLegendClassifier.band(25.0, 25.0, circ.cdp, circ.con, exhaust.eon)
        )
        assertEquals(
            EnvironmentalFieldPalette.temperatureColor(TemperatureLegendBand.TARGET),
            VisualizationRgb.GREEN
        )
        val field = build(startedSnapshot(temperatureC = 25.0, humidityPercent = 70.0))
        assertTrue(field.samples.all { it.temperatureBand == TemperatureLegendBand.TARGET })
        val hot = build(startedSnapshot(temperatureC = 40.0, humidityPercent = 40.0))
        assertTrue(hot.samples.all { it.temperatureBand == TemperatureLegendBand.EON_OR_ABOVE })
        assertEquals(
            VisualizationRgb.RED,
            EnvironmentalFieldPalette.temperatureColor(TemperatureLegendBand.EON_OR_ABOVE)
        )
    }

    @Test
    fun humidityClassificationUsesFonFspFdpAndNotFogDensity() {
        assertEquals(
            HumidityLegendBand.TARGET_CONTROL,
            HumidityLegendClassifier.band(70.0, fogger.fon, fogger.fsp, fogger.fdp)
        )
        assertEquals(
            VisualizationRgb.TEAL,
            EnvironmentalFieldPalette.humidityColor(HumidityLegendBand.TARGET_CONTROL)
        )
        assertEquals(
            VisualizationRgb.AMBER,
            EnvironmentalFieldPalette.humidityColor(HumidityLegendBand.FOGGER_DEMAND)
        )
        assertFalse(Phase3D2C.TREATS_FOG_DENSITY_AS_RH)
        assertFalse(EnvironmentalFieldVisual.ADVANCES_SOLVER_FROM_FRAMES)
        val field = build(startedSnapshot(temperatureC = 25.0, humidityPercent = 70.0))
        field.samples.forEach { sample ->
            assertNotEquals(FogMistVisual.MAX_PARTICLES_PER_FOGGER.toDouble(), sample.relativeHumidityPercent)
            assertTrue(sample.hasFiniteHumidityRatio)
        }
    }

    @Test
    fun minAverageMaxAreCalculatedFromTheSnapshotSamples() {
        val field = build(startedSnapshot())
        val temps = field.samples.mapNotNull { it.temperatureC }
        val rhs = field.samples.mapNotNull { it.relativeHumidityPercent }
        assertEquals(temps.minOrNull(), field.minTemperatureC)
        assertEquals(temps.maxOrNull(), field.maxTemperatureC)
        assertEquals(temps.average(), field.averageTemperatureC!!, 1e-9)
        assertEquals(rhs.minOrNull(), field.minRelativeHumidityPercent)
        assertEquals(rhs.maxOrNull(), field.maxRelativeHumidityPercent)
        assertEquals(rhs.average(), field.averageRelativeHumidityPercent!!, 1e-9)
    }

    @Test
    fun fieldDoesNotAddVisualTemperatureDecayOrColourByEquipmentPresence() {
        val fan = Phase3D2AFixtures.instance(EquipmentType.EXHAUST_FAN, "ex-1", 1, xM = 2.5, yM = 9.5)
        val snap = startedSnapshot(instances = listOf(fan), temperatureC = 28.0, humidityPercent = 55.0)
        val field = build(snap)
        val near = field.samples.minBy { abs(it.positionM.yM - fan.positionM.yM) + abs(it.positionM.xM - fan.positionM.xM) }
        val far = field.samples.maxBy { abs(it.positionM.yM - fan.positionM.yM) }
        assertEquals(near.temperatureC!!, far.temperatureC!!, 1e-6)
        assertEquals(near.temperatureBand, far.temperatureBand)
        assertFalse(Phase3D2C.WRITES_VISUAL_TEMPERATURE_DECAY)
        assertFalse(Phase3D2C.WRITES_RENDERER_DISTANCE_DECAY)
    }

    @Test
    fun equipmentContributionsRemainInTheCombinedSolverSnapshot() {
        val fan = Phase3D2AFixtures.instance(EquipmentType.EXHAUST_FAN, "ex-adv", 1, xM = 2.5, yM = 9.4, yawDeg = 90.0)
        val request = Phase3D2AFixtures.demoRequest(
            listOf(fan),
            32.0,
            40.0,
            weather = WeatherBoundary.demoSimulated(26.0, 50.0),
            lengthM = DemoGreenhouseProfile.LENGTH_M,
            widthM = DemoGreenhouseProfile.WIDTH_M,
            heightM = DemoGreenhouseProfile.HEIGHT_M,
            runtimeById = mapOf(fan.id to EquipmentRuntimeState.ON)
        )
        val runtime = PhysicsFieldRuntime()
        runtime.start(request)
        val after = runtime.advance(8.0)
        val field = build(after)
        field.samples.forEach { sample ->
            val physicsPoint = UserPhysicsCoordinateAdapter.positionToPhysics(sample.positionM)
            assertEquals(
                SnapshotFieldSampler.temperatureC(after, physicsPoint),
                sample.temperatureC
            )
        }
        runtime.stop()
    }

    @Test
    fun relativeHumidityStaysPhysicallyHandledAndInvalidInputsStayUnavailable() {
        assertEquals(
            HumidityLegendBand.UNAVAILABLE,
            HumidityLegendClassifier.band(Double.NaN, fogger.fon, fogger.fsp, fogger.fdp)
        )
        assertEquals(
            HumidityLegendBand.UNAVAILABLE,
            HumidityLegendClassifier.band(-1.0, fogger.fon, fogger.fsp, fogger.fdp)
        )
        assertTrue(HumidityLegendClassifier.color(Double.NaN, fogger.fon, fogger.fsp, fogger.fdp).isUnavailable)
        val field = build(startedSnapshot())
        field.samples.forEach { sample ->
            val rh = sample.relativeHumidityPercent
            if (rh != null) {
                assertTrue(rh in 0.0..100.0)
            }
        }
    }

    @Test
    fun interpolationDoesNotReplacePhysicsAndStaysBounded() {
        val snap = startedSnapshot()
        val grid = SnapshotFieldSampler.gridOf(snap)!!
        val mid = com.greenhands.app.heat.simulation.model.Vec3(5.0, 2.5, 1.5)
        val interpolated = grid.interpolateScalar(grid.temperatureC, mid)
        assertNotNull(interpolated)
        val minT = grid.temperatureC.min()
        val maxT = grid.temperatureC.max()
        assertTrue(interpolated!! + 1e-9 >= minT)
        assertTrue(interpolated - 1e-9 <= maxT)
    }

    private fun startedSnapshot(
        instances: List<com.greenhands.app.equipment.model.EquipmentInstance> = emptyList(),
        temperatureC: Double = 25.0,
        humidityPercent: Double = 70.0
    ) = PhysicsFieldRuntime().run {
        val snap = start(
            Phase3D2AFixtures.demoRequest(
                instances,
                temperatureC,
                humidityPercent,
                weather = WeatherBoundary.demoSimulated(25.0, 70.0),
                lengthM = DemoGreenhouseProfile.LENGTH_M,
                widthM = DemoGreenhouseProfile.WIDTH_M,
                heightM = DemoGreenhouseProfile.HEIGHT_M
            )
        )
        stop()
        snap
    }

    private fun build(
        snap: com.greenhands.app.heat.runtime.contracts.PhysicsFieldSnapshot
    ) = EnvironmentalFieldBuilder.build(
        physics = snap,
        previewMode = PreviewMode.DEMO,
        widthM = DemoGreenhouseProfile.WIDTH_M,
        lengthM = DemoGreenhouseProfile.LENGTH_M,
        heightM = DemoGreenhouseProfile.HEIGHT_M,
        canopyHeightM = DemoGreenhouseProfile.HEIGHT_M * 0.26,
        thresholds = thresholds,
        cropName = "Tomato",
        stageName = "Vegetative Growth",
        generation = 1,
        visualizationRunning = true
    )
}
