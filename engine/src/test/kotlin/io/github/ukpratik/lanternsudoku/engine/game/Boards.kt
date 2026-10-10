// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.engine.game

import io.github.ukpratik.lanternsudoku.engine.model.Grid
import io.github.ukpratik.lanternsudoku.engine.model.Houses
import io.github.ukpratik.lanternsudoku.engine.model.Masks

/** Test boards and helpers. The puzzle is bank record N-M-0000 (also used by SolverTest). */
internal object Boards {
    val givens: Grid = Grid.parse(".2438....58.1...2..7...4..64..91....9..548..2....23..47..4...3..4...1.67....6214.")

    fun fresh(): GameState = GameState.new("N-M-0000", givens)

    /** Empty (non-given) cells, ascending. */
    val empties: List<Int> = (0 until 81).filter { givens[it] == 0 }

    /** A given cell. */
    val aGiven: Int = (0 until 81).first { givens[it] != 0 }
}

internal fun GameState.act(a: Action, rules: Rules = Rules()): Transition = GameReducer.reduce(this, a, rules)

internal fun GameState.then(a: Action, rules: Rules = Rules()): GameState = act(a, rules).state

/** Sets entries directly (no history), as if played earlier. */
internal fun GameState.withEntries(vararg entries: Pair<Int, Int>): GameState {
    val cells = codes()
    for ((cell, d) in entries) {
        require(!isGiven(cell)) { "cell $cell is a given" }
        cells[cell] = CellCode.of(value = d)
    }
    return copy(cells = cells)
}

internal fun GameState.withNotes(cell: Int, vararg digits: Int): GameState {
    val cells = codes()
    cells[cell] = CellCode.of(notes = digits.fold(0) { m, d -> m or Masks.bit(d) })
    return copy(cells = cells)
}

internal fun GameState.withInput(input: InputState): GameState = copy(input = input)

/** The solution digit of cell i, and some digit that is wrong there. */
internal fun GameState.right(i: Int): Int = solution[i]

internal fun GameState.wrong(i: Int): Int = (1..9).first { it != solution[i] }

/** A wrong digit for cell i that doesn't clash with any given or entry in its houses (a "hidden" mistake). */
internal fun GameState.wrongNoConflict(i: Int): Int? =
    (1..9).firstOrNull { d -> d != solution[i] && Houses.PEERS[i].none { value(it) == d } }

/** Fills every empty cell with its solution digit, except [except]. */
internal fun GameState.solvedExcept(vararg except: Int): GameState {
    val cells = codes()
    for (i in Boards.empties) if (i !in except) cells[i] = CellCode.of(value = solution[i])
    return copy(cells = cells)
}

/** Empty cells whose solution digit is d. */
internal fun GameState.emptiesFor(d: Int): List<Int> = Boards.empties.filter { solution[it] == d }
