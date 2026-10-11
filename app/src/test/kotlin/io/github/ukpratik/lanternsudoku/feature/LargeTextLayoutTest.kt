// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.feature

import android.app.Activity
import android.content.Intent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.ukpratik.lanternsudoku.LanternApp
import io.github.ukpratik.lanternsudoku.MainActivity
import io.github.ukpratik.lanternsudoku.data.LoadResult
import io.github.ukpratik.lanternsudoku.data.SaveRequest
import io.github.ukpratik.lanternsudoku.engine.model.Level
import io.github.ukpratik.lanternsudoku.feature.game.SCROLL_GUTTER
import kotlinx.coroutines.runBlocking
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
 * US-021 / NFR-007 (TC-172, TC-173, TC-206) and the run-001 layout fixes (ISS-001 to ISS-004), on the compact 360×640
 * phone (3-button nav) at 100% and 200% system font scale and a regular 411×891 phone (gesture nav) at 200%. Native graphics, so the
 * bundled fonts are really measured.
 *
 * - Every screen's text fits: nothing is ellipsised, cut by its own bounds, broken in the middle of a word, clipped by a
 *   parent outside a scrolling container, or hidden in a scroll box too small to read (ISS-001's 16–56 dp box).
 * - The game opens with the whole board, the pad, the timer and Pause on screen, without scrolling (ISS-004c, D-036).
 * - Hint stages, Limit and Almost! show their title, explanation and buttons on screen while the board stays visible
 *   (ISS-001, ISS-002); the compact bar shows the mistake count (ISS-003).
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class LargeTextLayoutTest(private val qualifiers: String, private val fontScale: Float) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} at {1}x")
        fun screens() = listOf(
            // Robolectric draws no system bars, so the heights are what's left inside them: 360×640 with a 24 dp status
            // bar and a 48 dp 3-button bar (QA's E1), 411×891 with 24 dp gesture insets top and bottom.
            arrayOf<Any>("w360dp-h568dp-xhdpi", 1f),
            arrayOf<Any>("w360dp-h568dp-xhdpi", 2f),
            arrayOf<Any>("w411dp-h843dp-xxhdpi", 2f),
        )

        /** N-M-0000 with a wrong 7 at row 1, column 1 (ISS-001's set-up: an error-first hint). */
        const val WRONG_7 = "72438...158.17642..7.2.4..64.2917...9..548..2...623..47..4..23.24.831.67...76214."

        /** N-M-0000 full but for row 9, column 9, with row 1's 6 and 9 swapped (ISS-002's Almost! set-up; the last digit is 5). */
        const val ALMOST = "92438657158317642917925438646291785393754861285162379471649523824583196739876214."

        /** A scroll box must show at least this much of its text, or the text counts as hidden (ISS-001). */
        const val MIN_SCROLL_VIEWPORT_DP = 96f
    }

    private val compose = createAndroidComposeRule<MainActivity>()

    private val app get() = ApplicationProvider.getApplicationContext<LanternApp>()

    /** Screen size and font scale are set before the activity starts. */
    @get:Rule
    val rules: TestRule = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            RuntimeEnvironment.setQualifiers(qualifiers)
            RuntimeEnvironment.setFontScale(fontScale)
        }
    }).around(AppTeardown()).around(compose)

    private val where get() = "$qualifiers at ${fontScale}x"

    /**
     * OPEN (ISS-001/ISS-002 on 360×640, D-037): beside a board at its 270 dp floor, the smallest phone leaves a panel
     * about 230 dp at 100% text, while a long explanation, the Limit text or any hint at 200% needs 300–450 dp. There
     * the panel text scrolls (with a cue) above pinned buttons; showing it all needs a design decision. Until then these
     * cases only check what is guaranteed: the whole board, the panel's buttons, and a scrollable text box.
     */
    private val panelTextMayScroll get() = qualifiers.startsWith("w360dp")

    @Test
    fun everyScreenHolds() {
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("New game")).fetchSemanticsNodes().isNotEmpty() }
        assertTextFits("Play tab")

        compose.onNodeWithText("Easy").scrollIfNeededAndClick()
        waitForBoard()
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
        assertTextFits("Hint stage 1", panel = true)
        compose.onNodeWithTag("hint-primary", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertTextFits("Hint stage 2", panel = true)
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

        // Completion: back into the game, fill all but one cell (debug), and let a hint place the last digit.
        compose.onNodeWithText("Play").performClick()
        compose.onNode(hasContentDescription("Continue Easy", substring = true)).scrollIfNeededAndClick()
        waitForBoard()
        fillAllButOne()
        compose.onNodeWithTag("tool-hint", useUnmergedTree = true).scrollIfNeededAndClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("hint-primary"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("hint-primary", useUnmergedTree = true).scrollIfNeededAndClick()
        compose.waitForIdle()
        compose.onNodeWithTag("hint-primary", useUnmergedTree = true).scrollIfNeededAndClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Nice work.")).fetchSemanticsNodes().isNotEmpty() }
        assertTextFits("Completion")
    }

    /** ISS-004(c), D-036: the game opens with the board and the pad usable, timer and Pause visible, without scrolling. */
    @Test
    fun gameOpensWithBoardPadTimerAndPauseOnScreen() {
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("New game")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Easy").scrollIfNeededAndClick()
        waitForBoard()
        assertBoardOnScreen("Game idle")
        for (tag in (1..9).map { "key-$it" } + listOf("tool-undo", "tool-redo", "tool-erase", "tool-notes", "tool-hint")) {
            assertOnScreen(tag(tag), "Game idle: $tag")
        }
        assertOnScreen(compose.onNode(hasContentDescription("Pause")), "Game idle: Pause")
        assertOnScreen(compose.onNode(hasText("0:0", substring = true)), "Game idle: timer")
        assertTextFits("Game idle")
    }

    /** ISS-001: each hint stage shows its title and explanation, the buttons and the whole board, without scrolling. */
    @Test
    fun hintStagesShowTitleExplanationAndButtons() {
        openWithBoard(WRONG_7) // error-first: "Before the next step, something in the grid needs a look"
        assertHintReadable("Error-first stage 1")
        tag("hint-primary").performClick()
        compose.waitForIdle()
        assertHintReadable("Error-first stage 2")
        tag("hint-primary").performClick()
        compose.waitForIdle()
        assertHintReadable("Error-first stage 3 (removed)")
        tag("hint-primary").performClick() // Done
        compose.waitForIdle()

        // A logical hint on the corrected board: Where, Why, Do it.
        tag("tool-hint").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("hint-panel")).fetchSemanticsNodes().isNotEmpty() }
        assertHintReadable("Logical stage 1")
        tag("hint-primary").performClick()
        compose.waitForIdle()
        assertHintReadable("Logical stage 2")
        tag("hint-primary").performClick()
        compose.waitForIdle()
        assertHintReadable("Logical stage 3")
    }

    /** ISS-002 and ISS-003: the Limit and Almost! actions are on screen, and the compact bar counts mistakes. */
    @Test
    fun limitAndAlmostShowTheirActions_andTheBarCountsMistakes() {
        runBlocking { app.container.settingsRepository.setMistakeLimit(3) }
        openWithBoard(WRONG_7.replaceRange(0, 1, ".")) // no mistakes yet
        val counter = compose.onNode(hasText("Mistakes 0 of 3"), useUnmergedTree = true)
        assertOnScreen(counter, "Mistakes counter before")
        // Three wrong digits in row 1 (the solution there is 6 2 4 3 8 9 5 7 1): 1 at columns 1, 6 and 7.
        for ((i, cellIndex) in listOf(0, 5, 6).withIndex()) {
            tag("cell-$cellIndex").performClick()
            tag("key-1").performClick()
            compose.waitForIdle()
            if (i < 2) assertOnScreen(compose.onNode(hasText("Mistakes ${i + 1} of 3"), useUnmergedTree = true), "Mistakes counter after ${i + 1}")
        }
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("panel-primary"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        assertPanelReadable("Limit reached")
        tag("panel-primary").performClick() // Keep playing
        compose.waitForIdle()

        runBlocking { app.container.settingsRepository.setShowMistakes(false) }
        openWithBoard(ALMOST)
        tag("cell-80").performClick()
        tag("key-5").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Almost!", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        assertPanelReadable("Almost!")
    }

    /** ISS-004(a, b): with games played, Stats tiles and headings never break inside a word; the In progress badge fits. */
    @Test
    fun statsAndLevelBadgesHoldWithGamesPlayed() {
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("New game")).fetchSemanticsNodes().isNotEmpty() }
        val repo = app.container.gameRepository
        runBlocking {
            for ((i, level) in Level.entries.withIndex()) {
                val id = repo.startNew(level)
                val state = (repo.load(id) as LoadResult.Ok).state
                repo.write(SaveRequest.Complete(id, state, 600_000L + i * 61_000L))
            }
            repo.startNew(Level.MEDIUM) // in progress: the badge on the Medium row
        }
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("In progress")).fetchSemanticsNodes().isNotEmpty() }
        assertTextFits("Play tab with an In progress badge")
        compose.onNodeWithText("Stats").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("By level")).fetchSemanticsNodes().isNotEmpty() }
        assertTextFits("Stats with games")
        compose.onNodeWithText("By level").performScrollTo()
        assertTextFits("Stats by level")
    }

    // ---- helpers ----

    private fun tag(t: String) = compose.onNodeWithTag(t, useUnmergedTree = true)

    private fun waitForBoard() =
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("cell-80"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }

    /** The `dbg_load` + `dbg_board` QA hook on the running activity (BL-016), as QA used in run-001. */
    private fun openWithBoard(board: String) {
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("New game")).fetchSemanticsNodes().isNotEmpty() ||
            compose.onAllNodes(hasTestTag("cell-80"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        val intent = Intent().putExtra("dbg_load", "N-M-0000").putExtra("dbg_board", board)
        compose.runOnUiThread {
            Activity::class.java.getDeclaredMethod("onNewIntent", Intent::class.java).apply { isAccessible = true }.invoke(compose.activity, intent)
        }
        // The new game is on screen once row 1, column 1 shows what the board says.
        val first = if (board[0] == '.') "empty" else "entry ${board[0]}"
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasContentDescription("Row 1, column 1, $first", substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() &&
                compose.onAllNodes(hasText("Medium"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
        if (board == WRONG_7) {
            tag("tool-hint").performClick()
            compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("hint-panel")).fetchSemanticsNodes().isNotEmpty() }
            compose.waitForIdle()
        }
    }

    /** Debug "fill all but one cell": in the ⋮ menu, or on the Pause screen where there's no ⋮ (compact + large text). */
    private fun fillAllButOne() {
        if (compose.onAllNodes(hasContentDescription("More options")).fetchSemanticsNodes().isNotEmpty()) {
            compose.onNode(hasContentDescription("More options")).performClick()
            compose.onNodeWithText("Debug: fill all but one cell").performClick()
        } else {
            compose.onNode(hasContentDescription("Pause")).performClick()
            compose.onNodeWithText("Debug: fill all but one cell").scrollIfNeededAndClick()
        }
        compose.waitForIdle()
    }

    /** Scrolls the node into view when it sits in a scrolling column, then taps it. */
    private fun SemanticsNodeInteraction.scrollIfNeededAndClick() {
        runCatching { performScrollTo() }
        performClick()
    }

    private fun rootHeight() = compose.onNode(isRoot()).fetchSemanticsNode().size.height

    /** Fully visible: nothing cut by a parent or a scroll window, and inside the window. No scrolling is done. */
    private fun assertOnScreen(node: SemanticsNodeInteraction, what: String) {
        val n = node.fetchSemanticsNode()
        val visible = n.boundsInRoot
        assertWithMessage("$what is cut or off screen on $where (size ${n.size}, visible $visible, root ${rootHeight()})")
            .that(visible.height >= n.size.height - 1 && visible.width >= n.size.width - 1 && visible.bottom <= rootHeight() + 1 && visible.top >= -1)
            .isTrue()
    }

    private fun assertBoardOnScreen(what: String) {
        assertOnScreen(tag("cell-0"), "$what: board top left")
        assertOnScreen(tag("cell-80"), "$what: board bottom right")
        val cell = tag("cell-0").fetchSemanticsNode()
        assertWithMessage("$what: cell size on $where").that(cell.size.width / compose.density.density).isAtLeast(29.9f)
    }

    private val isHeading = SemanticsMatcher("is a heading") { it.config.getOrNull(SemanticsProperties.Heading) != null }

    private fun assertHintReadable(what: String) {
        compose.waitForIdle()
        assertBoardOnScreen(what)
        // The title (a heading inside the hint text) and the explanation (its text is read with the title's announcement).
        if (!panelTextMayScroll) assertOnScreen(compose.onNode(isHeading and hasAnyAncestor(hasTestTag("hint-text")), useUnmergedTree = true), "$what: title")
        if (!panelTextMayScroll) {
            assertOnScreen(tag("hint-body"), "$what: explanation")
        } else {
            assertWithMessage("$what: hint text scrolls").that(tag("hint-text").fetchSemanticsNode().config
                .getOrNull(SemanticsProperties.VerticalScrollAxisRange)).isNotNull()
        }
        assertOnScreen(tag("hint-primary"), "$what: primary button")
        assertOnScreen(tag("hint-secondary"), "$what: secondary button")
        assertTextFits(what, panel = true)
        assertTextClearsTheScrollThumb("hint-text", what)
    }

    private fun assertPanelReadable(what: String) {
        assertBoardOnScreen(what)
        if (!panelTextMayScroll) assertOnScreen(compose.onNode(isHeading and hasAnyAncestor(hasTestTag("panel-text")), useUnmergedTree = true), "$what: title")
        assertOnScreen(tag("panel-primary"), "$what: primary button")
        assertOnScreen(tag("panel-secondary"), "$what: secondary button")
        assertTextFits(what, panel = true)
        assertTextClearsTheScrollThumb("panel-text", what)
    }

    /**
     * ISS-007: the scroll thumb runs in the last [SCROLL_GUTTER] of the scrolling text area; no text node (title, chip,
     * explanation, legend, meta line, body, fine print) reaches into it, so no letter is drawn under the thumb.
     */
    private fun assertTextClearsTheScrollThumb(scrollTag: String, what: String) {
        val box = tag(scrollTag).fetchSemanticsNode()
        val thumbLeft = box.boundsInRoot.right - with(compose.density) { SCROLL_GUTTER.toPx() }
        val texts = compose.onAllNodes(hasAnyAncestor(hasTestTag(scrollTag)) and hasText("", substring = true), useUnmergedTree = true)
            .fetchSemanticsNodes() + compose.onAllNodes(hasAnyAncestor(hasTestTag(scrollTag)) and hasTestTag("hint-body"), useUnmergedTree = true)
            .fetchSemanticsNodes()
        assertWithMessage("$what: text nodes in $scrollTag").that(texts).isNotEmpty()
        for (n in texts) {
            // Unclipped right edge: where the node is laid out, not just its visible part.
            val right = n.positionInRoot.x + n.size.width
            assertWithMessage("$what on $where: '${n.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }}' ends at $right px, " +
                "the scroll thumb's gutter starts at $thumbLeft px").that(right).isAtMost(thumbLeft + 0.5f)
        }
    }

    private fun hasAnyAncestor(m: SemanticsMatcher) = androidx.compose.ui.test.hasAnyAncestor(m)

    /** [panel]: a hint/Almost!/Limit screen, where the small-scroll-box rule waits on the 360×640 decision ([panelTextMayScroll]). */
    private fun assertTextFits(screen: String, panel: Boolean = false) {
        compose.waitForIdle()
        val root = compose.onNode(isRoot()).fetchSemanticsNode()
        val density = compose.density.density
        val problems = mutableListOf<String>()
        fun visit(node: SemanticsNode, scrollBox: SemanticsNode?) {
            val vertical = node.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange) != null
            val isScroll = vertical || node.config.getOrNull(SemanticsProperties.HorizontalScrollAxisRange) != null
            val box = if (isScroll) node else scrollBox
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
                // A line break inside a word ("Compl/eted", "leve/l"): the box is too narrow for the word (ISS-004a).
                if (layout != null) {
                    val laid = layout.layoutInput.text.text
                    for (line in 0 until layout.lineCount - 1) {
                        val end = layout.getLineEnd(line)
                        if (end in 1 until laid.length && laid[end - 1].isLetterOrDigit() && laid[end].isLetterOrDigit()) {
                            problems += "'$text' breaks inside a word at '${laid.substring(maxOf(0, end - 6), minOf(laid.length, end + 4))}'"
                        }
                    }
                }
                val visible = node.boundsInRoot
                val cut = visible.height < node.size.height - 1 || visible.width < node.size.width - 1
                if (cut && box == null) {
                    // Clipped by a parent: the visible bounds are smaller than the node, outside any scrolling container.
                    problems += "'$text' is clipped (${node.size} visible ${visible.size})"
                } else if (cut && box != null && box.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange) != null &&
                    visible.height < node.size.height - 1 && box.size.height / density < MIN_SCROLL_VIEWPORT_DP && !(panel && panelTextMayScroll)
                ) {
                    // Text hidden in a scroll box too small to read in (ISS-001: a 16–56 dp window with no cue).
                    problems += "'$text' is hidden in a ${(box.size.height / density).toInt()} dp scroll box"
                }
            }
            node.children.forEach { visit(it, box) }
        }
        compose.onAllNodes(isRoot(), useUnmergedTree = true).fetchSemanticsNodes().forEach { visit(it, null) }
        assertWithMessage("$screen on $where (root ${root.size})").that(problems).isEmpty()
    }
}
