// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.engine.model

/**
 * An immutable 9×9 grid of values 0..9 (0 = empty). Cell index `i = row × 9 + col` (LLD §4.1).
 * Text form: 81 characters, `1`–`9` for digits and `.` (or `0`) for empty cells.
 */
class Grid(cells: IntArray) {
    private val cells: IntArray = cells.copyOf()

    init {
        require(cells.size == CELLS) { "A grid has $CELLS cells, got ${cells.size}" }
        require(cells.all { it in 0..9 }) { "Cell values must be 0..9" }
    }

    operator fun get(i: Int): Int = cells[i]

    fun toIntArray(): IntArray = cells.copyOf()

    val filledCount: Int get() = cells.count { it != 0 }

    val isFull: Boolean get() = cells.none { it == 0 }

    /** True when no row, column or box holds a digit twice (empty cells ignored). */
    fun isConsistent(): Boolean {
        for (h in 0 until Houses.COUNT) {
            var seen = 0
            for (c in Houses.HOUSE_CELLS[h]) {
                val v = cells[c]
                if (v == 0) continue
                val b = Masks.bit(v)
                if (seen and b != 0) return false
                seen = seen or b
            }
        }
        return true
    }

    fun with(cell: Int, value: Int): Grid = Grid(cells.copyOf().also { it[cell] = value })

    override fun equals(other: Any?): Boolean = other is Grid && cells.contentEquals(other.cells)

    override fun hashCode(): Int = cells.contentHashCode()

    /** 81-character form with `.` for empty cells. */
    override fun toString(): String = buildString(CELLS) { for (v in cells) append(if (v == 0) '.' else ('0' + v)) }

    companion object {
        const val CELLS = 81

        val EMPTY: Grid = Grid(IntArray(CELLS))

        /** Parses 81 characters (`1`–`9`, `.` or `0`); whitespace and `|`/`-`/`+` separators are ignored, so pretty boards parse too. */
        fun parse(text: String): Grid {
            val chars = text.filterNot { it.isWhitespace() || it == '|' || it == '-' || it == '+' }
            require(chars.length == CELLS) { "Expected $CELLS cells, got ${chars.length}" }
            return Grid(
                IntArray(CELLS) { i ->
                    when (val ch = chars[i]) {
                        '.', '0' -> 0
                        in '1'..'9' -> ch - '0'
                        else -> throw IllegalArgumentException("Bad cell character '$ch' at $i")
                    }
                },
            )
        }
    }
}

/** A digit placed in a cell. */
data class Placement(val cell: Int, val digit: Int)

/** A candidate removed from a cell. */
data class Elim(val cell: Int, val digit: Int)
