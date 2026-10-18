package com.greenhands.app

import com.greenhands.app.profile.InMemoryProfilePhotoRepository
import com.greenhands.app.profile.initialsFor
import com.greenhands.app.session.AppSessionViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfilePhotoRepositoryTest {

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
    fun registrationSucceedsWhenProfilePhotoIsSkipped() = runTest {
        val repo = InMemoryProfilePhotoRepository()
        val vm = AppSessionViewModel(repo)
        vm.registerDemoAccount("Ada Grower", "ada@greenhands.app")
        vm.login("ada@greenhands.app", false)
        assertEquals("Ada Grower", vm.state.value.userName)
        assertNull(vm.state.value.photoPath)
        assertEquals("AG", initialsFor("Ada Grower"))
    }

    @Test
    fun profileStateSupportsAddChangeAndRemove() = runTest {
        val repo = InMemoryProfilePhotoRepository()
        val vm = AppSessionViewModel(repo)
        vm.login("grower@greenhands.app", false)
        vm.applyProfilePhoto("/data/user/0/com.greenhands.app/files/profile/avatar.jpg")
        assertEquals("/data/user/0/com.greenhands.app/files/profile/avatar.jpg", vm.state.value.photoPath)
        vm.applyProfilePhoto("/data/user/0/com.greenhands.app/files/profile/avatar_2.jpg")
        assertEquals("/data/user/0/com.greenhands.app/files/profile/avatar_2.jpg", vm.state.value.photoPath)
        vm.clearProfilePhoto()
        assertNull(vm.state.value.photoPath)
        assertEquals("GH", initialsFor(""))
        assertEquals("AG", initialsFor("Ada Grower"))
    }

    @Test
    fun removingAPhotoRestoresInitials() = runTest {
        val repo = InMemoryProfilePhotoRepository("/tmp/photo.jpg")
        val vm = AppSessionViewModel(repo)
        vm.registerDemoAccount("Nia Field", "nia@greenhands.app", "/tmp/photo.jpg")
        vm.login("nia@greenhands.app", false)
        assertEquals("/tmp/photo.jpg", vm.state.value.photoPath)
        vm.clearProfilePhoto()
        assertNull(vm.state.value.photoPath)
        assertEquals("NF", initialsFor(vm.state.value.userName))
    }

    @Test
    fun loginWithoutSavedNameDoesNotInventDemoFromEmail() {
        val vm = AppSessionViewModel()
        vm.login("demo@greenhands.app", false)
        assertEquals("", vm.state.value.userName)
        assertNull(com.greenhands.app.profile.dashboardHeadingName(vm.state.value.userName))
        assertTrue(vm.state.value.dashboardTitle("GreenHands Dashboard") == "GreenHands Dashboard")
    }
}
