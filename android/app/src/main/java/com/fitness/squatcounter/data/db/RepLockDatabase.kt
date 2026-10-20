package com.fitness.squatcounter.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        SessionEntity::class,
        LedgerEntryEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class RepLockDatabase : RoomDatabase() {

    abstract fun sessionDao(): SessionDao
    abstract fun ledgerDao(): LedgerDao

    companion object {
        @Volatile
        private var INSTANCE: RepLockDatabase? = null

        fun getInstance(context: Context): RepLockDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RepLockDatabase::class.java,
                    "replock_ledger.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
