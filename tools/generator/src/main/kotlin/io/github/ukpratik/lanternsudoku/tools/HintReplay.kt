// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.tools

import io.github.ukpratik.lanternsudoku.engine.hint.HintSearch
import io.github.ukpratik.lanternsudoku.engine.model.Grid
import io.github.ukpratik.lanternsudoku.engine.solve.CandidateGrid

/** Statistics of solving a puzzle by taking hint after hint ("Do it" every time) from the dealt board. */
class ReplayStats {
    var hints = 0
    var chains = 0
    var fallbacks = 0
    var wrongPlacements = 0
    var badEliminations = 0
    var maxShownSteps = 0
    var maxSearchDepth = 0
    var maxNanos = 0L
    var totalNanos = 0L

    fun add(o: ReplayStats) {
        hints += o.hints; chains += o.chains; fallbacks += o.fallbacks
        wrongPlacements += o.wrongPlacements; badEliminations += o.badEliminations
        maxNanos = maxOf(maxNanos, o.maxNanos); totalNanos += o.totalNanos
        maxShownSteps = maxOf(maxShownSteps, o.maxShownSteps); maxSearchDepth = maxOf(maxSearchDepth, o.maxSearchDepth)
    }

    val meanMicros: Double get() = if (hints == 0) 0.0 else totalNanos / 1e3 / hints
    val maxMillis: Double get() = maxNanos / 1e6
}

/**
 * Replays the hint search over a whole solve (TC-112, TC-124): at every state, the search runs on the true candidates,
 * is timed, and is checked against the solution (placement = solution digit; no elimination removes a solution digit).
 */
object HintReplay {
    private const val OUTLIER_NANOS = 10_000_000L

    fun replay(givens: Grid, solution: Grid): ReplayStats {
        val s = ReplayStats()
        var board = givens
        while (!board.isFull) {
            val t0 = System.nanoTime()
            val hint = HintSearch.search(board)
            var dt = System.nanoTime() - t0
            // An outlier is re-timed and the fastest run kept: a GC or scheduling pause (CI runs module tests in
            // parallel) doesn't repeat, a genuinely slow search does. The search is deterministic, so the result is the same.
            repeat(if (dt > OUTLIER_NANOS) 2 else 0) {
                val t1 = System.nanoTime()
                HintSearch.search(board)
                dt = minOf(dt, System.nanoTime() - t1)
            }
            s.hints++
            s.totalNanos += dt
            s.maxNanos = maxOf(s.maxNanos, dt)
            val cell: Int
            if (hint == null) {
                s.fallbacks++
                // The M3 fallback: the empty cell with the fewest true candidates (lowest index on ties).
                val c = CandidateGrid.of(board)
                cell = (0 until 81).filter { board[it] == 0 }.minBy { Integer.bitCount(c.cand[it]) }
            } else {
                if (hint.isChain) s.chains++
                s.maxShownSteps = maxOf(s.maxShownSteps, hint.steps.size)
                s.maxSearchDepth = maxOf(s.maxSearchDepth, hint.searchDepth)
                for (step in hint.steps) for (e in step.eliminations) if (solution[e.cell] == e.digit) s.badEliminations++
                if (hint.placement.digit != solution[hint.placement.cell]) s.wrongPlacements++
                cell = hint.placement.cell
            }
            board = board.with(cell, solution[cell])
        }
        return s
    }
}
