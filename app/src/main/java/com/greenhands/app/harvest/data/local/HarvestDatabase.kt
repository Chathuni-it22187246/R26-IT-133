package com.greenhands.app.harvest.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [HarvestScanRecordEntity::class],
    version = 1,
    exportSchema = false
)
abstract class HarvestDatabase : RoomDatabase() {
    abstract fun scanRecordDao(): HarvestScanRecordDao

    companion object {
        private const val NAME = "harvest_scan_records.db"

        @Volatile
        private var instance: HarvestDatabase? = null

        fun get(context: Context): HarvestDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    HarvestDatabase::class.java,
                    NAME
                ).build().also { instance = it }
            }
        }
    }
}
