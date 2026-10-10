// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import android.graphics.Color
import androidx.core.graphics.drawable.toDrawable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ukpratik.lanternsudoku.ui.theme.LanternPalette
import io.github.ukpratik.lanternsudoku.ui.theme.paletteFor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import io.github.ukpratik.lanternsudoku.nav.LanternNavHost
import io.github.ukpratik.lanternsudoku.platform.DebugCommand
import io.github.ukpratik.lanternsudoku.platform.DebugHooks
import io.github.ukpratik.lanternsudoku.ui.theme.LanternTheme
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val openGame = Channel<Long>(Channel.BUFFERED)

    /** Date, time and time-zone changes while the app is visible refresh "today" (LLD §5; no background work). */
    private val dateChanged = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            (application as LanternApp).container.today.refresh()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as LanternApp).container
        setContent {
            // Prefetched in LanternApp, so the first frame normally has the saved theme; until then only the window
            // background shows (a few ms), never a wrong-theme flash. Changes recompose instantly (US-020).
            val settings by container.settingsState.collectAsStateWithLifecycle()
            val s = settings ?: return@setContent
            val palette = paletteFor(s.theme, s.highContrast)
            LaunchedEffect(palette) { applySystemBars(palette) }
            LanternTheme(palette, largeDigits = s.largeDigits) {
                LanternNavHost(container, openGame.receiveAsFlow())
            }
        }
        if (savedInstanceState == null) handleDebug(intent)
    }

    /** Status and navigation bar icons follow the app's palette, not the system's, when the player picks a theme. */
    private fun applySystemBars(palette: LanternPalette) {
        val style = if (palette.dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        window.setBackgroundDrawable(palette.colorScheme.background.toArgb().toDrawable())
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(this, dateChanged, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        (application as LanternApp).container.today.refresh()
    }

    override fun onStop() {
        unregisterReceiver(dateChanged)
        super.onStop()
    }

    /**
     * A running app gets hook intents here only when they are sent with `am start --activity-single-top` (BL-016): a plain
     * `am start -n` matching the task's root intent just brings the task to the front without delivering the extras.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDebug(intent)
    }

    /** Debug-build QA hooks only; the release [DebugHooks] never returns a command. */
    private fun handleDebug(intent: Intent?) {
        if (!DebugHooks.ENABLED) return
        val container = (application as LanternApp).container
        if (DebugHooks.applyDate(intent)) container.today.refresh()
        val command = DebugHooks.parse(intent) ?: return
        lifecycleScope.launch {
            val id = when (command) {
                is DebugCommand.Load -> {
                    command.board?.let(DebugHooks::requestBoard)
                    container.gameRepository.startWith(command.id, abandonExisting = true)
                }
                DebugCommand.Daily -> container.gameRepository.openOrCreateDaily(container.today.refresh()) ?: return@launch
                DebugCommand.NearSolved -> {
                    val card = container.gameRepository.continueCard().first() ?: return@launch
                    DebugHooks.requestNearSolved()
                    card.gameId
                }
            }
            openGame.send(id)
        }
    }
}
