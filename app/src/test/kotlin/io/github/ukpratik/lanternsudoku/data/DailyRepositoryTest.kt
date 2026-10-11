// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.data

import android.app.Application
import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.lanternsudoku.data.db.GameEntity
import io.github.ukpratik.lanternsudoku.data.db.LanternDatabase
import io.github.ukpratik.lanternsudoku.engine.daily.DailyMapper
import io.github.ukpratik.lanternsudoku.engine.model.Level
import io.github.ukpratik.lanternsudoku.engine.model.PuzzleId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The daily through the repository (US-014, US-015): opening by date, one row per date, the start date rule, the
 * 7-day strip, and "today" moving under it (midnight, time zone, clock set back). PRD v1.3 §5, D-028.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class DailyRepositoryTest {
    private val db: LanternDatabase = TestData.db()
    private val dao = db.gameDao()
    private var instant = Instant.parse("2026-10-15T10:00:00Z") // Thursday
    private var tz: ZoneId = ZoneId.of("Europe/London")
    private val clock = object : Clock() {
        override fun getZone(): ZoneId = tz
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = instant
    }
    private val today = Today(clock, { tz })
    private val repo = GameRepository(db, TestData.source(), Dispatchers.IO, clock, { tz }, today)

    @After
    fun tearDown() = db.close()

    private val thursday = LocalDate.of(2026, 10, 15)

    private suspend fun complete(id: Long) {
        val s = (repo.load(id) as LoadResult.Ok).state
        repo.write(SaveRequest.Complete(id, s, 500_000))
    }

    private suspend fun week() = withTimeout(5_000) { repo.dailyWeek(today.date).first() }

    @Test
    fun opensTodaysDaily_fromTheDailyBank_withItsDateAndStartDate() = runBlocking<Unit> {
        val id = repo.openOrCreateDaily(today.refresh())!!
        val row = dao.get(id)!!
        assertThat(row.kind).isEqualTo(GameEntity.KIND_DAILY)
        assertThat(row.puzzleId).isEqualTo(DailyMapper.forDate(thursday).id.toString()) // D-M-0001 (epoch 2026-10-12)
        assertThat(row.puzzleId).isEqualTo("D-M-0001")
        assertThat(row.level).isEqualTo(Level.MEDIUM.ordinal)
        assertThat(row.dailyEpochDay).isEqualTo(thursday.toEpochDay())
        assertThat(row.startedEpochDay).isEqualTo(thursday.toEpochDay())
        // The givens are the daily bank's record.
        val s = (repo.load(id) as LoadResult.Ok)
        assertThat(s.dailyDate).isEqualTo(thursday)
        assertThat(s.state.givens).isEqualTo(TestData.source().read(PuzzleId.parse("D-M-0001")).givens)
    }

    @Test
    fun TC135_secondOpenResumesTheSameRow_completedReturnsNull_oneRowPerDate() = runBlocking<Unit> {
        val id = repo.openOrCreateDaily(thursday)!!
        assertThat(repo.openOrCreateDaily(thursday)).isEqualTo(id)
        complete(id)
        assertThat(repo.openOrCreateDaily(thursday)).isNull()
        assertThat(dao.dailies(thursday.toEpochDay(), thursday.toEpochDay()).first()).hasSize(1)
    }

    @Test
    fun aSetAsideDailyCanBeStartedAgain() = runBlocking<Unit> {
        val id = repo.openOrCreateDaily(thursday)!!
        dao.discard(id, 0)
        val again = repo.openOrCreateDaily(thursday)!!
        assertThat(again).isNotEqualTo(id)
        assertThat(dao.get(id)).isNull()
        assertThat(dao.get(again)!!.status).isEqualTo(GameEntity.IN_PROGRESS)
    }

    @Test
    fun dailiesNeverAppearInNewGame() = runBlocking<Unit> {
        repo.openOrCreateDaily(thursday)
        val n = repo.startNew(Level.MEDIUM)
        assertThat(dao.get(n)!!.puzzleId).startsWith("N-M-")
        // The daily isn't the saved New game of its level either (one saved game per level plus dailies, TC-012).
        assertThat(repo.inProgress(Level.MEDIUM)!!.gameId).isEqualTo(n)
    }

    @Test
    fun TC130_aDailyStartedBeforeMidnightKeepsItsDate_andStillCountsForTheStreak() = runBlocking<Unit> {
        instant = Instant.parse("2026-10-15T22:55:00Z") // 23:55 BST Thursday
        val id = repo.openOrCreateDaily(today.refresh())!!
        instant = Instant.parse("2026-10-15T23:05:00Z") // 00:05 Friday
        complete(id)
        val row = dao.get(id)!!
        assertThat(row.dailyEpochDay).isEqualTo(thursday.toEpochDay())
        assertThat(row.completedEpochDay).isEqualTo(thursday.toEpochDay() + 1)
        val c = repo.completion(id)!!
        assertThat(c.dailyDate).isEqualTo(thursday)
        assertThat(c.share!!.date).isEqualTo(thursday)
        assertThat(c.daily).isEqualTo(DailyStats(current = 1, best = 1, daysPlayed = 1))
        // Friday's strip: Thursday done, Friday (today) not played.
        val w = week()
        assertThat(w.last().date).isEqualTo(thursday.plusDays(1))
        assertThat(w.last().state).isEqualTo(DayState.NOT_PLAYED)
        assertThat(w[5].state).isEqualTo(DayState.DONE)
    }

    @Test
    fun midnightWithADailyInProgress_todayMovesOn_theStartedDailyKeepsItsDate() = runBlocking<Unit> {
        val id = repo.openOrCreateDaily(today.refresh())!!
        instant = Instant.parse("2026-10-15T23:12:00Z") // 00:12 Friday (design state-daily-date-changed)
        today.refresh()
        val w = week()
        assertThat(w.last().date).isEqualTo(LocalDate.of(2026, 10, 16))
        assertThat(w.last().level).isEqualTo(Level.HARD)
        assertThat(w.last().state).isEqualTo(DayState.NOT_PLAYED)
        assertThat(w[5].state).isEqualTo(DayState.STARTED)
        assertThat(w[5].gameId).isEqualTo(id)
        assertThat(repo.continueCard().first()!!.dailyDate).isEqualTo(thursday)
    }

    @Test
    fun TC133_timeZoneAcrossTheDateLine_followsTheNewLocalDate() = runBlocking<Unit> {
        tz = ZoneId.of("Asia/Kolkata")
        instant = Instant.parse("2026-10-15T04:30:00Z") // Thu 10:00 in Kolkata
        val id = repo.openOrCreateDaily(today.refresh())!!
        tz = ZoneId.of("Pacific/Pago_Pago") // UTC-11: still Wednesday there
        assertThat(today.refresh()).isEqualTo(LocalDate.of(2026, 10, 14))
        assertThat(week().last().level).isEqualTo(Level.MEDIUM)
        assertThat(dao.get(id)!!.dailyEpochDay).isEqualTo(thursday.toEpochDay()) // the started daily keeps its date
        // Wednesday's daily is a different puzzle from Thursday's even at the same level (Wed/Thu slots).
        val wed = repo.openOrCreateDaily(today.refresh())!!
        assertThat(dao.get(wed)!!.puzzleId).isEqualTo("D-M-0000")
    }

    @Test
    fun TC134_clockSetBackToACompletedDate_showsItDone_noDuplicateRow() = runBlocking<Unit> {
        val thu = repo.openOrCreateDaily(today.refresh())!!
        complete(thu)
        instant = instant.minusSeconds(86_400) // Wednesday
        val wed = repo.openOrCreateDaily(today.refresh())!!
        complete(wed)
        instant = instant.plusSeconds(86_400) // back to Thursday
        today.refresh()
        assertThat(repo.openOrCreateDaily(thursday)).isNull()
        val w = week()
        assertThat(w.takeLast(2).map { it.state }).containsExactly(DayState.DONE, DayState.DONE).inOrder()
        assertThat(dao.dailies(0, Long.MAX_VALUE).first()).hasSize(2)
        assertThat(repo.dailyStats(today.date).first()).isEqualTo(DailyStats(current = 2, best = 2, daysPlayed = 2))
    }

    @Test
    fun pastDailyStartedToday_isDoneInTheStrip_butNotTheStreak() = runBlocking<Unit> {
        val tuesday = thursday.minusDays(2)
        val id = repo.openOrCreateDaily(tuesday)!! // started on Thursday
        assertThat(dao.get(id)!!.startedEpochDay).isEqualTo(thursday.toEpochDay())
        complete(id)
        val w = week()
        assertThat(w[4].date).isEqualTo(tuesday)
        assertThat(w[4].state).isEqualTo(DayState.DONE)
        assertThat(w[4].share!!.date).isEqualTo(tuesday)
        assertThat(repo.dailyStats(today.date).first()).isEqualTo(DailyStats(current = 0, best = 0, daysPlayed = 1))
    }

    @Test
    fun TC137_aDateBeforeTheEpochStillOpensADaily() = runBlocking<Unit> {
        val id = repo.openOrCreateDaily(LocalDate.of(2025, 1, 1))!!
        assertThat(dao.get(id)!!.puzzleId).isEqualTo("D-M-0854")
    }

    @Test
    fun weekIsTodayAndTheSixPreviousDates_oldestFirst_levelsByRotation() = runBlocking<Unit> {
        val w = week()
        assertThat(w.map { it.date }).containsExactlyElementsIn((6L downTo 0L).map { thursday.minusDays(it) }).inOrder()
        assertThat(w.map { it.level }).containsExactly(
            Level.HARD, Level.HARD, Level.EXPERT, Level.EASY, Level.EASY, Level.MEDIUM, Level.MEDIUM, // Fri…Thu
        ).inOrder()
        assertThat(w.map { it.state }.toSet()).containsExactly(DayState.NOT_PLAYED)
    }

    /** US-016 (TC-147): a past daily from the list counts toward days played and stats, not the streak; once done it reopens as its summary. */
    @Test
    fun TC147_pastDailyPlayedFromTheList_countsDaysPlayedNotStreak_andReopensAsItsSummary() = runBlocking<Unit> {
        val monday = thursday.minusDays(3)
        val id = repo.openOrCreateDaily(monday)!!
        assertThat(repo.completedView(id)).isNull() // not done yet: nothing to show but the game
        complete(id)
        assertThat(repo.dailyStats(today.date).first()).isEqualTo(DailyStats(current = 0, best = 0, daysPlayed = 1))
        assertThat(repo.stats(today.date).first().of(Level.EASY).completed).isEqualTo(1)
        val (summary, grid) = repo.completedView(id)!!
        assertThat(summary.dailyDate).isEqualTo(monday)
        assertThat(summary.newBest).isFalse() // that was news when it was solved
        assertThat(summary.share).isNotNull()
        assertThat(grid).hasLength(81)
        assertThat(repo.openOrCreateDaily(monday)).isNull() // completed: no second row (TC-135)
        assertThat(week()[3].gameId).isEqualTo(id) // the list row knows which game to open
    }
}
