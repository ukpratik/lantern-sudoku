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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ukpratik.lanternsudoku.R
import io.github.ukpratik.lanternsudoku.engine.game.Action
import io.github.ukpratik.lanternsudoku.engine.game.GameState
import io.github.ukpratik.lanternsudoku.ui.theme.LanternColors
import io.github.ukpratik.lanternsudoku.ui.theme.LocalLanternColors
import io.github.ukpratik.lanternsudoku.ui.theme.UiFamily

/** 3×3 number pad + tool column (design §5). Regular: 256 dp tall; compact: 168 dp with a 2-column tool grid. */
@Composable
fun Controls(game: GameState, compact: Boolean, colors: LanternColors, onAction: (Action) -> Unit, modifier: Modifier = Modifier) {
    val height = if (compact) 168.dp else 256.dp
    Row(modifier.fillMaxWidth().height(height).padding(horizontal = 16.dp)) {
        NumberPad(game, compact, colors, onAction, Modifier.weight(1f).fillMaxHeight())
        Spacer(Modifier.width(12.dp))
        if (compact) ToolGrid(game, onAction) else ToolColumn(game, onAction)
    }
}

@Composable
private fun NumberPad(game: GameState, compact: Boolean, colors: LanternColors, onAction: (Action) -> Unit, modifier: Modifier) {
    val gap = if (compact) 6.dp else 8.dp
    Column(modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
        for (r in 0 until 3) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(gap)) {
                for (c in 0 until 3) {
                    val d = r * 3 + c + 1
                    NumberKey(
                        digit = d,
                        remaining = game.remaining(d),
                        locked = game.input.lock == d,
                        notesMode = game.input.notesMode,
                        compact = compact,
                        colors = colors,
                        onTap = { onAction(Action.TapDigit(d)) },
                        onLongPress = { onAction(Action.LongPressDigit(d)) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
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
            Box(Modifier.fillMaxSize().padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 20.dp)) {
                Text(
                    digit.toString(),
                    color = fg,
                    fontFamily = UiFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 19.sp,
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
                fontSize = if (compact) 26.sp else 30.sp,
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
                Text(remaining.toString(), color = countColor, fontFamily = UiFamily, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
            }
        }
    }
}

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
            R.drawable.ic_notes,
            R.string.tool_notes,
            sub = if (game.input.notesMode) R.string.on else R.string.off,
            on = game.input.notesMode,
            action = Action.ToggleNotes,
        ),
        Tool("hint", R.drawable.ic_lightbulb, R.string.tool_hint, action = Action.OpenHint, tint = hintTint),
    )
}

@Composable
private fun ToolColumn(game: GameState, onAction: (Action) -> Unit) {
    Column(Modifier.width(100.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (t in tools(game)) ToolButton(t, compact = false, onAction = onAction, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ToolGrid(game: GameState, onAction: (Action) -> Unit) {
    val all = tools(game)
    Column(Modifier.width(116.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (pair in all.take(4).chunked(2)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (t in pair) ToolButton(t, compact = true, onAction = onAction, modifier = Modifier.weight(1f))
            }
        }
        ToolButton(all[4], compact = false, onAction = onAction, modifier = Modifier.fillMaxWidth())
    }
}

/** Tool button (design §5): icon + label, 48 dp; disabled at 42% with the label kept; "on" = amber-soft pill + outline. */
@Composable
private fun ToolButton(t: Tool, compact: Boolean, onAction: (Action) -> Unit, modifier: Modifier) {
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
        .height(48.dp)
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
    val iconSize: Dp = if (compact) 20.dp else 22.dp
    if (compact) {
        Column(base, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(painterResource(t.icon), contentDescription = null, tint = t.tint ?: scheme.onSurface, modifier = Modifier.size(iconSize))
            Text(
                if (t.sub != null) "$label ${stringResource(t.sub)}" else label,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
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
