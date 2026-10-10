// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.engine.model

/**
 * Precomputed geometry (LLD §4.1). Houses 0–8 are rows, 9–17 columns, 18–26 boxes; box = (row / 3) × 3 + col / 3.
 * Every list is in ascending order, which the deterministic scan order relies on.
 */
object Houses {
    const val COUNT = 27
    const val ROW0 = 0
    const val COL0 = 9
    const val BOX0 = 18

    fun row(cell: Int): Int = cell / 9
    fun col(cell: Int): Int = cell % 9
    fun box(cell: Int): Int = (cell / 27) * 3 + (cell % 9) / 3
    fun cell(row: Int, col: Int): Int = row * 9 + col

    fun isRow(house: Int): Boolean = house < COL0
    fun isCol(house: Int): Boolean = house in COL0 until BOX0
    fun isBox(house: Int): Boolean = house >= BOX0

    /** `HOUSE_CELLS[h]` = the 9 cells of house h, ascending. */
    val HOUSE_CELLS: Array<IntArray> = Array(COUNT) { h ->
        when {
            h < COL0 -> IntArray(9) { cell(h, it) }
            h < BOX0 -> IntArray(9) { cell(it, h - COL0) }
            else -> {
                val b = h - BOX0
                IntArray(9) { cell((b / 3) * 3 + it / 3, (b % 3) * 3 + it % 3) }
            }
        }
    }

    /** `CELL_HOUSES[i]` = [row house, column house, box house] of cell i. */
    val CELL_HOUSES: Array<IntArray> = Array(81) { intArrayOf(ROW0 + row(it), COL0 + col(it), BOX0 + box(it)) }

    /** `PEERS[i]` = the 20 other cells sharing a house with cell i, ascending. */
    val PEERS: Array<IntArray> = Array(81) { i ->
        (0 until 81).filter { j -> j != i && (row(j) == row(i) || col(j) == col(i) || box(j) == box(i)) }.toIntArray()
    }

    /** Human-readable house name for logs and the hand-check report (never shown in the app; copy lives in strings.xml). */
    fun name(house: Int): String = when {
        isRow(house) -> "row ${house - ROW0 + 1}"
        isCol(house) -> "column ${house - COL0 + 1}"
        else -> "box ${house - BOX0 + 1}"
    }

    /** `r3c5`-style cell name (1-based), for logs and reports. */
    fun cellName(cell: Int): String = "r${row(cell) + 1}c${col(cell) + 1}"
}

/** 9-bit candidate masks: bit `d - 1` stands for digit d (LLD §4.1). */
object Masks {
    const val ALL = 0x1FF

    fun bit(digit: Int): Int = 1 shl (digit - 1)

    fun count(mask: Int): Int = Integer.bitCount(mask)

    /** The lowest digit in a non-empty mask. */
    fun lowestDigit(mask: Int): Int = Integer.numberOfTrailingZeros(mask) + 1

    /** Digits of the mask, ascending. */
    fun digits(mask: Int): IntArray {
        val out = IntArray(Integer.bitCount(mask))
        var m = mask
        var k = 0
        while (m != 0) {
            out[k++] = Integer.numberOfTrailingZeros(m) + 1
            m = m and (m - 1)
        }
        return out
    }

    fun format(mask: Int): String = digits(mask).joinToString("")
}
