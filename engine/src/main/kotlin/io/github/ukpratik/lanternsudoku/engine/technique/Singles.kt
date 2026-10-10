// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.engine.technique

import io.github.ukpratik.lanternsudoku.engine.hint.Step
import io.github.ukpratik.lanternsudoku.engine.model.Houses
import io.github.ukpratik.lanternsudoku.engine.model.Masks
import io.github.ukpratik.lanternsudoku.engine.model.Placement
import io.github.ukpratik.lanternsudoku.engine.solve.CandidateGrid

/**
 * A technique's instance search. Instances are passed to `emit` in the deterministic scan order (houses ascending,
 * digits ascending, cells ascending); `emit` returns true to stop. Returns true if it was stopped.
 */
internal fun interface Finder {
    fun find(c: CandidateGrid, emit: (Step) -> Boolean): Boolean
}

/** Tier 1: a house with exactly one empty cell. Scan: houses 0..26. */
internal object FullHouse : Finder {
    override fun find(c: CandidateGrid, emit: (Step) -> Boolean): Boolean {
        for (h in 0 until Houses.COUNT) {
            var empty = -1
            var count = 0
            for (cell in Houses.HOUSE_CELLS[h]) if (c.values[cell] == 0) { empty = cell; count++ }
            if (count != 1) continue
            val missing = Masks.ALL and c.houseUsed[h].inv()
            if (Masks.count(missing) != 1 || c.cand[empty] and missing == 0) continue // contradiction: not a full house
            val cause = Houses.HOUSE_CELLS[h].filter { it != empty }.toIntArray()
            val step = Step(Technique.FULL_HOUSE, h, intArrayOf(h), missing, cause, emptyList(), Placement(empty, Masks.lowestDigit(missing)))
            if (emit(step)) return true
        }
        return false
    }
}

/** Tier 1: a digit with one possible cell in a house. Scan: boxes first, then rows, then columns (LLD §4.2); digits ascending. */
internal object HiddenSingle : Finder {
    private val ORDER = IntArray(Houses.COUNT) { (it + Houses.BOX0) % Houses.COUNT }

    override fun find(c: CandidateGrid, emit: (Step) -> Boolean): Boolean {
        for (h in ORDER) {
            val cells = Houses.HOUSE_CELLS[h]
            for (d in 1..9) {
                val b = Masks.bit(d)
                if (c.houseUsed[h] and b != 0) continue
                var target = -1
                var count = 0
                for (cell in cells) if (c.cand[cell] and b != 0) { target = cell; count++ }
                if (count != 1) continue
                val step = Step(
                    Technique.HIDDEN_SINGLE, h, intArrayOf(h), b, blockers(c, h, target, d), emptyList(), Placement(target, d),
                )
                if (emit(step)) return true
            }
        }
        return false
    }

    /** Cells holding [digit] that see one of the house's other empty cells: the reason those cells can't take it. */
    private fun blockers(c: CandidateGrid, house: Int, target: Int, digit: Int): IntArray {
        val mark = BooleanArray(81)
        for (cell in Houses.HOUSE_CELLS[house]) {
            if (cell == target || c.values[cell] != 0) continue
            for (p in Houses.PEERS[cell]) if (c.values[p] == digit) mark[p] = true
        }
        return (0 until 81).filter { mark[it] }.toIntArray()
    }
}

/** Tier 1: a cell with one candidate. Scan: cells ascending. Cause = the lowest peer holding each other digit. */
internal object NakedSingle : Finder {
    override fun find(c: CandidateGrid, emit: (Step) -> Boolean): Boolean {
        for (cell in 0 until 81) {
            val m = c.cand[cell]
            if (c.values[cell] != 0 || Masks.count(m) != 1) continue
            val d = Masks.lowestDigit(m)
            val mark = BooleanArray(81)
            for (other in 1..9) {
                if (other == d) continue
                Houses.PEERS[cell].firstOrNull { c.values[it] == other }?.let { mark[it] = true }
            }
            val box = Houses.CELL_HOUSES[cell][2]
            val step = Step(
                Technique.NAKED_SINGLE, box, intArrayOf(box), m, (0 until 81).filter { mark[it] }.toIntArray(), emptyList(), Placement(cell, d),
            )
            if (emit(step)) return true
        }
        return false
    }
}
