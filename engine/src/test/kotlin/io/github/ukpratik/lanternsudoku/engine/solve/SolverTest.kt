// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.engine.solve

import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.lanternsudoku.engine.model.Grid
import io.github.ukpratik.lanternsudoku.engine.model.Level
import io.github.ukpratik.lanternsudoku.engine.technique.Technique
import org.junit.Test

/** Grader on known puzzles (our own bank records 0 of each New-game file), backtracking solve and count. */
class SolverTest {
    private val easy = Grid.parse(".47...9..91..7.2..6.8..3..4.897....617.624.894....957.7..5..1.2..4.6..97..1...65.")
    private val medium = Grid.parse(".2438....58.1...2..7...4..64..91....9..548..2....23..47..4...3..4...1.67....6214.")
    private val hard = Grid.parse(".84.1....2..5...4..65.9.8....9.....85..1.2..74.....6....6.2.38..5...8..4....4.16.")
    private val expert = Grid.parse("..825.3...2.6...54.........2.....8.9...9.6...6.7.....5.........85...2.7...3.481..")

    /** Needs more than the ladder (found by our generator): the grader must say "stuck", never guess. */
    private val beyondLadder = Grid.parse("..........9.4..26...3..945..4..1..727..8.3..961..4..3..742..3...32..4.1..........")

    @Test
    fun gradesKnownPuzzles() {
        assertThat(Grader.grade(easy).hardest).isEqualTo(Technique.HIDDEN_SINGLE)
        assertThat(Grader.grade(medium).hardest).isEqualTo(Technique.POINTING)
        assertThat(Grader.grade(hard).hardest).isEqualTo(Technique.HIDDEN_PAIR)
        assertThat(Grader.grade(expert).hardest).isEqualTo(Technique.X_WING)
        assertThat(Grader.grade(expert).level).isEqualTo(Level.EXPERT)
    }

    @Test
    fun gradeIsStableOnRecompute() {
        for (p in listOf(easy, medium, hard, expert)) {
            val a = Grader.grade(p)
            val b = Grader.grade(p)
            assertThat(a.steps).isEqualTo(b.steps)
            assertThat(a.hardest).isEqualTo(b.hardest)
        }
    }

    @Test
    fun beyondTheLadderIsStuckNotGuessed() {
        val g = Grader.grade(beyondLadder)
        assertThat(g.level).isNull()
        assertThat(g.solved).isFalse()
        assertThat(BacktrackingSolver.countSolutions(beyondLadder)).isEqualTo(1)
    }

    @Test
    fun levelSolverCannotSolveAHarderPuzzle() {
        assertThat(LogicalSolver.forLevel(Level.HARD).solve(expert).solved).isFalse()
        assertThat(LogicalSolver.forLevel(Level.MEDIUM).solve(hard).solved).isFalse()
        assertThat(LogicalSolver.forLevel(Level.EASY).solve(medium).solved).isFalse()
        assertThat(LogicalSolver.forLevel(Level.EXPERT).solve(expert).solved).isTrue()
    }

    @Test
    fun logicalSolutionEqualsBacktrackingSolution() {
        for (p in listOf(easy, medium, hard, expert)) {
            val logical = LogicalSolver().solve(p)
            assertThat(logical.solved).isTrue()
            assertThat(logical.grid).isEqualTo(BacktrackingSolver.solve(p))
            assertThat(logical.grid.isConsistent()).isTrue()
        }
    }

    @Test
    fun noLogicalStepEverRemovesASolutionDigit() {
        for (p in listOf(easy, medium, hard, expert, beyondLadder)) {
            val solution = BacktrackingSolver.solve(p)!!
            for (step in LogicalSolver().solve(p).steps) {
                step.placement?.let { assertThat(it.digit).isEqualTo(solution[it.cell]) }
                for (e in step.eliminations) assertThat(e.digit).isNotEqualTo(solution[e.cell])
            }
        }
    }

    @Test
    fun countsSolutions() {
        assertThat(BacktrackingSolver.countSolutions(expert)).isEqualTo(1)
        assertThat(BacktrackingSolver.countSolutions(Grid.EMPTY, 2)).isEqualTo(2)
        assertThat(BacktrackingSolver.countSolutions(Grid.EMPTY, 7)).isEqualTo(7)
        // Remove one more given from a minimal puzzle → several solutions.
        val firstGiven = (0 until 81).first { expert[it] != 0 }
        assertThat(BacktrackingSolver.countSolutions(expert.with(firstGiven, 0))).isEqualTo(2)
    }

    @Test
    fun conflictingGivensHaveNoSolution() {
        val bad = Grid.EMPTY.with(0, 5).with(8, 5)
        assertThat(BacktrackingSolver.countSolutions(bad)).isEqualTo(0)
        assertThat(BacktrackingSolver.solve(bad)).isNull()
    }

    @Test
    fun solvedGridIsAValidCompletionOfTheGivens() {
        val s = BacktrackingSolver.solve(hard)!!
        assertThat(s.isFull).isTrue()
        assertThat(s.isConsistent()).isTrue()
        for (i in 0 until 81) if (hard[i] != 0) assertThat(s[i]).isEqualTo(hard[i])
    }

    @Test
    fun trueCandidatesIgnoreAnythingButPlacedDigits() {
        val c = CandidateGrid.of(Grid.EMPTY.with(0, 1))
        assertThat(c.cand[1] and 1).isEqualTo(0) // 1 removed from a row peer
        assertThat(c.cand[80]).isEqualTo(0x1FF) // untouched elsewhere
        assertThat(c.cand[0]).isEqualTo(0)
    }
}
