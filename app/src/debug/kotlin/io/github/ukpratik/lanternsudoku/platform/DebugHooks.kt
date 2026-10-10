// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.platform

import android.content.Intent
import io.github.ukpratik.lanternsudoku.engine.game.CellCode
import io.github.ukpratik.lanternsudoku.engine.game.GameState
import io.github.ukpratik.lanternsudoku.engine.game.InputState
import io.github.ukpratik.lanternsudoku.engine.game.Status
import io.github.ukpratik.lanternsudoku.engine.model.Grid
import io.github.ukpratik.lanternsudoku.engine.model.PuzzleId
import java.time.LocalDate

/**
 * Debug-build QA hooks (qa-handover "Debug hooks"; documented in 06-build/handover.md). The release source set has a
 * no-op twin, so none of this ships.
 *
 * ```
 * adb shell am start -n io.github.ukpratik.lanternsudoku.debug/io.github.ukpratik.lanternsudoku.MainActivity --es dbg_load N-H-0123
 * adb shell am start -n io.github.ukpratik.lanternsudoku.debug/io.github.ukpratik.lanternsudoku.MainActivity --es dbg_load N-H-0000 --es dbg_board .84.1....2..58..4...
 * adb shell am start -n io.github.ukpratik.lanternsudoku.debug/io.github.ukpratik.lanternsudoku.MainActivity --ez dbg_near_solved true
 * adb shell am start -n io.github.ukpratik.lanternsudoku.debug/io.github.ukpratik.lanternsudoku.MainActivity --es dbg_date 2026-10-15
 * adb shell am start -n io.github.ukpratik.lanternsudoku.debug/io.github.ukpratik.lanternsudoku.MainActivity --es dbg_date clear
 * adb shell am start -n io.github.ukpratik.lanternsudoku.debug/io.github.ukpratik.lanternsudoku.MainActivity --es dbg_date 2026-10-15 --ez dbg_daily true
 * ```
 */
object DebugHooks {
    const val ENABLED = true

    @Volatile
    private var dateOverride: LocalDate? = null

    /**
     * `dbg_date <yyyy-mm-dd>` overrides "today" for the whole app (daily, started/completed dates, streak) until the
     * process ends or `dbg_date clear`. Returns true if the intent carried the extra.
     */
    fun applyDate(intent: Intent?): Boolean {
        val text = intent?.getStringExtra("dbg_date") ?: return false
        dateOverride = if (text.isBlank() || text == "clear") null else runCatching { LocalDate.parse(text) }.getOrNull()
        return true
    }

    fun todayOverride(): LocalDate? = dateOverride

    @Volatile
    private var nearSolvedPending = false

    /** Asks the next Game screen that loads to apply [nearSolved] (applied after load, so no stale save can undo it). */
    fun requestNearSolved() {
        nearSolvedPending = true
    }

    fun consumeNearSolved(): Boolean = nearSolvedPending.also { nearSolvedPending = false }

    @Volatile
    private var boardPending: String? = null

    /** Asks the next Game screen that loads to fill in the entries of [board] (`dbg_board`, with `dbg_load`). */
    fun requestBoard(board: String) {
        boardPending = board
    }

    fun consumeBoard(): String? = boardPending.also { boardPending = null }

    /**
     * Fills in the non-given digits of [board] (81 characters, `.` for empty) as entries, history cleared. Digits that
     * contradict a given are ignored; wrong digits stay, so error-first hints can be staged too.
     */
    fun withBoard(s: GameState, board: String): GameState {
        val g = runCatching { Grid.parse(board) }.getOrNull() ?: return s
        val cells = s.codes()
        var seq = s.seq
        for (i in 0 until 81) if (!s.isGiven(i) && g[i] != 0) cells[i] = CellCode.of(value = g[i], seq = ++seq)
        return s.copy(cells = cells, seq = seq, input = InputState(), undo = emptyList(), redo = emptyList(), status = Status.PLAYING)
    }

    fun parse(intent: Intent?): DebugCommand? {
        intent ?: return null
        intent.getStringExtra("dbg_load")?.let { text ->
            return runCatching { DebugCommand.Load(PuzzleId.parse(text), intent.getStringExtra("dbg_board")) }.getOrNull()
        }
        if (intent.getBooleanExtra("dbg_daily", false)) return DebugCommand.Daily
        if (intent.getBooleanExtra("dbg_near_solved", false)) return DebugCommand.NearSolved
        return null
    }

    /** Fills every empty cell but the last one with its solution digit; history is cleared (a set-up, not moves). */
    fun nearSolved(s: GameState): GameState {
        val empties = (0 until 81).filter { !s.isGiven(it) && s.value(it) != s.solution[it] }
        if (empties.size <= 1) return s
        val cells = s.codes()
        for (i in empties.dropLast(1)) cells[i] = CellCode.of(value = s.solution[i])
        cells[empties.last()] = 0
        return s.copy(cells = cells, input = InputState(), undo = emptyList(), redo = emptyList(), status = Status.PLAYING)
    }
}
