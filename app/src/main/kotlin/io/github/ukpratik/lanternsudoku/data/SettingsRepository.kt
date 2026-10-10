// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.ukpratik.lanternsudoku.engine.game.Rules
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * The settings M2 uses (LLD §2.2). Defaults: Show mistakes on, Mistake limit off (US-010), auto-cleanup on (US-006),
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

/** Settings › Theme (US-020). Stored by name, so reordering never changes a saved choice. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

class SettingsRepository(private val store: DataStore<Preferences>) {
    val settings: Flow<Settings> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e } // DataStore docs: read errors → defaults
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
    }
}
