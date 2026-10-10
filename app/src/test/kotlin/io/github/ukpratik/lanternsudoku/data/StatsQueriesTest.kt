// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.data

import android.app.Application
import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.lanternsudoku.data.db.GameEntity
import io.github.ukpratik.lanternsudoku.data.db.GameEntity.Companion.ABANDONED
import io.github.ukpratik.lanternsudoku.data.db.GameEntity.Companion.COMPLETED
import io.github.ukpratik.lanternsudoku.data.db.GameEntity.Companion.DISCARDED
import io.github.ukpratik.lanternsudoku.data.db.GameEntity.Companion.IN_PROGRESS
import io.github.ukpratik.lanternsudoku.data.db.LanternDatabase
import io.github.ukpratik.lanternsudoku.engine.model.Level
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Stats, streak and days-played queries on Room with seeded rows (US-015, US-019; LLD §2.1): TC-161, TC-144, TC-140,
 * TC-145, TC-155 and the best-time check. Everything is derived by query; nothing is a stored counter (ADR-006).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class StatsQueriesTest {
    private val db: LanternDatabase = TestData.db()
    private val dao = db.gameDao()
    private val d = LocalDate.of(2026, 10, 15).toEpochDay()
    private val repo = GameRepository(db, TestData.source(limit = 5), Dispatchers.IO, today = Today(override = { LocalDate.ofEpochDay(d) }))

    @After
    fun tearDown() = db.close()

    private var n = 0

    private suspend fun row(
        level: Level,
        status: Int,
        elapsedMs: Long = 0,
        clean: Boolean = false,
        daily: Long? = null,
        started: Long = daily ?: d,
        completed: Long? = if (status == COMPLETED) started else null,
        hints3: Int = if (status == COMPLETED && !clean) 1 else 0,
    ): Long = dao.insert(
        GameEntity(
            puzzleId = if (daily != null) "D-${level.code}-%04d".format(n++) else "N-${level.code}-%04d".format(n++),
            kind = if (daily != null) GameEntity.KIND_DAILY else GameEntity.KIND_NEW,
            level = level.ordinal,
            dailyEpochDay = daily,
            status = status,
            startedAt = 0,
            startedEpochDay = started,
            updatedAt = 0,
            completedEpochDay = completed,
            elapsedMs = elapsedMs,
            hints3 = hints3,
            clean = clean,
            filledCount = 81,
            gridSummary = ".".repeat(81),
            stateVersion = null,
            stateBlob = null,
        ),
    )

    @Test
    fun TC161_perLevelAndOverallStats_dailiesInTheirLevel_discardedNotStarted() = runBlocking<Unit> {
        // Easy: 2 completed (5:00 clean, 6:00 with a stage-3 hint), 1 abandoned, 1 discarded, 1 in progress.
        row(Level.EASY, COMPLETED, 300_000, clean = true)
        row(Level.EASY, COMPLETED, 360_000, clean = false)
        row(Level.EASY, ABANDONED)
        row(Level.EASY, DISCARDED)
        row(Level.EASY, IN_PROGRESS)
        // Medium: a clean New game 9:47 and a clean daily 8:41 (D-019: dailies count toward their level and best).
        row(Level.MEDIUM, COMPLETED, 587_000, clean = true)
        row(Level.MEDIUM, COMPLETED, 521_000, clean = true, daily = d)
        // Hard: only a non-clean completion → no best time, but an average.
        row(Level.HARD, COMPLETED, 900_000, clean = false)

        val view = repo.stats(flowOf(LocalDate.ofEpochDay(d))).first()
        val easy = view.of(Level.EASY)
        assertThat(easy.started).isEqualTo(4) // discarded excluded, in progress included
        assertThat(easy.completed).isEqualTo(2)
        assertThat(easy.abandoned).isEqualTo(1)
        assertThat(easy.ratePercent).isEqualTo(50)
        assertThat(easy.bestMs).isEqualTo(300_000) // clean solves only
        assertThat(easy.averageMs).isEqualTo(330_000) // all completed
        assertThat(easy.clean).isEqualTo(1)

        val medium = view.of(Level.MEDIUM)
        assertThat(medium.completed).isEqualTo(2)
        assertThat(medium.bestMs).isEqualTo(521_000)
        assertThat(medium.averageMs).isEqualTo(554_000)

        val hard = view.of(Level.HARD)
        assertThat(hard.bestMs).isNull()
        assertThat(hard.averageMs).isEqualTo(900_000)
        assertThat(view.of(Level.EXPERT)).isEqualTo(LevelStats())
        assertThat(view.of(Level.EXPERT).ratePercent).isNull()

        val all = view.overall
        assertThat(all.started).isEqualTo(7)
        assertThat(all.completed).isEqualTo(5)
        assertThat(all.abandoned).isEqualTo(1)
        assertThat(all.clean).isEqualTo(3)
        assertThat(all.bestMs).isEqualTo(300_000)
        assertThat(all.averageMs).isEqualTo((300_000L + 360_000 + 587_000 + 521_000 + 900_000) / 5)
        assertThat(all.ratePercent).isEqualTo(71) // 5 / 7
        assertThat(view.isEmpty).isFalse()
    }

    @Test
    fun TC163_emptyUntilTheFirstCompletion_abandoningAloneStaysEmpty() = runBlocking<Unit> {
        assertThat(repo.stats(flowOf(LocalDate.ofEpochDay(d))).first().isEmpty).isTrue()
        row(Level.EASY, ABANDONED)
        row(Level.MEDIUM, IN_PROGRESS)
        assertThat(repo.stats(flowOf(LocalDate.ofEpochDay(d))).first().isEmpty).isTrue()
        row(Level.MEDIUM, COMPLETED, 1_000, clean = true)
        assertThat(repo.stats(flowOf(LocalDate.ofEpochDay(d))).first().isEmpty).isFalse()
    }

    @Test
    fun TC144_daysPlayed_distinctDates_dailyOnItsDate() = runBlocking<Unit> {
        // 2 New games on D, 1 daily on D, 1 New game on D+1 → 2.
        row(Level.EASY, COMPLETED, started = d)
        row(Level.EASY, COMPLETED, started = d)
        row(Level.EASY, COMPLETED, daily = d)
        row(Level.HARD, COMPLETED, started = d + 1)
        // Not completed: never a day played.
        row(Level.HARD, ABANDONED, started = d + 2)
        row(Level.HARD, IN_PROGRESS, started = d + 3)
        assertThat(dao.daysPlayed().first()).isEqualTo(2)
    }

    @Test
    fun TC145_midnightCrossoverDaily_countsOnItsStartDate_forStreakAndDaysPlayed() = runBlocking<Unit> {
        // Started D 23:55, completed D+1 00:05.
        row(Level.MEDIUM, COMPLETED, daily = d, started = d, completed = d + 1)
        assertThat(dao.streakDays().first()).containsExactly(d)
        assertThat(dao.daysPlayed().first()).isEqualTo(1)
        val stats = repo.dailyStats(flowOf(LocalDate.ofEpochDay(d + 1))).first()
        assertThat(stats).isEqualTo(DailyStats(current = 1, best = 1, daysPlayed = 1))
    }

    @Test
    fun TC140_pastDailyStartedLater_neverExtendsTheStreak() = runBlocking<Unit> {
        row(Level.MEDIUM, COMPLETED, daily = d) // today's, on the day
        row(Level.EASY, COMPLETED, daily = d - 1, started = d) // yesterday's, played today from the past list
        row(Level.EASY, COMPLETED, daily = d - 3) // an older on-the-day daily
        assertThat(dao.streakDays().first()).containsExactly(d, d - 3)
        val stats = repo.dailyStats(flowOf(LocalDate.ofEpochDay(d))).first()
        assertThat(stats.current).isEqualTo(1)
        assertThat(stats.best).isEqualTo(1)
        assertThat(stats.daysPlayed).isEqualTo(3) // past dailies still count toward days played
    }

    @Test
    fun streakCountsOnlyCompletedDailies_endingTodayOrYesterday() = runBlocking<Unit> {
        row(Level.EASY, COMPLETED, daily = d - 3)
        row(Level.EASY, COMPLETED, daily = d - 2)
        row(Level.MEDIUM, COMPLETED, daily = d - 1)
        row(Level.MEDIUM, IN_PROGRESS, daily = d) // today's started, not finished: the streak is still alive (TC-141)
        row(Level.HARD, COMPLETED) // a New game: never the streak
        val today = repo.dailyStats(flowOf(LocalDate.ofEpochDay(d))).first()
        assertThat(today).isEqualTo(DailyStats(current = 3, best = 3, daysPlayed = 4)) // + the New game's day
        // Four days later with nothing played: 0 next to best 3 (TC-143; the copy never says "broken").
        val later = repo.dailyStats(flowOf(LocalDate.ofEpochDay(d + 4))).first()
        assertThat(later).isEqualTo(DailyStats(current = 0, best = 3, daysPlayed = 4))
    }

    @Test
    fun TC155_cleanIsSetAtCompletion_onlyWithoutStageThreeHints() = runBlocking<Unit> {
        val a = repo.startNew(Level.EASY)
        val b = repo.startNew(Level.MEDIUM)
        val sa = (repo.load(a) as LoadResult.Ok).state
        val sb = (repo.load(b) as LoadResult.Ok).state
        repo.write(SaveRequest.Complete(a, sa.copy(counters = sa.counters.copy(hints1 = 1, hints2 = 1)), 1_000))
        repo.write(SaveRequest.Complete(b, sb.copy(counters = sb.counters.copy(hints3 = 1)), 1_000))
        assertThat(dao.get(a)!!.clean).isTrue()
        assertThat(dao.get(b)!!.clean).isFalse()
    }

    @Test
    fun bestTimeCheck_excludesThisGame_andCountsCleanSolvesOnly() = runBlocking<Unit> {
        row(Level.MEDIUM, COMPLETED, 587_000, clean = true)
        row(Level.MEDIUM, COMPLETED, 400_000, clean = false) // faster but not clean: ignored
        val mine = row(Level.MEDIUM, COMPLETED, 510_000, clean = true)
        assertThat(dao.bestCleanExcluding(Level.MEDIUM.ordinal, mine)).isEqualTo(587_000)
        val summary = repo.completion(mine)!!
        assertThat(summary.previousBestMs).isEqualTo(587_000)
        assertThat(summary.newBest).isTrue()
        assertThat(summary.clean).isTrue()

        val slower = row(Level.MEDIUM, COMPLETED, 600_000, clean = true)
        assertThat(repo.completion(slower)!!.newBest).isFalse()
        val notClean = row(Level.MEDIUM, COMPLETED, 100_000, clean = false)
        assertThat(repo.completion(notClean)!!.newBest).isFalse() // TC-156: only a faster *clean* solve
        val firstHard = row(Level.HARD, COMPLETED, 900_000, clean = true)
        assertThat(repo.completion(firstHard)!!.newBest).isTrue() // the first clean solve at a level is a best
        assertThat(repo.completion(firstHard)!!.previousBestMs).isNull()
    }

    @Test
    fun completionOfAnInProgressGame_isNull() = runBlocking<Unit> {
        assertThat(repo.completion(row(Level.EASY, IN_PROGRESS))).isNull()
        assertThat(repo.completion(12_345)).isNull()
    }
}
