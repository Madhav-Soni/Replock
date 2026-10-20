package com.fitness.squatcounter.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LedgerDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEntry(entry: LedgerEntryEntity): Long

    @Query("SELECT * FROM ledger_entries ORDER BY id ASC")
    suspend fun getAllEntries(): List<LedgerEntryEntity>

    @Query("SELECT * FROM ledger_entries ORDER BY id ASC")
    fun getAllEntriesFlow(): Flow<List<LedgerEntryEntity>>

    @Query("SELECT * FROM ledger_entries ORDER BY id DESC LIMIT 1")
    suspend fun getLatestEntry(): LedgerEntryEntity?

    @Query("""
        SELECT COALESCE(SUM(amountSeconds), 0) FROM ledger_entries 
        WHERE entryType = 'EARN' AND wallClockTimeMs >= :startOfDayMs AND wallClockTimeMs <= :endOfDayMs
    """)
    suspend fun getEarnedSecondsBetween(startOfDayMs: Long, endOfDayMs: Long): Int

    @Query("""
        SELECT COALESCE(SUM(amountSeconds), 0) FROM ledger_entries 
        WHERE entryType = 'EARN' AND exerciseType = :exerciseType 
        AND wallClockTimeMs >= :startOfDayMs AND wallClockTimeMs <= :endOfDayMs
    """)
    suspend fun getEarnedSecondsForExerciseBetween(startOfDayMs: Long, endOfDayMs: Long, exerciseType: String): Int

    @Query("SELECT * FROM ledger_entries WHERE sessionId = :sessionId")
    suspend fun getEntriesForSession(sessionId: String): List<LedgerEntryEntity>
}
