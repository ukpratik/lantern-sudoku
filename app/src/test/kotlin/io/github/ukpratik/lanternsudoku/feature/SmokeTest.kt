// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.feature

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.lanternsudoku.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose smoke tests on the real app (Robolectric): first-run Home (TC-001), start Easy → 81 cell nodes with TalkBack
 * labels (US-022 basic, TC-175), place a digit, and the Continue card after returning Home (US-001).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class SmokeTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val isCell = SemanticsMatcher("is a board cell") {
        it.config.getOrElseNullable(SemanticsProperties.TestTag) { null }?.startsWith("cell-") == true
    }

    private fun cellTags() = compose.onAllNodes(isCell, useUnmergedTree = true)

    @Test
    fun firstRun_startEasy_placeDigit_thenContinueFromHome() {
        compose.onNodeWithText("New game").assertExists()
        compose.onNodeWithText("Today's puzzle").assertExists()
        compose.onNodeWithText("Start here").assertExists()

        compose.onNodeWithText("Easy").performClick()
        compose.waitUntil(10_000) { cellTags().fetchSemanticsNodes().size == 81 }

        // Find an empty cell from its label, select it, place a digit with the pad.
        val empty = cellTags().fetchSemanticsNodes().first { node ->
            node.config.getOrElseNullable(SemanticsProperties.ContentDescription) { null }?.single()?.endsWith("empty") == true
        }
        val tag = empty.config[SemanticsProperties.TestTag]
        val label = empty.config[SemanticsProperties.ContentDescription].single()
        assertThat(label).matches("Row \\d, column \\d, empty")
        compose.onNodeWithTag(tag, useUnmergedTree = true).performClick()
        compose.onNodeWithTag(tag, useUnmergedTree = true)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "selected"))
        compose.onNodeWithTag("key-4", useUnmergedTree = true).performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasTestTag(tag) and hasContentDescription(label.replace("empty", "entry 4")), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Undo is now available; leave via the bar's Back and find the Continue card.
        compose.onNodeWithTag("tool-undo", useUnmergedTree = true).assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Undo")))
        compose.onNode(hasContentDescription("Back")).performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("In progress")).fetchSemanticsNodes().isNotEmpty() }
        // One target with one label (TalkBack): "Continue Easy, 0:00, 37 of 81 cells".
        compose.onNode(hasContentDescription("Continue Easy", substring = true)).assertExists().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag(tag) and hasContentDescription(label.replace("empty", "entry 4")), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }
}
