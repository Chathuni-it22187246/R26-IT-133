package com.greenhands.app

import android.content.ContentValues
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.core.view.WindowCompat
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.greenhands.app.heat.data.InMemoryHeatConfigRepository
import com.greenhands.app.heat.model.ControlMode
import com.greenhands.app.heat.model.Crop
import com.greenhands.app.heat.model.SchedulePeriod
import com.greenhands.app.heat.profile.CropProfileRegistry
import com.greenhands.app.heat.ui.HeatConfigViewModel
import com.greenhands.app.session.AppSessionViewModel
import com.greenhands.app.ui.navigation.GreenHandsNavGraph
import com.greenhands.app.ui.navigation.Routes
import com.greenhands.app.ui.screens.LoginScreen
import com.greenhands.app.ui.screens.SplashScreen
import com.greenhands.app.ui.screens.WelcomeScreen
import com.greenhands.app.ui.theme.GreenHandsTheme
import com.greenhands.app.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class UiReviewScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val shot = mutableStateOf("splash")
    private val dest = mutableStateOf(Routes.DASHBOARD)

    @Test
    fun capturePublicationScreenshots() {
        composeRule.activity.runOnUiThread {
            WindowCompat.setDecorFitsSystemWindows(composeRule.activity.window, false)
        }

        val session = AppSessionViewModel()
        session.login("demo@greenhands.app", false)
        session.setThemeMode(ThemeMode.DARK)
        val heatVm = HeatConfigViewModel(InMemoryHeatConfigRepository())
        heatVm.selectCrop(Crop.TOMATO)
        heatVm.onStageClicked(CropProfileRegistry.stageProfile(Crop.TOMATO, "vegetative").stage)
        heatVm.selectPeriod(SchedulePeriod.DAY)

        composeRule.setContent {
            ScreenshotHost(shot.value, dest.value, session, heatVm)
        }
        capture("01_splash_dark.png")

        showStandalone("welcome")
        capture("02_welcome_dark.png")

        showStandalone("login")
        capture("03_login_dark.png")

        showGraph(Routes.DASHBOARD)
        capture("04_dashboard_dark.png")

        showGraph(Routes.HEAT_SELECT_CROP)
        waitFor("select_crop")
        capture("05_crops_dark.png")

        showGraph(Routes.HEAT_SELECT_STAGE)
        waitFor("select_stage")
        capture("06_stage_dark.png")

        heatVm.selectPeriod(SchedulePeriod.DAY)
        showGraph(Routes.HEAT_CLIMATE)
        waitFor("climate_targets")
        capture("07_climate_day_dark.png")

        composeRule.onNodeWithTag("climate_period_night").performClick()
        composeRule.waitForIdle()
        capture("08_climate_night_dark.png")

        heatVm.selectPeriod(SchedulePeriod.DAY)
        showGraph(Routes.HEAT_CIRCULATION)
        waitFor("circulation_fan")
        capture("09_equipment_automatic.png")

        heatVm.requestControlMode(ControlMode.ADVANCED)
        heatVm.confirmAdvancedMode()
        composeRule.waitForIdle()
        capture("10_equipment_advanced.png")

        showGraph(Routes.HEAT_SUMMARY)
        waitFor("heat_summary")
        capture("11_summary_dark.png")

        showGraph(Routes.SOURCES)
        waitFor("sources_screen")
        capture("12_sources_dark.png")

        showGraph(Routes.ACCOUNT)
        waitFor("account_home")
        capture("13_account_dark.png")

        session.setThemeMode(ThemeMode.LIGHT)
        showGraph(Routes.DASHBOARD)
        capture("15_dashboard_light.png")

        session.setThemeMode(ThemeMode.DARK)
        showGraph(Routes.DASHBOARD)
        composeRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        composeRule.waitForIdle()
        waitFor("dashboard_grid")
        Thread.sleep(1_200)
        capture("14_dashboard_landscape.png")
        composeRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        composeRule.waitForIdle()
    }

    private fun showStandalone(id: String) {
        composeRule.runOnIdle { shot.value = id }
        composeRule.waitForIdle()
        Thread.sleep(200)
    }

    private fun showGraph(route: String) {
        composeRule.runOnIdle {
            dest.value = route
            shot.value = "graph"
        }
        composeRule.waitForIdle()
        Thread.sleep(250)
    }

    private fun waitFor(tag: String) {
        composeRule.waitUntil(8_000) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun capture(name: String) {
        composeRule.waitForIdle()
        Thread.sleep(400)
        exec("mkdir -p /sdcard/Pictures/greenhands-ui-review")
        val drawn = captureDecorView()
        val uiShot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val chosen = pickRendered(uiShot, drawn)
        persistPng(chosen, name)
        exec("screencap -p /sdcard/Pictures/greenhands-ui-review/$name")
        if (!isMostlyBlack(chosen)) {
            persistPng(chosen, name)
        }
    }

    private fun captureDecorView(): Bitmap {
        val view = composeRule.activity.window.decorView
        val width = view.width.coerceAtLeast(1)
        val height = view.height.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            view.draw(Canvas(bitmap))
        }
        return bitmap
    }

    private fun pickRendered(first: Bitmap?, second: Bitmap): Bitmap {
        if (first != null && !isMostlyBlack(first)) return first
        if (!isMostlyBlack(second)) return second
        return first ?: second
    }

    private fun isMostlyBlack(bitmap: Bitmap): Boolean {
        val step = 18
        var lit = 0
        var samples = 0
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val color = bitmap.getPixel(x, y)
                val luminance = ((color shr 16) and 0xFF) + ((color shr 8) and 0xFF) + (color and 0xFF)
                if (luminance > 48) lit++
                samples++
                x += step
            }
            y += step
        }
        return samples == 0 || lit < samples * 0.03
    }

    private fun persistPng(bitmap: Bitmap, name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "ui-review")
        dir.mkdirs()
        File(dir, name).outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
        }
        val cache = File(context.cacheDir, name)
        cache.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
        }
        exec("rm -f /sdcard/Pictures/greenhands-ui-review/$name")
        exec("sh -c 'cat ${cache.absolutePath} > /sdcard/Pictures/greenhands-ui-review/$name'")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/greenhands-ui-review")
            }
            resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)?.let { uri ->
                resolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
                }
            }
        }
    }

    private fun exec(command: String) {
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).use { pfd ->
            java.io.FileInputStream(pfd.fileDescriptor).readBytes()
        }
    }
}

@Composable
private fun ScreenshotHost(
    shot: String,
    dest: String,
    session: AppSessionViewModel,
    heatVm: HeatConfigViewModel
) {
    when (shot) {
        "splash" -> GreenHandsTheme(ThemeMode.DARK) { SplashScreen {} }
        "welcome" -> GreenHandsTheme(ThemeMode.DARK) { WelcomeScreen({}, {}) }
        "login" -> GreenHandsTheme(ThemeMode.DARK) {
            LoginScreen("", null, {}, { _, _ -> }, {}, {}, {})
        }
        else -> {
            val sessionState by session.state.collectAsState()
            GreenHandsTheme(themeMode = sessionState.themeMode) {
                key(dest) {
                    GreenHandsNavGraph(
                        navController = rememberNavController(),
                        sessionViewModel = session,
                        startDestination = dest,
                        heatViewModel = heatVm
                    )
                }
            }
        }
    }
}
