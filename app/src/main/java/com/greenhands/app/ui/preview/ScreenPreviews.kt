package com.greenhands.app.ui.preview

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.greenhands.app.heat.domain.HeatStageChange
import com.greenhands.app.heat.model.ControlMode
import com.greenhands.app.heat.model.Crop
import com.greenhands.app.heat.model.HeatConfiguration
import com.greenhands.app.heat.model.SchedulePeriod
import com.greenhands.app.heat.profile.CropProfileRegistry
import com.greenhands.app.heat.ui.CirculationFanScreen
import com.greenhands.app.heat.ui.ClimateTargetsScreen
import com.greenhands.app.heat.ui.ConfigurationSummaryScreen
import com.greenhands.app.heat.ui.HeatUiState
import com.greenhands.app.heat.ui.SelectCropScreen
import com.greenhands.app.heat.ui.SelectStageScreen
import com.greenhands.app.heat.ui.SourcesScreen
import com.greenhands.app.session.SessionState
import com.greenhands.app.ui.screens.AccountHomeScreen
import com.greenhands.app.ui.screens.DashboardScreen
import com.greenhands.app.ui.screens.LoginScreen
import com.greenhands.app.ui.screens.WelcomeScreen
import com.greenhands.app.ui.theme.GreenHandsTheme
import com.greenhands.app.ui.theme.ThemeMode

@Preview(name = "Welcome Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Preview(name = "Welcome Light", uiMode = Configuration.UI_MODE_NIGHT_NO, showBackground = true)
@Composable
fun WelcomePreview() {
    GreenHandsTheme(ThemeMode.SYSTEM) { WelcomeScreen({}, {}) }
}

@Preview(name = "Login Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Preview(name = "Login Light", uiMode = Configuration.UI_MODE_NIGHT_NO, showBackground = true)
@Composable
fun LoginPreview() {
    GreenHandsTheme(ThemeMode.SYSTEM) {
        LoginScreen("", null, {}, { _, _ -> }, {}, {}, {})
    }
}

@Preview(name = "Dashboard Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Preview(name = "Dashboard Light", uiMode = Configuration.UI_MODE_NIGHT_NO, showBackground = true)
@Preview(name = "Dashboard Landscape", widthDp = 840, heightDp = 412, uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun DashboardPreview() {
    GreenHandsTheme(ThemeMode.SYSTEM) {
        DashboardScreen(
            session = SessionState(userName = "Ada Grower"),
            onOpenModule = {},
            onOpenProfile = {},
            onOpenNotifications = {}
        )
    }
}

@Preview(name = "Crops Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun CropsPreview() {
    GreenHandsTheme(ThemeMode.SYSTEM) { SelectCropScreen({}) }
}

@Preview(name = "Stage Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun StagePreview() {
    GreenHandsTheme(ThemeMode.SYSTEM) {
        SelectStageScreen(Crop.TOMATO, CropProfileRegistry.stageProfile(Crop.TOMATO, "vegetative").stage, null, {}, {}, {}, {})
    }
}

@Preview(name = "Climate Day Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun ClimateDayPreview() {
    GreenHandsTheme(ThemeMode.SYSTEM) {
        ClimateTargetsScreen(
            ui = previewUi(SchedulePeriod.DAY),
            onPeriod = {},
            onDayTempInput = {},
            onNightTempInput = {},
            onDayRhInput = {},
            onNightRhInput = {},
            onEdit = {},
            onReset = {},
            onSaveContinue = {},
            onBack = {},
            onConfirmDiscard = {},
            onCancelDiscard = {}
        )
    }
}

@Preview(name = "Climate Night Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun ClimateNightPreview() {
    GreenHandsTheme(ThemeMode.SYSTEM) {
        ClimateTargetsScreen(
            ui = previewUi(SchedulePeriod.NIGHT),
            onPeriod = {},
            onDayTempInput = {},
            onNightTempInput = {},
            onDayRhInput = {},
            onNightRhInput = {},
            onEdit = {},
            onReset = {},
            onSaveContinue = {},
            onBack = {},
            onConfirmDiscard = {},
            onCancelDiscard = {}
        )
    }
}

@Preview(name = "Equipment Automatic", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun EquipmentAutomaticPreview() {
    GreenHandsTheme(ThemeMode.SYSTEM) {
        CirculationFanScreen(previewUi(SchedulePeriod.DAY), {}, {}, {}, {}, {}, {}, {}, {})
    }
}

@Preview(name = "Equipment Advanced", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun EquipmentAdvancedPreview() {
    GreenHandsTheme(ThemeMode.SYSTEM) {
        CirculationFanScreen(
            previewUi(SchedulePeriod.DAY).copy(config = previewConfig().copy(controlMode = ControlMode.ADVANCED)),
            {}, {}, {}, {}, {}, {}, {}, {}
        )
    }
}

@Preview(name = "Summary Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun SummaryPreview() {
    GreenHandsTheme(ThemeMode.SYSTEM) {
        ConfigurationSummaryScreen(previewUi(), {}, {}, {}, {}, {}, {}, {})
    }
}

@Preview(name = "Sources Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun SourcesPreview() {
    GreenHandsTheme(ThemeMode.SYSTEM) { SourcesScreen() }
}

@Preview(name = "Account Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Preview(name = "Account Light", uiMode = Configuration.UI_MODE_NIGHT_NO, showBackground = true)
@Composable
fun AccountPreview() {
    GreenHandsTheme(ThemeMode.SYSTEM) {
        AccountHomeScreen(SessionState(userName = "Demo Researcher"), {}, {}, {}, {}, {}, {}, {})
    }
}

private fun previewConfig(): HeatConfiguration {
    val crop = Crop.TOMATO
    val stage = CropProfileRegistry.stageProfile(crop, "vegetative").stage
    return HeatStageChange.apply(HeatConfiguration(), crop, stage)
}

private fun previewUi(period: SchedulePeriod = SchedulePeriod.DAY): HeatUiState {
    val config = previewConfig()
    return HeatUiState(
        config = config,
        loaded = true,
        schedulePeriod = period,
        dayTempInput = "25.5",
        nightTempInput = "19.0",
        dayRhInput = "75.0",
        nightRhInput = "75.0",
        cspInput = "25.5",
        cdpInput = "23.5",
        conInput = "27.5"
    )
}
