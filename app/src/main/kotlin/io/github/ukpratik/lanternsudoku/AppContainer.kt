// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import io.github.ukpratik.lanternsudoku.data.GameRepository
import io.github.ukpratik.lanternsudoku.data.PuzzleSource
import io.github.ukpratik.lanternsudoku.data.SaveQueue
import io.github.ukpratik.lanternsudoku.data.SettingsRepository
import io.github.ukpratik.lanternsudoku.data.Today
import io.github.ukpratik.lanternsudoku.platform.DebugHooks
import io.github.ukpratik.lanternsudoku.data.db.LanternDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import io.github.ukpratik.lanternsudoku.data.Settings
import io.github.ukpratik.lanternsudoku.feature.game.CoachMarkStore
import io.github.ukpratik.lanternsudoku.platform.RatePrompt
import io.github.ukpratik.lanternsudoku.platform.StoreReview

/**
 * Manual dependency injection (ADR-007). Everything is `by lazy`, so nothing heavy happens at startup (NFR-001).
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    /** Outlives every screen: autosaves keep running when the Game screen goes away (LLD §4.8). */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val io: CoroutineDispatcher = Dispatchers.IO

    val database: LanternDatabase by lazy { LanternDatabase.create(appContext) }

    val puzzleSource: PuzzleSource by lazy { PuzzleSource(appContext.assets::open, io) }

    /** The local date, re-read on resume, tab switches and date/time-zone broadcasts; `dbg_date` in debug builds. */
    val today: Today = Today(override = DebugHooks::todayOverride)

    val gameRepository: GameRepository by lazy { GameRepository(database, puzzleSource, io, today = today) }

    val saveQueue: SaveQueue by lazy { SaveQueue(appScope, gameRepository, io) }

    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(PreferenceDataStoreFactory.create(scope = CoroutineScope(io + SupervisorJob())) {
            appContext.preferencesDataStoreFile("settings")
        })
    }

    /** US-024: first-game tips, progress in DataStore. */
    val coachMarks: CoachMarkStore by lazy { CoachMarkStore(settingsRepository) { gameRepository.startedCount() } }

    /** US-025: the play flavour's In-App Review, the fdroid flavour's no-op (ADR-008). */
    val ratePrompt: RatePrompt by lazy {
        RatePrompt(settingsRepository, { gameRepository.completedCount() }, { today.refresh().toEpochDay() }, StoreReview.prompter(appContext))
    }

    /** Read once at start-up (LanternApp) so the first frame has the saved theme (LLD §2.2); null until then. */
    val settingsState: StateFlow<Settings?> by lazy { settingsRepository.settings.stateIn(appScope, SharingStarted.Eagerly, null) }
}
