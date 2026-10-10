// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.feature

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.lanternsudoku.engine.game.Action
import io.github.ukpratik.lanternsudoku.engine.game.GameReducer
import io.github.ukpratik.lanternsudoku.engine.game.GameState
import io.github.ukpratik.lanternsudoku.engine.game.Rules
import io.github.ukpratik.lanternsudoku.engine.model.Grid
import io.github.ukpratik.lanternsudoku.feature.game.BoardChange
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-022 / TC-176: the polite live-region text after each move (design game-cell-selected, -notes-mode, -digit-locked). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class BoardChangeTest {
    private val res = ApplicationProvider.getApplicationContext<Application>().resources
    private val rules = Rules(showMistakes = true, mistakeLimit = 0, autoCleanup = true)
    private val givens = ".47...9..91..7.2..6.8..3..4.897....617.624.894....957.7..5..1.2..4.6..97..1...65."
    private val start = GameState.new("N-E-0000", Grid.parse(givens))
    private var seq = 0

    /** Applies [actions] and describes what the last one did. */
    private fun said(from: GameState, vararg actions: Action): Pair<GameState, String?> {
        var s = from
        var text: String? = null
        for (a in actions) {
            val t = GameReducer.reduce(s, a, rules)
            text = BoardChange.of(++seq, s, t.state, a, t.effects)?.describe(res)
            s = t.state
        }
        return s to text
    }

    @Test
    fun placingAndErasing() {
        // Row 1, column 1 is empty in N-E-0000.
        val (placed, text) = said(start, Action.TapCell(0), Action.TapDigit(5))
        assertThat(text).isEqualTo("Placed 5, row 1, column 1")
        assertThat(said(placed, Action.TapErase).second).isEqualTo("Erased row 1, column 1")
        assertThat(said(placed, Action.Undo).second).isEqualTo("Erased row 1, column 1")
    }

    @Test
    fun selectingAlone_isNotAnnounced() {
        assertThat(said(start, Action.TapCell(0)).second).isNull()
        assertThat(said(start, Action.ToggleNotes).second).isNull()
    }

    @Test
    fun notes_addedAndRemoved() {
        val (added, text) = said(start, Action.ToggleNotes, Action.TapCell(3), Action.TapDigit(9))
        assertThat(text).isEqualTo("Note 9 added, row 1, column 4")
        assertThat(said(added, Action.TapDigit(9)).second).isEqualTo("Note 9 removed, row 1, column 4")
    }

    @Test
    fun blockedTapWhileLocked_saysWhatIsThere() {
        // Lock 5, then tap the given 4 at row 1, column 2.
        val (_, text) = said(start, Action.LongPressDigit(5), Action.TapCell(1))
        assertThat(text).isEqualTo("Row 1, column 2 has 4. 5 is locked.")
    }

    @Test
    fun movesInsideAnOpenHint_areLeftToTheHintPanel() {
        val (open, _) = said(start, Action.OpenHint)
        assertThat(said(open, Action.HintNext, Action.HintNext).second).isNull()
    }
}
