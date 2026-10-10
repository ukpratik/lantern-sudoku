// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.engine.game

import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.lanternsudoku.engine.game.Action.Back
import io.github.ukpratik.lanternsudoku.engine.game.Action.KeepLooking
import io.github.ukpratik.lanternsudoku.engine.game.Action.KeepPlaying
import io.github.ukpratik.lanternsudoku.engine.game.Action.Restart
import io.github.ukpratik.lanternsudoku.engine.game.Action.ShowThem
import io.github.ukpratik.lanternsudoku.engine.game.Action.TapCell
import io.github.ukpratik.lanternsudoku.engine.game.Action.TapDigit
import io.github.ukpratik.lanternsudoku.engine.game.Action.TapErase
import io.github.ukpratik.lanternsudoku.engine.game.Action.Undo
import io.github.ukpratik.lanternsudoku.engine.model.Houses
import org.junit.Test

/** US-010 (mistakes, limit; PRD v1.3), US-011 (Almost!), completion (PRD §5) and Restart. */
class MistakesTest {
    private val fresh = Boards.fresh()
    private val empties = Boards.empties
    private val showOn = Rules(showMistakes = true)
    private val showOff = Rules(showMistakes = false)
    private val limit3 = Rules(showMistakes = true, mistakeLimit = 3)

    private fun GameState.placeWrong(i: Int, rules: Rules): Transition = then(TapCell(i), rules).act(TapDigit(wrong(i)), rules)

    private fun GameState.placeRight(i: Int, rules: Rules = showOn): GameState = then(TapCell(i), rules).then(TapDigit(right(i)), rules)

    @Test
    fun TC080_defaults_showMistakesOn_limitOff() {
        assertThat(Rules()).isEqualTo(Rules(showMistakes = true, mistakeLimit = 0, autoCleanup = true))
    }

    @Test
    fun TC082_wrongDigitIsCountedAndMarked_evenWithoutAConflict() {
        val i = empties.first { fresh.wrongNoConflict(it) != null }
        val d = fresh.wrongNoConflict(i)!!
        val s = fresh.then(TapCell(i)).then(TapDigit(d), showOn)
        assertThat(s.counters.mistakes).isEqualTo(1)
        val h = Highlights.of(s, showMistakes = true)
        assertThat(h.has(i, BoardHighlights.MISTAKE)).isTrue()
        assertThat(h.has(i, BoardHighlights.CONFLICT)).isFalse()
        assertThat(s.boxFlags and BoxFlags.mistake(Houses.box(i))).isNotEqualTo(0)
    }

    @Test
    fun TC090_v13_showOff_mistakesStillCounted_butNotMarked() {
        val s = fresh.placeWrong(empties[0], showOff).state
        assertThat(s.counters.mistakes).isEqualTo(1)
        assertThat(Highlights.of(s, showMistakes = false).has(empties[0], BoardHighlights.MISTAKE)).isFalse()
    }

    @Test
    fun TC084_limitOff_twentyMistakesNeverEndTheGame() {
        var s = fresh
        repeat(20) { k ->
            val t = s.placeWrong(empties[k], showOn)
            assertThat(t.effects.filterIsInstance<Effect.OpenPanel>()).isEmpty()
            s = t.state
        }
        assertThat(s.status).isEqualTo(Status.PLAYING)
        assertThat(s.counters.mistakes).isEqualTo(20)
    }

    @Test
    fun TC085_limit3_tripsExactlyAtTheThird_releasesLock_boardInert() {
        var s = fresh
        repeat(2) { k -> s = s.placeWrong(empties[k], limit3).state }
        assertThat(s.status).isEqualTo(Status.PLAYING)
        // Third mistake via a digit lock: the panel opens and the lock is released.
        val target = empties[2]
        val t = s.then(Back, limit3).then(TapDigit(s.wrong(target)), limit3).act(TapCell(target), limit3)
        assertThat(t.state.status).isEqualTo(Status.LIMIT)
        assertThat(t.effects).contains(Effect.OpenPanel(Panel.LIMIT))
        assertThat(t.state.input.lock).isNull()
        // Board taps are inert until a choice is made.
        val inert = t.state.act(TapCell(empties[3]), limit3)
        assertThat(inert.state).isEqualTo(t.state)
        assertThat(t.state.act(Undo, limit3).state).isEqualTo(t.state)
    }

    @Test
    fun TC086_keepPlaying_turnsLimitOffForThisGame() {
        var s = fresh
        repeat(3) { k -> s = s.placeWrong(empties[k], limit3).state }
        assertThat(s.status).isEqualTo(Status.LIMIT)
        s = s.then(KeepPlaying, limit3)
        assertThat(s.status).isEqualTo(Status.PLAYING)
        assertThat(s.overrides.limitOff).isTrue()
        repeat(3) { k -> s = s.placeWrong(empties[3 + k], limit3).state }
        assertThat(s.status).isEqualTo(Status.PLAYING)
        assertThat(s.counters.mistakes).isEqualTo(6)
    }

    @Test
    fun TC088_backOnLimitPanel_isKeepPlaying() {
        var s = fresh
        repeat(3) { k -> s = s.placeWrong(empties[k], limit3).state }
        val t = s.act(Back, limit3)
        assertThat(t.handled).isTrue()
        assertThat(t.state.status).isEqualTo(Status.PLAYING)
        assertThat(t.state.overrides.limitOff).isTrue()
    }

    @Test
    fun TC087_restart_clearsEntriesNotesCountersAndHistory_samePuzzle() {
        var s = fresh.withNotes(empties[5], 1, 2)
        repeat(3) { k -> s = s.placeWrong(empties[k], limit3).state }
        val t = s.act(Restart, limit3)
        assertThat(t.state).isEqualTo(Boards.fresh())
        assertThat(t.state.puzzleId).isEqualTo(s.puzzleId)
        assertThat(t.effects).containsExactly(Effect.Persist, Effect.Restarted).inOrder()
    }

    @Test
    fun TC089_loweringTheLimitMidGame_neverEndsItRetroactively() {
        var s = fresh
        repeat(4) { k -> s = s.placeWrong(empties[k], showOn).state }
        // The limit is now 3 but nothing happens until the next mistake.
        val notWrong = s.placeRight(empties[4], limit3)
        assertThat(notWrong.status).isEqualTo(Status.PLAYING)
        val fifth = notWrong.placeWrong(empties[5], limit3)
        assertThat(fifth.state.status).isEqualTo(Status.LIMIT)
    }

    @Test
    fun TC091_v13_showOff_limitIsSuspended() {
        val rules = Rules(showMistakes = false, mistakeLimit = 3)
        var s = fresh
        repeat(3) { k -> s = s.placeWrong(empties[k], rules).state }
        assertThat(s.status).isEqualTo(Status.PLAYING)
        assertThat(s.counters.mistakes).isEqualTo(3)
    }

    // ---- US-011 Almost! ------------------------------------------------------------------------------------------

    /** Every empty cell solved except [wrongCell] (wrong) and [last] (empty). */
    private fun almostFull(wrongCell: Int, last: Int): GameState =
        fresh.solvedExcept(wrongCell, last).withEntries(wrongCell to fresh.wrong(wrongCell))

    private val wrongCell = empties[10]
    private val last = empties[20]

    @Test
    fun TC092_fullButWrong_withShowOff_opensAlmost() {
        val t = almostFull(wrongCell, last).then(TapCell(last), showOff).act(TapDigit(fresh.right(last)), showOff)
        assertThat(t.state.status).isEqualTo(Status.ALMOST)
        assertThat(t.effects).contains(Effect.OpenPanel(Panel.ALMOST))
        assertThat(t.effects).doesNotContain(Effect.Completed)
    }

    @Test
    fun TC093_showThem_marksMistakesForThisGame() {
        val almost = almostFull(wrongCell, last).then(TapCell(last), showOff).then(TapDigit(fresh.right(last)), showOff)
        val shown = almost.then(ShowThem, showOff)
        assertThat(shown.status).isEqualTo(Status.PLAYING)
        assertThat(shown.overrides.showMistakes).isTrue()
    }

    @Test
    fun TC094_keepLooking_closes_andReappearsOnlyWhenFullAgainAfterAChange() {
        val almost = almostFull(wrongCell, last).then(TapCell(last), showOff).then(TapDigit(fresh.right(last)), showOff)
        val looking = almost.then(KeepLooking, showOff)
        assertThat(looking.status).isEqualTo(Status.PLAYING)
        // A tap that changes nothing doesn't reopen it.
        val tapped = looking.act(TapCell(empties[0]), showOff)
        assertThat(tapped.state.status).isEqualTo(Status.PLAYING)
        // Erase a cell, place a wrong digit again: full and wrong again → Almost! again.
        val other = empties[30]
        val again = tapped.state.then(TapCell(other), showOff).then(TapErase, showOff)
            .then(TapDigit(fresh.wrong(other)), showOff)
        assertThat(again.status).isEqualTo(Status.ALMOST)
    }

    @Test
    fun TC095_fixingTheWrongCellsAfterAlmost_completes() {
        val almost = almostFull(wrongCell, last).then(TapCell(last), showOff).then(TapDigit(fresh.right(last)), showOff)
        val t = almost.then(KeepLooking, showOff).then(TapCell(wrongCell), showOff).act(TapDigit(fresh.right(wrongCell)), showOff)
        assertThat(t.state.status).isEqualTo(Status.COMPLETED)
        assertThat(t.effects).containsExactly(Effect.Persist, Effect.Haptic(HapticKind.CONFIRM), Effect.Completed).inOrder()
    }

    @Test
    fun TC096_fullButWrong_withShowOn_staysPlaying_noAlmost() {
        val t = almostFull(wrongCell, last).then(TapCell(last), showOn).act(TapDigit(fresh.right(last)), showOn)
        assertThat(t.state.status).isEqualTo(Status.PLAYING)
        assertThat(t.effects.filterIsInstance<Effect.OpenPanel>()).isEmpty()
        assertThat(Highlights.of(t.state, true).has(wrongCell, BoardHighlights.MISTAKE)).isTrue()
    }

    @Test
    fun TC097_backOnAlmost_isKeepLooking_boardUnchanged() {
        val almost = almostFull(wrongCell, last).then(TapCell(last), showOff).then(TapDigit(fresh.right(last)), showOff)
        val t = almost.act(Back, showOff)
        assertThat(t.handled).isTrue()
        assertThat(t.state.status).isEqualTo(Status.PLAYING)
        assertThat(t.state.codes()).isEqualTo(almost.codes())
    }

    // ---- Completion ------------------------------------------------------------------------------------------------

    @Test
    fun completingMove_completes_andTheBoardIsFinal() {
        val s = fresh.solvedExcept(last)
        val t = s.then(TapCell(last)).act(TapDigit(fresh.right(last)))
        assertThat(t.state.status).isEqualTo(Status.COMPLETED)
        assertThat(t.effects).contains(Effect.Completed)
        assertThat(t.state.act(TapCell(empties[0])).state).isEqualTo(t.state)
        assertThat(t.state.act(Back).handled).isFalse()
    }

    @Test
    fun TC160_fullBoardWithOneWrongDigit_isNotCompleted() {
        val t = almostFull(wrongCell, last).then(TapCell(last), showOn).act(TapDigit(fresh.right(last)), showOn)
        assertThat(t.state.status).isNotEqualTo(Status.COMPLETED)
        assertThat(t.effects).doesNotContain(Effect.Completed)
    }
}
