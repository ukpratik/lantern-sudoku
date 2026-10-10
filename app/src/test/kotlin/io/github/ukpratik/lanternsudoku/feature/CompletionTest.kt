// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.feature

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.lanternsudoku.data.CompletionSummary
import io.github.ukpratik.lanternsudoku.data.DailyStats
import io.github.ukpratik.lanternsudoku.data.GameRepository
import io.github.ukpratik.lanternsudoku.data.LoadResult
import io.github.ukpratik.lanternsudoku.data.SaveQueue
import io.github.ukpratik.lanternsudoku.data.SaveRequest
import io.github.ukpratik.lanternsudoku.data.Settings
import io.github.ukpratik.lanternsudoku.data.TestData
import io.github.ukpratik.lanternsudoku.data.Today
import io.github.ukpratik.lanternsudoku.engine.game.Action
import io.github.ukpratik.lanternsudoku.engine.game.BoxFlags
import io.github.ukpratik.lanternsudoku.engine.game.CellCode
import io.github.ukpratik.lanternsudoku.engine.game.Status
import io.github.ukpratik.lanternsudoku.engine.model.Level
import io.github.ukpratik.lanternsudoku.feature.game.GameEvent
import io.github.ukpratik.lanternsudoku.feature.game.GameViewModel
import io.github.ukpratik.lanternsudoku.feature.game.completionAnnouncement
import io.github.ukpratik.lanternsudoku.feature.game.hintsByStage
import io.github.ukpratik.lanternsudoku.feature.share.ShareText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** US-018 completion as a Game-screen state: the summary read back from the row, best time, dailies, copy. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class CompletionTest {
    private val db = TestData.db()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var date = LocalDate.of(2026, 10, 15)
    private val today = Today(override = { date })
    private val repo = GameRepository(db, TestData.source(), Dispatchers.IO, today = today)
    private val queue = SaveQueue(scope, repo, Dispatchers.IO)
    private var now = 10_000L
    private val res = ApplicationProvider.getApplicationContext<Application>().resources

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        scope.cancel()
        db.close()
    }

    private fun vm(id: Long) = GameViewModel(id, repo, queue, MutableStateFlow(Settings()), now = { now })

    /** Fills all but the last empty cell with the solution (+ [counters]/[flags]), then plays the last digit in [playMs]. */
    private fun solve(id: Long, playMs: Long, hints3: Int = 0, hints2: Int = 0, mistakes: Int = 0, flags: Int = 0): GameViewModel = runBlocking {
        val s = (repo.load(id) as LoadResult.Ok).state
        val empties = (0 until 81).filter { !s.isGiven(it) }
        val cells = s.codes()
        for (i in empties.dropLast(1)) cells[i] = CellCode.of(value = s.solution[i])
        repo.write(
            SaveRequest.Full(
                id,
                s.copy(cells = cells, counters = s.counters.copy(hints2 = hints2, hints3 = hints3, mistakes = mistakes), boxFlags = flags),
                0,
            ),
        )
        val m = vm(id)
        withTimeout(5_000) { m.state.first { it.game != null } }
        m.onResume()
        now += playMs
        m.onAction(Action.TapCell(empties.last()))
        m.onAction(Action.TapDigit(s.solution[empties.last()]))
        m
    }

    private fun GameViewModel.awaitCompletion(): CompletionSummary =
        runBlocking { withTimeout(5_000) { state.first { it.completion != null }.completion!! } }

    @Test
    fun regularGame_summaryHasTimeHintsMistakesClean_andNoShare() {
        val id = runBlocking { repo.startNew(Level.MEDIUM) }
        val m = solve(id, playMs = 61_000, hints2 = 1, mistakes = 1)
        assertThat(m.state.value.game!!.status).isEqualTo(Status.COMPLETED)
        val c = m.awaitCompletion()
        assertThat(c.level).isEqualTo(Level.MEDIUM)
        assertThat(c.elapsedMs).isEqualTo(61_000)
        assertThat(c.hints2).isEqualTo(1)
        assertThat(c.mistakes).isEqualTo(1)
        assertThat(c.clean).isTrue() // a stage-2 hint doesn't reveal a digit
        assertThat(c.newBest).isTrue() // the first clean Medium
        assertThat(c.dailyDate).isNull()
        assertThat(c.share).isNull() // TC-152: no Share on regular games
        assertThat(c.daily).isNull()
    }

    @Test
    fun TC156_newBestOnlyForAFasterCleanSolve() {
        val first = runBlocking { repo.startNew(Level.HARD) }
        solve(first, playMs = 500_000).awaitCompletion()
        val faster = runBlocking { repo.startNew(Level.HARD) }
        val c1 = solve(faster, playMs = 400_000).awaitCompletion()
        assertThat(c1.newBest).isTrue()
        assertThat(c1.previousBestMs).isEqualTo(500_000)
        val fastestButHinted = runBlocking { repo.startNew(Level.HARD) }
        val c2 = solve(fastestButHinted, playMs = 100_000, hints3 = 1).awaitCompletion()
        assertThat(c2.clean).isFalse()
        assertThat(c2.newBest).isFalse()
        assertThat(c2.previousBestMs).isEqualTo(400_000)
    }

    @Test
    fun daily_summaryCarriesItsDate_streakLine_andTheShareText() {
        val id = runBlocking { repo.openOrCreateDaily(date)!! }
        val m = solve(id, playMs = 521_000, hints2 = 1, flags = BoxFlags.hint(7))
        val c = m.awaitCompletion()
        assertThat(c.dailyDate).isEqualTo(date)
        assertThat(m.state.value.dailyDate).isEqualTo(date)
        assertThat(c.daily).isEqualTo(DailyStats(current = 1, best = 1, daysPlayed = 1))
        assertThat(ShareText.build(c.share!!)).isEqualTo(
            "Lantern Sudoku · Daily 15 Oct 2026 · Medium · 8:41 · 1 hint · no mistakes\n🟨🟨🟨\n🟨🟨🟨\n🟨💡🟨",
        )
    }

    @Test
    fun newGameAfterADaily_withASavedGameAtThatLevel_asksContinueOrNew() {
        val saved = runBlocking { repo.startNew(Level.MEDIUM) }
        val id = runBlocking { repo.openOrCreateDaily(date)!! }
        val m = solve(id, playMs = 1_000)
        m.awaitCompletion()
        m.newGameSameLevel()
        val sheet = runBlocking { withTimeout(5_000) { m.state.first { it.continueOrNew != null }.continueOrNew!! } }
        assertThat(sheet.gameId).isEqualTo(saved)
        val opened = mutableListOf<Long>()
        runBlocking {
            m.onContinueSaved()
            opened += (withTimeout(5_000) { m.eventFlow.first { it is GameEvent.OpenGame } } as GameEvent.OpenGame).gameId
        }
        assertThat(opened).containsExactly(saved)
    }

    @Test
    fun TC157_newGameAfterARegularGame_opensANewPuzzleAtTheSameLevel() {
        val id = runBlocking { repo.startNew(Level.EASY) }
        val m = solve(id, playMs = 1_000)
        m.awaitCompletion()
        m.newGameSameLevel()
        val next = runBlocking { (withTimeout(5_000) { m.eventFlow.first { it is GameEvent.OpenGame } } as GameEvent.OpenGame).gameId }
        assertThat(next).isNotEqualTo(id)
        val row = runBlocking { db.gameDao().get(next)!! }
        assertThat(row.puzzleId).startsWith("N-E-")
    }

    @Test
    fun hintsByStage_copy() {
        assertThat(hintsByStage(res, 0, 0, 0)).isEqualTo("none taken")
        assertThat(hintsByStage(res, 0, 1, 0)).isEqualTo("went to Why")
        assertThat(hintsByStage(res, 0, 0, 2)).isEqualTo("both to Do it")
        assertThat(hintsByStage(res, 3, 0, 0)).isEqualTo("all to Where")
        assertThat(hintsByStage(res, 1, 0, 1)).isEqualTo("1 Where · 1 Do it") // TC-154
    }

    @Test
    fun talkBackAnnouncement_matchesTheDesign() {
        val c = CompletionSummary(1, Level.MEDIUM, 1_266_000, 0, 0, 2, 1, clean = false, previousBestMs = 587_000, newBest = false)
        assertThat(completionAnnouncement(res, c)).isEqualTo("Puzzle solved. Nice work. Time 21 minutes 6 seconds. 2 hints. 1 mistake.")
        val clean = c.copy(hints3 = 0, mistakes = 0, elapsedMs = 3_725_000)
        assertThat(completionAnnouncement(res, clean)).isEqualTo("Puzzle solved. Nice work. Time 1 hour 2 minutes 5 seconds. No hints. No mistakes.")
    }
}
