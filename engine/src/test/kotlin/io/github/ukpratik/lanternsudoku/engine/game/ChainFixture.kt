// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.engine.game

import io.github.ukpratik.lanternsudoku.engine.model.Grid

/**
 * A real chain position (D-031): bank record N-H-0000 part-solved by hints. Its hint is Pointing → Hidden pair →
 * Pointing → Hidden single (r8c1 = 1), four steps.
 */
internal object ChainFixture {
    val givens: Grid = Grid.parse(".84.1....2..5...4..65.9.8....9.....85..1.2..74.....6....6.2.38..5...8..4....4.16.")
    val board: Grid = Grid.parse(".84.1....2..58..4..65.9.8..6.9.....85381624974..8.96...46.21385.5.638..48.3.4516.")

    fun state(): GameState {
        val s = GameState.new("N-H-0000", givens)
        val entries = (0 until 81).filter { givens[it] == 0 && board[it] != 0 }.map { it to board[it] }
        return s.withEntries(*entries.toTypedArray())
    }
}
