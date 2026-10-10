// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ukpratik.lanternsudoku.data.ContinueSummary
import io.github.ukpratik.lanternsudoku.data.GameRepository
import io.github.ukpratik.lanternsudoku.engine.daily.DailyMapper
import io.github.ukpratik.lanternsudoku.engine.model.Level
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.LocalDate

/** Play tab state (US-001, US-002). [loaded] turns true with the first database emission. */
data class HomeUiState(
    val loaded: Boolean = false,
    val firstRun: Boolean = false,
    val continueCard: ContinueSummary? = null,
    val inProgress: Set<Level> = emptySet(),
    val today: LocalDate = LocalDate.now(),
    val todayLevel: Level = Level.EASY,
    val discardNotice: Boolean = false,
    /** The level whose Continue-or-new sheet is open. */
    val sheet: ContinueSummary? = null,
    val openFailed: Boolean = false,
)

sealed interface HomeEvent {
    data class OpenGame(val gameId: Long) : HomeEvent
}

class HomeViewModel(private val repository: GameRepository, private val today: () -> LocalDate = { LocalDate.now() }) : ViewModel() {
    private val sheet = MutableStateFlow<ContinueSummary?>(null)
    private val openFailed = MutableStateFlow(false)
    private val events = Channel<HomeEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    val state: StateFlow<HomeUiState> = combine(
        repository.continueCard(),
        repository.inProgressLevels(),
        repository.isFirstRun(),
        repository.discardNotice,
        combine(sheet, openFailed) { s, f -> s to f },
    ) { card, levels, firstRun, notice, (sheetFor, failed) ->
        val date = today()
        HomeUiState(
            loaded = true,
            firstRun = firstRun,
            continueCard = card,
            inProgress = levels,
            today = date,
            todayLevel = DailyMapper.forDate(date).level,
            discardNotice = notice,
            sheet = sheetFor,
            openFailed = failed,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        // Cold start: set aside unreadable saves before Home offers to continue them (design state-save-corrupt).
        viewModelScope.launch { repository.verifyInProgress() }
    }

    fun onContinueCard() {
        val card = state.value.continueCard ?: return
        events.trySend(HomeEvent.OpenGame(card.gameId))
    }

    /** US-002: a level with a saved game asks first; otherwise an unplayed puzzle opens immediately. */
    fun onLevel(level: Level) {
        viewModelScope.launch {
            val saved = repository.inProgress(level)
            if (saved != null) sheet.value = saved else start(level, abandonExisting = false)
        }
    }

    fun onSheetContinue() {
        val s = sheet.value ?: return
        sheet.value = null
        events.trySend(HomeEvent.OpenGame(s.gameId))
    }

    /** "Start new" marks the saved game abandoned (PRD §5) and opens a fresh puzzle; the sheet is the confirmation. */
    fun onSheetStartNew() {
        val s = sheet.value ?: return
        sheet.value = null
        viewModelScope.launch { start(s.level, abandonExisting = true) }
    }

    fun onSheetDismiss() {
        sheet.value = null
    }

    fun onDismissNotice() = repository.dismissDiscardNotice()

    fun onDismissOpenFailed() {
        openFailed.value = false
    }

    private suspend fun start(level: Level, abandonExisting: Boolean) {
        try {
            events.send(HomeEvent.OpenGame(repository.startNew(level, abandonExisting)))
        } catch (e: IOException) { // an unreadable bank asset (shouldn't happen: CI-validated)
            openFailed.value = true
        }
    }
}
