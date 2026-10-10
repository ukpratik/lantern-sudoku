// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.platform

import android.content.Intent
import io.github.ukpratik.lanternsudoku.engine.game.GameState

/** Release twin of the debug-build QA hooks: does nothing, so no hook ships. */
object DebugHooks {
    const val ENABLED = false

    @Suppress("UNUSED_PARAMETER", "FunctionOnlyReturningConstant")
    fun parse(intent: Intent?): DebugCommand? = null

    fun nearSolved(s: GameState): GameState = s

    fun requestNearSolved() = Unit

    fun consumeNearSolved(): Boolean = false

    @Suppress("UNUSED_PARAMETER")
    fun requestBoard(board: String) = Unit

    fun consumeBoard(): String? = null

    @Suppress("UNUSED_PARAMETER")
    fun withBoard(s: GameState, board: String): GameState = s
}
