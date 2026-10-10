// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.engine.game

import io.github.ukpratik.lanternsudoku.engine.model.Grid
import io.github.ukpratik.lanternsudoku.engine.model.Houses

/**
 * What the board shows for each cell (US-004, US-008, US-010). [flags] holds one bit set per cell (see the constants);
 * [digit] is the highlighted digit (0 for none): the lock wins, then a completed-key highlight, then the selected
 * cell's value.
 */
class BoardHighlights(val digit: Int, private val flags: IntArray) {
    fun has(cell: Int, flag: Int): Boolean = flags[cell] and flag != 0

    fun flags(cell: Int): Int = flags[cell]

    companion object {
        const val SELECTED = 1
        const val PEER = 2
        const val SAME = 4
        const val CONFLICT = 8
        const val MISTAKE = 16
    }
}

object Highlights {
    /**
     * @param showMistakes the effective setting: Show mistakes on, or "Show them" for this game (US-010/011).
     * @param highlightSame US-023 "Highlight same digit" (default on); the lock's digit and a tapped completed key always show (TC-074).
     */
    fun of(s: GameState, showMistakes: Boolean, highlightSame: Boolean = true): BoardHighlights {
        val flags = IntArray(Grid.CELLS)
        val sel = s.input.selection
        if (sel != null) {
            flags[sel] = flags[sel] or BoardHighlights.SELECTED
            for (p in Houses.PEERS[sel]) flags[p] = flags[p] or BoardHighlights.PEER
        }
        // The lock always wins (US-008), then a tapped completed key, then the selected cell's digit (if enabled).
        val selValue = sel?.let { s.value(it) } ?: 0
        val digit = s.input.lock ?: s.input.highlight ?: if (highlightSame) selValue else 0
        if (digit != 0) for (i in 0 until Grid.CELLS) if (s.value(i) == digit) flags[i] = flags[i] or BoardHighlights.SAME
        markConflicts(s, flags)
        if (showMistakes) for (i in 0 until Grid.CELLS) if (s.isWrong(i)) flags[i] = flags[i] or BoardHighlights.MISTAKE
        return BoardHighlights(digit, flags)
    }

    /** Conflicts (PRD §5): equal digits in one house. Always shown, whatever the mistakes setting. */
    private fun markConflicts(s: GameState, flags: IntArray) {
        for (h in 0 until Houses.COUNT) {
            val cells = Houses.HOUSE_CELLS[h]
            for (a in 0 until 9) {
                val va = s.value(cells[a])
                if (va == 0) continue
                for (b in a + 1 until 9) {
                    if (s.value(cells[b]) == va) {
                        flags[cells[a]] = flags[cells[a]] or BoardHighlights.CONFLICT
                        flags[cells[b]] = flags[cells[b]] or BoardHighlights.CONFLICT
                    }
                }
            }
        }
    }
}
