package com.greenhands.app.equipment

import com.greenhands.app.equipment.data.EquipmentLayoutCodec
import com.greenhands.app.equipment.data.InMemoryEquipmentLayoutRepository
import com.greenhands.app.equipment.domain.CompactLiveCoordinates
import com.greenhands.app.equipment.domain.EquipmentLabelCatalog
import com.greenhands.app.equipment.domain.EquipmentRecommendationEngine
import com.greenhands.app.equipment.domain.InstanceNumbering
import com.greenhands.app.equipment.domain.MovementPlanes
import com.greenhands.app.equipment.domain.PlacementContext
import com.greenhands.app.equipment.domain.PlacementGesturePolicy
import com.greenhands.app.equipment.domain.PlacementPicking
import com.greenhands.app.equipment.domain.PlacementSuggester
import com.greenhands.app.equipment.domain.PreviewPlacementAdapter
import com.greenhands.app.equipment.domain.SuggestionEvidence
import com.greenhands.app.equipment.domain.ViewportLabelLayout
import com.greenhands.app.equipment.domain.ViewportMath
import com.greenhands.app.equipment.model.DemoEquipmentProfile
import com.greenhands.app.equipment.model.DomainPoint
import com.greenhands.app.equipment.model.EquipmentAssetCatalog
import com.greenhands.app.equipment.model.EquipmentLabels
import com.greenhands.app.equipment.model.EquipmentLayout
import com.greenhands.app.equipment.model.EquipmentType
import com.greenhands.app.equipment.model.LayoutOrigin
import com.greenhands.app.equipment.model.PreviewInteractionMode
import com.greenhands.app.equipment.model.SelectedEquipmentTool
import com.greenhands.app.equipment.ui.EquipmentPlacementViewModel
import com.greenhands.app.equipment.ui.OverlayClimate
import com.greenhands.app.preview3d.domain.CropRowGeometry
import com.greenhands.app.preview3d.domain.SceneScaleMapper
import com.greenhands.app.preview3d.model.DemoGreenhouseProfile
import com.greenhands.app.preview3d.model.PreviewMode
import com.greenhands.app.preview3d.ui.PreviewOverlayLayout
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

class InstanceNumberingTest {
    private fun fan(id: String, n: Int) = EquipmentAssetCatalog.newInstance(
        EquipmentType.CIRCULATION_FAN, id, DomainPoint(2.0, 0.8, 1.6), 0.0, displayNumber = n
    )

    @Test
    fun multipleCirculationFansAreNumbered1_2_3IndependentlyOfExhaustAndFogger() {
        val context = PlacementContext(10.0, 5.0, 3.0, 3, 0.78)
        val suggested = PlacementSuggester.suggest(context, 3, 2, 2)
        val fans = suggested.filter { it.type == EquipmentType.CIRCULATION_FAN }
        val exhaust = suggested.filter { it.type == EquipmentType.EXHAUST_FAN }
        val foggers = suggested.filter { it.type == EquipmentType.FOGGER }
        assertEquals(listOf(1, 2, 3), fans.map { it.displayNumber })
        assertEquals(listOf("Circulation Fan 1", "Circulation Fan 2", "Circulation Fan 3"), fans.map { it.displayLabel() })
        assertEquals(listOf(1, 2), exhaust.map { it.displayNumber })
        assertEquals("Exhaust Fan 1", exhaust.first().displayLabel())
        assertEquals(listOf(1, 2), foggers.map { it.displayNumber })
        assertEquals("Fogger 1", foggers.first().displayLabel())
    }

    @Test
    fun deletingFan1DoesNotRenameFan2AndNextUsesLowestAvailable() {
        val existing = listOf(fan("a", 1), fan("b", 2), fan("c", 3))
        val remaining = existing.filter { it.displayNumber != 1 }
        assertEquals(listOf(2, 3), remaining.map { it.displayNumber })
        assertEquals(1, InstanceNumbering.nextNumber(EquipmentType.CIRCULATION_FAN, remaining))
        assertEquals(1, InstanceNumbering.nextNumber(EquipmentType.EXHAUST_FAN, remaining))
    }

    @Test
    fun missingNumbersAreAssignedByTypeAndCreationOrder() {
        val unordered = listOf(
            EquipmentAssetCatalog.newInstance(EquipmentType.FOGGER, "f-late", DomainPoint(3.0, 2.0, 2.2), 0.0),
            EquipmentAssetCatalog.newInstance(EquipmentType.CIRCULATION_FAN, "c-a", DomainPoint(2.0, 0.8, 1.6), 0.0),
            EquipmentAssetCatalog.newInstance(EquipmentType.CIRCULATION_FAN, "c-b", DomainPoint(4.0, 0.8, 1.6), 0.0),
            EquipmentAssetCatalog.newInstance(EquipmentType.EXHAUST_FAN, "e-a", DomainPoint(10.0, 2.5, 1.5), 0.0)
        )
        val numbered = InstanceNumbering.assignMissing(unordered)
        assertEquals("Fogger 1", numbered[0].displayLabel())
        assertEquals("Circulation Fan 1", numbered[1].displayLabel())
        assertEquals("Circulation Fan 2", numbered[2].displayLabel())
        assertEquals("Exhaust Fan 1", numbered[3].displayLabel())
        assertEquals(numbered[1].id, "c-a")
        assertNotEquals(numbered[1].id, numbered[2].id)
    }
}

class EquipmentLayoutNumberingPersistenceTest {
    @Test
    fun numberingSurvivesPersistenceAndLegacyRecordsWithoutDisplayNumber() {
        val original = listOf(
            EquipmentAssetCatalog.newInstance(EquipmentType.CIRCULATION_FAN, "c1", DomainPoint(2.0, 0.8, 1.6), 0.0, displayNumber = 1),
            EquipmentAssetCatalog.newInstance(EquipmentType.CIRCULATION_FAN, "c2", DomainPoint(4.0, 4.2, 1.6), 180.0, displayNumber = 2)
        )
        val encoded = EquipmentLayoutCodec.encode(
            com.greenhands.app.equipment.model.EquipmentLayout(
                layoutId = "L2",
                configurationId = "C2",
                userId = "grower@greenhands.app",
                lengthM = 10.0,
                widthM = 5.0,
                heightM = 3.0,
                cropRowCount = 3,
                instances = original
            )
        )
        val restored = EquipmentLayoutCodec.decode(encoded)!!
        assertEquals(listOf("Circulation Fan 1", "Circulation Fan 2"), restored.instances.map { it.displayLabel() })
        assertEquals("c1", restored.instances[0].id)

        val legacyInst = "old-fan,CIRCULATION_FAN,2.0,0.8,1.6,0.0,3b2.1,SUSPENDED_OR_BAY,NOT_RUNNING,0,true,"
        val legacy = "v=1|layoutId=old|configId=cfg|userId=u|len=10.0|wid=5.0|hgt=3.0|rows=3|inst=$legacyInst"
        val decoded = EquipmentLayoutCodec.decode(legacy)!!
        assertEquals(1, decoded.instances.single().displayNumber)
        assertEquals("Circulation Fan 1", decoded.instances.single().displayLabel())
        assertEquals("old-fan", decoded.instances.single().id)
        assertEquals(0.8, decoded.instances.single().positionM.xM, 0.0)
        assertEquals(2.0, decoded.instances.single().positionM.yM, 0.0)
        assertEquals(90.0, decoded.instances.single().yawDeg, 0.0)
        assertEquals(2, decoded.schemaVersion)
    }
}

class MovementPlaneTest {
    private val context = PlacementContext(10.0, 5.0, 3.0, 3, 0.78)

    @Test
    fun circulationAndFoggerDragKeepZAndMoveXY() {
        val current = DomainPoint(4.0, 1.1, 2.15)
        val hit = DomainPoint(6.2, 3.4, 0.1)
        val movedFan = MovementPlanes.applyHit(EquipmentType.CIRCULATION_FAN, current, hit, context, false)
        assertEquals(6.2, movedFan.xM, 0.0)
        assertEquals(3.4, movedFan.yM, 0.0)
        assertEquals(2.15, movedFan.zM, 0.0)
        val movedFog = MovementPlanes.applyHit(EquipmentType.FOGGER, current, hit, context, false)
        assertEquals(6.2, movedFog.xM, 0.0)
        assertEquals(3.4, movedFog.yM, 0.0)
        assertEquals(2.15, movedFog.zM, 0.0)
    }

    @Test
    fun exhaustDragMovesXZAndLocksYToSelectedWall() {
        val current = DomainPoint(1.5, 10.0, 1.4)
        val hit = DomainPoint(3.8, 3.0, 2.1)
        val far = MovementPlanes.applyHit(EquipmentType.EXHAUST_FAN, current, hit, context, lockFarWall = true)
        assertEquals(3.8, far.xM, 0.0)
        assertEquals(10.0, far.yM, 0.0)
        assertEquals(2.1, far.zM, 0.0)
        val near = MovementPlanes.applyHit(EquipmentType.EXHAUST_FAN, DomainPoint(1.5, 0.0, 1.4), hit, context, lockFarWall = false)
        assertEquals(3.8, near.xM, 0.0)
        assertEquals(0.0, near.yM, 0.0)
        assertEquals(2.1, near.zM, 0.0)
    }

    @Test
    fun rayPlaneHitUpdatesDomainCoordinates() {
        val layout = SceneScaleMapper.layout(DemoGreenhouseProfile.dimensions())
        val current = DomainPoint(4.0, 1.1, 2.15)
        val plane = MovementPlanes.scenePlane(EquipmentType.CIRCULATION_FAN, current, layout, false)
        val origin = ViewportMath.Vec3(0.0, 8.0, 8.0)
        val targetScene = com.greenhands.app.equipment.domain.DomainSceneCoordinateAdapter.toScene(
            DomainPoint(5.5, 2.0, 2.15),
            layout
        )
        val direction = ViewportMath.Vec3(
            targetScene.x - origin.x,
            targetScene.y - origin.y,
            targetScene.z - origin.z
        )
        val hit = PlacementPicking.hitDomain(origin, direction, plane, layout)
        assertNotNull(hit)
        val moved = MovementPlanes.applyHit(EquipmentType.CIRCULATION_FAN, current, hit!!, context, false)
        assertEquals(5.5, moved.xM, 1e-6)
        assertEquals(2.0, moved.yM, 1e-6)
        assertEquals(2.15, moved.zM, 1e-9)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class EquipmentDragViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setMain() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun reset() {
        Dispatchers.resetMain()
    }

    private fun boundVm(): EquipmentPlacementViewModel {
        val vm = EquipmentPlacementViewModel(InMemoryEquipmentLayoutRepository())
        vm.attach("grower@greenhands.app", "cfg", 10.0, 5.0, 3.0, 3, OverlayClimate(null, null, "—", "—", "Not published", null, null, null))
        vm.enterPlacementMode()
        return vm
    }

    @Test
    fun objectDragUpdatesDomainCoordinatesAndInvalidDragDoesNotCommit() = runTest {
        val vm = boundVm()
        vm.tapPalette(EquipmentType.CIRCULATION_FAN)
        vm.confirmGhost()
        val placed = vm.state.value.instances.single()
        assertEquals("Circulation Fan 1", placed.displayLabel())
        val origin = placed.positionM
        vm.startDrag(placed.id)
        vm.dragTo(placed.id, DomainPoint(origin.xM + 0.3, origin.yM, 0.05))
        val dragged = vm.state.value.instances.single()
        assertEquals(origin.xM + 0.3, dragged.positionM.xM, 1e-9)
        assertEquals(origin.yM, dragged.positionM.yM, 1e-9)
        assertEquals(origin.zM, dragged.positionM.zM, 1e-9)
        vm.endDrag()

        vm.startDrag(placed.id)
        vm.dragTo(placed.id, DomainPoint(99.0, 99.0, origin.zM))
        assertFalse(vm.state.value.instances.single().validity.isValid)
        vm.endDrag()
        val reverted = vm.state.value.instances.single()
        assertEquals(origin.xM + 0.3, reverted.positionM.xM, 1e-9)
        assertEquals(origin.yM, reverted.positionM.yM, 1e-9)
        assertTrue(reverted.validity.isValid)
    }

    @Test
    fun numberingSurvivesViewModelSaveAndDoesNotRenameOnDelete() = runTest {
        val repo = InMemoryEquipmentLayoutRepository()
        val vm = EquipmentPlacementViewModel(repo)
        vm.attach("grower@greenhands.app", "cfg-n", 10.0, 5.0, 3.0, 3, OverlayClimate(null, null, "—", "—", "Not published", null, null, null))
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.CIRCULATION_FAN)
        vm.confirmGhost()
        vm.duplicateSelected()
        vm.duplicateSelected()
        assertEquals(listOf(1, 2, 3), vm.state.value.instances.map { it.displayNumber })
        val first = vm.state.value.instances.first { it.displayNumber == 1 }
        vm.select(first.id)
        vm.requestDelete()
        vm.deleteSelected()
        assertEquals(listOf(2, 3), vm.state.value.instances.map { it.displayNumber })
        vm.tapPalette(EquipmentType.CIRCULATION_FAN)
        vm.confirmGhost()
        assertTrue(vm.state.value.instances.any { it.displayNumber == 1 && it.displayLabel() == "Circulation Fan 1" })
        vm.saveLayout()
        val vm2 = EquipmentPlacementViewModel(repo)
        vm2.attach("grower@greenhands.app", "cfg-n", 10.0, 5.0, 3.0, 3, OverlayClimate(null, null, "—", "—", "Not published", null, null, null))
        assertEquals(setOf(1, 2, 3), vm2.state.value.instances.map { it.displayNumber }.toSet())
        assertEquals(setOf("Circulation Fan 1", "Circulation Fan 2", "Circulation Fan 3"), vm2.state.value.instances.map { it.displayLabel() }.toSet())
        assertEquals(3, vm2.state.value.instances.map { it.id }.distinct().size)
    }

    @Test
    fun deleteUsesStableIdAndDoesNotRemoveOrRenameOtherFans() = runTest {
        val repo = InMemoryEquipmentLayoutRepository()
        val vm = EquipmentPlacementViewModel(repo)
        vm.attach("grower@greenhands.app", "cfg-del", 10.0, 5.0, 3.0, 3, OverlayClimate(null, null, "—", "—", "Not published", null, null, null))
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.CIRCULATION_FAN)
        vm.confirmGhost()
        vm.duplicateSelected()
        val fan1 = vm.state.value.instances.single { it.displayNumber == 1 }
        val fan2 = vm.state.value.instances.single { it.displayNumber == 2 }
        val fan2Pose = fan2.positionM
        vm.select(fan1.id)
        vm.requestDelete()
        assertTrue(vm.state.value.showDeleteConfirm)
        assertEquals(fan1.id, vm.state.value.pendingDeleteId)
        assertEquals("Circulation Fan 1", vm.state.value.pendingDeleteLabel)
        assertEquals("Delete Fan 1", vm.state.value.pendingDeleteConfirm)
        vm.deleteSelected()
        val remaining = vm.state.value.instances
        assertEquals(1, remaining.size)
        assertEquals(fan2.id, remaining.single().id)
        assertEquals("Circulation Fan 2", remaining.single().displayLabel())
        assertEquals(2, remaining.single().displayNumber)
        assertEquals(fan2Pose.xM, remaining.single().positionM.xM, 0.0)
        assertEquals(fan2Pose.yM, remaining.single().positionM.yM, 0.0)
        assertFalse(vm.state.value.showUndo)
        assertFalse(vm.state.value.statusMessage.orEmpty().contains("Deleted", ignoreCase = true))
        vm.undoDelete()
        assertEquals(2, vm.state.value.instances.size)
        assertTrue(vm.state.value.instances.any { it.id == fan1.id && it.displayLabel() == "Circulation Fan 1" })
        assertTrue(vm.state.value.instances.any { it.id == fan2.id && it.displayLabel() == "Circulation Fan 2" })
        vm.select(fan1.id)
        vm.requestDelete()
        vm.deleteById(fan1.id)
        vm.saveLayout()
        val vm2 = EquipmentPlacementViewModel(repo)
        vm2.attach("grower@greenhands.app", "cfg-del", 10.0, 5.0, 3.0, 3, OverlayClimate(null, null, "—", "—", "Not published", null, null, null))
        assertEquals(listOf(fan2.id), vm2.state.value.instances.map { it.id })
        assertEquals(listOf("Circulation Fan 2"), vm2.state.value.instances.map { it.displayLabel() })
    }
}

class ViewportLabelMathTest {
    @Test
    fun coordinateLabelFollowsProjectedPointAndStaysClamped() {
        val first = ViewportMath.ndcToScreen(-0.2, 0.1, 1.0, 400.0, 800.0)!!
        val second = ViewportMath.ndcToScreen(0.4, -0.2, 1.0, 400.0, 800.0)!!
        assertTrue(second.first > first.first)
        assertTrue(second.second > first.second)
        val clamped = ViewportMath.clampLabel(-40.0, 900.0, 400.0, 800.0, 8.0)
        assertEquals(8.0, clamped.first, 0.0)
        assertEquals(792.0, clamped.second, 0.0)
        val live = CompactLiveCoordinates.xyz(4.2, 1.1, 2.15)
        assertEquals("X 4.20 m · Y 1.10 m · Z 2.15 m", live)
        assertFalse(CompactLiveCoordinates.containsLongAxisDescriptions(live))
        assertEquals("Circulation Fan 2", EquipmentLabels.label(EquipmentType.CIRCULATION_FAN, 2))
        assertEquals("Delete Fan 1", EquipmentLabels.deleteAction(EquipmentType.CIRCULATION_FAN, 1))
    }

    @Test
    fun everyPlacedInstanceHasNearbyNameAndSelectedShowsLiveXYZ() {
        val fan1 = EquipmentAssetCatalog.newInstance(EquipmentType.CIRCULATION_FAN, "id-1", DomainPoint(2.0, 0.8, 1.6), 0.0, displayNumber = 1)
        val fan2 = EquipmentAssetCatalog.newInstance(EquipmentType.CIRCULATION_FAN, "id-2", DomainPoint(4.2, 1.1, 2.15), 0.0, displayNumber = 2)
        val fog = EquipmentAssetCatalog.newInstance(EquipmentType.FOGGER, "id-f", DomainPoint(5.0, 2.0, 2.2), 0.0, displayNumber = 1)
        val contents = EquipmentLabelCatalog.contents(listOf(fan1, fan2, fog), selectedId = "id-2")
        assertEquals(3, contents.size)
        assertEquals(setOf("Circulation Fan 1", "Circulation Fan 2", "Fogger 1"), contents.map { it.name }.toSet())
        val selected = contents.single { it.selected }
        assertEquals("id-2", selected.id)
        assertTrue(selected.showCoordinates)
        assertEquals(4.2, selected.xM, 0.0)
        assertEquals(1.1, selected.yM, 0.0)
        assertEquals(2.15, selected.zM, 0.0)
        contents.filter { !it.selected }.forEach { assertFalse(it.showCoordinates) }
        val hidden = EquipmentLabelCatalog.contents(listOf(fan1, fan2, fog), selectedId = "id-2", labelsVisible = false)
        assertEquals(listOf("Circulation Fan 2"), hidden.map { it.name })
        assertTrue(hidden.single().showCoordinates)
    }

    @Test
    fun overlappingLabelsAreSeparatedAndClampedInsideViewport() {
        val inputs = listOf(
            ViewportLabelLayout.Input("a", 200.0, 200.0, 80.0, 20.0, selected = true),
            ViewportLabelLayout.Input("b", 200.0, 200.0, 80.0, 20.0, selected = false)
        )
        val placed = ViewportLabelLayout.place(inputs, 400.0, 400.0, padPx = 8.0, liftPx = 8.0)
        assertEquals(2, placed.size)
        val a = placed.single { it.id == "a" }
        val b = placed.single { it.id == "b" }
        val overlap = a.xPx < b.xPx + b.widthPx && a.xPx + a.widthPx > b.xPx &&
            a.yPx < b.yPx + b.heightPx && a.yPx + a.heightPx > b.yPx
        assertFalse(overlap)
        placed.forEach { item ->
            assertTrue(item.xPx >= 8.0)
            assertTrue(item.yPx >= 8.0)
            assertTrue(item.xPx + item.widthPx <= 400.0)
            assertTrue(item.yPx + item.heightPx <= 400.0)
        }
    }
}

class PlacementGesturePolicyTest {
    @Test
    fun viewModeOrbitIsPreservedAndPlacementDragDoesNotOrbit() {
        assertFalse(
            PlacementGesturePolicy.blocksCameraOrbit(
                PreviewInteractionMode.VIEW, orbitAllowed = false, pointerCount = 1, dragging = false
            )
        )
        assertTrue(
            PlacementGesturePolicy.blocksCameraOrbit(
                PreviewInteractionMode.PLACEMENT, orbitAllowed = false, pointerCount = 1, dragging = true
            )
        )
        assertTrue(
            PlacementGesturePolicy.blocksCameraOrbit(
                PreviewInteractionMode.PLACEMENT, orbitAllowed = false, pointerCount = 1, dragging = false
            )
        )
        assertFalse(
            PlacementGesturePolicy.blocksCameraOrbit(
                PreviewInteractionMode.PLACEMENT, orbitAllowed = true, pointerCount = 1, dragging = false
            )
        )
        assertTrue(
            PlacementGesturePolicy.blocksCameraOrbit(
                PreviewInteractionMode.PLACEMENT, orbitAllowed = true, pointerCount = 1, dragging = true
            )
        )
        assertFalse(
            PlacementGesturePolicy.blocksCameraOrbit(
                PreviewInteractionMode.PLACEMENT, orbitAllowed = false, pointerCount = 2, dragging = true
            )
        )
    }
}

class CompactOverlayLayoutTest {
    @Test
    fun compactPortraitOverlayDoesNotOverlapAndLandscapeSidePanelIsValid() {
        val portrait = PreviewOverlayLayout.compactPortrait(
            width = 412f,
            height = 892f,
            headerHeight = 88f,
            cropRowHeight = 56f,
            chromeHeight = 220f
        )
        assertTrue(portrait.overlappingPairs().isEmpty())
        assertTrue(portrait.viewport.area() > 0f)
        assertEquals(0f, portrait.header.top, 0f)
        assertTrue(portrait.cropRow.top >= portrait.header.bottom - 0.01f)
        assertTrue(portrait.chrome.top >= portrait.viewport.bottom - 0.01f)

        val landscape = PreviewOverlayLayout.landscapeWide(
            width = 915f,
            height = 412f,
            headerHeight = 72f,
            cropRowHeight = 52f,
            sidePanelWidth = 320f
        )
        assertTrue(landscape.overlappingPairs().isEmpty())
        assertTrue(landscape.viewport.area() > 0f)
        assertNotNull(landscape.sidePanel)
        assertTrue(PreviewOverlayLayout.isWideLandscape(915f, true))
        assertFalse(PreviewOverlayLayout.isWideLandscape(412f, false))
    }

    @Test
    fun compactPortraitKeepsViewportPaletteAndSuggestionRailVisibleTogether() {
        val portrait = PreviewOverlayLayout.compactPortraitPlacement(
            width = 412f,
            height = 892f,
            headerHeight = 88f,
            cropRowHeight = 56f
        )
        val viewportH = portrait.viewport.bottom - portrait.viewport.top
        val chromeH = portrait.chrome.bottom - portrait.chrome.top
        assertTrue(viewportH >= PreviewOverlayLayout.PORTRAIT_MIN_VIEWPORT_DP)
        assertTrue(viewportH > chromeH)
        assertEquals(
            PreviewOverlayLayout.PORTRAIT_TOOLBAR_DP + PreviewOverlayLayout.PORTRAIT_PALETTE_DP,
            chromeH,
            0.01f
        )
        val palette = PreviewOverlayLayout.paletteStrip(412f, 892f)
        val rail = PreviewOverlayLayout.suggestionRail(portrait.viewport)
        assertTrue(palette.area() > 0f)
        assertTrue(rail.area() > 0f)
        assertFalse(portrait.viewport.overlaps(palette))
        assertTrue(rail.left >= portrait.viewport.left)
        assertEquals(portrait.viewport.bottom, rail.bottom, 0.01f)
        assertTrue(portrait.overlappingPairs().isEmpty())
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class EmptyPreviewAndLayoutOriginTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setMain() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun reset() {
        Dispatchers.resetMain()
    }

    private val climate = OverlayClimate(null, null, "—", "—", "Not published", null, null, null, 25.1, 80.0)

    @Test
    fun freshPreviewContainsZeroEquipmentAndSuggestionsStaySeparate() = runTest {
        val repo = InMemoryEquipmentLayoutRepository()
        val demo = EquipmentPlacementViewModel(repo)
        demo.attach(
            "grower@greenhands.app",
            "demo-cfg",
            DemoGreenhouseProfile.LENGTH_M,
            DemoGreenhouseProfile.WIDTH_M,
            DemoGreenhouseProfile.HEIGHT_M,
            DemoGreenhouseProfile.CROP_ROW_COUNT,
            climate,
            PreviewMode.DEMO
        )
        assertTrue(demo.state.value.placedEquipment.isEmpty())
        assertTrue(demo.state.value.suggestedPlacements.isEmpty())
        assertTrue(demo.state.value.showsAutoPlaceSuggestedLayout)
        val mine = EquipmentPlacementViewModel(repo)
        mine.attach("grower@greenhands.app", "mine-cfg", 12.0, 6.0, 3.5, 4, climate, PreviewMode.MY_GREENHOUSE)
        assertTrue(mine.state.value.placedEquipment.isEmpty())
        mine.enterPlacementMode()
        mine.openSuggestionGallery(EquipmentType.CIRCULATION_FAN)
        assertTrue(mine.state.value.placedEquipment.isEmpty())
        mine.closeSuggestionGallery()
        assertTrue(mine.state.value.placedEquipment.isEmpty())
    }

    @Test
    fun savedManualLayoutRestoresAndLegacyAutomaticDoesNot() = runTest {
        val repo = InMemoryEquipmentLayoutRepository()
        val vm = EquipmentPlacementViewModel(repo)
        vm.attach("grower@greenhands.app", "cfg-manual", 10.0, 5.0, 3.0, 3, climate)
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.CIRCULATION_FAN)
        vm.saveLayout()
        val restored = EquipmentPlacementViewModel(repo)
        restored.attach("grower@greenhands.app", "cfg-manual", 10.0, 5.0, 3.0, 3, climate)
        assertEquals(1, restored.state.value.instances.size)
        assertEquals("Saved equipment layout loaded", restored.state.value.statusMessage)

        val legacy = EquipmentLayout(
            layoutId = "auto",
            configurationId = "cfg-legacy",
            userId = "grower@greenhands.app",
            lengthM = 10.0,
            widthM = 5.0,
            heightM = 3.0,
            cropRowCount = 3,
            instances = PlacementSuggester.suggest(PlacementContext(10.0, 5.0, 3.0, 3, 0.78), 2, 0, 0),
            origin = LayoutOrigin.UNSPECIFIED,
            explicitlySaved = false
        )
        assertEquals(LayoutOrigin.LEGACY_AUTOMATIC, legacy.resolvedOrigin())
        assertFalse(legacy.shouldRestoreAsPlaced())
        repo.save(legacy)
        val skip = EquipmentPlacementViewModel(repo)
        skip.attach("grower@greenhands.app", "cfg-legacy", 10.0, 5.0, 3.0, 3, climate)
        assertTrue(skip.state.value.instances.isEmpty())
    }

    @Test
    fun paletteDragPlacesOnlyOnValidReleaseAndDoesNotOpenExactEditor() = runTest {
        val vm = EquipmentPlacementViewModel(InMemoryEquipmentLayoutRepository())
        vm.attach("grower@greenhands.app", "cfg-drag", 10.0, 5.0, 3.0, 3, climate)
        vm.enterPlacementMode()
        vm.beginPaletteDrag(EquipmentType.CIRCULATION_FAN)
        vm.paletteDragTo(EquipmentType.CIRCULATION_FAN, DomainPoint(4.2, 1.1, 2.15))
        assertNotNull(vm.state.value.ghost)
        assertTrue(vm.state.value.ghost!!.isGhost)
        assertFalse(vm.state.value.showExactEditor)
        val live = EquipmentLabelCatalog.contents(
            vm.state.value.displayInstances,
            vm.state.value.selectedId,
            vm.state.value.draggingId
        )
        assertTrue(live.single { it.selected }.showCoordinates)
        vm.endPaletteDrag(overViewport = true)
        assertEquals(1, vm.state.value.instances.size)
        assertNull(vm.state.value.ghost)
        assertFalse(vm.state.value.showExactEditor)
        assertEquals("Circulation Fan 1", vm.state.value.instances.single().displayLabel())

        vm.beginPaletteDrag(EquipmentType.CIRCULATION_FAN)
        vm.paletteDragTo(EquipmentType.CIRCULATION_FAN, DomainPoint(99.0, 99.0, 2.15))
        vm.endPaletteDrag(overViewport = true)
        assertEquals(1, vm.state.value.instances.size)
        assertNull(vm.state.value.ghost)
    }

    @Test
    fun applyLayoutIsBlockedWhenCountCannotBeCalculatedAndWorksWhenRatedAirflowIsProvided() = runTest {
        val vm = EquipmentPlacementViewModel(InMemoryEquipmentLayoutRepository())
        vm.attach("grower@greenhands.app", "cfg-apply", 10.0, 5.0, 3.0, 3, climate, PreviewMode.MY_GREENHOUSE)
        vm.enterPlacementMode()
        vm.openSuggestionGallery(EquipmentType.CIRCULATION_FAN)
        assertEquals(EquipmentType.CIRCULATION_FAN, vm.state.value.galleryType)
        assertFalse(vm.state.value.showsAutoPlaceSuggestedLayout)
        assertFalse(vm.state.value.showsApplyLayout(EquipmentType.CIRCULATION_FAN))
        vm.applySuggestionLayout(EquipmentType.CIRCULATION_FAN)
        assertTrue(vm.state.value.instances.isEmpty())
        assertEquals("Cannot apply layout — required inputs are missing.", vm.state.value.statusMessage)
        vm.autoPlaceSuggestedLayout(EquipmentType.CIRCULATION_FAN)
        assertTrue(vm.state.value.instances.isEmpty())

        vm.updateRecommendationInput(circCfm = "1000")
        assertTrue(vm.state.value.showsApplyLayout(EquipmentType.CIRCULATION_FAN))
        assertFalse(vm.state.value.showsApplyLayout(EquipmentType.EXHAUST_FAN))
        vm.applySuggestionLayout(EquipmentType.CIRCULATION_FAN)
        assertTrue(vm.state.value.instances.isNotEmpty())
        assertTrue(vm.state.value.instances.all { it.type == EquipmentType.CIRCULATION_FAN && !it.isGhost })
        val ids = vm.state.value.instances.map { it.id }
        val first = vm.state.value.instances.first()
        vm.startDrag(first.id)
        vm.dragTo(first.id, DomainPoint(first.positionM.xM + 0.4, first.positionM.yM, first.positionM.zM))
        vm.endDrag()
        assertEquals(first.positionM.xM + 0.4, vm.state.value.instances.first { it.id == first.id }.positionM.xM, 1e-9)
        vm.undoDelete()
        assertTrue(vm.state.value.instances.none { it.id in ids })
    }

    @Test
    fun demoStartsEmptyAndAlwaysExposesAutoPlaceWithoutApplyingOnOpen() = runTest {
        val repo = InMemoryEquipmentLayoutRepository()
        val vm = EquipmentPlacementViewModel(repo)
        vm.attach(
            "grower@greenhands.app",
            "demo-empty",
            DemoGreenhouseProfile.LENGTH_M,
            DemoGreenhouseProfile.WIDTH_M,
            DemoGreenhouseProfile.HEIGHT_M,
            DemoGreenhouseProfile.CROP_ROW_COUNT,
            climate,
            PreviewMode.DEMO
        )
        assertTrue(vm.state.value.placedEquipment.isEmpty())
        assertTrue(vm.state.value.instances.isEmpty())
        assertTrue(vm.state.value.showsAutoPlaceSuggestedLayout)
        assertFalse(vm.state.value.showsApplyLayout(EquipmentType.CIRCULATION_FAN))
        vm.enterPlacementMode()
        EquipmentType.entries.forEach { type ->
            vm.openSuggestionGallery(type)
            assertTrue(vm.state.value.showsAutoPlaceSuggestedLayout)
            assertFalse(vm.state.value.showsApplyLayout(type))
            assertTrue(vm.state.value.placedEquipment.isEmpty())
            assertEquals(
                "Demo equipment profile — research-guided example, not installation guidance.",
                DemoEquipmentProfile.NOTICE
            )
        }
        vm.closeSuggestionGallery()
        assertTrue(vm.state.value.placedEquipment.isEmpty())
        assertTrue(repo.snapshot("grower@greenhands.app").isEmpty())
        assertFalse(vm.state.value.saved)
    }

    @Test
    fun demoAutoPlaceCreatesCalculatedMovableDeletableLayoutAndDoesNotSave() = runTest {
        val repo = InMemoryEquipmentLayoutRepository()
        val vm = EquipmentPlacementViewModel(repo)
        vm.attach(
            "grower@greenhands.app",
            "demo-auto",
            DemoGreenhouseProfile.LENGTH_M,
            DemoGreenhouseProfile.WIDTH_M,
            DemoGreenhouseProfile.HEIGHT_M,
            DemoGreenhouseProfile.CROP_ROW_COUNT,
            climate,
            PreviewMode.DEMO
        )
        vm.enterPlacementMode()
        vm.openSuggestionGallery(EquipmentType.CIRCULATION_FAN)
        assertTrue(vm.state.value.showsAutoPlaceSuggestedLayout)
        vm.autoPlaceSuggestedLayout(EquipmentType.CIRCULATION_FAN)
        val expected = EquipmentRecommendationEngine.circulationCount(
            DemoEquipmentProfile.inputs(
                lengthM = DemoGreenhouseProfile.LENGTH_M,
                widthM = DemoGreenhouseProfile.WIDTH_M,
                heightM = DemoGreenhouseProfile.HEIGHT_M,
                cropRowCount = DemoGreenhouseProfile.CROP_ROW_COUNT,
                canopyHeightM = DemoGreenhouseProfile.HEIGHT_M * CropRowGeometry.HEIGHT_FILL,
                indoorTemperatureC = climate.optimalTemperatureC,
                indoorRhPercent = climate.optimalHumidityPercent
            )
        ).appliedCount
        assertNotNull(expected)
        assertTrue(expected!! > 0)
        val placed = vm.state.value.instances
        assertEquals(expected, placed.size)
        assertTrue(placed.all { it.type == EquipmentType.CIRCULATION_FAN && !it.isGhost })
        assertEquals(
            (1..expected).map { "Circulation Fan $it" },
            placed.sortedBy { it.displayNumber }.map { it.displayLabel() }
        )
        assertFalse(vm.state.value.saved)
        assertTrue(repo.snapshot("grower@greenhands.app").isEmpty())

        val first = placed.first { it.displayNumber == 1 }
        val originalYaw = first.yawDeg
        vm.select(first.id)
        vm.rotateSelected(90.0)
        val rotated = vm.state.value.instances.first { it.id == first.id }
        assertEquals(originalYaw + 90.0, rotated.yawDeg, 1e-9)
        vm.startDrag(first.id)
        vm.dragTo(first.id, DomainPoint(rotated.positionM.xM + 0.4, rotated.positionM.yM, rotated.positionM.zM))
        vm.endDrag()
        val moved = vm.state.value.instances.first { it.id == first.id }
        assertEquals(rotated.positionM.xM + 0.4, moved.positionM.xM, 1e-9)

        val otherIds = vm.state.value.instances.filter { it.id != first.id }.map { it.id }
        vm.select(first.id)
        vm.requestDelete()
        vm.deleteSelected()
        val remaining = vm.state.value.instances
        assertTrue(remaining.none { it.id == first.id })
        assertEquals(otherIds.toSet(), remaining.map { it.id }.toSet())
        remaining.filter { it.displayNumber == 2 }.forEach {
            assertEquals("Circulation Fan 2", it.displayLabel())
        }
        assertFalse(vm.state.value.saved)
        assertTrue(repo.snapshot("grower@greenhands.app").isEmpty())
    }

    @Test
    fun compactOptimalLabelUsesConfiguredToptAndRhoptNotRanges() {
        assertEquals("Optimal: 25.1°C · 80.0 % RH", climate.compactOptimalLabel())
        val missing = OverlayClimate(null, null, "24.0–26.1°C", "75–100%", "—", null, null, null)
        assertEquals("Not available", missing.compactOptimalLabel())
    }
}

class SuggestionEvidenceAndPickingTest {
    @Test
    fun threeTypeRationalesNeverClaimSriLankaValidationOrUniversalKimSpacing() {
        val context = PlacementContext(10.0, 5.0, 3.0, 3, 0.78)
        EquipmentType.entries.forEach { type ->
            val why = SuggestionEvidence.rationale(type, null, context, illustrationOnly = true)
            assertEquals(SuggestionEvidence.SRI_LANKA_STATUS, why.sriLankaStatus)
            assertFalse(why.preference.contains("Best", ignoreCase = true))
            assertTrue(why.illustrationOnly)
            if (type == EquipmentType.FOGGER) {
                assertTrue(why.adaptations.any { it.contains("2.3 m") })
            }
            if (type == EquipmentType.CIRCULATION_FAN) {
                assertTrue(why.adaptations.any { it.contains("10–15 ft") })
            }
            if (type == EquipmentType.EXHAUST_FAN) {
                assertTrue(why.limitations.any { it.contains("make-up air") })
            }
        }
        assertEquals(2, SuggestionEvidence.illustrationCount(EquipmentType.CIRCULATION_FAN, null))
        assertFalse(SuggestionEvidence.canApplyCalculatedLayout(null))
        assertTrue(SuggestionEvidence.showsAutoPlaceSuggestedLayout(true))
        assertFalse(SuggestionEvidence.showsAutoPlaceSuggestedLayout(false))
        assertFalse(SuggestionEvidence.showsApplyLayout(true, null))
        assertFalse(SuggestionEvidence.showsApplyLayout(false, null))
        val demoRec = EquipmentRecommendationEngine.recommend(
            DemoEquipmentProfile.inputs(
                lengthM = DemoGreenhouseProfile.LENGTH_M,
                widthM = DemoGreenhouseProfile.WIDTH_M,
                heightM = DemoGreenhouseProfile.HEIGHT_M,
                cropRowCount = DemoGreenhouseProfile.CROP_ROW_COUNT,
                canopyHeightM = DemoGreenhouseProfile.HEIGHT_M * CropRowGeometry.HEIGHT_FILL
            )
        )
        assertTrue(SuggestionEvidence.canApplyCalculatedLayout(demoRec.circulation))
        assertTrue(SuggestionEvidence.canApplyCalculatedLayout(demoRec.exhaust))
        assertTrue(SuggestionEvidence.canApplyCalculatedLayout(demoRec.fogger))
        assertEquals(
            "Demo equipment profile — research-guided example, not installation guidance.",
            DemoEquipmentProfile.NOTICE
        )
    }

    @Test
    fun rayAabbPicksTheCloserEquipment() {
        val layout = SceneScaleMapper.layout(DemoGreenhouseProfile.dimensions())
        val a = EquipmentAssetCatalog.newInstance(EquipmentType.CIRCULATION_FAN, "near", DomainPoint(2.5, 2.0, 1.6), 0.0, displayNumber = 1)
        val b = EquipmentAssetCatalog.newInstance(EquipmentType.CIRCULATION_FAN, "far", DomainPoint(2.5, 8.0, 1.6), 0.0, displayNumber = 2)
        val placements = com.greenhands.app.equipment.render.EquipmentSceneMapper.placements(listOf(a, b), layout)
        val target = com.greenhands.app.equipment.domain.DomainSceneCoordinateAdapter.toScene(a.positionM, layout)
        val origin = ViewportMath.Vec3(target.x, target.y + 4.0, target.z + 4.0)
        val direction = ViewportMath.Vec3(target.x - origin.x, target.y - origin.y, target.z - origin.z)
        val id = PreviewPlacementAdapter.pickEquipment(origin, direction, placements)
        assertEquals("near", id)
        assertEquals(SelectedEquipmentTool.MOVE, SelectedEquipmentTool.MOVE)
    }
}
