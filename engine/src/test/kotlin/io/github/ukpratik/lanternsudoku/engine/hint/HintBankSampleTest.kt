// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.engine.hint

import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.lanternsudoku.engine.bank.BankCodec
import io.github.ukpratik.lanternsudoku.engine.game.Action
import io.github.ukpratik.lanternsudoku.engine.game.CellCode
import io.github.ukpratik.lanternsudoku.engine.game.GameReducer
import io.github.ukpratik.lanternsudoku.engine.game.GameState
import io.github.ukpratik.lanternsudoku.engine.game.Rules
import io.github.ukpratik.lanternsudoku.engine.game.Status
import io.github.ukpratik.lanternsudoku.engine.gen.SplitMix64
import io.github.ukpratik.lanternsudoku.engine.model.Level
import io.github.ukpratik.lanternsudoku.engine.model.Masks
import io.github.ukpratik.lanternsudoku.engine.model.Pool
import io.github.ukpratik.lanternsudoku.engine.model.PuzzleId
import io.github.ukpratik.lanternsudoku.engine.model.bankFileName
import io.github.ukpratik.lanternsudoku.engine.technique.Technique
import org.junit.Test
import java.io.File

/**
 * TC-112 through the whole hint flow (US-012): bank puzzles of every file are solved by "Hint → Why → Do it" in the
 * reducer, with random player notes (often wrong) and occasional wrong entries along the way. At every stage 3 the
 * placed digit equals the solution, no shown elimination removes a solution digit, wrong digits are always caught
 * first, and no bank state needs the fallback.
 */
class HintBankSampleTest {
    private val rules = Rules(showMistakes = false)

    @Test
    fun stage3AlwaysPlacesTheSolution_andEliminationsAreTrue() {
        val rnd = SplitMix64(20261010L)
        var hints = 0
        var wrongCaught = 0
        var chains = 0
        val seen = sortedSetOf<Technique>()
        for (pool in Pool.entries) for (level in Level.entries) {
            val records = BankCodec.decodeAll(File("../bank/v1/${bankFileName(pool, level)}").readBytes())
            // 25 records per file, spread over the file: 200 puzzles, about 9,000 hint states.
            for (k in 0 until SAMPLE) {
                val index = k * (records.size / SAMPLE)
                var s = GameState.new(PuzzleId(pool, level, index).toString(), records[index].givens)
                var guard = 0
                while (s.status != Status.COMPLETED) {
                    check(guard++ < 200) { "no progress on ${s.puzzleId}" }
                    s = scribble(s, rnd)
                    val planted = maybeMistake(s, rnd)
                    if (planted != null) {
                        s = planted.first
                        val plan = HintEngine.hint(s)
                        assertThat(plan).isEqualTo(HintPlan.WrongDigit(planted.second, s.value(planted.second)))
                        s = s.act(Action.OpenHint).act(Action.HintNext).act(Action.HintNext).act(Action.CloseHint)
                        assertThat(s.value(planted.second)).isEqualTo(0)
                        wrongCaught++
                        continue
                    }
                    val opened = s.act(Action.OpenHint)
                    val plan = opened.input.hint!!.plan
                    assertThat(plan).isInstanceOf(HintPlan.Logical::class.java) // never the fallback on bank puzzles
                    plan as HintPlan.Logical
                    if (plan.isChain) chains++
                    plan.steps.forEach { seen += it.technique }
                    for (step in plan.steps) {
                        assertThat(step.technique.tier).isAtMost(level.tier)
                        for (e in step.eliminations) assertThat(e.digit).isNotEqualTo(s.solution[e.cell])
                    }
                    val p = plan.placement
                    val after = opened.act(Action.HintNext).act(Action.HintNext)
                    assertThat(after.value(p.cell)).isEqualTo(s.solution[p.cell])
                    // The eliminated candidates are gone from the player's notes; the solution digit never is.
                    for (step in plan.steps) for (e in step.eliminations) {
                        if (after.value(e.cell) == 0) assertThat(after.notes(e.cell) and Masks.bit(e.digit)).isEqualTo(0)
                    }
                    s = if (after.status == Status.COMPLETED) after else after.act(Action.CloseHint)
                    hints++
                }
                assertThat(s.isSolved).isTrue()
            }
        }
        println("hint sample: $hints hints, $chains chains, $wrongCaught wrong digits caught")
        assertThat(hints).isGreaterThan(8_000)
        assertThat(wrongCaught).isGreaterThan(100)
        assertThat(chains).isGreaterThan(0)
        // The sample's hints cover the ladder (a hidden triple is rare in hints: the app's copy test has a fixed one).
        assertThat(seen).containsAtLeastElementsIn(Technique.entries - Technique.HIDDEN_TRIPLE)
    }

    private fun GameState.act(a: Action): GameState = GameReducer.reduce(this, a, rules).state

    /** Random player notes in a few empty cells: sometimes right, often wrong. Notes never change a hint. */
    private fun scribble(s: GameState, rnd: SplitMix64): GameState {
        val cells = s.codes()
        repeat(3) {
            val i = rnd.nextInt(81)
            if (s.value(i) == 0) cells[i] = CellCode.withNotes(cells[i], rnd.nextInt(512))
        }
        return s.copy(cells = cells)
    }

    /** One time in 20, a wrong digit placed as the newest move. */
    private fun maybeMistake(s: GameState, rnd: SplitMix64): Pair<GameState, Int>? {
        if (rnd.nextInt(20) != 0) return null
        val empties = (0 until 81).filter { s.value(it) == 0 }
        if (empties.size < 2) return null
        val i = empties[rnd.nextInt(empties.size)]
        val d = (s.solution[i] % 9) + 1
        val cells = s.codes()
        cells[i] = CellCode.of(value = d, seq = s.seq + 1)
        return s.copy(cells = cells, seq = s.seq + 1) to i
    }

    private companion object {
        const val SAMPLE = 25
    }
}
