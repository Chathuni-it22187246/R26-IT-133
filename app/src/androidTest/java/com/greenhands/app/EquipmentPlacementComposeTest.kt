package com.greenhands.app

import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.greenhands.app.equipment.data.InMemoryEquipmentLayoutRepository
import com.greenhands.app.equipment.model.EquipmentType
import com.greenhands.app.equipment.model.PreviewInteractionMode
import com.greenhands.app.equipment.ui.CompactCropEnvironmentRow
import com.greenhands.app.equipment.ui.CoordinateEditor
import com.greenhands.app.equipment.ui.CropStatusCard
import com.greenhands.app.equipment.ui.DeleteEquipmentDialog
import com.greenhands.app.equipment.ui.EnvironmentStatusCard
import com.greenhands.app.equipment.ui.EquipmentPalette
import com.greenhands.app.equipment.ui.EquipmentPlacementViewModel
import com.greenhands.app.equipment.ui.EquipmentThresholdCard
import com.greenhands.app.equipment.ui.OverlayClimate
import com.greenhands.app.equipment.ui.PlacementActions
import com.greenhands.app.equipment.ui.PlacementControlStrip
import com.greenhands.app.equipment.ui.SuggestionRail
import com.greenhands.app.equipment.ui.ViewportCoordinateLabel
import com.greenhands.app.equipment.future.EnvironmentDataSourceKind
import com.greenhands.app.preview3d.ui.PlacementPortraitWorkspace
import com.greenhands.app.ui.theme.GreenHandsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EquipmentPlacementComposeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun boundVm(): EquipmentPlacementViewModel {
        val vm = EquipmentPlacementViewModel(InMemoryEquipmentLayoutRepository())
        vm.attach("grower@greenhands.app", "cfg", 10.0, 5.0, 3.0, 3, OverlayClimate(null, null, "24–27°C", "70–80%", "VPD target not published", null, null, null))
        return vm
    }

    @Test
    fun openingPlacementModeShowsPaletteAndAllowsTwoCirculationFans() {
        val vm = boundVm()
        composeRule.setContent {
            GreenHandsTheme {
                val state by vm.state.collectAsState()
                PlacementActions(
                    mode = state.mode,
                    onEnterPlacement = { vm.enterPlacementMode() },
                    onEnterView = { vm.enterViewMode() },
                    onSuggest = {},
                    onSave = { vm.saveLayout() },
                    onClear = {}
                )
                if (state.mode == PreviewInteractionMode.PLACEMENT) {
                    EquipmentPalette(onTap = { vm.tapPalette(it) }, onDragStart = {}, onDragEnd = {})
                }
            }
        }
        composeRule.onNodeWithTag("placement_enter").assertIsDisplayed().performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("equipment_palette").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("equipment_palette").assertIsDisplayed()
        composeRule.onNodeWithTag("palette_circulation").performClick()
        composeRule.runOnIdle {
            vm.confirmGhost()
            vm.duplicateSelected()
        }
        composeRule.runOnIdle {
            assertEquals(2, vm.state.value.instances.count { it.type == EquipmentType.CIRCULATION_FAN })
        }
    }

    @Test
    fun coordinateEditorAndInvalidWarningAndSave() {
        val vm = boundVm()
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.CIRCULATION_FAN)
        composeRule.waitForIdle()
        composeRule.setContent {
            GreenHandsTheme {
                val state by vm.state.collectAsState()
                androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
                    PlacementActions(
                        mode = state.mode,
                        onEnterPlacement = {},
                        onEnterView = {},
                        onSuggest = {},
                        onSave = { vm.saveLayout() },
                        onClear = {}
                    )
                    CoordinateEditor(
                    state = state,
                    onApply = { x, y, z, yaw -> vm.applyNumericEdit(x, y, z, yaw) },
                    onRotate = { vm.rotateSelected() },
                    onDuplicate = { vm.duplicateSelected() },
                    onDelete = { vm.deleteSelected() },
                    onConfirmGhost = { vm.confirmGhost() },
                    onCancelGhost = { vm.cancelGhost() }
                )
                }
            }
        }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("coordinate_editor").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("coordinate_editor").assertExists()
        composeRule.onNodeWithTag("coord_instance_name").assertExists()
        composeRule.onNodeWithTag("coord_position_section").assertExists()
        composeRule.onNodeWithTag("coord_size_section").assertExists()
        composeRule.onNodeWithTag("coordinate_live").assertExists()
        composeRule.onNodeWithTag("coord_x").performTextReplacement("99")
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("placement_invalid_warning").fetchSemanticsNodes().isNotEmpty() ||
                vm.state.value.selected?.validity?.isValid == false ||
                vm.state.value.editError != null
        }
        composeRule.onNodeWithTag("placement_save").assertExists()
        composeRule.onNodeWithTag("placement_delete").performClick()
        composeRule.runOnIdle { assertTrue(vm.state.value.instances.isEmpty()) }
    }

    @Test
    fun previewDataSourceDoesNotClaimLive() {
        composeRule.setContent {
            GreenHandsTheme {
                EnvironmentStatusCard(kind = EnvironmentDataSourceKind.PREVIEW)
                CropStatusCard(climate = OverlayClimate(null, null, "24–27°C", "70–80%", "VPD target not published", null, null, null))
                EquipmentThresholdCard(climate = OverlayClimate(null, null, "—", "—", "—", null, null, null), expanded = true, onToggle = {})
            }
        }
        composeRule.onNodeWithTag("placement_env_source").assertIsDisplayed()
        composeRule.onNodeWithTag("placement_env_source").assertTextContains("Preview")
        composeRule.onAllNodesWithText("Live").fetchSemanticsNodes().let { nodes ->
            assertTrue(nodes.isEmpty())
        }
        composeRule.onNodeWithTag("placement_runtime_state").assertIsDisplayed()
        composeRule.onNodeWithText("Not running", substring = true).assertIsDisplayed()
    }

    @Test
    fun compactPortraitChromeAndDeleteConfirmationUseStableNames() {
        val vm = boundVm()
        vm.enterPlacementMode()
        vm.tapPalette(EquipmentType.CIRCULATION_FAN)
        composeRule.waitForIdle()
        vm.duplicateSelected()
        val fan1 = vm.state.value.instances.first { it.displayNumber == 1 }
        vm.select(fan1.id)
        composeRule.setContent {
            GreenHandsTheme {
                val state by vm.state.collectAsState()
                androidx.compose.foundation.layout.Column {
                    CompactCropEnvironmentRow(
                        climate = OverlayClimate(null, null, "24–27°C", "70–80%", "VPD target not published", null, null, null),
                        kind = EnvironmentDataSourceKind.PREVIEW
                    )
                    EquipmentThresholdCard(
                        climate = OverlayClimate(null, null, "—", "—", "—", null, null, null),
                        expanded = false,
                        onToggle = { vm.toggleEquipmentCard() },
                        instanceNames = state.instances.map { it.displayLabel() }
                    )
                    CoordinateEditor(
                        state = state,
                        onApply = { _, _, _, _ -> },
                        onRotate = {},
                        onDuplicate = {},
                        onDelete = { vm.requestDelete() },
                        onConfirmGhost = {},
                        onCancelGhost = {}
                    )
                    if (state.showDeleteConfirm) {
                        DeleteEquipmentDialog(
                            name = state.pendingDeleteLabel,
                            confirmLabel = state.pendingDeleteConfirm,
                            onConfirm = { vm.deleteSelected() },
                            onDismiss = { vm.cancelDelete() }
                        )
                    }
                }
            }
        }
        composeRule.onNodeWithTag("placement_crop_card").assertIsDisplayed()
        composeRule.onNodeWithTag("placement_crop_optimal").assertIsDisplayed()
        composeRule.onNodeWithTag("placement_env_source").assertTextContains("Preview")
        composeRule.onNodeWithTag("placement_equipment_card").assertIsDisplayed()
        composeRule.onNodeWithTag("coord_instance_name").assertIsDisplayed()
        composeRule.onNodeWithText("Circulation Fan 1", substring = true).assertExists()
        composeRule.onNodeWithTag("placement_delete").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("placement_delete_confirm").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("placement_delete_title").assertIsDisplayed()
        composeRule.onNodeWithText("Delete Circulation Fan 1", substring = true).assertExists()
        composeRule.onNodeWithText("Only Circulation Fan 1 will be removed", substring = true).assertExists()
        composeRule.onNodeWithText("Delete Fan 1", substring = true).assertExists()
        composeRule.onNodeWithTag("placement_delete_confirm").performClick()
        composeRule.runOnIdle {
            assertEquals(1, vm.state.value.instances.size)
            assertEquals(2, vm.state.value.instances.single().displayNumber)
        }
    }

    @Test
    fun viewportCoordinateLabelShowsNameAndXYZ() {
        composeRule.setContent {
            GreenHandsTheme {
                ViewportCoordinateLabel(name = "Circulation Fan 2", xM = 4.2, yM = 1.1, zM = 2.15)
            }
        }
        composeRule.onNodeWithTag("viewport_coord_label").assertIsDisplayed()
        composeRule.onNodeWithText("Circulation Fan 2").assertIsDisplayed()
        composeRule.onNodeWithText("X 4.20 m · Y 1.10 m · Z 2.15 m").assertIsDisplayed()
    }

    @Test
    fun compactNameLabelsOmitXYZUntilSelected() {
        composeRule.setContent {
            GreenHandsTheme {
                androidx.compose.foundation.layout.Column {
                    ViewportCoordinateLabel(
                        name = "Circulation Fan 1",
                        xM = 2.0,
                        yM = 0.8,
                        zM = 1.6,
                        compact = true,
                        selected = false
                    )
                    ViewportCoordinateLabel(
                        name = "Circulation Fan 2",
                        xM = 4.2,
                        yM = 1.1,
                        zM = 2.15,
                        compact = false,
                        selected = true
                    )
                }
            }
        }
        composeRule.onNodeWithTag("viewport_equip_label").assertIsDisplayed()
        composeRule.onNodeWithText("Circulation Fan 1").assertIsDisplayed()
        composeRule.onNodeWithTag("viewport_coord_label").assertIsDisplayed()
        composeRule.onNodeWithText("X 4.20 m · Y 1.10 m · Z 2.15 m").assertIsDisplayed()
        composeRule.onAllNodesWithText("X 2.00 m", substring = true).fetchSemanticsNodes().let { nodes ->
            assertTrue(nodes.isEmpty())
        }
    }

    @Test
    fun compactPortraitShowsViewportPaletteAndSuggestionButtonsTogether() {
        composeRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        composeRule.setContent {
            GreenHandsTheme {
                PlacementPortraitWorkspace(
                    modifier = Modifier.fillMaxSize(),
                    inPlacement = true,
                    controlStrip = {
                        PlacementControlStrip(
                            mode = PreviewInteractionMode.PLACEMENT,
                            orbitAllowed = false,
                            labelsVisible = true,
                            moveSelected = true,
                            onEnterPlacement = {},
                            onExitPlacement = {},
                            onMove = {},
                            onOrbit = {},
                            onLabels = {},
                            onEquipmentStatus = {},
                            onReset = {},
                            onSave = {},
                            onClear = {}
                        )
                    },
                    palette = { EquipmentPalette(onTap = {}) },
                    suggestionRail = {
                        SuggestionRail(
                            onOpen = {},
                            modifier = Modifier.align(Alignment.CenterEnd)
                        )
                    },
                    selectedOverlay = {},
                    viewport = {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(Color.DarkGray)
                                .testTag("preview_viewport")
                        )
                    }
                )
            }
        }
        composeRule.onNodeWithTag("preview_viewport").assertIsDisplayed()
        composeRule.onNodeWithTag("equipment_palette").assertIsDisplayed()
        composeRule.onNodeWithTag("palette_circulation").assertIsDisplayed()
        composeRule.onNodeWithTag("palette_exhaust").assertIsDisplayed()
        composeRule.onNodeWithTag("palette_fogger").assertIsDisplayed()
        composeRule.onNodeWithTag("suggestion_rail").assertIsDisplayed()
        composeRule.onNodeWithTag("suggestion_circulation").assertIsDisplayed()
        composeRule.onNodeWithTag("suggestion_exhaust").assertIsDisplayed()
        composeRule.onNodeWithTag("suggestion_fogger").assertIsDisplayed()
        composeRule.onNodeWithTag("placement_toolbar").assertIsDisplayed()
        composeRule.onNodeWithTag("placement_move_mode").assertIsDisplayed()
        composeRule.onNodeWithTag("preview_orbit_toggle").assertIsDisplayed()
        composeRule.onNodeWithTag("preview_labels_toggle").assertIsDisplayed()
        composeRule.onNodeWithTag("placement_view_mode").assertIsDisplayed()
    }
}
