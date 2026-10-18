package com.greenhands.app.ar

import com.greenhands.app.ar.domain.ArEquipmentStatusCopy
import com.greenhands.app.ar.domain.ArOverlayLayout
import com.greenhands.app.ar.domain.MyGreenhousePersistence
import com.greenhands.app.equipment.data.InMemoryEquipmentLayoutRepository
import com.greenhands.app.equipment.domain.ArPlacementAdapterContract
import com.greenhands.app.equipment.domain.InstanceNumbering
import com.greenhands.app.equipment.domain.PlacementContext
import com.greenhands.app.equipment.domain.SuggestionEvidence
import com.greenhands.app.equipment.domain.UserPlacementCoordinates
import com.greenhands.app.equipment.model.DemoEquipmentProfile
import com.greenhands.app.equipment.model.EquipmentLabels
import com.greenhands.app.equipment.model.EquipmentType
import com.greenhands.app.equipment.model.EvidenceClassification
import com.greenhands.app.equipment.model.displayLabel
import com.greenhands.app.equipment.ui.EquipmentPlacementViewModel
import com.greenhands.app.equipment.ui.OverlayClimate
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ArSuggestionApplyLayoutTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val climate = OverlayClimate(
        crop = null,
        stage = null,
        temperatureLabel = "—",
        humidityLabel = "—",
        vpdLabel = "Not published",
        circulation = null,
        exhaust = null,
        fogger = null,
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
    fun demoAlwaysShowsArApplyLayoutUsingDocumentedProfile() = runTest {
        val vm = EquipmentPlacementViewModel(InMemoryEquipmentLayoutRepository())
        vm.attach(
            "grower@greenhands.app",
            "demo-ar-apply",
            DemoGreenhouseProfile.LENGTH_M,
            DemoGreenhouseProfile.WIDTH_M,
            DemoGreenhouseProfile.HEIGHT_M,
            DemoGreenhouseProfile.CROP_ROW_COUNT,
            climate,
            PreviewMode.DEMO
        )
        vm.openSuggestionGallery(EquipmentType.CIRCULATION_FAN)
        assertTrue(vm.state.value.showsArApplyLayout(EquipmentType.CIRCULATION_FAN))
        assertTrue(vm.state.value.showsArApplyLayout(EquipmentType.EXHAUST_FAN))
        assertTrue(vm.state.value.showsArApplyLayout(EquipmentType.FOGGER))
        assertFalse(vm.state.value.showsApplyLayout(EquipmentType.CIRCULATION_FAN))
        assertTrue(SuggestionEvidence.showsArApplyLayout(true, vm.state.value.recommendation?.circulation))
        assertEquals(SuggestionEvidence.AR_DEMO_NOTICE, "Research-guided example — not installation guidance.")
        assertEquals(
            DemoEquipmentProfile.NOTICE,
            "Demo equipment profile — research-guided example, not installation guidance."
        )
        assertEquals(
            ArPlacementAdapterContract.STATUS,
            "Phase 3C.2B research-guided suggestions and Apply Layout"
        )
        assertFalse(ArPlacementAdapterContract.REQUIRES_LATER.contains("Apply Layout"))
    }

    @Test
    fun myGreenhouseHidesApplyLayoutWhenRatedInputsAreMissing() = runTest {
        val vm = EquipmentPlacementViewModel(InMemoryEquipmentLayoutRepository())
        vm.attach(
            "grower@greenhands.app",
            "mine-missing",
            10.0,
            5.0,
            3.0,
            3,
            climate,
            PreviewMode.MY_GREENHOUSE
        )
        vm.openSuggestionGallery(EquipmentType.CIRCULATION_FAN)
        assertFalse(vm.state.value.showsArApplyLayout(EquipmentType.CIRCULATION_FAN))
        assertFalse(vm.state.value.showsApplyLayout(EquipmentType.CIRCULATION_FAN))
        assertFalse(
            SuggestionEvidence.canApplyCalculatedLayout(
                SuggestionEvidence.countFor(EquipmentType.CIRCULATION_FAN, vm.state.value.recommendation)
            )
        )
        assertEquals(
            SuggestionEvidence.MISSING_EQUIPMENT_INPUTS,
            "Cannot calculate — missing equipment inputs"
        )
        assertEquals(
            SuggestionEvidence.EXAMPLE_PATTERN_ONLY,
            "Example placement pattern only."
        )
        vm.requestApplySuggestionLayout(EquipmentType.CIRCULATION_FAN)
        assertTrue(vm.state.value.instances.isEmpty())
        assertFalse(vm.state.value.showReplaceLayoutConfirm)
        assertEquals("Cannot apply layout — required inputs are missing.", vm.state.value.statusMessage)

        vm.openSuggestionGallery(EquipmentType.EXHAUST_FAN)
        assertFalse(vm.state.value.showsArApplyLayout(EquipmentType.EXHAUST_FAN))
        vm.openSuggestionGallery(EquipmentType.FOGGER)
        assertFalse(vm.state.value.showsArApplyLayout(EquipmentType.FOGGER))
    }

    @Test
    fun applyLayoutReplacesOnlyTheSelectedTypeAfterConfirmation() = runTest {
        val vm = EquipmentPlacementViewModel(InMemoryEquipmentLayoutRepository())
        vm.attach("grower@greenhands.app", "cfg-replace", 10.0, 5.0, 3.0, 3, climate, PreviewMode.MY_GREENHOUSE)
        vm.enterPlacementMode()
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
        val exhaust = vm.state.value.instances.filter { it.type == EquipmentType.EXHAUST_FAN }
        val fogger = vm.state.value.instances.filter { it.type == EquipmentType.FOGGER }
        val originalCirc = vm.state.value.instances.filter { it.type == EquipmentType.CIRCULATION_FAN }
        assertTrue(exhaust.isNotEmpty())
        assertTrue(fogger.isNotEmpty())
        assertTrue(originalCirc.isNotEmpty())
        val exhaustIds = exhaust.map { it.id }
        val foggerIds = fogger.map { it.id }
        val exhaustLabels = exhaust.map { it.displayLabel() to it.displayNumber }
        val foggerLabels = fogger.map { it.displayLabel() to it.displayNumber }
        val originalCircIds = originalCirc.map { it.id }

        vm.openSuggestionGallery(EquipmentType.CIRCULATION_FAN)
        assertTrue(vm.state.value.showsArApplyLayout(EquipmentType.CIRCULATION_FAN))
        vm.requestApplySuggestionLayout(EquipmentType.CIRCULATION_FAN)
        assertTrue(vm.state.value.showReplaceLayoutConfirm)
        assertEquals(EquipmentType.CIRCULATION_FAN, vm.state.value.pendingReplaceType)
        assertEquals(
            "Replace the current ${EquipmentLabels.typeTitle(EquipmentType.CIRCULATION_FAN)} layout?",
            "Replace the current Circulation Fan layout?"
        )
        assertEquals(originalCircIds, vm.state.value.instances.filter { it.type == EquipmentType.CIRCULATION_FAN }.map { it.id })

        vm.cancelReplaceLayout()
        assertFalse(vm.state.value.showReplaceLayoutConfirm)
        assertEquals(originalCircIds, vm.state.value.instances.filter { it.type == EquipmentType.CIRCULATION_FAN }.map { it.id })

        vm.requestApplySuggestionLayout(EquipmentType.CIRCULATION_FAN)
        vm.confirmReplaceLayout()
        val after = vm.state.value.instances
        val keptExhaust = after.filter { it.type == EquipmentType.EXHAUST_FAN }
        val keptFogger = after.filter { it.type == EquipmentType.FOGGER }
        val newCirc = after.filter { it.type == EquipmentType.CIRCULATION_FAN }
        assertEquals(exhaustIds, keptExhaust.map { it.id })
        assertEquals(exhaustLabels, keptExhaust.map { it.displayLabel() to it.displayNumber })
        assertEquals(foggerIds, keptFogger.map { it.id })
        assertEquals(foggerLabels, keptFogger.map { it.displayLabel() to it.displayNumber })
        assertTrue(newCirc.isNotEmpty())
        assertTrue(newCirc.none { it.id in originalCircIds })
        assertEquals(
            (1..newCirc.size).toList(),
            newCirc.sortedBy { it.displayNumber }.map { it.displayNumber }
        )
        newCirc.forEach { inst ->
            assertEquals("Circulation Fan ${inst.displayNumber}", inst.displayLabel())
            assertTrue(inst.fromSuggestion)
            assertFalse(inst.isGhost)
        }
        assertTrue(originalCircIds.none { id -> after.any { it.id == id } })
        assertNull(vm.state.value.pendingReplaceType)
        assertFalse(vm.state.value.showReplaceLayoutConfirm)
        assertFalse(vm.state.value.saved)
    }

    @Test
    fun appliedPositionsStayInsideUserXyzBoundsAndDoNotPersistUntilSave() = runTest {
        val repo = InMemoryEquipmentLayoutRepository()
        val vm = EquipmentPlacementViewModel(repo)
        vm.attach(
            "grower@greenhands.app",
            "cfg-bounds",
            DemoGreenhouseProfile.LENGTH_M,
            DemoGreenhouseProfile.WIDTH_M,
            DemoGreenhouseProfile.HEIGHT_M,
            DemoGreenhouseProfile.CROP_ROW_COUNT,
            climate,
            PreviewMode.DEMO
        )
        vm.openSuggestionGallery(EquipmentType.CIRCULATION_FAN)
        vm.requestApplySuggestionLayout(EquipmentType.CIRCULATION_FAN)
        val placed = vm.state.value.instances
        assertTrue(placed.isNotEmpty())
        assertTrue(placed.all { it.type == EquipmentType.CIRCULATION_FAN })
        placed.forEach { inst ->
            assertTrue(
                UserPlacementCoordinates.inBounds(
                    inst.positionM,
                    DemoGreenhouseProfile.WIDTH_M,
                    DemoGreenhouseProfile.LENGTH_M,
                    DemoGreenhouseProfile.HEIGHT_M
                )
            )
            assertTrue(inst.positionM.xM in 0.0..DemoGreenhouseProfile.WIDTH_M)
            assertTrue(inst.positionM.yM in 0.0..DemoGreenhouseProfile.LENGTH_M)
            assertTrue(inst.positionM.zM in 0.0..DemoGreenhouseProfile.HEIGHT_M)
        }
        assertFalse(vm.state.value.saved)
        assertTrue(repo.snapshot("grower@greenhands.app").isEmpty())
        val circIds = placed.map { it.id }

        vm.openSuggestionGallery(EquipmentType.EXHAUST_FAN)
        vm.requestApplySuggestionLayout(EquipmentType.EXHAUST_FAN)
        val mixed = vm.state.value.instances
        assertTrue(mixed.any { it.type == EquipmentType.EXHAUST_FAN })
        mixed.filter { it.type == EquipmentType.EXHAUST_FAN }.forEach { inst ->
            assertTrue(
                UserPlacementCoordinates.inBounds(
                    inst.positionM,
                    DemoGreenhouseProfile.WIDTH_M,
                    DemoGreenhouseProfile.LENGTH_M,
                    DemoGreenhouseProfile.HEIGHT_M
                )
            )
        }
        assertEquals(circIds, mixed.filter { it.type == EquipmentType.CIRCULATION_FAN }.map { it.id })
        assertFalse(vm.state.value.saved)
        assertTrue(repo.snapshot("grower@greenhands.app").isEmpty())

        vm.saveLayout()
        assertFalse(vm.state.value.saved)
        assertEquals(MyGreenhousePersistence.DEMO_SESSION_ONLY, vm.state.value.statusMessage)
        assertTrue(repo.snapshot("grower@greenhands.app").isEmpty())

        val moved = mixed.first { it.type == EquipmentType.CIRCULATION_FAN }
        vm.select(moved.id)
        vm.setAxisX(moved.positionM.xM + 0.2)
        vm.attach(
            "grower@greenhands.app",
            "cfg-bounds",
            DemoGreenhouseProfile.LENGTH_M,
            DemoGreenhouseProfile.WIDTH_M,
            DemoGreenhouseProfile.HEIGHT_M,
            DemoGreenhouseProfile.CROP_ROW_COUNT,
            climate,
            PreviewMode.DEMO
        )
        val handedOff = vm.state.value.instances.first { it.id == moved.id }
        assertEquals(moved.positionM.xM + 0.2, handedOff.positionM.xM, 1e-9)
        assertEquals(moved.displayLabel(), handedOff.displayLabel())
        assertTrue(repo.snapshot("grower@greenhands.app").isEmpty())
        val reloaded = EquipmentPlacementViewModel(repo)
        reloaded.attach(
            "grower@greenhands.app",
            "cfg-bounds",
            DemoGreenhouseProfile.LENGTH_M,
            DemoGreenhouseProfile.WIDTH_M,
            DemoGreenhouseProfile.HEIGHT_M,
            DemoGreenhouseProfile.CROP_ROW_COUNT,
            climate,
            PreviewMode.DEMO
        )
        assertTrue(reloaded.state.value.instances.isEmpty())
    }

    @Test
    fun replacingATypeReusesLowestFreeNumbersWithoutRenamingOthers() = runTest {
        val vm = EquipmentPlacementViewModel(InMemoryEquipmentLayoutRepository())
        vm.attach("grower@greenhands.app", "cfg-numbers", 10.0, 5.0, 3.0, 3, climate, PreviewMode.MY_GREENHOUSE)
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.EXHAUST_FAN)
        vm.tapPalette(EquipmentType.CIRCULATION_FAN)
        vm.duplicateSelected()
        val exhaustId = vm.state.value.instances.single { it.type == EquipmentType.EXHAUST_FAN }.id
        val fan2 = vm.state.value.instances.single { it.type == EquipmentType.CIRCULATION_FAN && it.displayNumber == 2 }
        vm.select(vm.state.value.instances.single { it.displayNumber == 1 && it.type == EquipmentType.CIRCULATION_FAN }.id)
        vm.requestDelete()
        vm.deleteSelected()
        assertEquals("Circulation Fan 2", fan2.displayLabel())
        assertEquals(2, vm.state.value.instances.single { it.type == EquipmentType.CIRCULATION_FAN }.displayNumber)
        assertEquals(1, InstanceNumbering.nextNumber(EquipmentType.CIRCULATION_FAN, vm.state.value.instances))

        vm.updateRecommendationInput(circCfm = "1000")
        vm.applySuggestionLayout(EquipmentType.CIRCULATION_FAN)
        val remainingExhaust = vm.state.value.instances.single { it.type == EquipmentType.EXHAUST_FAN }
        assertEquals(exhaustId, remainingExhaust.id)
        assertEquals("Exhaust Fan 1", remainingExhaust.displayLabel())
        val circ = vm.state.value.instances.filter { it.type == EquipmentType.CIRCULATION_FAN }
        assertTrue(circ.none { it.id == fan2.id })
        assertEquals(1, circ.minOf { it.displayNumber })
        circ.forEach { assertEquals("Circulation Fan ${it.displayNumber}", it.displayLabel()) }
    }
}

class ArSuggestionOverlayLayoutTest {

    @Test
    fun compactPortraitAndLandscapeKeepSuggestionsAwayFromChrome() {
        val portrait = ArOverlayLayout.compactPortrait(360f, 800f, placed = true)
        assertTrue(portrait.overlappingPairs().isEmpty())
        assertTrue(portrait.suggestionConflicts().isEmpty())
        assertTrue(portrait.suggestions.area() > 0f)
        assertTrue(portrait.suggestions.left >= portrait.viewport.left)
        assertTrue(portrait.suggestions.right <= portrait.viewport.right + 0.01f)
        assertTrue(portrait.suggestions.top >= portrait.header.bottom)
        assertTrue(portrait.suggestions.bottom <= portrait.chrome.top + 0.01f)
        assertFalse(portrait.suggestions.overlaps(portrait.header))
        assertFalse(portrait.suggestions.overlaps(portrait.chrome))
        assertFalse(portrait.suggestions.overlaps(portrait.palette))
        val portraitBar = ArOverlayLayout.selectedBar(portrait.viewport, landscape = false)
        assertFalse(portraitBar.overlaps(portrait.suggestions))

        val landscape = ArOverlayLayout.compactLandscape(800f, 360f, placed = true)
        assertTrue(landscape.overlappingPairs().isEmpty())
        assertTrue(landscape.suggestionConflicts().isEmpty())
        assertTrue(landscape.suggestions.area() > 0f)
        assertEquals(ArOverlayLayout.HEADER_LANDSCAPE_DP, landscape.header.height, 0.01f)
        assertTrue(landscape.suggestions.top >= landscape.header.bottom)
        assertTrue(landscape.suggestions.bottom <= landscape.chrome.top + 0.01f)
        assertFalse(landscape.suggestions.overlaps(landscape.header))
        assertFalse(landscape.suggestions.overlaps(landscape.chrome))
        assertFalse(landscape.suggestions.overlaps(landscape.palette))
        val landscapeBar = ArOverlayLayout.selectedBar(landscape.viewport, landscape = true)
        assertFalse(landscapeBar.overlaps(landscape.suggestions))
        assertTrue(landscapeBar.bottom <= landscape.viewport.bottom)

        val unplaced = ArOverlayLayout.compactPortrait(360f, 800f, placed = false)
        assertEquals(0f, unplaced.suggestions.area(), 0f)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ArPhase3C2BUiCorrectionTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val climate = OverlayClimate(
        crop = null,
        stage = null,
        temperatureLabel = "—",
        humidityLabel = "—",
        vpdLabel = "Not published",
        circulation = null,
        exhaust = null,
        fogger = null,
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
    fun suggestionSheetsHideResearchPreferredAndEvidenceClassificationLines() {
        assertEquals("Research-preferred for current inputs", SuggestionEvidence.RESEARCH_PREFERRED_HEADLINE)
        assertFalse(SuggestionEvidence.showsPreferenceHeadline(SuggestionEvidence.RESEARCH_PREFERRED_HEADLINE))
        assertTrue(SuggestionEvidence.showsPreferenceHeadline(SuggestionEvidence.MISSING_EQUIPMENT_INPUTS))
        assertEquals("Research-guided example — not installation guidance.", SuggestionEvidence.AR_DEMO_NOTICE)
        assertEquals("Research guidance", EvidenceClassification.RESEARCH_GUIDANCE.displayLabel())
        assertEquals("Evidence: Research guidance", "Evidence: ${EvidenceClassification.RESEARCH_GUIDANCE.displayLabel()}")

        val context = PlacementContext(10.0, 5.0, 3.0, 3, 0.78)
        val missing = SuggestionEvidence.rationale(
            EquipmentType.CIRCULATION_FAN,
            null,
            context,
            illustrationOnly = true
        )
        assertTrue(SuggestionEvidence.showsPreferenceHeadline(missing.preference))
        assertTrue(missing.source.isNotBlank())
        assertTrue(missing.supportedRule.isNotBlank())
        assertTrue(missing.currentInputs.isNotEmpty())
        assertEquals(EvidenceClassification.RESEARCH_GUIDANCE, missing.classification)

        val vm = EquipmentPlacementViewModel(InMemoryEquipmentLayoutRepository())
        vm.attach(
            "grower@greenhands.app",
            "demo-headline",
            DemoGreenhouseProfile.LENGTH_M,
            DemoGreenhouseProfile.WIDTH_M,
            DemoGreenhouseProfile.HEIGHT_M,
            DemoGreenhouseProfile.CROP_ROW_COUNT,
            climate,
            PreviewMode.DEMO
        )
        vm.openSuggestionGallery(EquipmentType.CIRCULATION_FAN)
        val preferred = SuggestionEvidence.rationale(
            EquipmentType.CIRCULATION_FAN,
            vm.state.value.recommendation,
            vm.placementContext(),
            illustrationOnly = false
        )
        assertEquals(SuggestionEvidence.RESEARCH_PREFERRED_HEADLINE, preferred.preference)
        assertFalse(SuggestionEvidence.showsPreferenceHeadline(preferred.preference))
        assertTrue(preferred.source.isNotBlank())
        assertNotNull(preferred.classification)
    }

    @Test
    fun selectedEquipmentRowsKeepDeleteAndExactPositionFullyVisible() {
        val portrait = ArOverlayLayout.compactPortrait(360f, 800f, placed = true)
        val chip = ArOverlayLayout.selectedBar(portrait.viewport, landscape = false)
        assertEquals(ArOverlayLayout.SELECTED_BAR_DP, chip.height, 0.01f)
        assertFalse(chip.overlaps(portrait.suggestions))
        assertFalse(chip.overlaps(portrait.chrome))
        assertFalse(chip.overlaps(portrait.header))
        val bar = ArOverlayLayout.selectedSheetBar(360f)
        val name = ArOverlayLayout.selectedNameRow(bar)
        val row1 = ArOverlayLayout.selectedActionRow1(bar)
        val row2 = ArOverlayLayout.selectedActionRow2(bar)
        val row3 = ArOverlayLayout.selectedActionRow3(bar)
        assertFalse(name.overlaps(row1))
        assertFalse(row1.overlaps(row2))
        assertFalse(row2.overlaps(row3))
        assertEquals(ArOverlayLayout.ACTION_ROW_HEIGHT_DP, row1.height, 0.01f)
        assertEquals(ArOverlayLayout.ACTION_ROW_HEIGHT_DP, row2.height, 0.01f)
        assertEquals(ArOverlayLayout.ACTION_ROW_HEIGHT_DP, row3.height, 0.01f)
        val delete = ArOverlayLayout.deleteButton(row2)
        val exact = ArOverlayLayout.exactPositionButton(row2)
        assertFalse(delete.overlaps(exact))
        assertTrue(delete.left >= bar.left - 0.01f)
        assertTrue(delete.right <= bar.right + 0.01f)
        assertTrue(exact.right <= bar.right + 0.01f)
        assertTrue(exact.width >= ArOverlayLayout.EXACT_POSITION_MIN_WIDTH_DP - 0.01f)
        assertTrue(delete.width >= ArOverlayLayout.DELETE_MIN_WIDTH_DP - 0.01f)
        assertTrue(labelFitsSingleLine("Exact Position", exact.width))
        assertTrue(labelFitsSingleLine("Delete", delete.width))
        val moveRotateDup = ArOverlayLayout.equalButtons(row1, 3)
        assertEquals(3, moveRotateDup.size)
        assertFalse(moveRotateDup[0].overlaps(moveRotateDup[1]))
        assertFalse(moveRotateDup[1].overlaps(moveRotateDup[2]))
        assertTrue(labelFitsSingleLine("Rotate 90°", moveRotateDup[1].width))
        val xyz = ArOverlayLayout.equalButtons(row3, 3)
        assertTrue(xyz.all { it.height >= 40f - 0.01f })

        val landscape = ArOverlayLayout.compactLandscape(800f, 360f, placed = true)
        val landscapeChip = ArOverlayLayout.selectedBar(landscape.viewport, landscape = true)
        assertFalse(landscapeChip.overlaps(landscape.suggestions))
        val landscapeBar = ArOverlayLayout.selectedSheetBar(800f)
        val landscapeExact = ArOverlayLayout.exactPositionButton(
            ArOverlayLayout.selectedActionRow2(landscapeBar)
        )
        assertTrue(landscapeExact.width >= ArOverlayLayout.EXACT_POSITION_MIN_WIDTH_DP - 0.01f)
        assertTrue(labelFitsSingleLine("Exact Position", landscapeExact.width))
    }

    @Test
    fun lowerChromeRowsStayVisibleOnCompactPortraitAndLandscape() {
        listOf(
            ArOverlayLayout.compactPortrait(360f, 800f, placed = true),
            ArOverlayLayout.compactLandscape(800f, 360f, placed = true)
        ).forEach { slots ->
            assertTrue(slots.overlappingPairs().isEmpty())
            val row1 = ArOverlayLayout.chromeRow(slots.chrome, 0)
            assertTrue(row1.bottom <= slots.chrome.bottom + 0.01f)
            assertFalse(row1.overlaps(slots.header))
            val r1 = ArOverlayLayout.equalButtons(row1, 4)
            assertEquals(4, r1.size)
            assertTrue(r1.zipWithNext().none { it.first.overlaps(it.second) })
            assertTrue(r1.all { it.height >= 40f - 0.01f && it.width >= 48f - 0.01f })
            assertEquals("Save My Greenhouse", MyGreenhousePersistence.SAVE_ACTION)
            assertEquals("Save", MyGreenhousePersistence.SAVE_CHROME_LABEL)
            assertTrue(labelFitsSingleLine("Add", r1[0].width))
            assertTrue(labelFitsSingleLine("Move", r1[1].width))
            assertTrue(labelFitsSingleLine("Labels", r1[2].width))
            assertTrue(labelFitsSingleLine("More", r1[3].width))
            val sheet = ArOverlayLayout.selectedSheetBar(360f)
            val sheetRow = ArOverlayLayout.selectedActionRow2(sheet)
            assertTrue(labelFitsSingleLine("Equipment Status", sheet.width))
            assertTrue(labelFitsSingleLine("Clear Equipment", sheet.width))
            assertTrue(labelFitsSingleLine("Reset View", sheet.width))
            assertTrue(labelFitsSingleLine("Reposition", sheet.width))
            assertTrue(labelFitsSingleLine(MyGreenhousePersistence.SAVE_CHROME_LABEL, sheetRow.width))
            assertEquals(0f, slots.palette.area(), 0.01f)
            assertTrue(slots.viewport.height > slots.chrome.height)
            assertTrue(slots.suggestionConflicts().isEmpty())
        }
    }

    @Test
    fun equipmentStatusCopyShowsCountsSavedStateAndNotRunning() = runTest {
        val vm = EquipmentPlacementViewModel(InMemoryEquipmentLayoutRepository())
        vm.attach("grower@greenhands.app", "cfg-status", 10.0, 5.0, 3.0, 3, climate, PreviewMode.MY_GREENHOUSE)
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.CIRCULATION_FAN)
        vm.tapPalette(EquipmentType.EXHAUST_FAN)
        val state = vm.state.value
        assertEquals(1, state.equipmentCount(EquipmentType.CIRCULATION_FAN))
        assertEquals(1, state.equipmentCount(EquipmentType.EXHAUST_FAN))
        assertEquals(0, state.equipmentCount(EquipmentType.FOGGER))
        assertEquals(
            "Circulation Fan: 2",
            ArEquipmentStatusCopy.typeCountLine(EquipmentType.CIRCULATION_FAN, 2)
        )
        assertEquals(
            "Exhaust Fan: 1",
            ArEquipmentStatusCopy.typeCountLine(EquipmentType.EXHAUST_FAN, 1)
        )
        assertEquals("Fogger: 0", ArEquipmentStatusCopy.typeCountLine(EquipmentType.FOGGER, 0))
        assertEquals(ArEquipmentStatusCopy.NOT_RUNNING, "Not running")
        assertEquals(ArEquipmentStatusCopy.UNSAVED_WORKING_COPY, ArEquipmentStatusCopy.savedStateLabel(false))
        vm.saveLayout()
        assertEquals(ArEquipmentStatusCopy.SAVED_LAYOUT, ArEquipmentStatusCopy.savedStateLabel(vm.state.value.saved))
        assertEquals("Optimal: 25.0°C · 70.0 % RH", climate.compactOptimalLabel())
        vm.toggleEquipmentCard()
        assertTrue(vm.state.value.equipmentCardExpanded)
    }

    @Test
    fun clearEquipmentAffectsWorkingCopyOnlyUntilSave() = runTest {
        val repo = InMemoryEquipmentLayoutRepository()
        val vm = EquipmentPlacementViewModel(repo)
        vm.attach("grower@greenhands.app", "cfg-clear", 10.0, 5.0, 3.0, 3, climate, PreviewMode.MY_GREENHOUSE)
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.CIRCULATION_FAN)
        vm.tapPalette(EquipmentType.EXHAUST_FAN)
        vm.saveLayout()
        val savedIds = vm.state.value.instances.map { it.id }
        assertEquals(2, savedIds.size)
        assertTrue(vm.state.value.saved)
        vm.select(savedIds.first())
        vm.duplicateSelected()
        assertEquals(3, vm.state.value.instances.size)
        assertFalse(vm.state.value.saved)

        vm.requestClear()
        assertTrue(vm.state.value.showClearConfirm)
        assertEquals(3, vm.state.value.instances.size)
        vm.cancelClear()
        assertFalse(vm.state.value.showClearConfirm)
        assertEquals(3, vm.state.value.instances.size)
        assertEquals(savedIds, vm.state.value.instances.filter { it.id in savedIds }.map { it.id })

        vm.requestClear()
        vm.confirmClear()
        assertTrue(vm.state.value.instances.isEmpty())
        assertFalse(vm.state.value.saved)
        assertFalse(vm.state.value.showClearConfirm)
        val stored = repo.snapshot("grower@greenhands.app").single()
        assertEquals(savedIds, stored.instances.map { it.id })

        val restored = EquipmentPlacementViewModel(repo)
        restored.attach("grower@greenhands.app", "cfg-clear", 10.0, 5.0, 3.0, 3, climate, PreviewMode.MY_GREENHOUSE)
        assertEquals(savedIds, restored.state.value.instances.map { it.id })
        assertTrue(restored.state.value.saved)
    }

    private fun labelFitsSingleLine(label: String, widthDp: Float, pxPerChar: Float = 6.8f): Boolean =
        label.length * pxPerChar <= widthDp
}
