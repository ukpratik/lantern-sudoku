// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.engine.technique

import io.github.ukpratik.lanternsudoku.engine.hint.Step
import io.github.ukpratik.lanternsudoku.engine.model.Elim
import io.github.ukpratik.lanternsudoku.engine.model.Houses
import io.github.ukpratik.lanternsudoku.engine.model.Masks
import io.github.ukpratik.lanternsudoku.engine.solve.CandidateGrid

/** Eliminations of the digits in [mask] from [cells] (ascending cells, then ascending digits), skipping cells in [except]. */
private fun elims(c: CandidateGrid, cells: IntArray, mask: Int, except: (Int) -> Boolean): List<Elim> {
    val out = ArrayList<Elim>()
    for (cell in cells) {
        if (except(cell)) continue
        val hit = c.cand[cell] and mask
        if (hit != 0) for (d in Masks.digits(hit)) out += Elim(cell, d)
    }
    return out
}

/** Cells of [house] holding candidate [digit], ascending. */
private fun cellsWith(c: CandidateGrid, house: Int, digit: Int): IntArray {
    val b = Masks.bit(digit)
    return Houses.HOUSE_CELLS[house].filter { c.cand[it] and b != 0 }.toIntArray()
}

/** Tier 2: a digit's candidates in a box all lie on one row (then: one column) → remove it from that line outside the box. */
internal object Pointing : Finder {
    override fun find(c: CandidateGrid, emit: (Step) -> Boolean): Boolean {
        for (box in Houses.BOX0 until Houses.COUNT) {
            for (d in 1..9) {
                if (c.houseUsed[box] and Masks.bit(d) != 0) continue
                val cells = cellsWith(c, box, d)
                if (cells.size < 2) continue
                for (line in linesShared(cells)) {
                    val e = elims(c, Houses.HOUSE_CELLS[line], Masks.bit(d)) { Houses.CELL_HOUSES[it][2] == box }
                    if (e.isNotEmpty() && emit(Step(Technique.POINTING, box, intArrayOf(box, line), Masks.bit(d), cells, e, null))) return true
                }
            }
        }
        return false
    }

    /** The row house and/or column house shared by every cell (row first). */
    private fun linesShared(cells: IntArray): IntArray {
        val out = ArrayList<Int>(2)
        if (cells.all { Houses.row(it) == Houses.row(cells[0]) }) out += Houses.ROW0 + Houses.row(cells[0])
        if (cells.all { Houses.col(it) == Houses.col(cells[0]) }) out += Houses.COL0 + Houses.col(cells[0])
        return out.toIntArray()
    }
}

/** Tier 2: a digit's candidates in a row or column all lie in one box → remove it from the rest of that box. */
internal object BoxLine : Finder {
    override fun find(c: CandidateGrid, emit: (Step) -> Boolean): Boolean {
        for (line in 0 until Houses.BOX0) {
            for (d in 1..9) {
                if (c.houseUsed[line] and Masks.bit(d) != 0) continue
                val cells = cellsWith(c, line, d)
                if (cells.size < 2) continue
                val box = Houses.CELL_HOUSES[cells[0]][2]
                if (cells.any { Houses.CELL_HOUSES[it][2] != box }) continue
                val e = elims(c, Houses.HOUSE_CELLS[box], Masks.bit(d)) { Houses.CELL_HOUSES[it].contains(line) }
                if (e.isNotEmpty() && emit(Step(Technique.BOX_LINE, line, intArrayOf(line, box), Masks.bit(d), cells, e, null))) return true
            }
        }
        return false
    }
}

/** Calls [block] with each k-combination of `0 until n` in lexicographic order; stops when it returns true. */
private inline fun combinations(n: Int, k: Int, block: (IntArray) -> Boolean): Boolean {
    if (k > n) return false
    val idx = IntArray(k) { it }
    while (true) {
        if (block(idx)) return true
        var i = k - 1
        while (i >= 0 && idx[i] == n - k + i) i--
        if (i < 0) return false
        idx[i]++
        for (j in i + 1 until k) idx[j] = idx[j - 1] + 1
    }
}

/**
 * Tiers 2–3: [size] cells of a house whose candidates together are exactly [size] digits → remove those digits from the
 * house's other cells. One implementation for the pair (size 2) and the triple (size 3), as the LLD asks.
 */
internal class NakedSubset(private val size: Int, private val technique: Technique) : Finder {
    override fun find(c: CandidateGrid, emit: (Step) -> Boolean): Boolean {
        for (h in 0 until Houses.COUNT) {
            val pool = Houses.HOUSE_CELLS[h].filter { c.values[it] == 0 && Masks.count(c.cand[it]) in 2..size }.toIntArray()
            val stopped = combinations(pool.size, size) { idx ->
                var union = 0
                for (i in idx) union = union or c.cand[pool[i]]
                if (Masks.count(union) != size) return@combinations false
                val subset = IntArray(size) { pool[idx[it]] }
                val e = elims(c, Houses.HOUSE_CELLS[h], union) { it in subset }
                e.isNotEmpty() && emit(Step(technique, h, intArrayOf(h), union, subset, e, null))
            }
            if (stopped) return true
        }
        return false
    }
}

/**
 * Tier 3: [size] digits confined to the same [size] cells of a house → remove every other candidate from those cells.
 * One implementation for the pair and the triple.
 */
internal class HiddenSubset(private val size: Int, private val technique: Technique) : Finder {
    override fun find(c: CandidateGrid, emit: (Step) -> Boolean): Boolean {
        for (h in 0 until Houses.COUNT) {
            val cells = Houses.HOUSE_CELLS[h]
            // positions[d] = mask over the house's 9 slots holding candidate d
            val digits = ArrayList<Int>(9)
            val positions = IntArray(10)
            for (d in 1..9) {
                if (c.houseUsed[h] and Masks.bit(d) != 0) continue
                var pos = 0
                for (k in 0 until 9) if (c.cand[cells[k]] and Masks.bit(d) != 0) pos = pos or (1 shl k)
                positions[d] = pos
                if (Integer.bitCount(pos) in 2..size) digits += d
            }
            val stopped = combinations(digits.size, size) { idx ->
                var slots = 0
                var digitMask = 0
                for (i in idx) {
                    slots = slots or positions[digits[i]]
                    digitMask = digitMask or Masks.bit(digits[i])
                }
                if (Integer.bitCount(slots) != size) return@combinations false
                val subset = (0 until 9).filter { slots and (1 shl it) != 0 }.map { cells[it] }.toIntArray()
                val e = elims(c, subset, Masks.ALL and digitMask.inv()) { false }
                e.isNotEmpty() && emit(Step(technique, h, intArrayOf(h), digitMask, subset, e, null))
            }
            if (stopped) return true
        }
        return false
    }
}

/**
 * Tier 4: a digit in exactly two cells in each of two rows, in the same two columns → remove it from the rest of those
 * columns; then the column-based form. Scan: base lines ascending (r1 < r2), digits ascending.
 */
internal object XWing : Finder {
    override fun find(c: CandidateGrid, emit: (Step) -> Boolean): Boolean {
        for (base0 in intArrayOf(Houses.ROW0, Houses.COL0)) {
            val cover0 = if (base0 == Houses.ROW0) Houses.COL0 else Houses.ROW0
            for (a in 0 until 9) for (b in a + 1 until 9) for (d in 1..9) {
                val pa = slots(c, base0 + a, d)
                if (Integer.bitCount(pa) != 2 || pa != slots(c, base0 + b, d)) continue
                val s1 = Integer.numberOfTrailingZeros(pa)
                val s2 = 31 - Integer.numberOfLeadingZeros(pa)
                val baseA = base0 + a
                val baseB = base0 + b
                val coverA = cover0 + s1
                val coverB = cover0 + s2
                val corners = intArrayOf(
                    Houses.HOUSE_CELLS[baseA][s1], Houses.HOUSE_CELLS[baseA][s2], Houses.HOUSE_CELLS[baseB][s1], Houses.HOUSE_CELLS[baseB][s2],
                ).sortedArray()
                val coverCells = (Houses.HOUSE_CELLS[coverA] + Houses.HOUSE_CELLS[coverB]).sortedArray()
                val e = elims(c, coverCells, Masks.bit(d)) { it in corners }
                if (e.isNotEmpty() && emit(Step(Technique.X_WING, baseA, intArrayOf(baseA, baseB, coverA, coverB), Masks.bit(d), corners, e, null))) {
                    return true
                }
            }
        }
        return false
    }

    /** Slots (0..8 within the house) holding candidate [digit]. */
    private fun slots(c: CandidateGrid, house: Int, digit: Int): Int {
        if (c.houseUsed[house] and Masks.bit(digit) != 0) return 0
        var m = 0
        val cells = Houses.HOUSE_CELLS[house]
        for (k in 0 until 9) if (c.cand[cells[k]] and Masks.bit(digit) != 0) m = m or (1 shl k)
        return m
    }
}
