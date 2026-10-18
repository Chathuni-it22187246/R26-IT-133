package com.greenhands.app.heat.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface HeatSessionRepository {
    fun records(userId: String): Flow<UserHeatSession>
    suspend fun load(userId: String): UserHeatSession
    suspend fun save(session: UserHeatSession)
}

class InMemoryHeatSessionRepository : HeatSessionRepository {
    private val mutex = Mutex()
    private val store = mutableMapOf<String, MutableStateFlow<UserHeatSession>>()

    override fun records(userId: String): Flow<UserHeatSession> {
        val id = userId.normalizedUserId().orEmpty()
        return flowFor(id)
    }

    override suspend fun load(userId: String): UserHeatSession {
        val id = userId.normalizedUserId() ?: return UserHeatSession("")
        return mutex.withLock { flowFor(id).value }
    }

    override suspend fun save(session: UserHeatSession) {
        val id = session.userId.normalizedUserId() ?: return
        mutex.withLock {
            flowFor(id).value = session.copy(userId = id)
        }
    }

    fun snapshot(userId: String): UserHeatSession {
        val id = userId.normalizedUserId() ?: return UserHeatSession("")
        return store[id]?.value ?: UserHeatSession(id)
    }

    private fun flowFor(userId: String): MutableStateFlow<UserHeatSession> =
        store.getOrPut(userId) { MutableStateFlow(UserHeatSession(userId)) }
}

/**
 * User-scoped Active/Recent records. Uses the existing heat DataStore file with a
 * distinct key prefix so [DataStoreHeatConfigRepository] workspace writes cannot wipe them.
 */
class DataStoreHeatSessionRepository(
    private val dataStore: DataStore<Preferences>
) : HeatSessionRepository {

    constructor(context: Context) : this(context.applicationContext.heatConfigDataStore)

    override fun records(userId: String): Flow<UserHeatSession> {
        val id = userId.normalizedUserId().orEmpty()
        return dataStore.data.map { prefs -> prefs.readSession(id) }
    }

    override suspend fun load(userId: String): UserHeatSession {
        val id = userId.normalizedUserId() ?: return UserHeatSession("")
        return dataStore.data.map { prefs -> prefs.readSession(id) }.first()
    }

    override suspend fun save(session: UserHeatSession) {
        val id = session.userId.normalizedUserId() ?: return
        dataStore.edit { prefs ->
            val active = session.active
            if (active == null) {
                prefs.remove(Keys.active(id))
            } else {
                prefs[Keys.active(id)] = HeatSessionCodec.encode(active)
            }
            val recent = session.recent
            if (recent.isEmpty()) {
                prefs.remove(Keys.recent(id))
            } else {
                prefs[Keys.recent(id)] = HeatSessionCodec.encodeList(recent)
            }
        }
    }

    private fun Preferences.readSession(userId: String): UserHeatSession {
        if (userId.isBlank()) return UserHeatSession("")
        return UserHeatSession(
            userId = userId,
            active = this[Keys.active(userId)]?.let { HeatSessionCodec.decode(it) },
            recent = HeatSessionCodec.decodeList(this[Keys.recent(userId)])
        )
    }

    private object Keys {
        const val PREFIX = "heat_session_"
        fun active(userId: String) = stringPreferencesKey("${PREFIX}active_$userId")
        fun recent(userId: String) = stringPreferencesKey("${PREFIX}recent_$userId")
    }
}
