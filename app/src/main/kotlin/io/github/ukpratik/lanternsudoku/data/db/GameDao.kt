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
        "SELECT id, kind, level, daily_epoch_day, elapsed_ms, filled_count, grid_summary FROM games WHERE status = 0 " +
            "ORDER BY updated_at DESC, id DESC LIMIT 1",
    )
    fun continueCard(): Flow<ContinueRow?>

    @Query(
        "SELECT id, kind, level, daily_epoch_day, elapsed_ms, filled_count, grid_summary FROM games " +
            "WHERE status = 0 AND kind = 0 AND level = :level LIMIT 1",
    )
    suspend fun inProgress(level: Int): ContinueRow?

    @Query("SELECT DISTINCT level FROM games WHERE status = 0 AND kind = 0")
    fun inProgressLevels(): Flow<List<Int>>

    @Query("SELECT COUNT(*) FROM games")
    fun gameCount(): Flow<Int>

    /** US-025: completed puzzles of any kind (the rate prompt waits for 5). */
    @Query("SELECT COUNT(*) FROM games WHERE status = 1")
    suspend fun completedCount(): Int

    /** US-024: how many games were ever started (coach marks are for the first game after install). */
    @Query("SELECT COUNT(*) FROM games")
    suspend fun startedCount(): Int

    @Query("SELECT puzzle_id, MAX(started_at) AS last_started FROM games WHERE kind = 0 AND level = :level GROUP BY puzzle_id")
    suspend fun playedSet(level: Int): List<PlayedRow>

    @Query("SELECT id FROM games WHERE status = 0")
    suspend fun inProgressIds(): List<Long>

    /**
     * One row's save, read on its own so a value that can't be read as text (a BLOB-typed value from storage damage or
     * tampering throws "Unable to convert BLOB to string") fails for that row only (ISS-006).
     */
    @Query("SELECT state_blob FROM games WHERE id = :id")
    suspend fun stateBlob(id: Long): String?

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

    // ---- Daily (US-014) ----

    @Query("SELECT * FROM games WHERE daily_epoch_day = :day")
    suspend fun daily(day: Long): GameEntity?

    /** Only a set-aside (DISCARDED) daily row is ever deleted, so its date can be started again. */
    @Query("DELETE FROM games WHERE id = :id AND status = 3")
    suspend fun deleteDiscarded(id: Long): Int

    /** The 7-day strip and today's row (LLD §2.1 "Past 7 dailies"). */
    @Query("SELECT id, level, daily_epoch_day, status, elapsed_ms, hints1, hints2, hints3, mistakes, box_flags FROM games " +
        "WHERE kind = 1 AND daily_epoch_day BETWEEN :from AND :to")
    fun dailies(from: Long, to: Long): Flow<List<DailyRow>>

    // ---- Streak and days played (US-015, PRD v1.3 §5, D-028) ----

    /** Only on-the-day dailies count: completed, and started on their own date. Past dailies never extend the streak. */
    @Query("SELECT daily_epoch_day FROM games WHERE kind = 1 AND status = 1 AND started_epoch_day = daily_epoch_day")
    fun streakDays(): Flow<List<Long>>

    /** Distinct dates with a completion: a daily on its daily date, any other game on its completion date. */
    @Query("SELECT COUNT(DISTINCT COALESCE(daily_epoch_day, completed_epoch_day)) FROM games WHERE status = 1")
    fun daysPlayed(): Flow<Int>

    // ---- Stats (US-019): derived by query, never counters (ADR-006). Dailies count toward their level (D-019). ----

    @Query(
        "SELECT level, SUM(status <> 3) AS started, SUM(status = 1) AS completed, SUM(status = 2) AS abandoned, " +
            "SUM(status = 1 AND clean) AS clean, MIN(CASE WHEN status = 1 AND clean THEN elapsed_ms END) AS best_ms, " +
            "SUM(CASE WHEN status = 1 THEN elapsed_ms ELSE 0 END) AS total_ms FROM games GROUP BY level",
    )
    fun levelStats(): Flow<List<LevelStatsRow>>

    /** The best-time check at completion (LLD §2.1): the level's best clean time, excluding this game. */
    @Query("SELECT MIN(elapsed_ms) FROM games WHERE status = 1 AND clean = 1 AND level = :level AND id <> :id")
    suspend fun bestCleanExcluding(level: Int, id: Long): Long?
}
