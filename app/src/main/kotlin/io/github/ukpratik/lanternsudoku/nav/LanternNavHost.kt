// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.nav

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import io.github.ukpratik.lanternsudoku.AppContainer
import io.github.ukpratik.lanternsudoku.feature.game.GameScreen
import io.github.ukpratik.lanternsudoku.feature.game.GameViewModel
import io.github.ukpratik.lanternsudoku.feature.home.HomeScreen
import io.github.ukpratik.lanternsudoku.feature.home.HomeViewModel
import io.github.ukpratik.lanternsudoku.feature.settings.SettingsScreen
import io.github.ukpratik.lanternsudoku.feature.settings.SettingsViewModel
import kotlinx.coroutines.flow.Flow

/**
 * Home → Game(id) → Settings. ViewModels come from [AppContainer] via `viewModelFactory` (ADR-007). After process death
 * the back stack restores `GameRoute(gameId)` and the Game ViewModel reloads that row (LLD §5).
 *
 * @param openGame games to open on top of Home (debug QA hooks); each emission navigates once.
 */
@Composable
fun LanternNavHost(container: AppContainer, openGame: Flow<Long>, nav: NavHostController = rememberNavController()) {
    LaunchedEffect(nav) {
        openGame.collect { id -> nav.navigate(GameRoute(id)) { popUpTo(HomeRoute) } }
    }
    NavHost(nav, startDestination = HomeRoute) {
        composable<HomeRoute> {
            val vm: HomeViewModel = viewModel(factory = viewModelFactory { initializer { HomeViewModel(container.gameRepository) } })
            HomeScreen(vm, onOpenGame = { nav.navigate(GameRoute(it)) }, onOpenSettings = { nav.navigate(SettingsRoute) })
        }
        composable<GameRoute> { entry ->
            val gameId = entry.toRoute<GameRoute>().gameId
            val vm: GameViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        GameViewModel(
                            gameId = gameId,
                            repository = container.gameRepository,
                            saveQueue = container.saveQueue,
                            settings = container.settingsRepository.settings,
                            now = SystemClock::elapsedRealtime,
                        )
                    }
                },
            )
            GameScreen(
                vm,
                onLeave = { nav.popBackStack(HomeRoute, inclusive = false) },
                onOpenGame = { id -> nav.navigate(GameRoute(id)) { popUpTo(HomeRoute) } },
                onOpenSettings = { nav.navigate(SettingsRoute) },
            )
        }
        composable<SettingsRoute> {
            val vm: SettingsViewModel = viewModel(factory = viewModelFactory { initializer { SettingsViewModel(container.settingsRepository) } })
            SettingsScreen(vm, onBack = { nav.popBackStack() })
        }
    }
}
