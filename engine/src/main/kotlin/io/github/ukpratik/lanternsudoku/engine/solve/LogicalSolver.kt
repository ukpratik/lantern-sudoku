// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.engine.solve

import io.github.ukpratik.lanternsudoku.engine.hint.Step
import io.github.ukpratik.lanternsudoku.engine.model.Grid
import io.github.ukpratik.lanternsudoku.engine.model.Level
import io.github.ukpratik.lanternsudoku.engine.technique.StepFinder
import io.github.ukpratik.lanternsudoku.engine.technique.Technique

/** Result of a logical solve: the steps taken, and the final grid (complete when [solved]). */
class SolveResult(val steps: List<Step>, val solved: Boolean, val grid: Grid)

/**
 * Human-style solver (LLD §4.3): repeatedly takes the simplest step among [allowed] and applies it, until solved or stuck.
 * No guessing, ever.
 */
class LogicalSolver(val allowed: Set<Technique> = Technique.entries.toSet()) {
    fun nextStep(c: CandidateGrid): Step? = StepFinder.next(c, allowed)

    fun solve(givens: Grid): SolveResult {
        val c = CandidateGrid.of(givens)
        val steps = ArrayList<Step>()
        while (!c.isSolved) {
            if (c.hasContradiction()) break
            val step = nextStep(c) ?: break
            c.apply(step)
            steps += step
        }
        return SolveResult(steps, c.isSolved && c.toGrid().isConsistent(), c.toGrid())
    }

    companion object {
        /** A solver limited to the techniques of [level] and below. */
        fun forLevel(level: Level): LogicalSolver = LogicalSolver(Technique.upTo(level))
    }
}

/** A grade (LLD §4.3): the hardest technique used and its level, or both null when the ladder gets stuck. */
class Grade(val level: Level?, val hardest: Technique?, val steps: List<Step>, val solution: Grid?) {
    val solved: Boolean get() = level != null

    /** How many times each technique was used, in enum order (for reports). */
    fun techniqueCounts(): Map<Technique, Int> = steps.groupingBy { it.technique }.eachCount().toSortedMap()
}

/** Grades a puzzle by the hardest technique the simplest-first solver needs (US-013). Deterministic by construction. */
object Grader {
    private val FULL = LogicalSolver()

    fun grade(givens: Grid): Grade {
        val r = FULL.solve(givens)
        if (!r.solved) return Grade(null, null, r.steps, null)
        val hardest = r.steps.maxOfOrNull { it.technique } ?: Technique.FULL_HOUSE
        return Grade(hardest.level, hardest, r.steps, r.grid)
    }
}
