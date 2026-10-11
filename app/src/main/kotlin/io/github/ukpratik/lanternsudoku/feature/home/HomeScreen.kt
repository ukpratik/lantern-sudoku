// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.feature.home

import androidx.activity.compose.BackHandler
import androidx.activity.compose.ReportDrawnWhen
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ukpratik.lanternsudoku.R
import io.github.ukpratik.lanternsudoku.data.ContinueSummary
import io.github.ukpratik.lanternsudoku.data.DailyDay
import io.github.ukpratik.lanternsudoku.data.DayState
import io.github.ukpratik.lanternsudoku.data.ShareFacts
import io.github.ukpratik.lanternsudoku.feature.share.ShareText
import io.github.ukpratik.lanternsudoku.ui.components.DayDot
import io.github.ukpratik.lanternsudoku.ui.components.LONG_DATE
import io.github.ukpratik.lanternsudoku.ui.components.SHORT_DATE
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.ukpratik.lanternsudoku.engine.model.Level
import io.github.ukpratik.lanternsudoku.ui.board.MiniBoard
import io.github.ukpratik.lanternsudoku.ui.components.PrimaryButton
import io.github.ukpratik.lanternsudoku.ui.components.SecondaryButton
import io.github.ukpratik.lanternsudoku.ui.components.formatElapsed
import io.github.ukpratik.lanternsudoku.ui.components.label
import io.github.ukpratik.lanternsudoku.ui.components.sheetTitle
import io.github.ukpratik.lanternsudoku.ui.components.summary
import io.github.ukpratik.lanternsudoku.ui.theme.DisplayFamily
import io.github.ukpratik.lanternsudoku.ui.theme.LanternColors
import io.github.ukpratik.lanternsudoku.ui.theme.LocalLanternColors

/** Active tab = filled icon (design §5 "Tabs", §6). */
private enum class HomeTab(val label: Int, @param:DrawableRes val icon: Int, @param:DrawableRes val activeIcon: Int) {
    PLAY(R.string.tab_play, R.drawable.ic_grid, R.drawable.ic_grid_filled),
    DAILY(R.string.tab_daily, R.drawable.ic_calendar, R.drawable.ic_calendar_filled),
    STATS(R.string.tab_stats, R.drawable.ic_insights, R.drawable.ic_insights_filled),
}

/** Home: Play · Daily · Stats as tab state in one destination (LLD §5). */
@Composable
fun HomeScreen(vm: HomeViewModel, onOpenGame: (Long) -> Unit, onOpenSettings: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val daily by vm.daily.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(HomeTab.PLAY.ordinal) }
    // NFR-001: Home is "fully drawn" once the Play tab's summary has loaded (Macrobenchmark's timeToFullDisplay).
    ReportDrawnWhen { state.loaded }
    val context = LocalContext.current
    val share: (ShareFacts) -> Unit = { ShareText.share(context, it) }
    LaunchedEffect(vm) {
        vm.eventFlow.collect { e ->
            if (e is HomeEvent.OpenGame) {
                tab = HomeTab.PLAY.ordinal // leaving a game (Back, Close, Home) returns to Play (design §8.2, TC-159)
                onOpenGame(e.gameId)
            }
        }
    }
    // "Today" is re-read on every resume and tab switch (design state-daily-date-changed); no background work.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, vm) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) vm.refreshDate() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(tab) { vm.refreshDate() }
    // From Daily or Stats, Back returns to Play; at the Play root the system handles Back (design §8.2).
    BackHandler(enabled = tab != HomeTab.PLAY.ordinal) { tab = HomeTab.PLAY.ordinal }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.widthIn(max = 480.dp).fillMaxSize()) {
                    when (HomeTab.entries[tab]) {
                        HomeTab.PLAY -> PlayTab(state, vm, onOpenSettings, share)
                        HomeTab.DAILY -> DailyTab(daily, vm::onPlayDaily, share) { id ->
                            tab = HomeTab.PLAY.ordinal // leaving the summary returns to Play, like every game (TC-159)
                            onOpenGame(id)
                        }
                        HomeTab.STATS -> StatsTab(stats, onPlayToday = { vm.onPlayDaily() }, onPickLevel = { tab = HomeTab.PLAY.ordinal })
                    }
                }
            }
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                val dailyDot = state.loaded && state.todayDaily.state == DayState.NOT_PLAYED
                val dailyNotPlayed = stringResource(R.string.tab_daily_not_played)
                for (t in HomeTab.entries) {
                    val icon = if (tab == t.ordinal) t.activeIcon else t.icon
                    NavigationBarItem(
                        selected = tab == t.ordinal,
                        onClick = { tab = t.ordinal },
                        icon = {
                            if (t == HomeTab.DAILY && dailyDot) {
                                // The amber dot: today's puzzle hasn't been started (design §6 "Tabs"). Nothing else nags.
                                BadgedBox(badge = { Badge(Modifier.size(9.dp), containerColor = MaterialTheme.colorScheme.primary) }) {
                                    Icon(painterResource(icon), contentDescription = null)
                                }
                            } else {
                                Icon(painterResource(icon), contentDescription = null)
                            }
                        },
                        modifier = if (t == HomeTab.DAILY && dailyDot) Modifier.semantics { contentDescription = dailyNotPlayed } else Modifier,
                        label = { Text(stringResource(t.label), style = MaterialTheme.typography.labelMedium) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.onSurface,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        }
    }
    state.sheet?.let { ContinueOrNewSheet(it, vm::onSheetContinue, vm::onSheetStartNew, vm::onSheetDismiss) }
}

@Composable
private fun PlayTab(state: HomeUiState, vm: HomeViewModel, onOpenSettings: () -> Unit, onShare: (ShareFacts) -> Unit) {
    val k = LocalLanternColors.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        AppBar(onOpenSettings)
        Column(Modifier.padding(horizontal = 16.dp)) {
            val greeting = if (state.firstRun) stringResource(R.string.greeting_first_run) else state.todayDaily.date.format(LONG_DATE)
            Text(greeting, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 12.dp))
            if (state.discardNotice) {
                Notice(stringResource(R.string.save_discarded_notice), onDismiss = vm::onDismissNotice)
                Spacer(Modifier.height(12.dp))
            }
            if (state.openFailed) {
                Notice(stringResource(R.string.open_failed), onDismiss = vm::onDismissOpenFailed)
                Spacer(Modifier.height(12.dp))
            }
            state.continueCard?.let {
                ContinueCard(it, k, vm::onContinueCard)
                Spacer(Modifier.height(12.dp))
            }
            TodayRow(state.todayDaily, onPlay = { vm.onPlayDaily(state.todayDaily.date) }, onShare = onShare)
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
                Text(stringResource(R.string.new_game), style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).semantics { heading() })
                Text(stringResource(R.string.pick_a_level), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer, contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Column {
                    for (level in Level.entries) {
                        val badge = when {
                            level in state.inProgress -> R.string.badge_in_progress
                            state.firstRun && level == Level.EASY -> R.string.badge_start_here
                            else -> null
                        }
                        LevelRow(level, badge) { vm.onLevel(level) }
                        if (level != Level.EXPERT) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun AppBar(onOpenSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        BrandMark()
        Spacer(Modifier.width(10.dp))
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { heading() })
        IconButton(onClick = onOpenSettings) {
            Icon(painterResource(R.drawable.ic_settings), contentDescription = stringResource(R.string.settings))
        }
    }
}

/** The 3×3 brand mark with one glowing amber cell (design §12). Decorative. */
@Composable
private fun BrandMark() {
    Canvas(Modifier.size(28.dp)) {
        drawRoundRect(Color(0xFF1E2A47), cornerRadius = CornerRadius(7.dp.toPx()))
        val pad = 3.dp.toPx()
        val gap = 2.dp.toPx()
        val c = (size.width - 2 * pad - 2 * gap) / 3
        for (r in 0 until 3) for (col in 0 until 3) {
            val on = r == 1 && col == 1
            drawRoundRect(if (on) Color(0xFFF3BE63) else Color(0xFF34405F), topLeft = Offset(pad + col * (c + gap), pad + r * (c + gap)),
                size = Size(c, c), cornerRadius = CornerRadius(2.dp.toPx()))
        }
    }
}

@Composable
private fun Notice(text: String, onDismiss: () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer, contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_info), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite })
            IconButton(onClick = onDismiss) {
                Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.dismiss))
            }
        }
    }
}

@Composable
private fun ContinueCard(card: ContinueSummary, k: LanternColors, onClick: () -> Unit) {
    val level = stringResource(card.level.label)
    val time = formatElapsed(card.elapsedMs)
    val date = card.dailyDate
    // A daily keeps its own date: after midnight it still reads "Continue · Daily, Thu 15 Oct" (state-daily-date-changed).
    val description = if (date == null) {
        stringResource(R.string.continue_card_description, level, time, card.filled)
    } else {
        stringResource(R.string.continue_daily_description, date.format(LONG_DATE), level, time, card.filled)
    }
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(k.inkCard)
            .clickable(onClickLabel = stringResource(R.string.resume), role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                onClick { onClick(); true }
            },
    ) {
        // Inner amber radial glow, not a shadow (design §4).
        Canvas(Modifier.matchParentSize()) {
            drawCircle(Brush.radialGradient(listOf(k.glow, Color.Transparent), center = Offset(65.dp.toPx(), 45.dp.toPx()), radius = 95.dp.toPx()),
                radius = 95.dp.toPx(), center = Offset(65.dp.toPx(), 45.dp.toPx()))
        }
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            MiniBoard(card.gridSummary, 104.dp, k, Modifier.clip(RoundedCornerShape(4.dp)))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(if (date == null) R.string.continue_eyebrow else R.string.continue_daily_eyebrow),
                    style = MaterialTheme.typography.labelSmall, color = k.onInkAccent)
                Text(date?.format(SHORT_DATE) ?: level, fontFamily = DisplayFamily, fontSize = 26.sp, color = k.onInk,
                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp))
                Text(
                    if (date == null) stringResource(R.string.continue_meta, time, card.filled) else stringResource(R.string.continue_daily_meta, level, time, card.filled),
                    style = MaterialTheme.typography.bodyMedium, color = k.onInkMuted,
                )
                Spacer(Modifier.height(12.dp))
                PrimaryButton(stringResource(R.string.resume), onClick, icon = R.drawable.ic_play, modifier = Modifier.heightIn(min = 44.dp))
            }
        }
    }
}

/**
 * Compact Today's puzzle row (D-018, design play-tab): day dot · "Today's puzzle" · date and level · Play. Started:
 * "started · 6:15" and Resume. Done: amber ✓ dot, "done · 8:41" and a Share text button (design §6 "Today row").
 */
@Composable
private fun TodayRow(day: DailyDay, onPlay: () -> Unit, onShare: (ShareFacts) -> Unit) {
    val level = stringResource(day.level.label)
    val meta = stringResource(R.string.today_row_meta, day.date.format(SHORT_DATE), level)
    val done = day.state == DayState.DONE
    val action = stringResource(if (day.state == DayState.STARTED) R.string.resume else R.string.play)
    val description = if (done) {
        stringResource(R.string.today_row_done_description, day.date.format(LONG_DATE), level, formatElapsed(day.elapsedMs))
    } else {
        stringResource(R.string.today_row_description, day.date.format(LONG_DATE), level, action)
    }
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer, contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 72.dp)
                .then(if (done) Modifier else Modifier.clickable(role = Role.Button, onClick = onPlay))
                .padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(Modifier.weight(1f).clearAndSetSemantics {
                contentDescription = description
                if (!done) {
                    role = Role.Button
                    onClick { onPlay(); true }
                }
            }, verticalAlignment = Alignment.CenterVertically) {
                DayDot(day.state, today = true, label = day.date.dayOfMonth.toString())
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.today_title), style = MaterialTheme.typography.titleMedium)
                    Text(meta, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    when (day.state) {
                        DayState.DONE -> stringResource(R.string.today_row_done, formatElapsed(day.elapsedMs))
                        DayState.STARTED -> stringResource(R.string.today_row_started, formatElapsed(day.elapsedMs))
                        DayState.NOT_PLAYED -> null
                    }?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            val facts = day.share
            if (done && facts != null) {
                val shareDescription = stringResource(R.string.share_result_description)
                TextButton(onClick = { onShare(facts) }, modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = shareDescription }) {
                    Icon(painterResource(R.drawable.ic_share), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.share), style = MaterialTheme.typography.labelLarge)
                }
            } else if (!done) {
                PrimaryButton(action, onPlay, icon = R.drawable.ic_play, modifier = Modifier.heightIn(min = 40.dp))
            }
        }
    }
}

@Composable
private fun LevelRow(level: Level, badge: Int?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 68.dp).clickable(role = Role.Button, onClick = onClick)
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Pips(level.ordinal + 1)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            // The badge wraps under the name when both don't fit (200% text, ISS-004).
            FlowRow(itemVerticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(level.label), style = MaterialTheme.typography.titleMedium)
                if (badge != null) {
                    Text(
                        stringResource(badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(start = 6.dp).clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer).padding(horizontal = 7.dp, vertical = 3.dp),
                    )
                }
            }
            Text(stringResource(level.summary), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
        }
        Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Difficulty pips: filled = level, outline = the rest (shape, not colour). Decorative; the name says the level. */
@Composable
private fun Pips(filled: Int) {
    val ink = MaterialTheme.colorScheme.onSurface
    Row(Modifier.width(44.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        for (n in 1..4) {
            Box(
                Modifier.size(8.dp).clip(CircleShape).border(1.5.dp, ink, CircleShape)
                    .background(if (n <= filled) ink else Color.Transparent),
            )
        }
    }
}

/** The Continue-or-new sheet (US-002, design level-picker-continue-or-new); also after a daily's "New <level> game". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ContinueOrNewSheet(saved: ContinueSummary, onContinue: () -> Unit, onStartNew: () -> Unit, onDismiss: () -> Unit) {
    val k = LocalLanternColors.current
    val level = stringResource(saved.level.label)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.40f),
    ) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MiniBoard(saved.gridSummary, 64.dp, k)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(stringResource(saved.level.sheetTitle), style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics { heading() })
                    Text(stringResource(R.string.sheet_meta, formatElapsed(saved.elapsedMs), saved.filled),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(20.dp))
            PrimaryButton(stringResource(R.string.sheet_continue), onContinue, icon = R.drawable.ic_play,
                modifier = Modifier.fillMaxWidth().height(56.dp))
            Spacer(Modifier.height(12.dp))
            SecondaryButton(stringResource(R.string.sheet_start_new, level), onStartNew, Modifier.fillMaxWidth().heightIn(min = 56.dp))
            Text(stringResource(R.string.sheet_fine_print), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
        }
    }
}
