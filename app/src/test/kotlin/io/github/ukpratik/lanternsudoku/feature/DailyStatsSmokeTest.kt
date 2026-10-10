// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.feature

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.lanternsudoku.LanternApp
import io.github.ukpratik.lanternsudoku.MainActivity
import io.github.ukpratik.lanternsudoku.data.LoadResult
import io.github.ukpratik.lanternsudoku.data.SaveRequest
import io.github.ukpratik.lanternsudoku.engine.daily.DailyMapper
import io.github.ukpratik.lanternsudoku.engine.game.CellCode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric smoke tests for the Daily and Stats tabs (US-014, US-015, US-019) and the completion state (US-018):
 * empty stats, today's card and strip, play today's daily from the Daily tab, finish it, see the daily completion with
 * Share, and the numbers on the Stats and Daily tabs afterwards.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class DailyStatsSmokeTest {
    @get:Rule(order = 0)
    val teardown = AppTeardown()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    private val container get() = (compose.activity.application as LanternApp).container

    private fun waitFor(text: String, substring: Boolean = false) =
        compose.waitUntil(10_000) { compose.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty() }

    private fun waitForDescription(text: String, substring: Boolean = false) =
        compose.waitUntil(10_000) { compose.onAllNodes(hasContentDescription(text, substring = substring)).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun statsEmptyState_thenDailyTab_thenPlayAndFinishToday_thenStats() {
        // TC-163: empty state, no zeros table.
        compose.onNodeWithText("Stats").performClick()
        waitFor("Your numbers will glow here")
        assertThat(compose.onAllNodes(hasText("Started")).fetchSemanticsNodes()).isEmpty()

        // Daily tab: today's card, streak line (0, never "broken") and the 7-day strip with today last.
        compose.onNodeWithText("Daily").performClick()
        waitFor("Play today's puzzle")
        val today = container.today.date.value
        val level = DailyMapper.forDate(today).level.name.lowercase().replaceFirstChar(Char::uppercaseChar)
        compose.onNode(hasContentDescription("0-day streak. Best 0. 0 days played.")).assertExists()
        compose.onNode(hasContentDescription("Today, ", substring = true)).assertExists()
        assertThat(compose.onAllNodes(hasContentDescription(", not played.", substring = true)).fetchSemanticsNodes()).hasSize(7)
        assertThat(compose.onAllNodes(hasText("broken", substring = true)).fetchSemanticsNodes()).isEmpty()
        // The tab dot: today's puzzle isn't started.
        compose.onNode(hasContentDescription("Daily, today's puzzle not played yet")).assertExists()

        // Play it: the game bar says "Daily · <date>".
        compose.onNodeWithText("Play today's puzzle").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("cell-0"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        waitFor("Daily · ", substring = true)

        // Leave, then stage a near-solved board (as the debug hook would) and place the last digit through the UI.
        compose.onNode(hasContentDescription("Back")).performClick()
        waitForDescription("Continue Daily", substring = true)
        val (gameId, lastCell, digit) = runBlocking {
            container.saveQueue.flush() // the leaving save lands first, so it can't overwrite the staged board
            val repo = container.gameRepository
            val id = repo.continueCard().first()!!.gameId
            val s = (repo.load(id) as LoadResult.Ok).state
            val empties = (0 until 81).filter { !s.isGiven(it) }
            val cells = s.codes()
            for (i in empties.dropLast(1)) cells[i] = CellCode.of(value = s.solution[i])
            repo.write(SaveRequest.Full(id, s.copy(cells = cells), 0))
            Triple(id, empties.last(), s.solution[empties.last()])
        }
        compose.onNode(hasContentDescription("Continue Daily", substring = true)).performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("cell-$lastCell"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("cell-$lastCell", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("key-$digit", useUnmergedTree = true).performClick()

        // The completion state (US-018) for a daily: summary, streak line, Share result first (US-017).
        waitFor("Nice work.")
        compose.onNodeWithText("Daily · ", substring = true).assertExists()
        compose.onNodeWithText("Share result").assertExists()
        compose.onNodeWithText("New $level game").assertExists()
        compose.onNodeWithText("Home").assertExists()
        compose.onNode(hasContentDescription("Puzzle solved. Nice work.", substring = true)).assertExists()
        compose.onNode(hasContentDescription("1-day streak. Best 1. 1 day played.")).assertExists()
        compose.onNode(hasContentDescription("Clean solve", substring = true)).assertExists()
        assertThat(runBlocking { container.gameRepository.completion(gameId) }!!.dailyDate).isEqualTo(today)

        // Home: the Today row is done with Share; no Daily tab dot any more.
        compose.onNodeWithText("Home").performClick()
        waitForDescription("done in", substring = true)
        assertThat(compose.onAllNodes(hasContentDescription("Daily, today's puzzle not played yet")).fetchSemanticsNodes()).isEmpty()

        // Stats: the full layout replaces the empty state; dailies count toward their level (D-019).
        compose.onNodeWithText("Stats").performClick()
        waitFor("By level")
        compose.onNode(hasContentDescription("1, Completed")).assertExists()
        compose.onNode(hasContentDescription("100%, Completion")).assertExists()
        compose.onNode(hasContentDescription("1-day streak. Best 1. 1 day played.")).assertExists()
        compose.onNode(hasContentDescription("$level: 1 completed", substring = true)).performScrollTo().assertExists()
        // The level chips filter the tiles (TC-164): another level has nothing.
        val other = if (level == "Easy") "Hard" else "Easy"
        compose.onNode(hasText(other) and hasClickAction()).performClick() // the chip, not the by-level row
        compose.waitUntil(5_000) { compose.onAllNodes(hasContentDescription("0, Completed")).fetchSemanticsNodes().isNotEmpty() }

        // Daily tab: today's card reads Done with Share, and today's dot in the strip is done.
        compose.onNodeWithText("Daily").performClick()
        waitFor("Done · ", substring = true)
        compose.onNode(hasContentDescription("Today, ", substring = true) and hasContentDescription(", done.", substring = true)).assertExists()
    }
}
