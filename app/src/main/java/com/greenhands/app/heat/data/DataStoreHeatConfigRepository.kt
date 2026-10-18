package com.greenhands.app.heat.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.greenhands.app.heat.model.Crop
import com.greenhands.app.heat.model.HeatConfiguration
import com.greenhands.app.heat.model.SchedulePeriod
import com.greenhands.app.heat.profile.CropProfileRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val STORE_NAME = "heat_configuration"

val Context.heatConfigDataStore: DataStore<Preferences> by preferencesDataStore(name = STORE_NAME)

class DataStoreHeatConfigRepository(
    private val dataStore: DataStore<Preferences>
) : HeatConfigRepository {

    constructor(context: Context) : this(context.applicationContext.heatConfigDataStore)

    override val workspace: Flow<HeatWorkspace> = dataStore.data.map { prefs -> prefs.toWorkspace() }

    override suspend fun save(config: HeatConfiguration) {
        dataStore.edit { prefs ->
            val current = prefs.toWorkspace()
            writeWorkspace(prefs, current.withSaved(config))
        }
    }

    override suspend fun saveWorkspace(workspace: HeatWorkspace) {
        dataStore.edit { prefs -> writeWorkspace(prefs, workspace) }
    }

    override suspend fun clear() {
        dataStore.edit { prefs ->
            prefs.asMap().keys.filter { it.name.startsWith(Keys.PREFIX) }.forEach { prefs.remove(it) }
            prefs[Keys.SCHEMA] = HeatConfigCodec.SCHEMA_V2
            prefs[Keys.MIGRATED] = true
        }
    }

    private fun writeWorkspace(prefs: MutablePreferences, workspace: HeatWorkspace) {
        prefs.asMap().keys.filter { it.name.startsWith(Keys.PREFIX) }.forEach { prefs.remove(it) }
        prefs[Keys.SCHEMA] = HeatConfigCodec.SCHEMA_V2
        prefs[Keys.MIGRATED] = workspace.migratedFromV1 || prefs[Keys.MIGRATED] == true
        if (workspace.selectedCrop != null) {
            prefs[Keys.SELECTED_CROP] = workspace.selectedCrop.id
        } else {
            prefs.remove(Keys.SELECTED_CROP)
        }
        Crop.entries.forEach { crop ->
            val stageKey = Keys.selectedStage(crop.id)
            val stageId = workspace.selectedStageByCrop[crop.id]
            if (stageId != null) prefs[stageKey] = stageId else prefs.remove(stageKey)
        }
        workspace.configurations.forEach { (key, config) ->
            prefs[Keys.configBlob(key)] = HeatConfigCodec.encode(config)
        }
        prefs[Keys.LAST_PERIOD] = workspace.lastSchedulePeriod.name
    }

    private fun Preferences.toWorkspace(): HeatWorkspace {
        val schema = this[Keys.SCHEMA]
        if (schema == HeatConfigCodec.SCHEMA_V2 || this[Keys.MIGRATED] == true) {
            return readV2()
        }
        val hasV1 = this[V1Keys.CROP] != null || this[V1Keys.STAGE] != null || this[V1Keys.TARGET_TEMP] != null
        return if (hasV1) {
            HeatConfigCodec.migrateV1(
                cropId = this[V1Keys.CROP],
                stageId = this[V1Keys.STAGE],
                targetTemp = this[V1Keys.TARGET_TEMP],
                targetRh = this[V1Keys.TARGET_RH],
                mode = this[V1Keys.CONTROL_MODE],
                csp = this[V1Keys.CSP],
                cdp = this[V1Keys.CDP],
                con = this[V1Keys.CON],
                esp = this[V1Keys.ESP],
                edp = this[V1Keys.EDP],
                eon = this[V1Keys.EON],
                fsp = this[V1Keys.FSP],
                fon = this[V1Keys.FON],
                fdp = this[V1Keys.FDP],
                saved = this[V1Keys.SAVED] ?: false
            )
        } else {
            readV2()
        }
    }

    private fun Preferences.readV2(): HeatWorkspace {
        val selected = Crop.fromId(this[Keys.SELECTED_CROP])
        val stages = mutableMapOf<String, String>()
        Crop.entries.forEach { crop ->
            this[Keys.selectedStage(crop.id)]?.let { stages[crop.id] = it }
        }
        val configs = mutableMapOf<String, com.greenhands.app.heat.model.HeatConfiguration>()
        CropProfileRegistry.profiles.forEach { profile ->
            profile.stages.forEach { stage ->
                val key = HeatConfigCodec.key(profile.crop.id, stage.stage.id)
                this[Keys.configBlob(key)]?.let { blob ->
                    HeatConfigCodec.decode(blob)?.let { configs[key] = it }
                }
            }
        }
        val period = runCatching {
            SchedulePeriod.valueOf(this[Keys.LAST_PERIOD] ?: SchedulePeriod.DAY.name)
        }.getOrDefault(SchedulePeriod.DAY)
        return HeatWorkspace(
            selectedCrop = selected,
            selectedStageByCrop = stages,
            configurations = configs,
            schemaVersion = HeatConfigCodec.SCHEMA_V2,
            migratedFromV1 = this[Keys.MIGRATED] ?: false,
            lastSchedulePeriod = period
        )
    }

    private object Keys {
        const val PREFIX = "heat_v2_"
        val SCHEMA = intPreferencesKey("${PREFIX}schema")
        val MIGRATED = booleanPreferencesKey("${PREFIX}migrated_from_v1")
        val SELECTED_CROP = stringPreferencesKey("${PREFIX}selected_crop")
        val LAST_PERIOD = stringPreferencesKey("${PREFIX}last_schedule_period")
        fun selectedStage(cropId: String) = stringPreferencesKey("${PREFIX}stage_$cropId")
        fun configBlob(key: String) = stringPreferencesKey("${PREFIX}config_$key")
    }

    private object V1Keys {
        val CROP = stringPreferencesKey("crop_id")
        val STAGE = stringPreferencesKey("stage_id")
        val TARGET_TEMP = doublePreferencesKey("target_temperature_c")
        val TARGET_RH = doublePreferencesKey("target_humidity_percent")
        val CONTROL_MODE = stringPreferencesKey("control_mode")
        val CSP = doublePreferencesKey("circulation_csp")
        val CDP = doublePreferencesKey("circulation_cdp")
        val CON = doublePreferencesKey("circulation_con")
        val ESP = doublePreferencesKey("exhaust_esp")
        val EDP = doublePreferencesKey("exhaust_edp")
        val EON = doublePreferencesKey("exhaust_eon")
        val FSP = doublePreferencesKey("fogger_fsp")
        val FON = doublePreferencesKey("fogger_fon")
        val FDP = doublePreferencesKey("fogger_fdp")
        val SAVED = booleanPreferencesKey("configuration_saved")
    }
}
