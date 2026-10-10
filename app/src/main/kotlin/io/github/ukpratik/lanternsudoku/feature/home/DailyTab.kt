// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ukpratik.lanternsudoku.R
import io.github.ukpratik.lanternsudoku.data.DailyDay
import io.github.ukpratik.lanternsudoku.data.DayState
import io.github.ukpratik.lanternsudoku.data.ShareFacts
import io.github.ukpratik.lanternsudoku.ui.components.DayDot
import io.github.ukpratik.lanternsudoku.ui.components.LONG_DATE
import io.github.ukpratik.lanternsudoku.ui.components.PrimaryButton
import io.github.ukpratik.lanternsudoku.ui.components.StreakLine
import io.github.ukpratik.lanternsudoku.ui.components.WEEKDAY
import io.github.ukpratik.lanternsudoku.ui.components.WEEKDAY_INITIAL
import io.github.ukpratik.lanternsudoku.ui.components.formatElapsed
import io.github.ukpratik.lanternsudoku.ui.components.label
import io.github.ukpratik.lanternsudoku.ui.theme.DisplayFamily
import io.github.ukpratik.lanternsudoku.ui.theme.LocalLanternColors
import java.time.LocalDate

/**
 * Daily tab (US-014, US-015; design daily-tab): today's ink card, the streak line and the 7-day strip (today + the 6
 * previous dates, D-028). Playing past days from the strip is US-016 (M6); in 1.0 the strip shows their state only.
 */
@Composable
internal fun DailyTab(state: DailyUiState, onPlay: (LocalDate) -> Unit, onShare: (ShareFacts) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(
            stringResource(R.string.daily_title), style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.fillMaxWidth().height(64.dp).padding(start = 20.dp, top = 18.dp).semantics { heading() },
        )
        val today = state.today ?: return@Column
        Column(Modifier.padding(horizontal = 16.dp)) {
            TodayCard(today, state.typicalMs, onPlay, onShare)
            Spacer(Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    StreakLine(state.stats)
                    Spacer(Modifier.height(14.dp))
                    WeekStrip(state.week)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TodayCard(day: DailyDay, typicalMs: Long?, onPlay: (LocalDate) -> Unit, onShare: (ShareFacts) -> Unit) {
    val k = LocalLanternColors.current
    val res = LocalResources.current
    val level = stringResource(day.level.label)
    val meta = typicalMs?.let {
        val min = ((it + 30_000) / 60_000).toInt().coerceAtLeast(1)
        stringResource(R.string.daily_card_meta_typical, res.getQuantityString(R.plurals.minutes_short, min, min))
    } ?: stringResource(R.string.daily_card_meta)
    val action = when (day.state) {
        DayState.NOT_PLAYED -> stringResource(R.string.daily_play)
        DayState.STARTED -> stringResource(R.string.daily_resume)
        DayState.DONE -> stringResource(R.string.daily_done, formatElapsed(day.elapsedMs))
    }
    val playable = day.state != DayState.DONE
    val description = stringResource(R.string.daily_card_description, day.date.format(LONG_DATE), level, action)
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(k.inkCard)
            .then(if (playable) Modifier.clickable(role = Role.Button) { onPlay(day.date) } else Modifier),
    ) {
        Canvas(Modifier.matchParentSize()) {
            val c = Offset(size.width - 60.dp.toPx(), 40.dp.toPx())
            drawCircle(Brush.radialGradient(listOf(k.glow, Color.Transparent), center = c, radius = 120.dp.toPx()), radius = 120.dp.toPx(), center = c)
        }
        Column(Modifier.padding(20.dp)) {
            Column(Modifier.clearAndSetSemantics { contentDescription = description }) {
                Text(stringResource(R.string.daily_today_eyebrow), style = MaterialTheme.typography.labelSmall, color = k.onInkAccent)
                Text(stringResource(R.string.daily_card_title, day.date.format(WEEKDAY), level), fontFamily = DisplayFamily, fontSize = 26.sp,
                    color = k.onInk, modifier = Modifier.padding(top = 4.dp, bottom = 2.dp))
                Text(meta, style = MaterialTheme.typography.bodyMedium, color = k.onInkMuted)
            }
            Spacer(Modifier.height(14.dp))
            if (playable) {
                PrimaryButton(action, { onPlay(day.date) }, icon = R.drawable.ic_play, modifier = Modifier.height(48.dp))
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(action, style = MaterialTheme.typography.titleMedium, color = k.onInk, modifier = Modifier.weight(1f))
                    day.share?.let { facts ->
                        PrimaryButton(stringResource(R.string.share), { onShare(facts) }, icon = R.drawable.ic_share,
                            modifier = Modifier.height(48.dp).semantics { contentDescription = res.getString(R.string.share_result_description) })
                    }
                }
            }
        }
    }
}

/** The 7-day strip, oldest first, today last: weekday initial, day dot by shape, day of month (design daily-tab). */
@Composable
private fun WeekStrip(week: List<DailyDay>) {
    val res = LocalResources.current
    val label = stringResource(R.string.last_7_days)
    Row(Modifier.fillMaxWidth().semantics { contentDescription = label }, horizontalArrangement = Arrangement.SpaceBetween) {
        week.forEachIndexed { i, day ->
            val today = i == week.lastIndex
            val stateText = res.getString(
                when (day.state) {
                    DayState.DONE -> R.string.day_done
                    DayState.STARTED -> R.string.day_started
                    DayState.NOT_PLAYED -> R.string.day_not_played
                },
            )
            val level = res.getString(day.level.label)
            val spoken = res.getString(if (today) R.string.strip_today else R.string.strip_day, day.date.format(LONG_DATE), level, stateText)
            Column(
                Modifier.width(42.dp).clearAndSetSemantics { contentDescription = spoken },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                val muted = MaterialTheme.colorScheme.onSurfaceVariant
                Text(day.date.format(WEEKDAY_INITIAL), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp), color = muted)
                DayDot(day.state, today)
                Text(day.date.dayOfMonth.toString(), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp), color = muted)
            }
        }
    }
}
