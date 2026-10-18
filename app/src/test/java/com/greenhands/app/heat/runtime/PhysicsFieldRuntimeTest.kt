package com.greenhands.app.heat.runtime

import com.greenhands.app.equipment.model.EquipmentRuntimeState
import com.greenhands.app.equipment.model.EquipmentType
import com.greenhands.app.heat.runtime.adapter.PerUuidEquipmentBridge
import com.greenhands.app.heat.runtime.contracts.FieldValidationStatus
import com.greenhands.app.heat.runtime.contracts.FreshnessPolicy
import com.greenhands.app.heat.runtime.contracts.InsideClimateSource
import com.greenhands.app.heat.runtime.contracts.ProbeAvailability
import com.greenhands.app.heat.runtime.contracts.WeatherBoundary
import com.greenhands.app.heat.runtime.contracts.WeatherBoundaryKind
import com.greenhands.app.heat.runtime.contracts.WeatherBoundaryStatus
import com.greenhands.app.heat.simulation.physics.Psychrometrics
import com.greenhands.app.heat.simulation.physics.SimulationException
import com.greenhands.app.preview3d.model.PreviewMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PerUuidEquipmentBridgeTest {

    @Test
    fun onlyOnEquipmentCreatesActiveSources() {
        val c1 = Phase3D2AFixtures.instance(EquipmentType.CIRCULATION_FAN, "c1", 1)
        val c2 = Phase3D2AFixtures.instance(EquipmentType.CIRCULATION_FAN, "c2", 2)
        val e1 = Phase3D2AFixtures.instance(EquipmentType.EXHAUST_FAN, "e1", 1, yawDeg = 180.0)
        val f1 = Phase3D2AFixtures.instance(EquipmentType.FOGGER, "f1", 1)
        val instances = listOf(c1, c2, e1, f1)
        val specs = com.greenhands.app.heat.runtime.adapter.EquipmentSpecificationResolver.resolveAll(
            PreviewMode.DEMO,
            instances
        )
        val weather = WeatherBoundary.estimated(
            18.0, 50.0, 0L, 0L, 7.0, 80.0, 0L, FreshnessPolicy()
        )
        val runtime = mapOf(
            c1.id to EquipmentRuntimeState.ON,
            c2.id to EquipmentRuntimeState.OFF,
            e1.id to EquipmentRuntimeState.NOT_RUNNING,
            f1.id to EquipmentRuntimeState.ON
        )
        val sources = PerUuidEquipmentBridge.buildActiveSources(instances, runtime, specs, weather)
        assertEquals(listOf(c1.id), sources.circulation.map { it.id })
        assertTrue(sources.exhaust.isEmpty())
        assertEquals(listOf(f1.id), sources.foggers.map { it.id })
        assertFalse(sources.readiness.single { it.equipmentUuid == c2.id }.contributesActiveSource)
        assertFalse(sources.readiness.single { it.equipmentUuid == e1.id }.contributesActiveSource)
        assertEquals(c1.positionM.yM, sources.circulation.single().positionM.xM, 0.0)
        assertEquals(c1.positionM.xM, sources.circulation.single().positionM.yM, 0.0)
    }

    @Test
    fun deletingFan1DoesNotTransferFan2Identity() {
        val fan1 = Phase3D2AFixtures.instance(EquipmentType.CIRCULATION_FAN, "keep-uuid-1", 1)
        val fan2 = Phase3D2AFixtures.instance(EquipmentType.CIRCULATION_FAN, "keep-uuid-2", 2)
        val remaining = listOf(fan2)
        val specs = com.greenhands.app.heat.runtime.adapter.EquipmentSpecificationResolver.resolveAll(
            PreviewMode.DEMO,
            remaining
        )
        val sources = PerUuidEquipmentBridge.buildActiveSources(
            remaining,
            mapOf(fan2.id to EquipmentRuntimeState.ON),
            specs,
            WeatherBoundary.missing()
        )
        assertEquals(listOf(fan2.id), sources.circulation.map { it.id })
        assertFalse(sources.readiness.any { it.equipmentUuid == fan1.id })
        assertEquals("keep-uuid-2", sources.readiness.single().equipmentUuid)
    }
}

class PhysicsFieldRuntimeTest {

    @Test
    fun deterministicRepeatedRunsProduceIdenticalSnapshots() {
        val fan = Phase3D2AFixtures.instance(EquipmentType.CIRCULATION_FAN, "c1", 1)
        val request = Phase3D2AFixtures.demoRequest(listOf(fan), 30.0, 60.0)
        fun runOnce() = PhysicsFieldRuntime().run {
            start(request)
            repeat(8) { advance(0.25) }
            snapshot()!!
        }
        val a = runOnce()
        val b = runOnce()
        assertEquals(a, b)
        assertEquals(FieldValidationStatus.UNCALIBRATED, a.validationStatus)
        assertEquals(InsideClimateSource.SIMULATED, a.insideSource)
        assertTrue(a.warnings.contains(PhysicsRuntimeCopy.DEMO_SIMULATION_DISCLAIMER))
    }

    @Test
    fun startStopClearsTheEngineAndRejectsFurtherSteps() {
        val fan = Phase3D2AFixtures.instance(EquipmentType.CIRCULATION_FAN, "c1", 1)
        val runtime = PhysicsFieldRuntime()
        runtime.start(Phase3D2AFixtures.demoRequest(listOf(fan), 30.0, 60.0))
        assertTrue(runtime.isRunning())
        runtime.advance(0.25)
        runtime.stop()
        assertFalse(runtime.isRunning())
        assertNull(runtime.snapshot())
        try {
            runtime.advance(0.25)
            throw AssertionError("advance after stop must fail")
        } catch (_: SimulationException) {
        }
        runtime.start(Phase3D2AFixtures.demoRequest(listOf(fan), 30.0, 60.0))
        assertTrue(runtime.isRunning())
        runtime.stop()
    }

    @Test(expected = SimulationException::class)
    fun rejectsNanDimensions() {
        val fan = Phase3D2AFixtures.instance(EquipmentType.CIRCULATION_FAN, "c1", 1)
        PhysicsFieldRuntime().start(
            Phase3D2AFixtures.demoRequest(listOf(fan), 30.0, 60.0, lengthM = Double.NaN)
        )
    }

    @Test
    fun myGreenhouseStartsTheFieldRuntimeWithLiveBoundaries() {
        val fan = Phase3D2AFixtures.instance(EquipmentType.CIRCULATION_FAN, "c1", 1)
        val weather = WeatherBoundary.estimated(
            25.80, 89.00, 0L, 0L, null, null, 0L, FreshnessPolicy()
        )
        val runtime = PhysicsFieldRuntime()
        val snap = runtime.start(
            Phase3D2AFixtures.myGreenhouseRequest(
                instances = listOf(fan),
                temperatureC = 28.41,
                humidityPercent = 61.25,
                weather = weather
            )
        )
        assertTrue(runtime.isRunning())
        assertEquals(InsideClimateSource.ACTUAL_IOT, snap.insideSource)
        assertEquals(28.41, snap.meanTemperatureC, 1e-9)
        assertNotEquals(25.0, snap.meanTemperatureC, 0.0)
        assertNotEquals(70.0, snap.meanRelativeHumidityPercent, 0.0)
        assertFalse(snap.warnings.contains(PhysicsRuntimeCopy.DEMO_SIMULATION_DISCLAIMER))
        assertEquals(WeatherBoundaryStatus.AVAILABLE, snap.weatherStatus)
        assertNotEquals(WeatherBoundaryKind.DEMO_SIMULATED, snap.weatherKind)
        runtime.stop()
    }

    @Test
    fun circulationDoesNotCreateAMeanTemperatureHeatSink() {
        val fan = Phase3D2AFixtures.instance(EquipmentType.CIRCULATION_FAN, "c1", 1, xM = 0.8, yM = 1.5, zM = 1.0)
        val runtime = PhysicsFieldRuntime()
        val snap0 = runtime.start(Phase3D2AFixtures.demoRequest(listOf(fan), 30.0, 55.0, dtS = 0.20))
        val mean0 = snap0.meanTemperatureC
        repeat(40) { runtime.advance(0.20) }
        val snap1 = runtime.snapshot()!!
        assertEquals(mean0, snap1.meanTemperatureC, 1e-8)
        assertTrue(snap1.runtimeById.values.single() == EquipmentRuntimeState.ON)
        assertTrue(snap1.equipment.single().contributesActiveSource)
        runtime.stop()
    }

    @Test
    fun exhaustRespectsTheOutdoorBoundary() {
        val exhaust = Phase3D2AFixtures.instance(
            EquipmentType.EXHAUST_FAN, "e1", 1, xM = 1.5, yM = 5.2, zM = 1.2, yawDeg = 90.0
        )
        val weather = WeatherBoundary.estimated(
            outsideTemperatureC = 20.0,
            outsideHumidityPercent = 50.0,
            weatherDataTimeMillis = 0L,
            fetchedAtMillis = 0L,
            greenhouseLatitude = 7.0,
            greenhouseLongitude = 80.0,
            nowMillis = 0L,
            policy = FreshnessPolicy()
        )
        val runtime = PhysicsFieldRuntime()
        runtime.start(
            Phase3D2AFixtures.demoRequest(
                instances = listOf(exhaust),
                temperatureC = 32.0,
                humidityPercent = 50.0,
                weather = weather,
                dtS = 1.0
            )
        )
        var previous = runtime.snapshot()!!.meanTemperatureC
        repeat(20) {
            val mean = runtime.advance(1.0).meanTemperatureC
            assertTrue("mean must fall toward 20 °C: $previous → $mean", mean <= previous + 1e-9)
            assertTrue(mean >= 20.0 - 1e-6)
            previous = mean
        }
        runtime.stop()
    }

    @Test
    fun hotterOutdoorAirDoesNotFalselyCoolThroughExhaust() {
        val exhaust = Phase3D2AFixtures.instance(EquipmentType.EXHAUST_FAN, "e1", 1, yawDeg = 90.0)
        val weather = WeatherBoundary.estimated(
            35.0, 50.0, 0L, 0L, 7.0, 80.0, 0L, FreshnessPolicy()
        )
        val runtime = PhysicsFieldRuntime()
        runtime.start(
            Phase3D2AFixtures.demoRequest(listOf(exhaust), 32.0, 50.0, weather, dtS = 1.0)
        )
        val t0 = runtime.snapshot()!!.meanTemperatureC
        val t1 = runtime.advance(2.0).meanTemperatureC
        assertTrue("hotter outdoor air must not reduce mean T ($t0 → $t1)", t1 >= t0 - 1e-12)
        runtime.stop()
    }

    @Test
    fun foggerAddsMoistureAndUsesLatentCooling() {
        val fogger = Phase3D2AFixtures.instance(EquipmentType.FOGGER, "f1", 1, xM = 1.5, yM = 2.0, zM = 1.8)
        val runtime = PhysicsFieldRuntime()
        val start = runtime.start(
            Phase3D2AFixtures.demoRequest(
                listOf(fogger),
                temperatureC = 32.0,
                humidityPercent = 40.0,
                runtimeById = mapOf(fogger.id to EquipmentRuntimeState.ON)
            )
        )
        val t0 = start.meanTemperatureC
        val w0 = start.humidityRatioKgKg.average()
        val after = runtime.advance(0.50)
        val w1 = after.humidityRatioKgKg.average()
        assertTrue("fogger must add moisture", w1 > w0)
        assertTrue("latent cooling must reduce mean T", after.meanTemperatureC < t0)
        assertTrue(after.equipment.single().contributesActiveSource)
        runtime.stop()
    }

    @Test
    fun humidityStaysNonNegativeAndDisplayedRhDoesNotExceed100() {
        val fogger = Phase3D2AFixtures.instance(EquipmentType.FOGGER, "f1", 1)
        val runtime = PhysicsFieldRuntime()
        runtime.start(
            Phase3D2AFixtures.demoRequest(
                listOf(fogger),
                temperatureC = 30.0,
                humidityPercent = 99.0,
                runtimeById = mapOf(fogger.id to EquipmentRuntimeState.ON)
            )
        )
        repeat(6) {
            val snap = runtime.advance(0.25)
            snap.humidityRatioKgKg.forEach { w ->
                if (w != 0.0) {
                    assertTrue(w >= 0.0 && w.isFinite())
                }
            }
            snap.relativeHumidityPercent.forEach { rh ->
                if (rh != 0.0) {
                    assertTrue("displayed RH $rh", rh in 0.0..100.0000001)
                }
            }
            assertTrue(snap.meanRelativeHumidityPercent <= 100.0000001)
            assertTrue(snap.finiteValues)
        }
        runtime.stop()
    }

    @Test
    fun probesAt1To5mPreserveUuidAndDoNotClampOutsidePoints() {
        val fan = Phase3D2AFixtures.instance(
            EquipmentType.CIRCULATION_FAN,
            id = "probe-fan",
            number = 1,
            xM = 0.5,
            yM = 2.0,
            zM = 1.5,
            yawDeg = 0.0
        )
        val runtime = PhysicsFieldRuntime()
        val snap = runtime.start(
            Phase3D2AFixtures.demoRequest(
                instances = listOf(fan),
                temperatureC = 30.0,
                humidityPercent = 60.0,
                lengthM = 10.0,
                widthM = 5.0,
                heightM = 3.0
            )
        )
        val probes = snap.probes.filter { it.equipmentUuid == fan.id }
        assertEquals(listOf(1.0, 2.0, 3.0, 4.0, 5.0), probes.map { it.distanceM })
        probes.forEach { sample ->
            assertEquals(fan.id, sample.equipmentUuid)
            assertEquals(FieldValidationStatus.UNCALIBRATED.takeIf {
                sample.availability == ProbeAvailability.PREDICTED_UNCALIBRATED
            } ?: FieldValidationStatus.MODULE_UNAVAILABLE, sample.validationStatus)
        }
        val inside = probes.filter { it.distanceM <= 4.0 }
        inside.forEach { sample ->
            assertEquals(ProbeAvailability.PREDICTED_UNCALIBRATED, sample.availability)
            val temperature = sample.temperatureC
            assertTrue(temperature != null && temperature.isFinite())
            assertEquals(FieldValidationStatus.UNCALIBRATED, sample.validationStatus)
        }
        val five = probes.single { it.distanceM == 5.0 }
        assertEquals(ProbeAvailability.OUTSIDE_GREENHOUSE, five.availability)
        assertNull(five.temperatureC)
        assertNull(five.relativeHumidityPercent)
        assertEquals(PhysicsRuntimeCopy.OUTSIDE_GREENHOUSE, five.reason)
        runtime.stop()
    }

    @Test
    fun offAndNotRunningCreateNoSourceInARunningField() {
        val fan = Phase3D2AFixtures.instance(EquipmentType.CIRCULATION_FAN, "c1", 1)
        val runtime = PhysicsFieldRuntime()
        val snap = runtime.start(
            Phase3D2AFixtures.demoRequest(
                listOf(fan),
                temperatureC = 25.0,
                humidityPercent = 70.0
            )
        )
        assertEquals(EquipmentRuntimeState.OFF, snap.runtimeById[fan.id])
        assertFalse(snap.equipment.single().contributesActiveSource)
        runtime.stop()
    }

    @Test
    fun assimilateReplacesTheFieldInsteadOfMixing() {
        val fan = Phase3D2AFixtures.instance(EquipmentType.CIRCULATION_FAN, "c1", 1)
        val runtime = PhysicsFieldRuntime()
        runtime.start(Phase3D2AFixtures.demoRequest(listOf(fan), 30.0, 60.0))
        runtime.advance(0.25)
        val updated = runtime.assimilateUniform(
            com.greenhands.app.heat.runtime.contracts.InsideClimateReading.simulated(22.0, 55.0),
            mapOf(fan.id to EquipmentRuntimeState.OFF)
        )
        assertEquals(22.0, updated.meanTemperatureC, 1e-9)
        val omega = Psychrometrics.humidityRatioKgKg(22.0, 55.0, 101325.0)
        assertEquals(omega, updated.humidityRatioKgKg.first { it > 0.0 }, 1e-12)
        runtime.stop()
    }
}
