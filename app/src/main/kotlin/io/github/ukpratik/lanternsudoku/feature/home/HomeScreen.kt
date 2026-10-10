// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.feature.home

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
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
import io.github.ukpratik.lanternsudoku.engine.model.Level
import io.github.ukpratik.lanternsudoku.ui.board.MiniBoard
import io.github.ukpratik.lanternsudoku.ui.components.PrimaryButton
import io.github.ukpratik.lanternsudoku.ui.components.SecondaryButton
import io.github.ukpratik.lanternsudoku.ui.components.formatElapsed
import io.github.ukpratik.lanternsudoku.ui.components.label
import io.github.ukpratik.lanternsudoku.ui.components.summary
import io.github.ukpratik.lanternsudoku.ui.theme.DisplayFamily
import io.github.ukpratik.lanternsudoku.ui.theme.LanternColors
import io.github.ukpratik.lanternsudoku.ui.theme.LocalLanternColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class HomeTab(val label: Int, @param:DrawableRes val icon: Int) {
    PLAY(R.string.tab_play, R.drawable.ic_grid),
    DAILY(R.string.tab_daily, R.drawable.ic_calendar),
    STATS(R.string.tab_stats, R.drawable.ic_insights),
}

/** Home: Play · Daily · Stats as tab state in one destination (LLD §5). Daily and Stats arrive in M4. */
@Composable
fun HomeScreen(vm: HomeViewModel, onOpenGame: (Long) -> Unit, onOpenSettings: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(HomeTab.PLAY.ordinal) }
    LaunchedEffect(vm) {
        vm.eventFlow.collect { e -> if (e is HomeEvent.OpenGame) onOpenGame(e.gameId) }
    }
    // From Daily or Stats, Back returns to Play; at the Play root the system handles Back (design §8.2).
    BackHandler(enabled = tab != HomeTab.PLAY.ordinal) { tab = HomeTab.PLAY.ordinal }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.widthIn(max = 480.dp).fillMaxSize()) {
                    when (HomeTab.entries[tab]) {
                        HomeTab.PLAY -> PlayTab(state, vm, onOpenSettings)
                        HomeTab.DAILY, HomeTab.STATS -> ComingSoon(HomeTab.entries[tab])
                    }
                }
            }
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                for (t in HomeTab.entries) {
                    NavigationBarItem(
                        selected = tab == t.ordinal,
                        onClick = { tab = t.ordinal },
                        icon = { Icon(painterResource(t.icon), contentDescription = null) },
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
    state.sheet?.let { ContinueOrNewSheet(it, vm) }
}

@Composable
private fun PlayTab(state: HomeUiState, vm: HomeViewModel, onOpenSettings: () -> Unit) {
    val k = LocalLanternColors.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        AppBar(onOpenSettings)
        Column(Modifier.padding(horizontal = 16.dp)) {
            val greeting = if (state.firstRun) stringResource(R.string.greeting_first_run) else state.today.format(LONG_DATE)
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
            TodayRow(state.today, state.todayLevel)
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
    Row(Modifier.fillMaxWidth().height(64.dp).padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
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
    val description = stringResource(R.string.continue_card_description, level, time, card.filled)
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
                Text(stringResource(R.string.continue_eyebrow), style = MaterialTheme.typography.labelSmall, color = k.onInkAccent)
                Text(level, fontFamily = DisplayFamily, fontSize = 26.sp, color = k.onInk, modifier = Modifier.padding(top = 4.dp, bottom = 2.dp))
                Text(stringResource(R.string.continue_meta, time, card.filled), style = MaterialTheme.typography.bodyMedium, color = k.onInkMuted)
                Spacer(Modifier.height(12.dp))
                PrimaryButton(stringResource(R.string.resume), onClick, icon = R.drawable.ic_play, modifier = Modifier.height(44.dp))
            }
        }
    }
}

/** Compact Today's puzzle row (D-018). M2 shows the date and level from the weekday rotation; playing it arrives in M4. */
@Composable
private fun TodayRow(today: LocalDate, level: Level) {
    val accent = MaterialTheme.colorScheme.primary
    val soft = MaterialTheme.colorScheme.secondaryContainer
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer, contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.matchParentSize()) {
                    drawCircle(soft)
                    drawCircle(accent, style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))))
                }
                Text(today.dayOfMonth.toString(), style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.today_title), style = MaterialTheme.typography.titleMedium)
                Text("${today.format(SHORT_DATE)} · ${stringResource(level.label)}", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.today_soon), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            PrimaryButton(stringResource(R.string.play), onClick = {}, icon = R.drawable.ic_play, enabled = false, modifier = Modifier.height(40.dp))
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
            Row(verticalAlignment = Alignment.CenterVertically) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContinueOrNewSheet(saved: ContinueSummary, vm: HomeViewModel) {
    val k = LocalLanternColors.current
    val level = stringResource(saved.level.label)
    ModalBottomSheet(
        onDismissRequest = vm::onSheetDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.40f),
    ) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MiniBoard(saved.gridSummary, 64.dp, k)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(stringResource(R.string.sheet_title, level), style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics { heading() })
                    Text(stringResource(R.string.sheet_meta, formatElapsed(saved.elapsedMs), saved.filled),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(20.dp))
            PrimaryButton(stringResource(R.string.sheet_continue), vm::onSheetContinue, icon = R.drawable.ic_play,
                modifier = Modifier.fillMaxWidth().height(56.dp))
            Spacer(Modifier.height(12.dp))
            SecondaryButton(stringResource(R.string.sheet_start_new, level), vm::onSheetStartNew, Modifier.fillMaxWidth().height(56.dp))
            Text(stringResource(R.string.sheet_fine_print), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
        }
    }
}

@Composable
private fun ComingSoon(tab: HomeTab) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(tab.label), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.tab_coming_soon), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

private val LONG_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.UK)
private val SHORT_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK)
