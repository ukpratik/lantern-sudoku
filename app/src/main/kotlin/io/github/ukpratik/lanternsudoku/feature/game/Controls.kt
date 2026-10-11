// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.feature.game

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ukpratik.lanternsudoku.R
import io.github.ukpratik.lanternsudoku.engine.game.Action
import io.github.ukpratik.lanternsudoku.engine.game.GameState
import io.github.ukpratik.lanternsudoku.ui.theme.LanternColors
import io.github.ukpratik.lanternsudoku.ui.theme.LocalDigitScale
import io.github.ukpratik.lanternsudoku.ui.theme.LocalLanternColors
import io.github.ukpratik.lanternsudoku.ui.theme.UiFamily

/**
 * 3×3 number pad + tool column (design §5). Regular: 256 dp tall; compact: 168 dp with a 2-column tool grid. With large
 * text (US-021) tool labels move under their icons and [height]/[toolHeight] come from [GameChrome]; keys stretch to
 * the tools' height.
 */
@Composable
fun Controls(
    game: GameState,
    compact: Boolean,
    colors: LanternColors,
    onAction: (Action) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = if (compact) 168.dp else 256.dp,
    toolHeight: Dp = 48.dp,
    largeText: Boolean = false,
    iconOnlyTools: Boolean = false,
    coach: CoachUi? = null,
) {
    Row(modifier.fillMaxWidth().height(height).padding(horizontal = 16.dp)) {
        NumberPad(game, compact, colors, onAction, Modifier.weight(1f).fillMaxHeight(), coach)
        Spacer(Modifier.width(12.dp))
        if (compact) ToolGrid(game, onAction, toolHeight, largeText, iconOnlyTools, coach) else ToolColumn(game, onAction, toolHeight, largeText, coach)
    }
}

/** The key the "lock a number" tip points at: 5 as in the design, or the first digit that can still be locked. */
private fun coachDigit(game: GameState): Int = (listOf(5) + (1..9)).firstOrNull { !game.isComplete(it) } ?: 5

@Composable
private fun NumberPad(game: GameState, compact: Boolean, colors: LanternColors, onAction: (Action) -> Unit, modifier: Modifier, coach: CoachUi?) {
    val gap = if (compact) 6.dp else 8.dp
    val tipDigit = coachDigit(game)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
        for (r in 0 until 3) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(gap)) {
                for (c in 0 until 3) {
                    val d = r * 3 + c + 1
                    val key: @Composable (Modifier) -> Unit = { m -> NumberKey(
                        digit = d,
                        remaining = game.remaining(d),
                        locked = game.input.lock == d,
                        notesMode = game.input.notesMode,
                        compact = compact,
                        colors = colors,
                        onTap = { onAction(Action.TapDigit(d)) },
                        onLongPress = { onAction(Action.LongPressDigit(d)) },
                        modifier = m,
                    ) }
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        if (d == tipDigit) CoachAnchor(coach, CoachTip.LOCK) { key(Modifier.fillMaxSize()) } else key(Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}

/**
 * Number key states (design §5): default (digit + count), locked (inverse fill, amber outline, lock glyph), complete
 * (dashed border, ✓ instead of the count; stays tappable), notes mode (digit at its 3×3 note position + pen glyph).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NumberKey(
    digit: Int,
    remaining: Int,
    locked: Boolean,
    notesMode: Boolean,
    compact: Boolean,
    colors: LanternColors,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier,
) {
    val complete = remaining == 0
    val shape = RoundedCornerShape(16.dp)
    // Key digits scale with Larger digits only, never the system font scale (design §3.1: 30 → 36): sized in dp and
    // converted with the density's own (possibly non-linear) font-scale curve. The count is capped at 1.3× system scale.
    val density = LocalDensity.current
    val digitScale = LocalDigitScale.current
    val keyDigit = with(density) { ((if (compact) 26 else 30).dp * digitScale).toSp() }
    val noteDigitDp = 19.dp
    val countSize = with(density) { (12.5.dp * minOf(fontScale, 1.3f)).toSp() }
    val description = when {
        complete -> stringResource(R.string.key_complete, digit)
        locked -> stringResource(R.string.key_locked, digit, remaining)
        notesMode -> stringResource(R.string.key_note_mode, digit, remaining)
        else -> stringResource(R.string.key_left, digit, remaining)
    }
    val bg = when {
        locked -> colors.lockedKeyBg
        complete -> Color.Transparent
        else -> colors.keyBg
    }
    val fg = when {
        locked -> colors.lockedKeyFg
        complete -> colors.doneKeyFg
        else -> colors.keyFg
    }
    val countColor = when {
        locked -> colors.lockedKeyCount
        complete -> colors.doneKeyFg
        else -> colors.keyCount
    }
    Box(
        modifier
            .clip(shape)
            .background(bg)
            .drawBehind {
                val r = CornerRadius(16.dp.toPx())
                when {
                    locked -> drawRoundRect(colors.lockedKeyRing, cornerRadius = r, style = Stroke(3.dp.toPx()))
                    complete -> drawRoundRect(
                        colors.doneKeyBorder,
                        cornerRadius = r,
                        style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
                    )
                    else -> drawRoundRect(colors.keyBorder, cornerRadius = r, style = Stroke(1.5.dp.toPx()))
                }
            }
            .combinedClickable(onClick = onTap, onLongClick = onLongPress)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                testTag = "key-$digit"
                onClick { onTap(); true }
                onLongClick { onLongPress(); true }
            },
    ) {
        if (notesMode && !locked) {
            // The digit drops to pencil size at its note position (1 top-left … 9 bottom-right).
            val col = (digit - 1) % 3
            val row = (digit - 1) / 3
            BoxWithConstraints(Modifier.fillMaxSize().padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 20.dp)) {
                // Three rows of pencil digits must fit the key (compact keys are 52 dp): shrink below 19 dp if needed.
                val size = with(density) { minOf(noteDigitDp * digitScale, maxHeight / 3).toSp() }
                Text(
                    digit.toString(),
                    color = fg,
                    fontFamily = UiFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = size,
                    style = tightLine(size),
                    modifier = Modifier.align(
                        when (row * 3 + col) {
                            0 -> Alignment.TopStart
                            1 -> Alignment.TopCenter
                            2 -> Alignment.TopEnd
                            3 -> Alignment.CenterStart
                            4 -> Alignment.Center
                            5 -> Alignment.CenterEnd
                            6 -> Alignment.BottomStart
                            7 -> Alignment.BottomCenter
                            else -> Alignment.BottomEnd
                        },
                    ),
                )
            }
            Icon(
                painterResource(R.drawable.ic_notes),
                contentDescription = null,
                tint = countColor,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 7.dp).size(14.dp),
            )
        } else {
            Text(
                digit.toString(),
                color = fg,
                fontFamily = UiFamily,
                fontWeight = when {
                    locked -> FontWeight.Bold
                    complete -> FontWeight.Normal
                    else -> FontWeight.Medium
                },
                fontSize = keyDigit,
                style = tightLine(keyDigit),
                modifier = Modifier.align(Alignment.Center),
            )
        }
        if (locked) {
            Icon(painterResource(R.drawable.ic_lock), contentDescription = null, tint = fg,
                modifier = Modifier.align(Alignment.TopStart).padding(start = 8.dp, top = 7.dp).size(16.dp))
        }
        Box(Modifier.align(Alignment.BottomEnd).padding(end = if (compact) 8.dp else 10.dp, bottom = if (compact) 5.dp else 7.dp)) {
            if (complete) {
                Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = countColor, modifier = Modifier.size(16.dp))
            } else {
                Text(remaining.toString(), color = countColor, fontFamily = UiFamily, fontWeight = FontWeight.Bold, fontSize = countSize,
                    style = tightLine(countSize))
            }
        }
    }
}

/**
 * Key glyphs set their own line height: MaterialTheme provides body-large's 23 sp line height to every Text, which at
 * 200% font scale would make a fixed-size key digit's line box far taller than the key (US-021).
 */
@Composable
private fun tightLine(size: TextUnit): TextStyle = LocalTextStyle.current.copy(
    lineHeight = size,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
)

private data class Tool(
    val tag: String,
    @param:DrawableRes val icon: Int,
    val label: Int,
    val sub: Int? = null,
    val on: Boolean = false,
    val enabled: Boolean = true,
    val action: Action?,
    val tint: Color? = null,
)

@Composable
private fun tools(game: GameState): List<Tool> {
    val hintTint = LocalLanternColors.current.hintIcon
    return listOf(
        Tool("undo", R.drawable.ic_undo, R.string.tool_undo, enabled = game.undo.isNotEmpty(), action = Action.Undo),
        Tool("redo", R.drawable.ic_redo, R.string.tool_redo, enabled = game.redo.isNotEmpty(), action = Action.Redo),
        Tool("erase", R.drawable.ic_erase, R.string.tool_erase, on = game.input.eraseMode, action = Action.TapErase),
        Tool(
            "notes",
            if (game.input.notesMode) R.drawable.ic_notes_filled else R.drawable.ic_notes,
            R.string.tool_notes,
            sub = if (game.input.notesMode) R.string.on else R.string.off,
            on = game.input.notesMode,
            action = Action.ToggleNotes,
        ),
        Tool("hint", R.drawable.ic_lightbulb, R.string.tool_hint, action = Action.OpenHint, tint = hintTint),
    )
}

@Composable
private fun ToolColumn(game: GameState, onAction: (Action) -> Unit, toolHeight: Dp, largeText: Boolean, coach: CoachUi?) {
    Column(Modifier.width(if (largeText) 112.dp else 100.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (t in tools(game)) Anchored(t, coach) { ToolButton(t, stacked = largeText, height = toolHeight, onAction = onAction, modifier = Modifier.fillMaxWidth()) }
    }
}

/** The Notes and Hint tools carry their coach-mark tips (US-024). */
@Composable
private fun Anchored(t: Tool, coach: CoachUi?, content: @Composable () -> Unit) {
    when (t.tag) {
        "notes" -> CoachAnchor(coach, CoachTip.NOTES, content)
        "hint" -> CoachAnchor(coach, CoachTip.HINT, content)
        else -> content()
    }
}

@Composable
private fun ToolGrid(game: GameState, onAction: (Action) -> Unit, toolHeight: Dp, largeText: Boolean, iconOnly: Boolean, coach: CoachUi?) {
    val all = tools(game)
    // D-037: on the smallest phones with large text the grid shows icons only (TalkBack keeps the full names and states),
    // so the pad keeps its 168 dp and the board and pad fit without scrolling (ISS-004).
    Column(Modifier.width(if (largeText && !iconOnly) 160.dp else 116.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (pair in all.take(4).chunked(2)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (t in pair) {
                    Box(Modifier.weight(1f)) {
                        Anchored(t, coach) {
                            ToolButton(t, stacked = true, height = toolHeight, onAction = onAction, modifier = Modifier.fillMaxWidth(), small = !largeText, iconOnly = iconOnly)
                        }
                    }
                }
            }
        }
        Anchored(all[4], coach) {
            ToolButton(all[4], stacked = largeText && !iconOnly, height = toolHeight, onAction = onAction, modifier = Modifier.fillMaxWidth(), iconOnly = iconOnly)
        }
    }
}

/**
 * Tool button (design §5): icon + label, 48 dp; disabled at 42% with the label kept; "on" = amber-soft pill + outline
 * (+ filled icon for Notes). [stacked] puts the label under the icon (compact grid, and large text, design §3.1).
 */
@Composable
private fun ToolButton(
    t: Tool,
    stacked: Boolean,
    height: Dp,
    onAction: (Action) -> Unit,
    modifier: Modifier,
    small: Boolean = false,
    iconOnly: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val label = stringResource(t.label)
    val state = when {
        !t.enabled -> stringResource(R.string.tool_state, label, stringResource(R.string.unavailable))
        t.sub != null -> stringResource(R.string.tool_state, label, stringResource(t.sub))
        t.on -> stringResource(R.string.tool_state, label, stringResource(R.string.on))
        else -> label
    }
    val shape = RoundedCornerShape(14.dp)
    val base = modifier
        .height(height)
        .clip(shape)
        .then(if (t.on) Modifier.background(scheme.secondaryContainer).border(BorderStroke(1.5.dp, scheme.primary), shape) else Modifier)
        .clickable(enabled = t.enabled && t.action != null, role = Role.Button) { t.action?.let(onAction) }
        .clearAndSetSemantics {
            contentDescription = state
            role = Role.Button
            testTag = "tool-${t.tag}"
            if (!t.enabled) disabled()
            if (t.enabled && t.action != null) onClick { onAction(t.action); true }
        }
        .alpha(if (t.enabled) 1f else 0.42f)
    val iconSize: Dp = if (small) 20.dp else 22.dp
    if (iconOnly) {
        Box(base, contentAlignment = Alignment.Center) {
            Icon(painterResource(t.icon), contentDescription = null, tint = t.tint ?: scheme.onSurface, modifier = Modifier.size(24.dp))
        }
    } else if (stacked) {
        Column(base, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(painterResource(t.icon), contentDescription = null, tint = t.tint ?: scheme.onSurface, modifier = Modifier.size(iconSize))
            // Compact keeps "Notes Off" at 11 sp; large text shows the name only (On = pill + filled icon; TalkBack says it).
            Text(
                if (t.sub != null && small) "$label ${stringResource(t.sub)}" else label,
                style = if (small) MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp) else MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
        }
    } else {
        Row(base.padding(start = 10.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(t.icon), contentDescription = null, tint = t.tint ?: scheme.onSurface, modifier = Modifier.size(iconSize))
            Spacer(Modifier.width(8.dp))
            Column {
                Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                if (t.sub != null) {
                    Text(
                        stringResource(t.sub),
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, fontWeight = if (t.on) FontWeight.Bold else FontWeight.Medium),
                        color = if (t.on) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
