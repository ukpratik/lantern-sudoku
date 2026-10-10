// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.feature.game

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp

/**
 * Heights (dp) of everything on the Game screen except the board, at the current system font scale (US-021, NFR-007).
 * At 1.0× these are the design's numbers (design-system §4.1: bar 56, status 32, pad/tools 256, compact 168), so
 * [extraRegular] and [extraCompact] are 0 and [io.github.ukpratik.lanternsudoku.ui.board.BoardGeometry] is unchanged.
 * With large text (≥ 1.5×, design §3.1) the bar and status line make room for two lines, tool labels move under their
 * icons, and the controls grow; the board takes what's left (down to the 30 dp cell floor, then the column scrolls).
 * Budgets are fixed per font scale, never measured per frame, so the board never jumps as the status text changes.
 */
data class GameChrome(
    val largeText: Boolean,
    val barRegular: Float,
    val barCompact: Float,
    val status: Float,
    val toolHeight: Float,
    val padRegular: Float,
    val padCompact: Float,
) {
    /** Compact folds the status into the bar subtitle, except with large text, where it keeps its own lines. */
    fun bar(compact: Boolean): Float = if (compact && !largeText) barCompact else barRegular

    fun statusLine(compact: Boolean): Float = if (compact && !largeText) COMPACT_GAP else status

    fun pad(compact: Boolean): Float = if (compact) padCompact else padRegular

    val extraRegular: Float get() = (barRegular - BAR) + (status - STATUS) + (padRegular - PAD)
    val extraCompact: Float get() = (bar(true) - BAR) + (statusLine(true) - COMPACT_GAP) + (padCompact - PAD_COMPACT)

    companion object {
        const val BAR = 56f
        const val STATUS = 32f
        const val COMPACT_GAP = 8f
        const val PAD = 256f
        const val PAD_COMPACT = 168f
        const val TOOL = 48f
        const val LARGE_TEXT = 1.5f

        /** Line heights come from the density's own sp → dp curve (non-linear on Android 14+). */
        fun of(density: Density, showTimer: Boolean): GameChrome = with(density) {
            val large = fontScale >= LARGE_TEXT
            val title = TITLE_LINE.sp.toDp().value
            val timer = TIMER_LINE.sp.toDp().value
            val label = LABEL_LINE.sp.toDp().value
            val small = SMALL_LINE.sp.toDp().value
            if (!large) {
                return GameChrome(false, maxOf(BAR, title + 16f), maxOf(BAR, title + small + 12f), maxOf(STATUS, label + 12f), TOOL, PAD, PAD_COMPACT)
            }
            // Title on up to two lines with the timer under it; status on up to two lines with the mistake count below.
            val bar = maxOf(BAR, 2 * title + (if (showTimer) timer else 0f) + 12f)
            val status = 2 * label + MISTAKES_LINE.sp.toDp().value + 12f
            val tool = maxOf(TOOL, ICON + label + 10f)
            GameChrome(true, bar, bar, status, tool, maxOf(PAD, 5 * tool + 4 * TOOL_GAP), maxOf(PAD_COMPACT, 3 * tool + 2 * TOOL_GAP))
        }

        private const val TITLE_LINE = 24 // game title 19 sp
        private const val TIMER_LINE = 20 // timer 16 sp
        private const val LABEL_LINE = 16 // label-m 13 sp
        private const val SMALL_LINE = 15 // compact subtitle 12 sp
        private const val MISTAKES_LINE = 20 // the mistake count (body-m line height)
        private const val ICON = 22f
        const val TOOL_GAP = 4f
    }
}
