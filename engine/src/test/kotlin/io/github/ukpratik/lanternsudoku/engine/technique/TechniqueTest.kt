// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.engine.technique

import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.lanternsudoku.engine.model.Elim
import io.github.ukpratik.lanternsudoku.engine.model.Grid
import io.github.ukpratik.lanternsudoku.engine.model.Houses
import io.github.ukpratik.lanternsudoku.engine.model.Masks
import io.github.ukpratik.lanternsudoku.engine.model.Placement
import io.github.ukpratik.lanternsudoku.engine.solve.CandidateGrid
import org.junit.Test

/**
 * Every technique, positive and negative, on hand-made boards (LLD §8). Boards start from the true candidates of a grid;
 * candidate-level patterns are then carved with [only] / [without], as a player's candidates would look mid-solve.
 */
class TechniqueTest {
    private fun grid(vararg placed: Pair<Int, Int>): CandidateGrid {
        val cells = IntArray(81)
        for ((cell, d) in placed) cells[cell] = d
        return CandidateGrid.of(Grid(cells))
    }

    /** Restricts [cell] to [digits]. */
    private fun CandidateGrid.only(cell: Int, digits: String): CandidateGrid = apply {
        for (d in 1..9) if (d.toString() !in digits) eliminate(cell, d)
    }

    /** Removes [digit] from every listed cell. */
    private fun CandidateGrid.without(digit: Int, cells: Iterable<Int>): CandidateGrid = apply { cells.forEach { eliminate(it, digit) } }

    private fun cell(r: Int, c: Int) = Houses.cell(r - 1, c - 1)

    private fun row(r: Int) = (1..9).map { cell(r, it) }

    // --- Full house ---

    @Test
    fun fullHouseFindsTheLastCellOfARow() {
        val g = grid(*(1..8).map { cell(1, it) to it }.toTypedArray())
        val step = StepFinder.next(g)!!
        assertThat(step.technique).isEqualTo(Technique.FULL_HOUSE)
        assertThat(step.placement).isEqualTo(Placement(cell(1, 9), 9))
        assertThat(step.house).isEqualTo(0)
    }

    @Test
    fun fullHouseNeedsExactlyOneEmptyCell() {
        val g = grid(*(1..7).map { cell(1, it) to it }.toTypedArray())
        assertThat(StepFinder.first(Technique.FULL_HOUSE, g)).isNull()
    }

    // --- Hidden single ---

    @Test
    fun hiddenSingleInABoxWithItsBlockers() {
        // 1s in r1c4, r2c7, r6c2, r8c3 leave r3c1 as the only place for 1 in box 1.
        val g = grid(cell(1, 4) to 1, cell(2, 7) to 1, cell(6, 2) to 1, cell(8, 3) to 1)
        val step = StepFinder.next(g)!!
        assertThat(step.technique).isEqualTo(Technique.HIDDEN_SINGLE)
        assertThat(step.placement).isEqualTo(Placement(cell(3, 1), 1))
        assertThat(step.house).isEqualTo(Houses.BOX0)
        assertThat(step.causeCells.toList()).containsExactly(cell(1, 4), cell(2, 7), cell(6, 2), cell(8, 3)).inOrder()
    }

    @Test
    fun noHiddenSingleWhenTwoCellsRemain() {
        val g = grid(cell(1, 4) to 1, cell(2, 7) to 1, cell(6, 2) to 1)
        assertThat(StepFinder.all(Technique.HIDDEN_SINGLE, g).filter { it.house == Houses.BOX0 }).isEmpty()
    }

    @Test
    fun hiddenSingleScansBoxesBeforeRows() {
        // Row 1: 9 only in r1c5. Box 5: 8 only in r5c5. Boxes come first, so box 5 wins although row 1 is house 0.
        val g = grid().without(9, row(1) - cell(1, 5))
        val box5 = Houses.HOUSE_CELLS[Houses.BOX0 + 4].toList()
        g.without(8, box5 - cell(5, 5))
        val step = StepFinder.first(Technique.HIDDEN_SINGLE, g)!!
        assertThat(step.placement).isEqualTo(Placement(cell(5, 5), 8))
        assertThat(StepFinder.all(Technique.HIDDEN_SINGLE, g).map { it.placement }).contains(Placement(cell(1, 5), 9))
    }

    // --- Naked single ---

    @Test
    fun nakedSingleWithPeersHoldingTheOtherEightDigits() {
        val g = grid(
            cell(1, 4) to 1, cell(1, 5) to 2, cell(1, 6) to 3,
            cell(4, 1) to 4, cell(5, 1) to 5, cell(6, 1) to 6,
            cell(2, 2) to 7, cell(3, 3) to 8,
        )
        val step = StepFinder.first(Technique.NAKED_SINGLE, g)!!
        assertThat(step.placement).isEqualTo(Placement(cell(1, 1), 9))
        assertThat(step.house).isEqualTo(Houses.BOX0)
        assertThat(step.causeCells).hasLength(8)
    }

    @Test
    fun noNakedSingleWithTwoCandidates() {
        val g = grid(cell(1, 4) to 1, cell(1, 5) to 2, cell(1, 6) to 3, cell(4, 1) to 4, cell(5, 1) to 5, cell(6, 1) to 6, cell(2, 2) to 7)
        assertThat(StepFinder.all(Technique.NAKED_SINGLE, g).map { it.placement!!.cell }).doesNotContain(cell(1, 1))
    }

    // --- Pointing ---

    @Test
    fun pointingRemovesFromTheRowOutsideTheBox() {
        val box1 = Houses.HOUSE_CELLS[Houses.BOX0].toList()
        val g = grid().without(5, box1 - cell(1, 1) - cell(1, 2))
        val step = StepFinder.next(g)!!
        assertThat(step.technique).isEqualTo(Technique.POINTING)
        assertThat(step.houses.toList()).containsExactly(Houses.BOX0, 0).inOrder()
        assertThat(step.causeCells.toList()).containsExactly(cell(1, 1), cell(1, 2)).inOrder()
        assertThat(step.eliminations).containsExactlyElementsIn((4..9).map { Elim(cell(1, it), 5) }).inOrder()
    }

    @Test
    fun pointingNeedsOneLine() {
        val box1 = Houses.HOUSE_CELLS[Houses.BOX0].toList()
        val g = grid().without(5, box1 - cell(1, 1) - cell(2, 2))
        assertThat(StepFinder.first(Technique.POINTING, g)).isNull()
    }

    // --- Box-line reduction ---

    @Test
    fun boxLineRemovesFromTheRestOfTheBox() {
        val g = grid().without(5, (4..9).map { cell(1, it) })
        val step = StepFinder.next(g)!!
        assertThat(step.technique).isEqualTo(Technique.BOX_LINE)
        assertThat(step.houses.toList()).containsExactly(0, Houses.BOX0).inOrder()
        assertThat(step.eliminations.map { it.cell }).containsExactly(cell(2, 1), cell(2, 2), cell(2, 3), cell(3, 1), cell(3, 2), cell(3, 3))
    }

    @Test
    fun noBoxLineWhenTheLineSpansTwoBoxes() {
        val g = grid().without(5, (5..9).map { cell(1, it) })
        assertThat(StepFinder.first(Technique.BOX_LINE, g)).isNull()
    }

    // --- Naked pair / triple ---

    @Test
    fun nakedPairRemovesItsDigitsFromTheHouse() {
        val g = grid().only(cell(1, 1), "12").only(cell(1, 5), "12")
        val step = StepFinder.next(g)!!
        assertThat(step.technique).isEqualTo(Technique.NAKED_PAIR)
        assertThat(step.house).isEqualTo(0)
        assertThat(Masks.format(step.digits)).isEqualTo("12")
        assertThat(step.causeCells.toList()).containsExactly(cell(1, 1), cell(1, 5)).inOrder()
        assertThat(step.eliminations).hasSize(14) // 7 other cells × 2 digits
    }

    @Test
    fun noNakedPairForDifferentPairs() {
        val g = grid().only(cell(1, 1), "12").only(cell(1, 5), "13")
        assertThat(StepFinder.first(Technique.NAKED_PAIR, g)).isNull()
    }

    @Test
    fun nakedTripleWithNoCellHoldingAllThree() {
        val g = grid().only(cell(1, 1), "12").only(cell(1, 5), "23").only(cell(1, 9), "13")
        val step = StepFinder.next(g)!!
        assertThat(step.technique).isEqualTo(Technique.NAKED_TRIPLE)
        assertThat(Masks.format(step.digits)).isEqualTo("123")
        assertThat(step.eliminations).hasSize(18)
    }

    @Test
    fun noNakedTripleWhenTheUnionIsFourDigits() {
        val g = grid().only(cell(1, 1), "12").only(cell(1, 5), "23").only(cell(1, 9), "14")
        assertThat(StepFinder.first(Technique.NAKED_TRIPLE, g)).isNull()
    }

    // --- Hidden pair / triple ---

    @Test
    fun hiddenPairRemovesOtherCandidatesFromItsCells() {
        val g = grid().without(1, row(1) - cell(1, 1) - cell(1, 5)).without(2, row(1) - cell(1, 1) - cell(1, 5))
        val step = StepFinder.next(g)!!
        assertThat(step.technique).isEqualTo(Technique.HIDDEN_PAIR)
        assertThat(Masks.format(step.digits)).isEqualTo("12")
        assertThat(step.causeCells.toList()).containsExactly(cell(1, 1), cell(1, 5)).inOrder()
        assertThat(step.eliminations).hasSize(14)
        assertThat(step.eliminations.none { it.digit == 1 || it.digit == 2 }).isTrue()
    }

    @Test
    fun noHiddenPairWhenTheDigitsSpanThreeCells() {
        val g = grid().without(1, row(1) - cell(1, 1) - cell(1, 5)).without(2, row(1) - cell(1, 1) - cell(1, 9))
        assertThat(StepFinder.all(Technique.HIDDEN_PAIR, g).filter { it.house == 0 }).isEmpty()
    }

    @Test
    fun hiddenTripleInARow() {
        val keep = listOf(cell(1, 1), cell(1, 5), cell(1, 9))
        val g = grid().without(1, row(1) - keep).without(2, row(1) - keep).without(3, row(1) - keep)
        val step = StepFinder.next(g)!!
        assertThat(step.technique).isEqualTo(Technique.HIDDEN_TRIPLE)
        assertThat(Masks.format(step.digits)).isEqualTo("123")
        assertThat(step.eliminations).hasSize(18)
    }

    @Test
    fun noHiddenTripleForFourCells() {
        val keep = listOf(cell(1, 1), cell(1, 5), cell(1, 9))
        val g = grid().without(1, row(1) - keep).without(2, row(1) - keep).without(3, row(1) - keep - cell(1, 7))
        assertThat(StepFinder.all(Technique.HIDDEN_TRIPLE, g).filter { it.house == 0 }).isEmpty()
    }

    // --- X-Wing ---

    @Test
    fun xWingOnRowsRemovesFromTheCoverColumns() {
        val corners = listOf(cell(2, 2), cell(2, 8), cell(5, 2), cell(5, 8))
        val g = grid().without(7, row(2) + row(5) - corners)
        val step = StepFinder.next(g)!!
        assertThat(step.technique).isEqualTo(Technique.X_WING)
        assertThat(step.houses.toList()).containsExactly(1, 4, Houses.COL0 + 1, Houses.COL0 + 7).inOrder()
        assertThat(step.causeCells.toList()).containsExactlyElementsIn(corners).inOrder()
        assertThat(step.eliminations).hasSize(14)
        assertThat(step.eliminations.all { it.digit == 7 && Houses.col(it.cell) in setOf(1, 7) }).isTrue()
    }

    @Test
    fun xWingOnColumns() {
        val corners = listOf(cell(2, 3), cell(2, 6), cell(7, 3), cell(7, 6))
        val cols = (1..9).flatMap { listOf(cell(it, 3), cell(it, 6)) }
        val g = grid().without(4, cols - corners)
        val step = StepFinder.first(Technique.X_WING, g)!!
        assertThat(step.houses.toList()).containsExactly(Houses.COL0 + 2, Houses.COL0 + 5, 1, 6).inOrder()
        assertThat(step.eliminations.all { Houses.row(it.cell) in setOf(1, 6) }).isTrue()
    }

    @Test
    fun noXWingWhenColumnsDiffer() {
        val g = grid().without(7, row(2) - cell(2, 2) - cell(2, 8)).without(7, row(5) - cell(5, 2) - cell(5, 7))
        assertThat(StepFinder.first(Technique.X_WING, g)).isNull()
    }

    // --- Ladder and scan order ---

    @Test
    fun ladderCodesAndTiersArePermanent() {
        assertThat(Technique.entries.map { it.code }.joinToString("")).isEqualTo("abcdefghij")
        assertThat(Technique.entries.map { it.tier }).containsExactly(1, 1, 1, 2, 2, 2, 3, 3, 3, 4).inOrder()
    }

    @Test
    fun scanOrderTakesTheLowestHouseFirst() {
        // Two naked pairs, rows 7 and 3: the row-3 one comes first, and `all` lists them in house order.
        val g = grid().only(cell(7, 1), "45").only(cell(7, 9), "45").only(cell(3, 2), "12").only(cell(3, 8), "12")
        val all = StepFinder.all(Technique.NAKED_PAIR, g)
        assertThat(all.first().house).isEqualTo(2)
        assertThat(all.map { it.house }).isInOrder()
    }

    @Test
    fun stepSearchIsRepeatable() {
        val g = grid().only(cell(7, 1), "45").only(cell(7, 9), "45").only(cell(3, 2), "12").only(cell(3, 8), "12")
        val a = Technique.entries.flatMap { StepFinder.all(it, g.copy()) }
        val b = Technique.entries.flatMap { StepFinder.all(it, g.copy()) }
        assertThat(a).isEqualTo(b)
    }
}
