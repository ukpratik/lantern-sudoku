// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.platform

import io.github.ukpratik.lanternsudoku.engine.model.PuzzleId

/**
 * QA hooks (qa-handover "Debug hooks"), parsed from launch-intent extras by the debug build's [DebugHooks] only.
 * The release build's [DebugHooks] never returns one.
 */
sealed interface DebugCommand {
    /** `dbg:load <puzzleId>`: start that New-game puzzle (abandoning a saved game at its level). */
    data class Load(val id: PuzzleId) : DebugCommand

    /** `dbg:near-solved`: open the current game with all but one empty cell filled from the solution. */
    data object NearSolved : DebugCommand
}
