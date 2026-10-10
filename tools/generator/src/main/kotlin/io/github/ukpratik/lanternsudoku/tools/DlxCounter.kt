// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.tools

import io.github.ukpratik.lanternsudoku.engine.model.Grid

/**
 * Independent solution counter for the validator (LLD §4.5): Knuth's Algorithm X with dancing links over the
 * 324-column exact-cover matrix (cell, row-digit, column-digit, box-digit). Written from the published algorithm and
 * sharing no code with the engine's backtracking solver apart from the [Grid] input type.
 */
class DlxCounter {
    class Result(val count: Int, val firstSolution: Grid?)

    fun count(givens: Grid, limit: Int = 2): Result = Matrix().run(givens, limit)

    private class Matrix {
        private val cols = 324
        private val size = 1 + cols + 729 * 4
        private val left = IntArray(size)
        private val right = IntArray(size)
        private val up = IntArray(size)
        private val down = IntArray(size)
        private val colOf = IntArray(size)
        private val rowOf = IntArray(size)
        private val colSize = IntArray(cols + 1)
        private val rowFirst = IntArray(729)
        private val chosen = IntArray(81)
        private var depth = 0
        private var count = 0
        private var limit = 2
        private var first: IntArray? = null
        private val base = IntArray(81)

        init {
            // Root 0 and column headers 1..324 in a circular list.
            for (c in 0..cols) {
                left[c] = if (c == 0) cols else c - 1
                right[c] = if (c == cols) 0 else c + 1
                up[c] = c
                down[c] = c
            }
            var node = cols + 1
            for (r in 0 until 729) {
                val cell = r / 9
                val d = r % 9
                val row = cell / 9
                val col = cell % 9
                val box = (row / 3) * 3 + col / 3
                val columns = intArrayOf(1 + cell, 1 + 81 + row * 9 + d, 1 + 162 + col * 9 + d, 1 + 243 + box * 9 + d)
                rowFirst[r] = node
                for (k in 0 until 4) {
                    val n = node + k
                    val c = columns[k]
                    colOf[n] = c
                    rowOf[n] = r
                    // append at the bottom of column c
                    up[n] = up[c]; down[n] = c; down[up[c]] = n; up[c] = n
                    colSize[c]++
                    left[n] = if (k == 0) node + 3 else n - 1
                    right[n] = if (k == 3) node else n + 1
                }
                node += 4
            }
        }

        private fun cover(c: Int) {
            right[left[c]] = right[c]; left[right[c]] = left[c]
            var i = down[c]
            while (i != c) {
                var j = right[i]
                while (j != i) {
                    down[up[j]] = down[j]; up[down[j]] = up[j]; colSize[colOf[j]]--
                    j = right[j]
                }
                i = down[i]
            }
        }

        private fun uncover(c: Int) {
            var i = up[c]
            while (i != c) {
                var j = left[i]
                while (j != i) {
                    colSize[colOf[j]]++; down[up[j]] = j; up[down[j]] = j
                    j = left[j]
                }
                i = up[i]
            }
            right[left[c]] = c; left[right[c]] = c
        }

        private fun isCovered(c: Int): Boolean = right[left[c]] != c

        fun run(givens: Grid, limit: Int): Result {
            this.limit = limit
            for (cell in 0 until 81) {
                val v = givens[cell]
                base[cell] = v
                if (v == 0) continue
                val start = rowFirst[cell * 9 + v - 1]
                // A given whose constraint is already satisfied conflicts with another given: no solution.
                var n = start
                do {
                    if (isCovered(colOf[n])) return Result(0, null)
                    n = right[n]
                } while (n != start)
                n = start
                do {
                    cover(colOf[n])
                    n = right[n]
                } while (n != start)
            }
            search()
            return Result(count, first?.let(::Grid))
        }

        /** Returns true when the count reached the limit (stop everything). */
        private fun search(): Boolean {
            if (right[0] == 0) {
                count++
                if (first == null) {
                    val g = base.copyOf()
                    for (k in 0 until depth) {
                        val r = rowOf[chosen[k]]
                        g[r / 9] = r % 9 + 1
                    }
                    first = g
                }
                return count >= limit
            }
            // Choose the column with the fewest rows (Knuth's S heuristic); first one on ties.
            var c = right[0]
            var best = c
            while (c != 0) {
                if (colSize[c] < colSize[best]) best = c
                c = right[c]
            }
            if (colSize[best] == 0) return false
            cover(best)
            var r = down[best]
            var stop = false
            while (r != best && !stop) {
                chosen[depth++] = r
                var j = right[r]
                while (j != r) { cover(colOf[j]); j = right[j] }
                stop = search()
                j = left[r]
                while (j != r) { uncover(colOf[j]); j = left[j] }
                depth--
                r = down[r]
            }
            uncover(best)
            return stop
        }
    }
}
