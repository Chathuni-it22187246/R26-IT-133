package com.greenhands.app.ar

import com.greenhands.app.ar.domain.ArHudContent
import com.greenhands.app.ar.domain.ArLiveEnvironmentReading
import com.greenhands.app.ar.domain.ArRuntimeCopy
import com.greenhands.app.ar.domain.ArTabletopUiState
import com.greenhands.app.ar.domain.EquipmentRuntimeEvaluator
import com.greenhands.app.ar.domain.EquipmentRuntimeInputSource
import com.greenhands.app.ar.domain.EquipmentStateProvenance
import com.greenhands.app.ar.domain.EquipmentStateSource
import com.greenhands.app.ar.domain.MyGreenhousePersistence
import com.greenhands.app.equipment.data.EquipmentLayoutCodec
import com.greenhands.app.equipment.data.InMemoryEquipmentLayoutRepository
import com.greenhands.app.equipment.model.DomainPoint
import com.greenhands.app.equipment.model.EquipmentAssetCatalog
import com.greenhands.app.equipment.model.EquipmentLayout
import com.greenhands.app.equipment.model.EquipmentRuntimeState
import com.greenhands.app.equipment.model.EquipmentType
import com.greenhands.app.equipment.model.LayoutOrigin
import com.greenhands.app.equipment.ui.EquipmentPlacementViewModel
import com.greenhands.app.equipment.ui.OverlayClimate
import com.greenhands.app.heat.data.HeatConfigCodec
import com.greenhands.app.heat.data.HeatSessionCodec
import com.greenhands.app.heat.data.HeatSessionRecord
import com.greenhands.app.heat.data.InMemoryHeatConfigRepository
import com.greenhands.app.heat.domain.HeatFormulas
import com.greenhands.app.heat.model.Crop
import com.greenhands.app.heat.profile.CropProfileRegistry
import com.greenhands.app.heat.ui.HeatConfigViewModel
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
class ArVisualizationCorrectionTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val circ = HeatFormulas.circulation(25.0)
    private val climate = OverlayClimate(
        crop = Crop.TOMATO,
        stage = CropProfileRegistry.stageProfile(Crop.TOMATO, "vegetative").stage,
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
    fun demoHudReceivesSimulatedValuesAndDoesNotShowSensorDisconnected() {
        val hud = ArHudContent.from(
            climate,
            PreviewMode.DEMO,
            simulatedTemperatureC = 26.4,
            simulatedHumidityPercent = 68.5
        )
        assertEquals("26.4 °C", hud.liveTemperatureLabel)
        assertEquals("68.5 % RH", hud.liveHumidityLabel)
        assertEquals(ArHudContent.DEMO_SIMULATION, hud.liveStatusLabel)
        assertEquals("Demo simulation", hud.liveStatusLabel)
        assertNotEquals(ArHudContent.SENSOR_NOT_CONNECTED, hud.liveStatusLabel)
        assertFalse(hud.allUserCopy().any { it.contains(ArHudContent.SENSOR_NOT_CONNECTED) })
        hud.allUserCopy().forEach { line ->
            assertFalse(ArHudContent.hasDuplicatedPercentUnit(line))
        }
        assertEquals("Demo simulation", EquipmentRuntimeEvaluator.runtimeInput(
            PreviewMode.DEMO,
            26.4,
            68.5,
            ArLiveEnvironmentReading.DISCONNECTED
        ).sourceLabel())
        assertEquals(ArRuntimeCopy.SIMULATED_DISCLAIMER, "Demo simulation — not a live IoT reading")
    }

    @Test
    fun demoControllerHudUsesSimulatedValuesFromTheViewModel() = runTest {
        val vm = EquipmentPlacementViewModel(InMemoryEquipmentLayoutRepository())
        attach(vm, "cfg-demo-hud", PreviewMode.DEMO)
        assertEquals(25.0, vm.state.value.simulatedTemperatureC)
        assertEquals(70.0, vm.state.value.simulatedHumidityPercent)
        val hud = hud(vm)
        assertEquals("25.0 °C", hud.liveTemperatureLabel)
        assertEquals("70.0 % RH", hud.liveHumidityLabel)
        assertEquals("Demo simulation", hud.liveStatusLabel)
        assertFalse(hud.liveHumidityLabel.contains("% %"))
        vm.setSimulatedTemperature(27.0)
        vm.setSimulatedHumidity(64.0)
        val updated = hud(vm)
        assertEquals("27.0 °C", updated.liveTemperatureLabel)
        assertEquals("64.0 % RH", updated.liveHumidityLabel)
        assertEquals("Demo simulation", updated.liveStatusLabel)
    }

    @Test
    fun myGreenhouseHudKeepsDisconnectedPlaceholdersAndIgnoresSimulatedValues() {
        val hud = ArHudContent.from(
            climate,
            PreviewMode.MY_GREENHOUSE,
            live = ArLiveEnvironmentReading.DISCONNECTED,
            simulatedTemperatureC = 26.4,
            simulatedHumidityPercent = 68.5
        )
        assertEquals("-- °C", hud.liveTemperatureLabel)
        assertEquals("-- % RH", hud.liveHumidityLabel)
        assertEquals("Sensor not connected", hud.liveStatusLabel)
        assertNull(ArLiveEnvironmentReading.DISCONNECTED.temperatureC)
        assertNull(ArLiveEnvironmentReading.DISCONNECTED.relativeHumidityPercent)
        assertFalse(ArLiveEnvironmentReading.DISCONNECTED.connected)
        hud.allUserCopy().forEach { line ->
            assertFalse(ArHudContent.hasDuplicatedPercentUnit(line))
        }
    }

    @Test
    fun humidityLabelsNeverDuplicatePercentUnit() {
        assertFalse(ArHudContent.hasDuplicatedPercentUnit("70.0 % RH"))
        assertFalse(ArHudContent.hasDuplicatedPercentUnit("-- % RH"))
        assertTrue(ArHudContent.hasDuplicatedPercentUnit("70.0 % % RH"))
        assertEquals("70.0 % RH", ArHudContent.humidityReadingLabel(70.0))
        assertEquals("-- % RH", ArHudContent.humidityReadingLabel(null))
        assertEquals("Optimal: 25.0°C · 70.0 % RH", climate.compactOptimalLabel())
        assertFalse(ArHudContent.hasDuplicatedPercentUnit(climate.compactOptimalLabel()))
    }

    @Test
    fun simulatedPredictedAndActualTerminology() {
        assertEquals(
            EquipmentStateProvenance.SIMULATED,
            EquipmentStateSource.forInput(EquipmentRuntimeInputSource.DEMO_SIMULATED)
        )
        assertEquals(
            EquipmentStateProvenance.PREDICTED,
            EquipmentStateSource.forInput(EquipmentRuntimeInputSource.MY_GREENHOUSE_LIVE)
        )
        assertEquals(
            EquipmentStateProvenance.ACTUAL,
            EquipmentStateSource.forInput(
                EquipmentRuntimeInputSource.MY_GREENHOUSE_LIVE,
                deviceTelemetryReported = true
            )
        )
        assertFalse(
            EquipmentStateSource.claimsActualState(
                EquipmentStateSource.forMode(PreviewMode.DEMO)
            )
        )
        assertFalse(
            EquipmentStateSource.claimsActualState(
                EquipmentStateSource.forMode(PreviewMode.MY_GREENHOUSE)
            )
        )
        assertEquals(
            "Simulated ON",
            EquipmentRuntimeEvaluator.label(EquipmentRuntimeState.ON, EquipmentStateProvenance.SIMULATED)
        )
        assertEquals(
            "Predicted OFF",
            EquipmentRuntimeEvaluator.label(EquipmentRuntimeState.OFF, EquipmentStateProvenance.PREDICTED)
        )
        assertEquals(
            "Actual ON",
            EquipmentRuntimeEvaluator.label(EquipmentRuntimeState.ON, EquipmentStateProvenance.ACTUAL)
        )
        assertEquals(
            "Not running",
            EquipmentRuntimeEvaluator.label(EquipmentRuntimeState.NOT_RUNNING, EquipmentStateProvenance.ACTUAL)
        )
        val fan = placedFan("fan-demo")
        val running = mapOf(fan.id to EquipmentRuntimeState.ON)
        val demoHud = ArHudContent.workingCopyStatus(
            listOf(fan),
            running,
            EquipmentStateProvenance.SIMULATED
        )
        assertEquals("Circulation Fan 1 — Simulated ON", demoHud.instances.single().displayLine())
        assertTrue(ArHudContent.usesOnOffRuntimeLabel(demoHud.instances.single().state))
        val predicted = ArHudContent.workingCopyStatus(
            listOf(fan),
            running,
            EquipmentStateProvenance.PREDICTED
        )
        assertEquals("Circulation Fan 1 — Predicted ON", predicted.instances.single().displayLine())
        assertFalse(predicted.instances.single().state.startsWith("Actual"))
    }

    @Test
    fun demoCannotPersistLayoutChangesAndDoesNotDeleteOldStoredData() = runTest {
        val repo = InMemoryEquipmentLayoutRepository()
        val old = EquipmentLayout(
            layoutId = "old-demo",
            configurationId = "cfg-demo-old",
            userId = "grower@greenhands.app",
            lengthM = DemoGreenhouseProfile.LENGTH_M,
            widthM = DemoGreenhouseProfile.WIDTH_M,
            heightM = DemoGreenhouseProfile.HEIGHT_M,
            cropRowCount = DemoGreenhouseProfile.CROP_ROW_COUNT,
            instances = listOf(placedFan("old-fan")),
            origin = LayoutOrigin.MANUAL,
            explicitlySaved = true
        )
        repo.save(old)
        val vm = EquipmentPlacementViewModel(repo)
        attach(vm, "cfg-demo-old", PreviewMode.DEMO)
        assertTrue(vm.state.value.instances.isEmpty())
        assertEquals("old-demo", repo.snapshot("grower@greenhands.app").single().layoutId)
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.CIRCULATION_FAN)
        assertEquals(1, vm.state.value.instances.size)
        vm.saveLayout()
        assertFalse(vm.state.value.saved)
        assertFalse(vm.state.value.showsPersistentSave)
        assertEquals(MyGreenhousePersistence.DEMO_SESSION_ONLY, vm.state.value.statusMessage)
        val stored = repo.snapshot("grower@greenhands.app").single()
        assertEquals("old-demo", stored.layoutId)
        assertEquals("old-fan", stored.instances.single().id)
        attach(vm, "cfg-demo-old", PreviewMode.DEMO)
        assertEquals(1, vm.state.value.instances.size)
        assertNotEquals("old-fan", vm.state.value.instances.single().id)
    }

    @Test
    fun myGreenhouseSavesOnlyConfigurationAndLayoutFields() = runTest {
        val layoutRepo = InMemoryEquipmentLayoutRepository()
        val configRepo = InMemoryHeatConfigRepository()
        val heatVm = HeatConfigViewModel(configRepo)
        heatVm.selectCrop(Crop.TOMATO)
        val vegetative = CropProfileRegistry.stageProfile(Crop.TOMATO, "vegetative").stage
        assertTrue(heatVm.onStageClicked(vegetative))
        assertTrue(heatVm.saveClimate())
        heatVm.saveMyGreenhouse()
        val config = configRepo.snapshot().current()
        assertEquals(Crop.TOMATO, config.crop)
        assertEquals("vegetative", config.stage?.id)
        assertNotNull(config.dayTemperatureC ?: config.targetTemperatureC)
        assertNotNull(config.dayHumidityPercent ?: config.targetHumidityPercent)
        assertNotNull(config.circulation)
        assertNotNull(config.exhaust)
        assertNotNull(config.fogger)

        val vm = EquipmentPlacementViewModel(layoutRepo)
        attach(vm, "cfg-mine-save", PreviewMode.MY_GREENHOUSE)
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.CIRCULATION_FAN)
        val placed = vm.state.value.instances.single()
        vm.onLiveEnvironment(
            ArLiveEnvironmentReading(temperatureC = 99.1, relativeHumidityPercent = 12.3, connected = true)
        )
        vm.startBehavior()
        assertTrue(vm.state.value.behaviorRunning)
        assertEquals(EquipmentRuntimeState.ON, vm.state.value.runtimeById[placed.id])
        vm.saveLayout()
        assertTrue(vm.state.value.saved)
        assertTrue(vm.state.value.showsPersistentSave)
        val stored = layoutRepo.snapshot("grower@greenhands.app").single()
        val session = HeatSessionRecord(
            id = "cfg-mine-save",
            createdAtMillis = 1L,
            cropId = config.crop?.id,
            cropDisplayName = config.crop?.displayName,
            stageId = config.stage?.id,
            stageDisplayName = config.stage?.shortLabel,
            dayTemperatureC = config.dayTemperatureC,
            nightTemperatureC = config.nightTemperatureC,
            dayHumidityPercent = config.dayHumidityPercent,
            nightHumidityPercent = config.nightHumidityPercent,
            previewMode = PreviewMode.MY_GREENHOUSE.name,
            lengthM = stored.lengthM,
            widthM = stored.widthM,
            heightM = stored.heightM,
            cropRowCount = stored.cropRowCount
        )
        val snapshot = MyGreenhousePersistence.snapshot(config, session, stored)
        assertEquals(DemoGreenhouseProfile.LENGTH_M, snapshot.lengthM)
        assertEquals(DemoGreenhouseProfile.WIDTH_M, snapshot.widthM)
        assertEquals(DemoGreenhouseProfile.HEIGHT_M, snapshot.heightM)
        assertEquals(DemoGreenhouseProfile.CROP_ROW_COUNT, snapshot.cropRowCount)
        assertEquals("tomato", snapshot.cropId)
        assertEquals("Tomato", snapshot.cropName)
        assertEquals("vegetative", snapshot.stageId)
        assertEquals(placed.id, snapshot.equipment.single().id)
        assertEquals("Circulation Fan 1", snapshot.equipment.single().name)
        assertEquals(placed.positionM.xM, snapshot.equipment.single().xM, 0.0)
        assertEquals(placed.positionM.yM, snapshot.equipment.single().yM, 0.0)
        assertEquals(placed.positionM.zM, snapshot.equipment.single().zM, 0.0)
        assertEquals(placed.yawDeg, snapshot.equipment.single().yawDeg, 0.0)
        assertEquals(EquipmentRuntimeState.NOT_RUNNING, stored.instances.single().runtimeState)

        val encodedLayout = EquipmentLayoutCodec.encode(stored)
        val encodedConfig = HeatConfigCodec.encode(config)
        val encodedSession = HeatSessionCodec.encode(session)
        assertTrue(MyGreenhousePersistence.layoutKeysAreAllowed(encodedLayout))
        assertTrue(MyGreenhousePersistence.configKeysAreAllowed(encodedConfig))
        assertFalse(
            MyGreenhousePersistence.containsForbiddenPersistedState(
                encodedLayout,
                encodedConfig,
                encodedSession
            )
        )
        assertFalse(encodedLayout.contains("99.1"))
        assertFalse(encodedLayout.contains("12.3"))
        assertFalse(encodedLayout.contains(",ON,"))
        val arPose = ArTabletopUiState(visualScaleFactor = 1.337, yawDeg = 47.47)
        assertFalse(encodedLayout.contains(arPose.visualScaleFactor.toString()))
        assertFalse(encodedLayout.contains("47.47"))
        assertFalse(encodedLayout.contains("visualScale"))
        assertFalse(encodedLayout.contains("cameraPosition"))
        assertFalse(encodedLayout.contains("behaviorRunning"))
        assertFalse(encodedConfig.contains("99.1"))
        assertFalse(encodedConfig.contains("connected"))
        assertEquals(MyGreenhousePersistence.SAVE_ACTION, "Save My Greenhouse")
        assertEquals(MyGreenhousePersistence.SAVE_CHROME_LABEL, "Save")
        assertEquals(MyGreenhousePersistence.SAVED, "My Greenhouse saved")
        assertEquals(MyGreenhousePersistence.SAVED, vm.state.value.statusMessage)
    }

    @Test
    fun startVisualizationIsDisabledForMyGreenhouseWithoutIot() = runTest {
        val vm = EquipmentPlacementViewModel(InMemoryEquipmentLayoutRepository())
        attach(vm, "cfg-mine-disconnected", PreviewMode.MY_GREENHOUSE)
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.CIRCULATION_FAN)
        assertEquals(ArRuntimeCopy.MY_GREENHOUSE_START_DISABLED, vm.startBlockReason())
        vm.startBehavior()
        assertFalse(vm.state.value.behaviorRunning)
        val hud = ArHudContent.from(
            climate,
            PreviewMode.MY_GREENHOUSE,
            vm.state.value.liveEnvironment,
            vm.state.value.instances,
            vm.state.value.runtimeById
        )
        assertEquals("-- °C", hud.liveTemperatureLabel)
        assertEquals("-- % RH", hud.liveHumidityLabel)
        assertEquals("Sensor not connected", hud.liveStatusLabel)
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

    private fun placedFan(id: String) = EquipmentAssetCatalog.newInstance(
        type = EquipmentType.CIRCULATION_FAN,
        id = id,
        positionM = DomainPoint(2.0, 1.5, 1.6),
        yawDeg = 0.0,
        displayNumber = 1
    )
}
