// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.feature.game

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
 * Wraps one anchor (a number key, the Notes or Hint tool) in an anchored, persistent M3 rich tooltip (design review #6,
 * D-020): title, one sentence and "1 of 3 · Skip · Next", in the inverse (ink) colours, max 320 dp, caret to the
 * anchor. No spotlight and no scrim: the popup isn't focusable, so the board and pad stay playable under it, and a tap
 * outside doesn't dismiss it (only Skip, Next, doing the action or Back do).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CoachAnchor(coach: CoachUi?, tip: CoachTip, content: @Composable () -> Unit) {
    val state = rememberTooltipState(isPersistent = true)
    val active = coach?.tip == tip
    // M3 1.4 misplaces the caret when a centred tooltip would cross the window's left edge (it shifts the caret by the
    // overflow although the popup is clamped). Keep the tooltip narrow enough to stay centred on an anchor near the left
    // (the "5" key): at most twice the anchor's distance from the left edge, never wider than the design's 320 dp.
    val density = LocalDensity.current
    var anchorCenter by remember { mutableStateOf(Float.MAX_VALUE) }
    val maxWidth = with(density) { minOf(320.dp, (2 * anchorCenter).toDp() - 2.dp) }
    // show() suspends while the tip is up; leaving composition or another tip cancels it, which hides it.
    LaunchedEffect(active) { if (active) state.show() }
    val scheme = MaterialTheme.colorScheme
    TooltipBox(
        // Centred over the anchor: M3 1.4 places the rich tooltip's caret as if the tooltip were centred (caretX).
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(8.dp),
        tooltip = {
            if (coach != null && active) {
                val number = tip.ordinal + 1
                val total = CoachTip.entries.size
                val title = stringResource(tip.title)
                val body = stringResource(tip.body)
                val spoken = stringResource(R.string.coach_announce, number, total, title, body)
                RichTooltip(
                    modifier = Modifier.semantics {
                        testTag = "coach-mark"
                        liveRegion = LiveRegionMode.Polite
                    },
                    title = { Text(title, modifier = Modifier.semantics { heading(); contentDescription = spoken }) },
                    action = {
                        // "1 of 3 · Skip · Next" on one line while it fits; at large text the buttons wrap under the count.
                        FlowRow(
                            Modifier.fillMaxWidth().padding(bottom = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                            itemVerticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(stringResource(R.string.coach_progress, number, total), style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.weight(1f).padding(end = 8.dp))
                            val skip = stringResource(R.string.coach_skip_description)
                            TextButton(coach.onSkip, Modifier.heightIn(min = 48.dp).semantics { contentDescription = skip; testTag = "coach-skip" }) {
                                Text(stringResource(R.string.coach_skip))
                            }
                            TextButton(coach.onNext, Modifier.heightIn(min = 48.dp).semantics { testTag = "coach-next" }) {
                                Text(stringResource(if (number == total) R.string.coach_done else R.string.coach_next))
                            }
                        }
                    },
                    caretShape = TooltipDefaults.caretShape(),
                    maxWidth = maxWidth,
                    colors = TooltipDefaults.richTooltipColors(
                        containerColor = scheme.inverseSurface,
                        contentColor = scheme.inverseOnSurface,
                        titleContentColor = scheme.inverseOnSurface,
                        actionContentColor = scheme.inversePrimary,
                    ),
                ) { Text(body, style = MaterialTheme.typography.bodyMedium) }
            }
        },
        state = state,
        onDismissRequest = {}, // a tap outside plays on; the tip stays (design coach-mark)
        focusable = false,
        enableUserInput = false,
        hasAction = true,
        content = { Box(Modifier.onGloballyPositioned { anchorCenter = it.boundsInWindow().center.x }) { content() } },
    )
}
