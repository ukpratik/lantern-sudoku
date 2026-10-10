// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.feature

import android.app.Application
import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.lanternsudoku.data.GameRepository
import io.github.ukpratik.lanternsudoku.data.LoadResult
import io.github.ukpratik.lanternsudoku.data.SaveQueue
import io.github.ukpratik.lanternsudoku.data.SaveRequest
import io.github.ukpratik.lanternsudoku.data.Settings
import io.github.ukpratik.lanternsudoku.data.TestData
import io.github.ukpratik.lanternsudoku.data.db.GameEntity
import io.github.ukpratik.lanternsudoku.engine.game.Action
import io.github.ukpratik.lanternsudoku.engine.game.CellCode
import io.github.ukpratik.lanternsudoku.engine.game.GameState
import io.github.ukpratik.lanternsudoku.engine.game.Status
import io.github.ukpratik.lanternsudoku.engine.model.Level
import io.github.ukpratik.lanternsudoku.feature.game.GameEvent
import io.github.ukpratik.lanternsudoku.feature.game.GameViewModel
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

/** GameViewModel with a real repository and save queue: autosave, process-death restore, play time, completion, limit. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class GameViewModelTest {
    private val db = TestData.db()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val repo = GameRepository(db, TestData.source(), Dispatchers.IO)
    private val queue = SaveQueue(scope, repo, Dispatchers.IO)
    private val settings = MutableStateFlow(Settings())
    private var now = 10_000L

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        scope.cancel()
        db.close()
    }

    private fun vm(id: Long) = GameViewModel(id, repo, queue, settings, now = { now })

    private fun GameViewModel.awaitGame(): GameState = runBlocking { withTimeout(5_000) { state.first { it.game != null }.game!! } }

    private fun GameViewModel.place(cell: Int, digit: Int) {
        onAction(Action.TapCell(cell))
        onAction(Action.TapDigit(digit))
    }

    @Test
    fun TC014_movesAutosave_andAFreshViewModelAfterProcessDeathRestoresEverything() = runBlocking<Unit> {
        val id = repo.startNew(Level.MEDIUM)
        val first = vm(id)
        val s0 = first.awaitGame()
        val empties = (0 until 81).filter { !s0.isGiven(it) }
        first.onResume()
        first.place(empties[0], s0.solution[empties[0]])
        first.place(empties[1], (1..9).first { it != s0.solution[empties[1]] }) // a mistake
        first.onAction(Action.ToggleNotes)
        first.place(empties[2], 4)
        first.onAction(Action.Undo)
        first.onAction(Action.Redo)
        now += 7_000
        first.onPause()
        first.onStop()
        queue.flush()
        val before = first.state.value.game!!

        // Process death: nothing survives but the database; the route's gameId reloads the row.
        val restored = vm(id).awaitGame()
        assertThat(restored.codes()).isEqualTo(before.codes())
        assertThat(restored.undo).isEqualTo(before.undo)
        assertThat(restored.redo).isEqualTo(before.redo)
        assertThat(restored.counters.mistakes).isEqualTo(1)
        assertThat(restored.input.selection).isNull() // transient input isn't saved
        assertThat(db.gameDao().get(id)!!.elapsedMs).isEqualTo(7_000)
    }

    @Test
    fun TC028_playTimeCountsOnlyWhileResumed() = runBlocking<Unit> {
        val id = repo.startNew(Level.EASY)
        val m = vm(id)
        m.awaitGame()
        m.onResume()
        now += 3_000
        m.onPause()
        now += 60_000 // backgrounded
        m.onResume()
        now += 2_000
        m.pause() // the pause screen stops it too
        now += 30_000
        m.resume()
        now += 1_000
        m.onPause()
        queue.flush()
        assertThat(m.elapsed.value).isEqualTo(6_000)
        assertThat(db.gameDao().get(id)!!.elapsedMs).isEqualTo(6_000)
    }

    @Test
    fun completingMove_writesACompletedRow_andStopsTheClock() = runBlocking<Unit> {
        val id = repo.startNew(Level.EASY)
        val s = (repo.load(id) as LoadResult.Ok).state
        val empties = (0 until 81).filter { !s.isGiven(it) }
        val cells = s.codes()
        for (i in empties.dropLast(1)) cells[i] = CellCode.of(value = s.solution[i])
        repo.write(SaveRequest.Full(id, s.copy(cells = cells), 0))
        val m = vm(id)
        m.awaitGame()
        m.onResume()
        now += 5_000
        m.place(empties.last(), s.solution[empties.last()])
        now += 30_000
        queue.flush()
        assertThat(m.state.value.game!!.status).isEqualTo(Status.COMPLETED)
        val row = db.gameDao().get(id)!!
        assertThat(row.status).isEqualTo(GameEntity.COMPLETED)
        assertThat(row.elapsedMs).isEqualTo(5_000) // TC-032: stops on the completing move
        assertThat(row.stateBlob).isNull()
        // The completion summary (US-018) is read back from that row.
        val summary = withTimeout(5_000) { m.state.first { it.completion != null } }.completion!!
        assertThat(summary.elapsedMs).isEqualTo(5_000)
    }

    @Test
    fun TC085_TC086_limitPanel_keepPlayingPersistsTheOverride() = runBlocking<Unit> {
        settings.value = Settings(mistakeLimit = 3)
        val id = repo.startNew(Level.EASY)
        val m = vm(id)
        val s = m.awaitGame()
        val empties = (0 until 81).filter { !s.isGiven(it) }
        repeat(3) { k -> m.place(empties[k], (1..9).first { it != s.solution[empties[k]] }) }
        assertThat(m.state.value.game!!.status).isEqualTo(Status.LIMIT)
        m.onBack() // = Keep playing
        m.onStop()
        queue.flush()
        val restored = vm(id).awaitGame()
        assertThat(restored.status).isEqualTo(Status.PLAYING)
        assertThat(restored.overrides.limitOff).isTrue()
    }

    @Test
    fun restart_clearsTheBoardAndTheTime() = runBlocking<Unit> {
        val id = repo.startNew(Level.EASY)
        val m = vm(id)
        val s = m.awaitGame()
        m.onResume()
        now += 4_000
        val cell = (0 until 81).first { !s.isGiven(it) }
        m.place(cell, 5)
        m.restart()
        m.onPause()
        queue.flush()
        assertThat(m.state.value.game!!.value(cell)).isEqualTo(0)
        assertThat(db.gameDao().get(id)!!.elapsedMs).isEqualTo(0)
    }

    @Test
    fun backWithNothingToConsume_leavesTheGame() = runBlocking<Unit> {
        val id = repo.startNew(Level.EASY)
        val m = vm(id)
        m.awaitGame()
        m.onBack()
        assertThat(withTimeout(5_000) { m.eventFlow.first { it is GameEvent.Leave } }).isEqualTo(GameEvent.Leave)
    }

    @Test
    fun unreadableSave_leavesAndRaisesTheNotice() = runBlocking<Unit> {
        val id = repo.startNew(Level.EASY)
        db.openHelper.writableDatabase.execSQL("UPDATE games SET state_blob = 'nope' WHERE id = $id")
        val m = vm(id)
        assertThat(withTimeout(5_000) { m.eventFlow.first() }).isEqualTo(GameEvent.Leave)
        assertThat(repo.discardNotice.value).isTrue()
    }

    @Test
    fun hints_countsPerStageArePersisted_theClockKeepsRunning_andBackClosesThePanelFirst() = runBlocking<Unit> {
        val id = repo.startNew(Level.MEDIUM)
        val m = vm(id)
        m.awaitGame()
        m.onResume()
        m.onAction(Action.OpenHint)
        m.onAction(Action.HintNext) // Why
        now += 4_000 // reading the explanation: the panel doesn't pause play time (PRD §5, TC-027)
        m.onBack() // closes the panel, doesn't leave
        assertThat(m.state.value.game!!.input.hint).isNull()
        m.onAction(Action.OpenHint)
        m.onAction(Action.HintNext)
        m.onAction(Action.HintNext) // Do it
        m.onAction(Action.CloseHint)
        m.onPause()
        m.onStop()
        queue.flush()
        assertThat(m.elapsed.value).isEqualTo(4_000)
        val restored = vm(id).awaitGame()
        assertThat(restored.counters.hints2).isEqualTo(1)
        assertThat(restored.counters.hints3).isEqualTo(1)
        assertThat(restored.input.hint).isNull()
        assertThat(restored.boxFlags).isNotEqualTo(0) // the hint boxes are flagged for the share grid
    }
}
