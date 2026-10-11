// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.feature

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import com.google.common.truth.Truth.assertWithMessage
import io.github.ukpratik.lanternsudoku.data.CompletionSummary
import io.github.ukpratik.lanternsudoku.data.DailyStats
import io.github.ukpratik.lanternsudoku.data.ShareFacts
import io.github.ukpratik.lanternsudoku.engine.game.GameState
import io.github.ukpratik.lanternsudoku.engine.model.Grid
import io.github.ukpratik.lanternsudoku.engine.model.Level
import io.github.ukpratik.lanternsudoku.feature.game.CompletionContent
import io.github.ukpratik.lanternsudoku.ui.theme.LanternTheme
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
import java.time.LocalDate
import kotlin.math.hypot
import kotlin.math.min

/**
 * ISS-008 (US-018, US-021): the completion actions, for every level, regular and daily, on the screens of
 * [LargeTextLayoutTest]. Each label takes at most two lines and keeps clear of its button's rounded outline: every
 * corner of the label sits at least [MIN_INSET_DP] inside the stadium shape. Native graphics, so the fonts are measured.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class CompletionActionsLayoutTest(private val qualifiers: String, private val fontScale: Float) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} at {1}x")
        fun screens() = listOf(
            arrayOf<Any>("w360dp-h568dp-xhdpi", 1f),
            arrayOf<Any>("w360dp-h568dp-xhdpi", 2f),
            arrayOf<Any>("w411dp-h843dp-xxhdpi", 2f),
        )

        const val MIN_INSET_DP = 8f
        const val SOLVED = "624389571583176429179254386462917853937548612851623794716495238245831967398762145"
    }

    private val compose = createComposeRule()

    @get:Rule
    val rules: TestRule = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            RuntimeEnvironment.setQualifiers(qualifiers)
            RuntimeEnvironment.setFontScale(fontScale)
        }
    }).around(compose)

    private fun summary(level: Level, daily: Boolean): CompletionSummary {
        val date = LocalDate.of(2026, 10, 15)
        return CompletionSummary(
            gameId = 1, level = level, elapsedMs = 1_266_000, hints1 = 1, hints2 = 0, hints3 = 1, mistakes = 1, clean = false,
            previousBestMs = 900_000, newBest = false,
            dailyDate = if (daily) date else null,
            daily = if (daily) DailyStats(current = 12, best = 30, daysPlayed = 40) else null,
            share = if (daily) ShareFacts(date, level, 1_266_000, 2, 1, 0) else null,
        )
    }

    @Test
    fun actionsFitTheirButtons_everyLevel_regularAndDaily() {
        val cases = Level.entries.flatMap { listOf(it to false, it to true) }
        var case by mutableStateOf(cases.first())
        val grid = GameState.gridSummary(GameState.new("N-M-0000", Grid.parse(SOLVED)))
        compose.setContent { Screen(grid, summary(case.first, case.second)) }
        val problems = mutableListOf<String>()
        for (c in cases) {
            compose.runOnIdle { case = c }
            compose.waitForIdle()
            for (tag in listOf("completion-new-game", "completion-home")) problems += check(tag, "${c.first} ${if (c.second) "daily" else "regular"}")
        }
        assertWithMessage("$qualifiers at ${fontScale}x").that(problems).isEmpty()
    }

    @Composable
    private fun Screen(grid: String, s: CompletionSummary) = LanternTheme {
        CompletionContent(grid, s, onClose = {}, onNewGame = {}, onShare = {}, modifier = Modifier.fillMaxSize())
    }

    private fun check(tag: String, what: String): List<String> {
        val density = compose.density.density
        val button = compose.onNode(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNode()
        val label = compose.onNode(hasAnyAncestor(hasTestTag(tag)) and hasText("", substring = true), useUnmergedTree = true).fetchSemanticsNode()
        val text = label.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }
        val layouts = mutableListOf<TextLayoutResult>()
        label.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
        val lines = layouts.firstOrNull()?.lineCount ?: 0
        val out = mutableListOf<String>()
        if (lines > 2) out += "$what: '$text' takes $lines lines"
        val inset = stadiumInset(button.boundsInRoot, label.boundsInRoot) / density
        if (inset < MIN_INSET_DP) out += "$what: '$text' is ${"%.1f".format(inset)} dp from its outline (button ${button.boundsInRoot}, label ${label.boundsInRoot})"
        return out
    }

    /** The smallest distance from a corner of [inner] to the outline of a fully rounded (stadium) [outer]; negative outside. */
    private fun stadiumInset(outer: Rect, inner: Rect): Float {
        val r = min(outer.width, outer.height) / 2
        return listOf(inner.topLeft, inner.topRight, inner.bottomLeft, inner.bottomRight).minOf { p ->
            val cx = p.x.coerceIn(outer.left + r, outer.right - r)
            val cy = p.y.coerceIn(outer.top + r, outer.bottom - r)
            r - hypot(p.x - cx, p.y - cy)
        }
    }
}
