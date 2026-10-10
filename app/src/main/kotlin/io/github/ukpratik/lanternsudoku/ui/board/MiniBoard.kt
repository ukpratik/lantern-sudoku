// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.ui.board

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.ukpratik.lanternsudoku.ui.theme.LanternColors
import io.github.ukpratik.lanternsudoku.ui.theme.UiFamily

/**
 * The Continue card's thumbnail (US-001), drawn from the 81-char `grid_summary` column ('1'–'9' given, 'a'–'i' entry,
 * '.' empty), so Home never decodes a blob. Decorative: the card's own label describes the game.
 */
@Composable
fun MiniBoard(summary: String, size: Dp, colors: LanternColors, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer(cacheSize = 32)
    Canvas(modifier.size(size)) {
        val cs = this.size.width / 9f
        drawRoundRect(colors.cell, cornerRadius = CornerRadius(4.dp.toPx()))
        val thin = 0.5.dp.toPx()
        for (n in 1 until 9) {
            val p = n * cs
            val (color, w) = if (n % 3 == 0) colors.boxLine to 1.dp.toPx() else colors.line to thin
            drawLine(color, Offset(p, 0f), Offset(p, this.size.height), w)
            drawLine(color, Offset(0f, p), Offset(this.size.width, p), w)
        }
        val stroke = 1.5.dp.toPx()
        drawRoundRect(colors.boxLine, topLeft = Offset(stroke / 2, stroke / 2), size = Size(this.size.width - stroke, this.size.height - stroke),
            cornerRadius = CornerRadius(4.dp.toPx()), style = Stroke(stroke))
        val base = TextStyle(fontFamily = UiFamily, fontSize = (cs * 0.66f).toSp())
        for (i in 0 until minOf(81, summary.length)) {
            val ch = summary[i]
            val (digit, given) = when (ch) {
                in '1'..'9' -> (ch - '0') to true
                in 'a'..'i' -> (ch - 'a' + 1) to false
                else -> continue
            }
            val layout = measurer.measure(digit.toString(), base.copy(fontWeight = if (given) FontWeight.Bold else FontWeight.Normal))
            val cx = (i % 9 + 0.5f) * cs
            val cy = (i / 9 + 0.5f) * cs
            drawText(layout, color = if (given) colors.given else colors.entry,
                topLeft = Offset(cx - layout.size.width / 2f, cy - layout.size.height / 2f))
        }
    }
}
