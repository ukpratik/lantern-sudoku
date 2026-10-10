// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.engine.game

import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.lanternsudoku.engine.game.Action.Back
import io.github.ukpratik.lanternsudoku.engine.game.Action.LongPressDigit
import io.github.ukpratik.lanternsudoku.engine.game.Action.Redo
import io.github.ukpratik.lanternsudoku.engine.game.Action.TapCell
import io.github.ukpratik.lanternsudoku.engine.game.Action.TapDigit
import io.github.ukpratik.lanternsudoku.engine.game.Action.TapErase
import io.github.ukpratik.lanternsudoku.engine.game.Action.ToggleNotes
import io.github.ukpratik.lanternsudoku.engine.game.Action.Undo
import io.github.ukpratik.lanternsudoku.engine.model.Houses
import io.github.ukpratik.lanternsudoku.engine.model.Masks
import org.junit.Test

/**
 * The US-004/US-005 state table (PRD v1.3, LLD §4.7), one test per row, named with the row and the QA case it automates.
 * Rows 10a/10b are the v1.3 completed-key rows (D-028).
 */
class GameReducerTest {
    private val fresh = Boards.fresh()
    private val a = Boards.empties[0]
    private val b = Boards.empties[1]
    private val c = Boards.empties[2]
    private val given = Boards.aGiven

    private val doubleTick = Effect.Haptic(HapticKind.DOUBLE_TICK)

    /** A digit with all 9 copies on the board (placed as entries), and the state that has it. */
    private val completeDigit = 3
    private val withComplete: GameState = fresh.withEntries(*fresh.emptiesFor(completeDigit).map { it to completeDigit }.toTypedArray())

    // ---- US-005 table ---------------------------------------------------------------------------------------------

    @Test
    fun row01_TC041_noSelectionNoLock_tapIncompleteDigit_locksIt() {
        val t = fresh.act(TapDigit(5))
        assertThat(t.state.input.lock).isEqualTo(5)
        assertThat(t.state.input.selection).isNull()
        assertThat(t.effects).contains(Effect.Haptic(HapticKind.TICK))
        assertThat(Highlights.of(t.state, showMistakes = true).digit).isEqualTo(5)
    }

    @Test
    fun row02_TC042_longPressDigit_locksAndClearsSelection() {
        val t = fresh.then(TapCell(a)).act(LongPressDigit(5))
        assertThat(t.state.input.lock).isEqualTo(5)
        assertThat(t.state.input.selection).isNull()
        assertThat(t.effects).containsExactly(Effect.Haptic(HapticKind.LONG_PRESS))
    }

    @Test
    fun row03_TC043_longPressLockedDigit_isNoOp() {
        val locked = fresh.then(TapDigit(5))
        val t = locked.act(LongPressDigit(5))
        assertThat(t.state).isEqualTo(locked)
        assertThat(t.effects).isEmpty()
    }

    @Test
    fun row04_TC044_locked_tapEmptyCell_placesDigit() {
        val t = fresh.then(TapDigit(5)).act(TapCell(a))
        assertThat(t.state.value(a)).isEqualTo(5)
        assertThat(t.state.input.lock).isEqualTo(5)
        assertThat(t.state.undo).hasSize(1)
        assertThat(t.effects).contains(Effect.Persist)
    }

    @Test
    fun row04_TC045_lockedInNotesMode_tapEmptyCell_togglesNote() {
        val start = fresh.withNotes(a, 1).then(TapDigit(5)).then(ToggleNotes)
        val once = start.then(TapCell(a))
        assertThat(Masks.digits(once.notes(a)).toList()).containsExactly(1, 5).inOrder()
        assertThat(once.value(a)).isEqualTo(0)
        val twice = once.then(TapCell(a))
        assertThat(Masks.digits(twice.notes(a)).toList()).containsExactly(1)
    }

    @Test
    fun row05_TC046_locked_tapEntryEqualToLock_removesIt_undoable() {
        val start = fresh.withEntries(a to 5).then(TapDigit(5))
        val removed = start.then(TapCell(a))
        assertThat(removed.value(a)).isEqualTo(0)
        assertThat(removed.then(Undo).value(a)).isEqualTo(5)
    }

    @Test
    fun row06_TC047_locked_tapDifferentEntry_neverOverwrites() {
        val start = fresh.withEntries(a to 7).then(TapDigit(5))
        val t = start.act(TapCell(a))
        assertThat(t.state.value(a)).isEqualTo(7)
        assertThat(t.state.input.lock).isEqualTo(5)
        assertThat(t.state.input.selection).isNull()
        assertThat(t.state.undo).isEmpty()
        assertThat(t.effects).containsExactly(doubleTick)
        assertThat(Highlights.of(t.state, true).digit).isEqualTo(5)
    }

    @Test
    fun row06_TC048_locked_tapGiven_neverOverwrites() {
        val start = fresh.then(TapDigit(5))
        val t = start.act(TapCell(given))
        assertThat(t.state).isEqualTo(start)
        assertThat(t.effects).containsExactly(doubleTick)
    }

    @Test
    fun row07_TC049_locked_eraseModeClearsEntries_andTogglesOff() {
        val start = fresh.withEntries(a to 7, b to 5).then(TapDigit(5))
        val erasing = start.then(TapErase)
        assertThat(erasing.input.eraseMode).isTrue()
        val clearedA = erasing.then(TapCell(a))
        assertThat(clearedA.value(a)).isEqualTo(0)
        val g = clearedA.act(TapCell(given))
        assertThat(g.state.value(given)).isEqualTo(fresh.value(given))
        assertThat(g.effects).containsExactly(doubleTick)
        val off = g.state.then(TapErase)
        assertThat(off.input.eraseMode).isFalse()
        assertThat(off.then(TapCell(c)).value(c)).isEqualTo(5)
    }

    @Test
    fun row07_eraseModeClearsNotes() {
        val s = fresh.withNotes(a, 2, 4).then(TapDigit(5)).then(TapErase).then(TapCell(a))
        assertThat(s.notes(a)).isEqualTo(0)
    }

    @Test
    fun row08_TC050_tapLockedKey_releasesLock() {
        assertThat(fresh.then(TapDigit(5)).then(TapDigit(5)).input.lock).isNull()
    }

    @Test
    fun row08_TC051_back_leavesEraseModeFirst_thenReleasesLock_thenIsUnhandled() {
        val s = fresh.then(TapDigit(5)).then(TapErase)
        val one = s.act(Back)
        assertThat(one.state.input.eraseMode).isFalse()
        assertThat(one.state.input.lock).isEqualTo(5)
        val two = one.state.act(Back)
        assertThat(two.state.input.lock).isNull()
        assertThat(two.handled).isTrue()
        assertThat(two.state.act(Back).handled).isFalse()
    }

    @Test
    fun row09_TC052_tapAnotherIncompleteDigit_switchesLock() {
        assertThat(fresh.then(TapDigit(5)).then(TapDigit(8)).input.lock).isEqualTo(8)
    }

    @Test
    fun row10a_TC053_completedKey_withLock_highlightsOnly_andReleasesLock() {
        val s = withComplete.then(TapDigit(5))
        val t = s.act(TapDigit(completeDigit))
        assertThat(t.state.input.lock).isNull()
        assertThat(t.state.input.highlight).isEqualTo(completeDigit)
        assertThat(t.state.codes()).isEqualTo(s.codes())
        assertThat(Highlights.of(t.state, true).digit).isEqualTo(completeDigit)
    }

    @Test
    fun row10a_completedKey_noSelectionNoLock_highlightsOnly_noLock() {
        val t = withComplete.act(TapDigit(completeDigit))
        assertThat(t.state.input.lock).isNull()
        assertThat(t.state.input.highlight).isEqualTo(completeDigit)
        // Tapping it again clears the highlight; tapping an incomplete key locks as usual (row 1).
        assertThat(t.state.then(TapDigit(completeDigit)).input.highlight).isNull()
        val locked = t.state.then(TapDigit(5))
        assertThat(locked.input.lock).isEqualTo(5)
        assertThat(locked.input.highlight).isNull()
    }

    @Test
    fun row10b_v13_completedKey_withEditableCellSelected_placesTheDigit_asConflict() {
        val target = Boards.empties.first { withComplete.solution[it] != completeDigit }
        val s = withComplete.then(TapCell(target))
        val t = s.act(TapDigit(completeDigit))
        assertThat(t.state.value(target)).isEqualTo(completeDigit)
        assertThat(t.state.input.selection).isEqualTo(target)
        assertThat(t.state.input.lock).isNull()
        assertThat(Highlights.of(t.state, true).has(target, BoardHighlights.CONFLICT)).isTrue()
        assertThat(t.state.remaining(completeDigit)).isEqualTo(0)
    }

    @Test
    fun row11_TC035_selectedEmptyCell_tapDigit_places_keepsSelection() {
        val t = fresh.then(TapCell(a)).act(TapDigit(4))
        assertThat(t.state.value(a)).isEqualTo(4)
        assertThat(t.state.input.selection).isEqualTo(a)
        assertThat(t.state.undo).hasSize(1)
    }

    @Test
    fun row11_TC036_selectedCellWithEntry_tapAnotherDigit_replaces_undoRestores() {
        val s = fresh.withEntries(a to 7).then(TapCell(a)).then(TapDigit(4))
        assertThat(s.value(a)).isEqualTo(4)
        assertThat(s.then(Undo).value(a)).isEqualTo(7)
    }

    @Test
    fun row11_selectedCell_tapSameDigit_isNoOp() {
        val s = fresh.withEntries(a to 7).then(TapCell(a))
        val t = s.act(TapDigit(7))
        assertThat(t.state).isEqualTo(s)
    }

    @Test
    fun row12_TC038_tapSelectedCellAgain_orBack_deselects() {
        val sel = fresh.then(TapCell(a))
        assertThat(sel.then(TapCell(a)).input.selection).isNull()
        val back = sel.act(Back)
        assertThat(back.state.input.selection).isNull()
        assertThat(back.handled).isTrue()
    }

    @Test
    fun row13_TC034_tapEditableCell_selectsIt() {
        val s = fresh.then(TapCell(a))
        assertThat(s.input.selection).isEqualTo(a)
        assertThat(s.input.lock).isNull()
        assertThat(fresh.then(TapCell(a)).then(TapCell(b)).input.selection).isEqualTo(b)
    }

    @Test
    fun row13_TC037_givenSelected_highlightOnly_digitsIgnored() {
        val s = fresh.then(TapCell(given))
        assertThat(s.input.selection).isEqualTo(given)
        val t = s.act(TapDigit(4))
        assertThat(t.state).isEqualTo(s)
        assertThat(Highlights.of(s, true).digit).isEqualTo(fresh.value(given))
    }

    @Test
    fun row14_TC039_erase_clearsEntry_clearsNotes_noOpOnGiven() {
        val entry = fresh.withEntries(a to 6).then(TapCell(a)).then(TapErase)
        assertThat(entry.value(a)).isEqualTo(0)
        val notes = fresh.withNotes(a, 1, 2).then(TapCell(a)).then(TapErase)
        assertThat(notes.notes(a)).isEqualTo(0)
        val g = fresh.then(TapCell(given))
        val t = g.act(TapErase)
        assertThat(t.state).isEqualTo(g)
        assertThat(t.state.undo).isEmpty()
    }

    @Test
    fun row15_TC054_lockAutoReleasesWhenNinthCopyPlaced() {
        val d = 5
        val spots = fresh.emptiesFor(d)
        val eight = fresh.withEntries(*spots.dropLast(1).map { it to d }.toTypedArray())
        val t = eight.then(TapDigit(d)).act(TapCell(spots.last()))
        assertThat(t.state.input.lock).isNull()
        assertThat(t.state.remaining(d)).isEqualTo(0)
        assertThat(t.state.isComplete(d)).isTrue()
    }

    @Test
    fun row16_noSelectionNoLock_erase_isNoOpWithDoubleTick() {
        val t = fresh.act(TapErase)
        assertThat(t.state).isEqualTo(fresh)
        assertThat(t.effects).containsExactly(doubleTick)
    }

    @Test
    fun invariant_selectingACellReleasesNothingButTappingDigitWithSelectionNeverLocks() {
        val s = fresh.then(TapCell(a)).then(TapDigit(4))
        assertThat(s.input.lock).isNull()
        // Long-press with a selection: the lock takes over and the selection clears (mutually exclusive).
        val l = s.then(LongPressDigit(6))
        assertThat(l.input.selection).isNull()
        assertThat(l.input.lock).isEqualTo(6)
    }

    @Test
    fun back_withNothingActive_isUnhandled() {
        assertThat(fresh.act(Back).handled).isFalse()
    }

    // ---- US-006 notes ----------------------------------------------------------------------------------------------

    @Test
    fun TC059_notesMode_digitAddsThenRemovesCandidate() {
        val s = fresh.then(ToggleNotes).then(TapCell(a))
        val once = s.then(TapDigit(2))
        assertThat(once.notes(a)).isEqualTo(Masks.bit(2))
        assertThat(once.value(a)).isEqualTo(0)
        assertThat(once.then(TapDigit(2)).notes(a)).isEqualTo(0)
    }

    @Test
    fun TC060_TC061_autoCleanupRemovesFromPeers_andUndoRestores() {
        val target = a
        val d = 9
        val rowPeer = Houses.PEERS[target].first { Houses.row(it) == Houses.row(target) && !fresh.isGiven(it) }
        val colPeer = Houses.PEERS[target].first { Houses.col(it) == Houses.col(target) && !fresh.isGiven(it) }
        val boxPeer = Houses.PEERS[target].first {
            Houses.box(it) == Houses.box(target) && Houses.row(it) != Houses.row(target) && Houses.col(it) != Houses.col(target) &&
                !fresh.isGiven(it)
        }
        val unrelated = Boards.empties.first { it != target && it !in Houses.PEERS[target] }
        var s = fresh
        for (cell in listOf(rowPeer, colPeer, boxPeer, unrelated)) s = s.withNotes(cell, 1, d)
        val placed = s.then(TapCell(target)).then(TapDigit(d))
        for (cell in listOf(rowPeer, colPeer, boxPeer)) assertThat(placed.notes(cell)).isEqualTo(Masks.bit(1))
        assertThat(placed.notes(unrelated)).isEqualTo(Masks.bit(1) or Masks.bit(d))
        assertThat(placed.undo).hasSize(1) // one step covers the placement and the clean-up
        val undone = placed.then(Undo)
        for (cell in listOf(rowPeer, colPeer, boxPeer, unrelated)) assertThat(undone.notes(cell)).isEqualTo(Masks.bit(1) or Masks.bit(d))
        assertThat(undone.value(target)).isEqualTo(0)
    }

    @Test
    fun autoCleanupOff_TC184_keepsPeerNotes() {
        val peer = Houses.PEERS[a].first { !fresh.isGiven(it) }
        val s = fresh.withNotes(peer, 9).then(TapCell(a)).then(TapDigit(9), Rules(autoCleanup = false))
        assertThat(s.notes(peer)).isEqualTo(Masks.bit(9))
    }

    @Test
    fun TC062_placingInCellWithNotesHidesThem_undoRestores() {
        val s = fresh.withNotes(a, 1, 4, 7).then(TapCell(a)).then(TapDigit(4))
        assertThat(s.notes(a)).isEqualTo(0)
        assertThat(s.value(a)).isEqualTo(4)
        val undone = s.then(Undo)
        assertThat(Masks.digits(undone.notes(a)).toList()).containsExactly(1, 4, 7).inOrder()
    }

    @Test
    fun notesNeverGoInFilledCells() {
        val s = fresh.withEntries(a to 6).then(ToggleNotes).then(TapCell(a))
        val t = s.act(TapDigit(2))
        assertThat(t.state.notes(a)).isEqualTo(0)
        assertThat(t.effects).containsExactly(doubleTick)
    }

    // ---- US-007 undo, redo, erase ----------------------------------------------------------------------------------

    @Test
    fun TC065_newMoveAfterUndo_clearsRedo() {
        var s = fresh.then(TapCell(a)).then(TapDigit(1)).then(TapCell(b)).then(TapDigit(2)).then(TapCell(c)).then(TapDigit(3))
        s = s.then(Undo)
        assertThat(s.redo).hasSize(1)
        s = s.then(TapCell(a)).then(TapDigit(9))
        assertThat(s.redo).isEmpty()
    }

    @Test
    fun TC066_eraseCanBeUndone() {
        val s = fresh.withEntries(a to 6).then(TapCell(a)).then(TapErase)
        assertThat(s.then(Undo).value(a)).isEqualTo(6)
    }

    @Test
    fun undoRedo_withEmptyHistory_areNoOps() {
        assertThat(fresh.act(Undo).state).isEqualTo(fresh)
        assertThat(fresh.act(Redo).state).isEqualTo(fresh)
    }

    @Test
    fun redoReappliesUndoneMove() {
        val s = fresh.then(TapCell(a)).then(TapDigit(4))
        val back = s.then(Undo).then(Redo)
        assertThat(back.value(a)).isEqualTo(4)
        assertThat(back.undo).hasSize(1)
        assertThat(back.redo).isEmpty()
    }

    @Test
    fun TC067_undoDoesNotReduceMistakeCount() {
        val s = fresh.then(TapCell(a)).then(TapDigit(fresh.wrong(a)))
        assertThat(s.counters.mistakes).isEqualTo(1)
        val undone = s.then(Undo)
        assertThat(undone.value(a)).isEqualTo(0)
        assertThat(undone.counters.mistakes).isEqualTo(1)
    }
}
