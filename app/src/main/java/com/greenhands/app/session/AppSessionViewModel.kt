package com.greenhands.app.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.greenhands.app.profile.InMemoryProfilePhotoRepository
import com.greenhands.app.profile.ProfilePhotoRepository
import com.greenhands.app.profile.dashboardHeadingName
import com.greenhands.app.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SessionState(
    val isLoggedIn: Boolean = false,
    val userName: String = "",
    val userEmail: String = "demo@greenhands.app",
    val themeMode: ThemeMode = ThemeMode.DARK,
    val demoModeEnabled: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val rememberMe: Boolean = false,
    val rememberedEmail: String = "",
    val loginBanner: String? = null,
    val heatWorkspaceActive: Boolean = false,
    val photoPath: String? = null
) {
    fun dashboardTitle(fallback: String): String = dashboardHeadingName(userName) ?: fallback
}

class AppSessionViewModel(
    private val photoRepository: ProfilePhotoRepository = InMemoryProfilePhotoRepository()
) : ViewModel() {
    private val registeredNames = mutableMapOf<String, String>()
    private val _state = MutableStateFlow(SessionState())
    val state: StateFlow<SessionState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            photoRepository.photoPath.collect { path ->
                _state.update { it.copy(photoPath = path) }
            }
        }
    }

    fun login(email: String, rememberMe: Boolean) {
        val trimmed = email.trim()
        val name = registeredNames[trimmed.lowercase()].orEmpty()
        _state.update {
            it.copy(
                isLoggedIn = true,
                userEmail = trimmed,
                userName = name,
                rememberMe = rememberMe,
                rememberedEmail = if (rememberMe) trimmed else "",
                loginBanner = null,
                heatWorkspaceActive = false
            )
        }
    }

    fun registerDemoAccount(name: String, email: String, photoPath: String? = null) {
        val trimmedEmail = email.trim()
        registeredNames[trimmedEmail.lowercase()] = name.trim()
        viewModelScope.launch {
            if (photoPath.isNullOrBlank()) {
                photoRepository.clear()
            } else {
                photoRepository.setLocalPath(photoPath)
            }
        }
        _state.update {
            it.copy(
                loginBanner = "Demo account created for $trimmedEmail. Sign in with any valid email and a password of at least 6 characters.",
                photoPath = photoPath
            )
        }
    }

    fun consumeLoginBanner() {
        _state.update { it.copy(loginBanner = null) }
    }

    fun updateProfile(name: String, email: String) {
        val trimmedEmail = email.trim()
        val trimmedName = name.trim()
        registeredNames[trimmedEmail.lowercase()] = trimmedName
        _state.update {
            it.copy(
                userName = trimmedName,
                userEmail = trimmedEmail,
                rememberedEmail = if (it.rememberMe) trimmedEmail else it.rememberedEmail
            )
        }
    }

    fun applyProfilePhoto(path: String?) {
        viewModelScope.launch {
            if (path.isNullOrBlank()) {
                photoRepository.clear()
            } else {
                photoRepository.setLocalPath(path)
            }
        }
        _state.update { it.copy(photoPath = path) }
    }

    fun clearProfilePhoto() {
        applyProfilePhoto(null)
    }

    fun enterHeatWorkspace() {
        _state.update { it.copy(heatWorkspaceActive = true) }
    }

    fun exitHeatWorkspace() {
        _state.update { it.copy(heatWorkspaceActive = false) }
    }

    fun setThemeMode(mode: ThemeMode) {
        _state.update { it.copy(themeMode = mode) }
    }

    fun setDemoMode(enabled: Boolean) {
        _state.update { it.copy(demoModeEnabled = enabled) }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        _state.update { it.copy(notificationsEnabled = enabled) }
    }

    fun resetDemoSettings() {
        _state.update {
            it.copy(
                themeMode = ThemeMode.DARK,
                demoModeEnabled = true,
                notificationsEnabled = true
            )
        }
    }

    fun logout() {
        _state.update { current ->
            current.copy(
                isLoggedIn = false,
                loginBanner = null,
                userName = "",
                heatWorkspaceActive = false,
                userEmail = if (current.rememberMe) current.userEmail else "demo@greenhands.app"
            )
        }
    }
}

class AppSessionViewModelFactory(
    private val photoRepository: ProfilePhotoRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return AppSessionViewModel(photoRepository) as T
    }
}
