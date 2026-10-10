// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.engine.daily

import io.github.ukpratik.lanternsudoku.engine.model.Level
import io.github.ukpratik.lanternsudoku.engine.model.Pool
import io.github.ukpratik.lanternsudoku.engine.model.PuzzleId
import java.time.DayOfWeek
import java.time.LocalDate

/** The daily for a local date. */
data class DailyRef(val date: LocalDate, val level: Level, val id: PuzzleId)

/** D-019 weekday rotation: Mon/Tue Easy, Wed/Thu Medium, Fri/Sat Hard, Sun Expert. Permanent after 1.0 (R-010). */
object Rotation {
    fun level(day: DayOfWeek): Level = when (day) {
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY -> Level.EASY
        DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY -> Level.MEDIUM
        DayOfWeek.FRIDAY, DayOfWeek.SATURDAY -> Level.HARD
        DayOfWeek.SUNDAY -> Level.EXPERT
    }

    /** The weekdays of a level, Monday first (e.g. EASY → [MONDAY, TUESDAY]). */
    fun days(level: Level): List<DayOfWeek> = DayOfWeek.entries.filter { level(it) == level }

    /** Number of weekday slots a level has, which is also its daily-file length divided by [DailyMapper.PERIOD_WEEKS]. */
    fun slots(level: Level): Int = days(level).size
}

/**
 * Date → daily puzzle (US-014, ADR-005). Depends only on the local calendar date: never on time zone, clock time or the
 * bank's size, so appending puzzles never shifts a past daily.
 */
object DailyMapper {
    /**
     * A fixed Monday. **Provisional:** M4 sets it once to the Monday of the 1.0 release week (ADR-005) and the golden
     * table is regenerated then; after 1.0 it never changes again.
     */
    val EPOCH: LocalDate = LocalDate.of(2027, 1, 4)

    const val PERIOD_WEEKS = 520

    fun forDate(date: LocalDate): DailyRef {
        val week = Math.floorDiv(date.toEpochDay() - EPOCH.toEpochDay(), 7L)
        val level = Rotation.level(date.dayOfWeek)
        val days = Rotation.days(level)
        val index = Math.floorMod(week, PERIOD_WEEKS.toLong()).toInt() * days.size + days.indexOf(date.dayOfWeek)
        return DailyRef(date, level, PuzzleId(Pool.DAILY, level, index))
    }

    /** Today and the [n] − 1 previous dates, newest first (US-016 strip). */
    fun lastDays(today: LocalDate, n: Int = 7): List<DailyRef> = (0 until n).map { forDate(today.minusDays(it.toLong())) }

    /** Records a daily file must hold for [level]: 520 weeks × the level's weekday slots (1,040 / 1,040 / 1,040 / 520). */
    fun dailyCount(level: Level): Int = PERIOD_WEEKS * Rotation.slots(level)
}
