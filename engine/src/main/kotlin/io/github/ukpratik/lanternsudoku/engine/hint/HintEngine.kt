// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.engine.hint

import io.github.ukpratik.lanternsudoku.engine.game.CellCode
import io.github.ukpratik.lanternsudoku.engine.game.GameState
import io.github.ukpratik.lanternsudoku.engine.model.Grid
import io.github.ukpratik.lanternsudoku.engine.model.Houses
import io.github.ukpratik.lanternsudoku.engine.model.Level
import io.github.ukpratik.lanternsudoku.engine.model.Placement
import io.github.ukpratik.lanternsudoku.engine.model.PuzzleId
import io.github.ukpratik.lanternsudoku.engine.solve.CandidateGrid
import io.github.ukpratik.lanternsudoku.engine.technique.Technique

/** What a hint is about (LLD §3.1, §4.4). [target] is the cell the hint ends on; its box gets the HINT share flag. */
sealed interface HintPlan {
    val target: Int

    /** Error first (US-012): the most recently placed wrong digit. Stages: box → cell → remove. */
    data class WrongDigit(val cell: Int, val digit: Int) : HintPlan {
        override val target: Int get() = cell
    }

    /**
     * The simplest technique plus the placement it unlocks, found on the true candidates. [candidatesBefore] holds,
     * for each step, the true candidate masks just before that step (after the earlier steps' eliminations): the stage-2
     * overlay shows them where the player's notes are missing or different (US-012 "for this hint only").
     */
    class Logical(val hint: LogicalHint, val candidatesBefore: List<IntArray>) : HintPlan {
        val steps: List<Step> get() = hint.steps
        val placement: Placement get() = hint.placement

        /** More than one elimination before the single: stage 2 walks the steps one at a time (D-031). */
        val isChain: Boolean get() = hint.isChain
        override val target: Int get() = hint.placement.cell
    }

    /** No explainable step (shouldn't happen on bank puzzles): reveal one correct digit. Never "guess". */
    data class Fallback(val placement: Placement) : HintPlan {
        override val target: Int get() = placement.cell
    }
}

/** The hint engine (LLD §4.4): error first, then the step search on the true candidates, then the fallback. */
object HintEngine {
    fun hint(s: GameState): HintPlan {
        wrongDigit(s)?.let { return it }
        val board = s.board() // mistake-free here: givens plus correct entries
        // Within the puzzle's level first (D-032), then the whole ladder, then the fallback.
        val tier = runCatching { PuzzleId.parse(s.puzzleId).level.tier }.getOrDefault(Level.EXPERT.tier)
        val hint = HintSearch.search(board, tier) ?: (if (tier < Level.EXPERT.tier) HintSearch.search(board) else null)
            ?: return fallback(board, s.solution)
        return HintPlan.Logical(hint, candidatesBefore(board, hint.steps))
    }

    /** The most recently placed wrong digit (highest move sequence; lowest cell on a tie), or null. */
    fun wrongDigit(s: GameState): HintPlan.WrongDigit? {
        var best = -1
        var bestSeq = -1
        for (i in 0 until Grid.CELLS) {
            if (!s.isWrong(i)) continue
            val seq = CellCode.seq(s.code(i))
            if (seq > bestSeq) {
                best = i
                bestSeq = seq
            }
        }
        return if (best < 0) null else HintPlan.WrongDigit(best, s.value(best))
    }

    /** The empty cell with the fewest true candidates (lowest index on ties) and its solution digit (LLD §4.4 step 6). */
    fun fallback(board: Grid, solution: Grid): HintPlan.Fallback {
        val c = CandidateGrid.of(board)
        var best = -1
        for (i in 0 until Grid.CELLS) {
            if (board[i] != 0) continue
            if (best < 0 || Integer.bitCount(c.cand[i]) < Integer.bitCount(c.cand[best])) best = i
        }
        require(best >= 0) { "A full board has no fallback" }
        return HintPlan.Fallback(Placement(best, solution[best]))
    }

    private fun candidatesBefore(board: Grid, steps: List<Step>): List<IntArray> {
        val g = CandidateGrid.of(board)
        return steps.map { step -> g.cand.copyOf().also { g.apply(step) } }
    }

    /** Houses tinted for "Where": the step's primary house, or both base lines of an X-Wing. */
    fun whereHouses(step: Step): IntArray =
        if (step.technique == Technique.X_WING) step.houses.copyOfRange(0, 2) else intArrayOf(step.house)

    /** Box house of a cell (for the error-first "Where"). */
    fun boxOf(cell: Int): Int = Houses.CELL_HOUSES[cell][2]
}
