// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.feature

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.lanternsudoku.LanternApp
import io.github.ukpratik.lanternsudoku.MainActivity
import io.github.ukpratik.lanternsudoku.data.LoadResult
import io.github.ukpratik.lanternsudoku.data.SaveRequest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * M6 (1.0.1) through the real app on Robolectric: coach marks with Skip (US-024, TC-186/187), Fill notes with its
 * confirmation (US-009, TC-076/079), Show timer off (US-023, TC-183), and the Daily tab's past days (US-016, TC-147):
 * a strip tap, a done day opening its summary, a not-played day opening its daily.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class M6SmokeTest {
    @get:Rule(order = 0)
    val teardown = AppTeardown()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    private val app get() = ApplicationProvider.getApplicationContext<LanternApp>()

    private fun tag(t: String) = compose.onNodeWithTag(t, useUnmergedTree = true)

    private fun exists(m: SemanticsMatcher) = compose.onAllNodes(m, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private val hasNotes = SemanticsMatcher("a cell with notes") {
        it.config.getOrElseNullable(SemanticsProperties.ContentDescription) { null }?.any { d -> d.contains(", notes ") } == true
    }

    @Test
    fun coachMarks_fillNotes_andShowTimer() {
        compose.waitUntil(10_000) { exists(hasText("New game")) }
        compose.onNodeWithText("Easy").performClick()
        compose.waitUntil(10_000) { exists(hasTestTag("coach-mark")) }
        compose.onNodeWithText("Lock a number").assertExists()
        compose.onNodeWithText("1 of 3").assertExists()
        // The board stays playable under the tip: select a cell.
        tag("cell-80").performClick()
        tag("coach-skip").performClick()
        compose.waitUntil(5_000) { !exists(hasTestTag("coach-mark")) }

        // Fill notes: no notes yet, so no question (TC-079); then the notes exist and it asks (TC-076); Cancel keeps them.
        compose.onNode(hasContentDescription("More options")).performClick()
        compose.onNodeWithText("Fill notes").performClick()
        compose.waitUntil(5_000) { exists(hasNotes) }
        assertThat(exists(hasText("Replace your notes?"))).isFalse()
        compose.onNode(hasContentDescription("More options")).performClick()
        compose.onNodeWithText("Fill notes").performClick()
        compose.onNodeWithText("Replace your notes?").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        assertThat(exists(hasNotes)).isTrue()
        // One undo step takes every note away again (TC-077).
        tag("tool-undo").performClick()
        compose.waitUntil(5_000) { !exists(hasNotes) }

        // Settings › Show timer off: the bar has no timer (TC-183).
        compose.onNode(hasContentDescription("Pause")).performClick()
        compose.onNodeWithText("Settings").performScrollTo().performClick()
        compose.onNodeWithText("Show timer").performScrollTo().performClick()
        compose.onNode(hasContentDescription("Back")).performClick()
        compose.onNode(hasContentDescription("Resume")).performClick()
        compose.waitUntil(5_000) { !exists(hasText("0:0", substring = true)) }
        assertThat(runBlocking { app.container.settingsRepository.settings.first() }.showTimer).isFalse()
    }

    @Test
    fun pastDays_stripTap_doneDayOpensItsSummary_notPlayedDayOpensItsDaily() {
        compose.waitUntil(10_000) { exists(hasText("New game")) }
        val repo = app.container.gameRepository
        val today = app.container.today.refresh()
        val yesterday = today.minusDays(1)
        runBlocking {
            val id = repo.openOrCreateDaily(yesterday)!!
            repo.write(SaveRequest.Complete(id, (repo.load(id) as LoadResult.Ok).state, 300_000L))
        }
        compose.onNodeWithText("Daily").performClick()
        compose.waitUntil(10_000) { exists(hasTestTag("past-$yesterday")) }
        compose.onNodeWithText("Past 7 days").assertExists()
        compose.onNodeWithText("Don't count toward the streak").assertExists()

        // A strip day jumps to its row; TalkBack offers it as a button.
        tag("strip-${today.minusDays(5)}").performClick()
        compose.waitForIdle()
        tag("past-${today.minusDays(5)}").assertExists()

        // Done: its summary, settled, with Share (no game to play).
        tag("past-$yesterday").performScrollTo().performClick()
        compose.waitUntil(10_000) { exists(hasText("Nice work.")) }
        compose.onNodeWithText("Share result").assertExists()
        compose.onNodeWithText("Home").performClick()
        compose.waitUntil(10_000) { exists(hasText("New game")) }

        // Not played: opens that day's daily; it never counts toward the streak.
        compose.onNodeWithText("Daily").performClick()
        compose.waitUntil(10_000) { exists(hasTestTag("past-${today.minusDays(2)}")) }
        tag("past-${today.minusDays(2)}").performScrollTo().performClick()
        compose.waitUntil(10_000) { exists(hasText("Daily · ", substring = true)) && exists(hasTestTag("cell-0")) }
    }
}
