package com.greenhands.app.harvest.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HarvestScanRecordDao {
    @Query("SELECT * FROM harvest_scan_records ORDER BY scannedAtEpochMillis DESC")
    fun observeNewestFirst(): Flow<List<HarvestScanRecordEntity>>

    @Query("SELECT * FROM harvest_scan_records WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): HarvestScanRecordEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: HarvestScanRecordEntity)

    @Query("DELETE FROM harvest_scan_records WHERE id = :id")
    suspend fun deleteById(id: String): Int

    @Query("DELETE FROM harvest_scan_records")
    suspend fun deleteAll()
}
