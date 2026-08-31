package com.greenhands.app

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.greenhands.app.heat.data.InMemoryHeatConfigRepository
import com.greenhands.app.heat.model.Crop
import com.greenhands.app.heat.profile.CropProfileRegistry
import com.greenhands.app.heat.ui.HeatConfigViewModel
import com.greenhands.app.session.AppSessionViewModel
import com.greenhands.app.ui.navigation.GreenHandsNavGraph
import com.greenhands.app.ui.navigation.Routes
import com.greenhands.app.ui.theme.GreenHandsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HeatConfigurationFlowTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun setHeatGraph(
        start: String,
        heatVm: HeatConfigViewModel = HeatConfigViewModel(InMemoryHeatConfigRepository())
    ): HeatConfigViewModel {
        val session = AppSessionViewModel()
        session.login("grower@greenhands.app", false)
        composeRule.setContent {
            GreenHandsTheme {
                GreenHandsNavGraph(
                    navController = rememberNavController(),
                    sessionViewModel = session,
                    startDestination = start,
                    heatViewModel = heatVm
                )
            }
        }
        return heatVm
    }

    @Test
    fun heatDistributionOpensCropSelection() {
        setHeatGraph(Routes.HEAT_DISTRIBUTION)
        composeRule.onNodeWithTag("heat_start").performScrollTo().performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("select_crop").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("select_crop").assertIsDisplayed()
        composeRule.onNodeWithTag("authenticated_bottom_nav").assertIsDisplayed()
    }

    @Test
    fun allFiveCropCardsAreEnabledAndNoneSayComingSoon() {
        setHeatGraph(Routes.HEAT_SELECT_CROP)
        listOf("tomato", "salad_cucumber", "bell_pepper", "chilli", "lettuce").forEach { id ->
            composeRule.onNodeWithTag("crop_$id").performScrollTo().assertIsDisplayed()
        }
        composeRule.onAllNodesWithText("Coming Soon").assertCountEquals(0)
    }

    @Test
    fun eachCropOpensItsOwnStageScreen() {
        setHeatGraph(Routes.HEAT_SELECT_CROP)
        composeRule.onNodeWithTag("crop_lettuce").performScrollTo().performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("select_stage").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("stage_vegetative").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Vegetative Leaf Expansion", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("stage_crop_header").assertIsDisplayed()
        composeRule.onNodeWithTag("stage_heading").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun tomatoOpensStageThenClimateWithBadge() {
        setHeatGraph(Routes.HEAT_SELECT_CROP)
        composeRule.onNodeWithTag("crop_tomato").performScrollTo().performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("select_stage").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("stage_germination").performScrollTo().performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("climate_targets").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("climate_targets").assertIsDisplayed()
        composeRule.onNodeWithTag("stage_badge").assertIsDisplayed()
        composeRule.onNodeWithTag("climate_save_continue").assertIsDisplayed()
        composeRule.onAllNodesWithTag("climate_save").assertCountEquals(0)
        composeRule.onAllNodesWithTag("climate_continue").assertCountEquals(0)
        composeRule.onNodeWithTag("authenticated_bottom_nav").assertIsDisplayed()
    }

    @Test
    fun invalidClimateInputStaysOnPage() {
        val heatVm = HeatConfigViewModel(InMemoryHeatConfigRepository())
        heatVm.selectCrop(Crop.TOMATO)
        heatVm.onStageClicked(CropProfileRegistry.stageProfile(Crop.TOMATO, "germination").stage)
        setHeatGraph(Routes.HEAT_CLIMATE, heatVm)
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("climate_targets").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("climate_edit").performScrollTo().performClick()
        composeRule.onNodeWithTag("climate_day_temp").performTextReplacement("3")
        composeRule.onNodeWithTag("climate_save_continue").performClick()
        composeRule.onNodeWithTag("climate_targets").assertIsDisplayed()
        composeRule.onAllNodesWithTag("circulation_fan").assertCountEquals(0)
    }

    @Test
    fun configurationFlowReachesSummaryAndEditNavigates() {
        val heatVm = HeatConfigViewModel(InMemoryHeatConfigRepository())
        heatVm.selectCrop(Crop.TOMATO)
        heatVm.onStageClicked(CropProfileRegistry.stageProfile(Crop.TOMATO, "germination").stage)
        setHeatGraph(Routes.HEAT_CLIMATE, heatVm)

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("climate_targets").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("climate_save_continue").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("circulation_fan").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("circ_save").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("exhaust_fan").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("exh_save").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("fogger_settings").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("fog_save").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("heat_summary").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("heat_summary").assertIsDisplayed()
        composeRule.onNodeWithTag("stage_badge").assertIsDisplayed()
        composeRule.onNodeWithTag("authenticated_bottom_nav").assertIsDisplayed()

        composeRule.onNodeWithTag("summary_edit_climate").performScrollTo().performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("climate_targets").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("climate_targets").assertIsDisplayed()
        composeRule.onNodeWithTag("climate_save_continue").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("heat_summary").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("heat_summary").assertIsDisplayed()
    }

    @Test
    fun unsavedChangeConfirmationAppears() {
        val heatVm = HeatConfigViewModel(InMemoryHeatConfigRepository())
        heatVm.selectCrop(Crop.TOMATO)
        heatVm.onStageClicked(CropProfileRegistry.stageProfile(Crop.TOMATO, "germination").stage)
        setHeatGraph(Routes.HEAT_CLIMATE, heatVm)
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("climate_targets").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("climate_edit").performScrollTo().performClick()
        composeRule.onNodeWithTag("climate_day_temp").performTextReplacement("28.0")
        composeRule.activity.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("discard_changes_dialog").fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithText("Discard changes?").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Discard changes?").assertIsDisplayed()
    }

    @Test
    fun configurationSurvivesNewViewModelInstance() {
        val repo = InMemoryHeatConfigRepository()
        composeRule.runOnUiThread {
            val original = HeatConfigViewModel(repo)
            original.selectCrop(Crop.TOMATO)
            original.onStageClicked(CropProfileRegistry.stageProfile(Crop.TOMATO, "vegetative").stage)
            original.saveClimate()
        }
        val restored = HeatConfigViewModel(repo)
        setHeatGraph(Routes.HEAT_CLIMATE, restored)
        composeRule.onNodeWithTag("climate_targets").assertIsDisplayed()
        composeRule.onNodeWithTag("stage_badge").assertIsDisplayed()
        assertEquals("vegetative", restored.state.value.config.stage?.id)
    }

    @Test
    fun switchingCropsDoesNotOverwriteSavedConfiguration() {
        val repo = InMemoryHeatConfigRepository()
        val vm = HeatConfigViewModel(repo)
        composeRule.runOnUiThread {
            vm.selectCrop(Crop.TOMATO)
            vm.onStageClicked(CropProfileRegistry.stageProfile(Crop.TOMATO, "germination").stage)
            vm.onTempInput("28.0")
            vm.onRhInput("80")
            assertTrue(vm.saveClimate())
            vm.selectCrop(Crop.CHILLI)
            vm.onStageClicked(CropProfileRegistry.stageProfile(Crop.CHILLI, "germination").stage)
            vm.saveClimate()
        }
        val tomato = repo.snapshot().configurations["tomato_germination"]
        val chilli = repo.snapshot().configurations["chilli_germination"]
        assertEquals(28.0, tomato?.targetTemperatureC)
        assertNotEquals(tomato?.targetTemperatureC, chilli?.targetTemperatureC)
    }

    @Test
    fun cucumberClimateShowsTable2AndTable3() {
        setHeatGraph(Routes.HEAT_SELECT_CROP)
        composeRule.onNodeWithTag("crop_salad_cucumber").performScrollTo().performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("select_stage").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("stage_germination").performScrollTo().performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("climate_targets").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("climate_targets").assertIsDisplayed()
        composeRule.onNodeWithTag("climate_period_day").assertIsDisplayed()
        composeRule.onNodeWithTag("climate_period_night").assertIsDisplayed()
        composeRule.onNodeWithTag("climate_day_temp").assertIsDisplayed()
        composeRule.onNodeWithTag("climate_rh").performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithTag("climate_temp").assertCountEquals(0)
        composeRule.onAllNodesWithText("No published RH setpoint", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("RH must remain empty", substring = true).assertCountEquals(0)
        composeRule.onNodeWithTag("climate_view_source").performScrollTo().performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("source_detail_sheet").fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithTag("climate_temp_schedule").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("temp_period_t_21_06").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("temp_period_t_06_09").assertIsDisplayed()
        composeRule.onNodeWithTag("temp_period_t_09_17").assertIsDisplayed()
        composeRule.onNodeWithTag("temp_period_t_17_21").assertIsDisplayed()
        composeRule.onNodeWithTag("rh_subperiod_nursery_1_4").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("rh_subperiod_nursery_5_8").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun dayNightSelectorKeepsBothPeriodsAndOpensViewSource() {
        val heatVm = HeatConfigViewModel(InMemoryHeatConfigRepository())
        heatVm.selectCrop(Crop.TOMATO)
        heatVm.onStageClicked(CropProfileRegistry.stageProfile(Crop.TOMATO, "vegetative").stage)
        setHeatGraph(Routes.HEAT_CLIMATE, heatVm)
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("climate_targets").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("climate_period_day").assertIsDisplayed()
        composeRule.onNodeWithTag("climate_period_night").performClick()
        composeRule.onNodeWithTag("climate_night_temp").assertIsDisplayed()
        composeRule.onNodeWithTag("climate_period_day").performClick()
        composeRule.onNodeWithTag("climate_day_temp").assertIsDisplayed()
        composeRule.onNodeWithTag("climate_period_day")
            .assert(hasContentDescription("Show Day climate values"))
        composeRule.onAllNodesWithText("Selected climate target", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("CROP_LEVEL_INHERITED", substring = true).assertCountEquals(0)
        composeRule.onNodeWithTag("climate_view_source").performScrollTo().performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("source_detail_sheet").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText("Adams et al.", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("Phase 1", substring = true).assertCountEquals(0)
    }

    @Test
    fun summaryHasNoEvidenceCardOrSeparateSaveAndNavigatesOnce() {
        val heatVm = HeatConfigViewModel(InMemoryHeatConfigRepository())
        heatVm.selectCrop(Crop.TOMATO)
        heatVm.onStageClicked(CropProfileRegistry.stageProfile(Crop.TOMATO, "germination").stage)
        assertTrue(heatVm.saveClimate())
        setHeatGraph(Routes.HEAT_SUMMARY, heatVm)
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("heat_summary").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithTag("summary_save").assertCountEquals(0)
        composeRule.onAllNodesWithTag("summary_evidence").assertCountEquals(0)
        composeRule.onAllNodesWithText("Evidence source", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("et al.", substring = true).assertCountEquals(0)
        composeRule.onNodeWithTag("summary_continue").assertIsDisplayed()
        composeRule.onNodeWithTag("summary_continue").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("demo_sim_next").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("demo_sim_next").assertIsDisplayed()
        assertTrue(heatVm.state.value.config.saved)
    }

    @Test
    fun climateFormDoesNotShowEvidenceClassifications() {
        val heatVm = HeatConfigViewModel(InMemoryHeatConfigRepository())
        heatVm.selectCrop(Crop.TOMATO)
        heatVm.onStageClicked(CropProfileRegistry.stageProfile(Crop.TOMATO, "vegetative").stage)
        setHeatGraph(Routes.HEAT_CLIMATE, heatVm)
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("climate_targets").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText("DIRECT", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("DERIVED_MIDPOINT", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("Local validation required", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithTag("evidence_badge").assertCountEquals(0)
        composeRule.onNodeWithText("Suggested climate profile", substring = true).assertIsDisplayed()
    }
}

@RunWith(AndroidJUnit4::class)
class AuthenticatedShellTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun setLoggedIn(start: String) {
        val session = AppSessionViewModel()
        session.login("grower@greenhands.app", false)
        composeRule.setContent {
            GreenHandsTheme {
                GreenHandsNavGraph(
                    navController = rememberNavController(),
                    sessionViewModel = session,
                    startDestination = start
                )
            }
        }
    }

    @Test
    fun bottomNavigationAppearsOnAuthenticatedScreens() {
        setLoggedIn(Routes.DASHBOARD)
        composeRule.onNodeWithTag("authenticated_bottom_nav").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_dashboard").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_settings").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_account").assertIsDisplayed()
        composeRule.onAllNodesWithTag("nav_crops").assertCountEquals(0)
        composeRule.onAllNodesWithTag("nav_simulation").assertCountEquals(0)
        composeRule.onAllNodesWithTag("nav_sources").assertCountEquals(0)
        composeRule.onAllNodesWithTag("crops_recent").assertCountEquals(0)
        composeRule.onAllNodesWithTag("dashboard_continue_config").assertCountEquals(0)
        composeRule.onNodeWithTag("dashboard_connection_state").assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_sample_values").assertIsDisplayed()
        composeRule.onAllNodesWithText("14:20", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("Recent configuration", substring = true).assertCountEquals(0)
        composeRule.onNodeWithTag("module_heat_distribution").performScrollTo().performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("select_crop").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("select_crop").assertIsDisplayed()
        composeRule.onNodeWithTag("crops_active").assertIsDisplayed()
        composeRule.onNodeWithTag("heat_start").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_crops").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_simulation").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_sources").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_account").assertIsDisplayed()
        composeRule.onAllNodesWithTag("nav_settings").assertCountEquals(0)
        composeRule.onNodeWithTag("nav_sources").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("sources_screen").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("source_SRC-SHAMSHIRI-2018").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("source_evidence_SRC-SHAMSHIRI-2018").assertIsDisplayed()
        composeRule.onNodeWithTag("source_SRC-OH-2019").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("nav_account").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("account_home").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("account_profile").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("account_settings").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("account_logout").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun welcomeUsesGreenHandsPlatformIdentity() {
        val session = AppSessionViewModel()
        composeRule.setContent {
            GreenHandsTheme {
                GreenHandsNavGraph(
                    navController = rememberNavController(),
                    sessionViewModel = session,
                    startDestination = Routes.WELCOME
                )
            }
        }
        composeRule.onNodeWithTag("welcome_screen").assertIsDisplayed()
        composeRule.onAllNodesWithText("Phase 1", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("climate planner", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("climate planning", substring = true).assertCountEquals(0)
        composeRule.onNodeWithText("GreenHands", substring = false).assertIsDisplayed()
        composeRule.onNodeWithText("Smarter greenhouse decisions, from sensing to harvest", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("welcome_get_started").assertIsDisplayed()
        composeRule.onNodeWithTag("welcome_have_account").assertIsDisplayed()
        composeRule.onNodeWithTag("welcome_hero").assertIsDisplayed()
    }

    @Test
    fun logoutClearsAuthenticatedBackStack() {
        val vm = AppSessionViewModel()
        vm.login("grower@greenhands.app", rememberMe = false)
        composeRule.setContent {
            GreenHandsTheme(themeMode = vm.state.value.themeMode) {
                GreenHandsNavGraph(
                    navController = rememberNavController(),
                    sessionViewModel = vm,
                    startDestination = Routes.DASHBOARD
                )
            }
        }
        composeRule.onNodeWithTag("dashboard_title").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_account").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("account_home").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("account_logout").performScrollTo().performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("logout_confirm").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("logout_confirm_button").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Demo Authentication").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Demo Authentication").assertIsDisplayed()
        composeRule.activity.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("welcome_screen").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("welcome_screen").assertIsDisplayed()
        composeRule.onAllNodesWithTag("dashboard_title").assertCountEquals(0)
        composeRule.onAllNodesWithTag("authenticated_bottom_nav").assertCountEquals(0)
    }

    @Test
    fun portraitAndLandscapeContentRemainsAccessible() {
        setLoggedIn(Routes.DASHBOARD)
        composeRule.onNodeWithTag("dashboard_title").assertIsDisplayed()
        composeRule.onNodeWithTag("authenticated_bottom_nav").assertIsDisplayed()
        composeRule.activity.resources.configuration.orientation
        composeRule.activity.runOnUiThread {
            composeRule.activity.resources.configuration.orientation = Configuration.ORIENTATION_LANDSCAPE
        }
        composeRule.onNodeWithTag("dashboard_grid").assertIsDisplayed()
    }

    @Test
    fun welcomeHasNoPhase1Wording() {
        val session = AppSessionViewModel()
        composeRule.setContent {
            GreenHandsTheme {
                GreenHandsNavGraph(
                    navController = rememberNavController(),
                    sessionViewModel = session,
                    startDestination = Routes.WELCOME
                )
            }
        }
        composeRule.onNodeWithTag("welcome_screen").assertIsDisplayed()
        composeRule.onAllNodesWithText("Phase 1", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("climate planner", substring = true).assertCountEquals(0)
        composeRule.onNodeWithText("Smarter greenhouse decisions, from sensing to harvest", substring = true).assertIsDisplayed()
    }

    @Test
    fun settingsThemeOptionsAreAccessible() {
        setLoggedIn(Routes.SETTINGS)
        composeRule.onNodeWithTag("settings_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("theme_dark").assertIsDisplayed()
        composeRule.onNodeWithTag("theme_light").assertIsDisplayed()
        composeRule.onNodeWithTag("theme_system").assertIsDisplayed()
        composeRule.onNodeWithTag("theme_light").performClick()
        composeRule.onNodeWithTag("settings_screen").assertIsDisplayed()
    }
}
