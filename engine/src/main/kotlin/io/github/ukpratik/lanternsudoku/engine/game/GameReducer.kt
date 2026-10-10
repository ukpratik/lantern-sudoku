// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.engine.game

import io.github.ukpratik.lanternsudoku.engine.game.Action.Back
import io.github.ukpratik.lanternsudoku.engine.game.Action.CloseHint
import io.github.ukpratik.lanternsudoku.engine.game.Action.HintNext
import io.github.ukpratik.lanternsudoku.engine.game.Action.HintStage
import io.github.ukpratik.lanternsudoku.engine.game.Action.HintStep
import io.github.ukpratik.lanternsudoku.engine.game.Action.OpenHint
import io.github.ukpratik.lanternsudoku.engine.game.Action.KeepLooking
import io.github.ukpratik.lanternsudoku.engine.game.Action.KeepPlaying
import io.github.ukpratik.lanternsudoku.engine.game.Action.LongPressDigit
import io.github.ukpratik.lanternsudoku.engine.game.Action.Redo
import io.github.ukpratik.lanternsudoku.engine.game.Action.Restart
import io.github.ukpratik.lanternsudoku.engine.game.Action.ShowThem
import io.github.ukpratik.lanternsudoku.engine.game.Action.TapCell
import io.github.ukpratik.lanternsudoku.engine.game.Action.TapDigit
import io.github.ukpratik.lanternsudoku.engine.game.Action.TapErase
import io.github.ukpratik.lanternsudoku.engine.game.Action.ToggleNotes
import io.github.ukpratik.lanternsudoku.engine.game.Action.Undo
import io.github.ukpratik.lanternsudoku.engine.hint.HintEngine
import io.github.ukpratik.lanternsudoku.engine.hint.HintPlan
import io.github.ukpratik.lanternsudoku.engine.hint.HintSession
import io.github.ukpratik.lanternsudoku.engine.model.Elim
import io.github.ukpratik.lanternsudoku.engine.model.Grid
import io.github.ukpratik.lanternsudoku.engine.model.Houses
import io.github.ukpratik.lanternsudoku.engine.model.Masks
import io.github.ukpratik.lanternsudoku.engine.model.Placement

/**
 * The input state machine (PRD US-004 to US-011, v1.3; LLD §4.7). Pure and synchronous: the ViewModel calls it on the
 * main thread and maps [Effect]s to the platform. Row numbers in comments refer to the LLD §4.7 table; rows 10a/10b
 * are the PRD v1.3 completed-key rows (D-028).
 */
object GameReducer {
    fun reduce(s: GameState, a: Action, rules: Rules): Transition {
        if (a == Restart) return restart(s)
        return when (s.status) {
            Status.COMPLETED -> if (a == Back) Transition(s, handled = false) else same(s)
            Status.LIMIT -> when (a) {
                // Back on the Limit panel = Keep playing, the non-destructive choice (design §8.2).
                KeepPlaying, Back -> Transition(
                    s.copy(status = Status.PLAYING, overrides = s.overrides.copy(limitOff = true)),
                    listOf(Effect.Persist),
                )
                else -> same(s)
            }
            Status.ALMOST -> when (a) {
                ShowThem -> Transition(
                    s.copy(status = Status.PLAYING, overrides = s.overrides.copy(showMistakes = true)),
                    listOf(Effect.Persist),
                )
                // Back on Almost! = Keep looking (TC-097).
                KeepLooking, Back -> Transition(s.copy(status = Status.PLAYING), listOf(Effect.Persist))
                else -> same(s)
            }
            Status.PLAYING -> playing(s, a, rules)
        }
    }

    private fun playing(s: GameState, a: Action, rules: Rules): Transition = if (s.input.hint != null) hinting(s, a, rules) else when (a) {
        is TapDigit -> tapDigit(s, a.digit, rules)
        is LongPressDigit -> longPress(s, a.digit)
        is TapCell -> tapCell(s, a.cell, rules)
        TapErase -> tapErase(s)
        ToggleNotes -> Transition(s.copy(input = s.input.copy(notesMode = !s.input.notesMode)))
        Undo -> undo(s)
        Redo -> redo(s)
        Back -> back(s)
        OpenHint -> openHint(s, rules)
        // Panel actions outside their panel do nothing.
        ShowThem, KeepLooking, KeepPlaying, Restart, HintNext, is HintStage, is HintStep, CloseHint -> same(s)
    }

    // ---- teaching hints (US-012, LLD §4.4) ----------------------------------------------------------------------------

    /**
     * Opens the hint panel. Selection, lock, erase mode and a key highlight are released (the pad is hidden; design
     * hint-error-first) and not restored. The hint counts as stage 1 now and moves to deeper stages as the player goes;
     * the target's box gets the HINT share flag. The fallback reveals its digit at once (a stage-3 hint).
     */
    private fun openHint(s: GameState, rules: Rules): Transition {
        val plan = HintEngine.hint(s)
        val flags = s.boxFlags or BoxFlags.hint(Houses.box(plan.target))
        val input = InputState(notesMode = s.input.notesMode, hint = HintSession(plan, 1))
        val opened = s.copy(input = input, counters = s.counters.copy(hints1 = s.counters.hints1 + 1), boxFlags = flags)
        if (plan is HintPlan.Fallback) return doIt(opened, rules)
        return Transition(opened, listOf(Effect.Persist))
    }

    private fun hinting(s: GameState, a: Action, rules: Rules): Transition {
        val h = requireNotNull(s.input.hint)
        return when (a) {
            CloseHint, Back -> closeHint(s)
            HintNext -> when (h.stage) {
                1 -> toStage(s, 2)
                2 -> doIt(s, rules)
                else -> closeHint(s) // Done
            }
            is HintStage -> if (h.stage < 3 && a.stage in 1..2 && a.stage != h.stage) toStage(s, a.stage) else same(s)
            is HintStep -> if (h.stage == 2 && a.step in 0 until h.stepCount && a.step != h.step) {
                Transition(s.copy(input = s.input.copy(hint = h.copy(step = a.step))))
            } else {
                same(s)
            }
            // Stage 3's Undo: reverts the placement (or removal) and its note changes as one step and closes the panel.
            // The hint count isn't refunded (US-007).
            Undo -> if (h.stage == 3) undo(closeHint(s).state) else same(s)
            // The board and pad are inert while the panel is up (a role cell only reads its role aloud).
            else -> same(s)
        }
    }

    private fun closeHint(s: GameState): Transition = Transition(s.copy(input = s.input.copy(hint = null)))

    /** Moves to [stage] (1 or 2); reaching a deeper stage moves this hint's count there. */
    private fun toStage(s: GameState, stage: Int): Transition {
        val h = requireNotNull(s.input.hint)
        val deepest = maxOf(h.deepest, stage)
        val next = s.copy(input = s.input.copy(hint = h.copy(stage = stage, deepest = deepest, step = 0)), counters = recount(s.counters, h.deepest, deepest))
        return Transition(next, if (deepest != h.deepest) listOf(Effect.Persist) else emptyList())
    }

    /** Each hint is counted once, at its deepest stage (LLD §4.4 Counting; TC-111). */
    private fun recount(c: Counters, from: Int, to: Int): Counters {
        if (from == to) return c
        val minus = when (from) {
            1 -> c.copy(hints1 = c.hints1 - 1)
            2 -> c.copy(hints2 = c.hints2 - 1)
            else -> c.copy(hints3 = c.hints3 - 1)
        }
        return when (to) {
            1 -> minus.copy(hints1 = minus.hints1 + 1)
            2 -> minus.copy(hints2 = minus.hints2 + 1)
            else -> minus.copy(hints3 = minus.hints3 + 1)
        }
    }

    /** Stage 3 ("Do it"): remove the wrong digit, or place the unlocked digit and tidy the notes. One undo step. */
    private fun doIt(s: GameState, rules: Rules): Transition {
        val h = requireNotNull(s.input.hint)
        val counted = s.copy(counters = recount(s.counters, h.deepest, 3))
        return when (val plan = h.plan) {
            is HintPlan.WrongDigit -> {
                val cells = counted.codes()
                cells[plan.cell] = 0
                val next = commit(counted, cells)
                Transition(
                    next.copy(input = next.input.copy(hint = h.copy(stage = 3, deepest = 3, step = 0))),
                    listOf(Effect.Persist, Effect.Haptic(HapticKind.CONFIRM)),
                )
            }
            is HintPlan.Logical -> placeHint(counted, h, plan.placement, plan.steps.flatMap { it.eliminations }, rules)
            is HintPlan.Fallback -> placeHint(counted, h, plan.placement, emptyList(), rules)
        }
    }

    /**
     * Places the hint's digit (always the solution digit), removes the step's eliminated candidates from the player's
     * notes (US-012: "if the player has notes") and, with auto-cleanup on, the digit from the peers' notes (US-006).
     */
    private fun placeHint(s: GameState, h: HintSession, p: Placement, elims: List<Elim>, rules: Rules): Transition {
        check(p.digit == s.solution[p.cell]) { "A hint placement must equal the solution" }
        val cells = s.codes()
        val seq = s.seq + 1
        cells[p.cell] = CellCode.of(value = p.digit, seq = seq)
        for (e in elims) {
            if (e.cell == p.cell || s.isGiven(e.cell)) continue
            val code = cells[e.cell]
            if (CellCode.value(code) == 0) cells[e.cell] = CellCode.withNotes(code, CellCode.notes(code) and Masks.bit(e.digit).inv())
        }
        if (rules.autoCleanup) {
            val bit = Masks.bit(p.digit)
            for (q in Houses.PEERS[p.cell]) {
                if (s.isGiven(q)) continue
                val code = cells[q]
                if (CellCode.value(code) == 0) cells[q] = CellCode.withNotes(code, CellCode.notes(code) and bit.inv())
            }
        }
        var removed = 0
        for (i in 0 until Grid.CELLS) if (i != p.cell && CellCode.notes(cells[i]) != CellCode.notes(s.code(i))) removed++
        val next = commit(s, cells).copy(seq = seq)
        if (next.isSolved) {
            return Transition(
                next.copy(status = Status.COMPLETED, input = InputState()),
                listOf(Effect.Persist, Effect.Haptic(HapticKind.CONFIRM), Effect.Completed),
            )
        }
        return Transition(
            next.copy(input = next.input.copy(hint = h.copy(stage = 3, deepest = 3, step = 0, notesRemoved = removed))),
            listOf(Effect.Persist, Effect.Haptic(HapticKind.CONFIRM)),
        )
    }

    // ---- digits ---------------------------------------------------------------------------------------------------

    private fun tapDigit(s: GameState, d: Int, rules: Rules): Transition {
        require(d in 1..9) { "digit $d" }
        val input = s.input
        val sel = input.selection
        if (sel != null) {
            // Rows 11 and 10b: cell-first, including a completed key (v1.3: the extra digit then shows as a conflict).
            if (s.isGiven(sel)) return same(s) // US-004: a given is selected for highlighting only.
            return if (input.notesMode) toggleNote(s, sel, d) else placeOrNoop(s, sel, d, rules)
        }
        if (s.isComplete(d)) {
            // Row 10a: highlight only; any lock is released. Tapping the same completed key again clears the highlight.
            val highlight = if (input.lock == null && input.highlight == d) null else d
            return Transition(s.copy(input = input.copy(lock = null, eraseMode = false, highlight = highlight)), tick())
        }
        val lock = input.lock
        return when {
            lock == d -> Transition(s.copy(input = input.copy(lock = null, eraseMode = false))) // row 8
            lock != null -> Transition(s.copy(input = input.copy(lock = d, eraseMode = false))) // row 9
            else -> Transition(s.copy(input = input.copy(lock = d, highlight = null)), tick()) // row 1
        }
    }

    private fun longPress(s: GameState, d: Int): Transition {
        require(d in 1..9) { "digit $d" }
        val input = s.input
        if (s.isComplete(d)) {
            return Transition(s.copy(input = input.copy(selection = null, lock = null, eraseMode = false, highlight = d)), tick())
        }
        if (input.lock == d) return same(s) // row 3
        return Transition( // row 2
            s.copy(input = input.copy(selection = null, lock = d, eraseMode = false, highlight = null)),
            listOf(Effect.Haptic(HapticKind.LONG_PRESS)),
        )
    }

    // ---- cells ----------------------------------------------------------------------------------------------------

    private fun tapCell(s: GameState, i: Int, rules: Rules): Transition {
        require(i in 0 until Grid.CELLS) { "cell $i" }
        val input = s.input
        val lock = input.lock
        if (lock == null) {
            // Rows 12 and 13.
            return if (input.selection == i) {
                Transition(s.copy(input = input.copy(selection = null)))
            } else {
                Transition(s.copy(input = input.copy(selection = i, highlight = null)))
            }
        }
        if (input.eraseMode) { // row 7: in erase mode taps clear entries or notes; givens are a no-op
            return if (s.isGiven(i)) blocked(s) else clearCell(s, i) ?: same(s)
        }
        if (s.isGiven(i)) return blocked(s) // row 6
        val v = s.value(i)
        return when {
            v == lock -> clearCell(s, i) ?: same(s) // row 5: remove the entry (undoable)
            v != 0 -> blocked(s) // row 6: never overwrite; the highlight stays on the lock
            input.notesMode -> toggleNote(s, i, lock) // row 4, notes
            else -> placeOrNoop(s, i, lock, rules) // row 4 (+ row 15 auto-release inside)
        }
    }

    private fun tapErase(s: GameState): Transition {
        val input = s.input
        if (input.lock != null) return Transition(s.copy(input = input.copy(eraseMode = !input.eraseMode))) // row 7
        val sel = input.selection ?: return blocked(s) // row 16
        if (s.isGiven(sel)) return same(s) // row 14: givens can't be erased
        return clearCell(s, sel) ?: same(s) // row 14
    }

    private fun back(s: GameState): Transition {
        val input = s.input
        return when {
            input.eraseMode -> Transition(s.copy(input = input.copy(eraseMode = false))) // row 8: erase mode first
            input.lock != null -> Transition(s.copy(input = input.copy(lock = null))) // row 8
            input.selection != null -> Transition(s.copy(input = input.copy(selection = null))) // row 12
            input.highlight != null -> Transition(s.copy(input = input.copy(highlight = null)))
            else -> Transition(s, handled = false) // the screen leaves the game
        }
    }

    // ---- board changes ----------------------------------------------------------------------------------------------

    private fun placeOrNoop(s: GameState, i: Int, d: Int, rules: Rules): Transition {
        if (CellCode.value(s.code(i)) == d) return same(s) // already there: nothing to replace
        val cells = s.codes()
        val seq = s.seq + 1
        cells[i] = CellCode.of(value = d, seq = seq) // US-006: notes in the cell are hidden (undo restores them)
        if (rules.autoCleanup) {
            val bit = Masks.bit(d)
            for (p in Houses.PEERS[i]) {
                if (s.isGiven(p)) continue
                val code = cells[p]
                if (CellCode.value(code) == 0 && CellCode.notes(code) and bit != 0) {
                    cells[p] = CellCode.withNotes(code, CellCode.notes(code) and bit.inv())
                }
            }
        }
        val wrong = d != s.solution[i]
        val counters = if (wrong) s.counters.copy(mistakes = s.counters.mistakes + 1) else s.counters
        val boxFlags = if (wrong) s.boxFlags or BoxFlags.mistake(Houses.box(i)) else s.boxFlags
        var next = commit(s, cells).copy(seq = seq, counters = counters, boxFlags = boxFlags)

        // Row 15: the lock releases itself when the 9th copy is placed.
        if (next.input.lock == d && next.isComplete(d)) next = next.copy(input = next.input.copy(lock = null, eraseMode = false))

        if (next.isSolved) {
            return Transition(
                next.copy(status = Status.COMPLETED, input = InputState()),
                listOf(Effect.Persist, Effect.Haptic(HapticKind.CONFIRM), Effect.Completed),
            )
        }
        // US-010 (v1.3): the limit only applies while Show mistakes is on, and only trips on a new mistake, so lowering the
        // limit mid-game never ends it retroactively (TC-089).
        val limit = rules.mistakeLimit
        if (wrong && rules.showMistakes && limit > 0 && !next.overrides.limitOff && next.counters.mistakes >= limit) {
            return panel(next, Status.LIMIT, Panel.LIMIT)
        }
        // US-011: full but wrong, with mistakes hidden → a gentle "Almost!".
        if (next.isFull && !(rules.showMistakes || next.overrides.showMistakes)) return panel(next, Status.ALMOST, Panel.ALMOST)
        return Transition(next, listOf(Effect.Persist, Effect.Haptic(HapticKind.TICK)))
    }

    private fun panel(s: GameState, status: Status, panel: Panel): Transition = Transition(
        // A digit lock (and erase mode) is released when a panel opens (design §5).
        s.copy(status = status, input = s.input.copy(lock = null, eraseMode = false)),
        listOf(Effect.Persist, Effect.Haptic(HapticKind.CONTEXT_CLICK), Effect.OpenPanel(panel)),
    )

    private fun toggleNote(s: GameState, i: Int, d: Int): Transition {
        if (s.value(i) != 0) return blocked(s) // notes only go in empty cells
        val cells = s.codes()
        cells[i] = CellCode.withNotes(cells[i], CellCode.notes(cells[i]) xor Masks.bit(d))
        return Transition(commit(s, cells), listOf(Effect.Persist, Effect.Haptic(HapticKind.TICK)))
    }

    /** Clears the entry, or the notes if there's no entry. Null when there's nothing to clear. */
    private fun clearCell(s: GameState, i: Int): Transition? {
        val code = s.code(i)
        if (code == 0 || (CellCode.value(code) == 0 && CellCode.notes(code) == 0)) return null
        val cells = s.codes()
        cells[i] = if (CellCode.value(code) != 0) 0 else CellCode.withNotes(code, 0) and CellCode.HINT_BIT.inv()
        return Transition(commit(s, cells), listOf(Effect.Persist, Effect.Haptic(HapticKind.TICK)))
    }

    /** Pushes the diff between the current cells and [cells] onto undo and clears redo (ADR-014). */
    private fun commit(s: GameState, cells: IntArray): GameState {
        val triples = ArrayList<Int>(6)
        for (i in 0 until Grid.CELLS) {
            val before = s.code(i)
            if (before != cells[i]) {
                triples += i
                triples += before
                triples += cells[i]
            }
        }
        if (triples.isEmpty()) return s
        return s.copy(cells = cells, undo = s.undo + Delta(triples.toIntArray()), redo = emptyList())
    }

    private fun undo(s: GameState): Transition {
        val delta = s.undo.lastOrNull() ?: return same(s)
        val cells = s.codes()
        for (k in 0 until delta.size) cells[delta.cell(k)] = delta.before(k)
        return Transition(s.copy(cells = cells, undo = s.undo.dropLast(1), redo = s.redo + delta), listOf(Effect.Persist))
    }

    private fun redo(s: GameState): Transition {
        val delta = s.redo.lastOrNull() ?: return same(s)
        val cells = s.codes()
        for (k in 0 until delta.size) cells[delta.cell(k)] = delta.after(k)
        return Transition(s.copy(cells = cells, undo = s.undo + delta, redo = s.redo.dropLast(1)), listOf(Effect.Persist))
    }

    /** Restart puzzle (US-010): same puzzle, entries, notes, history, counters and overrides cleared; time to 0. */
    private fun restart(s: GameState): Transition = Transition(
        GameState(s.puzzleId, s.givens, s.solution, IntArray(Grid.CELLS), InputState(notesMode = false)),
        listOf(Effect.Persist, Effect.Restarted),
    )

    private fun same(s: GameState) = Transition(s)

    /** A tap that is deliberately refused: two light ticks, never a buzz (design §8). */
    private fun blocked(s: GameState) = Transition(s, listOf(Effect.Haptic(HapticKind.DOUBLE_TICK)))

    private fun tick() = listOf(Effect.Haptic(HapticKind.TICK))
}
