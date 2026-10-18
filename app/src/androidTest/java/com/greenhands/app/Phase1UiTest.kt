package com.greenhands.app

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.greenhands.app.session.AppSessionViewModel
import com.greenhands.app.ui.navigation.GreenHandsNavGraph
import com.greenhands.app.ui.navigation.Routes
import com.greenhands.app.ui.screens.LoginScreen
import com.greenhands.app.ui.theme.GreenHandsTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoginScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun loginShowsValidationErrorsForEmptyFields() {
        var loggedIn = false
        composeRule.setContent {
            GreenHandsTheme {
                LoginScreen(
                    initialEmail = "",
                    banner = null,
                    onBannerShown = {},
                    onLogin = { _, _ -> loggedIn = true },
                    onForgotPassword = {},
                    onRegister = {},
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithTag("login_submit").performClick()
        composeRule.onNodeWithText("Enter your email").assertIsDisplayed()
        composeRule.onNodeWithText("Enter your password").assertIsDisplayed()
        assertTrue(!loggedIn)
    }

    @Test
    fun validDemoCredentialsInvokeLogin() {
        var loggedInEmail: String? = null
        composeRule.setContent {
            GreenHandsTheme {
                LoginScreen(
                    initialEmail = "",
                    banner = null,
                    onBannerShown = {},
                    onLogin = { email, _ -> loggedInEmail = email },
                    onForgotPassword = {},
                    onRegister = {},
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithTag("login_email").performTextInput("demo@greenhands.app")
        composeRule.onNodeWithTag("login_password").performTextInput("secret1")
        composeRule.onNodeWithTag("login_submit").performClick()
        composeRule.waitForIdle()
        assertTrue(loggedInEmail == "demo@greenhands.app")
    }
}

@RunWith(AndroidJUnit4::class)
class LogoutNavigationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun logoutClearsDashboardFromBackStack() {
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
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("account_home").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("account_logout").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("logout_confirm").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("logout_confirm_button").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Demo Authentication").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Demo Authentication").assertIsDisplayed()

        composeRule.activity.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("welcome_screen").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("welcome_screen").assertIsDisplayed()
        val dashboardVisible = composeRule
            .onAllNodesWithTag("dashboard_title")
            .fetchSemanticsNodes()
            .isNotEmpty()
        org.junit.Assert.assertTrue(!dashboardVisible)
    }
}
