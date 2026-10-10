// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.engine.daily

import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.lanternsudoku.engine.model.Level
import io.github.ukpratik.lanternsudoku.engine.model.Pool
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class DailyMapperTest {
    @Test
    fun goldenDates() {
        // TC-127: committed table of 30 dates (pre-epoch, DST dates, the 520-week wrap) → IDs.
        val rows = javaClass.getResource("/fixtures/golden-dailies.csv")!!.readText().lines().filter { it.isNotBlank() && !it.startsWith("#") }
        assertThat(rows).hasSize(30)
        for (row in rows) {
            val (date, weekday, id) = row.split(',')
            val ref = DailyMapper.forDate(LocalDate.parse(date))
            assertThat(ref.date.dayOfWeek.name).isEqualTo(weekday)
            assertThat(ref.id.toString()).isEqualTo(id)
        }
    }

    @Test
    fun epochIsAMonday() {
        assertThat(DailyMapper.EPOCH.dayOfWeek).isEqualTo(DayOfWeek.MONDAY)
    }

    @Test
    fun rotationFollowsD019() {
        val monday = DailyMapper.EPOCH.plusWeeks(3)
        val levels = (0L until 7L).map { DailyMapper.forDate(monday.plusDays(it)).level }
        assertThat(levels).containsExactly(
            Level.EASY, Level.EASY, Level.MEDIUM, Level.MEDIUM, Level.HARD, Level.HARD, Level.EXPERT,
        ).inOrder()
    }

    @Test
    fun mondayAndTuesdayNeverShareAPuzzle() {
        // TC-126, for 10 weeks either side of the epoch; same for Wed/Thu and Fri/Sat.
        for (w in -10L until 10L) {
            val mon = DailyMapper.EPOCH.plusWeeks(w)
            for (pair in listOf(0L to 1L, 2L to 3L, 4L to 5L)) {
                val a = DailyMapper.forDate(mon.plusDays(pair.first)).id
                val b = DailyMapper.forDate(mon.plusDays(pair.second)).id
                assertThat(a.level).isEqualTo(b.level)
                assertThat(a).isNotEqualTo(b)
            }
        }
    }

    @Test
    fun everyDateMapsIntoTheDailyPoolWithinItsFileAndConsecutiveDaysDiffer() {
        // Over 25 years from 1990, every index is inside the daily file and each (level, index) repeats only every 520 weeks.
        var d = LocalDate.of(1990, 1, 1)
        val seen = HashMap<String, LocalDate>()
        while (d.year < 2015) {
            val ref = DailyMapper.forDate(d)
            assertThat(ref.id.pool).isEqualTo(Pool.DAILY)
            assertThat(ref.id.index).isIn(0 until DailyMapper.dailyCount(ref.level))
            seen.put(ref.id.toString(), d)?.let { prev -> assertThat(prev.plusWeeks(520)).isEqualTo(d) }
            d = d.plusDays(1)
        }
    }

    @Test
    fun datesBeforeTheEpochWork() {
        val ref = DailyMapper.forDate(DailyMapper.EPOCH.minusDays(1)) // the Sunday before
        assertThat(ref.level).isEqualTo(Level.EXPERT)
        assertThat(ref.id.index).isEqualTo(519)
        assertThat(DailyMapper.forDate(LocalDate.of(1900, 1, 1)).id.index).isAtLeast(0)
    }

    @Test
    fun onlyTheLocalDateMattersNotTheZoneOrDst() {
        // One instant is two different local dates in Kolkata and Los Angeles: each device gets its own date's daily,
        // and two devices on the same local date get the same puzzle whatever their zone (TC-128 logic).
        val instant = Instant.parse("2027-03-14T20:00:00Z") // US DST starts that day
        val kolkata = instant.atZone(ZoneId.of("Asia/Kolkata")).toLocalDate()
        val la = instant.atZone(ZoneId.of("America/Los_Angeles")).toLocalDate()
        assertThat(kolkata).isEqualTo(LocalDate.of(2027, 3, 15))
        assertThat(la).isEqualTo(LocalDate.of(2027, 3, 14))
        assertThat(DailyMapper.forDate(kolkata)).isEqualTo(DailyMapper.forDate(LocalDate.of(2027, 3, 15)))
        // 00:30 and 23:30 local on a DST-change day are the same date, so the same daily.
        val zone = ZoneId.of("Europe/London")
        val early = LocalDate.of(2026, 10, 25).atTime(0, 30).atZone(zone).toLocalDate()
        val late = LocalDate.of(2026, 10, 25).atTime(23, 30).atZone(zone).toLocalDate()
        assertThat(DailyMapper.forDate(early)).isEqualTo(DailyMapper.forDate(late))
    }

    @Test
    fun dailyFileSizesFollowTheSlots() {
        assertThat(Level.entries.map { DailyMapper.dailyCount(it) }).containsExactly(1040, 1040, 1040, 520).inOrder()
    }

    @Test
    fun lastDaysIsTodayAndSixBefore() {
        val today = LocalDate.of(2027, 2, 3)
        val days = DailyMapper.lastDays(today)
        assertThat(days.map { it.date }).containsExactlyElementsIn((0L until 7L).map { today.minusDays(it) }).inOrder()
    }
}
