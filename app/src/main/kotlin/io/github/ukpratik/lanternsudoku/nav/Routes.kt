// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.lanternsudoku.nav

import kotlinx.serialization.Serializable

/** One flat NavHost with type-safe routes (LLD §5). Completion is a Game-screen state, not a route. */
@Serializable
data object HomeRoute

@Serializable
data class GameRoute(val gameId: Long)

@Serializable
data object SettingsRoute
