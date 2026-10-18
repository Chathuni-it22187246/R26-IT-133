package com.greenhands.app.heat.runtime

import com.greenhands.app.equipment.model.EquipmentRuntimeState
import com.greenhands.app.equipment.model.EquipmentType
import com.greenhands.app.heat.runtime.adapter.DemoOutsideBoundary
import com.greenhands.app.heat.runtime.adapter.EquipmentSpecificationResolver
import com.greenhands.app.heat.runtime.adapter.PerUuidEquipmentBridge
import com.greenhands.app.heat.runtime.contracts.WeatherBoundary
import com.greenhands.app.heat.runtime.contracts.WeatherBoundaryKind
import com.greenhands.app.heat.runtime.contracts.WeatherBoundaryProvenance
import com.greenhands.app.heat.runtime.contracts.WeatherBoundaryStatus
import com.greenhands.app.preview3d.model.PreviewMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoOutsideBoundaryTest {

    @Test
    fun demoObtainsAvailableSimulatedBoundaryFromTargetTrh() {
        val boundary = DemoOutsideBoundary.fromActiveCropTargets(
            PreviewMode.DEMO,
            optimalTemperatureC = 25.0,
            optimalHumidityPercent = 70.0
        )
        assertEquals(WeatherBoundaryStatus.AVAILABLE, boundary.status)
        assertTrue(boundary.permitsQuantitativeExhaust())
        assertEquals(25.0, boundary.outsideTemperatureC!!, 0.0)
        assertEquals(70.0, boundary.outsideHumidityPercent!!, 0.0)
    }

    @Test
    fun demoBoundaryHasCorrectSourceAndProvenanceLabels() {
        val boundary = WeatherBoundary.demoSimulated(24.0, 65.0)
        assertEquals(PhysicsRuntimeCopy.DEMO_SIMULATED_OUTSIDE_BOUNDARY, boundary.sourceLabel)
        assertEquals(
            "Demo simulated outside boundary — not measured weather.",
            boundary.sourceLabel
        )
        assertEquals(WeatherBoundaryProvenance.DEMO_ONLY_ASSUMPTION, boundary.provenance)
        assertEquals(WeatherBoundaryKind.DEMO_SIMULATED, boundary.kind)
        assertNull(boundary.greenhouseLatitude)
        assertNull(boundary.greenhouseLongitude)
        assertNull(boundary.provider)
        assertNull(boundary.weatherDataTimeMillis)
        assertFalse(boundary.sourceLabel.contains("Open-Meteo"))
        assertFalse(boundary.sourceLabel.contains("Actual"))
        assertFalse(boundary.sourceLabel.contains("Measured"))
        assertNotEquals(PhysicsRuntimeCopy.ESTIMATED_OUTSIDE_WEATHER, boundary.sourceLabel)
        assertNotEquals(WeatherBoundaryKind.ESTIMATED_OUTSIDE_WEATHER, boundary.kind)
        assertNotEquals(WeatherBoundaryProvenance.WEATHER_API_ESTIMATE, boundary.provenance)
    }

    @Test
    fun demoBoundaryIsNotCopiedToMyGreenhouse() {
        val leaked = DemoOutsideBoundary.fromActiveCropTargets(
            PreviewMode.MY_GREENHOUSE,
            optimalTemperatureC = 25.0,
            optimalHumidityPercent = 70.0
        )
        assertEquals(WeatherBoundaryStatus.MISSING, leaked.status)
        assertFalse(leaked.permitsQuantitativeExhaust())
        assertNotEquals(WeatherBoundaryKind.DEMO_SIMULATED, leaked.kind)
        assertNotEquals(WeatherBoundaryProvenance.DEMO_ONLY_ASSUMPTION, leaked.provenance)
        assertNotEquals(PhysicsRuntimeCopy.DEMO_SIMULATED_OUTSIDE_BOUNDARY, leaked.sourceLabel)
        assertNull(leaked.outsideTemperatureC)
        assertNull(leaked.outsideHumidityPercent)
    }

    @Test
    fun missingDemoTargetKeepsQuantitativeExhaustUnavailable() {
        val missingTemperature = DemoOutsideBoundary.fromActiveCropTargets(
            PreviewMode.DEMO,
            optimalTemperatureC = null,
            optimalHumidityPercent = 70.0
        )
        val missingHumidity = DemoOutsideBoundary.fromActiveCropTargets(
            PreviewMode.DEMO,
            optimalTemperatureC = 25.0,
            optimalHumidityPercent = null
        )
        val nanTarget = DemoOutsideBoundary.fromActiveCropTargets(
            PreviewMode.DEMO,
            optimalTemperatureC = Double.NaN,
            optimalHumidityPercent = 70.0
        )
        listOf(missingTemperature, missingHumidity, nanTarget).forEach { boundary ->
            assertFalse(boundary.permitsQuantitativeExhaust())
            assertEquals(WeatherBoundaryStatus.MISSING, boundary.status)
        }

        val exhaust = Phase3D2AFixtures.instance(EquipmentType.EXHAUST_FAN, "e1", 1)
        val specs = EquipmentSpecificationResolver.resolveAll(PreviewMode.DEMO, listOf(exhaust))
        val sources = PerUuidEquipmentBridge.buildActiveSources(
            listOf(exhaust),
            mapOf(exhaust.id to EquipmentRuntimeState.ON),
            specs,
            missingTemperature
        )
        assertTrue(sources.exhaust.isEmpty())
        assertEquals(
            PhysicsRuntimeCopy.MODULE_UNAVAILABLE_WEATHER,
            sources.readiness.single().unavailableReason
        )
    }

    @Test
    fun exhaustCoolsTowardACoolerDemoBoundary() {
        val exhaust = Phase3D2AFixtures.instance(
            EquipmentType.EXHAUST_FAN, "e1", 1, xM = 1.5, yM = 5.2, zM = 1.2, yawDeg = 90.0
        )
        val weather = DemoOutsideBoundary.fromActiveCropTargets(PreviewMode.DEMO, 20.0, 50.0)
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
            previous = mean
        }
        runtime.stop()
    }

    @Test
    fun exhaustHeatsTowardAHotterInjectedDemoBoundary() {
        val exhaust = Phase3D2AFixtures.instance(EquipmentType.EXHAUST_FAN, "e1", 1, yawDeg = 90.0)
        val weather = WeatherBoundary.demoSimulated(35.0, 50.0)
        val runtime = PhysicsFieldRuntime()
        runtime.start(
            Phase3D2AFixtures.demoRequest(listOf(exhaust), 32.0, 50.0, weather, dtS = 1.0)
        )
        val t0 = runtime.snapshot()!!.meanTemperatureC
        val t1 = runtime.advance(2.0).meanTemperatureC
        assertTrue("hotter Demo boundary must not reduce mean T ($t0 → $t1)", t1 >= t0 - 1e-12)
        runtime.stop()
    }

    @Test
    fun ventilationAloneDoesNotCoolBelowTheDemoBoundary() {
        val exhaust = Phase3D2AFixtures.instance(
            EquipmentType.EXHAUST_FAN, "e1", 1, xM = 1.5, yM = 5.2, zM = 1.2, yawDeg = 90.0
        )
        val weather = WeatherBoundary.demoSimulated(20.0, 50.0)
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
        repeat(20) {
            val mean = runtime.advance(1.0).meanTemperatureC
            assertTrue("ventilation must not cool below 20 °C: $mean", mean >= 20.0 - 1e-6)
        }
        runtime.stop()
    }
}
