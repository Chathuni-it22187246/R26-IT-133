package com.greenhands.app.ar

import com.greenhands.app.ar.domain.ArHudContent
import com.greenhands.app.ar.domain.ArHudEquipmentSummary
import com.greenhands.app.ar.domain.ArHudPhase3C3A
import com.greenhands.app.ar.domain.ArLiveEnvironmentReading
import com.greenhands.app.ar.domain.ArOverlayLayout
import com.greenhands.app.ar.domain.ArThresholdGlossary
import com.greenhands.app.equipment.data.InMemoryEquipmentLayoutRepository
import com.greenhands.app.equipment.domain.PlacementSuggester
import com.greenhands.app.equipment.domain.PlacementValidator
import com.greenhands.app.equipment.model.DomainPoint
import com.greenhands.app.equipment.model.EquipmentType
import com.greenhands.app.equipment.ui.EquipmentPlacementViewModel
import com.greenhands.app.equipment.ui.OverlayClimate
import com.greenhands.app.heat.domain.CropDefaults
import com.greenhands.app.heat.model.CirculationThresholds
import com.greenhands.app.heat.model.Crop
import com.greenhands.app.heat.model.ExhaustThresholds
import com.greenhands.app.heat.model.FoggerThresholds
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

class ArHudClimateMappingTest {

    @Test
    fun phaseContractIsStatusHudOnly() {
        assertEquals(
            "Phase 3C.3A compact AR environmental and equipment status HUD",
            ArHudPhase3C3A.STATUS
        )
        assertTrue(ArHudPhase3C3A.LIVE_CONTRACT.contains("Nullable"))
        assertTrue(ArHudPhase3C3A.LIVE_CONTRACT.contains("disconnected"))
    }

    @Test
    fun cropStageAndOptimalTargetsComeFromActiveConfiguration() {
        val stage = CropDefaults.stage(Crop.TOMATO, "flowering")
        val climate = OverlayClimate(
            crop = Crop.TOMATO,
            stage = stage,
            temperatureLabel = "24.0–27.0°C",
            humidityLabel = "60–80%",
            vpdLabel = "Not published",
            circulation = CirculationThresholds(26.0, 24.0, 28.0),
            exhaust = ExhaustThresholds(30.0, 28.0, 32.0),
            fogger = FoggerThresholds(70.0, 68.0, 72.0),
            optimalTemperatureC = 26.0,
            optimalHumidityPercent = 70.0
        )
        val hud = ArHudContent.from(climate, PreviewMode.MY_GREENHOUSE)
        assertEquals(Crop.TOMATO, hud.crop)
        assertEquals("Tomato", hud.cropName)
        assertEquals("Flowering", hud.stageName)
        assertEquals("26.0 °C", hud.optimalTemperatureLabel)
        assertEquals("70.0 % RH", hud.optimalHumidityLabel)
        assertFalse(hud.optimalTemperatureLabel.contains("–"))
        assertFalse(hud.optimalHumidityLabel.contains("60–80"))
    }

    @Test
    fun demoModeFillsRegistryDefaultsWhenConfigurationIsEmpty() {
        val empty = emptyClimate()
        val hud = ArHudContent.from(empty, PreviewMode.DEMO)
        val demoStage = ArHudContent.demoStageFor(Crop.TOMATO)
        assertEquals(Crop.TOMATO, hud.crop)
        assertEquals("Tomato", hud.cropName)
        assertEquals(demoStage.shortLabel, hud.stageName)
        assertEquals("Vegetative", hud.stageName)
        assertEquals(
            ArHudContent.optimalTemperatureLabel(ArHudContent.demoOptimalTemperatureC()),
            hud.optimalTemperatureLabel
        )
        assertEquals(
            ArHudContent.optimalHumidityLabel(ArHudContent.demoOptimalHumidityPercent()),
            hud.optimalHumidityLabel
        )
        assertEquals(25.5, ArHudContent.demoOptimalTemperatureC(), 0.0)
        assertEquals(75.0, ArHudContent.demoOptimalHumidityPercent(), 0.0)
        assertEquals("25.5 °C", hud.optimalTemperatureLabel)
        assertEquals("75.0 % RH", hud.optimalHumidityLabel)
        val resolved = ArHudContent.resolveOverlay(empty, PreviewMode.DEMO)
        assertEquals(25.5, resolved.optimalTemperatureC)
        assertEquals(75.0, resolved.optimalHumidityPercent)
        assertEquals(25.5, resolved.circulation?.csp)
        assertEquals(75.0, resolved.fogger?.fsp)
    }

    @Test
    fun demoModeKeepsActiveConfigurationInsteadOfReplacingWithTomatoDefaults() {
        val lettuceStage = CropDefaults.stage(Crop.LETTUCE, "heading")
        val climate = OverlayClimate(
            crop = Crop.LETTUCE,
            stage = lettuceStage,
            temperatureLabel = "Optimal temperature: 21.0°C",
            humidityLabel = "Optimal humidity: 55.0%",
            vpdLabel = "Not published",
            circulation = null,
            exhaust = null,
            fogger = null,
            optimalTemperatureC = 21.0,
            optimalHumidityPercent = 55.0
        )
        val hud = ArHudContent.from(climate, PreviewMode.DEMO)
        assertEquals(Crop.LETTUCE, hud.crop)
        assertEquals("Greenhouse Lettuce", hud.cropName)
        assertEquals("Heading", hud.stageName)
        assertEquals("21.0 °C", hud.optimalTemperatureLabel)
        assertEquals("55.0 % RH", hud.optimalHumidityLabel)
        assertNotEquals(ArHudContent.demoOptimalTemperatureC(), 21.0, 0.0)
    }

    @Test
    fun myGreenhouseDoesNotReceiveDemoDefaultsWhenDataIsMissing() {
        val hud = ArHudContent.from(emptyClimate(), PreviewMode.MY_GREENHOUSE)
        assertNull(hud.crop)
        assertEquals(ArHudContent.UNAVAILABLE, hud.cropName)
        assertEquals(ArHudContent.UNAVAILABLE, hud.stageName)
        assertEquals(ArHudContent.UNAVAILABLE, hud.optimalTemperatureLabel)
        assertEquals(ArHudContent.UNAVAILABLE, hud.optimalHumidityLabel)
        val resolved = ArHudContent.resolveOverlay(emptyClimate(), PreviewMode.MY_GREENHOUSE)
        assertNull(resolved.crop)
        assertNull(resolved.stage)
        assertNull(resolved.optimalTemperatureC)
        assertNull(resolved.optimalHumidityPercent)
        assertNotEquals("Tomato", hud.cropName)
        assertFalse(hud.optimalTemperatureLabel.contains("25.5"))
        assertFalse(hud.optimalHumidityLabel.contains("75.0"))
    }

    @Test
    fun myGreenhouseUsesConfiguredCropNotHardCodedTomatoTargets() {
        val chilliStage = CropDefaults.stage(Crop.CHILLI, "vegetative")
        val climate = OverlayClimate(
            crop = Crop.CHILLI,
            stage = chilliStage,
            temperatureLabel = "Optimal temperature: 28.0°C",
            humidityLabel = "Optimal humidity: 65.0%",
            vpdLabel = "Not published",
            circulation = null,
            exhaust = null,
            fogger = null,
            optimalTemperatureC = 28.0,
            optimalHumidityPercent = 65.0
        )
        val hud = ArHudContent.from(climate, PreviewMode.MY_GREENHOUSE)
        assertEquals("Chilli", hud.cropName)
        assertEquals(chilliStage.shortLabel, hud.stageName)
        assertEquals("28.0 °C", hud.optimalTemperatureLabel)
        assertEquals("65.0 % RH", hud.optimalHumidityLabel)
        assertNotEquals("25.5 °C", hud.optimalTemperatureLabel)
        assertNotEquals("70.0 % RH", hud.optimalHumidityLabel)
    }

    private fun emptyClimate() = OverlayClimate(
        crop = null,
        stage = null,
        temperatureLabel = "24.0–27.0°C",
        humidityLabel = "70–80%",
        vpdLabel = "Not published",
        circulation = null,
        exhaust = null,
        fogger = null
    )
}

class ArHudLiveReadingContractTest {

    @Test
    fun disconnectedLiveReadingsStayPlaceholders() {
        val hud = ArHudContent.from(
            OverlayClimate(null, null, "—", "—", "Not published", null, null, null),
            PreviewMode.MY_GREENHOUSE,
            ArLiveEnvironmentReading.DISCONNECTED
        )
        assertEquals(ArHudContent.LIVE_TEMPERATURE_PLACEHOLDER, hud.liveTemperatureLabel)
        assertEquals(ArHudContent.LIVE_HUMIDITY_PLACEHOLDER, hud.liveHumidityLabel)
        assertEquals(ArHudContent.SENSOR_NOT_CONNECTED, hud.liveStatusLabel)
        assertEquals("-- °C", hud.liveTemperatureLabel)
        assertEquals("-- % RH", hud.liveHumidityLabel)
        assertEquals("Sensor not connected", hud.liveStatusLabel)
        assertNull(ArLiveEnvironmentReading.DISCONNECTED.temperatureC)
        assertNull(ArLiveEnvironmentReading.DISCONNECTED.relativeHumidityPercent)
        assertFalse(ArLiveEnvironmentReading.DISCONNECTED.connected)
    }

    @Test
    fun disconnectedContractIgnoresNumericPayloads() {
        val leaked = ArLiveEnvironmentReading(
            temperatureC = 25.5,
            relativeHumidityPercent = 75.0,
            connected = false
        )
        assertEquals("-- °C", ArHudContent.liveTemperatureLabel(leaked))
        assertEquals("-- % RH", ArHudContent.liveHumidityLabel(leaked))
        assertEquals("Sensor not connected", ArHudContent.liveStatusLabel(leaked))
    }

    @Test
    fun connectedNullMeasurementsShowUnavailableNotInventedValues() {
        val connectedEmpty = ArLiveEnvironmentReading(
            temperatureC = null,
            relativeHumidityPercent = null,
            connected = true
        )
        assertEquals(ArHudContent.UNAVAILABLE, ArHudContent.liveTemperatureLabel(connectedEmpty))
        assertEquals(ArHudContent.UNAVAILABLE, ArHudContent.liveHumidityLabel(connectedEmpty))
        assertEquals("Sensor connected", ArHudContent.liveStatusLabel(connectedEmpty))
        assertNotEquals("25.5 °C", ArHudContent.liveTemperatureLabel(connectedEmpty))
        assertNotEquals("0.0 °C", ArHudContent.liveTemperatureLabel(connectedEmpty))
    }

    @Test
    fun connectedContractCanAcceptLaterIotValuesWithoutRelabelling() {
        val live = ArLiveEnvironmentReading(
            temperatureC = 24.2,
            relativeHumidityPercent = 61.0,
            connected = true
        )
        assertEquals("24.2 °C", ArHudContent.liveTemperatureLabel(live))
        assertEquals("61.0 % RH", ArHudContent.liveHumidityLabel(live))
        assertEquals("Sensor connected", ArHudContent.liveStatusLabel(live))
        assertEquals(
            ArHudContent.LIVE_TEMPERATURE_PLACEHOLDER,
            ArHudContent.from(
                OverlayClimate(null, null, "—", "—", "Not published", null, null, null),
                PreviewMode.DEMO
            ).liveTemperatureLabel
        )
        val demoHud = ArHudContent.from(
            OverlayClimate(null, null, "—", "—", "Not published", null, null, null),
            PreviewMode.DEMO
        )
        assertEquals(ArHudContent.DEMO_SIMULATION, demoHud.liveStatusLabel)
        assertNotEquals(ArHudContent.SENSOR_NOT_CONNECTED, demoHud.liveStatusLabel)
    }
}

class ArHudEquipmentStateTest {

    @Test
    fun zeroPlacedItemsShowNonePlacedAggregates() {
        assertEquals(EquipmentType.entries.toList(), ArHudContent.configuredEquipmentTypes())
        val lines = ArHudContent.equipmentLines()
        assertEquals(3, lines.size)
        assertEquals("Circulation Fans (0) — None placed", lines[0].displayLine())
        assertEquals("Exhaust Fans (0) — None placed", lines[1].displayLine())
        assertEquals("Foggers (0) — None placed", lines[2].displayLine())
        lines.forEach { line ->
            assertEquals(0, line.count)
            assertEquals(ArHudContent.NONE_PLACED, line.state)
            assertFalse(ArHudContent.usesOnOffRuntimeLabel(line.state))
        }
        val hud = ArHudContent.from(
            OverlayClimate(null, null, "—", "—", "Not published", null, null, null),
            PreviewMode.MY_GREENHOUSE
        )
        assertEquals(lines.map { it.displayLine() }, hud.equipment.map { it.displayLine() })
        assertTrue(hud.instanceStatuses.isEmpty())
    }

    @Test
    fun compactHudCopyOmitsThresholdCodesThatBelongInEquipmentStatus() {
        val hud = ArHudContent.from(
            OverlayClimate(
                crop = Crop.TOMATO,
                stage = CropDefaults.stage(Crop.TOMATO, "vegetative"),
                temperatureLabel = "—",
                humidityLabel = "—",
                vpdLabel = "Not published",
                circulation = CirculationThresholds(25.5, 23.5, 27.5),
                exhaust = ExhaustThresholds(29.5, 27.5, 31.5),
                fogger = FoggerThresholds(75.0, 73.0, 77.0),
                optimalTemperatureC = 25.5,
                optimalHumidityPercent = 75.0
            ),
            PreviewMode.MY_GREENHOUSE
        )
        hud.allUserCopy().forEach { line ->
            assertFalse("HUD must not show $line as a threshold code", ArThresholdGlossary.containsThresholdCode(line))
        }
        assertEquals(9, ArThresholdGlossary.LINES.size)
        assertEquals("CSP — Circulation set point", ArThresholdGlossary.CSP)
        assertEquals("CDP — Circulation dead/off point", ArThresholdGlossary.CDP)
        assertEquals("CON — Circulation turns ON", ArThresholdGlossary.CON)
        assertEquals("ESP — Exhaust set point", ArThresholdGlossary.ESP)
        assertEquals("EDP — Exhaust turns OFF", ArThresholdGlossary.EDP)
        assertEquals("EON — Exhaust turns ON", ArThresholdGlossary.EON)
        assertEquals("FSP — Fogger humidity set point", ArThresholdGlossary.FSP)
        assertEquals("FON — Fogger turns ON below this humidity", ArThresholdGlossary.FON)
        assertEquals("FDP — Fogger turns OFF above this humidity", ArThresholdGlossary.FDP)
        assertEquals(ArHudContent.CONTROLS_CIRCULATION, ArHudContent.controlHeading(EquipmentType.CIRCULATION_FAN))
        assertEquals(ArHudContent.CONTROLS_EXHAUST, ArHudContent.controlHeading(EquipmentType.EXHAUST_FAN))
        assertEquals(ArHudContent.CONTROLS_FOGGER, ArHudContent.controlHeading(EquipmentType.FOGGER))
        ArThresholdGlossary.LINES.forEach { line ->
            assertTrue(ArThresholdGlossary.containsThresholdCode(line))
        }
    }
}

class ArHudOverlayPlacementTest {

    @Test
    fun compactPortraitHudStaysOffExistingArControls() {
        val slots = ArOverlayLayout.compactPortrait(360f, 800f, placed = true)
        assertTrue(slots.overlappingPairs().isEmpty())
        assertTrue(slots.suggestionConflicts().isEmpty())
        assertTrue(slots.statusHudConflicts().isEmpty())
        assertTrue(slots.statusHud.area() > 0f)
        assertFalse(slots.statusHud.overlaps(slots.header))
        assertFalse(slots.statusHud.overlaps(slots.suggestions))
        assertFalse(slots.statusHud.overlaps(slots.chrome))
        assertFalse(slots.statusHud.overlaps(slots.palette))
        val bar = ArOverlayLayout.selectedBar(slots.viewport, landscape = false)
        assertFalse(slots.statusHud.overlaps(bar))
        val dock = ArOverlayLayout.chromeRow(slots.chrome, 0)
        val dockButtons = ArOverlayLayout.equalButtons(dock, 4)
        assertEquals(4, dockButtons.size)
        dockButtons.forEach { button ->
            assertFalse(slots.statusHud.overlaps(button))
        }
        assertTrue(slots.statusHud.top >= slots.header.bottom)
        assertTrue(slots.statusHud.bottom <= slots.chrome.top + 0.01f)
        assertTrue(slots.statusHud.right <= slots.suggestions.left + 0.01f)
        assertEquals(ArOverlayLayout.STATUS_SUMMARY_HEIGHT_DP, slots.statusHud.height, 0.01f)
        assertTrue(slots.statusHud.width >= ArOverlayLayout.STATUS_HUD_MIN_WIDTH_DP - 0.01f)
        assertTrue(slots.viewport.height >= ArOverlayLayout.MIN_VIEWPORT_DP)
        assertEquals(0f, slots.palette.area(), 0.01f)
        assertFalse(ArOverlayLayout.statusHudCrowded(slots.viewport, landscape = false))
        assertTrue(
            ArHudEquipmentSummary.sectionStacked(
                listOf(
                    "Circulation Fan (1)" to "1 Simulated ON",
                    "Exhaust Fan (1)" to "1 Simulated ON",
                    "Fogger (1)" to "1 Simulated ON"
                ),
                ArOverlayLayout.STATUS_HUD_WIDTH_DP
            )
        )
    }

    @Test
    fun compactLandscapeHudCollapsesWithoutCoveringSuggestionsOrChrome() {
        val slots = ArOverlayLayout.compactLandscape(800f, 360f, placed = true)
        assertTrue(slots.overlappingPairs().isEmpty())
        assertTrue(slots.suggestionConflicts().isEmpty())
        assertTrue(slots.statusHudConflicts().isEmpty())
        assertTrue(ArOverlayLayout.statusHudCrowded(slots.viewport, landscape = true))
        assertEquals(ArOverlayLayout.STATUS_HUD_COLLAPSED_HEIGHT_DP, slots.statusHud.height, 0.01f)
        assertEquals(ArOverlayLayout.STATUS_HUD_COLLAPSED_WIDTH_DP, slots.statusHud.width, 0.01f)
        assertTrue(slots.statusHud.right <= slots.suggestions.left + 0.01f)
        assertFalse(
            ArHudEquipmentSummary.sectionStacked(
                listOf(
                    "Circulation Fan (1)" to "Simulated ON",
                    "Exhaust Fan (1)" to "Simulated ON",
                    "Fogger (1)" to "Simulated ON"
                ),
                280f
            )
        )
        assertFalse(slots.statusHud.overlaps(slots.header))
        assertFalse(slots.statusHud.overlaps(slots.suggestions))
        assertFalse(slots.statusHud.overlaps(slots.chrome))
        assertFalse(slots.statusHud.overlaps(slots.palette))
        val bar = ArOverlayLayout.selectedBar(slots.viewport, landscape = true)
        val selectedHud = ArOverlayLayout.statusHudRect(slots.viewport, landscape = true, selectedVisible = true)
        assertTrue(selectedHud.area() > 0f)
        assertFalse(selectedHud.overlaps(slots.suggestions))
        assertFalse(selectedHud.overlaps(bar))
        assertFalse(selectedHud.overlaps(slots.header))
        assertTrue(selectedHud.height <= ArOverlayLayout.STATUS_HUD_CHIP_HEIGHT_DP + 0.01f)
        val dock = ArOverlayLayout.chromeRow(slots.chrome, 0)
        ArOverlayLayout.equalButtons(dock, 4).forEach { button ->
            assertFalse(slots.statusHud.overlaps(button))
        }
    }

    @Test
    fun unplacedSessionHasNoStatusHud() {
        val portrait = ArOverlayLayout.compactPortrait(360f, 800f, placed = false)
        val landscape = ArOverlayLayout.compactLandscape(800f, 360f, placed = false)
        assertEquals(0f, portrait.statusHud.area(), 0f)
        assertEquals(0f, landscape.statusHud.area(), 0f)
        assertTrue(portrait.statusHudConflicts().isEmpty())
        assertTrue(landscape.statusHudConflicts().isEmpty())
    }

    @Test
    fun oppoPortraitAndLandscapeKeepHudInsideViewport() {
        listOf(
            Triple(ArOverlayLayout.compactPortrait(360f, 800f, placed = true), false, "portrait"),
            Triple(ArOverlayLayout.compactLandscape(800f, 360f, placed = true), true, "landscape")
        ).forEach { (slots, landscape, _) ->
            assertTrue(slots.statusHud.left >= slots.viewport.left)
            assertTrue(slots.statusHud.top >= slots.viewport.top)
            assertTrue(slots.statusHud.right <= slots.viewport.right + 0.01f)
            assertTrue(slots.statusHud.bottom <= slots.viewport.bottom + 0.01f)
            assertTrue(slots.statusHudConflicts().isEmpty())
            assertFalse(slots.statusHud.overlaps(slots.suggestions))
            val selectedHud = ArOverlayLayout.statusHudRect(
                slots.viewport,
                landscape = landscape,
                selectedVisible = true
            )
            val bar = ArOverlayLayout.selectedBar(slots.viewport, landscape)
            assertFalse(selectedHud.overlaps(bar))
            assertFalse(selectedHud.overlaps(slots.suggestions))
            assertFalse(selectedHud.overlaps(slots.header))
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ArHudEquipmentInstanceStatusTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val climate = OverlayClimate(
        crop = null,
        stage = null,
        temperatureLabel = "—",
        humidityLabel = "—",
        vpdLabel = "Not published",
        circulation = null,
        exhaust = null,
        fogger = null
    )

    @Before
    fun setMain() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun reset() {
        Dispatchers.resetMain()
    }

    private fun hud(vm: EquipmentPlacementViewModel) = ArHudContent.from(
        climate,
        vm.state.value.previewMode,
        live = vm.state.value.liveEnvironment,
        instances = vm.state.value.instances,
        runtimeById = vm.state.value.runtimeById,
        simulatedTemperatureC = vm.state.value.simulatedTemperatureC,
        simulatedHumidityPercent = vm.state.value.simulatedHumidityPercent,
        visualizationRunning = vm.state.value.behaviorRunning,
        actualRelayById = vm.state.value.actualRelayById
    )

    private fun attach(vm: EquipmentPlacementViewModel, configId: String = "cfg-hud-inst") {
        vm.attach(
            "grower@greenhands.app",
            configId,
            DemoGreenhouseProfile.LENGTH_M,
            DemoGreenhouseProfile.WIDTH_M,
            DemoGreenhouseProfile.HEIGHT_M,
            DemoGreenhouseProfile.CROP_ROW_COUNT,
            climate,
            PreviewMode.MY_GREENHOUSE
        )
    }

    private fun placeFogger(vm: EquipmentPlacementViewModel, yM: Double, aisleIndex: Int = 0) {
        val ctx = vm.placementContext()
        val aisles = PlacementSuggester.aisleCentres(ctx)
        val x = aisles[aisleIndex.coerceIn(aisles.indices)]
        val z = PlacementValidator.overheadZ(ctx)
        vm.beginPaletteDrag(EquipmentType.FOGGER)
        vm.paletteDragTo(EquipmentType.FOGGER, DomainPoint(x, yM, z))
        vm.endPaletteDrag(overViewport = true)
    }

    @Test
    fun multipleInstancesAreListedIndividuallyWithStableNames() = runTest {
        val vm = EquipmentPlacementViewModel(InMemoryEquipmentLayoutRepository())
        attach(vm)
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.CIRCULATION_FAN)
        vm.duplicateSelected()
        vm.tapPalette(EquipmentType.EXHAUST_FAN)
        placeFogger(vm, 1.8)
        placeFogger(vm, 8.2)
        val aisles = PlacementSuggester.aisleCentres(vm.placementContext())
        if (aisles.size > 1) {
            placeFogger(vm, 1.8, aisles.lastIndex)
        } else {
            placeFogger(vm, 3.2)
        }
        val snapshot = ArHudContent.workingCopyStatus(
            vm.state.value.instances,
            vm.state.value.runtimeById,
            presentation = vm.equipmentPresentation()
        )
        assertEquals(
            listOf(
                "Circulation Fan 1 — Not evaluated",
                "Circulation Fan 2 — Not evaluated",
                "Exhaust Fan 1 — Not evaluated",
                "Fogger 1 — Not evaluated",
                "Fogger 2 — Not evaluated",
                "Fogger 3 — Not evaluated"
            ),
            snapshot.instances.map { it.displayLine() }
        )
        snapshot.instances.forEach { row ->
            assertEquals(ArHudContent.NOT_EVALUATED, row.state)
            assertTrue(snapshot.byId.containsKey(row.instanceId))
            assertEquals(row, snapshot.byId[row.instanceId])
            assertFalse(ArHudContent.usesOnOffRuntimeLabel(row.state))
        }
        val hud = hud(vm)
        assertEquals("Circulation Fans (2) — Not evaluated", hud.equipment[0].displayLine())
        assertEquals("Exhaust Fan (1) — Not evaluated", hud.equipment[1].displayLine())
        assertEquals("Foggers (3) — Not evaluated", hud.equipment[2].displayLine())
        assertEquals(snapshot.instances.map { it.displayLine() }, hud.instanceStatuses.map { it.displayLine() })
    }

    @Test
    fun aggregateTypeCountsUseSingularAndPlural() {
        assertEquals("Circulation Fan (1)", ArHudContent.aggregateTitle(EquipmentType.CIRCULATION_FAN, 1))
        assertEquals("Circulation Fans (2)", ArHudContent.aggregateTitle(EquipmentType.CIRCULATION_FAN, 2))
        assertEquals("Circulation Fans (0)", ArHudContent.aggregateTitle(EquipmentType.CIRCULATION_FAN, 0))
        assertEquals("Exhaust Fan (1)", ArHudContent.aggregateTitle(EquipmentType.EXHAUST_FAN, 1))
        assertEquals("Exhaust Fans (0)", ArHudContent.aggregateTitle(EquipmentType.EXHAUST_FAN, 0))
        assertEquals("Exhaust Fans (2)", ArHudContent.aggregateTitle(EquipmentType.EXHAUST_FAN, 2))
        assertEquals("Fogger (1)", ArHudContent.aggregateTitle(EquipmentType.FOGGER, 1))
        assertEquals("Foggers (0)", ArHudContent.aggregateTitle(EquipmentType.FOGGER, 0))
        assertEquals("Foggers (3)", ArHudContent.aggregateTitle(EquipmentType.FOGGER, 3))
    }

    @Test
    fun deletingFan1KeepsFan2NameAndUpdatesCountsImmediately() = runTest {
        val vm = EquipmentPlacementViewModel(InMemoryEquipmentLayoutRepository())
        attach(vm, "cfg-hud-del")
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.CIRCULATION_FAN)
        vm.duplicateSelected()
        val fan1 = vm.state.value.instances.single { it.displayNumber == 1 }
        val fan2 = vm.state.value.instances.single { it.displayNumber == 2 }
        assertEquals("Circulation Fan 2", fan2.displayLabel())
        vm.select(fan1.id)
        vm.requestDelete()
        vm.deleteSelected()
        val hud = hud(vm)
        assertEquals(listOf("Circulation Fan 2 — Not evaluated"), hud.instanceStatuses.map { it.displayLine() })
        assertEquals(fan2.id, hud.instanceStatuses.single().instanceId)
        assertEquals("Circulation Fan (1) — Not evaluated", hud.equipment[0].displayLine())
        assertEquals(2, fan2.displayNumber)
        assertEquals("Circulation Fan 2", vm.state.value.instances.single().displayLabel())
    }

    @Test
    fun hudUpdatesAfterAddDuplicateDeleteClearAndApplyLayout() = runTest {
        val vm = EquipmentPlacementViewModel(InMemoryEquipmentLayoutRepository())
        vm.attach(
            "grower@greenhands.app",
            "cfg-hud-live",
            DemoGreenhouseProfile.LENGTH_M,
            DemoGreenhouseProfile.WIDTH_M,
            DemoGreenhouseProfile.HEIGHT_M,
            DemoGreenhouseProfile.CROP_ROW_COUNT,
            climate,
            PreviewMode.DEMO
        )
        vm.enterPlacementMode()
        assertEquals("Circulation Fans (0) — None placed", hud(vm).equipment[0].displayLine())

        vm.beginPaletteDrag(EquipmentType.CIRCULATION_FAN)
        vm.paletteDragTo(EquipmentType.CIRCULATION_FAN, DomainPoint(4.2, 1.1, 2.15))
        assertNotNull(vm.state.value.ghost)
        assertTrue(vm.state.value.ghost!!.isGhost)
        assertEquals("Circulation Fans (0) — None placed", hud(vm).equipment[0].displayLine())
        assertTrue(hud(vm).instanceStatuses.isEmpty())
        vm.endPaletteDrag(overViewport = true)
        assertEquals("Circulation Fan (1) — Not running", hud(vm).equipment[0].displayLine())
        assertEquals(listOf("Circulation Fan 1 — Not running"), hud(vm).instanceStatuses.map { it.displayLine() })

        vm.duplicateSelected()
        assertEquals("Circulation Fans (2) — Not running", hud(vm).equipment[0].displayLine())
        assertEquals(2, hud(vm).instanceStatuses.size)

        val fan1 = vm.state.value.instances.single { it.displayNumber == 1 }
        vm.select(fan1.id)
        vm.requestDelete()
        vm.deleteSelected()
        assertEquals("Circulation Fan (1) — Not running", hud(vm).equipment[0].displayLine())
        assertEquals("Circulation Fan 2", hud(vm).instanceStatuses.single().name)

        vm.requestClear()
        vm.confirmClear()
        assertTrue(hud(vm).instanceStatuses.isEmpty())
        assertEquals("Circulation Fans (0) — None placed", hud(vm).equipment[0].displayLine())
        assertEquals("Exhaust Fans (0) — None placed", hud(vm).equipment[1].displayLine())
        assertEquals("Foggers (0) — None placed", hud(vm).equipment[2].displayLine())

        vm.updateRecommendationInput(
            circCfm = "1000",
            exhaustM3s = "2.5",
            fogFlow = "0.002",
            fogEta = "0.5",
            heatLoad = "5000"
        )
        vm.applySuggestionLayout(EquipmentType.CIRCULATION_FAN)
        vm.applySuggestionLayout(EquipmentType.EXHAUST_FAN)
        vm.applySuggestionLayout(EquipmentType.FOGGER)
        val afterApply = hud(vm)
        assertTrue(afterApply.equipment[0].count >= 1)
        assertTrue(afterApply.equipment[1].count >= 1)
        assertTrue(afterApply.equipment[2].count >= 1)
        assertEquals(afterApply.equipment[0].count + afterApply.equipment[1].count + afterApply.equipment[2].count, afterApply.instanceStatuses.size)
        afterApply.instanceStatuses.forEach { row ->
            assertEquals(ArHudContent.NOT_RUNNING, row.state)
            assertTrue(row.name.matches(Regex("^(Circulation Fan|Exhaust Fan|Fogger) \\d+$")))
        }
        assertEquals(afterApply.instanceStatuses.size, afterApply.instanceStatuses.map { it.instanceId }.toSet().size)
    }

    @Test
    fun unsavedWorkingCopyCountsDifferFromSavedLayoutUntilSave() = runTest {
        val repo = InMemoryEquipmentLayoutRepository()
        val vm = EquipmentPlacementViewModel(repo)
        attach(vm, "cfg-hud-save")
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.CIRCULATION_FAN)
        vm.tapPalette(EquipmentType.EXHAUST_FAN)
        vm.saveLayout()
        val savedHud = hud(vm)
        assertTrue(vm.state.value.saved)
        assertEquals(2, savedHud.instanceStatuses.size)
        val stored = repo.snapshot("grower@greenhands.app").single()
        assertEquals(2, stored.instances.size)

        val circulation = vm.state.value.instances.single { it.type == EquipmentType.CIRCULATION_FAN }
        vm.select(circulation.id)
        vm.duplicateSelected()
        placeFogger(vm, 1.8)
        val working = hud(vm)
        assertFalse(vm.state.value.saved)
        assertEquals("Circulation Fans (2) — Not evaluated", working.equipment[0].displayLine())
        assertEquals("Exhaust Fan (1) — Not evaluated", working.equipment[1].displayLine())
        assertEquals("Fogger (1) — Not evaluated", working.equipment[2].displayLine())
        assertEquals(2, repo.snapshot("grower@greenhands.app").single().instances.size)
        assertEquals(4, working.instanceStatuses.size)
        assertEquals(2, stored.instances.size)

        vm.saveLayout()
        val persisted = repo.snapshot("grower@greenhands.app").single()
        assertEquals(hud(vm).instanceStatuses.map { it.instanceId }.toSet(), persisted.instances.map { it.id }.toSet())
        assertTrue(vm.state.value.saved)
    }

    @Test
    fun instanceIdsStayStableSoLaterRuntimeCanSetOnOffIndependently() = runTest {
        val vm = EquipmentPlacementViewModel(InMemoryEquipmentLayoutRepository())
        attach(vm, "cfg-hud-runtime-key")
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.CIRCULATION_FAN)
        vm.duplicateSelected()
        val fan1 = vm.state.value.instances.single { it.displayNumber == 1 }
        val fan2 = vm.state.value.instances.single { it.displayNumber == 2 }
        val keyed = ArHudContent.workingCopyStatus(
            vm.state.value.instances,
            runtimeById = mapOf(
                fan1.id to com.greenhands.app.equipment.model.EquipmentRuntimeState.ON,
                fan2.id to com.greenhands.app.equipment.model.EquipmentRuntimeState.OFF
            )
        )
        assertEquals("ON", keyed.byId[fan1.id]?.state)
        assertEquals("OFF", keyed.byId[fan2.id]?.state)
        assertEquals("Circulation Fan 1", keyed.byId[fan1.id]?.name)
        assertEquals("Circulation Fan 2", keyed.byId[fan2.id]?.name)
        val phaseHud = hud(vm)
        phaseHud.instanceStatuses.forEach { row ->
            assertEquals(ArHudContent.NOT_EVALUATED, row.state)
        }
        assertEquals("Circulation Fans (2) — Not evaluated", phaseHud.equipment[0].displayLine())
    }

    @Test
    fun loadingSavedLayoutUpdatesInstanceStatusImmediately() = runTest {
        val repo = InMemoryEquipmentLayoutRepository()
        val writer = EquipmentPlacementViewModel(repo)
        attach(writer, "cfg-hud-load")
        writer.enterPlacementMode()
        writer.tapPalette(EquipmentType.CIRCULATION_FAN)
        writer.duplicateSelected()
        writer.saveLayout()
        val savedNames = writer.state.value.instances.map { it.displayLabel() }.sorted()
        val savedIds = writer.state.value.instances.map { it.id }.toSet()

        val reader = EquipmentPlacementViewModel(repo)
        attach(reader, "cfg-hud-load")
        val loaded = hud(reader)
        assertEquals(savedIds, loaded.instanceStatuses.map { it.instanceId }.toSet())
        assertEquals(savedNames, loaded.instanceStatuses.map { it.name }.sorted())
        assertEquals("Circulation Fans (2) — Not evaluated", loaded.equipment[0].displayLine())
        loaded.instanceStatuses.forEach { row ->
            assertEquals(ArHudContent.NOT_EVALUATED, row.state)
        }
    }
}

class ArHudEquipmentSummaryLayoutTest {

    @Test
    fun narrowPortraitWrapsTitleAndStateOntoTwoLinesWithoutDroppingCount() {
        val title = "Circulation Fan (1)"
        val state = "1 Simulated ON"
        assertTrue(ArHudEquipmentSummary.stacked(title, state, ArOverlayLayout.STATUS_HUD_WIDTH_DP))
        assertTrue(ArHudEquipmentSummary.countVisible(title))
        assertFalse(title.contains("..."))
        assertFalse(state.contains("..."))
        assertFalse(ArHudEquipmentSummary.countVisible("Circulation Fan (..."))
    }

    @Test
    fun wideLayoutKeepsLabelCountLeftAndStateRight() {
        val rows = listOf(
            "Circulation Fan (1)" to "Simulated ON",
            "Exhaust Fan (1)" to "Simulated ON",
            "Fogger (1)" to "Simulated ON"
        )
        assertEquals(
            listOf(false, false, false),
            ArHudEquipmentSummary.assignedStacked(rows, 280f)
        )
        assertEquals(
            listOf(false, false, false),
            ArHudEquipmentSummary.assignedStacked(
                rows,
                ArOverlayLayout.STATUS_HUD_COLLAPSED_WIDTH_DP
            )
        )
        assertFalse(
            ArHudEquipmentSummary.sectionStacked(
                listOf(
                    "Circulation Fan (1)" to "1 Simulated ON",
                    "Exhaust Fan (1)" to "1 Simulated ON",
                    "Fogger (1)" to "1 Simulated ON"
                ),
                280f
            )
        )
    }

    @Test
    fun circulationExhaustAndFoggerShareOneSectionLayoutMode() {
        val rows = listOf(
            "Circulation Fan (1)" to "Not running",
            "Exhaust Fan (1)" to "Not running",
            "Fogger (1)" to "Not running"
        )
        val perRow = rows.map { (title, state) ->
            ArHudEquipmentSummary.stacked(title, state, ArOverlayLayout.STATUS_HUD_WIDTH_DP)
        }
        assertEquals(listOf(true, false, false), perRow)

        val compactAssigned = ArHudEquipmentSummary.assignedStacked(
            rows,
            ArOverlayLayout.STATUS_HUD_WIDTH_DP
        )
        assertEquals(listOf(true, true, true), compactAssigned)
        assertEquals(1, compactAssigned.distinct().size)
        compactAssigned.forEach { stacked ->
            assertTrue(stacked)
        }

        val wideAssigned = ArHudEquipmentSummary.assignedStacked(rows, 280f)
        assertEquals(listOf(false, false, false), wideAssigned)
        assertEquals(1, wideAssigned.distinct().size)
        wideAssigned.forEach { stacked ->
            assertFalse(stacked)
        }

        rows.forEach { (title, _) ->
            assertTrue(ArHudEquipmentSummary.countVisible(title))
        }

        ArHudEquipmentSummary.SAMPLE_STATES.forEach { state ->
            val stateRows = listOf(
                "Circulation Fan (1)" to state,
                "Exhaust Fan (1)" to state,
                "Fogger (1)" to state
            )
            val assigned = ArHudEquipmentSummary.assignedStacked(
                stateRows,
                ArOverlayLayout.STATUS_HUD_WIDTH_DP
            )
            assertEquals(3, assigned.size)
            assertEquals(1, assigned.distinct().size)
            assertEquals(
                1,
                ArHudEquipmentSummary.assignedStacked(stateRows, 280f).distinct().size
            )
        }
    }

    @Test
    fun supportedStatesStayReadableWithoutEllipsizingTheCount() {
        val titles = listOf(
            ArHudContent.aggregateTitle(EquipmentType.CIRCULATION_FAN, 1),
            ArHudContent.aggregateTitle(EquipmentType.EXHAUST_FAN, 1),
            ArHudContent.aggregateTitle(EquipmentType.FOGGER, 1),
            ArHudContent.aggregateTitle(EquipmentType.CIRCULATION_FAN, 0),
            ArHudContent.aggregateTitle(EquipmentType.EXHAUST_FAN, 2),
            ArHudContent.aggregateTitle(EquipmentType.FOGGER, 3)
        )
        titles.forEach { title ->
            assertTrue(title, ArHudEquipmentSummary.countVisible(title))
            assertFalse(title.contains("..."))
        }
        ArHudEquipmentSummary.SAMPLE_STATES.forEach { state ->
            assertFalse(state.contains("..."))
            val rows = titles.map { title -> title to state }
            val compactAssigned = ArHudEquipmentSummary.assignedStacked(
                rows,
                ArOverlayLayout.STATUS_HUD_WIDTH_DP
            )
            assertEquals(titles.size, compactAssigned.size)
            assertEquals(1, compactAssigned.distinct().size)
            val wideAssigned = ArHudEquipmentSummary.assignedStacked(rows, 280f)
            assertEquals(1, wideAssigned.distinct().size)
            assertTrue(wideAssigned.all { !it })
            if (compactAssigned.first()) {
                assertEquals(
                    ArHudEquipmentSummary.TWO_LINE_HEIGHT_DP,
                    ArHudEquipmentSummary.rowHeightDp(true)
                )
            } else {
                assertEquals(
                    ArHudEquipmentSummary.SINGLE_LINE_HEIGHT_DP,
                    ArHudEquipmentSummary.rowHeightDp(false)
                )
            }
        }
    }

    @Test
    fun portraitAndLandscapeHudStayClearOfTheSuggestionRail() {
        val portrait = ArOverlayLayout.compactPortrait(360f, 800f, placed = true)
        val landscape = ArOverlayLayout.compactLandscape(800f, 360f, placed = true)
        listOf(portrait, landscape).forEach { slots ->
            assertTrue(slots.statusHudConflicts().isEmpty())
            assertFalse(slots.statusHud.overlaps(slots.suggestions))
            assertTrue(slots.statusHud.right <= slots.suggestions.left + 0.01f)
            assertTrue(slots.statusHud.bottom <= slots.chrome.top + 0.01f)
        }
        val compactRows = listOf(
            "Circulation Fan (1)" to "Simulated ON",
            "Exhaust Fan (1)" to "Simulated ON",
            "Fogger (1)" to "Simulated ON"
        )
        val portraitAssigned = ArHudEquipmentSummary.assignedStacked(
            compactRows,
            ArOverlayLayout.STATUS_HUD_WIDTH_DP
        )
        assertEquals(listOf(true, true, true), portraitAssigned)
        val landscapeAssigned = ArHudEquipmentSummary.assignedStacked(
            compactRows,
            280f
        )
        assertEquals(listOf(false, false, false), landscapeAssigned)
    }
}

