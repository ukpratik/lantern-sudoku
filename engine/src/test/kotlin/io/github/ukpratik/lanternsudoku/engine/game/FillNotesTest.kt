// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.engine.game

import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.lanternsudoku.engine.game.Action.FillNotes
import io.github.ukpratik.lanternsudoku.engine.game.Action.TapCell
import io.github.ukpratik.lanternsudoku.engine.game.Action.TapDigit
import io.github.ukpratik.lanternsudoku.engine.game.Action.ToggleNotes
import io.github.ukpratik.lanternsudoku.engine.game.Action.Undo
import io.github.ukpratik.lanternsudoku.engine.game.Action.Redo
import io.github.ukpratik.lanternsudoku.engine.model.Houses
import io.github.ukpratik.lanternsudoku.engine.model.Masks
import org.junit.Test

/** US-009 Fill notes (TC-075, TC-077, TC-078), as the reducer does it. */
class FillNotesTest {
    private val fresh = Boards.fresh()

    /** The digits no given or entry among cell i's peers shows (TC-075's expectation, computed independently). */
    private fun expected(s: GameState, i: Int): Int {
        val seen = Houses.PEERS[i].map { s.value(it) }.filter { it != 0 }.toSet()
        return (1..9).filter { it !in seen }.fold(0) { m, d -> m or Masks.bit(d) }
    }

    @Test
    fun tc075_everyEmptyCellGetsTheCandidatesOfTheCurrentBoard_entriesUntouched() {
        val a = Boards.empties[0]
        val b = Boards.empties[10]
        // A right entry and a wrong one: the notes follow the player's board, not the solution.
        val s = fresh.withEntries(a to fresh.right(a), b to fresh.wrong(b)).withNotes(Boards.empties[20], 1)
        val t = s.act(FillNotes)
        for (i in 0 until 81) {
            if (s.value(i) != 0) {
                assertThat(t.state.code(i)).isEqualTo(s.code(i))
            } else {
                assertThat(t.state.notes(i)).isEqualTo(expected(s, i))
            }
        }
        assertThat(t.effects).containsExactly(Effect.Persist, Effect.Haptic(HapticKind.TICK))
        // The true candidates of every empty cell include its solution digit when no entry is wrong.
        val clean = fresh.then(FillNotes)
        for (i in Boards.empties) assertThat(clean.notes(i) and Masks.bit(clean.solution[i])).isNotEqualTo(0)
    }

    @Test
    fun tc077_undoRestoresAllNotesInOneStep_redoRefills() {
        val noted = fresh.withNotes(Boards.empties[3], 2, 7).withNotes(Boards.empties[4], 9)
        val filled = noted.then(FillNotes)
        assertThat(filled.undo.size).isEqualTo(noted.undo.size + 1)
        val undone = filled.then(Undo)
        assertThat(undone.codes()).isEqualTo(noted.codes())
        assertThat(undone.then(Redo).codes()).isEqualTo(filled.codes())
    }

    @Test
    fun tc078_filledNotesStayEditable() {
        val filled = fresh.then(FillNotes)
        val cell = Boards.empties[5]
        val d = Masks.lowestDigit(filled.notes(cell))
        val edited = filled.then(TapCell(cell)).then(ToggleNotes).then(TapDigit(d))
        assertThat(edited.notes(cell)).isEqualTo(filled.notes(cell) and Masks.bit(d).inv())
        for (i in Boards.empties.filter { it != cell }) assertThat(edited.notes(i)).isEqualTo(filled.notes(i))
    }

    @Test
    fun fillingTwiceRecordsNothing_andAHintOrPanelKeepsTheBoardInert() {
        val filled = fresh.then(FillNotes)
        val again = filled.act(FillNotes)
        assertThat(again.state).isEqualTo(filled)
        assertThat(again.effects).isEmpty()

        val hinting = fresh.then(Action.OpenHint)
        assertThat(hinting.act(FillNotes).state).isEqualTo(hinting)
        val almost = fresh.copy(status = Status.ALMOST)
        assertThat(almost.act(FillNotes).state).isEqualTo(almost)
    }

    @Test
    fun keepsSelectionLockAndModes() {
        val locked = fresh.then(TapDigit(5)).then(ToggleNotes)
        val t = locked.then(FillNotes)
        assertThat(t.input).isEqualTo(locked.input)
    }
}
