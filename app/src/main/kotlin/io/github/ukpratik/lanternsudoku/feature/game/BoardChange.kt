// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.feature.game

import android.content.res.Resources
import io.github.ukpratik.lanternsudoku.R
import io.github.ukpratik.lanternsudoku.engine.game.Action
import io.github.ukpratik.lanternsudoku.engine.game.Effect
import io.github.ukpratik.lanternsudoku.engine.game.GameState
import io.github.ukpratik.lanternsudoku.engine.game.HapticKind
import io.github.ukpratik.lanternsudoku.engine.model.Masks

/**
 * What one player action did to the board, for TalkBack's polite live region (US-022; design game-cell-selected,
 * game-notes-mode, game-digit-locked): "Placed 4, row 2, column 8", "Note 9 removed, row 5, column 5",
 * "Row 1, column 1 has 7. 5 is locked." Pure, so it's unit-tested; [seq] makes a repeated message a new announcement.
 * Hint stage 3 is announced by the hint panel itself, so changes made while a hint is open are not described here.
 * Restart puzzle is announced as such (ISS-009); any other change that touches more than one cell's digit, or the
 * notes of more than one cell without a digit change, is left unannounced rather than described as a one-cell edit.
 */
data class BoardChange(val seq: Int, val kind: Kind, val cell: Int, val digit: Int, val other: Int = 0) {
    enum class Kind { PLACED, ERASED, NOTE_ADDED, NOTE_REMOVED, BLOCKED, RESTARTED }

    fun describe(res: Resources): String {
        val r = cell / 9 + 1
        val c = cell % 9 + 1
        return when (kind) {
            Kind.PLACED -> res.getString(R.string.announce_placed, digit, r, c)
            Kind.ERASED -> res.getString(R.string.announce_erased, r, c)
            Kind.NOTE_ADDED -> res.getString(R.string.announce_note_added, digit, r, c)
            Kind.NOTE_REMOVED -> res.getString(R.string.announce_note_removed, digit, r, c)
            Kind.BLOCKED -> res.getString(R.string.announce_blocked, r, c, other, digit)
            Kind.RESTARTED -> res.getString(R.string.announce_restarted)
        }
    }

    companion object {
        /**
         * The change [action] made from [before] to [after], or null when nothing worth announcing changed (a
         * selection, a mode switch: those are read from the status line and the controls' own state).
         */
        fun of(seq: Int, before: GameState, after: GameState, action: Action, effects: List<Effect>): BoardChange? {
            if (effects.contains(Effect.Restarted)) return BoardChange(seq, Kind.RESTARTED, 0, 0)
            if (before.input.hint != null || after.input.hint != null) return null
            val lock = before.input.lock
            if (action is Action.TapCell && lock != null && effects.any { it is Effect.Haptic && it.kind == HapticKind.DOUBLE_TICK }) {
                val v = before.value(action.cell)
                return if (v != 0) BoardChange(seq, Kind.BLOCKED, action.cell, lock, other = v) else null
            }
            // The cell whose value changed is the subject (auto-cleanup also touches peers' notes); else a notes change.
            val valueCells = (0 until 81).filter { before.value(it) != after.value(it) }
            if (valueCells.size > 1) return null
            val valueCell = valueCells.firstOrNull()
            if (valueCell != null) {
                val v = after.value(valueCell)
                return if (v != 0) BoardChange(seq, Kind.PLACED, valueCell, v) else BoardChange(seq, Kind.ERASED, valueCell, before.value(valueCell))
            }
            val notesCells = (0 until 81).filter { before.notes(it) != after.notes(it) }
            if (notesCells.size != 1) return null
            val notesCell = notesCells.single()
            val was = before.notes(notesCell)
            val now = after.notes(notesCell)
            val added = now and was.inv()
            val removed = was and now.inv()
            return when {
                added != 0 && Integer.bitCount(added) == 1 && removed == 0 -> BoardChange(seq, Kind.NOTE_ADDED, notesCell, Masks.digits(added).first())
                removed != 0 && Integer.bitCount(removed) == 1 && added == 0 -> BoardChange(seq, Kind.NOTE_REMOVED, notesCell, Masks.digits(removed).first())
                now == 0 -> BoardChange(seq, Kind.ERASED, notesCell, 0)
                else -> null
            }
        }
    }
}
