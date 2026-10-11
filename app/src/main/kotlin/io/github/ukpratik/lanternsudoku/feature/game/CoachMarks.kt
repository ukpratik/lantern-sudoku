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

/** One placement rule for all three tips (D-046). Pure, so it is unit-tested; all values in px of the host's coordinates. */
data class CoachPlacement(val left: Float, val bottom: Float, val width: Float, val maxHeight: Float, val caretX: Float) {
    companion object {
        /**
         * The tip sits **above the pad**, its caret [caret] px tall pointing down at the anchor's centre, the caret's point
         * on the pad's top edge ([bottom]): it covers part of the board, never a key or a tool. It is as wide as the pad less [margin] each side,
         * at most [maxWidth], centred on the anchor where it can be. Its height is capped by the room between the board's
         * top ([boardTop], or the host's top if the board scrolled away) and the pad, so it always stays on screen inside
         * the safe-drawing area (the host); text beyond that scrolls, the actions don't (D-038 panels). [maxHeight]
         * includes the caret.
         */
        fun of(pad: Rect, anchor: Rect, boardTop: Float, hostHeight: Float, maxWidth: Float, margin: Float, caret: Float, corner: Float): CoachPlacement {
            val width = minOf(maxWidth, pad.width - 2 * margin).coerceAtLeast(0f)
            val left = (anchor.center.x - width / 2).coerceIn(pad.left + margin, maxOf(pad.left + margin, pad.right - margin - width))
            val bottom = minOf(pad.top, hostHeight) // the caret's point
            val top = maxOf(boardTop, 0f) + margin
            val caretX = (anchor.center.x - left).coerceIn(corner + caret, maxOf(corner + caret, width - corner - caret))
            return CoachPlacement(left, bottom, width, (bottom - top).coerceAtLeast(0f), caretX)
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
        )
    }
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
                val caretColor = scheme.inverseSurface
                Box(
                    Modifier.fillMaxWidth().height(CARET).drawBehind {
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
        },
        modifier = Modifier.matchParentSize(),
    ) { measurables, constraints ->
        val w = p.width.roundToInt()
        val tipPlaceable = measurables.first().measure(Constraints(minWidth = w, maxWidth = w, maxHeight = p.maxHeight.toInt()))
        layout(constraints.maxWidth, constraints.maxHeight) {
            tipPlaceable.place(p.left.roundToInt(), p.bottom.toInt() - tipPlaceable.height)
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
