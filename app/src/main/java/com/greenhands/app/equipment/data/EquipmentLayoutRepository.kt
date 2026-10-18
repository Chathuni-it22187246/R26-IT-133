package com.greenhands.app.equipment.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.greenhands.app.equipment.model.EquipmentLayout
import com.greenhands.app.heat.data.normalizedUserId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface EquipmentLayoutRepository {
    fun layouts(userId: String): Flow<List<EquipmentLayout>>
    suspend fun load(userId: String, configurationId: String): EquipmentLayout?
    suspend fun save(layout: EquipmentLayout)
    suspend fun delete(userId: String, configurationId: String)
}

class InMemoryEquipmentLayoutRepository : EquipmentLayoutRepository {
    private val mutex = Mutex()
    private val store = mutableMapOf<String, MutableStateFlow<List<EquipmentLayout>>>()

    override fun layouts(userId: String): Flow<List<EquipmentLayout>> {
        val id = userId.normalizedUserId().orEmpty()
        return flowFor(id)
    }

    override suspend fun load(userId: String, configurationId: String): EquipmentLayout? {
        val id = userId.normalizedUserId() ?: return null
        return mutex.withLock {
            flowFor(id).value.firstOrNull { it.configurationId == configurationId }
        }
    }

    override suspend fun save(layout: EquipmentLayout) {
        val id = layout.userId.normalizedUserId() ?: return
        mutex.withLock {
            val current = flowFor(id)
            current.value = listOf(layout.copy(userId = id)) +
                current.value.filter { it.configurationId != layout.configurationId }
        }
    }

    override suspend fun delete(userId: String, configurationId: String) {
        val id = userId.normalizedUserId() ?: return
        mutex.withLock {
            val current = flowFor(id)
            current.value = current.value.filter { it.configurationId != configurationId }
        }
    }

    fun snapshot(userId: String): List<EquipmentLayout> {
        val id = userId.normalizedUserId() ?: return emptyList()
        return store[id]?.value.orEmpty()
    }

    private fun flowFor(userId: String): MutableStateFlow<List<EquipmentLayout>> =
        store.getOrPut(userId) { MutableStateFlow(emptyList()) }
}

private const val STORE_NAME = "equipment_layouts"

val Context.equipmentLayoutDataStore: DataStore<Preferences> by preferencesDataStore(name = STORE_NAME)

/**
 * Isolated DataStore file and key prefix. Does not reuse heat_v2_ or heat_session_ keys.
 */
class DataStoreEquipmentLayoutRepository(
    private val dataStore: DataStore<Preferences>
) : EquipmentLayoutRepository {

    constructor(context: Context) : this(context.applicationContext.equipmentLayoutDataStore)

    override fun layouts(userId: String): Flow<List<EquipmentLayout>> {
        val id = userId.normalizedUserId().orEmpty()
        return dataStore.data.map { prefs -> prefs.readList(id) }
    }

    override suspend fun load(userId: String, configurationId: String): EquipmentLayout? {
        val id = userId.normalizedUserId() ?: return null
        return dataStore.data.map { prefs ->
            prefs.readList(id).firstOrNull { it.configurationId == configurationId }
        }.first()
    }

    override suspend fun save(layout: EquipmentLayout) {
        val id = layout.userId.normalizedUserId() ?: return
        dataStore.edit { prefs ->
            val current = prefs.readList(id).filter { it.configurationId != layout.configurationId }
            val next = listOf(layout.copy(userId = id)) + current
            prefs[Keys.list(id)] = EquipmentLayoutCodec.encodeList(next)
        }
    }

    override suspend fun delete(userId: String, configurationId: String) {
        val id = userId.normalizedUserId() ?: return
        dataStore.edit { prefs ->
            val next = prefs.readList(id).filter { it.configurationId != configurationId }
            if (next.isEmpty()) prefs.remove(Keys.list(id))
            else prefs[Keys.list(id)] = EquipmentLayoutCodec.encodeList(next)
        }
    }

    private fun Preferences.readList(userId: String): List<EquipmentLayout> {
        if (userId.isBlank()) return emptyList()
        return EquipmentLayoutCodec.decodeList(this[Keys.list(userId)])
    }

    private object Keys {
        const val PREFIX = "eq_layout_v1_"
        fun list(userId: String) = stringPreferencesKey("${PREFIX}user_$userId")
    }
}
