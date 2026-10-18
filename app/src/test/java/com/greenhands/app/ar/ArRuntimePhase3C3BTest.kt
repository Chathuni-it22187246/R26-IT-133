package com.greenhands.app.ar

import com.greenhands.app.ar.domain.ArHudContent
import com.greenhands.app.ar.domain.ArLiveEnvironmentReading
import com.greenhands.app.ar.domain.ArRuntimeCopy
import com.greenhands.app.ar.domain.ArRuntimePhase3C3B
import com.greenhands.app.ar.domain.ArTabletopUiState
import com.greenhands.app.ar.domain.EquipmentRuntimeEvaluator
import com.greenhands.app.ar.domain.EquipmentRuntimeInputSource
import com.greenhands.app.ar.domain.EquipmentRuntimeThresholds
import com.greenhands.app.equipment.data.EquipmentLayoutCodec
import com.greenhands.app.equipment.data.InMemoryEquipmentLayoutRepository
import com.greenhands.app.equipment.domain.PlacementSuggester
import com.greenhands.app.equipment.domain.PlacementValidator
import com.greenhands.app.equipment.model.DomainPoint
import com.greenhands.app.equipment.model.EquipmentAssetCatalog
import com.greenhands.app.equipment.model.EquipmentRuntimeState
import com.greenhands.app.equipment.model.EquipmentType
import com.greenhands.app.equipment.ui.EquipmentPlacementViewModel
import com.greenhands.app.equipment.ui.OverlayClimate
import com.greenhands.app.heat.domain.HeatFormulas
import com.greenhands.app.heat.model.CirculationThresholds
import com.greenhands.app.heat.model.ExhaustThresholds
import com.greenhands.app.heat.simulation.control.EquipmentController
import com.greenhands.app.heat.simulation.model.ControlSource
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ArRuntimePhaseContractTest {

    @Test
    fun phaseContractIsRuntimeControllerOnly() {
        assertEquals("Phase 3C.3B equipment runtime state controller", ArRuntimePhase3C3B.STATUS)
        assertFalse(ArRuntimePhase3C3B.STARTS_ON_DOUBLE_TAP)
        assertFalse(ArRuntimePhase3C3B.PERSISTS_WITH_LAYOUT)
        assertFalse(ArRuntimePhase3C3B.ANIMATES_GLB)
        assertEquals("Start Visualization", ArRuntimeCopy.START)
        assertEquals("Stop Visualization", ArRuntimeCopy.STOP)
        assertEquals("Visualization", ArRuntimeCopy.BEHAVIOR)
        assertEquals("Demo simulation — not a live IoT reading", ArRuntimeCopy.SIMULATED_DISCLAIMER)
        assertEquals("Demo simulation", ArRuntimeCopy.INPUT_DEMO)
        assertTrue(ArRuntimePhase3C3B.VISUALIZATION_SEPARATE_FROM_LIVE_MONITORING)
        assertTrue(ArRuntimePhase3C3B.STOP_HIDES_VISUAL_EFFECTS_ONLY)
        assertTrue(ArRuntimePhase3C3B.STOP_DOES_NOT_CONTROL_PHYSICAL_EQUIPMENT)
        assertFalse(ArTabletopUiState().sessionNote.contains("ON"))
        assertFalse(ArTabletopUiState().sessionNote.contains("runtime"))
    }
}

class EquipmentRuntimeTruthTableTest {

    private val circ = HeatFormulas.circulation(25.0)
    private val exhaust = HeatFormulas.exhaustFromCirculation(circ)
    private val fogger = HeatFormulas.fogger(70.0)

    @Test
    fun circulationBoundariesAndHysteresis() {
        assertEquals(23.0, circ.cdp, 0.0)
        assertEquals(27.0, circ.con, 0.0)
        assertEquals(
            EquipmentRuntimeState.ON,
            EquipmentRuntimeEvaluator.nextCirculation(EquipmentRuntimeState.OFF, 27.0, circ)
        )
        assertEquals(
            EquipmentRuntimeState.OFF,
            EquipmentRuntimeEvaluator.nextCirculation(EquipmentRuntimeState.OFF, 26.9, circ)
        )
        assertEquals(
            EquipmentRuntimeState.ON,
            EquipmentRuntimeEvaluator.nextCirculation(EquipmentRuntimeState.ON, 26.9, circ)
        )
        assertEquals(
            EquipmentRuntimeState.OFF,
            EquipmentRuntimeEvaluator.nextCirculation(EquipmentRuntimeState.ON, 23.0, circ)
        )
        assertEquals(
            EquipmentRuntimeState.ON,
            EquipmentRuntimeEvaluator.nextCirculation(EquipmentRuntimeState.ON, 23.1, circ)
        )
        assertEquals(
            EquipmentRuntimeState.OFF,
            EquipmentRuntimeEvaluator.nextCirculation(EquipmentRuntimeState.OFF, 23.1, circ)
        )
        assertEquals(
            EquipmentRuntimeState.OFF,
            EquipmentRuntimeEvaluator.nextCirculation(EquipmentRuntimeState.NOT_RUNNING, 25.0, circ)
        )
        assertEquals(
            EquipmentRuntimeState.ON,
            EquipmentRuntimeEvaluator.nextCirculation(EquipmentRuntimeState.ON, 25.0, circ)
        )
    }

    @Test
    fun exhaustBoundariesPreserveEdpEqualsCon() {
        assertEquals(circ.con, exhaust.edp, 0.0)
        assertEquals(31.0, exhaust.eon, 0.0)
        assertEquals(
            EquipmentRuntimeState.ON,
            EquipmentRuntimeEvaluator.nextExhaust(EquipmentRuntimeState.OFF, 31.0, exhaust)
        )
        assertEquals(
            EquipmentRuntimeState.OFF,
            EquipmentRuntimeEvaluator.nextExhaust(EquipmentRuntimeState.OFF, 30.9, exhaust)
        )
        assertEquals(
            EquipmentRuntimeState.ON,
            EquipmentRuntimeEvaluator.nextExhaust(EquipmentRuntimeState.ON, 30.9, exhaust)
        )
        assertEquals(
            EquipmentRuntimeState.OFF,
            EquipmentRuntimeEvaluator.nextExhaust(EquipmentRuntimeState.ON, 27.0, exhaust)
        )
        assertEquals(
            EquipmentRuntimeState.ON,
            EquipmentRuntimeEvaluator.nextExhaust(EquipmentRuntimeState.ON, 27.1, exhaust)
        )
        assertEquals(
            EquipmentRuntimeState.OFF,
            EquipmentRuntimeEvaluator.nextExhaust(EquipmentRuntimeState.NOT_RUNNING, 29.0, exhaust)
        )
    }

    @Test
    fun foggerBoundariesAndHysteresis() {
        assertEquals(68.0, fogger.fon, 0.0)
        assertEquals(72.0, fogger.fdp, 0.0)
        assertEquals(
            EquipmentRuntimeState.ON,
            EquipmentRuntimeEvaluator.nextFogger(EquipmentRuntimeState.OFF, 68.0, fogger)
        )
        assertEquals(
            EquipmentRuntimeState.OFF,
            EquipmentRuntimeEvaluator.nextFogger(EquipmentRuntimeState.OFF, 68.1, fogger)
        )
        assertEquals(
            EquipmentRuntimeState.ON,
            EquipmentRuntimeEvaluator.nextFogger(EquipmentRuntimeState.ON, 68.1, fogger)
        )
        assertEquals(
            EquipmentRuntimeState.OFF,
            EquipmentRuntimeEvaluator.nextFogger(EquipmentRuntimeState.ON, 72.0, fogger)
        )
        assertEquals(
            EquipmentRuntimeState.ON,
            EquipmentRuntimeEvaluator.nextFogger(EquipmentRuntimeState.ON, 71.9, fogger)
        )
        assertEquals(
            EquipmentRuntimeState.OFF,
            EquipmentRuntimeEvaluator.nextFogger(EquipmentRuntimeState.NOT_RUNNING, 70.0, fogger)
        )
    }

    @Test
    fun evaluatorMatchesPhase3AControllerWithoutChangingIt() {
        val controller = EquipmentController.fromTargets(25.0, 70.0, ControlSource.GREENHOUSE_MEAN)
        var circState = EquipmentRuntimeState.OFF
        var exhaustState = EquipmentRuntimeState.OFF
        var fogState = EquipmentRuntimeState.OFF
        val steps = listOf(
            25.0 to 70.0,
            26.9 to 70.0,
            27.0 to 70.0,
            24.0 to 70.0,
            23.0 to 70.0,
            30.9 to 70.0,
            31.0 to 70.0,
            27.1 to 70.0,
            27.0 to 70.0,
            25.0 to 68.1,
            25.0 to 68.0,
            25.0 to 71.0,
            25.0 to 72.0
        )
        steps.forEach { (temperature, humidity) ->
            circState = EquipmentRuntimeEvaluator.nextCirculation(circState, temperature, circ)
            exhaustState = EquipmentRuntimeEvaluator.nextExhaust(exhaustState, temperature, exhaust)
            fogState = EquipmentRuntimeEvaluator.nextFogger(fogState, humidity, fogger)
            controller.update(temperature, humidity)
            assertEquals(controller.circulationOn, circState == EquipmentRuntimeState.ON)
            assertEquals(controller.exhaustOn, exhaustState == EquipmentRuntimeState.ON)
            assertEquals(controller.foggerOn, fogState == EquipmentRuntimeState.ON)
        }
    }

    @Test
    fun uuidKeyedStatesStayIndependentInsideTheDeadband() {
        val fan1 = instance(EquipmentType.CIRCULATION_FAN, "fan-1", 1)
        val fan2 = instance(EquipmentType.CIRCULATION_FAN, "fan-2", 2)
        val thresholds = EquipmentRuntimeThresholds(circ, exhaust, fogger)
        val mixed = EquipmentRuntimeEvaluator.evaluateAll(
            instances = listOf(fan1, fan2),
            previousById = mapOf(
                fan1.id to EquipmentRuntimeState.ON,
                fan2.id to EquipmentRuntimeState.OFF
            ),
            thresholds = thresholds,
            temperatureC = 25.0,
            humidityPercent = 70.0
        )
        assertEquals(EquipmentRuntimeState.ON, mixed[fan1.id])
        assertEquals(EquipmentRuntimeState.OFF, mixed[fan2.id])
        val hud = ArHudContent.workingCopyStatus(listOf(fan1, fan2), mixed)
        assertEquals("Circulation Fan 1 — ON", hud.byId[fan1.id]?.displayLine())
        assertEquals("Circulation Fan 2 — OFF", hud.byId[fan2.id]?.displayLine())
        assertEquals("Circulation Fans (2) — 1 ON · 1 OFF", hud.aggregates[0].displayLine())
    }
}

class EquipmentRuntimeValidationTest {

    private val circ = HeatFormulas.circulation(25.0)
    private val exhaust = HeatFormulas.exhaustFromCirculation(circ)
    private val fogger = HeatFormulas.fogger(70.0)
    private val thresholds = EquipmentRuntimeThresholds(circ, exhaust, fogger)

    @Test
    fun missingAndInvalidThresholdsFailInsteadOfGuessing() {
        val fan = instance(EquipmentType.CIRCULATION_FAN, "c1", 1)
        val empty = EquipmentRuntimeThresholds(null, null, null)
        assertEquals(
            ArRuntimeCopy.MISSING_CIRCULATION,
            EquipmentRuntimeEvaluator.validate(listOf(fan), empty, 30.0, 70.0)
        )
        val invalidOrder = EquipmentRuntimeThresholds(
            CirculationThresholds(25.0, 27.0, 23.0),
            exhaust,
            fogger
        )
        assertEquals(
            ArRuntimeCopy.INVALID_THRESHOLDS,
            EquipmentRuntimeEvaluator.validate(listOf(fan), invalidOrder, 30.0, 70.0)
        )
        val exhaustFan = instance(EquipmentType.EXHAUST_FAN, "e1", 1)
        val brokenEdp = EquipmentRuntimeThresholds(
            circ,
            ExhaustThresholds(29.0, 26.0, 31.0),
            fogger
        )
        assertEquals(
            ArRuntimeCopy.EDP_EQUALS_CON,
            EquipmentRuntimeEvaluator.validate(listOf(exhaustFan), brokenEdp, 32.0, 70.0)
        )
        val fog = instance(EquipmentType.FOGGER, "f1", 1)
        assertEquals(
            ArRuntimeCopy.MISSING_FOGGER,
            EquipmentRuntimeEvaluator.validate(
                listOf(fog),
                EquipmentRuntimeThresholds(circ, exhaust, null),
                25.0,
                60.0
            )
        )
    }

    @Test
    fun missingReadingsFailForTheEquipmentThatNeedsThem() {
        val fan = instance(EquipmentType.CIRCULATION_FAN, "c1", 1)
        val fog = instance(EquipmentType.FOGGER, "f1", 1)
        assertEquals(
            ArRuntimeCopy.MISSING_TEMPERATURE,
            EquipmentRuntimeEvaluator.validate(listOf(fan), thresholds, null, 70.0)
        )
        assertEquals(
            ArRuntimeCopy.MISSING_HUMIDITY,
            EquipmentRuntimeEvaluator.validate(listOf(fog), thresholds, 25.0, null)
        )
        assertEquals(
            ArRuntimeCopy.MISSING_TEMPERATURE_AND_HUMIDITY,
            EquipmentRuntimeEvaluator.validate(listOf(fan, fog), thresholds, null, null)
        )
        assertNull(EquipmentRuntimeEvaluator.validate(listOf(fan), thresholds, 25.0, null))
        assertNull(EquipmentRuntimeEvaluator.validate(listOf(fog), thresholds, null, 70.0))
    }

    @Test
    fun myGreenhouseDisconnectedBlocksStartWithoutInventingReadings() {
        val fan = instance(EquipmentType.CIRCULATION_FAN, "c1", 1)
        val leaked = ArLiveEnvironmentReading(25.5, 75.0, connected = false)
        val input = EquipmentRuntimeEvaluator.runtimeInput(
            PreviewMode.MY_GREENHOUSE,
            simulatedTemperatureC = 25.5,
            simulatedHumidityPercent = 75.0,
            live = leaked
        )
        assertEquals(EquipmentRuntimeInputSource.MY_GREENHOUSE_LIVE, input.source)
        assertNull(input.temperatureC)
        assertNull(input.humidityPercent)
        assertEquals(
            ArRuntimeCopy.MY_GREENHOUSE_START_DISABLED,
            EquipmentRuntimeEvaluator.startBlockReason(
                listOf(fan),
                thresholds,
                input,
                PreviewMode.MY_GREENHOUSE
            )
        )
    }
}

class EquipmentRuntimeHudSummaryTest {

    @Test
    fun aggregateSummariesCoverOnOffAndNotRunning() {
        val fan1 = instance(EquipmentType.CIRCULATION_FAN, "c1", 1)
        val fan2 = instance(EquipmentType.CIRCULATION_FAN, "c2", 2)
        val exhaust = instance(EquipmentType.EXHAUST_FAN, "e1", 1)
        val fog = instance(EquipmentType.FOGGER, "f1", 1)
        val placed = listOf(fan1, fan2, exhaust, fog)
        val running = mapOf(
            fan1.id to EquipmentRuntimeState.ON,
            fan2.id to EquipmentRuntimeState.ON,
            exhaust.id to EquipmentRuntimeState.OFF,
            fog.id to EquipmentRuntimeState.ON
        )
        val hud = ArHudContent.workingCopyStatus(placed, running)
        assertEquals("Circulation Fans (2) — 2 ON", hud.aggregates[0].displayLine())
        assertEquals("Exhaust Fan (1) — 1 OFF", hud.aggregates[1].displayLine())
        assertEquals("Fogger (1) — 1 ON", hud.aggregates[2].displayLine())
        val mixed = ArHudContent.workingCopyStatus(
            listOf(fan1, fan2),
            mapOf(fan1.id to EquipmentRuntimeState.ON, fan2.id to EquipmentRuntimeState.OFF)
        )
        assertEquals("Circulation Fans (2) — 1 ON · 1 OFF", mixed.aggregates[0].displayLine())
        val idle = ArHudContent.workingCopyStatus(placed)
        idle.instances.forEach { assertEquals(ArHudContent.NOT_RUNNING, it.state) }
        assertEquals("Circulation Fans (2) — Not running", idle.aggregates[0].displayLine())
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class EquipmentRuntimeSessionTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val circ = HeatFormulas.circulation(25.0)
    private val climate = OverlayClimate(
        crop = null,
        stage = null,
        temperatureLabel = "—",
        humidityLabel = "—",
        vpdLabel = "Not published",
        circulation = circ,
        exhaust = HeatFormulas.exhaustFromCirculation(circ),
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
    fun startStopAndDemoSliderReevaluation() = runTest {
        val vm = demoVm("cfg-runtime-start")
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.CIRCULATION_FAN)
        vm.duplicateSelected()
        vm.tapPalette(EquipmentType.EXHAUST_FAN)
        placeFogger(vm)
        assertEquals(
            listOf("Circulation Fan 1", "Circulation Fan 2", "Exhaust Fan 1", "Fogger 1"),
            vm.state.value.instances.map { it.displayLabel() }
        )
        assertEquals(25.0, vm.state.value.simulatedTemperatureC)
        assertEquals(70.0, vm.state.value.simulatedHumidityPercent)
        vm.startBehavior()
        val started = vm.state.value
        assertTrue(started.behaviorRunning)
        assertNull(started.behaviorError)
        started.runtimeById.values.forEach { assertEquals(EquipmentRuntimeState.OFF, it) }
        assertEquals(
            "Circulation Fans (2) — 2 Simulated OFF",
            hud(vm).equipment[0].displayLine()
        )

        vm.setSimulatedTemperature(27.0)
        assertEquals(EquipmentRuntimeState.ON, vm.state.value.runtimeById[fanId(vm, 1)])
        assertEquals(EquipmentRuntimeState.ON, vm.state.value.runtimeById[fanId(vm, 2)])
        assertEquals("Circulation Fans (2) — 2 Simulated ON", hud(vm).equipment[0].displayLine())

        vm.setSimulatedTemperature(25.0)
        assertEquals(EquipmentRuntimeState.ON, vm.state.value.runtimeById[fanId(vm, 1)])
        vm.setSimulatedTemperature(31.0)
        val exhaustId = vm.state.value.instances.single { it.type == EquipmentType.EXHAUST_FAN }.id
        assertEquals(EquipmentRuntimeState.ON, vm.state.value.runtimeById[exhaustId])
        vm.setSimulatedHumidity(68.0)
        val fogId = vm.state.value.instances.single { it.type == EquipmentType.FOGGER }.id
        assertEquals(EquipmentRuntimeState.ON, vm.state.value.runtimeById[fogId])

        vm.stopBehavior()
        assertFalse(vm.state.value.behaviorRunning)
        vm.state.value.runtimeById.values.forEach { assertEquals(EquipmentRuntimeState.NOT_RUNNING, it) }
        assertEquals("Circulation Fan 1 — Not running", hud(vm).instanceStatuses.first().displayLine())
        assertEquals("Exhaust Fan 1 — Not running", hud(vm).instanceStatuses[2].displayLine())
        assertEquals("Fogger 1 — Not running", hud(vm).instanceStatuses[3].displayLine())
    }

    @Test
    fun newInstanceKeepsIndependentStateWhileRunning() = runTest {
        val vm = demoVm("cfg-runtime-independent")
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.CIRCULATION_FAN)
        vm.duplicateSelected()
        vm.startBehavior()
        vm.setSimulatedTemperature(27.0)
        vm.setSimulatedTemperature(25.0)
        assertEquals(EquipmentRuntimeState.ON, vm.state.value.runtimeById[fanId(vm, 1)])
        assertEquals(EquipmentRuntimeState.ON, vm.state.value.runtimeById[fanId(vm, 2)])
        vm.duplicateSelected()
        val fan3 = vm.state.value.instances.single { it.displayNumber == 3 }
        assertEquals(EquipmentRuntimeState.OFF, vm.state.value.runtimeById[fan3.id])
        assertEquals(EquipmentRuntimeState.ON, vm.state.value.runtimeById[fanId(vm, 1)])
        assertEquals("Circulation Fans (3) — 2 Simulated ON · 1 Simulated OFF", hud(vm).equipment[0].displayLine())
        assertEquals(3, vm.state.value.runtimeById.size)
    }

    @Test
    fun runtimeIsSessionOnlyAndStrippedFromSavedLayouts() = runTest {
        val repo = InMemoryEquipmentLayoutRepository()
        val vm = EquipmentPlacementViewModel(repo)
        attach(vm, "cfg-runtime-save", PreviewMode.MY_GREENHOUSE)
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.CIRCULATION_FAN)
        vm.onLiveEnvironment(
            ArLiveEnvironmentReading(temperatureC = 27.0, relativeHumidityPercent = 60.0, connected = true)
        )
        vm.startBehavior()
        val id = vm.state.value.instances.single().id
        assertEquals(EquipmentRuntimeState.ON, vm.state.value.runtimeById[id])
        assertEquals(EquipmentRuntimeState.NOT_RUNNING, vm.state.value.instances.single().runtimeState)
        vm.saveLayout()
        val stored = repo.snapshot("grower@greenhands.app").single()
        assertEquals(EquipmentRuntimeState.NOT_RUNNING, stored.instances.single().runtimeState)
        val encoded = EquipmentLayoutCodec.encode(stored)
        assertTrue(encoded.contains("NOT_RUNNING"))
        assertFalse(encoded.contains(",ON,"))
        val onInstance = stored.instances.single().copy(runtimeState = EquipmentRuntimeState.ON)
        val encodedOn = EquipmentLayoutCodec.encode(stored.copy(instances = listOf(onInstance)))
        assertTrue(encodedOn.contains("NOT_RUNNING"))
        assertFalse(encodedOn.contains(",ON,"))
        val decoded = EquipmentLayoutCodec.decode(encodedOn)!!
        assertEquals(EquipmentRuntimeState.NOT_RUNNING, decoded.instances.single().runtimeState)
        assertEquals(EquipmentRuntimeState.ON, vm.state.value.runtimeById[id])
        assertTrue(vm.state.value.behaviorRunning)
    }

    @Test
    fun myGreenhouseCannotStartUntilALiveReadingExists() = runTest {
        val vm = EquipmentPlacementViewModel(InMemoryEquipmentLayoutRepository())
        attach(vm, "cfg-runtime-live", PreviewMode.MY_GREENHOUSE)
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.CIRCULATION_FAN)
        assertEquals(ArRuntimeCopy.MY_GREENHOUSE_START_DISABLED, vm.startBlockReason())
        vm.startBehavior()
        assertFalse(vm.state.value.behaviorRunning)
        assertEquals(ArRuntimeCopy.MY_GREENHOUSE_START_DISABLED, vm.state.value.behaviorError)
        vm.state.value.runtimeById.values.forEach { assertEquals(EquipmentRuntimeState.NOT_RUNNING, it) }

        vm.onLiveEnvironment(
            ArLiveEnvironmentReading(temperatureC = 31.0, relativeHumidityPercent = 60.0, connected = true)
        )
        assertNull(vm.startBlockReason())
        vm.startBehavior()
        assertTrue(vm.state.value.behaviorRunning)
        assertEquals(EquipmentRuntimeState.ON, vm.state.value.runtimeById.values.single())

        vm.onLiveEnvironment(ArLiveEnvironmentReading.DISCONNECTED)
        assertFalse(vm.state.value.behaviorRunning)
        assertEquals(ArRuntimeCopy.INPUT_UNAVAILABLE, vm.state.value.behaviorError)
        assertEquals(EquipmentRuntimeState.NOT_RUNNING, vm.state.value.runtimeById.values.single())
    }

    @Test
    fun leavingArAndChangingConfigurationStopSafely() = runTest {
        val vm = demoVm("cfg-runtime-leave")
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.CIRCULATION_FAN)
        vm.startBehavior()
        vm.setSimulatedTemperature(27.0)
        assertTrue(vm.state.value.behaviorRunning)
        vm.onLeaveArScreen()
        assertFalse(vm.state.value.behaviorRunning)
        assertEquals(EquipmentRuntimeState.NOT_RUNNING, vm.state.value.runtimeById.values.single())

        vm.startBehavior()
        vm.setSimulatedTemperature(27.0)
        assertTrue(vm.state.value.behaviorRunning)
        attach(vm, "cfg-runtime-other", PreviewMode.DEMO)
        assertFalse(vm.state.value.behaviorRunning)
        assertEquals(ArRuntimeCopy.CONFIGURATION_CHANGED, vm.state.value.behaviorError)
        vm.state.value.runtimeById.values.forEach { assertEquals(EquipmentRuntimeState.NOT_RUNNING, it) }
    }

    @Test
    fun invalidThresholdsBlockStart() = runTest {
        val broken = climate.copy(
            circulation = CirculationThresholds(25.0, 28.0, 23.0)
        )
        val vm = EquipmentPlacementViewModel(InMemoryEquipmentLayoutRepository())
        vm.attach(
            "grower@greenhands.app",
            "cfg-runtime-invalid",
            DemoGreenhouseProfile.LENGTH_M,
            DemoGreenhouseProfile.WIDTH_M,
            DemoGreenhouseProfile.HEIGHT_M,
            DemoGreenhouseProfile.CROP_ROW_COUNT,
            broken,
            PreviewMode.DEMO
        )
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.CIRCULATION_FAN)
        vm.startBehavior()
        assertFalse(vm.state.value.behaviorRunning)
        assertEquals(ArRuntimeCopy.INVALID_THRESHOLDS, vm.state.value.behaviorError)
    }

    private fun demoVm(configId: String): EquipmentPlacementViewModel {
        val vm = EquipmentPlacementViewModel(InMemoryEquipmentLayoutRepository())
        attach(vm, configId, PreviewMode.DEMO)
        return vm
    }

    private fun attach(vm: EquipmentPlacementViewModel, configId: String, mode: PreviewMode) {
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

    private fun hud(vm: EquipmentPlacementViewModel) = ArHudContent.from(
        climate,
        PreviewMode.DEMO,
        instances = vm.state.value.instances,
        runtimeById = vm.state.value.runtimeById,
        simulatedTemperatureC = vm.state.value.simulatedTemperatureC,
        simulatedHumidityPercent = vm.state.value.simulatedHumidityPercent,
        visualizationRunning = vm.state.value.behaviorRunning,
        actualRelayById = vm.state.value.actualRelayById
    )

    private fun fanId(vm: EquipmentPlacementViewModel, number: Int): String =
        vm.state.value.instances.single { it.type == EquipmentType.CIRCULATION_FAN && it.displayNumber == number }.id

    private fun placeFogger(vm: EquipmentPlacementViewModel) {
        val ctx = vm.placementContext()
        val x = PlacementSuggester.aisleCentres(ctx).first()
        val z = PlacementValidator.overheadZ(ctx)
        vm.beginPaletteDrag(EquipmentType.FOGGER)
        vm.paletteDragTo(EquipmentType.FOGGER, DomainPoint(x, 1.8, z))
        vm.endPaletteDrag(overViewport = true)
    }
}

private fun instance(type: EquipmentType, id: String, number: Int) =
    EquipmentAssetCatalog.newInstance(
        type = type,
        id = id,
        positionM = DomainPoint(1.0, 1.0, 2.0),
        yawDeg = 0.0,
        displayNumber = number
    )
