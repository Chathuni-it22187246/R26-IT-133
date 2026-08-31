package com.greenhands.app.profile

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Local profile-photo path store. A later Firebase Storage implementation can
 * replace or supplement this without changing session UI.
 */
interface ProfilePhotoRepository {
    val photoPath: Flow<String?>
    suspend fun setLocalPath(path: String?)
    suspend fun clear()
}

class InMemoryProfilePhotoRepository(
    initialPath: String? = null
) : ProfilePhotoRepository {
    private val _photoPath = MutableStateFlow(initialPath)
    override val photoPath: Flow<String?> = _photoPath.asStateFlow()

    override suspend fun setLocalPath(path: String?) {
        _photoPath.value = path
    }

    override suspend fun clear() {
        _photoPath.value = null
    }

    fun current(): String? = _photoPath.value
}

fun initialsFor(name: String): String {
    val initials = name.trim()
        .split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { part -> part.first().uppercase() }
    return initials.ifBlank { "GH" }
}

fun dashboardHeadingName(savedName: String?): String? {
    val name = savedName?.trim().orEmpty()
    return name.ifBlank { null }
}
