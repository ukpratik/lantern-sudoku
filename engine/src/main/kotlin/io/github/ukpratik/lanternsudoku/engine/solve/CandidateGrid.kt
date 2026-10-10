// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.engine.solve

import io.github.ukpratik.lanternsudoku.engine.hint.Step
import io.github.ukpratik.lanternsudoku.engine.model.Grid
import io.github.ukpratik.lanternsudoku.engine.model.Houses
import io.github.ukpratik.lanternsudoku.engine.model.Masks

/**
 * Mutable values plus 9-bit candidate masks (LLD §4.1), used by the techniques, the logical solver and the hint search.
 * A filled cell has candidate mask 0. [copy] it for look-ahead.
 */
class CandidateGrid private constructor(
    val values: IntArray,
    val cand: IntArray,
    /** `houseUsed[h]` = mask of digits already placed in house h. */
    val houseUsed: IntArray,
) {
    fun copy(): CandidateGrid = CandidateGrid(values.copyOf(), cand.copyOf(), houseUsed.copyOf())

    /** Places [digit] in [cell] and removes it from the 20 peers. */
    fun place(cell: Int, digit: Int) {
        val b = Masks.bit(digit)
        values[cell] = digit
        cand[cell] = 0
        for (h in Houses.CELL_HOUSES[cell]) houseUsed[h] = houseUsed[h] or b
        for (p in Houses.PEERS[cell]) cand[p] = cand[p] and b.inv()
    }

    fun eliminate(cell: Int, digit: Int) {
        cand[cell] = cand[cell] and Masks.bit(digit).inv()
    }

    fun apply(step: Step) {
        step.placement?.let { place(it.cell, it.digit) }
        for (e in step.eliminations) eliminate(e.cell, e.digit)
    }

    val isSolved: Boolean get() = values.none { it == 0 }

    /** True if some empty cell has no candidate left, or some house can no longer place a missing digit. */
    fun hasContradiction(): Boolean {
        for (i in 0 until 81) if (values[i] == 0 && cand[i] == 0) return true
        for (h in 0 until Houses.COUNT) {
            var possible = houseUsed[h]
            for (c in Houses.HOUSE_CELLS[h]) possible = possible or cand[c]
            if (possible != Masks.ALL) return true
        }
        return false
    }

    fun toGrid(): Grid = Grid(values)

    companion object {
        /**
         * True candidates (US-012, LLD §4.1): for every empty cell, the digits not already placed in its three houses.
         * No other eliminations; the player's notes are never an input.
         */
        fun of(grid: Grid): CandidateGrid {
            val values = grid.toIntArray()
            val used = IntArray(Houses.COUNT)
            for (i in 0 until 81) {
                val v = values[i]
                if (v != 0) for (h in Houses.CELL_HOUSES[i]) used[h] = used[h] or Masks.bit(v)
            }
            val cand = IntArray(81) { i ->
                if (values[i] != 0) 0 else Masks.ALL and (used[Houses.CELL_HOUSES[i][0]] or used[Houses.CELL_HOUSES[i][1]] or used[Houses.CELL_HOUSES[i][2]]).inv()
            }
            return CandidateGrid(values, cand, used)
        }
    }
}
