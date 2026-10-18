package com.greenhands.app

import com.greenhands.app.session.AppSessionViewModel
import com.greenhands.app.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppSessionViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setMain() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun reset() {
        Dispatchers.resetMain()
    }

    @Test
    fun loginThenLogoutClearsSession() {
        val vm = AppSessionViewModel()
        vm.login("grower@greenhands.app", rememberMe = false)
        assertTrue(vm.state.value.isLoggedIn)
        assertEquals("grower@greenhands.app", vm.state.value.userEmail)

        vm.logout()
        assertFalse(vm.state.value.isLoggedIn)
        assertFalse(
            "Dashboard session must not remain after logout",
            vm.state.value.isLoggedIn
        )
        assertFalse(vm.state.value.heatWorkspaceActive)
    }

    @Test
    fun registrationNameIsUsedOnLogin() {
        val vm = AppSessionViewModel()
        vm.registerDemoAccount("Ada Grower", "ada@greenhands.app")
        assertTrue(vm.state.value.loginBanner!!.contains("ada@greenhands.app"))
        vm.login("ada@greenhands.app", rememberMe = true)
        assertEquals("Ada Grower", vm.state.value.userName)
        assertTrue(vm.state.value.rememberMe)
    }

    @Test
    fun themeAndDemoSettingsReset() {
        val vm = AppSessionViewModel()
        vm.setThemeMode(ThemeMode.LIGHT)
        vm.setDemoMode(false)
        vm.setNotificationsEnabled(false)
        vm.resetDemoSettings()
        assertEquals(ThemeMode.DARK, vm.state.value.themeMode)
        assertTrue(vm.state.value.demoModeEnabled)
        assertTrue(vm.state.value.notificationsEnabled)
    }

    @Test
    fun editProfileUpdatesSessionState() {
        val vm = AppSessionViewModel()
        vm.login("demo@greenhands.app", false)
        vm.updateProfile("Nia Field", "nia@greenhands.app")
        assertEquals("Nia Field", vm.state.value.userName)
        assertEquals("nia@greenhands.app", vm.state.value.userEmail)
    }

    @Test
    fun heatWorkspaceEnterAndExit() {
        val vm = AppSessionViewModel()
        vm.enterHeatWorkspace()
        assertTrue(vm.state.value.heatWorkspaceActive)
        vm.exitHeatWorkspace()
        assertFalse(vm.state.value.heatWorkspaceActive)
    }
}
