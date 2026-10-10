// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import io.github.ukpratik.lanternsudoku.ui.components.quiet
import io.github.ukpratik.lanternsudoku.ui.components.spokenAs
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ukpratik.lanternsudoku.R
import io.github.ukpratik.lanternsudoku.data.DailyStats
import io.github.ukpratik.lanternsudoku.data.LevelStats
import io.github.ukpratik.lanternsudoku.data.StatsView
import io.github.ukpratik.lanternsudoku.engine.model.Level
import io.github.ukpratik.lanternsudoku.ui.components.PrimaryButton
import io.github.ukpratik.lanternsudoku.ui.components.Tile
import io.github.ukpratik.lanternsudoku.ui.components.TileDefaults
import io.github.ukpratik.lanternsudoku.ui.components.formatElapsed
import io.github.ukpratik.lanternsudoku.ui.components.label
import io.github.ukpratik.lanternsudoku.ui.components.spokenElapsed
import io.github.ukpratik.lanternsudoku.ui.theme.DisplayFamily
import io.github.ukpratik.lanternsudoku.ui.theme.LocalLanternColors
import io.github.ukpratik.lanternsudoku.ui.theme.TimerStyle

/**
 * Stats tab (US-019, US-015; design stats, stats-empty). Everything is derived by query (ADR-006); dailies count toward
 * their level (D-019). Level chips filter the tiles; the daily block and the by-level table are always whole.
 */
@Composable
internal fun StatsTab(state: StatsUiState, onPlayToday: () -> Unit, onPickLevel: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(
            stringResource(R.string.stats_title), style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.fillMaxWidth().height(64.dp).padding(start = 20.dp, top = 18.dp).semantics { heading() },
        )
        if (!state.loaded) return@Column
        if (state.view.isEmpty) {
            EmptyStats(onPlayToday, onPickLevel)
        } else {
            FullStats(state.view)
        }
    }
}

@Composable
private fun FullStats(view: StatsView) {
    var filter by rememberSaveable { mutableIntStateOf(-1) } // -1 = All levels
    val level = Level.entries.getOrNull(filter)
    Column(Modifier.padding(horizontal = 16.dp)) {
        val filterLabel = stringResource(R.string.stats_level_filter)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).semantics { contentDescription = filterLabel },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Chip(stringResource(R.string.stats_all), filter == -1) { filter = -1 }
            for (l in Level.entries) Chip(stringResource(l.label), filter == l.ordinal) { filter = l.ordinal }
        }
        Spacer(Modifier.height(12.dp))
        DailyBlock(view.daily)
        Spacer(Modifier.height(12.dp))
        Tiles(view.of(level))
        // The note wraps under the heading when both don't fit on one line (200% text: "By level" never breaks, ISS-004).
        FlowRow(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 20.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            itemVerticalAlignment = Alignment.Bottom,
        ) {
            Text(stringResource(R.string.stats_by_level), style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(end = 12.dp).semantics { heading() })
            Text(stringResource(R.string.stats_by_level_note), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LevelTable(view)
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text, style = MaterialTheme.typography.labelMedium) },
        leadingIcon = if (selected) {
            { Icon(painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(18.dp)) }
        } else {
            null
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        modifier = Modifier.heightIn(min = 48.dp),
    )
}

/** The daily block on the ink card, matching the Daily tab: streak · best · days played (US-015, TC-142). */
@Composable
private fun DailyBlock(daily: DailyStats) {
    val k = LocalLanternColors.current
    val res = LocalResources.current
    val played = res.getQuantityString(R.plurals.days_played, daily.daysPlayed, daily.daysPlayed)
    val description = res.getString(R.string.streak_description, daily.current, daily.best, played)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(k.inkCard)
            .spokenAs(description),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.stats_daily_eyebrow), style = MaterialTheme.typography.labelSmall, color = k.onInkAccent, modifier = quiet)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth()) {
                for ((value, label) in listOf(daily.current to R.string.stats_streak, daily.best to R.string.stats_best, daily.daysPlayed to R.string.stats_days_played)) {
                    Column(Modifier.weight(1f)) {
                        Text(value.toString(), fontFamily = DisplayFamily, fontSize = 30.sp, color = k.onInk, modifier = quiet)
                        Text(stringResource(label), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp), color = k.onInkMuted, modifier = quiet)
                    }
                }
            }
        }
    }
}

/** Overall (or one level's) tiles: started, completed, completion rate, abandoned, clean solves, average time. */
@Composable
private fun Tiles(s: LevelStats) {
    val none = stringResource(R.string.stat_none)
    val tiles = listOf(
        s.started.toString() to R.string.stat_started,
        s.completed.toString() to R.string.stat_completed,
        (s.ratePercent?.let { stringResource(R.string.stat_percent, it) } ?: none) to R.string.stat_rate,
        s.abandoned.toString() to R.string.stat_abandoned,
        s.clean.toString() to R.string.stat_clean,
        (s.averageMs?.let(::formatElapsed) ?: none) to R.string.stat_average,
    )
    val labelled = tiles.map { (value, label) -> value to stringResource(label) }
    BoxWithConstraints {
        val columns = tileColumns(labelled, maxWidth)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (row in labelled.chunked(columns)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((value, label) in row) Tile(value, label, Modifier.weight(1f))
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/**
 * Three tiles a row, or fewer when a value or a single word of a label wouldn't fit a tile at this text size, so no
 * word is ever broken ("Compl/eted", ISS-004). Design §3.1: tiles go 2-up with large text.
 */
@Composable
private fun tileColumns(tiles: List<Pair<String, String>>, width: Dp): Int {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labelStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp)
    val valueStyle = TextStyle(fontFamily = DisplayFamily, fontSize = 24.sp)
    return remember(tiles, width, density, measurer) {
        val widest = tiles.maxOf { (value, label) ->
            val words = label.split(' ').maxOf { measurer.measure(it, labelStyle, softWrap = false).size.width }
            maxOf(words, measurer.measure(value, valueStyle, softWrap = false).size.width)
        }
        with(density) {
            listOf(3, 2).firstOrNull { n -> ((width - 8.dp * (n - 1)) / n - TileDefaults.PADDING * 2).roundToPx() >= widest } ?: 1
        }
    }
}

/** Per level: completed, best (clean solves only) and average (all completed). Read row by row by TalkBack. */
@Composable
private fun LevelTable(view: StatsView) {
    val res = LocalResources.current
    val none = stringResource(R.string.stat_none)
    val header = listOf(stringResource(R.string.stats_col_level), stringResource(R.string.stats_col_done), stringResource(R.string.stats_col_best),
        stringResource(R.string.stats_col_avg))
    val rows = Level.entries.map { l ->
        val s = view.of(l)
        val name = res.getString(l.label)
        val spoken = res.getString(
            R.string.stats_row_description, name, s.completed,
            s.bestMs?.let { spokenElapsed(res, it) } ?: none, s.averageMs?.let { spokenElapsed(res, it) } ?: none,
        )
        listOf(name, s.completed.toString(), s.bestMs?.let(::formatElapsed) ?: none, s.averageMs?.let(::formatElapsed) ?: none) to spoken
    }
    Surface(
        shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer, contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        BoxWithConstraints {
            val stacked = tableNeedsStacking(rows.map { it.first } + listOf(header), maxWidth - 32.dp)
            Column(Modifier.padding(vertical = 6.dp)) {
                TableRow(header, header = true, description = null, stacked = stacked)
                for ((cells, spoken) in rows) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    TableRow(cells, header = false, description = spoken, stacked = stacked)
                }
            }
        }
    }
}

private val headerStyle @Composable get() = MaterialTheme.typography.labelMedium
private val nameStyle @Composable get() = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp)
private val valueStyle = TimerStyle.copy(fontSize = 15.sp)

/** True when a level name or a value wouldn't fit its column on one line (large text on a narrow phone, ISS-004). */
@Composable
private fun tableNeedsStacking(rows: List<List<String>>, width: Dp): Boolean {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val header = headerStyle
    val name = nameStyle
    return remember(rows, width, density, measurer, header, name) {
        val unit = with(density) { width.toPx() } / (1.4f + 3f)
        rows.withIndex().any { (r, cells) ->
            cells.withIndex().any { (i, text) ->
                val style = when {
                    r == rows.lastIndex -> header
                    i == 0 -> name
                    else -> valueStyle
                }
                measurer.measure(text, style, softWrap = false).size.width > unit * (if (i == 0) 1.4f else 1f)
            }
        }
    }
}

/** One table row; [stacked] puts the level name on its own line above the three values (the header drops "Level"). */
@Composable
private fun TableRow(cells: List<String>, header: Boolean, description: String?, stacked: Boolean) {
    val style = if (header) headerStyle else valueStyle
    val color = if (header) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    val textMod = if (description != null) quiet else Modifier
    Column(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 16.dp, vertical = if (stacked) 6.dp else 0.dp)
            .then(if (description != null) Modifier.spokenAs(description) else Modifier),
        verticalArrangement = Arrangement.Center,
    ) {
        if (stacked && !header) Text(cells[0], style = nameStyle, color = color, modifier = textMod)
        Row(Modifier.fillMaxWidth().then(if (stacked) Modifier else Modifier.heightIn(min = 44.dp)), verticalAlignment = Alignment.CenterVertically) {
            cells.forEachIndexed { i, text ->
                if (i == 0 && stacked) return@forEachIndexed
                Text(
                    text, style = if (i == 0 && !header) nameStyle else style, color = color,
                    textAlign = if (i == 0) TextAlign.Start else TextAlign.End,
                    modifier = Modifier.weight(if (i == 0) 1.4f else 1f).then(textMod),
                )
            }
        }
    }
}

/** Empty state (design stats-empty): the launcher motif, one clear way forward and one quieter. No zeros table. */
@Composable
private fun EmptyStats(onPlayToday: () -> Unit, onPickLevel: () -> Unit) {
    val k = LocalLanternColors.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.size(96.dp)) {
            drawRoundRect(Color(0xFF1E2A47), cornerRadius = CornerRadius(22.dp.toPx()))
            val pad = 12.dp.toPx()
            val gap = 6.dp.toPx()
            val c = (size.width - 2 * pad - 2 * gap) / 3
            for (r in 0 until 3) for (col in 0 until 3) {
                val on = r == 1 && col == 1
                val tl = Offset(pad + col * (c + gap), pad + r * (c + gap))
                if (on) {
                    drawCircle(Brush.radialGradient(listOf(k.glow, Color.Transparent), center = tl + Offset(c / 2, c / 2), radius = c * 1.4f),
                        radius = c * 1.4f, center = tl + Offset(c / 2, c / 2))
                }
                drawRoundRect(if (on) Color(0xFFF3BE63) else Color(0xFF34405F), topLeft = tl, size = Size(c, c), cornerRadius = CornerRadius(5.dp.toPx()))
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.stats_empty_title), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.stats_empty_body), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        PrimaryButton(stringResource(R.string.daily_play), onPlayToday, icon = R.drawable.ic_play, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp))
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onPickLevel, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.pick_a_level), style = MaterialTheme.typography.labelLarge)
        }
    }
}
