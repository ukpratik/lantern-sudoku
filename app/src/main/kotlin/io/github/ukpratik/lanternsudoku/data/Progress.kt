// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.data

import io.github.ukpratik.lanternsudoku.engine.model.Level
import java.time.LocalDate

/** Streak, best streak and days played (US-015, PRD v1.3 §5). The same three numbers everywhere (TC-142). */
data class DailyStats(val current: Int = 0, val best: Int = 0, val daysPlayed: Int = 0)

/** A daily date's state in the strip, by shape (design §6 "Day dot"). */
enum class DayState { NOT_PLAYED, STARTED, DONE }

/**
 * One date of the 7-day strip, or today's card (US-014, US-016).
 * [gameId] is the row once the daily was opened; [share] is set once it's completed.
 */
data class DailyDay(
    val date: LocalDate,
    val level: Level,
    val state: DayState,
    val elapsedMs: Long = 0,
    val gameId: Long? = null,
    val share: ShareFacts? = null,
)

/** What the share text needs (US-017): never the grid, only counts and the 9 box flags. */
data class ShareFacts(val date: LocalDate, val level: Level, val elapsedMs: Long, val hints: Int, val mistakes: Int, val boxFlags: Int)

/** One level's (or all levels') stats (US-019). Derived by query, never stored as counters (ADR-006). */
data class LevelStats(
    val started: Int = 0,
    val completed: Int = 0,
    val abandoned: Int = 0,
    val clean: Int = 0,
    /** Best time over clean solves only; null without a clean solve. */
    val bestMs: Long? = null,
    /** Sum of play time over all completed games (for the average). */
    val totalMs: Long = 0,
) {
    /** Completed / started as a whole percentage; null when nothing was started. */
    val ratePercent: Int? get() = if (started == 0) null else Math.round(completed * 100.0 / started).toInt()

    /** Average play time over all completed games (clean or not); null when none. */
    val averageMs: Long? get() = if (completed == 0) null else totalMs / completed

    operator fun plus(o: LevelStats) = LevelStats(
        started = started + o.started,
        completed = completed + o.completed,
        abandoned = abandoned + o.abandoned,
        clean = clean + o.clean,
        bestMs = listOfNotNull(bestMs, o.bestMs).minOrNull(),
        totalMs = totalMs + o.totalMs,
    )
}

/** The Stats tab (US-019): per level, overall, and the daily block (US-015). */
data class StatsView(val perLevel: Map<Level, LevelStats> = emptyMap(), val daily: DailyStats = DailyStats()) {
    val overall: LevelStats get() = perLevel.values.fold(LevelStats()) { a, b -> a + b }

    fun of(level: Level?): LevelStats = if (level == null) overall else perLevel[level] ?: LevelStats()

    /** The empty state shows until the first completion (TC-163: abandoning alone doesn't count). */
    val isEmpty: Boolean get() = overall.completed == 0
}

/** The completion summary (US-018), read back from the completed row. */
data class CompletionSummary(
    val gameId: Long,
    val level: Level,
    val elapsedMs: Long,
    val hints1: Int,
    val hints2: Int,
    val hints3: Int,
    val mistakes: Int,
    val clean: Boolean,
    /** The level's best clean time before this game (null if none). */
    val previousBestMs: Long?,
    /** A clean solve faster than every earlier clean solve at this level (the first clean solve counts). */
    val newBest: Boolean,
    /** Set for dailies: the daily's own date, the streak line and the share text. */
    val dailyDate: LocalDate? = null,
    val daily: DailyStats? = null,
    val share: ShareFacts? = null,
) {
    val hints: Int get() = hints1 + hints2 + hints3
}
