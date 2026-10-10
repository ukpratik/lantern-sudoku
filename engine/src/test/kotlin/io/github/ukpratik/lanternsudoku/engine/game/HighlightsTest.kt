// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.engine.game

import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.lanternsudoku.engine.game.Action.TapCell
import io.github.ukpratik.lanternsudoku.engine.game.Action.TapDigit
import io.github.ukpratik.lanternsudoku.engine.model.Houses
import org.junit.Test

/** US-008 highlights, conflicts and remaining counts. */
class HighlightsTest {
    private val fresh = Boards.fresh()

    @Test
    fun selection_tintsRowColumnBox_andSameDigit() {
        val given = Boards.aGiven
        val s = fresh.then(TapCell(given))
        val h = Highlights.of(s, showMistakes = true)
        assertThat(h.has(given, BoardHighlights.SELECTED)).isTrue()
        for (p in Houses.PEERS[given]) assertThat(h.has(p, BoardHighlights.PEER)).isTrue()
        val d = s.value(given)
        for (i in 0 until 81) assertThat(h.has(i, BoardHighlights.SAME)).isEqualTo(s.value(i) == d)
    }

    @Test
    fun TC047_lockWinsTheHighlight() {
        val s = fresh.then(TapDigit(5))
        val h = Highlights.of(s, showMistakes = true)
        assertThat(h.digit).isEqualTo(5)
        for (i in 0 until 81) assertThat(h.has(i, BoardHighlights.SAME)).isEqualTo(s.value(i) == 5)
    }

    @Test
    fun highlightSameOff_selectionDoesNotTintSameDigit_butLockStillDoes() {
        val s = fresh.then(TapCell(Boards.aGiven))
        assertThat(Highlights.of(s, showMistakes = true, highlightSame = false).digit).isEqualTo(0)
        assertThat(Highlights.of(fresh.then(TapDigit(4)), true, highlightSame = false).digit).isEqualTo(4)
    }

    @Test
    fun TC071_conflict_marksBothDuplicates_whateverTheMistakeSetting() {
        // Put a given's digit into an empty peer of that given.
        val given = Boards.aGiven
        val peer = Houses.PEERS[given].first { !fresh.isGiven(it) }
        val s = fresh.withEntries(peer to fresh.value(given))
        val h = Highlights.of(s, showMistakes = false)
        assertThat(h.has(given, BoardHighlights.CONFLICT)).isTrue()
        assertThat(h.has(peer, BoardHighlights.CONFLICT)).isTrue()
        assertThat(h.has(peer, BoardHighlights.MISTAKE)).isFalse() // hidden while Show mistakes is off
        // TC-070: clearing the entry clears both markers.
        val cleared = Highlights.of(fresh, showMistakes = false)
        assertThat(cleared.has(given, BoardHighlights.CONFLICT)).isFalse()
    }

    @Test
    fun TC072_remainingCount_is9MinusPlaced_wrongOnesIncluded() {
        val d = 7
        val placedGivens = fresh.placedCount(d)
        val spots = fresh.emptiesFor(d)
        // Fill all but 3 of the remaining 7s; make one of them wrong by placing the 7 elsewhere.
        val keep = 9 - placedGivens - 3
        val right = spots.take(keep - 1).map { it to d }
        val wrongCell = Boards.empties.first { fresh.solution[it] != d }
        val s = fresh.withEntries(*(right + (wrongCell to d)).toTypedArray())
        assertThat(s.remaining(d)).isEqualTo(3)
        assertThat(s.isComplete(d)).isFalse()
    }

    @Test
    fun TC073_remainingAtZero_isComplete() {
        val d = 2
        val s = fresh.withEntries(*fresh.emptiesFor(d).map { it to d }.toTypedArray())
        assertThat(s.remaining(d)).isEqualTo(0)
        assertThat(s.isComplete(d)).isTrue()
    }

    @Test
    fun gridSummary_encodesGivensEntriesAndEmpties() {
        val e = Boards.empties[0]
        val s = fresh.withEntries(e to 4)
        val summary = GameState.gridSummary(s)
        assertThat(summary).hasLength(81)
        assertThat(summary[e]).isEqualTo('d')
        assertThat(summary[Boards.aGiven]).isEqualTo('0' + fresh.value(Boards.aGiven))
        assertThat(summary[Boards.empties[1]]).isEqualTo('.')
    }
}
