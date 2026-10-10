// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.feature.game

import android.content.res.Resources
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ukpratik.lanternsudoku.R
import io.github.ukpratik.lanternsudoku.data.CompletionSummary
import io.github.ukpratik.lanternsudoku.data.ShareFacts
import io.github.ukpratik.lanternsudoku.ui.board.MiniBoard
import io.github.ukpratik.lanternsudoku.ui.components.PrimaryButton
import io.github.ukpratik.lanternsudoku.ui.components.SHORT_DATE
import io.github.ukpratik.lanternsudoku.ui.components.SecondaryButton
import io.github.ukpratik.lanternsudoku.ui.components.StreakLine
import io.github.ukpratik.lanternsudoku.ui.components.Tile
import io.github.ukpratik.lanternsudoku.ui.components.formatElapsed
import io.github.ukpratik.lanternsudoku.ui.components.label
import io.github.ukpratik.lanternsudoku.ui.components.spokenElapsed
import io.github.ukpratik.lanternsudoku.ui.theme.DisplayFamily
import io.github.ukpratik.lanternsudoku.ui.theme.LocalLanternColors

/**
 * Completion (US-018; design completion, completion-daily-share): a state of the Game screen, not a destination
 * (D-020). The pad and tools give way to the summary and the solved board shrinks into a glowing card. Close or Back
 * leaves for Play. Dailies add the streak line and Share result as the primary action (US-017).
 */
@Composable
internal fun CompletionContent(
    gridSummary: String,
    summary: CompletionSummary,
    onClose: () -> Unit,
    onNewGame: () -> Unit,
    onShare: (ShareFacts) -> Unit,
    modifier: Modifier = Modifier,
) {
    val res = LocalResources.current
    val level = stringResource(summary.level.label)
    val date = summary.dailyDate
    val announcement = completionAnnouncement(res, summary)
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.close)) }
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier.semantics {
                    contentDescription = announcement
                    liveRegion = LiveRegionMode.Polite
                    heading()
                },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (date == null) stringResource(R.string.completion_eyebrow, level) else stringResource(R.string.completion_daily_eyebrow, date.format(SHORT_DATE), level),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(stringResource(R.string.completed_title), fontFamily = DisplayFamily, fontSize = 34.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp, bottom = 14.dp))
            }
            GlowingBoard(gridSummary)
            Spacer(Modifier.height(18.dp))
            SummaryTiles(res, summary, level)
            summary.daily?.let {
                Spacer(Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) { StreakLine(it, Modifier.padding(14.dp)) }
            }
            Spacer(Modifier.height(16.dp))
        }
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val share = summary.share
            val newGame = stringResource(R.string.new_level_game, level)
            if (share != null) {
                val shareDescription = stringResource(R.string.share_result_description)
                PrimaryButton(stringResource(R.string.share_result), { onShare(share) }, icon = R.drawable.ic_share,
                    modifier = Modifier.fillMaxWidth().height(56.dp).semantics { contentDescription = shareDescription })
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton(newGame, onNewGame, Modifier.weight(1f).heightIn(min = 48.dp))
                    SecondaryButton(stringResource(R.string.home), onClose, Modifier.weight(1f).heightIn(min = 48.dp))
                }
            } else {
                PrimaryButton(newGame, onNewGame, icon = R.drawable.ic_add, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp))
                SecondaryButton(stringResource(R.string.home), onClose, Modifier.fillMaxWidth().heightIn(min = 56.dp))
            }
        }
    }
}

@Composable
private fun SummaryTiles(res: Resources, summary: CompletionSummary, level: String) {
    val timeSub = when {
        summary.newBest -> stringResource(R.string.tile_new_best, level)
        summary.previousBestMs != null -> stringResource(R.string.tile_best, formatElapsed(summary.previousBestMs))
        else -> null
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile(formatElapsed(summary.elapsedMs), stringResource(R.string.tile_time), Modifier.weight(1f), sub = timeSub)
            Tile(summary.hints.toString(), res.getQuantityString(R.plurals.tile_hints, summary.hints), Modifier.weight(1f),
                sub = hintsByStage(res, summary.hints1, summary.hints2, summary.hints3))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile(summary.mistakes.toString(), res.getQuantityString(R.plurals.tile_mistakes, summary.mistakes), Modifier.weight(1f))
            val clean = summary.clean
            Tile(
                value = if (clean) "✓" else stringResource(R.string.stat_none),
                label = stringResource(R.string.tile_clean),
                modifier = Modifier.weight(1f),
                sub = stringResource(if (clean) R.string.tile_clean_yes else R.string.tile_clean_no),
                icon = if (clean) {
                    { Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp)) }
                } else {
                    null
                },
            )
        }
    }
}

/**
 * The solved board in a glowing card (design completion). Calm animation: the boxes warm up one by one in amber
 * (9 × 60 ms, 600 ms, decelerate) and settle to the glow. With "Remove animations" on, the final state appears with a
 * 150 ms fade instead (TC-158).
 */
@Composable
private fun GlowingBoard(gridSummary: String) {
    val k = LocalLanternColors.current
    val reduced = rememberReducedMotion()
    val warm = remember { Animatable(if (reduced) 1f else 0f) }
    val settle = remember { Animatable(if (reduced) 1f else 0f) }
    val fade = remember { Animatable(if (reduced) 0f else 1f) }
    LaunchedEffect(reduced) {
        if (reduced) {
            fade.animateTo(1f, tween(150))
        } else {
            warm.animateTo(1f, tween(600, easing = LinearOutSlowInEasing))
            settle.animateTo(1f, tween(400, easing = LinearEasing))
        }
    }
    val amber = MaterialTheme.colorScheme.primaryContainer
    Box(Modifier.size(240.dp).alpha(fade.value), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val glow = k.glow.copy(alpha = k.glow.alpha * (0.4f + 0.6f * warm.value))
            drawCircle(Brush.radialGradient(listOf(glow, Color.Transparent), radius = size.minDimension / 2f), radius = size.minDimension / 2f)
        }
        Box(Modifier.size(200.dp).clip(RoundedCornerShape(8.dp))) {
            MiniBoard(gridSummary, 200.dp, k)
            Canvas(Modifier.matchParentSize()) {
                val box = size.width / 3f
                for (b in 0 until 9) {
                    val local = (warm.value * 9f - b).coerceIn(0f, 1f)
                    val a = 0.32f * local * (1f - settle.value)
                    if (a <= 0f) continue
                    drawRoundRect(amber.copy(alpha = a), topLeft = Offset((b % 3) * box, (b / 3) * box), size = Size(box, box),
                        cornerRadius = CornerRadius(2.dp.toPx()))
                }
            }
        }
    }
}

/** True when the system "Remove animations" setting is on (animator duration scale 0). */
@Composable
internal fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}

/**
 * Hints by deepest stage (US-018): "went to Why" (one), "both to Do it" (two at one stage), "all to Where", or
 * "1 Where · 1 Do it" when they differ; "none taken" without hints.
 */
internal fun hintsByStage(res: Resources, h1: Int, h2: Int, h3: Int): String {
    val counts = intArrayOf(h1, h2, h3)
    val names = listOf(R.string.hint_stage_where, R.string.hint_stage_why, R.string.hint_stage_do).map(res::getString)
    val total = h1 + h2 + h3
    val used = counts.indices.filter { counts[it] > 0 }
    return when {
        total == 0 -> res.getString(R.string.hints_none)
        used.size == 1 -> res.getString(
            when (total) {
                1 -> R.string.hints_one_to
                2 -> R.string.hints_both_to
                else -> R.string.hints_all_to
            },
            names[used[0]],
        )
        else -> used.joinToString(" · ") { res.getString(R.string.hints_stage_count, counts[it], names[it]) }
    }
}

/** "Puzzle solved. Nice work. Time 21 minutes 6 seconds. 2 hints. 1 mistake." (design completion, TalkBack). */
internal fun completionAnnouncement(res: Resources, s: CompletionSummary): String {
    val hints = if (s.hints == 0) res.getString(R.string.no_hints) else res.getQuantityString(R.plurals.hints_spoken, s.hints, s.hints)
    val mistakes = if (s.mistakes == 0) res.getString(R.string.no_mistakes) else res.getQuantityString(R.plurals.mistakes_plural, s.mistakes, s.mistakes)
    return res.getString(
        R.string.completion_announcement, spokenElapsed(res, s.elapsedMs), hints.replaceFirstChar(Char::uppercaseChar), mistakes.replaceFirstChar(Char::uppercaseChar),
    )
}
