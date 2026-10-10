// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.feature.game

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp

/**
 * Heights (dp) of everything on the Game screen except the board, at the current system font scale (US-021, NFR-007).
 * At 1.0× these are the design's numbers (design-system §4.1: bar 56, status 32, pad/tools 256, compact 168), so
 * [extraRegular] and [extraCompact] are 0 and [io.github.ukpratik.lanternsudoku.ui.board.BoardGeometry] is unchanged.
 * With large text (≥ 1.5×, design §3.1) the regular layout makes room for two-line title and status and tool labels
 * under their icons. Compact + large text (D-037, ISS-004) keeps the board and pad on screen instead: a one-row bar
 * (title and timer, no ⋮; Restart stays in Pause) with the status folded into one subtitle line, and icon-only tools
 * when labelled tools would push the board under its 30 dp cell floor (then the pad keeps the compact 168 dp). Budgets are fixed per font scale and title, never measured per frame, so the board
 * never jumps as the status text changes.
 */
data class GameChrome(
    val largeText: Boolean,
    val barRegular: Float,
    val barCompact: Float,
    val status: Float,
    val toolHeight: Float,
    val padRegular: Float,
    val padCompact: Float,
    val iconOnlyTools: Boolean = false,
) {
    /** Compact folds the status into the bar subtitle (at any font scale since D-037). */
    fun bar(compact: Boolean): Float = if (compact) barCompact else barRegular

    fun statusLine(compact: Boolean): Float = if (compact) COMPACT_GAP else status

    fun pad(compact: Boolean): Float = if (compact) padCompact else padRegular

    /** Tool buttons: 48 dp when icon-only (labels stay in TalkBack), else [toolHeight]. */
    fun tool(compact: Boolean): Float = if (compact && iconOnlyTools) TOOL else toolHeight

    val extraRegular: Float get() = (barRegular - BAR) + (status - STATUS) + (padRegular - PAD)
    val extraCompact: Float get() = (barCompact - BAR) + (padCompact - PAD_COMPACT)

    companion object {
        const val BAR = 56f
        const val STATUS = 32f
        const val COMPACT_GAP = 8f
        const val PAD = 256f
        const val PAD_COMPACT = 168f
        const val TOOL = 48f
        const val LARGE_TEXT = 1.5f

        /**
         * Line heights come from the density's own sp → dp curve (non-linear on Android 14+ and in Compose).
         * @param compactLargeBar the compact bar's height with large text, measured from the real title, timer and
         *   subtitle by the Game screen ([compactLargeBar]); ignored below 1.5×.
         * @param iconOnlyTools compact tools without labels (large text when labelled ones don't leave room, D-037).
         */
        fun of(density: Density, showTimer: Boolean, compactLargeBar: Float = 0f, iconOnlyTools: Boolean = false): GameChrome = with(density) {
            val large = fontScale >= LARGE_TEXT
            val title = TITLE_LINE.sp.toDp().value
            val timer = TIMER_LINE.sp.toDp().value
            val label = LABEL_LINE.sp.toDp().value
            val small = SMALL_LINE.sp.toDp().value
            if (!large) {
                return GameChrome(false, maxOf(BAR, title + 16f), maxOf(BAR, title + small + 12f), maxOf(STATUS, label + 12f), TOOL, PAD, PAD_COMPACT)
            }
            // Regular: title on up to two lines with the timer under it; status on up to two lines with the mistake count below.
            val bar = maxOf(BAR, 2 * title + (if (showTimer) timer else 0f) + 12f)
            val status = 2 * label + MISTAKES_LINE.sp.toDp().value + 12f
            val tool = maxOf(TOOL, ICON + label + 10f)
            val padCompact = if (iconOnlyTools) PAD_COMPACT else maxOf(PAD_COMPACT, 3 * tool + 2 * TOOL_GAP)
            GameChrome(true, bar, maxOf(BAR, compactLargeBar), status, tool, maxOf(PAD, 5 * tool + 4 * TOOL_GAP), padCompact, iconOnlyTools)
        }

        /** The compact bar with large text: the title row (title and timer side by side when they fit) + one subtitle line. */
        fun compactLargeBar(titleRow: Float, subtitle: Float): Float = maxOf(BAR, titleRow + subtitle + BAR_PADDING)

        /** Width left for the bar's title column: the row's 4 dp padding each side, Back and Pause (48 each), 4 dp gap. */
        fun titleWidth(column: Float): Float = column - 2 * 4f - 2 * 48f - 4f

        const val BAR_PADDING = 8f
        const val TITLE_TIMER_GAP = 10f
        private const val TITLE_LINE = 24 // game title 19 sp
        private const val TIMER_LINE = 20 // timer 16 sp
        private const val LABEL_LINE = 16 // label-m 13 sp
        private const val SMALL_LINE = 15 // compact subtitle 12 sp
        private const val MISTAKES_LINE = 20 // the mistake count (body-m line height)
        private const val ICON = 22f
        const val TOOL_GAP = 4f
    }
}
