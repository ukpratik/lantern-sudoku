// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.platform

import android.content.Intent
import io.github.ukpratik.lanternsudoku.engine.game.CellCode
import io.github.ukpratik.lanternsudoku.engine.game.GameState
import io.github.ukpratik.lanternsudoku.engine.game.InputState
import io.github.ukpratik.lanternsudoku.engine.game.Status
import io.github.ukpratik.lanternsudoku.engine.model.PuzzleId

/**
 * Debug-build QA hooks (qa-handover "Debug hooks"; documented in 06-build/handover.md). The release source set has a
 * no-op twin, so none of this ships.
 *
 * ```
 * adb shell am start -n io.github.ukpratik.lanternsudoku.debug/io.github.ukpratik.lanternsudoku.MainActivity --es dbg_load N-H-0123
 * adb shell am start -n io.github.ukpratik.lanternsudoku.debug/io.github.ukpratik.lanternsudoku.MainActivity --ez dbg_near_solved true
 * ```
 */
object DebugHooks {
    const val ENABLED = true

    @Volatile
    private var nearSolvedPending = false

    /** Asks the next Game screen that loads to apply [nearSolved] (applied after load, so no stale save can undo it). */
    fun requestNearSolved() {
        nearSolvedPending = true
    }

    fun consumeNearSolved(): Boolean = nearSolvedPending.also { nearSolvedPending = false }

    fun parse(intent: Intent?): DebugCommand? {
        intent ?: return null
        intent.getStringExtra("dbg_load")?.let { text ->
            return runCatching { DebugCommand.Load(PuzzleId.parse(text)) }.getOrNull()
        }
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
