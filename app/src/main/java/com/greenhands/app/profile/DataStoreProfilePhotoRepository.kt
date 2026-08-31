package com.greenhands.app.profile

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val STORE_NAME = "profile_preferences"

val Context.profilePhotoDataStore: DataStore<Preferences> by preferencesDataStore(name = STORE_NAME)

class DataStoreProfilePhotoRepository(
    private val dataStore: DataStore<Preferences>
) : ProfilePhotoRepository {

    constructor(context: Context) : this(context.applicationContext.profilePhotoDataStore)

    override val photoPath: Flow<String?> = dataStore.data.map { prefs ->
        prefs[Keys.PHOTO_PATH]?.ifBlank { null }
    }

    override suspend fun setLocalPath(path: String?) {
        dataStore.edit { prefs ->
            if (path.isNullOrBlank()) {
                prefs.remove(Keys.PHOTO_PATH)
            } else {
                prefs[Keys.PHOTO_PATH] = path
            }
        }
    }

    override suspend fun clear() {
        setLocalPath(null)
    }

    private object Keys {
        val PHOTO_PATH = stringPreferencesKey("profile_photo_path")
    }
}
