// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.engine.daily

/**
 * A completed game, reduced to the local dates the PRD §5 rules need (all as epoch days in the device's zone at the
 * time of the event, so time zones and DST never enter the maths here).
 *
 * @property dailyEpochDay the daily's own date (null for New-game puzzles).
 * @property startedEpochDay local date the game was started.
 * @property completedEpochDay local date the game was completed.
 */
data class CompletedGame(val dailyEpochDay: Long?, val startedEpochDay: Long, val completedEpochDay: Long) {
    val isDaily: Boolean get() = dailyEpochDay != null

    /** An on-the-day daily: started on its own date (PRD v1.3 §5, D-028). Only these count toward the streak. */
    val countsForStreak: Boolean get() = dailyEpochDay != null && startedEpochDay == dailyEpochDay

    /** The date this game adds to "days played": a daily's own date, otherwise the completion date (PRD v1.3 §5). */
    val playedDay: Long get() = dailyEpochDay ?: completedEpochDay
}

/** Streak and days-played maths (US-015, PRD v1.3 §5). Pure functions over epoch days. */
object Streaks {
    /** The dates that count toward the streak: completed dailies started on their own date. */
    fun streakDays(games: Iterable<CompletedGame>): Set<Long> = games.filter { it.countsForStreak }.mapTo(sortedSetOf()) { it.dailyEpochDay!! }

    /** Distinct dates with at least one completed puzzle, dailies by their daily date. */
    fun daysPlayed(games: Iterable<CompletedGame>): Int = games.mapTo(HashSet()) { it.playedDay }.size

    /**
     * Consecutive dates in [completedDays] ending today, or ending yesterday when today isn't done yet; otherwise 0.
     * A streak is never "broken" in copy: it simply reads 0 (US-015).
     */
    fun current(completedDays: Set<Long>, today: Long): Int {
        var day = when {
            today in completedDays -> today
            (today - 1) in completedDays -> today - 1
            else -> return 0
        }
        var n = 0
        while (day in completedDays) { n++; day-- }
        return n
    }

    /** The longest run of consecutive dates. */
    fun best(completedDays: Set<Long>): Int {
        var best = 0
        for (d in completedDays) {
            if ((d - 1) in completedDays) continue // not the start of a run
            var n = 0
            while ((d + n) in completedDays) n++
            if (n > best) best = n
        }
        return best
    }
}
