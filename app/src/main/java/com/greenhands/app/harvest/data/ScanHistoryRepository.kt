package com.greenhands.app.harvest.data

import android.content.Context
import com.greenhands.app.harvest.data.local.HarvestDatabase
import com.greenhands.app.harvest.data.local.HarvestScanRecordDao
import com.greenhands.app.harvest.data.local.HarvestScanRecordMapper
import com.greenhands.app.harvest.model.ScanRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Persists fruit/leaf scan snapshots. Implementations must not write
 * bundled CSV reference assets.
 */
interface ScanHistoryRepository {
    val records: Flow<List<ScanRecord>>

    suspend fun add(record: ScanRecord)

    suspend fun delete(id: String)

    suspend fun getById(id: String): ScanRecord?

    suspend fun clear()
}

class InMemoryScanHistoryRepository : ScanHistoryRepository {
    private val _records = MutableStateFlow<List<ScanRecord>>(emptyList())
    override val records: Flow<List<ScanRecord>> = _records.asStateFlow()

    override suspend fun add(record: ScanRecord) {
        _records.update { current ->
            (current.filterNot { it.id == record.id } + record)
                .sortedByDescending { it.scannedAtEpochMillis }
        }
    }

    override suspend fun delete(id: String) {
        _records.update { current -> current.filterNot { it.id == id } }
    }

    override suspend fun getById(id: String): ScanRecord? =
        _records.value.firstOrNull { it.id == id }

    override suspend fun clear() {
        _records.value = emptyList()
    }
}

class RoomScanHistoryRepository(
    private val dao: HarvestScanRecordDao
) : ScanHistoryRepository {

    constructor(context: Context) : this(HarvestDatabase.get(context).scanRecordDao())

    override val records: Flow<List<ScanRecord>> =
        dao.observeNewestFirst().map { rows -> rows.map(HarvestScanRecordMapper::toDomain) }

    override suspend fun add(record: ScanRecord) {
        dao.insert(HarvestScanRecordMapper.toEntity(record))
    }

    override suspend fun delete(id: String) {
        dao.deleteById(id)
    }

    override suspend fun getById(id: String): ScanRecord? =
        dao.getById(id)?.let(HarvestScanRecordMapper::toDomain)

    override suspend fun clear() {
        dao.deleteAll()
    }
}
