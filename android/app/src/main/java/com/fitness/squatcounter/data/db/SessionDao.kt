package com.fitness.squatcounter.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.fitness.squatcounter.data.SessionStatus

@Dao
interface SessionDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSession(session: SessionEntity): Long

    @Query("SELECT * FROM sessions WHERE sessionId = :sessionId LIMIT 1")
    suspend fun getSession(sessionId: String): SessionEntity?

    @Query("UPDATE sessions SET status = :status, endTimeMs = :endTimeMs WHERE sessionId = :sessionId")
    suspend fun updateSessionStatus(sessionId: String, status: SessionStatus, endTimeMs: Long)

    @Query("UPDATE sessions SET validReps = validReps + 1 WHERE sessionId = :sessionId")
    suspend fun incrementValidReps(sessionId: String)

    @Query("UPDATE sessions SET rejectedReps = rejectedReps + 1 WHERE sessionId = :sessionId")
    suspend fun incrementRejectedReps(sessionId: String)

    @Query("SELECT * FROM sessions ORDER BY startTimeMs DESC")
    suspend fun getAllSessions(): List<SessionEntity>
}
