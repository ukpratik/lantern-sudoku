// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.engine.daily

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** Streak and days-played maths per PRD v1.3 §5 and D-028 (TC-139, TC-140, TC-144, TC-145). */
class StreaksTest {
    private val d = LocalDate.of(2027, 5, 10).toEpochDay()

    private fun daily(day: Long, started: Long = day, completed: Long = started) = CompletedGame(day, started, completed)
    private fun newGame(completed: Long) = CompletedGame(null, completed, completed)

    @Test
    fun currentCountsConsecutiveDaysEndingToday() {
        assertThat(Streaks.current(setOf(d - 2, d - 1, d), d)).isEqualTo(3)
    }

    @Test
    fun currentStaysAliveUntilTodayIsPlayed() {
        assertThat(Streaks.current(setOf(d - 2, d - 1), d)).isEqualTo(2) // TC-141
    }

    @Test
    fun currentIsZeroAfterAGap() {
        assertThat(Streaks.current(setOf(d - 4, d - 3, d - 2), d)).isEqualTo(0) // TC-143: 0, never "broken"
        assertThat(Streaks.current(emptySet(), d)).isEqualTo(0)
    }

    @Test
    fun gapsSplitRuns() {
        val days = setOf(d - 9, d - 8, d - 7, d - 6, d - 3, d - 1, d)
        assertThat(Streaks.current(days, d)).isEqualTo(2)
        assertThat(Streaks.best(days)).isEqualTo(4)
        assertThat(Streaks.best(emptySet())).isEqualTo(0)
    }

    @Test
    fun onlyOnTheDayDailiesCountTowardTheStreak() {
        // TC-140: today's daily on time; yesterday's daily played today from the past list → streak 1.
        val games = listOf(daily(d), daily(d - 1, started = d))
        assertThat(Streaks.current(Streaks.streakDays(games), d)).isEqualTo(1)
        // ...but both count toward days played, each by its daily date (PRD v1.3 §5).
        assertThat(Streaks.daysPlayed(games)).isEqualTo(2)
    }

    @Test
    fun newGamesNeverCountTowardTheStreak() {
        assertThat(Streaks.streakDays(listOf(newGame(d), newGame(d - 1)))).isEmpty()
    }

    @Test
    fun daysPlayedCountsDistinctDates() {
        // TC-144: 2 New games on D, 1 daily on D, 1 New game on D+1 → 2.
        val games = listOf(newGame(d), newGame(d), daily(d), newGame(d + 1))
        assertThat(Streaks.daysPlayed(games)).isEqualTo(2)
        assertThat(Streaks.daysPlayed(emptyList())).isEqualTo(0)
    }

    @Test
    fun midnightCrossoverDailyBelongsToItsStartDate() {
        // TC-145 under PRD v1.3 §5 / D-028: started D 23:55, completed D+1 00:05 → streak and days played both use D.
        // (TC-145's "days played includes D+1" predates v1.3; flagged to QA.)
        val g = daily(d, started = d, completed = d + 1)
        assertThat(Streaks.streakDays(listOf(g))).containsExactly(d)
        assertThat(g.playedDay).isEqualTo(d)
        assertThat(Streaks.current(Streaks.streakDays(listOf(g)), d + 1)).isEqualTo(1)
    }

    @Test
    fun duplicatesAreHarmless() {
        val games = listOf(daily(d), daily(d), daily(d - 1))
        assertThat(Streaks.current(Streaks.streakDays(games), d)).isEqualTo(2)
    }

    @Test
    fun dstAndTimeZoneShiftsOnlyMatterThroughTheLocalDate() {
        // The caller stores local epoch days; a 23-hour or 25-hour DST day is still one day apart.
        val zone = ZoneId.of("Europe/London")
        val before = ZonedDateTime.of(2026, 10, 24, 22, 0, 0, 0, zone).toLocalDate().toEpochDay()
        val after = ZonedDateTime.of(2026, 10, 25, 23, 0, 0, 0, zone).toLocalDate().toEpochDay()
        assertThat(Streaks.current(setOf(before, after), after)).isEqualTo(2)
        // Flying west across the date line can repeat a local date: the set ignores the repeat.
        assertThat(Streaks.current(setOf(after, after), after)).isEqualTo(1)
    }

    @Test
    fun clockMovedBackCountsFromTheNewToday() {
        // TC-134 maths: days D-2..D done, then the clock is set back to D-1. Today (D-1) is done, so the streak runs
        // back from it; the "future" day D is simply not part of it. Moving the clock forward again restores 3.
        val days = setOf(d - 2, d - 1, d)
        assertThat(Streaks.current(days, d - 1)).isEqualTo(2)
        assertThat(Streaks.current(days, d)).isEqualTo(3)
        assertThat(Streaks.best(days)).isEqualTo(3)
        // Set back further, to before anything was played: 0, never negative, never "broken".
        assertThat(Streaks.current(days, d - 10)).isEqualTo(0)
    }

    @Test
    fun pastDailiesNeverCountEvenWhenTheyFillAGap() {
        // TC-140: D-2 and D on the day; D-1's daily played later (started on D) doesn't bridge the gap.
        val games = listOf(daily(d - 2), daily(d - 1, started = d), daily(d))
        assertThat(Streaks.current(Streaks.streakDays(games), d)).isEqualTo(1)
        assertThat(Streaks.best(Streaks.streakDays(games))).isEqualTo(1)
        assertThat(Streaks.daysPlayed(games)).isEqualTo(3)
    }

    @Test
    fun aDailyStartedBeforeItsOwnDateDoesNotCount() {
        // Clock moved back then forward: started on D-1 (the device said so), daily date D. Not "on its own date".
        val g = daily(d, started = d - 1, completed = d)
        assertThat(g.countsForStreak).isFalse()
        assertThat(g.playedDay).isEqualTo(d)
    }

    @Test
    fun timeZoneShiftAcrossTheDateLineCanSkipADate() {
        // Kolkata → Pago Pago repeats a date; Pago Pago → Kiritimati can skip one. A skipped local date with no daily
        // ends the streak like any other gap (no anti-cheat, US-014): the rule is purely about local dates.
        assertThat(Streaks.current(setOf(d - 2, d), d)).isEqualTo(1)
    }

    @Test
    fun newGamesCountForDaysPlayedOnTheirCompletionDate() {
        // A New game started on D and finished after midnight counts on D+1 (PRD v1.3 §5).
        val g = CompletedGame(null, d, d + 1)
        assertThat(g.playedDay).isEqualTo(d + 1)
        assertThat(Streaks.daysPlayed(listOf(g, daily(d)))).isEqualTo(2)
    }
}
