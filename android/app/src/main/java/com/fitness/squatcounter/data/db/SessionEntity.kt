package com.fitness.squatcounter.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.fitness.squatcounter.data.SessionStatus

/**
 * Workout session record.
 */
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey
    val sessionId: String,
    val exerciseType: String = "squats",
    val startTimeMs: Long,
    val endTimeMs: Long? = null,
    val status: SessionStatus = SessionStatus.INTERRUPTED,
    val validReps: Int = 0,
    val rejectedReps: Int = 0
)
