package com.greenhands.app.heat.visualization

import com.greenhands.app.ar.domain.ArRuntimeCopy
import com.greenhands.app.ar.domain.MyGreenhousePersistence
import com.greenhands.app.equipment.data.EquipmentLayoutCodec
import com.greenhands.app.equipment.data.InMemoryEquipmentLayoutRepository
import com.greenhands.app.equipment.model.EquipmentLayout
import com.greenhands.app.equipment.model.EquipmentType
import com.greenhands.app.equipment.ui.EquipmentPlacementViewModel
import com.greenhands.app.equipment.ui.OverlayClimate
import com.greenhands.app.heat.domain.HeatFormulas
import com.greenhands.app.heat.runtime.PhysicsFieldRuntime
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
class EnvironmentalFieldStateTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val circ = HeatFormulas.circulation(25.0)
    private val exhaust = HeatFormulas.exhaustFromCirculation(circ)
    private val fogger = HeatFormulas.fogger(70.0)
    private val climate = OverlayClimate(
        crop = com.greenhands.app.heat.model.Crop.TOMATO,
        stage = com.greenhands.app.heat.profile.CropProfileRegistry.stagesFor(
            com.greenhands.app.heat.model.Crop.TOMATO
        ).first { it.id == "vegetative" },
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
    }

    @After
    fun reset() {
        Dispatchers.resetMain()
    }

    @Test
    fun temperatureIsTheInitialDemoSessionSelectionAndLayersAreExclusive() = runTest {
        val vm = demoVm("cfg-field-initial")
        assertEquals(EnvironmentalFieldLayer.TEMPERATURE, vm.state.value.environmentalLayer)
        assertEquals(EnvironmentalFieldHeight.WHOLE, vm.state.value.fieldHeight)
        vm.setEnvironmentalLayer(EnvironmentalFieldLayer.HUMIDITY)
        assertEquals(EnvironmentalFieldLayer.HUMIDITY, vm.state.value.environmentalLayer)
        vm.setEnvironmentalLayer(EnvironmentalFieldLayer.OFF)
        assertTrue(vm.environmentalFieldFrame().isEmpty)
        assertEquals(EnvironmentalFieldAvailability.LAYER_OFF, vm.environmentalFieldFrame().availability)
        vm.setEnvironmentalLayer(EnvironmentalFieldLayer.TEMPERATURE)
        assertEquals(EnvironmentalFieldLayer.TEMPERATURE, vm.state.value.environmentalLayer)
        assertNotEquals(EnvironmentalFieldLayer.HUMIDITY, vm.state.value.environmentalLayer)
    }

    @Test
    fun noFieldIsRenderedBeforeStartAndStartShowsTheSelectedField() = runTest {
        val vm = demoVm("cfg-field-start")
        val before = vm.environmentalFieldFrame()
        assertTrue(before.isEmpty)
        assertEquals(EnvironmentalFieldAvailability.HIDDEN_BEFORE_START, before.availability)
        assertEquals(VisualizationCopy.FIELD_START_TO_DISPLAY, before.message)
        assertEquals(VisualizationCopy.PREDICTED_UNCALIBRATED, before.legend.predictionLabel)
        vm.setSimulatedTemperature(28.0)
        vm.setSimulatedHumidity(55.0)
        vm.startBehavior()
        val after = vm.environmentalFieldFrame()
        assertFalse(after.isEmpty)
        assertEquals(EnvironmentalFieldAvailability.AVAILABLE, after.availability)
        assertTrue(after.cellCount > 0)
        assertTrue(after.cellCount <= EnvironmentalFieldVisual.MAX_CELLS)
        assertTrue(after.activeNodeCount <= EnvironmentalFieldVisual.MAX_GEOMETRY_NODES)
        assertEquals(EnvironmentalFieldLayer.TEMPERATURE, after.layer)
        assertNotNull(vm.state.value.physicsSnapshot)
    }

    @Test
    fun heightFiltersSelectTheCorrectSamples() = runTest {
        val vm = startedVm("cfg-field-height")
        val whole = vm.environmentalFieldFrame()
        val levels = whole.visibleSamples.map { it.level }.toSet()
        assertTrue(levels.contains(EnvironmentalFieldLevel.CROP))
        assertTrue(levels.contains(EnvironmentalFieldLevel.MIDDLE))
        assertTrue(levels.contains(EnvironmentalFieldLevel.UPPER))
        vm.setFieldHeight(EnvironmentalFieldHeight.CROP)
        assertTrue(vm.environmentalFieldFrame().visibleSamples.all { it.level == EnvironmentalFieldLevel.CROP })
        vm.setFieldHeight(EnvironmentalFieldHeight.MIDDLE)
        assertTrue(vm.environmentalFieldFrame().visibleSamples.all { it.level == EnvironmentalFieldLevel.MIDDLE })
        vm.setFieldHeight(EnvironmentalFieldHeight.UPPER)
        assertTrue(vm.environmentalFieldFrame().visibleSamples.all { it.level == EnvironmentalFieldLevel.UPPER })
    }

    @Test
    fun switchingLayerOrHeightDoesNotAdvanceTheSolver() = runTest {
        val vm = startedVm("cfg-field-switch")
        val elapsed = vm.state.value.physicsSnapshot!!.elapsedTimeS
        val identity = vm.state.value.physicsSnapshot
        vm.setEnvironmentalLayer(EnvironmentalFieldLayer.HUMIDITY)
        val humidity = vm.environmentalFieldFrame()
        assertEquals(EnvironmentalFieldLayer.HUMIDITY, humidity.layer)
        assertEquals("%RH", humidity.legend.unit)
        assertEquals(VisualizationCopy.FIELD_HUMIDITY_TITLE, humidity.legend.title)
        assertEquals(elapsed, vm.state.value.physicsSnapshot!!.elapsedTimeS, 0.0)
        assertTrue(vm.state.value.physicsSnapshot === identity)
        vm.setFieldHeight(EnvironmentalFieldHeight.CROP)
        assertEquals(elapsed, vm.state.value.physicsSnapshot!!.elapsedTimeS, 0.0)
        assertTrue(vm.state.value.physicsSnapshot === identity)
        vm.setEnvironmentalLayer(EnvironmentalFieldLayer.TEMPERATURE)
        assertEquals(VisualizationCopy.FIELD_UNIT_C, vm.environmentalFieldFrame().legend.unit)
    }

    @Test
    fun stopClearsFieldEffectsButKeepsTheFixedPadPanel() = runTest {
        val vm = demoVm("cfg-field-stop")
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.EXHAUST_FAN)
        vm.setSimulatedTemperature(28.0)
        vm.setSimulatedHumidity(55.0)
        vm.startBehavior()
        assertFalse(vm.environmentalFieldFrame().isEmpty)
        val padBefore = vm.padInletEffectFrame()
        assertNotNull(padBefore.panel)
        vm.stopBehavior()
        assertTrue(vm.environmentalFieldFrame().isEmpty)
        assertNull(vm.state.value.physicsSnapshot)
        val padAfter = vm.padInletEffectFrame()
        assertNotNull(padAfter.panel)
        assertTrue(PadInletVisual.PANEL_IS_FIXED_STRUCTURE)
        assertTrue(PadInletVisual.PANEL_REMAINS_WHEN_EXHAUST_OFF)
    }

    @Test
    fun staleSnapshotsAreIgnored() {
        val snap = PhysicsFieldRuntime().run {
            val started = start(
                com.greenhands.app.heat.runtime.Phase3D2AFixtures.demoRequest(
                    emptyList(),
                    25.0,
                    70.0,
                    weather = com.greenhands.app.heat.runtime.contracts.WeatherBoundary.demoSimulated(25.0, 70.0),
                    lengthM = DemoGreenhouseProfile.LENGTH_M,
                    widthM = DemoGreenhouseProfile.WIDTH_M,
                    heightM = DemoGreenhouseProfile.HEIGHT_M
                )
            )
            stop()
            started
        }
        val built = EnvironmentalFieldBuilder.build(
            physics = snap,
            previewMode = PreviewMode.DEMO,
            widthM = DemoGreenhouseProfile.WIDTH_M,
            lengthM = DemoGreenhouseProfile.LENGTH_M,
            heightM = DemoGreenhouseProfile.HEIGHT_M,
            canopyHeightM = 0.78,
            thresholds = EnvironmentalFieldThresholds(
                25.0, circ.cdp, circ.con, exhaust.eon, fogger.fon, fogger.fsp, fogger.fdp
            ),
            cropName = "Tomato",
            stageName = "Vegetative",
            generation = 4,
            visualizationRunning = true
        )
        val stale = EnvironmentalFieldPlanner.plan(
            built,
            EnvironmentalFieldLayer.TEMPERATURE,
            EnvironmentalFieldHeight.WHOLE,
            generation = 9,
            legendExpanded = false
        )
        assertTrue(stale.isEmpty)
        assertEquals(EnvironmentalFieldAvailability.UNAVAILABLE, stale.availability)
        assertEquals(VisualizationCopy.FIELD_STALE_SNAPSHOT, stale.message)
    }

    @Test
    fun invalidMyGreenhouseShowsUnavailableRatherThanDemoDefaults() {
        val missing = EnvironmentalFieldBuilder.build(
            physics = null,
            previewMode = PreviewMode.MY_GREENHOUSE,
            widthM = 8.0,
            lengthM = 12.0,
            heightM = 3.5,
            canopyHeightM = 0.9,
            thresholds = EnvironmentalFieldThresholds(null, null, null, null, null, null, null),
            cropName = VisualizationCopy.COLOUR_UNAVAILABLE,
            stageName = VisualizationCopy.COLOUR_UNAVAILABLE,
            generation = 1,
            visualizationRunning = true
        )
        assertEquals(EnvironmentalFieldAvailability.UNAVAILABLE, missing.availability)
        assertEquals(VisualizationCopy.FIELD_MY_GREENHOUSE_UNAVAILABLE, missing.unavailableReason)
        assertTrue(missing.samples.isEmpty())
        assertFalse(missing.widthM == DemoGreenhouseProfile.WIDTH_M && missing.samples.isNotEmpty())

        val noThresholds = EnvironmentalFieldBuilder.build(
            physics = null,
            previewMode = PreviewMode.MY_GREENHOUSE,
            widthM = 8.0,
            lengthM = 12.0,
            heightM = 3.5,
            canopyHeightM = 0.9,
            thresholds = EnvironmentalFieldThresholds(null, null, null, null, null, null, null),
            cropName = VisualizationCopy.COLOUR_UNAVAILABLE,
            stageName = VisualizationCopy.COLOUR_UNAVAILABLE,
            generation = 1,
            visualizationRunning = false
        )
        assertEquals(EnvironmentalFieldAvailability.HIDDEN_BEFORE_START, noThresholds.availability)
        assertEquals(8.0, noThresholds.widthM, 0.0)
        assertEquals(12.0, noThresholds.lengthM, 0.0)
        assertNotEquals(DemoGreenhouseProfile.LENGTH_M, noThresholds.lengthM, 0.0)
    }

    @Test
    fun predictedUncalibratedIsPresentAndDemoIsNeverLiveIot() = runTest {
        val vm = startedVm("cfg-field-label")
        val legend = vm.environmentalFieldLegend()
        assertEquals(VisualizationCopy.PREDICTED_UNCALIBRATED, legend.predictionLabel)
        assertFalse(legend.showsLiveIot)
        assertFalse(legend.title.contains(VisualizationCopy.LIVE_IOT_FORBIDDEN_LABEL))
        assertFalse(legend.predictionLabel.contains("Live IoT", ignoreCase = true))
        assertFalse(legend.predictionLabel.contains(ArRuntimeCopy.INPUT_LIVE))
        assertEquals(VisualizationCopy.FIELD_TEMPERATURE_TITLE, legend.title)
        assertEquals(VisualizationCopy.FIELD_UNIT_C, legend.unit)
        assertTrue(legend.cropStageText.contains("Tomato"))
        vm.setEnvironmentalLayer(EnvironmentalFieldLayer.HUMIDITY)
        assertEquals(VisualizationCopy.FIELD_UNIT_RH, vm.environmentalFieldLegend().unit)
        assertEquals("%RH", vm.environmentalFieldLegend().unit)
    }

    @Test
    fun layerPreferenceIsSessionOnlyAndNotPersisted() = runTest {
        val vm = startedVm("cfg-field-persist")
        vm.setEnvironmentalLayer(EnvironmentalFieldLayer.HUMIDITY)
        vm.setFieldHeight(EnvironmentalFieldHeight.CROP)
        val encoded = EquipmentLayoutCodec.encode(
            MyGreenhousePersistence.persistableLayout(
                EquipmentLayout(
                    layoutId = "lay",
                    configurationId = "cfg-field-persist",
                    userId = "grower@greenhands.app",
                    lengthM = DemoGreenhouseProfile.LENGTH_M,
                    widthM = DemoGreenhouseProfile.WIDTH_M,
                    heightM = DemoGreenhouseProfile.HEIGHT_M,
                    cropRowCount = DemoGreenhouseProfile.CROP_ROW_COUNT,
                    instances = vm.state.value.instances
                )
            )
        )
        assertFalse(encoded.contains("environmentalLayer"))
        assertFalse(encoded.contains("HUMIDITY"))
        assertFalse(encoded.contains("fieldHeight"))
        assertFalse(Phase3D2C.PERSISTS_LAYER_PREFERENCE)
        assertFalse(MyGreenhousePersistence.containsForbiddenPersistedState(encoded))
    }

    @Test
    fun phaseContractKeepsFrozenBoundaries() {
        assertEquals(
            "Phase 3D.2C whole-greenhouse temperature and humidity field visualization",
            Phase3D2C.STATUS
        )
        assertTrue(Phase3D2C.RENDERS_FIELD_LAYERS)
        assertTrue(Phase3D2C.ADDS_LAYER_LEGEND)
        assertFalse(Phase3D2C.ADDS_NETWORKING)
        assertFalse(Phase3D2C.CHANGES_PHASE_3A)
        assertFalse(Phase3D2C.CHANGES_PHASE_3D2A)
        assertFalse(Phase3D2C.CHANGES_CIRCULATION_RINGS)
        assertFalse(Phase3D2C.CHANGES_EXHAUST_ARROWS)
        assertFalse(Phase3D2C.CHANGES_COOLING_PAD)
        assertFalse(Phase3D2C.CHANGES_FOGGER_MIST)
        assertFalse(Phase3D2C.PUBLIC_FOGGER_PROBES)
        assertFalse(Phase3D2C.PUBLIC_CIRCULATION_PROBES)
        assertFalse(Phase3D2B3.RENDERS_FIELD_LAYERS)
        assertFalse(Phase3D2B2.RENDERS_FIELD_LAYERS)
        assertFalse(Phase3D2B1.RENDERS_FIELD_LAYERS)
        assertEquals("TEMPERATURE", EnvironmentalFieldVisual.INITIAL_DEMO_LAYER)
        assertTrue(EnvironmentalFieldVisual.ONE_LAYER_AT_A_TIME)
    }

    private fun startedVm(configId: String): EquipmentPlacementViewModel {
        val vm = demoVm(configId)
        vm.setSimulatedTemperature(28.0)
        vm.setSimulatedHumidity(55.0)
        vm.startBehavior()
        return vm
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
