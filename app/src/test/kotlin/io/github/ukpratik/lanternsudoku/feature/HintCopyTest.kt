// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.feature

import android.app.Application
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.ukpratik.lanternsudoku.engine.game.Action
import io.github.ukpratik.lanternsudoku.engine.game.GameReducer
import io.github.ukpratik.lanternsudoku.engine.game.GameState
import io.github.ukpratik.lanternsudoku.engine.game.Rules
import io.github.ukpratik.lanternsudoku.engine.game.CellCode
import io.github.ukpratik.lanternsudoku.engine.hint.HintPlan
import io.github.ukpratik.lanternsudoku.engine.hint.HintView
import io.github.ukpratik.lanternsudoku.engine.model.Grid
import io.github.ukpratik.lanternsudoku.engine.model.Houses
import io.github.ukpratik.lanternsudoku.engine.model.Masks
import io.github.ukpratik.lanternsudoku.engine.technique.Technique
import io.github.ukpratik.lanternsudoku.feature.game.HintCopy
import io.github.ukpratik.lanternsudoku.feature.game.HintText
import io.github.ukpratik.lanternsudoku.feature.game.cellLabels
import io.github.ukpratik.lanternsudoku.engine.game.Highlights
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Hint copy (US-012) generated for real bank positions, one per technique: every stage resolves (TC-109), no
 * placeholder is left, the text names the step's real houses and digits, and nothing says "guess" (TC-110).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class HintCopyTest {
    private val res: Resources = ApplicationProvider.getApplicationContext<Application>().resources
    private val rules = Rules()

    private fun GameState.act(a: Action) = GameReducer.reduce(this, a, rules).state

    private fun HintText.all() = listOfNotNull(status, eyebrow, title, chip, stepLabel, body, meta, primary, secondary, announcement) + legend.map { it.text }

    private fun assertClean(t: HintText, where: String) {
        for (s in t.all()) {
            assertWithMessage("$where: '$s'").that(s).isNotEmpty()
            assertWithMessage("$where: unfilled placeholder in '$s'").that(s).doesNotContainMatch("%|\\$[sd]")
            assertWithMessage("$where: '$s'").that(s.lowercase()).doesNotContain("guess")
            assertWithMessage("$where: '$s'").that(s.lowercase()).doesNotContain("trial")
        }
    }

    private fun house(h: Int) = HintCopy.houseName(res, h)

    @Test
    fun TC109_everyTechnique_hasStage1_2_3Copy_namingTheRealHousesAndDigits() {
        val covered = sortedSetOf<Technique>()
        for (f in HintFixtures.ALL) {
            val s1 = f.state().act(Action.OpenHint)
            val plan = s1.input.hint!!.plan as HintPlan.Logical
            val step = plan.steps[0]
            assertWithMessage("${f.id} ${f.note}").that(step.technique).isEqualTo(f.technique)
            covered += step.technique
            val digits = Masks.digits(step.digits)

            // Stage 1: where only; names the house, never the technique or the answer.
            val t1 = HintCopy.of(res, s1)!!
            assertClean(t1, "${f.technique} stage 1")
            if (step.technique != Technique.X_WING) assertThat(t1.title).contains(house(step.house))
            assertThat(t1.all().joinToString(" ")).doesNotContain(HintCopy.techniqueName(res, step.technique))

            // Stage 2: technique name, explanation with the real houses and digits, then what it unlocks.
            val s2 = s1.act(Action.HintNext)
            val t2 = HintCopy.of(res, s2)!!
            assertClean(t2, "${f.technique} stage 2")
            assertThat(t2.title).isEqualTo(HintCopy.techniqueName(res, step.technique))
            assertThat(t2.chip).isNotNull()
            val sentences = t2.body.split(Regex("(?<=[.:])\\s+(?=[A-Z])")).size
            assertWithMessage("${f.technique}: ${t2.body}").that(sentences).isAtMost(3)
            // A naked single is about one cell (the ringed one), not a house.
            if (step.technique != Technique.NAKED_SINGLE) for (h in step.houses) assertThat(t2.body).contains(houseNameIn(step.technique, h, step.houses))
            for (d in digits) assertThat(t2.body).contains("$d")
            assertThat(t2.body).contains("${plan.placement.digit}")
            if (plan.steps.size == 2) {
                val single = plan.steps[1]
                if (single.technique == Technique.HIDDEN_SINGLE) assertThat(t2.body).contains("in ${house(single.house)}: the ringed cell")
                if (single.technique == Technique.NAKED_SINGLE) assertThat(t2.body).endsWith("one possible digit: ${plan.placement.digit}.")
            }
            assertThat(t2.announcement).contains(t2.body)

            // Stage 3: the placed digit and its cell.
            val s3 = s2.act(Action.HintNext)
            val t3 = HintCopy.of(res, s3)!!
            assertClean(t3, "${f.technique} stage 3")
            assertThat(t3.title).isEqualTo("${plan.placement.digit} goes in ${HintCopy.cellName(res, plan.placement.cell)}")
            assertThat(s3.value(plan.placement.cell)).isEqualTo(s3.solution[plan.placement.cell])
        }
        assertThat(covered).containsExactlyElementsIn(Technique.entries)
    }

    /** X-Wing names its lines in pairs ("rows 1 and 9"); everything else names each house. */
    private fun houseNameIn(t: Technique, h: Int, houses: IntArray): String {
        if (t != Technique.X_WING) return house(h)
        val pair = if (h == houses[0] || h == houses[1]) houses.copyOfRange(0, 2) else houses.copyOfRange(2, 4)
        val n = pair.map { if (Houses.isRow(it)) it - Houses.ROW0 + 1 else it - Houses.COL0 + 1 }
        return if (Houses.isRow(pair[0])) "rows ${n[0]} and ${n[1]}" else "columns ${n[0]} and ${n[1]}"
    }

    @Test
    fun designExample_pointingInBox8_readsLikeTheDesign() {
        val f = HintFixtures.ALL.first { it.id == "N-M-0000" }
        val t = HintCopy.of(res, f.state().act(Action.OpenHint).act(Action.HintNext))!!
        assertThat(t.body).isEqualTo(
            "In box 1, the 9 can only go in the dashed cells, and they all lie in column 3. So no other cell in column 3 can be 9. " +
                "That leaves one place for 9 in row 8: the ringed cell.",
        )
    }

    @Test
    fun chain_eachStepHasItsOwnCopy_stepKofN() {
        val s = HintFixtures.CHAIN.state().act(Action.OpenHint).act(Action.HintNext)
        val plan = s.input.hint!!.plan as HintPlan.Logical
        assertThat(plan.isChain).isTrue()
        val n = plan.steps.size
        val bodies = (0 until n).map { k ->
            val t = HintCopy.of(res, s.act(Action.HintStep(k)))!!
            assertClean(t, "chain step $k")
            assertThat(t.stepLabel).isEqualTo("Step ${k + 1} of $n")
            assertThat(t.title).isEqualTo(HintCopy.techniqueName(res, plan.steps[k].technique))
            t.body
        }
        assertThat(bodies.toSet()).hasSize(n)
        assertThat(bodies.last()).startsWith("With the earlier steps done")
        val done = HintCopy.of(res, s.act(Action.HintNext))!!
        assertClean(done, "chain stage 3")
        assertThat(done.body).startsWith("Pointing in box 8 and ${n - 1} more steps showed where the ${plan.placement.digit} goes.")
    }

    @Test
    fun errorFirst_andFallback_copy() {
        val f = HintFixtures.ALL.first()
        val base = f.state()
        val cell = (0 until 81).first { base.value(it) == 0 }
        val wrongDigit = (base.solution[cell] % 9) + 1
        val cells = base.codes().also { it[cell] = CellCode.of(value = wrongDigit, seq = 5) }
        var s = base.copy(cells = cells, seq = 5).act(Action.OpenHint)
        val t1 = HintCopy.of(res, s)!!
        assertClean(t1, "wrong 1")
        assertThat(t1.title).isEqualTo("A digit on the board is wrong")
        s = s.act(Action.HintNext)
        val t2 = HintCopy.of(res, s)!!
        assertClean(t2, "wrong 2")
        assertThat(t2.title).isEqualTo("This $wrongDigit doesn't fit here")
        assertThat(t2.body).contains(HintCopy.cellName(res, cell))
        s = s.act(Action.HintNext)
        val t3 = HintCopy.of(res, s)!!
        assertClean(t3, "wrong 3")
        assertThat(t3.title).isEqualTo("Removed the $wrongDigit from ${HintCopy.cellName(res, cell)}")

        val hard = Grid.parse("8..........36......7..9.2...5...7.......457.....1...3...1....68..85...1..9....4..")
        val fb = GameState.new("X", hard).act(Action.OpenHint)
        val tf = HintCopy.of(res, fb)!!
        assertClean(tf, "fallback")
        assertThat(tf.eyebrow).isEqualTo("Hint · no step to explain here")
        val p = (fb.input.hint!!.plan as HintPlan.Fallback).placement
        assertThat(tf.title).isEqualTo("${p.digit} goes in ${HintCopy.cellName(res, p.cell)}")
    }

    @Test
    fun talkBack_roleCellsSayTheirRole() {
        val f = HintFixtures.ALL.first { it.id == "N-M-0000" }
        val s = f.state().act(Action.OpenHint).act(Action.HintNext)
        val view = HintView.of(s)!!
        val labels = cellLabels(res, s, Highlights.of(s, showMistakes = true), view)
        assertThat(labels.state(view.target)).contains("answer cell")
        val cause = (0 until 81).first { view.cause[it] }
        assertThat(labels.state(cause)).contains("reason")
        val struck = (0 until 81).first { view.struck[it] != 0 }
        assertThat(labels.state(struck)).contains("9 ruled out")
    }

    @Test
    fun TC110_noStringSaysGuessOrTrialAndError() {
        val dir = File("src/main/res")
        val files = dir.walkTopDown().filter { it.isFile && it.name.startsWith("strings") && it.extension == "xml" }.toList()
        assertThat(files).isNotEmpty()
        for (f in files) {
            val text = f.readText().lowercase()
            for (word in listOf("guess", "trial and error", "trial-and-error")) {
                assertWithMessage("${f.path} contains '$word'").that(text).doesNotContain(word)
            }
        }
    }
}
