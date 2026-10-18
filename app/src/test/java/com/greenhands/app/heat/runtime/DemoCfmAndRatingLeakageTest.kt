package com.greenhands.app.heat.runtime

import com.greenhands.app.ar.domain.ArRuntimeCopy
import com.greenhands.app.equipment.model.DemoEquipmentProfile
import com.greenhands.app.equipment.model.DomainPoint
import com.greenhands.app.equipment.model.EquipmentRuntimeState
import com.greenhands.app.equipment.model.EquipmentType
import com.greenhands.app.heat.runtime.adapter.AirflowUnits
import com.greenhands.app.heat.runtime.adapter.DemoPhysicsRatings
import com.greenhands.app.heat.runtime.adapter.EquipmentSpecificationResolver
import com.greenhands.app.heat.runtime.adapter.PerUuidEquipmentBridge
import com.greenhands.app.heat.runtime.contracts.FakeRuntimeClock
import com.greenhands.app.heat.runtime.contracts.FreshnessPolicy
import com.greenhands.app.heat.runtime.contracts.FreshnessStatus
import com.greenhands.app.heat.runtime.contracts.InsideClimateReading
import com.greenhands.app.heat.runtime.contracts.InsideClimateSource
import com.greenhands.app.heat.runtime.contracts.QuantitativeReadiness
import com.greenhands.app.heat.runtime.contracts.SpecificationProvenance
import com.greenhands.app.heat.runtime.contracts.WeatherBoundary
import com.greenhands.app.heat.runtime.contracts.WeatherBoundaryProvenance
import com.greenhands.app.heat.runtime.contracts.WeatherBoundaryStatus
import com.greenhands.app.preview3d.model.PreviewMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoCfmAndRatingLeakageTest {

    @Test
    fun phase3d2aDoesNotRenderOrNetwork() {
        assertEquals("Phase 3D.2A deterministic physics field runtime foundation", Phase3D2A.STATUS)
        assertFalse(Phase3D2A.RENDERS_AR_EFFECTS)
        assertFalse(Phase3D2A.ADDS_NETWORKING)
        assertFalse(Phase3D2A.ADDS_ML)
        assertFalse(Phase3D2A.PERSISTS_RUNTIME)
    }

    @Test
    fun demoCirculation1000CfmConvertsToApproximately0p4719M3s() {
        assertEquals(1000.0, DemoEquipmentProfile.CIRCULATION_RATED_CFM, 0.0)
        val si = AirflowUnits.demoCirculationRatedM3s()
        assertEquals(0.4719, si, 5e-5)
        assertEquals(AirflowUnits.cfmToCubicMetresPerSecond(1000.0), si, 0.0)
        assertNotEquals(DemoEquipmentProfile.EXHAUST_RATED_AIRFLOW_M3S, si, 0.0)
        assertEquals(2.5, DemoPhysicsRatings.exhaustRatedAirflowM3s(), 0.0)
        assertEquals(0.002, DemoPhysicsRatings.foggerWaterFlowKgS(), 0.0)
        assertEquals(0.50, DemoPhysicsRatings.foggerEvaporationEfficiency(), 0.0)
    }

    @Test
    fun myGreenhouseDoesNotReceiveDemoRatings() {
        val fan = Phase3D2AFixtures.instance(EquipmentType.CIRCULATION_FAN, "fan-1", 1)
        val exhaust = Phase3D2AFixtures.instance(EquipmentType.EXHAUST_FAN, "ex-1", 1)
        val fogger = Phase3D2AFixtures.instance(EquipmentType.FOGGER, "fog-1", 1)
        val specs = EquipmentSpecificationResolver.resolveAll(
            PreviewMode.MY_GREENHOUSE,
            listOf(fan, exhaust, fogger)
        )
        specs.values.forEach { spec ->
            assertEquals(SpecificationProvenance.MISSING, spec.provenance)
            assertEquals(QuantitativeReadiness.QUALITATIVE_ONLY, spec.readiness)
            assertEquals(PhysicsRuntimeCopy.QUANTITATIVE_UNAVAILABLE_RATING, spec.unavailableReason)
            assertNull(spec.ratedAirflowM3s)
            assertNull(spec.foggerWaterFlowKgS)
            assertNull(spec.foggerEvaporationEfficiency)
            assertNotEquals(AirflowUnits.demoCirculationRatedM3s(), spec.ratedAirflowM3s ?: -1.0, 0.0)
            assertNotEquals(DemoEquipmentProfile.EXHAUST_RATED_AIRFLOW_M3S, spec.ratedAirflowM3s ?: -1.0, 0.0)
            assertNotEquals(DemoEquipmentProfile.FOGGER_WATER_FLOW_KG_S, spec.foggerWaterFlowKgS ?: -1.0, 0.0)
        }
        val demo = EquipmentSpecificationResolver.resolve(PreviewMode.DEMO, fan)
        assertEquals(AirflowUnits.demoCirculationRatedM3s(), demo.ratedAirflowM3s!!, 1e-12)
        assertEquals(SpecificationProvenance.DEMO_PREVIEW_ASSUMPTION, demo.provenance)
        val produced = com.greenhands.app.equipment.model.SavedManufacturerSpecFactory.fromRatings(
            listOf(fan, exhaust, fogger),
            com.greenhands.app.equipment.model.ManufacturerEquipmentRatings()
        )
        produced.values.forEach { spec ->
            assertEquals(SpecificationProvenance.MISSING, spec.provenance)
            assertNull(spec.ratedAirflowM3s)
            assertNull(spec.effectRadiusM)
            assertNotEquals(AirflowUnits.demoCirculationRatedM3s(), spec.ratedAirflowM3s ?: -1.0, 0.0)
        }
    }
}

class SourceAndFreshnessContractTest {

    @Test
    fun sourceLabelsMatchScientificTruthWords() {
        val demo = InsideClimateReading.simulated(25.0, 70.0)
        assertEquals(InsideClimateSource.SIMULATED, demo.source)
        assertEquals(PhysicsRuntimeCopy.DEMO_SIMULATION, demo.sourceLabel)
        assertEquals(FreshnessStatus.DEMO_CURRENT, demo.freshness)
        assertEquals(PhysicsRuntimeCopy.DEMO_SIMULATION_DISCLAIMER, ArRuntimeCopy.SIMULATED_DISCLAIMER)

        val recorded = InsideClimateReading.recorded(24.0, 60.0, 1_000L)
        assertEquals(InsideClimateSource.RECORDED, recorded.source)
        assertEquals(PhysicsRuntimeCopy.RECORDED_DATA, recorded.sourceLabel)

        val clock = FakeRuntimeClock(10_000L)
        val iot = InsideClimateReading.actualIot(
            temperatureC = 26.0,
            relativeHumidityPercent = 65.0,
            timestampMillis = 10_000L,
            sensorPositionM = DomainPoint(1.0, 2.0, 1.2),
            nowMillis = clock.nowMillis(),
            policy = FreshnessPolicy()
        )
        assertEquals(InsideClimateSource.ACTUAL_IOT, iot.source)
        assertEquals(PhysicsRuntimeCopy.LIVE_READING, iot.sourceLabel)
        assertEquals(FreshnessStatus.FRESH, iot.freshness)

        val weather = WeatherBoundary.estimated(
            outsideTemperatureC = 22.0,
            outsideHumidityPercent = 55.0,
            weatherDataTimeMillis = 10_000L,
            fetchedAtMillis = 10_000L,
            greenhouseLatitude = 7.0,
            greenhouseLongitude = 80.0,
            nowMillis = clock.nowMillis(),
            policy = FreshnessPolicy()
        )
        assertEquals(PhysicsRuntimeCopy.ESTIMATED_OUTSIDE_WEATHER, weather.sourceLabel)
        assertEquals(WeatherBoundaryStatus.AVAILABLE, weather.status)
        assertEquals(WeatherBoundaryProvenance.WEATHER_API_ESTIMATE, weather.provenance)
        assertEquals("Open-Meteo", weather.provider)
        assertTrue(weather.permitsQuantitativeExhaust())
    }

    @Test
    fun iotAndWeatherFreshnessBoundariesUseInjectedClock() {
        val policy = FreshnessPolicy()
        val clock = FakeRuntimeClock(0L)
        val iotFresh = InsideClimateReading.actualIot(
            25.0, 70.0, 0L, null, clock.nowMillis() + 14_999L, policy
        )
        assertEquals(FreshnessStatus.FRESH, iotFresh.freshness)
        val iotStale = InsideClimateReading.actualIot(
            25.0, 70.0, 0L, null, clock.nowMillis() + 15_000L, policy
        )
        assertEquals(FreshnessStatus.STALE, iotStale.freshness)

        val available = WeatherBoundary.estimated(
            20.0, 50.0, 0L, 0L, 7.0, 80.0,
            nowMillis = 30L * 60L * 1_000L - 1L,
            policy = policy
        )
        assertEquals(WeatherBoundaryStatus.AVAILABLE, available.status)
        val stale = WeatherBoundary.estimated(
            20.0, 50.0, 0L, 0L, 7.0, 80.0,
            nowMillis = 30L * 60L * 1_000L,
            policy = policy
        )
        assertEquals(WeatherBoundaryStatus.STALE, stale.status)
        assertFalse(stale.permitsQuantitativeExhaust())
        val missing = WeatherBoundary.missing()
        assertEquals(WeatherBoundaryStatus.MISSING, missing.status)
        assertFalse(missing.permitsQuantitativeExhaust())
    }

    @Test
    fun missingOrStaleWeatherDisablesOnlyQuantitativeExhaust() {
        val circ = Phase3D2AFixtures.instance(EquipmentType.CIRCULATION_FAN, "c1", 1)
        val exhaust = Phase3D2AFixtures.instance(EquipmentType.EXHAUST_FAN, "e1", 1)
        val fogger = Phase3D2AFixtures.instance(EquipmentType.FOGGER, "f1", 1)
        val instances = listOf(circ, exhaust, fogger)
        val specs = EquipmentSpecificationResolver.resolveAll(PreviewMode.DEMO, instances)
        val on = Phase3D2AFixtures.statesOn(instances)

        val missing = PerUuidEquipmentBridge.buildActiveSources(
            instances, on, specs, WeatherBoundary.missing()
        )
        assertEquals(1, missing.circulation.size)
        assertEquals(circ.id, missing.circulation.single().id)
        assertTrue(missing.exhaust.isEmpty())
        assertEquals(1, missing.foggers.size)
        assertEquals(fogger.id, missing.foggers.single().id)
        val exhaustReadiness = missing.readiness.single { it.equipmentUuid == exhaust.id }
        assertFalse(exhaustReadiness.contributesActiveSource)
        assertEquals(PhysicsRuntimeCopy.MODULE_UNAVAILABLE_WEATHER, exhaustReadiness.unavailableReason)

        val clock = FakeRuntimeClock(0L)
        val weather = WeatherBoundary.estimated(
            18.0, 50.0, 0L, 0L, 7.0, 80.0, clock.nowMillis(), FreshnessPolicy()
        )
        val available = PerUuidEquipmentBridge.buildActiveSources(instances, on, specs, weather)
        assertEquals(1, available.exhaust.size)

        val staleWeather = WeatherBoundary.estimated(
            18.0, 50.0, 0L, 0L, 7.0, 80.0, 30L * 60L * 1_000L, FreshnessPolicy()
        )
        val stale = PerUuidEquipmentBridge.buildActiveSources(instances, on, specs, staleWeather)
        assertTrue(stale.exhaust.isEmpty())
        assertEquals(1, stale.circulation.size)
        assertEquals(1, stale.foggers.size)
    }

    @Test
    fun missingRatingDisablesOnlyThatModule() {
        val rated = Phase3D2AFixtures.instance(EquipmentType.CIRCULATION_FAN, "rated", 1)
        val unrated = Phase3D2AFixtures.instance(EquipmentType.CIRCULATION_FAN, "unrated", 2)
        val specs = mapOf(
            rated.id to EquipmentSpecificationResolver.resolve(PreviewMode.DEMO, rated),
            unrated.id to EquipmentSpecificationResolver.resolve(PreviewMode.MY_GREENHOUSE, unrated)
        )
        val sources = PerUuidEquipmentBridge.buildActiveSources(
            listOf(rated, unrated),
            mapOf(
                rated.id to EquipmentRuntimeState.ON,
                unrated.id to EquipmentRuntimeState.ON
            ),
            specs,
            WeatherBoundary.missing()
        )
        assertEquals(listOf(rated.id), sources.circulation.map { it.id })
        assertTrue(sources.readiness.single { it.equipmentUuid == rated.id }.contributesActiveSource)
        val blocked = sources.readiness.single { it.equipmentUuid == unrated.id }
        assertFalse(blocked.contributesActiveSource)
        assertEquals(PhysicsRuntimeCopy.QUANTITATIVE_UNAVAILABLE_RATING, blocked.unavailableReason)
    }
}
