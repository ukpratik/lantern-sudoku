// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.engine.game

import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.lanternsudoku.engine.game.Action.Back
import io.github.ukpratik.lanternsudoku.engine.game.Action.CloseHint
import io.github.ukpratik.lanternsudoku.engine.game.Action.HintNext
import io.github.ukpratik.lanternsudoku.engine.game.Action.HintStage
import io.github.ukpratik.lanternsudoku.engine.game.Action.HintStep
import io.github.ukpratik.lanternsudoku.engine.game.Action.OpenHint
import io.github.ukpratik.lanternsudoku.engine.game.Action.Redo
import io.github.ukpratik.lanternsudoku.engine.game.Action.TapCell
import io.github.ukpratik.lanternsudoku.engine.game.Action.TapDigit
import io.github.ukpratik.lanternsudoku.engine.game.Action.TapErase
import io.github.ukpratik.lanternsudoku.engine.game.Action.Undo
import io.github.ukpratik.lanternsudoku.engine.hint.HintEngine
import io.github.ukpratik.lanternsudoku.engine.hint.HintPlan
import io.github.ukpratik.lanternsudoku.engine.hint.HintView
import io.github.ukpratik.lanternsudoku.engine.model.Grid
import io.github.ukpratik.lanternsudoku.engine.model.Houses
import io.github.ukpratik.lanternsudoku.engine.model.Masks
import io.github.ukpratik.lanternsudoku.engine.save.DecodeResult
import io.github.ukpratik.lanternsudoku.engine.save.StateCodec
import io.github.ukpratik.lanternsudoku.engine.solve.CandidateGrid
import org.junit.Test

/** The hint panel in the reducer (US-012, LLD §4.4): stages, counting, error first, fallback, chains, undo. */
class HintReducerTest {
    private val fresh = Boards.fresh()

    private fun Transition.hint() = requireNotNull(state.input.hint)

    @Test
    fun opening_releasesInput_countsStage1_flagsTheTargetBox() {
        val busy = fresh.then(TapDigit(5)).then(TapErase)
        val t = busy.act(OpenHint)
        val h = t.hint()
        assertThat(h.stage).isEqualTo(1)
        assertThat(h.plan).isInstanceOf(HintPlan.Logical::class.java)
        assertThat(t.state.input.lock).isNull()
        assertThat(t.state.input.eraseMode).isFalse()
        assertThat(t.state.counters).isEqualTo(Counters(hints1 = 1))
        assertThat(t.state.boxFlags and BoxFlags.hint(Houses.box(h.plan.target))).isNotEqualTo(0)
        assertThat(t.effects).containsExactly(Effect.Persist)
        // The board itself didn't change.
        assertThat(t.state.codes()).isEqualTo(fresh.codes())
    }

    @Test
    fun TC111_eachHintIsCountedOnceAtItsDeepestStage() {
        var s = fresh.then(OpenHint).then(CloseHint) // stage 1
        s = s.then(OpenHint).then(HintNext).then(CloseHint) // stage 2
        s = s.then(OpenHint).then(HintNext).then(HintNext).then(CloseHint) // stage 3
        assertThat(s.counters).isEqualTo(Counters(hints1 = 1, hints2 = 1, hints3 = 1))
    }

    @Test
    fun goingBackAStageKeepsTheDeepestCount() {
        val s = fresh.then(OpenHint).then(HintNext).then(HintStage(1))
        assertThat(s.input.hint!!.stage).isEqualTo(1)
        assertThat(s.input.hint!!.deepest).isEqualTo(2)
        assertThat(s.counters).isEqualTo(Counters(hints2 = 1))
        val again = s.then(HintStage(2))
        assertThat(again.counters).isEqualTo(Counters(hints2 = 1))
    }

    @Test
    fun stage3_placesTheSolutionDigit_tidiesNotes_isOneUndoStep() {
        val plan = HintEngine.hint(fresh) as HintPlan.Logical
        val p = plan.placement
        // Player notes: the eliminated candidates, the digit in a peer, and something in the target cell.
        var s = fresh
        val elims = plan.steps.flatMap { it.eliminations }
        for (e in elims) s = s.withNotes(e.cell, e.digit, (e.digit % 9) + 1)
        val peer = Houses.PEERS[p.cell].first { !s.isGiven(it) && it != p.cell && elims.none { e -> e.cell == it } }
        s = s.withNotes(peer, p.digit).withNotes(p.cell, 1, 2, 3)
        val before = s
        val t = s.then(OpenHint).then(HintNext).act(HintNext)
        val after = t.state
        assertThat(after.value(p.cell)).isEqualTo(after.solution[p.cell])
        assertThat(after.notes(peer) and Masks.bit(p.digit)).isEqualTo(0)
        for (e in elims) assertThat(after.notes(e.cell) and Masks.bit(e.digit)).isEqualTo(0)
        assertThat(after.undo).hasSize(before.undo.size + 1)
        assertThat(after.counters).isEqualTo(Counters(hints3 = 1))
        assertThat(t.hint().stage).isEqualTo(3)
        assertThat(t.hint().notesRemoved).isAtLeast(1)
        assertThat(t.effects).containsExactly(Effect.Persist, Effect.Haptic(HapticKind.CONFIRM))
        assertThat(HintView.of(after)!!.placed).isEqualTo(p.cell)

        // TC-068: one Undo (the panel's) reverts the placement and every note removal; the count isn't refunded.
        val undone = after.then(Undo)
        assertThat(undone.codes()).isEqualTo(before.codes())
        assertThat(undone.input.hint).isNull()
        assertThat(undone.counters.hints3).isEqualTo(1)
        // Redo puts it all back.
        assertThat(undone.then(Redo).codes()).isEqualTo(after.codes())
    }

    @Test
    fun stage3WithAutoCleanupOff_keepsPeerNotes_butRemovesTheStepsEliminations() {
        val plan = HintEngine.hint(fresh) as HintPlan.Logical
        val p = plan.placement
        val peer = Houses.PEERS[p.cell].first { !fresh.isGiven(it) && plan.steps.none { s -> s.eliminations.any { e -> e.cell == it } } }
        val off = Rules(autoCleanup = false)
        val s = fresh.withNotes(peer, p.digit).then(OpenHint, off).then(HintNext, off).then(HintNext, off)
        assertThat(s.notes(peer)).isEqualTo(Masks.bit(p.digit))
    }

    @Test
    fun TC099_errorFirst_targetsTheMostRecentlyPlacedWrongDigit() {
        val (a, b) = Boards.empties.take(2)
        val s = fresh.then(TapCell(a)).then(TapDigit(fresh.wrong(a))).then(TapCell(a))
            .then(TapCell(b)).then(TapDigit(fresh.wrong(b))).then(TapCell(b))
        val plan = HintEngine.hint(s)
        assertThat(plan).isEqualTo(HintPlan.WrongDigit(b, fresh.wrong(b)))
        // TC-100: erase B → the earlier one.
        val erased = s.then(TapCell(b)).then(TapErase).then(TapCell(b))
        assertThat(HintEngine.hint(erased)).isEqualTo(HintPlan.WrongDigit(a, fresh.wrong(a)))
    }

    @Test
    fun TC102_noTechniqueHintWhileAnyMistakeExists() {
        // A wrong digit that clashes with nothing, far from any step; also with Show mistakes off.
        for (i in Boards.empties) {
            val d = fresh.wrongNoConflict(i) ?: continue
            val s = fresh.withEntries(i to d)
            assertThat(HintEngine.hint(s)).isEqualTo(HintPlan.WrongDigit(i, d))
        }
    }

    @Test
    fun errorFirstStages_boxThenCellThenRemove_countedAsStage3() {
        val a = Boards.empties[0]
        val s = fresh.withEntries(a to fresh.wrong(a))
        val s1 = s.then(OpenHint, Rules(showMistakes = false))
        val v1 = HintView.of(s1)!!
        assertThat((0 until 81).filter { v1.house[it] }).isEqualTo(Houses.HOUSE_CELLS[Houses.BOX0 + Houses.box(a)].toList())
        assertThat(v1.target).isEqualTo(-1)
        val s2 = s1.then(HintNext)
        assertThat(HintView.of(s2)!!.target).isEqualTo(a)
        val t3 = s2.act(HintNext)
        assertThat(t3.state.value(a)).isEqualTo(0)
        assertThat(t3.state.counters).isEqualTo(Counters(hints3 = 1))
        assertThat(t3.state.undo).hasSize(1)
        // Afterwards the next hint is a technique hint.
        assertThat(HintEngine.hint(t3.state.then(CloseHint))).isInstanceOf(HintPlan.Logical::class.java)
        // Undo puts the wrong digit back.
        assertThat(t3.state.then(Undo).value(a)).isEqualTo(fresh.wrong(a))
    }

    @Test
    fun TC107_fallback_revealsTheFewestCandidatesCell() {
        // A non-bank board beyond the ladder (no step within the chain limit): the fallback reveals one digit.
        val hard = Grid.parse("8..........36......7..9.2...5...7.......457.....1...3...1....68..85...1..9....4..")
        val s = GameState.new("X", hard)
        val plan = HintEngine.hint(s)
        assertThat(plan).isInstanceOf(HintPlan.Fallback::class.java)
        plan as HintPlan.Fallback
        val c = CandidateGrid.of(hard)
        val empties = (0 until 81).filter { hard[it] == 0 }
        val fewest = empties.minOf { Integer.bitCount(c.cand[it]) }
        assertThat(plan.placement.cell).isEqualTo(empties.first { Integer.bitCount(c.cand[it]) == fewest })
        assertThat(plan.placement.digit).isEqualTo(s.solution[plan.placement.cell])

        // In the reducer it places at once and counts as stage 3 (TC-108's engine part).
        val t = s.act(OpenHint)
        assertThat(t.state.value(plan.placement.cell)).isEqualTo(plan.placement.digit)
        assertThat(t.state.counters).isEqualTo(Counters(hints3 = 1))
        assertThat(t.hint().stage).isEqualTo(3)
        assertThat(t.state.then(Undo).value(plan.placement.cell)).isEqualTo(0)
    }

    @Test
    fun TC217_backClosesThePanelFirst_andTheBoardIsInertWhileOpen() {
        val open = fresh.then(TapCell(Boards.empties[0])).then(OpenHint)
        assertThat(open.input.selection).isNull() // released on open
        assertThat(open.act(TapCell(Boards.empties[1])).state).isEqualTo(open)
        assertThat(open.act(TapDigit(4)).state).isEqualTo(open)
        assertThat(open.act(Undo).state).isEqualTo(open) // only stage 3 has Undo
        val back = open.act(Back)
        assertThat(back.handled).isTrue()
        assertThat(back.state.input.hint).isNull()
        assertThat(back.state.act(Back).handled).isFalse() // then the screen leaves
    }

    @Test
    fun TC103_stage2ShowsTrueCandidatesWhereNotesAreWrong_andLeavesThePlayersNotesAlone() {
        val plan = HintEngine.hint(fresh) as HintPlan.Logical
        val step = plan.steps[0]
        val roleCells = (step.causeCells.toList() + step.eliminations.map { it.cell } + plan.placement.cell).filter { fresh.value(it) == 0 }.distinct()
        // Wrong notes in every role cell: a false 1-2 pair, missing the real candidates.
        var s = fresh
        for (i in roleCells) s = s.withNotes(i, 1, 2)
        val opened = s.then(OpenHint).then(HintNext)
        val v = HintView.of(opened)!!
        for (i in roleCells) {
            val truth = plan.candidatesBefore[0][i]
            if (truth != Masks.bit(1) or Masks.bit(2)) assertThat(v.candidates[i]).isEqualTo(truth)
        }
        // Cells outside the step keep the player's notes, and closing changes no note.
        assertThat((0 until 81).filter { it !in roleCells }.all { v.candidates[it] == -1 }).isTrue()
        assertThat(opened.then(CloseHint).codes()).isEqualTo(s.codes())
    }

    @Test
    fun stage2Roles_matchTheStep() {
        val plan = HintEngine.hint(fresh) as HintPlan.Logical
        val v = HintView.of(fresh.then(OpenHint).then(HintNext))!!
        val step = plan.steps[0]
        assertThat(v.target).isEqualTo(plan.placement.cell)
        assertThat((0 until 81).filter { v.cause[it] }).isEqualTo(step.causeCells.toList())
        for (e in step.eliminations) assertThat(v.struck[e.cell] and Masks.bit(e.digit)).isNotEqualTo(0)
        // Stage 1 shows only where: no target, cause or struck candidate.
        val w = HintView.of(fresh.then(OpenHint))!!
        assertThat(w.target).isEqualTo(-1)
        assertThat(w.cause.none { it }).isTrue()
        assertThat(w.struck.all { it == 0 }).isTrue()
        assertThat(w.house.count { it }).isAtLeast(9)
    }

    @Test
    fun aHintThatFillsTheLastCellCompletesTheGame() {
        val last = Boards.empties.last()
        val s = fresh.solvedExcept(last)
        val t = s.then(OpenHint).then(HintNext).act(HintNext)
        assertThat(t.state.status).isEqualTo(Status.COMPLETED)
        assertThat(t.state.input.hint).isNull()
        assertThat(t.effects).contains(Effect.Completed)
    }

    @Test
    fun hintCountsArePersistedWithTheGame_butTheOpenPanelIsNot() {
        val s = fresh.then(OpenHint).then(CloseHint).then(OpenHint).then(HintNext).then(HintNext)
        val back = (StateCodec.decode(StateCodec.encode(s)) as DecodeResult.Ok).state
        assertThat(back.counters).isEqualTo(Counters(hints1 = 1, hints3 = 1))
        assertThat(back.input.hint).isNull()
    }

    @Test
    fun chainSteps_walkOneAtATime() {
        val chain = ChainFixture.state()
        val s = chain.then(OpenHint).then(HintNext)
        val h = s.input.hint!!
        val plan = h.plan as HintPlan.Logical
        assertThat(plan.isChain).isTrue()
        assertThat(h.stepCount).isEqualTo(plan.steps.size)
        for (k in 0 until h.stepCount) {
            val at = s.then(HintStep(k))
            assertThat(at.input.hint!!.step).isEqualTo(k)
            val v = HintView.of(at)!!
            assertThat((0 until 81).filter { v.cause[it] }).isEqualTo(plan.steps[k].causeCells.toList())
            for (e in plan.steps[k].eliminations) assertThat(v.struck[e.cell] and Masks.bit(e.digit)).isNotEqualTo(0)
        }
        assertThat(s.act(HintStep(h.stepCount)).state).isEqualTo(s) // out of range
        val done = s.then(HintStep(1)).then(HintNext)
        assertThat(done.value(plan.placement.cell)).isEqualTo(done.solution[plan.placement.cell])
        assertThat(done.counters).isEqualTo(Counters(hints3 = 1))
    }
}
