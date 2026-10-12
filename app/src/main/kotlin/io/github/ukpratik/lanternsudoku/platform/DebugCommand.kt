// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.platform

import io.github.ukpratik.lanternsudoku.engine.model.PuzzleId

/**
 * QA hooks (see the debug build's DebugHooks), parsed from launch-intent extras by the debug build's [DebugHooks] only.
 * The release build's [DebugHooks] never returns one.
 */
sealed interface DebugCommand {
    /**
     * `dbg:load <puzzleId>`: start that New-game puzzle (abandoning a saved game at its level). With `dbg_board`
     * (81 characters, givens plus entries), the entries are filled in as if played: a fixed hint position for QA.
     */
    data class Load(val id: PuzzleId, val board: String? = null) : DebugCommand

    /** `dbg:near-solved`: open the current game with all but one empty cell filled from the solution. */
    data object NearSolved : DebugCommand

    /** `dbg_daily`: open (or resume) today's daily, as the Today row's Play does; combine with `dbg_date`. */
    data object Daily : DebugCommand
}
