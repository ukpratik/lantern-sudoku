// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.feature.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

data class GameUiState(
    val loading: Boolean = true,
    val game: GameState? = null,
    val level: Level = Level.EASY,
    val settings: Settings = Settings(),
    val paused: Boolean = false,
    val saveFailed: Boolean = false,
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
                _state.update { it.copy(loading = false, game = r.state, level = r.level) }
                if (DebugHooks.ENABLED && DebugHooks.consumeNearSolved()) debugNearSolved()
                if (resumed) startClock()
            }
            is LoadResult.Discarded, LoadResult.Missing -> events.send(GameEvent.Leave)
        }
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
        if (t.state != game) _state.update { it.copy(game = t.state) }
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
        if (_state.value.paused) resume() else onAction(Action.Back)
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

    /** Completion's "New <level> game" (US-018 full summary in M4). */
    fun newGameSameLevel() {
        viewModelScope.launch {
            saveQueue.flush() // the completion is written before the next game is picked
            events.send(GameEvent.OpenGame(repository.startNew(_state.value.level)))
        }
    }

    /** Debug builds only (QA hook `dbg:near-solved`): fill all but one empty cell with the solution. */
    fun debugNearSolved() {
        if (!DebugHooks.ENABLED) return
        val game = _state.value.game ?: return
        val next = DebugHooks.nearSolved(game)
        _state.update { it.copy(game = next) }
        persist(next)
    }

    private fun persist(s: GameState) = saveQueue.offer(SaveRequest.Full(gameId, s, clock.elapsedMs()))

    private fun complete(s: GameState) {
        stopClock()
        saveQueue.offer(SaveRequest.Complete(gameId, s, clock.elapsedMs()))
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
