// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Every write that touches an in-progress game is guarded by `status = 0`, so a save that arrives after the game was
 * completed, abandoned or discarded changes nothing.
 */
@Dao
interface GameDao {
    @Insert
    suspend fun insert(game: GameEntity): Long

    @Query("SELECT * FROM games WHERE id = :id")
    suspend fun get(id: Long): GameEntity?

    @Query(
        "SELECT id, kind, level, elapsed_ms, filled_count, grid_summary FROM games WHERE status = 0 " +
            "ORDER BY updated_at DESC, id DESC LIMIT 1",
    )
    fun continueCard(): Flow<ContinueRow?>

    @Query(
        "SELECT id, kind, level, elapsed_ms, filled_count, grid_summary FROM games " +
            "WHERE status = 0 AND kind = 0 AND level = :level LIMIT 1",
    )
    suspend fun inProgress(level: Int): ContinueRow?

    @Query("SELECT DISTINCT level FROM games WHERE status = 0 AND kind = 0")
    fun inProgressLevels(): Flow<List<Int>>

    @Query("SELECT COUNT(*) FROM games")
    fun gameCount(): Flow<Int>

    @Query("SELECT puzzle_id, MAX(started_at) AS last_started FROM games WHERE kind = 0 AND level = :level GROUP BY puzzle_id")
    suspend fun playedSet(level: Int): List<PlayedRow>

    @Query("SELECT id, state_blob FROM games WHERE status = 0")
    suspend fun inProgressBlobs(): List<BlobRow>

    /** The autosave write (US-003): blob and summary in one atomic UPDATE. Returns the rows changed (0 = not in progress). */
    @Query(
        "UPDATE games SET state_blob = :blob, state_version = :version, elapsed_ms = :elapsedMs, mistakes = :mistakes, " +
            "hints1 = :hints1, hints2 = :hints2, hints3 = :hints3, filled_count = :filledCount, grid_summary = :gridSummary, " +
            "box_flags = :boxFlags, updated_at = :now WHERE id = :id AND status = 0",
    )
    suspend fun saveState(
        id: Long,
        blob: String,
        version: Int,
        elapsedMs: Long,
        mistakes: Int,
        hints1: Int,
        hints2: Int,
        hints3: Int,
        filledCount: Int,
        gridSummary: String,
        boxFlags: Int,
        now: Long,
    ): Int

    /** Play time only (LLD §4.8): written about once a second while playing, without re-encoding the blob. */
    @Query("UPDATE games SET elapsed_ms = :elapsedMs WHERE id = :id AND status = 0")
    suspend fun saveElapsed(id: Long, elapsedMs: Long): Int

    @Query("UPDATE games SET status = 2, state_blob = NULL, state_version = NULL, updated_at = :now WHERE id = :id AND status = 0")
    suspend fun abandon(id: Long, now: Long): Int

    @Query("UPDATE games SET status = 3, state_blob = NULL, state_version = NULL, updated_at = :now WHERE id = :id AND status = 0")
    suspend fun discard(id: Long, now: Long): Int

    @Query(
        "UPDATE games SET status = 1, state_blob = NULL, state_version = NULL, completed_at = :now, completed_epoch_day = :day, " +
            "elapsed_ms = :elapsedMs, mistakes = :mistakes, hints1 = :hints1, hints2 = :hints2, hints3 = :hints3, clean = :clean, " +
            "filled_count = 81, grid_summary = :gridSummary, box_flags = :boxFlags, updated_at = :now WHERE id = :id AND status = 0",
    )
    suspend fun complete(
        id: Long,
        now: Long,
        day: Long,
        elapsedMs: Long,
        mistakes: Int,
        hints1: Int,
        hints2: Int,
        hints3: Int,
        clean: Boolean,
        gridSummary: String,
        boxFlags: Int,
    ): Int
}
