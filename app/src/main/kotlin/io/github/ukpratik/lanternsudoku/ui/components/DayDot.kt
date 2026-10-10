// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.ukpratik.lanternsudoku.data.DayState

/**
 * The day dot (design §6): shape carries the meaning, not colour, so it reads in greyscale (TC-146).
 * done = ink disc + ✓ · started = half-filled · not played = outline ring · today = dashed amber ring on amber-soft
 * (half-filled once started) · today done = amber fill + ✓ + glow. Decorative: the caller's label says the state.
 */
@Composable
fun DayDot(state: DayState, today: Boolean, size: Dp = 32.dp, label: String? = null) {
    val ink = MaterialTheme.colorScheme.onSurface
    val amber = MaterialTheme.colorScheme.primary
    val amberFill = MaterialTheme.colorScheme.primaryContainer
    val soft = MaterialTheme.colorScheme.secondaryContainer
    val paper = MaterialTheme.colorScheme.surface
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val r = this.size.minDimension / 2f
            val stroke = 2.dp.toPx()
            val inner = r - stroke / 2
            when {
                today && state == DayState.DONE -> {
                    drawCircle(Brush.radialGradient(listOf(amberFill.copy(alpha = 0.6f), Color.Transparent), radius = r), radius = r)
                    drawCircle(amberFill, radius = inner)
                    drawCircle(amber, radius = inner, style = Stroke(stroke))
                    check(ink, r)
                }
                today -> {
                    drawCircle(soft, radius = inner)
                    if (state == DayState.STARTED) half(ink, inner)
                    drawCircle(amber, radius = inner, style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))))
                }
                state == DayState.DONE -> {
                    drawCircle(ink, radius = inner)
                    check(paper, r)
                }
                state == DayState.STARTED -> {
                    half(ink, inner)
                    drawCircle(ink, radius = inner, style = Stroke(stroke))
                }
                else -> drawCircle(ink.copy(alpha = 0.55f), radius = inner, style = Stroke(1.5.dp.toPx()))
            }
        }
        // The Today row's dot carries the day of the month (design play-tab); not over a fill it would hide in.
        if (label != null && today && state == DayState.NOT_PLAYED) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = ink)
        }
    }
}

/** The left half filled: "started" (a half-filled disc). */
private fun DrawScope.half(color: Color, r: Float) {
    drawArc(color, startAngle = 90f, sweepAngle = 180f, useCenter = true, topLeft = Offset(center.x - r, center.y - r), size = Size(2 * r, 2 * r))
}

private fun DrawScope.check(color: Color, r: Float) {
    val s = r * 0.9f
    val p = Path().apply {
        moveTo(center.x - s * 0.5f, center.y + s * 0.02f)
        lineTo(center.x - s * 0.12f, center.y + s * 0.4f)
        lineTo(center.x + s * 0.55f, center.y - s * 0.35f)
    }
    drawPath(p, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}
