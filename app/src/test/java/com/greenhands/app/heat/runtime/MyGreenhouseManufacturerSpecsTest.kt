package com.greenhands.app.heat.runtime

import com.greenhands.app.equipment.data.EquipmentLayoutCodec
import com.greenhands.app.equipment.data.InMemoryEquipmentLayoutRepository
import com.greenhands.app.equipment.domain.AirflowUnits
import com.greenhands.app.equipment.model.DemoEquipmentProfile
import com.greenhands.app.equipment.model.EquipmentRuntimeState
import com.greenhands.app.equipment.model.EquipmentType
import com.greenhands.app.equipment.model.ManufacturerSpecCopy
import com.greenhands.app.equipment.ui.EquipmentPlacementViewModel
import com.greenhands.app.equipment.ui.OverlayClimate
import com.greenhands.app.heat.domain.HeatFormulas
import com.greenhands.app.heat.runtime.adapter.DemoPhysicsRatings
import com.greenhands.app.heat.runtime.contracts.SpecificationProvenance
import com.greenhands.app.preview3d.model.DemoGreenhouseProfile
import com.greenhands.app.preview3d.model.PreviewMode
import com.greenhands.app.telemetry.FakeGreenhouseTelemetryRepository
import com.greenhands.app.telemetry.GreenhouseTelemetryState
import com.greenhands.app.telemetry.TelemetryFixtures
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
class MyGreenhouseManufacturerSpecsTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val climate = OverlayClimate(
        crop = null,
        stage = null,
        temperatureLabel = "25.0°C",
        humidityLabel = "70.0 % RH",
        vpdLabel = "Not published",
        circulation = HeatFormulas.circulation(25.0),
        exhaust = HeatFormulas.exhaustFromCirculation(HeatFormulas.circulation(25.0)),
        fogger = HeatFormulas.fogger(70.0),
        optimalTemperatureC = 25.0,
        optimalHumidityPercent = 70.0
    )

    @Before
    fun setMain() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun reset() {
        Dispatchers.resetMain()
    }

    @Test
    fun productionConvertsSavedUnitsWithoutDemoOrFixtureLeakage() {
        val circM3s = AirflowUnits.cubicFeetPerMinuteToM3s(CIRC_CFM)
        val fogKgS = AirflowUnits.litresPerHourToKgS(FOG_LPH)
        assertEquals(CIRC_CFM, AirflowUnits.m3sToCubicFeetPerMinute(circM3s), 1e-9)
        assertEquals(FOG_LPH, AirflowUnits.kgSToLitresPerHour(fogKgS), 1e-9)
        assertNotEquals(DemoEquipmentProfile.CIRCULATION_RATED_CFM, CIRC_CFM, 0.0)
        assertNotEquals(DemoPhysicsRatings.circulationRatedM3s(), circM3s, 0.0)
        assertNotEquals(DemoEquipmentProfile.EXHAUST_RATED_AIRFLOW_M3S, EXH_M3S, 0.0)
        assertNotEquals(DemoEquipmentProfile.FOGGER_WATER_FLOW_KG_S, fogKgS, 0.0)
        assertNotEquals(0.55, circM3s, 0.0)
        assertNotEquals(2.0, EXH_M3S, 0.0)
        assertNotEquals(0.0015, fogKgS, 0.0)
    }

    @Test
    fun productionConfigurationResolvesSavedManufacturerSpecs() = runTest {
        val repo = InMemoryEquipmentLayoutRepository()
        val telemetry = FakeGreenhouseTelemetryRepository()
        val vm = liveVm(repo, telemetry, "cfg-specs-resolve")
        placeAll(vm)
        enterProductionSpecs(vm)
        val specs = vm.resolvedManufacturerSpecs()
        val circ = vm.state.value.instances.single { it.type == EquipmentType.CIRCULATION_FAN }
        val exh = vm.state.value.instances.single { it.type == EquipmentType.EXHAUST_FAN }
        val fog = vm.state.value.instances.single { it.type == EquipmentType.FOGGER }
        val circSpec = specs.getValue(circ.id)
        val exhSpec = specs.getValue(exh.id)
        val fogSpec = specs.getValue(fog.id)
        assertEquals(AirflowUnits.cubicFeetPerMinuteToM3s(CIRC_CFM), circSpec.ratedAirflowM3s!!, 1e-12)
        assertEquals(CIRC_THROW_M, circSpec.effectRadiusM!!, 0.0)
        assertEquals(EXH_M3S, exhSpec.ratedAirflowM3s!!, 0.0)
        assertEquals(AirflowUnits.litresPerHourToKgS(FOG_LPH), fogSpec.foggerWaterFlowKgS!!, 1e-12)
        assertEquals(FOG_SPRAY_M, fogSpec.effectRadiusM!!, 0.0)
        assertEquals(SpecificationProvenance.MANUFACTURER_INPUT, circSpec.provenance)
        assertEquals(SpecificationProvenance.MANUFACTURER_INPUT, exhSpec.provenance)
        assertEquals(SpecificationProvenance.MANUFACTURER_INPUT, fogSpec.provenance)
        assertNull(circSpec.fanDiameterM)
        assertNotEquals(DemoPhysicsRatings.CIRCULATION_OUTLET_DIAMETER_M, circSpec.effectRadiusM)
        assertNotEquals(DemoPhysicsRatings.circulationRatedM3s(), circSpec.ratedAirflowM3s)
        assertNotEquals(DemoEquipmentProfile.EXHAUST_RATED_AIRFLOW_M3S, exhSpec.ratedAirflowM3s)
        assertNotEquals(DemoEquipmentProfile.FOGGER_WATER_FLOW_KG_S, fogSpec.foggerWaterFlowKgS)
        assertTrue(vm.quantitativeVisualizationComplete())
        assertTrue(vm.missingManufacturerSpecs().isEmpty())
    }

    @Test
    fun reopeningPreservesSavedManufacturerSpecs() = runTest {
        val repo = InMemoryEquipmentLayoutRepository()
        val telemetry = FakeGreenhouseTelemetryRepository()
        val first = liveVm(repo, telemetry, "cfg-specs-reopen")
        placeAll(first)
        enterProductionSpecs(first)
        first.saveLayout()
        val stored = repo.snapshot("grower@greenhands.app").single()
        assertEquals(CIRC_CFM, stored.circulationRatedCfm!!, 0.0)
        assertEquals(CIRC_THROW_M, stored.circulationThrowRadiusM!!, 0.0)
        assertEquals(EXH_M3S, stored.exhaustRatedAirflowM3s!!, 0.0)
        assertEquals(FOG_LPH, stored.foggerWaterFlowLph!!, 0.0)
        assertEquals(FOG_SPRAY_M, stored.foggerSprayRadiusM!!, 0.0)
        val encoded = EquipmentLayoutCodec.encode(stored)
        assertTrue(MyGreenhousePersistenceKeysAllowed(encoded))
        val decoded = EquipmentLayoutCodec.decode(encoded)!!
        assertEquals(CIRC_CFM, decoded.circulationRatedCfm!!, 0.0)
        assertEquals(CIRC_THROW_M, decoded.circulationThrowRadiusM!!, 0.0)

        val second = EquipmentPlacementViewModel(repo, telemetryRepository = telemetry)
        attach(second, "cfg-specs-reopen")
        assertEquals(CIRC_CFM.toString(), second.state.value.circulationRatedCfmInput)
        assertEquals(CIRC_THROW_M.toString(), second.state.value.circulationThrowRadiusMInput)
        assertEquals(EXH_M3S.toString(), second.state.value.exhaustRatedM3sInput)
        assertEquals(FOG_LPH.toString(), second.state.value.foggerFlowLphInput)
        assertEquals(FOG_SPRAY_M.toString(), second.state.value.foggerSprayRadiusMInput)
        val specs = second.resolvedManufacturerSpecs()
        val circ = second.state.value.instances.single { it.type == EquipmentType.CIRCULATION_FAN }
        assertEquals(AirflowUnits.cubicFeetPerMinuteToM3s(CIRC_CFM), specs.getValue(circ.id).ratedAirflowM3s!!, 1e-12)
        assertEquals(CIRC_THROW_M, specs.getValue(circ.id).effectRadiusM!!, 0.0)
        assertTrue(second.quantitativeVisualizationComplete())
    }

    @Test
    fun missingSpecsProduceExplicitUiStateInsteadOfSilentEffects() = runTest {
        val repo = InMemoryEquipmentLayoutRepository()
        val telemetry = FakeGreenhouseTelemetryRepository()
        val vm = liveVm(repo, telemetry, "cfg-specs-missing")
        telemetry.publish(GreenhouseTelemetryState.Live(TelemetryFixtures.accepted(34.0, 40.0)))
        telemetry.publishWeather(TelemetryFixtures.liveWeatherState())
        placeAll(vm)
        val missing = vm.missingManufacturerSpecs()
        assertFalse(vm.quantitativeVisualizationComplete())
        assertTrue(missing.contains(ManufacturerSpecCopy.CIRCULATION_AIRFLOW))
        assertTrue(missing.contains(ManufacturerSpecCopy.CIRCULATION_THROW))
        assertTrue(missing.contains(ManufacturerSpecCopy.EXHAUST_AIRFLOW))
        assertTrue(missing.contains(ManufacturerSpecCopy.FOGGER_FLOW))
        assertTrue(missing.contains(ManufacturerSpecCopy.FOGGER_SPRAY))
        vm.startBehavior()
        assertTrue(vm.state.value.behaviorRunning)
        assertTrue(vm.circulationEffectFrame().isEmpty)
        assertTrue(vm.foggerEffectFrame().isEmpty)
        assertFalse(vm.quantitativeVisualizationComplete())
        assertEquals(ManufacturerSpecCopy.INCOMPLETE, ManufacturerSpecCopy.INCOMPLETE)
        val specs = vm.resolvedManufacturerSpecs()
        specs.values.forEach { spec ->
            assertNotEquals(DemoPhysicsRatings.circulationRatedM3s(), spec.ratedAirflowM3s ?: -1.0, 0.0)
            assertNotEquals(DemoEquipmentProfile.EXHAUST_RATED_AIRFLOW_M3S, spec.ratedAirflowM3s ?: -1.0, 0.0)
            assertNotEquals(DemoEquipmentProfile.FOGGER_WATER_FLOW_KG_S, spec.foggerWaterFlowKgS ?: -1.0, 0.0)
        }
    }

    @Test
    fun validSavedSpecsDriveOnOffPlannersAndReachTheRuntime() = runTest {
        val repo = InMemoryEquipmentLayoutRepository()
        val telemetry = FakeGreenhouseTelemetryRepository()
        val vm = liveVm(repo, telemetry, "cfg-specs-effects")
        placeAll(vm)
        enterProductionSpecs(vm)
        telemetry.publish(GreenhouseTelemetryState.Live(TelemetryFixtures.accepted(34.0, 40.0)))
        telemetry.publishWeather(TelemetryFixtures.liveWeatherState())
        vm.startBehavior()
        assertTrue(vm.state.value.behaviorRunning)
        val circId = vm.state.value.instances.single { it.type == EquipmentType.CIRCULATION_FAN }.id
        val exhId = vm.state.value.instances.single { it.type == EquipmentType.EXHAUST_FAN }.id
        val fogId = vm.state.value.instances.single { it.type == EquipmentType.FOGGER }.id
        assertEquals(EquipmentRuntimeState.ON, vm.state.value.runtimeById[circId])
        assertEquals(EquipmentRuntimeState.ON, vm.state.value.runtimeById[exhId])
        assertEquals(EquipmentRuntimeState.ON, vm.state.value.runtimeById[fogId])
        assertFalse(vm.circulationEffectFrame().isEmpty)
        assertFalse(vm.exhaustEffectFrame().isEmpty)
        assertFalse(vm.foggerEffectFrame().isEmpty)
        val snap = vm.state.value.physicsSnapshot
        assertNotNull(snap)
        val circRow = snap!!.equipment.single { it.equipmentUuid == circId }
        assertEquals(AirflowUnits.cubicFeetPerMinuteToM3s(CIRC_CFM), circRow.specification.ratedAirflowM3s!!, 1e-12)
        assertEquals(CIRC_THROW_M, circRow.specification.effectRadiusM!!, 0.0)
        val exhRow = snap.equipment.single { it.equipmentUuid == exhId }
        assertEquals(EXH_M3S, exhRow.specification.ratedAirflowM3s!!, 0.0)
        val fogRow = snap.equipment.single { it.equipmentUuid == fogId }
        assertEquals(AirflowUnits.litresPerHourToKgS(FOG_LPH), fogRow.specification.foggerWaterFlowKgS!!, 1e-12)
        assertEquals(FOG_SPRAY_M, fogRow.specification.effectRadiusM!!, 0.0)

        telemetry.publish(GreenhouseTelemetryState.Live(TelemetryFixtures.accepted(22.0, 80.0)))
        assertEquals(EquipmentRuntimeState.OFF, vm.state.value.runtimeById[circId])
        assertEquals(EquipmentRuntimeState.OFF, vm.state.value.runtimeById[exhId])
        assertEquals(EquipmentRuntimeState.OFF, vm.state.value.runtimeById[fogId])
        assertTrue(vm.circulationEffectFrame().isEmpty)
        assertTrue(vm.exhaustEffectFrame().isEmpty)
        assertTrue(vm.foggerEffectFrame().isEmpty)
    }

    @Test
    fun demoModeDoesNotReceiveProductionRatingsOrPersistThem() = runTest {
        val repo = InMemoryEquipmentLayoutRepository()
        val telemetry = FakeGreenhouseTelemetryRepository()
        val vm = EquipmentPlacementViewModel(repo, telemetryRepository = telemetry)
        attach(vm, "cfg-specs-demo", PreviewMode.DEMO)
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.CIRCULATION_FAN)
        vm.updateManufacturerSpecInput(circCfm = CIRC_CFM.toString(), circThrowM = CIRC_THROW_M.toString())
        assertTrue(vm.resolvedManufacturerSpecs().isEmpty())
        vm.setSimulatedTemperature(26.4)
        vm.setSimulatedHumidity(70.0)
        vm.startBehavior()
        val spec = vm.state.value.physicsSnapshot!!.equipment.single().specification
        assertEquals(DemoPhysicsRatings.circulationRatedM3s(), spec.ratedAirflowM3s!!, 1e-12)
        assertNotEquals(AirflowUnits.cubicFeetPerMinuteToM3s(CIRC_CFM), spec.ratedAirflowM3s!!, 1e-9)
        vm.saveLayout()
        assertTrue(repo.snapshot("grower@greenhands.app").isEmpty())
    }

    private fun liveVm(
        repo: InMemoryEquipmentLayoutRepository,
        telemetry: FakeGreenhouseTelemetryRepository,
        configId: String
    ): EquipmentPlacementViewModel {
        val vm = EquipmentPlacementViewModel(repo, telemetryRepository = telemetry)
        attach(vm, configId, PreviewMode.MY_GREENHOUSE)
        return vm
    }

    private fun attach(
        vm: EquipmentPlacementViewModel,
        configId: String,
        mode: PreviewMode = PreviewMode.MY_GREENHOUSE
    ) {
        vm.attach(
            "grower@greenhands.app",
            configId,
            DemoGreenhouseProfile.LENGTH_M,
            DemoGreenhouseProfile.WIDTH_M,
            DemoGreenhouseProfile.HEIGHT_M,
            DemoGreenhouseProfile.CROP_ROW_COUNT,
            climate,
            mode
        )
    }

    private fun placeAll(vm: EquipmentPlacementViewModel) {
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.CIRCULATION_FAN)
        val circ = vm.state.value.instances.single { it.type == EquipmentType.CIRCULATION_FAN }
        vm.select(circ.id)
        vm.applyNumericEdit("1.20", "3.00", circ.positionM.zM.toString(), circ.yawDeg.toString())
        vm.tapPalette(EquipmentType.EXHAUST_FAN)
        vm.tapPalette(EquipmentType.FOGGER)
        val fog = vm.state.value.instances.single { it.type == EquipmentType.FOGGER }
        vm.select(fog.id)
        vm.applyNumericEdit("3.60", "7.50", fog.positionM.zM.toString(), "0")
        assertEquals(
            vm.state.value.statusMessage.orEmpty(),
            3,
            vm.state.value.instances.size
        )
        assertTrue(vm.state.value.instances.all { it.validity.isValid })
    }

    private fun enterProductionSpecs(vm: EquipmentPlacementViewModel) {
        vm.updateManufacturerSpecInput(
            circCfm = CIRC_CFM.toString(),
            circThrowM = CIRC_THROW_M.toString(),
            exhaustM3s = EXH_M3S.toString(),
            fogFlowLph = FOG_LPH.toString(),
            fogSprayM = FOG_SPRAY_M.toString()
        )
    }

    private fun MyGreenhousePersistenceKeysAllowed(encoded: String): Boolean =
        com.greenhands.app.ar.domain.MyGreenhousePersistence.layoutKeysAreAllowed(encoded)

    companion object {
        private const val CIRC_CFM = 800.0
        private const val CIRC_THROW_M = 4.5
        private const val EXH_M3S = 1.7
        private const val FOG_LPH = 10.8
        private const val FOG_SPRAY_M = 1.8
    }
}
