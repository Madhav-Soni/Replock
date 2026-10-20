package com.fitness.squatcounter.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.fitness.squatcounter.data.LedgerEntryType

/**
 * Append-only ledger entry.
 * Unit of credit is screen-time seconds.
 *
 * Enforces idempotency via unique index on (sessionId, repIndex).
 */
@Entity(
    tableName = "ledger_entries",
    indices = [
        Index(value = ["sessionId", "repIndex"], unique = true),
        Index(value = ["wallClockTimeMs"]),
        Index(value = ["entryType"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["sessionId"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class LedgerEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: String,
    val repIndex: Int?, // 1, 2, 3... for reps; null for SPEND, EXPIRE, ADJUST
    val entryType: LedgerEntryType,
    val amountSeconds: Int,
    val exerciseType: String? = null,
    val wallClockTimeMs: Long,
    val elapsedRealtimeMs: Long,
    val bootCount: Int
)
