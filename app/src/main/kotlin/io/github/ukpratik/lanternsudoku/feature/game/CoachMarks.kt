// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.feature.game

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.roundToInt
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import io.github.ukpratik.lanternsudoku.R
import io.github.ukpratik.lanternsudoku.data.SettingsRepository
import io.github.ukpratik.lanternsudoku.engine.game.GameState

/**
 * The three first-game tips (US-024; design coach-mark): lock a number, Notes, Hint. [bit] is the tip's bit in the
 * stored `coach_marks_done` mask (LLD §2.2).
 */
enum class CoachTip(val bit: Int, @param:StringRes val title: Int, @param:StringRes val body: Int) {
    LOCK(1, R.string.coach_lock_title, R.string.coach_lock_body),
    NOTES(2, R.string.coach_notes_title, R.string.coach_notes_body),
    HINT(4, R.string.coach_hint_title, R.string.coach_hint_body),
    ;

    companion object {
        const val ALL = 7

        /** The first tip not yet seen or skipped, or null when all are done. */
        fun next(mask: Int): CoachTip? = entries.firstOrNull { mask and it.bit == 0 }

        /**
         * Doing what the tip teaches moves on (design coach-mark): locking a digit, turning Notes on, opening a hint.
         */
        fun done(tip: CoachTip, before: GameState, after: GameState): Boolean = when (tip) {
            LOCK -> before.input.lock == null && after.input.lock != null
            NOTES -> !before.input.notesMode && after.input.notesMode
            HINT -> before.input.hint == null && after.input.hint != null
        }
    }
}

/**
 * Coach-mark progress (US-024, PRD v1.3): the tips belong to the first game started after install and carry on into the
 * next games until all 3 are seen or skipped; none shows again after that. A tip counts as seen once it is shown.
 * Progress lives in DataStore (`coach_marks_done`), so it survives the app and follows Android backup.
 */
class CoachMarkStore(private val settings: SettingsRepository, private val startedCount: suspend () -> Int) {
    /**
     * The stored mask. On the first game after 1.0.1 arrives, an install that already has other games (an update from
     * 1.0.0) starts with every tip done: they are for new players, not for an update (D-044).
     */
    suspend fun start(): Int = settings.marks().coachMarks ?: (if (startedCount() > 1) CoachTip.ALL else 0).also { settings.setCoachMarks(it) }

    suspend fun save(mask: Int) = settings.setCoachMarks(mask)
}

/** What the pad needs to show a tip: which one, its position ("1 of 3") and the two actions. */
data class CoachUi(val tip: CoachTip, val onNext: () -> Unit, val onSkip: () -> Unit)

/**
 * Where the coach tip may go, in root coordinates (ISS-010, ISS-011; D-046): the number pad with its tools (the tip never
 * covers it), the top of the board (the tip never covers the bar above it) and each tip's anchor (its caret points there).
 * The game screen owns one and provides it with [LocalCoachTargets]; the pad, the board and the anchors report into it.
 */
@Stable
class CoachTargets {
    var pad: Rect? by mutableStateOf(null)
    var boardTop: Float? by mutableStateOf(null)
    val anchors = mutableStateMapOf<CoachTip, Rect>()
}

val LocalCoachTargets = compositionLocalOf<CoachTargets?> { null }

/**
 * One placement rule for all three tips (D-046, ISS-014). Pure, so it is unit-tested; all values in px of the host's
 * coordinates. [pointX], [pointY] is where the caret's point lands (the test checks it lies on the anchor). For a tool tip
 * ([stemX] not null) the caret is a leader: a line from the tip's right edge down the pad's end margin, right of the
 * tools, to the tool's middle, ending in an arrowhead pointing left into the tool.
 */
data class CoachPlacement(
    val left: Float,
    val bottom: Float,
    val width: Float,
    val maxHeight: Float,
    val caretX: Float,
    val pointX: Float,
    val pointY: Float,
    val stemX: Float? = null,
) {
    companion object {
        /**
         * The tip sits **above the pad**, its bottom on the pad's top edge ([bottom]): it covers part of the board, never
         * a key or a tool. It is as wide as the pad less [margin] each side, at most [maxWidth]. Its height is capped by
         * the room between the board's top ([boardTop], or the host's top if the board scrolled away) and the pad, so it
         * always stays on screen inside the safe-drawing area (the host); text beyond that scrolls, the actions don't
         * (D-038 panels). [maxHeight] includes the caret.
         *
         * - Key tip (lock): centred on the anchor where it can be, the [caret] px caret pointing down at the anchor's
         *   column, its point on the pad's top edge (D-046).
         * - Tool tip ([side], Notes and Hint, ISS-014): the tools are a column or grid whose top tool is Undo, so a caret on
         *   the pad's top edge would point at Undo/Redo. The tip is flush with the pad's end (less [margin]) and its
         *   leader runs down the margin right of the tools to the anchor's middle; the point sits [caret]/4 inside it.
         */
        fun of(
            pad: Rect, anchor: Rect, boardTop: Float, hostHeight: Float, maxWidth: Float, margin: Float, caret: Float, corner: Float,
            side: Boolean = false,
        ): CoachPlacement {
            val width = minOf(maxWidth, pad.width - 2 * margin).coerceAtLeast(0f)
            val minLeft = pad.left + margin
            val maxLeft = maxOf(minLeft, pad.right - margin - width)
            val left = if (side) maxLeft else (anchor.center.x - width / 2).coerceIn(minLeft, maxLeft)
            val bottom = minOf(pad.top, hostHeight) // the bottom of the caret row
            val top = maxOf(boardTop, 0f) + margin
            val maxHeight = (bottom - top).coerceAtLeast(0f)
            if (side) {
                val stemX = left + width
                return CoachPlacement(left, bottom, width, maxHeight, caretX = width, pointX = anchor.right - caret / 4, pointY = anchor.center.y, stemX = stemX)
            }
            val caretX = (anchor.center.x - left).coerceIn(corner + caret, maxOf(corner + caret, width - corner - caret))
            return CoachPlacement(left, bottom, width, maxHeight, caretX, pointX = left + caretX, pointY = bottom)
        }
    }
}

/**
 * Marks one control (a number key, the Notes or Hint tool) as the anchor of [tip]: its bounds go to [LocalCoachTargets]
 * while it is composed, so the tip shows only while its control is on screen (not over the hint panel or Pause).
 */
@Composable
internal fun CoachAnchor(coach: CoachUi?, tip: CoachTip, content: @Composable () -> Unit) {
    val targets = LocalCoachTargets.current
    if (coach == null || targets == null) {
        content()
        return
    }
    DisposableEffect(targets, tip) { onDispose { targets.anchors.remove(tip) } }
    Box(Modifier.onGloballyPositioned { targets.anchors[tip] = it.boundsInRoot() }) { content() }
}

/**
 * The coach tip (US-024, design coach-mark, design review #6, D-020): title, one sentence and "1 of 3 · Skip · Next" in
 * the rich-tooltip style (ink container, 12 dp corners, caret to the target, max 320 dp), placed by [CoachPlacement]:
 * above the pad, over the board, so the control it teaches and every key and tool stay visible and tappable (ISS-010).
 * It is drawn in the screen's own layout inside the safe-drawing area, not as a popup window, so it can't run under the
 * system bars (ISS-011); the title and text scroll when the room is short, the action row never does. No spotlight, no
 * scrim; a tap outside it plays on, and only Skip, Next, doing the action or Back move on.
 */
@Composable
internal fun BoxScope.CoachTipOverlay(coach: CoachUi?, targets: CoachTargets) {
    var host by remember { mutableStateOf<Rect?>(null) }
    Box(Modifier.matchParentSize().onGloballyPositioned { host = it.boundsInRoot() })
    val tip = coach?.tip ?: return
    val pad = targets.pad ?: return
    val anchor = targets.anchors[tip] ?: return
    val h = host ?: return
    val density = LocalDensity.current
    val p = with(density) {
        CoachPlacement.of(
            pad = pad.translate(-h.left, -h.top),
            anchor = anchor.translate(-h.left, -h.top),
            boardTop = (targets.boardTop ?: h.top) - h.top,
            hostHeight = h.height,
            maxWidth = 320.dp.toPx(),
            margin = 8.dp.toPx(),
            caret = CARET.toPx(),
            corner = 12.dp.toPx(),
            side = tip != CoachTip.LOCK,
        )
    }
    val caretColor = MaterialTheme.colorScheme.inverseSurface
    // The leader runs over the screen's own background, where the ink colour can vanish (dark themes): it takes the
    // accent, which reads on the background in all four themes.
    val leaderColor = MaterialTheme.colorScheme.primary
    // The tip is measured at the placement's width and height cap, then placed with its caret on the pad's top edge.
    Layout(
        content = {
            Column {
                val scheme = MaterialTheme.colorScheme
                Surface(
                    modifier = Modifier.weight(1f, fill = false),
                    shape = RoundedCornerShape(12.dp),
                    color = scheme.inverseSurface,
                    contentColor = scheme.inverseOnSurface,
                    shadowElevation = 3.dp,
                ) { CoachTipContent(coach, tip) }
                Box(
                    Modifier.fillMaxWidth().height(CARET).drawBehind {
                        if (p.stemX != null) return@drawBehind // a tool tip's leader is drawn below
                        val x = p.caretX
                        drawPath(Path().apply {
                            moveTo(x - size.height, 0f)
                            lineTo(x + size.height, 0f)
                            lineTo(x, size.height)
                            close()
                        }, caretColor)
                    },
                )
            }
            // ISS-014: a tool tip's leader (under the tip), from the tip's end edge down the pad's end margin to its tool.
            Box(Modifier.drawBehind {
                val stemX = p.stemX ?: return@drawBehind
                val head = CARET.toPx()
                val stroke = 3.dp.toPx()
                val from = p.bottom - head - 12.dp.toPx()
                drawLine(leaderColor, Offset(stemX - stroke / 2, from), Offset(stemX - stroke / 2, p.pointY), stroke)
                drawLine(leaderColor, Offset(stemX, p.pointY), Offset(p.pointX + head, p.pointY), stroke)
                drawPath(Path().apply {
                    moveTo(p.pointX, p.pointY)
                    lineTo(p.pointX + head * 1.25f, p.pointY - head)
                    lineTo(p.pointX + head * 1.25f, p.pointY + head)
                    close()
                }, leaderColor)
            })
            // Where the caret's point lands; tests check it lies on the anchor (ISS-014). No content, so TalkBack skips it.
            Box(Modifier.size(2.dp).semantics { testTag = "coach-caret" })
        },
        modifier = Modifier.matchParentSize(),
    ) { measurables, constraints ->
        val w = p.width.roundToInt()
        val tipPlaceable = measurables[0].measure(Constraints(minWidth = w, maxWidth = w, maxHeight = p.maxHeight.toInt()))
        val leader = measurables[1].measure(Constraints.fixed(constraints.maxWidth, constraints.maxHeight))
        val point = measurables[2].measure(Constraints())
        layout(constraints.maxWidth, constraints.maxHeight) {
            leader.place(0, 0)
            tipPlaceable.place(p.left.roundToInt(), p.bottom.toInt() - tipPlaceable.height)
            point.place(p.pointX.roundToInt() - point.width / 2, p.pointY.roundToInt() - point.height / 2)
        }
    }
}

private val CARET = 8.dp

@Composable
private fun CoachTipContent(coach: CoachUi, tip: CoachTip) {
    val number = tip.ordinal + 1
    val total = CoachTip.entries.size
    val title = stringResource(tip.title)
    val body = stringResource(tip.body)
    val spoken = stringResource(R.string.coach_announce, number, total, title, body)
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.semantics {
            testTag = "coach-mark"
            liveRegion = LiveRegionMode.Polite
        }.padding(start = 16.dp, end = 16.dp - SCROLL_GUTTER, top = 12.dp, bottom = 4.dp),
    ) {
        // Title and text scroll when the room above the pad is short (360×640 at 200%); the actions below never do.
        val scroll = rememberScrollState()
        Column(
            Modifier.weight(1f, fill = false).scrollCue(scroll, fade = scheme.inverseSurface, thumb = scheme.inverseOnSurface)
                .verticalScroll(scroll).padding(end = SCROLL_GUTTER).semantics { testTag = "coach-text" },
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading(); contentDescription = spoken })
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
        // "1 of 3 · Skip · Next" on one line while it fits; at large text the buttons wrap under the count. All three in
        // inversePrimary on inverseSurface (design-system §2 actionContentColor; ISS-012): a TextButton's own default is
        // primary, which is unreadable on the ink container in three of the four themes.
        val action = scheme.inversePrimary
        val buttons = coachActionColors(scheme)
        FlowRow(
            Modifier.fillMaxWidth().padding(end = SCROLL_GUTTER),
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.coach_progress, number, total), style = MaterialTheme.typography.labelMedium, color = action,
                modifier = Modifier.weight(1f).padding(end = 8.dp))
            val skip = stringResource(R.string.coach_skip_description)
            TextButton(coach.onSkip, Modifier.heightIn(min = 48.dp).semantics { contentDescription = skip; testTag = "coach-skip" }, colors = buttons) {
                Text(stringResource(R.string.coach_skip))
            }
            TextButton(coach.onNext, Modifier.heightIn(min = 48.dp).semantics { testTag = "coach-next" }, colors = buttons) {
                Text(stringResource(if (number == total) R.string.coach_done else R.string.coach_next))
            }
        }
    }
}

/** Skip / Next / Got it on the ink tip: inversePrimary text, no fill (ISS-012; PaletteTest checks it in every theme). */
internal fun coachActionColors(scheme: ColorScheme) = ButtonColors(
    containerColor = Color.Transparent,
    contentColor = scheme.inversePrimary,
    disabledContainerColor = Color.Transparent,
    disabledContentColor = scheme.inverseOnSurface.copy(alpha = 0.38f),
)
