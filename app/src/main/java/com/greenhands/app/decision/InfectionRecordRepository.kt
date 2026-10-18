package com.greenhands.app.decision

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

private val Context.infectionRecordStore by preferencesDataStore(name = "infection_tracker")

class InfectionRecordRepository private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _records = MutableStateFlow<List<TrackedInfectionRecord>>(emptyList())
    val records: StateFlow<List<TrackedInfectionRecord>> = _records

    init {
        scope.launch { _records.value = readAll() }
    }

    fun record(id: String): TrackedInfectionRecord? = _records.value.firstOrNull { it.id == id }

    suspend fun addFromScan(
        crop: String,
        decision: InfectionDecisionResponse,
        detection: DetectedInfection,
        targetKind: PlantTargetKind = PlantTargetKind.Leaf
    ): TrackedInfectionRecord {
        val risk = InfectionPriority.computeRisk(detection, decision.severityLevel)
        val now = System.currentTimeMillis()
        val record = TrackedInfectionRecord(
            id = UUID.randomUUID().toString(),
            plantType = decision.plantType.ifBlank { crop },
            infectionName = decision.infectionShortName.ifBlank { detection.label },
            infectionFullName = decision.infectionFullName.ifBlank { detection.label },
            description = listOf(
                decision.visibleSymptoms,
                decision.treatmentDescription
            ).firstOrNull { it.isNotBlank() } ?: "Tracked from Advanced Infection Checkup.",
            createdAtMillis = now,
            formedAtMillis = InfectionPriority.estimateFormedAtMillis(risk, now),
            history = listOf(risk),
            targetKind = targetKind.name
        )
        persist(_records.value + record)
        return record
    }

    suspend fun appendScanUpdate(
        recordId: String,
        decision: InfectionDecisionResponse,
        detection: DetectedInfection
    ): TrackedInfectionRecord? {
        val current = _records.value.firstOrNull { it.id == recordId } ?: return null
        val risk = InfectionPriority.computeRisk(detection, decision.severityLevel)
        val updated = current.copy(
            infectionFullName = decision.infectionFullName.ifBlank { current.infectionFullName },
            description = decision.visibleSymptoms.ifBlank { current.description },
            history = current.history + risk
        )
        persist(_records.value.map { if (it.id == recordId) updated else it })
        return updated
    }

    private suspend fun persist(next: List<TrackedInfectionRecord>) {
        _records.value = next.sortedByDescending { it.createdAtMillis }
        appContext.infectionRecordStore.edit { prefs ->
            prefs[Keys.RECORDS] = gson.toJson(next)
        }
    }

    private suspend fun readAll(): List<TrackedInfectionRecord> {
        val raw = appContext.infectionRecordStore.data.first()[Keys.RECORDS].orEmpty()
        if (raw.isBlank()) return emptyList()
        return try {
            val type = object : TypeToken<List<TrackedInfectionRecord>>() {}.type
            gson.fromJson<List<TrackedInfectionRecord>>(raw, type).orEmpty()
                .sortedByDescending { it.createdAtMillis }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private object Keys {
        val RECORDS = stringPreferencesKey("tracked_infections_json")
    }

    companion object {
        @Volatile
        private var instance: InfectionRecordRepository? = null

        fun get(context: Context): InfectionRecordRepository {
            return instance ?: synchronized(this) {
                instance ?: InfectionRecordRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
