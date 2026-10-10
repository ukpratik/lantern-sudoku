// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.feature.game

import android.content.res.Resources
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ukpratik.lanternsudoku.R
import io.github.ukpratik.lanternsudoku.data.Settings
import io.github.ukpratik.lanternsudoku.engine.game.Action
import io.github.ukpratik.lanternsudoku.engine.game.BoardHighlights
import io.github.ukpratik.lanternsudoku.engine.game.GameState
import io.github.ukpratik.lanternsudoku.engine.game.Highlights
import io.github.ukpratik.lanternsudoku.engine.game.Status
import io.github.ukpratik.lanternsudoku.engine.model.Level
import io.github.ukpratik.lanternsudoku.engine.model.Masks
import io.github.ukpratik.lanternsudoku.platform.DebugHooks
import io.github.ukpratik.lanternsudoku.platform.Haptics
import io.github.ukpratik.lanternsudoku.ui.board.Board
import io.github.ukpratik.lanternsudoku.ui.board.BoardGeometry
import io.github.ukpratik.lanternsudoku.ui.board.BoardModel
import io.github.ukpratik.lanternsudoku.ui.board.CellLabels
import io.github.ukpratik.lanternsudoku.ui.components.PrimaryButton
import io.github.ukpratik.lanternsudoku.ui.components.formatElapsed
import io.github.ukpratik.lanternsudoku.ui.components.label
import io.github.ukpratik.lanternsudoku.ui.theme.GameTitleStyle
import io.github.ukpratik.lanternsudoku.ui.theme.LocalLanternColors
import io.github.ukpratik.lanternsudoku.ui.theme.TimerStyle

private const val BAR = 56f
private const val STATUS = 32f

@Composable
fun GameScreen(vm: GameViewModel, onLeave: () -> Unit, onOpenGame: (Long) -> Unit, onOpenSettings: () -> Unit) {
    val ui by vm.state.collectAsStateWithLifecycle()
    val view = LocalView.current
    val hapticsOn by rememberUpdatedState(ui.settings.haptics)
    val leave by rememberUpdatedState(onLeave)
    val open by rememberUpdatedState(onOpenGame)
    LaunchedEffect(vm) {
        vm.eventFlow.collect { e ->
            when (e) {
                is GameEvent.Haptic -> if (hapticsOn) Haptics.perform(view, e.kind)
                GameEvent.Leave -> leave()
                is GameEvent.OpenGame -> open(e.gameId)
            }
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, vm) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> vm.onResume()
                Lifecycle.Event.ON_PAUSE -> vm.onPause()
                Lifecycle.Event.ON_STOP -> vm.onStop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    BackHandler { vm.onBack() }

    var confirmRestart by remember { mutableStateOf(false) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        val game = ui.game
        if (game == null) {
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing))
        } else {
            BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.TopCenter) {
                val geo = BoardGeometry.compute(maxWidth.value, maxHeight.value)
                GameContent(
                    vm = vm,
                    game = game,
                    level = ui.level,
                    settings = ui.settings,
                    paused = ui.paused,
                    saveFailed = ui.saveFailed,
                    geo = geo,
                    availableHeight = maxHeight.value,
                    onRestart = { confirmRestart = true },
                    onOpenSettings = onOpenSettings,
                    onLeave = onLeave,
                )
            }
        }
    }
    if (confirmRestart) {
        AlertDialog(
            onDismissRequest = { confirmRestart = false },
            title = { Text(stringResource(R.string.restart_dialog_title)) },
            text = { Text(stringResource(R.string.restart_dialog_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestart = false
                    vm.restart()
                }) { Text(stringResource(R.string.restart)) }
            },
            dismissButton = { TextButton(onClick = { confirmRestart = false }) { Text(stringResource(R.string.cancel)) } },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        )
    }
}

@Composable
private fun GameContent(
    vm: GameViewModel,
    game: GameState,
    level: Level,
    settings: Settings,
    paused: Boolean,
    saveFailed: Boolean,
    geo: BoardGeometry,
    availableHeight: Float,
    onRestart: () -> Unit,
    onOpenSettings: () -> Unit,
    onLeave: () -> Unit,
) {
    val k = LocalLanternColors.current
    val levelName = stringResource(level.label)
    val status = statusText(game)
    // The space under the board: whatever is left, but never less than the controls need (then the column scrolls).
    val above = BAR + (if (geo.compact) 8f else STATUS) + geo.board
    val minBottom = geo.controls - (above - geo.board)
    val bottom = maxOf(availableHeight - above, minBottom)
    Column(
        Modifier.width(geo.column.dp).fillMaxSize().then(if (geo.scrolls) Modifier.verticalScroll(rememberScrollState()) else Modifier),
    ) {
        GameBar(
            levelName = levelName,
            elapsedMs = vm.elapsed.collectAsStateWithLifecycle().value,
            showTimer = settings.showTimer,
            subtitle = if (geo.compact) status else null,
            paused = paused,
            onBack = onLeave, // the bar arrow leaves; system Back follows the priority order (design §8.2)
            onPause = { if (paused) vm.resume() else vm.pause() },
            onRestart = onRestart,
            onDebugNearSolved = vm::debugNearSolved,
        )
        if (!geo.compact) StatusLine(status, game, settings) else Spacer(Modifier.height(8.dp))
        if (paused) {
            PauseCover(levelName, vm.elapsed.collectAsStateWithLifecycle().value, game.filledCount, vm::resume, onRestart, onOpenSettings, onLeave,
                Modifier.fillMaxWidth().height((geo.board + bottom).dp))
            return@Column
        }
        val showMistakes = settings.showMistakes || game.overrides.showMistakes
        val highlights = Highlights.of(game, showMistakes = showMistakes, highlightSame = settings.highlightSameDigit)
        val resources = LocalResources.current
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Board(
                model = BoardModel(
                    values = IntArray(81) { game.value(it) },
                    givens = BooleanArray(81) { game.isGiven(it) },
                    notes = IntArray(81) { game.notes(it) },
                    highlights = highlights,
                ),
                size = geo.board.dp,
                colors = k,
                labels = cellLabels(resources, game, highlights),
                onTapCell = { vm.onAction(Action.TapCell(it)) },
            )
        }
        Box(Modifier.fillMaxWidth().height(bottom.dp)) {
            val panel = when (game.status) {
                Status.LIMIT -> PanelKind.LIMIT
                Status.ALMOST -> PanelKind.ALMOST
                Status.COMPLETED -> PanelKind.COMPLETED
                Status.PLAYING -> PanelKind.PAD
            }
            AnimatedContent(panel, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(150)) }, label = "pad-slot") { kind ->
                when (kind) {
                    PanelKind.PAD -> Column(Modifier.fillMaxSize()) {
                        Spacer(Modifier.weight(1f))
                        if (saveFailed) SaveFailedBar(vm::retrySave)
                        Controls(game, geo.compact, k, vm::onAction)
                        Spacer(Modifier.height(if (geo.compact) 8.dp else 12.dp))
                    }
                    PanelKind.LIMIT -> GamePanel(
                        icon = R.drawable.ic_flag,
                        title = stringResource(R.string.limit_title, game.counters.mistakes),
                        body = stringResource(R.string.limit_body, settings.mistakeLimit),
                        finePrint = stringResource(R.string.limit_fine_print),
                        primary = stringResource(R.string.keep_playing) to { vm.onAction(Action.KeepPlaying) },
                        secondary = stringResource(R.string.restart_puzzle) to { vm.restart() },
                    )
                    PanelKind.ALMOST -> GamePanel(
                        icon = R.drawable.ic_lightbulb,
                        title = stringResource(R.string.almost_title),
                        body = stringResource(R.string.almost_body),
                        finePrint = null,
                        primary = stringResource(R.string.show_them) to { vm.onAction(Action.ShowThem) },
                        secondary = stringResource(R.string.keep_looking) to { vm.onAction(Action.KeepLooking) },
                    )
                    PanelKind.COMPLETED -> {
                        val mistakes = game.counters.mistakes
                        val mistakesText = if (mistakes == 0) stringResource(R.string.no_mistakes) else pluralStringResource(R.plurals.mistakes_plural, mistakes, mistakes)
                        GamePanel(
                            icon = R.drawable.ic_check,
                            title = stringResource(R.string.completed_title),
                            body = stringResource(R.string.completed_meta, levelName, formatElapsed(vm.elapsed.collectAsStateWithLifecycle().value), mistakesText),
                            finePrint = null,
                            primary = stringResource(R.string.new_level_game, levelName) to vm::newGameSameLevel,
                            secondary = stringResource(R.string.back_to_play) to onLeave,
                        )
                    }
                }
            }
        }
    }
}

private enum class PanelKind { PAD, LIMIT, ALMOST, COMPLETED }

@Composable
private fun GameBar(
    levelName: String,
    elapsedMs: Long,
    showTimer: Boolean,
    subtitle: String?,
    paused: Boolean,
    onBack: () -> Unit,
    onPause: () -> Unit,
    onRestart: () -> Unit,
    onDebugNearSolved: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().height(BAR.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back)) }
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(levelName, style = GameTitleStyle)
                if (showTimer) {
                    Spacer(Modifier.width(10.dp))
                    Text(formatElapsed(elapsedMs), style = TimerStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.labelMedium.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize), maxLines = 1)
        }
        IconButton(onClick = onPause) {
            Icon(
                painterResource(if (paused) R.drawable.ic_play else R.drawable.ic_pause),
                contentDescription = stringResource(if (paused) R.string.resume else R.string.pause),
            )
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.more)) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.restart_puzzle)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_restart), contentDescription = null) },
                    onClick = {
                        menu = false
                        onRestart()
                    },
                )
                if (DebugHooks.ENABLED) {
                    DropdownMenuItem(
                        text = { Text("Debug: fill all but one cell") },
                        onClick = {
                            menu = false
                            onDebugNearSolved()
                        },
                    )
                }
            }
        }
    }
}

/** The line under the bar: state in words (not colour alone) and the mistake counter (design game-idle). */
@Composable
private fun StatusLine(status: String?, game: GameState, settings: Settings) {
    val shown = settings.showMistakes || game.overrides.showMistakes
    val limitOn = settings.showMistakes && settings.mistakeLimit > 0 && !game.overrides.limitOff
    Row(Modifier.fillMaxWidth().height(28.dp).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            status.orEmpty(),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
            maxLines = 1,
        )
        if (shown) {
            val n = game.counters.mistakes
            if (n > 0) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(LocalLanternColors.current.danger))
                Spacer(Modifier.width(6.dp))
            }
            Text(
                if (limitOn) stringResource(R.string.mistakes_of_limit, n, settings.mistakeLimit) else stringResource(R.string.mistakes_count, n),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = MaterialTheme.typography.labelMedium.fontSize),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun statusText(game: GameState): String? {
    val input = game.input
    val lock = input.lock
    val sel = input.selection
    return when {
        game.status == Status.LIMIT -> stringResource(R.string.status_limit)
        game.status == Status.ALMOST -> stringResource(R.string.status_almost)
        game.status == Status.COMPLETED -> stringResource(R.string.status_completed)
        lock != null && input.eraseMode -> stringResource(R.string.status_erase, lock)
        lock != null && input.notesMode -> stringResource(R.string.status_locked_notes, lock)
        lock != null -> stringResource(R.string.status_locked, lock)
        input.notesMode -> stringResource(R.string.status_notes)
        sel != null -> stringResource(R.string.status_selected, sel / 9 + 1, sel % 9 + 1)
        else -> null
    }
}

/** US-022 basic labels: "Row r, column c, given d | entry d | empty, notes 1 2 4" + selected / conflict / mistake. */
private fun cellLabels(res: Resources, game: GameState, h: BoardHighlights): CellLabels = CellLabels(
    description = { i ->
        val r = i / 9 + 1
        val c = i % 9 + 1
        val v = game.value(i)
        when {
            game.isGiven(i) -> res.getString(R.string.cell_given, r, c, v)
            v != 0 -> res.getString(R.string.cell_entry, r, c, v)
            game.notes(i) != 0 -> res.getString(R.string.cell_notes, r, c, Masks.digits(game.notes(i)).joinToString(" "))
            else -> res.getString(R.string.cell_empty, r, c)
        }
    },
    state = { i ->
        buildList {
            if (h.has(i, BoardHighlights.SELECTED)) add(res.getString(R.string.cell_selected))
            if (h.has(i, BoardHighlights.MISTAKE)) add(res.getString(R.string.cell_mistake))
            if (h.has(i, BoardHighlights.CONFLICT)) add(res.getString(R.string.cell_conflict))
        }.takeIf { it.isNotEmpty() }?.joinToString(", ")
    },
)

/** Non-blocking save-failed message above the pad, never over keys or the board (design snackbar-save-failed). */
@Composable
private fun SaveFailedBar(onRetry: () -> Unit) {
    Snackbar(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(12.dp),
        containerColor = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        action = { TextButton(onClick = onRetry) { Text(stringResource(R.string.retry), color = MaterialTheme.colorScheme.inversePrimary) } },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_error), contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.save_failed), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Pause (design pause): the board is covered so the clock can't be paused to study it; the timer is stopped. */
@Composable
private fun PauseCover(
    levelName: String,
    elapsedMs: Long,
    filled: Int,
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onOpenSettings: () -> Unit,
    onLeave: () -> Unit,
    modifier: Modifier,
) {
    val k = LocalLanternColors.current
    Column(modifier.padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(k.inkCard).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.paused), style = MaterialTheme.typography.headlineSmall, color = k.onInk)
            Text(stringResource(R.string.paused_meta, levelName, formatElapsed(elapsedMs), filled), style = MaterialTheme.typography.bodyMedium,
                color = k.onInkMuted, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
            PrimaryButton(stringResource(R.string.resume), onResume, icon = R.drawable.ic_play, modifier = Modifier.height(48.dp))
        }
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer, contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Column {
                MenuRow(R.drawable.ic_restart, stringResource(R.string.restart_puzzle), stringResource(R.string.restart_hint), onRestart)
                MenuRow(R.drawable.ic_settings, stringResource(R.string.settings), null, onOpenSettings)
                MenuRow(R.drawable.ic_home, stringResource(R.string.back_to_play), null, onLeave)
            }
        }
        Text(stringResource(R.string.game_saved), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun MenuRow(icon: Int, title: String, sub: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
