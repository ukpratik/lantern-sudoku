// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.tools

import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.lanternsudoku.engine.model.Grid
import io.github.ukpratik.lanternsudoku.engine.solve.BacktrackingSolver
import org.junit.Test

class DlxCounterTest {
    private val expert = Grid.parse("..825.3...2.6...54.........2.....8.9...9.6...6.7.....5.........85...2.7...3.481..")

    @Test
    fun uniquePuzzleHasOneSolutionEqualToBacktracking() {
        val r = DlxCounter().count(expert)
        assertThat(r.count).isEqualTo(1)
        assertThat(r.firstSolution).isEqualTo(BacktrackingSolver.solve(expert))
        assertThat(r.firstSolution!!.isConsistent()).isTrue()
    }

    @Test
    fun countStopsAtTheLimit() {
        assertThat(DlxCounter().count(Grid.EMPTY, 2).count).isEqualTo(2)
        assertThat(DlxCounter().count(Grid.EMPTY, 9).count).isEqualTo(9)
    }

    @Test
    fun underConstrainedPuzzleHasSeveral() {
        val given = (0 until 81).first { expert[it] != 0 }
        assertThat(DlxCounter().count(expert.with(given, 0)).count).isEqualTo(2)
    }

    @Test
    fun conflictingGivensHaveNone() {
        assertThat(DlxCounter().count(Grid.EMPTY.with(0, 3).with(80, 3).with(1, 3)).count).isEqualTo(0)
        val solved = BacktrackingSolver.solve(expert)!!
        assertThat(DlxCounter().count(solved).count).isEqualTo(1)
    }

    @Test
    fun agreesWithBacktrackingOnRandomSubsets() {
        val solved = BacktrackingSolver.solve(expert)!!
        val rng = io.github.ukpratik.lanternsudoku.engine.gen.SplitMix64(11)
        repeat(200) {
            val cells = solved.toIntArray()
            repeat(50 + rng.nextInt(15)) { cells[rng.nextInt(81)] = 0 }
            val g = Grid(cells)
            assertThat(DlxCounter().count(g, 3).count).isEqualTo(BacktrackingSolver.countSolutions(g, 3))
        }
    }
}
