// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.engine.solve

import io.github.ukpratik.lanternsudoku.engine.model.Grid
import io.github.ukpratik.lanternsudoku.engine.model.Houses
import io.github.ukpratik.lanternsudoku.engine.model.Masks

/**
 * Bitmask depth-first search with the minimum-remaining-values heuristic and ascending digit order (LLD §4.5).
 * Used by the generator's dig loop and by the app for the solution at game start. Shares no code with the validator's
 * DLX counter in `:tools:generator`.
 */
object BacktrackingSolver {
    /** The unique-or-first solution, or null if there is none (or the givens conflict). */
    fun solve(givens: Grid): Grid? {
        val s = Search.of(givens) ?: return null
        s.limit = 1
        s.run()
        return s.first?.let(::Grid)
    }

    /** Number of solutions, counting stops at [limit] (default 2: enough to tell "unique" from "not unique"). */
    fun countSolutions(givens: Grid, limit: Int = 2): Int {
        val s = Search.of(givens) ?: return 0
        s.limit = limit
        s.run()
        return s.count
    }

    /** Fills a grid with [order] supplying the digit order at each node (used by the generator to make random solutions). */
    internal fun fill(givens: Grid, order: (candidates: Int) -> IntArray): Grid? {
        val s = Search.of(givens) ?: return null
        s.limit = 1
        s.digitOrder = order
        s.run()
        return s.first?.let(::Grid)
    }

    private class Search(val values: IntArray, val rows: IntArray, val cols: IntArray, val boxes: IntArray) {
        var limit = 2
        var count = 0
        var first: IntArray? = null
        var digitOrder: ((Int) -> IntArray)? = null

        fun run() {
            dfs()
        }

        private fun dfs(): Boolean {
            // Pick the empty cell with the fewest candidates (lowest index on ties).
            var best = -1
            var bestMask = 0
            var bestCount = 10
            for (i in 0 until 81) {
                if (values[i] != 0) continue
                val m = free(i)
                val n = Integer.bitCount(m)
                if (n < bestCount) {
                    best = i; bestMask = m; bestCount = n
                    if (n <= 1) break
                }
            }
            if (best < 0) {
                count++
                if (first == null) first = values.copyOf()
                return count >= limit
            }
            if (bestCount == 0) return false
            val r = Houses.row(best)
            val c = Houses.col(best)
            val b = Houses.box(best)
            val order = digitOrder?.invoke(bestMask) ?: Masks.digits(bestMask)
            for (d in order) {
                val bit = Masks.bit(d)
                values[best] = d
                rows[r] = rows[r] or bit; cols[c] = cols[c] or bit; boxes[b] = boxes[b] or bit
                val stop = dfs()
                rows[r] = rows[r] and bit.inv(); cols[c] = cols[c] and bit.inv(); boxes[b] = boxes[b] and bit.inv()
                values[best] = 0
                if (stop) return true
            }
            return false
        }

        private fun free(i: Int): Int = Masks.ALL and (rows[Houses.row(i)] or cols[Houses.col(i)] or boxes[Houses.box(i)]).inv()

        companion object {
            fun of(givens: Grid): Search? {
                val values = givens.toIntArray()
                val rows = IntArray(9)
                val cols = IntArray(9)
                val boxes = IntArray(9)
                for (i in 0 until 81) {
                    val v = values[i]
                    if (v == 0) continue
                    val bit = Masks.bit(v)
                    val r = Houses.row(i)
                    val c = Houses.col(i)
                    val b = Houses.box(i)
                    if ((rows[r] or cols[c] or boxes[b]) and bit != 0) return null
                    rows[r] = rows[r] or bit; cols[c] = cols[c] or bit; boxes[b] = boxes[b] or bit
                }
                return Search(values, rows, cols, boxes)
            }
        }
    }
}
