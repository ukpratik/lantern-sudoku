// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.ukpratik.lanternsudoku.engine.game.Rules
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * The player's settings (LLD §2.2, US-023). Defaults: Show mistakes on, Mistake limit off (US-010), auto-cleanup on (US-006),
 * highlight same digit on, haptics on, timer shown (US-023); theme follows the system, High contrast and Larger digits off
 * (US-020, US-021).
 */
data class Settings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val highContrast: Boolean = false,
    val largeDigits: Boolean = false,
    val showMistakes: Boolean = true,
    val mistakeLimit: Int = 0,
    val autoCleanupNotes: Boolean = true,
    val highlightSameDigit: Boolean = true,
    val haptics: Boolean = true,
    val showTimer: Boolean = true,
) {
    /** The reducer's view. v1.3: the limit is suspended while Show mistakes is off. */
    val rules: Rules get() = Rules(showMistakes = showMistakes, mistakeLimit = mistakeLimit, autoCleanup = autoCleanupNotes)

    companion object {
        val LIMIT_OPTIONS = listOf(0, 3, 5)
    }
}

/**
 * App bookkeeping kept next to the settings (LLD §2.2), never shown in Settings: coach-mark progress (US-024) and the
 * rate prompt's dates (US-025). [coachMarks] is null until the first game decides it.
 */
data class AppMarks(val coachMarks: Int? = null, val firstLaunchEpochDay: Long? = null, val lastReviewPromptAt: Long = 0)

/** Settings › Theme (US-020). Stored by name, so reordering never changes a saved choice. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

class SettingsRepository(private val store: DataStore<Preferences>) {
    private val prefs: Flow<Preferences> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e } // DataStore docs: read errors → defaults

    val settings: Flow<Settings> = prefs
        .map { p ->
            Settings(
                theme = ThemeMode.entries.firstOrNull { it.name == p[THEME] } ?: ThemeMode.SYSTEM,
                highContrast = p[HIGH_CONTRAST] ?: false,
                largeDigits = p[LARGE_DIGITS] ?: false,
                showMistakes = p[SHOW_MISTAKES] ?: true,
                mistakeLimit = (p[MISTAKE_LIMIT] ?: 0).takeIf { it in Settings.LIMIT_OPTIONS } ?: 0,
                autoCleanupNotes = p[AUTO_CLEANUP] ?: true,
                highlightSameDigit = p[HIGHLIGHT_SAME] ?: true,
                haptics = p[HAPTICS] ?: true,
                showTimer = p[SHOW_TIMER] ?: true,
            )
        }

    val marks: Flow<AppMarks> = prefs.map { p -> AppMarks(p[COACH_MARKS], p[FIRST_LAUNCH], p[LAST_REVIEW] ?: 0) }

    suspend fun marks(): AppMarks = marks.first()

    /** US-024: the bitmask of coach-mark tips seen or skipped (LLD §2.2 `coach_marks_done`). */
    suspend fun setCoachMarks(mask: Int) {
        store.edit { it[COACH_MARKS] = mask }
    }

    /** US-025 "never on the first day": the first launch's local date, recorded once. */
    suspend fun recordFirstLaunch(epochDay: Long) {
        store.edit { if (it[FIRST_LAUNCH] == null) it[FIRST_LAUNCH] = epochDay }
    }

    /** US-025 "at most once per 60 days": when the review flow was last requested (shown or not, Play decides). */
    suspend fun recordReviewPrompt(atMs: Long) {
        store.edit { it[LAST_REVIEW] = atMs }
    }

    suspend fun setAutoCleanupNotes(on: Boolean) {
        store.edit { it[AUTO_CLEANUP] = on }
    }

    suspend fun setHighlightSameDigit(on: Boolean) {
        store.edit { it[HIGHLIGHT_SAME] = on }
    }

    suspend fun setShowTimer(on: Boolean) {
        store.edit { it[SHOW_TIMER] = on }
    }

    suspend fun setHaptics(on: Boolean) {
        store.edit { it[HAPTICS] = on }
    }

    suspend fun setShowMistakes(on: Boolean) {
        store.edit { it[SHOW_MISTAKES] = on }
    }

    suspend fun setMistakeLimit(limit: Int) {
        require(limit in Settings.LIMIT_OPTIONS) { "limit $limit" }
        store.edit { it[MISTAKE_LIMIT] = limit }
    }

    suspend fun setTheme(mode: ThemeMode) {
        store.edit { it[THEME] = mode.name }
    }

    suspend fun setHighContrast(on: Boolean) {
        store.edit { it[HIGH_CONTRAST] = on }
    }

    suspend fun setLargeDigits(on: Boolean) {
        store.edit { it[LARGE_DIGITS] = on }
    }

    private companion object {
        val THEME = stringPreferencesKey("theme_mode")
        val HIGH_CONTRAST = booleanPreferencesKey("high_contrast")
        val LARGE_DIGITS = booleanPreferencesKey("larger_digits")
        val SHOW_MISTAKES = booleanPreferencesKey("show_mistakes")
        val MISTAKE_LIMIT = intPreferencesKey("mistake_limit")
        val AUTO_CLEANUP = booleanPreferencesKey("auto_cleanup_notes")
        val HIGHLIGHT_SAME = booleanPreferencesKey("highlight_same_digit")
        val HAPTICS = booleanPreferencesKey("haptics")
        val SHOW_TIMER = booleanPreferencesKey("show_timer")
        val COACH_MARKS = intPreferencesKey("coach_marks_done")
        val FIRST_LAUNCH = longPreferencesKey("first_launch_epoch_day")
        val LAST_REVIEW = longPreferencesKey("last_review_prompt_at")
    }
}
