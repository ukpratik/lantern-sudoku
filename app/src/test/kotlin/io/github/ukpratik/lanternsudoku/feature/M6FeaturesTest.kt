// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.feature

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.lanternsudoku.data.GameRepository
import io.github.ukpratik.lanternsudoku.data.SaveQueue
import io.github.ukpratik.lanternsudoku.data.Settings
import io.github.ukpratik.lanternsudoku.data.SettingsRepository
import io.github.ukpratik.lanternsudoku.data.TestData
import io.github.ukpratik.lanternsudoku.engine.game.Action
import io.github.ukpratik.lanternsudoku.engine.game.GameState
import io.github.ukpratik.lanternsudoku.engine.model.Level
import io.github.ukpratik.lanternsudoku.engine.technique.Technique
import io.github.ukpratik.lanternsudoku.feature.game.BoardChange
import io.github.ukpratik.lanternsudoku.feature.game.CoachMarkStore
import io.github.ukpratik.lanternsudoku.feature.game.CoachTip
import io.github.ukpratik.lanternsudoku.feature.game.GameEvent
import io.github.ukpratik.lanternsudoku.feature.game.GameViewModel
import io.github.ukpratik.lanternsudoku.feature.game.HintCopy
import io.github.ukpratik.lanternsudoku.feature.learn.LearnExample
import io.github.ukpratik.lanternsudoku.feature.settings.Licences
import io.github.ukpratik.lanternsudoku.engine.hint.HintPlan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** M6 logic on Robolectric: coach marks (US-024), Fill notes' announcement (US-009), Learn pages (US-026), licences (US-023). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class M6FeaturesTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val db = TestData.db()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val repo = GameRepository(db, TestData.source(), Dispatchers.IO)
    private val queue = SaveQueue(scope, repo, Dispatchers.IO)
    private val settings by lazy { SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { java.io.File(tmp.root, "s.preferences_pb") }) }
    private val coach by lazy { CoachMarkStore(settings) { repo.startedCount() } }
    private val res get() = ApplicationProvider.getApplicationContext<Application>().resources

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        scope.cancel()
        db.close()
    }

    private fun vm(id: Long) = GameViewModel(id, repo, queue, MutableStateFlow(Settings()), now = { 0L }, coach = coach)

    private fun GameViewModel.awaitGame(): GameState = runBlocking { withTimeout(5_000) { state.first { it.game != null }.game!! } }

    private fun stored() = runBlocking { settings.marks().coachMarks }

    /** The tip is decided right after the game loads (one DataStore read); waits for it, or for the store to settle. */
    private fun GameViewModel.awaitTip(): CoachTip? = runBlocking {
        withTimeout(5_000) { while (stored() == null) kotlinx.coroutines.delay(10) }
        kotlinx.coroutines.delay(50)
        state.value.coachTip
    }

    private fun eventually(check: () -> Boolean) = runBlocking { withTimeout(5_000) { while (!check()) kotlinx.coroutines.delay(10) } }

    @Test
    fun tc186_tc189_firstGameShowsThreeTipsInOrder_doingTheActionAdvances() = runBlocking<Unit> {
        val v = vm(repo.startNew(Level.EASY))
        v.awaitGame()
        assertThat(v.awaitTip()).isEqualTo(CoachTip.LOCK)
        v.onAction(Action.TapDigit(5)) // locks 5 (TC-189)
        assertThat(v.state.value.game!!.input.lock).isEqualTo(5)
        assertThat(v.state.value.coachTip).isEqualTo(CoachTip.NOTES)
        v.onCoachNext()
        assertThat(v.state.value.coachTip).isEqualTo(CoachTip.HINT)
        v.onAction(Action.OpenHint)
        assertThat(v.state.value.coachTip).isNull()
        eventually { stored() == CoachTip.ALL }
        // Never again (TC-187).
        val next = vm(repo.startNew(Level.MEDIUM))
        next.awaitGame()
        assertThat(next.awaitTip()).isNull()
    }

    @Test
    fun tc187_tc188_backWithATipCountsAsSkip_andTheGameStays() = runBlocking<Unit> {
        val v = vm(repo.startNew(Level.EASY))
        v.awaitGame()
        assertThat(v.awaitTip()).isEqualTo(CoachTip.LOCK)
        v.onCoachNext() // tip 2 (Notes) showing
        val events = mutableListOf<GameEvent>()
        val job = scope.launchCollect(v, events)
        v.onBack()
        assertThat(v.state.value.coachTip).isNull()
        eventually { stored() == CoachTip.ALL }
        assertThat(events).doesNotContain(GameEvent.Leave)
        job.cancel()
    }

    @Test
    fun leavingMidTips_theUnseenOnesShowInTheNextGame() = runBlocking<Unit> {
        val first = vm(repo.startNew(Level.EASY))
        first.awaitGame()
        assertThat(first.awaitTip()).isEqualTo(CoachTip.LOCK) // shown = seen
        eventually { stored() == CoachTip.LOCK.bit }
        val second = vm(repo.startNew(Level.HARD))
        second.awaitGame()
        eventually { second.state.value.coachTip != null }
        assertThat(second.state.value.coachTip).isEqualTo(CoachTip.NOTES)
    }

    @Test
    fun anUpdateFrom100WithGamesAlreadyPlayed_showsNoTips() = runBlocking<Unit> {
        repo.startNew(Level.EASY)
        val v = vm(repo.startNew(Level.MEDIUM)) // a second game exists before the tips are ever decided
        v.awaitGame()
        assertThat(v.awaitTip()).isNull()
        assertThat(stored()).isEqualTo(CoachTip.ALL)
    }

    @Test
    fun fillNotesIsAnnouncedAsOneChange_andItsUndoIsNot() {
        val s = GameState.new("N-M-0000", io.github.ukpratik.lanternsudoku.engine.model.Grid.parse(BOARD))
        val t = io.github.ukpratik.lanternsudoku.engine.game.GameReducer.reduce(s, Action.FillNotes, io.github.ukpratik.lanternsudoku.engine.game.Rules())
        val change = BoardChange.of(1, s, t.state, Action.FillNotes, t.effects)!!
        assertThat(change.describe(res)).isEqualTo("Notes filled in every empty cell")
        val u = io.github.ukpratik.lanternsudoku.engine.game.GameReducer.reduce(t.state, Action.Undo, io.github.ukpratik.lanternsudoku.engine.game.Rules())
        assertThat(BoardChange.of(2, t.state, u.state, Action.Undo, u.effects)).isNull()
    }

    @Test
    fun tc193_learnHasEveryLadderTechnique_eachPageIsItsTechniquesStage2Text() {
        assertThat(LearnExample.ALL.map { it.technique }).containsExactlyElementsIn(Technique.entries).inOrder()
        for (ex in LearnExample.ALL) {
            val s = ex.stage2()
            val plan = s.input.hint!!.plan as HintPlan.Logical
            assertThat(plan.steps[0].technique).isEqualTo(ex.technique)
            assertThat(s.input.hint!!.stage).isEqualTo(2)
            val text = ex.text(res)
            assertThat(text.title).isEqualTo(HintCopy.techniqueName(res, ex.technique))
            assertThat(text.body).doesNotContain("%")
            assertThat(text.body.lowercase()).doesNotContain("guess")
        }
    }

    @Test
    fun licencesListEveryShippedLibraryWithItsLicenceText() {
        val all = Licences.read(res)
        assertThat(all.size).isGreaterThan(20)
        assertThat(all.map { it.name }).containsAtLeast("Figtree", "Fraunces", "Material Symbols")
        for (e in all) {
            assertThat(e.licences).isNotEmpty()
            for (l in e.licences) assertThat(l.text).isNotEmpty()
        }
        assertThat(all.first { it.name == "Fraunces" }.licences.single().name).isEqualTo("SIL Open Font License 1.1")
    }

    private fun CoroutineScope.launchCollect(v: GameViewModel, into: MutableList<GameEvent>) =
        kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { v.eventFlow.collect { into += it } }

    private companion object {
        /** N-M-0000's givens. */
        const val BOARD = ".2438....58.1...2..7...4..64..91....9..548..2....23..47..4...3..4...1.67....6214."
    }
}
