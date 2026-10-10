// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.ui.board

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.ukpratik.lanternsudoku.engine.game.BoardHighlights
import io.github.ukpratik.lanternsudoku.engine.hint.HintView
import io.github.ukpratik.lanternsudoku.engine.model.Masks
import io.github.ukpratik.lanternsudoku.ui.theme.LanternColors
import io.github.ukpratik.lanternsudoku.ui.theme.UiFamily

/** What one board frame shows: values, which are givens, notes, and the highlight flags (pure data for the canvas). */
@Immutable
class BoardModel(
    val values: IntArray,
    val givens: BooleanArray,
    val notes: IntArray,
    val highlights: BoardHighlights,
    /** The open hint's roles (US-012), or null. */
    val hint: HintView? = null,
)

/** Per-cell TalkBack text and state; built by the screen from strings.xml (US-022). */
@Immutable
class CellLabels(val description: (Int) -> String, val state: (Int) -> String?)

/**
 * The board: one [Canvas] for drawing plus a 9 × 9 overlay of transparent nodes for taps and TalkBack (LLD §4.10).
 * Digits are sized from the cell (0.62 × cell, 0.74 with Larger digits in M5), never from the system font scale.
 */
@Composable
fun Board(
    model: BoardModel,
    size: Dp,
    colors: LanternColors,
    labels: CellLabels,
    onTapCell: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer(cacheSize = 64)
    val shape = RoundedCornerShape(6.dp)
    Box(modifier.size(size).clip(shape)) {
        Canvas(Modifier.fillMaxSize()) { drawBoard(model, colors, measurer) }
        val cell = size / 9
        val interaction = remember { MutableInteractionSource() }
        Column(Modifier.fillMaxSize()) {
            for (r in 0 until 9) {
                Row {
                    for (c in 0 until 9) {
                        val i = r * 9 + c
                        val state = labels.state(i)
                        Box(
                            Modifier
                                .size(cell)
                                .clickable(interactionSource = interaction, indication = null) { onTapCell(i) }
                                .semantics {
                                    contentDescription = labels.description(i)
                                    if (state != null) stateDescription = state
                                    testTag = "cell-$i"
                                },
                        )
                    }
                }
            }
        }
    }
}

/** Draw order per LLD §4.10: fills → lines → glow/ring → digits and notes → marks. */
private fun DrawScope.drawBoard(m: BoardModel, k: LanternColors, measurer: TextMeasurer) {
    val cs = size.width / 9f
    val h = m.highlights
    drawRect(k.cell)

    // 1. Cell fills by priority (design §5): selected > conflict > same > cause (hatch) > house/target > peer.
    val hint = m.hint
    for (i in 0 until 81) {
        val f = h.flags(i)
        val cause = hint != null && hint.cause[i]
        val fill = when {
            f and BoardHighlights.SELECTED != 0 -> k.selected
            f and BoardHighlights.CONFLICT != 0 -> k.conflict
            f and BoardHighlights.SAME != 0 -> k.sameDigit
            cause -> null
            hint != null && hint.house[i] -> k.hintHouse
            f and BoardHighlights.PEER != 0 -> k.peer
            else -> null
        }
        if (fill != null) {
            drawRect(fill, topLeft = cellOrigin(i, cs), size = Size(cs, cs))
        } else if (cause) {
            drawHatch(cellOrigin(i, cs), cs, k)
        }
    }

    // 2. Lines: thin cell lines, then box lines (outer border included).
    val thin = 1.dp.toPx()
    for (n in 1 until 9) {
        if (n % 3 == 0) continue
        val p = n * cs
        drawLine(k.line, Offset(p, 0f), Offset(p, size.height), thin)
        drawLine(k.line, Offset(0f, p), Offset(size.width, p), thin)
    }
    val box = k.boxStroke.toPx()
    for (n in 1 until 3) {
        val p = n * 3 * cs
        drawLine(k.boxLine, Offset(p, 0f), Offset(p, size.height), box)
        drawLine(k.boxLine, Offset(0f, p), Offset(size.width, p), box)
    }
    drawRoundRect(k.boxLine, style = Stroke(box), topLeft = Offset(box / 2, box / 2),
        size = Size(size.width - box, size.height - box), cornerRadius = CornerRadius(6.dp.toPx()))

    // 3. Selection: inner glow (static radial gradient, no blur) + ring.
    for (i in 0 until 81) {
        if (h.flags(i) and BoardHighlights.SELECTED == 0) continue
        val o = cellOrigin(i, cs)
        val center = Offset(o.x + cs / 2, o.y + cs / 2)
        drawRect(Brush.radialGradient(listOf(Color.Transparent, k.glow), center, cs * 0.75f), topLeft = o, size = Size(cs, cs))
        val ring = 2.dp.toPx()
        drawRoundRect(k.selectedRing, topLeft = Offset(o.x + ring / 2, o.y + ring / 2), size = Size(cs - ring, cs - ring),
            cornerRadius = CornerRadius(3.dp.toPx()), style = Stroke(ring))
    }

    // 3b. Hint roles: the cause's dashed outline and the target's thick ring with a glow (shape, not colour alone).
    if (hint != null) {
        val dash = 2.dp.toPx()
        for (i in 0 until 81) {
            if (!hint.cause[i]) continue
            val o = cellOrigin(i, cs)
            val inset = 2.dp.toPx() + dash / 2
            drawRoundRect(k.hintCauseLine, topLeft = Offset(o.x + inset, o.y + inset), size = Size(cs - 2 * inset, cs - 2 * inset),
                cornerRadius = CornerRadius(5.dp.toPx()), style = Stroke(dash, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))))
        }
        if (hint.target >= 0) {
            val o = cellOrigin(hint.target, cs)
            val center = Offset(o.x + cs / 2, o.y + cs / 2)
            drawRect(Brush.radialGradient(listOf(Color.Transparent, k.glow), center, cs * 0.75f), topLeft = o, size = Size(cs, cs))
            val ring = k.ringStroke.toPx()
            val inset = 1.dp.toPx() + ring / 2
            drawRoundRect(k.hintTargetRing, topLeft = Offset(o.x + inset, o.y + inset), size = Size(cs - 2 * inset, cs - 2 * inset),
                cornerRadius = CornerRadius(8.dp.toPx()), style = Stroke(ring))
        }
    }

    // 4. Digits and notes.
    val digitStyle = TextStyle(fontFamily = UiFamily, fontSize = (cs * DIGIT_RATIO).toSp())
    val noteStyle = TextStyle(fontFamily = UiFamily, fontWeight = FontWeight.Medium, fontSize = (cs * NOTE_RATIO).toSp())
    for (i in 0 until 81) {
        val o = cellOrigin(i, cs)
        val v = m.values[i]
        val f = h.flags(i)
        if (v != 0) {
            val mistake = f and BoardHighlights.MISTAKE != 0
            val color = when {
                mistake -> k.danger
                m.givens[i] -> k.given
                else -> k.entry
            }
            val weight = if (m.givens[i]) FontWeight.Bold else FontWeight.Normal
            val layout = measurer.measure(v.toString(), digitStyle.copy(fontWeight = weight))
            val lift = if (mistake) cs * 0.06f else 0f
            drawCentered(layout, color, Offset(o.x + cs / 2, o.y + cs / 2 - lift))
            if (mistake) { // mistake marker: a dot under the digit (not colour alone)
                drawCircle(k.danger, radius = 2.5.dp.toPx(), center = Offset(o.x + cs / 2, o.y + cs - 4.5.dp.toPx()))
            }
        } else if ((hint?.candidates?.get(i)?.takeIf { it >= 0 } ?: m.notes[i]) != 0) {
            // While a hint is open, role cells may show the true candidates for this hint only (US-012).
            val mask = hint?.candidates?.get(i)?.takeIf { it >= 0 } ?: m.notes[i]
            val role = hint != null && (hint.cause[i] || hint.target == i)
            val inset = if (role) 3.dp.toPx() else 2.dp.toPx()
            val third = (cs - 2 * inset) / 3f
            for (d in Masks.digits(mask)) {
                val center = Offset(o.x + inset + ((d - 1) % 3 + 0.5f) * third, o.y + inset + ((d - 1) / 3 + 0.5f) * third)
                val bit = Masks.bit(d)
                if (hint != null && hint.struck[i] and bit != 0) { // ruled out: bold danger digit, struck through
                    drawCentered(measurer.measure(d.toString(), noteStyle.copy(fontWeight = FontWeight.Bold)), k.danger, center)
                    val half = cs * 0.12f
                    drawLine(k.danger, Offset(center.x - half, center.y + half), Offset(center.x + half, center.y - half), 1.8.dp.toPx())
                } else if (hint != null && ((hint.cause[i] && hint.key and bit != 0) || (hint.target == i && hint.targetKey and bit != 0))) { // the step's key candidate: a disc with an amber halo
                    drawCircle(k.hintTargetRing, radius = cs * 0.155f + 1.5.dp.toPx(), center = center)
                    drawCircle(k.noteHighlightBg, radius = cs * 0.155f, center = center)
                    drawCentered(measurer.measure(d.toString(), noteStyle.copy(fontWeight = FontWeight.Bold)), k.noteHighlightFg, center)
                } else if (d == h.digit) { // same-digit note: a filled disc
                    drawCircle(k.noteHighlightBg, radius = cs * 0.155f, center = center)
                    drawCentered(measurer.measure(d.toString(), noteStyle.copy(fontWeight = FontWeight.Bold)), k.noteHighlightFg, center)
                } else {
                    drawCentered(measurer.measure(d.toString(), noteStyle), k.note, center)
                }
            }
        }
        // 5. Conflict marker: a corner triangle on every duplicate (not colour alone).
        if (f and BoardHighlights.CONFLICT != 0) {
            val s = k.markSize.toPx()
            val path = Path().apply {
                moveTo(o.x + cs - s, o.y)
                lineTo(o.x + cs, o.y)
                lineTo(o.x + cs, o.y + s)
                close()
            }
            drawPath(path, k.danger)
        }
        // Placed by hint: a small amber dot in the corner until the panel closes (design hint-stage-3).
        if (hint != null && hint.placed == i) {
            drawCircle(k.hintTargetRing, radius = 3.5.dp.toPx(), center = Offset(o.x + cs - 6.dp.toPx(), o.y + 6.dp.toPx()))
        }
    }
}

/** The cause hatch (design .cause): 135° stripes of cause-b over cause-a, clipped to the cell. */
private fun DrawScope.drawHatch(o: Offset, cs: Float, k: LanternColors) {
    drawRect(k.hintCauseA, topLeft = o, size = Size(cs, cs))
    val step = 6.dp.toPx()
    val w = 2.dp.toPx()
    clipRect(o.x, o.y, o.x + cs, o.y + cs) {
        var x = -cs
        while (x < cs) {
            drawLine(k.hintCauseB, Offset(o.x + x, o.y), Offset(o.x + x + cs, o.y + cs), w)
            x += step
        }
    }
}

private fun cellOrigin(i: Int, cs: Float) = Offset((i % 9) * cs, (i / 9) * cs)

private fun DrawScope.drawCentered(layout: TextLayoutResult, color: Color, center: Offset) {
    drawText(layout, color = color, topLeft = Offset(center.x - layout.size.width / 2f, center.y - layout.size.height / 2f))
}

private const val DIGIT_RATIO = 0.62f
private const val NOTE_RATIO = 0.27f
