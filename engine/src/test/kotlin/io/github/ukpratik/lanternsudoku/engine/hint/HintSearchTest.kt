// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.engine.hint

import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.lanternsudoku.engine.gen.Generator
import io.github.ukpratik.lanternsudoku.engine.model.Grid
import io.github.ukpratik.lanternsudoku.engine.model.Level
import io.github.ukpratik.lanternsudoku.engine.model.Pool
import io.github.ukpratik.lanternsudoku.engine.solve.BacktrackingSolver
import io.github.ukpratik.lanternsudoku.engine.technique.Technique
import org.junit.Test

/** The hint step search (LLD §4.4 steps 2–5) on whole solves: always a placement equal to the solution. */
class HintSearchTest {
    private val expert = Grid.parse("..825.3...2.6...54.........2.....8.9...9.6...6.7.....5.........85...2.7...3.481..")

    @Test
    fun aSingleIsReturnedOnItsOwn() {
        val easy = Grid.parse(".47...9..91..7.2..6.8..3..4.897....617.624.894....957.7..5..1.2..4.6..97..1...65.")
        val hint = HintSearch.search(easy)!!
        assertThat(hint.steps).hasSize(1)
        assertThat(hint.steps.single().technique.isSingle).isTrue()
    }

    @Test
    fun everyHintOfAWholeSolveIsCorrect() {
        // Property (TC-112): over generated puzzles of every level, every placement = solution and no elimination removes it.
        for (level in Level.entries) for (index in 0 until 3) {
            val givens = Generator.generate(Pool.NEWGAME, level, index).record.givens
            val solution = BacktrackingSolver.solve(givens)!!
            var board = givens
            while (!board.isFull) {
                val hint = HintSearch.search(board)
                assertThat(hint).isNotNull()
                hint!!
                assertThat(hint.placement.digit).isEqualTo(solution[hint.placement.cell])
                assertThat(hint.steps.last().placement).isEqualTo(hint.placement)
                for (s in hint.steps.dropLast(1)) {
                    assertThat(s.technique.isSingle).isFalse()
                    for (e in s.eliminations) assertThat(e.digit).isNotEqualTo(solution[e.cell])
                }
                board = board.with(hint.placement.cell, hint.placement.digit)
            }
        }
    }

    @Test
    fun expertBoardEventuallyNeedsAnEliminationBeforeASingle() {
        val solution = BacktrackingSolver.solve(expert)!!
        var board = expert
        val used = sortedSetOf<Technique>()
        while (!board.isFull) {
            val hint = HintSearch.search(board)!!
            hint.steps.forEach { used += it.technique }
            board = board.with(hint.placement.cell, solution[hint.placement.cell])
        }
        assertThat(used.any { !it.isSingle }).isTrue()
    }

    @Test
    fun stepSearchIsFast() {
        // NFR-002 (≤ 200 ms on device); on the JVM a typical search is far below 10 ms. Warm up, then time a whole solve.
        repeat(3) { HintSearch.search(expert) }
        val solution = BacktrackingSolver.solve(expert)!!
        var board = expert
        var worst = 0L
        while (!board.isFull) {
            val t0 = System.nanoTime()
            val hint = HintSearch.search(board)!!
            worst = maxOf(worst, System.nanoTime() - t0)
            board = board.with(hint.placement.cell, solution[hint.placement.cell])
        }
        assertThat(worst / 1_000_000).isLessThan(50L)
    }
}
