// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.feature.game

import android.database.sqlite.SQLiteException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ukpratik.lanternsudoku.data.CompletionSummary
import io.github.ukpratik.lanternsudoku.data.ContinueSummary
import io.github.ukpratik.lanternsudoku.data.GameRepository
import io.github.ukpratik.lanternsudoku.data.LoadResult
import io.github.ukpratik.lanternsudoku.data.SaveQueue
import io.github.ukpratik.lanternsudoku.data.SaveRequest
import io.github.ukpratik.lanternsudoku.data.Settings
import io.github.ukpratik.lanternsudoku.engine.game.Action
import io.github.ukpratik.lanternsudoku.engine.game.Effect
import io.github.ukpratik.lanternsudoku.engine.game.GameReducer
import io.github.ukpratik.lanternsudoku.engine.game.GameState
import io.github.ukpratik.lanternsudoku.engine.game.HapticKind
import io.github.ukpratik.lanternsudoku.engine.game.Status
import io.github.ukpratik.lanternsudoku.engine.model.Level
import io.github.ukpratik.lanternsudoku.platform.DebugHooks
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate

data class GameUiState(
    val loading: Boolean = true,
    val game: GameState? = null,
    val level: Level = Level.EASY,
    val settings: Settings = Settings(),
    val paused: Boolean = false,
    val saveFailed: Boolean = false,
    /** The daily's own date (the bar reads "Daily · Thu 15 Oct"); null for New games. */
    val dailyDate: LocalDate? = null,
    /** The completion summary (US-018), read back once the completing write has landed. */
    val completion: CompletionSummary? = null,
    /** "New <level> game" after a daily, with a saved game at that level: the Continue-or-new choice (US-002). */
    val continueOrNew: ContinueSummary? = null,
    /** The last placement, note change or blocked tap, for TalkBack's polite live region (US-022). */
    val change: BoardChange? = null,
    /** The coach-mark tip on screen (US-024), or null. */
    val coachTip: CoachTip? = null,
    /**
     * A daily completed earlier, opened from the Daily tab's Past 7 days list (US-016): only its summary shows, from
     * [completedGrid]; there's no game to play and it never counts as "just completed" (no rate prompt, US-025).
     */
    val completedGrid: String? = null,
)

sealed interface GameEvent {
    data class Haptic(val kind: HapticKind) : GameEvent

    /** Leave the game screen (Back with nothing to consume, or the save couldn't be opened). */
    data object Leave : GameEvent

    data class OpenGame(val gameId: Long) : GameEvent
}

/**
 * Holds the authoritative [GameState] and runs [GameReducer] on the main thread (pure, microseconds), then maps
 * effects to the platform: saves through [SaveQueue], haptics and completion (LLD §4.9). Play time: [PlayClock].
 */
class GameViewModel(
    private val gameId: Long,
    private val repository: GameRepository,
    private val saveQueue: SaveQueue,
    settings: Flow<Settings>,
    private val now: () -> Long,
    private val wallClock: () -> Long = System::currentTimeMillis,
    private val coach: CoachMarkStore? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(GameUiState())
    val state: StateFlow<GameUiState> = _state.asStateFlow()

    private val _elapsed = MutableStateFlow(0L)

    /** Play time for the bar, updated once a second; separate so the board doesn't recompose with the timer. */
    val elapsed: StateFlow<Long> = _elapsed.asStateFlow()

    private val events = Channel<GameEvent>(Channel.BUFFERED)
    val eventFlow: Flow<GameEvent> = events.receiveAsFlow()

    private var clock = PlayClock(now)
    private var resumed = false
    private var ticker: Job? = null
    private var saveFailedShownAt = Long.MIN_VALUE / 2
    private var changeSeq = 0
    private var coachMask = CoachTip.ALL

    init {
        viewModelScope.launch { settings.collect { s -> _state.update { it.copy(settings = s) } } }
        viewModelScope.launch {
            saveQueue.lastWriteOk.collect { ok ->
                if (ok) {
                    _state.update { it.copy(saveFailed = false) }
                } else if (!_state.value.saveFailed && wallClock() - saveFailedShownAt >= SAVE_FAILED_EVERY_MS) {
                    saveFailedShownAt = wallClock() // design: shown at most once a minute
                    _state.update { it.copy(saveFailed = true) }
                }
            }
        }
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        when (val r = repository.load(gameId)) {
            is LoadResult.Ok -> {
                clock = PlayClock(now, r.elapsedMs)
                _elapsed.value = r.elapsedMs
                _state.update { it.copy(loading = false, game = r.state, level = r.level, dailyDate = r.dailyDate) }
                if (DebugHooks.ENABLED && DebugHooks.consumeNearSolved()) debugNearSolved()
                if (DebugHooks.ENABLED) DebugHooks.consumeBoard()?.let(::debugBoard)
                if (resumed) startClock()
                if (r.state.status != Status.COMPLETED) coach?.let { coachMask = it.start(); presentNextTip() }
            }
            LoadResult.Missing -> {
                // A completed daily from the Past 7 days list (US-016) opens on its summary; anything else leaves.
                val done = repository.completedView(gameId) ?: return events.send(GameEvent.Leave)
                _state.update {
                    it.copy(loading = false, level = done.first.level, dailyDate = done.first.dailyDate, completion = done.first, completedGrid = done.second)
                }
            }
            is LoadResult.Discarded -> events.send(GameEvent.Leave)
        }
    }

    // ---- coach marks (US-024) ----

    /** Shows the next unseen tip and records it as seen (a tip counts as seen once shown, PRD v1.3). */
    private fun presentNextTip() {
        val tip = CoachTip.next(coachMask)
        if (tip != null) {
            coachMask = coachMask or tip.bit
            saveCoach()
        }
        _state.update { it.copy(coachTip = tip) }
    }

    /** "Next" on a tip (on the last one it reads "Got it"). */
    fun onCoachNext() {
        if (_state.value.coachTip != null) presentNextTip()
    }

    /** "Skip" ends all the tips for good; Back with a tip showing does the same (design §8.2). */
    fun onCoachSkip() {
        if (_state.value.coachTip == null) return
        coachMask = CoachTip.ALL
        saveCoach()
        _state.update { it.copy(coachTip = null) }
    }

    private fun saveCoach() {
        val store = coach ?: return
        val mask = coachMask
        viewModelScope.launch { store.save(mask) }
    }

    fun onAction(action: Action) {
        val ui = _state.value
        val game = ui.game ?: return
        if (ui.paused) return
        val t = GameReducer.reduce(game, action, ui.settings.rules)
        if (!t.handled) {
            events.trySend(GameEvent.Leave)
            return
        }
        val change = BoardChange.of(++changeSeq, game, t.state, action, t.effects)
        if (t.state != game || change != null) _state.update { it.copy(game = t.state, change = change ?: it.change) }
        ui.coachTip?.let { tip ->
            if (t.state.status == Status.COMPLETED) _state.update { it.copy(coachTip = null) } // the rest wait for the next game
            else if (CoachTip.done(tip, game, t.state)) presentNextTip()
        }
        for (e in t.effects) {
            when (e) {
                Effect.Persist -> if (t.state.status != Status.COMPLETED) persist(t.state)
                is Effect.Haptic -> events.trySend(GameEvent.Haptic(e.kind))
                Effect.Completed -> complete(t.state)
                Effect.Restarted -> {
                    clock.reset()
                    _elapsed.value = 0
                    persist(t.state)
                }
                is Effect.OpenPanel -> Unit // the status drives the panel
            }
        }
    }

    /** Back priority (design §8.2): pause cover first, then the reducer (panel > erase > lock > selection > leave). */
    fun onBack() {
        val ui = _state.value
        val game = ui.game
        when {
            ui.paused -> resume()
            game == null -> if (ui.completion != null) events.trySend(GameEvent.Leave)
            // A tip is only on screen with the pad (no panel); Back then dismisses it, counted as Skip (design §8.2).
            ui.coachTip != null && game.status == Status.PLAYING && game.input.hint == null -> onCoachSkip()
            else -> onAction(Action.Back)
        }
    }

    fun onResume() {
        resumed = true
        if (!_state.value.paused) startClock()
    }

    fun onPause() {
        resumed = false
        stopClock()
    }

    /** ON_STOP: offer the latest full state (the write runs in the app scope and isn't cancelled by leaving). */
    fun onStop() {
        stopClock()
        _state.value.game?.takeIf { it.status != Status.COMPLETED }?.let(::persist)
    }

    fun pause() {
        if (_state.value.game?.status == Status.COMPLETED) return
        _state.update { it.copy(paused = true) }
        stopClock()
    }

    fun resume() {
        _state.update { it.copy(paused = false) }
        if (resumed) startClock()
    }

    fun restart() {
        _state.update { it.copy(paused = false) }
        onAction(Action.Restart)
        if (resumed) startClock()
    }

    fun retrySave() {
        _state.update { it.copy(saveFailed = false) }
        _state.value.game?.takeIf { it.status != Status.COMPLETED }?.let(::persist)
    }

    fun dismissSaveFailed() = _state.update { it.copy(saveFailed = false) }

    /**
     * Completion's "New <level> game" (US-018). After a regular game there's never a saved game at that level (it was
     * this one); after a daily there may be, and then the player chooses Continue or Start new (US-002).
     */
    fun newGameSameLevel() {
        viewModelScope.launch {
            saveQueue.flush() // the completion is written before the next game is picked
            val level = _state.value.level
            val saved = repository.inProgress(level)
            if (saved != null) {
                _state.update { it.copy(continueOrNew = saved) }
            } else {
                events.send(GameEvent.OpenGame(repository.startNew(level)))
            }
        }
    }

    fun onContinueSaved() {
        val saved = _state.value.continueOrNew ?: return
        _state.update { it.copy(continueOrNew = null) }
        events.trySend(GameEvent.OpenGame(saved.gameId))
    }

    fun onStartNewInstead() {
        val saved = _state.value.continueOrNew ?: return
        _state.update { it.copy(continueOrNew = null) }
        viewModelScope.launch { events.send(GameEvent.OpenGame(repository.startNew(saved.level, abandonExisting = true))) }
    }

    fun onDismissContinueOrNew() = _state.update { it.copy(continueOrNew = null) }

    /** Debug builds only (QA hook `dbg:near-solved`): fill all but one empty cell with the solution. */
    fun debugNearSolved() {
        if (!DebugHooks.ENABLED) return
        val game = _state.value.game ?: return
        val next = DebugHooks.nearSolved(game)
        _state.update { it.copy(game = next) }
        persist(next)
    }

    /** Debug builds only (QA hook `dbg_board`, with `dbg_load`): fill in a fixed position's entries. */
    private fun debugBoard(board: String) {
        val game = _state.value.game ?: return
        val next = DebugHooks.withBoard(game, board)
        _state.update { it.copy(game = next) }
        persist(next)
    }

    private fun persist(s: GameState) = saveQueue.offer(SaveRequest.Full(gameId, s, clock.elapsedMs()))

    private fun complete(s: GameState) {
        stopClock()
        saveQueue.offer(SaveRequest.Complete(gameId, s, clock.elapsedMs()))
        viewModelScope.launch {
            saveQueue.flush()
            // If the completing write failed (storage full), the summary still shows from memory, without the best-time
            // comparison; the save-failed message has already told the player.
            val read = try {
                repository.completion(gameId)
            } catch (e: SQLiteException) {
                null
            }
            val c = read ?: s.counters.let { n ->
                val ui = _state.value
                CompletionSummary(gameId, ui.level, clock.elapsedMs(), n.hints1, n.hints2, n.hints3, n.mistakes, clean = n.hints3 == 0,
                    previousBestMs = null, newBest = false, dailyDate = ui.dailyDate)
            }
            _state.update { it.copy(completion = c) }
        }
    }

    private fun startClock() {
        val game = _state.value.game ?: return
        if (game.status == Status.COMPLETED || clock.running) return
        clock.start()
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (isActive) {
                delay(TICK_MS - clock.elapsedMs() % TICK_MS)
                _elapsed.value = clock.elapsedMs()
                saveQueue.offer(SaveRequest.Elapsed(gameId, clock.elapsedMs()))
            }
        }
    }

    private fun stopClock() {
        ticker?.cancel()
        ticker = null
        if (!clock.running) return
        clock.stop()
        _elapsed.value = clock.elapsedMs()
        if (_state.value.game?.status != Status.COMPLETED) saveQueue.offer(SaveRequest.Elapsed(gameId, clock.elapsedMs()))
    }

    private companion object {
        const val TICK_MS = 1_000L
        const val SAVE_FAILED_EVERY_MS = 60_000L
    }
}
