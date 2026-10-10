// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ukpratik.lanternsudoku.data.ContinueSummary
import io.github.ukpratik.lanternsudoku.data.DailyDay
import io.github.ukpratik.lanternsudoku.data.DailyStats
import io.github.ukpratik.lanternsudoku.data.DayState
import io.github.ukpratik.lanternsudoku.data.GameRepository
import io.github.ukpratik.lanternsudoku.data.StatsView
import io.github.ukpratik.lanternsudoku.data.Today
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
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

/** Play tab state (US-001, US-002, D-018). [loaded] turns true with the first database emission. */
data class HomeUiState(
    val loaded: Boolean = false,
    val firstRun: Boolean = false,
    val continueCard: ContinueSummary? = null,
    val inProgress: Set<Level> = emptySet(),
    /** Today's daily (date, rotation level, state): the Play tab's row, the Daily card and the tab dot. */
    val todayDaily: DailyDay = DailyDay(LocalDate.now(), DailyMapper.forDate(LocalDate.now()).level, DayState.NOT_PLAYED),
    val discardNotice: Boolean = false,
    /** The level whose Continue-or-new sheet is open. */
    val sheet: ContinueSummary? = null,
    val openFailed: Boolean = false,
)

/** Stats tab (US-019). */
data class StatsUiState(val loaded: Boolean = false, val view: StatsView = StatsView())

sealed interface HomeEvent {
    data class OpenGame(val gameId: Long) : HomeEvent
}

/** Daily tab (US-014, US-015, design daily-tab): today's card, the streak line and the 7-day strip (oldest first). */
data class DailyUiState(
    val loaded: Boolean = false,
    val week: List<DailyDay> = emptyList(),
    val stats: DailyStats = DailyStats(),
    /** The player's own average time at today's level ("about 12 min"); null until they've completed one. */
    val typicalMs: Long? = null,
) {
    val today: DailyDay? get() = week.lastOrNull()
}

/**
 * Home: Play, Daily and Stats tabs (LLD §5). "Today" comes from [today], which is re-read on resume and on tab switches
 * ([refreshDate]), so the Today row moves on after midnight while an in-progress daily keeps its own date.
 */
class HomeViewModel(private val repository: GameRepository, private val today: Today = repository.today) : ViewModel() {
    private val sheet = MutableStateFlow<ContinueSummary?>(null)
    private val openFailed = MutableStateFlow(false)
    private val events = Channel<HomeEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    private val week = repository.dailyWeek(today.date).shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    /** The Stats tab (US-019) and the daily numbers (US-015). */
    val stats: StateFlow<StatsUiState> = repository.stats(today.date).map { StatsUiState(loaded = true, view = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    val daily: StateFlow<DailyUiState> = combine(week, stats) { days, st ->
        DailyUiState(loaded = true, week = days, stats = st.view.daily, typicalMs = days.lastOrNull()?.let { st.view.of(it.level).averageMs })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DailyUiState())

    val state: StateFlow<HomeUiState> = combine(
        repository.continueCard(),
        repository.inProgressLevels(),
        repository.isFirstRun(),
        combine(repository.discardNotice, week) { n, w -> n to w.last() },
        combine(sheet, openFailed) { s, f -> s to f },
    ) { card, levels, firstRun, (notice, todayDaily), (sheetFor, failed) ->
        HomeUiState(
            loaded = true,
            firstRun = firstRun,
            continueCard = card,
            inProgress = levels,
            todayDaily = todayDaily,
            discardNotice = notice,
            sheet = sheetFor,
            openFailed = failed,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        // Cold start: set aside unreadable saves before Home offers to continue them (design state-save-corrupt).
        viewModelScope.launch { repository.verifyInProgress() }
    }

    /** ON_RESUME and tab switches: re-read the local date (design state-daily-date-changed). No dialog, no banner. */
    fun refreshDate() {
        today.refresh()
    }

    /**
     * Today's puzzle (US-014): opens the daily for the date shown, creating it on first play. A daily keeps that date
     * even if it's finished after midnight. A completed daily isn't reopened (its row offers Share instead).
     */
    fun onPlayDaily(date: LocalDate = today.refresh()) {
        viewModelScope.launch {
            try {
                repository.openOrCreateDaily(date)?.let { events.send(HomeEvent.OpenGame(it)) }
            } catch (e: IOException) {
                openFailed.value = true
            }
        }
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
