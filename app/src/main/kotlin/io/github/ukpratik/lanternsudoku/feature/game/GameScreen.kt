// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.feature.game

import android.content.res.Resources
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import io.github.ukpratik.lanternsudoku.ui.components.DAY_MONTH
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
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
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
import io.github.ukpratik.lanternsudoku.engine.hint.HintPlan
import io.github.ukpratik.lanternsudoku.engine.hint.HintView
import io.github.ukpratik.lanternsudoku.engine.model.Level
import io.github.ukpratik.lanternsudoku.feature.home.ContinueOrNewSheet
import io.github.ukpratik.lanternsudoku.feature.share.ShareText
import io.github.ukpratik.lanternsudoku.ui.components.SHORT_DATE
import androidx.compose.ui.platform.LocalContext
import java.time.LocalDate
import io.github.ukpratik.lanternsudoku.engine.model.Masks
import io.github.ukpratik.lanternsudoku.platform.DebugHooks
import io.github.ukpratik.lanternsudoku.platform.Haptics
import io.github.ukpratik.lanternsudoku.ui.board.Board
import io.github.ukpratik.lanternsudoku.ui.board.BoardGeometry
import io.github.ukpratik.lanternsudoku.ui.board.BoardModel
import io.github.ukpratik.lanternsudoku.ui.board.CellLabels
import io.github.ukpratik.lanternsudoku.ui.components.PrimaryButton
import io.github.ukpratik.lanternsudoku.ui.components.focusOnAppear
import androidx.compose.ui.semantics.heading
import io.github.ukpratik.lanternsudoku.ui.components.formatElapsed
import io.github.ukpratik.lanternsudoku.ui.components.label
import io.github.ukpratik.lanternsudoku.ui.theme.GameTitleStyle
import io.github.ukpratik.lanternsudoku.ui.theme.LocalLanternColors
import io.github.ukpratik.lanternsudoku.ui.theme.TimerStyle


@Composable
fun GameScreen(
    vm: GameViewModel,
    onLeave: () -> Unit,
    onOpenGame: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    /** Leaving the summary of a game just completed, for Home (US-025: the only moment a rate prompt may follow). */
    onCompletionLeft: () -> Unit = {},
) {
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
    var confirmFill by remember { mutableStateOf(false) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        val game = ui.game
        val pastSummary = ui.completion
        val pastGrid = ui.completedGrid
        if (game == null && pastSummary != null && pastGrid != null) {
            // US-016: a past daily already done, opened from the Daily tab, shows its summary (no rate prompt).
            val context = LocalContext.current
            BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.TopCenter) {
                CompletionContent(
                    gridSummary = pastGrid,
                    summary = pastSummary,
                    onClose = onLeave,
                    onNewGame = vm::newGameSameLevel,
                    onShare = { ShareText.share(context, it) },
                    modifier = Modifier.width(minOf(maxWidth.value, BoardGeometry.MAX_COLUMN).dp),
                    animate = false,
                )
            }
        } else if (game == null) {
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing))
        } else {
            BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.TopCenter) {
                val density = LocalDensity.current
                val column = minOf(maxWidth.value, BoardGeometry.MAX_COLUMN)
                // D-037: with large text, compact uses a shorter daily title ("Daily · 15 Oct") so the bar keeps one title line.
                val compactTitle = gameTitle(ui.level, ui.dailyDate, short = true)
                val compactLargeBar = if (density.fontScale >= GameChrome.LARGE_TEXT) {
                    compactLargeBarHeight(compactTitle, ui.settings.showTimer, column)
                } else {
                    0f
                }
                // Labelled tools while the board keeps its 30 dp cells; icon-only when they'd make the column scroll (D-037).
                val labelled = GameChrome.of(density, ui.settings.showTimer, compactLargeBar)
                val labelledGeo = BoardGeometry.compute(maxWidth.value, maxHeight.value, labelled.extraRegular, labelled.extraCompact)
                val chrome = if (labelled.largeText && labelledGeo.compact && labelledGeo.scrolls) {
                    GameChrome.of(density, ui.settings.showTimer, compactLargeBar, iconOnlyTools = true)
                } else {
                    labelled
                }
                val geo = BoardGeometry.compute(maxWidth.value, maxHeight.value, chrome.extraRegular, chrome.extraCompact)
                val completion = ui.completion
                if (game.status == Status.COMPLETED && completion != null) {
                    val context = LocalContext.current
                    CompletionContent(
                        gridSummary = GameState.gridSummary(game),
                        summary = completion,
                        onClose = {
                            onCompletionLeft()
                            onLeave()
                        },
                        onNewGame = vm::newGameSameLevel,
                        onShare = { ShareText.share(context, it) },
                        modifier = Modifier.width(geo.column.dp),
                    )
                    return@BoxWithConstraints
                }
                GameContent(
                    vm = vm,
                    game = game,
                    level = ui.level,
                    title = if (geo.compact && chrome.largeText) compactTitle else gameTitle(ui.level, ui.dailyDate, short = false),
                    settings = ui.settings,
                    paused = ui.paused,
                    saveFailed = ui.saveFailed,
                    change = ui.change,
                    geo = geo,
                    chrome = chrome,
                    availableHeight = maxHeight.value,
                    onRestart = { confirmRestart = true },
                    onFillNotes = {
                        // US-009: ask first only when there are notes to replace (TC-076, TC-079).
                        if ((0 until 81).any { game.notes(it) != 0 }) confirmFill = true else vm.onAction(Action.FillNotes)
                    },
                    onOpenSettings = onOpenSettings,
                    onLeave = onLeave,
                    coach = ui.coachTip?.let { CoachUi(it, vm::onCoachNext, vm::onCoachSkip) },
                )
            }
        }
    }
    ui.continueOrNew?.let { ContinueOrNewSheet(it, vm::onContinueSaved, vm::onStartNewInstead, vm::onDismissContinueOrNew) }
    if (confirmFill) {
        AlertDialog(
            onDismissRequest = { confirmFill = false },
            title = { Text(stringResource(R.string.fill_notes_dialog_title)) },
            text = { Text(stringResource(R.string.fill_notes_dialog_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmFill = false
                    vm.onAction(Action.FillNotes)
                }) { Text(stringResource(R.string.fill_notes_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmFill = false }) { Text(stringResource(R.string.cancel)) } },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        )
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
    title: String,
    settings: Settings,
    paused: Boolean,
    saveFailed: Boolean,
    change: BoardChange?,
    geo: BoardGeometry,
    chrome: GameChrome,
    availableHeight: Float,
    onRestart: () -> Unit,
    onFillNotes: () -> Unit,
    onOpenSettings: () -> Unit,
    onLeave: () -> Unit,
    coach: CoachUi?,
) {
    val k = LocalLanternColors.current
    val levelName = stringResource(level.label)
    val resources = LocalResources.current
    // Compact folds the status into the bar subtitle with the short forms (design game-compact-360x740).
    val status = statusText(game, short = geo.compact) ?: HintCopy.of(resources, game)?.status
    // The space under the board: whatever is left, but never less than the controls need (then the column scrolls).
    val above = chrome.bar(geo.compact) + chrome.statusLine(geo.compact) + geo.board
    val minBottom = geo.controls - (above - geo.board)
    val bottom = maxOf(availableHeight - above, minBottom)
    Column(
        Modifier.width(geo.column.dp).fillMaxSize().then(if (geo.scrolls) Modifier.verticalScroll(rememberScrollState()) else Modifier),
    ) {
        GameBar(
            levelName = title,
            elapsedMs = vm.elapsed.collectAsStateWithLifecycle().value,
            showTimer = settings.showTimer,
            subtitle = if (geo.compact) {
                { CompactSubtitle(status, mistakesText(game, settings)) }
            } else {
                null
            },
            height = chrome.bar(geo.compact),
            largeText = chrome.largeText,
            compactLarge = geo.compact && chrome.largeText,
            paused = paused,
            onBack = onLeave, // the bar arrow leaves; system Back follows the priority order (design §8.2)
            onPause = { if (paused) vm.resume() else vm.pause() },
            onRestart = onRestart,
            onFillNotes = onFillNotes,
            onDebugNearSolved = vm::debugNearSolved,
        )
        if (geo.compact) Spacer(Modifier.height(GameChrome.COMPACT_GAP.dp))
        else StatusLine(status, game, settings, chrome.status, chrome.largeText)
        if (paused) {
            PauseCover(levelName, vm.elapsed.collectAsStateWithLifecycle().value, game.filledCount, vm::resume, onRestart, onOpenSettings, onLeave,
                Modifier.fillMaxWidth().height((geo.board + bottom).dp), onFillNotes = {
                    vm.resume()
                    onFillNotes()
                }, onDebugNearSolved = {
                    vm.resume()
                    vm.debugNearSolved()
                })
            return@Column
        }
        val showMistakes = settings.showMistakes || game.overrides.showMistakes
        val highlights = Highlights.of(game, showMistakes = showMistakes, highlightSame = settings.highlightSameDigit)
        val hintView = HintView.of(game)
        // BL-015 (design §7): Almost! rises after a 400 ms pause, so the last digit lands before the panel replaces the pad.
        var almostDue by remember { mutableStateOf(false) }
        LaunchedEffect(game.status) {
            almostDue = false
            if (game.status == Status.ALMOST) {
                delay(ALMOST_DELAY_MS)
                almostDue = true
            }
        }
        val panel = when (game.status) {
            Status.LIMIT -> PanelKind.LIMIT
            Status.ALMOST -> if (almostDue) PanelKind.ALMOST else PanelKind.PAD
            Status.COMPLETED -> PanelKind.COMPLETED
            Status.PLAYING -> if (game.input.hint != null) PanelKind.HINT else PanelKind.PAD
        }
        // ISS-001/002: a panel takes the height it needs and the board gives way, down to the 30 dp cell floor (the
        // board stays whole and visible). Only what still doesn't fit scrolls inside the panel; its buttons stay pinned.
        BoardAndSlot(
            board = geo.board,
            slot = bottom,
            floor = BoardGeometry.BOARD_FLOOR,
            boardContent = { size ->
                Box(contentAlignment = Alignment.Center) {
                    Board(
                        model = BoardModel(
                            values = IntArray(81) { game.value(it) },
                            givens = BooleanArray(81) { game.isGiven(it) },
                            notes = IntArray(81) { game.notes(it) },
                            highlights = highlights,
                            hint = hintView,
                        ),
                        size = size,
                        colors = k,
                        labels = cellLabels(resources, game, highlights, hintView),
                        onTapCell = { vm.onAction(Action.TapCell(it)) },
                    )
                    // US-022: the last move, read politely by TalkBack ("Placed 4, row 2, column 8"). One stable live-region
                    // node whose text changes (instead of the deprecated View.announceForAccessibility); 1 dp, nothing drawn.
                    val said = change?.describe(resources).orEmpty()
                    Box(Modifier.align(Alignment.TopStart).size(1.dp).semantics {
                        liveRegion = LiveRegionMode.Polite
                        if (said.isNotEmpty()) contentDescription = said // no text, no stop for TalkBack's swipe order
                        testTag = "move-announcement"
                    })
                }
            },
        ) {
            // Design §7: 220 ms fade + 16 dp rise in, 150 ms fade out. Compose scales these by the system animator duration
            // scale, so "Remove animations" makes the swap instant (reduced motion).
            val rise = with(LocalDensity.current) { 16.dp.roundToPx() }
            val slotMin = Modifier.fillMaxWidth().heightIn(min = bottom.dp)
            AnimatedContent(
                panel,
                transitionSpec = { (fadeIn(tween(220)) + slideInVertically(tween(220)) { rise }) togetherWith fadeOut(tween(150)) },
                label = "pad-slot",
            ) { kind ->
                when (kind) {
                    PanelKind.PAD -> Column(Modifier.fillMaxWidth().height(bottom.dp)) {
                        Spacer(Modifier.weight(1f))
                        if (saveFailed) SaveFailedBar(vm::retrySave)
                        Controls(game, geo.compact, k, vm::onAction, height = chrome.pad(geo.compact).dp,
                            toolHeight = chrome.tool(geo.compact).dp, largeText = chrome.largeText,
                            iconOnlyTools = geo.compact && chrome.iconOnlyTools, coach = coach)
                        Spacer(Modifier.height(if (geo.compact) 8.dp else 12.dp))
                    }
                    PanelKind.HINT -> HintPanel(game, vm::onAction, slotMin)
                    PanelKind.LIMIT -> GamePanel(
                        icon = R.drawable.ic_flag,
                        title = stringResource(R.string.limit_title, game.counters.mistakes),
                        body = stringResource(R.string.limit_body, settings.mistakeLimit),
                        finePrint = stringResource(R.string.limit_fine_print),
                        primary = stringResource(R.string.keep_playing) to { vm.onAction(Action.KeepPlaying) },
                        secondary = stringResource(R.string.restart_puzzle) to { vm.restart() },
                        modifier = slotMin,
                        compact = geo.compact,
                    )
                    PanelKind.ALMOST -> GamePanel(
                        icon = R.drawable.ic_lightbulb,
                        title = stringResource(R.string.almost_title),
                        body = stringResource(R.string.almost_body),
                        finePrint = null,
                        primary = stringResource(R.string.show_them) to { vm.onAction(Action.ShowThem) },
                        secondary = stringResource(R.string.keep_looking) to { vm.onAction(Action.KeepLooking) },
                        modifier = slotMin,
                        compact = geo.compact,
                    )
                    // The summary replaces the whole screen once the completing write is read back (a few ms).
                    PanelKind.COMPLETED -> Box(Modifier.fillMaxWidth().height(bottom.dp))
                }
            }
        }
    }
}

/**
 * The board above the pad slot (design §4.1). The slot's content is measured first, between the slot's own height and
 * whatever leaves the board at [floor]; the board takes the rest, centred, never larger than [board]. With the pad in
 * the slot nothing changes; a taller panel shrinks the board instead of squeezing its own text (ISS-001, ISS-002).
 */
@Composable
private fun BoardAndSlot(
    board: Float,
    slot: Float,
    floor: Float,
    boardContent: @Composable (Dp) -> Unit,
    slotContent: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    Layout(
        contents = listOf(
            // The board's size comes from the height it's given (a square, centred), so it follows the slot in the same pass.
            { BoxWithConstraints(contentAlignment = Alignment.Center) { boardContent(minOf(maxWidth, maxHeight)) } },
            slotContent,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) { (boardMeasurables, slotMeasurables), constraints ->
        val total = with(density) { (board + slot).dp.roundToPx() }
        val boardMax = with(density) { board.dp.roundToPx() }
        val maxSlot = total - with(density) { minOf(board, floor).dp.roundToPx() }
        val slotPlaceable = slotMeasurables.first().measure(constraints.copy(minHeight = 0, maxHeight = maxSlot))
        val boardPx = (total - slotPlaceable.height).coerceAtMost(boardMax)
        val boardPlaceable = boardMeasurables.first().measure(Constraints.fixed(constraints.maxWidth, boardPx))
        layout(constraints.maxWidth, total) {
            boardPlaceable.place(0, 0)
            slotPlaceable.place(0, total - slotPlaceable.height)
        }
    }
}

private enum class PanelKind { PAD, HINT, LIMIT, ALMOST, COMPLETED }

private const val ALMOST_DELAY_MS = 400L

@Composable
private fun GameBar(
    levelName: String,
    elapsedMs: Long,
    showTimer: Boolean,
    subtitle: (@Composable () -> Unit)?,
    height: Float,
    largeText: Boolean,
    compactLarge: Boolean,
    paused: Boolean,
    onBack: () -> Unit,
    onPause: () -> Unit,
    onRestart: () -> Unit,
    onFillNotes: () -> Unit,
    onDebugNearSolved: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().height(height.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back)) }
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            if (compactLarge) {
                // D-037: compact + large text keeps one title row (the timer wraps under the title only when both don't
                // fit) and one subtitle line, measured by compactLargeBarHeight, so the board and pad stay on screen.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(GameChrome.TITLE_TIMER_GAP.dp), itemVerticalAlignment = Alignment.Bottom) {
                    Text(levelName, style = GameTitleStyle, maxLines = 2)
                    if (showTimer) Text(formatElapsed(elapsedMs), style = TimerStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            } else if (largeText) {
                // US-021: with large text the title may take two lines and the timer goes under it (GameChrome budget).
                Text(levelName, style = GameTitleStyle, maxLines = 2)
                if (showTimer) Text(formatElapsed(elapsedMs), style = TimerStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(levelName, style = GameTitleStyle, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                    if (showTimer) {
                        Spacer(Modifier.width(10.dp))
                        Text(formatElapsed(elapsedMs), style = TimerStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
            subtitle?.invoke()
        }
        IconButton(onClick = onPause) {
            Icon(
                painterResource(if (paused) R.drawable.ic_play else R.drawable.ic_pause),
                contentDescription = stringResource(if (paused) R.string.resume else R.string.pause),
            )
        }
        // D-037: no ⋮ in compact + large text; its only entry, Restart puzzle, is also on the Pause screen.
        if (!compactLarge) Box {
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
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.fill_notes)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_edit), contentDescription = null) },
                    onClick = {
                        menu = false
                        onFillNotes()
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
private fun StatusLine(status: String?, game: GameState, settings: Settings, height: Float, largeText: Boolean) {
    val shown = settings.showMistakes || game.overrides.showMistakes
    val limitOn = settings.showMistakes && settings.mistakeLimit > 0 && !game.overrides.limitOff
    val statusText: @Composable (Modifier) -> Unit = { m ->
        Text(
            status.orEmpty(),
            style = MaterialTheme.typography.labelMedium,
            modifier = m.semantics { liveRegion = LiveRegionMode.Polite },
            maxLines = if (largeText) 2 else 1,
        )
    }
    val mistakes: @Composable () -> Unit = {
        if (shown) {
            val n = game.counters.mistakes
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (n > 0) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(LocalLanternColors.current.danger))
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    if (limitOn) stringResource(R.string.mistakes_of_limit, n, settings.mistakeLimit) else stringResource(R.string.mistakes_count, n),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = MaterialTheme.typography.labelMedium.fontSize),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
    if (largeText) {
        // US-021: two lines for the state, the mistake count on its own line (GameChrome budget).
        Column(Modifier.fillMaxWidth().height(height.dp).padding(horizontal = 18.dp, vertical = 4.dp)) {
            statusText(Modifier)
            Spacer(Modifier.weight(1f))
            mistakes()
        }
    } else {
        Row(Modifier.fillMaxWidth().height((height - 4).dp).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            statusText(Modifier.weight(1f))
            mistakes()
        }
        Spacer(Modifier.height(4.dp))
    }
}

/** The state in words; [short] is the compact bar's form ("5 locked", design game-compact-360x740). */
@Composable
private fun statusText(game: GameState, short: Boolean = false): String? {
    val input = game.input
    val lock = input.lock
    val sel = input.selection
    return when {
        game.status == Status.LIMIT -> stringResource(R.string.status_limit)
        game.status == Status.ALMOST -> stringResource(R.string.status_almost)
        game.status == Status.COMPLETED -> stringResource(R.string.status_completed)
        lock != null && input.eraseMode -> stringResource(if (short) R.string.status_erase_short else R.string.status_erase, lock)
        lock != null && input.notesMode -> stringResource(if (short) R.string.status_locked_notes_short else R.string.status_locked_notes, lock)
        lock != null -> stringResource(if (short) R.string.status_locked_short else R.string.status_locked, lock)
        input.notesMode -> stringResource(if (short) R.string.status_notes_short else R.string.status_notes)
        sel != null -> stringResource(if (short) R.string.status_selected_short else R.string.status_selected, sel / 9 + 1, sel % 9 + 1)
        else -> null
    }
}

/** "Mistakes 1", or "Mistakes 2 of 3" with a limit (design §9); null when mistakes aren't shown. */
@Composable
private fun mistakesText(game: GameState, settings: Settings): String? {
    if (!(settings.showMistakes || game.overrides.showMistakes)) return null
    val n = game.counters.mistakes
    val limitOn = settings.showMistakes && settings.mistakeLimit > 0 && !game.overrides.limitOff
    return if (limitOn) stringResource(R.string.mistakes_of_limit, n, settings.mistakeLimit) else stringResource(R.string.mistakes_count, n)
}

/** The bar title: the level, or "Daily · Thu 15 Oct" ([short]: "Daily · 15 Oct"); a daily keeps its own date (design state-daily-date-changed). */
@Composable
private fun gameTitle(level: Level, dailyDate: LocalDate?, short: Boolean): String =
    dailyDate?.let { stringResource(R.string.game_title_daily, it.format(if (short) DAY_MONTH else SHORT_DATE)) } ?: stringResource(level.label)

private val CompactSubtitleStyle: TextStyle
    @Composable get() = MaterialTheme.typography.labelMedium.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize)

/**
 * The compact bar's height with large text (D-037): the title row (title and timer side by side when both fit,
 * otherwise the timer under the title) plus one subtitle line, from the real text at this font scale. Measured once per
 * title and width, so the board never moves as the clock or the status changes.
 */
@Composable
private fun compactLargeBarHeight(title: String, showTimer: Boolean, column: Float): Float {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val subtitleStyle = CompactSubtitleStyle
    return remember(title, showTimer, column, density, measurer, subtitleStyle) {
        with(density) {
            val width = GameChrome.titleWidth(column).dp.roundToPx()
            val titleFull = measurer.measure(title, GameTitleStyle, constraints = Constraints(maxWidth = width), maxLines = 2).size.height
            val titleOneLine = measurer.measure(title, GameTitleStyle, softWrap = false).size.width
            val timer = measurer.measure("0:00:00", TimerStyle, softWrap = false).size // the widest time, so it never re-wraps
            val subtitle = measurer.measure("Mistakes 0", subtitleStyle, softWrap = false).size.height
            val row = when {
                !showTimer -> titleFull
                titleOneLine + GameChrome.TITLE_TIMER_GAP.dp.roundToPx() + timer.width <= width -> maxOf(titleFull, timer.height)
                else -> titleFull + timer.height
            }
            GameChrome.compactLargeBar(row.toDp().value, subtitle.toDp().value)
        }
    }
}

/**
 * The compact bar subtitle: "5 locked · Mistakes 1" (design game-compact-360x740, ISS-003). One line; the mistake count
 * always shows, and the state goes first when there's room for both.
 */
@Composable
private fun CompactSubtitle(status: String?, mistakes: String?) {
    val style = CompactSubtitleStyle
    Layout(
        content = {
            Text(status.orEmpty(), style = style, maxLines = 1, softWrap = false)
            Text(" · ", style = style, maxLines = 1, softWrap = false)
            Text(mistakes.orEmpty(), style = style, maxLines = 1, softWrap = false, modifier = Modifier.semantics { testTag = "bar-mistakes" })
        },
    ) { (statusM, sepM, mistakesM), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val m = if (mistakes != null) mistakesM.measure(loose) else null
        val sep = sepM.measure(loose)
        val room = constraints.maxWidth - (m?.let { it.width + sep.width } ?: 0)
        val s = if (status != null && statusM.maxIntrinsicWidth(Constraints.Infinity) <= room) statusM.measure(loose) else null
        val h = maxOf(m?.height ?: 0, s?.height ?: 0, sep.height)
        layout(constraints.maxWidth, h) {
            var x = 0
            s?.let { it.place(x, 0); x += it.width }
            if (s != null && m != null) { sep.place(x, 0); x += sep.width }
            m?.place(x, 0)
        }
    }
}

/**
 * US-022 basic labels: "Row r, column c, given d | entry d | empty, notes 1 2 4" + selected / conflict / mistake. While
 * a hint is open, role cells add their role ("answer cell", "reason", "9 ruled out") and show the hint's candidates.
 */
internal fun cellLabels(res: Resources, game: GameState, h: BoardHighlights, hint: HintView? = null): CellLabels = CellLabels(
    description = { i ->
        val r = i / 9 + 1
        val c = i % 9 + 1
        val v = game.value(i)
        val notes = hint?.candidates?.get(i)?.takeIf { it >= 0 } ?: game.notes(i)
        when {
            game.isGiven(i) -> res.getString(R.string.cell_given, r, c, v)
            v != 0 -> res.getString(R.string.cell_entry, r, c, v)
            notes != 0 -> res.getString(R.string.cell_notes, r, c, Masks.digits(notes).joinToString(" "))
            else -> res.getString(R.string.cell_empty, r, c)
        }
    },
    state = { i ->
        buildList {
            if (h.has(i, BoardHighlights.SELECTED)) add(res.getString(R.string.cell_selected))
            if (h.has(i, BoardHighlights.MISTAKE)) add(res.getString(R.string.cell_mistake))
            if (h.has(i, BoardHighlights.CONFLICT)) add(res.getString(R.string.cell_conflict))
            if (hint != null) addAll(hintRoles(res, game, hint, i))
        }.takeIf { it.isNotEmpty() }?.joinToString(", ")
    },
)

private fun hintRoles(res: Resources, game: GameState, hint: HintView, i: Int): List<String> = buildList {
    val wrong = game.input.hint?.plan is HintPlan.WrongDigit
    when {
        hint.placed == i -> add(res.getString(R.string.hint_role_placed))
        hint.target == i -> add(res.getString(if (wrong) R.string.hint_role_wrong else R.string.hint_role_target))
    }
    if (hint.cause[i]) add(res.getString(R.string.hint_role_cause))
    if (hint.struck[i] != 0) add(res.getString(R.string.hint_role_struck, Masks.digits(hint.struck[i]).joinToString(" ")))
    if (isEmpty() && hint.house[i] && hint.target < 0) add(res.getString(R.string.hint_role_where))
}

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
    onFillNotes: () -> Unit,
    onDebugNearSolved: (() -> Unit)? = null,
) {
    val k = LocalLanternColors.current
    Column(modifier.padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(k.inkCard).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.paused), style = MaterialTheme.typography.headlineSmall, color = k.onInk,
                modifier = Modifier.focusOnAppear().semantics { heading() })
            Text(stringResource(R.string.paused_meta, levelName, formatElapsed(elapsedMs), filled), style = MaterialTheme.typography.bodyMedium,
                color = k.onInkMuted, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
            PrimaryButton(stringResource(R.string.resume), onResume, icon = R.drawable.ic_play, modifier = Modifier.heightIn(min = 48.dp))
        }
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer, contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Column {
                MenuRow(R.drawable.ic_restart, stringResource(R.string.restart_puzzle), stringResource(R.string.restart_hint), onRestart)
                MenuRow(R.drawable.ic_edit, stringResource(R.string.fill_notes), stringResource(R.string.fill_notes_hint), onFillNotes)
                MenuRow(R.drawable.ic_settings, stringResource(R.string.settings), null, onOpenSettings)
                MenuRow(R.drawable.ic_home, stringResource(R.string.back_to_play), null, onLeave)
                // Debug builds: the ⋮ menu's QA entry, for layouts without ⋮ (compact + large text, D-037).
                if (DebugHooks.ENABLED && onDebugNearSolved != null) MenuRow(R.drawable.ic_notes, "Debug: fill all but one cell", null, onDebugNearSolved)
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
