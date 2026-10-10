// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.ui.board

/**
 * Game-screen layout from the space measured inside `WindowInsets.safeDrawing` (design-system §4.1, LLD §4.10). Pure, so
 * it is unit-tested against the design's worked examples. All values in dp.
 *
 * @property column content column width (max 480, centred)
 * @property compact short-screen layout: status line folds into the bar, 52 dp keys, tools as a 2-column grid
 * @property scrolls the formula gave a cell under the 30 dp floor: the board stays at 270 and the column scrolls
 * @property extra extra height the controls need because large system text grew the bar, status line or tools (US-021);
 *   0 at the default font scale, where the design's numbers apply exactly
 */
data class BoardGeometry(
    val column: Float,
    val gutter: Float,
    val board: Float,
    val compact: Boolean,
    val scrolls: Boolean,
    val extra: Float = 0f,
) {
    val cell: Float get() = board / 9f
    val controls: Float get() = (if (compact) CONTROLS_COMPACT else CONTROLS_REGULAR) + extra

    companion object {
        const val MAX_COLUMN = 480f
        const val CONTROLS_REGULAR = 368f // bar 56 + status 32 + 12 + pad/tools 256 + 12
        const val CONTROLS_COMPACT = 240f // bar 56 + 8 + pad/tools 168 + 8
        const val COMPACT_BELOW = 728f // = 360 board + 368 regular controls
        const val BOARD_FLOOR = 270f // 30 dp cells

        /**
         * @param extraRegular / [extraCompact] how much taller than the design's 368 / 240 dp the controls are at the
         *   current font scale (measured by the Game screen); compact is chosen when regular controls wouldn't leave a
         *   360 dp board.
         */
        fun compute(width: Float, availableHeight: Float, extraRegular: Float = 0f, extraCompact: Float = 0f): BoardGeometry {
            val column = minOf(width, MAX_COLUMN)
            val gutter = if (column >= 380f) 15f else 8f
            val compact = availableHeight < COMPACT_BELOW + extraRegular
            val extra = if (compact) extraCompact else extraRegular
            val controls = (if (compact) CONTROLS_COMPACT else CONTROLS_REGULAR) + extra
            val fit = minOf(column - 2 * gutter, availableHeight - controls)
            return BoardGeometry(column, gutter, maxOf(fit, BOARD_FLOOR), compact, scrolls = fit < BOARD_FLOOR, extra = extra)
        }
    }
}
