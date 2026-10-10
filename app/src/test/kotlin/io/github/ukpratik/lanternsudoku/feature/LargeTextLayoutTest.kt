// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.feature

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.text.TextLayoutResult
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.ukpratik.lanternsudoku.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * US-021 / NFR-007 (TC-172, TC-173, TC-206): at 200% system font scale, on the compact 360×640 phone (3-button nav) and a
 * regular 411×891 phone, every screen's text fits: no text is ellipsised, cut by its own bounds, or clipped by a parent
 * outside a scrolling container. Native graphics, so the bundled fonts are really measured. Also checks that the board
 * digits ignore the font scale (the board keeps its geometry) and the 30 dp cell floor.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class LargeTextLayoutTest(private val qualifiers: String) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun screens() = listOf(arrayOf("w360dp-h640dp-xhdpi"), arrayOf("w411dp-h891dp-xxhdpi"))
    }

    private val compose = createAndroidComposeRule<MainActivity>()

    /** Screen size and 2.0× font scale are set before the activity starts. */
    @get:Rule
    val rules: TestRule = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            RuntimeEnvironment.setQualifiers(qualifiers)
            RuntimeEnvironment.setFontScale(2f)
        }
    }).around(AppTeardown()).around(compose)

    @Test
    fun everyScreenHoldsAt200Percent() {
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("New game")).fetchSemanticsNodes().isNotEmpty() }
        assertTextFits("Play tab")

        compose.onNodeWithText("Easy").scrollIfNeededAndClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("cell-80"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        assertTextFits("Game idle")
        // The board keeps a cell of at least 30 dp whatever the text size.
        val cell = compose.onNodeWithTag("cell-0", useUnmergedTree = true).fetchSemanticsNode()
        assertThat(cell.size.width / compose.density.density).isAtLeast(29.9f)

        // The longest status line: a digit lock in notes mode.
        compose.onNodeWithTag("key-5", useUnmergedTree = true).performTouchInput { longClick() }
        compose.onNodeWithTag("tool-notes", useUnmergedTree = true).scrollIfNeededAndClick()
        compose.waitForIdle()
        assertTextFits("Game locked + notes")
        compose.onNodeWithTag("tool-notes", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("key-5", useUnmergedTree = true).performTouchInput { longClick() }

        compose.onNodeWithTag("tool-hint", useUnmergedTree = true).scrollIfNeededAndClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("hint-panel")).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        assertTextFits("Hint stage 1")
        compose.onNodeWithTag("hint-primary", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertTextFits("Hint stage 2")
        compose.onNodeWithTag("hint-secondary", useUnmergedTree = true).performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("key-1"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }

        compose.onNode(hasContentDescription("Pause")).performClick()
        compose.waitForIdle()
        assertTextFits("Pause")
        compose.onNodeWithText("Settings").scrollIfNeededAndClick()
        compose.waitForIdle()
        assertTextFits("Settings")
        compose.onNode(hasContentDescription("Back")).performClick()
        compose.waitForIdle()
        compose.onAllNodes(hasContentDescription("Back")).fetchSemanticsNodes().let { if (it.isNotEmpty()) compose.onNode(hasContentDescription("Back")).performClick() }
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Daily")).fetchSemanticsNodes().isNotEmpty() }
        assertTextFits("Play tab with Continue")

        compose.onNodeWithText("Daily").performClick()
        compose.waitForIdle()
        assertTextFits("Daily tab")
        compose.onNodeWithText("Stats").performClick()
        compose.waitForIdle()
        assertTextFits("Stats tab")

        // Completion: back into the game, fill all but one cell (debug menu), and let a hint place the last digit.
        compose.onNodeWithText("Play").performClick()
        compose.onNode(hasContentDescription("Continue Easy", substring = true)).scrollIfNeededAndClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("cell-80"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasContentDescription("More options")).performClick()
        compose.onNodeWithText("Debug: fill all but one cell").performClick()
        compose.onNodeWithTag("tool-hint", useUnmergedTree = true).scrollIfNeededAndClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("hint-primary"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("hint-primary", useUnmergedTree = true).scrollIfNeededAndClick()
        compose.waitForIdle()
        compose.onNodeWithTag("hint-primary", useUnmergedTree = true).scrollIfNeededAndClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Nice work.")).fetchSemanticsNodes().isNotEmpty() }
        assertTextFits("Completion")
    }

    /** Scrolls the node into view when it sits in a scrolling column (short screens at 200%), then taps it. */
    private fun androidx.compose.ui.test.SemanticsNodeInteraction.scrollIfNeededAndClick() {
        runCatching { performScrollTo() }
        performClick()
    }

    private fun assertTextFits(screen: String) {
        compose.waitForIdle()
        val root = compose.onNode(isRoot()).fetchSemanticsNode()
        val problems = mutableListOf<String>()
        fun visit(node: SemanticsNode, scrolling: Boolean) {
            val inScroll = scrolling || node.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange) != null ||
                node.config.getOrNull(SemanticsProperties.HorizontalScrollAxisRange) != null
            val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }
            if (text != null && text.isNotBlank()) {
                val layouts = mutableListOf<TextLayoutResult>()
                node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
                val layout = layouts.firstOrNull()
                // Height overflow covers maxLines cut-offs and fixed-height boxes; ellipsis is checked on its own. (Width
                // "overflow" is reported for every wrap-content Text by this API, so it isn't used.)
                if (layout != null && (layout.didOverflowHeight || (0 until layout.lineCount).any { layout.isLineEllipsized(it) })) {
                    problems += "'$text' is cut (${layout.lineCount} lines, ${layout.size}, needs ${layout.multiParagraph.height.toInt()} px)"
                }
                // Clipped by a parent: the visible bounds are smaller than the node, outside any scrolling container.
                val visible = node.boundsInRoot
                if (!inScroll && (visible.height < node.size.height - 1 || visible.width < node.size.width - 1)) {
                    problems += "'$text' is clipped (${node.size} visible ${visible.size})"
                }
            }
            node.children.forEach { visit(it, inScroll) }
        }
        compose.onAllNodes(isRoot(), useUnmergedTree = true).fetchSemanticsNodes().forEach { visit(it, false) }
        assertWithMessage("$screen at 200% on $qualifiers (root ${root.size})").that(problems).isEmpty()
    }
}
