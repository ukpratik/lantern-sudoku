// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun TemporaryFolder.repo(scope: kotlinx.coroutines.CoroutineScope) =
        SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { java.io.File(root, "settings.preferences_pb") })

    @Test
    fun TC080_defaults_showMistakesOn_limitOff_autoCleanupOn() = runTest {
        val s = tmp.repo(backgroundScope).settings.first()
        assertThat(s.showMistakes).isTrue()
        assertThat(s.mistakeLimit).isEqualTo(0)
        assertThat(s.autoCleanupNotes).isTrue()
        assertThat(s.rules.showMistakes).isTrue()
    }

    @Test
    fun TC081_limitOptions_persist_andTheLimitIsSuspendedWhileMistakesAreHidden() = runTest {
        val repo = tmp.repo(backgroundScope)
        repo.setMistakeLimit(3)
        repo.setShowMistakes(false)
        val s = repo.settings.first()
        assertThat(s.mistakeLimit).isEqualTo(3)
        assertThat(s.rules.showMistakes).isFalse() // the reducer suspends the limit (PRD v1.3)
        assertThat(runCatching { repo.setMistakeLimit(4) }.isFailure).isTrue()
    }
}
