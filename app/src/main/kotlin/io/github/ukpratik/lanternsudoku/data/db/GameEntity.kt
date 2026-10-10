// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per game (LLD §2.1, ADR-006). Summary columns are written in the same UPDATE as the blob, so Home and Stats
 * never decode a blob. The blob is non-null only while IN_PROGRESS.
 */
@Entity(
    tableName = "games",
    indices = [
        Index(value = ["kind", "level", "status"]),
        Index(value = ["daily_epoch_day"], unique = true), // NULL for NEW games; SQLite allows many NULLs
        Index(value = ["puzzle_id"]),
    ],
)
data class GameEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "puzzle_id") val puzzleId: String,
    val kind: Int,
    val level: Int,
    @ColumnInfo(name = "daily_epoch_day") val dailyEpochDay: Long? = null,
    val status: Int,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    // Local date the game was started on (streak rule: only on-the-day dailies count, D-028/D-029; D-030).
    @ColumnInfo(name = "started_epoch_day") val startedEpochDay: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "completed_at") val completedAt: Long? = null,
    @ColumnInfo(name = "completed_epoch_day") val completedEpochDay: Long? = null,
    @ColumnInfo(name = "elapsed_ms") val elapsedMs: Long = 0,
    val hints1: Int = 0,
    val hints2: Int = 0,
    val hints3: Int = 0,
    val mistakes: Int = 0,
    val clean: Boolean = false,
    @ColumnInfo(name = "filled_count") val filledCount: Int,
    @ColumnInfo(name = "grid_summary") val gridSummary: String,
    @ColumnInfo(name = "box_flags") val boxFlags: Int = 0,
    @ColumnInfo(name = "state_version") val stateVersion: Int?,
    @ColumnInfo(name = "state_blob") val stateBlob: String?,
) {
    companion object {
        const val KIND_NEW = 0
        const val KIND_DAILY = 1

        const val IN_PROGRESS = 0
        const val COMPLETED = 1
        const val ABANDONED = 2
        const val DISCARDED = 3
    }
}

/** Home "Continue" card and the Continue-or-new sheet (LLD §2.1). */
data class ContinueRow(
    val id: Long,
    val kind: Int,
    val level: Int,
    @ColumnInfo(name = "daily_epoch_day") val dailyEpochDay: Long?,
    @ColumnInfo(name = "elapsed_ms") val elapsedMs: Long,
    @ColumnInfo(name = "filled_count") val filledCount: Int,
    @ColumnInfo(name = "grid_summary") val gridSummary: String,
)

/** The played set of a level (US-002). */
data class PlayedRow(@ColumnInfo(name = "puzzle_id") val puzzleId: String, @ColumnInfo(name = "last_started") val lastStarted: Long)

data class BlobRow(val id: Long, @ColumnInfo(name = "state_blob") val stateBlob: String?)

/** A daily's row for the strip, today's card and the share text (US-014, US-016, US-017). */
data class DailyRow(
    val id: Long,
    val level: Int,
    @ColumnInfo(name = "daily_epoch_day") val dailyEpochDay: Long,
    val status: Int,
    @ColumnInfo(name = "elapsed_ms") val elapsedMs: Long,
    val hints1: Int,
    val hints2: Int,
    val hints3: Int,
    val mistakes: Int,
    @ColumnInfo(name = "box_flags") val boxFlags: Int,
)

/** One level's stats (US-019); see [GameDao.levelStats]. */
data class LevelStatsRow(
    val level: Int,
    val started: Int,
    val completed: Int,
    val abandoned: Int,
    val clean: Int,
    @ColumnInfo(name = "best_ms") val bestMs: Long?,
    @ColumnInfo(name = "total_ms") val totalMs: Long,
)
