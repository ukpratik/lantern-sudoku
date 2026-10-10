// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku

import android.content.Intent
import android.graphics.Color
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

    override fun onCreate(savedInstanceState: Bundle?) {
        // M2 ships the light palette only (M5 adds dark and high contrast), so the system bars keep dark icons.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val container = (application as LanternApp).container
        setContent {
            LanternTheme {
                LanternNavHost(container, openGame.receiveAsFlow())
            }
        }
        if (savedInstanceState == null) handleDebug(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDebug(intent)
    }

    /** Debug-build QA hooks only; the release [DebugHooks] never returns a command. */
    private fun handleDebug(intent: Intent?) {
        if (!DebugHooks.ENABLED) return
        val command = DebugHooks.parse(intent) ?: return
        val container = (application as LanternApp).container
        lifecycleScope.launch {
            val id = when (command) {
                is DebugCommand.Load -> container.gameRepository.startWith(command.id, abandonExisting = true)
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
