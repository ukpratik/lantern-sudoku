// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.engine.hint

/**
 * The open hint panel (US-012): transient, like the rest of `InputState`, so a resumed game lands on a neutral board.
 *
 * @property stage 1 Where, 2 Why, 3 Do it (the digit has been placed or removed).
 * @property deepest the deepest stage this hint reached; the hint is counted once, at that stage (LLD §4.4 Counting).
 * @property step the step shown in stage 2 of a chain (D-031), 0-based; always 0 otherwise.
 * @property notesRemoved stage 3: how many cells' notes lost a candidate when the digit was placed (design "Removed from N notes").
 */
data class HintSession(
    val plan: HintPlan,
    val stage: Int,
    val deepest: Int = stage,
    val step: Int = 0,
    val notesRemoved: Int = 0,
) {
    init {
        require(stage in 1..3 && deepest in stage..3) { "stage $stage, deepest $deepest" }
    }

    /** Steps walked in stage 2: every step of a chain, else one combined view. */
    val stepCount: Int get() = (plan as? HintPlan.Logical)?.takeIf { it.isChain }?.steps?.size ?: 1
}
