package com.greenhands.app.equipment

import com.greenhands.app.equipment.data.InMemoryEquipmentLayoutRepository
import com.greenhands.app.equipment.domain.CompactLiveCoordinates
import com.greenhands.app.equipment.domain.PlacementAxis
import com.greenhands.app.equipment.domain.PlacementAxisLimits
import com.greenhands.app.equipment.domain.PlacementContext
import com.greenhands.app.equipment.domain.PlacementCoordinateGuide
import com.greenhands.app.equipment.domain.PlacementSuggester
import com.greenhands.app.equipment.model.DomainPoint
import com.greenhands.app.equipment.model.EquipmentAssetCatalog
import com.greenhands.app.equipment.model.EquipmentType
import com.greenhands.app.equipment.model.SelectedEquipmentTool
import com.greenhands.app.equipment.ui.EquipmentPlacementViewModel
import com.greenhands.app.equipment.ui.OverlayClimate
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class Phase3B2UiCorrectionsTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val climate = OverlayClimate(null, null, "—", "—", "Not published", null, null, null)

    @Before
    fun setMain() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun reset() {
        Dispatchers.resetMain()
    }

    @Test
    fun compactSelectedLabelContainsXyzWithoutLongAxisWords() {
        val label = CompactLiveCoordinates.xyz(1.2, 4.5, 2.1)
        assertEquals("X 1.20 m · Y 4.50 m · Z 2.10 m", label)
        assertTrue(label.contains("X "))
        assertTrue(label.contains("Y "))
        assertTrue(label.contains("Z "))
        assertFalse(CompactLiveCoordinates.containsLongAxisDescriptions(label))
        assertFalse(label.contains("Width position"))
        assertFalse(label.contains("Length position"))
        assertFalse(label.contains("Height position"))
    }

    @Test
    fun coordinateGuideContainsGreenhouseFixedDirections() {
        assertEquals("Position guide", PlacementCoordinateGuide.TITLE)
        assertEquals("X: Left → Right (Width)", PlacementCoordinateGuide.LINE_X)
        assertEquals("Y: Front → Back (Length)", PlacementCoordinateGuide.LINE_Y)
        assertEquals("Z: Floor (0 m) → Roof (Height)", PlacementCoordinateGuide.LINE_Z)
        assertEquals("XYZ", PlacementCoordinateGuide.COLLAPSED_LABEL)
        assertTrue(PlacementCoordinateGuide.LINE_X.contains("Width"))
        assertTrue(PlacementCoordinateGuide.LINE_Y.contains("Length"))
        assertTrue(PlacementCoordinateGuide.LINE_Z.contains("Height"))
    }

    @Test
    fun xSliderChangesOnlyX() = runTest {
        val vm = boundVm()
        placeFan(vm)
        val inst = vm.state.value.instances.single()
        val origin = inst.positionM
        val limit = PlacementAxisLimits.forAxis(inst, emptyList(), vm.placementContext(), PlacementAxis.X)
        val target = nudge(origin.xM, limit.minM, limit.maxM)
        vm.setSelectedTool(SelectedEquipmentTool.X)
        vm.setAxisX(target)
        val moved = vm.state.value.instances.single().positionM
        assertEquals(target, moved.xM, 1e-6)
        assertEquals(origin.yM, moved.yM, 1e-9)
        assertEquals(origin.zM, moved.zM, 1e-9)
    }

    @Test
    fun ySliderChangesOnlyY() = runTest {
        val vm = boundVm()
        placeFan(vm)
        val inst = vm.state.value.instances.single()
        val origin = inst.positionM
        val limit = PlacementAxisLimits.forAxis(inst, emptyList(), vm.placementContext(), PlacementAxis.Y)
        val target = nudge(origin.yM, limit.minM, limit.maxM)
        vm.setSelectedTool(SelectedEquipmentTool.Y)
        vm.setAxisY(target)
        val moved = vm.state.value.instances.single().positionM
        assertEquals(origin.xM, moved.xM, 1e-9)
        assertEquals(target, moved.yM, 1e-6)
        assertEquals(origin.zM, moved.zM, 1e-9)
    }

    @Test
    fun zSliderChangesOnlyZ() = runTest {
        val vm = boundVm()
        placeFan(vm)
        val inst = vm.state.value.instances.single()
        val origin = inst.positionM
        val limit = PlacementAxisLimits.forAxis(inst, emptyList(), vm.placementContext(), PlacementAxis.Z)
        val target = nudge(origin.zM, limit.minM, limit.maxM)
        vm.setSelectedTool(SelectedEquipmentTool.Z)
        vm.setAxisZ(target)
        val moved = vm.state.value.instances.single().positionM
        assertEquals(origin.xM, moved.xM, 1e-9)
        assertEquals(origin.yM, moved.yM, 1e-9)
        assertEquals(target, moved.zM, 1e-6)
    }

    @Test
    fun sliderLimitsRespectGreenhouseAndEquipmentBounds() {
        val context = PlacementContext(10.0, 5.0, 3.0, 3, 0.78)
        val fan = PlacementSuggester.circulationLoop(context, 1) { _, _ -> "c0" }.single()
        val x = PlacementAxisLimits.forAxis(fan, emptyList(), context, PlacementAxis.X)
        val y = PlacementAxisLimits.forAxis(fan, emptyList(), context, PlacementAxis.Y)
        val z = PlacementAxisLimits.forAxis(fan, emptyList(), context, PlacementAxis.Z)
        assertTrue(x.enabled)
        assertTrue(y.enabled)
        assertTrue(z.enabled)
        assertTrue(x.minM >= 0.0)
        assertTrue(x.maxM <= context.widthM)
        assertTrue(y.minM >= 0.0)
        assertTrue(y.maxM <= context.lengthM)
        assertTrue(z.minM >= 0.0)
        assertTrue(z.maxM <= context.heightM)
        val exhaust = PlacementSuggester.exhaustEndWall(context, 1, farWall = true) { _, _ -> "e0" }.single()
        val exhaustY = PlacementAxisLimits.forAxis(exhaust, emptyList(), context, PlacementAxis.Y)
        assertFalse(exhaustY.enabled)
        assertEquals(PlacementAxisLimits.EXHAUST_Y_LOCK, exhaustY.lockReason)
        assertEquals(exhaust.positionM.yM, exhaustY.minM, 0.0)
        val exhaustX = PlacementAxisLimits.forAxis(exhaust, emptyList(), context, PlacementAxis.X)
        assertTrue(exhaustX.enabled)
        assertTrue(exhaustX.maxM <= context.widthM)
    }

    @Test
    fun exactPositionRemainsAvailableAndFitsOneLine() = runTest {
        val vm = boundVm()
        placeFan(vm)
        vm.setShowExactEditor(true)
        assertTrue(vm.state.value.showExactEditor)
        assertEquals(SelectedEquipmentTool.EXACT, vm.state.value.selectedTool)
        val portrait = PreviewOverlayLayout.compactPortraitPlacement(412f, 892f, 88f, 56f)
        val bar = PreviewOverlayLayout.selectedActionBar(portrait.viewport)
        val row2 = PreviewOverlayLayout.selectedActionRow2(bar)
        val exact = PreviewOverlayLayout.exactPositionButton(row2)
        assertTrue(exact.right - exact.left >= PreviewOverlayLayout.EXACT_POSITION_MIN_WIDTH_DP - 0.01f)
        assertTrue(exact.bottom - exact.top <= PreviewOverlayLayout.ACTION_ROW_HEIGHT_DP + 0.01f)
        val row1 = PreviewOverlayLayout.selectedActionRow1(bar)
        assertTrue(exact.top >= row1.bottom - 0.01f)
        assertFalse(row1.overlaps(row2))
    }

    @Test
    fun doneAndModeControlsUseTwoRowsWithoutOverlappingRail() {
        val portrait = PreviewOverlayLayout.compactPortraitPlacement(412f, 892f, 88f, 56f)
        val row1 = PreviewOverlayLayout.placementModeRow1(412f, portrait.chrome.top)
        val row2 = PreviewOverlayLayout.placementModeRow2(412f, portrait.chrome.top)
        assertEquals(PreviewOverlayLayout.MODE_ROW_HEIGHT_DP, row1.bottom - row1.top, 0.01f)
        assertEquals(PreviewOverlayLayout.MODE_ROW_HEIGHT_DP, row2.bottom - row2.top, 0.01f)
        assertEquals(row1.bottom, row2.top, 0.01f)
        assertFalse(row1.overlaps(row2))
        val done = PreviewOverlayLayout.doneButton(row2)
        assertTrue(done.right - done.left >= PreviewOverlayLayout.DONE_MIN_WIDTH_DP - 0.01f)
        assertTrue(done.bottom - done.top <= PreviewOverlayLayout.MODE_ROW_HEIGHT_DP + 0.01f)
        val reset = PreviewOverlayLayout.resetViewButton(row2)
        assertTrue(reset.right - reset.left >= PreviewOverlayLayout.RESET_VIEW_MIN_WIDTH_DP - 0.01f)
        assertFalse(reset.overlaps(done))
        val rail = PreviewOverlayLayout.suggestionRail(portrait.viewport)
        val bar = PreviewOverlayLayout.selectedActionBar(portrait.viewport)
        val guide = PreviewOverlayLayout.coordinateGuide(portrait.viewport, collapsed = false)
        assertFalse(bar.overlaps(rail))
        assertFalse(guide.overlaps(rail))
        assertFalse(guide.overlaps(bar))
        assertFalse(row1.overlaps(rail))
        assertFalse(row2.overlaps(rail))
        assertNotNull(portrait.viewport)
    }

    private fun boundVm(): EquipmentPlacementViewModel {
        val vm = EquipmentPlacementViewModel(InMemoryEquipmentLayoutRepository())
        vm.attach("grower@greenhands.app", "cfg-ui", 10.0, 5.0, 3.0, 3, climate)
        vm.enterPlacementMode()
        return vm
    }

    private fun nudge(current: Double, minM: Double, maxM: Double): Double {
        val up = current + 0.1
        if (up <= maxM) return up
        val down = current - 0.1
        if (down >= minM) return down
        return (minM + maxM) * 0.5
    }

    private fun placeFan(vm: EquipmentPlacementViewModel) {
        vm.tapPalette(EquipmentType.CIRCULATION_FAN)
        vm.confirmGhost()
        assertEquals(1, vm.state.value.instances.size)
    }
}
