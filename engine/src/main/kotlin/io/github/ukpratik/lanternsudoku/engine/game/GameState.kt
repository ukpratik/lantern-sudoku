// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.engine.game

import io.github.ukpratik.lanternsudoku.engine.hint.HintSession
import io.github.ukpratik.lanternsudoku.engine.model.Grid
import io.github.ukpratik.lanternsudoku.engine.model.Masks
import io.github.ukpratik.lanternsudoku.engine.solve.BacktrackingSolver

/**
 * One `Int` per cell (LLD §4.7): bits 0–3 value (0 empty), bits 4–12 notes mask, bit 13 hint-placed, bits 14–31 the
 * move sequence number of the placement (for "most recently placed wrong digit", US-012). Givens are always 0 here:
 * the givens grid is canonical.
 */
object CellCode {
    private const val VALUE_MASK = 0xF
    private const val NOTES_SHIFT = 4
    const val HINT_BIT = 1 shl 13
    private const val SEQ_SHIFT = 14

    fun value(code: Int): Int = code and VALUE_MASK
    fun notes(code: Int): Int = (code ushr NOTES_SHIFT) and Masks.ALL
    fun hinted(code: Int): Boolean = code and HINT_BIT != 0
    fun seq(code: Int): Int = code ushr SEQ_SHIFT

    fun of(value: Int = 0, notes: Int = 0, hinted: Boolean = false, seq: Int = 0): Int {
        require(value in 0..9) { "value $value" }
        require(seq >= 0 && seq < (1 shl 18)) { "seq $seq" }
        return value or (notes shl NOTES_SHIFT) or (if (hinted) HINT_BIT else 0) or (seq shl SEQ_SHIFT)
    }

    fun withNotes(code: Int, notes: Int): Int = (code and (Masks.ALL shl NOTES_SHIFT).inv()) or (notes shl NOTES_SHIFT)
}

/** PLAYING, or a panel that blocks the board (LIMIT, ALMOST), or COMPLETED (final). */
enum class Status { PLAYING, LIMIT, ALMOST, COMPLETED }

/**
 * Transient input state, never persisted (LLD §2.3). Invariant: `selection != null ⇒ lock == null` (US-005).
 * [highlight] is the "completed key tapped" highlight (US-005 row 10): no lock, digit highlighted only.
 * [hint] is the open hint panel (US-012); while it's open the board and pad don't take input.
 */
data class InputState(
    val selection: Int? = null,
    val lock: Int? = null,
    val eraseMode: Boolean = false,
    val notesMode: Boolean = false,
    val highlight: Int? = null,
    val hint: HintSession? = null,
)

/** Per-game counters. Never undone (US-007): a stage-3 hint isn't refunded and mistakes aren't erased. */
data class Counters(val mistakes: Int = 0, val hints1: Int = 0, val hints2: Int = 0, val hints3: Int = 0)

/** Per-game overrides: "Show them" (US-011) and "Keep playing" (US-010). Persisted. */
data class Overrides(val showMistakes: Boolean = false, val limitOff: Boolean = false)

/** One undo step: flat triples (cell, beforeCode, afterCode) for the cells whose code changed (ADR-014). */
class Delta(val triples: IntArray) {
    init {
        require(triples.size % 3 == 0 && triples.isNotEmpty()) { "A delta is non-empty triples" }
    }

    val size: Int get() = triples.size / 3
    fun cell(k: Int): Int = triples[3 * k]
    fun before(k: Int): Int = triples[3 * k + 1]
    fun after(k: Int): Int = triples[3 * k + 2]

    override fun equals(other: Any?): Boolean = other is Delta && triples.contentEquals(other.triples)
    override fun hashCode(): Int = triples.contentHashCode()
    override fun toString(): String = "Delta(${triples.joinToString(",")})"
}

/** Box flags for the share grid (LLD §2.1): 2 bits per box in reading order, HINT then MISTAKE. */
object BoxFlags {
    fun hint(box: Int): Int = 1 shl (2 * box)
    fun mistake(box: Int): Int = 1 shl (2 * box + 1)
}

/**
 * The whole game, immutable (LLD §4.7). [cells] is copied in and never exposed mutably; reducers build new states.
 */
class GameState(
    val puzzleId: String,
    val givens: Grid,
    val solution: Grid,
    cells: IntArray,
    val input: InputState = InputState(),
    val undo: List<Delta> = emptyList(),
    val redo: List<Delta> = emptyList(),
    val seq: Int = 0,
    val counters: Counters = Counters(),
    val overrides: Overrides = Overrides(),
    val boxFlags: Int = 0,
    val status: Status = Status.PLAYING,
) {
    private val cells: IntArray = cells.copyOf()

    init {
        require(this.cells.size == Grid.CELLS) { "81 cells" }
        require(solution.isFull) { "solution must be full" }
        require(input.selection == null || input.lock == null) { "selection and lock are mutually exclusive" }
    }

    fun code(i: Int): Int = cells[i]
    fun codes(): IntArray = cells.copyOf()

    fun isGiven(i: Int): Boolean = givens[i] != 0

    /** The digit shown in cell i: the given, else the entry, else 0. */
    fun value(i: Int): Int = givens[i].takeIf { it != 0 } ?: CellCode.value(cells[i])

    /** Player notes in cell i (only meaningful while the cell is empty). */
    fun notes(i: Int): Int = if (isGiven(i)) 0 else CellCode.notes(cells[i])

    /** How many cells show digit d (givens and entries, right or wrong). */
    fun placedCount(d: Int): Int {
        var n = 0
        for (i in 0 until Grid.CELLS) if (value(i) == d) n++
        return n
    }

    /** US-008: 9 minus the number placed, never below 0. */
    fun remaining(d: Int): Int = (9 - placedCount(d)).coerceAtLeast(0)

    fun isComplete(d: Int): Boolean = placedCount(d) >= 9

    val filledCount: Int get() = (0 until Grid.CELLS).count { value(it) != 0 }

    val isFull: Boolean get() = (0 until Grid.CELLS).all { value(it) != 0 }

    val isSolved: Boolean get() = (0 until Grid.CELLS).all { value(it) == solution[it] }

    /** True when the entry in cell i differs from the solution (PRD §5 "Mistake"). */
    fun isWrong(i: Int): Boolean = !isGiven(i) && CellCode.value(cells[i]).let { it != 0 && it != solution[i] }

    /** Current board as a grid of values. */
    fun board(): Grid = Grid(IntArray(Grid.CELLS) { value(it) })

    fun copy(
        cells: IntArray = this.cells,
        input: InputState = this.input,
        undo: List<Delta> = this.undo,
        redo: List<Delta> = this.redo,
        seq: Int = this.seq,
        counters: Counters = this.counters,
        overrides: Overrides = this.overrides,
        boxFlags: Int = this.boxFlags,
        status: Status = this.status,
    ): GameState = GameState(puzzleId, givens, solution, cells, input, undo, redo, seq, counters, overrides, boxFlags, status)

    /** Equality of everything that is saved or shown, including the transient input (tests compare whole states). */
    override fun equals(other: Any?): Boolean = other is GameState &&
        puzzleId == other.puzzleId && givens == other.givens && solution == other.solution &&
        cells.contentEquals(other.cells) && input == other.input && undo == other.undo && redo == other.redo &&
        seq == other.seq && counters == other.counters && overrides == other.overrides && boxFlags == other.boxFlags &&
        status == other.status

    override fun hashCode(): Int = cells.contentHashCode() * 31 + puzzleId.hashCode()

    override fun toString(): String = "GameState($puzzleId, board=${board()}, input=$input, status=$status, undo=${undo.size}, " +
        "redo=${redo.size}, seq=$seq, $counters, $overrides)"

    companion object {
        /** A fresh game. The solution is computed once here (unique by the bank's validation). */
        fun new(puzzleId: String, givens: Grid): GameState {
            val solution = requireNotNull(BacktrackingSolver.solve(givens)) { "Puzzle $puzzleId has no solution" }
            return GameState(puzzleId, givens, solution, IntArray(Grid.CELLS))
        }

        /** 81-char summary for Home and Stats (LLD §2.1): '1'–'9' given, 'a'–'i' entry, '.' empty. */
        fun gridSummary(s: GameState): String = buildString(Grid.CELLS) {
            for (i in 0 until Grid.CELLS) {
                val g = s.givens[i]
                val v = CellCode.value(s.code(i))
                append(
                    when {
                        g != 0 -> '0' + g
                        v != 0 -> 'a' + (v - 1)
                        else -> '.'
                    },
                )
            }
        }
    }
}
