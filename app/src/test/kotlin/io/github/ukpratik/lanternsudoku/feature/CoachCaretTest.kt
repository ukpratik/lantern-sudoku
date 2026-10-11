// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.feature

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
 * ISS-014 (US-024, design coach-mark "caret to the target"): each coach tip's caret points at its own control. Tip 1 at
 * the number pad (its column over the 5 key), tip 2 at Notes and tip 3 at Hint, not at Undo / Redo above them. Runs the
 * compact 2-column tool grid (360×640 at 100% and 200%) and the regular tool column (411×891 at 100%).
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class CoachCaretTest(private val qualifiers: String, private val fontScale: Float) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} at {1}x")
        fun screens() = listOf(
            // Heights inside the system bars, as in LargeTextLayoutTest.
            arrayOf<Any>("w360dp-h568dp-xhdpi", 1f),
            arrayOf<Any>("w360dp-h568dp-xhdpi", 2f),
            arrayOf<Any>("w411dp-h843dp-xxhdpi", 1f),
        )
    }

    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: TestRule = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            RuntimeEnvironment.setQualifiers(qualifiers)
            RuntimeEnvironment.setFontScale(fontScale)
        }
    }).around(AppTeardown()).around(compose)

    @Test
    fun eachCaretPointsAtItsOwnControl() {
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("New game")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Easy").apply { runCatching { performScrollTo() } }.performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("coach-mark"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        // Tip 1's caret sits on the pad's top edge (D-046), so only its x is on the key (ISS-015, deferred, is the row).
        assertCaretOn("Tip 1 (lock)", "key-5", checkY = false)
        tag("coach-next").performClick()
        assertCaretOn("Tip 2 (notes)", "tool-notes", checkY = true)
        tag("coach-next").performClick()
        assertCaretOn("Tip 3 (hint)", "tool-hint", checkY = true)
    }

    private fun assertCaretOn(what: String, target: String, checkY: Boolean) {
        compose.waitForIdle()
        val point = tag("coach-caret").fetchSemanticsNode().boundsInRoot.center
        val b = tag(target).fetchSemanticsNode().boundsInRoot
        val where = "$qualifiers at ${fontScale}x"
        assertWithMessage("$what: caret x ${point.x} is outside $target [${b.left}, ${b.right}] on $where")
            .that(point.x in b.left..b.right).isTrue()
        if (checkY) {
            assertWithMessage("$what: caret y ${point.y} is outside $target [${b.top}, ${b.bottom}] on $where")
                .that(point.y in b.top..b.bottom).isTrue()
        }
    }

    private fun tag(t: String) = compose.onNodeWithTag(t, useUnmergedTree = true)
}
