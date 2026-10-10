// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.engine.hint

import io.github.ukpratik.lanternsudoku.engine.game.GameState
import io.github.ukpratik.lanternsudoku.engine.model.Grid
import io.github.ukpratik.lanternsudoku.engine.model.Houses
import io.github.ukpratik.lanternsudoku.engine.model.Masks

/**
 * What the board shows for the open hint (design hint-stage-1/2/3, hint-error-first, hint-fallback; LLD §4.10): pure
 * data, so the role mapping is unit-tested and the canvas only draws it.
 *
 * @property house cells tinted as "Where" (stage 1, kept in stage 2).
 * @property target the answer cell (thick ring), or -1.
 * @property cause the reason cells (hatch + dashed outline).
 * @property struck per cell, the candidates the step rules out (struck through).
 * @property key the digits the step is about: drawn in a disc with a halo in the cause cells.
 * @property targetKey the answer digit's candidate, discs in the target cell (0 when none).
 * @property candidates per cell, the candidates to show for this hint only (-1 = show the player's own notes).
 * @property placed the cell placed by this hint (small amber dot until the panel closes), or -1.
 */
class HintView(
    val house: BooleanArray,
    val target: Int,
    val cause: BooleanArray,
    val struck: IntArray,
    val key: Int,
    val candidates: IntArray,
    val placed: Int,
    val targetKey: Int = 0,
) {
    fun isRole(cell: Int): Boolean = cell == target || cause[cell] || struck[cell] != 0

    companion object {
        /** The view of [s]'s open hint, or null when no hint panel is open. */
        fun of(s: GameState): HintView? {
            val session = s.input.hint ?: return null
            val house = BooleanArray(Grid.CELLS)
            val cause = BooleanArray(Grid.CELLS)
            val struck = IntArray(Grid.CELLS)
            val candidates = IntArray(Grid.CELLS) { -1 }
            fun tint(h: Int) = Houses.HOUSE_CELLS[h].forEach { house[it] = true }
            return when (val plan = session.plan) {
                is HintPlan.WrongDigit -> {
                    tint(HintEngine.boxOf(plan.cell))
                    HintView(house, if (session.stage >= 2) plan.cell else -1, cause, struck, 0, candidates, -1)
                }
                is HintPlan.Fallback -> HintView(house, plan.placement.cell, cause, struck, 0, candidates, plan.placement.cell)
                is HintPlan.Logical -> when (session.stage) {
                    1 -> {
                        HintEngine.whereHouses(plan.steps[0]).forEach(::tint)
                        HintView(house, -1, cause, struck, 0, candidates, -1)
                    }
                    2 -> {
                        val k = if (plan.isChain) session.step else 0
                        val step = plan.steps[k]
                        HintEngine.whereHouses(step).forEach(::tint)
                        step.causeCells.forEach { cause[it] = true }
                        for (e in step.eliminations) struck[e.cell] = struck[e.cell] or Masks.bit(e.digit)
                        val target = plan.placement.cell
                        // The candidates the step relies on, where the player's notes are missing or different.
                        val before = plan.candidatesBefore[k]
                        for (i in 0 until Grid.CELLS) {
                            if (!(i == target || cause[i] || struck[i] != 0) || s.value(i) != 0) continue
                            if (s.notes(i) != before[i]) candidates[i] = before[i]
                        }
                        val answer = Masks.bit(plan.placement.digit)
                        val key = if (step.technique.isSingle) answer else step.digits
                        HintView(house, target, cause, struck, key, candidates, -1, answer)
                    }
                    else -> HintView(house, plan.placement.cell, cause, struck, 0, candidates, plan.placement.cell)
                }
            }
        }
    }
}
